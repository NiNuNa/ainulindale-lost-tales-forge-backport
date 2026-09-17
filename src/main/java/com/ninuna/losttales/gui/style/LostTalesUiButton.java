package com.ninuna.losttales.gui.style;

import org.lwjgl.opengl.GL11;

/**
 * Draws a small icon button in the pose its
 * {@link LostTalesUiButtonMotion} has reached. Every pressable glyph the
 * mod draws comes through here, so a button's feel is written once and
 * no two of them answer the pointer differently.
 *
 * <p>The pose is put on the matrix rather than folded into the drawn
 * coordinates, so a glyph moving between whole pixels keeps its shape
 * instead of stepping across the display grid, and a turn happens about
 * the glyph's own middle.</p>
 */
public final class LostTalesUiButton {
    private LostTalesUiButton() {}

    /**
     * Puts a button's pose on the matrix, turning about the middle of a
     * box {@code width} by {@code height} at {@code left}, {@code top}.
     * Always paired with {@link #endPose()} in a finally block. What is
     * drawn between the two is drawn where it was laid out; the pose
     * carries it.
     *
     * <p>Pass the box of the <em>artwork</em>, not of the button's
     * frame, so a turn pivots on the middle of the sprite itself. The
     * two are the same point wherever the artwork is centred in its
     * frame, which is every case in the chat, but a glyph set off to one
     * side would swing rather than spin.</p>
     */
    public static void beginPose(LostTalesUiButtonMotion motion, float left,
                                 float top, float width, float height) {
        GL11.glPushMatrix();
        GL11.glTranslated(0.0D, motion.offsetY(), 0.0D);
        float turn = motion.turnDegrees();
        if (turn != 0.0F) {
            float pivotX = left + width / 2.0F;
            float pivotY = top + height / 2.0F;
            GL11.glTranslatef(pivotX, pivotY, 0.0F);
            GL11.glRotatef(turn, 0.0F, 0.0F, 1.0F);
            GL11.glTranslatef(-pivotX, -pivotY, 0.0F);
        }
    }

    public static void endPose() {
        GL11.glPopMatrix();
    }

    /**
     * A glyph and its lit artwork in the button's pose: the whole of a
     * plain icon button, in one call.
     */
    public static void drawGlyph(LostTalesUiSheet resting,
                                 LostTalesUiSheet lit,
                                 LostTalesUiButtonMotion motion,
                                 float left, float top, int alpha) {
        beginPose(motion, left, top, resting.getWidth(),
                resting.getHeight());
        try {
            LostTalesUiSheet.drawPairWithShadow(resting, lit, motion.lit(),
                    left, top, alpha);
        } finally {
            endPose();
        }
    }

    /**
     * The same for a glyph whose two states are drawn one over the other
     * as the control turns between them, each at its own strength: the
     * window's fullscreen corners. Both are posed together, so the pair
     * reads as one control rather than two sliding apart.
     */
    public static void drawCrossingGlyphs(LostTalesUiSheet resting,
                                          LostTalesUiSheet lit,
                                          LostTalesUiSheet other,
                                          LostTalesUiSheet otherLit,
                                          LostTalesUiButtonMotion motion,
                                          float share, float left, float top,
                                          int alpha) {
        float inward = Math.max(0.0F, Math.min(1.0F, share));
        beginPose(motion, left, top, resting.getWidth(),
                resting.getHeight());
        try {
            LostTalesUiSheet.drawPairWithShadow(resting, lit, motion.lit(),
                    left, top, Math.round(alpha * (1.0F - inward)));
            LostTalesUiSheet.drawPairWithShadow(other, otherLit,
                    motion.lit(), left, top, Math.round(alpha * inward));
        } finally {
            endPose();
        }
    }
}
