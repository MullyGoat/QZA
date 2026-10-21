package com.qza.dungeon;

import com.qza.chat.ChatFocus;
import com.qza.stats.DungeonClass;
import com.qza.util.DungeonState;
import com.qza.util.IgnUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DungeonTeam {
    private static final Pattern TAB_PLAYER = Pattern.compile(
            "^\\[\\d+] (?:\\[[^]]+] )*(\\w{1,16}) .*\\((\\w+)(?: \\w+)?\\)$");
    private static final Pattern DOOR_OPENED = Pattern.compile(
            "^(?:\\[[^]]*?] )?(\\w{1,16}) opened a WITHER door!$");
    private static final Pattern WATCHER = Pattern.compile("^\\[BOSS] The Watcher: .+$");
    private static final int SCAN_TICKS = 10;

    public record Teammate(String name, String dungeonClass, boolean dead) {
    }

    private static final Map<String, String> classes = new HashMap<>();
    private static List<Teammate> team = List.of();
    private static String selfClass;
    private static String doorOpener;
    private static int sinceScan;

    private DungeonTeam() {
    }

    public static void tick() {
        if (++sinceScan < SCAN_TICKS) {
            return;
        }
        sinceScan = 0;
        if (DungeonState.inDungeon()) {
            scan();
        }
    }

    public static void onChatMessage(String raw) {
        if (raw == null) {
            return;
        }
        String message = IgnUtil.stripCodes(raw).trim();
        Matcher door = DOOR_OPENED.matcher(message);
        if (door.matches()) {
            doorOpener = door.group(1);
        } else if (WATCHER.matcher(message).matches()) {
            doorOpener = null;
        }
    }

    public static void scan() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        Map<String, Teammate> found = new LinkedHashMap<>();
        for (PlayerInfo info : connection.getListedOnlinePlayers()) {
            Component display = info.getTabListDisplayName();
            if (display == null) {
                continue;
            }
            Matcher matcher = TAB_PLAYER.matcher(IgnUtil.stripCodes(display.getString()).trim());
            if (!matcher.matches()) {
                continue;
            }
            String name = matcher.group(1);
            String key = name.toLowerCase(Locale.ROOT);
            boolean dead = matcher.group(2).equalsIgnoreCase("DEAD");
            String dungeonClass = dead ? null : DungeonClass.of(matcher.group(2));
            if (dungeonClass != null) {
                classes.put(key, dungeonClass);
            } else {
                dungeonClass = classes.get(key);
            }
            if (ChatFocus.isSelf(name)) {
                selfClass = dungeonClass;
                continue;
            }
            found.putIfAbsent(key, new Teammate(name, dungeonClass, dead));
        }
        team = List.copyOf(new ArrayList<>(found.values()));
    }

    public static List<Teammate> teammates() {
        return team;
    }

    public static String selfClass() {
        return selfClass;
    }

    public static String doorOpener() {
        return doorOpener;
    }

    public static void reset() {
        classes.clear();
        team = List.of();
        selfClass = null;
        doorOpener = null;
        sinceScan = 0;
    }
}
