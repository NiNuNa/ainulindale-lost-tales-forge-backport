package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
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
import org.lwjgl.opengl.GL11;

/**
 * Draws a showcased stack's icon at chat scale. Vanilla item rendering is
 * fixed at sixteen pixels, so the icon is scaled on the matrix; eight
 * pixels (a clean 2:1) keeps item sprites crisp inside the eleven-pixel
 * chat band. The shadow pass renders the same icon in silhouette mode, so
 * items share the text and emote shadow colour instead of a tinted copy.
 *
 * <p>The icon fades with its line. {@code RenderItem} resets the vertex
 * colour to full alpha in every branch it has, so the line's opacity is
 * applied through the texture environment instead, which every textured
 * quad it draws passes through. Blocks that vanilla draws as little
 * cubes are the one exception: for them it also turns blending off and
 * raises the alpha test to one half, which would hold the cube opaque
 * and then drop it at half fade, so those are posed exactly as vanilla
 * poses them but drawn here, with blending on.</p>
 */
final class ChatItemRenderer {
    /** On-screen icon edge inside the ten-pixel reserved slot. */
    static final float ICON_SIZE = 8.0F;
    private static final float VANILLA_ICON_SIZE = 16.0F;
    /** Blocks as cubes, without a world; vanilla's own is private. */
    private static final RenderBlocks BLOCK_RENDERER = new RenderBlocks();
    /** The depth RenderItem adds for its effect-capable GUI pass. */
    private static final float EFFECT_PASS_Z = 50.0F;

    private ChatItemRenderer() {}

    static void draw(Minecraft minecraft, ItemStack stack,
                     float x, float y, float size, int alpha) {
        drawInternal(minecraft, stack, x, y, size, alpha, true);
    }

    static void drawShadow(Minecraft minecraft, ItemStack stack,
                           float x, float y, float size,
                           int shadowRgb, int alpha) {
        LostTalesSilhouetteRenderState.begin(shadowRgb);
        try {
            drawInternal(minecraft, stack, x, y, size, alpha, false);
        } finally {
            LostTalesSilhouetteRenderState.end();
        }
    }

    private static void drawInternal(Minecraft minecraft, ItemStack stack,
                                     float x, float y, float size,
                                     int alpha, boolean effects) {
        if (minecraft == null || stack == null || stack.getItem() == null
                || size <= 0.0F || alpha <= 3 || minecraft.fontRenderer == null) {
            return;
        }
        RenderItem renderer = RenderItem.getInstance();
        float scale = size / VANILLA_ICON_SIZE;
        int opacity = Math.min(255, alpha);
        // RenderItem leaves lighting on, the depth function and mask changed,
        // and (for enchanted stacks) the glint's additive blend func behind;
        // any of those garbles the text and head icon drawn after it. Saving
        // and restoring the whole affected state is the only reliable fence.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(x, y, 0.0F);
            GL11.glScalef(scale, scale, 1.0F);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            LostTalesSilhouetteRenderState.beginConstantAlpha(
                    opacity / 255.0F);
            try {
                if (effects && opacity == 255) {
                    // Full strength: vanilla's own pass, glint and any
                    // mod renderer included.
                    renderer.renderItemAndEffectIntoGUI(minecraft.fontRenderer,
                            minecraft.getTextureManager(), stack, 0, 0);
                } else if (effects && hasCustomRenderer(stack)) {
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
