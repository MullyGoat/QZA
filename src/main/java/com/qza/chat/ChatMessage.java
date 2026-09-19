package com.qza.chat;

import net.minecraft.network.chat.Component;

public class ChatMessage {
    public boolean outgoing;
    public String text = "";
    public long time;
    public String speaker;

    public boolean system;

    public transient Component rich;

    public ChatMessage() {
    }

    public ChatMessage(boolean outgoing, String text, long time) {
        this.outgoing = outgoing;
        this.text = text;
        this.time = time;
    }

    public ChatMessage(Component rich, String text, long time, String speaker, boolean outgoing) {
        this.rich = rich;
        this.text = text;
        this.time = time;
        this.speaker = speaker;
        this.outgoing = outgoing;
    }
}
