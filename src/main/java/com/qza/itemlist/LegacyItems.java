package com.qza.itemlist;

import com.mojang.serialization.Dynamic;
import com.qza.QZA;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class LegacyItems {
    private static final int LEGACY_DATA_VERSION = 99;
    private static final int SPLASH_BIT = 16384;
    private static final int AWKWARD = 16;
    private static final String BARRIER = "minecraft:barrier";

    private static final List<Holder<Potion>> POTIONS = List.of(
            Potions.WATER, Potions.REGENERATION, Potions.SWIFTNESS, Potions.FIRE_RESISTANCE,
            Potions.POISON, Potions.HEALING, Potions.NIGHT_VISION, Potions.WATER,
            Potions.WEAKNESS, Potions.STRENGTH, Potions.SLOWNESS, Potions.LEAPING,
            Potions.HARMING, Potions.WATER_BREATHING, Potions.INVISIBILITY, Potions.WATER);

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private LegacyItems() {
    }

    static String modernId(String legacyId, int damage) {
        if (legacyId == null || legacyId.isBlank()) {
            return BARRIER;
        }
        if (legacyId.equals("minecraft:potion")) {
            return (damage & SPLASH_BIT) != 0 ? "minecraft:splash_potion" : "minecraft:potion";
        }
        return CACHE.computeIfAbsent(legacyId + "|" + damage, key -> convert(legacyId, damage));
    }

    static int potion(String legacyId, int damage) {
        if (!"minecraft:potion".equals(legacyId)) {
            return -1;
        }
        return damage == AWKWARD ? AWKWARD : damage & 15;
    }

    static Holder<Potion> potionHolder(int index) {
        if (index == AWKWARD) {
            return Potions.AWKWARD;
        }
        return index >= 0 && index < POTIONS.size() ? POTIONS.get(index) : Potions.WATER;
    }

    private static String convert(String legacyId, int damage) {
        try {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", legacyId);
            tag.putByte("Count", (byte) 1);
            tag.putShort("Damage", (short) damage);
            Tag fixed = DataFixers.getDataFixer().update(References.ITEM_STACK,
                    new Dynamic<>(NbtOps.INSTANCE, tag), LEGACY_DATA_VERSION,
                    SharedConstants.getCurrentVersion().dataVersion().version()).getValue();
            if (fixed instanceof CompoundTag result) {
                String id = result.getStringOr("id", "");
                if (known(id)) {
                    return id;
                }
            }
        } catch (Throwable e) {
            QZA.LOGGER.debug("Could not convert legacy item {}:{}", legacyId, damage, e);
        }
        return known(legacyId) ? legacyId : BARRIER;
    }

    private static boolean known(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed != null && !"minecraft:air".equals(id) && BuiltInRegistries.ITEM.containsKey(parsed);
    }
}
