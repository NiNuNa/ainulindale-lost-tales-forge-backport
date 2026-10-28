package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.lwjgl.input.Mouse;

/**
 * The dent at the top of a window whose page fills it ({@link
 * Borderless}): the selected tab's shape turned upside down, hanging from
 * the middle of the window's top edge for as long as the page fills it.
 * It holds the exit alone, which gives the window its row, strip and bar
 * back, as Escape does (WIN-42).
 */
final class BorderlessDent {
    /** What a point on a dent is. */
    enum Part { EXIT }

    private static final int EDGE = WindowStyle.EDGE_GAP;
    private static final int EXIT_WIDTH =
            LostTalesUiSheet.FULLSCREEN_EXIT.getWidth();
    private static final int EXIT_HEIGHT =
            LostTalesUiSheet.FULLSCREEN_EXIT.getHeight();
    /** Clear pixels round a control that answer with it, as the tab row's end controls do. */
    private static final int SLACK = 2;
    /** How wide a dent stands: its border pieces and the exit, the spacing rule's gap either side. */
    static final int WIDTH = TabRow.DENT_BORDER + EDGE + EXIT_WIDTH + EDGE
            + TabRow.DENT_BORDER;

    /** One window's dent: its exit's motion, and where it stood when last drawn. */
    private static final class Dent {
        final LostTalesUiButtonMotion exitMotion =
                new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
        LostTalesUiHitBox box;
        LostTalesUiHitBox exit;
    }

    /** Each window's dent, by its id. */
    private final Map<String, Dent> dents = new HashMap<String, Dent>();

    private Dent dentOf(String windowId) {
        Dent dent = this.dents.get(windowId);
        if (dent == null) {
            dent = new Dent();
            this.dents.put(windowId, dent);
        }
        return dent;
    }

    /** Forgets the dents of windows whose page no longer fills them. */
    void beginFrame() {
        Iterator<Map.Entry<String, Dent>> each = this.dents.entrySet().iterator();
        while (each.hasNext()) {
            Map.Entry<String, Dent> entry = each.next();
            if (!Borderless.isOn(WindowLayout.window(entry.getKey()))) {
                each.remove();
            } else {
                entry.getValue().box = null;
            }
        }
    }

    /**
     * Draws the dent of {@code window}, whose page fills it, hanging from
     * the top of the box {@code frame} was drawn in; {@code under} is the
     * part the pointer is on, if it is on this dent. It comes in as the
     * window's row slides out ({@code share}, 0 to 1).
     */
    void draw(WindowFrame frame, Window window, Part under, float share,
              PointerRegions regions) {
        int alpha = Math.round(255.0F * share);
        Dent dent = dentOf(window.getId());
        if (alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            dent.box = null;
            return;
        }
        LostTalesUiHitBox drawn = frame.drawnBox();
        int left = (int)Math.floor(drawn.left)
                + LostTalesUiInk.centredStart((int)Math.floor(drawn.width), WIDTH);
        int top = (int)Math.floor(drawn.top);
        dent.box = new LostTalesUiHitBox(left, top, WIDTH, TabRow.DENT_HEIGHT);
        int surface = Math.round(alpha * WindowStyle.opacity(
                net.minecraft.client.Minecraft.getMinecraft()));
        TabRow.drawDentShape(left, left + WIDTH, top, alpha, surface);
        long now = System.nanoTime();
        boolean pressed = Mouse.isButtonDown(0);
        int exitX = left + TabRow.DENT_BORDER + EDGE;
        // Centred in the dent's body, the odd pixel up.
        int exitY = top + Math.floorDiv(TabRow.DENT_BODY_HEIGHT
                - EXIT_HEIGHT, 2);
        dent.exit = new LostTalesUiHitBox(exitX, exitY, EXIT_WIDTH,
                EXIT_HEIGHT).grown(SLACK);
        LostTalesUiInk.beginContent();
        boolean onExit = under == Part.EXIT;
        dent.exitMotion.advance(now, onExit, onExit, onExit && pressed);
        LostTalesUiButton.drawGlyph(LostTalesUiSheet.FULLSCREEN_EXIT,
                LostTalesUiSheet.FULLSCREEN_EXIT_HOVER, dent.exitMotion,
                exitX, exitY, alpha);
        regions.add(left, top, left + WIDTH, top + TabRow.DENT_HEIGHT);
    }

    /** What part of a drawn dent a point is on, and in which window; null off every dent. */
    Hit hitAt(double x, double y) {
        for (Map.Entry<String, Dent> entry : this.dents.entrySet()) {
            Dent dent = entry.getValue();
            if (dent.box == null || !dent.box.contains(x, y)) {
                continue;
            }
            Part part = dent.exit != null && dent.exit.contains(x, y)
                    ? Part.EXIT : null;
            return new Hit(entry.getKey(), part);
        }
        return null;
    }

    /** A point on a dent: its window, and the control it is on (null for the bare dent). */
    static final class Hit {
        final String windowId;
        final Part part;

        Hit(String windowId, Part part) {
            this.windowId = windowId;
            this.part = part;
        }
    }
}
