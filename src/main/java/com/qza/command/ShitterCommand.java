package com.qza.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.qza.shitter.ShitterEntry;
import com.qza.shitter.ShitterList;
import com.qza.shitter.ShitterListPage;
import com.qza.util.ChatUtil;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class ShitterCommand {

    private ShitterCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(build("shitter"));
        dispatcher.register(build("shitterlist"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> build(String name) {
        return literal(name)
                .executes(ctx -> {
                    usage();
                    return 1;
                })
                .then(literal("add")
                        .then(argument("ign", StringArgumentType.word())
                                .executes(ctx -> add(
                                        StringArgumentType.getString(ctx, "ign"),
                                        "No reason given"))
                                .then(argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> add(
                                                StringArgumentType.getString(ctx, "ign"),
                                                StringArgumentType.getString(ctx, "reason"))))))
                .then(literal("remove")
                        .then(argument("ign", StringArgumentType.word())
                                .executes(ctx -> remove(StringArgumentType.getString(ctx, "ign")))))
                .then(literal("list")
                        .executes(ctx -> {
                            ShitterListPage.print(1);
                            return 1;
                        })
                        .then(argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ShitterListPage.print(IntegerArgumentType.getInteger(ctx, "page"));
                                    return 1;
                                })))
                .then(literal("clear")
                        .executes(ctx -> {
                            int removed = ShitterList.clear();
                            ChatUtil.success("Cleared " + removed + " entr" + (removed == 1 ? "y" : "ies") + ".");
                            return 1;
                        }))
                .then(literal("help")
                        .executes(ctx -> {
                            usage();
                            return 1;
                        }));
    }

    private static int add(String ign, String reason) {
        ShitterEntry previous = ShitterList.add(ign, reason);
        if (previous != null) {
            ChatUtil.send(Component.literal("Updated ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(ign).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(reason).withStyle(ChatFormatting.RED)));
        } else {
            ChatUtil.send(Component.literal("Added ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(ign).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" to the shitter list - ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(reason).withStyle(ChatFormatting.RED)));
        }
        return 1;
    }

    private static int remove(String ign) {
        ShitterEntry removed = ShitterList.remove(ign);
        if (removed == null) {
            ChatUtil.error(ign + " is not on the shitter list.");
            return 0;
        }
        ChatUtil.send(Component.literal("Removed ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(removed.name).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" from the shitter list.").withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    private static void usage() {
        ChatUtil.raw(Component.literal("Shitter List commands")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        help("/shitter add <ign> [reason]", "Add someone to the list");
        help("/shitter remove <ign>", "Take someone off the list");
        help("/shitter list [page]", "Show the list, 8 per page");
        help("/shitter clear", "Wipe the whole list");
    }

    private static void help(String command, String description) {
        ChatUtil.raw(Component.literal(" " + command + " ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal("- " + description).withStyle(ChatFormatting.GRAY)));
    }
}
