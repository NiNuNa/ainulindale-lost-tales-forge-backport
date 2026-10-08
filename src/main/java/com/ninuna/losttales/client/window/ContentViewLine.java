package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.MotionTransition;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiTheme;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.StatCollector;

/**
 * The line at the top of a window whose page fills it
 * ({@link ContentView}), saying how to leave, as a video player says how
 * to leave its full screen: <em>Exit Borderless (Esc)</em>. It shows for a
 * moment as the page comes to fill the window, and again while the
 * pointer is in the window near its top edge or on the line; a click on
 * it gives the window its row, strip and bar back. A popup: it wears the
 * window frame and never moves.
 */
final class ContentViewLine {
    /** Clear rows between the window's top edge and the line. */
    private static final int TOP = 4;
    /** How near the window's top edge the pointer brings the line back. */
    private static final int REACH = 16;

    /** Each window's line: its fade, and where it stood when last drawn. */
    private final Map<String, Line> lines = new HashMap<String, Line>();

    private static final class Line {
        final MotionTransition fade =
                new MotionTransition(MotionIds.WINDOW_VIEW_LINE);
        /** Null while the line is not shown. */
        LostTalesUiHitBox box;
    }

    /**
     * Draws the line of every window whose page fills it, where it is
     * wanted; the one under the pointer, {@code pointedId}, lights.
     */
    void draw(FontRenderer font, double pointerX, double pointerY,
              String pointedId, PointerRegions regions) {
        long now = System.nanoTime();
        Iterator<Map.Entry<String, Line>> gone =
                this.lines.entrySet().iterator();
        while (gone.hasNext()) {
            if (!ContentView.isOn(gone.next().getKey())) {
                gone.remove();
            }
        }
        String words = StatCollector.translateToLocal(
                "gui.losttales.window.view.leave");
        int width = WindowStyle.popupLineWidth(font, words);
        long hold = Math.round(Motions.param(MotionIds.WINDOW_VIEW_LINE,
                "hold", 2500.0F) * 1000000.0D);
        for (WindowFrame frame : WindowFrame.drawnFrames()) {
            if (!ContentView.isOn(frame.windowId)) {
                continue;
            }
            Line line = this.lines.get(frame.windowId);
            if (line == null) {
                line = new Line();
                this.lines.put(frame.windowId, line);
            }
            LostTalesUiHitBox window = frame.drawnBox();
            int left = (int)Math.floor(window.left) + LostTalesUiInk
                    .centredStart((int)Math.floor(window.width), width);
            int top = (int)Math.floor(window.top) + TOP;
            LostTalesUiHitBox at = new LostTalesUiHitBox(left, top, width,
                    WindowStyle.POPUP_LINE_HEIGHT);
            // A window lying over this one's top covers its line too.
            boolean covered = WindowFrame.drawnAt(left + width / 2.0D,
                    top + 1.0D) != frame;
            boolean nearTop = window.contains(pointerX, pointerY)
                    && pointerY < window.top + REACH;
            boolean wanted = now - ContentView.enteredNanos(frame.windowId)
                    < hold || nearTop || at.contains(pointerX, pointerY);
            float opacity = line.fade.advance(now, wanted && !covered);
            if (opacity < LostTalesUiInk.MIN_VISIBLE_ALPHA / 255.0F) {
                line.box = null;
                continue;
            }
            line.box = at;
            WindowStyle.drawPopupLine(font, words,
                    frame.windowId.equals(pointedId)
                            ? LostTalesUiTheme.accentRgb() : LostTalesUiInk.IVORY,
                    left, top, opacity);
            regions.add(left, top, left + width,
                    top + WindowStyle.POPUP_LINE_HEIGHT);
        }
    }

    /** The window whose line is under the point as it was last drawn; null for none. */
    String windowAt(double x, double y) {
        for (Map.Entry<String, Line> entry : this.lines.entrySet()) {
            LostTalesUiHitBox box = entry.getValue().box;
            if (box != null && box.contains(x, y)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
