package com.qza.itemlist;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RepoItem {
    private static final Identifier MODEL_PROBE = Identifier.withDefaultNamespace("stone");

    public final String id;
    public final String displayName;
    public final String plainName;
    public final List<String> lore;
    public final String craftText;
    public final boolean vanilla;
    public final boolean mob;

    final String itemId;
    final int potion;
    final String texture;
    final String signature;
    final UUID skullId;
    final int colour;
    final boolean glint;
    final Identifier model;
    final String[] titleWords;
    final String[] loreWords;

    private static Object frameToken;
    private static ItemStack coinStack;

    private ItemStack stack;
    private Object stackToken;
    private List<Component> tooltip;

    RepoItem(String id, String displayName, String plainName, List<String> lore, String craftText,
             boolean vanilla, boolean mob, String itemId, int potion, String texture, String signature,
             UUID skullId, int colour, boolean glint, Identifier model) {
        this.id = id;
        this.displayName = displayName;
        this.plainName = plainName;
        this.lore = lore;
        this.craftText = craftText;
        this.vanilla = vanilla;
        this.mob = mob;
        this.itemId = itemId;
        this.potion = potion;
        this.texture = texture;
        this.signature = signature;
        this.skullId = skullId;
        this.colour = colour;
        this.glint = glint;
        this.model = model;
        this.titleWords = ItemSearch.words(displayName);
        this.loreWords = ItemSearch.words(String.join(" ", lore));
    }

    static void beginFrame() {
        frameToken = Minecraft.getInstance().getModelManager().getItemModel(MODEL_PROBE);
    }

    public ItemStack stack() {
        if (stack == null || frameToken != stackToken) {
            stack = build();
            stackToken = frameToken;
        }
        return stack;
    }

    public List<Component> tooltip() {
        if (tooltip == null) {
            List<Component> lines = new ArrayList<>(lore.size() + 1);
            lines.add(Component.literal(displayName));
            for (String line : lore) {
                lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
            }
            tooltip = List.copyOf(lines);
        }
        return tooltip;
    }

    private ItemStack build() {
        Item base = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(itemId));
        if (base == null || base == Items.AIR) {
            base = Items.BARRIER;
        }
        ItemStack built = new ItemStack(base);

        if (potion >= 0) {
            built.set(DataComponents.POTION_CONTENTS, new PotionContents(LegacyItems.potionHolder(potion)));
        }
        if (texture != null && base == Items.PLAYER_HEAD) {
            built.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile()));
        }
        if (colour >= 0) {
            built.set(DataComponents.DYED_COLOR, new DyedItemColor(colour));
        }
        if (glint) {
            built.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        if (model != null && !model.equals(built.get(DataComponents.ITEM_MODEL)) && modelLoaded(model)) {
            built.set(DataComponents.ITEM_MODEL, model);
        }
        return built;
    }

    private GameProfile profile() {
        UUID uuid = skullId != null ? skullId
                : UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8));
        Property property = signature != null
                ? new Property("textures", texture, signature)
                : new Property("textures", texture);
        return new GameProfile(uuid, "", new PropertyMap(ImmutableMultimap.of("textures", property)));
    }

    private static boolean modelLoaded(Identifier id) {
        return !(Minecraft.getInstance().getModelManager().getItemModel(id) instanceof MissingItemModel);
    }

    static ItemStack coins() {
        if (coinStack == null) {
            coinStack = new ItemStack(Items.GOLD_NUGGET);
            coinStack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return coinStack;
    }
}
