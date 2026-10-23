package com.qza.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class PackSource implements Closeable {
    private static final String META = "pack.mcmeta";

    private final ZipFile zip;
    private final Path folder;
    private final String root;

    private PackSource(ZipFile zip, Path folder, String root) {
        this.zip = zip;
        this.folder = folder;
        this.root = root;
    }

    static boolean isPack(Path path) {
        if (Files.isDirectory(path)) {
            return Files.isRegularFile(path.resolve(META));
        }
        return Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".zip");
    }

    static PackSource open(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return new PackSource(null, path, "");
        }
        ZipFile zip = new ZipFile(path.toFile());
        String root = null;
        for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
            String name = entries.nextElement().getName();
            if (name.equals(META) || name.endsWith("/" + META)) {
                String prefix = name.substring(0, name.length() - META.length());
                if (root == null || prefix.length() < root.length()) {
                    root = prefix;
                }
            }
        }
        if (root == null) {
            zip.close();
            throw new IOException(path.getFileName() + " has no pack.mcmeta");
        }
        return new PackSource(zip, null, root);
    }

    List<String> files() throws IOException {
        List<String> files = new ArrayList<>();
        if (zip != null) {
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!entry.isDirectory() && name.startsWith(root)) {
                    files.add(name.substring(root.length()));
                }
            }
            return files;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            walk.filter(Files::isRegularFile)
                    .forEach(p -> files.add(folder.relativize(p).toString().replace('\\', '/')));
        }
        return files;
    }

    String description() {
        try {
            byte[] meta = read(META);
            if (meta == null) {
                return "";
            }
            JsonElement description = JsonParser.parseString(new String(meta, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("pack").get("description");
            return text(description);
        } catch (Exception e) {
            return "";
        }
    }

    private static String text(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        StringBuilder out = new StringBuilder();
        if (element.isJsonArray()) {
            for (JsonElement part : element.getAsJsonArray()) {
                out.append(text(part));
            }
            return out.toString();
        }
        JsonObject object = element.getAsJsonObject();
        out.append(text(object.get("text")));
        if (object.has("extra")) {
            out.append(text(object.get("extra")));
        }
        return out.toString();
    }

    byte[] read(String path) throws IOException {
        if (zip != null) {
            ZipEntry entry = zip.getEntry(root + path);
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return in.readAllBytes();
            }
        }
        Path file = folder.resolve(path);
        return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
    }

    @Override
    public void close() throws IOException {
        if (zip != null) {
            zip.close();
        }
    }
}
