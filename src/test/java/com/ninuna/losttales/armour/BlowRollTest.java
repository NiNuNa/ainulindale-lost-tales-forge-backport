package com.ninuna.losttales.armour;

import java.util.Random;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Deft Movement and Elusive roll once for each blow, though Forge asks twice. */
public final class BlowRollTest {

    @Test
    public void theSameBlowAskedTwiceRollsOnce() {
        CountingRandom random = new CountingRandom(0.05D);
        BlowRoll roll = new BlowRoll();
        Object player = new Object();
        Object blow = new Object();
        assertTrue(roll.misses(player, blow, 0.1D, random));
        assertTrue(roll.misses(player, blow, 0.1D, random));
        assertEquals(1, random.rolls);
    }

    @Test
    public void aNewBlowRollsAgain() {
        CountingRandom random = new CountingRandom(0.5D);
        BlowRoll roll = new BlowRoll();
        Object player = new Object();
        assertFalse(roll.misses(player, new Object(), 0.1D, random));
        assertFalse(roll.misses(player, new Object(), 0.1D, random));
        assertFalse(roll.misses(new Object(), new Object(), 0.1D, random));
        assertEquals(3, random.rolls);
    }

    @Test
    public void noChanceNeverMisses() {
        CountingRandom random = new CountingRandom(0.0D);
        BlowRoll roll = new BlowRoll();
        assertFalse(roll.misses(new Object(), new Object(), 0.0D, random));
        assertEquals(0, random.rolls);
    }

    @Test
    public void clearingForgetsTheLastBlow() {
        CountingRandom random = new CountingRandom(0.05D);
        BlowRoll roll = new BlowRoll();
        Object player = new Object();
        Object blow = new Object();
        roll.misses(player, blow, 0.1D, random);
        roll.clear();
        roll.misses(player, blow, 0.1D, random);
        assertEquals(2, random.rolls);
    }

    private static final class CountingRandom extends Random {
        private final double value;
        int rolls;

        CountingRandom(double value) {
            this.value = value;
        }

        @Override
        public double nextDouble() {
            this.rolls++;
            return this.value;
        }
    }
}
