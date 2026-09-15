package com.qza.gui;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.party.PartyNotification;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class GuiEditScreen extends Screen {
    private static final String PREVIEW_IGN = "Steve";
    private static final double SCALE_STEP = 0.05;

    private static final int DIM = 0x66000000;
    private static final int PINK = 0xFFFF55FF;
    private static final int HANDLE = 0xFFFFFFFF;

    private final boolean returnToSettings;

    private boolean dragging;
    private float grabFracX = 0.5f;
    private float grabFracY = 0.5f;

    public GuiEditScreen() {
        this(true);
    }

    public GuiEditScreen(boolean returnToSettings) {
        super(Component.literal("QZA"));
        this.returnToSettings = returnToSettings;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, DIM);
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int[] box = rect();
        PartyNotification.draw(graphics, this.font, PartyNotification.textFor(PREVIEW_IGN),
                box[0], box[1], PartyNotification.scale(), 1.0, 1f);

        if (dragging || inside(mouseX, mouseY, box)) {
            handles(graphics, box);
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

    private int[] rect() {
        String text = PartyNotification.textFor(PREVIEW_IGN);
        float scale = PartyNotification.scale();
        int w = Math.round(PartyNotification.boxWidth(this.font, text) * scale);
        int h = Math.round(PartyNotification.boxHeight(this.font) * scale);
        int[] pos = PartyNotification.topLeft(this.width, this.height, w, h);
        return new int[]{pos[0], pos[1], w, h};
    }

    private static boolean inside(double x, double y, int[] box) {
        return x >= box[0] && x <= box[0] + box[2]
                && y >= box[1] && y <= box[1] + box[3];
    }

    private void moveTo(double mouseX, double mouseY) {
        int[] box = rect();
        double left = mouseX - (grabFracX * box[2]);
        double top = mouseY - (grabFracY * box[3]);
        PartyNotification.setCentre(left + (box[2] / 2.0), top + (box[3] / 2.0),
                this.width, this.height);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int[] box = rect();
            if (inside(event.x(), event.y(), box)) {
                dragging = true;
                grabFracX = box[2] <= 0 ? 0.5f : (float) ((event.x() - box[0]) / box[2]);
                grabFracY = box[3] <= 0 ? 0.5f : (float) ((event.y() - box[1]) / box[3]);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (dragging) {
            moveTo(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            ConfigManager.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        if (dragging && verticalAmount != 0) {
            QZAConfig cfg = ConfigManager.get();
            double step = verticalAmount > 0 ? SCALE_STEP : -SCALE_STEP;
            cfg.partyNotifyScale = Math.max(PartyNotification.MIN_SCALE,
                    Math.min(PartyNotification.MAX_SCALE, cfg.partyNotifyScale + step));
            moveTo(mouseX, mouseY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        dragging = false;
        ConfigManager.save();
        Minecraft.getInstance().setScreen(returnToSettings ? new QZAScreen() : null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
