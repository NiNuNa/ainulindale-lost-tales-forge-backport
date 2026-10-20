package com.ninuna.losttales.progression.skill;

import java.util.Locale;

/**
 * The kinds of weapon and tool a skill file may name (PROG-01), each by
 * its lower-case word. The game tells them apart by vanilla's and LOTR's
 * item classes ({@code compat/lotr/LotrWeaponKinds}): LOTR has no class
 * of its own for maces, so a mace is a hammer, and a halberd or a
 * longspear is a pike.
 */
public enum WeaponKind {
    SWORD,
    DAGGER,
    BATTLEAXE,
    AXE,
    HAMMER,
    SPEAR,
    POLEARM,
    PIKE,
    LANCE,
    BOW,
    CROSSBOW,
    THROWING_AXE,
    SLING,
    BLOWGUN,
    PICKAXE,
    SHOVEL;

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The kind a file's word names; null for a word no kind has. */
    public static WeaponKind fromWord(String word) {
        if (word == null) {
            return null;
        }
        for (WeaponKind kind : values()) {
            if (kind.word().equals(word)) {
                return kind;
            }
        }
        return null;
    }

    /** Whether a file's word names a kind or an item ({@code namespace:path}). */
    public static boolean isNamed(String word) {
        return fromWord(word) != null
                || word != null && word.indexOf(':') > 0 && word.indexOf(':') < word.length() - 1;
    }
}
