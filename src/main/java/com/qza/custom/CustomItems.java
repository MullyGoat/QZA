package com.qza.custom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.config.CustomItemEntry;
import com.qza.config.QZAConfig;
import com.qza.custom.knife.KnifeFinish;
import com.qza.custom.knife.KnifeRenderer;
import com.qza.custom.knife.Knives;
import com.qza.inventory.ItemProtection;
import com.qza.mixin.CustomDataAccessor;
import com.qza.mixin.SpecialModelRenderersAccessor;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CustomItems {
    public static final String KARAMBIT = "karambit";
    public static final String BUTTERFLY = "butterfly";
    public static final String LIGHTSABER = "lightsaber";
    public static final String REALISTIC = "realistic";
    public static final String MINECRAFT = "minecraft";
    public static final List<String> LOOKS = List.of(REALISTIC, MINECRAFT);
    public static final List<String> SKINS = skins();
    public static final int DEFAULT_SIZE = 100;
    public static final int MIN_SIZE = 50;
    public static final int MAX_SIZE = 200;
    private static final int SIZE_STEP = 10;
    public static final List<String> MATERIALS = List.of("wooden", "stone", "copper", "iron", "golden", "diamond",
            "netherite");
    public static final List<String> STYLES = List.of("classic", "chosen", "vaapad", "elegant", "wanderer",
            "crossguard");
    public static final List<String> SABER_COLOURS = List.of("red", "orange", "yellow", "green", "cyan", "blue",
            "purple", "pink", "white");
    public static final Vector3f BUTTERFLY_PIVOT = HandSpace.texel(8.5f, 7.5f);

    private static final String DEFAULT_MATERIAL = "iron";
    private static final String DEFAULT_FINISH = "fade";
    private static final String PREVIEW_TAG = "qzaKnife";
    private static final Map<String, String> STYLE_COLOURS = Map.of("classic", "red", "chosen", "blue",
            "vaapad", "purple", "elegant", "green", "wanderer", "yellow", "crossguard", "red");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float SABER_EMITTER = 1f + 8f * 0.70710678f;
    private static final Vector3f EMITTER = HandSpace.texel(SABER_EMITTER, 16f - SABER_EMITTER);
    private static final float BLADE_LENGTH = 15.5f / 16f * 0.68f;
    private static final float BLADE_RADIUS = 0.85f / 16f * 0.68f;
    private static final float IGNITE_DELAY = 0.08f;
    private static final float IGNITE_SECONDS = 1.0f;
    private static final Vector3f HOOK_PIVOT = HandSpace.KARAMBIT_REVERSE.transformPosition(HandSpace.KARAMBIT_HOLD,
            new Vector3f());
    private static final float SETTLED = 10f;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final int SEEDS = 1000;
    private static final Map<String, CustomItemEntry> PREVIEWS = new HashMap<>();
    private static final Map<String, float[]> PIVOTS = new HashMap<>();
    private static final float[] VIEW = {0.6f, -0.28f, 0.16f, 0.02f, 22f, 35f};
    private static final float[] VIEW_KARAMBIT = {0.72f, -0.1f, -0.06f, 0f, 0f, 20f};
    private static final float[] VIEW_DAGGERS = {0.62f, -0.24f, 0.26f, 0.02f, 12f, 30f};
    private static final float[] VIEW_HAND = {1.0f, 0f, 0f, 0f, 12f, 22f};
    private static final float[] VIEW_HAND_KARAMBIT = {1.35f, 0f, 0f, 0f, 10f, 20f};
    private static final float[] VIEW_HAND_DAGGERS = {1.0f, 0f, 0f, 0f, 0f, 20f};
    private static final Vector3f TP_GRIP = HandSpace.HELD_TP.transformPosition(3.2f / 16f, 1f - 13.5f / 16f, 0.5f,
            new Vector3f());

    private static ItemStack partStack;
    private static ItemStack sharedStack;
    private static CustomItemEntry sharedEntry;
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
            if (parts.length > 1 && REALISTIC.equals(parts[1])) {
                return new Look(type, REALISTIC, "");
            }
            String material = parts.length > 1 && MATERIALS.contains(parts[1]) ? parts[1] : DEFAULT_MATERIAL;
            return new Look(type, material, "");
        }

        public String format() {
            return colour.isEmpty() ? type + ":" + variant : type + ":" + variant + ":" + colour;
        }

        public boolean realistic() {
            return REALISTIC.equals(variant);
        }
    }

    private static List<String> skins() {
        List<String> all = new ArrayList<>(Knives.ids());
        all.add(LIGHTSABER);
        return List.copyOf(all);
    }

    public static void init() {
        SpecialModelRenderersAccessor.qzaIdMapper().put(KnifeRenderer.TYPE, KnifeRenderer.MAP_CODEC);
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
        if (LIGHTSABER.equals(skin)) {
            return "Lightsaber";
        }
        Knives.Knife knife = Knives.get(skin);
        return knife == null ? "None" : knife.label();
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

    public static String lookLabel(String look) {
        return REALISTIC.equals(look) ? "Realistic" : "Minecraft";
    }

    public static String finishLabel(CustomItemEntry entry) {
        String label = KnifeFinish.get(entry.finish).label();
        String phase = KnifeFinish.phase(entry.finish, entry.seed);
        return phase.isEmpty() ? label : label + " (" + phase + ")";
    }

    public static String describe(CustomItemEntry entry) {
        if (LIGHTSABER.equals(entry.type)) {
            return colourLabel(entry.colour) + " " + variantLabel(entry.variant) + " Lightsaber";
        }
        if (realistic(entry)) {
            return skinLabel(entry.type) + " | " + finishLabel(entry);
        }
        return variantLabel(entry.variant) + " " + skinLabel(entry.type);
    }

    public static boolean realistic(CustomItemEntry entry) {
        return Knives.is(entry.type) && REALISTIC.equals(entry.style);
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
        if (entry.itemId == null || entry.itemId.isEmpty()) {
            entry.itemId = ItemProtection.skyblockId(stack);
        }
        return entry;
    }

    public static boolean sanitise(CustomItemEntry entry) {
        if (!SKINS.contains(entry.type)) {
            return false;
        }
        normalise(entry);
        return true;
    }

    public static Look skinFor(ItemStack stack, ItemOwner owner) {
        Look own = skin(stack);
        if (own != null) {
            sharedStack = null;
            sharedEntry = null;
            return own;
        }
        CustomItemEntry shared = SharedItems.entryFor(owner, stack);
        sharedStack = shared == null ? null : stack;
        sharedEntry = shared;
        return shared == null ? null : look(shared);
    }

    public static CustomItemEntry sharedEntry(ItemStack stack) {
        return stack != null && stack == sharedStack ? sharedEntry : null;
    }

    public static int sizeFor(ItemStack stack) {
        CustomItemEntry entry = active(stack);
        if (entry == null) {
            entry = sharedEntry(stack);
        }
        if (entry == null) {
            entry = previewEntry(stack);
        }
        return entry == null ? DEFAULT_SIZE : entry.size;
    }

    public static CustomItemEntry active(ItemStack stack) {
        return enabled() ? assigned(stack) : null;
    }

    public static Look skin(ItemStack stack) {
        CustomItemEntry entry = active(stack);
        if (entry == null) {
            entry = previewEntry(stack);
        }
        return entry == null ? null : look(entry);
    }

    public static CustomItemEntry previewEntry(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.STICK)) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = ((CustomDataAccessor) (Object) data).qzaTag();
        String raw = tag == null ? "" : tag.getStringOr(PREVIEW_TAG, "");
        if (raw.isEmpty()) {
            return null;
        }
        return PREVIEWS.computeIfAbsent(raw, CustomItems::parsePreview);
    }

    private static CustomItemEntry parsePreview(String raw) {
        String[] parts = raw.split("\\|", -1);
        CustomItemEntry entry = new CustomItemEntry();
        entry.type = parts[0];
        entry.style = parts.length > 1 ? parts[1] : MINECRAFT;
        entry.variant = parts.length > 2 ? parts[2] : DEFAULT_MATERIAL;
        entry.finish = parts.length > 3 ? parts[3] : DEFAULT_FINISH;
        try {
            entry.seed = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
        } catch (NumberFormatException e) {
            entry.seed = 0;
        }
        entry.wear = parts.length > 5 ? parts[5] : "fn";
        entry.colour = parts.length > 6 ? parts[6] : "";
        normalise(entry);
        return entry;
    }

    public static boolean is(ItemStack stack, String type) {
        CustomItemEntry entry = active(stack);
        return entry != null && type.equals(entry.type);
    }

    public static Look look(CustomItemEntry entry) {
        if (realistic(entry)) {
            return new Look(entry.type, REALISTIC, "");
        }
        return new Look(entry.type, entry.variant, LIGHTSABER.equals(entry.type) ? entry.colour : "");
    }

    private static void normalise(CustomItemEntry entry) {
        if (!SKINS.contains(entry.type)) {
            entry.type = KARAMBIT;
        }
        if (entry.size < MIN_SIZE || entry.size > MAX_SIZE) {
            entry.size = entry.size <= 0 ? DEFAULT_SIZE : Math.max(MIN_SIZE, Math.min(MAX_SIZE, entry.size));
        }
        if (LIGHTSABER.equals(entry.type)) {
            if (!STYLES.contains(entry.variant)) {
                entry.variant = STYLES.get(0);
            }
            if (!SABER_COLOURS.contains(entry.colour)) {
                entry.colour = STYLE_COLOURS.get(entry.variant);
            }
            return;
        }
        if (!MATERIALS.contains(entry.variant)) {
            entry.variant = DEFAULT_MATERIAL;
        }
        if (!LOOKS.contains(entry.style)) {
            entry.style = MINECRAFT;
        }
        if (!KnifeFinish.is(entry.finish)) {
            entry.finish = DEFAULT_FINISH;
        }
        entry.wear = KnifeFinish.WEARS.get(0);
        entry.seed = Math.floorMod(entry.seed, SEEDS);
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
        entry.itemId = ItemProtection.skyblockId(stack);
        entry.style = REALISTIC;
        entry.finish = DEFAULT_FINISH;
        entry.seed = RANDOM.nextInt(SEEDS);
        normalise(entry);
        entries().put(uuid, entry);
        ConfigManager.save();
        return true;
    }

    public static void remove(String uuid) {
        entries().remove(uuid);
        ConfigManager.save();
    }

    public static void setType(CustomItemEntry entry, String type) {
        if (SKINS.contains(type)) {
            retype(entry, type);
            ConfigManager.save();
        }
    }

    public static void retype(CustomItemEntry entry, String type) {
        if (type.equals(entry.type)) {
            return;
        }
        String keepMaterial = MATERIALS.contains(entry.variant) ? entry.variant : DEFAULT_MATERIAL;
        entry.type = type;
        entry.variant = LIGHTSABER.equals(type) ? STYLES.get(0) : keepMaterial;
        entry.colour = LIGHTSABER.equals(type) ? STYLE_COLOURS.get(entry.variant) : "";
        normalise(entry);
    }

    public static CustomItemEntry copy(CustomItemEntry entry) {
        CustomItemEntry copy = new CustomItemEntry();
        copy.type = entry.type;
        copy.variant = entry.variant;
        copy.colour = entry.colour;
        copy.name = entry.name;
        copy.equip = entry.equip;
        copy.glow = entry.glow;
        copy.block = entry.block;
        copy.style = entry.style;
        copy.finish = entry.finish;
        copy.seed = entry.seed;
        copy.wear = entry.wear;
        copy.itemId = entry.itemId;
        copy.size = entry.size;
        return copy;
    }

    public static void cycleType(CustomItemEntry entry, int step) {
        setType(entry, SKINS.get(Math.floorMod(SKINS.indexOf(entry.type) + step, SKINS.size())));
    }

    public static void cycleLook(CustomItemEntry entry, int step) {
        entry.style = LOOKS.get(Math.floorMod(LOOKS.indexOf(entry.style) + step, LOOKS.size()));
        ConfigManager.save();
    }

    public static void setFinish(CustomItemEntry entry, String finish) {
        if (KnifeFinish.is(finish)) {
            entry.finish = finish;
            ConfigManager.save();
        }
    }

    public static void cycleFinish(CustomItemEntry entry, int step) {
        List<String> ids = KnifeFinish.ids();
        setFinish(entry, ids.get(Math.floorMod(ids.indexOf(entry.finish) + step, ids.size())));
    }

    public static void cycleSize(CustomItemEntry entry, int step) {
        entry.size = Math.max(MIN_SIZE, Math.min(MAX_SIZE, entry.size + step * SIZE_STEP));
        ConfigManager.save();
    }

    public static void cycleSeed(CustomItemEntry entry, int step) {
        entry.seed = step > 0 ? RANDOM.nextInt(SEEDS) : Math.floorMod(entry.seed - 1, SEEDS);
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

    public static boolean pushThirdPerson(ItemStackRenderState state, ItemDisplayContext context, PoseStack pose) {
        CustomItemState tagged = (CustomItemState) state;
        if (!HandSpace.thirdPerson(context) || Look.parse(tagged.qzaSkin()) == null) {
            return false;
        }
        float size = tagged.qzaSize() / (float) DEFAULT_SIZE;
        if (size == 1f) {
            return false;
        }
        pose.pushPose();
        pose.mulPose(HandSpace.side(HandSpace.around(TP_GRIP, size), context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND));
        return true;
    }

    public static boolean pushViewmodel(ItemStackRenderState state, ItemDisplayContext context, PoseStack pose) {
        if (!HandSpace.firstPerson(context)) {
            return false;
        }
        Matrix4f transform = viewmodel(state);
        if (transform == null) {
            return false;
        }
        pose.pushPose();
        pose.mulPose(HandSpace.side(transform, context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND));
        return true;
    }

    public static Matrix4f viewmodel(ItemStackRenderState state) {
        CustomItemState tagged = (CustomItemState) state;
        Look look = Look.parse(tagged.qzaSkin());
        if (look == null) {
            return null;
        }
        float size = tagged.qzaSize() / (float) DEFAULT_SIZE;
        float[] view = view(look);
        if (view == null) {
            return size == 1f ? null : HandSpace.around(HandSpace.GRIP, size);
        }
        return viewmodel(view, size);
    }

    public static String knife(ItemStackRenderState state) {
        Look look = Look.parse(((CustomItemState) state).qzaSkin());
        return look != null && Knives.is(look.type()) ? look.type() : null;
    }

    public static boolean realistic(ItemStackRenderState state) {
        Look look = Look.parse(((CustomItemState) state).qzaSkin());
        return look != null && look.realistic();
    }

    public static boolean handOn() {
        return enabled() && ConfigManager.get().customItemHand;
    }

    private static float[] view(Look look) {
        Knives.Knife knife = look == null ? null : Knives.get(look.type());
        if (knife == null || !look.realistic()) {
            return null;
        }
        boolean hand = handOn();
        return switch (knife.kind()) {
            case KARAMBIT -> hand ? VIEW_HAND_KARAMBIT : VIEW_KARAMBIT;
            case DAGGERS -> hand ? VIEW_HAND_DAGGERS : VIEW_DAGGERS;
            default -> hand ? VIEW_HAND : VIEW;
        };
    }

    private static Matrix4f viewmodel(float[] view, float size) {
        Vector3f grip = HandSpace.GRIP;
        return new Matrix4f()
                .translate(view[1], view[2], view[3])
                .translate(grip)
                .rotateZ(HandSpace.rad(view[4]))
                .rotateY(HandSpace.rad(view[5]))
                .scale(view[0] * size)
                .translate(-grip.x, -grip.y, -grip.z);
    }

    public static boolean pushSwing(ItemStackRenderState state, ItemDisplayContext context, PoseStack pose) {
        float attack = karambitSwing(state, context);
        if (attack < 0f) {
            return false;
        }
        Matrix4f swap = vanillaSwing(attack).invert().mul(sweep(attack, ItemInspect.karambitPivot(state)));
        pose.pushPose();
        pose.mulPose(HandSpace.side(swap, context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND));
        return true;
    }

    public static boolean pushHook(ItemStackRenderState state, ItemDisplayContext context, PoseStack pose) {
        float attack = karambitSwing(state, context);
        if (attack < 0f) {
            return false;
        }
        pose.pushPose();
        pose.mulPose(HandSpace.side(hook(attack), context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND));
        return true;
    }

    private static float karambitSwing(ItemStackRenderState state, ItemDisplayContext context) {
        if (!HandSpace.firstPerson(context)) {
            return -1f;
        }
        Look look = Look.parse(((CustomItemState) state).qzaSkin());
        LocalPlayer player = Minecraft.getInstance().player;
        if (look == null || player == null || !KARAMBIT.equals(look.type())) {
            return -1f;
        }
        boolean right = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        if ((player.getMainArm() == HumanoidArm.RIGHT) != right) {
            return -1f;
        }
        float attack = Mc.attackAnim(player, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
        return attack <= 1.0e-4f ? -1f : attack;
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

    private static Matrix4f sweep(float attack, Vector3f pivot) {
        float root = Mth.sqrt(attack);
        float sweep = Mth.sin(root * Mth.PI);
        return new Matrix4f()
                .translate(-0.14f * sweep, 0.05f * Mth.sin(root * Mth.TWO_PI), -0.06f * Mth.sin(attack * Mth.PI))
                .translate(pivot)
                .rotateY(HandSpace.rad(18f * sweep))
                .rotateZ(HandSpace.rad(-8f * sweep))
                .translate(-pivot.x, -pivot.y, -pivot.z);
    }

    private static Matrix4f hook(float attack) {
        float sweep = Mth.sin(Mth.sqrt(attack) * Mth.PI);
        return new Matrix4f()
                .translate(HOOK_PIVOT)
                .rotateY(HandSpace.rad(22f * sweep))
                .rotateZ(HandSpace.rad(-10f * sweep))
                .rotateX(HandSpace.rad(72f * sweep))
                .translate(-HOOK_PIVOT.x, -HOOK_PIVOT.y, -HOOK_PIVOT.z);
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
        CompoundTag tag = new CompoundTag();
        tag.putString(PREVIEW_TAG, String.join("|", entry.type, entry.style, entry.variant, entry.finish,
                String.valueOf(entry.seed), entry.wear, entry.colour));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static Identifier model(Look look, ItemDisplayContext context) {
        boolean firstPerson = HandSpace.firstPerson(context);
        if (look.realistic()) {
            return KnifeRenderer.TYPE;
        }
        Knives.Knife knife = Knives.get(look.type());
        return switch (look.type()) {
            case KARAMBIT -> id("karambit_" + look.variant());
            case BUTTERFLY -> id((firstPerson ? "butterfly_handle_a_" : "butterfly_knife_") + look.variant());
            case LIGHTSABER -> id((firstPerson ? "lightsaber_hilt_" : "lightsaber_") + look.variant() + "_"
                    + look.colour());
            default -> knife == null ? null
                    : id(look.type() + (firstPerson && knife.folding() ? "_handle_" : "_") + look.variant());
        };
    }

    public static boolean push(ItemStackRenderState state, ItemDisplayContext context, PoseStack pose,
                               SubmitNodeCollector collector, int light, int overlay, int outline) {
        CustomItemState tagged = (CustomItemState) state;
        Look look = Look.parse(tagged.qzaSkin());
        LocalPlayer player = Minecraft.getInstance().player;
        if (look == null) {
            return false;
        }
        boolean firstPerson = HandSpace.firstPerson(context);
        boolean right = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        boolean mainHand = firstPerson && player != null && (player.getMainArm() == HumanoidArm.RIGHT) == right;
        float swing = mainHand
                ? Mth.sin(Mc.attackAnim(player, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true))
                * Mth.PI)
                : 0f;
        Knives.Knife knife = Knives.get(look.type());
        if (look.realistic() && knife != null) {
            float blade = 0f;
            float back = 0f;
            if (knife.kind() == Knives.Kind.BUTTERFLY) {
                blade = mainHand ? ItemInspect.butterflyBlade() : 0f;
                back = (mainHand ? ItemInspect.butterflyHandle() : 0f) + 35f * swing;
            } else if (knife.folding()) {
                blade = mainHand ? ItemInspect.knifeFold() : 0f;
            }
            KnifeRenderer.begin(context, mainHand, blade, back);
        }
        if (player == null) {
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
        if (!firstPerson) {
            return false;
        }
        if (KARAMBIT.equals(look.type())) {
            float grip = mainHand ? ItemInspect.karambitGrip() : 1f;
            pose.pushPose();
            pose.mulPose(HandSpace.side(HandSpace.karambitGrip(grip), right));
            return true;
        }
        if (look.realistic()) {
            return false;
        }
        if (knife != null && knife.folding()) {
            float fold = mainHand ? ItemInspect.knifeFold() : 0f;
            float[] pivot = foldPivot(knife.id());
            Matrix4f transform = HandSpace.around(HandSpace.texel(pivot[0], pivot[1]), -fold, HandSpace.FACE);
            drawPart(id(look.type() + "_blade_" + look.variant()), transform, right, context, pose, collector, light,
                    overlay, outline);
            return false;
        }
        switch (look.type()) {
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

    public static void end() {
        KnifeRenderer.end();
    }

    private static float[] foldPivot(String knife) {
        return PIVOTS.computeIfAbsent(knife, k -> {
            float[] pivot = Knives.pivotTexel(k);
            return pivot == null ? new float[]{8f, 8f} : pivot;
        });
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
