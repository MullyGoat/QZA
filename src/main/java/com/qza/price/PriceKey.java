package com.qza.price;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qza.itemlist.ItemRepo;
import com.qza.mixin.CustomDataAccessor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Locale;

public final class PriceKey {
    static final List<String> TIERS = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");

    private static final List<String> COLOURED_HATS = List.of("PARTY_HAT_CRAB", "PARTY_HAT_CRAB_ANIMATED",
            "BALLOON_HAT_2024", "BALLOON_HAT_2025", "CAKE_HAT_2026");
    private static final long MIDAS_SWORD_BID = 50_000_000L;
    private static final long MIDAS_STAFF_BID = 100_000_000L;

    private PriceKey() {
    }

    static CompoundTag tag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : ((CustomDataAccessor) (Object) data).qzaTag();
    }

    public static String of(ItemStack stack) {
        CompoundTag tag = tag(stack);
        if (tag == null) {
            return null;
        }
        String id = tag.getStringOr("id", "");
        return id.isEmpty() ? null : of(id, tag, stack.getHoverName().getString());
    }

    static String of(String id, CompoundTag tag, String name) {
        if (tag.contains("is_shiny")) {
            return "SHINY_" + id;
        }
        if (id.indexOf(';') > 0) {
            return fromRepoId(id);
        }
        switch (id) {
            case "ENCHANTED_BOOK" -> {
                CompoundTag enchants = tag.getCompoundOrEmpty("enchantments");
                if (enchants.size() == 1) {
                    String enchant = enchants.keySet().iterator().next();
                    return enchantKey(enchant, enchants.getIntOr(enchant, 0));
                }
            }
            case "PET" -> {
                Pet pet = Pet.of(tag);
                if (pet != null) {
                    return pet.key(pet.bracket());
                }
            }
            case "POTION" -> {
                String potion = tag.getStringOr("potion", "");
                if (!potion.isEmpty()) {
                    StringBuilder key = new StringBuilder(potion.toUpperCase(Locale.ROOT))
                            .append("_POTION_").append(tag.getIntOr("potion_level", 0));
                    if (tag.getBooleanOr("enhanced", false)) {
                        key.append("_ENHANCED");
                    }
                    if (tag.getBooleanOr("extended", false)) {
                        key.append("_EXTENDED");
                    }
                    if (tag.getBooleanOr("splash", false)) {
                        key.append("_SPLASH");
                    }
                    return key.toString();
                }
            }
            case "RUNE", "UNIQUE_RUNE" -> {
                CompoundTag runes = tag.getCompoundOrEmpty("runes");
                if (!runes.isEmpty()) {
                    String rune = runes.keySet().iterator().next();
                    return runeKey(rune, runes.getIntOr(rune, 0));
                }
            }
            case "ATTRIBUTE_SHARD" -> {
                int cut = name.indexOf(" Shard");
                String shard = cut < 0 ? null : ItemRepo.constants().shardsByName()
                        .get(name.substring(0, cut).trim().toLowerCase(Locale.ROOT));
                if (shard != null) {
                    return shard;
                }
            }
            case "NEW_YEAR_CAKE" -> {
                return id + "_" + tag.getIntOr("new_years_cake", 0);
            }
            case "PARTY_HAT_SLOTH" -> {
                String emoji = tag.getStringOr("party_hat_emoji", "");
                if (!emoji.isEmpty()) {
                    return id + "_" + emoji.toUpperCase(Locale.ROOT);
                }
            }
            case "MIDAS_SWORD" -> {
                if (tag.getLongOr("winning_bid", 0) >= MIDAS_SWORD_BID) {
                    return id + "_50M";
                }
            }
            case "MIDAS_STAFF" -> {
                if (tag.getLongOr("winning_bid", 0) >= MIDAS_STAFF_BID) {
                    return id + "_100M";
                }
            }
            case "ABICASE" -> {
                String model = tag.getStringOr("model", "");
                if (!model.isEmpty()) {
                    return id + "_" + model.toUpperCase(Locale.ROOT);
                }
            }
            default -> {
                if (COLOURED_HATS.contains(id)) {
                    String colour = tag.getStringOr("party_hat_color", "");
                    if (!colour.isEmpty()) {
                        return id + "_" + colour.toUpperCase(Locale.ROOT);
                    }
                }
            }
        }
        return id;
    }

    static String enchantKey(String enchant, int level) {
        return "ENCHANTMENT_" + enchant.toUpperCase(Locale.ROOT) + "_" + level;
    }

    static String runeKey(String rune, int level) {
        return rune.toUpperCase(Locale.ROOT) + "_RUNE_" + level;
    }

    private static String fromRepoId(String id) {
        ItemRepo.Constants constants = ItemRepo.constants();
        String stock = constants.stockById().get(id);
        if (stock != null) {
            return stock;
        }
        String shard = constants.shardsById().get(id);
        if (shard != null) {
            return shard;
        }
        int split = id.indexOf(';');
        String base = id.substring(0, split);
        String level = id.substring(split + 1);
        if (base.endsWith("_RUNE")) {
            return base + "_" + level;
        }
        if (base.startsWith("POTION_")) {
            return base.substring("POTION_".length()) + "_POTION_" + level;
        }
        JsonObject types = constants.pets().has("pet_types") && constants.pets().get("pet_types").isJsonObject()
                ? constants.pets().getAsJsonObject("pet_types") : new JsonObject();
        if (types.has(base) && level.length() == 1 && Character.isDigit(level.charAt(0))) {
            int tier = level.charAt(0) - '0';
            if (tier < TIERS.size()) {
                return "LVL_1_" + TIERS.get(tier) + "_" + base;
            }
        }
        return id;
    }

    record Pet(String type, String tier, double exp, String heldItem, String skin, int candy) {
        static Pet of(CompoundTag tag) {
            String raw = tag.getStringOr("petInfo", "");
            if (raw.isEmpty()) {
                return null;
            }
            try {
                JsonObject info = JsonParser.parseString(raw).getAsJsonObject();
                String type = text(info, "type");
                String tier = text(info, "tier");
                if (type.isEmpty() || tier.isEmpty()) {
                    return null;
                }
                double exp = info.has("exp") && info.get("exp").isJsonPrimitive() ? info.get("exp").getAsDouble() : 0;
                int candy = info.has("candyUsed") && info.get("candyUsed").isJsonPrimitive()
                        ? info.get("candyUsed").getAsInt() : 0;
                return new Pet(type, tier, exp, text(info, "heldItem"), text(info, "skin"), candy);
            } catch (RuntimeException e) {
                return null;
            }
        }

        private static String text(JsonObject json, String key) {
            return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : "";
        }

        String key(int level) {
            return "LVL_" + level + "_" + tier + "_" + type;
        }

        int level() {
            return PetLevels.level(type, tier, exp);
        }

        int bracket() {
            int level = level();
            if (level >= 200) {
                return 200;
            }
            return level >= 100 ? 100 : 1;
        }
    }
}
