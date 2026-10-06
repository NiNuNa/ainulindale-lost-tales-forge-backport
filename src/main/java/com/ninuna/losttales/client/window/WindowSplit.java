package com.ninuna.losttales.client.window;

import java.util.Locale;

/**
 * Two pages of one window shown together, as Chrome's split view: side by
 * side, or one over the other, with a divider the player drags. The first
 * stands on the left (or on top), the second right after it in the row;
 * the one in front of the window is the side the tool strip and the bar
 * serve. Each side keeps at least a quarter of the room.
 */
public final class WindowSplit {
    /** The least share of the room either side keeps. */
    public static final double LEAST_SHARE = 0.25D;
    /** The room the divider takes between the two sides, in GUI pixels. */
    public static final int DIVIDER = 3;

    private final WindowPage first;
    private final WindowPage second;
    /** Whether one stands over the other rather than beside it. */
    private final boolean stacked;
    /** The first side's share of the room. */
    private final double share;

    public WindowSplit(WindowPage first, WindowPage second, boolean stacked,
                       double share) {
        if (first == null || second == null || first.equals(second)) {
            throw new IllegalArgumentException("a split is two different pages");
        }
        this.first = first;
        this.second = second;
        this.stacked = stacked;
        this.share = clampShare(share);
    }

    public WindowPage first() { return this.first; }

    public WindowPage second() { return this.second; }

    /** Whether one side stands over the other. */
    public boolean isStacked() { return this.stacked; }

    /** The first side's share of the room. */
    public double share() { return this.share; }

    public boolean holds(WindowPage tab) {
        return this.first.equals(tab) || this.second.equals(tab);
    }

    /** The other side of {@code tab}; null for a tab not in the split. */
    public WindowPage other(WindowPage tab) {
        return this.first.equals(tab) ? this.second
                : this.second.equals(tab) ? this.first : null;
    }

    /** The same split with its sides changed round. */
    public WindowSplit swapped() {
        return new WindowSplit(this.second, this.first, this.stacked, 1.0D - this.share);
    }

    /** The same split the other way: side by side, or one over the other. */
    public WindowSplit turned(boolean stackedNow) {
        return new WindowSplit(this.first, this.second, stackedNow, this.share);
    }

    /** The same split with the room shared at {@code share}, held to the least share. */
    /** The same split with {@code page} standing where {@code old} stood. */
    public WindowSplit replacing(WindowPage old, WindowPage page) {
        return new WindowSplit(old.equals(this.first) ? page : this.first,
                old.equals(this.second) ? page : this.second, this.stacked,
                this.share);
    }

    public WindowSplit sharedAt(double share) {
        return new WindowSplit(this.first, this.second, this.stacked, share);
    }

    /** A share held between the least each side keeps. */
    public static double clampShare(double share) {
        if (Double.isNaN(share)) {
            return 0.5D;
        }
        return Math.max(LEAST_SHARE, Math.min(1.0D - LEAST_SHARE, share));
    }

    /**
     * Where the divider stands in a room from {@code start} to {@code end}
     * along the way the split runs: the first side's end, on a whole
     * pixel, the divider after it.
     */
    public double dividerAt(double start, double end) {
        return dividerAt(this.share, start, end);
    }

    private static double dividerAt(double share, double start, double end) {
        return Math.floor(start + (end - start - DIVIDER) * share);
    }

    /**
     * The share a divider dragged to {@code at} gives, in a room from
     * {@code start} to {@code end}: held to the least share.
     */
    public static double shareAt(double at, double start, double end) {
        double room = end - start - DIVIDER;
        return room <= 0.0D ? 0.5D : clampShare((at - start) / room);
    }

    /**
     * One side's box in a room: {@code left, top, right, bottom}, the
     * first side's or the second's, the divider between them.
     */
    public double[] box(boolean firstSide, double left, double top, double right,
                        double bottom) {
        return box(this.stacked, this.share, firstSide, left, top, right, bottom);
    }

    /** One side's box of any split, {@code stacked} or not, the first side's share {@code share}. */
    static double[] box(boolean stacked, double share, boolean firstSide,
                        double left, double top, double right, double bottom) {
        if (stacked) {
            double divider = dividerAt(share, top, bottom);
            return firstSide ? new double[] {left, top, right, divider}
                    : new double[] {left, divider + DIVIDER, right, bottom};
        }
        double divider = dividerAt(share, left, right);
        return firstSide ? new double[] {left, top, divider, bottom}
                : new double[] {divider + DIVIDER, top, right, bottom};
    }

    /** How the layout file writes the split's way: {@code across} or {@code down}. */
    public String wayId() {
        return this.stacked ? "down" : "across";
    }

    /** Whether a way the layout file wrote is one over the other. */
    public static boolean isStackedWay(String way) {
        return way != null && "down".equals(way.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof WindowSplit)) {
            return false;
        }
        WindowSplit split = (WindowSplit)other;
        return this.first.equals(split.first) && this.second.equals(split.second)
                && this.stacked == split.stacked && this.share == split.share;
    }

    @Override
    public int hashCode() {
        return this.first.hashCode() * 31 + this.second.hashCode();
    }
}
