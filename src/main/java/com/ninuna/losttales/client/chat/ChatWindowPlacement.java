package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.hud.HudPlacementLayout;
import net.minecraft.util.MathHelper;
import java.util.List;
import com.ninuna.losttales.client.gui.LostTalesGuiPointer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;

/**
 * Where chat windows sit on screen. A window is one unit — tab row,
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
 * <p>The closed-chat feed — one stack of every unmuted channel's
 * messages, shown only while the chat is closed — is placed the same way
 * by its own baseline, with no row and no bar. The HUD placement editor
 * moves the feed; windows are moved in the chat screen alone.</p>
 *
 * <p>Boxes are computed in fractional pixels with the same margin as
 * {@link HudPlacementLayout}, so a dragged window moves as smoothly as
 * the mouse instead of stepping by whole GUI pixels. A window keeps the
 * room its frame needs besides ({@link #FRAME_WIDTH}); the closed feed
 * has no frame and keeps the margin alone.</p>
 */
public final class ChatWindowPlacement {
    /** Height of a window's bar strip ({@link ChatInputBar#HEIGHT}):
     *  its first row is the window's bottom rule, as the tab strip's
     *  last row is the top one; the window's bottom frame edge runs just
     *  below it.
     *  <p>The strips are deliberately UI chrome and do not follow the
     *  vanilla chat-scale setting, exactly as vanilla's own input line
     *  does not: the setting scales what is read (the message stride,
     *  and with it the trailing strip), never what is operated. That
     *  asymmetry is a decision, not an oversight.</p> */
    public static final int INPUT_HEIGHT = ChatInputBar.HEIGHT;
    /**
     * The edge of a window's frame, a lit framed button's: one pixel just
     * outside its box.
     */
    static final int FRAME_EDGE_WIDTH = 1;
    /**
     * The window frame's whole width: its edge, and a pixel of the
     * window's own surface just outside that, lying under the edge as a
     * framed button's surface lies under its frame — all of it over
     * nothing of the window's own. Placement keeps that room for it, so
     * every frame shows whole and none lies over another window.
     */
    static final int FRAME_WIDTH = FRAME_EDGE_WIDTH + 1;
    /** How far a window's box stays inside the screen: the margin and its frame. */
    static final int EDGE_MARGIN = HudPlacementLayout.SCREEN_MARGIN
            + FRAME_WIDTH;
    /**
     * How far apart two windows stuck together stand: the margin and a
     * frame for each, so their frames stand side by side.
     */
    static final int WINDOW_GAP = HudPlacementLayout.SCREEN_MARGIN
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

    private ChatWindowPlacement() {}

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

        Box(double x, double y, int width, double height, int barHeight,
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
            return baseline() + this.barHeight - INPUT_HEIGHT;
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

    /** The game's own chat width: the feed's, and every window's default. */
    public static int chatWidth(Minecraft minecraft) {
        GuiNewChat chat = chat(minecraft);
        return chat == null ? 220 : chat.func_146228_f();
    }

    /**
     * The chat width one window is drawn and wrapped at: the width the
     * player gave it, or the game's while it has none. A window can only
     * hold a width of its own while its lines can be laid out to it.
     * A stored width the current screen cannot hold — the GUI scale
     * changed under a window resized large — is capped to what keeps
     * the box inside the screen margins, wrap and box together so no
     * line runs past the edge; the stored width itself is untouched, so
     * scaling back restores it.
     */
    public static int chatWidth(ChatWindow window, Minecraft minecraft) {
        int own = window == null ? 0 : window.getWidth();
        if (own > 0 && ChatWindowLines.isAvailable()) {
            return Math.min(own, maxOwnChatWidth(minecraft));
        }
        return chatWidth(minecraft);
    }

    /**
     * The widest chat width a window of its own may be drawn at on the
     * current screen: its box at that width just fits between the two
     * screen margins. Unbounded when the screen cannot be measured.
     */
    private static int maxOwnChatWidth(Minecraft minecraft) {
        int screenWidth = scaledScreenWidth(minecraft);
        if (screenWidth <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, chatWidthForBox(
                screenWidth - 2 * EDGE_MARGIN,
                minecraft));
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

    private static int scaledScreenHeight(Minecraft minecraft) {
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
        return chat == null ? 160 : ChatWindowFrame.boxWidth(chat);
    }

    /** The box width of one window, at its own (screen-capped) chat width. */
    public static int windowWidth(ChatWindow window, Minecraft minecraft) {
        int own = window == null ? 0 : window.getWidth();
        if (own <= 0 || !ChatWindowLines.isAvailable()) {
            return windowWidth(minecraft);
        }
        return (int)Math.round(boxWidthForChatWidth(
                chatWidth(window, minecraft), minecraft));
    }

    /**
     * The width a window's messages are laid out to, in the chat's own
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
                LostTalesChatOverlayRenderer.LINE_HEIGHT * (double)scale);
    }

    /** One message line at the chat scale, rounded to whole pixels. */
    public static int lineHeight(Minecraft minecraft) {
        return Math.max(1, (int)Math.round(lineStride(minecraft)));
    }

    /** What stands above the lines: the tab row, whose last pixel row
     *  is the window's top rule, and the tool strip under it. Chrome, so
     *  it keeps its size at every chat scale; see {@link #INPUT_HEIGHT}. */
    public static int rowHeight(Minecraft minecraft) {
        return ChatChannelTabBar.ROW_HEIGHT + TOOL_STRIP_HEIGHT;
    }

    /** What hangs below the baseline: the trailing strip — one line of
     *  always visible room between the newest message and the bottom
     *  rule, where the typing line lives — and the bar strip, whose
     *  first pixel row is the window's bottom rule. */
    public static int barHeight(Minecraft minecraft) {
        return lineHeight(minecraft) + INPUT_HEIGHT;
    }

    /** The smallest box: row, top margin, one line, trailing strip, bar. */
    public static int minHeight(Minecraft minecraft) {
        return rowHeight(minecraft) + HISTORY_TOP_MARGIN
                + lineHeight(minecraft) + barHeight(minecraft);
    }

    /**
     * The most lines this window may show: the height the player gave
     * it — fractions included, so the height is continuous — or the
     * game's chat-height setting while it has none. Every measure of the
     * window's height passes through here, so a resized window and the
     * box drawn for it always agree.
     */
    public static double lineCap(ChatWindow window, Minecraft minecraft) {
        double own = window == null ? 0.0D : window.getMaxLines();
        if (own > 0.0D) {
            return own;
        }
        GuiNewChat chat = chat(minecraft);
        return chat == null ? 20.0D
                : LostTalesChatOverlayRenderer.visibleLineCount(chat);
    }

    /**
     * The lines the window currently shows: its {@link #lineCap}, at
     * least one — the height the player gave it, or the game's chat
     * height while it has none. Never what its tabs hold: fewer messages
     * than it has room for show as empty rows above them, so neither
     * bringing another tab forward nor a message arriving ever resizes
     * the window, and resizing never moves it.
     */
    public static double currentLines(ChatWindow window,
                                      Minecraft minecraft) {
        return Math.max(1.0D, lineCap(window, minecraft));
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
     * The lines a box of {@code height} pixels holds: the inverse of
     * {@link #heightForLines}, fractions included, never fewer than one.
     */
    public static double linesForHeight(double height, Minecraft minecraft) {
        return Math.max(1.0D, (height - rowHeight(minecraft)
                - HISTORY_TOP_MARGIN - barHeight(minecraft))
                / lineStride(minecraft));
    }

    /** The box height the window currently shows: row, lines and bar. */
    public static double currentHeight(ChatWindow window,
                                       Minecraft minecraft) {
        return heightForLines(currentLines(window, minecraft), minecraft);
    }

    /**
     * The window's box for the given screen size, as it is drawn: its
     * {@link #restingBounds}, or the part of the screen it fills,
     * gliding between the two as it takes the screen or lets it go
     * ({@link #withFill}).
     */
    public static Box windowBounds(ChatWindow window, Minecraft minecraft,
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
    public static Box restingBounds(ChatWindow window, Minecraft minecraft,
                                    int screenWidth, int screenHeight) {
        List<ChatWindow> windows = ChatWindowLayout.windows();
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
                ChatWindow linked = windows.get(i);
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
                            == ChatWindow.LinkSide.LEFT
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

    private static ChatWindow window(List<ChatWindow> windows, String id) {
        for (int index = 0; index < windows.size(); index++) {
            if (windows.get(index).getId().equals(id)) {
                return windows.get(index);
            }
        }
        return null;
    }

    /** The window's box from its stored anchor alone, kept on screen. */
    static Box anchoredBounds(ChatWindow window, Minecraft minecraft,
                              int screenWidth, int screenHeight) {
        int width = windowWidth(window, minecraft);
        double room = roomForLines(currentLines(window, minecraft),
                minecraft);
        double height = heightForRoom(room, minecraft);
        // A stored height the current screen cannot hold — the GUI
        // scale changed under a window resized tall — is capped to what
        // fits between the screen margins by giving up message room; the
        // stored height itself is untouched, so scaling back restores it.
        double maxHeight = Math.max(minHeight(minecraft),
                screenHeight - 2.0D * EDGE_MARGIN);
        if (height > maxHeight) {
            room = Math.max(1.0D, room - (height - maxHeight));
            height = heightForRoom(room, minecraft);
        }
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
    static Box withFill(ChatWindow window, Box resting, Minecraft minecraft,
                        int screenWidth, int screenHeight) {
        ChatWindowFrame frame = ChatWindowFrame.find(window.getId());
        if (frame == null || !frame.hasSeenFill()) {
            return window.getFill() == ChatWindow.ScreenFill.NONE ? resting
                    : fillBounds(window.getFill(), minecraft, screenWidth,
                            screenHeight);
        }
        ChatWindowPlacement.Box from = frame.fillLegFrom();
        ChatWindow.ScreenFill to = frame.fillLegTo();
        double share = frame.fillShare();
        if (from == null && to == ChatWindow.ScreenFill.NONE) {
            return resting;
        }
        Box start = from == null ? resting : from;
        Box end = to == ChatWindow.ScreenFill.NONE ? resting
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
    static ChatWindow.ScreenFill columnFill(double left, double right,
                                            int screenWidth) {
        if (screenWidth <= 0) {
            return ChatWindow.ScreenFill.NONE;
        }
        return ChatWindow.ScreenFill.free((left - EDGE_MARGIN) / screenWidth,
                0.0D, (right + EDGE_MARGIN) / screenWidth, 1.0D);
    }

    /**
     * The box a window fills a part of the screen with: that part of the
     * screen with room for the frame round it, so two windows filling
     * neighbouring parts stand a window gap apart, its lines laid out to its
     * width — the whole screen, a half from one side, a quarter from
     * one corner.
     */
    static Box fillBounds(ChatWindow.ScreenFill fill, Minecraft minecraft,
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
    static int fillChatWidth(ChatWindow.ScreenFill fill, Minecraft minecraft,
                             int screenWidth) {
        return Math.max(ChatWindowLayout.MIN_CHAT_WIDTH, chatWidthForBox(
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
    public static int drawnChatWidth(ChatWindow window, Minecraft minecraft,
                                     int screenWidth) {
        int own = chatWidth(window, minecraft);
        ChatWindowFrame frame = ChatWindowFrame.find(window.getId());
        if (frame == null || !frame.hasSeenFill()) {
            return window.getFill() == ChatWindow.ScreenFill.NONE ? own
                    : fillChatWidth(window.getFill(), minecraft, screenWidth);
        }
        ChatWindowPlacement.Box from = frame.fillLegFrom();
        ChatWindow.ScreenFill to = frame.fillLegTo();
        int start = from == null ? own
                : chatWidthForBox(from.width, minecraft);
        int end = to == ChatWindow.ScreenFill.NONE ? own
                : fillChatWidth(to, minecraft, screenWidth);
        return (int)Math.round(start + (end - start) * frame.fillShare());
    }

    /**
     * Keeps the closed feed's box whole on the screen: pushed down where
     * it would cross the top margin, up where it would leave the screen
     * below. The feed is placed in the HUD editor and never hangs off.
     */
    static double keepOnScreen(double baseline, double height, int barHeight,
                               int screenHeight) {
        int margin = HudPlacementLayout.SCREEN_MARGIN;
        double minBaseline = margin + height - barHeight;
        double maxBaseline = Math.max(minBaseline,
                screenHeight - margin - barHeight);
        return Math.max(minBaseline, Math.min(maxBaseline, baseline));
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
    public static double windowPercentX(ChatWindow window, double x,
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
                ChatWindowLayout.clampWindowPercent(percent));
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
            return ChatWindowLayout.clampWindowPercent(
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
    public static double windowPercentY(ChatWindow window, double baseline,
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
    public static double baselineForRowTop(ChatWindow window,
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
    public static Anchor constrainWindow(ChatWindow window,
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

    /** The share of the screen the closed feed may fill at most. */
    private static final double FEED_HEIGHT_SHARE = 1.0D / 3.0D;

    /**
     * The most message lines the closed feed shows: as many whole ones
     * as fit in {@link #FEED_HEIGHT_SHARE} of the screen, measured with
     * the stride the renderer draws them at, so the feed grows with the
     * screen and the GUI scale without any line changing height. The
     * fraction rarely divides evenly, and the remainder is left as
     * margin rather than spent on a line that would be clipped. Falls
     * back to the game's own chat height when the screen cannot be
     * measured. Windows are unaffected: they follow {@link #lineCap}.
     */
    public static int feedLineCapacity(Minecraft minecraft) {
        int screenHeight = scaledScreenHeight(minecraft);
        if (screenHeight <= 0) {
            GuiNewChat chat = chat(minecraft);
            return chat == null ? 10
                    : LostTalesChatOverlayRenderer.visibleLineCount(chat);
        }
        return Math.max(1, (int)(screenHeight * FEED_HEIGHT_SHARE
                / lineStride(minecraft)));
    }

    /**
     * The feed's box height: the stack it currently holds, in lines and
     * fractions of one, at least one and at most its
     * {@link #feedLineCapacity}, measured with the same stride the
     * renderer draws them at.
     */
    public static double feedHeight(Minecraft minecraft) {
        ChatWindowFrame frame = ChatWindowFrame.feed();
        double lines = 1.0D;
        if (frame.lines != null) {
            lines = Math.max(1.0D, Math.min(feedLineCapacity(minecraft),
                    frame.contentLines()));
        }
        return roomForLines(lines, minecraft);
    }

    /** The closed-chat feed's box for the given screen size. */
    public static Box feedBounds(Minecraft minecraft, int screenWidth,
                                 int screenHeight) {
        int width = windowWidth(minecraft);
        double height = feedHeight(minecraft);
        double baseline = keepOnScreen(feedBaselineFor(
                ChatWindowLayout.feedOffsetY(), minecraft, screenHeight),
                height, 0, screenHeight);
        return new Box(position(ChatWindowLayout.feedOffsetX(), screenWidth,
                width), baseline - height, width, height, 0, height);
    }

    /** The feed's baseline for a percent; its smallest box is one line. */
    public static double feedBaselineFor(double percent, Minecraft minecraft,
                                         int screenHeight) {
        int minHeight = lineHeight(minecraft);
        return position(percent, screenHeight, minHeight) + minHeight;
    }

    /** The feed's percent for a left edge: the margin alone, no frame. */
    public static double feedPercentX(double x, Minecraft minecraft,
                                      int screenWidth) {
        return percent(x, screenWidth, windowWidth(minecraft));
    }

    public static double feedPercentY(double baseline, Minecraft minecraft,
                                      int screenHeight) {
        int minHeight = lineHeight(minecraft);
        return percent(baseline - minHeight, screenHeight, minHeight);
    }

    /**
     * The chat width, in GUI pixels, that gives a window box of about
     * {@code boxWidth}: the inverse of the box width
     * {@link ChatWindowFrame#boxWidth} derives from it.
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
     * The narrowest a window may be dragged, in the chat's own units:
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
        return Math.max(ChatWindowLayout.MIN_CHAT_WIDTH,
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
                                     ChatWindow window) {
        int row = ChatChannelTabBar.chatWidthForNarrowestRow(minecraft,
                window);
        return boxWidthForChatWidth(
                Math.max(minChatWidth(minecraft), row), minecraft);
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
     * {@link ChatWindowLayout#clampWindowPercent}.
     */
    static double position(double percent, int screenSize, int elementSize) {
        return position(percent, screenSize, elementSize,
                HudPlacementLayout.SCREEN_MARGIN);
    }

    /** As above, the element kept {@code margin} from the screen's edges. */
    static double position(double percent, int screenSize, int elementSize,
                           int margin) {
        double travel = Math.max(0, screenSize - elementSize - margin * 2);
        double bounded = ChatWindowLayout.clampWindowPercent(percent);
        if (bounded < 0.0D) {
            return margin + elementSize * bounded / 100.0D;
        }
        if (bounded > 100.0D) {
            return margin + travel + elementSize * (bounded - 100.0D) / 100.0D;
        }
        return margin + travel * bounded / 100.0D;
    }

    /** The inverse of {@link #position}. */
    static double percent(double position, int screenSize, int elementSize) {
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
            return ChatWindowLayout.clampWindowPercent(
                    offset * 100.0D / elementSize);
        }
        if (offset > travel) {
            return ChatWindowLayout.clampWindowPercent(
                    100.0D + (offset - travel) * 100.0D / elementSize);
        }
        return travel <= 0.0D ? 0.0D
                : ChatWindowLayout.clampWindowPercent(offset * 100.0D / travel);
    }

    private static GuiNewChat chat(Minecraft minecraft) {
        return minecraft == null || minecraft.ingameGUI == null
                ? null : minecraft.ingameGUI.getChatGUI();
    }
}
