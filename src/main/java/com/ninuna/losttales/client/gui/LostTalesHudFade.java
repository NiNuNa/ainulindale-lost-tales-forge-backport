package com.ninuna.losttales.client.gui;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.MotionTransition;
import com.ninuna.losttales.gui.style.LostTalesUiLayerFade;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiSleepMP;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;

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
    private static final LostTalesUiLayerFade LAYER =
            new LostTalesUiLayerFade();
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
        LAYER.release();
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

    /** Copies the frame as it stands. */
    private static boolean capture(Minecraft minecraft) {
        if (captureFailed) {
            return false;
        }
        if (!LAYER.beginDisplay(minecraft)) {
            captureFailed = true;
            return false;
        }
        return true;
    }

    /**
     * Lays the copy back over what was drawn since it was taken, at the
     * share of the way the HUD has gone: what was drawn shows at exactly
     * the fade's strength.
     */
    private static void restore(Minecraft minecraft) {
        if (!captured) {
            return;
        }
        captured = false;
        LAYER.end(minecraft, shown);
    }
}
