package com.ninuna.losttales.client.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Every curve starts at nothing and ends on its mark; the ones that swing say so. */
public final class MotionCurveTest {
    @Test
    public void everyNamedCurveRunsFromNothingToItsEnd() {
        for (MotionCurve curve : MotionCurve.named()) {
            assertEquals(curve.name() + " starts at nothing", 0.0F,
                    curve.apply(0.0F), 1.0E-5F);
            assertEquals(curve.name() + " ends on its mark", 1.0F,
                    curve.apply(1.0F), 1.0E-5F);
            assertEquals(curve.name() + " holds before its start", 0.0F,
                    curve.apply(-3.0F), 1.0E-5F);
            assertEquals(curve.name() + " holds past its end", 1.0F,
                    curve.apply(7.0F), 1.0E-5F);
        }
    }

    @Test
    public void everyNamedCurveReadsBackByItsName() {
        for (MotionCurve curve : MotionCurve.named()) {
            assertEquals(curve, MotionCurve.parse(curve.name()));
            assertEquals(curve, MotionCurve.parse(" "
                    + curve.name().toUpperCase(java.util.Locale.ROOT) + " "));
        }
        assertNull(MotionCurve.parse("wobbly"));
        assertNull(MotionCurve.parse(null));
    }

    @Test
    public void onlyTheSwingingCurvesOvershoot() {
        assertTrue(MotionCurve.BACK_OUT.overshoots());
        assertTrue(MotionCurve.SPRING.overshoots());
        assertTrue(MotionCurve.BOUNCE.overshoots());
        assertFalse(MotionCurve.EASE_OUT.overshoots());
        assertFalse(MotionCurve.SETTLE.overshoots());
        assertFalse(MotionCurve.LINEAR.overshoots());
        float highest = 0.0F;
        for (int step = 0; step <= 100; step++) {
            highest = Math.max(highest, MotionCurve.SPRING.apply(step / 100.0F));
        }
        assertTrue("a spring passes its end", highest > 1.05F);
    }

    @Test
    public void reducedMotionTradesASwingForEaseOut() {
        assertEquals(MotionCurve.EASE_OUT, MotionCurve.BACK_OUT.reduced());
        assertEquals(MotionCurve.EASE_OUT, MotionCurve.BOUNCE.reduced());
        assertEquals(MotionCurve.SETTLE, MotionCurve.SETTLE.reduced());
    }

    @Test
    public void aHandMadeCurveReadsAsCssWritesIt() {
        MotionCurve curve = MotionCurve.parse("cubic-bezier(0.25, 0.1, 0.25, 1)");
        assertEquals("cubic-bezier(0.25, 0.1, 0.25, 1)", curve.name());
        assertEquals(curve, MotionCurve.parse(curve.name()));
        assertFalse(curve.overshoots());
        assertEquals(0.0F, curve.apply(0.0F), 1.0E-4F);
        assertEquals(1.0F, curve.apply(1.0F), 1.0E-4F);
        float previous = 0.0F;
        for (int step = 1; step <= 100; step++) {
            float value = curve.apply(step / 100.0F);
            assertTrue("rises at " + step, value >= previous - 1.0E-4F);
            previous = value;
        }
        MotionCurve swinging = MotionCurve.parse("cubic-bezier(0.3, 1.6, 0.6, 1)");
        assertTrue(swinging.overshoots());
        // A straight line through its own corners is the straight curve.
        MotionCurve straight = MotionCurve.bezier(0.0F, 0.0F, 1.0F, 1.0F);
        assertEquals(0.3F, straight.apply(0.3F), 1.0E-3F);
    }

    @Test
    public void aMalformedHandMadeCurveIsNone() {
        assertNull(MotionCurve.parse("cubic-bezier(0.1, 0.2, 0.3)"));
        assertNull(MotionCurve.parse("cubic-bezier(a, b, c, d)"));
        assertNull(MotionCurve.parse("cubic-bezier(0.1, NaN, 0.3, 1)"));
        // Times outside 0..1 are kept inside it.
        assertEquals("cubic-bezier(1, 0, 0, 1)",
                MotionCurve.parse("cubic-bezier(4, 0, -2, 1)").name());
    }
}
