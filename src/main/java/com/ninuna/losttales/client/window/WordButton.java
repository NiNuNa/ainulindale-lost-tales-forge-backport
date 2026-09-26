package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.input.Mouse;

/**
 * A framed button holding a word, centred, as a sub-window's foot carries
 * them: Cancel, and the action. An action that ends something wears red
 * until it is lit; one that cannot be taken now stands in the aside tone
 * and never lights.
 */
public final class WordButton {
    private WordButton() {}

    /** How wide a button holding {@code label} is. */
    public static int width(FontRenderer font, String label) {
        return 2 * LostTalesUiFramedButton.WIDE_INSET
                + Math.max(0, font.getStringWidth(label) - 1);
    }

    public static void draw(FontRenderer font, LostTalesUiHitBox at,
                            String label, boolean ending, boolean enabled,
                            boolean hovered, LostTalesUiButtonMotion motion,
                            int alpha, int surfaceAlpha) {
        boolean lights = enabled && hovered;
        motion.advance(System.nanoTime(), lights, lights,
                lights && Mouse.isButtonDown(0));
        float lit = motion.lit();
        LostTalesUiFramedButton.drawSurface((float)at.left, (float)at.top,
                (float)at.width, (float)at.height, lit, surfaceAlpha);
        int width = Math.max(0, font.getStringWidth(label) - 1);
        int textX = (int)at.left + LostTalesUiFramedButton.WIDE_INSET;
        int textY = (int)at.top + (LostTalesUiFramedButton.HEIGHT
                - WindowStyle.LINE_HEIGHT) / 2 + WindowStyle.ROW_TEXT_TOP;
        int rgb = !enabled ? WindowStyle.asideRgb()
                : ending ? LostTalesUiInk.blend(LostTalesColors.rgb(
                        LostTalesColors.RED), LostTalesUiInk.IVORY, lit)
                : LostTalesUiInk.IVORY;
        LostTalesUiButton.beginPose(motion, textX, (float)at.top, width,
                (float)at.height);
        try {
            LostTalesUiInk.drawText(font, label, textX, textY, rgb, alpha);
        } finally {
            LostTalesUiButton.endPose();
        }
        LostTalesUiFramedButton.drawInk((float)at.left, (float)at.top,
                (int)at.width, (int)at.height, lit, alpha);
    }
}
