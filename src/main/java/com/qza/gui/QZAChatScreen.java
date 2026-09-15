package com.qza.gui;

import com.qza.chat.ChatConversation;
import com.qza.chat.ChatHistory;
import com.qza.chat.ChatMessage;
import com.qza.config.ConfigManager;
import com.qza.util.IgnUtil;
import com.qza.util.PlayerFaces;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class QZAChatScreen extends Screen {
    private static final int PANEL_BG = 0x55000000;
    private static final int RAIL_BG = 0x33000000;
    private static final int BOX_BORDER = 0x40FFFFFF;
    private static final int DIVIDER = 0xFFC8D4DC;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int TEXT_FAINT = 0xFF8A8A8A;
    private static final int PINK = 0xFFFF55FF;

    private static final int IN_BG = 0x8C23282E;
    private static final int IN_BORDER = 0x40FFFFFF;
    private static final int OUT_BG = 0x8C4A2A52;
    private static final int OUT_BORDER = 0x80FF55FF;

    private static final int HEADER_H = 44;
    private static final int RAIL_W = 190;
    private static final int CONTACT_H = 28;
    private static final int FACE = 16;
    private static final int INPUT_H = 18;
    private static final int SEND_W = 46;
    private static final int BUBBLE_PAD = 5;
    private static final int BUBBLE_GAP = 4;
    private static final int LINE_H = 10;

    private static final float TITLE_SCALE = 1.5f;

    private static String selected;

    private static final int MENU_W = 58;
    private static final int MENU_ROW_H = 12;

    private EditBox input;
    private EditBox ignInput;
    private boolean adding;

    private String menuFor;
    private int menuX;
    private int menuY;

    private final List<Bubble> bubbles = new ArrayList<>();
    private String builtFor;
    private int builtCount = -1;
    private int builtWidth = -1;

    private double railScroll;
    private double threadScroll;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int railX;
    private int railY;
    private int railH;
    private int threadX;
    private int threadY;
    private int threadW;
    private int threadH;
    private int inputY;

    public QZAChatScreen() {
        super(Component.literal("QZA Chat"));
    }

    private static float scale() {
        double pct = ConfigManager.get().guiScale;
        return (float) Math.max(0.5, Math.min(1.5, pct / 100.0));
    }

    private float offsetX() {
        return this.width * (1f - scale()) / 2f;
    }

    private float offsetY() {
        return this.height * (1f - scale()) / 2f;
    }

    @Override
    protected void init() {
        layout();

        if (selected == null || ChatHistory.get(selected) == null) {
            List<ChatConversation> all = ChatHistory.conversations();
            selected = all.isEmpty() ? null : all.get(0).name;
        }
        if (selected != null) {
            ChatHistory.markRead(selected);
        }

        int inputW = threadW - SEND_W - 6;
        input = new EditBox(this.font, threadX + 5, inputY + 5, Math.max(40, inputW - 10), 12,
                Component.empty());
        input.setBordered(false);
        input.setMaxLength(240);
        input.setHint(Component.literal("Message").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(input);

        ignInput = new EditBox(this.font, railX + 5, panelY + 24, RAIL_W - 41, 12,
                Component.empty());
        ignInput.setBordered(false);
        ignInput.setMaxLength(16);
        ignInput.setHint(Component.literal("Enter IGN").withStyle(ChatFormatting.DARK_GRAY));
        ignInput.visible = false;
        addRenderableWidget(ignInput);

        setInitialFocus(input);

        builtFor = null;
        rebuildBubbles(true);
    }

    private void layout() {
        panelX = 8;
        panelY = 6;
        panelW = this.width - 16;
        panelH = this.height - 12;

        railX = panelX + 10;
        railY = panelY + HEADER_H;
        int railBottom = panelY + panelH - 10;
        railH = Math.max(40, railBottom - railY);

        threadX = panelX + RAIL_W + 28;
        int threadRight = panelX + panelW - 12;
        threadW = Math.max(120, threadRight - threadX);
        threadY = railY + 22;
        inputY = railBottom - INPUT_H;
        threadH = Math.max(40, inputY - 8 - threadY);
    }

    private ChatConversation current() {
        return selected == null ? null : ChatHistory.get(selected);
    }

    private void rebuildBubbles(boolean toBottom) {
        ChatConversation conversation = current();
        int count = conversation == null || conversation.messages == null
                ? 0 : conversation.messages.size();

        boolean same = builtFor != null && builtFor.equals(selected)
                && builtCount == count && builtWidth == threadW;
        if (same) {
            return;
        }

        boolean grew = builtFor != null && builtFor.equals(selected) && count > builtCount;
        builtFor = selected;
        builtCount = count;
        builtWidth = threadW;
        bubbles.clear();

        if (conversation != null) {
            int maxTextW = Math.max(40, (int) (threadW * 0.72) - (BUBBLE_PAD * 2));
            int y = 0;
            for (ChatMessage message : conversation.messages) {
                List<FormattedCharSequence> lines =
                        this.font.split(Component.literal(message.text), maxTextW);
                if (lines.isEmpty()) {
                    continue;
                }
                int widest = 0;
                for (FormattedCharSequence line : lines) {
                    widest = Math.max(widest, this.font.width(line));
                }
                Bubble bubble = new Bubble(message.outgoing, lines,
                        widest + (BUBBLE_PAD * 2),
                        (lines.size() * LINE_H) + (BUBBLE_PAD * 2) - 1);
                bubble.y = y;
                bubbles.add(bubble);
                y += bubble.h + BUBBLE_GAP;
            }
        }

        if (toBottom || grew) {
            threadScroll = maxThreadScroll();
        } else {
            clampThreadScroll();
        }
    }

    private int threadContentHeight() {
        if (bubbles.isEmpty()) {
            return 0;
        }
        Bubble last = bubbles.get(bubbles.size() - 1);
        return last.y + last.h;
    }

    private double maxThreadScroll() {
        return Math.max(0, threadContentHeight() - threadH);
    }

    private void clampThreadScroll() {
        threadScroll = Math.max(0, Math.min(threadScroll, maxThreadScroll()));
    }

    private int railContentHeight() {
        return ChatHistory.conversations().size() * CONTACT_H;
    }

    private void clampRailScroll() {
        railScroll = Math.max(0, Math.min(railScroll, Math.max(0, railContentHeight() - railH)));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        rebuildBubbles(false);

        float s = scale();
        int mx = Math.round((mouseX - offsetX()) / s);
        int my = Math.round((mouseY - offsetY()) / s);

        graphics.pose().pushMatrix();
        graphics.pose().translate(offsetX(), offsetY());
        graphics.pose().scale(s, s);

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        outline(graphics, panelX, panelY, panelW, panelH, 0x33FFFFFF);
        graphics.fill(panelX, panelY + HEADER_H, panelX + RAIL_W + 8, panelY + panelH, RAIL_BG);

        drawHeader(graphics);
        drawAdd(graphics, mx, my);
        drawContacts(graphics, mx, my);
        drawThread(graphics);
        drawInput(graphics, mx, my);

        super.extractRenderState(graphics, mx, my, delta);

        drawMenu(graphics, mx, my);

        graphics.pose().popMatrix();
    }

    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x, y, x + w, y + 1, colour);
        graphics.fill(x, y + h - 1, x + w, y + h, colour);
        graphics.fill(x, y + 1, x + 1, y + h - 1, colour);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    private void drawHeader(GuiGraphicsExtractor graphics) {
        int centreX = panelX + (panelW / 2);
        graphics.pose().pushMatrix();
        graphics.pose().translate(centreX, panelY + 10);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.centeredText(this.font, "QZA CHAT", 0, 0, PINK);
        graphics.pose().popMatrix();

        graphics.fill(panelX + 8, panelY + HEADER_H - 6,
                panelX + panelW - 8, panelY + HEADER_H - 5, DIVIDER);
    }

    private void drawAdd(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!adding) {
            int[] r = addRect();
            boolean hovered = inside(mouseX, mouseY, r);
            graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3],
                    hovered ? 0xAA3C5A70 : 0x99223140);
            outline(graphics, r[0], r[1], r[2], r[3], hovered ? 0xFFAFD4EC : 0xFF6A8CA8);
            graphics.centeredText(this.font, "+ Add IGN",
                    r[0] + (r[2] / 2), r[1] + 4, TEXT);
            return;
        }

        int frameW = RAIL_W - 31;
        boolean valid = validIgn(ignInput.getValue()) != null;
        boolean empty = ignInput.getValue().trim().isEmpty();
        graphics.fill(railX, panelY + 20, railX + frameW, panelY + 36, 0x66000000);
        outline(graphics, railX, panelY + 20, frameW, 16,
                empty || valid ? BOX_BORDER : 0xFFE05555);

        int[] go = goRect();
        boolean hovered = inside(mouseX, mouseY, go);
        graphics.fill(go[0], go[1], go[0] + go[2], go[1] + go[3],
                valid && hovered ? 0xAA5A2E62 : 0x99321A38);
        outline(graphics, go[0], go[1], go[2], go[3],
                valid ? (hovered ? PINK : 0xFFB05CB8) : 0xFF5A5A5A);
        graphics.centeredText(this.font, "Go", go[0] + (go[2] / 2), go[1] + 4,
                valid ? TEXT : TEXT_FAINT);
    }

    private void drawMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (menuFor == null) {
            return;
        }

        int h = (MENU_ROW_H * 2) + 2;
        graphics.fill(menuX, menuY, menuX + MENU_W, menuY + h, 0xF00E1218);
        outline(graphics, menuX, menuY, MENU_W, h, PINK);

        String[] labels = {"Hide", "Delete"};
        for (int i = 0; i < labels.length; i++) {
            int rowY = menuY + 1 + (i * MENU_ROW_H);
            boolean hovered = mouseX >= menuX && mouseX <= menuX + MENU_W
                    && mouseY >= rowY && mouseY < rowY + MENU_ROW_H;
            if (hovered) {
                graphics.fill(menuX + 1, rowY, menuX + MENU_W - 1, rowY + MENU_ROW_H, 0x663C5A70);
            }
            graphics.text(this.font, labels[i], menuX + 5, rowY + 2,
                    i == 1 ? (hovered ? 0xFFFF7B7B : 0xFFE08A8A) : (hovered ? TEXT : 0xFFCCCCCC));
        }
    }

    private int menuHeight() {
        return (MENU_ROW_H * 2) + 2;
    }

    private void openMenu(String ign, double x, double y) {
        menuFor = ign;
        menuX = (int) Math.round(Math.min(x, railX + RAIL_W));
        menuY = (int) Math.round(Math.min(y, panelY + panelH - menuHeight() - 4));
    }

    private void afterRemoval() {
        List<ChatConversation> all = ChatHistory.conversations();
        selected = all.isEmpty() ? null : all.get(0).name;
        if (selected != null) {
            ChatHistory.markRead(selected);
        }
        railScroll = 0;
        clampRailScroll();
        builtFor = null;
        rebuildBubbles(true);
    }

    private int[] addRect() {
        return new int[]{railX, panelY + 20, 74, 16};
    }

    private int[] goRect() {
        return new int[]{railX + RAIL_W - 27, panelY + 20, 27, 16};
    }

    private static String validIgn(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        String name = IgnUtil.trailingName(trimmed);
        return name != null && name.equals(trimmed) ? name : null;
    }

    private void setAdding(boolean value) {
        adding = value;
        ignInput.visible = value;
        if (value) {
            ignInput.setValue("");
            setInitialFocus(ignInput);
        } else {
            ignInput.setValue("");
            setInitialFocus(input);
        }
    }

    private void confirmAdd() {
        String ign = validIgn(ignInput.getValue());
        if (ign == null) {
            return;
        }
        ChatHistory.start(ign);
        selected = ign;
        ChatHistory.markRead(ign);
        railScroll = 0;
        builtFor = null;
        rebuildBubbles(true);
        setAdding(false);
    }

    private void drawContacts(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<ChatConversation> all = ChatHistory.conversations();

        if (all.isEmpty()) {
            graphics.text(this.font, "No messages yet", railX + 4, railY + 6, TEXT_DIM);
            graphics.text(this.font, "Whispers you send and", railX + 4, railY + 22, TEXT_FAINT);
            graphics.text(this.font, "receive show up here.", railX + 4, railY + 32, TEXT_FAINT);
            return;
        }

        graphics.enableScissor(railX - 2, railY, railX + RAIL_W, railY + railH);

        int top = railY - (int) Math.round(railScroll);
        for (int i = 0; i < all.size(); i++) {
            ChatConversation conversation = all.get(i);
            int y = top + (i * CONTACT_H);
            if (y + CONTACT_H < railY || y > railY + railH) {
                continue;
            }

            boolean active = conversation.name.equalsIgnoreCase(selected);
            boolean hovered = mouseX >= railX - 2 && mouseX <= railX + RAIL_W
                    && mouseY >= y && mouseY < y + CONTACT_H
                    && mouseY >= railY && mouseY <= railY + railH;

            if (active) {
                graphics.fill(railX - 2, y, railX + RAIL_W, y + CONTACT_H - 1, 0x66000000);
                graphics.fill(railX - 2, y, railX, y + CONTACT_H - 1, PINK);
            } else if (hovered) {
                graphics.fill(railX - 2, y, railX + RAIL_W, y + CONTACT_H - 1, 0x22FFFFFF);
            }

            PlayerFaceExtractor.extractRenderState(graphics,
                    PlayerFaces.skinFor(conversation.name), railX + 5, y + 5, FACE);

            int nameX = railX + 5 + FACE + 6;
            int room = (railX + RAIL_W) - nameX - 26;
            graphics.text(this.font, trim(conversation.name, room), nameX, y + 4,
                    active ? PINK : TEXT);
            graphics.text(this.font, trim(conversation.preview(), room + 20), nameX, y + 15,
                    TEXT_FAINT);

            String when = ago(conversation.lastActivity);
            if (!when.isEmpty()) {
                graphics.text(this.font, when,
                        railX + RAIL_W - this.font.width(when) - 4, y + 4, TEXT_FAINT);
            }
            if (conversation.unread > 0) {
                String badge = conversation.unread > 9 ? "9+" : String.valueOf(conversation.unread);
                int bw = this.font.width(badge) + 6;
                int bx = railX + RAIL_W - bw - 4;
                graphics.fill(bx, y + 15, bx + bw, y + 25, PINK);
                graphics.text(this.font, badge, bx + 3, y + 16, 0xFF201020);
            }
        }

        graphics.disableScissor();

        int total = railContentHeight();
        if (total > railH) {
            int trackX = railX + RAIL_W + 2;
            graphics.fill(trackX, railY, trackX + 3, railY + railH, 0x33FFFFFF);
            int barH = Math.max(16, (int) ((float) railH / total * railH));
            int barY = railY + (int) ((railScroll / (total - railH)) * (railH - barH));
            graphics.fill(trackX, barY, trackX + 3, barY + barH, 0x99FFFFFF);
        }
    }

    private void drawThread(GuiGraphicsExtractor graphics) {
        ChatConversation conversation = current();

        if (conversation == null) {
            graphics.centeredText(this.font, "Pick a conversation on the left",
                    threadX + (threadW / 2), threadY + (threadH / 2) - 4, TEXT_DIM);
            return;
        }

        PlayerFaceExtractor.extractRenderState(graphics,
                PlayerFaces.skinFor(conversation.name), threadX, railY, FACE);
        graphics.text(this.font, conversation.name, threadX + FACE + 6, railY + 4, TEXT);
        graphics.fill(threadX, railY + 18, threadX + threadW, railY + 19, 0x33FFFFFF);

        if (bubbles.isEmpty()) {
            graphics.centeredText(this.font, "No messages in this conversation",
                    threadX + (threadW / 2), threadY + (threadH / 2) - 4, TEXT_DIM);
            return;
        }

        graphics.enableScissor(threadX, threadY, threadX + threadW, threadY + threadH);

        int top = threadY - (int) Math.round(threadScroll);
        for (Bubble bubble : bubbles) {
            int y = top + bubble.y;
            if (y + bubble.h < threadY - 4 || y > threadY + threadH + 4) {
                continue;
            }
            int x = bubble.outgoing ? threadX + threadW - bubble.w : threadX;

            graphics.fill(x, y, x + bubble.w, y + bubble.h,
                    bubble.outgoing ? OUT_BG : IN_BG);
            outline(graphics, x, y, bubble.w, bubble.h,
                    bubble.outgoing ? OUT_BORDER : IN_BORDER);

            int lineY = y + BUBBLE_PAD;
            for (FormattedCharSequence line : bubble.lines) {
                graphics.text(this.font, line, x + BUBBLE_PAD, lineY, TEXT);
                lineY += LINE_H;
            }
        }

        graphics.disableScissor();

        int total = threadContentHeight();
        if (total > threadH) {
            int trackX = threadX + threadW + 2;
            graphics.fill(trackX, threadY, trackX + 3, threadY + threadH, 0x33FFFFFF);
            int barH = Math.max(16, (int) ((float) threadH / total * threadH));
            int barY = threadY + (int) ((threadScroll / (total - threadH)) * (threadH - barH));
            graphics.fill(trackX, barY, trackX + 3, barY + barH, 0x99FFFFFF);
        }
    }

    private void drawInput(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int boxW = threadW - SEND_W - 6;
        graphics.fill(threadX, inputY, threadX + boxW, inputY + INPUT_H, 0x66000000);
        outline(graphics, threadX, inputY, boxW, INPUT_H, BOX_BORDER);

        int[] send = sendRect();
        boolean ready = current() != null && !input.getValue().trim().isEmpty();
        boolean hovered = mouseX >= send[0] && mouseX <= send[0] + send[2]
                && mouseY >= send[1] && mouseY <= send[1] + send[3];

        graphics.fill(send[0], send[1], send[0] + send[2], send[1] + send[3],
                ready && hovered ? 0xAA5A2E62 : 0x99321A38);
        outline(graphics, send[0], send[1], send[2], send[3],
                ready ? (hovered ? PINK : 0xFFB05CB8) : 0xFF5A5A5A);
        graphics.centeredText(this.font, "Send",
                send[0] + (send[2] / 2), send[1] + ((send[3] - 8) / 2),
                ready ? TEXT : TEXT_FAINT);
    }

    private int[] sendRect() {
        return new int[]{threadX + threadW - SEND_W, inputY, SEND_W, INPUT_H};
    }

    private static boolean inside(double x, double y, int[] rect) {
        return x >= rect[0] && x <= rect[0] + rect[2]
                && y >= rect[1] && y <= rect[1] + rect[3];
    }

    private String trim(String label, int maxWidth) {
        if (label == null) {
            return "";
        }
        if (maxWidth <= 0 || this.font.width(label) <= maxWidth) {
            return label;
        }
        String shown = label;
        while (shown.length() > 1 && this.font.width(shown + "...") > maxWidth) {
            shown = shown.substring(0, shown.length() - 1);
        }
        return shown + "...";
    }

    private static String ago(long time) {
        if (time <= 0) {
            return "";
        }
        long seconds = (System.currentTimeMillis() - time) / 1000L;
        if (seconds < 60) {
            return "now";
        }
        if (seconds < 3600) {
            return (seconds / 60) + "m";
        }
        if (seconds < 86400) {
            return (seconds / 3600) + "h";
        }
        return (seconds / 86400) + "d";
    }

    private void sendCurrent() {
        ChatConversation conversation = current();
        if (conversation == null) {
            return;
        }
        String text = input.getValue().trim();
        if (text.isEmpty()) {
            return;
        }
        ChatHistory.send(conversation.name, text);
        input.setValue("");
    }

    private MouseButtonEvent toLogical(MouseButtonEvent event) {
        float s = scale();
        float ox = offsetX();
        float oy = offsetY();
        if (s == 1f && ox == 0f && oy == 0f) {
            return event;
        }
        return new MouseButtonEvent((event.x() - ox) / s, (event.y() - oy) / s, event.buttonInfo());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent local = toLogical(event);
        double mouseX = local.x();
        double mouseY = local.y();

        if (menuFor != null) {
            boolean inMenu = mouseX >= menuX && mouseX <= menuX + MENU_W
                    && mouseY >= menuY && mouseY <= menuY + menuHeight();
            String target = menuFor;
            menuFor = null;

            if (inMenu && local.button() == 0) {
                int index = (int) ((mouseY - (menuY + 1)) / MENU_ROW_H);
                if (index == 0) {
                    ChatHistory.hide(target);
                    afterRemoval();
                } else if (index == 1) {
                    ChatHistory.delete(target);
                    afterRemoval();
                }
            }
            if (inMenu) {
                return true;
            }
        }

        if (local.button() == 1) {
            ChatConversation hit = contactAt(mouseX, mouseY);
            if (hit != null) {
                openMenu(hit.name, mouseX, mouseY);
                return true;
            }
            return false;
        }

        if (super.mouseClicked(local, doubleClick)) {
            return true;
        }
        if (local.button() != 0) {
            return false;
        }

        if (adding) {
            if (inside(mouseX, mouseY, goRect())) {
                confirmAdd();
                return true;
            }
        } else if (inside(mouseX, mouseY, addRect())) {
            setAdding(true);
            return true;
        }

        if (inside(mouseX, mouseY, sendRect())) {
            sendCurrent();
            return true;
        }

        if (mouseX >= railX - 2 && mouseX <= railX + RAIL_W
                && mouseY >= railY && mouseY <= railY + railH) {
            ChatConversation hit = contactAt(mouseX, mouseY);
            if (hit != null) {
                selected = hit.name;
                ChatHistory.markRead(selected);
                builtFor = null;
                rebuildBubbles(true);
                setInitialFocus(input);
            }
            return true;
        }

        return false;
    }

    private ChatConversation contactAt(double x, double y) {
        if (x < railX - 2 || x > railX + RAIL_W || y < railY || y > railY + railH) {
            return null;
        }
        List<ChatConversation> all = ChatHistory.conversations();
        int index = (int) ((y - railY + railScroll) / CONTACT_H);
        return index >= 0 && index < all.size() ? all.get(index) : null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        menuFor = null;

        float s = scale();
        double mx = (mouseX - offsetX()) / s;
        double my = (mouseY - offsetY()) / s;

        if (mx >= railX - 2 && mx <= railX + RAIL_W + 6 && my >= railY && my <= railY + railH) {
            railScroll -= verticalAmount * CONTACT_H;
            clampRailScroll();
            return true;
        }

        if (mx >= threadX && mx <= threadX + threadW + 6 && my >= threadY && my <= threadY + threadH) {
            threadScroll -= verticalAmount * 18;
            clampThreadScroll();
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (adding) {
                confirmAdd();
            } else {
                sendCurrent();
            }
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (menuFor != null) {
                menuFor = null;
                return true;
            }
            if (adding) {
                setAdding(false);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class Bubble {
        final boolean outgoing;
        final List<FormattedCharSequence> lines;
        final int w;
        final int h;
        int y;

        Bubble(boolean outgoing, List<FormattedCharSequence> lines, int w, int h) {
            this.outgoing = outgoing;
            this.lines = lines;
            this.w = w;
            this.h = h;
        }
    }
}
