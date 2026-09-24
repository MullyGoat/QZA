package com.qza.terminal;

import com.mojang.blaze3d.platform.NativeImage;
import com.qza.QZA;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class TerminalImages {
    public static final String NONE = "";

    private static final String README = """
            Drop a .png in this folder to use it as a terminal background.

            Pick one per template in /qza under F7 / M7, Custom Terminal GUI,
            Edit Terminal GUI, Edit Custom Template, Background.

            The image is stretched to fill the terminal panel, so one that is
            roughly as wide as it is tall will suit most terminals. A partly
            transparent png lets the panel colour show through behind it.

            Use Rename Images in the same screen to give them tidier names.
            """;

    private static final Map<String, Identifier> TEXTURES = new LinkedHashMap<>();
    private static final List<String> MISSING = new ArrayList<>();

    private static List<String> cached = new ArrayList<>();

    private TerminalImages() {
    }

    public static Path imageDir() {
        return ConfigManager.qzaDir().resolve("terminals");
    }

    public static void ensureDir() {
        Path dir = imageDir();
        try {
            Files.createDirectories(dir);
            Path readme = dir.resolve("README.txt");
            String existing = Files.isRegularFile(readme)
                    ? Files.readString(readme, StandardCharsets.UTF_8) : null;
            if (!README.equals(existing)) {
                Files.writeString(readme, README, StandardCharsets.UTF_8);
            }
        } catch (Throwable e) {
            QZA.LOGGER.error("Could not create the terminal image folder", e);
        }
    }

    public static List<String> reload() {
        ensureDir();
        List<String> found = new ArrayList<>();
        try (Stream<Path> stream = Files.list(imageDir())) {
            stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)))
                    .forEach(found::add);
        } catch (Throwable e) {
            QZA.LOGGER.error("Could not list the terminal image folder", e);
        }

        cached = found;
        MISSING.clear();
        return found;
    }

    public static List<String> files() {
        return cached.isEmpty() ? reload() : List.copyOf(cached);
    }

    public static List<String> choices() {
        List<String> out = new ArrayList<>();
        out.add(NONE);
        out.addAll(files());
        return out;
    }

    public static void openFolder() {
        ensureDir();
        Path dir = imageDir();
        Minecraft.getInstance().execute(() -> {
            try {
                Util.getPlatform().openUri(dir.toUri());
            } catch (Throwable first) {
                try {
                    java.awt.Desktop.getDesktop().open(dir.toFile());
                } catch (Throwable second) {
                    QZA.LOGGER.error("Could not open {}", dir, second);
                }
            }
        });
    }

    public static Identifier texture(String fileName) {
        if (fileName == null || fileName.isBlank() || MISSING.contains(fileName)) {
            return null;
        }
        Identifier known = TEXTURES.get(fileName);
        if (known != null) {
            return known;
        }

        try {
            Path path = imageDir().resolve(fileName);
            if (!Files.isRegularFile(path)) {
                MISSING.add(fileName);
                return null;
            }
            return upload(fileName, path);
        } catch (Throwable e) {
            QZA.LOGGER.error("Could not read the terminal background {}", fileName, e);
            MISSING.add(fileName);
            return null;
        }
    }

    private static Identifier upload(String fileName, Path path) {
        try (InputStream stream = Files.newInputStream(path)) {
            NativeImage image = NativeImage.read(stream);
            Identifier id = Identifier.fromNamespaceAndPath(QZA.MOD_ID,
                    "terminal/" + safe(fileName));
            Minecraft.getInstance().getTextureManager().register(id,
                    new DynamicTexture(() -> fileName, image));
            TEXTURES.put(fileName, id);
            return id;
        } catch (Throwable e) {
            QZA.LOGGER.error("Could not read the terminal background {}", fileName, e);
            MISSING.add(fileName);
            return null;
        }
    }

    public static void forget() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.getTextureManager() != null) {
                for (Identifier id : TEXTURES.values()) {
                    client.getTextureManager().release(id);
                }
            }
        } catch (Throwable e) {
            QZA.LOGGER.warn("Could not release the terminal backgrounds", e);
        }
        TEXTURES.clear();
        MISSING.clear();
    }

    private static String safe(String fileName) {
        StringBuilder out = new StringBuilder(fileName.length());
        for (int i = 0; i < fileName.length(); i++) {
            char c = Character.toLowerCase(fileName.charAt(i));
            out.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' || c == '/' ? c : '_');
        }
        return out.toString();
    }

    private static Map<String, String> aliases() {
        QZAConfig cfg = ConfigManager.get();
        if (cfg.terminalImageNames == null) {
            cfg.terminalImageNames = new LinkedHashMap<>();
        }
        return cfg.terminalImageNames;
    }

    public static String display(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "None";
        }
        String alias = aliases().get(fileName);
        return alias == null || alias.isBlank() ? stripExtension(fileName) : alias;
    }

    public static String aliasOf(String fileName) {
        String alias = aliases().get(fileName);
        return alias == null ? "" : alias;
    }

    public static void rename(String fileName, String alias) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        String trimmed = alias == null ? "" : alias.trim();
        if (trimmed.isEmpty() || trimmed.equals(stripExtension(fileName))) {
            aliases().remove(fileName);
        } else {
            aliases().put(fileName, trimmed);
        }
    }

    public static String stripExtension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }
}
