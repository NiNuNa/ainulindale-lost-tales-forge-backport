package com.ninuna.losttales.quest.player;

import com.ninuna.losttales.quest.LostTalesQuestIds;

/**
 * One reward waiting under its quest in the journal (QUEST-44): an item by
 * the id the game registers it under and its meta, with the relic mark it
 * is handed out with if any (CONTENT-11), or a quest's sealed starter
 * (QUEST-43), which is made only as it is collected. Plain data;
 * {@link WaitingRewards} holds them for a character.
 */
public final class WaitingReward {
    /** The highest meta an item stack keeps. */
    public static final int MAX_META = Short.MAX_VALUE;

    private final boolean starter;
    private final String id;
    private final int meta;
    private final int count;
    private final String relic;

    private WaitingReward(boolean starter, String id, int meta, int count, String relic) {
        this.starter = starter;
        this.id = id == null ? "" : id;
        this.meta = meta;
        this.count = count;
        this.relic = relic == null ? "" : relic;
    }

    /** {@code count} of the item registered as {@code itemId}, with its meta. */
    public static WaitingReward item(String itemId, int meta, int count) {
        return new WaitingReward(false, itemId, meta, count, "");
    }

    /** {@code count} of the item registered as {@code itemId}, each marked with the relic set {@code relicId}. */
    public static WaitingReward relic(String itemId, String relicId, int count) {
        return new WaitingReward(false, itemId, 0, count, relicId);
    }

    /** {@code count} sealed starters of the quest {@code questId}. */
    public static WaitingReward starter(String questId, int count) {
        return new WaitingReward(true, questId, 0, count, "");
    }

    /** The relic set the item is marked with; empty for none. */
    public String getRelic() {
        return this.relic;
    }

    /** Whether it is a quest's starter rather than an item. */
    public boolean isStarter() {
        return this.starter;
    }

    /** The item's registered id, or the starter's quest id. */
    public String getId() {
        return this.id;
    }

    /** The item's meta; 0 for a starter. */
    public int getMeta() {
        return this.meta;
    }

    public int getCount() {
        return this.count;
    }

    /** The same reward, {@code count} of it. */
    public WaitingReward withCount(int count) {
        return new WaitingReward(this.starter, this.id, this.meta, count, this.relic);
    }

    /** Whether the two are the same thing, whatever their counts: they wait as one. */
    public boolean isSameKind(WaitingReward other) {
        return other != null && other.starter == this.starter
                && other.meta == this.meta && other.id.equals(this.id)
                && other.relic.equals(this.relic);
    }

    /**
     * Whether it can wait: an id within the quest-id bound, a meta of 0
     * to {@link #MAX_META} (0 for a starter), a relic mark within that
     * bound too (none for a starter), and 1 to
     * {@link WaitingRewards#MAX_COUNT} of it.
     */
    public boolean isValid() {
        return LostTalesQuestIds.fits(this.id)
                && this.meta >= 0 && this.meta <= MAX_META
                && (!this.starter || this.meta == 0 && this.relic.length() == 0)
                && (this.relic.length() == 0 || LostTalesQuestIds.fits(this.relic))
                && this.count >= 1 && this.count <= WaitingRewards.MAX_COUNT;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof WaitingReward)) {
            return false;
        }
        WaitingReward that = (WaitingReward) other;
        return isSameKind(that) && that.count == this.count;
    }

    @Override
    public int hashCode() {
        return (((this.id.hashCode() * 31 + this.meta) * 31 + this.count) * 31
                + this.relic.hashCode()) * 2 + (this.starter ? 1 : 0);
    }

    @Override
    public String toString() {
        return (this.starter ? "starter:" : "") + this.id
                + (this.meta != 0 ? "@" + this.meta : "")
                + (this.relic.length() > 0 ? "#" + this.relic : "") + "*" + this.count;
    }
}
