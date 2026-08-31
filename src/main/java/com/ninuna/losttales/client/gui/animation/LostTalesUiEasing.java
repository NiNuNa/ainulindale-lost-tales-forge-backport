package com.ninuna.losttales.client.gui.animation;

/**
 * The curves a {@link LostTalesUiTransition} may travel on, as an enum so
 * a caller names one without allocating anything per frame.
 *
 * <p>{@link #LINEAR} is the identity and the one curve a reversal can
 * resume on without a visible kink; the others accelerate or decelerate
 * and are meant for a leg that starts from rest. {@link #BACK_OUT}
 * overshoots its end and returns, so it is only safe where the value
 * drives something that tolerates leaving the 0..1 range — a position,
 * not an array index.</p>
 */
public enum LostTalesUiEasing {
    LINEAR,
    /** Decelerating: fast away, gentle arrival. The default for controls. */
    EASE_OUT,
    /** Symmetric slow-in / slow-out. */
    SMOOTH,
    /** Decelerating with a small overshoot past the end. */
    BACK_OUT,
    /**
     * Slow-in / slow-out with a small hump of follow-through over the
     * middle, bounded to 0..1 — a panel that arrives with a little
     * weight rather than gliding to a stop. What the chat's pickers and
     * menus open on.
     */
    SETTLE;

    /** The curve at {@code progress}, which the caller has already clamped. */
    public float apply(float progress) {
        switch (this) {
            case LINEAR:
                return progress;
            case SMOOTH:
                return LostTalesGuiEasing.smoothStep(progress);
            case BACK_OUT:
                return LostTalesGuiEasing.subtleBackOut(progress);
            case SETTLE:
                return LostTalesGuiEasing.clamp(
                        LostTalesGuiEasing.smoothStep(progress)
                                + (float)Math.sin(progress * Math.PI)
                                        * (1.0F - progress) * 0.08F);
            default:
                return LostTalesGuiEasing.easeOutCubic(progress);
        }
    }
}
