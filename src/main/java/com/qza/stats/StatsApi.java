package com.qza.stats;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qza.QZA;
import com.qza.config.ConfigManager;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Reads dungeon stats from the QZA stats proxy.
 *
 * The proxy holds the Hypixel API key, so nothing secret lives in the mod. See
 * worker/README.md for deploying one. Results are cached briefly here as well
 * as at the proxy, so a player spamming "lf inv" costs one request.
 */
public final class StatsApi {
    /**
     * The deployed stats proxy, so players need no setup. Not a secret: it only
     * holds the Hypixel key server side. Overridden by statsProxyUrl in the
     * config when that is set.
     */
    private static final String DEFAULT_PROXY = "https://qza-stats.qza.workers.dev";

    private static final Pattern VALID_IGN = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final long CACHE_MILLIS = 120_000L;
    private static final int MAX_CACHE = 64;

    private static final Map<String, Entry> CACHE = new LinkedHashMap<>();

    private static HttpClient client;

    private StatsApi() {
    }

    public record Result(PlayerStats stats, String error) {
        public boolean ok() {
            return stats != null;
        }

        static Result of(PlayerStats stats) {
            return new Result(stats, null);
        }

        static Result failed(String message) {
            return new Result(null, message);
        }
    }

    private record Entry(Result result, long at) {
    }

    public static boolean configured() {
        return !baseUrl().isEmpty();
    }

    private static String baseUrl() {
        String configured = ConfigManager.get().statsProxyUrl;
        String base = configured == null || configured.isBlank() ? DEFAULT_PROXY : configured.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static synchronized HttpClient http() {
        if (client == null) {
            client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
        return client;
    }

    private static synchronized Result cached(String key) {
        Entry entry = CACHE.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() - entry.at() > CACHE_MILLIS) {
            CACHE.remove(key);
            return null;
        }
        return entry.result();
    }

    private static synchronized void remember(String key, Result result) {
        if (CACHE.size() >= MAX_CACHE) {
            var iterator = CACHE.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        CACHE.put(key, new Entry(result, System.currentTimeMillis()));
    }

    public static CompletableFuture<Result> fetch(String ign) {
        if (ign == null || !VALID_IGN.matcher(ign).matches()) {
            return CompletableFuture.completedFuture(
                    Result.failed("\"" + ign + "\" is not a valid name"));
        }
        return lookup("name=" + URLEncoder.encode(ign, StandardCharsets.UTF_8),
                ign.toLowerCase(java.util.Locale.ROOT), ign);
    }

    /** Party members arrive as uuids, which the proxy accepts directly. */
    public static CompletableFuture<Result> fetchByUuid(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(Result.failed("No uuid"));
        }
        String flat = uuid.toString().replace("-", "");
        return lookup("uuid=" + flat, "uuid:" + flat, flat);
    }

    private static CompletableFuture<Result> lookup(String query, String key, String label) {
        String base = baseUrl();
        if (base.isEmpty()) {
            return CompletableFuture.completedFuture(Result.failed(
                    "Stats proxy is not set up - see worker/README.md"));
        }

        Result hit = cached(key);
        if (hit != null) {
            return CompletableFuture.completedFuture(hit);
        }

        URI uri = URI.create(base + "/stats?" + query);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .header("User-Agent", "QZA-mod")
                .GET()
                .build();

        return http().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, error) -> {
                    Result result = interpret(response, error, label);
                    remember(key, result);
                    return result;
                });
    }

    private static Result interpret(HttpResponse<String> response, Throwable error, String ign) {
        if (error != null) {
            QZA.LOGGER.warn("Stats lookup for {} failed", ign, error);
            return Result.failed("Could not reach the stats proxy");
        }

        try {
            JsonElement parsed = JsonParser.parseString(response.body());
            if (!parsed.isJsonObject()) {
                return Result.failed("Stats proxy sent something unreadable");
            }
            JsonObject body = parsed.getAsJsonObject();

            if (!bool(body, "ok")) {
                String message = body.has("error") && body.get("error").isJsonPrimitive()
                        ? body.get("error").getAsString()
                        : "Stats lookup failed (" + response.statusCode() + ")";
                return Result.failed(message);
            }

            JsonObject runs = object(body, "runs");
            JsonObject pb = object(body, "pb");

            return Result.of(new PlayerStats(
                    string(body, "name", ign),
                    string(body, "uuid", ""),
                    string(body, "class", ""),
                    longOf(body, "cataExp"),
                    longOf(body, "secrets"),
                    nullableInt(body, "magicalPower"),
                    longMap(object(pb, "cata")),
                    longMap(object(pb, "master")),
                    intMap(object(runs, "cata")),
                    intMap(object(runs, "master"))));
        } catch (Exception e) {
            QZA.LOGGER.warn("Could not read stats for {}", ign, e);
            return Result.failed("Stats proxy sent something unreadable");
        }
    }

    private static boolean bool(JsonObject source, String key) {
        return source.has(key) && source.get(key).isJsonPrimitive()
                && source.get(key).getAsJsonPrimitive().isBoolean()
                && source.get(key).getAsBoolean();
    }

    private static JsonObject object(JsonObject source, String key) {
        if (source != null && source.has(key) && source.get(key).isJsonObject()) {
            return source.getAsJsonObject(key);
        }
        return new JsonObject();
    }

    private static String string(JsonObject source, String key, String fallback) {
        if (source.has(key) && source.get(key).isJsonPrimitive()) {
            String value = source.get(key).getAsString();
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    /**
     * Null stays null rather than becoming zero, so a value the proxy could not
     * read is never shown as a real number.
     */
    private static Integer nullableInt(JsonObject source, String key) {
        if (!source.has(key) || source.get(key).isJsonNull()
                || !source.get(key).isJsonPrimitive()) {
            return null;
        }
        try {
            return Math.max(0, source.get(key).getAsInt());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long longOf(JsonObject source, String key) {
        try {
            if (source.has(key) && source.get(key).isJsonPrimitive()) {
                long value = source.get(key).getAsLong();
                return Math.max(0L, value);
            }
        } catch (NumberFormatException ignored) {
            // Treated as absent.
        }
        return 0L;
    }

    private static Map<Integer, Long> longMap(JsonObject source) {
        Map<Integer, Long> out = new HashMap<>();
        for (String key : source.keySet()) {
            Integer floor = floorKey(key);
            if (floor == null) {
                continue;
            }
            try {
                long value = source.get(key).getAsLong();
                if (value > 0) {
                    out.put(floor, value);
                }
            } catch (Exception ignored) {
                // Skip anything that is not a number.
            }
        }
        return Map.copyOf(out);
    }

    private static Map<Integer, Integer> intMap(JsonObject source) {
        Map<Integer, Integer> out = new HashMap<>();
        for (String key : source.keySet()) {
            Integer floor = floorKey(key);
            if (floor == null) {
                continue;
            }
            try {
                int value = source.get(key).getAsInt();
                if (value > 0) {
                    out.put(floor, value);
                }
            } catch (Exception ignored) {
                // Skip anything that is not a number.
            }
        }
        return Map.copyOf(out);
    }

    private static Integer floorKey(String raw) {
        try {
            int floor = Integer.parseInt(raw.trim());
            return floor >= 0 && floor <= 7 ? floor : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
