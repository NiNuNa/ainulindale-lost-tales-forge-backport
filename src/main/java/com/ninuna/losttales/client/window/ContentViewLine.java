package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.MotionTransition;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.StatCollector;

/**
 * The line at the top of the screen while a tab stands alone
 * ({@link ContentView}), saying how to leave, as a browser says it of its
 * full screen: <em>Leave Full Screen (Esc)</em>. It shows for a moment as
 * the tab comes to stand alone, and again while the pointer is near the
 * screen's top edge or on it; a click on it puts the tab back into its
 * window. A popup: it wears the window frame and never moves.
 */
final class ContentViewLine {
    /** Clear rows between the screen's top edge and the line. */
    private static final int TOP = 4;
    /** How near the top edge the pointer brings the line back. */
    private static final int REACH = 16;

    private final MotionTransition fade =
            new MotionTransition(MotionIds.WINDOW_VIEW_LINE);
    /** Where the line stood when last drawn; null while it is not shown. */
    private LostTalesUiHitBox box;

    /** Draws the line where it is wanted; {@code pointed} lights its words. */
    void draw(FontRenderer font, int screenWidth, double pointerX,
              double pointerY, boolean pointed, PointerRegions regions) {
        long now = System.nanoTime();
        if (!ContentView.isOn()) {
            this.fade.settle(false);
            this.box = null;
            return;
        }
        String words = StatCollector.translateToLocal(
                "gui.losttales.window.view.leave");
        int width = WindowStyle.popupLineWidth(font, words);
        int left = (screenWidth - width) / 2;
        LostTalesUiHitBox at = new LostTalesUiHitBox(left, TOP, width,
                WindowStyle.POPUP_LINE_HEIGHT);
        long hold = Math.round(Motions.param(MotionIds.WINDOW_VIEW_LINE,
                "hold", 2500.0F) * 1000000.0D);
        boolean wanted = now - ContentView.enteredNanos() < hold
                || pointerY < REACH || at.contains(pointerX, pointerY);
        float opacity = this.fade.advance(now, wanted);
        if (opacity < LostTalesUiInk.MIN_VISIBLE_ALPHA / 255.0F) {
            this.box = null;
            return;
        }
        this.box = at;
        WindowStyle.drawPopupLine(font, words, pointed
                ? WindowStyle.LANDING_RGB : LostTalesUiInk.IVORY, left, TOP,
                opacity);
        regions.add(left, TOP, left + width,
                TOP + WindowStyle.POPUP_LINE_HEIGHT);
    }

    /** Whether the point is on the line as it was last drawn. */
    boolean contains(double x, double y) {
        return this.box != null && this.box.contains(x, y);
    }
}
