package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.hud.HudPlacementLayout;
import java.util.ArrayList;
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
 * its lines go; the stored position never changes. Windows never overlap:
 * other windows are walls exactly like the screen edges, for dragging and
 * for growth alike; only a window linked to the growing one moves with
 * it.
 *
 * <p>The closed-chat feed — one stack of every open, unmuted channel's
 * messages, shown only while the chat is closed — is placed the same way
 * by its own baseline, with no row and no bar.</p>
 *
 * <p>Boxes are computed in fractional pixels with the same margin as
 * {@link HudPlacementLayout}, so a dragged window moves as smoothly as
 * the mouse instead of stepping by whole GUI pixels.</p>
 */
public final class ChatWindowPlacement {
    /** Height of a window's input bar: the same as its control strip. */
    public static final int INPUT_HEIGHT = ChatChannelTabBar.ROW_HEIGHT;

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

        boolean overlaps(double left, double top, double right,
                         double bottom) {
            return left < right() && right > this.x
                    && top < bottom() && bottom > this.y;
        }

        /** The box grown by {@code by} on every side: a wall with its gap. */
        Box inflated(int by) {
            return new Box(this.x - by, this.y - by, this.width + by * 2,
                    this.height + by * 2, this.barHeight);
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
     * The window's box for the given screen size, with the other windows
     * taken into account. A window linked to this one moves with it; any
     * other window above is a border exactly like the screen edge, so a
     * window growing upward into it stops growing — its box holds fewer
     * lines — rather than overlap. A linked window that meets its own
     * ceiling stops this window the same way. Stored anchors never
     * change; it is all recomputed every frame and undoes itself as
     * lines go.
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
        // A linked window takes its place from its target first — above
        // it or below it, a margin apart — following chains in passes.
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
                // Its own ceiling: the top margin, or any window above
                // the place it wants that it would otherwise run into.
                double ceiling = margin + lines[i] * lineHeight + row;
                for (int v = 0; v < count; v++) {
                    if (v != i && v != t && baseline[v] < wanted
                            && besides(x, width, i, v, margin)) {
                        ceiling = Math.max(ceiling, baseline[v] + barHeight
                                + margin + lines[i] * lineHeight + row);
                    }
                }
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
        // Bottom-most first: each window settles against the ones above.
        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) {
            order[i] = Integer.valueOf(i);
        }
        java.util.Arrays.sort(order, new java.util.Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return Double.compare(baseline[b.intValue()],
                        baseline[a.intValue()]);
            }
        });
        for (Integer wObject : order) {
            int w = wObject.intValue();
            for (Integer uObject : order) {
                int u = uObject.intValue();
                if (u == w || baseline[u] >= baseline[w]
                        || !besides(x, width, w, u, margin)) {
                    continue;
                }
                double top = baseline[w] - lines[w] * lineHeight - row;
                double needed = baseline[u] + barHeight + margin - top;
                if (needed <= 0.0D) {
                    continue;
                }
                // The window above is a border, exactly like the screen
                // edge: this window stops growing and shows what fits. (A
                // window linked to this one has already moved with it.)
                double room = baseline[w] - row
                        - (baseline[u] + barHeight + margin);
                lines[w] = Math.max(1, Math.min(lines[w],
                        (int)Math.floor(room / lineHeight)));
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

    private static boolean besides(double[] x, int width, int a, int b,
                                   int margin) {
        return x[a] < x[b] + width + margin && x[a] + width + margin > x[b];
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
     * Keeps a window's requested position on screen and off the other
     * windows: the whole box as it currently shows stays inside the
     * margins, and {@code walls} — the other windows' boxes, each kept at
     * the same distance as the margins — stop it like the margins do.
     * Each axis is resolved on its own from the window's
     * current position, so a dragged window slides along a wall instead
     * of sticking to it; a window that already overlaps a wall (it grew
     * into it) may always move away. {@code window} null means a window
     * about to be created, at its smallest, placed clear of the walls.
     */
    public static Anchor constrainWindow(ChatWindow window,
                                         Minecraft minecraft,
                                         double x, double baseline,
                                         int screenWidth, int screenHeight,
                                         List<Box> walls) {
        int margin = HudPlacementLayout.SCREEN_MARGIN;
        int width = windowWidth(minecraft);
        int height = window == null ? minHeight(minecraft)
                : currentHeight(window, minecraft);
        int barHeight = barHeight(minecraft);
        double maxX = Math.max(margin, screenWidth - width - margin);
        double minBaseline = margin + height - barHeight;
        double maxBaseline = Math.max(minBaseline,
                screenHeight - margin - barHeight);
        double targetX = Math.max(margin, Math.min(maxX, x));
        double targetBaseline = Math.max(minBaseline,
                Math.min(maxBaseline, baseline));
        if (walls == null || walls.isEmpty()) {
            return new Anchor(targetX, targetBaseline);
        }
        List<Box> spaced = new ArrayList<Box>(walls.size());
        for (Box wall : walls) {
            spaced.add(wall.inflated(margin));
        }
        walls = spaced;
        int above = height - barHeight;
        if (window == null) {
            return placeClear(targetX, targetBaseline, width, above,
                    barHeight, margin, maxX, minBaseline, maxBaseline, walls);
        }
        Box current = anchoredBounds(window, minecraft, screenWidth,
                screenHeight);
        double fromX = current.x;
        double fromBaseline = current.baseline();
        // Walls the window already sits on do not hold it; it may leave.
        List<Box> solid = new ArrayList<Box>(walls.size());
        for (Box wall : walls) {
            if (!wall.overlaps(fromX, fromBaseline - above,
                    fromX + width, fromBaseline + barHeight)) {
                solid.add(wall);
            }
        }
        if (!overlapsAny(solid, targetX, targetBaseline, width, above,
                barHeight)) {
            return new Anchor(targetX, targetBaseline);
        }
        // Slide along the walls: resolve one axis at the other's current
        // value, then the second; both orders are tried and the result
        // nearer the target wins, so a corner is rounded either way.
        double xFirst = slideX(targetX, fromBaseline, fromX, width, above,
                barHeight, margin, maxX, solid);
        double xFirstBaseline = slideY(targetBaseline, xFirst, fromBaseline,
                width, above, barHeight, minBaseline, maxBaseline, solid);
        double yFirstBaseline = slideY(targetBaseline, fromX, fromBaseline,
                width, above, barHeight, minBaseline, maxBaseline, solid);
        double yFirst = slideX(targetX, yFirstBaseline, fromX, width, above,
                barHeight, margin, maxX, solid);
        boolean aClear = !overlapsAny(solid, xFirst, xFirstBaseline, width,
                above, barHeight);
        boolean bClear = !overlapsAny(solid, yFirst, yFirstBaseline, width,
                above, barHeight);
        double aDistance = Math.abs(xFirst - targetX)
                + Math.abs(xFirstBaseline - targetBaseline);
        double bDistance = Math.abs(yFirst - targetX)
                + Math.abs(yFirstBaseline - targetBaseline);
        if (aClear && (!bClear || aDistance <= bDistance)) {
            return new Anchor(xFirst, xFirstBaseline);
        }
        if (bClear) {
            return new Anchor(yFirst, yFirstBaseline);
        }
        // Boxed in on both axes: stay where the window is.
        return new Anchor(fromX, fromBaseline);
    }

    private static double slideX(double targetX, double baseline,
                                 double fromX, int width, int above,
                                 int barHeight, int margin, double maxX,
                                 List<Box> walls) {
        double x = targetX;
        for (int pass = 0; pass < walls.size(); pass++) {
            boolean moved = false;
            for (Box wall : walls) {
                if (wall.overlaps(x, baseline - above, x + width,
                        baseline + barHeight)) {
                    x = x > fromX ? Math.min(x, wall.x - width)
                            : Math.max(x, wall.right());
                    moved = true;
                }
            }
            if (!moved) {
                break;
            }
        }
        return Math.max(margin, Math.min(maxX, x));
    }

    private static double slideY(double targetBaseline, double x,
                                 double fromBaseline, int width, int above,
                                 int barHeight, double minBaseline,
                                 double maxBaseline, List<Box> walls) {
        double baseline = targetBaseline;
        for (int pass = 0; pass < walls.size(); pass++) {
            boolean moved = false;
            for (Box wall : walls) {
                if (wall.overlaps(x, baseline - above, x + width,
                        baseline + barHeight)) {
                    baseline = baseline > fromBaseline
                            ? Math.min(baseline, wall.y - barHeight)
                            : Math.max(baseline, wall.bottom() + above);
                    moved = true;
                }
            }
            if (!moved) {
                break;
            }
        }
        return Math.max(minBaseline, Math.min(maxBaseline, baseline));
    }

    /** The nearest clear spot for a brand-new window, by least push. */
    private static Anchor placeClear(double x, double baseline, int width,
                                     int above, int barHeight, int margin,
                                     double maxX, double minBaseline,
                                     double maxBaseline, List<Box> walls) {
        double bestX = x;
        double bestBaseline = baseline;
        double bestDistance = Double.MAX_VALUE;
        if (!overlapsAny(walls, x, baseline, width, above, barHeight)) {
            return new Anchor(x, baseline);
        }
        for (Box wall : walls) {
            double[][] candidates = {
                    {wall.x - width, baseline},
                    {wall.right(), baseline},
                    {x, wall.y - barHeight},
                    {x, wall.bottom() + above},
            };
            for (double[] candidate : candidates) {
                double cx = Math.max(margin, Math.min(maxX, candidate[0]));
                double cb = Math.max(minBaseline,
                        Math.min(maxBaseline, candidate[1]));
                if (overlapsAny(walls, cx, cb, width, above, barHeight)) {
                    continue;
                }
                double distance = Math.abs(cx - x) + Math.abs(cb - baseline);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestX = cx;
                    bestBaseline = cb;
                }
            }
        }
        return new Anchor(bestX, bestBaseline);
    }

    private static boolean overlapsAny(List<Box> walls, double x,
                                       double baseline, int width, int above,
                                       int barHeight) {
        for (Box wall : walls) {
            if (wall.overlaps(x, baseline - above, x + width,
                    baseline + barHeight)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The boxes of every window but {@code except} and the windows
     * linked to it (they follow it, so they cannot block it), for the
     * editor.
     */
    public static List<Box> wallsExcept(ChatWindow except, Minecraft minecraft,
                                        int screenWidth, int screenHeight) {
        List<ChatWindow> windows = ChatWindowLayout.windows();
        List<Box> walls = new ArrayList<Box>(windows.size());
        for (int index = 0; index < windows.size(); index++) {
            ChatWindow other = windows.get(index);
            if (other != except && (except == null
                    || !except.getId().equals(other.getLinkTarget()))) {
                walls.add(windowBounds(windows.get(index), minecraft,
                        screenWidth, screenHeight));
            }
        }
        return walls;
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
