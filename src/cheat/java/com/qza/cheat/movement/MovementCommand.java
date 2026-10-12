package com.qza.cheat.movement;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.qza.compat.Mc;
import com.qza.util.ChatUtil;
import com.qza.util.Scheduler;
import com.qza.util.SkyBlockArea;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class MovementCommand {
    private MovementCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("qza")
                .then(literal("movement").then(literal("copy")
                        .executes(ctx -> help())
                        .then(literal("start").executes(ctx -> {
                            MovementCopier.startCommand();
                            return 1;
                        }))
                        .then(literal("stop").executes(ctx -> {
                            MovementCopier.stopCommand();
                            return 1;
                        }))))
                .then(literal("recorder")
                        .executes(ctx -> {
                            Scheduler.schedule(1, () -> Mc.setScreen(new RecordingsScreen(null)));
                            return 1;
                        })
                        .then(literal("help").executes(ctx -> help()))
                        .then(literal("start").executes(ctx -> {
                            MovementCopier.startCommand();
                            return 1;
                        }))
                        .then(literal("stop").executes(ctx -> {
                            MovementCopier.stopCommand();
                            return 1;
                        }))
                        .then(literal("list").executes(ctx -> list()))
                        .then(literal("place").then(name().executes(ctx -> withRecording(ctx, MovementCommand::place))))
                        .then(literal("unplace").then(name().executes(ctx -> withRecording(ctx, MovementCommand::unplace))))
                        .then(literal("play").then(name().executes(ctx -> withRecording(ctx, MovementCommand::play))))
                        .then(literal("edit").then(name().executes(ctx -> withRecording(ctx, recording ->
                                Scheduler.schedule(1, () -> Mc.setScreen(new RecordingEditScreen(recording, null)))))))
                        .then(literal("delete").then(name().executes(ctx -> withRecording(ctx, MovementCommand::delete))))
                        .then(literal("lag").then(argument("seconds", IntegerArgumentType.integer(1, 10))
                                .executes(ctx -> lag(IntegerArgumentType.getInteger(ctx, "seconds")))))));
    }

    private static RequiredArgumentBuilder<FabricClientCommandSource, String> name() {
        return argument("name", StringArgumentType.greedyString())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Recordings.names(), builder));
    }

    private static int withRecording(CommandContext<FabricClientCommandSource> ctx, Consumer<Recording> action) {
        String name = StringArgumentType.getString(ctx, "name");
        Recording recording = Recordings.find(name);
        if (recording == null) {
            ChatUtil.error("No recording called \"" + name.trim() + "\". Use /qza recorder list to see them.");
            return 0;
        }
        action.accept(recording);
        return 1;
    }

    private static void place(Recording recording) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        BlockPos pos = MovementCopier.target(player);
        String area = SkyBlockArea.current();
        Recording other = Recordings.at(pos, area);
        if (other != null && other != recording) {
            other.block = null;
            Recordings.save(other);
            ChatUtil.info("Took " + other.name + " off that block.");
        }
        recording.block = new int[]{pos.getX(), pos.getY(), pos.getZ()};
        recording.area = area;
        Recordings.save(recording);
        MovementCopier.placed(recording);
        ChatUtil.success(recording.name + " is on " + recording.blockText() + ". " + recording.startHint());
        if (!MovementCopier.enabled()) {
            ChatUtil.info("Turn on Movement Copier in /qza under Cheats so it can play.");
        }
    }

    private static void unplace(Recording recording) {
        if (!recording.placed()) {
            ChatUtil.info(recording.name + " isn't on a block.");
            return;
        }
        recording.block = null;
        Recordings.save(recording);
        ChatUtil.success(recording.name + " is no longer on a block.");
    }

    private static void play(Recording recording) {
        if (!MovementCopier.enabled()) {
            MovementCopier.checkEnabled();
            return;
        }
        MovementCopier.playSoon(recording);
        if (MovementCopier.state() != MovementCopier.State.RECORDING
                && MovementCopier.state() != MovementCopier.State.COUNTDOWN) {
            ChatUtil.info("Playing " + recording.name + " from where you are once chat closes.");
        }
    }

    private static void delete(Recording recording) {
        MovementCopier.forget(recording);
        Recordings.remove(recording);
        ChatUtil.success("Deleted " + recording.name + ".");
    }

    private static int list() {
        List<Recording> all = Recordings.all();
        if (all.isEmpty()) {
            ChatUtil.info("No recordings yet. Start one with /qza movement copy start.");
            return 1;
        }
        ChatUtil.raw(Component.literal("QZA").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal(" recordings (" + all.size() + ")").withStyle(ChatFormatting.WHITE)));
        for (Recording recording : all) {
            ChatUtil.raw(Component.literal(" " + recording.name + " ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal("- " + CopierScreen.seconds(recording.playSeconds()) + ", "
                            + (recording.placed() ? "on " + recording.blockText() : "not placed"))
                            .withStyle(ChatFormatting.GRAY)));
        }
        return 1;
    }

    private static int lag(int seconds) {
        if (!MovementCopier.armLagTest(seconds)) {
            ChatUtil.error("Lag tests only work in singleplayer.");
            return 0;
        }
        ChatUtil.info("The server will freeze for " + seconds + "s, 1 second into your next playback.");
        return 1;
    }

    private static int help() {
        ChatUtil.raw(Component.literal("QZA").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal(" Movement Copier").withStyle(ChatFormatting.WHITE)));
        entry("/qza movement copy start", "Record after a countdown");
        entry("/qza movement copy stop", "Finish the recording, or stop a playback");
        entry("/qza recorder", "Open your recordings");
        entry("/qza recorder place <name>", "Put it on the block you look at");
        entry("/qza recorder unplace <name>", "Take it off its block");
        entry("/qza recorder play <name>", "Play it now from where you stand");
        entry("/qza recorder edit <name>", "Trim the start and end, and change the speed");
        entry("/qza recorder delete <name>", "Delete it");
        entry("/qza recorder list", "Print every recording");
        entry("/qza recorder lag <seconds>", "Singleplayer only: freeze the server 1s into the next playback");
        return 1;
    }

    private static void entry(String command, String description) {
        ChatUtil.raw(Component.literal(" " + command + " ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal("- " + description).withStyle(ChatFormatting.GRAY)));
    }
}
