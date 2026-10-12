package com.qza.cheat.movement;

import com.qza.compat.Mc;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

public final class MovementHud implements HudElement {
    private static final int PINK = 0xFFFF55FF;
    private static final int RED = 0xFFFF5555;
    private static final int YELLOW = 0xFFFFFF55;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int FAINT = 0xFFAAAAAA;
    private static final float BIG = 3.0f;

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (Mc.hudHidden() || client.font == null || client.player == null) {
            return;
        }
        Font font = client.font;
        int cx = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 4;

        switch (MovementCopier.state()) {
            case COUNTDOWN -> {
                int left = Math.max(1, (int) Math.ceil(MovementCopier.countdownMillis() / 1000.0));
                graphics.centeredText(font, "Recording in", cx, y, PINK);
                graphics.pose().pushMatrix();
                graphics.pose().translate(cx, y + 12);
                graphics.pose().scale(BIG, BIG);
                graphics.centeredText(font, String.valueOf(left), 0, 0, WHITE);
                graphics.pose().popMatrix();
            }
            case RECORDING -> {
                String text = "Recording " + time(MovementCopier.recordedSeconds());
                int width = font.width(text) + 10;
                int x = cx - (width / 2);
                if ((System.currentTimeMillis() / 500L) % 2L == 0L) {
                    graphics.fill(x, y + 1, x + 6, y + 7, RED);
                }
                graphics.text(font, text, x + 10, y, RED);
                String hint = MovementCopier.hasKey()
                        ? MovementCopier.keyLabel() + " or /qza movement copy stop to finish"
                        : "/qza movement copy stop to finish";
                graphics.centeredText(font, hint, cx, y + 12, FAINT);
            }
            case PLAYING -> {
                Recording playing = MovementCopier.current();
                if (playing == null) {
                    return;
                }
                if (MovementCopier.lining()) {
                    graphics.centeredText(font, "Starting " + playing.name, cx, y, PINK);
                    return;
                }
                graphics.centeredText(font, "Playing " + playing.name + "  "
                        + time(MovementCopier.playedSeconds()) + " / " + time(MovementCopier.playLength()),
                        cx, y, PINK);
                String waiting = MovementCopier.waiting();
                if (waiting != null) {
                    graphics.centeredText(font, "Paused, " + waiting, cx, y + 12, YELLOW);
                }
            }
            default -> {
                String stopped = MovementCopier.stopMessage();
                if (stopped != null) {
                    graphics.centeredText(font, stopped, cx, y, MovementCopier.stopColour());
                }
            }
        }
    }

    private static String time(double seconds) {
        return String.format(Locale.ROOT, "%.1fs", seconds);
    }
}
