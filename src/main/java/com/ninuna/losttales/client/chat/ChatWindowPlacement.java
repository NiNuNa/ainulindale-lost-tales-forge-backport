package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.hud.HudPlacementLayout;
import net.minecraft.util.MathHelper;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import org.lwjgl.input.Mouse;

/**
 * Where chat windows sit on screen, for the chat itself and for the HUD
 * placement editor alike. A window is one unit — tab row, messages and
 * its own input bar, all the same width — anchored by its <em>baseline</em>,
 * the edge the newest message sits on: messages grow upward from it and
 * the input bar hangs below it, so a window never moves when a message
 * arrives. The stored position is the baseline's percent of its travel
 * (with the box at its smallest, one empty line), and the visible box is
 * only as tall as what the window currently shows, so a short window can
 * be placed anywhere on the screen, its top edge included. A window that
 * grows past the top margin is pushed down just far enough to stay on
 * screen — it grows downward from there — and returns to its anchor as
 * its lines go; the stored position never changes. Windows keep off the
 * screen edges only: they may overlap one another — the one in use is
 * drawn in front — and only a window linked to a growing one moves with
 * it.
 *
 * <p>The closed-chat feed — one stack of every unmuted channel's
 * messages, shown only while the chat is closed — is placed the same way
 * by its own baseline, with no row and no bar.</p>
 *
 * <p>Boxes are computed in fractional pixels with the same margin as
 * {@link HudPlacementLayout}, so a dragged window moves as smoothly as
 * the mouse instead of stepping by whole GUI pixels.</p>
 */
public final class ChatWindowPlacement {
    /** Height of a window's bar strip: exactly the tab row's, so the
     *  window is framed by two strips of one height, each carrying its
     *  rule on the edge facing the messages — the tab strip's last row
     *  is the top rule, the bar strip's first row the bottom one.
     *  <p>The strips are deliberately UI chrome and do not follow the
     *  vanilla chat-scale setting, exactly as vanilla's own input line
     *  does not: the setting scales what is read (the message stride,
     *  and with it the trailing strip), never what is operated. That
     *  asymmetry is a decision, not an oversight.</p> */
    public static final int INPUT_HEIGHT = ChatChannelTabBar.ROW_HEIGHT;
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
        public final int height;
        /** What hangs below the baseline: the padding and the bar. */
        public final int barHeight;
        /**
         * Pixels the box has for message lines. It is the height the
         * window was resized to rather than a whole number of lines, so
         * the topmost line is clipped where the room ends.
         */
        public final int room;

        Box(double x, double y, int width, int height, int barHeight) {
            this(x, y, width, height, barHeight, 0);
        }

        Box(double x, double y, int width, int height, int barHeight,
            int room) {
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
     */
    public static int chatWidth(ChatWindow window, Minecraft minecraft) {
        int own = window == null ? 0 : window.getWidth();
        if (own > 0 && ChatWindowLines.isAvailable()) {
            return own;
        }
        return chatWidth(minecraft);
    }

    /** The box width of the feed, and of a window without one of its own. */
    public static int windowWidth(Minecraft minecraft) {
        GuiNewChat chat = chat(minecraft);
        return chat == null ? 160 : ChatWindowFrame.boxWidth(chat);
    }

    /** The box width of one window, at its own chat width. */
    public static int windowWidth(ChatWindow window, Minecraft minecraft) {
        int own = window == null ? 0 : window.getWidth();
        if (own <= 0 || !ChatWindowLines.isAvailable()) {
            return windowWidth(minecraft);
        }
        return (int)Math.round(boxWidthForChatWidth(own, minecraft));
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
     *  is the window's top rule. Chrome, so it keeps its size at every
     *  chat scale; see {@link #INPUT_HEIGHT}. */
    public static int rowHeight(Minecraft minecraft) {
        return ChatChannelTabBar.ROW_HEIGHT;
    }

    /** What hangs below the baseline: the trailing strip — one line of
     *  always visible room between the newest message and the bottom
     *  rule, where the typing line lives — and the bar strip, whose
     *  first pixel row is the window's bottom rule. */
    public static int barHeight(Minecraft minecraft) {
        return lineHeight(minecraft) + INPUT_HEIGHT;
    }

    /** The smallest box: tab row, one empty line, trailing strip, bar. */
    public static int minHeight(Minecraft minecraft) {
        return rowHeight(minecraft) + lineHeight(minecraft)
                + barHeight(minecraft);
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
     * The lines the window currently shows: those its view holds, at
     * least one, at most its {@link #lineCap}. A window still filling up
     * is as tall as the whole lines it holds; a full one keeps the
     * fraction of a line the player dragged it to. A window not drawn
     * yet shows one.
     */
    public static double currentLines(ChatWindow window,
                                      Minecraft minecraft) {
        double cap = lineCap(window, minecraft);
        ChatWindowFrame frame = window == null ? null
                : ChatWindowFrame.find(window.getId());
        if (frame == null || frame.lines == null) {
            return 1.0D;
        }
        return Math.max(1.0D, Math.min(cap, frame.lines.size()));
    }

    /** Pixels of message room {@code lines} lines take at this scale. */
    public static int roomForLines(double lines, Minecraft minecraft) {
        return Math.max(1, (int)Math.round(
                Math.max(1.0D, lines) * lineStride(minecraft)));
    }

    /** The box height a window with {@code room} pixels of lines takes. */
    public static int heightForRoom(int room, Minecraft minecraft) {
        return rowHeight(minecraft) + Math.max(1, room)
                + barHeight(minecraft);
    }

    /** The box height a window of {@code lines} message lines takes. */
    public static int heightForLines(double lines, Minecraft minecraft) {
        return heightForRoom(roomForLines(lines, minecraft), minecraft);
    }

    /**
     * The lines a box of {@code height} pixels holds: the inverse of
     * {@link #heightForLines}, fractions included, never fewer than one.
     */
    public static double linesForHeight(double height, Minecraft minecraft) {
        return Math.max(1.0D, (height - rowHeight(minecraft)
                - barHeight(minecraft)) / lineStride(minecraft));
    }

    /** The box height the window currently shows: row, lines and bar. */
    public static int currentHeight(ChatWindow window, Minecraft minecraft) {
        return heightForLines(currentLines(window, minecraft), minecraft);
    }

    /**
     * The window's box for the given screen size. A window linked to
     * another takes its place from its target — a margin above or below
     * it, following chains — and is kept on screen like any other. No
     * window is a border for another: windows may overlap, and a growing
     * one never loses lines to a neighbour. Stored anchors never change;
     * it is all recomputed every frame and undoes itself as lines go.
     */
    public static Box windowBounds(ChatWindow window, Minecraft minecraft,
                                   int screenWidth, int screenHeight) {
        List<ChatWindow> windows = ChatWindowLayout.windows();
        int count = windows.size();
        int index = windows.indexOf(window);
        if (index < 0) {
            return anchoredBounds(window, minecraft, screenWidth,
                    screenHeight);
        }
        int margin = HudPlacementLayout.SCREEN_MARGIN;
        int row = rowHeight(minecraft);
        int barHeight = barHeight(minecraft);
        double[] x = new double[count];
        double[] baseline = new double[count];
        int[] room = new int[count];
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
        // below it, a margin apart — following chains in passes, and
        // stops at the screen margins like any window.
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
                            ? x[t] - margin - widths[i]
                            : x[t] + widths[t] + margin;
                    wantedX = Math.max(margin, Math.min(
                            screenWidth - margin - widths[i], wantedX));
                    if (wantedX != x[i]) {
                        x[i] = wantedX;
                        moved = true;
                    }
                    continue;
                }
                double wanted = linked.isLinkedAbove()
                        ? baseline[t] - room[t] - row - margin - barHeight
                        : baseline[t] + barHeight + margin + room[i] + row;
                double ceiling = margin + room[i] + row;
                wanted = Math.max(ceiling,
                        Math.min(screenHeight - margin - barHeight, wanted));
                if (wanted != baseline[i]) {
                    baseline[i] = wanted;
                    moved = true;
                }
            }
            if (!moved) {
                break;
            }
        }
        int height = heightForRoom(room[index], minecraft);
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
        int room = roomForLines(currentLines(window, minecraft), minecraft);
        int height = heightForRoom(room, minecraft);
        int barHeight = barHeight(minecraft);
        double baseline = keepOnScreen(baselineFor(window.getOffsetY(),
                minecraft, screenHeight), height, barHeight, screenHeight);
        return new Box(position(window.getOffsetX(), screenWidth, width),
                baseline - (height - barHeight), width, height, barHeight,
                room);
    }

    /**
     * Pushes a baseline down when the box above it would cross the top
     * margin, as far as the bottom margin allows, so growth that would
     * leave the screen turns downward instead.
     */
    static double keepOnScreen(double baseline, int height, int barHeight,
                               int screenHeight) {
        int margin = HudPlacementLayout.SCREEN_MARGIN;
        double minBaseline = margin + height - barHeight;
        double maxBaseline = Math.max(minBaseline,
                screenHeight - margin - barHeight);
        return Math.max(minBaseline, Math.min(maxBaseline, baseline));
    }

    public static double windowPercentX(double x, Minecraft minecraft,
                                        int screenWidth) {
        return percent(x, screenWidth, windowWidth(minecraft));
    }

    /** As above for a window that has a width of its own. */
    public static double windowPercentX(ChatWindow window, double x,
                                        Minecraft minecraft,
                                        int screenWidth) {
        return percent(x, screenWidth, windowWidth(window, minecraft));
    }

    /**
     * The baseline for a percent: 0 puts the smallest box against the
     * top margin, 100 puts the bar against the bottom margin.
     */
    public static double baselineFor(double percent, Minecraft minecraft,
                                     int screenHeight) {
        int minHeight = minHeight(minecraft);
        return position(percent, screenHeight, minHeight)
                + minHeight - barHeight(minecraft);
    }

    public static double windowPercentY(double baseline, Minecraft minecraft,
                                        int screenHeight) {
        int minHeight = minHeight(minecraft);
        return percent(baseline - (minHeight - barHeight(minecraft)),
                screenHeight, minHeight);
    }

    /**
     * Keeps a window's requested position on screen: the whole box as it
     * currently shows stays inside the margins. Other windows do not
     * hold it; windows may overlap. {@code window} null means a window
     * about to be created, at its smallest.
     */
    public static Anchor constrainWindow(ChatWindow window,
                                         Minecraft minecraft,
                                         double x, double baseline,
                                         int screenWidth, int screenHeight) {
        int margin = HudPlacementLayout.SCREEN_MARGIN;
        int width = windowWidth(window, minecraft);
        int height = window == null ? minHeight(minecraft)
                : currentHeight(window, minecraft);
        int barHeight = barHeight(minecraft);
        double maxX = Math.max(margin, screenWidth - width - margin);
        double minBaseline = margin + height - barHeight;
        double maxBaseline = Math.max(minBaseline,
                screenHeight - margin - barHeight);
        return new Anchor(Math.max(margin, Math.min(maxX, x)),
                Math.max(minBaseline, Math.min(maxBaseline, baseline)));
    }

    /**
     * The feed's box height: the lines it currently holds, at least one.
     * The feed is not resizable, so it is always a whole number of them,
     * measured with the same stride the renderer draws them at.
     */
    public static int feedHeight(Minecraft minecraft) {
        GuiNewChat chat = chat(minecraft);
        ChatWindowFrame frame = ChatWindowFrame.feed();
        int lines = 1;
        if (frame.lines != null && chat != null) {
            lines = Math.max(1, Math.min(
                    LostTalesChatOverlayRenderer.visibleLineCount(chat),
                    frame.lines.size()));
        }
        return roomForLines(lines, minecraft);
    }

    /** The closed-chat feed's box for the given screen size. */
    public static Box feedBounds(Minecraft minecraft, int screenWidth,
                                 int screenHeight) {
        int width = windowWidth(minecraft);
        int height = feedHeight(minecraft);
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

    public static double feedPercentY(double baseline, Minecraft minecraft,
                                      int screenHeight) {
        int minHeight = lineHeight(minecraft);
        return percent(baseline - minHeight, screenHeight, minHeight);
    }

    /** {@code Chat: Party, OOC} — a window named by its tabs. */
    public static String displayName(ChatWindow window) {
        StringBuilder name = new StringBuilder("Chat: ");
        List<ChatTab> tabs = window.getTabs();
        for (int index = 0; index < tabs.size(); index++) {
            if (index > 0) {
                name.append(", ");
            }
            name.append(ClientChatChannelState.displayName(tabs.get(index)));
        }
        return name.toString();
    }

    /**
     * The pointer in fractional GUI pixels, from the raw mouse, so a drag
     * is not quantised to whole GUI pixels at higher GUI scales.
     */
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
     * Gives one window a width of its own. The window lays its own lines
     * out at it, so nothing else in the chat — no other window, and not
     * the closed-chat feed — changes with it.
     */
    public static void applyWindowWidth(Minecraft minecraft,
                                        ChatWindow window, int chatWidth) {
        if (window == null) {
            return;
        }
        if (!ChatWindowLines.isAvailable()) {
            ChatWindowLines.logUnavailableOnce();
            return;
        }
        ChatWindowLayout.setWindowWidth(window.getId(), chatWidth, true);
    }

    public static double preciseMouseX(Minecraft minecraft, int screenWidth) {
        return minecraft == null || minecraft.displayWidth <= 0 ? 0.0D
                : Mouse.getX() * (double)screenWidth / minecraft.displayWidth;
    }

    public static double preciseMouseY(Minecraft minecraft, int screenHeight) {
        return minecraft == null || minecraft.displayHeight <= 0 ? 0.0D
                : screenHeight - Mouse.getY() * (double)screenHeight
                        / minecraft.displayHeight - 1.0D;
    }

    /** Position of an element's leading edge for a percent of its travel. */
    static double position(double percent, int screenSize, int elementSize) {
        int margin = HudPlacementLayout.SCREEN_MARGIN;
        double travel = Math.max(0, screenSize - elementSize - margin * 2);
        double bounded = ChatWindowLayout.clampPercent(percent);
        return margin + travel * bounded / 100.0D;
    }

    /** The inverse of {@link #position}, clamped to the travel. */
    static double percent(double position, int screenSize, int elementSize) {
        int margin = HudPlacementLayout.SCREEN_MARGIN;
        double travel = Math.max(0, screenSize - elementSize - margin * 2);
        if (travel <= 0.0D) {
            return 0.0D;
        }
        return ChatWindowLayout.clampPercent(
                (position - margin) * 100.0D / travel);
    }

    private static GuiNewChat chat(Minecraft minecraft) {
        return minecraft == null || minecraft.ingameGUI == null
                ? null : minecraft.ingameGUI.getChatGUI();
    }
}
