package com.qza.dungeon;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.util.ChatUtil;
import com.qza.util.DungeonState;
import com.qza.util.IgnUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TerminalTracker {
    private static final Pattern START = Pattern.compile(
            "^\\[BOSS] Goldor: Who dares trespass into my domain\\?$"
                    + "|^\\[BOSS] Storm: I should have known that I stood no chance\\.$");
    private static final Pattern CORE = Pattern.compile("^The Core entrance is opening!$");
    private static final Pattern GATE = Pattern.compile("^The gate has been destroyed!$");
    public static final String GATE_TEXT = "Gate Destroyed";
    private static final Pattern PROGRESS = Pattern.compile(
            "^(\\w{1,16}) (?:activated|completed) a (terminal|device|lever)! \\((\\d+)/(\\d+)\\)(?:\\s.*)?$");

    private static final int SECTIONS = 4;
    private static final int LAST = SECTIONS - 1;
    private static final int[] TOTALS = {7, 8, 7, 7};
    private static final int LEVERS = 2;
    private static final int I4 = 3;
    private static final Vec3[] DEVICES = {
            new Vec3(110.5, 121.5, 91.5),
            new Vec3(60.5, 132.5, 143.5),
            new Vec3(0.5, 120.5, 77.5),
            new Vec3(63.5, 127.5, 35.5)};
    private static final double DEVICE_RANGE_SQ = 12.0 * 12.0;
    public static final double MIN_ALERT_SECONDS = 0.5;
    public static final double MAX_ALERT_SECONDS = 5.0;
    private static final long ALERT_FADE_MS = 500L;

    private static boolean active;
    private static int section;
    private static final int[] terminals = new int[SECTIONS];
    private static final int[] levers = new int[SECTIONS];
    private static final boolean[] devices = new boolean[SECTIONS];
    private static final boolean[] finished = new boolean[SECTIONS];
    private static final int[] totals = TOTALS.clone();
    private static int lastCount;
    private static int lastTotal;
    private static boolean movedByCount;
    private static final Map<String, Vec3> lastSeen = new HashMap<>();
    private static final Alert DONE_ALERT = new Alert(0x55FF55, 4.0f, 48);
    private static final Alert GATE_ALERT = new Alert(0xFF5555, 3.0f, 88);

    private static final class Alert {
        private final int colour;
        private final float scale;
        private final int bottomAboveCentre;
        private String text;
        private long until;
        private long fade;

        Alert(int colour, float scale, int bottomAboveCentre) {
            this.colour = colour;
            this.scale = scale;
            this.bottomAboveCentre = bottomAboveCentre;
        }

        void show(String shown, double seconds) {
            long shownFor = Math.round(Math.max(MIN_ALERT_SECONDS, Math.min(MAX_ALERT_SECONDS, seconds)) * 1000.0);
            text = shown;
            until = System.currentTimeMillis() + shownFor;
            fade = Math.min(ALERT_FADE_MS, shownFor / 2);
        }

        String showing() {
            return text != null && System.currentTimeMillis() < until ? text : null;
        }

        void render(GuiGraphicsExtractor graphics, Font font) {
            String shown = showing();
            if (shown == null) {
                return;
            }
            long left = until - System.currentTimeMillis();
            float alpha = left >= fade ? 1.0f : Math.max(0.0f, left / (float) fade);
            int argb = (Math.max(4, Math.round(alpha * 255)) << 24) | colour;
            graphics.pose().pushMatrix();
            graphics.pose().translate(graphics.guiWidth() / 2.0f, (graphics.guiHeight() / 2.0f) - bottomAboveCentre);
            graphics.pose().scale(scale, scale);
            graphics.text(font, shown, -font.width(shown) / 2, -font.lineHeight, argb);
            graphics.pose().popMatrix();
        }
    }

    private TerminalTracker() {
    }

    public static void onChatMessage(String raw) {
        if (raw == null) {
            return;
        }
        String message = IgnUtil.stripCodes(raw).trim();
        if (START.matcher(message).matches()) {
            if (!active && DungeonState.inDungeon() && DungeonState.catacombsFloor() == 7) {
                start();
            }
            return;
        }
        if (!active) {
            return;
        }
        if (CORE.matcher(message).matches()) {
            reset();
            return;
        }
        if (GATE.matcher(message).matches()) {
            QZAConfig cfg = ConfigManager.get();
            if (cfg.terminalTrackerEnabled && cfg.terminalGateAlert) {
                GATE_ALERT.show(GATE_TEXT, cfg.terminalDoneSeconds);
            }
            return;
        }
        Matcher progress = PROGRESS.matcher(message);
        if (!progress.matches()) {
            return;
        }
        onProgress(progress.group(1), progress.group(2),
                Integer.parseInt(progress.group(3)), Integer.parseInt(progress.group(4)));
    }

    static void onProgress(String name, String type, int count, int total) {
        boolean fresh = lastTotal > 0 && (total != lastTotal || count < lastCount);
        boolean repeated = lastTotal > 0 && total == lastTotal && count == lastCount;
        lastCount = count;
        lastTotal = total;
        if (fresh) {
            if (movedByCount) {
                movedByCount = false;
            } else if (section < LAST) {
                sectionDone(section);
                section++;
            }
        }
        if (total > 0 && total <= 9) {
            totals[section] = total;
        }

        if (repeated) {
            if ("device".equals(type)) {
                int other = otherDevice(name);
                if (other >= 0) {
                    devices[other] = true;
                }
            }
            return;
        }
        switch (type) {
            case "terminal" -> terminals[section]++;
            case "lever" -> levers[section]++;
            default -> devices[section] = true;
        }

        if (count >= total) {
            sectionDone(section);
            if (section < LAST) {
                section++;
                movedByCount = true;
            }
        }
    }

    private static void sectionDone(int s) {
        if (finished[s]) {
            return;
        }
        finished[s] = true;
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.terminalTrackerEnabled || !cfg.terminalDoneAlert) {
            return;
        }
        String text = doneText(s);
        DONE_ALERT.show(text, cfg.terminalDoneSeconds);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f));
        if (cfg.terminalDoneParty) {
            ChatUtil.sendCommand("pc " + text);
        }
    }

    public static String doneText(int s) {
        return "S" + (s + 1) + " Done!";
    }

    private static int otherDevice(String name) {
        Vec3 pos = positionOf(name);
        if (pos != null) {
            int nearest = -1;
            double best = DEVICE_RANGE_SQ;
            for (int i = 0; i < SECTIONS; i++) {
                double distance = pos.distanceToSqr(DEVICES[i]);
                if (i != section && !devices[i] && distance < best) {
                    nearest = i;
                    best = distance;
                }
            }
            if (nearest >= 0) {
                return nearest;
            }
        }
        if (section < I4 && !devices[I4]) {
            return I4;
        }
        for (int i = section + 1; i < SECTIONS; i++) {
            if (!devices[i]) {
                return i;
            }
        }
        return -1;
    }

    private static Vec3 positionOf(String name) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) {
            for (AbstractClientPlayer player : client.level.players()) {
                if (player.getGameProfile().name().equalsIgnoreCase(name)) {
                    return player.position();
                }
            }
        }
        return lastSeen.get(name.toLowerCase(Locale.ROOT));
    }

    public static void tick(Minecraft client) {
        if (!active || client.level == null) {
            return;
        }
        for (AbstractClientPlayer player : client.level.players()) {
            lastSeen.put(player.getGameProfile().name().toLowerCase(Locale.ROOT), player.position());
        }
    }

    private static void start() {
        reset();
        active = true;
    }

    public static void reset() {
        active = false;
        section = 0;
        for (int i = 0; i < SECTIONS; i++) {
            terminals[i] = 0;
            levers[i] = 0;
            devices[i] = false;
            finished[i] = false;
            totals[i] = TOTALS[i];
        }
        lastCount = 0;
        lastTotal = 0;
        movedByCount = false;
        lastSeen.clear();
    }

    public static boolean active() {
        return active;
    }

    public static int section() {
        return section;
    }

    public static String alertShowing() {
        return DONE_ALERT.showing();
    }

    public static String gateShowing() {
        return GATE_ALERT.showing();
    }

    public static void renderAlert(GuiGraphicsExtractor graphics, Font font) {
        DONE_ALERT.render(graphics, font);
        GATE_ALERT.render(graphics, font);
    }

    public static void renderHud(GuiGraphicsExtractor graphics, Font font) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.terminalTrackerEnabled || !active) {
            return;
        }
        SplitTimers.draw(graphics, font, lines(section, terminals, levers, devices, totals),
                cfg.terminalTrackerX, cfg.terminalTrackerY, cfg.terminalTrackerScale);
    }

    public static List<String> preview() {
        if (active) {
            return lines(section, terminals, levers, devices, totals);
        }
        return lines(0, new int[]{2, 0, 0, 0}, new int[]{1, 0, 0, 0},
                new boolean[]{false, false, false, true}, TOTALS);
    }

    private static List<String> lines(int s, int[] terms, int[] levs, boolean[] devs, int[] sums) {
        int total = sums[s];
        int terminalTotal = Math.max(0, total - LEVERS - 1);
        int count = Math.min(total, terms[s] + levs[s] + (devs[s] ? 1 : 0));
        List<String> out = new ArrayList<>();
        out.add("§6Section " + (s + 1) + "§7: " + colour(count, total) + count + "§7/" + total);
        out.add("§7Terminals: " + colour(terms[s], terminalTotal) + Math.min(terms[s], terminalTotal)
                + "§7/" + terminalTotal);
        out.add("§7Levers: " + colour(levs[s], LEVERS) + Math.min(levs[s], LEVERS) + "§7/" + LEVERS);
        out.add("§7Device: " + (devs[s] ? "§a1" : "§f0") + "§7/1");
        if (s < I4 && devs[I4]) {
            out.add("§7S4 Device: §aDone");
        }
        return out;
    }

    private static String colour(int count, int total) {
        if (count >= total) {
            return "§a";
        }
        return count > 0 ? "§e" : "§f";
    }

    public static void resetPlacement() {
        QZAConfig defaults = new QZAConfig();
        QZAConfig cfg = ConfigManager.get();
        cfg.terminalTrackerX = defaults.terminalTrackerX;
        cfg.terminalTrackerY = defaults.terminalTrackerY;
        cfg.terminalTrackerScale = defaults.terminalTrackerScale;
    }
}
