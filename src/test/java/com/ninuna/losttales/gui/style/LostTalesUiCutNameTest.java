package com.ninuna.losttales.gui.style;

import com.ninuna.losttales.client.motion.MotionTestSettings;
import com.ninuna.losttales.config.LostTalesConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A cut name (LOOK-37): when words are cut, how deep and how strongly
 * they fade into the ends they are cut at, the display columns the fade
 * is drawn in, how far a name rested on slides over time, what the
 * motion settings do to it, and the shared state rows name themselves to,
 * which never lets one row's or one window's slide reach another's.
 */
public final class LostTalesUiCutNameTest {

    private static final double EPSILON = 1.0E-6D;
    private static final long SECOND = 1000000000L;

    private MotionTestSettings settings;

    @Before
    public void setUp() {
        this.settings = MotionTestSettings.reset();
        // The slide as the motion file ships it: half a second's wait,
        // twenty pixels a second, a rest of 0.8 s, home in a tenth.
        MotionTestSettings.preview("{"
                + "\"window.tab.marquee\": {\"duration\": 500,"
                + " \"curve\": \"linear\","
                + " \"params\": {\"speed\": 20, \"pause\": 800}},"
                + "\"window.marquee.return\": {\"follow\": 0.1}}");
        LostTalesUiCutName.forgetAll();
    }

    @After
    public void tearDown() {
        LostTalesUiCutName.forgetAll();
        this.settings.restore();
    }

    /* ---- When a name is cut ---- */

    @Test
    public void wordsAreCutOnlyWhereTheyRunPastTheirRoom() {
        assertFalse(LostTalesUiCutName.isCut(40.0F, 40.0F));
        assertFalse(LostTalesUiCutName.isCut(39.0F, 40.0F));
        assertTrue(LostTalesUiCutName.isCut(41.0F, 40.0F));
        assertTrue(LostTalesUiCutName.isCut(40.0F, 39.5F));
        assertTrue(LostTalesUiCutName.isCut(10.0F, 0.0F));
    }

    @Test
    public void theOverflowIsTheWholePixelsTheRoomCannotShow() {
        assertEquals(0, LostTalesUiCutName.overflow(40.0F, 40.0F));
        assertEquals(0, LostTalesUiCutName.overflow(30.0F, 40.0F));
        assertEquals(10, LostTalesUiCutName.overflow(40.0F, 30.0F));
        // A fraction of a pixel short still needs the whole pixel.
        assertEquals(10, LostTalesUiCutName.overflow(40.0F, 30.5F));
        assertEquals(12, LostTalesUiCutName.overflow(12.0F, -3.0F));
    }

    @Test
    public void cutWordsStandTheirRoomWide() {
        assertEquals(30.0F, LostTalesUiCutName.shownWidth(40.0F, 30.0F), 0.0F);
        assertEquals(20.0F, LostTalesUiCutName.shownWidth(20.0F, 30.0F), 0.0F);
        assertEquals(0.0F, LostTalesUiCutName.shownWidth(20.0F, -5.0F), 0.0F);
    }

    /* ---- The fade ---- */

    @Test
    public void theFadeIsALineDeepButNeverMoreThanAThirdOfTheRoom() {
        assertEquals(LostTalesUiFading.REACH,
                LostTalesUiCutName.fadeDepth(90.0F), 0.0F);
        assertEquals(10.0F, LostTalesUiCutName.fadeDepth(30.0F), 0.0001F);
        assertEquals(0.0F, LostTalesUiCutName.fadeDepth(-4.0F), 0.0F);
    }

    @Test
    public void aNameAtHomeFadesIntoItsEndAlone() {
        float depth = LostTalesUiCutName.fadeDepth(30.0F);
        assertEquals(0.0F, LostTalesUiCutName.startFade(0.0F, depth), 0.0F);
        assertEquals(1.0F, LostTalesUiCutName.endFade(60.0F, 30.0F, 0.0F,
                depth), 0.0F);
        // A little past the end fades a little.
        assertEquals(0.5F, LostTalesUiCutName.endFade(35.0F, 30.0F, 0.0F,
                depth), 0.0001F);
    }

    @Test
    public void aSlidNameFadesIntoTheStartItHasPassedAndShowsItsEnd() {
        float depth = LostTalesUiCutName.fadeDepth(30.0F);
        assertEquals(0.5F, LostTalesUiCutName.startFade(5.0F, depth), 0.0001F);
        assertEquals(1.0F, LostTalesUiCutName.startFade(30.0F, depth), 0.0F);
        // Slid all the way, its end stands in the room and fades no more.
        assertEquals(0.0F, LostTalesUiCutName.endFade(60.0F, 30.0F, 30.0F,
                depth), 0.0F);
    }

    /** At GUI scale three every fading slice is one of three display pixels to a GUI pixel. */
    @Test
    public void theFadeIsDrawnInWholeDisplayColumns() {
        assertArrayEquals(new int[] {30, 120, 0, 30},
                LostTalesUiFading.fadeColumns(0.0D, 3.0D, 10.0D, 40.0D,
                        10.0F, false, true));
        assertArrayEquals(new int[] {30, 120, 30, 30},
                LostTalesUiFading.fadeColumns(0.0D, 3.0D, 10.0D, 40.0D,
                        10.0F, true, true));
    }

    @Test
    public void aShortRoomSharesItsColumnsBetweenItsTwoFades() {
        // Four GUI pixels at scale three: twelve columns, six each side.
        assertArrayEquals(new int[] {0, 12, 6, 6},
                LostTalesUiFading.fadeColumns(0.0D, 3.0D, 0.0D, 4.0D, 12.0F,
                        true, true));
    }

    @Test
    public void theCutRoundsInwardOnAFractionalPlace() {
        int[] columns = LostTalesUiFading.fadeColumns(1.5D, 2.0D, 0.25D,
                10.0D, 3.0F, false, true);
        assertEquals(2, columns[0]);
        assertEquals(21, columns[1]);
        assertNull(LostTalesUiFading.fadeColumns(0.0D, 3.0D, 5.0D, 5.1D,
                3.0F, false, true));
    }

    @Test
    public void aSliceFadesByItsMiddle() {
        assertEquals(0.125F, LostTalesUiFading.sliceShare(1.0F, 0, 4), 0.0001F);
        assertEquals(0.875F, LostTalesUiFading.sliceShare(1.0F, 3, 4), 0.0001F);
        assertEquals(1.0F, LostTalesUiFading.sliceShare(0.0F, 0, 4), 0.0F);
        assertEquals(0.5625F, LostTalesUiFading.sliceShare(0.5F, 0, 4), 0.0001F);
    }

    /* ---- The slide over time ---- */

    /** Still, out, rest, back, rest — on the clock alone. */
    @Test
    public void theSlideWaitsThenGoesOutRestsAndComesBack() {
        int overflow = 40;
        double delay = 0.5D;
        double speed = 20.0D;
        double pause = 0.8D;
        double slide = overflow / speed;
        assertEquals(0.0D, slide(0.0D, overflow), EPSILON);
        assertEquals(0.0D, slide(delay * 0.8D, overflow), EPSILON);
        // Half a second in: half a second's worth of sliding.
        assertEquals(0.5D * speed, slide(delay + 0.5D, overflow), EPSILON);
        // At the end of the slide and through the rest: the whole overflow.
        assertEquals(overflow, slide(delay + slide, overflow), EPSILON);
        assertEquals(overflow, slide(delay + slide + pause * 0.5D, overflow),
                EPSILON);
        // Halfway back.
        assertEquals(overflow / 2.0D,
                slide(delay + slide + pause + slide / 2.0D, overflow),
                EPSILON);
        // Home, resting, and round again.
        double cycle = 2.0D * (slide + pause);
        assertEquals(0.0D, slide(delay + cycle - pause * 0.5D, overflow),
                EPSILON);
        assertEquals(0.5D * speed, slide(delay + cycle + 0.5D, overflow),
                EPSILON);
    }

    /** The slide as {@link #setUp} plays it. */
    private static double slide(double rested, int overflow) {
        return LostTalesUiCutName.slideOffset(rested, overflow, 0.5D, 20.0D,
                0.8D);
    }

    @Test
    public void theMotionFileTimesTheSlide() {
        assertEquals(10.0D, LostTalesUiCutName.slideOffset(1.0D, 40), 0.001D);
        LostTalesConfig.animationSpeed = 2.0D;
        // Twice as fast: the wait halves and the pixels come twice as quick.
        assertEquals(20.0D, LostTalesUiCutName.slideOffset(0.75D, 40), 0.001D);
    }

    @Test
    public void nothingSlidesWhenNothingIsCut() {
        assertEquals(0.0D, LostTalesUiCutName.slideOffset(5.0D, 0), EPSILON);
        assertEquals(0.0D, LostTalesUiCutName.slideOffset(5.0D, -3), EPSILON);
    }

    @Test
    public void theSlideNeverLeavesTheOverflow() {
        int overflow = 17;
        for (double time = 0.0D; time < 20.0D; time += 0.037D) {
            double offset = LostTalesUiCutName.slideOffset(time, overflow);
            assertTrue(offset >= -EPSILON);
            assertTrue(offset <= overflow + EPSILON);
        }
    }

    /** Under reduced motion the slides are jumps: the end shows whole, then the start. */
    @Test
    public void aReducedSlideJumpsBetweenItsEnds() {
        int overflow = 40;
        double pause = 0.8D;
        assertEquals(0.0D, LostTalesUiCutName.slideOffset(0.4D, overflow,
                0.5D, Double.POSITIVE_INFINITY, pause), EPSILON);
        assertEquals(overflow, LostTalesUiCutName.slideOffset(0.6D, overflow,
                0.5D, Double.POSITIVE_INFINITY, pause), EPSILON);
        assertEquals(0.0D, LostTalesUiCutName.slideOffset(0.5D + pause + 0.1D,
                overflow, 0.5D, Double.POSITIVE_INFINITY, pause), EPSILON);
        LostTalesConfig.reducedMotion = true;
        boolean reachedTheEnd = false;
        for (double time = 0.0D; time < 6.0D; time += 0.01D) {
            double offset = LostTalesUiCutName.slideOffset(time, overflow);
            assertTrue(offset == 0.0D || offset == overflow);
            reachedTheEnd |= offset == overflow;
        }
        assertTrue(reachedTheEnd);
    }

    /* ---- A control's own slide ---- */

    @Test
    public void aRestedNameSlidesAndGlidesHomeWhenLeft() {
        LostTalesUiCutName.Slide slide = new LostTalesUiCutName.Slide();
        assertEquals(0.0F, slide.step(true, 40, 0.0D), 0.0F);
        assertEquals(0.0F, slide.step(true, 40, 0.4D), 0.0F);
        assertEquals(10.0F, slide.step(true, 40, 0.6D), 0.001F);
        // Left: on its way home, not there at once.
        float leaving = slide.step(false, 40, 0.02D);
        assertTrue(leaving > 0.0F && leaving < 10.0F);
        assertEquals(0.0F, slide.step(false, 40, 2.0D), 0.0F);
        // Rested again, it waits from the start once more.
        assertEquals(0.0F, slide.step(true, 40, 0.4D), 0.0F);
    }

    @Test
    public void aNameThatFitsNeverSlides() {
        LostTalesUiCutName.Slide slide = new LostTalesUiCutName.Slide();
        for (int frame = 0; frame < 100; frame++) {
            assertEquals(0.0F, slide.step(true, 0, 0.05D), 0.0F);
        }
    }

    @Test
    public void withAnimationsOffACutNameStaysAtItsStart() {
        LostTalesConfig.animations = false;
        LostTalesUiCutName.Slide slide = new LostTalesUiCutName.Slide();
        for (int frame = 0; frame < 100; frame++) {
            assertEquals(0.0F, slide.step(true, 40, 0.05D), 0.0F);
        }
    }

    @Test
    public void switchingAnimationsOffMidSlideSendsTheNameHomeAtOnce() {
        LostTalesUiCutName.Slide slide = new LostTalesUiCutName.Slide();
        slide.step(true, 40, 1.0D);
        assertTrue(slide.offset() > 0.0F);
        LostTalesConfig.animations = false;
        assertEquals(0.0F, slide.step(true, 40, 0.01D), 0.0F);
    }

    @Test
    public void underReducedMotionANameGoesHomeAtOnce() {
        LostTalesUiCutName.Slide slide = new LostTalesUiCutName.Slide();
        slide.step(true, 40, 1.0D);
        LostTalesConfig.reducedMotion = true;
        assertEquals(0.0F, slide.step(false, 40, 0.01D), 0.0F);
    }

    @Test
    public void aResetSlideStartsFromHome() {
        LostTalesUiCutName.Slide slide = new LostTalesUiCutName.Slide();
        slide.step(true, 40, 1.0D);
        slide.reset();
        assertEquals(0.0F, slide.offset(), 0.0F);
        assertEquals(0.0F, slide.step(true, 40, 0.4D), 0.0F);
    }

    /* ---- The shared state rows name themselves to ---- */

    @Test
    public void aNameNobodyRestsOnHoldsNothing() {
        Object page = new Object();
        for (int row = 0; row < 50; row++) {
            assertEquals(0.0F, LostTalesUiCutName.slideOf(page,
                    Integer.valueOf(row), false, 40, row * SECOND / 60), 0.0F);
        }
        assertEquals(0, LostTalesUiCutName.followedCount());
    }

    @Test
    public void aRowsSlideNeverReachesAnotherRowOrWindow() {
        Object page = new Object();
        Object otherWindow = new Object();
        Integer row = Integer.valueOf(3);
        long now = rest(page, row, SECOND, 1.0D) + SECOND / 60;
        assertTrue(LostTalesUiCutName.slideOf(page, row, true, 40, now)
                > 0.0F);
        // The same row of another window, and another row of this one.
        assertEquals(0.0F, LostTalesUiCutName.slideOf(otherWindow, row,
                false, 40, now), 0.0F);
        assertEquals(0.0F, LostTalesUiCutName.slideOf(page,
                Integer.valueOf(4), false, 40, now), 0.0F);
        assertEquals(1, LostTalesUiCutName.followedCount());
    }

    /** A window's pinned copy draws its names without the pointer, in the same frame. */
    @Test
    public void aCopyDrawnWithoutThePointerShowsTheSlideAndLeavesItAlone() {
        Object page = new Object();
        Integer row = Integer.valueOf(2);
        long now = rest(page, row, SECOND, 1.0D);
        float rested = LostTalesUiCutName.slideOf(page, row, true, 40, now);
        assertEquals(rested, LostTalesUiCutName.slideOf(page, row, false, 40,
                now), 0.0F);
        now += SECOND / 10;
        float later = LostTalesUiCutName.slideOf(page, row, true, 40, now);
        assertTrue(later > rested);
    }

    @Test
    public void aRowLeftGlidesHomeAndIsForgottenThere() {
        Object page = new Object();
        Integer row = Integer.valueOf(0);
        long now = rest(page, row, SECOND, 1.0D);
        now += SECOND / 60;
        float leaving = LostTalesUiCutName.slideOf(page, row, false, 40, now);
        assertTrue(leaving > 0.0F);
        assertEquals(1, LostTalesUiCutName.followedCount());
        for (int frame = 0; frame < 120; frame++) {
            now += SECOND / 60;
            LostTalesUiCutName.slideOf(page, row, false, 40, now);
        }
        assertEquals(0.0F, LostTalesUiCutName.slideOf(page, row, false, 40,
                now + SECOND / 60), 0.0F);
        assertEquals(0, LostTalesUiCutName.followedCount());
    }

    @Test
    public void theSharedStateFollowsAFewNamesAtMost() {
        Object page = new Object();
        long now = SECOND;
        for (int row = 0; row < LostTalesUiCutName.MOST_FOLLOWED * 3; row++) {
            now += SECOND / 600;
            LostTalesUiCutName.slideOf(page, Integer.valueOf(row), true, 40,
                    now);
        }
        assertTrue(LostTalesUiCutName.followedCount()
                <= LostTalesUiCutName.MOST_FOLLOWED);
    }

    @Test
    public void aNameNotDrawnForAMomentIsForgotten() {
        Object page = new Object();
        Integer row = Integer.valueOf(7);
        long now = rest(page, row, SECOND, 1.0D);
        assertEquals(1, LostTalesUiCutName.followedCount());
        now += LostTalesUiCutName.FORGET_NANOS + 1L;
        // Another name drawn later sweeps the stale one away; the row
        // rested on again waits from the start.
        LostTalesUiCutName.slideOf(page, Integer.valueOf(8), false, 40, now);
        assertEquals(0, LostTalesUiCutName.followedCount());
        assertEquals(0.0F, LostTalesUiCutName.slideOf(page, row, true, 40,
                now), 0.0F);
    }

    @Test
    public void closingTheScreenForgetsEverySlide() {
        Object page = new Object();
        rest(page, Integer.valueOf(1), SECOND, 1.0D);
        rest(page, Integer.valueOf(2), SECOND, 1.0D);
        assertEquals(2, LostTalesUiCutName.followedCount());
        LostTalesUiCutName.forgetAll();
        assertEquals(0, LostTalesUiCutName.followedCount());
    }

    @Test
    public void aNameWithoutAnOwnerNeverSlides() {
        assertEquals(0.0F, LostTalesUiCutName.slideOf(null, "name", true, 40,
                SECOND), 0.0F);
        assertEquals(0.0F, LostTalesUiCutName.slideOf(null, "name", true, 40,
                2 * SECOND), 0.0F);
        assertEquals(0, LostTalesUiCutName.followedCount());
    }

    @Test
    public void withAnimationsOffTheSharedStateHoldsNothing() {
        LostTalesConfig.animations = false;
        assertEquals(0.0F, LostTalesUiCutName.slideOf(new Object(), "name",
                true, 40, SECOND), 0.0F);
        assertEquals(0, LostTalesUiCutName.followedCount());
    }

    /**
     * Rests the pointer on a name from {@code from}, a frame at a time,
     * for {@code seconds}; answers the instant it stopped.
     */
    private static long rest(Object owner, Object key, long from,
                             double seconds) {
        long now = from;
        long end = from + Math.round(seconds * SECOND);
        while (now < end) {
            LostTalesUiCutName.slideOf(owner, key, true, 40, now);
            now += SECOND / 60;
        }
        LostTalesUiCutName.slideOf(owner, key, true, 40, now);
        return now;
    }
}
