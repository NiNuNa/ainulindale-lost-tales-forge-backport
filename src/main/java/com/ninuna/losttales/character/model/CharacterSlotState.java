package com.ninuna.losttales.character.model;

/**
 * A roster slot's state: hidden past the unlocked slots, else unlocked,
 * or occupied while a character holds it.
 */
public enum CharacterSlotState {
    HIDDEN,
    UNLOCKED,
    OCCUPIED
}
