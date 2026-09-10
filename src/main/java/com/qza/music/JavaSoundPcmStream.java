package com.qza.music;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

final class JavaSoundPcmStream implements PcmStream {
    private final AudioInputStream stream;
    private final int sampleRate;
    private final int channels;

    JavaSoundPcmStream(Path path) throws IOException {
        InputStream raw = new BufferedInputStream(Files.newInputStream(path), 1 << 16);
        AudioInputStream source;
        try {
            source = AudioSystem.getAudioInputStream(raw);
        } catch (UnsupportedAudioFileException e) {
            raw.close();
            throw new IOException("Not a readable audio file: " + path.getFileName(), e);
        }

        AudioFormat in = source.getFormat();
        int ch = in.getChannels() > 0 ? in.getChannels() : 2;
        float rate = in.getSampleRate() > 0 ? in.getSampleRate() : 44100f;

        AudioFormat target = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                rate,
                16,
                ch,
                ch * 2,
                rate,
                false );

        this.stream = AudioSystem.getAudioInputStream(target, source);
        this.sampleRate = (int) rate;
        this.channels = ch;
    }

    @Override
    public int sampleRate() {
        return sampleRate;
    }

    @Override
    public int channels() {
        return channels;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int frameBytes = channels * 2;
        int aligned = (length / frameBytes) * frameBytes;
        if (aligned == 0) {
            return 0;
        }
        return stream.read(buffer, offset, aligned);
    }

    @Override
    public void close() {
        try {
            stream.close();
        } catch (IOException ignored) {
        }
    }
}
