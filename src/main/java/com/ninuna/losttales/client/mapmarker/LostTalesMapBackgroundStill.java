package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.client.motion.Motions;

/**
 * The map behind the main menu and the loading screens holding still with
 * Animations off or reduced motion (MAP-14): no sway, and the main menu's
 * flyover held where it stood, at the zoom LOTR's menu swings about. The
 * travel screen's pan from where you stand to where you go is the
 * journey, not idle motion, and is left to LOTR.
 */
final class LostTalesMapBackgroundStill {
    /** The zoom LOTR's main menu swings about: where the held map rests. */
    static final float RESTING_ZOOM_EXP = -0.1F;
    /** How far the backgrounds sway either way while motion plays. */
    static final float SWAY_DEGREES = 3.0F;
    /** The sway's pace: one swing every 2π times this many seconds. */
    private static final double SWAY_SECONDS = 24.0D;

    private boolean holding;
    private double heldX;
    private double heldY;

    /** Whether the backgrounds hold still: the Animations switch off, or reduced motion. */
    static boolean stills() {
        return !Motions.flourishes();
    }

    /** The backgrounds' turn {@code seconds} in: their sway, none while they hold still. */
    static float swayDegrees(double seconds, boolean still) {
        return still ? 0.0F
                : (float)Math.sin(seconds / SWAY_SECONDS) * SWAY_DEGREES;
    }

    /**
     * Where the main menu's map stands, {@code x} then {@code y}: where
     * LOTR's flyover is while motion plays, and while it holds still the
     * place the flyover had reached as it began to.
     */
    double[] place(boolean still, double flyoverX, double flyoverY) {
        if (!still) {
            this.holding = false;
            return new double[] {flyoverX, flyoverY};
        }
        if (!this.holding) {
            this.holding = true;
            this.heldX = flyoverX;
            this.heldY = flyoverY;
        }
        return new double[] {this.heldX, this.heldY};
    }
}
