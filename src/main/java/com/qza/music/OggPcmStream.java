package com.qza.music;

import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ogg Vorbis decoding through LWJGL's STB bindings, which ship with Minecraft.
 *
 * This is a pure decoder -- it never touches OpenAL or Minecraft's sound engine,
 * so it is safe to drive from our own playback thread.
 */
final class OggPcmStream implements PcmStream {

    /** Native memory holding the whole file; must outlive the decoder handle. */
    private ByteBuffer fileData;
    private ShortBuffer sampleBuffer;
    private long handle;

    private final int sampleRate;
    private final int channels;

    OggPcmStream(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        this.fileData = MemoryUtil.memAlloc(bytes.length);
        this.fileData.put(bytes);
        this.fileData.flip();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.IntBuffer error = stack.mallocInt(1);
            this.handle = STBVorbis.stb_vorbis_open_memory(fileData, error, null);
            if (handle == MemoryUtil.NULL) {
                MemoryUtil.memFree(fileData);
                fileData = null;
                throw new IOException("stb_vorbis could not open " + path.getFileName()
                        + " (error " + error.get(0) + ")");
            }

            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            STBVorbis.stb_vorbis_get_info(handle, info);
            this.channels = info.channels();
            this.sampleRate = info.sample_rate();
        }
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
        if (handle == MemoryUtil.NULL) {
            return -1;
        }

        int framesWanted = (length / 2) / channels;
        if (framesWanted == 0) {
            return 0;
        }

        int shortsWanted = framesWanted * channels;
        if (sampleBuffer == null || sampleBuffer.capacity() < shortsWanted) {
            if (sampleBuffer != null) {
                MemoryUtil.memFree(sampleBuffer);
            }
            sampleBuffer = MemoryUtil.memAllocShort(shortsWanted);
        }
        sampleBuffer.clear().limit(shortsWanted);

        int framesRead = STBVorbis.stb_vorbis_get_samples_short_interleaved(
                handle, channels, sampleBuffer);
        if (framesRead <= 0) {
            return -1;
        }

        int shorts = framesRead * channels;
        int out = offset;
        for (int i = 0; i < shorts; i++) {
            short sample = sampleBuffer.get(i);
            buffer[out++] = (byte) (sample & 0xFF);
            buffer[out++] = (byte) ((sample >> 8) & 0xFF);
        }
        return shorts * 2;
    }

    @Override
    public void close() {
        if (handle != MemoryUtil.NULL) {
            STBVorbis.stb_vorbis_close(handle);
            handle = MemoryUtil.NULL;
        }
        if (sampleBuffer != null) {
            MemoryUtil.memFree(sampleBuffer);
            sampleBuffer = null;
        }
        if (fileData != null) {
            MemoryUtil.memFree(fileData);
            fileData = null;
        }
    }
}
