package com.ninuna.losttales.character.model;

/**
 * Derived cumulative slot state.
 *
 * VISIBLE represents a visible but still locked slot. The initial progression
 * rule does not currently produce that state, but retaining it keeps the data
 * model compatible with a later separate visibility progression rule.
 */
public enum CharacterSlotState {
    HIDDEN(false, false, false),
    VISIBLE(true, false, false),
    UNLOCKED(true, true, false),
    OCCUPIED(true, true, true);

    private final boolean visible;
    private final boolean unlocked;
    private final boolean occupied;

    CharacterSlotState(boolean visible, boolean unlocked, boolean occupied) {
        this.visible = visible;
        this.unlocked = unlocked;
        this.occupied = occupied;
    }

    public boolean isHidden() {
        return !this.visible;
    }

    public boolean isVisible() {
        return this.visible;
    }

    public boolean isUnlocked() {
        return this.unlocked;
    }

    public boolean isOccupied() {
        return this.occupied;
    }
}
