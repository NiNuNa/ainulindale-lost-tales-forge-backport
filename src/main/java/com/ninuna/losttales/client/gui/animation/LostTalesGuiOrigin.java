package com.ninuna.losttales.client.gui.animation;

import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Where a screen's fixed furniture stands: the bottom strip, the map's
 * compass, legend and prompts, drawn free of the screen's own entrance
 * motion. On a screen of its own that is the GUI's origin. A screen drawn
 * inside a window's box marks the box's origin while it draws, so the
 * same furniture stays in the box and lands where its presses are read.
 */
public final class LostTalesGuiOrigin {
    private static final float GUI_MODELVIEW_Z = -2000.0F;
    private static final FloatBuffer MARKED = BufferUtils.createFloatBuffer(16);
    private static boolean marked;

    private LostTalesGuiOrigin() {}

    /** Takes the matrix in force now as the origin, until {@link #unmark}. */
    public static void mark() {
        MARKED.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MARKED);
        MARKED.rewind();
        marked = true;
    }

    public static void unmark() {
        marked = false;
    }

    /** Replaces the matrix in force with the origin's. */
    public static void load() {
        if (marked) {
            MARKED.rewind();
            GL11.glLoadMatrix(MARKED);
            MARKED.rewind();
            return;
        }
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, GUI_MODELVIEW_Z);
    }
}
