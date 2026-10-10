package com.qza.search;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.gui.MarketSearchScreen;
import com.qza.itemlist.MarketItems;
import com.qza.itemlist.MarketItems.Market;
import com.qza.util.ChatUtil;
import com.qza.util.Scheduler;
import com.qza.util.SkyBlockArea;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class MarketSearch {
    public static final int HISTORY = 5;
    public static final int MAX_STARS = 10;

    private static final int COMMAND_SUGGESTIONS = 20;
    private static final String STAR = "✪";
    private static final String MASTER_STARS = "➊➋➌➍➎";

    private static boolean maxPetLevel;
    private static int stars;

    private MarketSearch() {
    }

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> preload());
    }

    public static void preload() {
        if (ConfigManager.get().marketSearchCommands) {
            MarketItems.ensureLoaded();
        }
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        register(dispatcher, "ahs", Market.AUCTION);
        register(dispatcher, "bzs", Market.BAZAAR);
    }

    private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, String name,
                                 Market market) {
        dispatcher.register(literal(name)
                .requires(source -> available())
                .executes(ctx -> open(market, ""))
                .then(argument("item", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> suggest(market, builder))
                        .executes(ctx -> open(market, StringArgumentType.getString(ctx, "item")))));
    }

    private static boolean available() {
        return ConfigManager.get().marketSearchCommands && SkyBlockArea.onSkyBlock();
    }

    private static CompletableFuture<Suggestions> suggest(Market market, SuggestionsBuilder builder) {
        MarketItems.ensureLoaded();
        for (MarketItems.Entry entry : MarketItems.matches(market, builder.getRemaining(), COMMAND_SUGGESTIONS)) {
            builder.suggest(entry.name());
        }
        return builder.buildFuture();
    }

    private static int open(Market market, String text) {
        MarketItems.ensureLoaded();
        Scheduler.schedule(1, () -> Mc.setScreen(new MarketSearchScreen(market, text)));
        return 1;
    }

    public static boolean maxPetLevel() {
        return maxPetLevel;
    }

    public static void toggleMaxPetLevel() {
        maxPetLevel = !maxPetLevel;
    }

    public static int stars() {
        return stars;
    }

    public static void setStars(int value) {
        stars = Math.max(0, Math.min(MAX_STARS, value));
    }

    public static List<String> history(Market market) {
        return market == Market.AUCTION
                ? ConfigManager.get().auctionSearchHistory
                : ConfigManager.get().bazaarSearchHistory;
    }

    public static void forget(Market market, String entry) {
        history(market).remove(entry);
        ConfigManager.save();
    }

    public static void search(Market market, String text) {
        String query = text == null ? "" : text.trim();
        if (query.isEmpty()) {
            return;
        }
        remember(market, query);

        if (market == Market.BAZAAR) {
            ChatUtil.sendCommand("bz " + query);
            return;
        }
        ChatUtil.sendCommand("auctionsearch " + auctionQuery(query));
    }

    private static String auctionQuery(String query) {
        String out = query;
        Integer maxLevel = MarketItems.petMaxLevel(query);
        if (maxLevel != null) {
            out = maxPetLevel ? "[Lvl " + maxLevel + "] " + out : "] " + out;
        }
        if (stars > 0 && (MarketItems.starable(query) || !MarketItems.ready(Market.AUCTION))) {
            StringBuilder suffix = new StringBuilder(" ").append(STAR.repeat(Math.min(stars, 5)));
            if (stars > 5) {
                suffix.append(MASTER_STARS.charAt(stars - 6));
            }
            out += suffix;
        }
        return out;
    }

    private static void remember(Market market, String query) {
        List<String> history = history(market);
        history.removeIf(old -> old == null || old.toLowerCase(Locale.ROOT).equals(query.toLowerCase(Locale.ROOT)));
        history.add(0, query);
        while (history.size() > HISTORY) {
            history.remove(history.size() - 1);
        }
        ConfigManager.save();
    }
}
