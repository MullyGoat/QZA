package com.qza.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.itemlist.ItemListOverlay;
import com.qza.util.ChatUtil;
import com.qza.util.SkyBlockArea;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.input.InputWithModifiers;

import java.util.List;
import java.util.Locale;

public final class CommandKeybinds {
    public static final String MOD_ANY = "any";
    public static final String MOD_NONE = "none";
    public static final String MOD_SHIFT = "shift";
    public static final String MOD_CTRL = "ctrl";
    public static final String MOD_ALT = "alt";
    public static final List<String> MODIFIERS = List.of(MOD_ANY, MOD_NONE, MOD_SHIFT, MOD_CTRL, MOD_ALT);

    private CommandKeybinds() {
    }

    public static boolean onInput(InputConstants.Key key, int action, InputWithModifiers modifiers) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.commandKeybindsEnabled || key == null || cfg.commandBinds.isEmpty()) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.getConnection() == null || Mc.overlay() != null) {
            return false;
        }

        String name = key.getName();
        for (CommandBind bind : cfg.commandBinds) {
            if (bind == null || !bind.enabled || !bind.bound() || !name.equals(bind.key)) {
                continue;
            }
            if (bind.command == null || bind.command.isBlank()) {
                continue;
            }
            if (!screenAllows(cfg.commandKeybindsInMenus || bind.inMenus)
                    || !modifierMatches(bind.modifier, modifiers) || !islandMatches(bind.islands)) {
                continue;
            }
            if (action == InputConstants.PRESS) {
                ChatUtil.sendTyped(bind.command.trim());
            }
            return true;
        }
        return false;
    }

    private static boolean screenAllows(boolean inMenus) {
        Screen screen = Mc.screen();
        if (screen == null) {
            return true;
        }
        if (!inMenus || !(screen instanceof AbstractContainerScreen<?>) || screen instanceof AnvilScreen) {
            return false;
        }
        if (ItemListOverlay.INSTANCE.searchFocused()) {
            return false;
        }
        return !(screen.getFocused() instanceof EditBox box && box.isFocused());
    }

    private static boolean modifierMatches(String modifier, InputWithModifiers input) {
        if (modifier == null || MOD_ANY.equals(modifier)) {
            return true;
        }
        boolean shift = input.hasShiftDown();
        boolean ctrl = input.hasControlDown();
        boolean alt = input.hasAltDown();
        return switch (modifier) {
            case MOD_NONE -> !shift && !ctrl && !alt;
            case MOD_SHIFT -> shift && !ctrl && !alt;
            case MOD_CTRL -> ctrl && !shift && !alt;
            case MOD_ALT -> alt && !shift && !ctrl;
            default -> true;
        };
    }

    private static boolean islandMatches(String islands) {
        if (islands == null || islands.isBlank()) {
            return true;
        }
        String area = SkyBlockArea.current();
        return area.isEmpty() || islands.toLowerCase(Locale.ROOT).contains(area.toLowerCase(Locale.ROOT));
    }

    public static String keyLabel(String key) {
        if (key == null || key.isBlank() || CommandBind.UNBOUND.equals(key)) {
            return "None";
        }
        try {
            return InputConstants.getKey(key).getDisplayName().getString();
        } catch (RuntimeException e) {
            return key;
        }
    }

    public static String modifierLabel(String modifier) {
        if (modifier == null) {
            return "Any";
        }
        return switch (modifier) {
            case MOD_NONE -> "None";
            case MOD_SHIFT -> "Shift";
            case MOD_CTRL -> "Ctrl";
            case MOD_ALT -> "Alt";
            default -> "Any";
        };
    }

    public static String nextModifier(String modifier) {
        int index = MODIFIERS.indexOf(modifier == null ? MOD_ANY : modifier);
        return MODIFIERS.get((index + 1) % MODIFIERS.size());
    }
}
