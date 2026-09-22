package com.qza.waypoint;

import com.qza.config.ConfigManager;
import com.qza.util.ChatUtil;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;

public final class WaypointEditor {
    private static boolean active;

    private WaypointEditor() {
    }

    public static boolean active() {
        return active;
    }

    public static void init() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!active || level == null || !level.isClientSide()) {
                return InteractionResult.PASS;
            }
            place(hit.getBlockPos());
            return InteractionResult.FAIL;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (active && client.screen instanceof PauseScreen) {
                client.setScreen(null);
                stop();
            }
        });
    }

    public static void toggle() {
        if (active) {
            stop();
        } else {
            start();
        }
    }

    public static void start() {
        if (active) {
            return;
        }
        if (!ConfigManager.get().waypointsEnabled) {
            ChatUtil.error("Waypoints are switched off in /qza.");
            return;
        }
        if (Minecraft.getInstance().player == null) {
            return;
        }

        active = true;
        ChatUtil.send(Component.literal("Waypoint edit mode ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("on").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(". Right click a block to mark it, right click a "
                        + "marked one to clear it.").withStyle(ChatFormatting.GRAY)));
        ChatUtil.send(Component.literal("Press ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Esc").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(" or run ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("/qza waypoint manual add")
                        .withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(" again to finish.").withStyle(ChatFormatting.GRAY)));
    }

    public static void stop() {
        if (!active) {
            return;
        }
        active = false;
        ChatUtil.send(Component.literal("Waypoint edit mode ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("off").withStyle(ChatFormatting.RED))
                .append(Component.literal(". " + WaypointList.size() + " waypoint"
                        + (WaypointList.size() == 1 ? "" : "s") + " saved.")
                        .withStyle(ChatFormatting.GRAY)));
    }

    public static void reset() {
        active = false;
    }

    private static void place(BlockPos pos) {
        if (WaypointList.removeAt(pos.getX(), pos.getY(), pos.getZ())) {
            ChatUtil.info("Cleared " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ".");
            return;
        }

        if (WaypointList.full()) {
            ChatUtil.error("That is " + WaypointList.MAX + " waypoints, which is the limit.");
            return;
        }

        String colour = "blue";
        WaypointList.add(new Waypoint(pos.getX(), pos.getY(), pos.getZ(),
                colour, 1, 1, 1, ""));
        ChatUtil.send(Component.literal("Marked ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ())
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" in " + colour + ".").withStyle(ChatFormatting.GRAY)));
    }
}
