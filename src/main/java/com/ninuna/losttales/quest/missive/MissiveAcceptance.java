package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.network.packet.LostTalesMissiveCodec;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestManager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * How the server reads a missive letter a page names and starts its
 * quest, from a board or from the player's own inventory alike. Every
 * check is made against the stack standing in the slot now.
 */
public final class MissiveAcceptance {
    private MissiveAcceptance() {}

    /** Whether the stack is a missive letter. */
    public static boolean isLetter(ItemStack stack) {
        return stack != null && stack.stackSize > 0
                && stack.getItem() == ELostTalesItem.MISSIVE_LETTER.getItem();
    }

    /**
     * The letter's quest id as its page reads it: empty for a letter that
     * cannot be read or is too large to be shown, null for no letter at
     * all. A page names a letter by this, and the server checks the slot
     * by it.
     */
    public static String pageQuestId(ItemStack stack) {
        if (!isLetter(stack)) {
            return null;
        }
        LostTalesMissiveData missive = LostTalesMissiveNbt.readFromItemStack(stack);
        return missive != null && LostTalesMissiveCodec.fits(missive)
                ? missive.getQuestId() : "";
    }

    /**
     * Why the letter in a slot cannot be accepted as the page named it:
     * no letter, a letter that cannot be read or does not carry this
     * world's seal, another letter than the one read. Null where it can.
     */
    public static MissiveBoardStateReason check(World world, ItemStack stack,
                                                String expectedQuestId) {
        if (!isLetter(stack)) {
            return MissiveBoardStateReason.GONE;
        }
        LostTalesMissiveData missive = LostTalesMissiveNbt.readFromItemStack(stack);
        if (missive == null || !missive.isValid()
                || !MissiveSeals.isGenuine(world, stack)) {
            return MissiveBoardStateReason.DAMAGED;
        }
        String expected = expectedQuestId == null ? "" : expectedQuestId.trim();
        return expected.length() == 0 || !expected.equals(missive.getQuestId())
                ? MissiveBoardStateReason.NOTICE_CHANGED : null;
    }

    /**
     * Starts the letter's quest for the player: {@link
     * MissiveBoardStateReason#ACCEPTED} once it runs, else why not. The
     * quest's own refusal line is said by the quest system first.
     */
    public static MissiveBoardStateReason start(EntityPlayerMP player,
                                                LostTalesMissiveData missive) {
        LostTalesQuestDefinition quest =
                LostTalesMissiveQuestFactory.createQuestDefinition(missive);
        if (quest == null) {
            return MissiveBoardStateReason.REFUSED;
        }
        LostTalesQuestManager.StartResult result =
                LostTalesQuestManager.startGeneratedQuest(player, quest,
                        missive.getTimeLimitTicks());
        if (result == LostTalesQuestManager.StartResult.STARTED) {
            return MissiveBoardStateReason.ACCEPTED;
        }
        if (result == LostTalesQuestManager.StartResult.ALREADY_ACTIVE) {
            return MissiveBoardStateReason.ALREADY_ACTIVE;
        }
        if (result == LostTalesQuestManager.StartResult.ALREADY_COMPLETED) {
            return MissiveBoardStateReason.ALREADY_COMPLETED;
        }
        if (result == LostTalesQuestManager.StartResult.REQUIREMENTS_NOT_MET) {
            return MissiveBoardStateReason.REQUIREMENTS;
        }
        return MissiveBoardStateReason.NOT_NOW;
    }
}
