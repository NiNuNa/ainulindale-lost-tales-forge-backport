package com.ninuna.losttales.client.mapmarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * A soft oval shade around the edge of the map.
 *
 * <p>An ellipse rather than four edges: the darkness is a function of how far
 * a point is from the middle, measured in each direction as a share of the
 * way to that side, so it reaches the corners well before the middles of the
 * sides and comes in as a smooth oval on any shape of screen. It is drawn as
 * a mesh fine enough that the hardware's own interpolation does the
 * smoothing, which costs a few hundred quads and no texture.</p>
 *
 * <p>Screen space, and deliberately so: the map turns and leans underneath
 * it, and a vignette that turned with it would read as a shadow painted on
 * the paper rather than as the edge of what the player can see.</p>
 */
@SideOnly(Side.CLIENT)
final class LostTalesLotrMapVignette {
    /** Darkness once the shade has reached its fullest. */
    static final float EDGE_ALPHA = 0.46F;
    /**
     * How far out the shade starts, as a share of the way to the edge.
     *
     * <p>Kept early, and well short of the sides, so the extra weight is
     * spread over a long falloff rather than arriving as a rim. The eased
     * curve between here and {@link #OUTER_RADIUS} is what keeps a stronger
     * shade from showing an edge.</p>
     */
    private static final float INNER_RADIUS = 0.42F;
    /**
     * Where it reaches full darkness. Past the middle of a side — which is at
     * one — so the sides stay clear and the corners carry the weight, which
     * is what makes it read as an oval rather than as a frame.
     */
    private static final float OUTER_RADIUS = 1.34F;
    /** Mesh resolution. Enough that no facet of it can be picked out. */
    private static final int COLUMNS = 32;
    private static final int ROWS = 20;

    private LostTalesLotrMapVignette() {
    }

    /**
     * How dark the shade is at a point, given as a share of the way from the
     * middle of the map to its edge along each axis.
     */
    static float shadeAt(float offsetX, float offsetY) {
        float radius = (float)Math.sqrt(
                offsetX * offsetX + offsetY * offsetY);
        if (radius <= INNER_RADIUS) {
            return 0.0F;
        }
        if (radius >= OUTER_RADIUS) {
            return EDGE_ALPHA;
        }
        float advanced = (radius - INNER_RADIUS)
                / (OUTER_RADIUS - INNER_RADIUS);
        return EDGE_ALPHA * advanced * advanced * (3.0F - 2.0F * advanced);
    }

    /**
     * @param bottomInset height at the bottom the strip has taken, which the
     *                    oval is measured against so its lower edge sits on
     *                    the strip rather than part way up the map
     */
    static void render(int width, int height, int bottomInset) {
        float bottom = height - Math.max(0, bottomInset);
        if (width <= 0 || bottom <= 0.0F) {
            return;
        }
        float halfWidth = width / 2.0F;
        float halfHeight = bottom / 2.0F;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LIGHTING_BIT);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GL11.glShadeModel(GL11.GL_SMOOTH);

            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            for (int column = 0; column < COLUMNS; column++) {
                float x0 = width * column / (float)COLUMNS;
                float x1 = width * (column + 1) / (float)COLUMNS;
                for (int row = 0; row < ROWS; row++) {
                    float y0 = bottom * row / (float)ROWS;
                    float y1 = bottom * (row + 1) / (float)ROWS;
                    corner(tessellator, x0, y1, halfWidth, halfHeight);
                    corner(tessellator, x1, y1, halfWidth, halfHeight);
                    corner(tessellator, x1, y0, halfWidth, halfHeight);
                    corner(tessellator, x0, y0, halfWidth, halfHeight);
                }
            }
            tessellator.draw();
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopAttrib();
        }
    }

    private static void corner(Tessellator tessellator, float x, float y,
                               float halfWidth, float halfHeight) {
        float alpha = shadeAt(
                (x - halfWidth) / halfWidth, (y - halfHeight) / halfHeight);
        tessellator.setColorRGBA_F(0.0F, 0.0F, 0.0F, alpha);
        tessellator.addVertex(x, y, 0.0D);
    }
}
