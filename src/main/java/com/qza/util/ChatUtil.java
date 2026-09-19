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
