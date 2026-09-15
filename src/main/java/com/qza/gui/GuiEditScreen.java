package com.qza.gui;

import com.qza.chat.ChatNotification;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.notify.NotificationBox;
import com.qza.party.PartyNotification;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

public class GuiEditScreen extends Screen {
    private static final double SCALE_STEP = 0.05;

    private static final int DIM = 0x66000000;
    private static final int PINK = 0xFFFF55FF;
    private static final int HANDLE = 0xFFFFFFFF;

    private final boolean returnToSettings;
    private final List<Target> targets = new ArrayList<>();

    private Target dragging;
    private float grabFracX = 0.5f;
    private float grabFracY = 0.5f;

    public GuiEditScreen() {
        this(true);
    }

    public GuiEditScreen(boolean returnToSettings) {
        super(Component.literal("QZA"));
        this.returnToSettings = returnToSettings;
    }

    public static void resetAll() {
        PartyNotification.resetPlacement();
        ChatNotification.resetPlacement();
        ConfigManager.save();
    }

    @Override
    protected void init() {
        QZAConfig cfg = ConfigManager.get();
        targets.clear();

        targets.add(new Target(
                PartyNotification.textFor("Steve"),
                NotificationBox.PARTY_TEXT,
                () -> cfg.partyInviteNotifyEnabled,
                () -> cfg.partyNotifyX, () -> cfg.partyNotifyY, () -> cfg.partyNotifyScale,
                (x, y) -> {
                    cfg.partyNotifyX = x;
                    cfg.partyNotifyY = y;
                },
                s -> cfg.partyNotifyScale = s));

        targets.add(new Target(
                "Steve: hey are you on?",
                NotificationBox.CHAT_TEXT,
                () -> cfg.chatNotifyEnabled,
                () -> cfg.chatNotifyX, () -> cfg.chatNotifyY, () -> cfg.chatNotifyScale,
                (x, y) -> {
                    cfg.chatNotifyX = x;
                    cfg.chatNotifyY = y;
                },
                s -> cfg.chatNotifyScale = s));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, DIM);
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        Target hovered = targetAt(mouseX, mouseY);
        for (Target target : targets) {
            if (!target.enabled.getAsBoolean()) {
                continue;
            }
            int[] box = rect(target);
            NotificationBox.draw(graphics, this.font, text(target),
                    box[0], box[1], scale(target), 1.0, 1f, target.colour);

            if (target == dragging || (dragging == null && target == hovered)) {
                handles(graphics, box);
            }
        }

        graphics.centeredText(this.font, "QZA GUI Edit Mode", this.width / 2, 14, PINK);
    }

    private void handles(GuiGraphicsExtractor graphics, int[] box) {
        int x = box[0];
        int y = box[1];
        int w = box[2];
        int h = box[3];
        int arm = Math.max(4, Math.min(10, w / 8));

        graphics.fill(x - 3, y - 3, x + arm, y - 2, HANDLE);
        graphics.fill(x - 3, y - 3, x - 2, y + arm, HANDLE);

        graphics.fill(x + w - arm, y - 3, x + w + 3, y - 2, HANDLE);
        graphics.fill(x + w + 2, y - 3, x + w + 3, y + arm, HANDLE);

        graphics.fill(x - 3, y + h + 2, x + arm, y + h + 3, HANDLE);
        graphics.fill(x - 3, y + h - arm, x - 2, y + h + 3, HANDLE);

        graphics.fill(x + w - arm, y + h + 2, x + w + 3, y + h + 3, HANDLE);
        graphics.fill(x + w + 2, y + h - arm, x + w + 3, y + h + 3, HANDLE);
    }

    private String text(Target target) {
        return NotificationBox.fit(this.font, target.preview);
    }

    private float scale(Target target) {
        return NotificationBox.clampScale(target.getScale.getAsDouble());
    }

    private int[] rect(Target target) {
        String text = text(target);
        float scale = scale(target);
        int w = Math.round(NotificationBox.width(this.font, text) * scale);
        int h = Math.round(NotificationBox.height(this.font) * scale);
        int[] pos = NotificationBox.topLeft(target.getX.getAsDouble(), target.getY.getAsDouble(),
                this.width, this.height, w, h);
        return new int[]{pos[0], pos[1], w, h};
    }

    private Target targetAt(double x, double y) {
        for (int i = targets.size() - 1; i >= 0; i--) {
            Target target = targets.get(i);
            if (!target.enabled.getAsBoolean()) {
                continue;
            }
            if (inside(x, y, rect(target))) {
                return target;
            }
        }
        return null;
    }

    private static boolean inside(double x, double y, int[] box) {
        return x >= box[0] && x <= box[0] + box[2]
                && y >= box[1] && y <= box[1] + box[3];
    }

    private void moveTo(Target target, double mouseX, double mouseY) {
        int[] box = rect(target);
        double left = mouseX - (grabFracX * box[2]);
        double top = mouseY - (grabFracY * box[3]);
        target.setPosition.accept(
                NotificationBox.fraction(left + (box[2] / 2.0), this.width),
                NotificationBox.fraction(top + (box[3] / 2.0), this.height));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            Target target = targetAt(event.x(), event.y());
            if (target != null) {
                int[] box = rect(target);
                dragging = target;
                grabFracX = box[2] <= 0 ? 0.5f : (float) ((event.x() - box[0]) / box[2]);
                grabFracY = box[3] <= 0 ? 0.5f : (float) ((event.y() - box[1]) / box[3]);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (dragging != null) {
            moveTo(dragging, event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            ConfigManager.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        if (dragging != null && verticalAmount != 0) {
            double step = verticalAmount > 0 ? SCALE_STEP : -SCALE_STEP;
            dragging.setScale.accept(Math.max(NotificationBox.MIN_SCALE,
                    Math.min(NotificationBox.MAX_SCALE,
                            dragging.getScale.getAsDouble() + step)));
            moveTo(dragging, mouseX, mouseY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        dragging = null;
        ConfigManager.save();
        Minecraft.getInstance().setScreen(returnToSettings ? new QZAScreen() : null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private interface PositionWriter {
        void accept(double x, double y);
    }

    private interface ScaleWriter {
        void accept(double scale);
    }

    private static final class Target {
        final String preview;
        final int colour;
        final BooleanSupplier enabled;
        final DoubleSupplier getX;
        final DoubleSupplier getY;
        final DoubleSupplier getScale;
        final PositionWriter setPosition;
        final ScaleWriter setScale;

        Target(String preview, int colour, BooleanSupplier enabled,
               DoubleSupplier getX, DoubleSupplier getY, DoubleSupplier getScale,
               PositionWriter setPosition, ScaleWriter setScale) {
            this.preview = preview;
            this.colour = colour;
            this.enabled = enabled;
            this.getX = getX;
            this.getY = getY;
            this.getScale = getScale;
            this.setPosition = setPosition;
            this.setScale = setScale;
        }
    }
}
