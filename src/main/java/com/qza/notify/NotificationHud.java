package com.qza.notify;

import com.qza.chat.ChatNotification;
import com.qza.dungeon.LeapNotification;
import com.qza.dungeon.SplitTimers;
import com.qza.dungeon.PYTimer;
import com.qza.dungeon.WitherKey;
import com.qza.party.PartyNotification;
import com.qza.timer.ClockDisplay;
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
        SplitTimers.renderHud(graphics, client.font);
        LeapNotification.renderHud(graphics, client.font);
        PYTimer.renderHud(graphics, client.font);
        ClockDisplay.renderHud(graphics, client.font);
    }
}
