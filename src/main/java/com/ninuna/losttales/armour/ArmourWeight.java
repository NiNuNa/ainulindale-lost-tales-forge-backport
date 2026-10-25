package com.ninuna.losttales.armour;

import java.util.Locale;

/**
 * What an armour piece weighs (CONTENT-06): light, heavy, or neither. A
 * weight file names each material and item by its lower-case word; four
 * pieces of one weight are a weight set (PROG-18), and the armour skills
 * count and grow by it (PROG-17). A piece of neither weight counts for
 * no skill and no weight set.
 */
public enum ArmourWeight {
    LIGHT,
    HEAVY,
    NEITHER;

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Whether pieces of this weight make a weight set and train a skill. */
    public boolean counts() {
        return this != NEITHER;
    }

    /** The weight a file's word names; null for a word no weight has. */
    public static ArmourWeight fromWord(String word) {
        if (word == null) {
            return null;
        }
        for (ArmourWeight weight : values()) {
            if (weight.word().equals(word)) {
                return weight;
            }
        }
        return null;
    }
}
