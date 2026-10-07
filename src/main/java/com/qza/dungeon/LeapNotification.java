package com.qza.dungeon;

import com.qza.chat.ChatFocus;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.notify.NotificationBox;
import com.qza.util.DungeonState;
import com.qza.util.IgnUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LeapNotification {
    public static final double MIN_DURATION = 1.0;
    public static final double MAX_DURATION = 5.0;

    private static final String SUFFIX = " Leaped to You!";

    private static final Pattern PARTY = Pattern.compile("^Party > [^:]*?(\\w{1,16}): (.+)$");
    private static final Pattern LEAP = Pattern.compile(
            "^(?:\\[[^]]*] )?(?:i )?(?:leaped|leapt|leaping) to +(\\w{1,16})!?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAB_CLASS = Pattern.compile(
            "^\\[\\d+] (\\w{1,16})(?: [^(]*)? \\((Archer|Berserk|Healer|Mage|Tank)\\b");
    private static final Pattern[] BOSS_ENTRY = {
            Pattern.compile("^\\[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable\\.$"),
            Pattern.compile("^\\[BOSS] Scarf: This is where the journey ends for you, Adventurers\\.$"),
            Pattern.compile("^\\[BOSS] The Professor: I was burdened with terrible news recently\\.\\.\\.$"),
            Pattern.compile("^\\[BOSS] Thorn: Welcome Adventurers! I am Thorn, the Spirit! "
                    + "And host of the Vegan Trials!$"),
            Pattern.compile("^\\[BOSS] Livid: Welcome, you've arrived right on time\\. "
                    + "I am Livid, the Master of Shadows\\.$"),
            Pattern.compile("^\\[BOSS] Sadan: So you made it all the way here\\.\\.\\. "
                    + "Now you wish to defy me\\? Sadan\\?!$"),
            Pattern.compile("^\\[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!$")};

    private static boolean inBoss;
    private static String shown;
    private static long shownAt;

    private LeapNotification() {
    }

    public static void onChatMessage(String raw) {
        if (raw == null) {
            return;
        }
        String message = IgnUtil.stripCodes(raw).trim();
        for (Pattern entry : BOSS_ENTRY) {
            if (entry.matcher(message).matches()) {
                inBoss = true;
                return;
            }
        }

        QZAConfig cfg = ConfigManager.get();
        if (!cfg.leapNotifyEnabled) {
            return;
        }
        Matcher party = PARTY.matcher(message);
        if (!party.matches()) {
            return;
        }
        String leaper = party.group(1);
        Matcher leap = LEAP.matcher(party.group(2).trim());
        if (!leap.matches() || !ChatFocus.isSelf(leap.group(1)) || ChatFocus.isSelf(leaper)) {
            return;
        }
        if (!DungeonState.inDungeon() || (cfg.leapNotifyBossOnly && !inBoss)) {
            return;
        }

        shown = text(leaper, cfg.leapNotifyClass ? classOf(leaper) : null);
        shownAt = System.currentTimeMillis();
    }

    private static String text(String leaper, String dungeonClass) {
        if (dungeonClass == null) {
            return leaper + SUFFIX;
        }
        return switch (dungeonClass) {
            case "Archer" -> "§6Arch";
            case "Berserk" -> "§4Bers";
            case "Healer" -> "§dHeal";
            case "Mage" -> "§bMage";
            default -> "§2Tank";
        } + "§f" + SUFFIX;
    }

    private static String classOf(String ign) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return null;
        }
        for (PlayerInfo info : connection.getListedOnlinePlayers()) {
            Component display = info.getTabListDisplayName();
            if (display == null) {
                continue;
            }
            Matcher matcher = TAB_CLASS.matcher(IgnUtil.stripCodes(display.getString()).trim());
            if (matcher.find() && matcher.group(1).equalsIgnoreCase(ign)) {
                return matcher.group(2);
            }
        }
        return null;
    }

    public static List<String> preview() {
        return List.of(ConfigManager.get().leapNotifyClass ? text("Player", "Berserk") : "Player" + SUFFIX);
    }

    public static void renderHud(GuiGraphicsExtractor graphics, Font font) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.leapNotifyEnabled || shown == null) {
            return;
        }
        double seconds = Math.max(MIN_DURATION, Math.min(MAX_DURATION, cfg.leapNotifyDuration));
        if (System.currentTimeMillis() - shownAt > seconds * 1000.0) {
            shown = null;
            return;
        }
        float scale = NotificationBox.clampScale(cfg.leapNotifyScale);
        int w = Math.round(font.width(shown) * scale);
        int h = Math.round(font.lineHeight * scale);
        int[] pos = NotificationBox.topLeft(cfg.leapNotifyX, cfg.leapNotifyY,
                graphics.guiWidth(), graphics.guiHeight(), w, h);
        NotificationBox.drawPlain(graphics, font, shown, pos[0], pos[1], scale, 0xFFFFFFFF);
    }

    public static void resetPlacement() {
        QZAConfig defaults = new QZAConfig();
        QZAConfig cfg = ConfigManager.get();
        cfg.leapNotifyX = defaults.leapNotifyX;
        cfg.leapNotifyY = defaults.leapNotifyY;
        cfg.leapNotifyScale = defaults.leapNotifyScale;
    }

    public static void reset() {
        inBoss = false;
        shown = null;
    }
}
