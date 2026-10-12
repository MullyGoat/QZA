package com.qza.compat;

import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.EnderMan;

import java.net.URI;

public final class Mc {
    private Mc() {
    }

    public static Screen screen() {
        return Minecraft.getInstance().gui.screen();
    }

    public static void setScreen(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }

    public static ChatComponent chat() {
        Minecraft client = Minecraft.getInstance();
        return client.gui == null ? null : client.gui.hud.getChat();
    }

    public static long chatQueueSize() {
        Minecraft client = Minecraft.getInstance();
        return client.gui == null || client.gui.chatListener() == null ? 0L : client.gui.chatListener().queueSize();
    }

    public static BossHealthOverlay bossOverlay() {
        Minecraft client = Minecraft.getInstance();
        return client.gui == null ? null : client.gui.hud.getBossOverlay();
    }

    public static Overlay overlay() {
        return Minecraft.getInstance().gui.overlay();
    }

    public static boolean hudHidden() {
        Minecraft client = Minecraft.getInstance();
        return client.gui != null && client.gui.hud.isHidden();
    }

    public static void showTitle(Component title, int fadeIn, int stay, int fadeOut) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui != null) {
            client.gui.hud.setTimes(fadeIn, stay, fadeOut);
            client.gui.hud.setTitle(title);
        }
    }

    public static void openUri(URI uri) {
        Util.getPlatform().openUri(uri);
    }

    public static Screen confirmLink(BooleanConsumer callback, URI uri) {
        return new ConfirmLinkScreen(callback, uri.toString(), false);
    }

    public static String keyName(int code) {
        return InputConstants.Type.KEYSYM.getOrCreate(code).getDisplayName().getString();
    }

    public static boolean isEnderman(Entity entity) {
        return entity instanceof EnderMan;
    }
}
