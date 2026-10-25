package com.ninuna.losttales.client.mapmarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Where one map was left: its place, zoom, turn and lean, so that opening
 * it again hands the player back the view they put away (MAP-12).
 *
 * <p>Each copy of the map page keeps one of its own
 * ({@link LostTalesMapPage}), which goes with the copy's content: a copy
 * opened anew, and every copy as the player leaves the world. The map on
 * a screen of its own, where it cannot stand in a window, keeps
 * {@link #screen()}, which is forgotten on disconnect.</p>
 *
 * <p>The map screen is a new object every time it is opened: LOTR puts
 * the camera back over the player and the map starts square and flat,
 * which is right for the first look only. Nothing here is sent, saved
 * or shared.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesMapViewMemory {
    /** The view of the map on a screen of its own, which opens once at a time. */
    private static final LostTalesMapViewMemory SCREEN =
            new LostTalesMapViewMemory();

    private boolean held;
    private float posX;
    private float posY;
    private float zoomExp;
    /**
     * The accumulated drags rather than the angles they bought: the drag
     * is what the gesture carries on from, so the next turn goes on from
     * where the last one stopped.
     */
    private float rotationInput;
    private float leanInput;

    /** The view of the map on a screen of its own. */
    static LostTalesMapViewMemory screen() {
        return SCREEN;
    }

    void remember(float posX, float posY, float zoomExp,
                  float rotationInput, float leanInput) {
        if (Float.isNaN(posX) || Float.isNaN(posY) || Float.isNaN(zoomExp)) {
            return;
        }
        this.posX = posX;
        this.posY = posY;
        this.zoomExp = zoomExp;
        this.rotationInput = Float.isNaN(rotationInput) ? 0.0F : rotationInput;
        this.leanInput = Float.isNaN(leanInput) ? 0.0F : leanInput;
        this.held = true;
    }

    boolean isHeld() {
        return this.held;
    }

    float getPosX() {
        return this.posX;
    }

    float getPosY() {
        return this.posY;
    }

    float getZoomExp() {
        return this.zoomExp;
    }

    float getRotationInput() {
        return this.rotationInput;
    }

    float getLeanInput() {
        return this.leanInput;
    }

    /** Forgets the view: the next opening centres on the player. */
    void forget() {
        this.held = false;
        this.posX = 0.0F;
        this.posY = 0.0F;
        this.zoomExp = 0.0F;
        this.rotationInput = 0.0F;
        this.leanInput = 0.0F;
    }

    /**
     * Forgets the screen's view, on leaving the world it was a view of.
     * The page copies' views go with their contents
     * ({@code WindowSession.clear}).
     */
    public static void clear() {
        SCREEN.forget();
    }
}
