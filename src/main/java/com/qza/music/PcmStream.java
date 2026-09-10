package com.qza.music;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public interface PcmStream extends AutoCloseable {
    int sampleRate();

    int channels();

    int read(byte[] buffer, int offset, int length) throws IOException;

    @Override
    void close();

    static PcmStream open(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".ogg")) {
            return new OggPcmStream(path);
        }
        if (name.endsWith(".wav") || name.endsWith(".wave")
                || name.endsWith(".aif") || name.endsWith(".aiff") || name.endsWith(".au")) {
            return new JavaSoundPcmStream(path);
        }
        throw new IOException("Unsupported audio format: " + path.getFileName());
    }

    static boolean isSupported(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        return name.endsWith(".ogg") || name.endsWith(".wav") || name.endsWith(".wave")
                || name.endsWith(".aif") || name.endsWith(".aiff") || name.endsWith(".au");
    }
}
