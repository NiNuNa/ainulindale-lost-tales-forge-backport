package com.ninuna.losttales.fellowship.model;

/**
 * The item a fellowship wears as its icon: its registry name and damage
 * value, nothing more, so an item's own data never travels with it. The
 * server takes it from the item the leader or a guide holds.
 */
public final class FellowshipIcon {
    /** The longest registry name an icon keeps, in characters. */
    public static final int MAX_ITEM_NAME_LENGTH = 128;
    /** The highest damage value an icon keeps. */
    public static final int MAX_DAMAGE = Short.MAX_VALUE;

    private final String itemName;
    private final int damage;

    public FellowshipIcon(String itemName, int damage) {
        if (!isWellFormed(itemName, damage)) {
            throw new IllegalArgumentException("fellowship icon is not well formed");
        }
        this.itemName = itemName;
        this.damage = damage;
    }

    /**
     * Whether a registry name and damage value can be an icon: a name of
     * printable ASCII without spaces, as registry names are, within its
     * bound, and a damage value from 0.
     */
    public static boolean isWellFormed(String itemName, int damage) {
        if (itemName == null || itemName.length() == 0
                || itemName.length() > MAX_ITEM_NAME_LENGTH
                || damage < 0 || damage > MAX_DAMAGE) {
            return false;
        }
        for (int index = 0; index < itemName.length(); index++) {
            char letter = itemName.charAt(index);
            if (letter <= ' ' || letter > '~') {
                return false;
            }
        }
        return true;
    }

    public String getItemName() {
        return this.itemName;
    }

    public int getDamage() {
        return this.damage;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof FellowshipIcon)) {
            return false;
        }
        FellowshipIcon icon = (FellowshipIcon)other;
        return this.damage == icon.damage && this.itemName.equals(icon.itemName);
    }

    @Override
    public int hashCode() {
        return this.itemName.hashCode() * 31 + this.damage;
    }
}
