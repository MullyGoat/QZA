package com.qza.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.qza.QZA;
import com.qza.config.ConfigManager;
import com.qza.util.ChatUtil;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ChatHistory {
    public static final String MODE_FOREVER = "forever";
    public static final String MODE_SESSION = "session";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<List<ChatConversation>>() {
    }.getType();

    private static final int MAX_MESSAGES = 500;

    private static final Map<String, ChatConversation> conversations = new LinkedHashMap<>();

    private ChatHistory() {
    }

    public static Path file() {
        return ConfigManager.qzaDir().resolve("chat").resolve("history.json");
    }

    public static boolean persists() {
        return !MODE_SESSION.equals(ConfigManager.get().chatHistoryMode);
    }

    public static void load() {
        conversations.clear();

        if (!persists()) {
            deleteFile();
            return;
        }

        Path path = file();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<ChatConversation> loaded = GSON.fromJson(reader, TYPE);
            if (loaded == null) {
                return;
            }
            for (ChatConversation conversation : loaded) {
                if (conversation == null || conversation.name == null || conversation.name.isBlank()) {
                    continue;
                }
                if (conversation.messages == null) {
                    conversation.messages = new ArrayList<>();
                }
                conversation.unread = 0;
                conversations.put(conversation.key(), conversation);
            }
        } catch (Exception e) {
            QZA.LOGGER.error("Failed to read chat history", e);
        }
    }

    public static void save() {
        if (!persists()) {
            return;
        }
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(new ArrayList<>(conversations.values()), writer);
            }
        } catch (IOException e) {
            QZA.LOGGER.error("Failed to write chat history", e);
        }
    }

    public static void clear() {
        conversations.clear();
        deleteFile();
    }

    private static void deleteFile() {
        try {
            Files.deleteIfExists(file());
        } catch (IOException e) {
            QZA.LOGGER.warn("Could not delete chat history", e);
        }
    }

    public static void onChatMessage(String raw) {
        WhisperParser.Whisper whisper = WhisperParser.parse(raw);
        if (whisper == null) {
            return;
        }
        if (ConfigManager.get().qzaChatEnabled) {
            record(whisper.ign(), whisper.outgoing(), whisper.text());
        }
        if (!whisper.outgoing()) {
            ChatNotification.show(whisper.ign(), whisper.text());
        }
    }

    public static ChatConversation start(String ign) {
        String key = ign.toLowerCase(Locale.ROOT);
        ChatConversation conversation = conversations.get(key);
        if (conversation == null) {
            conversation = new ChatConversation(ign);
            conversations.put(key, conversation);
        }
        conversation.lastActivity = System.currentTimeMillis();
        conversation.hidden = false;
        save();
        return conversation;
    }

    public static void record(String ign, boolean outgoing, String text) {
        String key = ign.toLowerCase(Locale.ROOT);
        ChatConversation conversation = conversations.get(key);
        if (conversation == null) {
            conversation = new ChatConversation(ign);
            conversations.put(key, conversation);
        } else {
            conversation.name = ign;
        }

        conversation.messages.add(new ChatMessage(outgoing, text, System.currentTimeMillis()));
        while (conversation.messages.size() > MAX_MESSAGES) {
            conversation.messages.remove(0);
        }
        conversation.lastActivity = System.currentTimeMillis();
        conversation.hidden = false;
        if (!outgoing) {
            conversation.unread++;
        }

        save();
    }

    public static void hide(String ign) {
        ChatConversation conversation = get(ign);
        if (conversation != null) {
            conversation.hidden = true;
            conversation.unread = 0;
            save();
        }
    }

    public static void delete(String ign) {
        if (ign != null && conversations.remove(ign.toLowerCase(Locale.ROOT)) != null) {
            save();
        }
    }

    public static void send(String ign, String text) {
        String trimmed = text == null ? "" : text.trim();
        if (ign == null || ign.isBlank() || trimmed.isEmpty()) {
            return;
        }
        ChatUtil.sendCommand("w " + ign + " " + trimmed);
    }

    public static List<ChatConversation> conversations() {
        List<ChatConversation> list = new ArrayList<>();
        for (ChatConversation conversation : conversations.values()) {
            if (!conversation.hidden) {
                list.add(conversation);
            }
        }
        list.sort(Comparator.comparingLong((ChatConversation c) -> c.lastActivity).reversed());
        return list;
    }

    public static ChatConversation get(String ign) {
        return ign == null ? null : conversations.get(ign.toLowerCase(Locale.ROOT));
    }

    public static void markRead(String ign) {
        ChatConversation conversation = get(ign);
        if (conversation != null && conversation.unread > 0) {
            conversation.unread = 0;
            save();
        }
    }

    public static int unreadTotal() {
        int total = 0;
        for (ChatConversation conversation : conversations.values()) {
            if (!conversation.hidden) {
                total += conversation.unread;
            }
        }
        return total;
    }
}
