package com.qza.music;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public interface PcmStream extends AutoCloseable {
    int sampleRate();

    int channels();

    int read(byte[] buffer, int offset, int length) throws IOException;

    @Override
    void close();

    default double lengthSeconds() {
        return 0.0;
    }

    default boolean seekSeconds(double seconds) {
        return false;
    }

    static double lengthOf(Path path) {
        try (PcmStream stream = PcmStream.open(path)) {
            return stream.lengthSeconds();
        } catch (Exception e) {
            return 0.0;
        }
    }

    static PcmStream open(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".mp3")) {
            return new Mp3PcmStream(path);
        }
        if (isOgg(name)) {
            return openOgg(path);
        }
        if (name.endsWith(".wav") || name.endsWith(".wave")
                || name.endsWith(".aif") || name.endsWith(".aiff") || name.endsWith(".au")) {
            return new JavaSoundPcmStream(path);
        }
        throw new IOException("Song is an unsupported audio format");
    }

    private static PcmStream openOgg(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        String head = headText(bytes);

        if (head.contains("OpusHead")) {
            return new OpusPcmStream(bytes);
        }
        if (head.contains("FLAC")) {
            throw new IOException("Song is Ogg FLAC, which QZA cannot play");
        }
        if (head.contains("Speex")) {
            throw new IOException("Song is Ogg Speex, which QZA cannot play");
        }
        return new OggPcmStream(bytes);
    }

    private static String headText(byte[] bytes) {
        int scan = Math.min(bytes.length, 256);
        StringBuilder head = new StringBuilder(scan);
        for (int i = 0; i < scan; i++) {
            byte b = bytes[i];
            head.append(b >= 32 && b <= 126 ? (char) b : '.');
        }
        return head.toString();
    }

    private static boolean isOgg(String name) {
        return name.endsWith(".ogg") || name.endsWith(".oga") || name.endsWith(".opus");
    }

    static boolean isSupported(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        return name.endsWith(".mp3") || isOgg(name)
                || name.endsWith(".wav") || name.endsWith(".wave")
                || name.endsWith(".aif") || name.endsWith(".aiff") || name.endsWith(".au");
    }
}
