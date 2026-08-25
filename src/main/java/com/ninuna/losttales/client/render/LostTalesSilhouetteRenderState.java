package com.ninuna.losttales.client.render;

import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Two texture-environment overrides for textured GUI quads, each
 * independent of the vertex colour so helpers that set their own
 * {@code glColor} (item rendering, head tinting) still honour them:
 *
 * <ul>
 * <li><b>Silhouette</b> ({@link #begin(int)}): every textured fragment
 * takes its RGB from one constant colour and only its alpha from the
 * texture. This is how emojis, head icons, and item icons get the same
 * solid drop shadow as text. Plain tinting cannot do this — multiplying
 * a sprite's own dark outline by the shadow colour yields near-black,
 * which is exactly the inconsistency this replaces.</li>
 * <li><b>Constant alpha</b> ({@link #beginConstantAlpha(float)}): every
 * textured fragment's alpha is the texture's times one constant opacity
 * instead of times the vertex alpha. Vanilla's {@code RenderItem} resets
 * the vertex colour to full alpha in every branch it has, so a fading
 * chat line's shared item would stay opaque without this.</li>
 * </ul>
 *
 * <p>The two nest in either order; while both are active the silhouette
 * supplies the RGB and the constant the alpha. Requires OpenGL 1.3
 * texture combiners, which Minecraft 1.7.10 already relies on.</p>
 */
public final class LostTalesSilhouetteRenderState {
    private static final FloatBuffer CONSTANT_COLOR =
            BufferUtils.createFloatBuffer(4);
    private static boolean active;
    private static int silhouetteRgb = 0xFFFFFF;
    private static boolean alphaActive;
    private static float constantAlpha = 1.0F;

    private LostTalesSilhouetteRenderState() {}

    /** Enters silhouette mode with the given opaque RGB; nestable no-op. */
    public static void begin(int rgb) {
        silhouetteRgb = rgb & 0xFFFFFF;
        active = true;
        apply();
    }

    /** Leaves silhouette mode; the constant alpha, if any, stays. */
    public static void end() {
        if (!active) {
            return;
        }
        active = false;
        apply();
    }

    public static boolean isActive() {
        return active;
    }

    /**
     * Makes every textured fragment's alpha the texture's times
     * {@code opacity} (0–1), whatever the vertex alpha; nestable no-op.
     */
    public static void beginConstantAlpha(float opacity) {
        constantAlpha = Math.max(0.0F, Math.min(1.0F, opacity));
        alphaActive = true;
        apply();
    }

    /** Restores the vertex alpha as the alpha source; the silhouette, if any, stays. */
    public static void endConstantAlpha() {
        if (!alphaActive) {
            return;
        }
        alphaActive = false;
        apply();
    }

    public static boolean isConstantAlphaActive() {
        return alphaActive;
    }

    /**
     * Writes the whole combiner from the current flags, or Minecraft's
     * default modulate environment when neither override is on. Written
     * in full each time so the two overrides never depend on the order
     * they were entered or left in.
     */
    private static void apply() {
        if (!active && !alphaActive) {
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE,
                    GL11.GL_MODULATE);
            return;
        }
        int rgb = active ? silhouetteRgb : 0xFFFFFF;
        CONSTANT_COLOR.clear();
        CONSTANT_COLOR.put(((rgb >> 16) & 0xFF) / 255.0F)
                .put(((rgb >> 8) & 0xFF) / 255.0F)
                .put((rgb & 0xFF) / 255.0F)
                .put(alphaActive ? constantAlpha : 1.0F);
        CONSTANT_COLOR.flip();
        GL11.glTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR,
                CONSTANT_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE,
                GL13.GL_COMBINE);
        if (active) {
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB,
                    GL11.GL_REPLACE);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB,
                    GL13.GL_CONSTANT);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB,
                    GL11.GL_SRC_COLOR);
        } else {
            // Texture times vertex colour: what GL_MODULATE does.
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB,
                    GL11.GL_MODULATE);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB,
                    GL11.GL_TEXTURE);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB,
                    GL11.GL_SRC_COLOR);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_RGB,
                    GL13.GL_PRIMARY_COLOR);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_RGB,
                    GL11.GL_SRC_COLOR);
        }
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA,
                GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA,
                GL11.GL_TEXTURE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA,
                GL11.GL_SRC_ALPHA);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_ALPHA,
                alphaActive ? GL13.GL_CONSTANT : GL13.GL_PRIMARY_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_ALPHA,
                GL11.GL_SRC_ALPHA);
    }
}
