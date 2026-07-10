package com.ninuna.losttales.client.input;

import com.ninuna.losttales.client.input.LostTalesInputBinding.Type;
import com.ninuna.losttales.client.input.LostTalesInputIconAtlas.Sprite;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.opengl.GL11;

/** Generic renderer for key-binding and mouse-wheel input hints. */
@SideOnly(Side.CLIENT)
public final class LostTalesInputIconRenderer {
    public static final int BASE_ICON_HEIGHT = LostTalesInputIconAtlas.SPRITE_HEIGHT;
    private static final int FALLBACK_HORIZONTAL_PADDING = 4;
    private static final int FALLBACK_MIN_WIDTH = 13;

    private static volatile boolean textureStatusKnown;
    private static volatile boolean textureAvailable;

    private LostTalesInputIconRenderer() {}

    /** Reads the binding's current code on every call so changed controls appear immediately. */
    public static int drawKeyBinding(Minecraft minecraft, KeyBinding keyBinding, float x, float y, float scale) {
        int keyCode = keyBinding == null ? Integer.MIN_VALUE : keyBinding.getKeyCode();
        return drawInput(minecraft, LostTalesInputBinding.getType(keyBinding), keyCode, x, y, scale);
    }

    public static int drawMouseWheel(Minecraft minecraft, float x, float y, float scale) {
        return drawInput(minecraft, Type.MOUSE_WHEEL, 0, x, y, scale);
    }

    public static int drawInput(Minecraft minecraft, Type type, int keyCode, float x, float y, float scale) {
        if (minecraft == null || scale <= 0.0F) {
            return 0;
        }

        Sprite sprite = LostTalesInputIconAtlas.findSprite(type, keyCode);
        if (sprite != null && isTextureAvailable(minecraft)) {
            drawSprite(minecraft, sprite, x, y, scale);
            return (int) Math.ceil(sprite.getWidth() * scale);
        }

        String label = LostTalesInputBinding.getFallbackLabel(type, keyCode);
        return drawFallback(minecraft.fontRenderer, label, x, y, scale);
    }

    /** Rechecks the texture after resource-pack reloads without parsing the image. */
    public static void onResourceManagerReload(IResourceManager resourceManager) {
        textureAvailable = resourceExists(resourceManager);
        textureStatusKnown = true;
    }

    private static boolean isTextureAvailable(Minecraft minecraft) {
        if (!textureStatusKnown) {
            onResourceManagerReload(minecraft.getResourceManager());
        }
        return textureAvailable;
    }

    private static boolean resourceExists(IResourceManager resourceManager) {
        if (resourceManager == null) {
            return false;
        }

        InputStream input = null;
        try {
            IResource resource = resourceManager.getResource(LostTalesInputIconAtlas.TEXTURE);
            input = resource == null ? null : resource.getInputStream();
            return input != null;
        } catch (IOException ignored) {
            return false;
        } catch (RuntimeException ignored) {
            return false;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                    // The availability check has already completed.
                }
            }
        }
    }

    private static void drawSprite(Minecraft minecraft, Sprite sprite, float x, float y, float scale) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(x, y, 0.0F);
            GL11.glScalef(scale, scale, 1.0F);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            minecraft.getTextureManager().bindTexture(LostTalesInputIconAtlas.TEXTURE);

            float u0 = (float) sprite.getU() / (float) LostTalesInputIconAtlas.TEXTURE_WIDTH;
            float u1 = (float) (sprite.getU() + sprite.getWidth()) / (float) LostTalesInputIconAtlas.TEXTURE_WIDTH;
            float v0 = (float) sprite.getV() / (float) LostTalesInputIconAtlas.TEXTURE_HEIGHT;
            float v1 = (float) (sprite.getV() + sprite.getHeight()) / (float) LostTalesInputIconAtlas.TEXTURE_HEIGHT;

            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(0.0D, sprite.getHeight(), 0.0D, u0, v1);
            tessellator.addVertexWithUV(sprite.getWidth(), sprite.getHeight(), 0.0D, u1, v1);
            tessellator.addVertexWithUV(sprite.getWidth(), 0.0D, 0.0D, u1, v0);
            tessellator.addVertexWithUV(0.0D, 0.0D, 0.0D, u0, v0);
            tessellator.draw();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static int drawFallback(FontRenderer font, String label, float x, float y, float scale) {
        if (font == null) {
            return 0;
        }
        if (label == null || label.length() == 0) {
            label = "?";
        }

        int width = Math.max(FALLBACK_MIN_WIDTH, font.getStringWidth(label) + FALLBACK_HORIZONTAL_PADDING * 2);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(x, y, 0.0F);
            GL11.glScalef(scale, scale, 1.0F);
            drawRect(0, 0, width, BASE_ICON_HEIGHT, 0xCC080808);
            drawRect(0, 0, width, 1, 0xDDB8B8B8);
            drawRect(0, BASE_ICON_HEIGHT - 1, width, BASE_ICON_HEIGHT, 0xDD303030);
            drawRect(0, 0, 1, BASE_ICON_HEIGHT, 0xDD909090);
            drawRect(width - 1, 0, width, BASE_ICON_HEIGHT, 0xDD303030);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            font.drawStringWithShadow(label, FALLBACK_HORIZONTAL_PADDING, (BASE_ICON_HEIGHT - font.FONT_HEIGHT) / 2, 0xFFFFFF);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
        return (int) Math.ceil(width * scale);
    }

    private static void drawRect(int left, int top, int right, int bottom, int color) {
        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(red, green, blue, alpha);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertex(left, bottom, 0.0D);
        tessellator.addVertex(right, bottom, 0.0D);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.addVertex(left, top, 0.0D);
        tessellator.draw();
    }
}
