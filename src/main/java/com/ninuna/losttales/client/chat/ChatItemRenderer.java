package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

/**
 * Draws a showcased stack's icon at chat scale. Vanilla item rendering is
 * fixed at sixteen pixels, so the icon is scaled on the matrix; eight
 * pixels (a clean 2:1) keeps item sprites crisp inside the eleven-pixel
 * chat band. The shadow pass renders the same icon in silhouette mode, so
 * items share the text and emote shadow colour instead of a tinted copy.
 */
final class ChatItemRenderer {
    /** On-screen icon edge inside the ten-pixel reserved slot. */
    static final float ICON_SIZE = 8.0F;
    private static final float VANILLA_ICON_SIZE = 16.0F;

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
            GL11.glColor4f(1.0F, 1.0F, 1.0F, alpha / 255.0F);
            if (effects) {
                renderer.renderItemAndEffectIntoGUI(minecraft.fontRenderer,
                        minecraft.getTextureManager(), stack, 0, 0);
            } else {
                // The glint pass would also be silhouetted; shadows only
                // need the base icon.
                renderer.renderItemIntoGUI(minecraft.fontRenderer,
                        minecraft.getTextureManager(), stack, 0, 0, false);
            }
        } catch (RuntimeException ignored) {
            // A broken modded item renderer must never take the chat down.
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
