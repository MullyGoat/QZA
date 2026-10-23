package com.qza.util;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.qza.QZA;
import com.qza.chat.ChatHistory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PlayerFaces {
    private static final long REFRESH_MS = 5000L;
    private static final String TEXTURES = "textures";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SAVED_TYPE = new TypeToken<LinkedHashMap<String, Saved>>() {
    }.getType();

    private static final Map<String, Optional<PlayerSkin>> cache = new HashMap<>();
    private static final Map<String, Known> seen = new HashMap<>();
    private static final Set<String> loading = new HashSet<>();
    private static Map<String, Saved> saved;
    private static long cachedAt;

    private PlayerFaces() {
    }

    private record Known(PlayerSkin skin, Saved textures) {
    }

    private record Saved(String uuid, String name, String value, String signature) {
    }

    public static PlayerSkin skinFor(String name) {
        if (name == null || name.isBlank()) {
            return DefaultPlayerSkin.getDefaultSkin();
        }

        long now = System.currentTimeMillis();
        if (now - cachedAt > REFRESH_MS) {
            cache.clear();
            cachedAt = now;
        }

        String key = name.toLowerCase(Locale.ROOT);
        Optional<PlayerSkin> live = cache.computeIfAbsent(key, k -> fromTab(name, k));
        Known known = seen.get(key);
        if (known != null) {
            keepForDms(key, known);
            return known.skin();
        }
        if (live.isPresent()) {
            return live.get();
        }
        loadSaved(key);
        return DefaultPlayerSkin.get(offlineId(name));
    }

    public static void clearCache() {
        cache.clear();
    }

    private static Optional<PlayerSkin> fromTab(String name, String key) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return Optional.empty();
        }
        PlayerInfo info = connection.getPlayerInfoIgnoreCase(name);
        if (info == null) {
            return Optional.empty();
        }
        PlayerSkin skin = info.getSkin();
        GameProfile profile = info.getProfile();
        Property textures = textures(profile);
        if (textures != null && !skin.equals(DefaultPlayerSkin.get(profile.id()))) {
            seen.put(key, new Known(skin, new Saved(profile.id().toString(), profile.name(), textures.value(),
                    textures.signature())));
        }
        return Optional.of(skin);
    }

    private static Property textures(GameProfile profile) {
        for (Property property : profile.properties().get(TEXTURES)) {
            if (property.value() != null && !property.value().isEmpty()) {
                return property;
            }
        }
        return null;
    }

    private static void keepForDms(String key, Known known) {
        Map<String, Saved> all = saved();
        Saved old = all.get(key);
        if (old != null && old.value().equals(known.textures().value())) {
            return;
        }
        if (ChatHistory.get(key) == null || !ChatHistory.savesToDisk()) {
            return;
        }
        all.put(key, known.textures());
        Map<String, Saved> snapshot = new LinkedHashMap<>(all);
        Util.ioPool().execute(() -> write(snapshot));
    }

    private static void loadSaved(String key) {
        if (loading.contains(key)) {
            return;
        }
        Saved entry = saved().get(key);
        if (entry == null) {
            return;
        }
        loading.add(key);
        GameProfile profile;
        try {
            profile = new GameProfile(UUID.fromString(entry.uuid()), entry.name(), new PropertyMap(
                    ImmutableMultimap.of(TEXTURES, new Property(TEXTURES, entry.value(), entry.signature()))));
        } catch (IllegalArgumentException e) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        client.getSkinManager().get(profile).thenAccept(skin -> skin.ifPresent(found ->
                client.execute(() -> seen.putIfAbsent(key, new Known(found, entry)))));
    }

    private static Map<String, Saved> saved() {
        if (saved == null) {
            saved = read();
        }
        return saved;
    }

    private static Path file() {
        return Path.of(System.getProperty("user.home"), ".qza", "skins.json");
    }

    private static Map<String, Saved> read() {
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Saved> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), SAVED_TYPE);
            return loaded == null ? new LinkedHashMap<>() : new LinkedHashMap<>(loaded);
        } catch (IOException | RuntimeException e) {
            QZA.LOGGER.warn("Could not read saved DM skins from {}", file, e);
            return new LinkedHashMap<>();
        }
    }

    private static synchronized void write(Map<String, Saved> snapshot) {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("skins.json.tmp");
            Files.writeString(tmp, GSON.toJson(snapshot, SAVED_TYPE), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            QZA.LOGGER.warn("Could not save DM skins to {}", file, e);
        }
    }

    private static UUID offlineId(String name) {
        String seed = "OfflinePlayer:" + name;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
