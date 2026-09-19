package com.qza.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.qza.QZA;
import com.qza.config.ConfigManager;
import com.qza.stats.AutoInvite;
import com.qza.util.ChatUtil;
import com.qza.util.PlayerLookup;
import net.minecraft.network.chat.Component;

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

    public static void onChatMessage(Component rich, String plain) {
        WhisperParser.Whisper whisper = WhisperParser.parse(plain);
        if (whisper == null) {
            return;
        }
        if (ConfigManager.get().qzaChatEnabled) {
            record(whisper.ign(), whisper.outgoing(), whisper.text());
            ChannelHistory.record(ChannelHistory.ALL, rich, plain,
                    whisper.ign(), whisper.outgoing());
        }
        if (whisper.outgoing()) {
            ChatFocus.sent(ChatFocus.TAB_DM);
        } else {
            ChatFocus.received(ChatFocus.TAB_DM);
            ChatNotification.show(whisper.ign(), whisper.text());
            AutoInvite.onWhisper(whisper.ign(), whisper.text());
        }
    }

    public static ChatConversation start(String ign) {
        ChatConversation conversation = resolve(ign);
        conversation.lastActivity = System.currentTimeMillis();
        conversation.hidden = false;
        save();
        return conversation;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public static ChatConversation getByUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        for (ChatConversation conversation : conversations.values()) {
            if (uuid.equalsIgnoreCase(conversation.uuid)) {
                return conversation;
            }
        }
        return null;
    }

    private static void renameTo(ChatConversation conversation, String newName) {
        String previous = conversation.name;
        conversations.remove(key(previous));
        conversation.name = newName;
        conversations.put(key(newName), conversation);
        ChatUtil.info(previous + " is now " + newName + " - chat history kept.");
    }

    private static void merge(ChatConversation keep, ChatConversation drop) {
        keep.messages.addAll(drop.messages);
        keep.messages.sort(Comparator.comparingLong(m -> m.time));
        while (keep.messages.size() > MAX_MESSAGES) {
            keep.messages.remove(0);
        }
        keep.unread += drop.unread;
        keep.lastActivity = Math.max(keep.lastActivity, drop.lastActivity);
        keep.hidden = keep.hidden && drop.hidden;
        if (keep.uuid == null || keep.uuid.isBlank()) {
            keep.uuid = drop.uuid;
        }
    }

    private static ChatConversation resolve(String ign) {
        String uuid = PlayerLookup.uuidFor(ign);
        ChatConversation named = conversations.get(key(ign));
        ChatConversation known = getByUuid(uuid);

        if (known == null) {
            if (named == null) {
                named = new ChatConversation(ign);
                conversations.put(key(ign), named);
            } else {
                named.name = ign;
            }
            if (uuid != null) {
                named.uuid = uuid;
            }
            return named;
        }

        if (named != null && named != known) {
            merge(known, named);
            conversations.remove(key(ign));
        }
        if (!ign.equalsIgnoreCase(known.name)) {
            renameTo(known, ign);
        } else {
            known.name = ign;
        }
        known.uuid = uuid;
        return known;
    }

    public static void refreshIdentities() {
        boolean changed = false;

        for (ChatConversation conversation : new ArrayList<>(conversations.values())) {
            if (conversation.uuid != null && !conversation.uuid.isBlank()) {
                continue;
            }
            String uuid = PlayerLookup.uuidFor(conversation.name);
            if (uuid != null) {
                conversation.uuid = uuid;
                changed = true;
            }
        }

        Map<String, ChatConversation> seen = new LinkedHashMap<>();
        for (ChatConversation conversation : new ArrayList<>(conversations.values())) {
            if (conversation.uuid == null || conversation.uuid.isBlank()) {
                continue;
            }
            String id = conversation.uuid.toLowerCase(Locale.ROOT);
            ChatConversation existing = seen.get(id);
            if (existing == null) {
                seen.put(id, conversation);
                continue;
            }
            ChatConversation keep = existing.lastActivity >= conversation.lastActivity
                    ? existing : conversation;
            ChatConversation drop = keep == existing ? conversation : existing;
            merge(keep, drop);
            conversations.remove(key(drop.name));
            seen.put(id, keep);
            changed = true;
        }

        if (changed) {
            save();
        }
    }

    public static void record(String ign, boolean outgoing, String text) {
        ChatConversation conversation = resolve(ign);

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

    /**
     * A locally generated line in a conversation, such as a stats check result.
     * Marked as a system line so it draws centred rather than looking like
     * something either side actually whispered.
     */
    public static void note(String ign, String text) {
        if (ign == null || ign.isBlank() || text == null || text.isBlank()) {
            return;
        }

        ChatConversation conversation = resolve(ign);
        ChatMessage note = new ChatMessage(false, text, System.currentTimeMillis());
        note.system = true;
        conversation.messages.add(note);
        while (conversation.messages.size() > MAX_MESSAGES) {
            conversation.messages.remove(0);
        }
        conversation.lastActivity = System.currentTimeMillis();
        conversation.hidden = false;

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
        // Typed by the player, so it takes the same route as anything typed in
        // vanilla chat: into their history, and past any mod that rewrites
        // what gets sent.
        ChatUtil.sendTyped("/w " + ign + " " + trimmed);
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
