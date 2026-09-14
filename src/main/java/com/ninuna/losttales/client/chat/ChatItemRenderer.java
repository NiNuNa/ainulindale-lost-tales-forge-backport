package com.ninuna.losttales.client.chat;

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
 * Draws a showcased stack's icon at chat scale. Vanilla item rendering is
 * fixed at sixteen pixels, so the icon is scaled on the matrix to the
 * size the caller asks for: in a message line that is the 10px content
 * box ({@link ChatInlineIcons#CONTENT_SIZE}), one clear row above and
 * below it. The shadow pass renders the same icon in silhouette mode, so
 * items share the text and emoji shadow colour instead of a tinted copy.
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
 * the plain screen frame rather than inside the chat's own scales, the
 * icon is scaled alike on every axis — the chat's matrices scale across
 * the screen and not into it, which would tilt a block's faces against
 * the lamps — and every normal is kept at unit length, since a block
 * shrunk to chat size would otherwise catch a fraction of the light.
 * Its shadow writes no depth: a block's faces lean toward the eye, and
 * a copy of it one pixel down and right would stand nearer than the
 * block along its right-hand side and cover it there.</p>
 *
 * <p>In a window cutting holes for its framed buttons, the icon and its
 * shadow take their depth from a slab behind everything the window
 * draws ({@link LostTalesChatOverlayRenderer#squeezeItemDepth}): the
 * icon still sorts its own faces, neither reaches into a hole, and what
 * the window draws after them covers them as it covers the words.</p>
 */
final class ChatItemRenderer {
    private static final float VANILLA_ICON_SIZE = 16.0F;
    /** The matrix read back to match depth to the scale across the screen. */
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    /** Blocks as cubes, without a world; vanilla's own is private. */
    private static final RenderBlocks BLOCK_RENDERER = new RenderBlocks();
    /** The depth RenderItem adds for its effect-capable GUI pass. */
    private static final float EFFECT_PASS_Z = 50.0F;

    private ChatItemRenderer() {}

    static void draw(Minecraft minecraft, ItemStack stack,
                     float x, float y, float size, int alpha) {
        drawInternal(minecraft, stack, x, y, size, alpha, false);
    }

    static void drawShadow(Minecraft minecraft, ItemStack stack,
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
            boolean holes = LostTalesChatOverlayRenderer.squeezeItemDepth();
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
}
