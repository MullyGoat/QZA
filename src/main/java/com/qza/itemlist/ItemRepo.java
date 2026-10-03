package com.qza.itemlist;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qza.QZA;
import com.qza.config.ConfigManager;
import com.qza.util.IgnUtil;
import net.minecraft.resources.Identifier;

import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ItemRepo {
    private static final String COMMIT_URL =
            "https://api.github.com/repos/NotEnoughUpdates/NotEnoughUpdates-REPO/commits/master";
    private static final String ARCHIVE_URL =
            "https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/archive/%s.zip";
    private static final String LATEST_ARCHIVE_URL =
            "https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/archive/refs/heads/master.zip";
    private static final long RETRY_MS = 60_000L;
    private static final long RECHECK_MS = 30 * 60_000L;
    private static final String BOOK = "minecraft:enchanted_book";

    private static final Pattern MOB = Pattern.compile(".*(_MONSTER|_NPC|_ANIMAL|_MINIBOSS|_BOSS|_SC)$");
    private static final Pattern TEXTURE = Pattern.compile("Value:\"([A-Za-z0-9+/=]+)\"");
    private static final Pattern SIGNATURE = Pattern.compile("Signature:\"([A-Za-z0-9+/=]+)\"");
    private static final Pattern SKULL_ID = Pattern.compile("SkullOwner:\\{Id:\"([0-9a-fA-F-]{36})\"");
    private static final Pattern COLOUR = Pattern.compile("\\bcolor:(\\d+)");
    private static final Pattern MODEL = Pattern.compile("ItemModel:\"([a-z0-9_.-]+:[a-z0-9_./-]+)\"");
    private static final String[] GRID = {"A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"};

    public enum State { IDLE, LOADING, READY, FAILED }

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "QZA Item List");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private static volatile State state = State.IDLE;
    private static volatile String status = "";
    private static volatile long failedAt;
    private static volatile long checkedAt;
    private static volatile boolean rechecking;
    private static volatile Data data = Data.EMPTY;

    record Data(List<RepoItem> items, Map<String, RepoItem> byId,
                Map<String, List<Recipe>> recipes, Map<String, List<Recipe>> usages) {
        static final Data EMPTY = new Data(List.of(), Map.of(), Map.of(), Map.of());
    }

    private ItemRepo() {
    }

    public static State state() {
        return state;
    }

    public static String status() {
        return status;
    }

    static Data data() {
        return data;
    }

    public static RepoItem item(String id) {
        return id == null ? null : data.byId().get(id);
    }

    public static List<Recipe> recipesFor(String id) {
        return data.recipes().getOrDefault(id, List.of());
    }

    public static List<Recipe> usagesOf(String id) {
        return data.usages().getOrDefault(id, List.of());
    }

    public static void ensureLoaded() {
        State now = state;
        long time = System.currentTimeMillis();
        if (now == State.IDLE || (now == State.FAILED && time - failedAt > RETRY_MS)) {
            start();
        } else if (now == State.READY && !rechecking && time - checkedAt > RECHECK_MS) {
            recheck();
        }
    }

    private static synchronized void start() {
        if (state == State.LOADING) {
            return;
        }
        state = State.LOADING;
        status = "Checking for item updates...";
        WORKER.execute(ItemRepo::load);
    }

    private static synchronized void recheck() {
        if (rechecking || state != State.READY) {
            return;
        }
        rechecking = true;
        checkedAt = System.currentTimeMillis();
        WORKER.execute(() -> {
            try {
                update();
            } catch (Throwable e) {
                QZA.LOGGER.warn("Could not update the item list", e);
            } finally {
                rechecking = false;
            }
        });
    }

    private static void update() throws Exception {
        Path dir = ConfigManager.qzaDir().resolve("itemlist");
        Path zip = dir.resolve("repo.zip");
        Path commitFile = dir.resolve("commit.txt");
        String have = Files.exists(commitFile) ? Files.readString(commitFile).trim() : "";
        String latest = latestCommit();
        if (latest.equals(have) && Files.exists(zip)) {
            return;
        }
        Path fresh = dir.resolve("update.zip");
        try {
            download(String.format(ARCHIVE_URL, latest), fresh);
            Data parsed = parse(fresh);
            Files.move(fresh, zip, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            Files.writeString(commitFile, latest);
            data = parsed;
            QZA.LOGGER.info("Item list updated to {} items", parsed.items().size());
        } finally {
            Files.deleteIfExists(fresh);
        }
    }

    private static void load() {
        try {
            Path dir = ConfigManager.qzaDir().resolve("itemlist");
            Files.createDirectories(dir);
            Path zip = dir.resolve("repo.zip");
            Path commitFile = dir.resolve("commit.txt");
            String have = Files.exists(commitFile) ? Files.readString(commitFile).trim() : "";

            String latest = null;
            checkedAt = System.currentTimeMillis();
            try {
                latest = latestCommit();
            } catch (Exception e) {
                QZA.LOGGER.warn("Could not check the item list for updates: {}", e.toString());
            }

            if (latest != null && (!latest.equals(have) || !Files.exists(zip))) {
                status = "Downloading items...";
                try {
                    download(String.format(ARCHIVE_URL, latest), zip);
                    Files.writeString(commitFile, latest);
                } catch (Exception e) {
                    QZA.LOGGER.warn("Could not download the item list: {}", e.toString());
                }
            } else if (latest == null && !Files.exists(zip)) {
                status = "Downloading items...";
                try {
                    download(LATEST_ARCHIVE_URL, zip);
                    Files.deleteIfExists(commitFile);
                } catch (Exception e) {
                    QZA.LOGGER.warn("Could not download the item list: {}", e.toString());
                }
            }
            if (!Files.exists(zip)) {
                fail("Could not download the item list");
                return;
            }

            status = "Loading items...";
            long started = System.currentTimeMillis();
            try {
                data = parse(zip);
            } catch (Exception e) {
                Files.deleteIfExists(zip);
                Files.deleteIfExists(commitFile);
                throw e;
            }
            state = State.READY;
            status = "";
            QZA.LOGGER.info("Item list loaded {} items in {} ms", data.items().size(),
                    System.currentTimeMillis() - started);
        } catch (Throwable e) {
            QZA.LOGGER.warn("Could not load the item list", e);
            fail("Could not load the item list");
        }
    }

    private static void fail(String message) {
        status = message;
        failedAt = System.currentTimeMillis();
        state = State.FAILED;
    }

    private static HttpClient http() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private static String latestCommit() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(COMMIT_URL))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "QZA")
                .GET()
                .build();
        HttpResponse<String> response = http().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("GitHub answered " + response.statusCode());
        }
        String sha = JsonParser.parseString(response.body()).getAsJsonObject().get("sha").getAsString();
        if (!sha.matches("[0-9a-f]{40}")) {
            throw new IllegalStateException("Unexpected commit id");
        }
        return sha;
    }

    private static void download(String url, Path target) throws Exception {
        Path temp = target.resolveSibling(target.getFileName() + ".part");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(3))
                .header("User-Agent", "QZA")
                .GET()
                .build();
        HttpResponse<Path> response = http().send(request, HttpResponse.BodyHandlers.ofFile(temp));
        if (response.statusCode() != 200) {
            Files.deleteIfExists(temp);
            throw new IllegalStateException("GitHub answered " + response.statusCode());
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static Data parse(Path zip) throws Exception {
        List<JsonObject> raw = new ArrayList<>();
        List<String> files = new ArrayList<>();
        JsonObject petNumbers = new JsonObject();
        JsonObject pets = new JsonObject();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                int slash = name.indexOf('/');
                String path = slash < 0 ? name : name.substring(slash + 1);
                if (path.equals("constants/petnums.json") || path.equals("constants/pets.json")) {
                    try (Reader reader = new InputStreamReader(file.getInputStream(entry), StandardCharsets.UTF_8)) {
                        JsonElement json = JsonParser.parseReader(reader);
                        if (json.isJsonObject()) {
                            if (path.endsWith("petnums.json")) {
                                petNumbers = json.getAsJsonObject();
                            } else {
                                pets = json.getAsJsonObject();
                            }
                        }
                    } catch (Exception e) {
                        QZA.LOGGER.debug("Skipping {}: {}", path, e.toString());
                    }
                    continue;
                }
                if (entry.isDirectory() || !path.startsWith("items/") || !path.endsWith(".json")) {
                    continue;
                }
                try (Reader reader = new InputStreamReader(file.getInputStream(entry), StandardCharsets.UTF_8)) {
                    JsonElement json = JsonParser.parseReader(reader);
                    if (json.isJsonObject()) {
                        raw.add(json.getAsJsonObject());
                        files.add(path.substring("items/".length(), path.length() - ".json".length()));
                    }
                } catch (Exception e) {
                    QZA.LOGGER.debug("Skipping item {}: {}", path, e.toString());
                }
            }
        }

        Map<String, RepoItem> byId = new HashMap<>(raw.size() * 2);
        List<RepoItem> items = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            RepoItem item = item(raw.get(i), files.get(i), petNumbers, pets);
            if (item != null && !byId.containsKey(item.id)) {
                byId.put(item.id, item);
                items.add(item);
            }
        }
        items.sort(Comparator.comparing(ItemRepo::isBook)
                .thenComparing(ItemRepo::sortName)
                .thenComparingInt(ItemRepo::bookLevel)
                .thenComparing(item -> item.id));

        Map<String, List<Recipe>> recipes = new HashMap<>();
        Map<String, List<Recipe>> usages = new HashMap<>();
        for (int i = 0; i < raw.size(); i++) {
            JsonObject json = raw.get(i);
            String id = string(json, "internalname", files.get(i));
            for (Recipe recipe : recipes(json, id)) {
                for (Ingredient output : recipe.outputs()) {
                    if (output != null) {
                        recipes.computeIfAbsent(output.id(), key -> new ArrayList<>()).add(recipe);
                    }
                }
                Set<String> seen = new LinkedHashSet<>();
                for (Ingredient input : recipe.inputs()) {
                    if (input != null && seen.add(input.id())) {
                        usages.computeIfAbsent(input.id(), key -> new ArrayList<>()).add(recipe);
                    }
                }
            }
        }

        return new Data(Collections.unmodifiableList(items), Collections.unmodifiableMap(byId),
                freeze(recipes), freeze(usages));
    }

    private static Map<String, List<Recipe>> freeze(Map<String, List<Recipe>> map) {
        Map<String, List<Recipe>> out = new HashMap<>(map.size() * 2);
        map.forEach((key, value) -> out.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(out);
    }

    private static String sortName(RepoItem item) {
        if (isBook(item)) {
            int split = item.id.indexOf(';');
            return split < 0 ? "" : item.id.substring(0, split);
        }
        String name = ItemSearch.clean(item.plainName);
        return name.startsWith("lvl ") ? name.replaceFirst("^lvl [^ ]+ ", "") : name;
    }

    private static boolean isBook(RepoItem item) {
        return BOOK.equals(item.itemId) && item.plainName.contains("Book");
    }

    private static int bookLevel(RepoItem item) {
        int split = item.id.indexOf(';');
        if (!isBook(item) || split < 0) {
            return 0;
        }
        try {
            return Integer.parseInt(item.id.substring(split + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static RepoItem item(JsonObject json, String file, JsonObject petNumbers, JsonObject pets) {
        String id = string(json, "internalname", file);
        if (id.isEmpty() || !json.has("itemid")) {
            return null;
        }
        String legacyId = string(json, "itemid", "minecraft:barrier");
        int damage = integer(json, "damage", 0);
        String name = string(json, "displayname", id);
        String nbt = string(json, "nbttag", "");
        List<String> lore = strings(json, "lore");

        Map<String, String> petText = PetText.replacements(id, petNumbers, pets);
        if (!petText.isEmpty()) {
            name = PetText.apply(name, petText);
            List<String> filled = new ArrayList<>(lore.size());
            for (String line : lore) {
                filled.add(PetText.apply(line, petText));
            }
            lore = List.copyOf(filled);
        }

        String texture = find(TEXTURE, nbt);
        String skull = find(SKULL_ID, nbt);
        UUID skullId = null;
        if (skull != null) {
            try {
                skullId = UUID.fromString(skull);
            } catch (IllegalArgumentException ignored) {
            }
        }
        String colourText = find(COLOUR, nbt);
        int colour = -1;
        if (colourText != null) {
            try {
                colour = Integer.parseInt(colourText);
            } catch (NumberFormatException ignored) {
            }
        }
        String modelText = find(MODEL, nbt);
        Identifier model = modelText == null ? null : Identifier.tryParse(modelText);

        boolean vanilla = false;
        try {
            vanilla = json.has("vanilla") && json.get("vanilla").getAsBoolean();
        } catch (Exception ignored) {
        }

        return new RepoItem(id, name, IgnUtil.stripCodes(name).trim(), lore,
                string(json, "crafttext", ""), vanilla, MOB.matcher(id).matches(),
                LegacyItems.modernId(legacyId, damage), LegacyItems.potion(legacyId, damage),
                texture, find(SIGNATURE, nbt), skullId, colour,
                nbt.contains("ench:[") || nbt.contains("CustomPotionEffects:["), model);
    }

    private static List<Recipe> recipes(JsonObject json, String id) {
        List<Recipe> out = new ArrayList<>();
        try {
            if (json.has("recipe") && json.get("recipe").isJsonObject()) {
                out.add(crafting(json.getAsJsonObject("recipe"), json, id));
            }
            if (json.has("recipes") && json.get("recipes").isJsonArray()) {
                for (JsonElement element : json.getAsJsonArray("recipes")) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    Recipe recipe = recipe(element.getAsJsonObject(), json, id);
                    if (recipe != null && !out.contains(recipe)) {
                        out.add(recipe);
                    }
                }
            }
        } catch (Exception e) {
            QZA.LOGGER.debug("Skipping recipes of {}: {}", id, e.toString());
        }
        return out;
    }

    private static Recipe recipe(JsonObject recipe, JsonObject item, String id) {
        String type = string(recipe, "type", "crafting");
        String output = string(recipe, "overrideOutputId", id);
        return switch (type) {
            case "crafting" -> crafting(recipe, item, id);
            case "forge" -> new Recipe.Forge(ingredients(recipe, "inputs"),
                    new Ingredient(output, number(recipe, "count", 1)), (long) number(recipe, "duration", 0));
            case "npc_shop" -> {
                Ingredient result = Ingredient.parse(string(recipe, "result", ""));
                yield result == null ? null : new Recipe.Shop(id, ingredients(recipe, "cost"), result);
            }
            case "trade" -> {
                Ingredient cost = Ingredient.parse(string(recipe, "cost", ""));
                Ingredient result = Ingredient.parse(string(recipe, "result", ""));
                yield cost == null || result == null ? null : new Recipe.Trade(cost, result);
            }
            case "drops" -> drops(recipe, item, id);
            case "katgrade" -> {
                Ingredient input = Ingredient.parse(string(recipe, "input", ""));
                Ingredient result = Ingredient.parse(string(recipe, "output", ""));
                yield input == null || result == null ? null : new Recipe.Kat(input, result,
                        ingredients(recipe, "items"), (long) number(recipe, "coins", 0),
                        (long) number(recipe, "time", 0));
            }
            default -> null;
        };
    }

    private static Recipe crafting(JsonObject recipe, JsonObject item, String id) {
        Ingredient[] grid = new Ingredient[9];
        for (int i = 0; i < GRID.length; i++) {
            grid[i] = Ingredient.parse(string(recipe, GRID[i], ""));
        }
        String text = string(recipe, "crafttext", string(item, "crafttext", ""));
        Ingredient output = new Ingredient(string(recipe, "overrideOutputId", id), number(recipe, "count", 1));
        return new Recipe.Crafting(Collections.unmodifiableList(Arrays.asList(grid)), output, text);
    }

    private static Recipe drops(JsonObject recipe, JsonObject item, String id) {
        List<Recipe.Drop> drops = new ArrayList<>();
        if (recipe.has("drops") && recipe.get("drops").isJsonArray()) {
            for (JsonElement element : recipe.getAsJsonArray("drops")) {
                if (element.isJsonPrimitive()) {
                    Ingredient drop = Ingredient.parse(element.getAsString());
                    if (drop != null) {
                        drops.add(new Recipe.Drop(drop, "", List.of()));
                    }
                } else if (element.isJsonObject()) {
                    JsonObject entry = element.getAsJsonObject();
                    Ingredient drop = Ingredient.parse(string(entry, "id", ""));
                    if (drop != null) {
                        drops.add(new Recipe.Drop(drop, string(entry, "chance", ""), strings(entry, "extra")));
                    }
                }
            }
        }
        if (drops.isEmpty()) {
            return null;
        }
        return new Recipe.Drops(id, string(recipe, "name", string(item, "displayname", id)),
                integer(recipe, "level", 0), (long) number(recipe, "coins", 0), (long) number(recipe, "xp", 0),
                List.copyOf(drops), strings(recipe, "extra"));
    }

    private static List<Ingredient> ingredients(JsonObject json, String key) {
        List<Ingredient> out = new ArrayList<>();
        if (!json.has(key)) {
            return out;
        }
        JsonElement element = json.get(key);
        if (element.isJsonArray()) {
            for (JsonElement entry : element.getAsJsonArray()) {
                if (entry.isJsonPrimitive()) {
                    Ingredient ingredient = Ingredient.parse(entry.getAsString());
                    if (ingredient != null) {
                        out.add(ingredient);
                    }
                }
            }
        } else if (element.isJsonPrimitive()) {
            Ingredient ingredient = Ingredient.parse(element.getAsString());
            if (ingredient != null) {
                out.add(ingredient);
            }
        }
        return out;
    }

    private static String find(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String string(JsonObject json, String key, String fallback) {
        try {
            JsonElement element = json.get(key);
            return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int integer(JsonObject json, String key, int fallback) {
        return (int) number(json, key, fallback);
    }

    private static double number(JsonObject json, String key, double fallback) {
        try {
            JsonElement element = json.get(key);
            return element != null && element.isJsonPrimitive() ? element.getAsDouble() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static List<String> strings(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        JsonArray array = element.getAsJsonArray();
        List<String> out = new ArrayList<>(array.size());
        for (JsonElement entry : array) {
            if (entry.isJsonPrimitive()) {
                out.add(entry.getAsString());
            }
        }
        return List.copyOf(out);
    }
}
