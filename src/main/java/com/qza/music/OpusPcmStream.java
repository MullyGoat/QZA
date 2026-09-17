package com.qza.music;

import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Ogg Opus playback. Concentus decodes Opus packets but knows nothing about the
 * Ogg container, so this class demuxes the pages itself (RFC 3533 / RFC 7845).
 */
final class OpusPcmStream implements PcmStream {
    private static final int RATE = 48000;
    private static final int MAX_FRAME = 5760;

    private final int channels;
    private final double lengthSeconds;
    private final List<byte[]> packets;
    private final short[] scratch;

    private OpusDecoder decoder;
    private int packetIndex;
    private int preSkip;

    private int pendingPos;
    private int pendingLen;

    OpusPcmStream(byte[] bytes) throws IOException {
        Container container = demux(bytes);
        this.packets = container.packets();

        if (packets.isEmpty()) {
            throw new IOException("Song has no Ogg pages");
        }

        byte[] head = packets.get(0);
        if (!startsWith(head, "OpusHead") || head.length < 19) {
            throw new IOException("Song is missing its Opus header");
        }

        int declared = head[9] & 0xFF;
        int family = head[18] & 0xFF;
        if (declared < 1 || declared > 2 || family > 1) {
            throw new IOException("Song is " + declared
                    + "-channel Opus - only mono and stereo can be played");
        }

        this.channels = declared;
        this.preSkip = (head[10] & 0xFF) | ((head[11] & 0xFF) << 8);
        this.scratch = new short[MAX_FRAME * channels];

        long granule = container.lastGranule() - preSkip;
        this.lengthSeconds = granule > 0 ? granule / (double) RATE : 0.0;

        // Skip the identification header and the comment header that follows it.
        this.packetIndex = packets.size() > 1 && startsWith(packets.get(1), "OpusTags") ? 2 : 1;

        try {
            this.decoder = new OpusDecoder(RATE, channels);
        } catch (OpusException e) {
            throw new IOException("Song could not be decoded (" + e.getMessage() + ")", e);
        }
    }

    private record Container(List<byte[]> packets, long lastGranule) {
    }

    private static Container demux(byte[] data) {
        List<byte[]> found = new ArrayList<>();
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        long lastGranule = 0;
        int pos = 0;

        while (true) {
            int page = findPage(data, pos);
            if (page < 0 || page + 27 > data.length) {
                break;
            }

            int segments = data[page + 26] & 0xFF;
            int headerLen = 27 + segments;
            if (page + headerLen > data.length) {
                break;
            }

            int bodyLen = 0;
            for (int i = 0; i < segments; i++) {
                bodyLen += data[page + 27 + i] & 0xFF;
            }
            if (page + headerLen + bodyLen > data.length) {
                break;
            }

            int offset = page + headerLen;
            for (int i = 0; i < segments; i++) {
                int segLen = data[page + 27 + i] & 0xFF;
                packet.write(data, offset, segLen);
                offset += segLen;
                if (segLen < 255) {
                    found.add(packet.toByteArray());
                    packet.reset();
                }
            }

            long granule = readLongLE(data, page + 6);
            if (granule > lastGranule) {
                lastGranule = granule;
            }

            pos = page + headerLen + bodyLen;
        }

        return new Container(found, lastGranule);
    }

    private static int findPage(byte[] data, int from) {
        for (int i = Math.max(0, from); i + 4 <= data.length; i++) {
            if (data[i] == 'O' && data[i + 1] == 'g'
                    && data[i + 2] == 'g' && data[i + 3] == 'S') {
                return i;
            }
        }
        return -1;
    }

    private static long readLongLE(byte[] data, int offset) {
        long value = 0;
        for (int i = 7; i >= 0; i--) {
            value = (value << 8) | (data[offset + i] & 0xFFL);
        }
        return value;
    }

    private static boolean startsWith(byte[] data, String magic) {
        if (data.length < magic.length()) {
            return false;
        }
        for (int i = 0; i < magic.length(); i++) {
            if (data[i] != magic.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private boolean fill() {
        while (decoder != null && packetIndex < packets.size()) {
            byte[] packet = packets.get(packetIndex++);
            if (packet.length == 0) {
                continue;
            }

            int samples;
            try {
                samples = decoder.decode(packet, 0, packet.length, scratch, 0, MAX_FRAME, false);
            } catch (OpusException e) {
                continue;
            }
            if (samples <= 0) {
                continue;
            }

            int first = 0;
            if (preSkip > 0) {
                int drop = Math.min(preSkip, samples);
                preSkip -= drop;
                first = drop;
                samples -= drop;
                if (samples == 0) {
                    continue;
                }
            }

            pendingPos = first * channels;
            pendingLen = (first + samples) * channels;
            return true;
        }
        return false;
    }

    @Override
    public double lengthSeconds() {
        return lengthSeconds;
    }

    @Override
    public int sampleRate() {
        return RATE;
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
            while (pendingPos < pendingLen && written < aligned) {
                short sample = scratch[pendingPos++];
                buffer[offset + written++] = (byte) (sample & 0xFF);
                buffer[offset + written++] = (byte) ((sample >> 8) & 0xFF);
            }
        }
        return written == 0 ? -1 : written;
    }

    @Override
    public void close() {
        decoder = null;
        packets.clear();
        pendingPos = 0;
        pendingLen = 0;
    }
}
