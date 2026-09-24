package com.qza.dungeon;

import com.qza.QZA;

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
    private static Method guiScale;
    private static Method originX;
    private static Method originY;
    private static Method slots;
    private static Method slotIndex;
    private static Method boxX;
    private static Method boxY;
    private static Method boxSize;

    private OdinTerminal() {
    }

    public static boolean present() {
        look();
        return instance != null;
    }

    public static float[] screenPos(int slot) {
        look();
        if (instance == null) {
            return null;
        }
        try {
            Object grid = gridField.get(instance);
            if (grid == null) {
                return null;
            }

            Object boxes = slots.invoke(grid);
            if (!(boxes instanceof List<?> list) || list.isEmpty()) {
                return null;
            }

            for (Object box : list) {
                if ((Integer) slotIndex.invoke(box) != slot) {
                    continue;
                }
                float scale = (Float) guiScale.invoke(instance);
                if (scale <= 0f) {
                    return null;
                }
                float size = ((Integer) boxSize.invoke(box)).floatValue();
                float bx = ((Integer) boxX.invoke(box)).floatValue();
                float by = ((Integer) boxY.invoke(box)).floatValue();

                return new float[]{
                        (Float) originX.invoke(grid) + ((bx + (size / 2f)) * scale),
                        (Float) originY.invoke(grid) + ((by + (size / 2f)) * scale)};
            }
            return null;
        } catch (Throwable e) {
            QZA.LOGGER.warn("Could not read Odin's terminal layout, falling back", e);
            instance = null;
            return null;
        }
    }

    private static void look() {
        if (looked) {
            return;
        }
        looked = true;

        try {
            Class<?> melody = Class.forName(MELODY_GUI);
            Class<?> term = Class.forName(TERM_GUI);

            instance = melody.getField("INSTANCE").get(null);

            gridField = term.getDeclaredField("grid");
            gridField.setAccessible(true);

            guiScale = term.getDeclaredMethod("getGuiScale");
            guiScale.setAccessible(true);

            Class<?> grid = Class.forName(TERM_GUI + "$Grid");
            originX = grid.getDeclaredMethod("getOriginX");
            originY = grid.getDeclaredMethod("getOriginY");
            slots = grid.getDeclaredMethod("getSlots");
            originX.setAccessible(true);
            originY.setAccessible(true);
            slots.setAccessible(true);

            Class<?> box = Class.forName(TERM_GUI + "$SlotBox");
            slotIndex = box.getDeclaredMethod("getSlotIndex");
            boxX = box.getDeclaredMethod("getBx");
            boxY = box.getDeclaredMethod("getBy");
            boxSize = box.getDeclaredMethod("getSize");
            slotIndex.setAccessible(true);
            boxX.setAccessible(true);
            boxY.setAccessible(true);
            boxSize.setAccessible(true);

            QZA.LOGGER.info("Odin's terminal GUI found, melody aim will follow its layout");
        } catch (ClassNotFoundException e) {
            instance = null;
        } catch (Throwable e) {
            QZA.LOGGER.warn("Odin's terminal GUI is there but not readable, "
                    + "melody aim will use the chest layout", e);
            instance = null;
        }
    }

    public static void forget() {
        looked = false;
        instance = null;
    }
}
