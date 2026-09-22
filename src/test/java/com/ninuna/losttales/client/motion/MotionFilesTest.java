package com.ninuna.losttales.client.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * The mod's own motion files: every one reads without a problem, every
 * motion stands in the file of its family, and every motion the code
 * plays is there with the parts, beats and numbers the code asks of it.
 */
public final class MotionFilesTest {
    private static final List<String> FOLLOWERS = Arrays.asList(
            MotionIds.UI_BUTTON_LIT, MotionIds.CHAT_SCROLL,
            MotionIds.CHAT_HOVER_FADE, MotionIds.CHAT_SCROLLBAR_FADE,
            MotionIds.CHAT_MARQUEE_RETURN, MotionIds.SCREEN_JOURNAL_SCROLL,
            MotionIds.SCREEN_DIALOGUE_GLIDE);
    private static final List<String> BUTTONS = Arrays.asList(
            MotionIds.UI_BUTTON_LIFT, MotionIds.UI_BUTTON_TURN,
            MotionIds.UI_BUTTON_SNAP);

    private static Map<String, Motion> bundled() throws IOException {
        Map<String, Motion> motions = new HashMap<String, Motion>();
        for (String family : Motions.FAMILIES) {
            MotionCodec.Result result = MotionCodec.read(text(family));
            assertTrue(family + ".json reads", result.isReadable());
            assertTrue(family + ".json: " + result.problems(),
                    result.problems().isEmpty());
            for (Map.Entry<String, Motion> motion
                    : result.motions().entrySet()) {
                assertEquals(motion.getKey() + " stands in its family's file",
                        family, familyOf(motion.getKey()));
                assertTrue(motion.getKey() + " says what it is for",
                        motion.getValue().about().length() > 0);
                motions.put(motion.getKey(), motion.getValue());
            }
        }
        return motions;
    }

    private static String text(String family) throws IOException {
        InputStream stream = MotionFilesTest.class.getResourceAsStream(
                "/assets/losttales/motion/" + family + ".json");
        assertNotNull(family + ".json is bundled", stream);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) > 0) {
                bytes.write(buffer, 0, read);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            stream.close();
        }
    }

    private static String familyOf(String id) {
        return id.substring(0, id.indexOf('.'));
    }

    private static List<String> codeIds() throws IllegalAccessException {
        List<String> ids = new ArrayList<String>();
        for (Field field : MotionIds.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers)
                    && field.getType() == String.class) {
                ids.add((String)field.get(null));
            }
        }
        return ids;
    }

    @Test
    public void everyMotionTheCodePlaysIsBundled() throws Exception {
        Map<String, Motion> motions = bundled();
        List<String> ids = codeIds();
        assertFalse(ids.isEmpty());
        for (String id : ids) {
            assertTrue(id + " is in " + familyOf(id) + ".json",
                    motions.containsKey(id));
            assertTrue(Motions.FAMILIES.contains(familyOf(id)));
        }
    }

    @Test
    public void everyTransitionGoesBothWaysAndEveryFollowerFollows()
            throws Exception {
        Map<String, Motion> motions = bundled();
        for (String id : codeIds()) {
            Motion motion = motions.get(id);
            if (FOLLOWERS.contains(id)) {
                assertTrue(id + " follows", motion.isFollower());
                assertTrue(id + " takes some time",
                        motion.followSeconds() > 0.0F);
                continue;
            }
            assertFalse(id + " plays beats", motion.isFollower());
            assertNotNull(id + " has a way on", motion.beat(Motion.ON));
            if (!BUTTONS.contains(id)) {
                assertNotNull(id + " has a way off", motion.beat(Motion.OFF));
            }
        }
    }

    @Test
    public void everyButtonHasItsGlyphsPosesAndBeats() throws Exception {
        Map<String, Motion> motions = bundled();
        for (String id : BUTTONS) {
            MotionPart glyph = motions.get(id).part("glyph");
            assertNotNull(id, glyph);
            for (String pose : new String[] {"rest", "on", "pressed"}) {
                assertFalse(id + " " + pose, Float.isNaN(
                        glyph.poseValue(pose, MotionTrack.Y)));
            }
            for (String beat : new String[] {"on", "off", "press", "release"}) {
                assertNotNull(id + " " + beat, glyph.beat(beat));
            }
            assertFalse(Float.isNaN(motions.get(id).param("turn")));
            assertTrue(motions.get(id).param("min_press") > 0.0F);
        }
        // The lift rests on whole pixels, so a settled button lands on
        // the display grid at every GUI Scale.
        MotionPart lift = motions.get(MotionIds.UI_BUTTON_LIFT).part("glyph");
        for (String pose : new String[] {"rest", "on", "pressed"}) {
            float place = lift.poseValue(pose, MotionTrack.Y);
            assertEquals(place, Math.round(place), 0.0F);
        }
    }

    @Test
    public void theChatLineHoverMovesItsChevronAndItsWords() throws Exception {
        Motion hover = bundled().get(MotionIds.CHAT_LINE_HOVER);
        for (String part : new String[] {"chevron", "words"}) {
            MotionPart found = hover.part(part);
            assertNotNull(part, found);
            assertNotNull(part + " on", found.beat(Motion.ON));
            assertNotNull(part + " off", found.beat(Motion.OFF));
            assertTrue(found.poses().containsKey(Motion.REST));
            assertTrue(found.poses().containsKey(Motion.ON));
        }
        assertTrue(hover.param("stagger_span") > 0.0F);
    }

    @Test
    public void everyNumberTheCodeReadsIsThere() throws Exception {
        Map<String, Motion> motions = bundled();
        Map<String, String[]> params = new HashMap<String, String[]>();
        params.put(MotionIds.CHAT_LINE_APPEAR,
                new String[] {"rise", "slide", "follow_through", "fade_lead"});
        params.put(MotionIds.CHAT_BAR_APPEAR,
                new String[] {"distance", "swing", "swings"});
        params.put(MotionIds.SCREEN_OPEN,
                new String[] {"start_x", "start_y", "start_scale"});
        params.put(MotionIds.SCREEN_CONTROL_BAR, new String[] {"delay", "travel"});
        params.put(MotionIds.MAP_POPUP_OPEN, new String[] {"travel", "start_scale"});
        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            for (String name : entry.getValue()) {
                assertFalse(entry.getKey() + " " + name, Float.isNaN(
                        motions.get(entry.getKey()).param(name)));
            }
        }
    }
}
