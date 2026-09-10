package com.qza.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class ChatUtil {

    private ChatUtil() {
    }

    /** Always applied -- QZA output is always identifiable as QZA output. */
    public static MutableComponent prefix() {
        return Component.literal("[").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("QZA").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal("] ").withStyle(ChatFormatting.AQUA));
    }

    /**
     * Client-side only chat line -- never sent to the server.
     * addClientSystemMessage tags the line as GuiMessageSource.SYSTEM_CLIENT,
     * which is exactly right for mod output.
     */
    public static void send(Component message) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.gui != null) {
                client.gui.getChat().addClientSystemMessage(prefix().append(message));
            }
        });
    }

    /** Client-side line with no prefix, for list output and separators. */
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
     * Runs a command on the server as if the player typed it.
     * Note: no leading slash -- sendCommand adds it.
     */
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
