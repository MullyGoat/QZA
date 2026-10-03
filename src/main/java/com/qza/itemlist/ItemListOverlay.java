package com.qza.itemlist;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class ItemListOverlay {
    public static final ItemListOverlay INSTANCE = new ItemListOverlay();

    private static final int PITCH = 18;
    private static final int MARGIN = 4;
    private static final int PAD = 5;
    private static final int BAR_H = 14;
    private static final int TOGGLE_W = 64;
    private static final int TOGGLE_W_SMALL = 34;
    private static final int WIDE = 170;
    private static final int ARROW_W = 14;
    private static final int MIN_W = 100;
    private static final int MIN_H = 90;
    private static final long FLASH_MS = 2000L;

    private static final int PANEL_BG = 0xB0101014;
    private static final int PANEL_BORDER = 0x40FFFFFF;
    private static final int SLOT_BG = 0x1CFFFFFF;
    private static final int SLOT_HOVER = 0x70FFFFFF;
    private static final int BUTTON_BG = 0x66223140;
    private static final int BUTTON_BG_HOVER = 0xAA3C5A70;
    private static final int BUTTON_BORDER = 0xFF6A8CA8;
    private static final int BUTTON_BORDER_HOVER = 0xFFAFD4EC;
    private static final int SEARCH_BG = 0xCC000000;
    private static final int SEARCH_BORDER = 0x80FFFFFF;
    private static final int SEARCH_BORDER_FOCUS = 0xFFFFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int FLASH = 0xFFFF7777;

    private final ExecutorService searcher = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "QZA Item Search");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger requested = new AtomicInteger();
    private volatile int finished;
    private volatile List<RepoItem> results = List.of();
    private Object searchedData;
    private String searchedQuery;

    private String query = "";
    private EditBox search;
    private int page;

    private boolean visible;
    private int left;
    private int top;
    private int right;
    private int bottom;
    private int gridX;
    private int gridY;
    private int cols;
    private int rows;
    private RepoItem hovered;
    private boolean pressConsumed;
    private String flash;
    private long flashUntil;

    private ItemListOverlay() {
    }

    private static QZAConfig cfg() {
        return ConfigManager.get();
    }

    private static boolean shown() {
        return cfg().itemListShown;
    }

    public int preferredLeft(int width) {
        if (!cfg().itemListEnabled || !shown()) {
            return width;
        }
        return width - (width / 3);
    }

    public boolean searchFocused() {
        return search != null && search.isFocused();
    }

    public void closed() {
        unfocusSearch();
        hovered = null;
    }

    public RepoItem hovered() {
        return hovered;
    }

    private void layout(int width, int height, int minLeft) {
        left = Math.max(width - (width / 3), minLeft);
        right = width - MARGIN;
        top = MARGIN;
        bottom = height - MARGIN;
        visible = right - left >= MIN_W && bottom - top >= MIN_H;

        int innerLeft = left + PAD;
        int innerRight = right - PAD;
        int gridTop = top + PAD + BAR_H + 4;
        int gridBottom = bottom - PAD - BAR_H - 4;
        cols = Math.max(1, (innerRight - innerLeft) / PITCH);
        rows = Math.max(1, (gridBottom - gridTop) / PITCH);
        gridX = innerLeft + ((innerRight - innerLeft) - (cols * PITCH)) / 2 + 1;
        gridY = gridTop + ((gridBottom - gridTop) - (rows * PITCH)) / 2 + 1;
    }

    private boolean wide() {
        return right - left >= WIDE;
    }

    private int[] toggleRect() {
        int w = wide() ? TOGGLE_W : TOGGLE_W_SMALL;
        return new int[]{right - PAD - w, top + PAD, w, BAR_H};
    }

    private int[] prevRect() {
        return new int[]{left + PAD, top + PAD, ARROW_W, BAR_H};
    }

    private int[] nextRect() {
        return new int[]{toggleRect()[0] - 4 - ARROW_W, top + PAD, ARROW_W, BAR_H};
    }

    private int[] searchRect() {
        return new int[]{left + PAD, bottom - PAD - BAR_H, right - left - (2 * PAD), BAR_H};
    }

    private static boolean inside(double x, double y, int[] r) {
        return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    private boolean insidePanel(double x, double y) {
        return visible && x >= left && x < right && y >= top && y < bottom;
    }

    private int perPage() {
        return Math.max(1, cols * rows);
    }

    private int pages(int count) {
        return Math.max(1, (count + perPage() - 1) / perPage());
    }

    public void render(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta,
                       int minLeft) {
        hovered = null;
        layout(screen.width, screen.height, minLeft);
        if (!visible) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        graphics.nextStratum();

        if (!shown()) {
            button(graphics, font, toggleRect(), wide() ? "Show Items" : "Show", mouseX, mouseY);
            return;
        }

        ItemRepo.ensureLoaded();
        ensureSearch(font);
        refreshResults();
        RepoItem.beginFrame();

        graphics.fill(left, top, right, bottom, PANEL_BG);
        outline(graphics, left, top, right - left, bottom - top, PANEL_BORDER);

        List<RepoItem> list = results;
        int pages = pages(list.size());
        page = Math.max(0, Math.min(page, pages - 1));

        button(graphics, font, toggleRect(), wide() ? "Hide Items" : "Hide", mouseX, mouseY);
        button(graphics, font, prevRect(), "<", mouseX, mouseY);
        button(graphics, font, nextRect(), ">", mouseX, mouseY);
        int[] prev = prevRect();
        int[] next = nextRect();
        int middle = (prev[0] + prev[2] + next[0]) / 2;
        String pageText = (page + 1) + "/" + pages;
        if (font.width(pageText) <= next[0] - (prev[0] + prev[2]) - 2) {
            graphics.centeredText(font, pageText, middle, prev[1] + 3, TEXT);
        }

        drawItems(graphics, font, list, mouseX, mouseY);
        drawSearch(graphics, mouseX, mouseY, delta);

        if (flash != null && System.currentTimeMillis() < flashUntil) {
            int[] s = searchRect();
            graphics.centeredText(font, font.plainSubstrByWidth(flash, right - left - 8),
                    (left + right) / 2, s[1] - 11, FLASH);
        }

        if (hovered != null) {
            graphics.setComponentTooltipForNextFrame(font, tooltip(hovered), mouseX, mouseY);
        }
    }

    private void drawItems(GuiGraphicsExtractor graphics, Font font, List<RepoItem> list, int mouseX, int mouseY) {
        int centreX = (left + right) / 2;
        int centreY = (gridY + gridY + (rows * PITCH)) / 2;

        if (list.isEmpty()) {
            String message;
            if (ItemRepo.state() != ItemRepo.State.READY) {
                message = ItemRepo.status();
            } else if (finished != requested.get()) {
                message = "";
            } else {
                message = "No items found";
            }
            if (!message.isEmpty()) {
                graphics.centeredText(font, font.plainSubstrByWidth(message, right - left - 8),
                        centreX, centreY - 4, TEXT_DIM);
            }
            return;
        }

        int start = page * perPage();
        int end = Math.min(list.size(), start + perPage());
        for (int i = start; i < end; i++) {
            int slot = i - start;
            int x = gridX + ((slot % cols) * PITCH);
            int y = gridY + ((slot / cols) * PITCH);
            boolean over = mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, over ? SLOT_HOVER : SLOT_BG);
            RepoItem item = list.get(i);
            graphics.item(item.stack(), x, y);
            if (over) {
                hovered = item;
            }
        }
    }

    private void drawSearch(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int[] r = searchRect();
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], SEARCH_BG);
        outline(graphics, r[0], r[1], r[2], r[3], search.isFocused() ? SEARCH_BORDER_FOCUS : SEARCH_BORDER);
        search.setX(r[0] + 4);
        search.setY(r[1] + 3);
        search.setWidth(r[2] - 8);
        search.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private static void button(GuiGraphicsExtractor graphics, Font font, int[] r, String label,
                               int mouseX, int mouseY) {
        boolean over = inside(mouseX, mouseY, r);
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], over ? BUTTON_BG_HOVER : BUTTON_BG);
        outline(graphics, r[0], r[1], r[2], r[3], over ? BUTTON_BORDER_HOVER : BUTTON_BORDER);
        graphics.centeredText(font, label, r[0] + (r[2] / 2), r[1] + 3, TEXT);
    }

    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x, y, x + w, y + 1, colour);
        graphics.fill(x, y + h - 1, x + w, y + h, colour);
        graphics.fill(x, y + 1, x + 1, y + h - 1, colour);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    private List<Component> tooltip(RepoItem item) {
        List<Component> lines = new ArrayList<>(item.tooltip());
        boolean recipes = !ItemRepo.recipesFor(item.id).isEmpty();
        boolean uses = !ItemRepo.usagesOf(item.id).isEmpty();
        if (recipes || uses) {
            lines.add(Component.empty());
        }
        if (recipes) {
            lines.add(Component.literal("Click to view recipe").withStyle(ChatFormatting.YELLOW));
        }
        if (uses) {
            lines.add(Component.literal("Right-click to view uses").withStyle(ChatFormatting.YELLOW));
        }
        return lines;
    }

    private void ensureSearch(Font font) {
        if (search != null) {
            return;
        }
        search = new EditBox(font, 0, 0, 100, 10, Component.empty());
        search.setBordered(false);
        search.setMaxLength(64);
        search.setHint(Component.literal("Search items...").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            page = 0;
        });
    }

    private void refreshResults() {
        ItemRepo.Data data = ItemRepo.data();
        String text = query;
        if (data == searchedData && text.equals(searchedQuery)) {
            return;
        }
        searchedData = data;
        searchedQuery = text;
        int ticket = requested.incrementAndGet();
        searcher.execute(() -> {
            if (ticket != requested.get()) {
                return;
            }
            List<RepoItem> found = ItemSearch.filter(data.items(), text);
            if (ticket == requested.get()) {
                results = found;
                finished = ticket;
            }
        });
    }

    private RepoItem itemAt(double x, double y) {
        if (x < gridX - 1 || y < gridY - 1) {
            return null;
        }
        int col = (int) Math.floor((x - gridX + 1) / PITCH);
        int row = (int) Math.floor((y - gridY + 1) / PITCH);
        if (col >= cols || row >= rows) {
            return null;
        }
        List<RepoItem> list = results;
        int index = (page * perPage()) + (row * cols) + col;
        return index < list.size() ? list.get(index) : null;
    }

    public boolean mouseClicked(Screen screen, MouseButtonEvent event) {
        pressConsumed = press(screen, event);
        return pressConsumed;
    }

    private boolean press(Screen screen, MouseButtonEvent event) {
        if (!visible || !cfg().itemListEnabled) {
            return false;
        }
        double x = event.x();
        double y = event.y();

        if (inside(x, y, toggleRect())) {
            cfg().itemListShown = !shown();
            ConfigManager.save();
            closed();
            click();
            return true;
        }
        if (!shown()) {
            return false;
        }
        if (!insidePanel(x, y)) {
            unfocusSearch();
            return false;
        }

        if (search != null && inside(x, y, searchRect())) {
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                search.setValue("");
            }
            search.setFocused(true);
            search.mouseClicked(event, false);
            return true;
        }
        unfocusSearch();

        if (inside(x, y, prevRect())) {
            page = Math.max(0, page - 1);
            click();
            return true;
        }
        if (inside(x, y, nextRect())) {
            page = Math.min(pages(results.size()) - 1, page + 1);
            click();
            return true;
        }

        RepoItem item = itemAt(x, y);
        if (item != null) {
            open(screen, item, event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        }
        return true;
    }

    public boolean mouseReleased() {
        boolean consumed = pressConsumed;
        pressConsumed = false;
        return consumed;
    }

    private void unfocusSearch() {
        if (search != null && search.isFocused()) {
            search.setFocused(false);
        }
    }

    public boolean mouseScrolled(double x, double y, double amount) {
        if (!shown() || !insidePanel(x, y) || amount == 0) {
            return false;
        }
        int pages = pages(results.size());
        page = Math.max(0, Math.min(pages - 1, page + (amount < 0 ? 1 : -1)));
        return true;
    }

    public boolean keyPressed(Screen screen, KeyEvent event, RepoItem screenHovered,
                              boolean typingElsewhere) {
        if (searchFocused()) {
            int key = event.key();
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                search.setFocused(false);
                return false;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                search.setFocused(false);
                return true;
            }
            search.keyPressed(event);
            return true;
        }
        if (!visible || !cfg().itemListEnabled || !shown() || typingElsewhere) {
            return false;
        }
        if (event.key() == GLFW.GLFW_KEY_F && event.hasControlDown() && search != null) {
            search.setFocused(true);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_R || event.key() == GLFW.GLFW_KEY_U) {
            RepoItem target = hovered != null ? hovered : screenHovered;
            if (target != null) {
                open(screen, target, event.key() == GLFW.GLFW_KEY_U);
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        if (!searchFocused()) {
            return false;
        }
        search.charTyped(event);
        return true;
    }

    void open(Screen screen, RepoItem item, boolean usages) {
        boolean hasRecipes = !ItemRepo.recipesFor(item.id).isEmpty();
        boolean hasUses = !ItemRepo.usagesOf(item.id).isEmpty();
        if (usages ? !hasUses : !hasRecipes) {
            if (!usages && hasUses) {
                flash("No recipe - right-click for uses");
            } else {
                flash(usages ? "Nothing uses " + item.plainName : "No recipe for " + item.plainName);
            }
            return;
        }
        click();
        closed();
        RecipeScreen.open(screen, item.id, usages);
    }

    void flash(String message) {
        flash = message;
        flashUntil = System.currentTimeMillis() + FLASH_MS;
    }

    private static void click() {
        AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
    }
}
