package com.ninuna.losttales.character.kit;

/**
 * One entry of a starting kit (CHAR-40): an item by its registry id, how
 * many of it, and its damage. A kit carries no NBT.
 */
public final class KitItem {
    private final String itemId;
    private final int count;
    private final int damage;

    public KitItem(String itemId, int count, int damage) {
        this.itemId = itemId == null ? "" : itemId;
        this.count = count;
        this.damage = damage;
    }

    public String getItemId() {
        return this.itemId;
    }

    public int getCount() {
        return this.count;
    }

    public int getDamage() {
        return this.damage;
    }

    /** Whether the two are the same item at the same damage, so one stack holds both. */
    public boolean stacksWith(KitItem other) {
        return other != null && this.damage == other.damage
                && this.itemId.equals(other.itemId);
    }

    /** This item and damage, {@code amount} of it. */
    public KitItem withCount(int amount) {
        return new KitItem(this.itemId, amount, this.damage);
    }

    @Override
    public String toString() {
        return this.itemId + "@" + this.damage + "*" + this.count;
    }
}
