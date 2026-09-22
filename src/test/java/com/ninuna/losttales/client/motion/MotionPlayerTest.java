package com.ninuna.losttales.client.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.config.LostTalesConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * A player answers where each part stands at any instant: from the time
 * since its beat began, starting each beat wherever the last left the
 * part, and as the settings in force say.
 */
public final class MotionPlayerTest {
    private static final long MILLIS = 1000000L;
    private static final String SLIDE = "test.slide";

    private MotionTestSettings settings;

    @Before
    public void setUp() {
        this.settings = MotionTestSettings.reset();
        MotionTestSettings.preview("{\"test.slide\": {\"parts\": {"
                + "\"box\": {\"poses\": {\"rest\": {\"x\": 0, \"fade\": 1},"
                + "  \"on\": {\"x\": 10, \"fade\": 0.5}, \"far\": {\"x\": 20}},"
                + "  \"beats\": {"
                + "    \"on\": {\"to\": \"on\", \"duration\": 100, \"curve\": \"linear\"},"
                + "    \"off\": {\"to\": \"rest\", \"duration\": 100, \"delay\": 50,"
                + "      \"curve\": \"linear\"},"
                + "    \"hop\": {\"to\": \"on\", \"duration\": 100, \"curve\": \"linear\","
                + "      \"tracks\": {\"x\": [{\"bump\": 4}]}}}},"
                + "\"row\": {\"poses\": {\"rest\": {\"x\": 0}, \"on\": {\"x\": 3}},"
                + "  \"beats\": {\"on\": {\"to\": \"on\", \"duration\": 100,"
                + "    \"stagger\": 10, \"curve\": \"linear\"}}}}}}");
    }

    @After
    public void tearDown() {
        this.settings.restore();
    }

    @Test
    public void aBeatTravelsFromItsStartToItsPose() {
        MotionPlayer player = new MotionPlayer(SLIDE);
        player.settle(Motion.REST);
        player.play(Motion.ON, 0L);
        assertEquals(0.0F, player.value("box", MotionTrack.X, 0L), 1.0E-5F);
        assertEquals(5.0F, player.value("box", MotionTrack.X, 50L * MILLIS),
                1.0E-5F);
        assertEquals(0.75F, player.value("box", MotionTrack.FADE,
                50L * MILLIS), 1.0E-5F);
        assertEquals(10.0F, player.value("box", MotionTrack.X, 100L * MILLIS),
                1.0E-5F);
        assertTrue(player.isSettled(100L * MILLIS));
        assertEquals("on", player.pose("box"));
    }

    @Test
    public void aBeatBegunMidwayStartsWhereThePartStands() {
        MotionPlayer player = new MotionPlayer(SLIDE);
        player.settle(Motion.REST);
        player.play(Motion.ON, 0L);
        player.play(Motion.OFF, 40L * MILLIS);
        // Its delay holds it where it was turned around.
        assertEquals(4.0F, player.value("box", MotionTrack.X, 60L * MILLIS),
                1.0E-5F);
        assertEquals(2.0F, player.value("box", MotionTrack.X, 140L * MILLIS),
                1.0E-5F);
        assertEquals(0.0F, player.value("box", MotionTrack.X, 190L * MILLIS),
                1.0E-5F);
    }

    @Test
    public void theCodeMayNameThePoseABeatGoesTo() {
        MotionPlayer player = new MotionPlayer(SLIDE);
        player.settle(Motion.REST);
        player.play(Motion.ON, "far", 0L);
        assertEquals(20.0F, player.target("box", MotionTrack.X), 0.0F);
        assertEquals(10.0F, player.value("box", MotionTrack.X, 50L * MILLIS),
                1.0E-5F);
        // A track the pose leaves alone keeps its value.
        assertEquals(1.0F, player.value("box", MotionTrack.FADE, 50L * MILLIS),
                1.0E-5F);
    }

    @Test
    public void aBumpRidesTheTravelAndReturns() {
        MotionPlayer player = new MotionPlayer(SLIDE);
        player.settle(Motion.REST);
        player.play("hop", 0L);
        assertEquals(9.0F, player.value("box", MotionTrack.X, 50L * MILLIS),
                1.0E-4F);
        assertEquals(10.0F, player.value("box", MotionTrack.X, 100L * MILLIS),
                1.0E-5F);
    }

    @Test
    public void staggeredItemsStartOneAfterAnother() {
        MotionPlayer player = new MotionPlayer(SLIDE);
        player.settle(Motion.REST);
        player.play(Motion.ON, 0L);
        assertEquals(1.5F, player.value("row", MotionTrack.X, 0, 50L * MILLIS),
                1.0E-5F);
        assertEquals(1.2F, player.value("row", MotionTrack.X, 1, 50L * MILLIS),
                1.0E-5F);
        assertEquals(1.35F, player.value("row", MotionTrack.X, 0.5F,
                50L * MILLIS), 1.0E-5F);
        assertTrue(player.isSettled(100L * MILLIS));
        assertFalse(player.isSettled(100L * MILLIS, 2));
        assertTrue(player.isSettled(120L * MILLIS, 2));
    }

    @Test
    public void thePartsOfAMotionPlayTogether() {
        MotionPlayer player = new MotionPlayer(SLIDE);
        player.settle(Motion.REST);
        player.play(Motion.OFF, 0L);
        // "row" has no off beat and carries on resting.
        assertNull(player.beat("row"));
        assertEquals(Motion.OFF, player.beat("box"));
    }

    @Test
    public void motionOffLandsEverythingAtOnce() {
        LostTalesConfig.animations = false;
        MotionPlayer player = new MotionPlayer(SLIDE);
        player.settle(Motion.REST);
        player.play(Motion.ON, 0L);
        assertEquals(10.0F, player.value("box", MotionTrack.X, 0L), 0.0F);
        assertTrue(player.isSettled(0L, 5));
    }

    @Test
    public void reducedMotionSetsTravelDownAndKeepsTheFadeShort() {
        LostTalesConfig.reducedMotion = true;
        MotionPlayer player = new MotionPlayer(SLIDE);
        player.settle(Motion.REST);
        player.play("hop", 0L);
        assertEquals(10.0F, player.value("box", MotionTrack.X, 0L), 0.0F);
        assertEquals(0.75F, player.value("box", MotionTrack.FADE,
                Motions.REDUCED_MILLIS / 2 * MILLIS), 1.0E-5F);
        assertEquals(0.5F, player.value("box", MotionTrack.FADE,
                Motions.REDUCED_MILLIS * MILLIS), 1.0E-5F);
    }

    @Test
    public void speedScalesEveryTime() {
        LostTalesConfig.animationSpeed = 2.0D;
        MotionPlayer player = new MotionPlayer(SLIDE);
        player.settle(Motion.REST);
        player.play(Motion.ON, 0L);
        assertEquals(10.0F, player.value("box", MotionTrack.X, 50L * MILLIS),
                1.0E-5F);
    }

    @Test
    public void anUnplayedPartStandsNeutral() {
        MotionPlayer player = new MotionPlayer(SLIDE);
        assertEquals(1.0F, player.value("box", MotionTrack.FADE, 0L), 0.0F);
        assertEquals(1.0F, player.value("nothing", MotionTrack.STRETCH_X, 0L),
                0.0F);
    }
}
