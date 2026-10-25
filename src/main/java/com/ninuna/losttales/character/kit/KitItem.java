package com.ninuna.losttales.character.kit;

/**
 * One entry of a starting kit (CHAR-40): an item by its registry id, how
 * many of it, its damage, and the relic set it is marked with if any
 * (CONTENT-11). A kit carries no other NBT.
 */
public final class KitItem {
    private final String itemId;
    private final int count;
    private final int damage;
    private final String relic;

    public KitItem(String itemId, int count, int damage) {
        this(itemId, count, damage, "");
    }

    public KitItem(String itemId, int count, int damage, String relic) {
        this.itemId = itemId == null ? "" : itemId;
        this.count = count;
        this.damage = damage;
        this.relic = relic == null ? "" : relic;
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

    /** The relic set the item is marked with; empty for none. */
    public String getRelic() {
        return this.relic;
    }

    /** Whether the two are the same item at the same damage and mark, so one stack holds both. */
    public boolean stacksWith(KitItem other) {
        return other != null && this.damage == other.damage
                && this.itemId.equals(other.itemId) && this.relic.equals(other.relic);
    }

    /** This item, damage and mark, {@code amount} of it. */
    public KitItem withCount(int amount) {
        return new KitItem(this.itemId, amount, this.damage, this.relic);
    }

    @Override
    public String toString() {
        return this.itemId + "@" + this.damage + (this.relic.length() > 0 ? "#" + this.relic : "")
                + "*" + this.count;
    }
}
