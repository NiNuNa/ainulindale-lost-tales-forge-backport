package com.ninuna.losttales.client.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * The Motion Lab's list keeps every motion under the heading of its
 * family, in the families' order and each file's own; the well's words
 * narrow it to the motions whose id or words about themselves hold them,
 * and a family with nothing left loses its heading.
 */
public final class MotionLabListTest {
    private static final List<String> FAMILIES = Arrays.asList("ui", "window",
            "chat");

    /** Motions as their files would give them: id, family, about. */
    private static final class Fixed implements MotionLabList.Source {
        private final Map<String, String[]> motions =
                new LinkedHashMap<String, String[]>();

        Fixed with(String id, String family, String about) {
            this.motions.put(id, new String[] {family, about});
            return this;
        }

        @Override
        public List<String> ids() {
            return new ArrayList<String>(this.motions.keySet());
        }

        @Override
        public String family(String id) {
            String[] motion = this.motions.get(id);
            return motion == null ? "" : motion[0];
        }

        @Override
        public String about(String id) {
            String[] motion = this.motions.get(id);
            return motion == null ? "" : motion[1];
        }
    }

    private static Fixed motions() {
        // File order puts chat before ui; the families' order wins.
        return new Fixed()
                .with("chat.line.hover", "chat", "A chat line under the pointer")
                .with("ui.button.lift", "ui", "A glyph button rising")
                .with("chat.row.move", "chat", "A row gliding to its place")
                .with("ui.button.snap", "ui", "Further and quicker, like a switch")
                .with("stray.motion", "", "No family file holds it");
    }

    private static List<String> described(List<MotionLabList.Line> lines) {
        List<String> described = new ArrayList<String>();
        for (MotionLabList.Line line : lines) {
            described.add(line.isMotion() ? line.id : "# " + line.family);
        }
        return described;
    }

    @Test
    public void everyMotionStandsUnderItsFamilyInTheFamiliesOrder() {
        List<MotionLabList.Line> lines = MotionLabList.of(FAMILIES, motions(),
                "");
        assertEquals(Arrays.asList("# ui", "ui.button.lift", "ui.button.snap",
                "# chat", "chat.line.hover", "chat.row.move"),
                described(lines));
    }

    @Test
    public void aFamilyWithNoMotionHasNoHeading() {
        assertFalse(described(MotionLabList.of(FAMILIES, motions(), ""))
                .contains("# window"));
    }

    @Test
    public void theWordsFindAMotionByItsIdOrByWhatItIsFor() {
        assertEquals(Arrays.asList("# chat", "chat.line.hover"),
                described(MotionLabList.of(FAMILIES, motions(), "line hover")));
        assertEquals(Arrays.asList("# ui", "ui.button.snap"),
                described(MotionLabList.of(FAMILIES, motions(), "SWITCH")));
        assertEquals(Arrays.asList("# ui", "ui.button.lift", "ui.button.snap",
                "# chat", "chat.line.hover", "chat.row.move"),
                described(MotionLabList.of(FAMILIES, motions(), "   ")));
        assertTrue(MotionLabList.of(FAMILIES, motions(), "nothing here")
                .isEmpty());
    }

    @Test
    public void theMotionsFoundAreTheLinesThatAreNotHeadings() {
        List<MotionLabList.Line> lines = MotionLabList.of(FAMILIES, motions(),
                "button");
        assertEquals(Arrays.asList("ui.button.lift", "ui.button.snap"),
                MotionLabList.motions(lines));
        assertTrue(MotionLabList.holds(lines, "ui.button.snap"));
        assertFalse(MotionLabList.holds(lines, "chat.row.move"));
        assertFalse(MotionLabList.holds(lines, null));
    }

    @Test
    public void walkingStopsAtEitherEndAndStartsFromTheFirst() {
        List<MotionLabList.Line> lines = MotionLabList.of(FAMILIES, motions(),
                "");
        assertEquals("ui.button.snap", MotionLabList.step(lines,
                "ui.button.lift", 1));
        assertEquals("chat.line.hover", MotionLabList.step(lines,
                "ui.button.snap", 1));
        assertEquals("chat.row.move", MotionLabList.step(lines,
                "chat.row.move", 1));
        assertEquals("ui.button.lift", MotionLabList.step(lines,
                "ui.button.lift", -1));
        assertEquals("the first, for a motion not listed", "ui.button.lift",
                MotionLabList.step(lines, "stray.motion", 1));
        assertNull(MotionLabList.step(MotionLabList.of(FAMILIES, motions(),
                "nothing here"), null, 0));
    }

    @Test
    public void aLineNamesItsMotionWithoutTheFamily() {
        assertEquals("line.hover", MotionLabList.shortName("chat.line.hover"));
        assertEquals("plain", MotionLabList.shortName("plain"));
        assertEquals("ends.", MotionLabList.shortName("ends."));
    }
}
