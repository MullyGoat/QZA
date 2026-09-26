package com.qza.dungeon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.util.DungeonState;
import com.qza.util.IgnUtil;
import com.qza.waypoint.WaypointColour;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public final class StarredMobs {
    private static final List<String> STARRED_NPCS =
            List.of("Shadow Assassin", "Lost Adventurer", "Diamond Guy", "King Midas");

    private static final AABB[] BOSS_ROOMS = {
            new AABB(-14, 55, 49, -72, 146, -40),
            new AABB(-40, 99, -40, 24, 54, 59),
            new AABB(-40, 118, -40, 42, 64, 37),
            new AABB(-40, 112, -40, 50, 53, 47),
            new AABB(-40, 112, -8, 50, 53, 118),
            new AABB(-40, 51, -8, 22, 110, 134),
            new AABB(-8, 0, -8, 134, 254, 147)};

    private static final float LINE_WIDTH = 2.0f;

    private static final Set<Integer> starred = new HashSet<>();
    private static final Set<Integer> checked = new HashSet<>();

    private static Supplier<RenderType> lines = RenderTypes::lines;

    private StarredMobs() {
    }

    public static void init() {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context ->
                draw(context.poseStack(), context.bufferSource()));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (active()) {
                starred.remove(entity.getId());
                checked.remove(entity.getId());
            }
        });
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> reset());
    }

    public static void useLines(Supplier<RenderType> type) {
        lines = type;
    }

    public static void onEntityData(int id) {
        if (!active()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(id);
        if (entity instanceof ArmorStand) {
            Component name = entity.getCustomName();
            if (name == null) {
                return;
            }
            String text = formatted(name);
            if (text.endsWith("§c❤") && text.contains("✯")) {
                check(entity, text);
            }
        } else if (entity instanceof Player player) {
            ClientPacketListener connection = client.getConnection();
            if (connection == null) {
                return;
            }
            PlayerInfo info = connection.getPlayerInfo(player.getUUID());
            if (info == null || info.getProfile() == null) {
                return;
            }
            if (STARRED_NPCS.contains(info.getProfile().name())) {
                starred.add(player.getId());
            }
        }
    }

    private static void check(Entity stand, String name) {
        if (!checked.add(stand.getId())) {
            return;
        }
        String plain = IgnUtil.stripCodes(name).toUpperCase(Locale.ROOT);
        int offset = plain.contains("WITHERMANCER") ? 3 : 1;
        int id = stand.getId() - offset;
        Entity mob = stand.level().getEntity(id);
        if (!(mob instanceof ArmorStand) && !starred.contains(id) && mob != null) {
            starred.add(id);
            return;
        }

        Player self = Minecraft.getInstance().player;
        List<Entity> below = stand.level().getEntities(stand, stand.getBoundingBox().move(0.0, -1.0, 0.0),
                it -> !(it instanceof ArmorStand) && !(it instanceof ExperienceOrb));
        for (Entity it : below) {
            if (starred.contains(it.getId())) {
                continue;
            }
            boolean fits;
            if (it instanceof Player player) {
                fits = !player.isInvisible() && player.getUUID().version() == 2 && player != self;
            } else {
                fits = !(it instanceof WitherBoss) && !(it instanceof AbstractArrow);
            }
            if (fits) {
                starred.add(it.getId());
                return;
            }
        }
    }

    private static boolean active() {
        int floor = DungeonState.catacombsFloor();
        if (floor < 0) {
            return false;
        }
        Player player = Minecraft.getInstance().player;
        if (player == null || floor < 1 || floor > BOSS_ROOMS.length) {
            return true;
        }
        return !BOSS_ROOMS[floor - 1].contains(player.getX(), player.getY(), player.getZ());
    }

    private static void draw(PoseStack poseStack, MultiBufferSource.BufferSource buffers) {
        QZAConfig cfg = ConfigManager.get();
        Minecraft client = Minecraft.getInstance();
        if (!cfg.starredMobsEnabled || client.level == null || !active()) {
            return;
        }

        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = client.gameRenderer.getMainCamera().position();
        VertexConsumer consumer = buffers.getBuffer(lines.get());

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        for (Entity entity : client.level.entitiesForRendering()) {
            Integer colour = colour(cfg, entity);
            if (colour == null) {
                continue;
            }
            Vec3 at = entity.getPosition(partial);
            AABB box = entity.getBoundingBox().move(at.subtract(entity.position()));
            ShapeRenderer.renderShape(poseStack, consumer,
                    Shapes.create(box.move(-box.minX, -box.minY, -box.minZ)),
                    box.minX, box.minY, box.minZ, colour, LINE_WIDTH);
        }
        poseStack.popPose();
    }

    private static Integer colour(QZAConfig cfg, Entity entity) {
        if (starred.contains(entity.getId())) {
            return WaypointColour.argb(cfg.starredMobColour);
        }
        if (entity instanceof Bat bat) {
            return cfg.starredMobsBats && !bat.isInvisible() && !bat.isPassenger()
                    ? WaypointColour.argb(cfg.starredMobsBatColour) : null;
        }
        if (entity instanceof EnderMan && cfg.starredMobsFels) {
            Component name = entity.getCustomName();
            if (name != null && "Dinnerbone".equals(name.getString())) {
                return WaypointColour.argb(cfg.starredMobsFelColour);
            }
        }
        return null;
    }

    private static String formatted(Component component) {
        StringBuilder out = new StringBuilder();
        component.visit((style, text) -> {
            appendStyle(out, style);
            out.append(text);
            return Optional.empty();
        }, Style.EMPTY);
        return out.toString();
    }

    private static void appendStyle(StringBuilder out, Style style) {
        TextColor colour = style.getColor();
        if (colour != null) {
            for (ChatFormatting format : ChatFormatting.values()) {
                if (format.isColor() && format.getColor() != null
                        && format.getColor() == colour.getValue()) {
                    out.append('§').append(format.getChar());
                    break;
                }
            }
        }
        if (style.isBold()) {
            out.append(ChatFormatting.BOLD);
        }
        if (style.isItalic()) {
            out.append(ChatFormatting.ITALIC);
        }
        if (style.isUnderlined()) {
            out.append(ChatFormatting.UNDERLINE);
        }
        if (style.isStrikethrough()) {
            out.append(ChatFormatting.STRIKETHROUGH);
        }
        if (style.isObfuscated()) {
            out.append(ChatFormatting.OBFUSCATED);
        }
    }

    public static void reset() {
        starred.clear();
        checked.clear();
    }
}
