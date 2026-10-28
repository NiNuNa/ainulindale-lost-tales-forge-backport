package com.ninuna.losttales.progression;

/**
 * The hourly cap as the server sets it (PROG-08): the most one skill of
 * one character earns by its acts in an hour, 0 for none, and whether a
 * finished quest's experience and a first find go past it.
 */
public final class HourlyCap {
    private final int amount;
    private final boolean finishedQuestsPass;
    private final boolean firstFindsPass;

    public HourlyCap(int amount, boolean finishedQuestsPass, boolean firstFindsPass) {
        this.amount = Math.max(0, amount);
        this.finishedQuestsPass = finishedQuestsPass;
        this.firstFindsPass = firstFindsPass;
    }

    /** The most one skill earns by its acts in an hour; 0 for no cap. */
    public int amount() {
        return this.amount;
    }

    /** Whether what a finished quest earns by its size goes past the cap. */
    public boolean finishedQuestsPass() {
        return this.finishedQuestsPass;
    }

    /** Whether what a first find earns goes past the cap. */
    public boolean firstFindsPass() {
        return this.firstFindsPass;
    }
}
