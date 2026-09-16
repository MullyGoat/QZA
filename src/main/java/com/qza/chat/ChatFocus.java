package com.qza.chat;

import com.qza.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.util.List;
import java.util.Locale;

public final class ChatFocus {
    public static final String TAB_DM = "dm";
    public static final String MODE_RECENT_TAB = "tab";
    public static final String MODE_RECEIVED = "received";
    public static final String MODE_SENT = "sent";

    public static final List<String> OPTIONS = List.of(
            MODE_RECENT_TAB,
            TAB_DM,
            ChannelParser.ALL,
            ChannelParser.PARTY,
            ChannelParser.GUILD,
            ChannelParser.COOP,
            MODE_RECEIVED,
            MODE_SENT);

    private static String lastReceived;
    private static String lastSent;

    private ChatFocus() {
    }

    public static void received(String tab) {
        lastReceived = tab;
    }

    public static void sent(String tab) {
        lastSent = tab;
    }

    public static String resolve(String currentTab) {
        String mode = ConfigManager.get().chatDefaultTab;
        if (mode == null || mode.isBlank()) {
            return currentTab;
        }
        return switch (mode) {
            case MODE_RECENT_TAB -> currentTab;
            case MODE_RECEIVED -> lastReceived == null ? currentTab : lastReceived;
            case MODE_SENT -> lastSent == null ? currentTab : lastSent;
            default -> mode;
        };
    }

    public static String label(String raw) {
        return switch (raw) {
            case TAB_DM -> "DMs";
            case ChannelParser.ALL -> "All Chat";
            case ChannelParser.PARTY -> "Party Chat";
            case ChannelParser.GUILD -> "Guild Chat";
            case ChannelParser.COOP -> "Coop Chat";
            case MODE_RECEIVED -> "Most Recently Received";
            case MODE_SENT -> "Most Recently Sent";
            default -> "Most Recent Tab";
        };
    }

    public static boolean isSelf(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        User user = Minecraft.getInstance().getUser();
        if (user == null || user.getName() == null) {
            return false;
        }
        return user.getName().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT));
    }
}
