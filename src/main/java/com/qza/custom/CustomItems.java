package com.qza.custom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.config.CustomItemEntry;
import com.qza.config.QZAConfig;
import com.qza.inventory.ItemProtection;
import com.qza.render.HandSpace;
import com.qza.render.SaberGlow;
import com.qza.tweaks.ItemInspect;
import com.qza.util.ChatUtil;
import com.qza.util.IgnUtil;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CustomItems {
    public static final String KARAMBIT = "karambit";
    public static final String BUTTERFLY = "butterfly";
    public static final String LIGHTSABER = "lightsaber";
    public static final List<String> SKINS = List.of(KARAMBIT, BUTTERFLY, LIGHTSABER);
    public static final List<String> MATERIALS = List.of("wooden", "stone", "copper", "iron", "golden", "diamond",
            "netherite");
    public static final List<String> STYLES = List.of("classic", "chosen", "vaapad", "elegant", "wanderer",
            "crossguard");
    public static final List<String> SABER_COLOURS = List.of("red", "orange", "yellow", "green", "cyan", "blue",
            "purple", "pink", "white");
    public static final Vector3f BUTTERFLY_PIVOT = HandSpace.texel(8.5f, 7.5f);

    private static final String DEFAULT_MATERIAL = "iron";
    private static final Map<String, String> STYLE_COLOURS = Map.of("classic", "red", "chosen", "blue",
            "vaapad", "purple", "elegant", "green", "wanderer", "yellow", "crossguard", "red");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float SABER_EMITTER = 1f + 8f * 0.70710678f;
    private static final Vector3f EMITTER = HandSpace.texel(SABER_EMITTER, 16f - SABER_EMITTER);
    private static final float BLADE_LENGTH = 15.5f / 16f * 0.68f;
    private static final float BLADE_RADIUS = 0.85f / 16f * 0.68f;
    private static final float IGNITE_DELAY = 0.08f;
    private static final float IGNITE_SECONDS = 1.0f;
    private static final Vector3f KARAMBIT_PIVOT = HandSpace.KARAMBIT_REVERSE.transformPosition(HandSpace.KARAMBIT_HOLD,
            new Vector3f());
    private static final float SETTLED = 10f;
    private static final RandomSource RANDOM = RandomSource.create();

    private static ItemStack partStack;
    private static boolean drawingPart;
    private static String heldKey = "";
    private static String heldUuid = "";
    private static boolean ignitePending;
    private static long litAt;

    private CustomItems() {
    }

    public record Look(String type, String variant, String colour) {
        public static Look parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String[] parts = raw.split(":");
            String type = parts[0];
            if (!SKINS.contains(type)) {
                return null;
            }
            if (LIGHTSABER.equals(type)) {
                String style = parts.length > 1 && STYLES.contains(parts[1]) ? parts[1] : STYLES.get(0);
                String colour = parts.length > 2 && SABER_COLOURS.contains(parts[2]) ? parts[2]
                        : STYLE_COLOURS.get(style);
                return new Look(type, style, colour);
            }
            String material = parts.length > 1 && MATERIALS.contains(parts[1]) ? parts[1] : DEFAULT_MATERIAL;
            return new Look(type, material, "");
        }

        public String format() {
            return colour.isEmpty() ? type + ":" + variant : type + ":" + variant + ":" + colour;
        }
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LocalPlayer player = client.player;
            ItemStack held = player == null ? ItemStack.EMPTY : player.getMainHandItem();
            String uuid = ItemProtection.uuid(held);
            String key = player == null ? "" : player.getInventory().getSelectedSlot() + ":" + uuid;
            if (key.equals(heldKey)) {
                return;
            }
            heldKey = key;
            heldUuid = uuid;
            litAt = Util.getNanos();
            ignitePending = true;
            CustomItemEntry entry = active(held);
            if (entry != null && equipOn(entry) && !LIGHTSABER.equals(entry.type)) {
                ItemInspect.equip(held, uuid, entry.type);
            }
        });
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("qza", path);
    }

    public static boolean enabled() {
        return ConfigManager.get().customItemsEnabled;
    }

    public static boolean drawingPart() {
        return drawingPart;
    }

    public static String skinLabel(String skin) {
        return switch (skin) {
            case KARAMBIT -> "Karambit";
            case BUTTERFLY -> "Butterfly Knife";
            case LIGHTSABER -> "Lightsaber";
            default -> "None";
        };
    }

    public static String variantLabel(String variant) {
        return switch (variant) {
            case "wooden" -> "Wooden";
            case "golden" -> "Gold";
            case "chosen" -> "Chosen One";
            default -> capitalise(variant);
        };
    }

    public static String colourLabel(String colour) {
        return capitalise(colour);
    }

    public static String describe(CustomItemEntry entry) {
        if (LIGHTSABER.equals(entry.type)) {
            return colourLabel(entry.colour) + " " + variantLabel(entry.variant) + " Lightsaber";
        }
        return variantLabel(entry.variant) + " " + skinLabel(entry.type);
    }

    public static int colourRgb(String colour) {
        return switch (colour) {
            case "red" -> 0xE0201C;
            case "orange" -> 0xFF7A12;
            case "yellow" -> 0xF2D21B;
            case "green" -> 0x3EDC1E;
            case "cyan" -> 0x22E6F2;
            case "purple" -> 0x9B1FE8;
            case "pink" -> 0xFF45C8;
            case "white" -> 0xEEF2FF;
            default -> 0x2448FF;
        };
    }

    private static String capitalise(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    public static Map<String, CustomItemEntry> entries() {
        QZAConfig cfg = ConfigManager.get();
        if (cfg.customItemSkins == null) {
            cfg.customItemSkins = new LinkedHashMap<>();
        }
        if (cfg.customItems != null && !cfg.customItems.isEmpty()) {
            for (Map.Entry<String, String> legacy : cfg.customItems.entrySet()) {
                Look look = Look.parse(legacy.getValue());
                if (look != null && !cfg.customItemSkins.containsKey(legacy.getKey())) {
                    CustomItemEntry entry = new CustomItemEntry();
                    entry.type = look.type();
                    entry.variant = look.variant();
                    entry.colour = look.colour();
                    cfg.customItemSkins.put(legacy.getKey(), entry);
                }
            }
            cfg.customItems.clear();
            ConfigManager.save();
        }
        return cfg.customItemSkins;
    }

    public static CustomItemEntry assigned(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Map<String, CustomItemEntry> all = entries();
        if (all.isEmpty()) {
            return null;
        }
        String uuid = ItemProtection.uuid(stack);
        if (uuid.isEmpty()) {
            return null;
        }
        CustomItemEntry entry = all.get(uuid);
        if (entry == null) {
            return null;
        }
        normalise(entry);
        if (entry.name == null || entry.name.isEmpty()) {
            entry.name = IgnUtil.stripCodes(stack.getHoverName().getString());
        }
        return entry;
    }

    public static CustomItemEntry active(ItemStack stack) {
        return enabled() ? assigned(stack) : null;
    }

    public static Look skin(ItemStack stack) {
        CustomItemEntry entry = active(stack);
        return entry == null ? null : look(entry);
    }

    public static boolean is(ItemStack stack, String type) {
        CustomItemEntry entry = active(stack);
        return entry != null && type.equals(entry.type);
    }

    public static Look look(CustomItemEntry entry) {
        return new Look(entry.type, entry.variant, LIGHTSABER.equals(entry.type) ? entry.colour : "");
    }

    private static void normalise(CustomItemEntry entry) {
        if (!SKINS.contains(entry.type)) {
            entry.type = KARAMBIT;
        }
        if (LIGHTSABER.equals(entry.type)) {
            if (!STYLES.contains(entry.variant)) {
                entry.variant = STYLES.get(0);
            }
            if (!SABER_COLOURS.contains(entry.colour)) {
                entry.colour = STYLE_COLOURS.get(entry.variant);
            }
        } else if (!MATERIALS.contains(entry.variant)) {
            entry.variant = DEFAULT_MATERIAL;
        }
    }

    public static boolean equipOn(CustomItemEntry entry) {
        return ConfigManager.get().customItemEquip && entry.equip;
    }

    public static boolean add(ItemStack stack, String type) {
        String uuid = ItemProtection.uuid(stack);
        if (uuid.isEmpty()) {
            ChatUtil.error("That item can't be changed because it has no SkyBlock UUID.");
            return false;
        }
        CustomItemEntry entry = new CustomItemEntry();
        entry.type = type;
        entry.name = IgnUtil.stripCodes(stack.getHoverName().getString());
        normalise(entry);
        entries().put(uuid, entry);
        ConfigManager.save();
        return true;
    }

    public static void remove(String uuid) {
        entries().remove(uuid);
        ConfigManager.save();
    }

    public static void cycleType(CustomItemEntry entry, int step) {
        entry.type = SKINS.get(Math.floorMod(SKINS.indexOf(entry.type) + step, SKINS.size()));
        String keepMaterial = MATERIALS.contains(entry.variant) ? entry.variant : DEFAULT_MATERIAL;
        entry.variant = LIGHTSABER.equals(entry.type) ? STYLES.get(0) : keepMaterial;
        entry.colour = LIGHTSABER.equals(entry.type) ? STYLE_COLOURS.get(entry.variant) : "";
        ConfigManager.save();
    }

    public static void cycleVariant(CustomItemEntry entry, int step) {
        List<String> options = LIGHTSABER.equals(entry.type) ? STYLES : MATERIALS;
        entry.variant = options.get(Math.floorMod(options.indexOf(entry.variant) + step, options.size()));
        ConfigManager.save();
    }

    public static void cycleColour(CustomItemEntry entry, int step) {
        entry.colour = SABER_COLOURS.get(Math.floorMod(SABER_COLOURS.indexOf(entry.colour) + step,
                SABER_COLOURS.size()));
        ConfigManager.save();
    }

    public static void toggleEquip(CustomItemEntry entry) {
        entry.equip = !entry.equip;
        ConfigManager.save();
    }

    public static void toggleGlow(CustomItemEntry entry) {
        entry.glow = !entry.glow;
        ConfigManager.save();
    }

    public static void toggleBlock(CustomItemEntry entry) {
        entry.block = !entry.block;
        ConfigManager.save();
    }

    public static boolean pushSwing(ItemStackRenderState state, ItemDisplayContext context, PoseStack pose) {
        if (!HandSpace.firstPerson(context)) {
            return false;
        }
        Look look = Look.parse(((CustomItemState) state).qzaSkin());
        LocalPlayer player = Minecraft.getInstance().player;
        if (look == null || player == null || !KARAMBIT.equals(look.type())) {
            return false;
        }
        boolean right = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        if ((player.getMainArm() == HumanoidArm.RIGHT) != right) {
            return false;
        }
        float attack = Mc.attackAnim(player, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
        if (attack <= 1.0e-4f) {
            return false;
        }
        Matrix4f swap = vanillaSwing(attack).invert().mul(underhand(attack));
        pose.pushPose();
        pose.mulPose(HandSpace.side(swap, right));
        return true;
    }

    private static Matrix4f vanillaSwing(float attack) {
        float root = Mth.sqrt(attack);
        float sweep = Mth.sin(root * Mth.PI);
        return new Matrix4f()
                .translate(-0.4f * sweep, 0.2f * Mth.sin(root * Mth.TWO_PI), -0.2f * Mth.sin(attack * Mth.PI))
                .rotateY(HandSpace.rad(45f + Mth.sin(attack * attack * Mth.PI) * -20f))
                .rotateZ(HandSpace.rad(sweep * -20f))
                .rotateX(HandSpace.rad(sweep * -80f))
                .rotateY(HandSpace.rad(-45f));
    }

    private static Matrix4f underhand(float attack) {
        float root = Mth.sqrt(attack);
        float sweep = Mth.sin(root * Mth.PI);
        return new Matrix4f()
                .translate(-0.3f * sweep, 0.07f * Mth.sin(root * Mth.TWO_PI), -0.24f * Mth.sin(attack * Mth.PI))
                .translate(KARAMBIT_PIVOT)
                .rotateY(HandSpace.rad(22f * sweep))
                .rotateZ(HandSpace.rad(-10f * sweep))
                .rotateX(HandSpace.rad(72f * sweep))
                .translate(-KARAMBIT_PIVOT.x, -KARAMBIT_PIVOT.y, -KARAMBIT_PIVOT.z);
    }

    public static List<ItemStack> inventoryItems() {
        List<ItemStack> found = new ArrayList<>();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return found;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && !ItemProtection.uuid(stack).isEmpty()) {
                found.add(stack);
            }
        }
        return found;
    }

    public static ItemStack preview(CustomItemEntry entry) {
        ItemStack stack = new ItemStack(Items.STICK);
        stack.set(DataComponents.ITEM_MODEL, model(look(entry), ItemDisplayContext.GUI));
        return stack;
    }

    public static Identifier model(Look look, ItemDisplayContext context) {
        boolean firstPerson = HandSpace.firstPerson(context);
        return switch (look.type()) {
            case KARAMBIT -> id("karambit_" + look.variant());
            case BUTTERFLY -> id((firstPerson ? "butterfly_handle_a_" : "butterfly_knife_") + look.variant());
            case LIGHTSABER -> id((firstPerson ? "lightsaber_hilt_" : "lightsaber_") + look.variant() + "_"
                    + look.colour());
            default -> null;
        };
    }

    public static boolean push(ItemStackRenderState state, ItemDisplayContext context, PoseStack pose,
                               SubmitNodeCollector collector, int light, int overlay, int outline) {
        CustomItemState tagged = (CustomItemState) state;
        Look look = Look.parse(tagged.qzaSkin());
        LocalPlayer player = Minecraft.getInstance().player;
        if (look == null || player == null) {
            return false;
        }
        if (HandSpace.thirdPerson(context)) {
            if (!KARAMBIT.equals(look.type())) {
                return false;
            }
            pose.pushPose();
            pose.mulPose(HandSpace.side(HandSpace.KARAMBIT_REVERSE_TP,
                    context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND));
            return true;
        }
        if (!HandSpace.firstPerson(context)) {
            return false;
        }
        boolean right = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        boolean mainHand = (player.getMainArm() == HumanoidArm.RIGHT) == right;
        float swing = mainHand
                ? Mth.sin(Mc.attackAnim(player, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true))
                * Mth.PI)
                : 0f;
        switch (look.type()) {
            case KARAMBIT -> {
                float grip = mainHand ? ItemInspect.karambitGrip() : 1f;
                pose.pushPose();
                pose.mulPose(HandSpace.side(HandSpace.karambitGrip(grip), right));
                return true;
            }
            case BUTTERFLY -> {
                float blade = mainHand ? ItemInspect.butterflyBlade() : 0f;
                float back = (mainHand ? ItemInspect.butterflyHandle() : 0f) + 35f * swing;
                drawPart(id("butterfly_blade_" + look.variant()), layered(blade, 1f), right, context, pose, collector,
                        light, overlay, outline);
                drawPart(id("butterfly_handle_b_" + look.variant()), layered(back, 2f), right, context, pose,
                        collector, light, overlay, outline);
                return false;
            }
            case LIGHTSABER -> {
                drawSaber(tagged.qzaUuid(), look, mainHand, right, context, pose, collector, overlay, outline);
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    private static void drawSaber(String uuid, Look look, boolean mainHand, boolean right, ItemDisplayContext context,
                                  PoseStack pose, SubmitNodeCollector collector, int overlay, int outline) {
        CustomItemEntry entry = uuid == null ? null : entries().get(uuid);
        long now = Util.getNanos();
        if (mainHand && ignitePending && heldUuid.equals(uuid)) {
            ignitePending = false;
            litAt = now;
        }
        boolean animate = mainHand && entry != null && equipOn(entry);
        float t = animate ? (now - litAt) / 1.0e9f : SETTLED;
        float s = Mth.clamp((t - IGNITE_DELAY) / IGNITE_SECONDS, 0f, 1f);
        float out = 1f - s;
        float lit = Math.max(0.01f, 1f - out * out * out);
        ItemStackRenderState blade = drawPart(id("lightsaber_blade_" + look.colour()),
                HandSpace.stretch(EMITTER, HandSpace.HANDLE, lit), right, context, pose, collector, FULL_BRIGHT,
                overlay, outline);
        if (entry != null && !entry.glow) {
            return;
        }
        Material.Baked material = blade.pickParticleMaterial(RANDOM);
        if (material == null) {
            return;
        }
        float seconds = (now % 1_000_000_000_000L) / 1.0e9f;
        float hum = 0.05f * Mth.sin(seconds * 9.3f * Mth.TWO_PI) + 0.035f * Mth.sin(seconds * 23.7f * Mth.TWO_PI + 1.3f)
                + 0.025f * Mth.sin(seconds * 41.1f * Mth.TWO_PI + 4.1f);
        float surge = 0.7f * (float) Math.exp(-Math.pow((t - IGNITE_DELAY - IGNITE_SECONDS) / 0.15f, 2));
        float crackle = t < 1.5f ? (RANDOM.nextFloat() - 0.5f) * 0.35f * (1.5f - t) : 0f;
        float flash = t < 0.6f ? (float) Math.exp(-t / 0.12f) : 0f;
        float intensity = (1f + hum + surge + crackle) * Math.min(1f, 0.35f + lit);
        Vector3f from = new Vector3f(EMITTER);
        Vector3f to = new Vector3f(HandSpace.HANDLE).mul(BLADE_LENGTH * lit).add(EMITTER);
        if (!right) {
            from.x = -from.x;
            to.x = -to.x;
        }
        SaberGlow.draw(collector, pose.last().pose(), material.sprite(), from, to, BLADE_RADIUS,
                colourRgb(look.colour()), intensity, flash);
    }

    private static Matrix4f layered(float degrees, float depth) {
        return new Matrix4f()
                .translate(new Vector3f(HandSpace.FACE).mul(depth * HandSpace.PIXEL))
                .mul(HandSpace.around(BUTTERFLY_PIVOT, degrees, HandSpace.FACE));
    }

    private static ItemStackRenderState drawPart(Identifier model, Matrix4f transform, boolean right,
                                                 ItemDisplayContext context, PoseStack pose,
                                                 SubmitNodeCollector collector, int light, int overlay, int outline) {
        Minecraft client = Minecraft.getInstance();
        ItemStackRenderState part = new ItemStackRenderState();
        if (partStack == null) {
            partStack = new ItemStack(Items.STICK);
        }
        partStack.set(DataComponents.ITEM_MODEL, model);
        client.getItemModelResolver().updateForTopItem(part, partStack, context, client.level, client.player, 0);
        pose.pushPose();
        pose.mulPose(HandSpace.side(transform, right));
        drawingPart = true;
        try {
            part.submit(pose, collector, light, overlay, outline);
        } finally {
            drawingPart = false;
            pose.popPose();
        }
        return part;
    }
}
