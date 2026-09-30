package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiEasing;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiRegionBlur;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.config.LostTalesConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

/**
 * The windows pinned to the screen while playing. Each stands where it
 * stands on the window screen, in the same shape, so nothing moves as the
 * screen opens and closes: its row shows the pinned tabs, and under it
 * the content of the one in front, a little fainter than on the window
 * screen ({@code pinnedWindowOpacity}). Nothing on it can be pressed, so
 * the row's controls, the tool strip's and the bar's are left out.
 *
 * <p>The arrow keys, held with the mod's Modifier Key, walk the tabs of
 * one pinned window and step from one pinned window to the next; the
 * window they reach shows at full strength a moment. With the window
 * screen open its windows are the screen's own, and nothing is drawn from
 * here.</p>
 */
public final class PinnedWindows {
    /** What draws a window's conversation under its row: the chat's. */
    public interface ConversationPainter {
        void draw(Minecraft minecraft, Window window, int screenWidth,
                  int screenHeight, LostTalesGuiAnimationSample shown);
    }

    /** How long a window stays lit after the keys reach it, or the screen closes. */
    private static final long LIT_NANOS = 600L * 1000000L;

    private static ConversationPainter painter;
    /** A row registers what it draws; while playing nothing answers the pointer. */
    private static final PointerRegions REGIONS = new PointerRegions();
    /** The pages drawn from here last frame, which are told when they leave. */
    private static final List<PageTab> SHOWN = new ArrayList<PageTab>();
    /** The pinned window the keys reach; null for the one in front. */
    private static String reachedId;
    private static long reachedNanos;
    /** When the window screen last closed: every pinned window eases down from full strength. */
    private static long closedNanos;
    private static boolean screenWasOpen;

    private PinnedWindows() {}

    /** The chat says how a conversation's window is drawn. */
    public static void setConversationPainter(ConversationPainter drawn) {
        painter = drawn;
    }

    /**
     * Whether the pinned windows stand on the screen: the player is in a
     * world and the window screen, whose own the windows are while it is
     * open, is not.
     */
    public static boolean standing(Minecraft minecraft) {
        return minecraft != null && minecraft.theWorld != null
                && minecraft.thePlayer != null
                && !(minecraft.currentScreen instanceof WindowScreen);
    }

    /**
     * The tabs of a window that show while playing, in row order: those
     * pinned that can be shown now.
     */
    private static List<WindowTab> pinnedTabs(Window window) {
        List<WindowTab> tabs = new ArrayList<WindowTab>();
        for (WindowTab tab : window.getTabs()) {
            if (tab.isAvailable() && WindowLayout.isPinned(tab)) {
                tabs.add(tab);
            }
        }
        return tabs;
    }

    /**
     * Whether the tab is what a pinned window shows while playing: the
     * pinned tab in front of its window, with no window screen open. Its
     * lines are read there, so the feed leaves them out.
     */
    public static boolean shows(WindowTab tab) {
        if (tab == null || !standing(Minecraft.getMinecraft())) {
            return false;
        }
        Window window = WindowLayout.windowOf(tab);
        if (window == null || !WindowLayout.isPinned(tab)) {
            return false;
        }
        List<WindowTab> tabs = pinnedTabs(window);
        return tab.equals(WindowFrame.activeTab(window, tabs));
    }

    /**
     * How a window comes in as the window screen opens. One with a tab
     * pinned is on screen already: it stays where it stands and only gains
     * its full strength. Every other window takes the screen's entrance.
     */
    static LostTalesGuiAnimationSample entrance(
            Window window, LostTalesGuiAnimationSample opening) {
        if (pinnedTabs(window).isEmpty()) {
            return opening;
        }
        float base = restingStrength();
        return LostTalesGuiAnimationSample.SETTLED.withOpacity(
                base + (1.0F - base) * opening.getOpacity());
    }

    /** The player's share of a window's strength while it stands pinned. */
    private static float restingStrength() {
        return Math.max(0.2F, Math.min(1.0F,
                LostTalesConfig.pinnedWindowOpacity / 100.0F));
    }

    /* ---- Drawing ---- */

    /** Draws every pinned window, back to front, after the rest of the HUD. */
    public static void render(Minecraft minecraft, float partialTicks) {
        long now = System.nanoTime();
        if (!standing(minecraft)) {
            screenWasOpen = minecraft != null
                    && minecraft.currentScreen instanceof WindowScreen;
            leavePages(new ArrayList<PageTab>());
            return;
        }
        if (screenWasOpen) {
            screenWasOpen = false;
            closedNanos = now;
        }
        List<Window> pinned = WindowLayout.pinnedWindows();
        List<PageTab> drawn = new ArrayList<PageTab>();
        if (pinned.isEmpty() || minecraft.gameSettings.hideGUI) {
            leavePages(drawn);
            return;
        }
        ScaledResolution resolution = new ScaledResolution(minecraft,
                minecraft.displayWidth, minecraft.displayHeight);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        if (depthTest) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        // Each window softens what lies behind it, as it does on the window
        // screen: one capture of the frame while anything is pinned.
        if (LostTalesConfig.windowBackgroundBlur
                && LostTalesConfig.enableGuiBackgroundBlur) {
            LostTalesGuiRegionBlur.getInstance().capture(minecraft,
                    partialTicks, (float)LostTalesConfig.guiBlurStrength);
        }
        WindowView.beginPinnedPass();
        try {
            REGIONS.reset();
            for (Window window : pinned) {
                drawWindow(minecraft, window, resolution.getScaledWidth(),
                        resolution.getScaledHeight(), strength(window, now),
                        partialTicks, depthTest, drawn);
            }
        } finally {
            WindowView.endPinnedPass();
            if (depthTest) {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
            }
        }
        leavePages(drawn);
    }

    /**
     * How strong a pinned window shows now: the player's share of its
     * strength on the window screen, up to the whole of it for a moment
     * after the keys reach it or the screen closes. Brightness only, so
     * nothing moves.
     */
    private static float strength(Window window, long now) {
        float base = restingStrength();
        if (!Motions.enabled()) {
            return base;
        }
        float lit = litShare(now - closedNanos);
        if (window.getId().equals(reachedId)) {
            lit = Math.max(lit, litShare(now - reachedNanos));
        }
        return base + (1.0F - base) * lit;
    }

    /** 1 as a window is lit, easing to 0 over {@link #LIT_NANOS}. */
    private static float litShare(long sinceNanos) {
        if (sinceNanos < 0L || sinceNanos >= LIT_NANOS) {
            return 0.0F;
        }
        return 1.0F - LostTalesGuiEasing.smoothStep(
                sinceNanos / (float)LIT_NANOS);
    }

    private static void drawWindow(Minecraft minecraft, Window window,
                                   int screenWidth, int screenHeight,
                                   float strength, float partialTicks,
                                   boolean depthTest, List<PageTab> drawn) {
        List<WindowTab> tabs = WindowFrame.visibleTabs(window);
        if (tabs.isEmpty()) {
            return;
        }
        WindowFrame frame = WindowFrame.of(window);
        LostTalesGuiAnimationSample shown =
                LostTalesGuiAnimationSample.SETTLED.withOpacity(strength);
        WindowTab front = WindowFrame.activeTab(window, tabs);
        if (front instanceof PageTab) {
            WindowDrawing.layOutPage(minecraft, window, frame, (PageTab)front,
                    screenWidth, screenHeight, shown);
        } else if (painter != null) {
            painter.draw(minecraft, window, screenWidth, screenHeight, shown);
        } else {
            frame.drawn = false;
        }
        if (!frame.drawn) {
            return;
        }
        TabRow.Row row = WindowDrawing.rowOf(window, frame, tabs);
        row.bare = true;
        // The strip is one plain band: no well is cut out of it.
        frame.tabBar.setToolStripHole(null);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(row.fractionX, row.fractionY, 0.0F);
            frame.tabBar.draw(minecraft.fontRenderer, REGIONS, row,
                    WindowHover.AWAY, WindowHover.AWAY, strength);
        } finally {
            GL11.glPopMatrix();
        }
        WindowDrawing.drawFrameSurface(minecraft, frame, shown);
        if (frame.page != null) {
            PageContent content = frame.page.content();
            if (!SHOWN.contains(frame.page)) {
                content.shown();
            }
            drawn.add(frame.page);
            WindowDrawing.drawPage(minecraft, frame, shown, WindowHover.AWAY,
                    WindowHover.AWAY, partialTicks, depthTest);
        }
        WindowDrawing.drawBottomRule(minecraft, frame, shown);
        WindowDrawing.drawFrameEdges(minecraft, frame, shown);
        WindowBar.drawBare(minecraft, frame, shown);
    }

    /** Tells every page drawn last frame and not this one that it left the screen. */
    private static void leavePages(List<PageTab> drawn) {
        for (PageTab page : SHOWN) {
            if (!drawn.contains(page)) {
                page.content().hidden();
            }
        }
        SHOWN.clear();
        SHOWN.addAll(drawn);
    }

    /* ---- Time ---- */

    /** Once a game tick while playing: the page in front of each pinned window keeps time. */
    public static void tick(Minecraft minecraft) {
        if (!standing(minecraft)) {
            return;
        }
        for (Window window : WindowLayout.pinnedWindows()) {
            List<WindowTab> tabs = pinnedTabs(window);
            PageContent content = tabs.isEmpty() ? null
                    : WindowPages.contentOf(WindowFrame.activeTab(window,
                            tabs));
            if (content != null) {
                content.tick();
            }
        }
    }

    /* ---- Keys ---- */

    /** The pinned window the keys reach: the one last stepped to, else the one in front. */
    private static Window reached(List<Window> pinned) {
        for (Window window : pinned) {
            if (window.getId().equals(reachedId)) {
                return window;
            }
        }
        return pinned.isEmpty() ? null : pinned.get(pinned.size() - 1);
    }

    /**
     * Walks the pinned tabs of the window the keys reach {@code step}
     * places, round from one end to the other, and lights the window.
     * Answers whether a pinned window was there to take the key.
     */
    public static boolean walkTabs(Minecraft minecraft, int step) {
        if (!standing(minecraft)) {
            return false;
        }
        Window window = reached(WindowLayout.pinnedWindows());
        if (window == null) {
            return false;
        }
        List<WindowTab> tabs = pinnedTabs(window);
        WindowTab from = WindowFrame.activeTab(window, tabs);
        WindowTab next = TabWalk.step(tabs, from, step);
        if (next != null && !next.equals(from)) {
            WindowLayout.setActiveTab(next);
        }
        reach(window);
        return true;
    }

    /**
     * Steps the keys to the next pinned window, or the one before, and
     * lights it. Answers whether a pinned window was there to take the key.
     */
    public static boolean stepWindow(Minecraft minecraft, int step) {
        if (!standing(minecraft)) {
            return false;
        }
        List<Window> pinned = WindowLayout.pinnedWindows();
        if (pinned.isEmpty()) {
            return false;
        }
        int at = pinned.indexOf(reached(pinned));
        reach(pinned.get(Math.floorMod(at + step, pinned.size())));
        return true;
    }

    private static void reach(Window window) {
        reachedId = window.getId();
        reachedNanos = System.nanoTime();
    }

    /** Lets go of the session as the player leaves the world. */
    public static void clear() {
        leavePages(new ArrayList<PageTab>());
        reachedId = null;
        reachedNanos = 0L;
        closedNanos = 0L;
        screenWasOpen = false;
    }
}
