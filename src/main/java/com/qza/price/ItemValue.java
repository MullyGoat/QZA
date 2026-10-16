package com.qza.price;

import com.qza.itemlist.ItemRepo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ItemValue {
    private static final double ENCHANT = 0.85;
    private static final double SILEX = 0.75;
    private static final double GOLDEN_BOUNTY = 0.75;
    private static final double RECOMBOBULATOR = 0.8;
    private static final double FUMING = 0.6;
    private static final double ESSENCE = 0.75;
    private static final double DIVAN_SLOTS = 0.9;
    private static final double KUUDRA_SLOTS = 0.6;
    private static final double POWER_SCROLL = 0.5;
    private static final double RUNE = 0.6;
    private static final double DYE = 0.9;
    private static final double SKIN = 0.67;
    private static final double ENRICHMENT = 0.5;
    private static final double CANDY_SHARE = 0.65;
    private static final double CANDY_MAX_100 = 5_000_000;
    private static final double CANDY_MAX = 2_500_000;
    private static final double SHEN_SHARE = 0.85;
    private static final int HOT_POTATO_BOOKS = 10;
    private static final int PULSE_CHARGE = 50_000;
    private static final int PICKONIMBUS_DURABILITY = 2000;

    private static final Map<String, Double> ENCHANT_WEIGHTS = Map.of(
            "COUNTER_STRIKE", 0.2,
            "BIG_BRAIN", 0.35,
            "OVERLOAD", 0.35,
            "ULTIMATE_INFERNO", 0.35,
            "ULTIMATE_SOUL_EATER", 0.35,
            "ULTIMATE_FATAL_TEMPO", 0.65);
    private static final Set<String> FIRST_LEVEL_ONLY = Set.of("EXPERTISE", "COMPACT", "CULTIVATING", "CHAMPION",
            "HECATOMB", "TOXOPHILITE");
    private static final Set<String> BOW_BLOCKED = Set.of("OVERLOAD", "POWER", "ULTIMATE_SOUL_EATER");
    private static final Set<String> BOW_BLOCKED_ITEMS = Set.of("BONE_BOOMERANG", "DEATH_BOW");
    private static final Set<String> NO_SILEX = Set.of("PROMISING_SPADE", "STONK_PICKAXE");
    private static final Set<String> NO_CANDY_LOSS = Set.of("ENDER_DRAGON", "GOLDEN_DRAGON", "SCATHA");
    private static final Set<String> GENERIC_SLOTS = Set.of("COMBAT", "OFFENSIVE", "DEFENSIVE", "MINING",
            "UNIVERSAL", "CHISEL");
    private static final List<String> KUUDRA_ARMOUR = List.of("CRIMSON_", "AURORA_", "TERROR_", "FERVOR_", "HOLLOW_");
    private static final List<String> MASTER_STARS = List.of("FIRST_MASTER_STAR", "SECOND_MASTER_STAR",
            "THIRD_MASTER_STAR", "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR");
    private static final List<String> DRILL_PARTS = List.of("drill_part_fuel_tank", "drill_part_engine",
            "drill_part_upgrade_module");

    private record Addon(String key, String item, double weight) {
    }

    private static final List<Addon> COUNTED = List.of(
            new Addon("polarvoid", "POLARVOID_BOOK", 1.0),
            new Addon("art_of_war_count", "THE_ART_OF_WAR", 0.6),
            new Addon("wood_singularity_count", "WOOD_SINGULARITY", 0.5),
            new Addon("farming_for_dummies_count", "FARMING_FOR_DUMMIES", 0.5),
            new Addon("jalapeno_count", "JALAPENO_BOOK", 0.8),
            new Addon("mana_disintegrator_count", "MANA_DISINTEGRATOR", 0.8),
            new Addon("tuned_transmission", "TRANSMISSION_TUNER", 0.7),
            new Addon("sack_pss", "POCKET_SACK_IN_A_SACK", 0.7));
    private static final List<Addon> FLAGS = List.of(
            new Addon("ethermerge", "ETHERWARP_CONDUIT", 1.0),
            new Addon("artOfPeaceApplied", "THE_ART_OF_PEACE", 0.8),
            new Addon("divan_powder_coating", "DIVAN_POWDER_COATING", 0.8));

    private ItemValue() {
    }

    public static double estimate(ItemStack stack) {
        CompoundTag tag = PriceKey.tag(stack);
        if (tag == null) {
            return 0;
        }
        String id = tag.getStringOr("id", "");
        if (id.isEmpty()) {
            return 0;
        }
        int count = Math.max(1, stack.getCount());
        if (id.equals("PET")) {
            PriceKey.Pet pet = PriceKey.Pet.of(tag);
            return pet == null ? 0 : pet(pet) * count;
        }
        String key = PriceKey.of(id, tag, stack.getHoverName().getString());
        if (id.equals("ENCHANTED_BOOK")) {
            double book = book(tag.getCompoundOrEmpty("enchantments"));
            return (book > 0 ? book : PriceData.price(key)) * count;
        }
        return (base(id, key, tag) + extras(id, tag)) * count;
    }

    private static double base(String id, String key, CompoundTag tag) {
        double base = PriceData.price(key);
        if (id.startsWith("MIDAS_")) {
            long bid = tag.getLongOr("winning_bid", 0);
            if (!key.equals(id) && base > 0) {
                return base;
            }
            if (bid > 0) {
                return bid;
            }
        }
        if (tag.contains("price")) {
            double paid = tag.getLongOr("price", 0) * SHEN_SHARE;
            if (base <= 0 || (tag.contains("auction") && tag.contains("bid"))) {
                base = Math.max(base, paid);
            }
        }
        if (tag.contains("pickonimbus_durability")) {
            base *= Math.min(1.0, tag.getIntOr("pickonimbus_durability", PICKONIMBUS_DURABILITY)
                    / (double) PICKONIMBUS_DURABILITY);
        }
        return base;
    }

    private static double extras(String id, CompoundTag tag) {
        PriceData.ItemInfo info = PriceData.info(id);
        String category = info == null ? "" : info.category();
        double total = enchants(id, tag.getCompoundOrEmpty("enchantments"));

        if (tag.getIntOr("rarity_upgrades", 0) > 0 && !tag.contains("item_tier")) {
            total += PriceData.price("RECOMBOBULATOR_3000") * RECOMBOBULATOR;
        }
        int potatoes = tag.getIntOr("hot_potato_count", 0);
        if (potatoes > 0) {
            total += Math.min(potatoes, HOT_POTATO_BOOKS) * PriceData.price("HOT_POTATO_BOOK");
            total += Math.max(0, potatoes - HOT_POTATO_BOOKS) * PriceData.price("FUMING_POTATO_BOOK") * FUMING;
        }
        String modifier = tag.getStringOr("modifier", "");
        if (!modifier.isEmpty() && !category.equals("ACCESSORY")) {
            String stone = ItemRepo.constants().reforgeStones().get(modifier.toLowerCase(Locale.ROOT));
            if (stone != null) {
                total += PriceData.price(stone);
            }
        }
        total += stars(tag, info);
        total += gems(id, tag.getCompoundOrEmpty("gems"), info);

        for (Tag scroll : tag.getListOrEmpty("ability_scroll")) {
            total += PriceData.price(scroll.asString().orElse(""));
        }
        String power = tag.getStringOr("power_ability_scroll", "");
        if (!power.isEmpty()) {
            total += PriceData.price(power) * POWER_SCROLL;
        }
        for (String part : DRILL_PARTS) {
            String item = tag.getStringOr(part, "");
            if (!item.isEmpty()) {
                total += PriceData.price(item.toUpperCase(Locale.ROOT));
            }
        }
        for (Addon addon : COUNTED) {
            int amount = tag.getIntOr(addon.key(), 0);
            if (amount > 0) {
                total += amount * PriceData.price(addon.item()) * addon.weight();
            }
        }
        for (Addon addon : FLAGS) {
            if (tag.contains(addon.key()) && tag.getIntOr(addon.key(), 1) > 0) {
                total += PriceData.price(addon.item()) * addon.weight();
            }
        }
        if (id.equals("PULSE_RING")) {
            total += (tag.getIntOr("thunder_charge", 0) / PULSE_CHARGE) * PriceData.price("THUNDER_IN_A_BOTTLE") * 0.8;
        }
        if (tag.contains("talisman_enrichment")) {
            total += PriceData.cheapest("TALISMAN_ENRICHMENT_") * ENRICHMENT;
        }
        CompoundTag runes = tag.getCompoundOrEmpty("runes");
        if (!id.endsWith("RUNE")) {
            for (String rune : runes.keySet()) {
                total += PriceData.price(PriceKey.runeKey(rune, runes.getIntOr(rune, 0))) * RUNE;
            }
        }
        String dye = tag.getStringOr("dye_item", "");
        if (!dye.isEmpty()) {
            total += PriceData.price(dye) * DYE;
        }
        String skin = tag.getStringOr("skin", "");
        if (!skin.isEmpty()) {
            total += PriceData.price(skin) * SKIN;
        }
        return total;
    }

    private static double enchants(String id, CompoundTag enchants) {
        double total = 0;
        for (String name : enchants.keySet()) {
            String enchant = name.toUpperCase(Locale.ROOT);
            int level = enchants.getIntOr(name, 0);
            if (BOW_BLOCKED_ITEMS.contains(id) && BOW_BLOCKED.contains(enchant)) {
                continue;
            }
            if (enchant.equals("SCAVENGER")) {
                if (level >= 6) {
                    total += PriceData.price("GOLDEN_BOUNTY") * GOLDEN_BOUNTY;
                }
                continue;
            }
            if (enchant.equals("EFFICIENCY")) {
                if (level > 5 && !NO_SILEX.contains(id)) {
                    total += (level - 5) * PriceData.price("SIL_EX") * SILEX;
                }
                continue;
            }
            total += enchantPrice(enchant, level) * ENCHANT_WEIGHTS.getOrDefault(enchant, ENCHANT);
        }
        return total;
    }

    private static double enchantPrice(String enchant, int level) {
        if (FIRST_LEVEL_ONLY.contains(enchant)) {
            return PriceData.price(PriceKey.enchantKey(enchant, 1));
        }
        double price = PriceData.price(PriceKey.enchantKey(enchant, level));
        if (enchant.startsWith("ULTIMATE_") && level > 1) {
            price = Math.max(price, PriceData.price(PriceKey.enchantKey(enchant, 1)) * Math.pow(2, level - 1));
        }
        return price;
    }

    private static double book(CompoundTag enchants) {
        if (enchants.isEmpty()) {
            return 0;
        }
        if (enchants.size() == 1) {
            String name = enchants.keySet().iterator().next();
            return PriceData.price(PriceKey.enchantKey(name, enchants.getIntOr(name, 0)));
        }
        double total = 0;
        for (String name : enchants.keySet()) {
            total += PriceData.price(PriceKey.enchantKey(name, enchants.getIntOr(name, 0))) * ENCHANT;
        }
        return total;
    }

    private static double stars(CompoundTag tag, PriceData.ItemInfo info) {
        int stars = Math.max(tag.getIntOr("dungeon_item_level", 0), tag.getIntOr("upgrade_level", 0));
        if (stars <= 0 || info == null) {
            return 0;
        }
        double total = 0;
        List<List<PriceData.Cost>> tiers = info.upgrades();
        for (int i = 0; i < Math.min(stars, tiers.size()); i++) {
            for (PriceData.Cost cost : tiers.get(i)) {
                if (cost.id() == null) {
                    continue;
                }
                double weight = cost.id().startsWith("ESSENCE_") ? ESSENCE : 1.0;
                total += PriceData.price(cost.id()) * cost.amount() * weight;
            }
        }
        if (stars > 5 && tiers.size() <= 5) {
            for (int i = 0; i < Math.min(stars - 5, MASTER_STARS.size()); i++) {
                total += PriceData.price(MASTER_STARS.get(i));
            }
        }
        return total;
    }

    private static double gems(String id, CompoundTag gems, PriceData.ItemInfo info) {
        double total = 0;
        for (String slot : gems.keySet()) {
            if (slot.endsWith("_gem") || slot.equals("unlocked_slots")) {
                continue;
            }
            Tag value = gems.get(slot);
            String quality = value instanceof CompoundTag compound
                    ? compound.getStringOr("quality", "") : value == null ? "" : value.asString().orElse("");
            if (quality.isEmpty()) {
                continue;
            }
            String type = slotType(slot);
            if (GENERIC_SLOTS.contains(type)) {
                type = gems.getStringOr(slot + "_gem", "");
            }
            if (!type.isEmpty()) {
                total += PriceData.price(quality.toUpperCase(Locale.ROOT) + "_" + type.toUpperCase(Locale.ROOT) + "_GEM");
            }
        }
        if (info != null && !info.gemSlots().isEmpty()) {
            double weight = id.startsWith("DIVAN_") ? DIVAN_SLOTS : kuudraArmour(id) ? KUUDRA_SLOTS : 0;
            if (weight > 0) {
                total += unlocks(gems.getListOrEmpty("unlocked_slots"), info.gemSlots()) * weight;
            }
        }
        return total;
    }

    private static double unlocks(ListTag unlocked, List<PriceData.GemSlot> slots) {
        double total = 0;
        for (Tag entry : unlocked) {
            String slot = entry.asString().orElse("");
            String type = slotType(slot);
            int index = slotIndex(slot);
            Map<String, Integer> seen = new HashMap<>();
            for (PriceData.GemSlot candidate : slots) {
                int at = seen.merge(candidate.type(), 1, Integer::sum) - 1;
                if (candidate.type().equals(type) && at == index) {
                    for (PriceData.Cost cost : candidate.costs()) {
                        total += cost.id() == null ? cost.coins() : PriceData.price(cost.id()) * cost.amount();
                    }
                    break;
                }
            }
        }
        return total;
    }

    private static String slotType(String slot) {
        int split = slot.lastIndexOf('_');
        return split < 0 ? slot : slot.substring(0, split);
    }

    private static int slotIndex(String slot) {
        int split = slot.lastIndexOf('_');
        try {
            return split < 0 ? 0 : Integer.parseInt(slot.substring(split + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean kuudraArmour(String id) {
        for (String name : KUUDRA_ARMOUR) {
            if (id.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private static double pet(PriceKey.Pet pet) {
        double first = PriceData.price(pet.key(1));
        double hundred = PriceData.price(pet.key(100));
        double top = PriceData.price(pet.key(200));
        int level = pet.level();
        int max = PetLevels.maxLevel(pet.type());
        double price;
        if (level >= 200 && top > 0) {
            price = top;
        } else if (level > 100 && max > 100 && hundred > 0 && top > 0) {
            price = hundred + (top - hundred) * (level - 100) / (double) (max - 100);
        } else if (level >= 100 && hundred > 0) {
            price = hundred;
        } else if (first > 0 && hundred > 0) {
            double needed = PetLevels.expToLevel(pet.type(), pet.tier(), 100);
            double share = needed <= 0 ? 0 : Math.min(1.0, pet.exp() / needed);
            price = first + (hundred - first) * share;
        } else {
            price = first > 0 ? first : hundred;
        }
        if (price <= 0) {
            return 0;
        }
        if (pet.candy() > 0 && !NO_CANDY_LOSS.contains(pet.type())) {
            double most = level >= 100 ? CANDY_MAX_100 : CANDY_MAX;
            price = Math.max(price * CANDY_SHARE, price - most);
        }
        if (!pet.heldItem().isEmpty()) {
            price += PriceData.price(pet.heldItem());
        }
        if (!pet.skin().isEmpty()) {
            price += PriceData.price("PET_SKIN_" + pet.skin()) * SKIN;
        }
        return price;
    }
}
