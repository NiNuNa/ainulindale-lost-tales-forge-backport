package com.ninuna.losttales.client.window;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A number in Settings steps to the next multiple of its step, ten with
 * Shift, and stops at its bounds; a typed number is taken only within
 * them and in the places it is shown to; Default puts back the shipped
 * value. Its row's chevrons and value answer
 * where they are drawn.
 */
public final class NumberStepperTest {
    private static final double EXACT = 1.0E-9D;

    @Test
    public void aStepLandsOnTheNextMultipleOfTheStep() {
        NumberStepper share = new NumberStepper(0.0D, 1.0D, 0.05D, 2);
        assertEquals(0.70D, share.stepped(0.65D, true, false), EXACT);
        assertEquals(0.60D, share.stepped(0.65D, false, false), EXACT);
        assertEquals("a value between two steps goes up to the next",
                0.80D, share.stepped(0.78D, true, false), EXACT);
        assertEquals("and down to the one below", 0.75D,
                share.stepped(0.78D, false, false), EXACT);
    }

    @Test
    public void shiftTakesTenSteps() {
        NumberStepper speed = new NumberStepper(0.25D, 4.0D, 0.05D, 2);
        assertEquals(1.50D, speed.stepped(1.0D, true, true), EXACT);
        assertEquals(0.50D, speed.stepped(1.0D, false, true), EXACT);
        NumberStepper rows = new NumberStepper(1.0D, 12.0D, 1.0D, 0);
        assertEquals(11.0D, rows.stepped(1.0D, true, true), EXACT);
    }

    @Test
    public void aStepStopsAtTheBounds() {
        NumberStepper radius = new NumberStepper(45.0D, 225.0D, 5.0D, 0);
        assertEquals(225.0D, radius.stepped(220.0D, true, true), EXACT);
        assertEquals(45.0D, radius.stepped(50.0D, false, true), EXACT);
        assertFalse(radius.canStep(225.0D, true));
        assertTrue(radius.canStep(225.0D, false));
        assertFalse(radius.canStep(45.0D, false));
        assertEquals("a value past a bound is read at the bound", 225.0D,
                radius.clamp(400.0D), EXACT);
    }

    @Test
    public void aValueReadsToThePlacesItIsShownTo() {
        assertEquals("0.65", new NumberStepper(0.0D, 1.0D, 0.05D, 2)
                .format(0.65D));
        assertEquals("1000", new NumberStepper(100.0D, 5000.0D, 50.0D, 0)
                .format(1000.0D));
        assertEquals("37.8", new NumberStepper(0.0D, 100.0D, 1.0D, 1)
                .format(37.84D));
        assertEquals("-2.25", new NumberStepper(-2.25D, 9.2D, 0.05D, 2)
                .format(-2.25D));
    }

    @Test
    public void aTypedNumberIsTakenOnlyWithinTheBounds() {
        NumberStepper history = new NumberStepper(100.0D, 5000.0D, 50.0D, 0);
        assertEquals(Double.valueOf(1500.0D), history.parse("1500"));
        assertEquals(Double.valueOf(100.0D), history.parse(" 100 "));
        assertNull("under the lower bound", history.parse("99"));
        assertNull("past the upper bound", history.parse("5001"));
        assertNull("no number at all", history.parse(""));
        assertNull(history.parse("-"));
        assertNull("words", history.parse("many"));
        assertNull("a fraction where only whole numbers are",
                history.parse("150.5"));

        NumberStepper share = new NumberStepper(0.0D, 1.0D, 0.05D, 2);
        assertEquals(Double.valueOf(0.5D), share.parse(".5"));
        assertEquals(Double.valueOf(1.0D), share.parse("1."));
        assertEquals(Double.valueOf(0.33D), share.parse("0.33"));
        assertNull("more places than are shown", share.parse("0.333"));
        assertNull("a minus where the bounds do not go below nought",
                share.parse("-0.5"));

        NumberStepper zoom = new NumberStepper(-2.25D, 9.2D, 0.05D, 2);
        assertEquals(Double.valueOf(-1.5D), zoom.parse("-1.5"));
        assertNull(zoom.parse("-3"));
    }

    @Test
    public void theFieldHoldsOnlyWhatANumberOfItsKindCan() {
        NumberStepper history = new NumberStepper(100.0D, 5000.0D, 50.0D, 0);
        assertTrue(history.mayBecome(""));
        assertTrue(history.mayBecome("12"));
        assertTrue("a number still being typed past a bound",
                history.mayBecome("99999"));
        assertFalse(history.mayBecome("1.5"));
        assertFalse(history.mayBecome("-1"));
        assertFalse(history.mayBecome("1e3"));
        assertFalse("past the longest number a field takes",
                history.mayBecome("1234567890123"));

        NumberStepper zoom = new NumberStepper(-2.25D, 9.2D, 0.05D, 2);
        assertTrue(zoom.mayBecome("-"));
        assertTrue(zoom.mayBecome("-0."));
        assertTrue(zoom.mayBecome("4.05"));
        assertFalse(zoom.mayBecome("4.055"));
        assertFalse(zoom.mayBecome("4..0"));
        assertFalse(zoom.mayBecome("4-"));
    }

    @Test
    public void defaultPutsBackTheShippedNumberWithinTheBounds() {
        HeldNumber number = new HeldNumber(0.35D);
        number.value = 0.9D;
        number.restore();
        assertEquals(0.35D, number.value, EXACT);

        HeldNumber past = new HeldNumber(7.0D);
        past.restore();
        assertEquals("a shipped value past a bound lands on it", 1.0D,
                past.value, EXACT);

        HeldNumber unknown = new HeldNumber(Double.NaN);
        unknown.value = 0.6D;
        unknown.restore();
        assertEquals("with no shipped value known it is left alone", 0.6D,
                unknown.value, EXACT);
    }

    @Test
    public void aNumberStepsTakesTypedValuesAndStopsAtItsBounds() {
        HeldNumber number = new HeldNumber(0.35D);
        number.value = 0.95D;
        assertTrue(number.move(true, false));
        assertEquals(1.0D, number.value, EXACT);
        assertFalse("at its bound it does not move", number.move(true, true));
        number.step(true);
        assertEquals("a right-click steps back", 0.95D, number.value, EXACT);
        assertTrue(number.take("0.2"));
        assertEquals(0.2D, number.value, EXACT);
        assertFalse(number.take("2"));
        assertEquals("a number past a bound is not taken", 0.2D,
                number.value, EXACT);
        assertEquals("0.20", number.value());
    }

    @Test
    public void aSteppersPartsAnswerWhereTheyAreDrawn() {
        // A stepper ending at 200 in a row from 10, 16 tall, its number 20
        // wide: the chevron up from 191, the number from 171, the chevron
        // down from 162.
        int cell = MenuWindow.STEPPER_CELL;
        assertEquals(MenuWindow.PART_MORE,
                MenuWindow.stepperPartAt(195.0D, 12.0D, 200, 10, 16, 20));
        assertEquals(MenuWindow.PART_MORE,
                MenuWindow.stepperPartAt(200 - cell, 25.9D, 200, 10, 16, 20));
        assertEquals(MenuWindow.PART_VALUE,
                MenuWindow.stepperPartAt(180.0D, 12.0D, 200, 10, 16, 20));
        assertEquals(MenuWindow.PART_LESS,
                MenuWindow.stepperPartAt(165.0D, 12.0D, 200, 10, 16, 20));
        assertNull("the label's side of the row is the row's",
                MenuWindow.stepperPartAt(200 - cell * 2 - 20 - 0.5D, 12.0D,
                        200, 10, 16, 20));
        assertNull("past the row's foot",
                MenuWindow.stepperPartAt(195.0D, 26.0D, 200, 10, 16, 20));
        assertNull("the pointer away",
                MenuWindow.stepperPartAt(WindowHover.AWAY, WindowHover.AWAY,
                        200, 10, 16, 20));
    }

    /** A number held here, a share of the whole, its shipped value given. */
    private static final class HeldNumber extends Settings.Numeric {
        double value;
        private final double shipped;

        HeldNumber(double shipped) {
            super("testNumber", "test.number", 0.05D, 2);
            this.shipped = shipped;
        }

        @Override
        protected double get() {
            return this.value;
        }

        @Override
        protected void set(double value) {
            this.value = value;
        }

        @Override
        protected double[] bounds() {
            return new double[] {0.0D, 1.0D};
        }

        @Override
        protected double shippedNumber() {
            return this.shipped;
        }
    }
}
