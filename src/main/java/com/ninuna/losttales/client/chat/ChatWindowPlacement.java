package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.hud.HudPlacementLayout;
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
    /** Height of a window's input bar: one text row with a margin. */
    public static final int INPUT_HEIGHT = 13;

    private ChatWindowPlacement() {}

    /** A box in fractional GUI pixels. */
    public static final class Box {
        public final double x;
        public final double y;
        public final int width;
        public final int height;
        /** What hangs below the baseline: one line's gap and the bar. */
        public final int barHeight;
        /** Lines the box has room for; a window stopped growing has fewer. */
        public final int lines;

        Box(double x, double y, int width, int height, int barHeight) {
            this(x, y, width, height, barHeight, 0);
        }

        Box(double x, double y, int width, int height, int barHeight,
            int lines) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.barHeight = barHeight;
            this.lines = lines;
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

        /** Top of the input bar: one chat line below the baseline. */
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

    public static int windowWidth(Minecraft minecraft) {
        GuiNewChat chat = chat(minecraft);
        return chat == null ? 160 : ChatWindowFrame.boxWidth(chat);
    }

    /** One message line at the chat scale. */
    public static int lineHeight(Minecraft minecraft) {
        GuiNewChat chat = chat(minecraft);
        float scale = chat == null ? 1.0F : chat.func_146244_h();
        return Math.max(1, Math.round(
                LostTalesChatOverlayRenderer.LINE_HEIGHT * scale));
    }

    /** Gap between the newest message and the input bar: one chat line. */
    public static int inputGap(Minecraft minecraft) {
        return lineHeight(minecraft);
    }

    /** The backdrop padding above the top line and below the newest. */
    public static int linePadding(Minecraft minecraft) {
        GuiNewChat chat = chat(minecraft);
        float scale = chat == null ? 1.0F : chat.func_146244_h();
        return Math.max(1, Math.round(
                LostTalesChatOverlayRenderer.LINE_PADDING * scale));
    }

    /** What stands above the lines: the tab row and the top padding. */
    public static int rowHeight(Minecraft minecraft) {
        return ChatChannelTabBar.ROW_HEIGHT + linePadding(minecraft);
    }

    /** What hangs below the baseline: padding, the gap and the bar. */
    public static int barHeight(Minecraft minecraft) {
        return linePadding(minecraft) + inputGap(minecraft) + INPUT_HEIGHT;
    }

    /** The smallest box: tab row, one empty line, gap, input bar. */
    public static int minHeight(Minecraft minecraft) {
        return rowHeight(minecraft) + lineHeight(minecraft)
                + barHeight(minecraft);
    }

    /**
     * The lines the window currently shows: those its view holds, at
     * least one, at most the chat height setting. A window not drawn yet
     * shows one.
     */
    public static int currentLines(ChatWindow window, Minecraft minecraft) {
        GuiNewChat chat = chat(minecraft);
        int cap = chat == null ? 20
                : LostTalesChatOverlayRenderer.visibleLineCount(chat);
        ChatWindowFrame frame = window == null ? null
                : ChatWindowFrame.find(window.getId());
        if (frame == null || frame.lines == null) {
            return 1;
        }
        return Math.max(1, Math.min(cap, frame.lines.size()));
    }

    /** The box height the window currently shows: row, lines and bar. */
    public static int currentHeight(ChatWindow window, Minecraft minecraft) {
        return heightFor(currentLines(window, minecraft), minecraft);
    }

    private static int heightFor(int lines, Minecraft minecraft) {
        return rowHeight(minecraft) + lines * lineHeight(minecraft)
                + barHeight(minecraft);
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
        int lineHeight = lineHeight(minecraft);
        int barHeight = barHeight(minecraft);
        double[] x = new double[count];
        double[] baseline = new double[count];
        int[] lines = new int[count];
        int width = windowWidth(minecraft);
        for (int i = 0; i < count; i++) {
            Box box = anchoredBounds(windows.get(i), minecraft, screenWidth,
                    screenHeight);
            x[i] = box.x;
            baseline[i] = box.baseline();
            lines[i] = box.lines;
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
                double wanted = linked.isLinkedAbove()
                        ? baseline[t] - lines[t] * lineHeight - row - margin
                                - barHeight
                        : baseline[t] + barHeight + margin
                                + lines[i] * lineHeight + row;
                double ceiling = margin + lines[i] * lineHeight + row;
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
        int height = heightFor(lines[index], minecraft);
        return new Box(x[index], baseline[index] - (height - barHeight),
                width, height, barHeight, lines[index]);
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
        int width = windowWidth(minecraft);
        int lines = currentLines(window, minecraft);
        int height = heightFor(lines, minecraft);
        int barHeight = barHeight(minecraft);
        double baseline = keepOnScreen(baselineFor(window.getOffsetY(),
                minecraft, screenHeight), height, barHeight, screenHeight);
        return new Box(position(window.getOffsetX(), screenWidth, width),
                baseline - (height - barHeight), width, height, barHeight,
                lines);
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
        int width = windowWidth(minecraft);
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

    /** The feed's box height: the lines it currently holds, at least one. */
    public static int feedHeight(Minecraft minecraft) {
        GuiNewChat chat = chat(minecraft);
        ChatWindowFrame frame = ChatWindowFrame.feed();
        int lines = 1;
        if (frame.lines != null && chat != null) {
            lines = Math.max(1, Math.min(
                    LostTalesChatOverlayRenderer.visibleLineCount(chat),
                    frame.lines.size()));
        }
        return lines * lineHeight(minecraft);
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
                width), baseline - height, width, height, 0);
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
