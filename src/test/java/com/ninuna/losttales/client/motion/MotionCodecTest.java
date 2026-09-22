package com.ninuna.losttales.client.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Motion files read inside their bounds: a motion that cannot be used is
 * left out with a line saying why, the rest of the file still counts,
 * and writing a file back reads as the same motions.
 */
public final class MotionCodecTest {
    @Test
    public void theShortFormIsAValueFromOffToOn() {
        MotionCodec.Result result = MotionCodec.read("{\"a.fade\": {"
                + "\"about\": \"A fade.\", \"duration\": 140, \"curve\": \"settle\","
                + "\"off\": {\"duration\": 90, \"curve\": \"linear\"}}}");
        assertTrue(result.isReadable());
        assertTrue(result.problems().isEmpty());
        Motion motion = result.motions().get("a.fade");
        assertEquals("A fade.", motion.about());
        MotionBeat on = motion.beat(Motion.ON);
        MotionBeat off = motion.beat(Motion.OFF);
        assertEquals(140, on.durationMillis());
        assertEquals(MotionCurve.SETTLE, on.curve());
        assertEquals(90, off.durationMillis());
        assertEquals(MotionCurve.LINEAR, off.curve());
        assertTrue(MotionCodec.isTransition(motion));
    }

    @Test
    public void aFollowerChasesItsValue() {
        Motion motion = MotionCodec.read("{\"a.scroll\": {\"follow\": 0.06}}")
                .motions().get("a.scroll");
        assertTrue(motion.isFollower());
        assertEquals(0.06F, motion.followSeconds(), 1.0E-6F);
        Motion slow = MotionCodec.read("{\"a.slow\": {\"follow\": 90}}")
                .motions().get("a.slow");
        assertEquals(MotionCodec.MAX_FOLLOW_SECONDS, slow.followSeconds(), 0.0F);
    }

    @Test
    public void partsPosesBeatsAndLayersAreRead() {
        MotionCodec.Result result = MotionCodec.read("{\"a.button\": {"
                + "\"parts\": {\"glyph\": {"
                + "  \"poses\": {\"rest\": {\"y\": 0}, \"on\": {\"y\": -1}},"
                + "  \"beats\": {\"on\": {\"to\": \"on\", \"duration\": 170,"
                + "    \"delay\": 10, \"stagger\": 5, \"curve\": \"ease_out\","
                + "    \"tracks\": {\"y\": [{\"ring\": -1, \"lobes\": 3}]},"
                + "    \"from\": {\"rest\": {\"y\": [{\"travel\": \"ease_out\", \"begin\": 0.28},"
                + "      {\"bump\": 0.34, \"end\": 0.28},"
                + "      {\"keys\": [[0.5, 0.2, \"linear\"]]}]}}}}}},"
                + "\"params\": {\"turn\": 5}}}");
        assertTrue(result.problems().toString(), result.problems().isEmpty());
        Motion motion = result.motions().get("a.button");
        MotionPart glyph = motion.part("glyph");
        assertEquals(-1.0F, glyph.poseValue("on", MotionTrack.Y), 0.0F);
        assertTrue(Float.isNaN(glyph.poseValue("on", MotionTrack.X)));
        MotionBeat on = glyph.beat("on");
        assertEquals("on", on.to());
        assertEquals(170, on.durationMillis());
        assertEquals(10, on.delayMillis());
        assertEquals(5, on.staggerMillis());
        assertEquals(MotionLayer.Kind.RING, on.layers(MotionTrack.Y, null)[0].kind());
        MotionLayer[] fromRest = on.layers(MotionTrack.Y, "rest");
        assertEquals(3, fromRest.length);
        assertEquals(MotionLayer.Kind.TRAVEL, fromRest[0].kind());
        assertEquals(0.28F, fromRest[0].begin(), 1.0E-6F);
        assertEquals(MotionLayer.Kind.BUMP, fromRest[1].kind());
        assertEquals(MotionLayer.Kind.KEYS, fromRest[2].kind());
        assertEquals(5.0F, motion.param("turn"), 0.0F);
        assertTrue(Float.isNaN(motion.param("missing")));
    }

    @Test
    public void numbersAreKeptInsideTheirBounds() {
        MotionCodec.Result result = MotionCodec.read("{\"a.wild\": {"
                + "\"parts\": {\"main\": {\"poses\": {\"far\": {\"x\": 90000, \"fade\": 7}},"
                + "\"beats\": {\"on\": {\"to\": \"far\", \"duration\": 999999,"
                + " \"delay\": -40, \"tracks\": {\"x\": [{\"bump\": 1000000}]}}}}},"
                + "\"params\": {\"big\": 1e12}}}");
        Motion motion = result.motions().get("a.wild");
        MotionPart main = motion.part("main");
        assertEquals(256.0F, main.poseValue("far", MotionTrack.X), 0.0F);
        assertEquals(1.0F, main.poseValue("far", MotionTrack.FADE), 0.0F);
        MotionBeat on = main.beat("on");
        assertEquals(MotionBeat.MAX_MILLIS, on.durationMillis());
        assertEquals(0, on.delayMillis());
        assertEquals(512.0F, on.layers(MotionTrack.X, null)[0].size(), 0.0F);
        assertEquals(MotionCodec.MAX_PARAM, motion.param("big"), 0.0F);
    }

    @Test
    public void aBrokenMotionIsLeftOutAndTheRestCounts() {
        MotionCodec.Result result = MotionCodec.read("{"
                + "\"good.one\": {\"duration\": 100},"
                + "\"Bad Id\": {\"duration\": 100},"
                + "\"no.shape\": {\"about\": \"nothing\"},"
                + "\"bad.curve\": {\"duration\": 100, \"curve\": \"wobbly\"},"
                + "\"bad.track\": {\"parts\": {\"main\": {\"poses\": {\"on\": {\"spin\": 1}}}}}}");
        assertTrue(result.isReadable());
        assertNotNull(result.motions().get("good.one"));
        assertNull(result.motions().get("no.shape"));
        assertFalse(result.motions().containsKey("Bad Id"));
        // A curve it does not know falls back and says so.
        assertEquals(MotionCurve.EASE_OUT,
                result.motions().get("bad.curve").beat(Motion.ON).curve());
        assertEquals(4, result.problems().size());
    }

    @Test
    public void aFileThatIsNotJsonCountsForNothing() {
        assertFalse(MotionCodec.read("{ not json").isReadable());
        assertFalse(MotionCodec.read("[1, 2]").isReadable());
        assertFalse(MotionCodec.read(null).isReadable());
        StringBuilder huge = new StringBuilder("{\"a\": \"");
        while (huge.length() <= MotionCodec.MAX_FILE_BYTES) {
            huge.append("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        }
        assertFalse(MotionCodec.read(huge.append("\"}").toString())
                .isReadable());
    }

    @Test
    public void aBeatGoingToAPoseThePartLacksGoesNowhere() {
        MotionCodec.Result result = MotionCodec.read("{\"a.b\": {\"parts\": {"
                + "\"main\": {\"poses\": {\"rest\": {\"x\": 0}},"
                + "\"beats\": {\"on\": {\"to\": \"missing\", \"duration\": 10}}}}}}");
        assertEquals("", result.motions().get("a.b").part("main").beat("on").to());
        assertEquals(1, result.problems().size());
    }

    @Test
    public void writingAFileBackReadsAsTheSameMotions() {
        String text = "{"
                + "\"a.fade\": {\"about\": \"A fade.\", \"duration\": 140, \"curve\": \"settle\"},"
                + "\"a.scroll\": {\"follow\": 0.06},"
                + "\"a.button\": {\"parts\": {\"glyph\": {"
                + "  \"poses\": {\"rest\": {\"y\": 0}, \"on\": {\"y\": -1.25}},"
                + "  \"beats\": {\"on\": {\"to\": \"on\", \"duration\": 116, \"curve\": \"back_out\","
                + "    \"tracks\": {\"y\": [{\"ring\": -1.25, \"lobes\": 3, \"end\": 0.5}]},"
                + "    \"from\": {\"rest\": {\"y\": [{\"travel\": \"cubic-bezier(0.2, 0, 0, 1)\","
                + "      \"begin\": 0.28}]}}}}}},"
                + "  \"params\": {\"turn\": 2.5}}}";
        MotionCodec.Result first = MotionCodec.read(text);
        String written = MotionCodec.write(first.motions());
        MotionCodec.Result second = MotionCodec.read(written);
        assertTrue(second.problems().isEmpty());
        assertEquals(written, MotionCodec.write(second.motions()));
        assertTrue(written.contains("\"duration\": 140"));
        assertTrue(written.contains("\"turn\": 2.5"));
        assertTrue(written.contains("cubic-bezier(0.2, 0, 0, 1)"));
    }
}
