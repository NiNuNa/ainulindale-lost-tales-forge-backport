package com.ninuna.losttales.armour;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The fixed list of effects a named set's bonus may hold (CONTENT-07),
 * each named in its file by its lower-case word, with its number in the
 * file: a share for all but {@link #KNOCKBACK}, whose number is a chance.
 */
public enum SetEffect {
    /** Lets a share less of a blow through, after the armour (PROG-19). */
    PROTECTION,
    /** A share more speed. */
    SPEED,
    /** A chance that a blow does not push you. */
    KNOCKBACK,
    /** Fire hurts a share less. */
    FIRE,
    /** Falls hurt a share less. */
    FALLS,
    /** Arrows, bolts and thrown weapons hurt a share less. */
    ARROWS,
    /** Poison wears off a share sooner. */
    POISON,
    /** A named skill gains a share more experience. */
    EXPERIENCE;

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The effect a file's word names; null for a word no effect has. */
    public static SetEffect fromWord(String word) {
        if (word == null) {
            return null;
        }
        for (SetEffect effect : values()) {
            if (effect.word().equals(word)) {
                return effect;
            }
        }
        return null;
    }

    /** Every effect's word, for a warning that names them. */
    public static List<String> words() {
        List<String> words = new ArrayList<String>();
        for (SetEffect effect : values()) {
            words.add(effect.word());
        }
        return words;
    }
}
