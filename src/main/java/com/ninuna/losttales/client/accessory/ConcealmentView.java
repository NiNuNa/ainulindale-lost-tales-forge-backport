package com.ninuna.losttales.client.accessory;

/**
 * How a player is drawn for the one looking (ACC-02): a player not
 * concealed as ever; a concealed one not at all, except for the wearer,
 * who sees a faint shade of their own body. Where the graphics card cannot
 * blend the shade, the wearer is hidden from themselves too.
 */
public enum ConcealmentView {
    SHOWN,
    SHADE,
    HIDDEN;

    /** How strongly the wearer's shade stands: faint, the world plain through it. */
    public static final float SHADE_ALPHA = 0.25F;

    public static ConcealmentView of(boolean concealed, boolean viewerIsWearer,
                                     boolean shadeDrawable) {
        if (!concealed) {
            return SHOWN;
        }
        return viewerIsWearer && shadeDrawable ? SHADE : HIDDEN;
    }
}
