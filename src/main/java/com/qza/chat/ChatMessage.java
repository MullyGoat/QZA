package com.qza.chat;

import net.minecraft.network.chat.Component;

public class ChatMessage {
    public boolean outgoing;
    public String text = "";
    public long time;
    public String speaker;

    /**
     * A line QZA produced itself, such as a stats check. Drawn centred so it
     * reads as neither sent nor received. Absent in older saved history, which
     * reads back as false.
     */
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
