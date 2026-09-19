package com.qza.music;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

final class Mp3PcmStream implements PcmStream {
    private final InputStream source;
    private final Bitstream bitstream;
    private final Decoder decoder;

    private final int sampleRate;
    private final int channels;
    private final double lengthSeconds;

    private SampleBuffer output;
    private int pendingPos;
    private int pendingLen;
    private boolean finished;

    Mp3PcmStream(Path path) throws IOException {
        long size = Files.size(path);
        InputStream raw = new BufferedInputStream(Files.newInputStream(path), 1 << 16);
        Bitstream stream = new Bitstream(raw);
        Decoder dec = new Decoder();

        Header header;
        try {
            header = stream.readFrame();
        } catch (BitstreamException e) {
            quietClose(stream, raw);
            throw new IOException("Song could not be read as mp3", e);
        }
        if (header == null) {
            quietClose(stream, raw);
            throw new IOException("Song has no mp3 audio frames");
        }

        double seconds = header.total_ms((int) Math.min(size, Integer.MAX_VALUE)) / 1000.0;

        SampleBuffer first;
        try {
            first = (SampleBuffer) dec.decodeFrame(header, stream);
            stream.closeFrame();
        } catch (DecoderException | RuntimeException e) {
            quietClose(stream, raw);
            throw new IOException("Song could not be decoded as mp3", e);
        }

        this.source = raw;
        this.bitstream = stream;
        this.decoder = dec;
        this.sampleRate = first.getSampleFrequency() > 0
                ? first.getSampleFrequency() : header.frequency();
        this.channels = first.getChannelCount() > 0
                ? first.getChannelCount()
                : (header.mode() == Header.SINGLE_CHANNEL ? 1 : 2);
        this.lengthSeconds = Math.max(0.0, seconds);

        this.output = first;
        this.pendingPos = 0;
        this.pendingLen = first.getBufferLength();
    }

    private static void quietClose(Bitstream stream, InputStream raw) {
        try {
            stream.close();
        } catch (BitstreamException ignored) {
        }
        try {
            raw.close();
        } catch (IOException ignored) {
        }
    }

    private boolean fill() {
        while (!finished) {
            Header header;
            try {
                header = bitstream.readFrame();
            } catch (BitstreamException e) {
                finished = true;
                return false;
            }
            if (header == null) {
                finished = true;
                return false;
            }

            try {
                output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                pendingPos = 0;
                pendingLen = output.getBufferLength();
            } catch (DecoderException | RuntimeException e) {
                pendingPos = 0;
                pendingLen = 0;
            } finally {
                bitstream.closeFrame();
            }

            if (pendingLen > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public double lengthSeconds() {
        return lengthSeconds;
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
    public int read(byte[] buffer, int offset, int length) {
        int frameBytes = channels * 2;
        int aligned = (length / frameBytes) * frameBytes;
        if (aligned == 0) {
            return 0;
        }

        int written = 0;
        while (written < aligned) {
            if (pendingPos >= pendingLen && !fill()) {
                break;
            }
            short[] samples = output.getBuffer();
            while (pendingPos < pendingLen && written < aligned) {
                short sample = samples[pendingPos++];
                buffer[offset + written++] = (byte) (sample & 0xFF);
                buffer[offset + written++] = (byte) ((sample >> 8) & 0xFF);
            }
        }
        return written == 0 ? -1 : written;
    }

    @Override
    public void close() {
        finished = true;
        pendingPos = 0;
        pendingLen = 0;
        quietClose(bitstream, source);
    }
}
