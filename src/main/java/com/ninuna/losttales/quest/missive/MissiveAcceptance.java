package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.network.packet.LostTalesMissiveCodec;
import com.ninuna.losttales.quest.QuestStartGate;
import com.ninuna.losttales.quest.QuestStartProof;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/**
 * How the server reads a missive letter a page names and starts its
 * quest, from a board or from the player's own inventory alike. Every
 * check is made against the stack standing in the slot now; the letter's
 * seal is checked where its quest starts ({@link QuestStartGate}).
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
     * no letter, a letter that cannot be read, another letter than the
     * one read. Null where it can be taken to the start gate.
     */
    public static MissiveBoardStateReason check(ItemStack stack,
                                                String expectedQuestId) {
        if (!isLetter(stack)) {
            return MissiveBoardStateReason.GONE;
        }
        LostTalesMissiveData missive = LostTalesMissiveNbt.readFromItemStack(stack);
        if (missive == null || !missive.isValid()) {
            return MissiveBoardStateReason.DAMAGED;
        }
        String expected = expectedQuestId == null ? "" : expectedQuestId.trim();
        return expected.length() == 0 || !expected.equals(missive.getQuestId())
                ? MissiveBoardStateReason.NOTICE_CHANGED : null;
    }

    /**
     * Starts the quest of a letter {@link #check} passed, through the
     * start gate, which checks its seal, once its notice's time is not
     * past and nobody took it before (QUEST-34): {@link
     * MissiveBoardStateReason#ACCEPTED} once it runs, else why not. The
     * gate says its own refusal line first.
     */
    public static MissiveBoardStateReason start(EntityPlayerMP player,
                                                ItemStack letter) {
        LostTalesMissiveData missive = LostTalesMissiveNbt.readFromItemStack(letter);
        if (missive == null) {
            return MissiveBoardStateReason.DAMAGED;
        }
        MissiveBoardStateReason refusal = MissivesTaken.refusal(player.worldObj, missive);
        if (refusal != null) {
            return refusal;
        }
        QuestStartGate.StartResult result = QuestStartGate.start(player,
                missive.getQuestId(), QuestStartProof.letter(letter));
        switch (result) {
            case STARTED:
                return MissiveBoardStateReason.ACCEPTED;
            case UNPROVEN:
                return MissiveBoardStateReason.DAMAGED;
            case UNKNOWN_QUEST:
                return MissiveBoardStateReason.REFUSED;
            case ALREADY_ACTIVE:
                return MissiveBoardStateReason.ALREADY_ACTIVE;
            case ALREADY_COMPLETED:
                return MissiveBoardStateReason.ALREADY_COMPLETED;
            case REQUIREMENTS_NOT_MET:
                return MissiveBoardStateReason.REQUIREMENTS;
            default:
                return MissiveBoardStateReason.NOT_NOW;
        }
    }
}
