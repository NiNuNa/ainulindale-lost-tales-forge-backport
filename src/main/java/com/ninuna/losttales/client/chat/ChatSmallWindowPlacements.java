package com.ninuna.losttales.client.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Where the player has put each kind of small window, remembered per
 * account in the layout file, and which windows were open when the chat
 * last closed. A window opens where its popup always opened until the
 * player moves or resizes it; from then on it opens where they left it.
 * A place is kept as shares of the room the screen leaves the window, so
 * it lands in the same part of any screen. A kind the player has only
 * moved keeps its content's own size, which changes with what it holds;
 * one they have resized keeps the size they gave it.
 */
public final class ChatSmallWindowPlacements {
    /** The widest and tallest a remembered size is read as. */
    static final int MAX_SIZE = 4096;

    /** A kind's remembered place, and the size the player gave it if they did. */
    static final class Placement {
        /** Share of the room left and right of the window, 0-100. */
        final double xPercent;
        /** Share of the room above and below the window, 0-100. */
        final double yPercent;
        /** The size the player gave the kind; 0 for a kind only moved, which its content sizes. */
        final int width;
        final int height;

        Placement(double xPercent, double yPercent, int width, int height) {
            this.xPercent = clampPercent(xPercent);
            this.yPercent = clampPercent(yPercent);
            boolean sized = width > 0 && height > 0;
            this.width = sized ? Math.min(MAX_SIZE, width) : 0;
            this.height = sized ? Math.min(MAX_SIZE, height) : 0;
        }

        /** Whether the player gave the kind its size, not only its place. */
        boolean isSized() {
            return this.width > 0;
        }

        /** The left edge of a window {@code width} wide on a screen {@code screenWidth} wide. */
        double left(int screenWidth, int width) {
            return ChatWindowPlacement.EDGE_MARGIN + this.xPercent / 100.0D
                    * room(screenWidth, width);
        }

        /** The top edge of a window {@code height} tall on a screen {@code screenHeight} tall. */
        double top(int screenHeight, int height) {
            return ChatWindowPlacement.EDGE_MARGIN + this.yPercent / 100.0D
                    * room(screenHeight, height);
        }
    }

    /** A window open as the chat closed: what it was, what it held, and where it stood. */
    static final class Reopening {
        final ChatSmallWindowKind kind;
        final String key;
        /** What its content needs to come back as it was; null for nothing. */
        final Object state;
        final double left;
        final double top;
        final int width;
        final int height;
        /** Whether the player had given it its size. */
        final boolean sized;

        Reopening(ChatSmallWindowKind kind, String key, Object state,
                  double left, double top, int width, int height,
                  boolean sized) {
            this.kind = kind;
            this.key = key;
            this.state = state;
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.sized = sized;
        }
    }

    private static final Map<ChatSmallWindowKind, Placement> PLACED =
            new EnumMap<ChatSmallWindowKind, Placement>(
                    ChatSmallWindowKind.class);
    /** The windows open as the chat last closed, back to front. */
    private static final List<Reopening> OPEN_AT_CLOSE =
            new ArrayList<Reopening>();

    private ChatSmallWindowPlacements() {}

    /** The kind's remembered place, or null while it opens where its popup did. */
    static synchronized Placement of(ChatSmallWindowKind kind) {
        return PLACED.get(kind);
    }

    /**
     * Remembers where the player left a window, with its size when
     * {@code sized}, and writes the layout file.
     */
    static void remember(ChatSmallWindowKind kind, double left, double top,
                         int width, int height, boolean sized,
                         int screenWidth, int screenHeight) {
        synchronized (ChatSmallWindowPlacements.class) {
            PLACED.put(kind, placementOf(left, top, width, height, sized,
                    screenWidth, screenHeight));
        }
        ChatWindowLayout.persist();
    }

    /**
     * The place of a window's box as shares of the room the screen leaves
     * it, and its size when the player gave it one.
     */
    static Placement placementOf(double left, double top, int width,
                                 int height, boolean sized, int screenWidth,
                                 int screenHeight) {
        return new Placement(percentOf(left, room(screenWidth, width)),
                percentOf(top, room(screenHeight, height)),
                sized ? width : 0, sized ? height : 0);
    }

    /** What the layout file said; replaces everything remembered. */
    static synchronized void load(Map<ChatSmallWindowKind, Placement> placed) {
        PLACED.clear();
        if (placed != null) {
            PLACED.putAll(placed);
        }
    }

    /** Everything remembered, for the layout file. */
    static synchronized Map<ChatSmallWindowKind, Placement> all() {
        return Collections.unmodifiableMap(
                new EnumMap<ChatSmallWindowKind, Placement>(PLACED));
    }

    /** Notes the windows open as the chat closes, back to front, so they come back with it. */
    static synchronized void rememberOpen(List<Reopening> windows) {
        OPEN_AT_CLOSE.clear();
        OPEN_AT_CLOSE.addAll(windows);
    }

    static synchronized List<Reopening> openAtClose() {
        return new ArrayList<Reopening>(OPEN_AT_CLOSE);
    }

    /** Leaving a server closes every window for good; their places stay. */
    public static synchronized void forgetOpen() {
        OPEN_AT_CLOSE.clear();
    }

    /** The room a screen {@code screen} long leaves a window {@code size} long. */
    private static double room(int screen, int size) {
        return Math.max(0.0D, screen - 2.0D * ChatWindowPlacement.EDGE_MARGIN
                - size);
    }

    private static double percentOf(double edge, double room) {
        return room <= 0.0D ? 0.0D : clampPercent(
                (edge - ChatWindowPlacement.EDGE_MARGIN) / room * 100.0D);
    }

    private static double clampPercent(double value) {
        return Double.isNaN(value) ? 0.0D : Math.max(0.0D, Math.min(100.0D,
                value));
    }
}
