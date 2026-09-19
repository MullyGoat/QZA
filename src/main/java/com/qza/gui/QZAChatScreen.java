package com.qza.gui;

import com.qza.chat.ChannelHistory;
import com.qza.chat.ChatConversation;
import com.qza.chat.ChatFocus;
import com.qza.chat.ChatHistory;
import com.qza.chat.ChatMessage;
import com.qza.party.PartyInvite;
import com.qza.stats.AutoInvite;
import com.qza.config.ConfigManager;
import com.qza.util.ChatUtil;
import com.qza.util.IgnUtil;
import com.qza.util.PlayerFaces;
import com.qza.util.PlayerLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    private static final int SYS_BG = 0x8C1E2A22;
    private static final int SYS_BORDER = 0xB055FF55;

    private static final int HEADER_H = 62;
    private static final int RAIL_W = 190;
    private static final int CONTACT_H = 28;
    private static final int FACE = 16;
    private static final int SMALL_FACE = 8;
    private static final int INPUT_H = 18;
    private static final int SEND_W = 46;
    private static final int BUBBLE_PAD = 5;
    private static final int BUBBLE_GAP = 4;
    private static final int LINE_H = 10;

    private static final float TITLE_SCALE = 1.5f;

    private static final String TAB_DM = "dm";
    private static final String[] TAB_KEYS = {
            TAB_DM, ChannelHistory.EVERYTHING, ChannelHistory.ALL,
            ChannelHistory.PARTY, ChannelHistory.GUILD, ChannelHistory.COOP};
    private static final String[] TAB_LABELS = {
            "DMs", "Everything", "All", "Party", "Guild", "Co-op"};
    private static final int TAB_H = 14;


    private static String selected;
    private static String selectedTab = TAB_DM;

    private static final int MENU_W = 92;
    private static final int MENU_ROW_H = 12;
    private static final String[] MENU_LABELS = {"Hide", "Delete", "Run Stats Check"};
    private static final long COPIED_MS = 1500L;
    private static final int INVITE_FIELD_W = 110;
    private static final int INVITE_GO_W = 27;

    private EditBox input;
    private EditBox ignInput;
    private boolean adding;
    private boolean inviting;

    private CommandSuggestions commandSuggestions;

    /**
     * How far back through the sent messages the arrows have walked. Equal to
     * the number of them when not walking, which is one past the newest, and
     * is where the half-typed line is kept while the arrows are in use.
     */
    private int historyPos;
    private String historyDraft = "";

    private String menuFor;
    private int menuX;
    private int menuY;
    private long copiedAt;

    private Bubble hoverBubble;
    private Segment hoverSegment;
    private long handCursor;
    private boolean handApplied;

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

    private String prefill;

    public QZAChatScreen() {
        super(Component.literal("QZA Chat"));

        String wanted = ChatFocus.resolve(selectedTab);
        for (String key : TAB_KEYS) {
            if (key.equals(wanted)) {
                selectedTab = wanted;
                break;
            }
        }
    }

    /**
     * Opens on a set tab with the box already filled in, ignoring the Default
     * Tab preference. The slash key uses this so it always lands on Everything
     * ready to type a command, the way vanilla chat does.
     */
    public QZAChatScreen(String tab, String text) {
        super(Component.literal("QZA Chat"));

        for (String key : TAB_KEYS) {
            if (key.equals(tab)) {
                selectedTab = tab;
                break;
            }
        }
        this.prefill = text;
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
        ChatHistory.refreshIdentities();

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

        // Vanilla's own component, so the popup, the highlight, the grey ghost
        // text and the argument usage hints all look and behave exactly as
        // they do in normal chat. Same arguments vanilla's ChatScreen uses.
        commandSuggestions = new CommandSuggestions(this.minecraft, this, input, this.font,
                false, false, 1, 10, true, 0xD0000000);
        input.setResponder(text -> refreshSuggestions());
        refreshSuggestions();

        // Cleared once applied, so a resize does not wipe what has been typed.
        if (prefill != null) {
            input.setValue(prefill);
            input.moveCursorToEnd(false);
            prefill = null;
        }

        forgetHistoryPosition();

        builtFor = null;
        rebuildBubbles(true);
    }

    private void layout() {
        panelX = 8;
        panelY = 6;
        panelW = this.width - 16;
        panelH = this.height - 12;

        boolean dm = isDm();

        railX = panelX + 10;
        railY = panelY + HEADER_H;
        int railBottom = panelY + panelH - 10;
        railH = Math.max(40, railBottom - railY);

        threadX = dm ? panelX + RAIL_W + 28 : panelX + 12;
        int threadRight = panelX + panelW - 12;
        threadW = Math.max(120, threadRight - threadX);
        threadY = railY + 22;
        inputY = railBottom - INPUT_H;
        threadH = Math.max(40, inputY - 8 - threadY);
    }

    private static boolean isDm() {
        return TAB_DM.equals(selectedTab);
    }

    private int[] settingsRect() {
        return new int[]{panelX + panelW - 28, panelY + 20, 16, 16};
    }

    private void drawSettings(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int[] r = settingsRect();
        boolean hovered = inside(mouseX, mouseY, r);
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3],
                hovered ? 0xAA3C5A70 : 0x66223140);
        outline(graphics, r[0], r[1], r[2], r[3], hovered ? 0xFFAFD4EC : 0xFF6A8CA8);

        int colour = hovered ? 0xFFFFFFFF : 0xFFCCCCCC;
        int x = r[0] + 3;
        int y = r[1] + 3;

        graphics.fill(x + 4, y, x + 7, y + 2, colour);
        graphics.fill(x + 4, y + 9, x + 7, y + 11, colour);
        graphics.fill(x, y + 4, x + 2, y + 7, colour);
        graphics.fill(x + 9, y + 4, x + 11, y + 7, colour);

        graphics.fill(x + 2, y + 2, x + 9, y + 4, colour);
        graphics.fill(x + 2, y + 7, x + 9, y + 9, colour);
        graphics.fill(x + 2, y + 4, x + 4, y + 7, colour);
        graphics.fill(x + 7, y + 4, x + 9, y + 7, colour);
    }

    private int[] tabRect(int index) {
        int x = railX;
        for (int i = 0; i < index; i++) {
            x += this.font.width(TAB_LABELS[i]) + 18;
        }
        return new int[]{x, panelY + 42, this.font.width(TAB_LABELS[index]) + 14, TAB_H};
    }

    private void selectTab(String tab) {
        if (tab.equals(selectedTab)) {
            return;
        }
        selectedTab = tab;
        menuFor = null;
        inviting = false;
        setAdding(false);
        refreshSuggestions();
        layout();
        repositionInput();
        builtFor = null;
        rebuildBubbles(true);
    }

    /**
     * True while the box holds nothing but a slash, where a list of every
     * command on the server would only be noise.
     */
    private static boolean isBareSlash(String text) {
        return text != null && text.startsWith("/") && text.substring(1).isBlank();
    }

    /** Completion is for running commands, which the DMs tab does not do. */
    private boolean suggestionsAllowed() {
        return !isDm() && !adding && !inviting && menuFor == null;
    }

    /**
     * Vanilla anchors the popup and the usage hint to {@code screen.height - 12},
     * which is where its own chat box sits at the bottom of the screen. QZA's box
     * is partway up inside the panel, so everything it draws is shifted by the
     * difference to land just above the box instead of on top of it.
     */
    private int suggestionShiftY() {
        return inputY - (this.height - 12);
    }

    /** Recomputes the popup and the grey ghost text after the line changes. */
    private void refreshSuggestions() {
        if (commandSuggestions == null || input == null) {
            return;
        }
        if (!suggestionsAllowed()) {
            commandSuggestions.setAllowSuggestions(false);
            input.setSuggestion(null);
            return;
        }

        commandSuggestions.setAllowSuggestions(true);
        commandSuggestions.updateCommandInfo();

        if (isBareSlash(input.getValue())) {
            commandSuggestions.hide();
            input.setSuggestion(null);
        }
    }

    /**
     * Keeps the gate honest between keystrokes, since opening a menu or the
     * IGN field does not change the text.
     */
    private void updateSuggestionGate() {
        if (commandSuggestions == null || input == null) {
            return;
        }
        if (!suggestionsAllowed()) {
            commandSuggestions.setAllowSuggestions(false);
            input.setSuggestion(null);
        } else {
            commandSuggestions.setAllowSuggestions(true);
        }
    }

    private void repositionInput() {
        int boxW = threadW - SEND_W - 6;
        input.setX(threadX + 5);
        input.setY(inputY + 5);
        input.setWidth(Math.max(40, boxW - 10));
    }

    private ChatConversation current() {
        return selected == null ? null : ChatHistory.get(selected);
    }

    private List<ChatMessage> currentMessages() {
        if (!isDm()) {
            return ChannelHistory.get(selectedTab);
        }
        ChatConversation conversation = current();
        return conversation == null || conversation.messages == null
                ? List.of() : conversation.messages;
    }

    private static Component content(ChatMessage message) {
        return message.rich != null ? message.rich : Component.literal(message.text);
    }

    private String builtKey() {
        return selectedTab + "/" + (isDm() ? String.valueOf(selected) : "");
    }

    private void rebuildBubbles(boolean toBottom) {
        List<ChatMessage> messages = currentMessages();
        int count = messages.size();
        String key = builtKey();

        if (key.equals(builtFor) && builtCount == count && builtWidth == threadW) {
            return;
        }

        boolean grew = key.equals(builtFor) && count > builtCount;
        builtFor = key;
        builtCount = count;
        builtWidth = threadW;
        bubbles.clear();

        double share = isDm() ? 0.72 : 0.94;
        int maxTextW = Math.max(40, (int) (threadW * share) - (BUBBLE_PAD * 2));
        int y = 0;
        for (ChatMessage message : messages) {
            String speaker = isDm() ? null : message.speaker;
            int faceRoom = speaker == null ? 0 : SMALL_FACE + 4;

            Component body = content(message);
            int wrapAt = Math.max(30, maxTextW - faceRoom);
            List<FormattedCharSequence> lines = this.font.split(body, wrapAt);
            if (lines.isEmpty()) {
                continue;
            }
            List<FormattedText> rawLines =
                    this.font.getSplitter().splitLines(body, wrapAt, Style.EMPTY);
            int widest = 0;
            for (FormattedCharSequence line : lines) {
                widest = Math.max(widest, this.font.width(line));
            }
            Bubble bubble = new Bubble(message.outgoing, message.text, speaker, faceRoom,
                    lines, rawLines,
                    widest + faceRoom + (BUBBLE_PAD * 2),
                    (lines.size() * LINE_H) + (BUBBLE_PAD * 2) - 1);
            bubble.system = message.system;
            bubble.y = y;
            bubbles.add(bubble);
            y += bubble.h + BUBBLE_GAP;
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

        if (!adding && !inviting && getFocused() != input) {
            setInitialFocus(input);
        }

        float s = scale();
        int mx = Math.round((mouseX - offsetX()) / s);
        int my = Math.round((mouseY - offsetY()) / s);

        updateHover(mx, my);
        updateSuggestionGate();

        graphics.pose().pushMatrix();
        graphics.pose().translate(offsetX(), offsetY());
        graphics.pose().scale(s, s);

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        outline(graphics, panelX, panelY, panelW, panelH, 0x33FFFFFF);
        if (isDm()) {
            graphics.fill(panelX, panelY + HEADER_H, panelX + RAIL_W + 8,
                    panelY + panelH, RAIL_BG);
        }

        drawHeader(graphics);
        drawSettings(graphics, mx, my);
        drawTabs(graphics, mx, my);
        if (isDm()) {
            drawAdd(graphics, mx, my);
            drawContacts(graphics, mx, my);
        }
        drawThread(graphics);
        drawInvite(graphics, mx, my);
        drawInput(graphics, mx, my);

        super.extractRenderState(graphics, mx, my, delta);

        drawHoverText(graphics);
        drawMenu(graphics, mx, my);

        // Last, so the list sits above the thread while it is open.
        if (commandSuggestions != null && suggestionsAllowed()) {
            int shift = suggestionShiftY();
            graphics.pose().pushMatrix();
            graphics.pose().translate(0f, (float) shift);
            commandSuggestions.extractRenderState(graphics, mx, my - shift);
            graphics.pose().popMatrix();
        }

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

    private void drawTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int i = 0; i < TAB_KEYS.length; i++) {
            int[] r = tabRect(i);
            boolean active = TAB_KEYS[i].equals(selectedTab);
            boolean hovered = inside(mouseX, mouseY, r);
            graphics.centeredText(this.font, TAB_LABELS[i],
                    r[0] + (r[2] / 2), r[1] + 3,
                    active ? PINK : (hovered ? TEXT : TEXT_DIM));
            graphics.fill(r[0], r[1] + TAB_H - 1, r[0] + r[2], r[1] + TAB_H,
                    active ? PINK : 0x33FFFFFF);
        }
    }

    private String channelTitle() {
        for (int i = 0; i < TAB_KEYS.length; i++) {
            if (TAB_KEYS[i].equals(selectedTab)) {
                return TAB_LABELS[i] + " Chat";
            }
        }
        return "Chat";
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

    private int[] kebabRect(int rowY) {
        return new int[]{railX + RAIL_W - 13, rowY + 2, 11, 13};
    }

    private void drawKebab(GuiGraphicsExtractor graphics, int[] r, boolean hovered) {
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3],
                hovered ? 0xAA3C5A70 : 0x44223140);
        outline(graphics, r[0], r[1], r[2], r[3], hovered ? 0xFFAFD4EC : 0x666A8CA8);

        int colour = hovered ? 0xFFFFFFFF : 0xFFCCCCCC;
        int x = r[0] + 5;
        int y = r[1] + 3;
        graphics.fill(x, y, x + 2, y + 2, colour);
        graphics.fill(x, y + 3, x + 2, y + 5, colour);
        graphics.fill(x, y + 6, x + 2, y + 8, colour);
    }

    /** Centred for QZA's own lines, otherwise sent right and received left. */
    private int bubbleX(Bubble bubble) {
        if (bubble.system) {
            return threadX + ((threadW - bubble.w) / 2);
        }
        return bubble.outgoing ? threadX + threadW - bubble.w : threadX;
    }

    private void drawMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (menuFor == null) {
            return;
        }

        int h = menuHeight();
        graphics.fill(menuX, menuY, menuX + MENU_W, menuY + h, 0xF00E1218);
        outline(graphics, menuX, menuY, MENU_W, h, PINK);

        for (int i = 0; i < MENU_LABELS.length; i++) {
            int rowY = menuY + 1 + (i * MENU_ROW_H);
            boolean hovered = mouseX >= menuX && mouseX <= menuX + MENU_W
                    && mouseY >= rowY && mouseY < rowY + MENU_ROW_H;
            if (hovered) {
                graphics.fill(menuX + 1, rowY, menuX + MENU_W - 1, rowY + MENU_ROW_H, 0x663C5A70);
            }
            int colour = switch (i) {
                case 1 -> hovered ? 0xFFFF7B7B : 0xFFE08A8A;
                case 2 -> hovered ? 0xFF9BE8A0 : 0xFF7FBF86;
                default -> hovered ? TEXT : 0xFFCCCCCC;
            };
            graphics.text(this.font, MENU_LABELS[i], menuX + 5, rowY + 2, colour);
        }
    }

    private int menuHeight() {
        return (MENU_ROW_H * MENU_LABELS.length) + 2;
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
        if (value) {
            inviting = false;
        }
        refreshIgnInput();
    }

    private void refreshIgnInput() {
        boolean active = adding || inviting;
        ignInput.visible = active;
        ignInput.setValue("");

        if (!active) {
            setInitialFocus(input);
            return;
        }
        if (adding) {
            ignInput.setX(railX + 5);
            ignInput.setY(panelY + 24);
            ignInput.setWidth(RAIL_W - 41);
        } else {
            int[] field = inviteFieldRect();
            ignInput.setX(field[0] + 5);
            ignInput.setY(field[1] + 4);
            ignInput.setWidth(field[2] - 10);
        }
        setInitialFocus(ignInput);
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

            if (hovered) {
                int[] kebab = kebabRect(y);
                drawKebab(graphics, kebab, inside(mouseX, mouseY, kebab));
            } else {
                String when = ago(conversation.lastActivity);
                if (!when.isEmpty()) {
                    graphics.text(this.font, when,
                            railX + RAIL_W - this.font.width(when) - 4, y + 4, TEXT_FAINT);
                }
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

        if (isDm()) {
            if (conversation == null) {
                graphics.centeredText(this.font, "Pick a conversation on the left",
                        threadX + (threadW / 2), threadY + (threadH / 2) - 4, TEXT_DIM);
                return;
            }
            PlayerFaceExtractor.extractRenderState(graphics,
                    PlayerFaces.skinFor(conversation.name), threadX, railY, FACE);
            graphics.text(this.font, conversation.name, threadX + FACE + 6, railY + 4, TEXT);
        } else {
            graphics.text(this.font, channelTitle(), threadX, railY + 4, PINK);
        }
        graphics.fill(threadX, railY + 18, threadX + threadW, railY + 19, 0x33FFFFFF);

        String hint = System.currentTimeMillis() - copiedAt < COPIED_MS
                ? "Copied to clipboard"
                : "Right-click a message to copy";
        int hintColour = System.currentTimeMillis() - copiedAt < COPIED_MS ? 0xFF7BE87B : TEXT_FAINT;
        graphics.text(this.font, hint,
                threadX + threadW - this.font.width(hint), railY + 4, hintColour);

        if (bubbles.isEmpty()) {
            graphics.centeredText(this.font,
                    isDm() ? "No messages in this conversation"
                            : "Nothing in this channel yet this session",
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
            int x = bubbleX(bubble);

            graphics.fill(x, y, x + bubble.w, y + bubble.h,
                    bubble.system ? SYS_BG : bubble.outgoing ? OUT_BG : IN_BG);
            outline(graphics, x, y, bubble.w, bubble.h,
                    bubble.system ? SYS_BORDER : bubble.outgoing ? OUT_BORDER : IN_BORDER);

            if (bubble.speaker != null && bubble.faceRoom > 0) {
                PlayerFaceExtractor.extractRenderState(graphics,
                        PlayerFaces.skinFor(bubble.speaker),
                        x + BUBBLE_PAD, y + BUBBLE_PAD, SMALL_FACE);
            }

            int textX = x + BUBBLE_PAD + bubble.faceRoom;
            int lineY = y + BUBBLE_PAD;
            for (FormattedCharSequence line : bubble.lines) {
                graphics.text(this.font, line, textX, lineY, TEXT);
                lineY += LINE_H;
            }

            if (bubble == hoverBubble && hoverSegment != null
                    && hoverSegment.style().getClickEvent() != null) {
                int underlineY = y + BUBBLE_PAD + (hoverSegment.line() * LINE_H) + 9;
                graphics.fill(textX + Math.round(hoverSegment.start()), underlineY,
                        textX + Math.round(hoverSegment.end()), underlineY + 1, 0xFFFFFFFF);
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
        boolean ready = !input.getValue().trim().isEmpty() && (!isDm() || current() != null);
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

    /**
     * The messages the player has sent, newest last. Vanilla's own list, so
     * this box and the normal chat box share one history.
     */
    private List<String> sentHistory() {
        return this.minecraft.gui.getChat().getRecentChat();
    }

    /** Back to the newest end, with no half-typed line held over. */
    private void forgetHistoryPosition() {
        historyPos = sentHistory().size();
        historyDraft = "";
    }

    /**
     * Walks the sent messages, the way the arrows do in vanilla chat. Stepping
     * back off the newest one restores whatever was half-typed at the time.
     */
    private void moveInHistory(int by) {
        List<String> sent = sentHistory();
        int size = sent.size();
        int target = Mth.clamp(historyPos + by, 0, size);
        if (target == historyPos) {
            return;
        }

        if (target == size) {
            historyPos = size;
            input.setValue(historyDraft);
            return;
        }

        if (historyPos == size) {
            historyDraft = input.getValue();
        }
        historyPos = target;
        input.setValue(sent.get(target));

        // After the responder has run, so a recalled command does not open the
        // popup and swallow the next press of the arrow.
        if (commandSuggestions != null) {
            commandSuggestions.setAllowSuggestions(false);
            input.setSuggestion(null);
        }
    }

    private void sendCurrent() {
        String text = input.getValue().trim();
        if (text.isEmpty()) {
            return;
        }

        if (text.startsWith("/") || ChannelHistory.EVERYTHING.equals(selectedTab)) {
            ChatUtil.sendTyped(text);
        } else if (isDm()) {
            ChatConversation conversation = current();
            if (conversation == null) {
                return;
            }
            ChatHistory.send(conversation.name, text);
        } else {
            ChatUtil.sendTyped("/" + ChannelHistory.command(selectedTab) + " " + text);
        }
        ChatFocus.sent(selectedTab);
        input.setValue("");
        forgetHistoryPosition();
    }

    /**
     * Parties only. Guild and coop invites are rare enough that a button is no
     * real saving, and a misclicked coop invite is not something the recipient
     * can easily be taken back out of.
     */
    private String inviteLabel() {
        return ChannelHistory.PARTY.equals(selectedTab) ? "Invite to Party" : null;
    }

    private int[] inviteRect() {
        String label = inviteLabel();
        int w = label == null ? 0 : this.font.width(label) + 16;
        return new int[]{threadX + (threadW / 2) - (w / 2), railY, w, 16};
    }

    private int[] inviteFieldRect() {
        int total = INVITE_FIELD_W + 4 + INVITE_GO_W;
        return new int[]{threadX + (threadW / 2) - (total / 2), railY, INVITE_FIELD_W, 16};
    }

    private int[] inviteGoRect() {
        int[] field = inviteFieldRect();
        return new int[]{field[0] + field[2] + 4, field[1], INVITE_GO_W, 16};
    }

    private void drawInvite(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        String label = inviteLabel();
        if (label == null) {
            return;
        }

        if (!inviting) {
            int[] r = inviteRect();
            boolean hovered = inside(mouseX, mouseY, r);
            graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3],
                    hovered ? 0xAA3C5A70 : 0x99223140);
            outline(graphics, r[0], r[1], r[2], r[3],
                    hovered ? 0xFFAFD4EC : 0xFF6A8CA8);
            graphics.centeredText(this.font, label, r[0] + (r[2] / 2), r[1] + 4, TEXT);
            return;
        }

        int[] field = inviteFieldRect();
        boolean valid = validIgn(ignInput.getValue()) != null;
        boolean empty = ignInput.getValue().trim().isEmpty();
        graphics.fill(field[0], field[1], field[0] + field[2], field[1] + field[3], 0x66000000);
        outline(graphics, field[0], field[1], field[2], field[3],
                empty || valid ? BOX_BORDER : 0xFFE05555);

        int[] go = inviteGoRect();
        boolean hovered = inside(mouseX, mouseY, go);
        graphics.fill(go[0], go[1], go[0] + go[2], go[1] + go[3],
                valid && hovered ? 0xAA5A2E62 : 0x99321A38);
        outline(graphics, go[0], go[1], go[2], go[3],
                valid ? (hovered ? PINK : 0xFFB05CB8) : 0xFF5A5A5A);
        graphics.centeredText(this.font, "Go", go[0] + (go[2] / 2), go[1] + 4,
                valid ? TEXT : TEXT_FAINT);
    }

    private void setInviting(boolean value) {
        inviting = value && inviteLabel() != null;
        if (inviting) {
            adding = false;
        }
        refreshIgnInput();
    }

    private void confirmInvite() {
        String invite = ChannelHistory.inviteCommand(selectedTab);
        String ign = validIgn(ignInput.getValue());
        if (invite == null || ign == null) {
            return;
        }
        if (ChannelHistory.PARTY.equals(selectedTab)) {
            PartyInvite.send(ign);
        } else {
            ChatUtil.sendCommand(invite + " " + ign);
        }
        setInviting(false);
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

        if (commandSuggestions != null && suggestionsAllowed()) {
            MouseButtonEvent shifted = new MouseButtonEvent(local.x(),
                    local.y() - suggestionShiftY(), local.buttonInfo());
            if (commandSuggestions.mouseClicked(shifted)) {
                return true;
            }
        }

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
                } else if (index == 2) {
                    AutoInvite.check(target);
                }
            }
            if (inMenu) {
                return true;
            }
        }

        if (local.button() == 1) {
            Bubble bubble = bubbleAt(mouseX, mouseY);
            if (bubble != null) {
                copy(bubble);
                return true;
            }
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

        Bubble clicked = bubbleAt(mouseX, mouseY);
        if (clicked != null && runClick(styleAtBubble(clicked, mouseX, mouseY))) {
            return true;
        }

        if (inside(mouseX, mouseY, settingsRect())) {
            QZAScreen.openCategory("Chat");
            this.minecraft.setScreen(new QZAScreen());
            return true;
        }

        if (inviteLabel() != null) {
            if (inviting) {
                if (inside(mouseX, mouseY, inviteGoRect())) {
                    confirmInvite();
                    return true;
                }
            } else if (inside(mouseX, mouseY, inviteRect())) {
                setInviting(true);
                return true;
            }
        }

        for (int i = 0; i < TAB_KEYS.length; i++) {
            if (inside(mouseX, mouseY, tabRect(i))) {
                selectTab(TAB_KEYS[i]);
                return true;
            }
        }

        if (isDm()) {
            if (adding) {
                if (inside(mouseX, mouseY, goRect())) {
                    confirmAdd();
                    return true;
                }
            } else if (inside(mouseX, mouseY, addRect())) {
                setAdding(true);
                return true;
            }
        }

        if (inside(mouseX, mouseY, sendRect())) {
            sendCurrent();
            return true;
        }

        if (isDm() && mouseX >= railX - 2 && mouseX <= railX + RAIL_W
                && mouseY >= railY && mouseY <= railY + railH) {
            ChatConversation hit = contactAt(mouseX, mouseY);
            if (hit != null && inside(mouseX, mouseY, kebabRect(contactRowY(hit)))) {
                openMenu(hit.name, mouseX, mouseY);
                return true;
            }
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

    private Bubble bubbleAt(double x, double y) {
        if (x < threadX || x > threadX + threadW || y < threadY || y > threadY + threadH) {
            return null;
        }
        int top = threadY - (int) Math.round(threadScroll);
        for (Bubble bubble : bubbles) {
            int by = top + bubble.y;
            int bx = bubbleX(bubble);
            if (x >= bx && x <= bx + bubble.w && y >= by && y <= by + bubble.h) {
                return bubble;
            }
        }
        return null;
    }

    private record Segment(Style style, int line, float start, float end) {
    }

    private Segment segmentAt(FormattedText line, int index, int targetX) {
        if (targetX < 0) {
            return null;
        }
        float[] used = {0f};
        return line.visit((style, text) -> {
            float width = this.font.getSplitter().stringWidth(FormattedText.of(text, style));
            if (targetX < used[0] + width) {
                return Optional.of(new Segment(style, index, used[0], used[0] + width));
            }
            used[0] += width;
            return Optional.empty();
        }, Style.EMPTY).orElse(null);
    }

    private Segment segmentAtBubble(Bubble bubble, double mouseX, double mouseY) {
        if (bubble.rawLines == null || bubble.rawLines.isEmpty()) {
            return null;
        }
        int top = threadY - (int) Math.round(threadScroll);
        int by = top + bubble.y;
        int bx = bubbleX(bubble);

        int index = (int) Math.floor((mouseY - (by + BUBBLE_PAD)) / (double) LINE_H);
        if (index < 0 || index >= bubble.rawLines.size()) {
            return null;
        }
        int x = (int) Math.floor(mouseX - (bx + BUBBLE_PAD + bubble.faceRoom));
        return segmentAt(bubble.rawLines.get(index), index, x);
    }

    private Style styleAtBubble(Bubble bubble, double mouseX, double mouseY) {
        Segment segment = segmentAtBubble(bubble, mouseX, mouseY);
        return segment == null ? null : segment.style();
    }

    private void updateHover(int mouseX, int mouseY) {
        hoverBubble = null;
        hoverSegment = null;

        Bubble bubble = bubbleAt(mouseX, mouseY);
        if (bubble == null) {
            applyHandCursor(false);
            return;
        }
        Segment segment = segmentAtBubble(bubble, mouseX, mouseY);
        if (segment == null) {
            applyHandCursor(false);
            return;
        }

        boolean clickable = segment.style().getClickEvent() != null;
        if (clickable || segment.style().getHoverEvent() != null) {
            hoverBubble = bubble;
            hoverSegment = segment;
        }
        applyHandCursor(clickable);
    }

    private void drawHoverText(GuiGraphicsExtractor graphics) {
        if (hoverBubble == null || hoverSegment == null) {
            return;
        }
        if (!(hoverSegment.style().getHoverEvent() instanceof HoverEvent.ShowText show)) {
            return;
        }

        List<FormattedCharSequence> lines =
                this.font.split(show.value(), Math.max(120, (int) (threadW * 0.8)));
        if (lines.isEmpty()) {
            return;
        }

        int widest = 0;
        for (FormattedCharSequence line : lines) {
            widest = Math.max(widest, this.font.width(line));
        }

        int top = threadY - (int) Math.round(threadScroll);
        int hoverX = bubbleX(hoverBubble);
        int textX = hoverX + BUBBLE_PAD + hoverBubble.faceRoom;

        int w = widest + 8;
        int h = (lines.size() * LINE_H) + 6;
        int x = Math.max(panelX + 4,
                Math.min(textX + Math.round(hoverSegment.start()),
                        panelX + panelW - w - 4));
        int y = top + hoverBubble.y + BUBBLE_PAD + (hoverSegment.line() * LINE_H) + LINE_H + 2;
        if (y + h > panelY + panelH - 4) {
            y = top + hoverBubble.y + BUBBLE_PAD + (hoverSegment.line() * LINE_H) - h - 2;
        }

        graphics.fill(x, y, x + w, y + h, 0xF00E1218);
        outline(graphics, x, y, w, h, PINK);

        int lineY = y + 4;
        for (FormattedCharSequence line : lines) {
            graphics.text(this.font, line, x + 4, lineY, TEXT);
            lineY += LINE_H;
        }
    }

    private void applyHandCursor(boolean hand) {
        if (hand == handApplied) {
            return;
        }
        long window = this.minecraft.getWindow().handle();
        if (hand) {
            if (handCursor == 0L) {
                handCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_POINTING_HAND_CURSOR);
            }
            if (handCursor == 0L) {
                return;
            }
            GLFW.glfwSetCursor(window, handCursor);
        } else {
            GLFW.glfwSetCursor(window, 0L);
        }
        handApplied = hand;
    }

    @Override
    public void removed() {
        applyHandCursor(false);
        if (handCursor != 0L) {
            GLFW.glfwDestroyCursor(handCursor);
            handCursor = 0L;
        }
        super.removed();
    }

    private boolean runClick(Style style) {
        ClickEvent event = style == null ? null : style.getClickEvent();
        if (event == null) {
            return false;
        }

        if (event instanceof ClickEvent.RunCommand run) {
            ChatUtil.sendChat(run.command());
            return true;
        }
        if (event instanceof ClickEvent.SuggestCommand suggest) {
            input.setValue(suggest.command());
            input.moveCursorToEnd(false);
            setInitialFocus(input);
            return true;
        }
        if (event instanceof ClickEvent.CopyToClipboard copy) {
            this.minecraft.keyboardHandler.setClipboard(copy.value());
            copiedAt = System.currentTimeMillis();
            return true;
        }
        if (event instanceof ClickEvent.OpenUrl open) {
            URI uri = open.uri();
            QZAChatScreen self = this;
            this.minecraft.setScreen(new ConfirmLinkScreen(confirmed -> {
                if (confirmed) {
                    Util.getPlatform().openUri(uri);
                }
                this.minecraft.setScreen(self);
            }, uri.toString(), false));
            return true;
        }
        return false;
    }

    private void copy(Bubble bubble) {
        String line;
        if (isDm() && !bubble.system) {
            ChatConversation conversation = current();
            if (conversation == null) {
                return;
            }
            line = (bubble.outgoing ? "To " : "From ")
                    + conversation.name + ": " + bubble.text;
        } else {
            line = bubble.text;
        }
        this.minecraft.keyboardHandler.setClipboard(line);
        copiedAt = System.currentTimeMillis();
    }

    private int contactRowY(ChatConversation conversation) {
        List<ChatConversation> all = ChatHistory.conversations();
        int top = railY - (int) Math.round(railScroll);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i) == conversation) {
                return top + (i * CONTACT_H);
            }
        }
        return Integer.MIN_VALUE;
    }

    private ChatConversation contactAt(double x, double y) {
        if (!isDm() || x < railX - 2 || x > railX + RAIL_W || y < railY || y > railY + railH) {
            return null;
        }
        List<ChatConversation> all = ChatHistory.conversations();
        int index = (int) ((y - railY + railScroll) / CONTACT_H);
        return index >= 0 && index < all.size() ? all.get(index) : null;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        float s = scale();
        return super.mouseDragged(toLogical(event), deltaX / s, deltaY / s);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(toLogical(event));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        if (commandSuggestions != null && suggestionsAllowed()
                && commandSuggestions.mouseScrolled(verticalAmount)) {
            return true;
        }

        menuFor = null;

        float s = scale();
        double mx = (mouseX - offsetX()) / s;
        double my = (mouseY - offsetY()) / s;

        if (isDm() && mx >= railX - 2 && mx <= railX + RAIL_W + 6
                && my >= railY && my <= railY + railH) {
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
        // The popup takes the arrows, Tab and Escape while it is open. It never
        // takes Enter, so sending still works. Suppressed on a bare slash so
        // Tab cannot force the full command list open either.
        if (commandSuggestions != null && suggestionsAllowed()
                && !isBareSlash(input.getValue())
                && commandSuggestions.keyPressed(event)) {
            return true;
        }
        // Only once the popup has passed, which is the order vanilla uses: the
        // arrows pick a suggestion while one is showing and walk what has been
        // sent otherwise. Skipped while the ign box has the screen.
        if (!adding && !inviting) {
            if (event.key() == GLFW.GLFW_KEY_UP) {
                moveInHistory(-1);
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_DOWN) {
                moveInHistory(1);
                return true;
            }
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (adding) {
                confirmAdd();
            } else if (inviting) {
                confirmInvite();
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
            if (inviting) {
                setInviting(false);
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
        final String text;
        final String speaker;
        final int faceRoom;
        final List<FormattedCharSequence> lines;
        final List<FormattedText> rawLines;
        final int w;
        final int h;
        int y;
        boolean system;

        Bubble(boolean outgoing, String text, String speaker, int faceRoom,
               List<FormattedCharSequence> lines, List<FormattedText> rawLines, int w, int h) {
            this.outgoing = outgoing;
            this.text = text;
            this.speaker = speaker;
            this.faceRoom = faceRoom;
            this.lines = lines;
            this.rawLines = rawLines;
            this.w = w;
            this.h = h;
        }
    }
}
