package com.qza.dungeon;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.notify.NotificationBox;
import com.qza.util.DungeonState;
import com.qza.util.IgnUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.regex.Pattern;

public final class WitherKey {
    public static final String TEXT = "WITHER KEY";

    public static final int WAITING_COLOUR = 0xFFAAAAAA;
    public static final int DROPPED_COLOUR = 0xFFFF5555;
    public static final int PICKED_COLOUR = 0xFF55FF55;

    private static final String ENTITY_NAME = "Wither Key";
    private static final int SCAN_TICKS = 5;

    private static final Pattern OBTAINED =
            Pattern.compile("^(?:\\[[^]]*?])? ?\\w{1,16} has obtained Wither Key!?$");
    private static final Pattern PICKED_UP =
            Pattern.compile("^A Wither Key was picked up!$");
    private static final Pattern DOOR_OPENED =
            Pattern.compile("^(?:\\[[^]]*?])? ?\\w{1,16} opened a WITHER door!$");
    private static final Pattern BLOOD_OPENED =
            Pattern.compile("^The BLOOD DOOR has been opened!$");

    private static final int WAITING = 0;
    private static final int DROPPED = 1;
    private static final int PICKED = 2;

    private static int state = WAITING;
    private static boolean bloodOpened;
    private static int sinceScan;

    private WitherKey() {
    }

    public static void onChatMessage(String raw) {
        if (!ConfigManager.get().witherKeyEnabled || raw == null) {
            return;
        }
        String message = IgnUtil.stripCodes(raw).trim();

        if (BLOOD_OPENED.matcher(message).matches()) {
            bloodOpened = true;
            state = WAITING;
            return;
        }
        if (DOOR_OPENED.matcher(message).matches()) {
            state = WAITING;
            return;
        }
        if (PICKED_UP.matcher(message).matches() || OBTAINED.matcher(message).matches()) {
            state = PICKED;
        }
    }

    public static void tick() {
        if (!ConfigManager.get().witherKeyEnabled || bloodOpened || state != WAITING) {
            sinceScan = 0;
            return;
        }
        if (++sinceScan < SCAN_TICKS) {
            return;
        }
        sinceScan = 0;

        if (keyOnGround()) {
            state = DROPPED;
        }
    }

    private static boolean keyOnGround() {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null || !DungeonState.inDungeon()) {
            return false;
        }
        for (Entity entity : level.entitiesForRendering()) {
            Component name = entity.getCustomName();
            if (name == null) {
                continue;
            }
            if (ENTITY_NAME.equals(IgnUtil.stripCodes(name.getString()).trim())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hidden() {
        return bloodOpened;
    }

    public static int colour() {
        return switch (state) {
            case DROPPED -> DROPPED_COLOUR;
            case PICKED -> PICKED_COLOUR;
            default -> WAITING_COLOUR;
        };
    }

    public static void renderHud(GuiGraphicsExtractor graphics, Font font) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.witherKeyEnabled || hidden() || !DungeonState.inDungeon()) {
            return;
        }

        float scale = NotificationBox.clampScale(cfg.witherKeyScale);
        int w = Math.round(font.width(TEXT) * scale);
        int h = Math.round(font.lineHeight * scale);
        int[] pos = NotificationBox.topLeft(cfg.witherKeyX, cfg.witherKeyY,
                graphics.guiWidth(), graphics.guiHeight(), w, h);

        NotificationBox.drawPlain(graphics, font, TEXT, pos[0], pos[1], scale, colour());
    }

    public static void resetPlacement() {
        QZAConfig defaults = new QZAConfig();
        QZAConfig cfg = ConfigManager.get();
        cfg.witherKeyX = defaults.witherKeyX;
        cfg.witherKeyY = defaults.witherKeyY;
        cfg.witherKeyScale = defaults.witherKeyScale;
    }

    public static void reset() {
        state = WAITING;
        bloodOpened = false;
        sinceScan = 0;
    }
}
