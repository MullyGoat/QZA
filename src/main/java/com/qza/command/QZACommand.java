package com.qza.command;

import com.mojang.brigadier.CommandDispatcher;
import com.qza.config.ConfigManager;
import com.qza.gui.QZAScreen;
import com.qza.music.MusicLibrary;
import com.qza.music.MusicManager;
import com.qza.shitter.ShitterList;
import com.qza.util.ChatUtil;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class QZACommand {
    private QZACommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("qza")
                .executes(ctx -> {
                    openSettings();
                    return 1;
                })
                .then(literal("help").executes(ctx -> {
                    help();
                    return 1;
                }))
                .then(literal("reload").executes(ctx -> {
                    ConfigManager.load();
                    ShitterList.load();
                    MusicManager.get().applySettings();
                    int tracks = MusicLibrary.reload().size();
                    ChatUtil.success("Reloaded config, " + ShitterList.size()
                            + " shitter(s) and " + tracks + " track(s).");
                    return 1;
                }))
                .then(literal("music")
                        .executes(ctx -> {
                            musicStatus();
                            return 1;
                        })
                        .then(literal("play").executes(ctx -> {
                            MusicManager.get().startPlaylist();
                            return 1;
                        }))
                        .then(literal("stop").executes(ctx -> {
                            MusicManager.get().player().fadeOutAndStop();
                            ChatUtil.info("Stopping music.");
                            return 1;
                        }))
                        .then(literal("reload").executes(ctx -> {
                            int found = MusicLibrary.reload().size();
                            ChatUtil.success("Found " + found + " track" + (found == 1 ? "" : "s") + ".");
                            return 1;
                        }))
                        .then(literal("folder").executes(ctx -> {
                            MusicLibrary.openFolder();
                            ChatUtil.info("Opened the music folder.");
                            return 1;
                        }))));

        dispatcher.register(literal("qzahelp").executes(ctx -> {
            help();
            return 1;
        }));
    }

    private static void openSettings() {
        Minecraft client = Minecraft.getInstance();

        client.execute(() -> client.setScreen(new QZAScreen()));
    }

    private static void musicStatus() {
        MusicManager manager = MusicManager.get();
        int tracks = MusicLibrary.count();
        if (manager.isPlaying()) {
            java.nio.file.Path current = manager.player().currentTrack();
            ChatUtil.send(Component.literal("Playing ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(current == null ? "?" : current.getFileName().toString())
                            .withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" (" + tracks + " in playlist).")
                            .withStyle(ChatFormatting.GRAY)));
        } else {
            ChatUtil.send(Component.literal("Not playing. ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(tracks + " track" + (tracks == 1 ? "" : "s"))
                            .withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" in the playlist.").withStyle(ChatFormatting.GRAY)));
        }
    }

    private static void help() {
        ChatUtil.raw(Component.literal("QZA")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal(" commands").withStyle(ChatFormatting.WHITE)));

        entry("/qza", "Open the settings GUI");
        entry("/shitter add <ign> [reason]", "Add someone to the shitter list");
        entry("/shitter remove <ign>", "Take someone off the list");
        entry("/shitter list [page]", "Show the list, 8 per page");
        entry("/shitter clear", "Wipe the list");
        entry("/qza music play|stop", "Manually control the terminal music");
        entry("/qza music folder", "Open the drag-and-drop music folder");
        entry("/qza music reload", "Re-scan the music folder");
        entry("/qza reload", "Reload config and list from disk");
    }

    private static void entry(String command, String description) {
        ChatUtil.raw(Component.literal(" " + command + " ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal("- " + description).withStyle(ChatFormatting.GRAY)));
    }
}
