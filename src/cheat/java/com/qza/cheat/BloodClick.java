package com.qza.cheat;

import com.mojang.blaze3d.platform.InputConstants;
import com.qza.cheat.click.AutoClicker;
import com.qza.cheat.mixin.KeyMappingAccessor;
import com.qza.cheat.movement.DungeonBoss;
import com.qza.compat.Mc;
import com.qza.dungeon.BloodCampTracker;
import com.qza.dungeon.BloodSkulls;
import com.qza.timer.ServerTickClock;
import com.qza.util.ChatUtil;
import com.qza.util.DungeonState;
import com.qza.util.IgnUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class BloodClick {
    private static final Set<String> NAMES = Set.of("Revoker", "Psycho", "Reaper", "Cannibal", "Mute", "Ooze",
            "Putrid", "Freak", "Leech", "Tear", "Parasite", "Flamer", "Skull", "Mr. Dead", "Vader", "Frost", "Walker",
            "Wandering Soul", "WanderingSoul", "Bonzo", "Scarf", "Livid", "Spirit Bear", "Giant");

    private static final double WATCHER_RANGE_SQ = 20.0 * 20.0;
    private static final double MOB_RANGE_SQ = 32.0 * 32.0;
    private static final double SKULL_NEAR_SQ = 4.0 * 4.0;
    private static final double MOVED_SQ = 1.0E-6;
    private static final long SPAWN_TICKS = 38L;
    private static final long FIRST_WAVE_TICKS = 40L;
    private static final double SECONDS_PER_TICK = 0.05;
    private static final double FLIGHT = 11.9;
    private static final double FIRST_FLIGHT = 16.1;
    private static final double MOB_SLACK = 0.2;
    private static final double BOX_BOTTOM = 1.5;
    private static final double BOX_TOP = 2.5;
    private static final double BOX_HALF = 0.5;
    private static final double BOX_SLACK = 0.1;
    private static final int FIRST_DELAY = 1;
    private static final int AGAIN_MIN = 4;
    private static final int AGAIN_MAX = 6;
    public static final double MIN_RANGE = 1.0;
    public static final double MAX_RANGE = 60.0;
    public static final double MIN_PRE_FIRE = 0.01;
    public static final double MAX_PRE_FIRE = 0.5;
    private static final double AIR_GAP = 0.05;
    private static final double FLOOR_SEARCH = 16.0;
    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;

    private static final class Skull {
        final Vec3 origin;
        boolean first;
        Vec3 last;
        long startTick = -1L;
        boolean serverClock;
        boolean fired;

        Skull(Vec3 origin) {
            this.origin = origin;
            this.last = origin;
        }

        Vec3 end() {
            if (startTick < 0L) {
                return null;
            }
            Vec3 path = last.subtract(origin);
            double length = path.length();
            if (length < 1.0E-6) {
                return null;
            }
            return origin.add(path.scale((first ? FIRST_FLIGHT : FLIGHT) / length));
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
            this.nextAt = seen + FIRST_DELAY;
        }
    }

    private static final Map<Integer, Mob> mobs = new HashMap<>();
    private static final Map<Integer, Skull> skulls = new HashMap<>();
    private static final Map<Integer, Vec3> seenAt = new HashMap<>();
    private static final Set<Integer> notMobs = new HashSet<>();
    private static int watcherId = -1;
    private static int clock;
    private static boolean firstWave = true;
    private static boolean clickedThisTick;
    private static boolean wasActive;
    private static boolean menuLogged;
    private static int preFiredAt = Integer.MIN_VALUE / 2;

    private BloodClick() {
    }

    public static boolean enabled() {
        return CheatConfigManager.get().bloodClickEnabled;
    }

    public static void reset() {
        clearTracking();
        firstWave = true;
    }

    private static void clearTracking() {
        mobs.clear();
        skulls.clear();
        seenAt.clear();
        notMobs.clear();
        watcherId = -1;
        wasActive = false;
        BloodLog.flush();
    }

    public static void onChat(String raw) {
        String message = IgnUtil.stripCodes(raw).trim();
        if (BloodCampTracker.GREETING.matcher(message).matches()) {
            firstWave = true;
            BloodLog.line("the Watcher greeted you, first wave spawns take " + (SPAWN_TICKS + FIRST_WAVE_TICKS) + " ticks");
        } else if (BloodCampTracker.MOVES.equals(message) && firstWave) {
            firstWave = false;
            BloodLog.line("the Watcher moved, later spawns take " + SPAWN_TICKS + " ticks");
        }
    }

    private static boolean active(Minecraft client) {
        return enabled() && client.player != null && client.level != null && DungeonState.inDungeon()
                && !DungeonBoss.inBoss();
    }

    private static long now(boolean server) {
        return server ? ServerTickClock.ticks() : clock;
    }

    private static double secondsUntil(Minecraft client, Skull skull) {
        double partial = skull.serverClock ? ServerTickClock.partialTick()
                : client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return (skull.due() - now(skull.serverClock) - partial) * SECONDS_PER_TICK;
    }

    private static boolean gone(ClientLevel level, int id) {
        Entity entity = level.getEntity(id);
        return entity == null || entity.isRemoved();
    }

    public static void onStartTick(Minecraft client) {
        clock++;
        clickedThisTick = false;
        if (!active(client)) {
            if (wasActive) {
                BloodLog.line("stopped watching: dungeon " + DungeonState.inDungeon() + ", boss " + DungeonBoss.inBoss());
            }
            if (wasActive || watcherId != -1 || !mobs.isEmpty() || !skulls.isEmpty() || !seenAt.isEmpty()) {
                clearTracking();
            }
            return;
        }
        if (!wasActive) {
            wasActive = true;
            BloodLog.line("watching for blood mobs, server ticks " + (ServerTickClock.ticking() ? "on" : "off"));
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
            if (entity instanceof Zombie zombie && BloodSkulls.watcher(zombie.getItemBySlot(EquipmentSlot.HEAD))) {
                watcherId = zombie.getId();
                BloodLog.line("found the Watcher at " + BloodLog.pos(zombie.position()));
                return zombie;
            }
        }
        return null;
    }

    private static void trackSkulls(Minecraft client, LocalPlayer player, ClientLevel level, Entity watcher) {
        if (watcher != null) {
            for (Entity entity : level.entitiesForRendering()) {
                if (!(entity instanceof ArmorStand stand) || stand.distanceToSqr(watcher) > WATCHER_RANGE_SQ) {
                    continue;
                }
                int id = stand.getId();
                Vec3 at = stand.position();
                Vec3 origin = seenAt.computeIfAbsent(id, key -> at);
                Skull skull = skulls.get(id);
                if (skull == null) {
                    if (notMobs.contains(id)) {
                        continue;
                    }
                    ItemStack head = stand.getItemBySlot(EquipmentSlot.HEAD);
                    if (head.isEmpty()) {
                        continue;
                    }
                    if (!BloodSkulls.mob(head)) {
                        notMobs.add(id);
                        continue;
                    }
                    skull = new Skull(origin);
                    skulls.put(id, skull);
                }
                if (at.distanceToSqr(skull.last) > MOVED_SQ) {
                    if (skull.startTick < 0L) {
                        skull.first = firstWave;
                        skull.serverClock = ServerTickClock.ticking();
                        skull.startTick = now(skull.serverClock);
                        BloodLog.line(String.format(Locale.ROOT, "skull %d started flying%s, timer hits 0 in %.2fs",
                                id, skull.first ? " (first wave)" : "", secondsUntil(client, skull)));
                    }
                    skull.last = at;
                }
            }
        }
        if (CheatConfigManager.get().bloodClickPreFire) {
            double lead = preFireSeconds() + (SECONDS_PER_TICK / 2.0);
            for (Map.Entry<Integer, Skull> entry : skulls.entrySet()) {
                Skull skull = entry.getValue();
                if (!skull.fired && skull.startTick >= 0L && !gone(level, entry.getKey())
                        && secondsUntil(client, skull) <= lead) {
                    preFire(client, player, level, entry.getKey(), skull);
                }
            }
        }
        skulls.keySet().removeIf(id -> gone(level, id));
        seenAt.keySet().removeIf(id -> gone(level, id));
        notMobs.removeIf(id -> gone(level, id));
    }

    private static double preFireSeconds() {
        return Math.max(MIN_PRE_FIRE, Math.min(MAX_PRE_FIRE, CheatConfigManager.get().bloodClickPreFireSeconds));
    }

    private static void preFire(Minecraft client, LocalPlayer player, ClientLevel level, int id, Skull skull) {
        if (clickedThisTick) {
            return;
        }
        skull.fired = true;
        Vec3 end = skull.end();
        if (end == null) {
            return;
        }
        if (Mc.screen() != null) {
            BloodLog.line("skull " + id + " about to spawn, not pre-firing, a menu is open");
            return;
        }
        AABB box = new AABB(end.x - BOX_HALF, end.y + BOX_BOTTOM, end.z - BOX_HALF,
                end.x + BOX_HALF, end.y + BOX_TOP, end.z + BOX_HALF).inflate(BOX_SLACK);
        double away = Math.sqrt(box.getCenter().distanceToSqr(player.getEyePosition()));
        if (!lookingAt(player, level, box)) {
            BloodLog.line(String.format(Locale.ROOT, "skull %d about to spawn, not aimed at its spawn box %s (%.1f away)",
                    id, BloodLog.pos(end), away));
            return;
        }
        if (!heldAllowed(player)) {
            BloodLog.line("skull " + id + " about to spawn, not pre-firing, "
                    + player.getMainHandItem().getHoverName().getString() + " isn't whitelisted");
            return;
        }
        double early = secondsUntil(client, skull);
        press(client);
        preFiredAt = clock;
        BloodLog.line(String.format(Locale.ROOT, "pre-fired skull %d's spawn box %.2fs before timer 0, %.1f away",
                id, early, away));
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
        if (mobs.isEmpty()) {
            return;
        }
        if (Mc.screen() != null) {
            if (!menuLogged) {
                menuLogged = true;
                BloodLog.line("a menu is open, so Minecraft can't click blood mobs until it closes");
            }
            return;
        }
        menuLogged = false;
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
            AABB box = entity.getBoundingBox().inflate(entity.getPickRadius() + MOB_SLACK);
            boolean looking = lookingAt(player, level, box);
            if (looking != mob.aimed) {
                mob.aimed = looking;
                BloodLog.line(String.format(Locale.ROOT, "mob %d %s, %d ticks old, %.1f blocks away", entity.getId(),
                        looking ? "aimed at" : "not aimed at any more", clock - mob.seen,
                        Math.sqrt(entity.distanceToSqr(player.getEyePosition()))));
            }
            if (!looking || clickedThisTick || clock - mob.seen < FIRST_DELAY || clock - preFiredAt < AGAIN_MIN) {
                continue;
            }
            boolean leaving = leavingNextTick(player, level, entity, box);
            if (clock < mob.nextAt && !(leaving && mob.clicks == 0)) {
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
        boolean held = key.getType() == InputConstants.Type.MOUSE
                && key.getValue() == InputConstants.MOUSE_BUTTON_LEFT && client.mouseHandler.isLeftPressed();
        KeyMapping.set(key, true);
        KeyMapping.click(key);
        if (!held) {
            KeyMapping.set(key, false);
        }
        clickedThisTick = true;
        if (client.player != null && client.player.isUsingItem()) {
            BloodLog.line("you're using an item, so Minecraft drops this click");
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
}
