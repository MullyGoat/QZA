package com.qza.chat;

import com.qza.config.ConfigManager;
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

    private static final Map<String, List<ChatMessage>> logs = new LinkedHashMap<>();

    private ChannelHistory() {
    }

    public static void onChatMessage(Component rich, String plain) {
        if (!ConfigManager.get().qzaChatEnabled) {
            return;
        }
        ChannelParser.Line line = ChannelParser.parse(plain);
        if (line == null) {
            return;
        }
        if (!ALL.equals(line.channel())) {
            record(line.channel(), rich, plain, line.speaker());
        }
        record(ALL, rich, plain, line.speaker());

        if (ChatFocus.isSelf(line.speaker())) {
            ChatFocus.sent(line.channel());
        } else {
            ChatFocus.received(line.channel());
        }
    }

    public static void onAnyChatLine(Component rich, String plain) {
        if (!ConfigManager.get().qzaChatEnabled || plain == null || plain.isBlank()) {
            return;
        }
        record(EVERYTHING, rich, plain, ChannelParser.speakerAnywhere(plain));
    }

    public static void record(String channel, Component rich, String text, String speaker) {
        List<ChatMessage> log = logs.computeIfAbsent(channel, key -> new ArrayList<>());
        log.add(new ChatMessage(rich, text, System.currentTimeMillis(), speaker));
        while (log.size() > MAX_MESSAGES) {
            log.remove(0);
        }
    }

    public static List<ChatMessage> get(String channel) {
        List<ChatMessage> log = logs.get(channel);
        return log == null ? List.of() : log;
    }

    public static void clear() {
        logs.clear();
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
