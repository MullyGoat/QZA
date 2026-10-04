package com.qza.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.qza.QZA;
import com.qza.config.ConfigManager;
import com.qza.stats.AutoInvite;
import com.qza.util.ChatUtil;
import com.qza.util.PlayerLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ChatHistory {
    public static final String MODE_FOREVER = "forever";
    public static final String MODE_SESSION = "session";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LEGACY_TYPE = new TypeToken<List<ChatConversation>>() {
    }.getType();

    private static final int MAX_MESSAGES = 500;
    private static final long RELOAD_MS = 1000L;

    private static final Map<String, ChatConversation> conversations = new LinkedHashMap<>();

    private static final Map<String, Integer> revisions = new LinkedHashMap<>();

    private static String account;
    private static String accountName = "";

    private static Account viewing;
    private static final Map<String, ChatConversation> viewed = new LinkedHashMap<>();
    private static long viewedModified;
    private static long viewedCheckedAt;
    private static int viewedRevision;

    public record Account(String id, String name, int chats, boolean current) {
    }

    static final class Store {
        String uuid;
        String name;
        List<ChatConversation> conversations = new ArrayList<>();
    }

    private ChatHistory() {
    }

    public static Path dir() {
        return Path.of(System.getProperty("user.home"), ".qza", "chat");
    }

    private static Path file(String id) {
        return dir().resolve(id + ".json");
    }

    private static Path legacyFile() {
        return ConfigManager.qzaDir().resolve("chat").resolve("history.json");
    }

    public static boolean persists() {
        return !MODE_SESSION.equals(ConfigManager.get().chatHistoryMode);
    }

    public static void load() {
        account = null;
        accountName = "";
        conversations.clear();
        revisions.clear();
        stopViewing();
    }

    private static void ensure() {
        Minecraft client = Minecraft.getInstance();
        User user = client == null ? null : client.getUser();
        if (user == null || user.getProfileId() == null) {
            return;
        }
        String id = id(user.getProfileId());
        if (id.equals(account)) {
            return;
        }
        if (account != null) {
            write();
        }

        account = id;
        accountName = user.getName() == null ? "" : user.getName();
        conversations.clear();
        revisions.clear();
        stopViewing();

        if (!persists()) {
            delete(file(id));
            delete(legacyFile());
            return;
        }
        Store store = read(file(id));
        if (store != null) {
            fill(conversations, store.conversations, true);
        }
        migrateLegacy();
    }

    private static String id(UUID uuid) {
        return uuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static void fill(Map<String, ChatConversation> into, List<ChatConversation> list,
                             boolean resetUnread) {
        if (list == null) {
            return;
        }
        for (ChatConversation conversation : list) {
            if (conversation == null || conversation.name == null || conversation.name.isBlank()) {
                continue;
            }
            if (conversation.messages == null) {
                conversation.messages = new ArrayList<>();
            }
            if (resetUnread) {
                conversation.unread = 0;
            }
            ChatConversation existing = into.get(conversation.key());
            if (existing == null) {
                into.put(conversation.key(), conversation);
            } else {
                merge(existing, conversation);
            }
        }
    }

    private static Store read(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, Store.class);
        } catch (Exception e) {
            QZA.LOGGER.warn("Failed to read {}", path.getFileName(), e);
            return null;
        }
    }

    private static void migrateLegacy() {
        Path legacy = legacyFile();
        if (!Files.isRegularFile(legacy)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(legacy, StandardCharsets.UTF_8)) {
            fill(conversations, GSON.fromJson(reader, LEGACY_TYPE), true);
        } catch (Exception e) {
            QZA.LOGGER.error("Failed to read old chat history", e);
            return;
        }
        if (!write()) {
            return;
        }
        try {
            Files.move(legacy, legacy.resolveSibling("history-before-accounts.json"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            QZA.LOGGER.warn("Could not move old chat history", e);
        }
    }

    public static void save() {
        ensure();
        write();
    }

    private static boolean write() {
        if (!persists() || account == null) {
            return false;
        }
        Store store = new Store();
        store.uuid = account;
        store.name = accountName;
        store.conversations = new ArrayList<>(conversations.values());

        Path path = file(account);
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(store, writer);
            }
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            QZA.LOGGER.error("Failed to write chat history", e);
            return false;
        }
    }

    public static void clear() {
        ensure();
        conversations.clear();
        revisions.clear();
        stopViewing();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir(), "*.json")) {
            for (Path path : files) {
                delete(path);
            }
        } catch (NoSuchFileException ignored) {
        } catch (IOException e) {
            QZA.LOGGER.warn("Could not clear chat history", e);
        }
        delete(legacyFile());
    }

    private static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            QZA.LOGGER.warn("Could not delete {}", path.getFileName(), e);
        }
    }

    public static String accountName() {
        ensure();
        return accountName;
    }

    public static List<Account> accounts() {
        ensure();
        List<Account> out = new ArrayList<>();
        if (account == null) {
            return out;
        }
        out.add(new Account(account, accountName, visible(conversations), true));

        List<Account> others = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir(), "*.json")) {
            for (Path path : files) {
                String name = path.getFileName().toString();
                String id = name.substring(0, name.length() - ".json".length());
                if (!id.matches("[0-9a-f]{32}") || id.equals(account)) {
                    continue;
                }
                Store store = read(path);
                if (store == null || store.conversations == null || store.conversations.isEmpty()) {
                    continue;
                }
                String shown = store.name == null || store.name.isBlank() ? id : store.name;
                int chats = 0;
                for (ChatConversation conversation : store.conversations) {
                    if (conversation != null && !conversation.hidden) {
                        chats++;
                    }
                }
                others.add(new Account(id, shown, chats, false));
            }
        } catch (NoSuchFileException ignored) {
        } catch (IOException e) {
            QZA.LOGGER.warn("Could not list chat accounts", e);
        }
        others.sort(Comparator.comparing(other -> other.name().toLowerCase(Locale.ROOT)));
        out.addAll(others);
        return out;
    }

    private static int visible(Map<String, ChatConversation> map) {
        int count = 0;
        for (ChatConversation conversation : map.values()) {
            if (!conversation.hidden) {
                count++;
            }
        }
        return count;
    }

    public static void view(Account other) {
        ensure();
        if (other == null || other.current() || other.id().equals(account)) {
            stopViewing();
            return;
        }
        viewing = other;
        reloadViewed();
    }

    public static boolean viewingOther() {
        ensure();
        return viewing != null;
    }

    public static String shownName() {
        ensure();
        return viewing == null ? accountName : viewing.name();
    }

    private static void stopViewing() {
        viewing = null;
        viewed.clear();
        viewedModified = 0;
        viewedRevision++;
    }

    private static void reloadViewed() {
        Path path = file(viewing.id());
        viewedCheckedAt = System.currentTimeMillis();
        viewedModified = modified(path);
        viewed.clear();
        Store store = read(path);
        if (store != null) {
            fill(viewed, store.conversations, false);
        }
        viewedRevision++;
    }

    private static long modified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private static Map<String, ChatConversation> shown() {
        ensure();
        if (viewing == null) {
            return conversations;
        }
        long now = System.currentTimeMillis();
        if (now - viewedCheckedAt > RELOAD_MS) {
            viewedCheckedAt = now;
            if (modified(file(viewing.id())) != viewedModified) {
                reloadViewed();
            }
        }
        return viewed;
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
        ensure();
        ChatConversation conversation = resolve(ign);
        conversation.lastActivity = System.currentTimeMillis();
        conversation.hidden = false;
        write();
        return conversation;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public static ChatConversation getByUuid(String uuid) {
        ensure();
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
        ensure();
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
            write();
        }
    }

    public static void record(String ign, boolean outgoing, String text) {
        ensure();
        ChatConversation conversation = resolve(ign);

        conversation.messages.add(new ChatMessage(outgoing, text, System.currentTimeMillis()));
        revisions.merge(key(conversation.name), 1, Integer::sum);
        while (conversation.messages.size() > MAX_MESSAGES) {
            conversation.messages.remove(0);
        }
        conversation.lastActivity = System.currentTimeMillis();
        conversation.hidden = false;
        if (!outgoing) {
            conversation.unread++;
        }

        write();
    }

    public static void note(String ign, String text) {
        if (ign == null || ign.isBlank() || text == null || text.isBlank()) {
            return;
        }

        ensure();
        ChatConversation conversation = resolve(ign);
        ChatMessage note = new ChatMessage(false, text, System.currentTimeMillis());
        note.system = true;
        conversation.messages.add(note);
        revisions.merge(key(conversation.name), 1, Integer::sum);
        while (conversation.messages.size() > MAX_MESSAGES) {
            conversation.messages.remove(0);
        }
        conversation.lastActivity = System.currentTimeMillis();
        conversation.hidden = false;

        write();
    }

    public static void hide(String ign) {
        ensure();
        ChatConversation conversation = ign == null ? null : conversations.get(key(ign));
        if (conversation != null) {
            conversation.hidden = true;
            conversation.unread = 0;
            write();
        }
    }

    public static void delete(String ign) {
        ensure();
        if (ign != null && conversations.remove(key(ign)) != null) {
            write();
        }
    }

    public static void send(String ign, String text) {
        String trimmed = text == null ? "" : text.trim();
        if (ign == null || ign.isBlank() || trimmed.isEmpty()) {
            return;
        }

        ChatUtil.sendTyped("/w " + ign + " " + trimmed);
    }

    public static List<ChatConversation> conversations() {
        List<ChatConversation> list = new ArrayList<>();
        for (ChatConversation conversation : shown().values()) {
            if (!conversation.hidden) {
                list.add(conversation);
            }
        }
        list.sort(Comparator.comparingLong((ChatConversation c) -> c.lastActivity).reversed());
        return list;
    }

    public static ChatConversation get(String ign) {
        return ign == null ? null : shown().get(key(ign));
    }

    public static int revision(String ign) {
        Map<String, ChatConversation> map = shown();
        if (map == viewed) {
            return viewedRevision;
        }
        if (ign == null) {
            return 0;
        }
        Integer at = revisions.get(key(ign));
        return at == null ? 0 : at;
    }

    public static void markRead(String ign) {
        ensure();
        ChatConversation conversation = ign == null ? null : conversations.get(key(ign));
        if (viewing == null && conversation != null && conversation.unread > 0) {
            conversation.unread = 0;
            write();
        }
    }

    public static int unreadTotal() {
        ensure();
        int total = 0;
        for (ChatConversation conversation : conversations.values()) {
            if (!conversation.hidden) {
                total += conversation.unread;
            }
        }
        return total;
    }
}
