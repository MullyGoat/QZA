package com.qza.chat;

import com.qza.config.ConfigManager;
import com.qza.util.IgnUtil;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChannelHistory {
    public static final String EVERYTHING = ChannelParser.EVERYTHING;
    public static final String ALL = ChannelParser.ALL;
    public static final String PARTY = ChannelParser.PARTY;
    public static final String GUILD = ChannelParser.GUILD;
    public static final String COOP = ChannelParser.COOP;

    private static final int MAX_MESSAGES = 300;

    public static final int MAX_MESSAGES_UNLIMITED = 20_000;

    private static final Map<String, List<ChatMessage>> logs = new LinkedHashMap<>();

    private static final Map<String, Integer> revisions = new LinkedHashMap<>();

    private static final int NO_BLOCK = 0;

    private static final int PARTY_LIST = 1;

    private static final int PLAYER_STATS = 2;

    private static final int MAX_LIST_ROWS = 12;

    private static final int MAX_STATS_ROWS = 16;

    private static Component pendingDivider;

    private static String pendingDividerText;

    private static int blockKind = NO_BLOCK;

    private static int blockRows = -1;

    private ChannelHistory() {
    }

    public static void onChatMessage(Component rich, String plain) {
        if (!ConfigManager.get().qzaChatEnabled) {
            return;
        }
        ChannelParser.Line line = ChannelParser.parse(plain);
        if (line == null) {
            if (capturePartyBlock(rich, plain)) {
                return;
            }
            if (ChannelParser.isPartyEvent(plain)) {
                note(PARTY, rich, plain);
            }
            return;
        }
        boolean own = isOwn(plain, line.speaker());
        if (!ALL.equals(line.channel())) {
            record(line.channel(), rich, plain, line.speaker(), own);
        }
        record(ALL, rich, plain, line.speaker(), own);

        if (ChatFocus.isSelf(line.speaker())) {
            ChatFocus.sent(line.channel());
        } else {
            ChatFocus.received(line.channel());
        }
    }

    private static boolean capturePartyBlock(Component rich, String plain) {
        String message = IgnUtil.stripCodes(plain).trim();

        int opened = NO_BLOCK;
        if (ChannelParser.isPartyListHeader(message)) {
            opened = PARTY_LIST;
        } else if (ChannelParser.isStatsHeader(message)) {
            opened = PLAYER_STATS;
        }

        if (opened != NO_BLOCK) {
            if (pendingDividerText != null) {
                note(PARTY, pendingDivider, pendingDividerText);
                forgetDivider();
            }
            note(PARTY, rich, plain);
            blockKind = opened;
            blockRows = 0;
            return true;
        }

        if (blockRows >= 0) {
            if (ChannelParser.isDivider(message)) {
                note(PARTY, rich, plain);
                endBlock();
                return true;
            }
            boolean belongs = blockKind == PARTY_LIST
                    ? ChannelParser.isPartyListRow(message)
                    : !message.isEmpty();
            int limit = blockKind == PARTY_LIST ? MAX_LIST_ROWS : MAX_STATS_ROWS;
            if (belongs && ++blockRows <= limit) {
                note(PARTY, rich, plain);
                return true;
            }
            endBlock();
            return false;
        }

        if (ChannelParser.isDivider(message)) {
            pendingDivider = rich;
            pendingDividerText = plain;
            return false;
        }

        forgetDivider();
        return false;
    }

    private static void endBlock() {
        blockKind = NO_BLOCK;
        blockRows = -1;
    }

    private static void forgetDivider() {
        pendingDivider = null;
        pendingDividerText = null;
    }

    public static void onAnyChatLine(Component rich, String plain) {
        if (!ConfigManager.get().qzaChatEnabled || plain == null || plain.isBlank()) {
            return;
        }
        String speaker = ChannelParser.speakerAnywhere(plain);
        record(EVERYTHING, rich, plain, speaker, isOwn(plain, speaker));
    }

    public static boolean isOwn(String plain, String speaker) {
        String text = IgnUtil.stripCodes(plain == null ? "" : plain).trim();
        if (text.startsWith("To ")) {
            return true;
        }
        if (text.startsWith("From ")) {
            return false;
        }
        return ChatFocus.isSelf(speaker);
    }

    public static void record(String channel, Component rich, String text,
                              String speaker, boolean outgoing) {
        List<ChatMessage> log = logs.computeIfAbsent(channel, key -> new ArrayList<>());
        log.add(new ChatMessage(rich, text, System.currentTimeMillis(), speaker, outgoing));
        revisions.merge(channel, 1, Integer::sum);

        int cap = cap();
        if (log.size() > cap) {
            log.subList(0, log.size() - cap).clear();
        }
    }

    private static int cap() {
        return ConfigManager.get().chatUnlimitedHistory
                ? MAX_MESSAGES_UNLIMITED : MAX_MESSAGES;
    }

    public static int revision(String channel) {
        Integer at = revisions.get(channel);
        return at == null ? 0 : at;
    }

    public static void note(String channel, Component rich, String text) {
        record(channel, rich, text, null, false);
    }

    public static List<ChatMessage> get(String channel) {
        List<ChatMessage> log = logs.get(channel);
        return log == null ? List.of() : log;
    }

    public static void clear() {
        logs.clear();
        endBlock();
        forgetDivider();
    }

    public static String inviteCommand(String channel) {
        return switch (channel) {
            case PARTY -> "party invite";
            case GUILD -> "guild invite";
            case COOP -> "coopadd";
            default -> null;
        };
    }

    public static String command(String channel) {
        return switch (channel) {
            case PARTY -> "pc";
            case GUILD -> "gc";
            case COOP -> "cc";
            default -> "ac";
        };
    }
}
