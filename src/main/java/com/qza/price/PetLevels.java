package com.qza.price;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qza.itemlist.ItemRepo;

import java.util.ArrayList;
import java.util.List;

final class PetLevels {
    static final int MAX_LEVEL = 100;
    private static final int APPEND_LEVELS = 1;

    private PetLevels() {
    }

    static int level(String type, String tier, double exp) {
        List<Double> curve = curve(type, tier);
        int level = 1;
        double left = exp;
        for (double need : curve) {
            if (left < need) {
                break;
            }
            left -= need;
            level++;
        }
        return Math.min(level, curve.size() + 1);
    }

    static double expToLevel(String type, String tier, int target) {
        List<Double> curve = curve(type, tier);
        double total = 0;
        for (int i = 0; i < Math.min(target - 1, curve.size()); i++) {
            total += curve.get(i);
        }
        return total;
    }

    static int maxLevel(String type) {
        JsonObject custom = custom(type);
        return custom != null && custom.has("max_level") ? custom.get("max_level").getAsInt() : MAX_LEVEL;
    }

    private static List<Double> curve(String type, String tier) {
        JsonObject pets = ItemRepo.constants().pets();
        List<Double> out = new ArrayList<>();
        JsonArray levels = array(pets, "pet_levels");
        if (levels == null) {
            return out;
        }
        int offset = offset(pets, type, tier);
        for (int i = offset; i < Math.min(levels.size(), offset + MAX_LEVEL - 1); i++) {
            out.add(levels.get(i).getAsDouble());
        }
        JsonObject custom = custom(type);
        JsonArray extra = custom == null ? null : array(custom, "pet_levels");
        if (extra != null && custom.has("type") && custom.get("type").getAsInt() == APPEND_LEVELS) {
            for (JsonElement need : extra) {
                out.add(need.getAsDouble());
            }
        }
        int max = maxLevel(type);
        while (out.size() > max - 1) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    private static int offset(JsonObject pets, String type, String tier) {
        JsonObject custom = custom(type);
        JsonObject offsets = custom != null && custom.has("rarity_offset") && custom.get("rarity_offset").isJsonObject()
                ? custom.getAsJsonObject("rarity_offset")
                : pets.has("pet_rarity_offset") && pets.get("pet_rarity_offset").isJsonObject()
                ? pets.getAsJsonObject("pet_rarity_offset") : null;
        return offsets != null && offsets.has(tier) ? offsets.get(tier).getAsInt() : 0;
    }

    private static JsonObject custom(String type) {
        JsonObject pets = ItemRepo.constants().pets();
        if (!pets.has("custom_pet_leveling") || !pets.get("custom_pet_leveling").isJsonObject()) {
            return null;
        }
        JsonObject all = pets.getAsJsonObject("custom_pet_leveling");
        return all.has(type) && all.get(type).isJsonObject() ? all.getAsJsonObject(type) : null;
    }

    private static JsonArray array(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonArray() ? json.getAsJsonArray(key) : null;
    }
}
