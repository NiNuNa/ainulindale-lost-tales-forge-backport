package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * A window's Snap Layouts sub-window: every layout the screen has room
 * for, the suggestions for that window first ({@link SnapLayouts}), three
 * to a row. A press on a zone sends the window there, a suggestion's
 * other windows with it, and closes the sub-window.
 *
 * <p>It opens from the Snap Layouts row of Window Options, from Alt+Z,
 * whose arrows then walk the zones and Return takes the one they stand on,
 * and as the pointer rests on the window's fullscreen control. Opened by
 * the pointer, it goes again once the pointer has left both the control
 * and the sub-window, unless something held it first: a press on it, or
 * its padlock.</p>
 */
final class SnapLayoutsWindow extends SubWindowContent {
    static final SubWindowKind KIND = SubWindowKind.register("snap_layouts",
            "gui.losttales.window.sub.snap_layouts");
    /** Layouts in a row. */
    static final int COLUMNS = 3;
    /** How long the pointer rests on the fullscreen control before it opens. */
    static final long OPEN_NANOS = 400L * 1000000L;
    /** How long the pointer may be away before one it opened goes. */
    static final long CLOSE_NANOS = 200L * 1000000L;
    /** The panel's own frame, which the sub-window's frame stands in for. */
    private static final int FRAME = WindowPlacement.FRAME_WIDTH;

    /** The window it sends to a zone; null before it first opens. */
    private String windowId;
    /** Whether the pointer resting on the fullscreen control opened it, and nothing has held it since. */
    private boolean byPointer;
    /** Whether Alt+Z opened it: the arrows walk its zones. */
    private boolean byKeys;
    /** The zone the arrows stand on, or -1. */
    private int keyZone = -1;
    /** The screen's size, for the layouts it offers. */
    private int screenWidth;
    private int screenHeight;
    /** How many layouts its size was last taken for. */
    private int measured = -1;
    /** Where the content stood when last drawn, in its own whole pixels; null before. */
    private LostTalesUiHitBox drawnBox;

    /** Takes the screen's size: the layouts it offers depend on it. */
    void bind(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    /**
     * Aims it at a window as it opens: {@code byPointer} when the pointer
     * resting on the fullscreen control opened it, {@code byKeys} when
     * Alt+Z did, its first zone under the arrows.
     */
    void aim(String windowId, boolean byPointer, boolean byKeys) {
        this.windowId = windowId;
        this.byPointer = byPointer;
        this.byKeys = byKeys;
        this.keyZone = byKeys ? 0 : -1;
    }

    /** The window it sends to a zone; null before it first opens. */
    String windowId() {
        return this.windowId;
    }

    /** Whether the pointer opened it and nothing has held it since: it goes with the pointer. */
    boolean goesWithPointer() {
        return this.byPointer;
    }

    /** Whether Alt+Z opened it: the arrows walk its zones. */
    boolean isByKeys() {
        return this.byKeys;
    }

    /** Something held it, a press on it or its padlock: it stays until it is closed. */
    void hold() {
        this.byPointer = false;
    }

    @Override
    public LostTalesUiSheet stripIcon() {
        return LostTalesUiSheet.FULLSCREEN;
    }

    /**
     * What it needs to come back with the screen: the window it is aimed
     * at, once something held it; one the pointer opened goes with the
     * screen.
     */
    @Override
    public Object sessionState() {
        return this.byPointer ? null : this.windowId;
    }

    private List<Window.ScreenFill[]> layouts() {
        return SnapLayouts.offered(Minecraft.getMinecraft(), this.screenWidth,
                this.screenHeight);
    }

    /** How many layouts it holds now, the suggestions among them. */
    private int count() {
        List<Window.ScreenFill[]> layouts = layouts();
        return SnapLayouts.suggestedFor(layouts, this.windowId).size()
                + layouts.size();
    }

    /**
     * Whether the layouts it holds changed in number since its size was
     * last taken, a suggestion coming or going: it takes its new size.
     */
    boolean takeNewSize() {
        int count = count();
        boolean changed = count != this.measured;
        this.measured = count;
        return changed;
    }

    /** The layouts laid out in a content box whose top left is {@code left}, {@code top}; null for none. */
    private SnapLayouts.Panel panel(double left, double top) {
        List<Window.ScreenFill[]> layouts = layouts();
        if (layouts.isEmpty()) {
            return null;
        }
        return SnapLayouts.lay(SnapLayouts.suggestedFor(layouts,
                this.windowId), layouts, COLUMNS, left - FRAME, top - FRAME,
                this.screenWidth, this.screenHeight);
    }

    private SnapLayouts.Panel drawnPanel() {
        return this.drawnBox == null ? null
                : panel(this.drawnBox.left, this.drawnBox.top);
    }

    @Override
    public int naturalWidth() {
        return SnapLayouts.panelWidth(count(), COLUMNS) - 2 * FRAME;
    }

    @Override
    public int naturalHeight(int width) {
        return SnapLayouts.panelHeight(count(), COLUMNS,
                SnapLayouts.thumbHeight(this.screenWidth, this.screenHeight))
                - 2 * FRAME;
    }

    @Override
    public int minWidth() {
        return naturalWidth();
    }

    @Override
    public int minHeight() {
        return naturalHeight(naturalWidth());
    }

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     int alpha, int surfaceAlpha) {
        this.drawnBox = box;
        SnapLayouts.Panel panel = panel(box.left, box.top);
        if (panel == null) {
            return;
        }
        int pointed = panel.zoneAt(pointerX, pointerY);
        boolean clipped = beginClip(minecraft, clipX, clipY, box.width,
                box.height);
        try {
            SnapLayouts.drawZones(minecraft, panel,
                    pointed >= 0 ? pointed : this.keyZone, alpha,
                    surfaceAlpha);
        } finally {
            endClip(clipped);
        }
    }

    @Override
    public boolean pressed(WindowHover hover, double x, double y, int button) {
        hold();
        SnapLayouts.Panel panel = drawnPanel();
        if (button == 0 && panel != null) {
            take(panel, panel.zoneAt(x, y));
        }
        return true;
    }

    /** Walks the arrows' zone one step. */
    void step(SnapKeys.Direction direction) {
        SnapLayouts.Panel panel = drawnPanel();
        if (panel != null) {
            this.keyZone = panel.step(this.keyZone, direction);
        }
    }

    /** Sends the window to the zone the arrows stand on. */
    void takeKeyZone() {
        take(drawnPanel(), this.keyZone);
    }

    private void take(SnapLayouts.Panel panel, int zone) {
        WindowScreen screen = WindowScreen.current();
        Window window = WindowLayout.window(this.windowId);
        if (panel != null && zone >= 0 && screen != null && window != null) {
            screen.snapFromLayouts(window, panel.fillOf(zone),
                    panel.layoutOf(zone), panel.companionsOf(zone));
        }
    }
}
