package com.qza.dungeon;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.notify.NotificationBox;
import com.qza.timer.NecronTimer;
import com.qza.util.DungeonState;
import com.qza.util.IgnUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.regex.Pattern;

public final class NecronLeap {
    public static final String TEXT = "LEAP TO P5!";
    public static final int TEXT_COLOUR = 0xFFFF55FF;

    private static final long SHOW_MS = 3000L;
    private static final float ARMED_PROGRESS = 0.5f;
    private static final float SEVENTY_MILLION = 0.0505f;
    private static final float DEAD_PROGRESS = 0.001f;
    private static final int GONE_TICKS = 2;

    private static final Pattern START = Pattern.compile(
            "^\\[BOSS] Necron: You went further than any human before, congratulations\\.$");

    private static boolean running;
    private static boolean armed;
    private static boolean hit70m;
    private static int goneFor;
    private static String dungeonClass;
    private static long shownAt = -1L;

    private NecronLeap() {
    }

    public static void onChatMessage(String raw) {
        if (!ConfigManager.get().necronLeapEnabled || raw == null) {
            return;
        }
        if (!START.matcher(IgnUtil.stripCodes(raw).trim()).matches() || !inM7()) {
            return;
        }
        running = true;
        armed = false;
        hit70m = false;
        goneFor = 0;
        shownAt = -1L;
        dungeonClass = ownClass();
    }

    private static boolean inM7() {
        return DungeonState.inDungeon() && DungeonState.catacombsMaster() && DungeonState.catacombsFloor() == 7;
    }

    public static void tick() {
        if (!running) {
            return;
        }
        if (!ConfigManager.get().necronLeapEnabled) {
            running = false;
            return;
        }
        if (dungeonClass == null) {
            dungeonClass = ownClass();
        }

        Float progress = NecronTimer.necronProgress();
        if (progress != null) {
            goneFor = 0;
            if (progress >= ARMED_PROGRESS) {
                armed = true;
            } else if (armed && !hit70m && progress <= SEVENTY_MILLION) {
                hit70m = true;
                if (leapsAt70m()) {
                    show();
                }
            } else if (hit70m && progress <= DEAD_PROGRESS) {
                died();
            }
            return;
        }
        if (!armed || ++goneFor < GONE_TICKS) {
            return;
        }
        if (!hit70m) {
            hit70m = true;
            if (!"Healer".equals(dungeonClass)) {
                show();
            }
            running = false;
            return;
        }
        died();
    }

    private static boolean leapsAt70m() {
        return !"Mage".equals(dungeonClass) && !"Healer".equals(dungeonClass);
    }

    private static void died() {
        running = false;
        if ("Mage".equals(dungeonClass)) {
            show();
        }
    }

    private static String ownClass() {
        User user = Minecraft.getInstance().getUser();
        String name = user == null ? null : user.getName();
        return name == null || name.isBlank() ? null : LeapNotification.classOf(name);
    }

    private static void show() {
        shownAt = System.currentTimeMillis();
        Minecraft client = Minecraft.getInstance();
        if (client.getSoundManager() != null) {
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 2.0f));
        }
    }

    public static void renderHud(GuiGraphicsExtractor graphics, Font font) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.necronLeapEnabled || shownAt < 0L) {
            return;
        }
        if (System.currentTimeMillis() - shownAt > SHOW_MS) {
            shownAt = -1L;
            return;
        }
        float scale = NotificationBox.clampScale(cfg.necronLeapScale);
        int w = Math.round(font.width(TEXT) * scale);
        int h = Math.round(font.lineHeight * scale);
        int[] pos = NotificationBox.topLeft(cfg.necronLeapX, cfg.necronLeapY,
                graphics.guiWidth(), graphics.guiHeight(), w, h);
        NotificationBox.drawPlain(graphics, font, TEXT, pos[0], pos[1], scale, TEXT_COLOUR);
    }

    public static void resetPlacement() {
        QZAConfig defaults = new QZAConfig();
        QZAConfig cfg = ConfigManager.get();
        cfg.necronLeapX = defaults.necronLeapX;
        cfg.necronLeapY = defaults.necronLeapY;
        cfg.necronLeapScale = defaults.necronLeapScale;
    }

    public static void reset() {
        running = false;
        armed = false;
        hit70m = false;
        goneFor = 0;
        dungeonClass = null;
        shownAt = -1L;
    }
}
