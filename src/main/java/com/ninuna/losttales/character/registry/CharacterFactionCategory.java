package com.ninuna.losttales.character.registry;

/**
 * LOTR-independent faction categories used by character validation.
 * The compatibility layer maps dependency-specific faction types to these
 * values so the core character package does not depend on LOTR classes.
 */
public enum CharacterFactionCategory {
    FREE,
    HUMAN,
    ELF,
    DWARF,
    ORC,
    TROLL,
    TREE
}
