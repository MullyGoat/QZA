package com.qza.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.discord.DiscordAlert;
import com.qza.discord.PartyFullAlert;
import com.qza.stats.AutoInvite;
import com.qza.gui.GuiEditScreen;
import com.qza.gui.QZAChatScreen;
import com.qza.gui.QZAScreen;
import com.qza.music.MusicLibrary;
import com.qza.music.MusicManager;
import com.qza.shitter.ShitterList;
import com.qza.util.ChatUtil;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
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
                .then(literal("chat").executes(ctx -> {
                    openChat();
                    return 1;
                }))
                .then(literal("stats")
                        .executes(ctx -> {
                            AutoInvite.reportSource();
                            return 1;
                        })
                        .then(argument("ign", StringArgumentType.word()).executes(ctx -> {
                            AutoInvite.check(StringArgumentType.getString(ctx, "ign"));
                            return 1;
                        })))
                .then(literal("discord")
                        .executes(ctx -> {
                            discordStatus();
                            return 1;
                        })
                        .then(literal("test").executes(ctx -> {
                            ChatUtil.info("Sending a test alert...");
                            DiscordAlert.test(ChatUtil::success, ChatUtil::error);
                            return 1;
                        }))
                        .then(literal("link").executes(ctx -> {
                            ChatUtil.info("Asking for a code...");
                            DiscordAlert.link(DiscordAlert::announceCode, ChatUtil::error);
                            return 1;
                        }))
                        .then(literal("unlink").executes(ctx -> {
                            DiscordAlert.unlink(ChatUtil::success, ChatUtil::error);
                            return 1;
                        })))
                .then(literal("gui")
                        .executes(ctx -> {
                            openEditor();
                            return 1;
                        })
                        .then(literal("reset").executes(ctx -> {
                            GuiEditScreen.resetAll();
                            ChatUtil.success("GUI reset to default.");
                            return 1;
                        })))
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

    private static void openEditor() {
        Minecraft client = Minecraft.getInstance();

        client.execute(() -> client.setScreen(new GuiEditScreen(false)));
    }

    private static void openChat() {
        Minecraft client = Minecraft.getInstance();

        client.execute(() -> client.setScreen(new QZAChatScreen()));
    }

    private static void discordStatus() {
        QZAConfig cfg = ConfigManager.get();

        String blocked = PartyFullAlert.blockedReason();
        if (blocked != null) {
            ChatUtil.send(Component.literal("Party full alerts are off. ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(blocked).withStyle(ChatFormatting.RED)));
            return;
        }

        String where = cfg.discordAlertDm && cfg.discordAlertChannel
                ? "a DM and a channel ping"
                : cfg.discordAlertDm ? "a DM" : "a channel ping";

        ChatUtil.send(Component.literal("Party full alerts are on, sending ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(where).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(". Fires when your party hits 5/5, as long as you "
                        + "lead it or the game is not focused.").withStyle(ChatFormatting.GRAY)));
        ChatUtil.send(Component.literal("Try it with ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("/qza discord test").withStyle(ChatFormatting.LIGHT_PURPLE)));
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
        entry("/qza chat", "Open QZA Chat");
        entry("/qza gui", "Open GUI edit mode to move and resize notifications");
        entry("/qza gui reset", "Restore the GUI layout to its default");
        entry("/qza music", "Show what is playing and how many tracks are loaded");
        entry("/qza music play", "Start the music now");
        entry("/qza music stop", "Stop the music");
        entry("/qza music folder", "Open the music folder");
        entry("/qza music reload", "Re-scan the music folder");
        entry("/qza stats", "Check the stats source against your own profile");
        entry("/qza stats <ign>", "Show someone's cata, floor PB and secret average");
        entry("/qza discord", "Show whether party full alerts are set up");
        entry("/qza discord link", "Get a code to link this game to your Discord");
        entry("/qza discord unlink", "Unlink and delete what the relay stored");
        entry("/qza discord test", "Send a test alert to Discord");
        entry("/qza reload", "Reload config and list from disk");
        entry("/qza help", "Print this list in game");
        entry("/shitter", "Show the shitter list commands");
        entry("/shitter add <ign> [reason]", "Add someone to the shitter list");
        entry("/shitter remove <ign>", "Take someone off the list");
        entry("/shitter list [page]", "Show the list, 8 per page");
        entry("/shitter clear", "Wipe the list");
    }

    private static void entry(String command, String description) {
        ChatUtil.raw(Component.literal(" " + command + " ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal("- " + description).withStyle(ChatFormatting.GRAY)));
    }
}
