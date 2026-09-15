package com.qza.chat;

public class ChatMessage {
    public boolean outgoing;
    public String text = "";
    public long time;

    public ChatMessage() {
    }

    public ChatMessage(boolean outgoing, String text, long time) {
        this.outgoing = outgoing;
        this.text = text;
        this.time = time;
    }
}
