package com.qza.dungeon;

import com.qza.chat.ChatFocus;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.notify.NotificationBox;
import com.qza.timer.ServerTickClock;
import com.qza.util.DungeonState;
import com.qza.util.IgnUtil;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CrystalTimer {
    public static final String SPAWNED_TEXT = "Crystal Spawned";
    public static final int SPAWNED_COLOUR = 0xFFFF55FF;
    public static final int SPAWNING_COLOUR = 0xFFFF5555;

    public static final long SECOND_SPAWN_TICKS = 160L;

    private static final double SECONDS_PER_TICK = 0.05;
    private static final double MILLIS_PER_TICK = 50.0;
    private static final int PAD_Y = 224;
    private static final double PLACE_RANGE_SQ = 36.0;
    private static final int INVENTORY_SLOTS = 36;
    private static final int RECHECK_TICKS = 10;
    private static final String CRYSTAL_ITEM = "Energy Crystal";

    private static final Pattern MAXOR_START =
            Pattern.compile("^\\[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!$");
    private static final Pattern STORM_START =
            Pattern.compile("^\\[BOSS] Storm: Pathetic Maxor, just like expected\\.$");
    private static final Pattern PICKED_UP =
            Pattern.compile("^(\\w{1,16}) picked up an Energy Crystal!$");

    private static final int HIDDEN = 0;
    private static final int SPAWNED = 1;
    private static final int SPAWNING = 2;

    private static int state = HIDDEN;
    private static long startTicks = -1L;
    private static long startMillis = -1L;
    private static boolean holding;
    private static int placed;
    private static int sincePlaced;

    private CrystalTimer() {
    }

    public static void init() {
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> onEntityLoad(entity));
    }

    public static void onChatMessage(String raw) {
        if (!ConfigManager.get().crystalTimerEnabled || raw == null) {
            return;
        }
        String message = IgnUtil.stripCodes(raw).trim();

        if (MAXOR_START.matcher(message).matches()) {
            reset();
            state = SPAWNED;
            startTicks = ServerTickClock.isAvailable() ? ServerTickClock.ticks() : -1L;
            startMillis = System.currentTimeMillis();
            sincePlaced = RECHECK_TICKS;
            return;
        }
        if (state == HIDDEN) {
            return;
        }
        if (STORM_START.matcher(message).matches()) {
            reset();
            return;
        }
        Matcher pickup = PICKED_UP.matcher(message);
        if (pickup.matches() && ChatFocus.isSelf(pickup.group(1))) {
            holding = true;
        }
    }

    public static void tick() {
        if (state == HIDDEN) {
            return;
        }
        if (!ConfigManager.get().crystalTimerEnabled) {
            reset();
            return;
        }
        if (sincePlaced < RECHECK_TICKS) {
            sincePlaced++;
        } else if (!holding && carryingCrystal()) {
            holding = true;
        }
        if (state == SPAWNING && elapsedTicks() >= SECOND_SPAWN_TICKS) {
            state = SPAWNED;
        }
    }

    private static boolean carryingCrystal() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < INVENTORY_SLOTS; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && IgnUtil.stripCodes(stack.getHoverName().getString()).contains(CRYSTAL_ITEM)) {
                return true;
            }
        }
        return false;
    }

    private static void onEntityLoad(Entity entity) {
        if (state == HIDDEN || !holding || !(entity instanceof EndCrystal)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || (int) Math.floor(entity.getY()) != PAD_Y) {
            return;
        }
        double dx = entity.getX() - player.getX();
        double dz = entity.getZ() - player.getZ();
        if ((dx * dx) + (dz * dz) >= PLACE_RANGE_SQ) {
            return;
        }
        placeCrystal();
    }

    static void placeCrystal() {
        holding = false;
        sincePlaced = 0;
        placed++;
        if (placed >= 2) {
            state = HIDDEN;
            return;
        }
        state = elapsedTicks() < SECOND_SPAWN_TICKS ? SPAWNING : SPAWNED;
    }

    private static long elapsedTicks() {
        if (startMillis < 0L) {
            return 0L;
        }
        if (startTicks >= 0L && ServerTickClock.isAvailable()) {
            return ServerTickClock.ticks() - startTicks;
        }
        return Math.round((System.currentTimeMillis() - startMillis) / MILLIS_PER_TICK);
    }

    public static String spawningText(double seconds) {
        return "Crystal Spawning in: " + String.format(Locale.ROOT, "%.2f", seconds) + "s";
    }

    public static void renderHud(GuiGraphicsExtractor graphics, Font font) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.crystalTimerEnabled || state == HIDDEN || !DungeonState.inDungeon()) {
            return;
        }
        boolean counting = state == SPAWNING;
        String text = counting
                ? spawningText(Math.max(0L, SECOND_SPAWN_TICKS - elapsedTicks()) * SECONDS_PER_TICK)
                : SPAWNED_TEXT;

        float scale = NotificationBox.clampScale(cfg.crystalTimerScale);
        int w = Math.round(font.width(text) * scale);
        int h = Math.round(font.lineHeight * scale);
        int[] pos = NotificationBox.topLeft(cfg.crystalTimerX, cfg.crystalTimerY,
                graphics.guiWidth(), graphics.guiHeight(), w, h);
        NotificationBox.drawPlain(graphics, font, text, pos[0], pos[1], scale,
                counting ? SPAWNING_COLOUR : SPAWNED_COLOUR);
    }

    public static void resetPlacement() {
        QZAConfig defaults = new QZAConfig();
        QZAConfig cfg = ConfigManager.get();
        cfg.crystalTimerX = defaults.crystalTimerX;
        cfg.crystalTimerY = defaults.crystalTimerY;
        cfg.crystalTimerScale = defaults.crystalTimerScale;
    }

    public static void reset() {
        state = HIDDEN;
        startTicks = -1L;
        startMillis = -1L;
        holding = false;
        placed = 0;
        sincePlaced = 0;
    }
}
