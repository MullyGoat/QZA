package com.qza.dungeon;

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
    public static final String PLACED_TEXT = "Crystal Placed";
    public static final int SPAWNED_COLOUR = 0xFFFF55FF;
    public static final int SPAWNING_COLOUR = 0xFFFF5555;

    public static final long CHARGE_TO_SPAWN_TICKS = 42L;
    public static final long HIT_TO_SPAWN_TICKS = 40L;
    public static final long QUICK_HIT_TICKS = 10L;

    private static final double SECONDS_PER_TICK = 0.05;
    private static final double MILLIS_PER_TICK = 50.0;
    private static final String CRYSTAL_ITEM = "Energy Crystal";
    private static final double SPAWN_SPOT_MIN_Y = 230.0;

    private static final Pattern MAXOR_START =
            Pattern.compile("^\\[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!$");
    private static final Pattern STORM_START =
            Pattern.compile("^\\[BOSS] Storm: Pathetic Maxor, just like expected\\.$");
    private static final Pattern LASER_CHARGING = Pattern.compile("^The Energy Laser is charging up!$");
    private static final Pattern MAXOR_HIT =
            Pattern.compile("^\\[BOSS] Maxor: (?:YOU TRICKED ME!|THAT BEAM! IT HURTS! IT HURTS!!)$");
    private static final Pattern PICKED_UP = Pattern.compile("^(\\w{1,16}) picked up an Energy Crystal!$");
    private static final Pattern CRYSTAL_ACTIVE = Pattern.compile("^\\d+/2 Energy Crystals are now active!$");

    private static boolean active;
    private static boolean finished;
    private static int set;
    private static boolean placed;
    private static boolean holding;
    private static long maxorTick = -1L;
    private static long maxorMillis = -1L;
    private static long chargeTick = -1L;
    private static long chargeMillis = -1L;
    private static long hitTick = -1L;
    private static long hitMillis = -1L;

    private CrystalTimer() {
    }

    public static void init() {
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> onEntity(entity, true));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> onEntity(entity, false));
    }

    private static boolean running() {
        return ConfigManager.get().crystalTimerEnabled || CrystalLog.on();
    }

    public static void onChatMessage(String raw) {
        if (raw == null || !running()) {
            return;
        }
        String message = IgnUtil.stripCodes(raw).trim();

        if (MAXOR_START.matcher(message).matches()) {
            reset();
            active = true;
            set = 1;
            maxorTick = nowTick();
            maxorMillis = System.currentTimeMillis();
            log(message + " -> " + SPAWNED_TEXT);
            return;
        }
        if (!active) {
            return;
        }
        if (STORM_START.matcher(message).matches()) {
            log(message + " -> done");
            reset();
            return;
        }
        if (LASER_CHARGING.matcher(message).matches()) {
            onCharging(message);
            return;
        }
        if (MAXOR_HIT.matcher(message).matches()) {
            if (set == 1 && hitMillis < 0L) {
                hitTick = nowTick();
                hitMillis = System.currentTimeMillis();
                log(message + " -> " + remaining() + " ticks until the second crystals");
            } else {
                log(message);
            }
            return;
        }
        Matcher pickup = PICKED_UP.matcher(message);
        if (pickup.matches()) {
            log(message);
            if (counting()) {
                secondSet(pickup.group(1) + " picked one up");
            }
            return;
        }
        if (CRYSTAL_ACTIVE.matcher(message).matches() || message.contains("Maxor is enraged")) {
            log(message);
        }
    }

    private static void onCharging(String message) {
        if (set != 1) {
            finished = true;
            log(message + " -> hidden, there are no more crystals");
            return;
        }
        if (chargeMillis >= 0L) {
            log(message + " again");
            return;
        }
        chargeTick = nowTick();
        chargeMillis = System.currentTimeMillis();
        log(message + " -> counting down from " + remaining() + " ticks");
    }

    private static void secondSet(String why) {
        set = 2;
        placed = false;
        log("second crystals are up (" + why + ") -> " + SPAWNED_TEXT);
    }

    public static void tick() {
        if (!active) {
            return;
        }
        if (!running()) {
            reset();
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        int slot = player == null ? -1 : crystalSlot(player.getInventory());
        if (slot >= 0 && !holding) {
            holding = true;
            log("your Energy Crystal is in slot " + (slot + 1));
            if (counting()) {
                secondSet("you picked one up");
            }
        } else if (slot < 0 && holding) {
            holding = false;
            log("your Energy Crystal left your inventory");
            if (!placed && !finished) {
                placed = true;
                log("-> " + PLACED_TEXT);
            }
        }
        if (counting() && remaining() <= 0L) {
            secondSet("countdown reached 0");
        }
    }

    private static int crystalSlot(Inventory inventory) {
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()
                    && CRYSTAL_ITEM.equalsIgnoreCase(IgnUtil.stripCodes(stack.getHoverName().getString()).trim())) {
                return i;
            }
        }
        return -1;
    }

    private static void onEntity(Entity entity, boolean added) {
        if (!active || !(entity instanceof EndCrystal)) {
            return;
        }
        if (CrystalLog.on()) {
            LocalPlayer player = Minecraft.getInstance().player;
            double distance = player == null ? -1.0
                    : Math.hypot(entity.getX() - player.getX(), entity.getZ() - player.getZ());
            log(String.format(Locale.ROOT, "End Crystal #%d %s at %.3f %.3f %.3f, %.1f blocks from you",
                    entity.getId(), added ? "appeared" : "went away", entity.getX(), entity.getY(),
                    entity.getZ(), distance));
        }
        if (added && counting() && entity.getY() > SPAWN_SPOT_MIN_Y) {
            secondSet("an End Crystal appeared at a spawn spot");
        }
    }

    private static boolean counting() {
        return active && !finished && set == 1 && (chargeMillis >= 0L || hitMillis >= 0L);
    }

    private static long remaining() {
        long afterCharge = since(chargeTick, chargeMillis);
        long afterHit = since(hitTick, hitMillis);
        if (afterCharge < 0L) {
            return HIT_TO_SPAWN_TICKS - afterHit;
        }
        if (afterHit >= 0L) {
            return afterCharge - afterHit <= QUICK_HIT_TICKS
                    ? CHARGE_TO_SPAWN_TICKS - afterCharge
                    : HIT_TO_SPAWN_TICKS - afterHit;
        }
        return Math.max(CHARGE_TO_SPAWN_TICKS - QUICK_HIT_TICKS, CHARGE_TO_SPAWN_TICKS - afterCharge);
    }

    private static long nowTick() {
        return ServerTickClock.isAvailable() ? ServerTickClock.ticks() : -1L;
    }

    private static long since(long tick, long millis) {
        if (millis < 0L) {
            return -1L;
        }
        if (tick >= 0L && ServerTickClock.ticking()) {
            return ServerTickClock.ticks() - tick;
        }
        return Math.round((System.currentTimeMillis() - millis) / MILLIS_PER_TICK);
    }

    private static void log(String event) {
        if (!CrystalLog.on()) {
            return;
        }
        StringBuilder line = new StringBuilder();
        line.append("server tick ").append(ServerTickClock.ticking() ? String.valueOf(ServerTickClock.ticks()) : "-");
        line.append(" | maxor +").append(since(maxorTick, maxorMillis)).append('t');
        long afterCharge = since(chargeTick, chargeMillis);
        if (afterCharge >= 0L) {
            line.append(" charging +").append(afterCharge).append('t');
        }
        long afterHit = since(hitTick, hitMillis);
        if (afterHit >= 0L) {
            line.append(" hit +").append(afterHit).append('t');
        }
        line.append(" | set ").append(set).append(" | ").append(event);
        CrystalLog.line(line.toString());
    }

    public static String countText(double seconds) {
        return "Crystals: " + String.format(Locale.ROOT, "%.2f", seconds) + "s";
    }

    public static String hudText() {
        if (!active || finished) {
            return null;
        }
        if (counting()) {
            long left = remaining();
            return left > 0L ? countText(left * SECONDS_PER_TICK) : SPAWNED_TEXT;
        }
        return placed ? PLACED_TEXT : SPAWNED_TEXT;
    }

    public static void renderHud(GuiGraphicsExtractor graphics, Font font) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.crystalTimerEnabled || !DungeonState.inDungeon()) {
            return;
        }
        String text = hudText();
        if (text == null) {
            return;
        }
        int colour = text.startsWith("Crystals:") ? SPAWNING_COLOUR : SPAWNED_COLOUR;

        float scale = NotificationBox.clampScale(cfg.crystalTimerScale);
        int w = Math.round(font.width(text) * scale);
        int h = Math.round(font.lineHeight * scale);
        int[] pos = NotificationBox.topLeft(cfg.crystalTimerX, cfg.crystalTimerY,
                graphics.guiWidth(), graphics.guiHeight(), w, h);
        NotificationBox.drawPlain(graphics, font, text, pos[0], pos[1], scale, colour);
    }

    public static void resetPlacement() {
        QZAConfig defaults = new QZAConfig();
        QZAConfig cfg = ConfigManager.get();
        cfg.crystalTimerX = defaults.crystalTimerX;
        cfg.crystalTimerY = defaults.crystalTimerY;
        cfg.crystalTimerScale = defaults.crystalTimerScale;
    }

    public static void reset() {
        active = false;
        finished = false;
        set = 0;
        placed = false;
        holding = false;
        maxorTick = -1L;
        maxorMillis = -1L;
        chargeTick = -1L;
        chargeMillis = -1L;
        hitTick = -1L;
        hitMillis = -1L;
        CrystalLog.end();
    }
}
