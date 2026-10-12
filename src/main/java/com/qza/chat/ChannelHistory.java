package com.qza.chat;

import com.qza.config.ConfigManager;
import com.qza.util.IgnUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

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

    private static final int MAX_CLIENT_STATS_ROWS = 30;

    private static Component pendingDivider;

    private static String pendingDividerText;

    private static int blockKind = NO_BLOCK;

    private static int blockRows = -1;

    private static int clientStatsRows = -1;

    private static final Pattern CLEAR_INFO = Pattern.compile(
            "^(?:\\[[^\\]]{1,12}]\\s*)?(?:[A-Za-z]{2,12} » ?)?\\w{1,16}: \\d+(?:-\\d+)? Rooms? \\| \\d+ Secrets?"
                    + "(?: \\| \\d+ Deaths?)?$");

    private static final Pattern MOD_PREFIX = Pattern.compile("^(?:\\[[^\\]]{1,12}]|[A-Za-z]{2,12} »)$");

    private static final int CLEAR_INFO_QUIET_TICKS = 3;

    private static final int MAX_CLEAR_INFO_ROWS = 8;

    private static final List<Component> clearInfo = new ArrayList<>();

    private static int clearInfoQuiet;

    private ChannelHistory() {
    }

    public static void tick() {
        if (clearInfo.isEmpty() || ++clearInfoQuiet < CLEAR_INFO_QUIET_TICKS) {
            return;
        }
        List<Component> rows = new ArrayList<>();
        rows.add(Component.literal("Clear Info").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        rows.addAll(clearInfo.subList(0, Math.min(clearInfo.size(), MAX_CLEAR_INFO_ROWS)));
        clearInfo.clear();

        MutableComponent rich = Component.empty();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                rich.append("\n");
                text.append('\n');
            }
            rich.append(rows.get(i));
            text.append(rows.get(i).getString());
        }
        ChatMessage message = record(PARTY, rich, text.toString(), null, false);
        message.rows = rows;
        message.centred = new boolean[rows.size()];
    }

    private static Component withoutPrefix(Component rich) {
        StringBuilder own = new StringBuilder();
        rich.getContents().visit(part -> {
            own.append(part);
            return Optional.empty();
        });
        List<Component> siblings = rich.getSiblings();
        if (siblings.isEmpty() || !MOD_PREFIX.matcher(IgnUtil.stripCodes(own.toString()).trim()).matches()) {
            return rich;
        }
        MutableComponent out = Component.empty();
        for (int i = 0; i < siblings.size(); i++) {
            out.append(i == 0 ? trimStart(siblings.get(i)) : siblings.get(i));
        }
        return out;
    }

    private static Component trimStart(Component part) {
        if (!(part.getContents() instanceof PlainTextContents plain)) {
            return part;
        }
        MutableComponent out = Component.literal(plain.text().stripLeading()).setStyle(part.getStyle());
        for (Component sibling : part.getSiblings()) {
            out.append(sibling);
        }
        return out;
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
                if (!ChatCleaner.hidesSeparators()) {
                    note(PARTY, pendingDivider, pendingDividerText);
                }
                forgetDivider();
            }
            note(PARTY, rich, plain);
            blockKind = opened;
            blockRows = 0;
            return true;
        }

        if (blockRows >= 0) {
            if (ChannelParser.isDivider(message)) {
                if (!ChatCleaner.hidesSeparators()) {
                    note(PARTY, rich, plain);
                }
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

    public static void onAnyChatLine(Component rich, String plain, boolean clientSide) {
        onAnyChatLine(rich, plain, clientSide, null, null);
    }

    public static void onAnyChatLine(Component rich, String plain, boolean clientSide,
                                     List<Component> rows, boolean[] centred) {
        if (!ConfigManager.get().qzaChatEnabled || plain == null) {
            return;
        }
        if (clientSide) {
            captureClientStats(rich, plain);
        }
        if (IgnUtil.stripCodes(plain).isBlank()) {
            return;
        }
        String speaker = ChannelParser.speakerAnywhere(plain);
        ChatMessage message = record(EVERYTHING, rich, plain, speaker, isOwn(plain, speaker));
        message.rows = rows;
        message.centred = centred;
    }

    private static void captureClientStats(Component rich, String plain) {
        int lineEnd = plain.indexOf('\n');
        if (lineEnd >= 0) {
            if (ChannelParser.isStatsHeader(IgnUtil.stripCodes(plain.substring(0, lineEnd)).trim())) {
                note(PARTY, rich, plain);
                clientStatsRows = -1;
            }
            return;
        }
        String message = IgnUtil.stripCodes(plain).trim();
        if (CLEAR_INFO.matcher(message).matches()) {
            clearInfo.add(withoutPrefix(rich));
            clearInfoQuiet = 0;
            return;
        }
        if (ChannelParser.isStatsHeader(message)) {
            note(PARTY, rich, plain);
            clientStatsRows = 0;
            return;
        }
        if (clientStatsRows < 0) {
            return;
        }
        if (ChannelParser.isDivider(message)) {
            if (!ChatCleaner.hidesSeparators()) {
                note(PARTY, rich, plain);
            }
            clientStatsRows = -1;
            return;
        }
        if (++clientStatsRows > MAX_CLIENT_STATS_ROWS) {
            clientStatsRows = -1;
            return;
        }
        if (!message.isEmpty()) {
            note(PARTY, rich, plain);
        }
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

    public static ChatMessage record(String channel, Component rich, String text,
                                     String speaker, boolean outgoing) {
        List<ChatMessage> log = logs.computeIfAbsent(channel, key -> new ArrayList<>());
        ChatMessage message = new ChatMessage(rich, text, System.currentTimeMillis(), speaker, outgoing);
        log.add(message);
        revisions.merge(channel, 1, Integer::sum);

        int cap = cap();
        if (log.size() > cap) {
            log.subList(0, log.size() - cap).clear();
        }
        return message;
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
        clientStatsRows = -1;
        clearInfo.clear();
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
