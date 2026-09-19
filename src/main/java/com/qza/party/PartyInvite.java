package com.qza.party;

import com.qza.chat.ChannelHistory;
import com.qza.util.ChatUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class PartyInvite {
    private PartyInvite() {
    }

    public static void send(String ign) {
        if (ign == null || ign.isBlank()) {
            return;
        }
        ChatUtil.sendCommand("party invite " + ign);
        announce(ign);
    }

    public static void announce(String ign) {
        MutableComponent rich = ChatUtil.prefix()
                .append(Component.literal(ign).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" has been invited to the party!")
                        .withStyle(ChatFormatting.GREEN));

        ChannelHistory.note(ChannelHistory.PARTY, rich,
                "[QZA] " + ign + " has been invited to the party!");
    }
}
