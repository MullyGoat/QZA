package com.qza.cheat.movement;

import com.mojang.blaze3d.platform.InputConstants;
import com.qza.chat.ChatKeybind;
import com.qza.cheat.CheatConfigManager;
import com.qza.cheat.mixin.KeyMappingAccessor;
import com.qza.cheat.mixin.MinecraftAccessor;
import com.qza.compat.Mc;
import com.qza.gui.QZAScreen;
import com.qza.keybind.CommandKeybinds;
import com.qza.timer.ServerTickClock;
import com.qza.util.ChatUtil;
import com.qza.util.Scheduler;
import com.qza.util.SkyBlockArea;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class MovementCopier {
    public enum State { IDLE, COUNTDOWN, RECORDING, PLAYING }

    public static final String WAIT_LAG = "waiting for the server";
    public static final String WAIT_TELEPORT = "waiting for the teleport";

    public static final int MAX_FRAMES = 20 * 60 * 10;

    private static final long TELEPORT_WAIT_MS = 3000L;
    private static final long LAG_WAIT_MS = 15000L;
    private static final long LIVE_MS = 2000L;
    private static final long STOP_SHOWN_MS = 4000L;
    private static final double TICK_MS = 50.0;
    private static final int TELEPORT_LOOKAHEAD = 40;
    private static final int ARM_TICKS = 10;
    private static final int LAND_TICKS = 2;
    private static final double MOVED_SQ = 0.05 * 0.05;
    private static final double PLACE_RANGE = 61.0;
    private static final double AIM_RANGE = 64.0;
    private static final double AIM_DRIFT = 0.01;
    private static final float MAX_AIM_FIX = 30.0f;
    private static final double MAX_DRIFT = 4.0;
    private static final double NEAR_BELOW = 0.3;
    private static final double SAME_START = 2.0;
    private static final int START_SEARCH = 3;
    private static final Identifier SPRINTING = Identifier.withDefaultNamespace("sprinting");
    private static final double SPRINT_BOOST = 1.3;
    private static final double SPEED_UNITS = 1000.0;
    private static final double SPEED_SLACK = 0.5;
    public static final double RING_RADIUS = 0.25;
    private static final double RING_REACH = 3.0;
    private static final double RING_HEIGHT = 0.1;
    private static final long SETTLE_MS = 1000L;
    private static final double STOPPED = 0.003;
    private static final long NOTICE_MS = 2500L;
    private static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF55FF55;
    private static final int YELLOW = 0xFFFFFF55;
    private static final int LAG_TEST_TICK = 20;

    private static State state = State.IDLE;
    private static long countdownEnd;
    private static Recording recording;
    private static int recordTeleports;

    private static Recording playing;
    private static Recording pending;
    private static String pendingWhy = "";
    private static int[] schedule = new int[0];
    private static int step;
    private static int applied = -1;
    private static int played;
    private static int teleportBase;
    private static int teleportsSeen;
    private static long playStarted;
    private static boolean frozen;
    private static String waiting;
    private static long waitingSince;
    private static int heldDelay;
    private static float lookYaw;
    private static float lookPitch;
    private static int aimFixes;
    private static double offX;
    private static double offY;
    private static double offZ;

    private static long serverTick = Long.MIN_VALUE;
    private static long serverTickAt;

    private static Vec3 preTeleport;
    private static int armed;
    private static int armedAge;
    private static int lagTestSeconds;
    private static boolean capturing;

    private static boolean lining;
    private static HumanTurn turn;
    private static long liningSince;
    private static float yawShift;
    private static boolean walkStart;

    private static final Set<Recording> insideRings = new HashSet<>();
    private static Vec3 lastTickPos;
    private static boolean ringsPrimed;
    private static int ringSettle;
    private static long noticeShownFor;

    private static String stopText;
    private static int stopColour = RED;
    private static long stopAt;
    private static long stopShownMs = STOP_SHOWN_MS;

    private MovementCopier() {
    }

    public static State state() {
        return state;
    }

    public static boolean enabled() {
        return CheatConfigManager.get().movementCopierEnabled;
    }

    public static boolean frozen() {
        return state == State.PLAYING && frozen;
    }

    public static boolean playing() {
        return state == State.PLAYING;
    }

    public static Recording current() {
        return state == State.PLAYING ? playing : null;
    }

    public static String waiting() {
        return state == State.PLAYING && !lining ? waiting : null;
    }

    public static boolean lining() {
        return state == State.PLAYING && lining;
    }

    public static String stopMessage() {
        return stopText != null && System.currentTimeMillis() - stopAt < stopShownMs ? stopText : null;
    }

    public static int stopColour() {
        return stopColour;
    }

    private static void show(String text, int colour, long ms) {
        stopText = text;
        stopColour = colour;
        stopAt = System.currentTimeMillis();
        stopShownMs = ms;
    }

    public static long countdownMillis() {
        return state == State.COUNTDOWN ? Math.max(0L, countdownEnd - System.currentTimeMillis()) : 0L;
    }

    public static double recordedSeconds() {
        return recording == null ? 0.0 : recording.size() * Recording.SECONDS_PER_FRAME;
    }

    public static double playedSeconds() {
        return step * Recording.SECONDS_PER_FRAME;
    }

    public static double playLength() {
        return schedule.length * Recording.SECONDS_PER_FRAME;
    }

    public static String keyLabel() {
        return CommandKeybinds.keyLabel(CheatConfigManager.get().movementCopierKey);
    }

    public static boolean hasKey() {
        String key = CheatConfigManager.get().movementCopierKey;
        return key != null && !key.isBlank();
    }

    public static boolean capturing() {
        return capturing;
    }

    public static void startCapture() {
        capturing = true;
    }

    public static void cancelCapture() {
        capturing = false;
    }

    public static boolean onKey(KeyEvent event, int action) {
        if (capturing) {
            if (!(Mc.screen() instanceof QZAScreen)) {
                capturing = false;
                return false;
            }
            if (action != InputConstants.PRESS) {
                return false;
            }
            capturing = false;
            int code = event.key();
            if (code != InputConstants.KEY_ESCAPE) {
                CheatConfigManager.get().movementCopierKey =
                        code == InputConstants.KEY_DELETE || code == InputConstants.KEY_BACKSPACE
                                ? "" : InputConstants.getKey(event).getName();
                CheatConfigManager.save();
            }
            ChatKeybind.swallowNextChar(event);
            return true;
        }

        if (action != InputConstants.PRESS) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || Mc.screen() != null || Mc.overlay() != null) {
            return false;
        }
        boolean recordKey = enabled() && hasKey()
                && InputConstants.getKey(event).getName().equals(CheatConfigManager.get().movementCopierKey);
        if (!recordKey && client.options.keyTogglePerspective.matches(event)) {
            return false;
        }

        if (state == State.PLAYING) {
            stopPlayback(recordKey ? "Stopped " + playing.name + "."
                    : "Stopped " + playing.name + " because you pressed a key.");
            if (recordKey) {
                ChatKeybind.swallowNextChar(event);
            }
            return recordKey;
        }
        if (armed > 0) {
            armed = 0;
            MovementLog.line("key pressed, not starting on this teleport");
        }
        if (!recordKey) {
            return false;
        }
        switch (state) {
            case IDLE -> beginCountdown();
            case COUNTDOWN -> cancelCountdown();
            case RECORDING -> finishRecording(false);
            default -> {
            }
        }
        ChatKeybind.swallowNextChar(event);
        return true;
    }

    public static void startCommand() {
        if (!checkEnabled()) {
            return;
        }
        switch (state) {
            case COUNTDOWN, RECORDING -> ChatUtil.error("Already recording. Use /qza movement copy stop to finish.");
            case PLAYING -> {
                stopPlayback(null);
                beginCountdown();
            }
            default -> beginCountdown();
        }
    }

    public static void stopCommand() {
        switch (state) {
            case COUNTDOWN -> cancelCountdown();
            case RECORDING -> finishRecording(true);
            case PLAYING -> stopPlayback("Stopped " + playing.name + ".");
            default -> {
                if (pending != null) {
                    pending = null;
                    ChatUtil.info("Cancelled the playback.");
                } else {
                    ChatUtil.error("Nothing is recording or playing.");
                }
            }
        }
    }

    public static boolean checkEnabled() {
        if (enabled()) {
            return true;
        }
        ChatUtil.error("Movement Copier is off. Turn it on in /qza under Cheats.");
        return false;
    }

    private static void beginCountdown() {
        pending = null;
        armed = 0;
        double seconds = CheatConfigManager.get().movementCountdown;
        if (seconds <= 0.0) {
            beginRecording();
            ChatUtil.info("Recording. " + stopHint());
            return;
        }
        state = State.COUNTDOWN;
        countdownEnd = System.currentTimeMillis() + Math.round(seconds * 1000.0);
        ChatUtil.info("Recording starts in " + Math.round(seconds) + "s. " + stopHint());
    }

    private static String stopHint() {
        return hasKey()
                ? "Press " + keyLabel() + " or use /qza movement copy stop to finish."
                : "Use /qza movement copy stop to finish.";
    }

    private static void cancelCountdown() {
        state = State.IDLE;
        ChatUtil.info("Recording cancelled.");
    }

    private static void beginRecording() {
        recording = new Recording();
        recording.created = System.currentTimeMillis();
        recordTeleports = 0;
        state = State.RECORDING;
    }

    private static void finishRecording(boolean viaCommand) {
        Recording done = recording;
        recording = null;
        state = State.IDLE;
        if (done == null) {
            return;
        }
        if (viaCommand) {
            while (!done.frames.isEmpty() && done.frames.get(done.frames.size() - 1).screen) {
                done.frames.remove(done.frames.size() - 1);
            }
        }
        if (done.frames.isEmpty()) {
            ChatUtil.error("Nothing was recorded.");
            return;
        }
        Scheduler.schedule(1, () -> Mc.setScreen(new RecordingNameScreen(done)));
    }

    private static void autoSave(String reason) {
        Recording done = recording;
        recording = null;
        state = State.IDLE;
        if (done == null || done.frames.isEmpty()) {
            return;
        }
        done.name = Recordings.nextName();
        boolean onStart = placeOnStart(done);
        Recordings.add(done);
        ChatUtil.info("Saved your recording as " + done.name + " because " + reason + "."
                + (onStart ? " It's on " + done.blockText() + ", the block you started on." : ""));
        if (onStart) {
            placed(done);
        }
    }

    public static boolean placeOnStart(Recording done) {
        BlockPos pos = done.startBlock;
        if (pos == null || !CheatConfigManager.get().movementPlaceStart) {
            return false;
        }
        Recording other = Recordings.at(pos, done.startArea);
        if (other != null && other != done && !other.name.equalsIgnoreCase(done.name)) {
            other.block = null;
            Recordings.save(other);
            ChatUtil.info("Took " + other.name + " off that block.");
        }
        done.block = new int[]{pos.getX(), pos.getY(), pos.getZ()};
        done.area = done.startArea == null ? "" : done.startArea;
        return true;
    }

    private static BlockPos startBlock(Level level, LocalPlayer player) {
        if (level == null) {
            return null;
        }
        BlockPos under = standingOn(player);
        if (solid(level, under)) {
            return under;
        }
        if (player.onGround() && player.mainSupportingBlockPos.isPresent()) {
            return player.mainSupportingBlockPos.get();
        }
        for (int down = 1; down <= START_SEARCH; down++) {
            BlockPos below = under.below(down);
            if (solid(level, below)) {
                return below;
            }
        }
        return null;
    }

    private static boolean solid(Level level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    public static void playSoon(Recording target) {
        if (!checkEnabled()) {
            return;
        }
        if (state == State.COUNTDOWN || state == State.RECORDING) {
            ChatUtil.error("Stop recording first.");
            return;
        }
        if (state == State.PLAYING) {
            stopPlayback(null);
        }
        pending = target;
        pendingWhy = "play button or command";
    }

    public static void onStartTick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }
        watchServer(client);
        if (armed > 0) {
            checkLanding(client, player);
        }
        checkRings(client, player);
        if (pending != null && state == State.IDLE && Mc.screen() == null) {
            Recording target = pending;
            pending = null;
            startPlayback(client, target, pendingWhy, false);
        }
        switch (state) {
            case COUNTDOWN -> {
                if (System.currentTimeMillis() >= countdownEnd) {
                    beginRecording();
                    capture(client, player);
                }
            }
            case RECORDING -> capture(client, player);
            case PLAYING -> playTick(client, player);
            default -> {
            }
        }
    }

    private static void watchServer(Minecraft client) {
        long now = serverTicks(client);
        if (now != serverTick) {
            serverTick = now;
            serverTickAt = System.currentTimeMillis();
        }
    }

    private static long serverTicks(Minecraft client) {
        IntegratedServer server = client.getSingleplayerServer();
        if (server != null) {
            return server.getTickCount();
        }
        return ServerTickClock.isAvailable() ? ServerTickClock.ticks() : -1L;
    }

    private static long quietMillis() {
        return System.currentTimeMillis() - serverTickAt;
    }

    private static boolean lagging() {
        if (serverTick < 0L || serverTickAt < playStarted - LIVE_MS) {
            return false;
        }
        return quietMillis() > CheatConfigManager.get().movementLagTolerance * TICK_MS;
    }

    private static KeyMapping[] heldKeys(Options options) {
        return new KeyMapping[]{options.keyUp, options.keyDown, options.keyLeft, options.keyRight,
                options.keyJump, options.keyShift, options.keySprint, options.keyAttack, options.keyUse};
    }

    private static KeyMapping[] clickKeys(Options options) {
        KeyMapping[] keys = new KeyMapping[MoveFrame.CLICK_KEYS];
        keys[MoveFrame.CLICK_ATTACK] = options.keyAttack;
        keys[MoveFrame.CLICK_USE] = options.keyUse;
        keys[MoveFrame.CLICK_PICK] = options.keyPickItem;
        keys[MoveFrame.CLICK_DROP] = options.keyDrop;
        keys[MoveFrame.CLICK_OFFHAND] = options.keySwapOffhand;
        for (int i = 0; i < 9; i++) {
            keys[MoveFrame.CLICK_HOTBAR + i] = options.keyHotbarSlots[i];
        }
        return keys;
    }

    private static void capture(Minecraft client, LocalPlayer player) {
        if (recording == null || client.isPaused()) {
            return;
        }
        if (recording.frames.isEmpty()) {
            recording.startBlock = startBlock(client.level, player);
            recording.startArea = SkyBlockArea.current();
        }
        MoveFrame frame = new MoveFrame();
        frame.yaw = player.getYRot();
        frame.pitch = player.getXRot();

        KeyMapping[] held = heldKeys(client.options);
        for (int i = 0; i < held.length; i++) {
            if (held[i].isDown()) {
                frame.keys |= 1 << i;
            }
        }

        KeyMapping[] clicks = clickKeys(client.options);
        for (int i = 0; i < clicks.length; i++) {
            int count = ((KeyMappingAccessor) clicks[i]).qzaClickCount();
            if (count > 0) {
                if (frame.clicks == null) {
                    frame.clicks = new int[MoveFrame.CLICK_KEYS];
                }
                frame.clicks[i] = count;
            }
        }

        frame.slot = player.getInventory().getSelectedSlot();
        frame.x = (float) player.getX();
        frame.y = (float) player.getY();
        frame.z = (float) player.getZ();
        frame.ground = player.onGround();
        frame.moveSpeed = (float) speedOf(player);
        frame.teleports = recordTeleports;
        frame.screen = Mc.screen() != null;

        MoveFrame last = recording.frames.isEmpty() ? null : recording.frames.get(recording.frames.size() - 1);
        boolean pressedUse = frame.held(MoveFrame.USE) && (last == null || !last.held(MoveFrame.USE));
        if (frame.clicks(MoveFrame.CLICK_USE) > 0 || frame.clicks(MoveFrame.CLICK_ATTACK) > 0 || pressedUse) {
            HitResult hit = player.pick(AIM_RANGE, 1.0f, false);
            if (hit.getType() == HitResult.Type.BLOCK) {
                Vec3 at = hit.getLocation();
                frame.aim = new float[]{(float) at.x, (float) at.y, (float) at.z};
            }
        }
        recording.frames.add(frame);

        if (recording.size() >= MAX_FRAMES) {
            ChatUtil.info("Recordings can be up to 10 minutes long, so this one stopped there.");
            finishRecording(false);
        }
    }

    public static double speedOf(LocalPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return 0.0;
        }
        double value = speed.getValue();
        if (speed.getModifier(SPRINTING) != null) {
            value /= SPRINT_BOOST;
        }
        return value * SPEED_UNITS;
    }

    public static double playerSpeed(LocalPlayer player) {
        double typed = CheatConfigManager.get().movementPlaySpeed;
        return typed > 0.0 ? typed : speedOf(player);
    }

    private static double recordedSpeed() {
        if (playing == null) {
            return 0.0;
        }
        int at = applied >= 0 ? applied : schedule.length > 0 ? schedule[0] : -1;
        return playing.speedAt(at);
    }

    public static float inputScale(LocalPlayer player) {
        if (state != State.PLAYING || lining || frozen || waiting != null || applied < 0 || playing == null
                || !player.onGround() || !CheatConfigManager.get().movementMatchSpeed) {
            return 1.0f;
        }
        double recorded = recordedSpeed();
        double now = playerSpeed(player);
        if (recorded <= 0.0 || now <= recorded) {
            return 1.0f;
        }
        return (float) (recorded / now);
    }

    public static String speedNote(LocalPlayer player) {
        if (state != State.PLAYING || lining || playing == null || !CheatConfigManager.get().movementMatchSpeed) {
            return null;
        }
        double recorded = recordedSpeed();
        double now = playerSpeed(player);
        if (recorded <= 0.0 || now <= 0.0) {
            return null;
        }
        if (now > recorded + SPEED_SLACK) {
            return String.format(Locale.ROOT, "Speed %d, matched to the recorded %d", Math.round(now),
                    Math.round(recorded));
        }
        if (now < recorded - SPEED_SLACK) {
            return String.format(Locale.ROOT, "Speed %d, slower than the recorded %d", Math.round(now),
                    Math.round(recorded));
        }
        return null;
    }

    public static boolean tooSlow(LocalPlayer player) {
        double recorded = recordedSpeed();
        return CheatConfigManager.get().movementMatchSpeed && recorded > 0.0
                && playerSpeed(player) < recorded - SPEED_SLACK;
    }

    private static void startPlayback(Minecraft client, Recording target, String why, boolean walkedIn) {
        int[] order = target.schedule();
        if (order.length == 0) {
            ChatUtil.error(target.name + " has nothing left to play. Check its start and end in /qza recorder.");
            return;
        }
        if (state == State.PLAYING && frozen) {
            ((MinecraftAccessor) client).qzaSetRightClickDelay(heldDelay);
        }
        pending = null;
        armed = 0;
        playing = target;
        schedule = order;
        step = 0;
        applied = -1;
        played = 0;
        teleportBase = target.frames.get(order[0]).teleports;
        teleportsSeen = 0;
        playStarted = System.currentTimeMillis();
        frozen = false;
        waiting = null;
        aimFixes = 0;
        stopText = null;
        offX = 0.0;
        offY = 0.0;
        offZ = 0.0;
        walkStart = walkedIn;
        state = State.PLAYING;

        MoveFrame first = target.frames.get(order[0]);
        LocalPlayer player = client.player;
        float yaw = player == null ? first.yaw : player.getYRot();
        float pitch = player == null ? first.pitch : player.getXRot();
        yawShift = 360.0f * Math.round((yaw - first.yaw) / 360.0f);
        turn = HumanTurn.plan(yaw, pitch, first.yaw + yawShift, first.pitch);
        lining = true;
        liningSince = System.currentTimeMillis();
        lookYaw = yaw;
        lookPitch = pitch;
        MovementLog.line("start " + target.name + " (" + why + "): " + order.length + " ticks at "
                + String.format(Locale.ROOT, "%.2f", target.speed) + "x, from " + MovementLog.pos(player)
                + " looking " + String.format(Locale.ROOT, "%.1f/%.1f", yaw, pitch) + ", recorded start "
                + MovementLog.pos(first) + " looking " + String.format(Locale.ROOT, "%.1f/%.1f", first.yaw + yawShift,
                first.pitch) + (turn == null ? ", no turn needed" : String.format(Locale.ROOT, ", turning over %.0fms",
                turn.totalMs())) + ", server ticks "
                + (serverTick < 0L ? "not available" : "last seen " + quietMillis() + "ms ago"));
    }

    private static boolean lineUp(Minecraft client, LocalPlayer player) {
        MoveFrame first = playing.frames.get(schedule[0]);
        holdStill(client, first.held(MoveFrame.SNEAK));
        long now = System.currentTimeMillis();
        boolean turned = turn == null || turn.done(System.nanoTime());
        boolean stopped = player.onGround() && player.getDeltaMovement().horizontalDistance() < STOPPED;
        long limit = SETTLE_MS + (turn == null ? 0L : Math.round(turn.totalMs()));
        if (!turned || (!stopped && now - liningSince < limit)) {
            return false;
        }
        if (turn != null) {
            mouseTowards(client, player, turn.at(System.nanoTime()));
        }
        lining = false;
        turn = null;
        teleportsSeen = 0;
        playStarted = now;
        double dx = player.getX() - first.x;
        double dy = player.getY() - first.y;
        double dz = player.getZ() - first.z;
        double away = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
        if (away > SAME_START) {
            offX = dx;
            offY = dy;
            offZ = dz;
        }
        MovementLog.line(String.format(Locale.ROOT, "lined up after %dms, %.2f blocks from the recorded start%s, "
                        + "look %.1f/%.1f, speed %.0f (recorded %.0f)", now - liningSince, away,
                away > SAME_START ? " so the route is moved to start here" : "", player.getYRot(), player.getXRot(),
                playerSpeed(player), playing.speedAt(schedule[0])));
        if (tooSlow(player)) {
            ChatUtil.info(String.format(Locale.ROOT, "You have %d speed but %s was recorded at %d. QZA can only "
                            + "slow you down to match, so the route may come up short.", Math.round(playerSpeed(player)),
                    playing.name, Math.round(playing.speedAt(schedule[0]))));
        }
        return true;
    }

    private static void holdStill(Minecraft client, boolean sneak) {
        KeyMapping[] held = heldKeys(client.options);
        for (int i = 0; i < held.length; i++) {
            ((KeyMappingAccessor) held[i]).qzaSetDown((1 << i) == MoveFrame.SNEAK && sneak);
        }
        for (KeyMapping key : clickKeys(client.options)) {
            ((KeyMappingAccessor) key).qzaSetClickCount(0);
        }
    }

    private static void playTick(Minecraft client, LocalPlayer player) {
        if (!player.isAlive()) {
            stopPlayback("Stopped " + playing.name + " because you died.");
            return;
        }
        if (Mc.screen() != null) {
            stopPlayback("Stopped " + playing.name + " because a menu opened.");
            return;
        }
        if (lining && !lineUp(client, player)) {
            return;
        }
        if (step >= schedule.length) {
            finishPlayback();
            return;
        }
        MoveFrame next = playing.frames.get(schedule[step]);
        String reason = teleportsSeen < next.teleports - teleportBase ? WAIT_TELEPORT
                : lagging() ? WAIT_LAG : null;
        if (reason != null) {
            pause(client, reason);
            return;
        }
        resume(client);

        double drift = drift(player, next);
        if (drift > MAX_DRIFT) {
            stopPlayback("Stopped " + playing.name + " because you got pushed "
                    + String.format(Locale.ROOT, "%.1f", drift) + " blocks off the recording.");
            return;
        }
        boolean fixed = apply(client, player, next, drift);
        if (MovementLog.on()) {
            MovementLog.line(String.format(Locale.ROOT, "tick %d frame %d at %s want %s drift %.3f look %.1f/%.1f%s "
                            + "keys %d%s tp %d/%d server %dms speed %.0f/%.0f", played, schedule[step],
                    MovementLog.pos(player), MovementLog.pos(next), drift, lookYaw, lookPitch,
                    fixed ? " (aim fixed)" : "", next.keys, next.clicked() ? " click" : "", teleportsSeen,
                    next.teleports - teleportBase, serverTick < 0L ? -1L : quietMillis(), playerSpeed(player),
                    playing.speedAt(schedule[step])));
        }
        applied = schedule[step];
        step++;
        played++;
        if (lagTestSeconds > 0 && played == LAG_TEST_TICK) {
            freezeServer(client, lagTestSeconds);
            lagTestSeconds = 0;
        }
    }

    private static double drift(LocalPlayer player, MoveFrame frame) {
        if (teleportsSeen != frame.teleports - teleportBase) {
            return 0.0;
        }
        double dx = player.getX() - (frame.x + offX);
        double dy = player.getY() - (frame.y + offY);
        double dz = player.getZ() - (frame.z + offZ);
        return Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
    }

    private static void pause(Minecraft client, String reason) {
        long now = System.currentTimeMillis();
        if (!reason.equals(waiting)) {
            waiting = reason;
            waitingSince = now;
            MovementLog.line("pause, " + reason + " at tick " + played + " (server quiet " + quietMillis() + "ms)");
        }
        MinecraftAccessor game = (MinecraftAccessor) client;
        if (WAIT_LAG.equals(reason)) {
            if (!frozen) {
                frozen = true;
                heldDelay = game.qzaRightClickDelay();
            } else {
                game.qzaSetRightClickDelay(heldDelay);
            }
        } else {
            if (frozen) {
                frozen = false;
                game.qzaSetRightClickDelay(heldDelay);
            }
            standStill(client);
        }
        for (KeyMapping key : clickKeys(client.options)) {
            ((KeyMappingAccessor) key).qzaSetClickCount(0);
        }

        long waited = now - waitingSince;
        if (WAIT_TELEPORT.equals(reason) && waited > TELEPORT_WAIT_MS) {
            stopPlayback("Stopped " + playing.name + " because the teleport at "
                    + String.format(Locale.ROOT, "%.2f", playedSeconds()) + "s never happened.");
        } else if (WAIT_LAG.equals(reason) && waited > LAG_WAIT_MS) {
            stopPlayback("Stopped " + playing.name + " because the server stopped responding.");
        }
    }

    private static void standStill(Minecraft client) {
        holdStill(client, applied >= 0 && playing.frames.get(applied).held(MoveFrame.SNEAK));
    }

    private static void resume(Minecraft client) {
        if (waiting != null) {
            MovementLog.line("resume after " + (System.currentTimeMillis() - waitingSince) + "ms " + waiting);
        }
        if (frozen) {
            frozen = false;
            ((MinecraftAccessor) client).qzaSetRightClickDelay(heldDelay);
        }
        waiting = null;
    }

    private static boolean apply(Minecraft client, LocalPlayer player, MoveFrame frame, double drift) {
        KeyMapping[] held = heldKeys(client.options);
        for (int i = 0; i < held.length; i++) {
            ((KeyMappingAccessor) held[i]).qzaSetDown(frame.held(1 << i));
        }
        KeyMapping[] clicks = clickKeys(client.options);
        for (int i = 0; i < clicks.length; i++) {
            ((KeyMappingAccessor) clicks[i]).qzaSetClickCount(frame.clicks(i));
        }
        Inventory inventory = player.getInventory();
        if (frame.slot >= 0 && frame.slot < Inventory.getSelectionSize()
                && inventory.getSelectedSlot() != frame.slot) {
            inventory.setSelectedSlot(frame.slot);
        }

        float yaw = frame.yaw + yawShift;
        float pitch = frame.pitch;
        boolean fixed = false;
        if (drift > AIM_DRIFT) {
            float[] aim = frame.aim;
            if (aim == null && step + 1 < schedule.length) {
                aim = playing.frames.get(schedule[step + 1]).aim;
            }
            float[] fix = aim == null ? null : aimAt(player, aim, yaw, pitch);
            if (fix != null) {
                yaw = fix[0];
                pitch = fix[1];
                fixed = true;
                aimFixes++;
            }
        }
        look(player, yaw, pitch);
        lookYaw = yaw;
        lookPitch = pitch;
        return fixed;
    }

    private static float[] aimAt(LocalPlayer player, float[] aim, float baseYaw, float basePitch) {
        Vec3 eye = player.getEyePosition();
        double dx = aim[0] + offX - eye.x;
        double dy = aim[1] + offY - eye.y;
        double dz = aim[2] + offZ - eye.z;
        double flat = Math.sqrt((dx * dx) + (dz * dz));
        if (flat < 1.0E-4 && Math.abs(dy) < 1.0E-4) {
            return null;
        }
        float yaw = (float) Math.toDegrees(Mth.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Mth.atan2(dy, flat));
        yaw = baseYaw + Mth.wrapDegrees(yaw - baseYaw);
        if (Math.abs(yaw - baseYaw) > MAX_AIM_FIX || Math.abs(pitch - basePitch) > MAX_AIM_FIX) {
            return null;
        }
        return new float[]{yaw, Mth.clamp(pitch, -90.0f, 90.0f)};
    }

    private static void look(LocalPlayer player, float yaw, float pitch) {
        float dy = yaw - player.getYRot();
        float dx = pitch - player.getXRot();
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO += dy;
        player.xRotO += dx;
    }

    public static void onFrame() {
        if (state != State.PLAYING) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || Mc.screen() != null) {
            return;
        }
        if (lining) {
            if (turn != null) {
                mouseTowards(client, player, turn.at(System.nanoTime()));
            }
            return;
        }
        if (waiting != null || applied < 0 || step >= schedule.length) {
            return;
        }
        MoveFrame to = playing.frames.get(schedule[step]);
        float t = Mth.clamp(client.getDeltaTracker().getGameTimeDeltaPartialTick(true), 0.0f, 1.0f);
        look(player, Mth.lerp(t, lookYaw, to.yaw + yawShift), Mth.lerp(t, lookPitch, to.pitch));
    }

    private static void mouseTowards(Minecraft client, LocalPlayer player, double[] want) {
        double feel = (client.options.sensitivity().get() * 0.6) + 0.2;
        double sens = feel * feel * feel * 8.0;
        double perPixel = sens * 0.15;
        long dx = Math.round((want[0] - player.getYRot()) / perPixel);
        long dy = Math.round((Mth.clamp(want[1], -90.0, 90.0) - player.getXRot()) / perPixel);
        if (dx != 0L || dy != 0L) {
            player.turn(dx * sens, dy * sens);
        }
    }

    private static void finishPlayback() {
        String name = playing.name;
        int fixes = aimFixes;
        stopPlayback(null);
        show("Finished " + name, GREEN, STOP_SHOWN_MS);
        MovementLog.line("finished " + name + " (" + fixes + " aim fixes)");
    }

    public static void stopPlayback(String message) {
        if (state != State.PLAYING) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (frozen) {
            ((MinecraftAccessor) client).qzaSetRightClickDelay(heldDelay);
        }
        if (message != null) {
            MovementLog.line("stop at tick " + played + ": " + message);
        }
        state = State.IDLE;
        playing = null;
        schedule = new int[0];
        frozen = false;
        waiting = null;
        lining = false;
        turn = null;
        releaseKeys(client);
        MovementLog.flush();
        if (message != null) {
            ChatUtil.info(message);
            show(message, RED, STOP_SHOWN_MS);
        }
    }

    private static void releaseKeys(Minecraft client) {
        if (client.options == null) {
            return;
        }
        for (KeyMapping key : heldKeys(client.options)) {
            ((KeyMappingAccessor) key).qzaSetDown(false);
        }
        for (KeyMapping key : clickKeys(client.options)) {
            ((KeyMappingAccessor) key).qzaSetClickCount(0);
        }
        if (Mc.screen() == null && client.getWindow() != null) {
            KeyMapping.setAll();
        }
    }

    public static boolean armLagTest(int seconds) {
        if (Minecraft.getInstance().getSingleplayerServer() == null) {
            return false;
        }
        lagTestSeconds = seconds;
        return true;
    }

    private static void freezeServer(Minecraft client, int seconds) {
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null) {
            return;
        }
        ChatUtil.info("Freezing the singleplayer server for " + seconds + "s.");
        server.execute(() -> {
            try {
                Thread.sleep(seconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public static void beforeTeleport() {
        Minecraft client = Minecraft.getInstance();
        if (client.isSameThread() && client.player != null) {
            preTeleport = client.player.position();
        }
    }

    public static void onTeleport() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (!client.isSameThread() || player == null) {
            return;
        }
        Vec3 before = preTeleport;
        preTeleport = null;
        Vec3 now = player.position();
        if (before != null && before.distanceToSqr(now) < MOVED_SQ) {
            return;
        }
        if (MovementLog.on()) {
            MovementLog.line("teleport from " + MovementLog.pos(before) + " to " + MovementLog.pos(now) + " while "
                    + state + ", block below " + MovementLog.pos(standingOn(player)) + ", area '"
                    + SkyBlockArea.current() + "'");
        }

        if (state == State.RECORDING) {
            recordTeleports++;
            return;
        }
        if (!enabled() || (state != State.IDLE && state != State.PLAYING)) {
            return;
        }
        if (state == State.PLAYING) {
            teleportsSeen++;
            if (teleportsSeen > allowedTeleports() && placedNear(player) == null) {
                stopPlayback("Stopped " + playing.name + " because the server moved you somewhere "
                        + "the recording didn't expect.");
            }
        }
        armed = ARM_TICKS;
        armedAge = 0;
        ringSettle = LAND_TICKS + 1;
        lastTickPos = null;
    }

    private static void checkLanding(Minecraft client, LocalPlayer player) {
        armed--;
        armedAge++;
        if (armedAge < LAND_TICKS || !player.onGround()) {
            if (armed <= 0) {
                MovementLog.line("teleport never landed on a block, at " + MovementLog.pos(player));
            }
            return;
        }
        armed = 0;
        BlockPos under = standingOn(player);
        String area = SkyBlockArea.current();
        Recording target = Recordings.at(under, area);
        MovementLog.line("landed on " + MovementLog.pos(under) + " -> "
                + (target == null ? "no recording there" : target.name + (target.walkIn() ? " (needs its ring)" : "")));
        if (target == null || target.walkIn() || (state == State.PLAYING && target == playing)) {
            return;
        }
        if (state != State.IDLE && state != State.PLAYING) {
            return;
        }
        if (Mc.screen() != null) {
            if (state == State.PLAYING) {
                stopPlayback(null);
            }
            pending = target;
            pendingWhy = "teleported onto its block, waited for a menu to close";
            return;
        }
        startPlayback(client, target, "teleported onto its block", false);
    }

    private static void checkRings(Minecraft client, LocalPlayer player) {
        Vec3 now = player.position();
        Vec3 before = lastTickPos == null ? now : lastTickPos;
        lastTickPos = now;
        if (ringSettle > 0) {
            ringSettle--;
            insideRings.clear();
            return;
        }
        boolean primed = ringsPrimed;
        ringsPrimed = true;
        if (!enabled()) {
            insideRings.clear();
            return;
        }
        Level level = client.level;
        if (level == null) {
            return;
        }
        double speed = Math.hypot(now.x - before.x, now.z - before.z) * 20.0;
        String area = SkyBlockArea.current();
        for (Recording candidate : Recordings.all()) {
            BlockPos pos = candidate.blockPos();
            double cx = pos == null ? 0.0 : pos.getX() + 0.5;
            double cz = pos == null ? 0.0 : pos.getZ() + 0.5;
            if (pos == null || !Recordings.sameArea(candidate, area)
                    || Math.abs(now.x - cx) > RING_REACH || Math.abs(now.z - cz) > RING_REACH) {
                insideRings.remove(candidate);
                continue;
            }
            boolean onTop = player.onGround() && Math.abs(now.y - ringY(level, pos)) < RING_HEIGHT;
            boolean inside = onTop && Math.hypot(now.x - cx, now.z - cz) <= RING_RADIUS;
            boolean through = onTop && passes(before, now, cx, cz);
            boolean was = insideRings.contains(candidate);
            if (inside) {
                insideRings.add(candidate);
            } else {
                insideRings.remove(candidate);
            }
            if (!primed || was || !(inside || through) || !candidate.walkIn()) {
                continue;
            }
            if (state != State.IDLE && state != State.PLAYING) {
                continue;
            }
            if ((state == State.PLAYING && candidate == playing) || Mc.screen() != null) {
                continue;
            }
            double limit = CheatConfigManager.get().movementWalkSpeed;
            if (speed > limit) {
                MovementLog.line(String.format(Locale.ROOT, "walked into %s's ring at %.1f blocks/s, over the %.0f limit",
                        candidate.name, speed, limit));
                long time = System.currentTimeMillis();
                if (time - noticeShownFor > NOTICE_MS) {
                    noticeShownFor = time;
                    show(String.format(Locale.ROOT, "Too fast to start %s (%.1f blocks/s)", candidate.name, speed),
                            YELLOW, NOTICE_MS);
                }
                continue;
            }
            MovementLog.line(String.format(Locale.ROOT, "walked into %s's ring at %.1f blocks/s", candidate.name, speed));
            startPlayback(client, candidate, "walked into its ring", true);
            return;
        }
    }

    private static boolean passes(Vec3 from, Vec3 to, double cx, double cz) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double length = (dx * dx) + (dz * dz);
        double t = length < 1.0E-9 ? 0.0 : Mth.clamp((((cx - from.x) * dx) + ((cz - from.z) * dz)) / length, 0.0, 1.0);
        return Math.hypot(from.x + (dx * t) - cx, from.z + (dz * t) - cz) <= RING_RADIUS;
    }

    public static double ringY(Level level, BlockPos pos) {
        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
        return pos.getY() + (shape.isEmpty() ? 1.0 : shape.max(Direction.Axis.Y));
    }

    public static void placed(Recording recording) {
        LocalPlayer player = Minecraft.getInstance().player;
        BlockPos pos = recording.blockPos();
        if (player != null && pos != null
                && Math.hypot(player.getX() - (pos.getX() + 0.5), player.getZ() - (pos.getZ() + 0.5)) <= RING_RADIUS) {
            insideRings.add(recording);
        }
    }

    private static Recording placedNear(LocalPlayer player) {
        String area = SkyBlockArea.current();
        Recording found = Recordings.at(standingOn(player), area);
        if (found == null) {
            found = Recordings.at(BlockPos.containing(player.getX(), player.getY() - NEAR_BELOW, player.getZ()), area);
        }
        return found;
    }

    private static int allowedTeleports() {
        int from = applied < 0 ? schedule[0] : applied;
        int ahead = (int) Math.ceil(TELEPORT_LOOKAHEAD * Math.max(1.0, playing.speed));
        int last = Math.min(playing.end() - 1, from + ahead);
        return playing.frames.get(last).teleports - teleportBase;
    }

    public static BlockPos standingOn(LocalPlayer player) {
        return BlockPos.containing(player.getX(), player.getY() - 0.01, player.getZ());
    }

    public static BlockPos target(LocalPlayer player) {
        HitResult hit = player.pick(PLACE_RANGE, 1.0f, false);
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            return block.getBlockPos();
        }
        return standingOn(player);
    }

    public static void onMotion(int entityId, Vec3 motion) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.getId() != entityId || state != State.PLAYING || !MovementLog.on()) {
            return;
        }
        MovementLog.line(String.format(Locale.ROOT, "server pushed you (%.3f, %.3f, %.3f) at tick %d",
                motion.x, motion.y, motion.z, played));
    }

    public static void forget(Recording target) {
        if (pending == target) {
            pending = null;
        }
        if (state == State.PLAYING && playing == target) {
            stopPlayback(null);
        }
    }

    public static void onLevelChange() {
        pending = null;
        capturing = false;
        lagTestSeconds = 0;
        armed = 0;
        ringsPrimed = false;
        lastTickPos = null;
        insideRings.clear();
        switch (state) {
            case COUNTDOWN -> state = State.IDLE;
            case RECORDING -> autoSave("you changed worlds");
            case PLAYING -> stopPlayback(null);
            default -> {
            }
        }
        MovementLog.flush();
    }

    public static void reset() {
        onLevelChange();
        state = State.IDLE;
    }
}
