package com.ninuna.losttales.client.window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the player has put each kind of sub-window, remembered per
 * account in the layout file, and which windows were open when the
 * screen last closed. A window opens where its popup always opened until the
 * player moves or resizes it; from then on it opens where they left it.
 * A place is kept from the corner of the room it was left nearest to, so
 * a picker left in a window's bottom right corner opens in the bottom
 * right corner of whichever window it opens in, however large. A kind the
 * player has only moved keeps its content's own size, which changes with
 * what it holds; one they have resized keeps the size they gave it.
 */
public final class SubWindowPlaces {
    /** The widest and tallest a remembered size is read as. */
    static final int MAX_SIZE = 4096;
    /** The farthest from its corner a remembered place is read as. */
    static final double MAX_DISTANCE = 4096.0D;

    /** A kind's remembered place, and the size the player gave it if they did. */
    static final class Placement {
        /** Whether it is measured from the room's right edge, else from its left. */
        final boolean fromRight;
        /** Whether it is measured from the room's bottom edge, else from its top. */
        final boolean fromBottom;
        /** How far in from those two edges it stands. */
        final double dx;
        final double dy;
        /** The size the player gave the kind; 0 for a kind only moved, which its content sizes. */
        final int width;
        final int height;

        Placement(boolean fromRight, boolean fromBottom, double dx, double dy,
                  int width, int height) {
            this.fromRight = fromRight;
            this.fromBottom = fromBottom;
            this.dx = clampDistance(dx);
            this.dy = clampDistance(dy);
            boolean sized = width > 0 && height > 0;
            this.width = sized ? Math.min(MAX_SIZE, width) : 0;
            this.height = sized ? Math.min(MAX_SIZE, height) : 0;
        }

        /** Whether the player gave the kind its size, not only its place. */
        boolean isSized() {
            return this.width > 0;
        }

        /** Where a window {@code width} wide stands across a room {@code roomWidth} wide. */
        double x(double roomWidth, int width) {
            return this.fromRight ? roomWidth - width - this.dx : this.dx;
        }

        /** Where a window {@code height} tall stands down a room {@code roomHeight} tall. */
        double y(double roomHeight, int height) {
            return this.fromBottom ? roomHeight - height - this.dy : this.dy;
        }

        /** The corner it is measured from as the layout file writes it. */
        String corner() {
            return (this.fromBottom ? "b" : "t") + (this.fromRight ? "r" : "l");
        }
    }

    /** A window open as the screen closed: what it was, what it held, and where it stood in which window. */
    public static final class Reopening {
        public final SubWindowKind kind;
        final String key;
        /** What its content needs to come back as it was; null for nothing. */
        public final Object state;
        /** The window it stood in; null for the bare screen. */
        final String parentId;
        final double x;
        final double y;
        final int width;
        final int height;
        /** Whether the player had given it its size. */
        final boolean sized;

        Reopening(SubWindowKind kind, String key, Object state,
                  String parentId, double x, double y, int width, int height,
                  boolean sized) {
            this.kind = kind;
            this.key = key;
            this.state = state;
            this.parentId = parentId;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.sized = sized;
        }
    }

    private static final Map<SubWindowKind, Placement> PLACED =
            new HashMap<SubWindowKind, Placement>();
    /** The windows open as the screen last closed, back to front. */
    private static final List<Reopening> OPEN_AT_CLOSE =
            new ArrayList<Reopening>();

    private SubWindowPlaces() {}

    /** The kind's remembered place, or null while it opens where its popup did. */
    static synchronized Placement of(SubWindowKind kind) {
        return PLACED.get(kind);
    }

    /**
     * Remembers where the player left a window in its room, with its size
     * when {@code sized}, and writes the layout file.
     */
    static void remember(SubWindowKind kind, double x, double y,
                         int width, int height, boolean sized,
                         double roomWidth, double roomHeight) {
        synchronized (SubWindowPlaces.class) {
            PLACED.put(kind, placementOf(x, y, width, height, sized,
                    roomWidth, roomHeight));
        }
        WindowLayout.persist();
    }

    /**
     * The place of a window standing at {@code x}, {@code y} in a room,
     * measured from the corner its middle is nearest to, and its size when
     * the player gave it one.
     */
    static Placement placementOf(double x, double y, int width, int height,
                                 boolean sized, double roomWidth,
                                 double roomHeight) {
        boolean fromRight = x + width / 2.0D > roomWidth / 2.0D;
        boolean fromBottom = y + height / 2.0D > roomHeight / 2.0D;
        return new Placement(fromRight, fromBottom,
                fromRight ? roomWidth - width - x : x,
                fromBottom ? roomHeight - height - y : y,
                sized ? width : 0, sized ? height : 0);
    }

    /** What the layout file said; replaces everything remembered. */
    static synchronized void load(Map<SubWindowKind, Placement> placed) {
        PLACED.clear();
        if (placed != null) {
            PLACED.putAll(placed);
        }
    }

    /** Everything remembered, for the layout file, in the order the kinds were registered. */
    static synchronized Map<SubWindowKind, Placement> all() {
        Map<SubWindowKind, Placement> all =
                new LinkedHashMap<SubWindowKind, Placement>();
        for (SubWindowKind kind : SubWindowKind.all()) {
            Placement placement = PLACED.get(kind);
            if (placement != null) {
                all.put(kind, placement);
            }
        }
        return Collections.unmodifiableMap(all);
    }

    /** Notes the windows open as the screen closes, back to front, so they come back with it. */
    public static synchronized void rememberOpen(List<Reopening> windows) {
        OPEN_AT_CLOSE.clear();
        OPEN_AT_CLOSE.addAll(windows);
    }

    public static synchronized List<Reopening> openAtClose() {
        return new ArrayList<Reopening>(OPEN_AT_CLOSE);
    }

    /** Leaving a server closes every window for good; their places stay. */
    public static synchronized void forgetOpen() {
        OPEN_AT_CLOSE.clear();
    }

    private static double clampDistance(double value) {
        return Double.isNaN(value) ? 0.0D
                : Math.max(0.0D, Math.min(MAX_DISTANCE, value));
    }
}
