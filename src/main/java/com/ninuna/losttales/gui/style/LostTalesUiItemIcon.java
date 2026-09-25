package com.ninuna.losttales.gui.style;

import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
import java.nio.FloatBuffer;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Draws an item's icon at any size: in a chat line, on a tab, in a
 * picker. Vanilla item rendering is fixed at sixteen pixels, so the icon
 * is scaled on the matrix to the size the caller asks for, and
 * {@link #drawFitted} lays it on whole display pixels per texel. The
 * shadow pass renders the same icon in silhouette mode, so items share
 * the text and emoji shadow colour instead of a tinted copy.
 *
 * <p>The icon fades with its line. {@code RenderItem} resets the vertex
 * colour to full alpha in every branch it has, so the line's opacity is
 * applied through the texture environment instead, which every textured
 * quad it draws passes through. Blocks that vanilla draws as little
 * cubes are the one exception: for them it also turns blending off and
 * raises the alpha test to one half, which would hold the cube opaque
 * and then drop it at half fade, so those are posed exactly as vanilla
 * poses them but drawn here, with blending on.</p>
 *
 * <p>An icon is lit as the hotbar lights it: the two lamps are aimed in
 * the plain screen frame rather than inside the caller's own scales, the
 * icon is scaled alike on every axis — a window's matrices scale across
 * the screen and not into it, which would tilt a block's faces against
 * the lamps — and every normal is kept at unit length, since a block
 * shrunk to icon size would otherwise catch a fraction of the light.
 * Its shadow writes no depth: a block's faces lean toward the eye, and
 * a copy of it one pixel down and right would stand nearer than the
 * block along its right-hand side and cover it there.</p>
 *
 * <p>In a window cutting holes for its framed buttons, the icon and its
 * shadow take their depth from a slab behind everything the window
 * draws ({@link #squeezeDepthInto}): the
 * icon still sorts its own faces, neither reaches into a hole, and what
 * the window draws after them covers them as it covers the words.</p>
 */
public final class LostTalesUiItemIcon {
    private static final float VANILLA_ICON_SIZE = 16.0F;
    /** The matrix read back to match depth to the scale across the screen. */
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    /** Blocks as cubes, without a world; vanilla's own is private. */
    private static final RenderBlocks BLOCK_RENDERER = new RenderBlocks();
    /** The depth RenderItem adds for its effect-capable GUI pass. */
    private static final float EFFECT_PASS_Z = 50.0F;

    private LostTalesUiItemIcon() {}

    public static void draw(Minecraft minecraft, ItemStack stack,
                     float x, float y, float size, int alpha) {
        drawInternal(minecraft, stack, x, y, size, alpha, false);
    }

    public static void drawShadow(Minecraft minecraft, ItemStack stack,
                           float x, float y, float size,
                           int shadowRgb, int alpha) {
        LostTalesSilhouetteRenderState.begin(shadowRgb);
        try {
            drawInternal(minecraft, stack, x, y, size, alpha, true);
        } finally {
            LostTalesSilhouetteRenderState.end();
        }
    }

    private static void drawInternal(Minecraft minecraft, ItemStack stack,
                                     float x, float y, float size,
                                     int alpha, boolean shadow) {
        if (minecraft == null || stack == null || stack.getItem() == null
                || size <= 0.0F || alpha <= 3 || minecraft.fontRenderer == null) {
            return;
        }
        RenderItem renderer = RenderItem.getInstance();
        float scale = size / VANILLA_ICON_SIZE;
        int opacity = Math.min(255, alpha);
        // RenderItem leaves lighting on, the depth function and mask changed,
        // and (for enchanted stacks) the glint's additive blend func behind,
        // and the icon may take a squeezed depth range; any of those garbles
        // the text and head icon drawn after it. Saving and restoring the
        // whole affected state is the only reliable fence.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT
                | GL11.GL_VIEWPORT_BIT);
        GL11.glPushMatrix();
        try {
            // The lamps are aimed in the plain screen frame, as the hotbar
            // aims them: aimed inside the chat's own scales, they would
            // lean with them.
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glPopMatrix();
            GL11.glTranslatef(x, y, 0.0F);
            matchDepthToScaleAcross();
            GL11.glScalef(scale, scale, scale);
            // Every normal back at unit length whatever the scale, or a
            // block shrunk to chat size catches a fraction of the light.
            GL11.glEnable(GL11.GL_NORMALIZE);
            GL11.glEnable(GL11.GL_BLEND);
            boolean holes = squeezeDepth();
            if (shadow) {
                // Nothing of the shadow may stand nearer than the icon it
                // falls from, so it writes no depth; among holes it is
                // still tested against them.
                if (holes) {
                    GL11.glEnable(GL11.GL_DEPTH_TEST);
                    GL11.glDepthMask(false);
                } else {
                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                }
            } else {
                // Tested and written, so a block's own faces sort,
                // whatever the chat around it does with depth.
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(true);
            }
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            LostTalesSilhouetteRenderState.beginConstantAlpha(
                    opacity / 255.0F);
            try {
                if (!shadow && opacity == 255) {
                    // Full strength: vanilla's own pass, glint and any
                    // mod renderer included.
                    renderer.renderItemAndEffectIntoGUI(minecraft.fontRenderer,
                            minecraft.getTextureManager(), stack, 0, 0);
                } else if (!shadow && hasCustomRenderer(stack)) {
                    renderer.renderItemAndEffectIntoGUI(minecraft.fontRenderer,
                            minecraft.getTextureManager(), stack, 0, 0);
                } else if (rendersAsCube(stack)) {
                    drawCube(minecraft, stack, renderer.zLevel + EFFECT_PASS_Z);
                } else {
                    // The glint is additive and keyed on the inverse of its
                    // texture's alpha, so it would brighten as the line
                    // fades; a fading icon (and every shadow) goes without.
                    renderer.renderItemIntoGUI(minecraft.fontRenderer,
                            minecraft.getTextureManager(), stack, 0, 0, false);
                }
            } finally {
                LostTalesSilhouetteRenderState.endConstantAlpha();
            }
        } catch (RuntimeException ignored) {
            // A broken modded item renderer must never take the chat down.
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /**
     * Scales depth by as much as the current matrix scales across the
     * screen, so what follows is scaled alike on every axis: the chat's
     * matrices — its scale, a small row's, the screen's opening — scale
     * across the screen and leave depth alone, and a block drawn in them
     * would have its faces tilted against the lamps.
     */
    private static void matchDepthToScaleAcross() {
        MATRIX.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
        float across = (float)Math.sqrt(MATRIX.get(0) * MATRIX.get(0)
                + MATRIX.get(1) * MATRIX.get(1)
                + MATRIX.get(2) * MATRIX.get(2));
        float depth = (float)Math.sqrt(MATRIX.get(8) * MATRIX.get(8)
                + MATRIX.get(9) * MATRIX.get(9)
                + MATRIX.get(10) * MATRIX.get(10));
        if (across > 0.0F && depth > 0.0F) {
            GL11.glScalef(1.0F, 1.0F, across / depth);
        }
    }

    /** A mod's inventory renderer takes precedence in vanilla; here too. */
    private static boolean hasCustomRenderer(ItemStack stack) {
        return MinecraftForgeClient.getItemRenderer(stack,
                IItemRenderer.ItemRenderType.INVENTORY) != null;
    }

    /** Vanilla's own test for drawing an item block as a cube. */
    private static boolean rendersAsCube(ItemStack stack) {
        if (stack.getItemSpriteNumber() != 0) {
            return false;
        }
        Block block = Block.getBlockFromItem(stack.getItem());
        return block != null
                && RenderBlocks.renderItemIn3d(block.getRenderType());
    }

    /**
     * A block in vanilla's inventory pose — the same translate, scale and
     * rotations {@code RenderItem} uses, so it reads exactly as it does in
     * a slot — with blending on and the alpha test at its translucent
     * level, so the constant alpha fades the whole cube.
     */
    private static void drawCube(Minecraft minecraft, ItemStack stack,
                                 float z) {
        Block block = Block.getBlockFromItem(stack.getItem());
        minecraft.getTextureManager().bindTexture(
                TextureMap.locationBlocksTexture);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        // Only the three facing sides, or the far ones would show through
        // the translucent near ones.
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(-2.0F, 3.0F, -3.0F + z);
            GL11.glScalef(10.0F, 10.0F, 10.0F);
            GL11.glTranslatef(1.0F, 0.5F, 1.0F);
            GL11.glScalef(1.0F, 1.0F, -1.0F);
            GL11.glRotatef(210.0F, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
            int tint = stack.getItem().getColorFromItemStack(stack, 0);
            GL11.glColor4f((tint >> 16 & 255) / 255.0F,
                    (tint >> 8 & 255) / 255.0F, (tint & 255) / 255.0F, 1.0F);
            GL11.glRotatef(-90.0F, 0.0F, 1.0F, 0.0F);
            BLOCK_RENDERER.useInventoryTint = true;
            BLOCK_RENDERER.renderBlockAsItem(block, stack.getItemDamage(),
                    1.0F);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** An item icon's sixteen texels; vanilla's item icon size. */
    public static final int ICON_TEXELS = 16;

    /**
     * How far past its box an item icon may reach on each side, in GUI
     * pixels, to keep every texel whole: the clear rows a message row
     * keeps around the content box and a tab keeps around its icon.
     */
    public static final int ITEM_OVERFLOW = 1;

    /**
     * The whole display pixels per texel an icon of {@code texels} is
     * drawn at in a box {@code box} display pixels wide: the count whose
     * icon is nearest the box in size, a tie going to the smaller, unless
     * that icon would reach past {@code room}, in which case the largest
     * that fits the box; zero when not even one pixel per texel fits the
     * room. Sixteen texels in a ten-pixel box: half size at GUI scale 2,
     * one and a third at 3, eight tenths at 4, close to one at 5.
     */
    public static int wholePixelsPerTexel(double box, double room, int texels) {
        if (texels <= 0 || box <= 0.0D) {
            return 0;
        }
        int lower = (int)Math.floor(box / texels + 1.0E-6D);
        int upper = lower + 1;
        int nearest = upper * texels - box < box - lower * texels
                ? upper : lower;
        if (nearest * texels > room + 1.0E-6D) {
            nearest = lower;
        }
        return Math.max(0, nearest);
    }

    /**
     * An item's icon in its box, crisp and never cut: drawn at the whole
     * display pixels per texel nearest the box's size, a tie going to the
     * smaller, from an origin on the display grid, so its pixel art keeps
     * every texel whole the way the emoji do rather than being squeezed
     * into the box. The nearest size may reach {@link #ITEM_OVERFLOW}
     * past the box on each side, into the clear rows around it; one that
     * would reach further gives way to the largest that fits the box.
     * Only where not even one pixel per texel fits the room — GUI scale
     * 1, sixteen texels in a ten-pixel box — is the icon squeezed to the
     * box as it always was, uneven pixels and all, rather than cut. The
     * faction banner and a channel's chosen item are drawn the same way.
     */
    public static void drawFitted(Minecraft minecraft, ItemStack stack,
                         float boxX, float boxY, float size, int alpha,
                         boolean silhouette) {
        if (minecraft == null || stack == null || size <= 0.0F) {
            return;
        }
        // The matrix the caller draws in only scales and translates:
        // the chat scale over the lines, a fraction of a pixel under the
        // strips and the bar. Its scale and offset give the box's place
        // on the screen, and with the GUI scale, how many display pixels
        // one of the caller's units is.
        FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrix);
        float scaleX = matrix.get(0);
        float scaleY = matrix.get(5);
        float shiftX = matrix.get(12);
        float shiftY = matrix.get(13);
        if (scaleX <= 0.0F || scaleY <= 0.0F) {
            return;
        }
        int factor = LostTalesDisplayPixels.scaleFactor();
        double unit = factor * scaleX;
        int ratio = wholePixelsPerTexel(size * unit,
                (size + 2 * ITEM_OVERFLOW) * unit, ICON_TEXELS);
        // Not one whole pixel per texel fits the room: squeezed to the
        // box, as the box is all the room there is.
        float drawn = ratio <= 0 ? size : (float)(ratio * ICON_TEXELS / unit);
        // Centred in the box, on the display grid, the odd display pixel
        // up and left.
        double originX = LostTalesDisplayPixels.floor(
                scaleX * boxX + shiftX + (size - drawn) / 2.0D * scaleX);
        double originY = LostTalesDisplayPixels.floor(
                scaleY * boxY + shiftY + (size - drawn) / 2.0D * scaleY);
        float x = (float)((originX - shiftX) / scaleX);
        float y = (float)((originY - shiftY) / scaleY);
        if (silhouette) {
            LostTalesUiItemIcon.drawShadow(minecraft, stack, x, y, drawn,
                    LostTalesUiInk.SHADOW, alpha);
        } else {
            LostTalesUiItemIcon.draw(minecraft, stack, x, y, drawn, alpha);
        }
    }

    public static void drawFitted(Minecraft minecraft, ItemStack stack,
                         float boxX, float boxY, float size, int alpha) {
        int shadow = LostTalesUiInk.shadowAlpha(alpha);
        if (shadow > 0) {
            drawFitted(minecraft, stack, boxX + LostTalesUiInk.SHADOW_OFFSET,
                    boxY + LostTalesUiInk.SHADOW_OFFSET, size, shadow,
                    true);
        }
        drawFitted(minecraft, stack, boxX, boxY, size, alpha, false);
    }

    /**
     * The slab of the depth buffer an item icon's own depth is squeezed
     * into, or NaN while none is asked for ({@link #squeezeDepthInto}).
     */
    private static double slabNear = Double.NaN;
    private static double slabFar = Double.NaN;

    /**
     * From here on an item icon's depth is squeezed into the slab from
     * {@code near} to {@code far}: what a window cutting holes for its
     * floating controls asks for, so an icon still sorts its own faces,
     * stays out of every hole as the words beside it do, and leaves a
     * depth that everything drawn after it passes.
     */
    public static void squeezeDepthInto(double near, double far) {
        slabNear = near;
        slabFar = far;
    }

    /** Item icons draw at their own depth again. */
    public static void releaseDepth() {
        slabNear = Double.NaN;
        slabFar = Double.NaN;
    }

    /**
     * Puts the slab in force, if one is asked for, and answers whether it
     * is. The caller saves and restores the depth range
     * ({@code GL_VIEWPORT_BIT}).
     */
    private static boolean squeezeDepth() {
        if (Double.isNaN(slabNear)) {
            return false;
        }
        GL11.glDepthRange(slabNear, slabFar);
        return true;
    }
}
