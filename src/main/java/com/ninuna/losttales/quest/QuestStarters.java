package com.ninuna.losttales.quest;

import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.LostTalesItemQuestStarter;
import com.ninuna.losttales.quest.missive.MissiveSeal;
import com.ninuna.losttales.quest.missive.MissiveSeals;
import com.ninuna.losttales.quest.missive.MissiveWords;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.Constants;
import net.minecraft.world.World;

/**
 * A quest's starter: the mod's own item ({@link LostTalesItemQuestStarter},
 * QUEST-39) naming its quest, sealed by the world as a missive letter is
 * (QUEST-34). Only a sealed starter starts its quest, and it is used up as
 * the quest starts, so a starter made or changed by hand starts nothing and
 * one starter starts one quest.
 *
 * <p>A starter keeps no name. Each reader's game words it: a bundled
 * quest's title from the lang line its id names, a server quest file's
 * from the words its operator wrote, which the starter carries
 * ({@link #TAG_TITLE}).</p>
 */
public final class QuestStarters {
    /** Where a starter keeps its seal. */
    public static final String TAG_SEAL = "LostTalesQuestSeal";
    /** What makes each starter its own, sealed with its quest. */
    public static final String TAG_NONCE = "LostTalesQuestNonce";
    /** A server quest file's title as its operator wrote it; a bundled quest's starter has none. */
    public static final String TAG_TITLE = "LostTalesQuestTitle";
    /** The most a carried title holds, in UTF-8 bytes, as a quest card's does. */
    static final int MAX_TITLE_BYTES = 256;
    private static final int NONCE_BYTES = 16;

    private QuestStarters() {}

    /** A sealed starter for the quest; null where the world cannot seal. */
    public static ItemStack create(World world, LostTalesQuestDefinition quest) {
        byte[] key = MissiveSeals.keyOf(world);
        if (key == null || quest == null) {
            return null;
        }
        byte[] nonce = new byte[NONCE_BYTES];
        new SecureRandom().nextBytes(nonce);
        byte[] seal = MissiveSeal.signBytes(key, content(quest.getId(), nonce));
        if (seal == null) {
            return null;
        }
        ItemStack stack = new ItemStack(ELostTalesItem.QUEST_STARTER.getItem());
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(QuestStartGate.TAG_QUEST_ID, quest.getId());
        tag.setByteArray(TAG_NONCE, nonce);
        tag.setByteArray(TAG_SEAL, seal);
        String title = carriedTitle(quest);
        if (title.length() > 0) {
            tag.setString(TAG_TITLE, title);
        }
        stack.setTagCompound(tag);
        return stack;
    }

    /**
     * The title a starter carries for the quest: a server quest file's
     * own words, bounded; nothing for a bundled quest, which each reader's
     * game words from its lang line, nor for a missive.
     */
    static String carriedTitle(LostTalesQuestDefinition quest) {
        if (quest == null || quest.isBundled()
                || MissiveWords.isMissive(quest.getWords())) {
            return "";
        }
        String title = quest.getTitle() == null ? "" : quest.getTitle().trim();
        return LostTalesQuestShareResolver.fitBytes(title, MAX_TITLE_BYTES);
    }

    /** Whether the stack is a quest starter, sealed or not. */
    public static boolean isStarter(ItemStack stack) {
        return stack != null && stack.getItem() instanceof LostTalesItemQuestStarter;
    }

    /** The quest a starter names; empty for any other stack or an id past the bound. */
    public static String questIdOf(ItemStack stack) {
        NBTTagCompound tag = isStarter(stack) ? stack.getTagCompound() : null;
        String questId = tag == null ? "" : tag.getString(QuestStartGate.TAG_QUEST_ID).trim();
        return LostTalesQuestIds.fits(questId) ? questId : "";
    }

    /**
     * The starter's quest title in this side's language: the title it
     * carries, else the lang line its quest's id names, else the id;
     * empty where it names no quest.
     */
    public static String titleOf(ItemStack stack) {
        String questId = questIdOf(stack);
        if (questId.length() == 0) {
            return "";
        }
        String carried = LostTalesQuestShareResolver.fitBytes(
                stack.getTagCompound().getString(TAG_TITLE).trim(), MAX_TITLE_BYTES);
        String key = LostTalesQuestWords.isKeySafe(questId)
                ? LostTalesQuestWords.titleKey(questId) : "";
        return title(questId, carried, key.length() > 0 && StatCollector.canTranslate(key)
                ? StatCollector.translateToLocal(key) : "");
    }

    /** The title a starter shows: what it carries, else its quest's lang line, else its quest's id. */
    static String title(String questId, String carried, String line) {
        if (carried != null && carried.length() > 0) {
            return carried;
        }
        return line != null && line.length() > 0 ? line : questId;
    }

    /** Whether the stack is a starter carrying this world's seal over the quest it names. */
    public static boolean isGenuine(World world, ItemStack stack) {
        NBTTagCompound tag = isStarter(stack) ? stack.getTagCompound() : null;
        if (tag == null || !tag.hasKey(TAG_SEAL, Constants.NBT.TAG_BYTE_ARRAY)
                || !tag.hasKey(TAG_NONCE, Constants.NBT.TAG_BYTE_ARRAY)) {
            return false;
        }
        byte[] key = MissiveSeals.keyOf(world);
        return key != null && MissiveSeal.verifiesBytes(key,
                content(tag.getString(QuestStartGate.TAG_QUEST_ID),
                        tag.getByteArray(TAG_NONCE)),
                tag.getByteArray(TAG_SEAL));
    }

    /** What a starter's seal covers: that it is a starter, its quest and its nonce. */
    static byte[] content(String questId, byte[] nonce) {
        byte[] id = ("starter\u0000" + (questId == null ? "" : questId) + "\u0000")
                .getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[id.length + (nonce == null ? 0 : nonce.length)];
        System.arraycopy(id, 0, all, 0, id.length);
        if (nonce != null) {
            System.arraycopy(nonce, 0, all, id.length, nonce.length);
        }
        return all;
    }
}
