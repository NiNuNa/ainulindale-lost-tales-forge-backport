package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiCutName;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/**
 * The two framed buttons every input bar starts with, the chat's and
 * every page's alike. The tab button names the tab in front, its icon
 * and its name in its colour; a click walks the window's tabs forward and
 * a right-click back. The identity button beside it shows who the player
 * is on that tab and opens its menu. The two read as one pair,
 * {@link #BUTTON_GAP} apart.
 *
 * <p>Where a bar is short of room the tab's name gives way first, cut at
 * the room it keeps and fading into its end, down to the icon alone;
 * resting the pointer on a cut name slides it along as a tab's does
 * (LOOK-37). A tab without an icon keeps its name whole. Each live bar
 * keeps its own instance for the tab button's light and slide and the
 * identity button's beat.</p>
 */
public final class BarLead {
    /** Clear space between the two buttons, frame to frame. */
    public static final int BUTTON_GAP = WindowStyle.BUTTON_GAP;
    /** The identity button: square, the framed buttons' one height. */
    public static final int IDENTITY_SIZE = LostTalesUiFramedButton.HEIGHT;

    /** What the identity button holds: a face, centred in it and moving with it. */
    public interface Face {
        int width();

        int height();

        /** Draws the face with its top-left at the point. */
        void draw(float x, float y, int alpha);
    }

    /** Who the player is on a tab, for its identity button: the screen asks its parts. */
    public interface Voice {
        /** The face the tab's identity button shows; null while there is nobody to show. */
        Face faceFor(WindowPage tab);

        /** Whether the button's menu is out for the tab: the button rests lit. */
        boolean menuOutFor(WindowPage tab);
    }

    /** The pair laid out on one bar: what is drawn, and what answers the pointer. */
    public static final class Fit {
        public final WindowPage tab;
        public final String label;
        /** Left edge of the tab's icon; -1 for a tab without one. */
        public final int iconLeft;
        public final int labelLeft;
        /** The whole name's width, and the room it keeps of that. */
        public final int labelWidth;
        public final int labelRoom;
        /** The tab button's frame. */
        public final int frameLeft;
        public final int frameRight;
        /** The identity button's frame starts here and is {@link #IDENTITY_SIZE} square. */
        public final int identityLeft;
        /** Just past the identity button's frame. */
        public final int right;

        Fit(WindowPage tab, String label, int iconLeft, int labelLeft,
            int labelWidth, int labelRoom, int frameLeft, int frameRight) {
            this.tab = tab;
            this.label = label;
            this.iconLeft = iconLeft;
            this.labelLeft = labelLeft;
            this.labelWidth = labelWidth;
            this.labelRoom = labelRoom;
            this.frameLeft = frameLeft;
            this.frameRight = frameRight;
            this.identityLeft = frameRight + BUTTON_GAP;
            this.right = this.identityLeft + IDENTITY_SIZE;
        }

        /** Whether the point is on the tab button's frame, standing at {@code top}: a framed button answers on its frame. */
        public boolean onTab(double x, double y, int top) {
            return LostTalesUiHitBox.contains(x, y, this.frameLeft, top,
                    this.frameRight - this.frameLeft,
                    LostTalesUiFramedButton.HEIGHT);
        }

        /** Whether the point is on the identity button's frame, standing at {@code top}. */
        public boolean onIdentity(double x, double y, int top) {
            return LostTalesUiHitBox.contains(x, y, this.identityLeft, top,
                    IDENTITY_SIZE, IDENTITY_SIZE);
        }
    }

    /**
     * The identity button's beat: the face rises inside its frame and
     * never tilts, since a tilted face reads as a mistake.
     */
    private final LostTalesUiButtonMotion identityMotion =
            new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
    /** The pose a resting bar's identity button is drawn in: never stepped, never lit. */
    private static final LostTalesUiButtonMotion RESTING =
            new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
    /** How far the tab button's frame has lit under the pointer. */
    private float tabLit;
    /** How far a cut name has slid under the pointer. */
    private final LostTalesUiCutName.Slide nameSlide =
            new LostTalesUiCutName.Slide();
    private long nanos;

    /* ---- The layout ---- */

    /** The widest the pair is: the tab's whole name showing. */
    public static int wholeWidth(WindowPage tab, WindowBar.Measure measure) {
        return tabFrameWidth(tab, measure, Integer.MAX_VALUE) + BUTTON_GAP
                + IDENTITY_SIZE;
    }

    /** The narrowest the pair is: the tab's icon alone, or its whole name without an icon. */
    public static int leastWidth(WindowPage tab, WindowBar.Measure measure) {
        return tabFrameWidth(tab, measure, 0) + BUTTON_GAP + IDENTITY_SIZE;
    }

    /**
     * The pair laid from {@code left}, {@code width} wide at most and
     * never under {@link #leastWidth}: the tab's name cut at the room left.
     */
    public static Fit fit(WindowPage tab, int left, int width,
                          WindowBar.Measure measure) {
        String label = tab == null ? "" : tab.title();
        boolean icon = tab != null && tab.hasIcon();
        int labelWidth = measure.width(label);
        int iconLeft = left + LostTalesUiFramedButton.WIDE_INSET;
        int iconRight = icon ? iconLeft + TabIcons.SIZE : iconLeft;
        int gap = icon ? TabIcons.GAP : 0;
        int frameWidth = tabFrameWidth(tab, measure,
                width - BUTTON_GAP - IDENTITY_SIZE);
        int whole = tabFrameWidth(tab, measure, Integer.MAX_VALUE);
        // A cut name keeps what the frame leaves after the icon and the
        // gap; the whole one keeps all of itself.
        int labelRoom = frameWidth >= whole ? labelWidth
                : Math.max(0, frameWidth - 2 * LostTalesUiFramedButton.WIDE_INSET
                        - TabIcons.SIZE - gap);
        return new Fit(tab, label, icon ? iconLeft : -1, iconRight + gap,
                labelWidth, labelRoom, left, left + frameWidth);
    }

    /**
     * The tab button's frame width, at most {@code room}: the wide inset
     * round the icon, the gap and the name, whose last column is spacing
     * rather than ink; cut down to the icon alone where the room is short.
     */
    private static int tabFrameWidth(WindowPage tab, WindowBar.Measure measure,
                                     int room) {
        boolean icon = tab != null && tab.hasIcon();
        int labelWidth = tab == null ? 0 : measure.width(tab.title());
        int inset = 2 * LostTalesUiFramedButton.WIDE_INSET;
        int whole = inset + (icon ? TabIcons.SIZE + TabIcons.GAP : 0)
                + Math.max(0, labelWidth - 1);
        if (!icon) {
            return whole;
        }
        return Math.max(inset + TabIcons.SIZE, Math.min(whole, room));
    }

    /* ---- The tab button ---- */

    /**
     * Moves the tab button's light and its name's slide on to now: a cut
     * name slides while the pointer rests on it and glides home once it
     * leaves, as a tab's does (LOOK-37).
     */
    public void advanceTab(Fit fit, boolean pointed) {
        long now = System.nanoTime();
        double elapsed = this.nanos == 0L ? 0.0D : (now - this.nanos) / 1.0E9D;
        this.nanos = now;
        this.tabLit = WindowStyle.hoverFade(this.tabLit, pointed, elapsed);
        this.nameSlide.step(pointed && fit.labelRoom > 0,
                LostTalesUiCutName.overflow(fit.labelWidth, fit.labelRoom),
                elapsed);
    }

    /**
     * The tab button as laid out in {@code fit}, lit as far as the pointer
     * lit it, its name slid along as far as it has slid.
     */
    public void drawTab(Minecraft minecraft, FontRenderer font, Fit fit,
                        int frameTop, int textTop, int surfaceAlpha,
                        int alpha) {
        drawTab(minecraft, font, fit, frameTop, textTop, this.tabLit,
                this.nameSlide.offset(), surfaceAlpha, alpha);
    }

    /** The tab button of a bar nobody types in: unlit, a cut name fading where its room ends and never sliding. */
    public static void drawTabAtRest(Minecraft minecraft, FontRenderer font,
                                     Fit fit, int frameTop, int textTop,
                                     int surfaceAlpha, int alpha) {
        drawTab(minecraft, font, fit, frameTop, textTop, 0.0F, 0.0F,
                surfaceAlpha, alpha);
    }

    /**
     * The framed button: its surface in the hole the bar left for it, the
     * tab's icon, its name in the tab's colour crossing to ivory as the
     * frame lights, as the main menu's buttons light, and the frame's ink.
     * A tab nothing can be sent in names itself in italics.
     */
    private static void drawTab(Minecraft minecraft, FontRenderer font, Fit fit,
                                int frameTop, int textTop, float lit,
                                float slide, int surfaceAlpha, int alpha) {
        int frameWidth = fit.frameRight - fit.frameLeft;
        LostTalesUiFramedButton.drawSurface(fit.frameLeft, frameTop, frameWidth,
                LostTalesUiFramedButton.HEIGHT, lit, surfaceAlpha);
        if (fit.iconLeft >= 0 && fit.tab != null) {
            // The icon's box stands in the frame's middle, the inset above
            // and below it, the name's capitals half a pixel above the box's.
            LostTalesUiInk.beginContent();
            fit.tab.drawIcon(minecraft, fit.iconLeft,
                    frameTop + LostTalesUiFramedButton.INSET, alpha,
                    TabMark.NONE);
        }
        if (fit.labelRoom > 0 && fit.tab != null) {
            drawLabel(font, fit, textTop, lit, slide, alpha);
        }
        LostTalesUiFramedButton.drawInk(fit.frameLeft, frameTop, frameWidth,
                LostTalesUiFramedButton.HEIGHT, lit, alpha);
    }

    /**
     * The name: whole in the room it keeps, or cut at the room's end,
     * fading into it and slid along by {@code slide}, as a tab's
     * (LOOK-37).
     */
    private static void drawLabel(FontRenderer font, Fit fit, int textTop,
                                  float lit, float slide, int alpha) {
        // Text is always at full opacity: a tab nothing can be sent in
        // reads italic rather than faint.
        String text = fit.tab.isReadOnly() ? "§o" + fit.label : fit.label;
        int color = LostTalesUiInk.blend(fit.tab.tone(), LostTalesUiInk.IVORY,
                lit);
        LostTalesUiCutName.drawTextSlid(font, text, fit.labelLeft, textTop,
                fit.labelRoom, color, alpha, slide);
    }

    /* ---- The identity button ---- */

    /** Moves the identity button's beat on: lit under the pointer and while its menu is out. */
    public void advanceIdentity(boolean pointed, boolean menuOut,
                                boolean pressed) {
        this.identityMotion.advance(System.nanoTime(), pointed || menuOut,
                pointed, pressed);
    }

    /**
     * The identity button as laid out in {@code fit}: its surface, the
     * face centred in it with the framed buttons' clear pixels round it
     * and moving inside the frame, which keeps its place, so the button
     * reads as a socket holding a face; then the frame's ink. With no
     * face it stands empty and unlit.
     */
    public void drawIdentity(Fit fit, int top, Face face, int surfaceAlpha,
                             int alpha) {
        drawIdentity(fit, top, face, face == null ? RESTING
                : this.identityMotion, surfaceAlpha, alpha);
    }

    /** The identity button of a bar nobody types in: unlit, the face where it was laid out. */
    public static void drawIdentityAtRest(Fit fit, int top, Face face,
                                          int surfaceAlpha, int alpha) {
        drawIdentity(fit, top, face, RESTING, surfaceAlpha, alpha);
    }

    private static void drawIdentity(Fit fit, int top, Face face,
                                     LostTalesUiButtonMotion motion,
                                     int surfaceAlpha, int alpha) {
        float lit = motion.lit();
        int left = fit.identityLeft;
        LostTalesUiFramedButton.drawSurface(left, top, IDENTITY_SIZE,
                IDENTITY_SIZE, lit, surfaceAlpha);
        if (face != null) {
            // Centred, the odd pixel up and to the left.
            float x = left + (IDENTITY_SIZE - face.width()) / 2;
            float y = top + (IDENTITY_SIZE - face.height()) / 2;
            LostTalesUiInk.beginContent();
            LostTalesUiButton.beginPose(motion, x, y, face.width(),
                    face.height());
            try {
                face.draw(x, y, alpha);
            } finally {
                LostTalesUiButton.endPose();
            }
        }
        LostTalesUiFramedButton.drawInk(left, top, IDENTITY_SIZE,
                IDENTITY_SIZE, lit, alpha);
    }
}
