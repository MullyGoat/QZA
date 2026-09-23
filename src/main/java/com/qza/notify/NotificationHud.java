package com.qza.notify;

import com.qza.chat.ChatNotification;
import com.qza.dungeon.WitherKey;
import com.qza.party.PartyNotification;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class NotificationHud implements HudElement {
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui || client.font == null) {
            return;
        }
        PartyNotification.renderHud(graphics, client.font);
        ChatNotification.renderHud(graphics, client.font);
        WitherKey.renderHud(graphics, client.font);
    }
}
