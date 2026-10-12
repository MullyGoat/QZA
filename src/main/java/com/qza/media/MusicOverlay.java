package com.qza.media;

import com.mojang.blaze3d.platform.NativeImage;
import com.qza.QZA;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.notify.NotificationBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Locale;

public final class MusicOverlay {
    private static final long POLL_MS = 750L;
    private static final long IDLE_MS = 1000L;
    private static final long ERROR_MS = 5000L;
    private static final int[] THUMBNAIL_POLLS = {0, 2, 5};
    private static final int THUMBNAIL_RETRIES = 8;
    private static final int COVER_SOURCE_H = 96;
    private static final long FILETIME_EPOCH = 116_444_736_000_000_000L;
    private static final long TICKS_PER_MS = 10_000L;
    private static final long MAX_UPDATE_AGE_MS = 6L * 60L * 60L * 1000L;
    private static final Identifier COVER = Identifier.fromNamespaceAndPath("qza", "music_cover");

    private static final int PAD = 4;
    private static final int COVER_H = 32;
    private static final int COVER_MAX_W = 57;
    private static final int GAP = 5;
    private static final int TEXT_MIN_W = 70;
    private static final int TEXT_MAX_W = 140;
    private static final int LINE = 10;
    private static final int BAR_H = 2;

    private static final int BACKGROUND = 0xCC14181E;
    private static final int BORDER = 0x40FFFFFF;
    private static final int TITLE = 0xFFFFFFFF;
    private static final int ARTIST = 0xFFAAAAAA;
    private static final int TIME = 0xFFCCCCCC;
    private static final int PAUSED = 0xFFFFD866;
    private static final int BAR_BACK = 0x40FFFFFF;
    private static final int BAR_FILL = 0xFFFF55FF;
    private static final int NOTE = 0xFF8A8A8A;

    private static final State EXAMPLE = new State("Song Title", "Artist", true, 225_000L, 83_000L, -1L, 0, null);

    private static volatile State state;
    private static volatile boolean unavailable =
            !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    private static Thread worker;

    private static String lastKey = "";
    private static int keyPolls;
    private static Cover cover;
    private static int coverVersion;
    private static long seenPosition = Long.MIN_VALUE;
    private static long seenAt;

    private static int uploadedVersion;
    private static DynamicTexture texture;

    private record Cover(int width, int height, int[] argb, int hash) {
    }

    private record State(String title, String subtitle, boolean playing, long lengthMs, long positionMs,
                         long anchorMs, int version, Cover cover) {
        long position(long now) {
            long at = playing && anchorMs >= 0 ? positionMs + Math.max(0L, now - anchorMs) : positionMs;
            return lengthMs > 0 ? Math.min(at, lengthMs) : at;
        }
    }

    private MusicOverlay() {
    }

    public static boolean available() {
        return !unavailable;
    }

    public static void init() {
        if (unavailable || worker != null) {
            return;
        }
        worker = new Thread(MusicOverlay::run, "QZA Music Overlay");
        worker.setDaemon(true);
        worker.start();
    }

    private static boolean enabled() {
        return ConfigManager.get().musicOverlayEnabled;
    }

    private static void run() {
        boolean started = false;
        while (true) {
            try {
                if (!enabled()) {
                    state = null;
                    sleep(IDLE_MS);
                    continue;
                }
                if (!started) {
                    started = WindowsMedia.start();
                    if (!started) {
                        QZA.LOGGER.warn("QZA Music Overlay could not reach the Windows media controls");
                        unavailable = true;
                        state = null;
                        return;
                    }
                }
                poll();
                sleep(POLL_MS);
            } catch (LinkageError e) {
                QZA.LOGGER.warn("QZA Music Overlay is not supported here", e);
                unavailable = true;
                state = null;
                return;
            } catch (RuntimeException e) {
                QZA.LOGGER.warn("QZA Music Overlay failed to read the media session", e);
                state = null;
                sleep(ERROR_MS);
            }
        }
    }

    private static void poll() {
        WindowsMedia.Snapshot snapshot = WindowsMedia.read(MusicOverlay::wantThumbnail);
        if (snapshot == null || snapshot.track().status() == WindowsMedia.CLOSED
                || snapshot.track().title().isBlank()) {
            state = null;
            return;
        }
        WindowsMedia.Track track = snapshot.track();
        if (snapshot.thumbnail() != null) {
            Cover decoded = decode(snapshot.thumbnail());
            if (decoded != null && (cover == null || cover.hash() != decoded.hash())) {
                cover = decoded;
                coverVersion++;
            }
        }

        long now = System.currentTimeMillis();
        boolean playing = track.status() == WindowsMedia.PLAYING;
        long length = Math.max(0L, (track.endTicks() - track.startTicks()) / TICKS_PER_MS);
        long position = Math.max(0L, (track.positionTicks() - track.startTicks()) / TICKS_PER_MS);
        if (position != seenPosition) {
            seenPosition = position;
            seenAt = now;
        }
        long anchor = seenAt;
        if (track.updatedTicks() > FILETIME_EPOCH) {
            long updated = (track.updatedTicks() - FILETIME_EPOCH) / TICKS_PER_MS;
            long age = now - updated;
            if (age >= 0 && age <= MAX_UPDATE_AGE_MS && (length <= 0 || position + age <= length + POLL_MS)) {
                anchor = updated;
            }
        }
        String subtitle = track.artist().isBlank() ? appName(track.app()) : track.artist();
        state = new State(track.title(), subtitle, playing, length, position, anchor, coverVersion, cover);
    }

    private static boolean wantThumbnail(WindowsMedia.Track track) {
        String key = track.key();
        if (!key.equals(lastKey)) {
            lastKey = key;
            keyPolls = 0;
            cover = null;
            coverVersion++;
            seenPosition = Long.MIN_VALUE;
        } else {
            keyPolls++;
        }
        if (cover == null && keyPolls < THUMBNAIL_RETRIES) {
            return true;
        }
        return Arrays.stream(THUMBNAIL_POLLS).anyMatch(poll -> poll == keyPolls);
    }

    private static Cover decode(byte[] bytes) {
        ByteBuffer input = MemoryUtil.memAlloc(bytes.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            input.put(bytes).flip();
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(input, w, h, channels, 4);
            if (pixels == null) {
                return null;
            }
            try {
                return scale(pixels, w.get(0), h.get(0), Arrays.hashCode(bytes));
            } finally {
                STBImage.stbi_image_free(pixels);
            }
        } finally {
            MemoryUtil.memFree(input);
        }
    }

    private static Cover scale(ByteBuffer rgba, int width, int height, int hash) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        int outH = Math.min(height, COVER_SOURCE_H);
        int outW = Math.max(1, Math.round(width * (outH / (float) height)));
        int[] out = new int[outW * outH];
        for (int y = 0; y < outH; y++) {
            int y0 = y * height / outH;
            int y1 = Math.max(y0 + 1, (y + 1) * height / outH);
            for (int x = 0; x < outW; x++) {
                int x0 = x * width / outW;
                int x1 = Math.max(x0 + 1, (x + 1) * width / outW);
                long r = 0;
                long g = 0;
                long b = 0;
                long a = 0;
                int count = 0;
                for (int sy = y0; sy < y1; sy++) {
                    for (int sx = x0; sx < x1; sx++) {
                        int at = ((sy * width) + sx) * 4;
                        r += rgba.get(at) & 0xFF;
                        g += rgba.get(at + 1) & 0xFF;
                        b += rgba.get(at + 2) & 0xFF;
                        a += rgba.get(at + 3) & 0xFF;
                        count++;
                    }
                }
                out[(y * outW) + x] = (int) ((a / count) << 24 | (r / count) << 16 | (g / count) << 8 | (b / count));
            }
        }
        return new Cover(outW, outH, out, hash);
    }

    private static String appName(String app) {
        String id = app == null ? "" : app.toLowerCase(Locale.ROOT);
        if (id.contains("spotify")) {
            return "Spotify";
        }
        if (id.contains("applemusic")) {
            return "Apple Music";
        }
        if (id.contains("youtube")) {
            return "YouTube Music";
        }
        if (id.contains("firefox") || id.equals("308046b0af4a39cb")) {
            return "Firefox";
        }
        if (id.contains("msedge")) {
            return "Microsoft Edge";
        }
        if (id.contains("chrome")) {
            return "Google Chrome";
        }
        if (id.contains("opera")) {
            return "Opera";
        }
        if (id.contains("brave")) {
            return "Brave";
        }
        return "";
    }

    public static void renderHud(GuiGraphicsExtractor graphics, Font font) {
        QZAConfig cfg = ConfigManager.get();
        State current = state;
        if (!cfg.musicOverlayEnabled || unavailable || current == null) {
            return;
        }
        int[] box = box(font, current, graphics.guiWidth(), graphics.guiHeight());
        draw(graphics, font, current, box[0], box[1]);
    }

    public static int[] previewBox(Font font, int screenW, int screenH) {
        return box(font, preview(), screenW, screenH);
    }

    public static void drawPreview(GuiGraphicsExtractor graphics, Font font, int x, int y) {
        draw(graphics, font, preview(), x, y);
    }

    private static State preview() {
        State current = state;
        return current != null ? current : EXAMPLE;
    }

    private static int[] box(Font font, State current, int screenW, int screenH) {
        QZAConfig cfg = ConfigManager.get();
        float scale = NotificationBox.clampScale(cfg.musicOverlayScale);
        int[] size = size(font, current);
        int w = Math.round(size[0] * scale);
        int h = Math.round(size[1] * scale);
        int[] pos = NotificationBox.topLeft(cfg.musicOverlayX, cfg.musicOverlayY, screenW, screenH, w, h);
        return new int[]{pos[0], pos[1], w, h};
    }

    private static int coverWidth(State current) {
        Cover art = current.cover();
        if (art == null) {
            return COVER_H;
        }
        return Math.max(COVER_H * 3 / 4, Math.min(COVER_MAX_W, Math.round(COVER_H * (art.width() / (float) art.height()))));
    }

    private static int textWidth(Font font, State current) {
        int wanted = Math.max(font.width(current.title()), font.width(current.subtitle()));
        wanted = Math.max(wanted, font.width(timeLeft(current, 0L) + "  " + timeRight(current)));
        return Math.max(TEXT_MIN_W, Math.min(TEXT_MAX_W, wanted));
    }

    private static int[] size(Font font, State current) {
        int w = PAD + coverWidth(current) + GAP + textWidth(font, current) + PAD;
        int h = PAD + Math.max(COVER_H, (LINE * 3) + BAR_H) + PAD;
        return new int[]{w, h};
    }

    private static void draw(GuiGraphicsExtractor graphics, Font font, State current, int x, int y) {
        QZAConfig cfg = ConfigManager.get();
        float scale = NotificationBox.clampScale(cfg.musicOverlayScale);
        int[] size = size(font, current);
        int coverW = coverWidth(current);
        int textW = textWidth(font, current);
        long now = System.currentTimeMillis();

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);

        graphics.fill(0, 0, size[0], size[1], BACKGROUND);
        graphics.fill(0, 0, size[0], 1, BORDER);
        graphics.fill(0, size[1] - 1, size[0], size[1], BORDER);
        graphics.fill(0, 1, 1, size[1] - 1, BORDER);
        graphics.fill(size[0] - 1, 1, size[0], size[1] - 1, BORDER);

        int coverY = (size[1] - COVER_H) / 2;
        if (current.cover() != null) {
            upload(current);
            graphics.blit(RenderPipelines.GUI_TEXTURED, COVER, PAD, coverY, 0f, 0f,
                    coverW, COVER_H, coverW, COVER_H);
        } else {
            graphics.fill(PAD, coverY, PAD + coverW, coverY + COVER_H, 0x55FFFFFF);
            graphics.centeredText(font, "♪", PAD + (coverW / 2), coverY + (COVER_H / 2) - 4, NOTE);
        }

        int textX = PAD + coverW + GAP;
        int textY = (size[1] - ((LINE * 3) + BAR_H)) / 2;
        graphics.text(font, trim(font, current.title(), textW), textX, textY, TITLE);
        graphics.text(font, trim(font, current.subtitle(), textW), textX, textY + LINE, ARTIST);

        long position = current.position(now);
        String left = timeLeft(current, now);
        String right = timeRight(current);
        graphics.text(font, left, textX, textY + (LINE * 2), current.playing() ? TIME : PAUSED);
        if (!right.isEmpty()) {
            graphics.text(font, right, textX + textW - font.width(right), textY + (LINE * 2), ARTIST);
            int barY = textY + (LINE * 3) - 1;
            graphics.fill(textX, barY, textX + textW, barY + BAR_H, BAR_BACK);
            int filled = (int) Math.round(textW * Math.min(1.0, position / (double) current.lengthMs()));
            if (filled > 0) {
                graphics.fill(textX, barY, textX + filled, barY + BAR_H, BAR_FILL);
            }
        }
        graphics.pose().popMatrix();
    }

    private static String timeLeft(State current, long now) {
        if (!current.playing()) {
            return "Paused";
        }
        return current.lengthMs() > 0 ? clock(current.position(now)) : "Playing";
    }

    private static String timeRight(State current) {
        return current.lengthMs() > 0 ? clock(current.lengthMs()) : "";
    }

    private static void upload(State current) {
        if (current.version() == uploadedVersion && texture != null) {
            return;
        }
        Cover art = current.cover();
        Minecraft client = Minecraft.getInstance();
        NativeImage image = new NativeImage(art.width(), art.height(), false);
        for (int py = 0; py < art.height(); py++) {
            for (int px = 0; px < art.width(); px++) {
                image.setPixel(px, py, art.argb()[(py * art.width()) + px]);
            }
        }
        client.getTextureManager().release(COVER);
        texture = new DynamicTexture(() -> "qza_music_cover", image);
        client.getTextureManager().register(COVER, texture);
        uploadedVersion = current.version();
    }

    private static String clock(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, secs);
    }

    private static String trim(Font font, String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String shown = text;
        while (shown.length() > 1 && font.width(shown + "...") > maxWidth) {
            shown = shown.substring(0, shown.length() - 1);
        }
        return shown.stripTrailing() + "...";
    }

    public static void resetPlacement() {
        QZAConfig defaults = new QZAConfig();
        QZAConfig cfg = ConfigManager.get();
        cfg.musicOverlayX = defaults.musicOverlayX;
        cfg.musicOverlayY = defaults.musicOverlayY;
        cfg.musicOverlayScale = defaults.musicOverlayScale;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
