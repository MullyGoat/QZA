package com.qza.custom;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qza.QZA;
import com.qza.config.ConfigManager;
import com.qza.config.CustomItemEntry;
import com.qza.config.QZAConfig;
import com.qza.inventory.ItemProtection;
import com.qza.util.IgnUtil;
import com.qza.util.SkyBlockArea;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.item.ItemStack;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SharedItems {
    private static final String API = "https://qza-items.qza.workers.dev/items";
    private static final long FETCH_MS = 120_000L;
    private static final long SETTLE_MS = 5_000L;
    private static final long RETRY_MS = 60_000L;
    private static final long LOGIN_RETRY_MS = 600_000L;
    private static final int MAX_ITEMS = 40;
    private static final String NONE = "[]";
    private static final String SIGNED_PREFIX = "qza-items\n";

    private static HttpClient client;
    private static volatile Map<UUID, List<Shared>> others = Map.of();
    private static volatile boolean fetching;
    private static volatile boolean uploading;
    private static volatile long fetchedAt;
    private static volatile long failedAt;
    private static volatile long retryAfter = RETRY_MS;
    private static volatile String sent;
    private static String pending;
    private static String payload = NONE;
    private static long changedAt;
    private static long builtAt;

    private SharedItems() {
    }

    private record Shared(String uuid, String itemId, String name, CustomItemEntry look) {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(SharedItems::tick);
    }

    public static boolean enabled() {
        return ConfigManager.get().sharedItemsEnabled;
    }

    private static void tick(Minecraft mc) {
        QZAConfig cfg = ConfigManager.get();
        if (mc.player == null || !SkyBlockArea.onSkyBlock()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (cfg.sharedItemsEnabled && cfg.sharedItemsShow && !fetching && now - fetchedAt > FETCH_MS) {
            fetch();
        }
        boolean sharing = cfg.sharedItemsEnabled && cfg.sharedItemsShare && cfg.customItemsEnabled;
        if (now - builtAt > 1000L) {
            builtAt = now;
            payload = sharing ? payload() : NONE;
        }
        String wanted = payload;
        if (sent == null && NONE.equals(wanted) && !cfg.sharedItemsUploaded) {
            sent = NONE;
        }
        if (wanted.equals(sent)) {
            pending = null;
            return;
        }
        if (!wanted.equals(pending)) {
            pending = wanted;
            changedAt = now;
        }
        if (!uploading && now - changedAt > SETTLE_MS && now - failedAt > retryAfter) {
            upload(wanted);
        }
    }

    private static String payload() {
        JsonArray items = new JsonArray();
        for (Map.Entry<String, CustomItemEntry> e : CustomItems.entries().entrySet()) {
            if (items.size() >= MAX_ITEMS) {
                break;
            }
            CustomItemEntry entry = e.getValue();
            JsonObject item = new JsonObject();
            item.addProperty("u", e.getKey().toLowerCase(Locale.ROOT));
            item.addProperty("i", entry.itemId == null ? "" : entry.itemId);
            item.addProperty("n", entry.name == null ? "" : entry.name);
            item.addProperty("t", entry.type);
            item.addProperty("s", entry.style == null ? "" : entry.style);
            item.addProperty("v", entry.variant == null ? "" : entry.variant);
            item.addProperty("c", entry.colour == null ? "" : entry.colour);
            item.addProperty("f", entry.finish == null ? "" : entry.finish);
            item.addProperty("p", entry.seed);
            item.addProperty("w", entry.wear == null ? "" : entry.wear);
            item.addProperty("z", entry.size);
            items.add(item);
        }
        return items.toString();
    }

    static synchronized HttpClient http() {
        if (client == null) {
            client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
        return client;
    }

    private static void upload(String items) {
        uploading = true;
        Minecraft mc = Minecraft.getInstance();
        User user = mc.getUser();
        mc.getProfileKeyPairManager().prepareKeyPair().thenAcceptAsync(found -> {
            ProfileKeyPair pair = found.orElse(null);
            if (pair == null) {
                failedAt = System.currentTimeMillis();
                retryAfter = LOGIN_RETRY_MS;
                QZA.LOGGER.info("QZA couldn't get your Minecraft chat key, so Shared Custom Items can't upload");
                return;
            }
            JsonObject body;
            try {
                body = signed(user, pair, SIGNED_PREFIX, items);
            } catch (GeneralSecurityException e) {
                failedAt = System.currentTimeMillis();
                retryAfter = LOGIN_RETRY_MS;
                QZA.LOGGER.info("QZA couldn't sign your Shared Custom Items: {}", e.getMessage());
                return;
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(API))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "QZA")
                    .PUT(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            try {
                HttpResponse<String> response = http().send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    sent = items;
                    retryAfter = RETRY_MS;
                    QZA.LOGGER.info("QZA shared your custom items with other QZA users");
                    mc.execute(() -> {
                        ConfigManager.get().sharedItemsUploaded = !NONE.equals(items);
                        ConfigManager.save();
                    });
                } else {
                    failedAt = System.currentTimeMillis();
                    retryAfter = RETRY_MS;
                    QZA.LOGGER.info("QZA Shared Custom Items upload failed ({}): {}", response.statusCode(),
                            response.body());
                }
            } catch (Exception e) {
                failedAt = System.currentTimeMillis();
                retryAfter = RETRY_MS;
                QZA.LOGGER.info("QZA Shared Custom Items upload failed: {}", e.getMessage());
            }
        }).whenComplete((v, t) -> {
            uploading = false;
            if (t != null) {
                failedAt = System.currentTimeMillis();
                retryAfter = RETRY_MS;
                QZA.LOGGER.info("QZA Shared Custom Items upload failed: {}", t.getMessage());
            }
        });
    }

    static JsonObject signed(User user, ProfileKeyPair pair, String prefix, String items)
            throws GeneralSecurityException {
        ProfilePublicKey.Data key = pair.publicKey().data();
        String uuid = user.getProfileId().toString().replace("-", "");
        long time = System.currentTimeMillis();
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.privateKey());
        signer.update((prefix + uuid + "\n" + user.getName() + "\n" + time + (items == null ? "" : "\n" + items))
                .getBytes(StandardCharsets.UTF_8));
        Base64.Encoder base64 = Base64.getEncoder();
        JsonObject body = new JsonObject();
        body.addProperty("uuid", uuid);
        body.addProperty("name", user.getName());
        body.addProperty("key", base64.encodeToString(key.key().getEncoded()));
        body.addProperty("expires", key.expiresAt().toEpochMilli());
        body.addProperty("keySig", base64.encodeToString(key.keySignature()));
        body.addProperty("time", time);
        if (items != null) {
            body.addProperty("items", items);
        }
        body.addProperty("sig", base64.encodeToString(signer.sign()));
        return body;
    }

    private static void fetch() {
        fetching = true;
        fetchedAt = System.currentTimeMillis();
        HttpRequest request = HttpRequest.newBuilder(URI.create(API))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "QZA")
                .GET()
                .build();
        http().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        others = parse(response.body());
                    }
                })
                .exceptionally(t -> {
                    QZA.LOGGER.info("QZA couldn't load Shared Custom Items: {}", t.getMessage());
                    return null;
                })
                .whenComplete((v, t) -> fetching = false);
    }

    private static Map<UUID, List<Shared>> parse(String body) {
        Map<UUID, List<Shared>> out = new HashMap<>();
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.has("players")) {
            return out;
        }
        for (Map.Entry<String, JsonElement> player : root.getAsJsonObject("players").entrySet()) {
            UUID id = uuid(player.getKey());
            if (id == null || !player.getValue().isJsonArray()) {
                continue;
            }
            List<Shared> list = new ArrayList<>();
            for (JsonElement element : player.getValue().getAsJsonArray()) {
                if (!element.isJsonObject() || list.size() >= MAX_ITEMS) {
                    continue;
                }
                JsonObject o = element.getAsJsonObject();
                CustomItemEntry entry = new CustomItemEntry();
                entry.type = text(o, "t");
                entry.style = text(o, "s");
                entry.variant = text(o, "v");
                entry.colour = text(o, "c");
                entry.finish = text(o, "f");
                entry.wear = text(o, "w");
                entry.seed = o.has("p") && o.get("p").isJsonPrimitive() ? o.get("p").getAsInt() : 0;
                entry.size = o.has("z") && o.get("z").isJsonPrimitive() ? o.get("z").getAsInt() : CustomItems.DEFAULT_SIZE;
                entry.equip = false;
                if (CustomItems.sanitise(entry)) {
                    list.add(new Shared(text(o, "u"), text(o, "i"), text(o, "n"), entry));
                }
            }
            if (!list.isEmpty()) {
                out.put(id, list);
            }
        }
        return out;
    }

    private static String text(JsonObject o, String key) {
        JsonElement value = o.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static UUID uuid(String flat) {
        if (flat.length() != 32) {
            return null;
        }
        try {
            return UUID.fromString(flat.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static CustomItemEntry entryFor(ItemOwner owner, ItemStack stack) {
        QZAConfig cfg = ConfigManager.get();
        if (owner == null || stack == null || stack.isEmpty() || !cfg.sharedItemsEnabled || !cfg.sharedItemsShow) {
            return null;
        }
        LivingEntity living = owner.asLivingEntity();
        if (!(living instanceof Player player) || player == Minecraft.getInstance().player) {
            return null;
        }
        List<Shared> list = others.get(player.getUUID());
        if (list == null || !SkyBlockArea.onSkyBlock()) {
            return null;
        }
        String uuid = ItemProtection.uuid(stack);
        if (!uuid.isEmpty()) {
            for (Shared shared : list) {
                if (uuid.equalsIgnoreCase(shared.uuid())) {
                    return shared.look();
                }
            }
        }
        String id = ItemProtection.skyblockId(stack);
        if (!id.isEmpty()) {
            for (Shared shared : list) {
                if (id.equals(shared.itemId())) {
                    return shared.look();
                }
            }
        }
        String name = IgnUtil.stripCodes(stack.getHoverName().getString());
        for (Shared shared : list) {
            if (!shared.name().isEmpty() && name.equals(shared.name())) {
                return shared.look();
            }
        }
        return null;
    }

    public static int sharedPlayers() {
        return others.size();
    }
}
