package com.qza.media;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.qza.QZA;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.notify.NotificationBox;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class MusicOverlay {
    private static final long POLL_MS = 750L;
    private static final long FAST_POLL_MS = 250L;
    private static final int FAST_POLLS = 4;
    private static final int NO_LENGTH_FAST_POLLS = 20;
    private static final long IDLE_MS = 1000L;
    private static final long ERROR_MS = 5000L;
    private static final int[] THUMBNAIL_POLLS = {0, 2, 5};
    private static final int THUMBNAIL_RETRIES = 8;
    private static final int COVER_SOURCE_H = 96;
    private static final long FILETIME_EPOCH = 116_444_736_000_000_000L;
    private static final long TICKS_PER_MS = 10_000L;
    private static final long MAX_UPDATE_AGE_MS = 6L * 60L * 60L * 1000L;
    private static final long PENDING_MS = 10_000L;
    private static final long REFRESH_SLACK_MS = 250L;
    private static final long CATCH_UP_MS = 1500L;
    private static final long RESTART_MS = 4000L;
    private static final int ALL_CONTROLS = WindowsMedia.CAN_PREVIOUS | WindowsMedia.CAN_NEXT
            | WindowsMedia.CAN_TOGGLE | WindowsMedia.CAN_SEEK;
    private static final Identifier COVER = Identifier.fromNamespaceAndPath("qza", "music_cover");

    private static final int PAD = 4;
    private static final int COVER_H = 32;
    private static final int COVER_MAX_W = 57;
    private static final int GAP = 5;
    private static final int TEXT_MIN_W = 70;
    private static final int TEXT_MAX_W = 140;
    private static final int LINE = 10;
    private static final int BAR_H = 2;
    private static final int SEEK_BAR_H = 3;
    private static final int SEEK_SLOP = 3;
    private static final int CONTROLS_GAP = 3;
    private static final int BUTTON_W = 14;
    private static final int BUTTON_H = 11;
    private static final int BUTTON_GAP = 4;
    private static final int BUTTONS = 3;
    private static final int ICON_H = 7;

    private static final int BACKGROUND = 0xCC14181E;
    private static final int BORDER = 0x40FFFFFF;
    private static final int TITLE = 0xFFFFFFFF;
    private static final int ARTIST = 0xFFAAAAAA;
    private static final int TIME = 0xFFCCCCCC;
    private static final int PAUSED = 0xFFFFD866;
    private static final int BAR_BACK = 0x40FFFFFF;
    private static final int BAR_FILL = 0xFFFF55FF;
    private static final int SEEK_TIME = 0xFFFFAAFF;
    private static final int SEEK_MARK = 0xAAFFFFFF;
    private static final int KNOB = 0xFFFFFFFF;
    private static final int BUTTON = 0x30FFFFFF;
    private static final int BUTTON_HOVER = 0x66FFFFFF;
    private static final int ICON_ON = 0xFFFFFFFF;
    private static final int ICON_OFF = 0xFF5A5A5A;
    private static final int NOTE = 0xFF8A8A8A;

    private static final State EXAMPLE = new State("", "", "Song Title", "Artist", true, ALL_CONTROLS,
            0L, 0L, 225_000L, 83_000L, -1L, 0, null);

    private static final LinkedBlockingQueue<Runnable> commands = new LinkedBlockingQueue<>();

    private static volatile State state;
    private static volatile Pending pending;
    private static volatile boolean unavailable =
            !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    private static Thread worker;

    private static String lastKey = "";
    private static int keyPolls;
    private static int coverTries;
    private static int fastPolls;
    private static Cover cover;
    private static int coverVersion;
    private static long seenPosition = Long.MIN_VALUE;
    private static long seenAt;

    private static int uploadedVersion;
    private static DynamicTexture texture;

    private record Cover(int width, int height, int[] argb, int hash) {
    }

    private record State(String app, String key, String title, String subtitle, boolean playing, int controls,
                         long startTicks, long updatedTicks, long lengthMs, long positionMs, long anchorMs,
                         int version, Cover cover) {
        long position(long now) {
            long at = playing && anchorMs >= 0 ? positionMs + Math.max(0L, now - anchorMs) : positionMs;
            return lengthMs > 0 ? Math.min(at, lengthMs) : at;
        }

        boolean can(int control) {
            return (controls & control) != 0;
        }

        State at(boolean nowPlaying, long position, long anchor) {
            return new State(app, key, title, subtitle, nowPlaying, controls, startTicks, updatedTicks, lengthMs,
                    position, anchor, version, cover);
        }
    }

    private record Pending(String key, Boolean playing, long positionMs, long at, long until, long updatedTicks) {
    }

    private record Layout(int w, int h, boolean cover, int coverW, int textX, int textY, int textW,
                          int barY, int barH, int buttonsX, int buttonsY) {
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
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof AbstractContainerScreen<?>) {
                ScreenMouseEvents.allowMouseClick(screen).register((current, event) -> !mouseClicked(current, event));
            }
        });
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
                    pending = null;
                    commands.clear();
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
                waitForWork(fastPolls > 0 ? FAST_POLL_MS : POLL_MS);
                if (fastPolls > 0) {
                    fastPolls--;
                }
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

    private static void waitForWork(long millis) {
        try {
            Runnable command = commands.poll(millis, TimeUnit.MILLISECONDS);
            while (command != null) {
                command.run();
                fastPolls = FAST_POLLS;
                command = commands.poll();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
        State before = state;
        boolean held = length <= 0 && position <= 0 && before != null && before.key().equals(track.key())
                && before.lengthMs() > 0;
        if (held) {
            length = before.lengthMs();
            position = before.position(now);
        }
        if (position != seenPosition) {
            seenPosition = position;
            seenAt = now;
        }
        long anchor = seenAt;
        long updated = track.updatedTicks() > FILETIME_EPOCH
                ? (track.updatedTicks() - FILETIME_EPOCH) / TICKS_PER_MS : -1L;
        if (updated >= 0 && !held) {
            long age = now - updated;
            if (age >= 0 && age <= MAX_UPDATE_AGE_MS && (length <= 0 || position + age <= length + POLL_MS)) {
                anchor = updated;
            }
        }

        Pending hold = pending;
        if (hold != null) {
            if (now > hold.until() || !hold.key().equals(track.key())) {
                pending = null;
            } else {
                boolean wanted = hold.playing() != null ? hold.playing() : playing;
                long expected = hold.positionMs() + (wanted ? Math.max(0L, now - hold.at()) : 0L);
                long reported = position + (playing ? Math.max(0L, now - anchor) : 0L);
                boolean refreshed = track.updatedTicks() != hold.updatedTicks()
                        && updated >= hold.at() - REFRESH_SLACK_MS;
                if (wanted == playing && (refreshed || Math.abs(reported - expected) <= CATCH_UP_MS)) {
                    pending = null;
                } else {
                    playing = wanted;
                    position = hold.positionMs();
                    anchor = hold.at();
                }
            }
        }

        int controls = track.controls() == 0 ? ALL_CONTROLS : track.controls();
        String subtitle = track.artist().isBlank() ? appName(track.app()) : track.artist();
        state = new State(track.app(), track.key(), track.title(), subtitle, playing, controls,
                track.startTicks(), track.updatedTicks(), length, position, anchor, coverVersion, cover);
        if (length <= 0 && keyPolls < NO_LENGTH_FAST_POLLS) {
            fastPolls = Math.max(fastPolls, 1);
        }
    }

    private static boolean wantThumbnail(WindowsMedia.Track track) {
        String key = track.key();
        if (!key.equals(lastKey)) {
            lastKey = key;
            keyPolls = 0;
            coverTries = 0;
            cover = null;
            coverVersion++;
            seenPosition = Long.MIN_VALUE;
        } else {
            keyPolls++;
        }
        if (!ConfigManager.get().musicOverlayCover) {
            return false;
        }
        if (cover == null && coverTries < THUMBNAIL_RETRIES) {
            coverTries++;
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

    private static State visible() {
        State current = state;
        if (!enabled() || unavailable || current == null) {
            return null;
        }
        return current;
    }

    public static void renderHud(GuiGraphicsExtractor graphics, Font font) {
        State current = visible();
        if (current == null || Mc.screen() instanceof AbstractContainerScreen<?>) {
            return;
        }
        Layout layout = layout(font, current, false);
        int[] box = box(font, current, layout, graphics.guiWidth(), graphics.guiHeight());
        draw(graphics, font, current, layout, box[0], box[1], Double.NaN, Double.NaN);
    }

    public static void renderScreen(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics,
                                    int mouseX, int mouseY) {
        State current = visible();
        if (current == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        Layout layout = layout(font, current, true);
        int[] box = box(font, current, layout, screen.width, screen.height);
        float scale = scale();
        draw(graphics, font, current, layout, box[0], box[1],
                (mouseX - box[0]) / (double) scale, (mouseY - box[1]) / (double) scale);
    }

    private static boolean mouseClicked(Screen screen, MouseButtonEvent event) {
        State current = visible();
        if (current == null) {
            return false;
        }
        Font font = Minecraft.getInstance().font;
        Layout layout = layout(font, current, true);
        int[] box = box(font, current, layout, screen.width, screen.height);
        float scale = scale();
        double mx = (event.x() - box[0]) / scale;
        double my = (event.y() - box[1]) / scale;
        if (mx < 0 || my < 0 || mx >= layout.w() || my >= layout.h()) {
            return false;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return true;
        }
        if (seekable(current) && onBar(layout, mx, my)) {
            act(current, WindowsMedia.Action.SEEK, timeAt(layout, current, mx));
            return true;
        }
        for (int i = 0; i < BUTTONS; i++) {
            if (enabled(current, i) && onButton(layout, i, mx, my)) {
                press(current, i);
                return true;
            }
        }
        return true;
    }

    private static void press(State current, int button) {
        switch (button) {
            case 0 -> {
                long now = System.currentTimeMillis();
                boolean restart = current.can(WindowsMedia.CAN_SEEK) && current.lengthMs() > 0
                        && (current.position(now) > RESTART_MS || !current.can(WindowsMedia.CAN_PREVIOUS));
                if (restart) {
                    act(current, WindowsMedia.Action.SEEK, 0L);
                } else {
                    act(current, WindowsMedia.Action.PREVIOUS, 0L);
                }
            }
            case 1 -> act(current, WindowsMedia.Action.TOGGLE, 0L);
            default -> act(current, WindowsMedia.Action.NEXT, 0L);
        }
    }

    private static void act(State current, WindowsMedia.Action action, long targetMs) {
        long now = System.currentTimeMillis();
        switch (action) {
            case TOGGLE -> {
                long position = current.position(now);
                boolean playing = !current.playing();
                pending = new Pending(current.key(), playing, position, now, now + PENDING_MS,
                        current.updatedTicks());
                state = current.at(playing, position, now);
            }
            case SEEK -> {
                pending = new Pending(current.key(), null, targetMs, now, now + PENDING_MS,
                        current.updatedTicks());
                state = current.at(current.playing(), targetMs, now);
            }
            default -> pending = null;
        }
        String app = current.app();
        long ticks = current.startTicks() + (targetMs * TICKS_PER_MS);
        commands.offer(() -> {
            if (!WindowsMedia.command(app, action, ticks)) {
                pending = null;
            }
        });
    }

    private static boolean enabled(State current, int button) {
        return switch (button) {
            case 0 -> current.can(WindowsMedia.CAN_PREVIOUS)
                    || (current.can(WindowsMedia.CAN_SEEK) && current.lengthMs() > 0);
            case 1 -> current.can(WindowsMedia.CAN_TOGGLE);
            default -> current.can(WindowsMedia.CAN_NEXT);
        };
    }

    private static boolean seekable(State current) {
        return current.lengthMs() > 0 && current.can(WindowsMedia.CAN_SEEK);
    }

    private static boolean onBar(Layout layout, double mx, double my) {
        return mx >= layout.textX() && mx <= layout.textX() + layout.textW()
                && my >= layout.barY() - SEEK_SLOP && my <= layout.barY() + layout.barH() + SEEK_SLOP;
    }

    private static long timeAt(Layout layout, State current, double mx) {
        double share = Math.max(0.0, Math.min(1.0, (mx - layout.textX()) / layout.textW()));
        return Math.round(share * current.lengthMs());
    }

    private static int buttonX(Layout layout, int button) {
        return layout.buttonsX() + (button * (BUTTON_W + BUTTON_GAP));
    }

    private static boolean onButton(Layout layout, int button, double mx, double my) {
        int x = buttonX(layout, button);
        return mx >= x && mx < x + BUTTON_W && my >= layout.buttonsY() && my < layout.buttonsY() + BUTTON_H;
    }

    public static int[] previewBox(Font font, int screenW, int screenH) {
        State current = preview();
        return box(font, current, layout(font, current, false), screenW, screenH);
    }

    public static void drawPreview(GuiGraphicsExtractor graphics, Font font, int x, int y) {
        State current = preview();
        draw(graphics, font, current, layout(font, current, false), x, y, Double.NaN, Double.NaN);
    }

    private static State preview() {
        State current = state;
        return current != null ? current : EXAMPLE;
    }

    private static float scale() {
        return NotificationBox.clampScale(ConfigManager.get().musicOverlayScale);
    }

    private static int[] box(Font font, State current, Layout layout, int screenW, int screenH) {
        QZAConfig cfg = ConfigManager.get();
        float scale = scale();
        Layout resting = layout(font, current, false);
        int[] pos = NotificationBox.topLeft(cfg.musicOverlayX, cfg.musicOverlayY, screenW, screenH,
                Math.round(resting.w() * scale), Math.round(resting.h() * scale));
        int w = Math.round(layout.w() * scale);
        int h = Math.round(layout.h() * scale);
        int x = Math.max(0, Math.min(pos[0], screenW - w));
        int y = Math.max(0, Math.min(pos[1], screenH - h));
        return new int[]{x, y, w, h};
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

    private static Layout layout(Font font, State current, boolean interactive) {
        boolean showCover = ConfigManager.get().musicOverlayCover;
        int coverW = showCover ? coverWidth(current) : 0;
        int textW = textWidth(font, current);
        int barH = interactive ? SEEK_BAR_H : BAR_H;
        int block = (LINE * 3) - 1 + barH + (interactive ? CONTROLS_GAP + BUTTON_H : 1);
        int inner = showCover ? Math.max(COVER_H, block) : block;
        int textX = PAD + (showCover ? coverW + GAP : 0);
        int textY = PAD + ((inner - block) / 2);
        int barY = textY + (LINE * 3) - 1;
        int buttonsW = (BUTTON_W * BUTTONS) + (BUTTON_GAP * (BUTTONS - 1));
        return new Layout(textX + textW + PAD, PAD + inner + PAD, showCover, coverW, textX, textY, textW,
                barY, barH, textX + ((textW - buttonsW) / 2), barY + barH + CONTROLS_GAP);
    }

    private static void draw(GuiGraphicsExtractor graphics, Font font, State current, Layout layout,
                             int x, int y, double mx, double my) {
        float scale = scale();
        boolean interactive = !Double.isNaN(mx);
        long now = System.currentTimeMillis();
        int w = layout.w();
        int h = layout.h();

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);

        if (!ConfigManager.get().musicOverlayHideBackground) {
            graphics.fill(0, 0, w, h, BACKGROUND);
            graphics.fill(0, 0, w, 1, BORDER);
            graphics.fill(0, h - 1, w, h, BORDER);
            graphics.fill(0, 1, 1, h - 1, BORDER);
            graphics.fill(w - 1, 1, w, h - 1, BORDER);
        }

        if (layout.cover()) {
            int coverW = layout.coverW();
            int coverY = (h - COVER_H) / 2;
            if (current.cover() != null) {
                upload(current);
                graphics.blit(RenderPipelines.GUI_TEXTURED, COVER, PAD, coverY, 0f, 0f,
                        coverW, COVER_H, coverW, COVER_H);
            } else {
                graphics.fill(PAD, coverY, PAD + coverW, coverY + COVER_H, 0x55FFFFFF);
                graphics.centeredText(font, "♪", PAD + (coverW / 2), coverY + (COVER_H / 2) - 4, NOTE);
            }
        }

        int textX = layout.textX();
        int textY = layout.textY();
        int textW = layout.textW();
        graphics.text(font, trim(font, current.title(), textW), textX, textY, TITLE);
        graphics.text(font, trim(font, current.subtitle(), textW), textX, textY + LINE, ARTIST);

        boolean hoverBar = interactive && seekable(current) && onBar(layout, mx, my);
        long position = current.position(now);
        String left = hoverBar ? clock(timeAt(layout, current, mx)) : timeLeft(current, now);
        int leftColour = hoverBar ? SEEK_TIME : current.playing() ? TIME : PAUSED;
        String right = timeRight(current);
        graphics.text(font, left, textX, textY + (LINE * 2), leftColour);
        if (!right.isEmpty()) {
            graphics.text(font, right, textX + textW - font.width(right), textY + (LINE * 2), ARTIST);
            int barY = layout.barY();
            int barH = layout.barH();
            graphics.fill(textX, barY, textX + textW, barY + barH, BAR_BACK);
            int filled = (int) Math.round(textW * Math.min(1.0, position / (double) current.lengthMs()));
            if (filled > 0) {
                graphics.fill(textX, barY, textX + filled, barY + barH, BAR_FILL);
            }
            if (interactive && seekable(current)) {
                int knob = textX + Math.max(1, Math.min(textW - 1, filled));
                graphics.fill(knob - 1, barY - 1, knob + 1, barY + barH + 1, KNOB);
            }
            if (hoverBar) {
                int mark = (int) Math.round(Math.max(textX, Math.min(textX + textW - 1, mx)));
                graphics.fill(mark, barY - 1, mark + 1, barY + barH + 1, SEEK_MARK);
            }
        }

        if (interactive) {
            drawButtons(graphics, current, layout, mx, my);
        }
        graphics.pose().popMatrix();
    }

    private static void drawButtons(GuiGraphicsExtractor graphics, State current, Layout layout,
                                    double mx, double my) {
        for (int i = 0; i < BUTTONS; i++) {
            int bx = buttonX(layout, i);
            int by = layout.buttonsY();
            boolean on = enabled(current, i);
            boolean hover = on && onButton(layout, i, mx, my);
            graphics.fill(bx, by, bx + BUTTON_W, by + BUTTON_H, hover ? BUTTON_HOVER : BUTTON);
            int colour = on ? ICON_ON : ICON_OFF;
            int iy = by + ((BUTTON_H - ICON_H) / 2);
            int centre = bx + (BUTTON_W / 2);
            switch (i) {
                case 0 -> drawBack(graphics, centre - 3, iy, colour);
                case 1 -> {
                    if (current.playing()) {
                        drawPause(graphics, centre - 3, iy, colour);
                    } else {
                        drawPlay(graphics, centre - 2, iy, colour);
                    }
                }
                default -> drawNext(graphics, centre - 3, iy, colour);
            }
        }
    }

    private static void drawBack(GuiGraphicsExtractor graphics, int x, int y, int colour) {
        graphics.fill(x, y, x + 1, y + ICON_H, colour);
        for (int k = 0; k < 4; k++) {
            graphics.fill(x + 2 + k, y + 3 - k, x + 3 + k, y + 4 + k, colour);
        }
    }

    private static void drawNext(GuiGraphicsExtractor graphics, int x, int y, int colour) {
        for (int k = 0; k < 4; k++) {
            graphics.fill(x + k, y + k, x + 1 + k, y + ICON_H - k, colour);
        }
        graphics.fill(x + 5, y, x + 6, y + ICON_H, colour);
    }

    private static void drawPlay(GuiGraphicsExtractor graphics, int x, int y, int colour) {
        for (int k = 0; k < 4; k++) {
            graphics.fill(x + k, y + k, x + 1 + k, y + ICON_H - k, colour);
        }
    }

    private static void drawPause(GuiGraphicsExtractor graphics, int x, int y, int colour) {
        graphics.fill(x, y, x + 2, y + ICON_H, colour);
        graphics.fill(x + 4, y, x + 6, y + ICON_H, colour);
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
