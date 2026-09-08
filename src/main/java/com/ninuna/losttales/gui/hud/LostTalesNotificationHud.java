package com.ninuna.losttales.gui.hud;

import com.ninuna.losttales.config.LostTalesConfig;

/**
 * The one place on the screen where passing notices appear: quest
 * banners, location discoveries, area names, and the character room's
 * journey line. One slot, placed once in the HUD editor, rather than a
 * position per kind of notice.
 *
 * <p>Within a frame each notice claims a strip of the slot, top down, in
 * the order it is drawn, so two notices shown at once stack under each
 * other instead of landing on the same pixels. The frame is begun once
 * before the HUD is drawn; a renderer that forgets to claim draws over
 * whatever else is there, which is what claiming exists to avoid.</p>
 *
 * <p>The slot's box is the footprint the editor shows and the anchor the
 * offsets place: wide enough for the widest notice, tall enough for a
 * discovery and one banner. More than that spills below it.</p>
 */
public final class LostTalesNotificationHud {

    /** The widest notice, the quest banner. */
    public static final int WIDTH = 272;
    /** A discovery notice, a gap and one quest banner. */
    public static final int HEIGHT = 72;
    /** Between stacked notices. */
    public static final int GAP = 4;

    /** Pixels of the slot claimed so far this frame. */
    private static int claimed;

    private LostTalesNotificationHud() {}

    public static int getPlacementWidth() {
        return WIDTH;
    }

    public static int getPlacementHeight() {
        return HEIGHT;
    }

    /** The HUD is about to be drawn; nothing has claimed the slot yet. */
    public static void beginFrame() {
        claimed = 0;
    }

    /** Where the slot stands on a screen of that size. */
    public static HudPlacementLayout.Bounds placement(int screenWidth, int screenHeight) {
        return HudPlacementLayout.calculate(screenWidth, screenHeight,
                WIDTH, HEIGHT,
                LostTalesConfig.notificationHudOffsetX,
                LostTalesConfig.notificationHudOffsetY,
                HudPlacementLayout.CoordinateMode.AVAILABLE_SPACE_PERCENT,
                HudPlacementLayout.CoordinateMode.AVAILABLE_SPACE_PERCENT);
    }

    /** The column notices are centred on. */
    public static int centerX(int screenWidth, int screenHeight) {
        HudPlacementLayout.Bounds bounds = placement(screenWidth, screenHeight);
        return bounds.x + bounds.width / 2;
    }

    /**
     * Claims the next strip of the slot for a notice that tall and
     * answers the strip's top. The next claim this frame lands under it.
     */
    public static int claim(int screenWidth, int screenHeight, int height) {
        HudPlacementLayout.Bounds bounds = placement(screenWidth, screenHeight);
        int top = stripTop(bounds.y, claimed);
        claimed += Math.max(0, height) + GAP;
        return top;
    }

    /** Where a strip starts once that many pixels of the slot are taken. */
    static int stripTop(int slotTop, int claimedBefore) {
        return slotTop + claimedBefore;
    }

    /** Pixels claimed so far this frame; what the next strip starts under. */
    static int claimedThisFrame() {
        return claimed;
    }
}
