package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.gui.LostTalesHudFade;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiEasing;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiRegionBlur;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesUiClip;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

/**
 * The windows pinned to the HUD, which stay on screen while playing. Each
 * stands where it stands on the window screen, in the same place and size,
 * with the page in front filling it ({@link ContentView}), a little
 * fainter than on the window screen ({@code pinnedWindowOpacity}): nothing
 * on its row, strip or bar can be pressed while playing, so they slide
 * out past its edges as the screen closes and back in as it opens. A
 * world page never shows here: it closes as the player walks away.
 *
 * <p>While the window screen is open, the windows it shows are its own
 * and nothing of them is drawn from here. A window its view hides — one
 * pinned to the HUD but not to the GUI, the screen opened by another
 * page's key — stays part of the HUD: it fades with the HUD as the screen
 * opens and comes back with it as the screen closes.</p>
 *
 * <p>The arrow keys, held with the mod's Modifier Key, walk the pages of
 * one pinned window and step from one pinned window to the next; the
 * window they reach shows at full strength a moment.</p>
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
    /** The pages drawn from here this frame, both passes. */
    private static final List<PageTab> DRAWN = new ArrayList<PageTab>();
    /**
     * The windows the window screen's view hid as it last stood: they
     * fade with the HUD, and go back under it once the HUD is whole.
     */
    private static final Set<String> WITH_HUD = new HashSet<String>();
    /** What the pass inside the HUD's fade draws this frame, back to front. */
    private static final List<Window> FADING = new ArrayList<Window>();
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
     * Whether the pinned windows stand as the HUD: the player is in a
     * world and the window screen, whose own the windows are while it is
     * open, is not.
     */
    public static boolean standing(Minecraft minecraft) {
        return inWorld(minecraft)
                && !(minecraft.currentScreen instanceof WindowScreen);
    }

    private static boolean inWorld(Minecraft minecraft) {
        return minecraft != null && minecraft.theWorld != null
                && minecraft.thePlayer != null;
    }

    /**
     * The tabs of a window that show while playing, in row order: every
     * page that can be shown now but a world page.
     */
    private static List<WindowTab> hudTabs(Window window) {
        List<WindowTab> tabs = new ArrayList<WindowTab>();
        for (WindowTab tab : window.getTabs()) {
            if (tab.isAvailable() && WindowLayout.isOnHud(tab)) {
                tabs.add(tab);
            }
        }
        return tabs;
    }

    /**
     * Whether the tab is what a pinned window shows while playing: the
     * tab in front of a window pinned to the HUD, with no window screen
     * open. Its lines are read there, so the feed leaves them out.
     */
    public static boolean shows(WindowTab tab) {
        if (tab == null || !standing(Minecraft.getMinecraft())) {
            return false;
        }
        Window window = WindowLayout.windowOf(tab);
        if (window == null || !WindowLayout.isOnHud(tab)) {
            return false;
        }
        return tab.equals(WindowFrame.activeTab(window, hudTabs(window)));
    }

    /**
     * How a window comes in as the window screen opens. One pinned to the
     * HUD is on screen already: it stays where it stands and only gains
     * its full strength. Every other window takes the screen's entrance.
     */
    static LostTalesGuiAnimationSample entrance(
            Window window, LostTalesGuiAnimationSample opening) {
        if (!window.isPinnedToHud() || hudTabs(window).isEmpty()) {
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

    /** Whether the open window screen draws the window: its view shows one of its tabs. */
    private static boolean shownByScreen(Window window) {
        for (WindowTab tab : window.getTabs()) {
            if (WindowView.isShown(tab)) {
                return true;
            }
        }
        return false;
    }

    /* ---- Drawing ---- */

    /**
     * Draws, under the HUD's panels, the windows pinned to the HUD that
     * take no part in its fade, back to front; and sorts out which do.
     */
    public static void render(Minecraft minecraft, float partialTicks) {
        settlePages();
        FADING.clear();
        if (!inWorld(minecraft)) {
            WITH_HUD.clear();
            return;
        }
        long now = System.nanoTime();
        boolean screenOpen = minecraft.currentScreen instanceof WindowScreen;
        if (screenOpen) {
            screenWasOpen = true;
            WITH_HUD.clear();
        } else {
            if (screenWasOpen) {
                screenWasOpen = false;
                closedNanos = now;
            }
            if (LostTalesHudFade.isWhole()) {
                WITH_HUD.clear();
            }
        }
        List<Window> under = new ArrayList<Window>();
        for (Window window : WindowLayout.hudWindows()) {
            if (screenOpen) {
                if (!shownByScreen(window)) {
                    WITH_HUD.add(window.getId());
                    FADING.add(window);
                }
            } else if (WITH_HUD.contains(window.getId())) {
                FADING.add(window);
            } else {
                under.add(window);
            }
        }
        if (minecraft.gameSettings.hideGUI) {
            FADING.clear();
            return;
        }
        if (under.isEmpty() && FADING.isEmpty()) {
            return;
        }
        // As on the window screen: one capture of the frame while anything
        // is pinned, blurred while the windows' blur is on. Each window
        // cuts away what lies behind it and softens the world under it.
        LostTalesGuiRegionBlur.getInstance().capture(minecraft, partialTicks,
                LostTalesConfig.windowBackgroundBlur
                        && LostTalesConfig.enableGuiBackgroundBlur
                        ? (float)LostTalesConfig.guiBlurStrength : 0.0F);
        draw(minecraft, under, now, partialTicks);
    }

    /**
     * Draws the pinned windows that fade with the HUD, inside its fading
     * layer: those the window screen's view hides.
     */
    public static void renderWithHud(Minecraft minecraft, float partialTicks) {
        if (!FADING.isEmpty() && inWorld(minecraft)) {
            draw(minecraft, new ArrayList<Window>(FADING), System.nanoTime(),
                    partialTicks);
        }
    }

    private static void draw(Minecraft minecraft, List<Window> windows,
                             long now, float partialTicks) {
        if (windows.isEmpty()) {
            return;
        }
        ScaledResolution resolution = new ScaledResolution(minecraft,
                minecraft.displayWidth, minecraft.displayHeight);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        if (depthTest) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        WindowView.beginPinnedPass();
        try {
            REGIONS.reset();
            for (Window window : windows) {
                drawWindow(minecraft, window, resolution.getScaledWidth(),
                        resolution.getScaledHeight(), strength(window, now),
                        partialTicks, depthTest);
            }
        } finally {
            WindowView.endPinnedPass();
            if (depthTest) {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
            }
        }
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
                                   boolean depthTest) {
        List<WindowTab> tabs = WindowFrame.visibleTabs(window);
        if (tabs.isEmpty()) {
            return;
        }
        WindowFrame frame = WindowFrame.of(window);
        LostTalesGuiAnimationSample shown =
                LostTalesGuiAnimationSample.SETTLED.withOpacity(strength);
        WindowTab front = WindowFrame.activeTab(window, tabs);
        // The page fills the window, cut to what it shows, its row, strip
        // and bar sliding out past the edges as the screen closes; once
        // they are out they are not drawn at all.
        frame.advanceFill(ContentView.fillOf(window));
        frame.fillWithPage(WindowPlacement.filledBox(window, minecraft,
                screenWidth, screenHeight));
        boolean cut = false;
        try {
            if (front instanceof PageTab) {
                WindowDrawing.layOutPage(minecraft, window, frame,
                        (PageTab)front, screenWidth, screenHeight, shown);
            } else if (painter != null) {
                painter.draw(minecraft, window, screenWidth, screenHeight,
                        shown);
            } else {
                frame.drawn = false;
            }
            if (!frame.drawn) {
                return;
            }
            LostTalesUiHitBox filled = WindowDrawing.filledCut(frame);
            cut = filled != null && LostTalesUiClip.beginOuter(minecraft,
                    filled);
            boolean furniture = frame.contentShare() < 1.0F;
            if (furniture) {
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
            }
            if (frame.page != null) {
                PageContent content = frame.page.content();
                if (!SHOWN.contains(frame.page)) {
                    content.shown();
                }
                DRAWN.add(frame.page);
                WindowDrawing.drawPage(minecraft, frame, shown,
                        WindowHover.AWAY, WindowHover.AWAY, partialTicks,
                        depthTest);
            }
            if (furniture) {
                WindowDrawing.drawBottomRule(minecraft, frame, shown);
                WindowDrawing.drawFrameEdges(minecraft, frame, shown);
                WindowBar.drawBare(minecraft, frame, shown);
            }
        } finally {
            LostTalesUiClip.endOuter(cut);
        }
        WindowDrawing.drawFilledRing(minecraft, frame, shown);
    }

    /**
     * Tells every page drawn from here the frame before and not in the last
     * one that it left the screen, and starts a new frame's count.
     */
    private static void settlePages() {
        for (PageTab page : SHOWN) {
            if (!DRAWN.contains(page)) {
                page.content().hidden();
            }
        }
        SHOWN.clear();
        SHOWN.addAll(DRAWN);
        DRAWN.clear();
    }

    /* ---- Time ---- */

    /** Once a game tick while playing: the page in front of each pinned window keeps time. */
    public static void tick(Minecraft minecraft) {
        if (!standing(minecraft)) {
            return;
        }
        for (Window window : WindowLayout.hudWindows()) {
            List<WindowTab> tabs = hudTabs(window);
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
     * Walks the pages of the pinned window the keys reach {@code step}
     * places, round from one end to the other, and lights the window.
     * Answers whether a pinned window was there to take the key.
     */
    public static boolean walkTabs(Minecraft minecraft, int step) {
        if (!standing(minecraft)) {
            return false;
        }
        Window window = reached(WindowLayout.hudWindows());
        if (window == null) {
            return false;
        }
        List<WindowTab> tabs = hudTabs(window);
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
        List<Window> pinned = WindowLayout.hudWindows();
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
        DRAWN.clear();
        settlePages();
        WITH_HUD.clear();
        FADING.clear();
        reachedId = null;
        reachedNanos = 0L;
        closedNanos = 0L;
        screenWasOpen = false;
    }
}
