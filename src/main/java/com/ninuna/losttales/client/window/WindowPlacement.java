package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiWindowFrame;
import com.ninuna.losttales.gui.hud.HudPlacementLayout;
import net.minecraft.util.MathHelper;
import java.util.List;
import com.ninuna.losttales.client.gui.LostTalesGuiPointer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;

/**
 * Where windows sit on screen. A window is one unit — tab row,
 * messages and its own input bar, all the same width — anchored by its
 * <em>baseline</em>, the edge the newest message sits on: messages stack
 * upward from it and
 * the input bar hangs below it, so a window never moves when a message
 * arrives. The stored position is the baseline's percent of its travel
 * (with the box at its smallest, one empty line), and the visible box is
 * as tall as the window's own height, or the game's chat height while it
 * has none — never what its tabs hold, so neither a message nor another
 * tab brought forward resizes it — and a short window can be placed
 * anywhere on the screen, its top edge included. A window taller than
 * the room above its baseline is pushed down just far enough to stay on
 * screen and returns to its anchor once it fits; the stored position
 * never changes. Windows keep off the screen edges only: they may
 * overlap one another — the one in use is drawn in front — and only a
 * window linked to another moves with it.
 *
 * <p>Boxes are computed in fractional pixels with the same margin as
 * {@link HudPlacementLayout}, so a dragged window moves as smoothly as
 * the mouse instead of stepping by whole GUI pixels. A window keeps the
 * room its frame needs besides ({@link #FRAME_WIDTH}).</p>
 */
public final class WindowPlacement {
    /** Height of a window's bar strip: its first row is the window's
     *  bottom rule, as the tab strip's last row is the top one, then a
     *  framed button with two clear rows above and below it. The
     *  window's bottom frame edge runs just below it.
     *  <p>The strips are deliberately UI chrome and do not follow the
     *  vanilla chat-scale setting, exactly as vanilla's own input line
     *  does not: the setting scales what is read (the message stride,
     *  and with it the trailing strip), never what is operated. That
     *  asymmetry is a decision, not an oversight.</p> */
    public static final int BAR_STRIP_HEIGHT = 1 + 2
            + LostTalesUiFramedButton.HEIGHT + 2;
    /**
     * The window frame's whole width ({@link LostTalesUiWindowFrame}): its
     * edge, and a pixel of the window's own surface under it, all of it
     * over nothing of the window's own. Placement keeps that room for it,
     * so every frame shows whole and none lies over another window.
     */
    public static final int FRAME_WIDTH = LostTalesUiWindowFrame.WIDTH;
    /** How far a window's box stays inside the screen: the margin and its frame. */
    public static final int EDGE_MARGIN = HudPlacementLayout.SCREEN_MARGIN
            + FRAME_WIDTH;
    /**
     * How far apart two windows stuck together stand: the margin and a
     * frame for each, so their frames stand side by side.
     */
    public static final int WINDOW_GAP = HudPlacementLayout.SCREEN_MARGIN
            + 2 * FRAME_WIDTH;
    /**
     * How much of a window stays on screen however far it is pushed past
     * the screen's left, right or bottom edge: a twentieth of its width
     * or height, so it may go ninety-five hundredths of the way out, and
     * never less than {@link #MIN_HOLD}. The top edge holds the whole
     * window instead: its tab strip never leaves the screen, as a desktop
     * keeps a window's title bar in reach.
     */
    public static final double HOLD_SHARE = 0.05D;
    /**
     * The least of a window that stays on screen past an edge, in GUI
     * pixels: a stretch the pointer can still take hold of.
     */
    public static final int MIN_HOLD = 8;
    /**
     * The tool strip between the tab row's rule and the history: the
     * window's room for the controls that read its history, a band of
     * the selected tab's surface seventeen rows tall, its last row the
     * window's top rule, and a handle on the window like the row above
     * it. Chrome like the strips, so it keeps its size at every chat
     * scale.
     */
    public static final int TOOL_STRIP_HEIGHT = 17;
    /**
     * Head-room between the window's top rule and the topmost line's
     * glyphs, owned by that line — its band extends up through it to the
     * rule. Chrome like the strips, so it keeps its size at every chat
     * scale.
     */
    public static final int HISTORY_TOP_MARGIN = 2;
    /**
     * How much of a window's width its messages may fill before they
     * wrap, measured from its left edge: the rest is clear margin at the
     * right. A share rather than a fixed inset, so a wide window and a
     * narrow one keep the same proportions.
     */
    public static final double TEXT_WIDTH_SHARE = 0.95D;

    private WindowPlacement() {}

    /** A box in fractional GUI pixels. */
    public static final class Box {
        public final double x;
        public final double y;
        public final int width;
        /** Fractional: the box is exactly as tall as its lines ask. */
        public final double height;
        /** What hangs below the baseline: the padding and the bar. */
        public final int barHeight;
        /**
         * Pixels the box has for message lines, fractions included: the
         * height the window was resized to rather than a whole number of
         * lines, so nothing anywhere rounds it and no edge of the box
         * wobbles against another while a resize runs. The topmost line
         * is clipped where the room ends.
         */
        public final double room;

        Box(double x, double y, int width, double height, int barHeight) {
            this(x, y, width, height, barHeight, 0.0D);
        }

        public Box(double x, double y, int width, double height, int barHeight,
            double room) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.barHeight = barHeight;
            this.room = room;
        }

        public double baseline() {
            return this.y + this.height - this.barHeight;
        }

        public double right() {
            return this.x + this.width;
        }

        public double bottom() {
            return this.y + this.height;
        }

        /** Top of the bar strip: the bottom rule is its first pixel
         *  row, and the bar's own furniture starts one row below it. */
        public double barTop() {
            return baseline() + this.barHeight - BAR_STRIP_HEIGHT;
        }
    }

    /** A window position: left edge and baseline, fractional. */
    public static final class Anchor {
        public final double x;
        public final double baseline;

        Anchor(double x, double baseline) {
            this.x = x;
            this.baseline = baseline;
        }
    }

    /**
     * Whether a window's lines are laid out to its own width. The chat
     * turns it off when it cannot lay its lines out per window: they then
     * keep the game's chat width in every window.
     */
    private static volatile boolean ownLineWidths = true;

    /** See {@link #ownLineWidths}. */
    public static void setOwnLineWidths(boolean allowed) {
        ownLineWidths = allowed;
    }

    /** The game's own chat width: the feed's, and every window's default. */
    public static int chatWidth(Minecraft minecraft) {
        GuiNewChat chat = chat(minecraft);
        return chat == null ? 220 : chat.func_146228_f();
    }

    /**
     * The chat width one window's lines are drawn and wrapped at: what
     * its box leaves them at the chat scale, or the game's while the
     * window has no width of its own. A window's lines follow its own
     * width only while they can be laid out to it.
     */
    public static int chatWidth(Window window, Minecraft minecraft) {
        int own = window == null ? 0 : window.getOwnWidth();
        if (own > 0 && ownLineWidths) {
            return Math.max(1, chatWidthForBox(windowWidth(window, minecraft),
                    minecraft));
        }
        return chatWidth(minecraft);
    }

    private static int scaledScreenWidth(Minecraft minecraft) {
        try {
            return new net.minecraft.client.gui.ScaledResolution(minecraft,
                    minecraft.displayWidth, minecraft.displayHeight)
                    .getScaledWidth();
        } catch (RuntimeException unavailable) {
            return 0;
        }
    }

    public static int scaledScreenHeight(Minecraft minecraft) {
        try {
            return new net.minecraft.client.gui.ScaledResolution(minecraft,
                    minecraft.displayWidth, minecraft.displayHeight)
                    .getScaledHeight();
        } catch (RuntimeException unavailable) {
            return 0;
        }
    }

    /** The box width of the feed, and of a window without one of its own. */
    public static int windowWidth(Minecraft minecraft) {
        GuiNewChat chat = chat(minecraft);
        return chat == null ? 160 : gameChatBoxWidth(chat);
    }

    /**
     * The box width of one window: its own, or the game's chat width's
     * while it has none. A stored width the current screen cannot hold —
     * the GUI scale changed under a window resized wide — is capped to
     * what keeps the box inside the screen margins; the stored width
     * itself is untouched, so scaling back restores it.
     */
    public static int windowWidth(Window window, Minecraft minecraft) {
        int own = window == null ? 0 : window.getOwnWidth();
        if (own <= 0) {
            return windowWidth(minecraft);
        }
        int screenWidth = scaledScreenWidth(minecraft);
        return screenWidth <= 0 ? own
                : Math.max(1, Math.min(own, screenWidth - 2 * EDGE_MARGIN));
    }

    /**
     * The width a window's messages are laid out to, in the windows' own
     * units: {@link #TEXT_WIDTH_SHARE} of its chat width. Both the
     * shared history and a window laying out for itself measure with
     * this, so every line stops at the same edge.
     */
    public static int wrapWidth(int chatWidth, float chatScale) {
        float scale = chatScale <= 0.0F ? 1.0F : chatScale;
        return Math.max(1, MathHelper.floor_float(
                (float)(chatWidth * TEXT_WIDTH_SHARE) / scale));
    }

    /**
     * The exact vertical stride of one message line at the chat scale,
     * as the renderer draws it. Message room is measured with this, so a
     * window of {@code n} lines is exactly as tall as {@code n} drawn
     * lines at every chat scale.
     */
    public static double lineStride(Minecraft minecraft) {
        GuiNewChat chat = chat(minecraft);
        float scale = chat == null ? 1.0F : chat.func_146244_h();
        return Math.max(1.0D,
                WindowStyle.LINE_HEIGHT * (double)scale);
    }

    /** One message line at the chat scale, rounded to whole pixels. */
    public static int lineHeight(Minecraft minecraft) {
        return Math.max(1, (int)Math.round(lineStride(minecraft)));
    }

    /** What stands above the lines: the tab row, whose last pixel row
     *  is the window's top rule, and the tool strip under it. Chrome, so
     *  it keeps its size at every chat scale; see {@link #BAR_STRIP_HEIGHT}. */
    public static int rowHeight(Minecraft minecraft) {
        return TabRow.ROW_HEIGHT + TOOL_STRIP_HEIGHT;
    }

    /** What hangs below the baseline: the trailing strip — one line of
     *  always visible room between the newest message and the bottom
     *  rule, where the typing line lives — and the bar strip, whose
     *  first pixel row is the window's bottom rule. */
    public static int barHeight(Minecraft minecraft) {
        return lineHeight(minecraft) + BAR_STRIP_HEIGHT;
    }

    /** The smallest box: row, top margin, one line, trailing strip, bar. */
    public static int minHeight(Minecraft minecraft) {
        return rowHeight(minecraft) + HISTORY_TOP_MARGIN
                + lineHeight(minecraft) + barHeight(minecraft);
    }

    /**
     * The box height of a window with no height of its own: the game's
     * chat-height setting in lines, each a line of words at scale 1, the
     * window's chrome round them. The chat scale sizes the words in a
     * window, never the window.
     */
    public static double defaultHeight(Minecraft minecraft) {
        GuiNewChat chat = chat(minecraft);
        double lines = chat == null ? 20.0D : gameChatLines(chat);
        return heightForRoom(lines * WindowStyle.LINE_HEIGHT, minecraft);
    }

    /**
     * The box height the window shows: the height the player gave it —
     * fractions included, so the height is continuous — or the game's
     * chat height while it has none, never less than the least box.
     * Never what its tabs hold: neither bringing another tab forward nor
     * a message arriving ever resizes the window, and the chat scale
     * sizes the words in it, never the window. Every measure of the
     * window's height passes through here, so a resized window and the
     * box drawn for it always agree.
     */
    public static double currentHeight(Window window, Minecraft minecraft) {
        double own = window == null ? 0.0D : window.getOwnHeight();
        return own > 0.0D ? Math.max(minHeight(minecraft), own)
                : defaultHeight(minecraft);
    }

    /**
     * Pixels of room a box {@code height} tall leaves what the window holds,
     * fractions included: what stands between its top rule and its
     * trailing strip. The topmost line is clipped where the room ends.
     */
    public static double roomForHeight(double height, Minecraft minecraft) {
        return Math.max(1.0D, height - rowHeight(minecraft)
                - HISTORY_TOP_MARGIN - barHeight(minecraft));
    }

    /** Pixels of message room {@code lines} lines take at this scale,
     *  fractions included: nothing rounds, so no edge of the box ever
     *  wobbles against another while a resize runs. */
    public static double roomForLines(double lines, Minecraft minecraft) {
        return Math.max(1.0D, Math.max(1.0D, lines) * lineStride(minecraft));
    }

    /** The box height a window with {@code room} pixels of lines takes. */
    public static double heightForRoom(double room, Minecraft minecraft) {
        return rowHeight(minecraft) + HISTORY_TOP_MARGIN
                + Math.max(1.0D, room) + barHeight(minecraft);
    }

    /** The box height a window of {@code lines} message lines takes. */
    public static double heightForLines(double lines, Minecraft minecraft) {
        return heightForRoom(roomForLines(lines, minecraft), minecraft);
    }


    /**
     * The window's box for the given screen size, as it is drawn: its
     * {@link #restingBounds}, or the part of the screen it fills,
     * gliding between the two as it takes the screen or lets it go
     * ({@link #withFill}).
     */
    public static Box windowBounds(Window window, Minecraft minecraft,
                                   int screenWidth, int screenHeight) {
        return withFill(window, restingBounds(window, minecraft,
                screenWidth, screenHeight), minecraft, screenWidth,
                screenHeight);
    }

    /**
     * The window's box from its own place and size. A window linked to
     * another takes its place from its target — a window gap above or
     * below it, following chains — and is kept on screen like any other. No
     * window is a border for another: windows may overlap, and a tall
     * one never loses lines to a neighbour. Stored anchors never change;
     * it is all recomputed every frame.
     * Links are followed between resting boxes, so a window filling the
     * screen never moves the windows stuck to it; this is also the box
     * it goes back to, and what a drag measures a window from.
     */
    public static Box restingBounds(Window window, Minecraft minecraft,
                                    int screenWidth, int screenHeight) {
        List<Window> windows = WindowLayout.windows();
        int count = windows.size();
        int index = windows.indexOf(window);
        if (index < 0) {
            return anchoredBounds(window, minecraft, screenWidth,
                    screenHeight);
        }
        int gap = WINDOW_GAP;
        int row = rowHeight(minecraft);
        int barHeight = barHeight(minecraft);
        double[] x = new double[count];
        double[] baseline = new double[count];
        double[] room = new double[count];
        int[] widths = new int[count];
        for (int i = 0; i < count; i++) {
            Box box = anchoredBounds(windows.get(i), minecraft, screenWidth,
                    screenHeight);
            x[i] = box.x;
            baseline[i] = box.baseline();
            room[i] = box.room;
            widths[i] = box.width;
        }
        // A linked window takes its place from its target — above it or
        // below it, a window gap apart, room for both frames — following
        // chains in passes, and stops at the screen margins like any
        // window.
        for (int pass = 0; pass < count; pass++) {
            boolean moved = false;
            for (int i = 0; i < count; i++) {
                Window linked = windows.get(i);
                if (!linked.isLinked()) {
                    continue;
                }
                int t = windows.indexOf(window(windows,
                        linked.getLinkTarget()));
                if (t < 0 || t == i) {
                    continue;
                }
                if (linked.getLinkSide().isHorizontal()) {
                    // Stuck to a side: it keeps its own baseline and
                    // takes its left edge from the window it holds.
                    double wantedX = linked.getLinkSide()
                            == Window.LinkSide.LEFT
                            ? x[t] - gap - widths[i]
                            : x[t] + widths[t] + gap;
                    wantedX = holdOnScreen(wantedX, widths[i], screenWidth);
                    if (wantedX != x[i]) {
                        x[i] = wantedX;
                        moved = true;
                    }
                    continue;
                }
                double wanted = linked.isLinkedAbove()
                        ? baseline[t] - room[t] - HISTORY_TOP_MARGIN - row
                                - gap - barHeight
                        : baseline[t] + barHeight + gap + room[i]
                                + HISTORY_TOP_MARGIN + row;
                wanted = holdBaseline(wanted,
                        row + HISTORY_TOP_MARGIN + room[i] + barHeight,
                        barHeight, screenHeight);
                if (wanted != baseline[i]) {
                    baseline[i] = wanted;
                    moved = true;
                }
            }
            if (!moved) {
                break;
            }
        }
        double height = heightForRoom(room[index], minecraft);
        return new Box(x[index], baseline[index] - (height - barHeight),
                widths[index], height, barHeight, room[index]);
    }

    private static Window window(List<Window> windows, String id) {
        for (int index = 0; index < windows.size(); index++) {
            if (windows.get(index).getId().equals(id)) {
                return windows.get(index);
            }
        }
        return null;
    }

    /** The window's box from its stored anchor alone, kept on screen. */
    static Box anchoredBounds(Window window, Minecraft minecraft,
                              int screenWidth, int screenHeight) {
        int width = windowWidth(window, minecraft);
        // A stored height the current screen cannot hold — the GUI
        // scale changed under a window resized tall — is capped to what
        // fits between the screen margins; the stored height itself is
        // untouched, so scaling back restores it.
        double maxHeight = Math.max(minHeight(minecraft),
                screenHeight - 2.0D * EDGE_MARGIN);
        double height = Math.min(maxHeight, currentHeight(window, minecraft));
        double room = roomForHeight(height, minecraft);
        int barHeight = barHeight(minecraft);
        double baseline = holdBaseline(baselineFor(window.getOffsetY(),
                height, minecraft, screenHeight), height, barHeight,
                screenHeight);
        return new Box(holdOnScreen(position(window.getOffsetX(),
                screenWidth, width, EDGE_MARGIN), width, screenWidth),
                baseline - (height - barHeight), width, height, barHeight,
                room);
    }


    /**
     * The window's box as it is drawn this instant: its resting box, the
     * part of the screen it fills, and a share of the way between the
     * box its frame's glide set out from and the one it is bound for
     * while it glides — the leg's start being the box drawn as the
     * glide began, so a window sent from one fill to another sets out
     * from where it stands.
     */
    static Box withFill(Window window, Box resting, Minecraft minecraft,
                        int screenWidth, int screenHeight) {
        WindowFrame frame = WindowFrame.find(window.getId());
        if (frame == null || !frame.hasSeenFill()) {
            return window.getFill() == Window.ScreenFill.NONE ? resting
                    : fillBounds(window.getFill(), minecraft, screenWidth,
                            screenHeight);
        }
        WindowPlacement.Box from = frame.fillLegFrom();
        Window.ScreenFill to = frame.fillLegTo();
        double share = frame.fillShare();
        if (from == null && to == Window.ScreenFill.NONE) {
            return resting;
        }
        Box start = from == null ? resting : from;
        Box end = to == Window.ScreenFill.NONE ? resting
                : fillBounds(to, minecraft, screenWidth, screenHeight);
        if (share >= 1.0D) {
            return end;
        }
        int width = (int)Math.round(boxWidthForChatWidth(drawnChatWidth(
                window, minecraft, screenWidth), minecraft));
        return between(start, end, width, share, minecraft);
    }

    /**
     * A box {@code share} of the way from one box to another, every edge
     * moving straight to where it is going: the left edge, the baseline
     * and the message room each a share of the way, so the tab row and
     * the bar arrive together. {@code width} is the width the lines are
     * laid out to at that share, which the box takes whole.
     */
    static Box between(Box from, Box to, int width, double share,
                       Minecraft minecraft) {
        double room = from.room + (to.room - from.room) * share;
        double height = heightForRoom(room, minecraft);
        double baseline = from.baseline()
                + (to.baseline() - from.baseline()) * share;
        return new Box(from.x + (to.x - from.x) * share,
                baseline - (height - from.barHeight), width, height,
                from.barHeight, room);
    }

    /**
     * The part of the screen a window standing between {@code left} and
     * {@code right} fills when it is stretched to the screen's whole
     * height in its own column, as a desktop window's top edge carried to
     * the top of the screen stretches it: its own width, with room for
     * the frame round it as every part keeps.
     */
    public static Window.ScreenFill columnFill(double left, double right,
                                            int screenWidth) {
        if (screenWidth <= 0) {
            return Window.ScreenFill.NONE;
        }
        return Window.ScreenFill.free((left - EDGE_MARGIN) / screenWidth,
                0.0D, (right + EDGE_MARGIN) / screenWidth, 1.0D);
    }

    /**
     * The box a window fills a part of the screen with: that part of the
     * screen with room for the frame round it, so two windows filling
     * neighbouring parts stand a window gap apart, its lines laid out to its
     * width — the whole screen, a half from one side, a quarter from
     * one corner.
     */
    static Box fillBounds(Window.ScreenFill fill, Minecraft minecraft,
                          int screenWidth, int screenHeight) {
        int margin = EDGE_MARGIN;
        int barHeight = barHeight(minecraft);
        int width = (int)Math.round(boxWidthForChatWidth(
                fillChatWidth(fill, minecraft, screenWidth), minecraft));
        double room = Math.max(1.0D, fill.height(screenHeight)
                - 2.0D * margin - rowHeight(minecraft) - HISTORY_TOP_MARGIN
                - barHeight);
        return new Box(fill.left(screenWidth) + margin,
                fill.top(screenHeight) + margin, width,
                heightForRoom(room, minecraft), barHeight, room);
    }

    /** The chat width a window filling {@code fill} is laid out at. */
    static int fillChatWidth(Window.ScreenFill fill, Minecraft minecraft,
                             int screenWidth) {
        return Math.max(WindowLayout.MIN_WINDOW_SIZE, chatWidthForBox(
                fill.width(screenWidth) - 2 * EDGE_MARGIN,
                minecraft));
    }

    /**
     * The chat width a window is laid out at this instant: its own
     * width, the width of the part of the screen it fills, and a share
     * of the way between the width its glide set out from and the one
     * it is bound for while it glides, so its lines re-wrap as the box
     * grows rather than all at once.
     */
    public static int drawnChatWidth(Window window, Minecraft minecraft,
                                     int screenWidth) {
        int own = chatWidth(window, minecraft);
        WindowFrame frame = WindowFrame.find(window.getId());
        if (frame == null || !frame.hasSeenFill()) {
            return window.getFill() == Window.ScreenFill.NONE ? own
                    : fillChatWidth(window.getFill(), minecraft, screenWidth);
        }
        WindowPlacement.Box from = frame.fillLegFrom();
        Window.ScreenFill to = frame.fillLegTo();
        int start = from == null ? own
                : chatWidthForBox(from.width, minecraft);
        int end = to == Window.ScreenFill.NONE ? own
                : fillChatWidth(to, minecraft, screenWidth);
        return (int)Math.round(start + (end - start) * frame.fillShare());
    }

    /**
     * A window's baseline held on the screen: its tab strip never above
     * the top margin — a window grown past the top is pushed down rather
     * than off — and below, {@link #hold} of the box still in view.
     */
    static double holdBaseline(double baseline, double height, int barHeight,
                               int screenHeight) {
        double minBaseline = EDGE_MARGIN + height - barHeight;
        double maxBaseline = Math.max(minBaseline,
                screenHeight - hold(height) + height - barHeight);
        return Math.max(minBaseline, Math.min(maxBaseline, baseline));
    }

    /**
     * A left edge held so that {@link #hold} of a box {@code width} wide
     * stays on the screen past either side, and a box that fits kept
     * inside the margins.
     */
    static double holdOnScreen(double x, int width, int screenWidth) {
        double hold = hold(width);
        double minX = Math.min(EDGE_MARGIN, hold - width);
        double maxX = Math.max(EDGE_MARGIN, screenWidth - hold);
        return Math.max(minX, Math.min(maxX, x));
    }

    /**
     * How much of a box {@code size} long stays on screen past an edge:
     * {@link #HOLD_SHARE} of it, at least {@link #MIN_HOLD}, never more
     * than the box.
     */
    static double hold(double size) {
        return Math.min(Math.max(0.0D, size),
                Math.max(MIN_HOLD, size * HOLD_SHARE));
    }

    public static double windowPercentX(double x, Minecraft minecraft,
                                        int screenWidth) {
        return percent(x, screenWidth, windowWidth(minecraft), EDGE_MARGIN);
    }

    /** As above for a window that has a width of its own. */
    public static double windowPercentX(Window window, double x,
                                        Minecraft minecraft,
                                        int screenWidth) {
        return percent(x, screenWidth, windowWidth(window, minecraft),
                EDGE_MARGIN);
    }

    /**
     * The baseline for a percent: 0 puts the smallest box against the
     * top margin, 100 puts the bar against the bottom margin, and past
     * 100 the window hangs below the screen by that many hundredths of
     * its own {@code height}, as it hangs past a side by a share of its
     * width. Nothing goes above the top: there the strip stays.
     */
    public static double baselineFor(double percent, double height,
                                     Minecraft minecraft, int screenHeight) {
        int minHeight = minHeight(minecraft);
        double bounded = Math.max(0.0D,
                WindowLayout.clampWindowPercent(percent));
        double above = minHeight - barHeight(minecraft);
        if (bounded > 100.0D) {
            return position(100.0D, screenHeight, minHeight, EDGE_MARGIN)
                    + above + Math.max(0.0D, height)
                    * (bounded - 100.0D) / 100.0D;
        }
        return position(bounded, screenHeight, minHeight, EDGE_MARGIN)
                + above;
    }

    /** The inverse of {@link #baselineFor}, for a box {@code height} tall. */
    public static double windowPercentY(double baseline, double height,
                                        Minecraft minecraft,
                                        int screenHeight) {
        double lowest = baselineFor(100.0D, height, minecraft, screenHeight);
        if (baseline > lowest && height > 0.0D) {
            return WindowLayout.clampWindowPercent(
                    100.0D + (baseline - lowest) * 100.0D / height);
        }
        int minHeight = minHeight(minecraft);
        return Math.max(0.0D, percent(baseline
                - (minHeight - barHeight(minecraft)), screenHeight,
                minHeight, EDGE_MARGIN));
    }

    /**
     * As above for a window at the height it shows now; {@code window}
     * null means a window about to be created, at its smallest.
     */
    public static double windowPercentY(Window window, double baseline,
                                        Minecraft minecraft,
                                        int screenHeight) {
        return windowPercentY(baseline, window == null
                ? minHeight(minecraft) : currentHeight(window, minecraft),
                minecraft, screenHeight);
    }

    /**
     * The baseline a window has when the top of its tab row lies at
     * {@code rowTop}: the row is the box's first pixel row, so this is
     * the box top plus everything that stands between it and the newest
     * message's line. {@code window} null means a window about to be
     * created, at its smallest. A tab carried out of a row is placed
     * with this, so the row it is dropped into lands exactly where the
     * row it came from was — a window three lines tall and a window
     * thirty do not answer the same pointer differently.
     */
    public static double baselineForRowTop(Window window,
                                           Minecraft minecraft,
                                           double rowTop) {
        double height = window == null ? minHeight(minecraft)
                : currentHeight(window, minecraft);
        return rowTop + height - barHeight(minecraft);
    }

    /**
     * Keeps a window's requested position on screen: its strip under the
     * top margin, and {@link #hold} of it in view past the other three
     * edges. Other windows do not hold it; windows may overlap.
     * {@code window} null means a window about to be created, at its
     * smallest.
     */
    public static Anchor constrainWindow(Window window,
                                         Minecraft minecraft,
                                         double x, double baseline,
                                         int screenWidth, int screenHeight) {
        int width = windowWidth(window, minecraft);
        double height = window == null ? minHeight(minecraft)
                : currentHeight(window, minecraft);
        return new Anchor(holdOnScreen(x, width, screenWidth),
                holdBaseline(baseline, height, barHeight(minecraft),
                        screenHeight));
    }

    /**
     * The chat width, in GUI pixels, that gives a window box of about
     * {@code boxWidth}: the inverse of the box width
     * {@link #gameChatBoxWidth} derives from it.
     */
    public static int chatWidthForBox(double boxWidth, Minecraft minecraft) {
        GuiNewChat chat = chat(minecraft);
        float scale = chat == null ? 1.0F : chat.func_146244_h();
        // boxWidth is 2 + (ceil(chatWidth / scale) + 4) * scale.
        return (int)Math.round(boxWidth - 2.0D - 4.0D * scale);
    }

    /** The box width a chat width of {@code chatWidth} pixels gives. */
    public static double boxWidthForChatWidth(int chatWidth,
                                              Minecraft minecraft) {
        GuiNewChat chat = chat(minecraft);
        float scale = chat == null ? 1.0F : chat.func_146244_h();
        return 2.0D + (Math.ceil(chatWidth / (double)scale) + 4.0D) * scale;
    }

    /**
     * The narrowest a window may be dragged, in the windows' own units:
     * enough for a message to still lay out under its sender rather than
     * falling back to vanilla's wrapping, and for the input bar to keep
     * its furniture. Measured in chat units, so the readable minimum is
     * the same amount of text at every chat scale.
     */
    public static final int MIN_READABLE_CHAT_WIDTH = 160;

    /** That minimum in GUI pixels, at the scale the chat is drawn at. */
    public static int minChatWidth(Minecraft minecraft) {
        GuiNewChat chat = chat(minecraft);
        float scale = chat == null ? 1.0F : chat.func_146244_h();
        return Math.max(WindowLayout.MIN_WINDOW_SIZE,
                Math.round(MIN_READABLE_CHAT_WIDTH * scale));
    }

    /** The narrowest box a window may be dragged to, in GUI pixels. */
    public static double minBoxWidth(Minecraft minecraft) {
        return boxWidthForChatWidth(minChatWidth(minecraft), minecraft);
    }

    /**
     * The narrowest box <em>this</em> window may be dragged to: the
     * readable minimum above, or the width its own tab row needs to keep
     * every tab in the strip — the widest whole, the others at their
     * icons — whichever is greater. A window with more tabs therefore
     * has a wider floor, so pulling an edge shortens the other tabs'
     * names down to their icons and then stops rather than dropping a
     * tab out of sight.
     */
    public static double minBoxWidth(Minecraft minecraft,
                                     Window window) {
        return Math.max(minBoxWidth(minecraft),
                TabRow.boxWidthForNarrowestRow(minecraft, window));
    }

    /**
     * The pointer in fractional GUI pixels, from the raw mouse, so a drag
     * is not quantised to whole GUI pixels at higher GUI scales.
     */
    public static double preciseMouseX(Minecraft minecraft, int screenWidth) {
        return LostTalesGuiPointer.x(minecraft, screenWidth);
    }

    /** As above, for y. */
    public static double preciseMouseY(Minecraft minecraft, int screenHeight) {
        return LostTalesGuiPointer.y(minecraft, screenHeight);
    }

    /** Position of an element's leading edge for a percent of its travel. */
    /**
     * Where a percent puts an element's near edge: 0 against the first
     * margin, 100 against the last, the values between a share of the
     * travel between them. Past either end the element hangs over the
     * edge by that many hundredths of its own size — a share of the
     * element rather than of the travel, so a window pushed half off
     * one screen is half off every screen — within
     * {@link WindowLayout#clampWindowPercent}.
     */
    public static double position(double percent, int screenSize,
                                  int elementSize) {
        return position(percent, screenSize, elementSize,
                HudPlacementLayout.SCREEN_MARGIN);
    }

    /** As above, the element kept {@code margin} from the screen's edges. */
    static double position(double percent, int screenSize, int elementSize,
                           int margin) {
        double travel = Math.max(0, screenSize - elementSize - margin * 2);
        double bounded = WindowLayout.clampWindowPercent(percent);
        if (bounded < 0.0D) {
            return margin + elementSize * bounded / 100.0D;
        }
        if (bounded > 100.0D) {
            return margin + travel + elementSize * (bounded - 100.0D) / 100.0D;
        }
        return margin + travel * bounded / 100.0D;
    }

    /** The inverse of {@link #position}. */
    public static double percent(double position, int screenSize,
                                 int elementSize) {
        return percent(position, screenSize, elementSize,
                HudPlacementLayout.SCREEN_MARGIN);
    }

    /** As above, the element kept {@code margin} from the screen's edges. */
    static double percent(double position, int screenSize, int elementSize,
                          int margin) {
        double travel = Math.max(0, screenSize - elementSize - margin * 2);
        double offset = position - margin;
        if (elementSize <= 0) {
            return 0.0D;
        }
        if (offset < 0.0D) {
            return WindowLayout.clampWindowPercent(
                    offset * 100.0D / elementSize);
        }
        if (offset > travel) {
            return WindowLayout.clampWindowPercent(
                    100.0D + (offset - travel) * 100.0D / elementSize);
        }
        return travel <= 0.0D ? 0.0D
                : WindowLayout.clampWindowPercent(offset * 100.0D / travel);
    }

    /**
     * The box width the game's chat width setting gives at the chat
     * scale: the width and the history band's own margins.
     */
    public static int gameChatBoxWidth(GuiNewChat chat) {
        if (chat == null) {
            return 0;
        }
        float scale = chat.func_146244_h();
        int unscaledWidth = MathHelper.ceiling_float_int(
                chat.func_146228_f() / scale);
        return Math.round(2.0F + (unscaledWidth + 4) * scale);
    }

    /**
     * Lines that fit the user's configured chat pixel height at this
     * chat's stride: vanilla counts that height in its own 9px lines.
     */
    public static int gameChatLines(GuiNewChat chat) {
        return Math.max(1, chat.func_146232_i() * 9 / WindowStyle.LINE_HEIGHT);
    }

    public static GuiNewChat chat(Minecraft minecraft) {
        return minecraft == null || minecraft.ingameGUI == null
                ? null : minecraft.ingameGUI.getChatGUI();
    }
}
