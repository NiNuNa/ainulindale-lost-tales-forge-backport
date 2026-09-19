package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.gui.animation.LostTalesGuiRegionBlur;
import com.ninuna.losttales.client.gui.animation.LostTalesUiEasing;
import com.ninuna.losttales.client.gui.animation.LostTalesUiTransition;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

/**
 * Where a carried window will go, shown as a desktop shows it: a pane
 * of frosted glass — what lies behind it blurred, the chat's surface
 * faint over that, and a window's frame round it — standing in the part
 * of the screen the window fills when the button comes up. It grows out
 * of the window as the pointer enters a snap zone, travels from zone to
 * zone, and shrinks back into the window when the pointer leaves; the
 * window itself keeps its size under the hand all the while. Let go in a
 * zone, the window glides into the pane as the pane fades where it
 * stands.
 *
 * <p>On a suggestion, every other window the suggestion sends along has
 * a pane of its own, growing out of that window into its zone with the
 * window's tab icon large in its middle, as a desktop previews a snap
 * group on its acrylic; the panes go back into their windows when the
 * pointer leaves the suggestion, and fade where they stand as the
 * windows glide into them when it is taken.</p>
 */
final class ChatSnapPreview {
    /**
     * The share of the chat's surface the glass is tinted with: half of
     * what a window's strips wear, so it reads as an empty window rather
     * than as one.
     */
    static final float GLASS_SHARE = 0.5F;
    /**
     * How many times its own size a window's tab icon stands in the pane
     * a suggestion sends the window to: a whole number, so the artwork
     * keeps its texels, and smaller only where a pane has no room for it.
     */
    static final int ICON_SCALE = 3;

    /** The window the panes belong to, the carried one, or null while none shows. */
    private String windowId;
    /** The carried window's own pane. */
    private final Track own = new Track();
    /** The panes of the other windows the suggestion under the pointer sends along, by window. */
    private final Map<String, Track> companions =
            new LinkedHashMap<String, Track>();

    ChatSnapPreview() {
        reset();
    }

    /** A pane's box, the window's box it stands for: the frame lies round it. */
    static final class Pane {
        final double left;
        final double top;
        final double right;
        final double bottom;

        Pane(double left, double top, double right, double bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        /** Every edge {@code share} of the way from this pane to {@code to}. */
        Pane toward(Pane to, double share) {
            return new Pane(this.left + (to.left - this.left) * share,
                    this.top + (to.top - this.top) * share,
                    this.right + (to.right - this.right) * share,
                    this.bottom + (to.bottom - this.bottom) * share);
        }

        /** The pane laid on whole display pixels, edge by edge. */
        Pane snapped() {
            return new Pane(ChatWindowFrame.snapToDisplayPixels(this.left),
                    ChatWindowFrame.snapToDisplayPixels(this.top),
                    ChatWindowFrame.snapToDisplayPixels(this.right),
                    ChatWindowFrame.snapToDisplayPixels(this.bottom));
        }

        /** The pane with its frame round it, as a box the blur and other windows are measured by. */
        ChatWindowPlacement.Box framed(int barHeight) {
            int ring = ChatWindowPlacement.FRAME_WIDTH;
            return new ChatWindowPlacement.Box(this.left - ring,
                    this.top - ring,
                    (int)Math.ceil(this.right - this.left + 2 * ring),
                    this.bottom - this.top + 2 * ring, barHeight);
        }
    }

    /** Where a window stands as drawn this frame, by id; null for one that has gone. */
    interface Boxes {
        ChatWindowPlacement.Box of(String windowId);
    }

    /** A companion's pane as it shows this frame, and whose it is. */
    static final class Shown {
        final String windowId;
        final Pane pane;
        final float opacity;

        Shown(String windowId, Pane pane, float opacity) {
            this.windowId = windowId;
            this.pane = pane;
            this.opacity = opacity;
        }
    }

    /** As below, with no other window sent along. */
    void aim(String windowId, ChatWindow.ScreenFill fill) {
        aim(windowId, fill,
                Collections.<String, ChatWindow.ScreenFill>emptyMap());
    }

    /**
     * One frame of a carry: the pane is bound for {@code fill} for the
     * window {@code windowId}, or back into the window for none, and each
     * of {@code companions} — the other windows a suggestion sends along,
     * each with its zone — has a pane of its own bound there, while a
     * window no longer among them has its pane go back into it. A new aim
     * starts a leg from the pane as it stands.
     */
    void aim(String windowId, ChatWindow.ScreenFill fill,
             Map<String, ChatWindow.ScreenFill> companions) {
        if (windowId == null) {
            reset();
            return;
        }
        if (!windowId.equals(this.windowId)) {
            // Another window's panes, still fading, give way at once.
            reset();
            this.windowId = windowId;
        }
        this.own.aim(fill);
        for (Map.Entry<String, Track> entry : this.companions.entrySet()) {
            if (!companions.containsKey(entry.getKey())) {
                entry.getValue().aim(ChatWindow.ScreenFill.NONE);
            }
        }
        for (Map.Entry<String, ChatWindow.ScreenFill> companion
                : companions.entrySet()) {
            Track track = this.companions.get(companion.getKey());
            if (track == null) {
                track = new Track();
                this.companions.put(companion.getKey(), track);
            }
            track.aim(companion.getValue());
        }
    }

    /**
     * The carry is over. Let go into the pane ({@code filled}), the
     * window glides into it and the pane fades where it stands, as every
     * companion's pane does; otherwise every pane goes back into its
     * window.
     */
    void release(boolean filled) {
        if (filled && this.own.isBound()) {
            this.own.land();
            for (Track track : this.companions.values()) {
                if (track.isBound()) {
                    track.land();
                }
            }
            return;
        }
        aim(this.windowId, ChatWindow.ScreenFill.NONE);
    }

    /** Forgets every pane at once. */
    void reset() {
        this.windowId = null;
        this.own.reset();
        this.companions.clear();
    }

    /** The window the pane is drawn under — between it and the windows behind it — or null. */
    String windowId() {
        return this.windowId;
    }

    /**
     * Moves the carried window's pane on to this instant and answers
     * where it stands, on whole display pixels, or null while nothing of
     * it shows. {@code window} is the window's box as it is drawn this
     * frame: where a pane growing out of the window sets out, and where
     * one going back into it is bound. Once no pane shows at all, the
     * preview is let go.
     */
    Pane advance(Minecraft minecraft, ChatWindowPlacement.Box window,
                 int screenWidth, int screenHeight) {
        if (this.windowId == null || window == null) {
            return null;
        }
        Pane pane = this.own.advance(minecraft, window, screenWidth,
                screenHeight, System.nanoTime());
        if (pane == null && this.companions.isEmpty()) {
            reset();
        }
        return pane;
    }

    /** How much of the carried window's pane shows, 0..1, as last advanced. */
    float opacity() {
        return this.own.opacity();
    }

    /**
     * Moves every companion's pane on to this instant and answers the
     * ones that show, each grown out of its own window as {@code boxes}
     * has it this frame. A companion whose pane has gone, or whose
     * window has, is let go.
     */
    List<Shown> advanceCompanions(Minecraft minecraft, Boxes boxes,
                                  int screenWidth, int screenHeight) {
        List<Shown> shown = new ArrayList<Shown>(this.companions.size());
        long now = System.nanoTime();
        Iterator<Map.Entry<String, Track>> iterator =
                this.companions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Track> entry = iterator.next();
            ChatWindowPlacement.Box box = boxes.of(entry.getKey());
            Pane pane = box == null ? null : entry.getValue().advance(
                    minecraft, box, screenWidth, screenHeight, now);
            if (pane == null) {
                iterator.remove();
                continue;
            }
            shown.add(new Shown(entry.getKey(), pane,
                    entry.getValue().opacity()));
        }
        return shown;
    }

    /**
     * One pane's way: out of its window into a part of the screen, from
     * part to part, and back into the window.
     */
    private static final class Track {
        private final LostTalesUiTransition shown =
                new LostTalesUiTransition();
        private final LostTalesUiTransition glide =
                new LostTalesUiTransition();
        /** What the running leg is bound for: a part of the screen, or none for the window itself. */
        private ChatWindow.ScreenFill legTo = ChatWindow.ScreenFill.NONE;
        /** Where the running leg set out: the pane as drawn then, or null for the window itself. */
        private Pane legFrom;
        /** The pane as last placed, or null. */
        private Pane placed;
        /** Whether the window was let go into the pane, which then fades where it stands. */
        private boolean landing;

        Track() {
            reset();
        }

        /** Binds the pane for {@code fill}, or back into the window for none. */
        void aim(ChatWindow.ScreenFill fill) {
            ChatWindow.ScreenFill wanted = fill == null
                    ? ChatWindow.ScreenFill.NONE : fill;
            this.landing = false;
            if (!wanted.equals(this.legTo)) {
                this.legFrom = this.placed;
                this.legTo = wanted;
                this.glide.settle(false);
            }
        }

        boolean isBound() {
            return this.legTo != ChatWindow.ScreenFill.NONE;
        }

        /** The window was let go into the pane: it fades where it stands. */
        void land() {
            this.landing = true;
        }

        /**
         * Back at rest, hidden, so the next pane fades in rather than
         * standing at once as a first sight would.
         */
        void reset() {
            this.legTo = ChatWindow.ScreenFill.NONE;
            this.legFrom = null;
            this.placed = null;
            this.landing = false;
            this.shown.settle(false);
            this.glide.settle(true);
        }

        /**
         * Moves the pane on to {@code now} and answers where it stands,
         * on whole display pixels, growing out of or going back into
         * {@code window}; null once nothing of it shows, which sets it
         * back at rest.
         */
        Pane advance(Minecraft minecraft, ChatWindowPlacement.Box window,
                     int screenWidth, int screenHeight, long now) {
            int duration = LostTalesConfig.enableChatAnimations
                    ? Math.max(1, LostTalesConfig.chatAnimationDurationMillis)
                    : 0;
            boolean on = isBound() && !this.landing;
            float opacity = this.shown.advance(now, on, duration,
                    LostTalesUiEasing.SMOOTH);
            float share = this.glide.advance(now, true, duration,
                    LostTalesUiEasing.SMOOTH);
            if (!on && this.shown.isSettled() && opacity <= 0.0F) {
                reset();
                return null;
            }
            Pane own = new Pane(window.x, window.y, window.right(),
                    window.bottom());
            Pane from = this.legFrom == null ? own : this.legFrom;
            Pane to = own;
            if (isBound()) {
                ChatWindowPlacement.Box fill = ChatWindowPlacement.fillBounds(
                        this.legTo, minecraft, screenWidth, screenHeight);
                to = new Pane(fill.x, fill.y, fill.right(), fill.bottom());
            }
            this.placed = from.toward(to, share);
            return this.placed.snapped();
        }

        float opacity() {
            return this.shown.clamped();
        }
    }

    /**
     * A companion window's front tab icon in the middle of its pane, as a
     * desktop lays an app's icon on the acrylic it previews the app with:
     * {@link #ICON_SCALE} times its own size, smaller where the pane has
     * no room for that and the icon's shadow, on the display's grid.
     */
    static void drawIcon(Minecraft minecraft, String windowId, Pane pane,
                         float opacity) {
        ChatWindow window = ChatWindowLayout.window(windowId);
        ChatTab front = window == null ? null : ChatWindowFrame.activeTab(
                window, ChatWindowFrame.visibleTabs(window));
        int alpha = Math.round(255.0F * Math.min(1.0F, opacity));
        if (minecraft == null || front == null || pane == null
                || alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        float width = (float)(pane.right - pane.left);
        float height = (float)(pane.bottom - pane.top);
        int scale = ICON_SCALE;
        while (scale > 1 && (scale * (LostTalesUiInk.ICON_SIZE + 1) > width
                || scale * (LostTalesUiInk.ICON_SIZE + 1) > height)) {
            scale--;
        }
        float size = scale * LostTalesUiInk.ICON_SIZE;
        float pixels = ChatWindowFrame.displayScaleFactor();
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef((float)pane.left
                            + LostTalesUiInk.centredStart(width, size, pixels),
                    (float)pane.top
                            + LostTalesUiInk.centredStart(height, size, pixels),
                    0.0F);
            GL11.glScalef(scale, scale, 1.0F);
            LostTalesChatVisualStyle.beginContent();
            ChatChannelIcons.draw(minecraft, front, 0.0F, 0.0F, alpha);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** The glass's surface over the pane with every hole left out. */
    private static void fillGlass(float left, float top, float right,
                                  float bottom, List<LostTalesUiHitBox> holes,
                                  int argb) {
        float cursor = top;
        for (LostTalesUiHitBox hole : holes) {
            float holeLeft = (float)hole.left;
            float holeTop = (float)hole.top;
            float holeRight = (float)hole.right();
            float holeBottom = (float)hole.bottom();
            LostTalesChatOverlayRenderer.fillRect(left, cursor, right, holeTop,
                    argb);
            LostTalesChatOverlayRenderer.fillRect(left, holeTop, holeLeft,
                    holeBottom, argb);
            LostTalesChatOverlayRenderer.fillRect(holeRight, holeTop, right,
                    holeBottom, argb);
            LostTalesUiFramedButton.fillCorners(holeLeft, holeTop,
                    holeRight - holeLeft, holeBottom - holeTop, argb);
            cursor = holeBottom;
        }
        LostTalesChatOverlayRenderer.fillRect(left, cursor, right, bottom,
                argb);
    }

    /**
     * Draws the pane at {@code opacity}: the blurred frame under it and
     * its frame's ring — a window's own rectangle of the blur — the
     * glass's faint surface inside, and the ring's surface and white
     * edges round it exactly as a window wears them, each edge fading
     * along its length from the lit corner.
     */
    static void draw(Minecraft minecraft, Pane pane, float opacity) {
        draw(minecraft, pane, opacity,
                Collections.<LostTalesUiHitBox>emptyList());
    }

    /**
     * As above, with {@code holes} left out of the glass's surface for
     * what stands on it with a surface of its own — snap assist's cards,
     * framed buttons whose corner pixels stay the glass's — so no surface
     * lies over another. The holes are one column, top to bottom.
     */
    static void draw(Minecraft minecraft, Pane pane, float opacity,
                     List<LostTalesUiHitBox> holes) {
        if (minecraft == null || pane == null || opacity <= 0.0F) {
            return;
        }
        float left = (float)pane.left;
        float top = (float)pane.top;
        float right = (float)pane.right;
        float bottom = (float)pane.bottom;
        if (right - left < 2.0F || bottom - top < 2.0F) {
            return;
        }
        int ring = ChatWindowPlacement.FRAME_WIDTH;
        LostTalesGuiRegionBlur.getInstance().drawRegion(left - ring,
                top - ring, right + ring, bottom + ring, opacity);
        float surfaceShare = opacity
                * LostTalesChatVisualStyle.chatOpacity(minecraft);
        fillGlass(left, top, right, bottom, holes,
                LostTalesChatVisualStyle.surfaceArgb(GLASS_SHARE
                        * surfaceShare));
        int surface = LostTalesChatVisualStyle.surfaceArgb(surfaceShare);
        // The ring's outermost corner pixels lie outside the frame's
        // rounding, as a window's own do.
        LostTalesChatOverlayRenderer.fillRect(left - ring + 1, top - ring,
                right + ring - 1, top - ring + 1, surface);
        LostTalesChatOverlayRenderer.fillRect(left - ring, top - ring + 1,
                right + ring, top, surface);
        LostTalesChatOverlayRenderer.fillRect(left - ring, top, left, bottom,
                surface);
        LostTalesChatOverlayRenderer.fillRect(right, top, right + ring,
                bottom, surface);
        LostTalesChatOverlayRenderer.fillRect(left - ring, bottom,
                right + ring, bottom + ring - 1, surface);
        LostTalesChatOverlayRenderer.fillRect(left - ring + 1,
                bottom + ring - 1, right + ring - 1, bottom + ring, surface);
        int alpha = Math.round(255.0F * Math.min(1.0F, opacity));
        if (alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        int edge = ChatWindowPlacement.FRAME_EDGE_WIDTH;
        float span = bottom - top + edge;
        LostTalesChatOverlayRenderer.drawTopEdge(left, right, top - edge,
                alpha);
        LostTalesChatOverlayRenderer.drawRightEdgeSegment(right, top, bottom,
                top - edge, span, alpha);
        LostTalesChatOverlayRenderer.drawLeftEdgeSegment(left - edge, top,
                bottom, bottom, span, alpha);
        LostTalesChatOverlayRenderer.drawBarBottomEdge(left, right, bottom,
                alpha);
        LostTalesChatVisualStyle.beginContent();
        LostTalesUiFramedButton.drawCornerInk(
                LostTalesUiSheet.FRAME_LIT_TOP_RIGHT,
                right + ring - LostTalesUiFramedButton.CORNER, top - ring,
                alpha);
        LostTalesUiFramedButton.drawCornerInk(
                LostTalesUiSheet.FRAME_LIT_BOTTOM_LEFT, left - ring,
                bottom + ring - LostTalesUiFramedButton.CORNER, alpha);
    }
}
