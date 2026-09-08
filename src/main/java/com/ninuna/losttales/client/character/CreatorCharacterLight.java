package com.ninuna.losttales.client.character;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraftforge.client.event.RenderPlayerEvent;

/**
 * A light on the player's own character while the creator is open, so
 * the character can be seen at night or underground.
 *
 * <p>Skyrim's creator has the same switch. Nothing in the world is lit:
 * only the player's body is drawn at full brightness, by giving it the
 * light map's brightest coordinates for the length of its own render and
 * putting the world's back afterwards. Other players, the ground and the
 * held item are drawn as they were.</p>
 *
 * <p>The switch belongs to whichever screen holds it and is dropped when
 * that screen lets go, so it never lights the character during play.</p>
 */
public final class CreatorCharacterLight {

    /** The brightest light map coordinate, on both axes. */
    private static final float FULL_BRIGHT = 240.0F;

    private static Object owner;
    private static boolean lit;
    private static boolean applied;

    private CreatorCharacterLight() {}

    /** The screen the switch belongs to; a screen that is not this one cannot flip it. */
    public static synchronized void bind(Object screen) {
        if (owner != screen) {
            owner = screen;
            lit = false;
        }
    }

    /** The screen lets go, and the light goes out with it. */
    public static synchronized void unbind(Object screen) {
        if (owner == screen) {
            owner = null;
            lit = false;
        }
    }

    public static synchronized boolean isLit() {
        return owner != null && lit;
    }

    public static synchronized void toggle(Object screen) {
        if (owner == screen) {
            lit = !lit;
        }
    }

    /** Before the player is drawn: the brightest light, if it is the local player and the switch is on. */
    public static synchronized void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        applied = false;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!isLit() || event == null || event.isCanceled()
                || minecraft == null || minecraft.currentScreen != owner
                || event.entityPlayer == null
                || event.entityPlayer != minecraft.thePlayer) {
            return;
        }
        OpenGlHelper.setLightmapTextureCoords(
                OpenGlHelper.lightmapTexUnit, FULL_BRIGHT, FULL_BRIGHT);
        applied = true;
    }

    /** After the player is drawn: the world's own light for it, as the render manager set it. */
    public static synchronized void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (!applied || event == null || event.entityPlayer == null) {
            applied = false;
            return;
        }
        applied = false;
        int packed = event.entityPlayer.getBrightnessForRender(
                event.partialRenderTick);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                (float)(packed % 65536), (float)(packed / 65536));
    }
}
