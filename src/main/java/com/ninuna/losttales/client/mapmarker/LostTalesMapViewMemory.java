package com.ninuna.losttales.client.mapmarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Where the map was left, so that reopening it hands the player back the view
 * they closed.
 *
 * <p>The map screen is a new object every time it is opened, and everything
 * about how it is being looked at lives on that object: LOTR puts the camera
 * back over the player and the Lost Tales screen starts square and flat. That
 * is right for the first look and wrong for every one after it — a player who
 * closes the map to walk a few paces and opens it again has to find their place
 * on it a second time.</p>
 *
 * <p>Held for as long as the client is connected and no longer. This is a view,
 * not progress: nothing here is sent, stored on disk or shared, and it is
 * dropped on disconnect along with every other client cache, because the map
 * image itself belongs to the world that was left.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesMapViewMemory {
    private static boolean held;
    private static float posX;
    private static float posY;
    private static float zoomExp;
    /**
     * The accumulated drags rather than the angles they bought.
     *
     * <p>The drag is what the gesture continues from, so keeping it is what
     * lets the next turn carry on from where the last one stopped instead of
     * starting again from square at whatever angle the map happens to be
     * drawn at.</p>
     */
    private static float rotationInput;
    private static float leanInput;

    private LostTalesMapViewMemory() {}

    static void remember(float posX, float posY, float zoomExp,
                         float rotationInput, float leanInput) {
        if (Float.isNaN(posX) || Float.isNaN(posY) || Float.isNaN(zoomExp)) {
            return;
        }
        LostTalesMapViewMemory.posX = posX;
        LostTalesMapViewMemory.posY = posY;
        LostTalesMapViewMemory.zoomExp = zoomExp;
        LostTalesMapViewMemory.rotationInput =
                Float.isNaN(rotationInput) ? 0.0F : rotationInput;
        LostTalesMapViewMemory.leanInput =
                Float.isNaN(leanInput) ? 0.0F : leanInput;
        held = true;
    }

    static boolean isHeld() {
        return held;
    }

    static float getPosX() {
        return posX;
    }

    static float getPosY() {
        return posY;
    }

    static float getZoomExp() {
        return zoomExp;
    }

    static float getRotationInput() {
        return rotationInput;
    }

    static float getLeanInput() {
        return leanInput;
    }

    /** Forgets the view, on leaving the world it was a view of. */
    public static void clear() {
        held = false;
        posX = 0.0F;
        posY = 0.0F;
        zoomExp = 0.0F;
        rotationInput = 0.0F;
        leanInput = 0.0F;
    }
}
