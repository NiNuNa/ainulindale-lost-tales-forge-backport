package com.ninuna.losttales.client.render;

import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Flat-colour silhouette mode for textured GUI quads: while active, every
 * textured fragment takes its RGB from one constant colour and only its
 * alpha from the texture (times the current vertex alpha). This is how
 * emotes, head icons, and item icons get the same solid drop shadow as
 * text. Plain tinting cannot do this — multiplying a sprite's own dark
 * outline by the shadow colour yields near-black, which is exactly the
 * inconsistency this replaces.
 *
 * <p>The constant lives in the texture environment rather than the vertex
 * colour so helpers that set their own {@code glColor} (item rendering,
 * head tinting) still produce the requested shadow colour. Requires
 * OpenGL 1.3 texture combiners, which Minecraft 1.7.10 already relies on.</p>
 */
public final class LostTalesSilhouetteRenderState {
    private static final FloatBuffer CONSTANT_COLOR =
            BufferUtils.createFloatBuffer(4);
    private static boolean active;

    private LostTalesSilhouetteRenderState() {}

    /** Enters silhouette mode with the given opaque RGB; nestable no-op. */
    public static void begin(int rgb) {
        CONSTANT_COLOR.clear();
        CONSTANT_COLOR.put(((rgb >> 16) & 0xFF) / 255.0F)
                .put(((rgb >> 8) & 0xFF) / 255.0F)
                .put((rgb & 0xFF) / 255.0F)
                .put(1.0F);
        CONSTANT_COLOR.flip();
        GL11.glTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR,
                CONSTANT_COLOR);
        if (active) {
            return;
        }
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE,
                GL13.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB,
                GL11.GL_REPLACE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB,
                GL13.GL_CONSTANT);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB,
                GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA,
                GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA,
                GL11.GL_TEXTURE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA,
                GL11.GL_SRC_ALPHA);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_ALPHA,
                GL13.GL_PRIMARY_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_ALPHA,
                GL11.GL_SRC_ALPHA);
        active = true;
    }

    /** Restores Minecraft's default modulate environment. */
    public static void end() {
        if (!active) {
            return;
        }
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE,
                GL11.GL_MODULATE);
        active = false;
    }

    public static boolean isActive() {
        return active;
    }
}
