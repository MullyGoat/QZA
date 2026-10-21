package com.qza.dungeon;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.notify.NotificationBox;
import com.qza.timer.ServerTickClock;
import com.qza.util.DungeonState;
import com.qza.util.IgnUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;
import java.util.regex.Pattern;

public final class RelicTimer {
    public static final long SPAWN_TICKS = 61L;
    public static final int COLOUR = 0xFFFF55FF;

    private static final double SECONDS_PER_TICK = 0.05;
    private static final double MILLIS_PER_TICK = 50.0;
    private static final float DEATH_VOLUME = 15.0f;
    private static final float DEATH_PITCH = 1.0f;

    private static final Pattern NECRON_ARGH = Pattern.compile("^\\[BOSS] Necron: ARGH!$");

    private static boolean armed;
    private static long startTicks = -1L;
    private static long startMillis = -1L;

    private RelicTimer() {
    }

    public static void onChatMessage(String raw) {
        if (!ConfigManager.get().relicTimerEnabled || raw == null) {
            return;
        }
        if (NECRON_ARGH.matcher(IgnUtil.stripCodes(raw).trim()).matches() && inM7()) {
            armed = true;
        }
    }

    public static void onSound(ClientboundSoundPacket packet) {
        if (!armed || !ConfigManager.get().relicTimerEnabled) {
            return;
        }
        if (packet.getVolume() != DEATH_VOLUME || packet.getPitch() != DEATH_PITCH
                || !packet.getSound().value().location().equals(SoundEvents.WITHER_HURT.location())) {
            return;
        }
        armed = false;
        startTicks = ServerTickClock.isAvailable() ? ServerTickClock.ticks() : -1L;
        startMillis = System.currentTimeMillis();
    }

    private static boolean inM7() {
        return DungeonState.inDungeon() && DungeonState.catacombsMaster() && DungeonState.catacombsFloor() == 7;
    }

    private static long elapsedTicks() {
        if (startTicks >= 0L && ServerTickClock.ticking()) {
            return ServerTickClock.ticks() - startTicks;
        }
        return Math.round((System.currentTimeMillis() - startMillis) / MILLIS_PER_TICK);
    }

    public static String text(double seconds) {
        return "Relics: " + String.format(Locale.ROOT, "%.2f", seconds) + "s";
    }

    public static void renderHud(GuiGraphicsExtractor graphics, Font font) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.relicTimerEnabled || startMillis < 0L) {
            return;
        }
        long left = SPAWN_TICKS - elapsedTicks();
        if (left <= 0L) {
            reset();
            return;
        }
        String text = text(left * SECONDS_PER_TICK);
        float scale = NotificationBox.clampScale(cfg.relicTimerScale);
        int w = Math.round(font.width(text) * scale);
        int h = Math.round(font.lineHeight * scale);
        int[] pos = NotificationBox.topLeft(cfg.relicTimerX, cfg.relicTimerY,
                graphics.guiWidth(), graphics.guiHeight(), w, h);
        NotificationBox.drawPlain(graphics, font, text, pos[0], pos[1], scale, COLOUR);
    }

    public static void resetPlacement() {
        QZAConfig defaults = new QZAConfig();
        QZAConfig cfg = ConfigManager.get();
        cfg.relicTimerX = defaults.relicTimerX;
        cfg.relicTimerY = defaults.relicTimerY;
        cfg.relicTimerScale = defaults.relicTimerScale;
    }

    public static void reset() {
        armed = false;
        startTicks = -1L;
        startMillis = -1L;
    }
}
