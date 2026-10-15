package com.qza.dungeon;

import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.util.ChatUtil;
import com.qza.util.DungeonState;
import com.qza.util.IgnUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
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
        Matcher progress = PROGRESS.matcher(message);
        if (!progress.matches()) {
            return;
        }
        onProgress(progress.group(1), progress.group(2),
                Integer.parseInt(progress.group(3)), Integer.parseInt(progress.group(4)));
    }

    static void onProgress(String name, String type, int count, int total) {
        boolean fresh = lastTotal > 0 && (total != lastTotal || count < lastCount);
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

        switch (type) {
            case "terminal" -> terminals[section]++;
            case "lever" -> levers[section]++;
            default -> devices[deviceSection(name)] = true;
        }

        if (done(section) >= totals[section]) {
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
        Mc.showTitle(Component.literal(text).withStyle(ChatFormatting.GREEN), 0, 30, 10);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f));
        if (cfg.terminalDoneParty) {
            ChatUtil.sendCommand("pc " + text);
        }
    }

    public static String doneText(int s) {
        return "S" + (s + 1) + " Done!";
    }

    private static int deviceSection(String name) {
        Vec3 pos = positionOf(name);
        if (pos != null) {
            int nearest = -1;
            double best = DEVICE_RANGE_SQ;
            for (int i = 0; i < SECTIONS; i++) {
                double distance = pos.distanceToSqr(DEVICES[i]);
                if (!devices[i] && distance < best) {
                    nearest = i;
                    best = distance;
                }
            }
            if (nearest >= 0) {
                return nearest;
            }
        }
        if (section == 0) {
            String dungeonClass = LeapNotification.classOf(name);
            if ("Berserk".equals(dungeonClass) && !devices[I4]) {
                return I4;
            }
            if (!devices[0]) {
                return 0;
            }
            return devices[I4] ? 0 : I4;
        }
        if (!devices[section]) {
            return section;
        }
        for (int i = section + 1; i < SECTIONS; i++) {
            if (!devices[i]) {
                return i;
            }
        }
        return section;
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

    private static int done(int s) {
        return terminals[s] + levers[s] + (devices[s] ? 1 : 0);
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
