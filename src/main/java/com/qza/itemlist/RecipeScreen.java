package com.qza.itemlist;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class RecipeScreen extends Screen {
    private static final Identifier CRAFTING_TABLE =
            Identifier.withDefaultNamespace("textures/gui/container/crafting_table.png");
    private static final int FRAME_W = 176;
    private static final int FRAME_H = 166;
    private static final int TAB_W = 28;
    private static final int TAB_H = 26;
    private static final int MAX_HISTORY = 50;
    private static final long FLASH_MS = 2000L;

    private static final int LABEL = 0xFF404040;
    private static final int PANEL = 0xFFC6C6C6;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int DARK = 0xFF373737;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int HIGHLIGHT = 0x80FFFFFF;

    private static final Deque<View> HISTORY = new ArrayDeque<>();
    private static ItemStack unknown;

    private record View(String id, boolean usages, Recipe.Type tab, int page) {
    }

    private record Spot(int x, int y, Ingredient ingredient, Recipe.Drop drop) {
    }

    private final boolean backToInventory;
    private final ItemListOverlay overlay = ItemListOverlay.INSTANCE;
    private final List<Spot> spots = new ArrayList<>();

    private String id;
    private boolean usages;
    private final Map<Recipe.Type, List<Recipe>> byType = new EnumMap<>(Recipe.Type.class);
    private final List<Recipe.Type> tabs = new ArrayList<>();
    private Recipe.Type tab;
    private int page;

    private int x0;
    private int y0;
    private Spot hoveredSpot;
    private ItemStack hoveredStack;
    private String flash;
    private long flashUntil;

    private RecipeScreen(boolean backToInventory) {
        super(Component.literal("Recipe"));
        this.backToInventory = backToInventory;
    }

    public static void open(Screen from, String id, boolean usages) {
        if (from instanceof RecipeScreen current) {
            current.navigate(id, usages);
            return;
        }
        Minecraft client = Minecraft.getInstance();
        boolean inventory = from instanceof InventoryScreen;
        HISTORY.clear();
        RecipeScreen screen = new RecipeScreen(inventory);
        screen.show(id, usages, null, 0);
        LocalPlayer player = client.player;
        boolean chest = from instanceof AbstractContainerScreen<?> && !inventory && player != null
                && player.containerMenu != player.inventoryMenu;
        int containerId = chest ? player.containerMenu.containerId : -1;
        client.setScreen(screen);
        if (chest) {
            player.connection.send(new ServerboundContainerClosePacket(containerId));
            player.containerMenu = player.inventoryMenu;
        }
    }

    private void navigate(String target, boolean showUsages) {
        if (target.equals(id) && showUsages == usages) {
            return;
        }
        HISTORY.push(new View(id, usages, tab, page));
        while (HISTORY.size() > MAX_HISTORY) {
            HISTORY.removeLast();
        }
        show(target, showUsages, null, 0);
    }

    private boolean back() {
        View view = HISTORY.poll();
        if (view == null) {
            return false;
        }
        show(view.id(), view.usages(), view.tab(), view.page());
        click();
        return true;
    }

    private void show(String target, boolean showUsages, Recipe.Type wanted, int wantedPage) {
        id = target;
        usages = showUsages;
        byType.clear();
        tabs.clear();
        for (Recipe recipe : showUsages ? ItemRepo.usagesOf(target) : ItemRepo.recipesFor(target)) {
            byType.computeIfAbsent(recipe.type(), key -> new ArrayList<>()).add(recipe);
        }
        tabs.addAll(byType.keySet());
        tab = wanted != null && byType.containsKey(wanted) ? wanted : (tabs.isEmpty() ? null : tabs.get(0));
        page = Math.max(0, Math.min(wantedPage, pageCount() - 1));
    }

    private List<Recipe> current() {
        return tab == null ? List.of() : byType.getOrDefault(tab, List.of());
    }

    private int pageCount() {
        return Math.max(1, current().size());
    }

    private Recipe recipe() {
        List<Recipe> list = current();
        return list.isEmpty() ? null : list.get(Math.max(0, Math.min(page, list.size() - 1)));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        this.extractTransparentBackground(graphics);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        RepoItem.beginFrame();
        spots.clear();
        hoveredSpot = null;
        hoveredStack = inventoryStackAt(mouseX, mouseY);

        int available = overlay.preferredLeft(this.width);
        x0 = Math.max(TAB_W + 4, (available - FRAME_W) / 2);
        y0 = Math.max(4, (this.height - FRAME_H) / 2);

        drawTabs(graphics, mouseX, mouseY, false);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CRAFTING_TABLE, x0, y0, 0, 0, FRAME_W, FRAME_H, 256, 256);
        drawTabs(graphics, mouseX, mouseY, true);

        Recipe recipe = recipe();
        if (!(recipe instanceof Recipe.Crafting)) {
            graphics.fill(x0 + 7, y0 + 15, x0 + 169, y0 + 71, PANEL);
        }
        drawTitle(graphics, recipe);
        if (recipe == null) {
            centred(graphics, "No recipes found", x0 + (FRAME_W / 2), y0 + 38, LABEL);
        } else {
            drawRecipe(graphics, recipe);
        }
        drawPager(graphics, mouseX, mouseY);
        drawBack(graphics, mouseX, mouseY);
        drawInventory(graphics);

        for (Spot spot : spots) {
            drawSpot(graphics, spot, mouseX, mouseY);
        }

        if (flash != null && System.currentTimeMillis() < flashUntil) {
            graphics.centeredText(this.font, flash, x0 + (FRAME_W / 2), y0 + FRAME_H + 4, 0xFFFF7777);
        }

        overlay.render(this, graphics, mouseX, mouseY, delta, x0 + FRAME_W + 4);

        if (overlay.hovered() == null) {
            if (hoveredSpot != null) {
                graphics.setComponentTooltipForNextFrame(this.font, tooltip(hoveredSpot), mouseX, mouseY);
            } else if (hoveredStack != null) {
                graphics.setTooltipForNextFrame(this.font, hoveredStack, mouseX, mouseY);
            }
            tabTooltip(graphics, mouseX, mouseY);
        }
    }

    private void drawTitle(GuiGraphicsExtractor graphics, Recipe recipe) {
        RepoItem item = ItemRepo.item(id);
        String name = item != null ? item.plainName : id;
        String title;
        if (recipe instanceof Recipe.Drops drops && !usages) {
            String mob = com.qza.util.IgnUtil.stripCodes(drops.name()).trim();
            title = drops.level() > 0 ? mob + " (Lv" + drops.level() + ")" : mob;
        } else {
            title = usages ? "Uses of " + name : name;
        }
        int width = HISTORY.isEmpty() ? 160 : 128;
        graphics.text(this.font, this.font.plainSubstrByWidth(title, width), x0 + 8, y0 + 6, LABEL, false);
    }

    private void drawRecipe(GuiGraphicsExtractor graphics, Recipe recipe) {
        switch (recipe) {
            case Recipe.Crafting crafting -> {
                for (int i = 0; i < 9; i++) {
                    Ingredient input = crafting.grid().get(i);
                    if (input != null) {
                        spots.add(new Spot(x0 + 30 + ((i % 3) * 18), y0 + 17 + ((i / 3) * 18), input, null));
                    }
                }
                spots.add(new Spot(x0 + 124, y0 + 35, crafting.output(), null));
                note(graphics, crafting.text());
            }
            case Recipe.Forge forge -> {
                grid(graphics, forge.inputs(), 0);
                output(graphics, forge.output());
                note(graphics, forge.seconds() > 0 ? "Forge time: " + duration(forge.seconds()) : "");
            }
            case Recipe.Shop shop -> {
                grid(graphics, shop.cost(), 0);
                output(graphics, shop.output());
                RepoItem npc = ItemRepo.item(shop.npc());
                note(graphics, "Sold by " + (npc != null ? npc.plainName : shop.npc()));
            }
            case Recipe.Trade trade -> {
                grid(graphics, List.of(trade.cost()), 4);
                output(graphics, trade.output());
                note(graphics, "Villager trade");
            }
            case Recipe.Kat kat -> {
                List<Ingredient> inputs = new ArrayList<>();
                inputs.add(kat.input());
                inputs.addAll(kat.items());
                grid(graphics, inputs, 0);
                output(graphics, kat.output());
                StringBuilder text = new StringBuilder();
                if (kat.coins() > 0) {
                    text.append(String.format("%,d coins", kat.coins()));
                }
                if (kat.seconds() > 0) {
                    text.append(text.isEmpty() ? "" : ", ").append(duration(kat.seconds()));
                }
                note(graphics, text.toString());
            }
            case Recipe.Drops drops -> {
                List<Recipe.Drop> list = drops.drops();
                for (int i = 0; i < Math.min(list.size(), 27); i++) {
                    int x = x0 + 8 + ((i % 9) * 18);
                    int y = y0 + 17 + ((i / 9) * 18);
                    slot(graphics, x, y);
                    spots.add(new Spot(x, y, list.get(i).item(), list.get(i)));
                }
            }
        }
    }

    private void grid(GuiGraphicsExtractor graphics, List<Ingredient> inputs, int startSlot) {
        int count = Math.min(inputs.size(), 9 - startSlot);
        for (int i = 0; i < count; i++) {
            int slot = startSlot + i;
            int x = x0 + 30 + ((slot % 3) * 18);
            int y = y0 + 17 + ((slot / 3) * 18);
            slot(graphics, x, y);
            spots.add(new Spot(x, y, inputs.get(i), null));
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, CRAFTING_TABLE, x0 + 90, y0 + 35, 90, 35, 22, 15, 256, 256);
    }

    private void output(GuiGraphicsExtractor graphics, Ingredient output) {
        int x = x0 + 124;
        int y = y0 + 35;
        bevel(graphics, x - 5, y - 5, 26, 26);
        spots.add(new Spot(x, y, output, null));
    }

    private void note(GuiGraphicsExtractor graphics, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String clean = com.qza.util.IgnUtil.stripCodes(text).trim();
        float scale = 0.75f;
        int maxWidth = (int) (78 / scale);
        String shown = this.font.plainSubstrByWidth(clean, maxWidth);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x0 + 132, y0 + 59);
        graphics.pose().scale(scale, scale);
        centred(graphics, shown, 0, 0, LABEL);
        graphics.pose().popMatrix();
    }

    private void drawPager(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (pageCount() <= 1) {
            return;
        }
        arrow(graphics, prevPage(), "<", mouseX, mouseY, page > 0);
        arrow(graphics, nextPage(), ">", mouseX, mouseY, page < pageCount() - 1);
        centred(graphics, (page + 1) + "/" + pageCount(), x0 + 143, y0 + 73, LABEL);
    }

    private int[] prevPage() {
        return new int[]{x0 + 116, y0 + 71, 11, 11};
    }

    private int[] nextPage() {
        return new int[]{x0 + 159, y0 + 71, 11, 11};
    }

    private int[] backRect() {
        return new int[]{x0 + FRAME_W - 34, y0 + 4, 28, 11};
    }

    private void drawBack(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!HISTORY.isEmpty()) {
            arrow(graphics, backRect(), "Back", mouseX, mouseY, true);
        }
    }

    private void arrow(GuiGraphicsExtractor graphics, int[] r, String label, int mouseX, int mouseY,
                       boolean enabled) {
        boolean over = enabled && inside(mouseX, mouseY, r);
        bevel(graphics, r[0], r[1], r[2], r[3]);
        if (over) {
            graphics.fill(r[0] + 1, r[1] + 1, r[0] + r[2] - 1, r[1] + r[3] - 1, 0xFFA0A0A0);
        }
        centred(graphics, label, r[0] + (r[2] / 2) + 1, r[1] + 2,
                enabled ? (over ? LIGHT : LABEL) : 0xFF9A9A9A);
    }

    private void drawInventory(GuiGraphicsExtractor graphics) {
        graphics.text(this.font, "Inventory", x0 + 8, y0 + 72, LABEL, false);
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        Inventory inventory = client.player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            int x = x0 + 8 + ((i % 9) * 18);
            int y = i < 9 ? y0 + 142 : y0 + 84 + (((i - 9) / 9) * 18);
            graphics.item(stack, x, y);
            graphics.itemDecorations(this.font, stack, x, y);
        }
    }

    private void drawSpot(GuiGraphicsExtractor graphics, Spot spot, int mouseX, int mouseY) {
        boolean over = mouseX >= spot.x() - 1 && mouseX < spot.x() + 17
                && mouseY >= spot.y() - 1 && mouseY < spot.y() + 17;
        if (over) {
            graphics.fill(spot.x(), spot.y(), spot.x() + 16, spot.y() + 16, HIGHLIGHT);
            hoveredSpot = spot;
        }
        ItemStack stack = stackOf(spot.ingredient());
        graphics.item(stack, spot.x(), spot.y());
        if (spot.ingredient().count() != 1 || spot.ingredient().isCoins()) {
            graphics.itemDecorations(this.font, stack, spot.x(), spot.y(), spot.ingredient().countText());
        }
    }

    private void drawTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean selectedPass) {
        if (tabs.size() < 2) {
            return;
        }
        for (int i = 0; i < tabs.size(); i++) {
            Recipe.Type type = tabs.get(i);
            boolean selected = type == tab;
            if (selected != selectedPass) {
                continue;
            }
            int[] r = tabRect(i);
            int x = selected ? r[0] : r[0] + 2;
            int w = selected ? r[2] + 3 : r[2] - 2;
            graphics.fill(x, r[1], x + w, r[1] + r[3], PANEL);
            graphics.fill(x, r[1], x + w, r[1] + 1, LIGHT);
            graphics.fill(x, r[1], x + 1, r[1] + r[3], LIGHT);
            graphics.fill(x, r[1] + r[3] - 1, x + w, r[1] + r[3], DARK);
            if (!selected) {
                graphics.fill(x + w - 1, r[1], x + w, r[1] + r[3], DARK);
            }
            Identifier icon = Identifier.tryParse(type.icon);
            ItemStack stack = icon == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.getValue(icon));
            graphics.item(stack, x + 6, r[1] + 5);
        }
    }

    private int[] tabRect(int index) {
        return new int[]{x0 - TAB_W + 3, y0 + 4 + (index * (TAB_H + 2)), TAB_W - 3, TAB_H};
    }

    private void tabTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (tabs.size() < 2) {
            return;
        }
        for (int i = 0; i < tabs.size(); i++) {
            if (inside(mouseX, mouseY, tabRect(i))) {
                Recipe.Type type = tabs.get(i);
                int count = byType.getOrDefault(type, List.of()).size();
                graphics.setComponentTooltipForNextFrame(this.font, List.of(
                        Component.literal(type.label),
                        Component.literal(count + (count == 1 ? " recipe" : " recipes"))
                                .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
                return;
            }
        }
    }

    private List<Component> tooltip(Spot spot) {
        Ingredient ingredient = spot.ingredient();
        List<Component> lines = new ArrayList<>();
        if (ingredient.isCoins()) {
            lines.add(Component.literal(ingredient.fullCount() + " Coins").withStyle(ChatFormatting.GOLD));
            return lines;
        }
        RepoItem item = ItemRepo.item(ingredient.id());
        if (item != null) {
            lines.addAll(item.tooltip());
        } else {
            lines.add(Component.literal(ingredient.id()).withStyle(ChatFormatting.RED));
        }
        if (ingredient.count() != 1) {
            lines.add(Component.empty());
            lines.add(Component.literal("Amount: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(ingredient.fullCount()).withStyle(ChatFormatting.WHITE)));
        }
        Recipe.Drop drop = spot.drop();
        if (drop != null) {
            if (!drop.chance().isBlank()) {
                if (ingredient.count() == 1) {
                    lines.add(Component.empty());
                }
                lines.add(Component.literal("Chance: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(drop.chance()).withStyle(ChatFormatting.WHITE)));
            }
            for (String extra : drop.extra()) {
                lines.add(Component.literal(extra));
            }
        }
        if (item != null) {
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
        }
        return lines;
    }

    private static ItemStack stackOf(Ingredient ingredient) {
        if (ingredient.isCoins()) {
            return RepoItem.coins();
        }
        RepoItem item = ItemRepo.item(ingredient.id());
        if (item != null) {
            return item.stack();
        }
        if (unknown == null) {
            unknown = new ItemStack(Items.BARRIER);
        }
        return unknown;
    }

    private void slot(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT);
        graphics.fill(x - 1, y - 1, x + 17, y, DARK);
        graphics.fill(x - 1, y - 1, x, y + 17, DARK);
        graphics.fill(x - 1, y + 16, x + 17, y + 17, LIGHT);
        graphics.fill(x + 16, y - 1, x + 17, y + 17, LIGHT);
    }

    private void bevel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, SLOT);
        graphics.fill(x, y, x + w, y + 1, DARK);
        graphics.fill(x, y, x + 1, y + h, DARK);
        graphics.fill(x, y + h - 1, x + w, y + h, LIGHT);
        graphics.fill(x + w - 1, y, x + w, y + h, LIGHT);
    }

    private static String duration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return minutes > 0 ? hours + "h " + minutes + "m" : hours + "h";
        }
        if (minutes > 0) {
            return secs > 0 ? minutes + "m " + secs + "s" : minutes + "m";
        }
        return secs + "s";
    }

    private void centred(GuiGraphicsExtractor graphics, String text, int centreX, int y, int colour) {
        graphics.text(this.font, text, centreX - (this.font.width(text) / 2), y, colour, false);
    }

    private static boolean inside(double x, double y, int[] r) {
        return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    private RepoItem hoveredItem() {
        if (hoveredSpot != null && !hoveredSpot.ingredient().isCoins()) {
            return ItemRepo.item(hoveredSpot.ingredient().id());
        }
        if (hoveredStack != null) {
            return ItemRepo.item(ItemList.skyblockId(hoveredStack));
        }
        return null;
    }

    private void openItem(RepoItem item, boolean showUsages) {
        if (item == null) {
            return;
        }
        boolean has = !(showUsages ? ItemRepo.usagesOf(item.id) : ItemRepo.recipesFor(item.id)).isEmpty();
        if (!has) {
            boolean uses = !ItemRepo.usagesOf(item.id).isEmpty();
            flash(!showUsages && uses ? "No recipe - right-click for uses"
                    : (showUsages ? "Nothing uses " : "No recipe for ") + item.plainName);
            return;
        }
        click();
        navigate(item.id, showUsages);
    }

    private void flash(String message) {
        flash = message;
        flashUntil = System.currentTimeMillis() + FLASH_MS;
    }

    private static void click() {
        AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
    }

    private ItemStack inventoryStackAt(double mouseX, double mouseY) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return null;
        }
        for (int i = 0; i < 36; i++) {
            int x = x0 + 8 + ((i % 9) * 18);
            int y = i < 9 ? y0 + 142 : y0 + 84 + (((i - 9) / 9) * 18);
            if (mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17) {
                ItemStack stack = client.player.getInventory().getItem(i);
                return stack.isEmpty() ? null : stack;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (overlay.mouseClicked(this, event)) {
            return true;
        }
        double x = event.x();
        double y = event.y();
        int button = event.button();

        if (button == GLFW.GLFW_MOUSE_BUTTON_4) {
            back();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (!HISTORY.isEmpty() && inside(x, y, backRect())) {
                back();
                return true;
            }
            if (pageCount() > 1 && inside(x, y, prevPage()) && page > 0) {
                page--;
                click();
                return true;
            }
            if (pageCount() > 1 && inside(x, y, nextPage()) && page < pageCount() - 1) {
                page++;
                click();
                return true;
            }
            if (tabs.size() > 1) {
                for (int i = 0; i < tabs.size(); i++) {
                    if (inside(x, y, tabRect(i)) && tabs.get(i) != tab) {
                        tab = tabs.get(i);
                        page = 0;
                        click();
                        return true;
                    }
                }
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            boolean showUsages = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            for (Spot spot : spots) {
                if (x >= spot.x() - 1 && x < spot.x() + 17 && y >= spot.y() - 1 && y < spot.y() + 17) {
                    if (!spot.ingredient().isCoins()) {
                        openItem(ItemRepo.item(spot.ingredient().id()), showUsages);
                    }
                    return true;
                }
            }
            ItemStack stack = inventoryStackAt(x, y);
            if (stack != null) {
                RepoItem item = ItemRepo.item(ItemList.skyblockId(stack));
                if (item != null) {
                    openItem(item, showUsages);
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (overlay.mouseScrolled(mouseX, mouseY, vertical)) {
            return true;
        }
        if (pageCount() > 1 && vertical != 0 && mouseX >= x0 && mouseX < x0 + FRAME_W
                && mouseY >= y0 && mouseY < y0 + FRAME_H) {
            page = Math.max(0, Math.min(pageCount() - 1, page + (vertical < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (overlay.keyPressed(this, event, hoveredItem(), false)) {
            return true;
        }
        int key = event.key();
        if (key == GLFW.GLFW_KEY_R || key == GLFW.GLFW_KEY_U) {
            RepoItem item = hoveredItem();
            if (item != null) {
                openItem(item, key == GLFW.GLFW_KEY_U);
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            back();
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT && page > 0) {
            page--;
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT && page < pageCount() - 1) {
            page++;
            return true;
        }
        if (key != GLFW.GLFW_KEY_ESCAPE && this.minecraft.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return overlay.charTyped(event) || super.charTyped(event);
    }

    @Override
    public void onClose() {
        HISTORY.clear();
        overlay.closed();
        if (backToInventory && this.minecraft.player != null) {
            this.minecraft.setScreen(new InventoryScreen(this.minecraft.player));
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
