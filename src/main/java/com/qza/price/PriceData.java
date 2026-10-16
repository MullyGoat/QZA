package com.qza.price;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qza.QZA;
import net.fabricmc.loader.api.FabricLoader;

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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

public final class PriceData {
    private static final String LOWEST_BIN_URL = "https://hysky.de/api/auctions/lowestbins";
    private static final String AVERAGE_URL = "https://hysky.de/api/auctions/lowestbins/average?days=";
    private static final String BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
    private static final String ITEMS_URL = "https://api.hypixel.net/v2/resources/skyblock/items";

    private static final long MINUTE = 60_000L;
    private static final long RETRY_MS = MINUTE;

    private static final Feed<Map<String, Double>> LOWEST_BIN =
            new Feed<>("lowest BIN", LOWEST_BIN_URL, MINUTE, PriceData::numbers);
    private static final Feed<Map<String, Double>> AVERAGE_1 =
            new Feed<>("1 day average", AVERAGE_URL + 1, 10 * MINUTE, PriceData::numbers);
    private static final Feed<Map<String, Double>> AVERAGE_3 =
            new Feed<>("3 day average", AVERAGE_URL + 3, 10 * MINUTE, PriceData::numbers);
    private static final Feed<Map<String, Bazaar>> BAZAAR =
            new Feed<>("bazaar", BAZAAR_URL, 2 * MINUTE, PriceData::bazaar);
    private static final Feed<Map<String, ItemInfo>> ITEMS =
            new Feed<>("item data", ITEMS_URL, 0, PriceData::items);

    private static volatile int generation;
    private static HttpClient client;

    public record Bazaar(double buy, double sell) {
    }

    record Cost(String id, int amount, long coins) {
    }

    record GemSlot(String type, List<Cost> costs) {
    }

    record ItemInfo(String category, List<List<Cost>> upgrades, List<GemSlot> gemSlots) {
    }

    private PriceData() {
    }

    public static void ensure(boolean oneDay, boolean threeDay, boolean items) {
        LOWEST_BIN.ensure();
        BAZAAR.ensure();
        if (oneDay) {
            AVERAGE_1.ensure();
        }
        if (threeDay) {
            AVERAGE_3.ensure();
        }
        if (items) {
            ITEMS.ensure();
        }
    }

    public static int generation() {
        return generation;
    }

    public static Double lowestBin(String key) {
        return positive(LOWEST_BIN.get(), key);
    }

    public static Double average(String key, int days) {
        return positive((days == 1 ? AVERAGE_1 : AVERAGE_3).get(), key);
    }

    public static Bazaar bazaar(String key) {
        Map<String, Bazaar> map = BAZAAR.get();
        return map == null || key == null ? null : map.get(key);
    }

    public static boolean bazaarLoaded() {
        return BAZAAR.get() != null;
    }

    static ItemInfo info(String id) {
        Map<String, ItemInfo> map = ITEMS.get();
        return map == null || id == null ? null : map.get(id);
    }

    static double price(String key) {
        if (key == null) {
            return 0;
        }
        Bazaar bazaar = bazaar(key);
        if (bazaar != null && bazaar.buy() > 0) {
            return bazaar.buy();
        }
        Double average = average(key, 3);
        if (average != null) {
            return average;
        }
        Double lowest = lowestBin(key);
        return lowest == null ? 0 : lowest;
    }

    static double cheapest(String prefix) {
        double best = 0;
        Map<String, Bazaar> map = BAZAAR.get();
        if (map != null) {
            for (Map.Entry<String, Bazaar> entry : map.entrySet()) {
                double buy = entry.getValue().buy();
                if (entry.getKey().startsWith(prefix) && buy > 0 && (best == 0 || buy < best)) {
                    best = buy;
                }
            }
        }
        return best;
    }

    private static Double positive(Map<String, Double> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Double value = map.get(key);
        return value == null || value <= 0 ? null : value;
    }

    private static Map<String, Double> numbers(JsonElement json) {
        Map<String, Double> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                out.put(entry.getKey(), value.getAsDouble());
            }
        }
        return Map.copyOf(out);
    }

    private static Map<String, Bazaar> bazaar(JsonElement json) {
        JsonObject products = json.getAsJsonObject().getAsJsonObject("products");
        Map<String, Bazaar> out = new HashMap<>(products.size() * 2);
        for (Map.Entry<String, JsonElement> entry : products.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject product = entry.getValue().getAsJsonObject();
            JsonObject quick = product.has("quick_status") && product.get("quick_status").isJsonObject()
                    ? product.getAsJsonObject("quick_status") : new JsonObject();
            double buy = top(product, "buy_summary", number(quick, "buyPrice"));
            double sell = top(product, "sell_summary", number(quick, "sellPrice"));
            out.put(entry.getKey(), new Bazaar(buy, sell));
        }
        return Map.copyOf(out);
    }

    private static double top(JsonObject product, String summary, double fallback) {
        if (product.has(summary) && product.get(summary).isJsonArray()) {
            JsonArray orders = product.getAsJsonArray(summary);
            if (!orders.isEmpty() && orders.get(0).isJsonObject()) {
                double price = number(orders.get(0).getAsJsonObject(), "pricePerUnit");
                if (price > 0) {
                    return price;
                }
            }
        }
        return fallback;
    }

    private static Map<String, ItemInfo> items(JsonElement json) {
        Map<String, ItemInfo> out = new HashMap<>();
        for (JsonElement element : json.getAsJsonObject().getAsJsonArray("items")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            String id = text(item, "id");
            if (id.isEmpty()) {
                continue;
            }
            List<List<Cost>> upgrades = new ArrayList<>();
            if (item.has("upgrade_costs") && item.get("upgrade_costs").isJsonArray()) {
                for (JsonElement tier : item.getAsJsonArray("upgrade_costs")) {
                    upgrades.add(tier.isJsonArray() ? costs(tier.getAsJsonArray()) : List.of());
                }
            }
            List<GemSlot> slots = new ArrayList<>();
            if (item.has("gemstone_slots") && item.get("gemstone_slots").isJsonArray()) {
                for (JsonElement slot : item.getAsJsonArray("gemstone_slots")) {
                    if (slot.isJsonObject()) {
                        JsonObject entry = slot.getAsJsonObject();
                        List<Cost> costs = entry.has("costs") && entry.get("costs").isJsonArray()
                                ? costs(entry.getAsJsonArray("costs")) : List.of();
                        slots.add(new GemSlot(text(entry, "slot_type"), costs));
                    }
                }
            }
            String category = text(item, "category");
            if (!upgrades.isEmpty() || !slots.isEmpty() || !category.isEmpty()) {
                out.put(id, new ItemInfo(category, List.copyOf(upgrades), List.copyOf(slots)));
            }
        }
        return Map.copyOf(out);
    }

    private static List<Cost> costs(JsonArray array) {
        List<Cost> out = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject cost = element.getAsJsonObject();
            String type = text(cost, "type");
            int amount = (int) number(cost, "amount");
            switch (type) {
                case "ESSENCE" -> out.add(new Cost("ESSENCE_" + text(cost, "essence_type").toUpperCase(Locale.ROOT),
                        Math.max(1, amount), 0));
                case "ITEM" -> out.add(new Cost(text(cost, "item_id"), Math.max(1, amount), 0));
                case "COINS" -> out.add(new Cost(null, 0, (long) number(cost, "coins")));
                default -> {
                }
            }
        }
        return List.copyOf(out);
    }

    private static String text(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static double number(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                ? value.getAsDouble() : 0;
    }

    private static synchronized HttpClient http() {
        if (client == null) {
            client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
        return client;
    }

    private static String userAgent() {
        String version = FabricLoader.getInstance().getModContainer(QZA.MOD_ID)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
        return QZA.MOD_NAME + "/" + version;
    }

    private static final class Feed<T> {
        private final String name;
        private final String url;
        private final long refreshMs;
        private final Function<JsonElement, T> parser;
        private final AtomicBoolean loading = new AtomicBoolean();
        private volatile T value;
        private volatile long loadedAt;
        private volatile long failedAt;

        Feed(String name, String url, long refreshMs, Function<JsonElement, T> parser) {
            this.name = name;
            this.url = url;
            this.refreshMs = refreshMs;
            this.parser = parser;
        }

        T get() {
            return value;
        }

        void ensure() {
            long now = System.currentTimeMillis();
            if (now - failedAt < RETRY_MS) {
                return;
            }
            if (value != null && (refreshMs <= 0 || now - loadedAt < refreshMs)) {
                return;
            }
            if (!loading.compareAndSet(false, true)) {
                return;
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", userAgent())
                    .header("Accept", "application/json")
                    .header("Accept-Encoding", "gzip")
                    .GET()
                    .build();
            http().sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                    .thenApply(this::read)
                    .whenComplete((result, error) -> {
                        if (error == null && result != null) {
                            value = result;
                            loadedAt = System.currentTimeMillis();
                            generation++;
                        } else {
                            failedAt = System.currentTimeMillis();
                            QZA.LOGGER.warn("Could not load the {} prices: {}", name,
                                    error == null ? "empty answer" : error.toString());
                        }
                        loading.set(false);
                    });
        }

        private T read(HttpResponse<InputStream> response) {
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("answered " + response.statusCode());
                }
                boolean gzip = response.headers().firstValue("Content-Encoding")
                        .map(value -> value.equalsIgnoreCase("gzip")).orElse(false);
                try (InputStream stream = gzip ? new GZIPInputStream(body) : body;
                     Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    return parser.apply(JsonParser.parseReader(reader));
                }
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }
}
