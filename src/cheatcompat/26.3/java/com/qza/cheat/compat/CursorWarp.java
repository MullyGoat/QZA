package com.qza.cheat.compat;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.sdl.SDLMouse;

public final class CursorWarp {
    private CursorWarp() {
    }

    public static void to(Window window, double x, double y) {
        SDLMouse.SDL_WarpMouseInWindow(window.handle(), (float) x, (float) y);
    }
}
