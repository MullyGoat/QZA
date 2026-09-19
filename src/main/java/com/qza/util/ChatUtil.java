package com.qza.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class ChatUtil {
    private ChatUtil() {
    }

    public static MutableComponent prefix() {
        return Component.literal("[").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("QZA").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal("] ").withStyle(ChatFormatting.AQUA));
    }

    public static void send(Component message) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.gui != null) {
                client.gui.getChat().addClientSystemMessage(prefix().append(message));
            }
        });
    }

    public static void raw(Component message) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.gui != null) {
                client.gui.getChat().addClientSystemMessage(message);
            }
        });
    }

    public static void info(String message) {
        send(Component.literal(message).withStyle(ChatFormatting.GRAY));
    }

    public static void success(String message) {
        send(Component.literal(message).withStyle(ChatFormatting.GREEN));
    }

    public static void error(String message) {
        send(Component.literal(message).withStyle(ChatFormatting.RED));
    }

    /**
     * Sends something the player typed, by the same route the vanilla chat box
     * uses.
     *
     * Two things come with that route and neither can be had by talking to the
     * connection directly. The line is put in the recent-chat history, which is
     * what the up arrow walks, shared with vanilla chat so a message sent in
     * either box comes back in both. And mods that hook the chat box get their
     * turn: Odin's emotes are applied from there, so "o/" only becomes a wave
     * for a message that goes through it.
     *
     * The screen is constructed and then thrown away, never shown or laid out.
     * Constructing one is enough, because handleChatInput reads nothing off it
     * but the Minecraft reference the Screen constructor already set.
     *
     * Reserved for what the player actually typed. Anything QZA sends on its
     * own goes through sendCommand, which keeps it out of their history.
     */
    public static void sendTyped(String message) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.getConnection() == null || client.player == null) {
                return;
            }
            new ChatScreen("", false).handleChatInput(message, true);
        });
    }

    public static void sendChat(String message) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            ClientPacketListener connection = client.getConnection();
            if (connection == null) {
                return;
            }
            if (message.startsWith("/")) {
                // sendCommand, not sendUnattendedCommand: the latter verifies
                // against the server's command tree and refuses anything it
                // does not recognise, which breaks Hypixel aliases like /d that
                // are never advertised. This is the path vanilla chat uses, and
                // Fabric hooks it too, so client commands still get caught.
                connection.sendCommand(message.substring(1));
            } else {
                connection.sendChat(message);
            }
        });
    }

    public static void sendCommand(String commandWithoutSlash) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            ClientPacketListener connection = client.getConnection();
            if (connection != null) {
                connection.sendCommand(commandWithoutSlash);
            }
        });
    }
}
