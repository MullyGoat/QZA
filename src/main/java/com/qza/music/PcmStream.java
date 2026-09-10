package com.qza.music;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * A decoded track, exposed as interleaved signed 16-bit little-endian PCM.
 */
public interface PcmStream extends AutoCloseable {

    int sampleRate();

    int channels();

    /**
     * @return number of bytes written into {@code buffer}, or -1 at end of stream.
     */
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

    /** Extensions {@link #open} can handle. */
    static boolean isSupported(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        return name.endsWith(".ogg") || name.endsWith(".wav") || name.endsWith(".wave")
                || name.endsWith(".aif") || name.endsWith(".aiff") || name.endsWith(".au");
    }
}
