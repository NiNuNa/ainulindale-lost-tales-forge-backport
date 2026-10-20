package com.ninuna.losttales.character.registry;

/**
 * LOTR-independent faction categories used by character validation: the
 * peoples a race may start among. The compatibility layer maps LOTR's
 * faction types to these values so the core character package does not
 * depend on LOTR classes.
 */
public enum CharacterFactionCategory {
    HUMAN,
    ELF,
    DWARF,
    ORC
}
