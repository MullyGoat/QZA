package com.qza.cheat.compat;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;

public final class CursorWarp {
    private CursorWarp() {
    }

    public static void to(Window window, double x, double y) {
        GLFW.glfwSetCursorPos(window.handle(), x, y);
    }
}
