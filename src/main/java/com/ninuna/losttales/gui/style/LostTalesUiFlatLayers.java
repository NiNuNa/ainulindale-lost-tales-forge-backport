package com.ninuna.losttales.gui.style;

import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * Overlapping layers drawn translucent as one flat picture. A head is a
 * face with a hat over it, a shadow under both and a status sphere in its
 * corner; drawn at half opacity one layer after another, every layer
 * shows through the one above it — the face through the hat, the shadow
 * through the face. Drawn through here, each pixel is painted by the
 * topmost layer that covers it and by nothing under it, so the picture
 * fades as one, the way a picture put together first and faded after
 * would.
 *
 * <p>Two passes over the same layers. The first writes depth alone, each
 * layer standing a hair nearer than the one before it
 * ({@link #nextLayer}); what it leaves on every pixel is the depth of the
 * topmost layer there. The second draws colour only where a fragment's
 * depth is exactly that one. A layer's depth comes from a polygon offset,
 * not from the matrix, so a layer a caller draws inside a push of its own
 * keeps its place. The topmost layer still stands behind the GUI's own
 * depth, and every layer in front of anything drawn far behind it: what
 * is drawn afterwards over the picture passes, and a depth mask the
 * caller stands in — the chat's holes — keeps working on the picture as
 * on anything else.</p>
 *
 * <p>A picture of opaque layers at full opacity needs none of this and is
 * drawn once, as is ({@link #draw}). One whose own layers are translucent
 * in places — a sprite's painted hollows over its shadow — would show the
 * layer under them even then, and is drawn flat whatever its opacity
 * ({@link #drawFlat}). Nested pictures are one picture: a picture drawn
 * while another is being drawn simply adds its layers to it.</p>
 */
public final class LostTalesUiFlatLayers {
    /** Draws a picture's layers, bottom first, calling {@link #nextLayer} between them. */
    public interface Layers {
        void draw();
    }

    /**
     * Layers one picture may hold with a depth of their own; further ones
     * share the last. A member's row with a cut name and title, each
     * fading in one-pixel slices, takes about forty.
     */
    private static final int MAX_LAYERS = 128;
    /**
     * Polygon-offset units between two layers: one of the depth buffer's
     * own steps, far less than the half GUI unit the chat keeps between
     * the layers of a window.
     */
    private static final float LAYER_UNITS = 1.0F;
    /** Where a picture's footprint is reset to before it is drawn: far behind the GUI. */
    private static final float RESET_DEPTH = -500.0F;
    /** Room past the footprint the reset covers, for shadows and hats standing out. */
    private static final float RESET_MARGIN = 2.0F;

    private static boolean active;
    private static int layer;

    private LostTalesUiFlatLayers() {}

    /**
     * Draws {@code layers} as one picture at {@code alpha} (0–255), the
     * opacity the layers themselves are drawn with. {@code left},
     * {@code top}, {@code right} and {@code bottom} bound everything the
     * layers draw, in the current matrix's units.
     */
    public static void draw(int alpha, float left, float top, float right,
                            float bottom, Layers layers) {
        if (layers == null) {
            return;
        }
        if (active || alpha >= 255) {
            layers.draw();
            return;
        }
        drawFlat(left, top, right, bottom, layers);
    }

    /**
     * Draws {@code layers} as one flat picture whatever its opacity, for
     * layers translucent in places of their own; bounded as for
     * {@link #draw}.
     */
    public static void drawFlat(float left, float top, float right,
                                float bottom, Layers layers) {
        if (layers == null) {
            return;
        }
        if (active) {
            layers.draw();
            return;
        }
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrites = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        int alphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        float alphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        active = true;
        try {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            if (!depthTest) {
                // Nothing stands in the depth buffer that this picture
                // should respect, but something may have been left there
                // — another picture drawn over the same pixels.
                resetFootprint(left - RESET_MARGIN, top - RESET_MARGIN,
                        right + RESET_MARGIN, bottom + RESET_MARGIN);
            }
            // A fully clear texel is not a layer; every other one is.
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.0F);
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glColorMask(false, false, false, false);
            GL11.glDepthMask(true);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            pass(layers);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(false);
            GL11.glDepthFunc(GL11.GL_EQUAL);
            pass(layers);
        } finally {
            active = false;
            GL11.glPolygonOffset(0.0F, 0.0F);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(depthWrites);
            GL11.glDepthFunc(depthFunc);
            if (!depthTest) {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }
            GL11.glAlphaFunc(alphaFunc, alphaRef);
            if (!alphaTest) {
                GL11.glDisable(GL11.GL_ALPHA_TEST);
            }
        }
    }

    /**
     * Between two layers of a picture being drawn flat: whatever is drawn
     * next stands in front of what was drawn before. Nothing outside a
     * picture, so any drawing that layers itself can say where its layers
     * part.
     */
    public static void nextLayer() {
        if (!active) {
            return;
        }
        layer = Math.min(MAX_LAYERS - 1, layer + 1);
        applyLayer();
    }

    /** Whether a picture is being drawn flat right now. */
    public static boolean isActive() {
        return active;
    }

    private static void pass(Layers layers) {
        layer = 0;
        applyLayer();
        layers.draw();
    }

    /**
     * The polygon offset of the layer being drawn: the bottom layer the
     * furthest behind, each one after it a step nearer, the last of them
     * still behind the GUI's own depth.
     */
    private static void applyLayer() {
        GL11.glPolygonOffset(0.0F, (MAX_LAYERS - layer) * LAYER_UNITS);
    }

    /** Lays the footprint far behind the GUI's depth, writing no colour. */
    private static void resetFootprint(float left, float top, float right,
                                       float bottom) {
        GL11.glColorMask(false, false, false, false);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_ALWAYS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertex(left, bottom, RESET_DEPTH);
        tessellator.addVertex(right, bottom, RESET_DEPTH);
        tessellator.addVertex(right, top, RESET_DEPTH);
        tessellator.addVertex(left, top, RESET_DEPTH);
        tessellator.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
