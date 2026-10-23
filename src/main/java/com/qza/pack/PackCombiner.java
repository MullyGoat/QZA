package com.qza.pack;

import com.qza.QZA;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.util.ChatUtil;
import com.qza.util.IgnUtil;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class PackCombiner {
    public static final String FOLDER = "QZA Combined Pack";
    public static final String NONE = "none";

    private static final String PACK_ID = "file/" + FOLDER;
    private static final String MARKER = "qza-generated.txt";
    private static final String MC = "assets/minecraft/";

    public enum Part {
        BLOCKS("Blocks", "blocks", List.of(MC + "textures/block/", MC + "models/block/", MC + "blockstates/",
                MC + "optifine/ctm/", MC + "optifine/colormap/", MC + "textures/colormap/", MC + "textures/entity/chest/",
                MC + "textures/entity/bed/", MC + "textures/entity/signs/", MC + "textures/entity/banner/",
                MC + "textures/entity/shulker/", MC + "textures/entity/decorated_pot/", MC + "textures/entity/bell/"),
                List.of()),
        ITEMS("Items", "items", List.of(MC + "textures/item/", MC + "items/", MC + "models/item/", MC + "equipment/",
                MC + "textures/entity/equipment/", MC + "textures/models/armor/", MC + "textures/trims/",
                MC + "textures/entity/shield", MC + "textures/misc/enchanted_glint", MC + "optifine/cit/"), List.of()),
        GUI("GUI & HUD", "the GUI and HUD", List.of(MC + "textures/gui/", MC + "textures/font/", MC + "font/",
                MC + "textures/mob_effect/", MC + "textures/map/"), List.of()),
        SKY("Sky & Effects", "the sky and effects", List.of(MC + "textures/environment/", MC + "textures/particle/",
                MC + "particles/", MC + "particle/", MC + "textures/effect/", MC + "optifine/sky/",
                MC + "optifine/lightmap/", "assets/fabricskyboxes/", MC + "textures/misc/"),
                List.of(MC + "textures/misc/enchanted_glint"));

        private static final List<Part> COPY_ORDER = List.of(SKY, GUI, BLOCKS, ITEMS);

        public final String title;
        public final String what;
        final List<String> takes;
        final List<String> skips;

        Part(String title, String what, List<String> takes, List<String> skips) {
            this.title = title;
            this.what = what;
            this.takes = takes;
            this.skips = skips;
        }

        boolean takes(String path) {
            for (String skip : skips) {
                if (path.startsWith(skip)) {
                    return false;
                }
            }
            for (String take : takes) {
                if (path.startsWith(take)) {
                    return true;
                }
            }
            return false;
        }
    }

    public record Choice(String file, Component title, Component description) {
    }

    private record Plan(java.util.Map<Part, String> sources, String theme, boolean diamonds, boolean hearts,
                        boolean xpBar, boolean crosshair, boolean glint) {
    }

    private static volatile boolean building;

    private PackCombiner() {
    }

    public static boolean building() {
        return building;
    }

    public static Path packsDir() {
        return Minecraft.getInstance().getResourcePackDirectory();
    }

    public static List<Choice> choices() {
        PackRepository repo = Minecraft.getInstance().getResourcePackRepository();
        repo.reload();
        java.util.Map<String, Pack> known = new java.util.HashMap<>();
        for (Pack pack : repo.getAvailablePacks()) {
            if (pack.getId().startsWith("file/")) {
                known.put(pack.getId().substring("file/".length()), pack);
            }
        }
        Path dir = packsDir();
        List<Choice> choices = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return choices;
        }
        try (Stream<Path> list = Files.list(dir)) {
            for (Path path : list.filter(PackSource::isPack).toList()) {
                String file = path.getFileName().toString();
                if (file.equals(FOLDER) || file.startsWith(FOLDER + ".")) {
                    continue;
                }
                Pack pack = known.get(file);
                if (pack != null) {
                    choices.add(new Choice(file, pack.getTitle(), pack.getDescription()));
                } else {
                    try (PackSource source = PackSource.open(path)) {
                        choices.add(new Choice(file, Component.literal(label(file)),
                                Component.literal(source.description())));
                    } catch (IOException e) {
                        QZA.LOGGER.debug("Skipping {} in the combiner: {}", file, e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            QZA.LOGGER.warn("Could not list resource packs", e);
        }
        choices.sort(Comparator.comparing(c -> label(c.file()).toLowerCase(Locale.ROOT)));
        return choices;
    }

    public static String label(String file) {
        if (file == null || file.isEmpty() || NONE.equals(file)) {
            return "None";
        }
        String name = IgnUtil.stripCodes(file);
        return name.toLowerCase(Locale.ROOT).endsWith(".zip") ? name.substring(0, name.length() - 4) : name;
    }

    public static String chosen(Part part) {
        QZAConfig cfg = ConfigManager.get();
        String file = switch (part) {
            case BLOCKS -> cfg.packCombinerBlocks;
            case ITEMS -> cfg.packCombinerItems;
            case GUI -> cfg.packCombinerGui;
            case SKY -> cfg.packCombinerSky;
        };
        return file == null || file.isEmpty() ? NONE : file;
    }

    public static void choose(Part part, String file) {
        QZAConfig cfg = ConfigManager.get();
        switch (part) {
            case BLOCKS -> cfg.packCombinerBlocks = file;
            case ITEMS -> cfg.packCombinerItems = file;
            case GUI -> cfg.packCombinerGui = file;
            case SKY -> cfg.packCombinerSky = file;
        }
        ConfigManager.save();
    }

    public static String summary(java.util.Map<Part, String> sources) {
        List<String> parts = new ArrayList<>();
        for (Part part : Part.values()) {
            String file = sources.get(part);
            if (file != null && !NONE.equals(file)) {
                parts.add(part.title + ": " + label(file));
            }
        }
        return parts.isEmpty() ? "nothing picked yet" : String.join(", ", parts);
    }

    public static java.util.Map<Part, String> chosenAll() {
        java.util.Map<Part, String> sources = new java.util.EnumMap<>(Part.class);
        for (Part part : Part.values()) {
            sources.put(part, chosen(part));
        }
        return sources;
    }

    public static void apply() {
        if (building) {
            return;
        }
        QZAConfig cfg = ConfigManager.get();
        Plan plan = new Plan(chosenAll(), cfg.packCombinerTheme, cfg.packCombinerDiamonds, cfg.packCombinerHearts,
                cfg.packCombinerXpBar, cfg.packCombinerCrosshair, cfg.packCombinerGlint);
        PackFormat format = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
        Path dir = packsDir();
        building = true;
        Thread thread = new Thread(() -> {
            try {
                int files = build(plan, dir, format);
                Minecraft.getInstance().execute(() -> {
                    building = false;
                    enable(dir, files, plan);
                });
            } catch (Throwable t) {
                QZA.LOGGER.error("Texturepack Combiner build failed", t);
                Minecraft.getInstance().execute(() -> {
                    building = false;
                    ChatUtil.error("Texturepack Combiner couldn't combine the packs: " + t.getMessage());
                });
            }
        }, "QZA Texturepack Combiner");
        thread.setDaemon(true);
        thread.start();
    }

    private static int build(Plan plan, Path dir, PackFormat format) throws IOException {
        Path tmp = dir.resolve(FOLDER + ".building");
        deleteGenerated(tmp, true);
        Files.createDirectories(tmp);
        Set<String> written = new LinkedHashSet<>();
        for (Part part : Part.COPY_ORDER) {
            copy(part, plan.sources().get(part), dir, tmp, written);
        }
        if (!PackTheme.DEFAULT.equals(plan.theme())) {
            if (plan.diamonds()) {
                List<String> diamonds = new ArrayList<>(PackTheme.DIAMONDS);
                for (String path : written) {
                    if (path.startsWith(MC + "textures/item/") && path.endsWith(".png")
                            && path.substring(path.lastIndexOf('/') + 1).contains("diamond")) {
                        String rel = path.substring(MC.length());
                        if (!diamonds.contains(rel)) {
                            diamonds.add(rel);
                        }
                    }
                }
                theme(tmp, diamonds, PackTheme.Rule.DIAMOND, plan.theme(), written);
            }
            if (plan.hearts()) {
                theme(tmp, PackTheme.HEARTS, PackTheme.Rule.COLOURED, plan.theme(), written);
            }
            if (plan.xpBar()) {
                theme(tmp, PackTheme.XP_BAR, PackTheme.Rule.COLOURED, plan.theme(), written);
            }
            if (plan.crosshair()) {
                theme(tmp, PackTheme.CROSSHAIR, PackTheme.Rule.TINT, plan.theme(), written);
            }
            if (plan.glint()) {
                theme(tmp, PackTheme.GLINT, PackTheme.Rule.TINT, plan.theme(), written);
            }
        }
        String description = summary(plan.sources());
        String mcmeta = "{\n  \"pack\": {\n    \"description\": \"" + description.replace("\\", "\\\\").replace("\"", "\\\"")
                + "\",\n"
                + "    \"pack_format\": " + format.major() + ",\n"
                + "    \"min_format\": [" + format.major() + ", " + format.minor() + "],\n"
                + "    \"max_format\": [" + format.major() + ", " + format.minor() + "]\n  }\n}\n";
        write(tmp, "pack.mcmeta", mcmeta.getBytes(StandardCharsets.UTF_8));
        try (InputStream icon = PackCombiner.class.getResourceAsStream("/assets/qza/icon.png")) {
            if (icon != null) {
                write(tmp, "pack.png", icon.readAllBytes());
            }
        }
        write(tmp, MARKER, ("Combined by QZA's Texturepack Combiner from packs in this folder. QZA deletes and "
                + "rebuilds it whenever you press Combine, so edits here are lost.\n").getBytes(StandardCharsets.UTF_8));
        return written.size();
    }

    private static void copy(Part part, String choice, Path dir, Path tmp, Set<String> written) throws IOException {
        if (choice == null || NONE.equals(choice)) {
            return;
        }
        Path source = dir.resolve(choice);
        if (!PackSource.isPack(source)) {
            throw new IOException(label(choice) + " isn't in your resourcepacks folder");
        }
        try (PackSource pack = PackSource.open(source)) {
            for (String path : pack.files()) {
                if (part.takes(path) && safe(path)) {
                    byte[] bytes = pack.read(path);
                    if (bytes != null) {
                        write(tmp, path, bytes);
                        written.add(path);
                    }
                }
            }
        }
    }

    private static void theme(Path tmp, List<String> paths, PackTheme.Rule rule, String theme, Set<String> written)
            throws IOException {
        for (String rel : paths) {
            String path = MC + rel;
            byte[] bytes = Files.isRegularFile(tmp.resolve(path)) ? Files.readAllBytes(tmp.resolve(path)) : vanilla(rel);
            if (bytes == null) {
                continue;
            }
            write(tmp, path, PackTheme.recolour(bytes, rule, theme));
            written.add(path);
        }
    }

    private static byte[] vanilla(String rel) throws IOException {
        IoSupplier<InputStream> supplier = Mc.vanillaResource(Identifier.withDefaultNamespace(rel));
        if (supplier == null) {
            return null;
        }
        try (InputStream in = supplier.get()) {
            return in.readAllBytes();
        }
    }

    private static boolean safe(String path) {
        if (path.isEmpty() || path.startsWith("/") || path.contains("\\") || path.contains(":")) {
            return false;
        }
        for (String part : path.split("/")) {
            if (part.equals("..") || part.equals(".")) {
                return false;
            }
        }
        return true;
    }

    private static void write(Path root, String path, byte[] bytes) throws IOException {
        Path file = root.resolve(path).normalize();
        if (!file.startsWith(root)) {
            throw new IOException("Bad path " + path);
        }
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    private static void deleteGenerated(Path folder, boolean force) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        if (!force && !Files.isRegularFile(folder.resolve(MARKER))) {
            throw new IOException("A folder called \"" + folder.getFileName() + "\" that QZA didn't make is already in "
                    + "your resourcepacks folder");
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static void enable(Path dir, int files, Plan plan) {
        Minecraft client = Minecraft.getInstance();
        Path out = dir.resolve(FOLDER);
        try {
            deleteGenerated(out, false);
            Files.move(dir.resolve(FOLDER + ".building"), out);
        } catch (IOException e) {
            QZA.LOGGER.error("Texturepack Combiner swap failed", e);
            ChatUtil.error("Texturepack Combiner couldn't save the combined pack: " + e.getMessage());
            return;
        }
        PackRepository repo = client.getResourcePackRepository();
        repo.reload();
        if (!repo.getAvailableIds().contains(PACK_ID)) {
            ChatUtil.error("Minecraft didn't pick up the combined pack.");
            return;
        }
        List<String> selected = new ArrayList<>(repo.getSelectedIds());
        selected.remove(PACK_ID);
        selected.add(PACK_ID);
        List<String> before = List.copyOf(client.options.resourcePacks);
        repo.setSelected(selected);
        client.options.updateResourcePacks(repo);
        if (before.equals(client.options.resourcePacks)) {
            client.reloadResourcePacks();
        }
        String theme = PackTheme.DEFAULT.equals(plan.theme()) ? "" : ", " + PackTheme.label(plan.theme()) + " theme";
        ChatUtil.success("Combined " + summary(plan.sources()) + " (" + files + " files" + theme + ").");
    }

    public static List<PackResources> onTop(List<PackResources> packs) {
        if (!ConfigManager.get().packCombinerEnabled || packs.isEmpty()
                || PACK_ID.equals(packs.get(packs.size() - 1).packId())) {
            return null;
        }
        List<PackResources> ordered = new ArrayList<>(packs.size());
        PackResources combined = null;
        for (PackResources pack : packs) {
            if (PACK_ID.equals(pack.packId())) {
                combined = pack;
            } else {
                ordered.add(pack);
            }
        }
        if (combined == null) {
            return null;
        }
        ordered.add(combined);
        return ordered;
    }

    public static void disable() {
        Minecraft client = Minecraft.getInstance();
        PackRepository repo = client.getResourcePackRepository();
        repo.reload();
        List<String> selected = new ArrayList<>(repo.getSelectedIds());
        if (selected.remove(PACK_ID)) {
            repo.setSelected(selected);
            client.options.updateResourcePacks(repo);
        }
    }

    public static void openFolder() {
        Path dir = packsDir();
        try {
            Mc.openUri(dir.toUri());
        } catch (Throwable first) {
            try {
                java.awt.Desktop.getDesktop().open(dir.toFile());
            } catch (Throwable second) {
                QZA.LOGGER.error("Could not open {}", dir, second);
            }
        }
    }
}
