package com.qza.itemlist;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

final class PetText {
    private static final String[] TIERS = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};
    private static final String ARROW = "➡";

    private PetText() {
    }

    static Map<String, String> replacements(String id, JsonObject petNumbers, JsonObject pets) {
        int semi = id.lastIndexOf(';');
        if (semi <= 0) {
            return Map.of();
        }
        String pet = id.substring(0, semi);
        int tier;
        try {
            tier = Integer.parseInt(id.substring(semi + 1));
        } catch (NumberFormatException e) {
            return Map.of();
        }
        if (tier < 0 || tier >= TIERS.length) {
            return Map.of();
        }

        Map<String, String> out = new HashMap<>();
        int maxLevel = 100;
        JsonObject custom = object(object(pets, "custom_pet_leveling"), pet);
        if (custom != null && custom.has("max_level")) {
            try {
                maxLevel = custom.get("max_level").getAsInt();
            } catch (Exception ignored) {
            }
        }
        out.put("LVL", "1" + ARROW + maxLevel);

        JsonObject info = object(object(petNumbers, pet), TIERS[tier]);
        JsonObject min = object(info, "1");
        JsonObject max = object(info, "100");
        if (min == null || max == null) {
            return out;
        }

        boolean fromZero = false;
        if (info.has("stats_levelling_curve")) {
            String[] curve = info.get("stats_levelling_curve").getAsString().split(":");
            fromZero = curve.length == 3 && curve[2].equals("1");
        }
        String prefix = fromZero ? "0" + ARROW : "";

        JsonArray otherMin = array(min, "otherNums");
        JsonArray otherMax = array(max, "otherNums");
        if (otherMin != null && otherMax != null) {
            for (int i = 0; i < otherMax.size() && i < otherMin.size(); i++) {
                out.put(Integer.toString(i), prefix + format(otherMin.get(i).getAsDouble())
                        + ARROW + format(otherMax.get(i).getAsDouble()));
            }
        }

        JsonObject statMin = object(min, "statNums");
        JsonObject statMax = object(max, "statNums");
        if (statMin != null && statMax != null) {
            for (Map.Entry<String, JsonElement> entry : statMax.entrySet()) {
                double low = statMin.has(entry.getKey()) ? statMin.get(entry.getKey()).getAsDouble() : 0;
                double high = entry.getValue().getAsDouble();
                out.put(entry.getKey(), prefix + format(low) + ARROW + format(high));
            }
        }
        return out;
    }

    static String apply(String text, Map<String, String> replacements) {
        if (text == null || text.indexOf('{') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            int close = c == '{' ? text.indexOf('}', i + 1) : -1;
            if (close > i) {
                String value = replacements.get(text.substring(i + 1, close));
                if (value != null) {
                    out.append(value);
                    i = close + 1;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.format("%,d", (long) value);
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static JsonObject object(JsonObject parent, String key) {
        if (parent == null || key == null) {
            return null;
        }
        JsonElement element = parent.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }
}
