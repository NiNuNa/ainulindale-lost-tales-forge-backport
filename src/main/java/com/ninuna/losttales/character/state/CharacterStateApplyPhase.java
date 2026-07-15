package com.ninuna.losttales.character.state;

/** Ordering boundary around race and other maximum-health attributes. */
public enum CharacterStateApplyPhase {
    BEFORE_ATTRIBUTES,
    AFTER_ATTRIBUTES,
    COORDINATOR_ONLY
}
