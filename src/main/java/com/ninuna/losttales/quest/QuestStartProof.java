package com.ninuna.losttales.quest;

import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;

/**
 * The way a quest start comes and what it brings to prove it, for
 * {@link QuestStartGate}. Nothing in it is taken on trust: the gate looks
 * at the world again for each way.
 */
public final class QuestStartProof {

    /** The ways a quest starts. */
    public enum Way {
        /** An operator's command; it needs no proof. */
        COMMAND,
        /** Using an item that names the quest: the stack in the player's hand. */
        ITEM,
        /** Touching the quest's giver: the creature or the block touched. */
        TOUCH,
        /** Taking an offer in conversation: the giver within reach and in sight. */
        CONVERSATION,
        /** Joining from a quest card: its author, still running the quest. */
        CARD,
        /** Accepting a missive: the letter, carrying the world's seal. */
        LETTER
    }

    private final Way way;
    private final ItemStack stack;
    private final Entity creature;
    private final boolean block;
    private final int x;
    private final int y;
    private final int z;
    private final UUID author;

    private QuestStartProof(Way way, ItemStack stack, Entity creature,
                            boolean block, int x, int y, int z, UUID author) {
        this.way = way;
        this.stack = stack;
        this.creature = creature;
        this.block = block;
        this.x = x;
        this.y = y;
        this.z = z;
        this.author = author;
    }

    public static QuestStartProof command() {
        return new QuestStartProof(Way.COMMAND, null, null, false, 0, 0, 0, null);
    }

    /** The stack the player used, which has to be the one in their hand. */
    public static QuestStartProof item(ItemStack held) {
        return new QuestStartProof(Way.ITEM, held, null, false, 0, 0, 0, null);
    }

    /** A creature the player touched. */
    public static QuestStartProof touch(Entity creature) {
        return new QuestStartProof(Way.TOUCH, null, creature, false, 0, 0, 0, null);
    }

    /** A block the player touched, by where it stands; the gate reads the block there itself. */
    public static QuestStartProof touch(int x, int y, int z) {
        return new QuestStartProof(Way.TOUCH, null, null, true, x, y, z, null);
    }

    /** An offer taken in conversation; the gate finds the giver itself. */
    public static QuestStartProof conversation() {
        return new QuestStartProof(Way.CONVERSATION, null, null, false, 0, 0, 0, null);
    }

    /** A card whose author's account is {@code authorId}. */
    public static QuestStartProof card(UUID authorId) {
        return new QuestStartProof(Way.CARD, null, null, false, 0, 0, 0, authorId);
    }

    /** A missive letter, on a board or in the player's inventory. */
    public static QuestStartProof letter(ItemStack letter) {
        return new QuestStartProof(Way.LETTER, letter, null, false, 0, 0, 0, null);
    }

    public Way getWay() {
        return this.way;
    }

    /** The item used or the letter; null for the other ways. */
    public ItemStack getStack() {
        return this.stack;
    }

    /** The creature touched; null for the other ways. */
    public Entity getCreature() {
        return this.creature;
    }

    /** Whether a block was touched, at {@link #getX()}, {@link #getY()}, {@link #getZ()}. */
    public boolean isBlock() {
        return this.block;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    /** The card author's account; null for the other ways. */
    public UUID getAuthor() {
        return this.author;
    }
}
