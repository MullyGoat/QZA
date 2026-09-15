package com.qza.chat;

import java.util.ArrayList;
import java.util.List;

public class ChatConversation {
    public String name = "";
    public String uuid;
    public long lastActivity;
    public int unread;
    public boolean hidden;
    public List<ChatMessage> messages = new ArrayList<>();

    public ChatConversation() {
    }

    public ChatConversation(String name) {
        this.name = name;
    }

    public String key() {
        return name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
    }

    public ChatMessage last() {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        return messages.get(messages.size() - 1);
    }

    public String preview() {
        ChatMessage last = last();
        if (last == null) {
            return "";
        }
        return (last.outgoing ? "You: " : "") + last.text;
    }
}
