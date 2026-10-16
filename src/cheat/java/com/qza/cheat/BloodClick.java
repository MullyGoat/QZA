package com.qza.cheat;

import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.platform.InputConstants;
import com.qza.cheat.click.AutoClicker;
import com.qza.cheat.mixin.KeyMappingAccessor;
import com.qza.cheat.movement.DungeonBoss;
import com.qza.compat.Mc;
import com.qza.timer.ServerTickClock;
import com.qza.util.ChatUtil;
import com.qza.util.DungeonState;
import com.qza.util.IgnUtil;
import net.minecraft.client.KeyMapping;
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
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

    private static final Set<String> NAMES = Set.of("Revoker", "Psycho", "Reaper", "Cannibal", "Mute", "Ooze",
            "Putrid", "Freak", "Leech", "Tear", "Parasite", "Flamer", "Skull", "Mr. Dead", "Vader", "Frost", "Walker",
            "Wandering Soul", "WanderingSoul", "Bonzo", "Scarf", "Livid", "Spirit Bear", "Giant");

    private static final Pattern TEXTURE = Pattern.compile("/texture/([0-9a-f]+)");
    private static final String WATCHER_MOVES = "[BOSS] The Watcher: Let's see how you can handle this.";
    private static final double WATCHER_RANGE_SQ = 20.0 * 20.0;
    private static final double MOB_RANGE_SQ = 32.0 * 32.0;
    private static final double SKULL_NEAR_SQ = 4.0 * 4.0;
    private static final double MOVED_SQ = 1.0E-6;
    private static final long SPAWN_TICKS = 38L;
    private static final long FIRST_WAVE_TICKS = 40L;
    private static final long PRE_FIRE_TICKS = 1L;
    private static final double FLIGHT = 11.9;
    private static final double FIRST_FLIGHT = 16.1;
    private static final double BOX_BOTTOM = 1.5;
    private static final double BOX_TOP = 2.5;
    private static final double BOX_HALF = 0.5;
    private static final double BOX_SLACK = 0.1;
    private static final int TIMER_SHIELD = 0;
    private static final int AGAIN_MIN = 4;
    private static final int AGAIN_MAX = 6;
    public static final double MIN_RANGE = 1.0;
    public static final double MAX_RANGE = 60.0;
    private static final double AIR_GAP = 0.05;
    private static final double FLOOR_SEARCH = 16.0;
    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;

    private static final class Skull {
        final boolean first;
        Vec3 last;
        Vec3 start;
        double sx;
        double sy;
        double sz;
        long startTick = -1L;
        boolean fired;

        Skull(Vec3 at, boolean first) {
            this.last = at;
            this.first = first;
        }

        Vec3 end() {
            double length = Math.sqrt((sx * sx) + (sy * sy) + (sz * sz));
            if (start == null || length < 1.0E-9) {
                return null;
            }
            double flight = (first ? FIRST_FLIGHT : FLIGHT) / length;
            return start.add(sx * flight, sy * flight, sz * flight);
        }

        long due() {
            return startTick + SPAWN_TICKS + (first ? FIRST_WAVE_TICKS : 0L);
        }
    }

    private static final class Mob {
        final int seen;
        int nextAt;
        int clicks;
        boolean air;
        boolean aimed;

        Mob(int seen) {
            this.seen = seen;
            this.nextAt = seen;
        }
    }

    private static final Map<Integer, Mob> mobs = new HashMap<>();
    private static final Map<Integer, Skull> skulls = new HashMap<>();
    private static final Map<String, String> hashes = new HashMap<>();
    private static int watcherId = -1;
    private static int clock;
    private static int timerClickAt = Integer.MIN_VALUE;
    private static boolean firstWave = true;
    private static boolean clickedThisTick;
    private static InputConstants.Key pressed;
    private static boolean wasActive;

    private BloodClick() {
    }

    public static boolean enabled() {
        return CheatConfigManager.get().bloodClickEnabled;
    }

    public static void reset() {
        mobs.clear();
        skulls.clear();
        watcherId = -1;
        firstWave = true;
        wasActive = false;
        BloodLog.flush();
    }

    public static void onChat(String message) {
        if (WATCHER_MOVES.equals(IgnUtil.stripCodes(message).trim()) && firstWave) {
            firstWave = false;
            BloodLog.line("the Watcher moved, later spawns take " + SPAWN_TICKS + " ticks");
        }
    }

    private static boolean active(Minecraft client) {
        return enabled() && client.player != null && client.level != null && DungeonState.inDungeon()
                && !DungeonBoss.inBoss();
    }

    private static long now() {
        return ServerTickClock.isAvailable() ? ServerTickClock.ticks() : clock;
    }

    public static void onStartTick(Minecraft client) {
        clock++;
        clickedThisTick = false;
        release(client);
        if (!active(client)) {
            if (wasActive) {
                BloodLog.line("stopped watching: dungeon " + DungeonState.inDungeon() + ", boss " + DungeonBoss.inBoss());
            }
            if (wasActive || watcherId != -1 || !mobs.isEmpty() || !skulls.isEmpty()) {
                reset();
            }
            return;
        }
        if (!wasActive) {
            wasActive = true;
            BloodLog.line("watching for blood mobs, server ticks " + (ServerTickClock.isAvailable() ? "on" : "off"));
        }
        Entity watcher = findWatcher(client.level);
        trackSkulls(client, client.player, client.level, watcher);
        trackMobs(client.player, client.level, watcher);
        clickMobs(client, client.player, client.level);
    }

    private static Entity findWatcher(ClientLevel level) {
        Entity watcher = watcherId == -1 ? null : level.getEntity(watcherId);
        if (watcher != null && watcher.isAlive()) {
            return watcher;
        }
        if (watcherId != -1) {
            BloodLog.line("lost the Watcher");
        }
        watcherId = -1;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof Zombie zombie && WATCHER.contains(hash(zombie.getItemBySlot(EquipmentSlot.HEAD)))) {
                watcherId = zombie.getId();
                BloodLog.line("found the Watcher at " + BloodLog.pos(zombie.position()));
                return zombie;
            }
        }
        return null;
    }

    private static void trackSkulls(Minecraft client, LocalPlayer player, ClientLevel level, Entity watcher) {
        long now = now();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand)
                    || (watcher != null && stand.distanceToSqr(watcher) > WATCHER_RANGE_SQ)) {
                continue;
            }
            Skull skull = skulls.get(stand.getId());
            if (skull == null) {
                if (!SKULLS.contains(hash(stand.getItemBySlot(EquipmentSlot.HEAD)))) {
                    continue;
                }
                skull = new Skull(stand.position(), firstWave);
                skulls.put(stand.getId(), skull);
            }
            Vec3 at = stand.position();
            double dx = at.x - skull.last.x;
            double dy = at.y - skull.last.y;
            double dz = at.z - skull.last.z;
            if ((dx * dx) + (dy * dy) + (dz * dz) > MOVED_SQ) {
                if (skull.startTick < 0L) {
                    skull.startTick = now;
                    skull.start = skull.last;
                    BloodLog.line(String.format(Locale.ROOT, "skull %d started flying%s, timer hits 0 in %d ticks",
                            stand.getId(), skull.first ? " (first wave)" : "", skull.due() - now));
                }
                skull.sx += dx;
                skull.sy += dy;
                skull.sz += dz;
                skull.last = at;
            }
        }
        skulls.entrySet().removeIf(entry -> {
            Skull skull = entry.getValue();
            boolean gone = level.getEntity(entry.getKey()) == null;
            if (!skull.fired && skull.startTick >= 0L && (gone || now >= skull.due() - PRE_FIRE_TICKS)) {
                fire(client, player, level, entry.getKey(), skull, gone);
            }
            return gone;
        });
    }

    private static void fire(Minecraft client, LocalPlayer player, ClientLevel level, int id, Skull skull, boolean gone) {
        if (clickedThisTick) {
            return;
        }
        skull.fired = true;
        Vec3 end = skull.end();
        if (end == null || Mc.screen() != null) {
            return;
        }
        AABB box = new AABB(end.x - BOX_HALF, end.y + BOX_BOTTOM, end.z - BOX_HALF,
                end.x + BOX_HALF, end.y + BOX_TOP, end.z + BOX_HALF).inflate(BOX_SLACK);
        double away = Math.sqrt(box.getCenter().distanceToSqr(player.getEyePosition()));
        if (!lookingAt(player, level, box)) {
            BloodLog.line(String.format(Locale.ROOT, "skull %d about to spawn%s, not aimed at its spawn box %s (%.1f away)",
                    id, gone ? " (skull gone)" : "", BloodLog.pos(end), away));
            return;
        }
        if (!heldAllowed(player)) {
            BloodLog.line("skull " + id + " about to spawn, not clicking, "
                    + player.getMainHandItem().getHoverName().getString() + " isn't whitelisted");
            return;
        }
        press(client);
        timerClickAt = clock;
        BloodLog.line(String.format(Locale.ROOT, "pre-fired skull %d's spawn box 1 tick before timer 0%s, %.1f away",
                id, gone ? " (skull gone)" : "", away));
    }

    private static boolean bloodMob(Entity entity) {
        if ("giant".equals(entity.getType().toShortString())) {
            return true;
        }
        return entity instanceof Player other && other.getUUID().version() != 4
                && NAMES.contains(IgnUtil.stripCodes(entity.getName().getString()).trim());
    }

    private static void trackMobs(LocalPlayer player, ClientLevel level, Entity watcher) {
        mobs.keySet().removeIf(id -> {
            Entity entity = level.getEntity(id);
            if (entity == null || !entity.isAlive()) {
                Mob mob = mobs.get(id);
                BloodLog.line("mob " + id + " gone after " + (clock - mob.seen) + " ticks, clicked " + mob.clicks + "x");
                return true;
            }
            return false;
        });
        if (watcher == null && skulls.isEmpty()) {
            return;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity) || entity == player || !entity.isAlive()
                    || mobs.containsKey(entity.getId()) || !bloodMob(entity) || !nearBlood(entity, watcher)) {
                continue;
            }
            mobs.put(entity.getId(), new Mob(clock));
            BloodLog.line(String.format(Locale.ROOT, "new blood mob %d '%s' at %s, %s, %.2f above the floor, %.1f from you",
                    entity.getId(), entity.getName().getString().trim(), BloodLog.pos(entity.position()),
                    inAir(level, entity) ? "in the air" : "on the floor", entity.getY() - floor(level, entity),
                    Math.sqrt(entity.distanceToSqr(player.getEyePosition()))));
        }
    }

    private static boolean nearBlood(Entity entity, Entity watcher) {
        if (watcher != null && entity.distanceToSqr(watcher) <= MOB_RANGE_SQ) {
            return true;
        }
        for (Skull skull : skulls.values()) {
            if (entity.position().distanceToSqr(skull.last) <= SKULL_NEAR_SQ) {
                return true;
            }
        }
        return false;
    }

    private static void clickMobs(Minecraft client, LocalPlayer player, ClientLevel level) {
        if (Mc.screen() != null || mobs.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, Mob> entry : mobs.entrySet()) {
            Entity entity = level.getEntity(entry.getKey());
            Mob mob = entry.getValue();
            if (entity == null) {
                continue;
            }
            boolean air = inAir(level, entity);
            if (air != mob.air) {
                mob.air = air;
                BloodLog.line("mob " + entity.getId() + (air ? " is in the air" : " is on the floor") + " at "
                        + BloodLog.pos(entity.position()));
            }
            if (!air) {
                continue;
            }
            AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
            boolean looking = lookingAt(player, level, box);
            if (looking != mob.aimed) {
                mob.aimed = looking;
                BloodLog.line(String.format(Locale.ROOT, "mob %d %s, %d ticks old, %.1f blocks away", entity.getId(),
                        looking ? "aimed at" : "not aimed at any more", clock - mob.seen,
                        Math.sqrt(entity.distanceToSqr(player.getEyePosition()))));
            }
            if (!looking || clickedThisTick) {
                continue;
            }
            boolean leaving = leavingNextTick(player, level, entity, box);
            if (clock < mob.nextAt && !leaving) {
                continue;
            }
            if (mob.clicks == 0 && clock - timerClickAt <= TIMER_SHIELD) {
                mob.nextAt = timerClickAt + AGAIN_MIN;
                continue;
            }
            if (!heldAllowed(player)) {
                if (mob.clicks == 0) {
                    BloodLog.line("not clicking mob " + entity.getId() + ", "
                            + player.getMainHandItem().getHoverName().getString() + " isn't whitelisted");
                }
                mob.nextAt = clock + AGAIN_MAX;
                continue;
            }
            press(client);
            mob.clicks++;
            mob.nextAt = clock + AGAIN_MIN + ThreadLocalRandom.current().nextInt((AGAIN_MAX - AGAIN_MIN) + 1);
            HitResult target = client.hitResult;
            boolean onMob = target instanceof EntityHitResult hit && hit.getEntity() == entity;
            BloodLog.line(String.format(Locale.ROOT, "clicked mob %d, %d ticks old%s, %.1f blocks away, crosshair %s "
                            + "(reach %.1f)", entity.getId(), clock - mob.seen,
                    leaving ? " (about to leave the crosshair)" : "",
                    Math.sqrt(entity.distanceToSqr(player.getEyePosition())),
                    onMob ? "on it" : "past reach (" + (target == null ? "none" : target.getType()) + ")",
                    player.entityInteractionRange()));
        }
    }

    private static void press(Minecraft client) {
        InputConstants.Key key = ((KeyMappingAccessor) client.options.keyAttack).qzaKey();
        KeyMapping.set(key, true);
        KeyMapping.click(key);
        pressed = key;
        clickedThisTick = true;
    }

    private static void release(Minecraft client) {
        if (pressed == null) {
            return;
        }
        boolean held = pressed.getType() == InputConstants.Type.MOUSE
                && pressed.getValue() == InputConstants.MOUSE_BUTTON_LEFT && client.mouseHandler.isLeftPressed();
        if (!held) {
            KeyMapping.set(pressed, false);
        }
        pressed = null;
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
