package com.qza.dungeon;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.notify.NotificationBox;
import com.qza.timer.ServerTickClock;
import com.qza.util.DungeonState;
import com.qza.util.ChatUtil;
import com.qza.util.IgnUtil;
import com.qza.util.Scheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SplitTimers {
    public static final int LINE_GAP = 1;

    private static final int ANNOUNCE_DELAY_TICKS = 10;
    private static final double SECONDS_PER_TICK = 0.05;

    private static final Pattern MORT = Pattern.compile(
            "^\\[NPC] Mort: (Here, I found this map when I first entered the dungeon\\."
                    + "|Right-click the Orb for spells, and Left-click \\(or Drop\\) to use your Ultimate!)$");
    private static final Pattern BLOOD_OPEN = Pattern.compile(
            "^\\[BOSS] The Watcher: (Ah, we meet again\\. As I foresaw\\.\\.\\."
                    + "|Congratulations, you made it through the Entrance\\."
                    + "|Ah, you've finally arrived\\.|Ah, we meet again\\.\\.\\."
                    + "|So you made it this far\\.\\.\\. interesting\\."
                    + "|You've managed to scratch and claw your way here, eh\\?"
                    + "|I'm starting to get tired of seeing you around here\\.\\.\\."
                    + "|Oh\\.\\. hello\\?|Things feel a little more roomy now, eh\\?)$"
                    + "|^The BLOOD DOOR has been opened!$");
    private static final Pattern WATCHER_DONE = Pattern.compile(
            "^\\[BOSS] The Watcher: You have proven yourself\\. You may pass\\.$");
    private static final Pattern MAXOR = Pattern.compile(
            "^\\[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!$");
    private static final Pattern STORM = Pattern.compile(
            "^\\[BOSS] Storm: Pathetic Maxor, just like expected\\.$");
    private static final Pattern GOLDOR = Pattern.compile(
            "^\\[BOSS] Goldor: Who dares trespass into my domain\\?$");
    private static final Pattern CORE = Pattern.compile("^The Core entrance is opening!$");
    private static final Pattern NECRON = Pattern.compile(
            "^\\[BOSS] Necron: You went further than any human before, congratulations\\.$");
    private static final Pattern WITHER_KING = Pattern.compile("^\\[BOSS] Wither King: You\\.\\.\\. again\\?$");
    private static final Pattern DEFEATED = Pattern.compile(
            "^\\s*☠ Defeated (.+) in 0?([\\dhms ]+?)\\s*(\\(NEW RECORD!\\))?$", Pattern.MULTILINE);

    private static final Split BLOOD_OPEN_SPLIT = new Split("§2Blood Open", MORT, BLOOD_OPEN);
    private static final Split BLOOD_CLEAR_SPLIT = new Split("§bBlood Clear", BLOOD_OPEN, WATCHER_DONE);
    private static final Split PORTAL_SPLIT = new Split("§dPortal Entry", WATCHER_DONE, MAXOR);
    private static final Split BOSS_ENTRY_SPLIT = new Split("§9Boss Entry", MORT, MAXOR);
    private static final Split MAXOR_SPLIT = new Split("§5Maxor", MAXOR, STORM);
    private static final Split STORM_SPLIT = new Split("§3Storm", STORM, GOLDOR);
    private static final Split TERMINALS_SPLIT = new Split("§6Terminals", GOLDOR, CORE);
    private static final Split GOLDOR_SPLIT = new Split("§7Goldor", CORE, NECRON);
    private static final Split NECRON_SPLIT = new Split("§cNecron", NECRON, WITHER_KING);
    private static final Split DRAGONS_SPLIT = new Split("§4Dragons", WITHER_KING, null);
    private static final Split TOTAL_SPLIT = new Split("§1Total", MORT, null);

    private static final Split[] ALL = {
            BLOOD_OPEN_SPLIT, BLOOD_CLEAR_SPLIT, PORTAL_SPLIT, BOSS_ENTRY_SPLIT, MAXOR_SPLIT, STORM_SPLIT,
            TERMINALS_SPLIT, GOLDOR_SPLIT, NECRON_SPLIT, DRAGONS_SPLIT, TOTAL_SPLIT};

    private static boolean runOver;

    private SplitTimers() {
    }

    public static void onChatMessage(String raw) {
        QZAConfig cfg = ConfigManager.get();
        if (!(cfg.splitTimersEnabled || cfg.lagTimerEnabled) || raw == null || runOver) {
            return;
        }
        String message = IgnUtil.stripCodes(raw).trim();
        if (message.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        long tick = ServerTickClock.ticks();

        if (DEFEATED.matcher(message).find()) {
            for (Split split : ALL) {
                if (split.running()) {
                    split.end(now, tick);
                }
            }
            runOver = true;
            if (cfg.lagTimerEnabled && TOTAL_SPLIT.started() && TOTAL_SPLIT.ticks() > 0L) {
                announceLag((TOTAL_SPLIT.millis() / 1000.0) - (TOTAL_SPLIT.ticks() * SECONDS_PER_TICK));
            }
            return;
        }

        for (Split split : ALL) {
            if (split.running() && split.end != null && split.end.matcher(message).matches()) {
                split.end(now, tick);
            }
        }
        for (Split split : ALL) {
            if (!split.started() && split.start.matcher(message).matches()) {
                split.start(now, tick);
            }
        }
    }

    private static void announceLag(double seconds) {
        String lost = lagText(seconds);
        if ("client".equals(ConfigManager.get().lagAnnounceMode)) {
            Scheduler.schedule(ANNOUNCE_DELAY_TICKS, () -> ChatUtil.raw(
                    Component.literal("[QZA] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                            .append(Component.literal("Time Lost to Lag: ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(lost).withStyle(ChatFormatting.GREEN))));
        } else {
            String text = "pc [QZA] Time Lost to Lag: " + lost;
            Scheduler.schedule(ANNOUNCE_DELAY_TICKS, () -> ChatUtil.sendCommand(text));
        }
    }

    static String lagText(double seconds) {
        long hundredths = Math.round(Math.max(0.0, seconds) * 100.0);
        long minutes = hundredths / 6000L;
        long rest = hundredths % 6000L;
        String secs = String.format(Locale.ROOT, "%d.%02d Seconds", rest / 100L, rest % 100L);
        if (minutes == 0L) {
            return secs;
        }
        return minutes + (minutes == 1L ? " Minute " : " Minutes ") + secs;
    }

    public static void reset() {
        runOver = false;
        for (Split split : ALL) {
            split.reset();
        }
    }

    public static void renderHud(GuiGraphicsExtractor graphics, Font font) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.splitTimersEnabled || !DungeonState.inDungeon() || DungeonState.catacombsFloor() != 7) {
            return;
        }
        draw(graphics, font, lines(cfg), cfg.splitTimersX, cfg.splitTimersY, cfg.splitTimersScale);
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, List<String> lines,
                            double fx, double fy, double scaleSetting) {
        float scale = NotificationBox.clampScale(scaleSetting);
        int[] size = size(font, lines, scale);
        int[] pos = NotificationBox.topLeft(fx, fy, graphics.guiWidth(), graphics.guiHeight(), size[0], size[1]);

        graphics.pose().pushMatrix();
        graphics.pose().translate(pos[0], pos[1]);
        graphics.pose().scale(scale, scale);
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), 0, i * (font.lineHeight + LINE_GAP), 0xFFFFFFFF);
        }
        graphics.pose().popMatrix();
    }

    public static int[] size(Font font, List<String> lines, float scale) {
        int widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, font.width(line));
        }
        int height = lines.isEmpty() ? 0 : (lines.size() * (font.lineHeight + LINE_GAP)) - LINE_GAP;
        return new int[]{Math.round(widest * scale), Math.round(height * scale)};
    }

    private static List<String> lines(QZAConfig cfg) {
        List<String> out = new ArrayList<>();
        for (Split split : ALL) {
            if (!split.started() || (split == BOSS_ENTRY_SPLIT && !cfg.splitTimersBossEntry)) {
                continue;
            }
            out.add(row(split.name, split.millis(), split.ticks(), cfg.splitTimersTickTime));
        }
        return out;
    }

    public static List<String> preview() {
        QZAConfig cfg = ConfigManager.get();
        boolean ticks = cfg.splitTimersTickTime;
        List<String> out = new ArrayList<>();
        out.add(row(BLOOD_OPEN_SPLIT.name, 21_350, 425, ticks));
        out.add(row(BLOOD_CLEAR_SPLIT.name, 78_900, 1_570, ticks));
        out.add(row(PORTAL_SPLIT.name, 7_150, 140, ticks));
        if (cfg.splitTimersBossEntry) {
            out.add(row(BOSS_ENTRY_SPLIT.name, 107_400, 2_135, ticks));
        }
        out.add(row(MAXOR_SPLIT.name, 31_200, 620, ticks));
        out.add(row(STORM_SPLIT.name, 44_650, 885, ticks));
        out.add(row(TERMINALS_SPLIT.name, 52_300, 1_040, ticks));
        out.add(row(GOLDOR_SPLIT.name, 18_750, 372, ticks));
        out.add(row(NECRON_SPLIT.name, 29_400, 584, ticks));
        out.add(row(DRAGONS_SPLIT.name, 61_250, 1_216, ticks));
        out.add(row(TOTAL_SPLIT.name, 344_950, 6_852, ticks));
        return out;
    }

    private static String row(String name, long millis, long ticks, boolean showTicks) {
        String text = name + "§f: " + format(millis);
        if (showTicks) {
            text += " §8(§7" + format(ticks * 50L) + "§8)";
        }
        return text;
    }

    static String format(long millis) {
        if (millis <= 0) {
            return "0s";
        }
        long hours = millis / 3_600_000L;
        long minutes = (millis % 3_600_000L) / 60_000L;
        double seconds = (millis % 60_000L) / 1000.0;
        StringBuilder out = new StringBuilder();
        if (hours > 0) {
            out.append(hours).append("h ");
        }
        if (minutes > 0) {
            out.append(minutes).append("m ");
        }
        return out.append(String.format(Locale.ROOT, "%.2f", seconds)).append('s').toString();
    }

    public static void resetPlacement() {
        QZAConfig defaults = new QZAConfig();
        QZAConfig cfg = ConfigManager.get();
        cfg.splitTimersX = defaults.splitTimersX;
        cfg.splitTimersY = defaults.splitTimersY;
        cfg.splitTimersScale = defaults.splitTimersScale;
    }

    private static final class Split {
        final String name;
        final Pattern start;
        final Pattern end;
        long startMs = -1L;
        long endMs = -1L;
        long startTick;
        long endTick;

        Split(String name, Pattern start, Pattern end) {
            this.name = name;
            this.start = start;
            this.end = end;
        }

        boolean started() {
            return startMs >= 0L;
        }

        boolean running() {
            return started() && endMs < 0L;
        }

        void start(long now, long tick) {
            startMs = now;
            startTick = tick;
        }

        void end(long now, long tick) {
            endMs = now;
            endTick = tick;
        }

        void reset() {
            startMs = -1L;
            endMs = -1L;
            startTick = 0L;
            endTick = 0L;
        }

        long millis() {
            if (!started()) {
                return 0L;
            }
            return (endMs >= 0L ? endMs : System.currentTimeMillis()) - startMs;
        }

        long ticks() {
            if (!started()) {
                return 0L;
            }
            return Math.max(0L, (endMs >= 0L ? endTick : ServerTickClock.ticks()) - startTick);
        }
    }
}
