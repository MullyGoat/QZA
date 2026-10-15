package com.qza.cheat;

import com.mojang.authlib.properties.Property;
import com.qza.cheat.click.AutoClicker;
import com.qza.cheat.mixin.KeyMappingAccessor;
import com.qza.cheat.movement.DungeonBoss;
import com.qza.compat.Mc;
import com.qza.util.ChatUtil;
import com.qza.util.DungeonState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BloodClick {
    private static final Set<String> WATCHER = Set.of(
            "5662b6fb4b8b586dc4cdf803b0444d9b41d245cdf668dab38fa6c064afe8e461",
            "2739d7f4e66a7db2ea6cd414e4c4ba41df7a92455c9fc42caab014665c367ad5",
            "bf6e1e7ed36586c2d98057002bc1adc981e2889f7bd7b5b3852bc55cc7802204",
            "4cec40008e1c31c1984f4d650abb3410f2037119fd624afc953563b73515a077",
            "b37dd18b5983a767e556dc64424af4b9abdb75d4c9e8b097818afbc431bf0e09",
            "f5f0d78fe38d1d7f75f08cdcf2a1855d6da0337e114a3c63e3bf3c618bc732b0",
            "51967db5e3199916252021903cf4e9952ef7cec220faaca1ba79bafe5938bd80",
            "9fd61e8055f6ee97ab5b6196a8d7ec98078ac37e00376157b6b520eaaa2f93af",
            "e5c1dc47a04ce57001a8b726f018cdef40b7ea9d7bd6d835ca495a0ef169f893");

    private static final Set<String> SKULLS = Set.of(
            "5a79860aca799407c0faa10b1bbcf42998fad4ebcf31d7a214180826b4ac94e1",
            "4774871190c878c9a2c4496c1e10257c6c4ea13807d72c15d7ac6ab3a7a9a8dc",
            "f4624a9a8c69ca204504abb043d47456cd9b09749a36357462303f276a229d4",
            "c919e5b8d56f062a21d224de14af771e2f55d09b59e7b099d09daa57540b79cf",
            "a89f6303af85877610912dc04b8b1e89724752f0a7eea05ab6547e228179c06f",
            "67237eddaebdbbdaacfa912885560ccdc65da93b4c3d513532868ec23bb5b448",
            "ff184c19e725623d32828a0a4e741e86f135ac63dbc828ff3c8468338f3683b",
            "5cccd53f5191c29a9dc8f0170fbdc4e59e66476aae33de27b468f1de1b7cf3b2",
            "b5ba76e02cab72fa7d8ac54ceec849976ab0b00a01068d68c266766bf70c3997",
            "aa23c8cde2943c84249de8351bc3540be5f8afaaba8b2cb032fc5acad78a269b",
            "9171f35b8f508142bd8c65417d0f324153ab9147739ee4d10dea733cc80eaa20",
            "7d12b2ade413a6cd7cca3c95e961ba9f0ae7165fa41fc7b5d5f094a01240c609",
            "96c3e31cfc66733275c42fcfb5d9a44342d643b55cd14c9c77d273a2352",
            "8421ba5b8e3573ef97beb5b40e15d15b20f30631c4c5330c3deda3047df0e92",
            "ad22772f769045fdc5be819ad68b01a97ac04c60886d2ca7afee39b282f7a383",
            "ad67f97d7f821729beb34a82c3f13592b40439fe5248e72576fde7aa180bf77",
            "fb3973a752b24a2f3abb003427f6dbe6ca3a61db0a1bcf351c6eab27ec27e50",
            "12716ecbf5b8da00b05f316ec6af61e8bd02805b21eb8e440151468dc656549c",
            "3260325171a7ba8460830c0eea515c757a665e5b16a14207ba1a3182752bee87",
            "62d8fd3aa5617b1dac0aae9c81f6dd70ad93a59942f460d27e4d55a5cb8918e8",
            "56fc854bb84cf4b7697297973e02b79bc10698460b51a639c60e5e417734e11",
            "7de7bbbdf22bfe17980d4e20687e386f11d59ee1db6f8b4762391b79a5ac532d",
            "c1007c5b7114abec734206d4fc613da4f3a0e99f71ff949cedadc99079135a0b");

    private static final Pattern TEXTURE = Pattern.compile("/texture/([0-9a-f]+)");
    private static final double WATCHER_RANGE_SQ = 20.0 * 20.0;
    private static final double MOVED_SQ = 1.0E-4;
    private static final double SPAWN_FLAT_SQ = 3.0 * 3.0;
    private static final double SPAWN_HIGH = 4.0;
    private static final int FLIGHT_TICKS = 20;
    private static final int FIRST_TICK = 1;
    private static final int LAST_TICK = 3;
    public static final double MIN_RANGE = 1.0;
    public static final double MAX_RANGE = 60.0;
    private static final double AIR_GAP = 0.05;
    private static final double FLOOR_SEARCH = 16.0;
    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;

    private static final class Skull {
        Vec3 last;
        int movedAt = -1;
        boolean used;

        Skull(Vec3 at) {
            last = at;
        }
    }

    private record Spawn(Entity mob, int at, int delay) {
    }

    private static final Map<Integer, Skull> skulls = new HashMap<>();
    private static final List<Spawn> spawns = new ArrayList<>();
    private static final Map<String, String> hashes = new HashMap<>();
    private static int watcherId = -1;
    private static int clock;

    private BloodClick() {
    }

    public static boolean enabled() {
        return CheatConfigManager.get().bloodClickEnabled;
    }

    public static void reset() {
        skulls.clear();
        spawns.clear();
        watcherId = -1;
    }

    private static boolean active(Minecraft client) {
        return enabled() && client.player != null && client.level != null && DungeonState.inDungeon()
                && !DungeonBoss.inBoss();
    }

    public static void onStartTick(Minecraft client) {
        clock++;
        if (!active(client)) {
            if (watcherId != -1 || !skulls.isEmpty() || !spawns.isEmpty()) {
                reset();
            }
            return;
        }
        track(client.level);
        clickSpawns(client, client.player, client.level);
    }

    private static void track(ClientLevel level) {
        Entity watcher = watcherId == -1 ? null : level.getEntity(watcherId);
        if (watcher == null || !watcher.isAlive()) {
            watcherId = -1;
            watcher = null;
            for (Entity entity : level.entitiesForRendering()) {
                if (entity instanceof Zombie zombie && WATCHER.contains(hash(zombie.getItemBySlot(EquipmentSlot.HEAD)))) {
                    watcherId = zombie.getId();
                    watcher = zombie;
                    break;
                }
            }
        }
        skulls.keySet().removeIf(id -> level.getEntity(id) == null);
        if (watcher == null) {
            return;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand) || stand.distanceToSqr(watcher) > WATCHER_RANGE_SQ) {
                continue;
            }
            Skull skull = skulls.get(stand.getId());
            if (skull == null) {
                if (!SKULLS.contains(hash(stand.getItemBySlot(EquipmentSlot.HEAD)))) {
                    continue;
                }
                skull = new Skull(stand.position());
                skulls.put(stand.getId(), skull);
            }
            Vec3 now = stand.position();
            if (skull.movedAt < 0 && now.distanceToSqr(skull.last) > MOVED_SQ) {
                skull.movedAt = clock;
            }
            skull.last = now;
        }
    }

    public static void onEntityLoad(Entity entity, ClientLevel level) {
        Minecraft client = Minecraft.getInstance();
        if (!(entity instanceof LivingEntity) || entity instanceof ArmorStand || entity == client.player
                || !active(client)) {
            return;
        }
        if (entity instanceof Player player && player.getUUID().version() == 4) {
            return;
        }
        Vec3 at = entity.position();
        for (Skull skull : skulls.values()) {
            if (skull.used || skull.movedAt < 0 || clock - skull.movedAt < FLIGHT_TICKS) {
                continue;
            }
            double dx = at.x - skull.last.x;
            double dz = at.z - skull.last.z;
            if ((dx * dx) + (dz * dz) <= SPAWN_FLAT_SQ && Math.abs(at.y - skull.last.y) <= SPAWN_HIGH) {
                skull.used = true;
                int delay = FIRST_TICK + ThreadLocalRandom.current().nextInt((LAST_TICK - FIRST_TICK) + 1);
                spawns.add(new Spawn(entity, clock, delay));
                return;
            }
        }
    }

    private static void clickSpawns(Minecraft client, LocalPlayer player, ClientLevel level) {
        boolean clicked = false;
        Iterator<Spawn> it = spawns.iterator();
        while (it.hasNext()) {
            Spawn spawn = it.next();
            Entity mob = spawn.mob;
            int age = clock - spawn.at;
            if (mob.isRemoved() || !mob.isAlive() || age > LAST_TICK) {
                it.remove();
                continue;
            }
            if (clicked || age < FIRST_TICK || Mc.screen() != null || !inAir(level, mob) || !heldAllowed(player)) {
                continue;
            }
            AABB box = mob.getBoundingBox().inflate(mob.getPickRadius());
            if (!lookingAt(player, level, box)) {
                continue;
            }
            if (age >= spawn.delay || age >= LAST_TICK || leavingNextTick(player, level, mob, box)) {
                KeyMappingAccessor attack = (KeyMappingAccessor) client.options.keyAttack;
                attack.qzaSetClickCount(attack.qzaClickCount() + 1);
                clicked = true;
                it.remove();
            }
        }
    }

    private static boolean inAir(ClientLevel level, Entity mob) {
        return !mob.onGround() && mob.getY() - floor(level, mob) > AIR_GAP;
    }

    private static boolean heldAllowed(LocalPlayer player) {
        return !CheatConfigManager.get().bloodClickWhitelist
                || items().containsKey(AutoClicker.uuidOf(player.getMainHandItem()));
    }

    public static Map<String, String> items() {
        return CheatConfigManager.get().bloodClickItems;
    }

    public static List<String> keys() {
        return new ArrayList<>(items().keySet());
    }

    public static String heldLabel() {
        ItemStack stack = AutoClicker.held();
        if (stack.isEmpty()) {
            return "Nothing held";
        }
        String uuid = AutoClicker.uuidOf(stack);
        if (uuid == null) {
            return "No UUID";
        }
        return items().containsKey(uuid) ? "Remove" : "Add";
    }

    public static void toggleHeld() {
        ItemStack stack = AutoClicker.held();
        String uuid = AutoClicker.identify(stack);
        if (uuid == null) {
            return;
        }
        String removed = items().remove(uuid);
        if (removed != null) {
            ChatUtil.info("BloodCamp Triggerbot taken off " + removed + ".");
        } else {
            String name = stack.getHoverName().getString();
            items().put(uuid, name);
            ChatUtil.success("BloodCamp Triggerbot will click with " + name + ".");
        }
        CheatConfigManager.save();
    }

    public static void forget(String uuid) {
        if (items().remove(uuid) != null) {
            CheatConfigManager.save();
        }
    }

    private static boolean leavingNextTick(LocalPlayer player, ClientLevel level, Entity mob, AABB box) {
        if (mob.onGround()) {
            return false;
        }
        double fall = ((mob.getY() - mob.yo) - GRAVITY) * DRAG;
        if (fall >= 0.0) {
            return false;
        }
        if (box.minY + fall <= floor(level, mob)) {
            return true;
        }
        return !lookingAt(player, level, box.move(0.0, fall, 0.0));
    }

    private static double floor(ClientLevel level, Entity mob) {
        Vec3 from = mob.position();
        BlockHitResult hit = level.clip(new ClipContext(from, from.add(0.0, -FLOOR_SEARCH, 0.0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        return hit.getType() == HitResult.Type.MISS ? Double.NEGATIVE_INFINITY : hit.getLocation().y;
    }

    private static boolean lookingAt(LocalPlayer player, ClientLevel level, AABB box) {
        Vec3 eye = player.getEyePosition();
        if (box.contains(eye)) {
            return true;
        }
        double range = Math.max(MIN_RANGE, Math.min(MAX_RANGE, CheatConfigManager.get().bloodClickRange));
        Optional<Vec3> hit = box.clip(eye, eye.add(player.getViewVector(1.0f).scale(range)));
        if (hit.isEmpty()) {
            return false;
        }
        BlockHitResult block = level.clip(new ClipContext(eye, hit.get(), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        return block.getType() == HitResult.Type.MISS;
    }

    private static String hash(ItemStack stack) {
        if (stack == null || !stack.is(Items.PLAYER_HEAD)) {
            return "";
        }
        ResolvableProfile profile = stack.get(DataComponents.PROFILE);
        if (profile == null) {
            return "";
        }
        for (Property property : profile.partialProfile().properties().get("textures")) {
            String value = property.value();
            String known = hashes.get(value);
            if (known != null) {
                return known;
            }
            String found = decode(value);
            if (hashes.size() > 512) {
                hashes.clear();
            }
            hashes.put(value, found);
            return found;
        }
        return "";
    }

    private static String decode(String value) {
        try {
            String json = new String(Base64.getDecoder().decode(value.trim()), StandardCharsets.UTF_8);
            Matcher m = TEXTURE.matcher(json);
            return m.find() ? m.group(1) : "";
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
