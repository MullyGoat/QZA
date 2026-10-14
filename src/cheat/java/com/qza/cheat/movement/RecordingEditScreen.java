package com.qza.cheat.movement;

import com.mojang.blaze3d.platform.InputConstants;
import com.qza.compat.Mc;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public class RecordingEditScreen extends CopierScreen {
    private static final int PANEL_W = 420;
    private static final int PANEL_H = 300;
    private static final int SWATCH_W = 60;
    private static final int SPEED_W = 44;
    private static final int MAX_SPEED_TEXT = 4;
    private static final int BAR_H = 20;
    private static final int LABEL_W = 50;
    private static final int BUTTON_W = 90;
    private static final int BUTTON_H = 18;
    private static final int GRAB = 4;
    private static final double SPEED_STEP = 0.25;

    private static final int COLOUR_STILL = 0xFF4A4A4A;
    private static final int COLOUR_MOVING = 0xFFB040B0;
    private static final int COLOUR_CLICK = 0xFFFFFFFF;
    private static final int COLOUR_TELEPORT = 0xFF55FFFF;
    private static final int COLOUR_CUT = 0xC0000000;
    private static final int COLOUR_START = 0xFF55FF55;
    private static final int COLOUR_END = 0xFFFF5555;

    private static Recording editing;

    private final Recording recording;
    private final Screen parent;
    private EditBox nameBox;
    private EditBox speedBox;

    private int panelX;
    private int panelY;
    private int panelW;
    private int bodyX;
    private int barX;
    private int barY;
    private int barW;
    private int trackX;
    private int trackY;
    private int trackW;

    private boolean[] columnMoving = new boolean[0];
    private boolean[] columnClick = new boolean[0];
    private boolean[] columnTeleport = new boolean[0];

    private int dragging;
    private int lastHandle = 1;

    public RecordingEditScreen(Recording recording, Screen parent) {
        this.recording = recording;
        this.parent = parent;
    }

    public static Recording editing() {
        return editing;
    }

    @Override
    protected void init() {
        editing = recording;
        panelW = Math.min(PANEL_W, Math.round(this.width / scale()) - 20);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - PANEL_H) / 2;
        bodyX = panelX + 16;
        barX = bodyX;
        barY = panelY + 82;
        barW = panelW - 32;
        trackX = bodyX + LABEL_W;
        trackY = panelY + 134;
        trackW = barW - LABEL_W - 50;

        String typed = nameBox == null ? recording.name : nameBox.getValue();
        nameBox = new EditBox(this.font, bodyX + LABEL_W + 4, panelY + 42, barW - LABEL_W - 8, 12, Component.empty());
        nameBox.setBordered(false);
        nameBox.setMaxLength(Recordings.MAX_NAME);
        nameBox.setHint(Component.literal(recording.name).withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setValue(typed);
        nameBox.moveCursorToEnd(false);
        addRenderableWidget(nameBox);

        String speedTyped = speedBox == null
                ? (recording.recordedSpeed > 0 ? String.valueOf(recording.recordedSpeed) : "")
                : speedBox.getValue();
        int detected = recording.detectedSpeed();
        speedBox = new EditBox(this.font, bodyX + LABEL_W + 4, panelY + 234, SPEED_W - 8, 12, Component.empty());
        speedBox.setBordered(false);
        speedBox.setMaxLength(MAX_SPEED_TEXT);
        speedBox.setResponder(text -> {
            String digits = text.replaceAll("[^0-9]", "");
            if (!digits.equals(text)) {
                speedBox.setValue(digits);
            }
        });
        speedBox.setHint(Component.literal(detected > 0 ? String.valueOf(detected) : "?")
                .withStyle(ChatFormatting.DARK_GRAY));
        speedBox.setValue(speedTyped);
        addRenderableWidget(speedBox);

        buildColumns();
    }

    private void buildColumns() {
        int columns = Math.max(1, barW);
        columnMoving = new boolean[columns];
        columnClick = new boolean[columns];
        columnTeleport = new boolean[columns];
        int size = recording.size();
        if (size == 0) {
            return;
        }
        for (int c = 0; c < columns; c++) {
            int from = (int) ((long) c * size / columns);
            int to = Math.max(from + 1, (int) ((long) (c + 1) * size / columns));
            for (int i = from; i < Math.min(to, size); i++) {
                if (recording.moving(i)) {
                    columnMoving[c] = true;
                    break;
                }
            }
        }
        for (int i = 0; i < size; i++) {
            int column = Math.min(columns - 1, (int) ((long) i * columns / size));
            MoveFrame frame = recording.frames.get(i);
            if (frame.clicked()) {
                columnClick[column] = true;
            }
            if (i > 0 && frame.teleports > recording.frames.get(i - 1).teleports) {
                columnTeleport[column] = true;
            }
        }
    }

    private int frameX(int frame) {
        int size = Math.max(1, recording.size());
        return barX + (int) Math.round((double) frame * barW / size);
    }

    private int frameAt(double x) {
        int size = recording.size();
        double fraction = Math.max(0.0, Math.min(1.0, (x - barX) / barW));
        return (int) Math.round(fraction * size);
    }

    private int startFrame() {
        return recording.start();
    }

    private int endFrame() {
        return recording.end();
    }

    private void setStart(int frame) {
        recording.trimStart = Math.max(0, Math.min(frame, endFrame() - 1));
        recording.clampTrim();
    }

    private void setEnd(int frame) {
        int size = recording.size();
        int end = Math.max(startFrame() + 1, Math.min(frame, size));
        recording.trimEnd = size - end;
        recording.clampTrim();
    }

    private int[] startRect() {
        return new int[]{bodyX + LABEL_W, panelY + 186, barW - LABEL_W, FIELD_H};
    }

    private int[] colourRect() {
        return new int[]{bodyX + LABEL_W, panelY + 208, SWATCH_W, FIELD_H};
    }

    private int[] resetRect() {
        return new int[]{panelX + (panelW / 2) - BUTTON_W - 4, panelY + PANEL_H - BUTTON_H - 10, BUTTON_W, BUTTON_H};
    }

    private int[] doneRect() {
        return new int[]{panelX + (panelW / 2) + 4, panelY + PANEL_H - BUTTON_H - 10, BUTTON_W, BUTTON_H};
    }

    private String typedName() {
        return Recordings.tidy(nameBox == null ? "" : nameBox.getValue());
    }

    private boolean nameTaken() {
        String name = typedName();
        Recording other = Recordings.find(name);
        return !name.isEmpty() && other != null && other != recording;
    }

    @Override
    protected void draw(GuiGraphicsExtractor graphics, int mx, int my) {
        panel(graphics, panelX, panelY, panelW, PANEL_H, "QZA EDIT RECORDING");

        graphics.text(this.font, "Name", bodyX, panelY + 42, TEXT_DIM);
        box(graphics, bodyX + LABEL_W, panelY + 38, barW - LABEL_W, !nameTaken());
        if (nameTaken()) {
            graphics.text(this.font, "Another recording is already called " + typedName(),
                    bodyX + LABEL_W, panelY + 57, BAD);
        }

        graphics.text(this.font, "Start and end", bodyX, panelY + 70, TEXT_DIM);
        drawBar(graphics);

        int start = startFrame();
        int end = endFrame();
        graphics.text(this.font, "Start " + seconds(start * Recording.SECONDS_PER_FRAME), barX,
                barY + BAR_H + 5, COLOUR_START);
        String endLabel = "End " + seconds(end * Recording.SECONDS_PER_FRAME);
        graphics.text(this.font, endLabel, barX + barW - this.font.width(endLabel), barY + BAR_H + 5, COLOUR_END);
        graphics.centeredText(this.font, "drag the lines or scroll over the bar", barX + (barW / 2),
                barY + BAR_H + 5, TEXT_FAINT);

        graphics.text(this.font, "Speed", bodyX, trackY - 2, TEXT_DIM);
        drawSpeed(graphics, mx, my);

        String keeps = "Keeps " + seconds(recording.keptSeconds()) + " of " + seconds(recording.seconds())
                + " and plays in " + seconds(recording.playSeconds());
        graphics.text(this.font, trim(keeps, barW), bodyX, panelY + 152, TEXT);
        graphics.text(this.font, trim("Speed only shortens the parts where you stand still.", barW), bodyX,
                panelY + 164, TEXT_FAINT);
        graphics.text(this.font, trim("Walking, jumping and falling always play at normal speed.", barW), bodyX,
                panelY + 174, TEXT_FAINT);

        graphics.text(this.font, "Starts", bodyX, panelY + 190, TEXT_DIM);
        int[] startButton = startRect();
        button(graphics, startButton, trim(recording.triggerLabel(), startButton[2] - 8), mx, my);

        graphics.text(this.font, "Colour", bodyX, panelY + 212, TEXT_DIM);
        int[] swatch = colourRect();
        boolean overSwatch = inside(mx, my, swatch);
        int argb = recording.argb();
        graphics.fill(swatch[0], swatch[1], swatch[0] + swatch[2], swatch[1] + swatch[3], argb);
        outline(graphics, swatch[0], swatch[1], swatch[2], swatch[3], overSwatch ? 0xFFFFFFFF : 0x80FFFFFF);
        graphics.centeredText(this.font, recording.colourName(), swatch[0] + (swatch[2] / 2), swatch[1] + 4,
                light(argb) ? 0xFF101010 : 0xFFFFFFFF);
        graphics.text(this.font, trim("Click to change the highlight colour", barW - LABEL_W - SWATCH_W - 8),
                swatch[0] + SWATCH_W + 8, panelY + 212, TEXT_FAINT);

        graphics.text(this.font, "Recorded", bodyX, panelY + 234, TEXT_DIM);
        box(graphics, bodyX + LABEL_W, panelY + 230, SPEED_W, true);
        int detected = recording.detectedSpeed();
        String speedHint = detected > 0
                ? "speed. Leave it empty to use the " + detected + " QZA measured"
                : "speed. Type the speed you had when you recorded this";
        graphics.text(this.font, trim(speedHint, barW - LABEL_W - SPEED_W - 8), bodyX + LABEL_W + SPEED_W + 8,
                panelY + 234, TEXT_FAINT);

        String placed = recording.placed()
                ? "On block " + recording.blockText()
                : "Not placed yet. Look at a block and use /qza recorder place " + recording.name;
        graphics.text(this.font, trim(placed, barW), bodyX, panelY + 254, recording.placed() ? TEXT_DIM : TEXT_FAINT);

        button(graphics, resetRect(), "Reset", mx, my);
        button(graphics, doneRect(), "Done", mx, my);
    }

    private static boolean light(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (r * 299) + (g * 587) + (b * 114) >= 128_000;
    }

    private void drawBar(GuiGraphicsExtractor graphics) {
        graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + BAR_H + 1, 0x66000000);
        for (int c = 0; c < columnMoving.length; c++) {
            int x = barX + c;
            graphics.fill(x, barY, x + 1, barY + BAR_H, columnMoving[c] ? COLOUR_MOVING : COLOUR_STILL);
            if (columnClick[c]) {
                graphics.fill(x, barY, x + 1, barY + 4, COLOUR_CLICK);
            }
            if (columnTeleport[c]) {
                graphics.fill(x, barY + BAR_H - 4, x + 1, barY + BAR_H, COLOUR_TELEPORT);
            }
        }
        int startX = frameX(startFrame());
        int endX = frameX(endFrame());
        if (startX > barX) {
            graphics.fill(barX, barY, startX, barY + BAR_H, COLOUR_CUT);
        }
        if (endX < barX + barW) {
            graphics.fill(endX, barY, barX + barW, barY + BAR_H, COLOUR_CUT);
        }
        graphics.fill(startX - 1, barY - 3, startX + 1, barY + BAR_H + 3, COLOUR_START);
        graphics.fill(endX - 1, barY - 3, endX + 1, barY + BAR_H + 3, COLOUR_END);
        outline(graphics, barX - 1, barY - 1, barW + 2, BAR_H + 2, BOX_BORDER);
    }

    private void drawSpeed(GuiGraphicsExtractor graphics, int mx, int my) {
        graphics.fill(trackX, trackY + 2, trackX + trackW, trackY + 4, 0xFF555555);
        double fraction = (recording.speed - Recording.MIN_SPEED) / (Recording.MAX_SPEED - Recording.MIN_SPEED);
        int filled = trackX + (int) Math.round(fraction * trackW);
        graphics.fill(trackX, trackY + 2, filled, trackY + 4, PINK);
        boolean hovered = inside(mx, my, new int[]{trackX - 4, trackY - 4, trackW + 8, 14});
        graphics.fill(filled - 3, trackY - 3, filled + 3, trackY + 9, hovered || dragging == 3 ? TEXT : 0xFFDDDDDD);
        graphics.text(this.font, String.format(Locale.ROOT, "%.2fx", recording.speed), trackX + trackW + 10,
                trackY - 2, TEXT);
    }

    private void setSpeedAt(double x) {
        double fraction = Math.max(0.0, Math.min(1.0, (x - trackX) / trackW));
        double raw = Recording.MIN_SPEED + (fraction * (Recording.MAX_SPEED - Recording.MIN_SPEED));
        recording.speed = Math.max(Recording.MIN_SPEED, Math.min(Recording.MAX_SPEED,
                Math.round(raw / SPEED_STEP) * SPEED_STEP));
    }

    @Override
    protected boolean clicked(MouseButtonEvent local) {
        if (local.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        double x = local.x();
        double y = local.y();
        if (inside(x, y, resetRect())) {
            recording.trimStart = 0;
            recording.trimEnd = 0;
            recording.speed = Recording.MIN_SPEED;
            return true;
        }
        if (inside(x, y, doneRect())) {
            onClose();
            return true;
        }
        if (inside(x, y, startRect())) {
            setFocused(null);
            recording.nextTrigger();
            return true;
        }
        if (inside(x, y, colourRect())) {
            setFocused(null);
            recording.nextColour();
            return true;
        }
        if (y >= barY - 4 && y <= barY + BAR_H + 4 && x >= barX - GRAB && x <= barX + barW + GRAB) {
            setFocused(null);
            int startX = frameX(startFrame());
            int endX = frameX(endFrame());
            boolean nearStart = Math.abs(x - startX) <= GRAB;
            boolean nearEnd = Math.abs(x - endX) <= GRAB;
            if (nearStart && nearEnd) {
                dragging = x < startX ? 1 : 2;
            } else if (nearStart) {
                dragging = 1;
            } else if (nearEnd) {
                dragging = 2;
            } else {
                dragging = Math.abs(x - startX) < Math.abs(x - endX) ? 1 : 2;
            }
            lastHandle = dragging;
            moveHandle(x);
            return true;
        }
        if (inside(x, y, new int[]{trackX - 4, trackY - 4, trackW + 8, 14})) {
            setFocused(null);
            dragging = 3;
            setSpeedAt(x);
            return true;
        }
        return false;
    }

    private void moveHandle(double x) {
        int frame = frameAt(x);
        if (dragging == 1) {
            setStart(frame);
        } else if (dragging == 2) {
            setEnd(frame);
        }
    }

    @Override
    protected boolean dragged(MouseButtonEvent local) {
        if (dragging == 1 || dragging == 2) {
            moveHandle(local.x());
            return true;
        }
        if (dragging == 3) {
            setSpeedAt(local.x());
            return true;
        }
        return false;
    }

    @Override
    protected void released(MouseButtonEvent local) {
        dragging = 0;
    }

    private void nudge(int handle, int frames) {
        if (handle == 1) {
            setStart(startFrame() + frames);
        } else {
            setEnd(endFrame() + frames);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int mx = logicalX(mouseX);
        int my = logicalY(mouseY);
        if (my >= barY - 4 && my <= barY + BAR_H + 4 && mx >= barX - GRAB && mx <= barX + barW + GRAB) {
            int startX = frameX(startFrame());
            int endX = frameX(endFrame());
            int handle = Math.abs(mx - startX) <= Math.abs(mx - endX) ? 1 : 2;
            lastHandle = handle;
            nudge(handle, verticalAmount > 0 ? 1 : -1);
            return true;
        }
        if (inside(mx, my, new int[]{trackX - 4, trackY - 6, trackW + 60, 18})) {
            recording.speed = Math.max(Recording.MIN_SPEED, Math.min(Recording.MAX_SPEED,
                    recording.speed + (verticalAmount > 0 ? SPEED_STEP : -SPEED_STEP)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        boolean typing = (nameBox != null && nameBox.isFocused()) || (speedBox != null && speedBox.isFocused());
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            onClose();
            return true;
        }
        if (!typing && (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT)) {
            int amount = event.hasShiftDown() ? 20 : 1;
            nudge(lastHandle, key == InputConstants.KEY_LEFT ? -amount : amount);
            return true;
        }
        return super.keyPressed(event);
    }

    private void applySpeed() {
        String text = speedBox == null ? "" : speedBox.getValue().trim();
        if (text.isEmpty()) {
            recording.recordedSpeed = 0;
            return;
        }
        try {
            recording.recordedSpeed = Math.max(0, Math.min(9999, Integer.parseInt(text)));
        } catch (NumberFormatException e) {
            recording.recordedSpeed = 0;
        }
    }

    @Override
    public void onClose() {
        if (!nameTaken()) {
            Recordings.rename(recording, typedName());
        }
        applySpeed();
        Recordings.save(recording);
        editing = null;
        Mc.setScreen(parent);
    }

    @Override
    public void removed() {
        if (editing == recording) {
            editing = null;
            applySpeed();
            Recordings.save(recording);
        }
        super.removed();
    }
}
