package com.ninuna.losttales.client.gui;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.MotionTransition;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiSleepMP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import org.lwjgl.opengl.GL11;

/**
 * The HUD stepping aside while the chat is open: the game's own
 * elements — the crosshair, the boss bar, health, armour, food, air,
 * the hotbar, experience — and the mod's panels fade out as the chat
 * opens and back in once it closes, leaving the world and the chat.
 *
 * <p>Nothing here knows how any element draws. The frame is copied as it
 * stands just before the first of them draws, they all draw as they
 * always do, and the copy is laid back over them at the share of the way
 * the fade has gone, so they fade as one layer whatever colours and blend
 * states each sets for itself. Fully out, they are not drawn at all, and
 * fully in, nothing is copied. The game's debug text, the player list and
 * the chat are never touched, nor is the overlay as a whole, which Forge
 * answers by leaving the screen's projection unset. The bed's chat is not
 * the chat: lying down keeps the HUD and the sleep fade.</p>
 */
public final class LostTalesHudFade {
    /** The game's elements that fade: the HUD the chat takes the place of. */
    private static final Set<ElementType> FADED = EnumSet.of(
            ElementType.CROSSHAIRS, ElementType.BOSSHEALTH,
            ElementType.HEALTH, ElementType.ARMOR, ElementType.FOOD,
            ElementType.HEALTHMOUNT, ElementType.AIR, ElementType.HOTBAR,
            ElementType.EXPERIENCE, ElementType.JUMPBAR);
    /**
     * The elements the game draws after the faded ones, none of them
     * faded: whichever comes first ends the faded layer.
     */
    private static final Set<ElementType> AFTER_FADED = EnumSet.of(
            ElementType.DEBUG, ElementType.TEXT, ElementType.CHAT,
            ElementType.PLAYER_LIST);

    private static final MotionTransition TRANSITION =
            new MotionTransition(MotionIds.HUD_CHAT_HIDE);
    /** How much of the HUD shows this frame: 1 fully, 0 not at all. */
    private static float shown = 1.0F;
    private static int texture = -1;
    private static int textureWidth = -1;
    private static int textureHeight = -1;
    /** Whether a copy of the frame waits to be laid back this frame. */
    private static boolean captured;
    /** Whether copying failed this frame; the fade then falls back to a cut. */
    private static boolean captureFailed;

    private LostTalesHudFade() {}

    /** Whether an element of the game's overlay is one this fades. */
    public static boolean fades(ElementType type) {
        return type != null && FADED.contains(type);
    }

    /** Whether an element ends the faded layer: one drawn after it, never faded. */
    public static boolean endsLayer(ElementType type) {
        return type != null && AFTER_FADED.contains(type);
    }

    /**
     * Whether the chat asks the HUD to step aside: a chat screen is open
     * — the mod's or the game's own — other than the bed's, and the
     * client has not turned this off.
     */
    static boolean chatWantsWorld(Minecraft minecraft) {
        return LostTalesConfig.hideHudWhileChatting && minecraft != null
                && minecraft.currentScreen instanceof GuiChat
                && !(minecraft.currentScreen instanceof GuiSleepMP);
    }

    /**
     * Before an element of the game's overlay draws. The overlay's start
     * advances the fade; a faded element is cancelled once the HUD is
     * gone, and the first of them to draw while it fades copies the
     * frame; the first element after them lays the copy back.
     */
    public static void onPre(RenderGameOverlayEvent.Pre event) {
        if (event == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (event.type == ElementType.ALL) {
            beginFrame(minecraft);
            return;
        }
        if (fades(event.type)) {
            if (isGone()) {
                event.setCanceled(true);
                return;
            }
            if (isFading() && !captured) {
                captured = capture(minecraft);
            }
            if (isFading() && !captured && shown < 0.5F) {
                // Without a copy there is nothing to fade with: the
                // element goes halfway through the fade instead.
                event.setCanceled(true);
            }
        } else if (endsLayer(event.type)) {
            restore(minecraft);
        }
    }

    /**
     * After the whole overlay: a copy no later element laid back — every
     * one of them off, or cancelled — is laid back now, before the mod's
     * panels draw.
     */
    public static void onOverlayEnd() {
        restore(Minecraft.getMinecraft());
    }

    /**
     * Before the mod's own panels draw: false when they should not draw
     * at all, the HUD having stepped aside. While it fades the frame is
     * copied, so {@link #endPanels} fades them as the game's were.
     */
    public static boolean beginPanels(Minecraft minecraft) {
        if (isGone()) {
            return false;
        }
        if (isFading()) {
            captured = capture(minecraft);
            if (!captured) {
                return shown >= 0.5F;
            }
        }
        return true;
    }

    /** After the mod's own panels: their copy, if one was taken, is laid back. */
    public static void endPanels(Minecraft minecraft) {
        restore(minecraft);
    }

    /** Settles on a shown HUD and lets the copy's texture go; on leaving a world. */
    public static void reset() {
        TRANSITION.settle(true);
        shown = 1.0F;
        captured = false;
        captureFailed = false;
        if (texture >= 0) {
            GL11.glDeleteTextures(texture);
        }
        texture = -1;
        textureWidth = -1;
        textureHeight = -1;
    }

    private static boolean isGone() {
        return shown <= 0.0F;
    }

    private static boolean isFading() {
        return shown > 0.0F && shown < 1.0F;
    }

    /**
     * Advances the fade for this frame: out while the chat is open, on
     * the chat's own bar entrance, and back in once it closes. With the
     * chat's animations off it simply cuts.
     */
    private static void beginFrame(Minecraft minecraft) {
        captured = false;
        captureFailed = false;
        TRANSITION.advance(System.nanoTime(), !chatWantsWorld(minecraft));
        shown = TRANSITION.clamped();
    }

    /** Copies the frame as it stands into the fade's texture. */
    private static boolean capture(Minecraft minecraft) {
        if (captureFailed || minecraft == null || minecraft.displayWidth <= 0
                || minecraft.displayHeight <= 0) {
            return false;
        }
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            ensureTexture(minecraft);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0,
                    minecraft.displayWidth, minecraft.displayHeight);
            return true;
        } catch (RuntimeException failure) {
            captureFailed = true;
            return false;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
        }
    }

    /**
     * Lays the copy back over what was drawn since it was taken, at the
     * share of the way the HUD has gone: what was drawn shows at exactly
     * the fade's opacity. The quad covers the overlay's exact fractional
     * size, which is what its projection maps onto the display.
     */
    private static void restore(Minecraft minecraft) {
        if (!captured) {
            return;
        }
        captured = false;
        float opacity = 1.0F - shown;
        if (texture < 0 || minecraft == null || opacity <= 0.0F) {
            return;
        }
        ScaledResolution resolution = new ScaledResolution(minecraft,
                minecraft.displayWidth, minecraft.displayHeight);
        double width = resolution.getScaledWidth_double();
        double height = resolution.getScaledHeight_double();
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, opacity);
            // The copy reads bottom-up; the overlay counts down from the top.
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(0.0D, height, 0.0D, 0.0D, 0.0D);
            tessellator.addVertexWithUV(width, height, 0.0D, 1.0D, 0.0D);
            tessellator.addVertexWithUV(width, 0.0D, 0.0D, 1.0D, 1.0D);
            tessellator.addVertexWithUV(0.0D, 0.0D, 0.0D, 0.0D, 1.0D);
            tessellator.draw();
        } finally {
            GL11.glPopAttrib();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
        }
    }

    /** A texture the display's size, made again when the display is resized. */
    private static void ensureTexture(Minecraft minecraft) {
        if (texture >= 0 && textureWidth == minecraft.displayWidth
                && textureHeight == minecraft.displayHeight) {
            return;
        }
        if (texture >= 0) {
            GL11.glDeleteTextures(texture);
        }
        textureWidth = minecraft.displayWidth;
        textureHeight = minecraft.displayHeight;
        texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
                GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER,
                GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S,
                GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T,
                GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, textureWidth,
                textureHeight, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE,
                (ByteBuffer)null);
    }
}
