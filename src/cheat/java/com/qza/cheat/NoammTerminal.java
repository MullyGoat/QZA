package com.qza.cheat;

import com.mojang.blaze3d.platform.Window;
import com.qza.QZA;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public final class NoammTerminal {
    private static final String ROOT = "com.github.noamm9.";
    private static final String TERMINALS = ROOT + "features.impl.floor7.terminals.";
    private static final float SLOT = 16f;
    private static final float REFERENCE_WIDTH = 960f;
    private static final float REFERENCE_HEIGHT = 540f;
    private static final float MENU_SCALE = 3f;

    private static boolean looked;
    private static Object solver;
    private static Object listener;
    private static Class<?> melody;
    private static Field enabled;
    private static Field inTerm;
    private static Field minColumn;
    private static Field minRow;
    private static Method currentHandler;
    private static Method melodyToggle;
    private static Method menuScale;
    private static Method slotGap;
    private static Method value;
    private static Method gridSize;
    private static Method claySlots;
    private static Method solution;
    private static Method slotId;

    private NoammTerminal() {
    }

    public static MelodyAim.Spot melodySpot(Window window, int slot) {
        look();
        if (solver == null) {
            return null;
        }
        try {
            if (!enabled.getBoolean(solver) || !inTerm.getBoolean(null)) {
                return null;
            }
            Object handler = currentHandler.invoke(listener);
            if (!melody.isInstance(handler)
                    || !Boolean.TRUE.equals(value.invoke(melodyToggle.invoke(solver)))) {
                return null;
            }

            int button = ((List<?>) claySlots.invoke(handler)).indexOf(slot);
            Object size = gridSize.invoke(handler);
            int columns = ((Number) size.getClass().getMethod("getFirst").invoke(size)).intValue();
            int rows = ((Number) size.getClass().getMethod("getSecond").invoke(size)).intValue();
            if (button < 0 || button >= rows || columns <= 0) {
                return MelodyAim.Spot.WAIT;
            }

            float uiScale = MENU_SCALE * ((Number) value.invoke(menuScale.invoke(solver))).floatValue();
            float gap = ((Number) value.invoke(slotGap.invoke(solver))).floatValue();
            float guiWidth = window.getGuiScaledWidth();
            float guiHeight = window.getGuiScaledHeight();
            float fit = Math.min(guiWidth / REFERENCE_WIDTH, guiHeight / REFERENCE_HEIGHT);
            if (uiScale <= 0f || fit <= 0f) {
                return MelodyAim.Spot.WAIT;
            }

            float width = columns * SLOT + (columns - 1) * gap;
            float height = rows * SLOT + (rows - 1) * gap;
            float offsetX = (guiWidth / fit) / uiScale / 2f - width / 2f;
            float offsetY = (guiHeight / fit) / uiScale / 2f - height / 2f;

            int[] origin = origin(handler);
            int index = columns + (button * 9) - 1;
            float cell = SLOT + gap;
            float x = ((index % 9) - origin[0]) * cell + (SLOT / 2f);
            float y = ((index / 9) - origin[1]) * cell + (SLOT / 2f);

            return new MelodyAim.Spot((x + offsetX) * uiScale * fit, (y + offsetY) * uiScale * fit);
        } catch (Throwable e) {
            QZA.LOGGER.warn("Could not read NoammAddons' terminal menu, melody aim will skip it", e);
            solver = null;
            return null;
        }
    }

    private static int[] origin(Object handler) throws ReflectiveOperationException {
        Integer column = (Integer) minColumn.get(null);
        Integer row = (Integer) minRow.get(null);
        if (column != null && row != null) {
            return new int[]{column, row};
        }

        List<?> clicks = (List<?>) solution.invoke(handler);
        if (clicks == null || clicks.isEmpty()) {
            return new int[]{0, 0};
        }
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        for (Object click : clicks) {
            int id = (Integer) slotId.invoke(click);
            left = Math.min(left, id % 9);
            top = Math.min(top, id / 9);
        }
        return new int[]{column != null ? column : left, row != null ? row : top};
    }

    private static void look() {
        if (looked) {
            return;
        }
        looked = true;

        try {
            Class<?> solverClass = Class.forName(TERMINALS + "TerminalSolver");
            Class<?> listenerClass = Class.forName(TERMINALS + "TerminalListener");
            Class<?> terminal = Class.forName(TERMINALS + "impl.Terminal");
            Class<?> click = Class.forName(TERMINALS + "TerminalClick");
            Class<?> holder = Class.forName(ROOT + "config.ConfigHolder");

            melody = Class.forName(TERMINALS + "impl.MelodyTerminal");
            listener = listenerClass.getField("INSTANCE").get(null);

            enabled = solverClass.getField("enabled");
            inTerm = listenerClass.getField("inTerm");
            minColumn = solverClass.getDeclaredField("cachedMinCol");
            minRow = solverClass.getDeclaredField("cachedMinRow");
            currentHandler = listenerClass.getMethod("getCurrentHandler");
            melodyToggle = solverClass.getDeclaredMethod("getMelodyTerm");
            menuScale = solverClass.getDeclaredMethod("getScale");
            slotGap = solverClass.getDeclaredMethod("getSlotGap");
            value = holder.getMethod("getValue");
            gridSize = melody.getMethod("getGridSize");
            claySlots = melody.getMethod("getClaySlots");
            solution = terminal.getMethod("getSolution");
            slotId = click.getMethod("getSlotId");

            for (AccessibleObject member : new AccessibleObject[]{minColumn, minRow,
                    melodyToggle, menuScale, slotGap}) {
                member.setAccessible(true);
            }

            solver = solverClass.getField("INSTANCE").get(null);
            QZA.LOGGER.info("NoammAddons' terminal menu found, melody aim will follow its layout");
        } catch (ClassNotFoundException e) {
            solver = null;
        } catch (Throwable e) {
            QZA.LOGGER.warn("NoammAddons' terminal menu is there but not readable, "
                    + "melody aim will skip it", e);
            solver = null;
        }
    }
}
