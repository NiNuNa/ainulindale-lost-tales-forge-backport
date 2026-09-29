package com.ninuna.losttales.client.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * The Motion Lab's steppers: a number steps by its own amount, ten at a
 * time when fast, lands on its step's grid and never leaves its bounds;
 * a curve and a choice step round through their names. A step that
 * changes the motion says so, and picking another part or beat only
 * builds the rows again. The edit reads back as a file would.
 */
public final class MotionLabEditorTest {
    private static final float EXACT = 1.0E-6F;

    private int changes;

    private MotionLabEditor editor(String id, String json) {
        MotionLabEditor editor = new MotionLabEditor(
                new MotionLabEditor.Words() {
                    @Override
                    public String get(String key) {
                        return key;
                    }
                }, new MotionLabEditor.Listener() {
                    @Override
                    public void changed() {
                        MotionLabEditorTest.this.changes++;
                    }
                });
        editor.edit(id, new JsonParser().parse(json).getAsJsonObject());
        return editor;
    }

    private static MotionLabEditor.Row row(MotionLabEditor editor,
                                           String label) {
        for (MotionLabEditor.Row row : editor.rows()) {
            if (row.label.equals(label)) {
                return row;
            }
        }
        throw new AssertionError("no row " + label + " in " + labels(editor));
    }

    private static List<String> labels(MotionLabEditor editor) {
        List<String> labels = new ArrayList<String>();
        for (MotionLabEditor.Row row : editor.rows()) {
            labels.add(row.label);
        }
        return labels;
    }

    @Test
    public void aNumberStepsByItsAmountAndTenTimesWhenFast() {
        assertEquals(210.0F, MotionLabEditor.stepped(200.0F, 10.0F, 1, false,
                0.0F, 5000.0F), EXACT);
        assertEquals(100.0F, MotionLabEditor.stepped(200.0F, 10.0F, -1, true,
                0.0F, 5000.0F), EXACT);
        assertEquals("a value off the grid lands on it", 0.3F,
                MotionLabEditor.stepped(0.26F, 0.05F, 1, false, 0.0F, 1.0F),
                EXACT);
    }

    @Test
    public void aNumberNeverLeavesItsBounds() {
        assertEquals(0.0F, MotionLabEditor.stepped(40.0F, 10.0F, -1, true,
                0.0F, 5000.0F), EXACT);
        assertEquals(5000.0F, MotionLabEditor.stepped(4990.0F, 10.0F, 1, true,
                0.0F, 5000.0F), EXACT);
        assertEquals(1.0F, MotionLabEditor.stepped(1.0F, 0.02F, 1, false,
                0.0F, 1.0F), EXACT);
    }

    @Test
    public void aChoiceStepsRoundFromTheLastToTheFirst() {
        assertEquals(0, MotionLabEditor.cycled(2, 1, 3));
        assertEquals(2, MotionLabEditor.cycled(0, -1, 3));
        assertEquals(1, MotionLabEditor.cycled(0, 1, 3));
        assertEquals(0, MotionLabEditor.cycled(4, 1, 0));
    }

    @Test
    public void aValueShowsWholeWhereItIsElseToThreePlaces() {
        assertEquals("200", MotionLabEditor.shown(200.0F));
        assertEquals("0.3", MotionLabEditor.shown(0.3F));
        assertEquals("0.333", MotionLabEditor.shown(1.0F / 3.0F));
    }

    @Test
    public void aTransitionsDurationStepsAndStaysWithinBounds() {
        MotionLabEditor editor = editor("test.fade",
                "{\"duration\": 200, \"curve\": \"ease_out\"}");
        assertEquals(labels(editor).toString(), 4, editor.rows().size());
        MotionLabEditor.Row duration = row(editor, "row.duration");
        duration.step(1, true);
        assertEquals("300", duration.value());
        assertEquals(1, this.changes);
        for (int step = 0; step < 10; step++) {
            duration.step(-1, true);
        }
        assertEquals("0", duration.value());
        for (int step = 0; step < 60; step++) {
            duration.step(1, true);
        }
        assertEquals(String.valueOf(MotionBeat.MAX_MILLIS), duration.value());
    }

    @Test
    public void theWayBackIsTheWayOnUntilItIsTunedOnItsOwn() {
        MotionLabEditor editor = editor("test.fade",
                "{\"duration\": 200, \"curve\": \"ease_out\"}");
        MotionLabEditor.Row back = row(editor, "row.off_duration");
        assertEquals("200", back.value());
        assertFalse(written(editor, "test.fade").has("off"));
        back.step(-1, false);
        assertEquals("190", back.value());
        assertEquals("200", row(editor, "row.duration").value());
        JsonObject off = written(editor, "test.fade").getAsJsonObject("off");
        assertNotNull(off);
        assertEquals("ease_out", off.get("curve").getAsString());
    }

    @Test
    public void aCurveStepsThroughTheNamedCurves() {
        MotionLabEditor editor = editor("test.fade",
                "{\"duration\": 200, \"curve\": \"ease_out\"}");
        MotionLabEditor.Row curve = row(editor, "row.curve");
        List<MotionCurve> named = MotionCurve.named();
        int at = named.indexOf(MotionCurve.EASE_OUT);
        curve.step(1, false);
        assertEquals(named.get((at + 1) % named.size()).name(), curve.value());
        curve.step(-1, false);
        assertEquals("ease_out", curve.value());
        assertEquals(2, this.changes);
    }

    @Test
    public void pickingAPartOrABeatBuildsTheRowsWithoutChangingTheMotion() {
        MotionLabEditor editor = editor("test.parts", "{\"parts\": {"
                + "\"first\": {\"poses\": {\"up\": {\"y\": -2}},"
                + " \"beats\": {\"on\": {\"to\": \"up\", \"duration\": 100},"
                + " \"off\": {\"duration\": 50}}},"
                + "\"second\": {\"beats\": {\"on\": {\"duration\": 70}}}}}");
        assertEquals("first", row(editor, "row.part").value());
        assertEquals("on", row(editor, "row.beat").value());
        assertEquals("up", row(editor, "row.to").value());
        assertEquals("100", row(editor, "row.duration").value());
        assertTrue(labels(editor).contains("up / y"));
        row(editor, "row.beat").step(1, false);
        assertEquals("off", row(editor, "row.beat").value());
        assertEquals("row.to_code", row(editor, "row.to").value());
        assertEquals("50", row(editor, "row.duration").value());
        row(editor, "row.part").step(1, false);
        assertEquals("second", row(editor, "row.part").value());
        assertEquals("70", row(editor, "row.duration").value());
        assertEquals(0, this.changes);
    }

    @Test
    public void whereABeatGoesIsAChoiceThatChangesTheMotion() {
        MotionLabEditor editor = editor("test.parts", "{\"parts\": {"
                + "\"first\": {\"poses\": {\"up\": {\"y\": -2}},"
                + " \"beats\": {\"on\": {\"to\": \"up\", \"duration\": 100}}}}}");
        row(editor, "row.to").step(-1, false);
        assertEquals(1, this.changes);
        assertEquals("row.to_code", row(editor, "row.to").value());
        assertFalse(written(editor, "test.parts").getAsJsonObject("parts")
                .getAsJsonObject("first").getAsJsonObject("beats")
                .getAsJsonObject("on").has("to"));
    }

    @Test
    public void theEditReadsBackAsAFileWould() {
        MotionLabEditor editor = editor("test.fade",
                "{\"about\": \"A fade.\", \"duration\": 200,"
                        + " \"curve\": \"ease_out\"}");
        row(editor, "row.duration").step(1, false);
        MotionCodec.Result result = editor.read();
        assertTrue(result.problems().toString(), result.problems().isEmpty());
        Motion motion = result.motions().get("test.fade");
        assertNotNull(motion);
        assertEquals("A fade.", motion.about());
        assertEquals(210, motion.beat(Motion.ON).durationMillis());
    }

    @Test
    public void theSameMotionReadAgainKeepsItsPartAndBeat() {
        String json = "{\"parts\": {\"first\": {\"beats\": {\"on\":"
                + " {\"duration\": 100}, \"off\": {\"duration\": 50}}}}}";
        MotionLabEditor editor = editor("test.parts", json);
        row(editor, "row.beat").step(1, false);
        editor.edit("test.parts", new JsonParser().parse(json)
                .getAsJsonObject());
        assertEquals("off", row(editor, "row.beat").value());
        editor.edit("test.other", new JsonParser().parse(json)
                .getAsJsonObject());
        assertEquals("on", row(editor, "row.beat").value());
    }

    /** The edited motion as the lab's Copy writes it. */
    private static JsonObject written(MotionLabEditor editor, String id) {
        return MotionCodec.encode(editor.read().motions().get(id));
    }
}
