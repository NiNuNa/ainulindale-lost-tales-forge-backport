package com.ninuna.losttales.gui.style;

import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * A name too long for its room (LOOK-37). It is drawn whole from its
 * start and cut where its room ends, the words themselves fading out into
 * that end; while the pointer rests on it, it slides along to show its
 * end, rests there, slides back and rests again, and it glides home once
 * the pointer leaves. Tabs, the bars' tab buttons, sub-window strips,
 * menu and list rows, cards, popups, the HUD and the creator all cut their
 * words here, so every cut looks and moves alike.
 *
 * <p>The slide is timed by {@code window.tab.marquee} and glides home by
 * {@code window.marquee.return}, at the speed in force. Under reduced
 * motion its slides are jumps; with the Animations switch off a cut name
 * stays at its start.</p>
 *
 * <p>A control that keeps its own state holds a {@link Slide}. Rows drawn
 * by shared code name themselves instead ({@link #slideOf}): an owner,
 * the page, menu or list drawing them, and a key unique among that
 * owner's names. That shared state follows the name the pointer rests on
 * and the few gliding home, forgets one not drawn for a moment, and is
 * cleared when the window screen closes ({@link #forgetAll}).</p>
 */
public final class LostTalesUiCutName {
    /** The most names the shared state follows at once: the one rested on and a few gliding home. */
    static final int MOST_FOLLOWED = 16;
    /** A followed name not drawn for this long is forgotten: its row went, or its screen did. */
    static final long FORGET_NANOS = 250000000L;
    /**
     * A name rested on this recently is still rested on for another copy
     * of it drawn without the pointer, such as a window's pinned copy: a
     * few frames, so both copies show one slide.
     */
    static final long HOLD_NANOS = 50000000L;
    /** Slack under which words count as fitting their room. */
    private static final float FIT_EPSILON = 1.0E-3F;

    private static final List<Followed> FOLLOWED =
            new ArrayList<Followed>(MOST_FOLLOWED);
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);

    private LostTalesUiCutName() {}

    /* ---- When a name is cut, and how far it fades ---- */

    /** Whether words {@code width} wide are cut in {@code room}. */
    public static boolean isCut(float width, float room) {
        return width > room + FIT_EPSILON;
    }

    /** How many whole pixels of words {@code width} wide the room cannot show: how far they slide. */
    public static int overflow(float width, float room) {
        return isCut(width, room)
                ? (int)Math.ceil(width - Math.max(0.0F, room) - FIT_EPSILON) : 0;
    }

    /** How wide cut words stand: their room, or their own width where they fit. */
    public static float shownWidth(float width, float room) {
        return Math.max(0.0F, Math.min(width, room));
    }

    /** How deep the fade at a cut end reaches into a room {@code room} wide. */
    public static float fadeDepth(float room) {
        return LostTalesUiFading.sideFadeDepth(room);
    }

    /** How strongly words slid {@code slid} past their room's start fade into it: none at home. */
    public static float startFade(float slid, float depth) {
        return LostTalesUiFading.sideFadeStrength(slid, depth);
    }

    /** How strongly words {@code width} wide, slid {@code slid}, fade into their room's end. */
    public static float endFade(float width, float room, float slid,
                                float depth) {
        return LostTalesUiFading.sideFadeStrength(width - slid - room, depth);
    }

    /* ---- The slide ---- */

    /**
     * How far a name the pointer has rested on for {@code restedSeconds}
     * is slid, as {@code window.tab.marquee} times it at the speed in
     * force; under reduced motion the slides are jumps.
     */
    public static double slideOffset(double restedSeconds, int overflowPx) {
        double speed = Motions.speed();
        double pxPerSecond = Motions.reduced() ? Double.POSITIVE_INFINITY
                : Motions.param(MotionIds.WINDOW_TAB_MARQUEE, "speed", 20.0F)
                        * speed;
        return slideOffset(restedSeconds, overflowPx,
                Motions.nanos(MotionIds.WINDOW_TAB_MARQUEE) / 1.0E9D,
                pxPerSecond, Motions.param(MotionIds.WINDOW_TAB_MARQUEE,
                        "pause", 800.0F) / 1000.0D / speed);
    }

    /**
     * How far a name is slid after {@code restedSeconds} under the
     * pointer, given the {@code overflowPx} of it its room cannot show:
     * nothing for {@code delaySeconds}, then out to the overflow at
     * {@code pxPerSecond} (infinite for a jump), {@code pauseSeconds}
     * there, back to nothing, a pause, and round again. Zero when nothing
     * overflows. A pure reading of the clock, so it looks the same at any
     * frame rate.
     */
    static double slideOffset(double restedSeconds, int overflowPx,
                              double delaySeconds, double pxPerSecond,
                              double pauseSeconds) {
        if (overflowPx <= 0) {
            return 0.0D;
        }
        double time = restedSeconds - delaySeconds;
        if (time <= 0.0D) {
            return 0.0D;
        }
        double slide = pxPerSecond > 0.0D ? overflowPx / pxPerSecond : 0.0D;
        double cycle = 2.0D * (slide + pauseSeconds);
        if (cycle <= 0.0D) {
            return 0.0D;
        }
        double phase = time % cycle;
        if (phase < slide) {
            return phase * pxPerSecond;
        }
        if (phase < slide + pauseSeconds) {
            return overflowPx;
        }
        if (phase < 2.0D * slide + pauseSeconds) {
            return overflowPx - (phase - slide - pauseSeconds) * pxPerSecond;
        }
        return 0.0D;
    }

    /**
     * One step of a name gliding home, by {@code window.marquee.return}:
     * at once while motion is off or reduced, and the last display pixel
     * simply arrived at, since an exponential tail spends longer and
     * longer covering it and reads as stepping rather than as a glide's
     * end.
     */
    public static float glideHome(float current, double elapsedSeconds) {
        float value = (float)Motions.followTravel(
                MotionIds.WINDOW_MARQUEE_RETURN, current, 0.0D,
                elapsedSeconds);
        return Math.abs(value) < displayStep() ? 0.0F : value;
    }

    /** One display pixel in GUI units: the finest step the screen has. */
    private static double displayStep() {
        return 1.0D / LostTalesDisplayPixels.scaleFactor();
    }

    /**
     * One name's slide, for a control keeping its own: how long the
     * pointer has rested on it and how far it is slid. Stepped once a
     * frame by whoever draws it.
     */
    public static final class Slide {
        private double restedSeconds;
        private float offset;
        private long nanos;

        /**
         * Moves the slide on to {@code now}: along while {@code rested} on
         * a name {@code overflowPx} too long for its room, home otherwise.
         * Answers how far it is slid.
         */
        public float advance(boolean rested, int overflowPx, long now) {
            double elapsed = this.nanos == 0L ? 0.0D
                    : Math.max(0.0D, (now - this.nanos) / 1.0E9D);
            this.nanos = now;
            return step(rested, overflowPx, elapsed);
        }

        /** As {@link #advance}, {@code elapsedSeconds} after the last step. */
        public float step(boolean rested, int overflowPx,
                          double elapsedSeconds) {
            if (rested && overflowPx > 0 && Motions.enabled()) {
                this.restedSeconds += elapsedSeconds;
                this.offset = (float)Math.min(overflowPx,
                        slideOffset(this.restedSeconds, overflowPx));
            } else {
                this.restedSeconds = 0.0D;
                this.offset = glideHome(this.offset, elapsedSeconds);
            }
            return this.offset;
        }

        /** How far the name is slid, in the units it is drawn in. */
        public float offset() {
            return this.offset;
        }

        /** Back at the start, the pointer's rest forgotten: the name has changed. */
        public void reset() {
            this.restedSeconds = 0.0D;
            this.offset = 0.0F;
        }

        long lastNanos() {
            return this.nanos;
        }
    }

    /* ---- The shared state for rows ---- */

    /**
     * How far the name {@code key} of {@code owner} is slid this frame:
     * along while {@code rested} on it and it runs {@code overflowPx} past
     * its room, home after. A name the pointer has not rested on, or that
     * is home, costs nothing and holds nothing.
     */
    public static float slideOf(Object owner, Object key, boolean rested,
                                int overflowPx) {
        return slideOf(owner, key, rested, overflowPx, System.nanoTime());
    }

    static float slideOf(Object owner, Object key, boolean rested,
                         int overflowPx, long now) {
        forgetStale(now);
        Followed followed = find(owner, key);
        if (followed == null) {
            if (!rested || overflowPx <= 0 || owner == null
                    || !Motions.enabled()) {
                return 0.0F;
            }
            if (FOLLOWED.size() >= MOST_FOLLOWED) {
                FOLLOWED.remove(0);
            }
            followed = new Followed(owner, key);
            FOLLOWED.add(followed);
        }
        if (rested) {
            followed.restedNanos = now;
        } else if (now - followed.restedNanos < HOLD_NANOS) {
            // Another copy of the name, drawn this frame without the
            // pointer: it shows the slide and leaves it alone.
            return followed.slide.offset();
        }
        float offset = followed.slide.advance(rested, overflowPx, now);
        if (!rested && offset <= 0.0F) {
            FOLLOWED.remove(followed);
        }
        return offset;
    }

    /** Forgets every name the shared state follows: the window screen has closed, or the world has. */
    public static void forgetAll() {
        FOLLOWED.clear();
    }

    /** How many names the shared state follows now. */
    static int followedCount() {
        return FOLLOWED.size();
    }

    private static Followed find(Object owner, Object key) {
        if (owner == null) {
            return null;
        }
        for (int index = 0; index < FOLLOWED.size(); index++) {
            Followed followed = FOLLOWED.get(index);
            if (followed.owner == owner && (followed.key == null ? key == null
                    : followed.key.equals(key))) {
                return followed;
            }
        }
        return null;
    }

    private static void forgetStale(long now) {
        for (int index = FOLLOWED.size() - 1; index >= 0; index--) {
            if (now - FOLLOWED.get(index).slide.lastNanos() > FORGET_NANOS) {
                FOLLOWED.remove(index);
            }
        }
    }

    /** A name the shared state follows: its owner, its key and its slide. */
    private static final class Followed {
        final Object owner;
        final Object key;
        final Slide slide = new Slide();
        /** When the pointer last rested on it. */
        long restedNanos;

        Followed(Object owner, Object key) {
            this.owner = owner;
            this.key = key;
        }
    }

    /* ---- Drawing ---- */

    /**
     * Words in the windows' ink ({@link LostTalesUiInk#drawText}) from
     * {@code x}, whole where they fit {@code room}, cut at its end and
     * fading into it where they do not, never sliding: a name nothing can
     * point at, or words that are not a name.
     */
    public static void drawText(FontRenderer font, String text, float x,
                                int y, float room, int rgb, int alpha) {
        drawTextSlid(font, text, x, y, room, rgb, alpha, 0.0F);
    }

    /**
     * As {@link #drawText(FontRenderer, String, float, int, float, int, int)},
     * for a name the pointer can rest on: {@code owner} and {@code key}
     * name it to the shared state, and it slides while {@code rested}.
     */
    public static void drawText(FontRenderer font, String text, float x,
                                int y, float room, int rgb, int alpha,
                                Object owner, Object key, boolean rested) {
        if (font == null || text == null) {
            return;
        }
        int width = font.getStringWidth(text);
        float slide = isCut(width, room)
                ? slideOf(owner, key, rested, overflow(width, room)) : 0.0F;
        drawTextSlid(font, text, x, y, room, rgb, alpha, slide);
    }

    /** As above, slid by {@code slide}: a control holding its own {@link Slide}. */
    public static void drawTextSlid(final FontRenderer font, final String text,
                                    final float x, final int y, float room,
                                    final int rgb, final int alpha,
                                    float slide) {
        if (font == null || text == null
                || alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        draw(x, font.getStringWidth(text), room, slide,
                new LostTalesUiFading.FadingPainter() {
                    @Override
                    public void paint(float share) {
                        drawInkText(font, text, x, y, rgb,
                                Math.round(alpha * share));
                    }
                });
    }

    /**
     * Words in vanilla's shadowed text, as the creator, the map's prompts
     * and the HUD's older panels draw them: {@code argb} carries their
     * opacity, none meaning whole. Cut and fading as
     * {@link #drawText(FontRenderer, String, float, int, float, int, int)},
     * never sliding.
     */
    public static void drawShadowed(FontRenderer font, String text, float x,
                                    int y, float room, int argb) {
        drawShadowedSlid(font, text, x, y, room, argb, 0.0F);
    }

    /** As above, a name the pointer can rest on, named to the shared state. */
    public static void drawShadowed(FontRenderer font, String text, float x,
                                    int y, float room, int argb, Object owner,
                                    Object key, boolean rested) {
        if (font == null || text == null) {
            return;
        }
        int width = font.getStringWidth(text);
        float slide = isCut(width, room)
                ? slideOf(owner, key, rested, overflow(width, room)) : 0.0F;
        drawShadowedSlid(font, text, x, y, room, argb, slide);
    }

    private static void drawShadowedSlid(final FontRenderer font,
                                         final String text, final float x,
                                         final int y, float room,
                                         final int argb, float slide) {
        if (font == null || text == null) {
            return;
        }
        final int alpha = (argb >>> 24) == 0 ? 255 : argb >>> 24;
        draw(x, font.getStringWidth(text), room, slide,
                new LostTalesUiFading.FadingPainter() {
                    @Override
                    public void paint(float share) {
                        drawShadowedText(font, text, x, y, argb,
                                Math.round(alpha * share));
                    }
                });
    }

    /**
     * Something {@code width} wide that {@code painter} draws from
     * {@code x}, in the space the caller draws in: whole where it fits
     * {@code room}; else cut to the room, fading into the end it runs past
     * and, slid {@code slide} along, into the start it has slid past. The
     * painter draws at its own place, at its opacity times the share it is
     * handed; the slide is laid on by the matrix, on a display pixel, so
     * glyphs stay on theirs. The cut narrows whatever cut stands.
     */
    public static void draw(float x, float width, float room, float slide,
                            final LostTalesUiFading.FadingPainter painter) {
        if (painter == null || !(room > 0.0F)) {
            return;
        }
        if (!isCut(width, room)) {
            painter.paint(1.0F);
            return;
        }
        final float slid = snappedSlide(slide, overflow(width, room));
        float depth = fadeDepth(room);
        LostTalesUiFading.drawFadingLocal(Minecraft.getMinecraft(), x,
                x + room, depth, startFade(slid, depth),
                endFade(width, room, slid, depth),
                slid == 0.0F ? painter : new LostTalesUiFading.FadingPainter() {
                    @Override
                    public void paint(float share) {
                        GL11.glPushMatrix();
                        try {
                            GL11.glTranslatef(-slid, 0.0F, 0.0F);
                            painter.paint(share);
                        } finally {
                            GL11.glPopMatrix();
                        }
                    }
                });
    }

    /**
     * A slide laid on the nearest display pixel of the space the caller
     * draws in, and never past the overflow, so the glyphs stay on their
     * pixels and the end stops where it shows.
     */
    private static float snappedSlide(float slide, int overflowPx) {
        if (!(slide > 0.0F)) {
            return 0.0F;
        }
        float held = Math.min(slide, overflowPx);
        MATRIX.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
        double perUnit = MATRIX.get(0) * (double)LostTalesDisplayPixels.scaleFactor();
        return perUnit > 0.0D ? (float)(Math.round(held * perUnit) / perUnit)
                : held;
    }

    /** Words in the windows' ink at {@code x}, which may lie between the GUI's pixels. */
    private static void drawInkText(FontRenderer font, String text, float x,
                                    int y, int rgb, int alpha) {
        if (alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        int whole = (int)Math.floor(x);
        float fraction = x - whole;
        if (fraction == 0.0F) {
            LostTalesUiInk.drawText(font, text, whole, y, rgb, alpha);
            return;
        }
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(fraction, 0.0F, 0.0F);
            LostTalesUiInk.drawText(font, text, whole, y, rgb, alpha);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * Words in vanilla's shadowed text at {@code alpha}: whole, as vanilla
     * draws them; fading, as one picture with their shadow, so the shadow
     * never shows through their strokes.
     */
    private static void drawShadowedText(final FontRenderer font,
                                         final String text, float x,
                                         final int y, int argb, int alpha) {
        if (alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        final int color = alpha << 24 | argb & 0xFFFFFF;
        final int whole = (int)Math.floor(x);
        float fraction = x - whole;
        LostTalesUiInk.beginContent();
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(fraction, 0.0F, 0.0F);
            if (alpha >= 255) {
                font.drawStringWithShadow(text, whole, y, color);
                return;
            }
            // Vanilla's shadow: the colour at a quarter, at its opacity.
            final int shadow = (color & 0xFCFCFC) >> 2 | color & 0xFF000000;
            LostTalesUiFlatLayers.draw(alpha, whole, y,
                    whole + font.getStringWidth(text) + 1,
                    y + font.FONT_HEIGHT + 1,
                    new LostTalesUiFlatLayers.Layers() {
                        @Override
                        public void draw() {
                            font.drawString(text, whole + 1, y + 1, shadow);
                            LostTalesUiFlatLayers.nextLayer();
                            font.drawString(text, whole, y, color);
                        }
                    });
        } finally {
            GL11.glPopMatrix();
        }
    }
}
