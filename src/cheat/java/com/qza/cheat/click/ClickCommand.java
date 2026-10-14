package com.qza.cheat.click;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.qza.cheat.CheatConfigManager;
import com.qza.compat.Mc;
import com.qza.util.ChatUtil;
import com.qza.util.Scheduler;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class ClickCommand {
    private ClickCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("qza").then(literal("autoclicker")
                .executes(ctx -> open())
                .then(literal("on").executes(ctx -> turn(true)))
                .then(literal("off").executes(ctx -> turn(false)))
                .then(literal("toggle").executes(ctx -> turn(!AutoClicker.enabled())))
                .then(literal("status").executes(ctx -> status()))
                .then(literal("unbind").executes(ctx -> AutoClicker.unbindHeld() ? 1 : 0))
                .then(literal("bind")
                        .executes(ctx -> AutoClicker.bindHeld(CheatConfigManager.get().autoClickerCps) ? 1 : 0)
                        .then(argument("clicks",
                                DoubleArgumentType.doubleArg(AutoClicker.MIN_CPS, AutoClicker.MAX_CPS))
                                .executes(ctx -> AutoClicker.bindHeld(
                                        DoubleArgumentType.getDouble(ctx, "clicks")) ? 1 : 0)))));
    }

    private static int open() {
        Scheduler.schedule(1, () -> Mc.setScreen(new AutoClickerScreen(null)));
        return 1;
    }

    private static int turn(boolean on) {
        AutoClicker.setEnabled(on);
        if (on) {
            ChatUtil.success("Auto Clicker on.");
        } else {
            ChatUtil.info("Auto Clicker off.");
        }
        return 1;
    }

    private static int status() {
        ChatUtil.info(AutoClicker.status());
        return 1;
    }
}
