package com.qza.dungeon;

import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.render.WorldDraw;
import com.qza.timer.ServerTickClock;
import com.qza.util.DungeonState;
import com.qza.util.IgnUtil;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class BloodCampTracker {
    public static final Pattern GREETING = Pattern.compile("^\\[BOSS] The Watcher: (?:"
            + "Congratulations, you made it through the Entrance\\."
            + "|Ah, you've finally arrived\\."
            + "|Ah, we meet again\\.\\.\\."
            + "|So you made it this far\\.\\.\\. interesting\\."
            + "|You've managed to scratch and claw your way here, eh\\?"
            + "|I'm starting to get tired of seeing you around here\\.\\.\\."
            + "|Oh\\.\\. hello\\?"
            + "|Things feel a little more roomy now, eh\\?"
            + "|Ah, we meet again\\. As I foresaw\\.\\.\\.)$");
    public static final String MOVES = "[BOSS] The Watcher: Let's see how you can handle this.";

    private static final double WATCHER_RANGE_SQ = 20.0 * 20.0;
    private static final double MOVED_SQ = 1.0E-6;
    private static final long SPAWN_TICKS = 38L;
    private static final long FIRST_WAVE_TICKS = 40L;
    private static final long MOVE_TICKS = 410L;
    private static final long KILL_TICKS = 400L;
    public static final String KILL_TEXT = "Kill Mobs";
    private static final double FLIGHT = 11.9;
    private static final double FIRST_FLIGHT = 16.1;
    private static final double SECONDS_PER_TICK = 0.05;
    private static final double MOVING_SECONDS = 3.0;

    private static final double BOX_HALF = 0.5;
    private static final double BOX_BOTTOM = 1.5;
    private static final double BOX_TOP = 2.5;
    private static final double CENTRE = 2.0;
    private static final double TIMER_HEIGHT = 2.95;
    private static final double WATCHER_LABEL_HEIGHT = 3.1;
    private static final double SMOOTH_MS = 100.0;
    private static final double GROW = 0.002;
    private static final float OUTLINE_WIDTH = 2.5f;
    private static final float PATH_WIDTH = 2.0f;
    private static final float TIMER_SCALE = 0.05f;
    private static final float WATCHER_SCALE = 0.035f;
    private static final double FADE_SECONDS = 2.0;

    private static final int PINK = 0xFFFF55FF;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int READY = 0xFF55FFFF;
    private static final int PATH_START = 0xB0FFFFFF;
    private static final int BACKDROP = 0x8C23282E;
    private static final int FILL_MIN_ALPHA = 0x30;
    private static final int FILL_MAX_ALPHA = 0x70;

    private static final class Track {
        final Vec3 origin;
        boolean first;
        Vec3 last;
        long startTick = -1L;
        boolean serverClock;
        Vec3 shown;
        long shownAt;

        Track(Vec3 origin) {
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

    private record Shown(int id, Vec3 end, double seconds, int colour) {
    }

    private static final Map<Integer, Track> tracks = new HashMap<>();
    private static final Map<Integer, Vec3> seenAt = new HashMap<>();
    private static final Set<Integer> notMobs = new HashSet<>();
    private static int watcherId = -1;
    private static boolean firstWave = true;
    private static long moveStart = -1L;
    private static boolean moveServerClock;
    private static boolean killShown;
    private static long clientTicks;

    private BloodCampTracker() {
    }

    public static void init() {
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            CameraRenderState camera = context.levelState().cameraRenderState;
            if (camera != null && camera.pos != null && enabled()) {
                draw(context.submitNodeCollector(), camera);
            }
        });
    }

    private static boolean enabled() {
        return ConfigManager.get().bloodTrackerEnabled;
    }

    public static void onChatMessage(String raw) {
        if (raw == null) {
            return;
        }
        String message = IgnUtil.stripCodes(raw).trim();
        if (GREETING.matcher(message).matches()) {
            firstWave = true;
            moveServerClock = ServerTickClock.ticking();
            moveStart = now(moveServerClock);
            killShown = false;
        } else if (MOVES.equals(message)) {
            firstWave = false;
            moveStart = -1L;
        }
    }

    public static void tick(Minecraft client) {
        clientTicks++;
        ClientLevel level = client.level;
        if (!enabled() || level == null || !DungeonState.inDungeon()) {
            if (!tracks.isEmpty() || !seenAt.isEmpty() || watcherId != -1) {
                clearMobs();
                watcherId = -1;
            }
            return;
        }
        if (moveStart >= 0L && !killShown && now(moveServerClock) - moveStart >= KILL_TICKS) {
            killShown = true;
            if (ConfigManager.get().bloodTrackerKillAlert) {
                Mc.showTitle(Component.literal(KILL_TEXT).withStyle(ChatFormatting.LIGHT_PURPLE), 0, 30, 10);
                client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f));
            }
        }
        Entity watcher = findWatcher(level);
        if (watcher != null) {
            track(level, watcher);
        }
        tracks.keySet().removeIf(id -> gone(level, id));
        seenAt.keySet().removeIf(id -> gone(level, id));
        notMobs.removeIf(id -> gone(level, id));
    }

    private static boolean gone(ClientLevel level, int id) {
        Entity entity = level.getEntity(id);
        return entity == null || entity.isRemoved();
    }

    private static Entity findWatcher(ClientLevel level) {
        Entity known = watcherId == -1 ? null : level.getEntity(watcherId);
        if (known != null && known.isAlive()) {
            return known;
        }
        watcherId = -1;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof Zombie zombie && BloodSkulls.watcher(zombie.getItemBySlot(EquipmentSlot.HEAD))) {
                watcherId = zombie.getId();
                return zombie;
            }
        }
        return null;
    }

    private static void track(ClientLevel level, Entity watcher) {
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand) || stand.distanceToSqr(watcher) > WATCHER_RANGE_SQ) {
                continue;
            }
            int id = stand.getId();
            Vec3 at = stand.position();
            Vec3 origin = seenAt.computeIfAbsent(id, key -> at);
            Track track = tracks.get(id);
            if (track == null) {
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
                track = new Track(origin);
                tracks.put(id, track);
            }
            if (at.distanceToSqr(track.last) > MOVED_SQ) {
                if (track.startTick < 0L) {
                    track.first = firstWave;
                    track.serverClock = ServerTickClock.ticking();
                    track.startTick = now(track.serverClock);
                }
                track.last = at;
            }
        }
    }

    private static long now(boolean server) {
        return server ? ServerTickClock.ticks() : clientTicks;
    }

    private static double partial(boolean server) {
        if (server) {
            return ServerTickClock.partialTick();
        }
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    private static double secondsUntil(long tick, boolean server) {
        return (tick - now(server) - partial(server)) * SECONDS_PER_TICK;
    }

    private static void draw(SubmitNodeCollector collector, CameraRenderState camera) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null) {
            return;
        }
        QZAConfig cfg = ConfigManager.get();
        long wall = System.currentTimeMillis();
        float entityPartial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<Shown> shown = new ArrayList<>();
        for (Map.Entry<Integer, Track> entry : tracks.entrySet()) {
            Track track = entry.getValue();
            Vec3 target = track.end();
            if (target == null) {
                continue;
            }
            if (track.shown == null) {
                track.shown = target;
            } else {
                double step = Math.min(1.0, (wall - track.shownAt) / SMOOTH_MS);
                track.shown = track.shown.lerp(target, step);
            }
            track.shownAt = wall;
            double seconds = secondsUntil(track.due(), track.serverClock);
            shown.add(new Shown(entry.getKey(), track.shown, seconds, colour(seconds)));
        }

        Vec3 cam = camera.pos;
        if (!shown.isEmpty()) {
            WorldDraw.submit(collector, RenderTypes.debugFilledBox(), (pose, buffer) -> {
                for (Shown mob : shown) {
                    WorldDraw.fill(buffer, pose, box(mob.end(), cam), withAlpha(mob.colour(), fillAlpha(mob.seconds())));
                }
            });
            WorldDraw.submit(collector, RenderTypes.lines(), (pose, buffer) -> {
                for (Shown mob : shown) {
                    WorldDraw.outline(buffer, pose, box(mob.end(), cam), mob.colour(), OUTLINE_WIDTH);
                    Entity stand = level.getEntity(mob.id());
                    if (cfg.bloodTrackerLine && stand != null) {
                        Vec3 from = stand.getPosition(entityPartial).add(0, CENTRE, 0).subtract(cam);
                        Vec3 to = mob.end().add(0, CENTRE, 0).subtract(cam);
                        WorldDraw.line(buffer, pose, from, to, PATH_START, mob.colour(), PATH_WIDTH);
                    }
                }
            });
            if (cfg.bloodTrackerTimer) {
                for (Shown mob : shown) {
                    WorldDraw.text(collector, camera, mob.end().add(0, TIMER_HEIGHT, 0), timerText(mob.seconds()),
                            mob.colour(), TIMER_SCALE, BACKDROP);
                }
            }
        }

        if (cfg.bloodTrackerWatcherTimer && moveStart >= 0L) {
            Entity watcher = watcherId == -1 ? null : level.getEntity(watcherId);
            double seconds = secondsUntil(moveStart + MOVE_TICKS, moveServerClock);
            if (watcher != null && seconds > -MOVING_SECONDS) {
                String text = seconds > 0
                        ? String.format(Locale.ROOT, "Watcher moves in %.1fs", seconds)
                        : "Watcher is moving";
                WorldDraw.text(collector, camera, watcher.getPosition(entityPartial).add(0, WATCHER_LABEL_HEIGHT, 0),
                        text, seconds > 0 ? PINK : READY, WATCHER_SCALE, BACKDROP);
            }
        }
    }

    private static AABB box(Vec3 end, Vec3 camera) {
        return new AABB(end.x - BOX_HALF, end.y + BOX_BOTTOM, end.z - BOX_HALF,
                end.x + BOX_HALF, end.y + BOX_TOP, end.z + BOX_HALF)
                .inflate(GROW)
                .move(-camera.x, -camera.y, -camera.z);
    }

    static String timerText(double seconds) {
        return seconds > 0 ? String.format(Locale.ROOT, "%.2fs", seconds) : "SPAWN!";
    }

    static int colour(double seconds) {
        if (seconds <= 0) {
            return READY;
        }
        if (seconds >= 1.0) {
            return PINK;
        }
        return mix(PINK, WHITE, 1.0 - seconds);
    }

    private static int fillAlpha(double seconds) {
        double close = 1.0 - Math.max(0.0, Math.min(1.0, seconds / FADE_SECONDS));
        return (int) Math.round(FILL_MIN_ALPHA + ((FILL_MAX_ALPHA - FILL_MIN_ALPHA) * close));
    }

    private static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    private static int mix(int from, int to, double amount) {
        double t = Math.max(0.0, Math.min(1.0, amount));
        int r = (int) Math.round(((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t));
        int g = (int) Math.round(((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t));
        int b = (int) Math.round((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * t));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void clearMobs() {
        tracks.clear();
        seenAt.clear();
        notMobs.clear();
    }

    public static void reset() {
        clearMobs();
        watcherId = -1;
        firstWave = true;
        moveStart = -1L;
        killShown = false;
    }

    public static int tracked() {
        return tracks.size();
    }
}
