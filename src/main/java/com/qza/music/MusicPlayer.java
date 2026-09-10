package com.qza.music;

import com.qza.QZA;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Streams a playlist on a dedicated daemon thread, completely separate from
 * Minecraft's own sound engine (so the in-game music slider does not affect it
 * and the render thread is never blocked).
 */
public final class MusicPlayer {

    private static final int BUFFER_BYTES = 8192;

    private volatile Thread thread;
    private volatile boolean stopRequested;
    private volatile boolean fadeOutRequested;
    private volatile float volume = 0.6f;
    private volatile int fadeMillis = 1500;
    private volatile Path currentTrack;

    public boolean isPlaying() {
        Thread t = thread;
        return t != null && t.isAlive();
    }

    public Path currentTrack() {
        return currentTrack;
    }

    /** 0.0 - 1.0, applied immediately even mid-track. */
    public void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
    }

    public void setFadeMillis(int fadeMillis) {
        this.fadeMillis = Math.max(0, fadeMillis);
    }

    public synchronized void play(List<Path> tracks, boolean shuffle, boolean loop) {
        stopNow();

        if (tracks == null || tracks.isEmpty()) {
            return;
        }

        List<Path> playlist = new ArrayList<>(tracks);
        if (shuffle) {
            java.util.Collections.shuffle(playlist);
        }

        stopRequested = false;
        fadeOutRequested = false;

        Thread worker = new Thread(() -> runPlaylist(playlist, loop), "QZA-Music");
        worker.setDaemon(true);
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        thread = worker;
        worker.start();
    }

    /** Fades out over the configured fade length, then stops. */
    public void fadeOutAndStop() {
        if (!isPlaying()) {
            return;
        }
        if (fadeMillis <= 0) {
            stopNow();
        } else {
            fadeOutRequested = true;
        }
    }

    /** Cuts the audio immediately and waits briefly for the thread to unwind. */
    public synchronized void stopNow() {
        stopRequested = true;
        fadeOutRequested = false;
        Thread worker = thread;
        thread = null;
        if (worker != null && worker.isAlive()) {
            try {
                worker.join(600);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        currentTrack = null;
    }

    // ------------------------------------------------------------------ worker

    private void runPlaylist(List<Path> playlist, boolean loop) {
        try {
            int index = 0;
            boolean firstTrack = true;
            while (!stopRequested) {
                if (index >= playlist.size()) {
                    if (!loop) {
                        break;
                    }
                    index = 0;
                }
                Path track = playlist.get(index++);
                currentTrack = track;
                playTrack(track, firstTrack ? fadeMillis : 0);
                firstTrack = false;
            }
        } catch (Throwable t) {
            QZA.LOGGER.error("Music thread died", t);
        } finally {
            currentTrack = null;
        }
    }

    private void playTrack(Path track, int fadeInMillis) {
        PcmStream stream = null;
        SourceDataLine line = null;
        boolean hardStop = false;

        try {
            stream = PcmStream.open(track);
            int channels = Math.max(1, stream.channels());
            int rate = stream.sampleRate() > 0 ? stream.sampleRate() : 44100;
            int frameBytes = channels * 2;

            AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
            line = AudioSystem.getSourceDataLine(format);
            // ~250 ms of buffering: enough to survive a GC pause, short enough
            // that a hard stop is not audibly late.
            int lineBuffer = align((rate * frameBytes) / 4, frameBytes);
            line.open(format, lineBuffer);
            line.start();

            byte[] buffer = new byte[align(BUFFER_BYTES, frameBytes)];
            long framesPlayed = 0;
            long fadeInFrames = (long) fadeInMillis * rate / 1000L;
            long fadeOutFrames = Math.max(1L, (long) fadeMillis * rate / 1000L);
            long fadeOutStartFrame = -1;

            while (true) {
                if (stopRequested) {
                    hardStop = true;
                    break;
                }
                if (fadeOutRequested && fadeOutStartFrame < 0) {
                    fadeOutStartFrame = framesPlayed;
                }

                int read = stream.read(buffer, 0, buffer.length);
                if (read <= 0) {
                    break;
                }

                applyGain(buffer, read, frameBytes, channels,
                        framesPlayed, fadeInFrames, fadeOutStartFrame, fadeOutFrames);
                line.write(buffer, 0, read);
                framesPlayed += read / frameBytes;

                if (fadeOutStartFrame >= 0 && framesPlayed - fadeOutStartFrame >= fadeOutFrames) {
                    // Fade finished -- end the whole playlist, not just this track.
                    stopRequested = true;
                    hardStop = true;
                    break;
                }
            }
        } catch (Exception e) {
            QZA.LOGGER.error("Could not play {}", track.getFileName(), e);
        } finally {
            if (line != null) {
                try {
                    if (hardStop) {
                        line.flush();
                    } else {
                        line.drain();
                    }
                } catch (Exception ignored) {
                }
                try {
                    line.stop();
                    line.close();
                } catch (Exception ignored) {
                }
            }
            if (stream != null) {
                stream.close();
            }
        }
    }

    /**
     * Scales every sample by the master volume and the fade envelope, in place.
     */
    private void applyGain(byte[] buffer, int bytes, int frameBytes, int channels,
                           long framesPlayed, long fadeInFrames,
                           long fadeOutStartFrame, long fadeOutFrames) {
        float master = this.volume;
        int frames = bytes / frameBytes;

        for (int frame = 0; frame < frames; frame++) {
            long globalFrame = framesPlayed + frame;
            float gain = master;

            if (fadeInFrames > 0 && globalFrame < fadeInFrames) {
                gain *= (float) globalFrame / (float) fadeInFrames;
            }
            if (fadeOutStartFrame >= 0) {
                float t = (float) (globalFrame - fadeOutStartFrame) / (float) fadeOutFrames;
                gain *= Math.max(0f, 1f - t);
            }
            if (gain >= 0.999f) {
                continue;
            }

            int base = frame * frameBytes;
            for (int c = 0; c < channels; c++) {
                int i = base + (c * 2);
                int sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
                int scaled = Math.round(sample * gain);
                if (scaled > Short.MAX_VALUE) {
                    scaled = Short.MAX_VALUE;
                } else if (scaled < Short.MIN_VALUE) {
                    scaled = Short.MIN_VALUE;
                }
                buffer[i] = (byte) (scaled & 0xFF);
                buffer[i + 1] = (byte) ((scaled >> 8) & 0xFF);
            }
        }
    }

    private static int align(int value, int frameBytes) {
        int aligned = (value / frameBytes) * frameBytes;
        return Math.max(frameBytes, aligned);
    }
}
