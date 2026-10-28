package com.ninuna.losttales.character.state;

/**
 * When a part is put on the player: before the race's attributes, after
 * them (health needs its maximum first), or by the switch itself (the
 * location).
 */
public enum CharacterStateApplyPhase {
    BEFORE_ATTRIBUTES,
    AFTER_ATTRIBUTES,
    COORDINATOR_ONLY
}
