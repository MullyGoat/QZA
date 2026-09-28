package com.qza.cheat;

import com.qza.QZA;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public final class OdinTerminal {
    private static final String MELODY_GUI =
            "com.odtheking.odin.features.impl.boss.termGUI.MelodyGui";
    private static final String TERM_GUI =
            "com.odtheking.odin.features.impl.boss.termGUI.TermGui";

    private static boolean looked;
    private static Object instance;
    private static Field gridField;
    private static Method active;
    private static Method guiScale;
    private static Method originX;
    private static Method originY;
    private static Method gridWidth;
    private static Method gridHeight;
    private static Method slots;
    private static Method slotIndex;
    private static Method boxX;
    private static Method boxY;
    private static Method boxSize;
    private static Object staleGrid;

    private OdinTerminal() {
    }

    public static void screenChanged() {
        look();
        staleGrid = null;
        if (instance == null) {
            return;
        }
        try {
            staleGrid = gridField.get(instance);
        } catch (Throwable e) {
            fail(e);
        }
    }

    public static MelodyAim.Spot melodySpot(AbstractContainerScreen<?> screen, int slot) {
        look();
        if (instance == null) {
            return null;
        }
        try {
            if (!(Boolean) active.invoke(instance)) {
                return null;
            }

            Object grid = gridField.get(instance);
            if (grid == null || grid == staleGrid || screen.width <= 0 || screen.height <= 0) {
                return MelodyAim.Spot.WAIT;
            }

            float scale = (Float) guiScale.invoke(instance);
            if (scale <= 0f) {
                return MelodyAim.Spot.WAIT;
            }

            float ox = (Float) originX.invoke(grid);
            float oy = (Float) originY.invoke(grid);
            float ex = (screen.width - ((Integer) gridWidth.invoke(grid)) * scale) / 2f;
            float ey = (screen.height - ((Integer) gridHeight.invoke(grid)) * scale) / 2f;
            if (Math.abs(ox - ex) > 0.5f || Math.abs(oy - ey) > 0.5f) {
                return MelodyAim.Spot.WAIT;
            }

            Object boxes = slots.invoke(grid);
            if (!(boxes instanceof List<?> list)) {
                return MelodyAim.Spot.WAIT;
            }
            for (Object box : list) {
                if ((Integer) slotIndex.invoke(box) != slot) {
                    continue;
                }
                float half = ((Integer) boxSize.invoke(box)) / 2f;
                return new MelodyAim.Spot(
                        ox + (((Integer) boxX.invoke(box)) + half) * scale,
                        oy + (((Integer) boxY.invoke(box)) + half) * scale);
            }
            return MelodyAim.Spot.WAIT;
        } catch (Throwable e) {
            fail(e);
            return null;
        }
    }

    private static void fail(Throwable e) {
        QZA.LOGGER.warn("Could not read Odin's terminal layout, melody aim will use the chest", e);
        instance = null;
        staleGrid = null;
    }

    private static void look() {
        if (looked) {
            return;
        }
        looked = true;

        try {
            Class<?> melody = Class.forName(MELODY_GUI);
            Class<?> term = Class.forName(TERM_GUI);

            Object gui = melody.getField("INSTANCE").get(null);

            gridField = term.getDeclaredField("grid");
            active = term.getDeclaredMethod("isActiveTermScreen");
            guiScale = term.getDeclaredMethod("getGuiScale");

            Class<?> grid = Class.forName(TERM_GUI + "$Grid");
            originX = grid.getDeclaredMethod("getOriginX");
            originY = grid.getDeclaredMethod("getOriginY");
            gridWidth = grid.getDeclaredMethod("getW");
            gridHeight = grid.getDeclaredMethod("getH");
            slots = grid.getDeclaredMethod("getSlots");

            Class<?> box = Class.forName(TERM_GUI + "$SlotBox");
            slotIndex = box.getDeclaredMethod("getSlotIndex");
            boxX = box.getDeclaredMethod("getBx");
            boxY = box.getDeclaredMethod("getBy");
            boxSize = box.getDeclaredMethod("getSize");

            for (AccessibleObject member : new AccessibleObject[]{gridField, active, guiScale,
                    originX, originY, gridWidth, gridHeight, slots, slotIndex, boxX, boxY, boxSize}) {
                member.setAccessible(true);
            }

            instance = gui;
            QZA.LOGGER.info("Odin's terminal GUI found, melody aim will follow its layout");
        } catch (ClassNotFoundException e) {
            instance = null;
        } catch (Throwable e) {
            QZA.LOGGER.warn("Odin's terminal GUI is there but not readable, "
                    + "melody aim will use the chest layout", e);
            instance = null;
        }
    }
}
