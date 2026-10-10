package com.qza.itemlist;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qza.QZA;
import com.qza.util.IgnUtil;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class MarketItems {
    public enum Market { AUCTION, BAZAAR }

    public record Entry(String name, RepoItem item) {
    }

    private static final String BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
    private static final long REFRESH_MS = 3_600_000L;
    private static final long RETRY_MS = 60_000L;
    private static final String PET_START = "[Lvl ";
    private static final String ARROW = "➡";

    private static volatile Set<String> bazaarProducts;
    private static volatile boolean bazaarLoading;
    private static volatile long bazaarAt;
    private static volatile boolean bazaarFailed;

    private static Index index = Index.EMPTY;

    private record Index(Object repo, Object products, List<Entry> auction, List<Entry> bazaar,
                         Map<String, Integer> petLevels, Set<String> starable) {
        static final Index EMPTY = new Index(null, null, List.of(), List.of(), Map.of(), Set.of());
    }

    private MarketItems() {
    }

    public static void beginFrame() {
        RepoItem.beginFrame();
    }

    public static boolean ready(Market market) {
        if (ItemRepo.state() != ItemRepo.State.READY) {
            return false;
        }
        return market == Market.AUCTION || bazaarProducts != null || bazaarFailed;
    }

    public static void ensureLoaded() {
        ItemRepo.ensureLoaded();
        long now = System.currentTimeMillis();
        if (bazaarLoading) {
            return;
        }
        boolean stale = bazaarProducts == null
                ? !bazaarFailed || now - bazaarAt > RETRY_MS
                : now - bazaarAt > REFRESH_MS;
        if (stale) {
            bazaarLoading = true;
            CompletableFuture.runAsync(MarketItems::loadBazaar);
        }
    }

    private static void loadBazaar() {
        try {
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(BAZAAR_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "QZA")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IllegalStateException("Hypixel answered " + response.statusCode());
            }
            Set<String> products = new HashSet<>();
            try (Reader reader = new InputStreamReader(response.body(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                for (Map.Entry<String, JsonElement> product : root.getAsJsonObject("products").entrySet()) {
                    if (selling(product.getValue())) {
                        products.add(product.getKey());
                    }
                }
            }
            bazaarProducts = Set.copyOf(products);
            bazaarFailed = false;
        } catch (Exception e) {
            QZA.LOGGER.warn("Could not load the bazaar item list: {}", e.toString());
            bazaarFailed = true;
        } finally {
            bazaarAt = System.currentTimeMillis();
            bazaarLoading = false;
        }
    }

    private static boolean selling(JsonElement product) {
        try {
            JsonObject status = product.getAsJsonObject().getAsJsonObject("quick_status");
            return status == null || status.get("sellVolume").getAsLong() > 0L;
        } catch (Exception e) {
            return true;
        }
    }

    public static List<Entry> matches(Market market, String query, int limit) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty() || limit <= 0) {
            return List.of();
        }
        String[] words = needle.split(" +");
        List<Entry> source = market == Market.AUCTION ? index().auction() : index().bazaar();
        List<Scored> found = new ArrayList<>();
        for (Entry entry : source) {
            int score = score(entry.name().toLowerCase(Locale.ROOT), needle, words);
            if (score >= 0) {
                found.add(new Scored(entry, score));
            }
        }
        found.sort(Comparator.comparingInt(Scored::score)
                .thenComparingInt(scored -> scored.entry().name().length())
                .thenComparing(scored -> scored.entry().name()));
        List<Entry> out = new ArrayList<>(Math.min(limit, found.size()));
        for (int i = 0; i < found.size() && out.size() < limit; i++) {
            out.add(found.get(i).entry());
        }
        return out;
    }

    private record Scored(Entry entry, int score) {
    }

    private static int score(String name, String needle, String[] words) {
        if (name.startsWith(needle)) {
            return 0;
        }
        if (name.contains(" " + needle)) {
            return 1;
        }
        if (name.contains(needle)) {
            return 2;
        }
        for (String word : words) {
            if (!name.contains(word)) {
                return -1;
            }
        }
        return 3;
    }

    public static RepoItem find(Market market, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String wanted = name.trim();
        for (Entry entry : market == Market.AUCTION ? index().auction() : index().bazaar()) {
            if (entry.name().equalsIgnoreCase(wanted)) {
                return entry.item();
            }
        }
        return null;
    }

    public static Integer petMaxLevel(String name) {
        return name == null ? null : index().petLevels().get(name.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean starable(String name) {
        return name != null && index().starable().contains(name.trim().toLowerCase(Locale.ROOT));
    }

    private static synchronized Index index() {
        ItemRepo.Data data = ItemRepo.data();
        Set<String> products = bazaarProducts;
        Index current = index;
        if (current.repo() == data && current.products() == products) {
            return current;
        }
        index = build(data, products);
        return index;
    }

    private static Index build(ItemRepo.Data data, Set<String> products) {
        Set<String> bazaarIds = new HashSet<>();
        if (products != null) {
            for (String product : products) {
                bazaarIds.add(data.bazaarStocks().getOrDefault(product, product.replace(':', '-')));
            }
        }

        Map<String, Entry> auction = new LinkedHashMap<>();
        Map<String, Entry> bazaar = new LinkedHashMap<>();
        Map<String, Integer> petLevels = new HashMap<>();
        Set<String> starable = new HashSet<>();

        for (RepoItem item : data.items()) {
            if (item.vanilla || item.mob) {
                continue;
            }
            boolean book = ItemRepo.isBook(item);
            boolean onBazaar = products == null ? book : bazaarIds.contains(item.id);
            if (onBazaar) {
                String name = book ? bookName(item) : item.plainName;
                if (!name.isEmpty()) {
                    bazaar.putIfAbsent(name.toLowerCase(Locale.ROOT), new Entry(name, item));
                }
                if (products != null) {
                    continue;
                }
            }
            if (book || soulbound(item)) {
                continue;
            }
            String name = item.plainName;
            if (name.startsWith(PET_START)) {
                int close = name.indexOf("] ");
                if (close < 0) {
                    continue;
                }
                String level = name.substring(PET_START.length(), close);
                name = name.substring(close + 2).trim();
                int arrow = level.indexOf(ARROW);
                petLevels.merge(name.toLowerCase(Locale.ROOT), level(level.substring(arrow + 1)), Math::max);
            }
            if (name.isEmpty()) {
                continue;
            }
            auction.putIfAbsent(name.toLowerCase(Locale.ROOT), new Entry(name, item));
            if (data.starable().contains(item.id)) {
                starable.add(name.toLowerCase(Locale.ROOT));
            }
        }

        if (products == null) {
            for (Entry entry : auction.values()) {
                bazaar.putIfAbsent(entry.name().toLowerCase(Locale.ROOT), entry);
            }
        }

        return new Index(data, products, List.copyOf(auction.values()), List.copyOf(bazaar.values()),
                Map.copyOf(petLevels), Set.copyOf(starable));
    }

    private static int level(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 100;
        }
    }

    private static String bookName(RepoItem item) {
        for (String line : item.lore) {
            String plain = IgnUtil.stripCodes(line).trim();
            if (!plain.isEmpty()) {
                return plain;
            }
        }
        return "";
    }

    private static boolean soulbound(RepoItem item) {
        for (String line : item.lore) {
            if (line.contains("Soulbound")) {
                return true;
            }
        }
        return false;
    }
}
