package com.ninuna.losttales.quest;

import com.ninuna.losttales.quest.missive.MissiveSeal;
import com.ninuna.losttales.quest.missive.MissiveSeals;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.Constants;
import net.minecraft.world.World;

/**
 * A quest's starter item: a sheet of paper naming its quest, sealed by the
 * world as a missive letter is (QUEST-34). Only a sealed starter starts its
 * quest, and it is used up as the quest starts, so a starter made or
 * changed by hand starts nothing and one starter starts one quest.
 */
public final class QuestStarters {
    /** Where a starter keeps its seal. */
    public static final String TAG_SEAL = "LostTalesQuestSeal";
    /** What makes each starter its own, sealed with its quest. */
    public static final String TAG_NONCE = "LostTalesQuestNonce";
    private static final int NONCE_BYTES = 16;

    private QuestStarters() {}

    /**
     * A sealed starter for the quest, named after it in this side's
     * language; null where the world cannot seal.
     */
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
        ItemStack stack = new ItemStack(Items.paper);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(QuestStartGate.TAG_QUEST_ID, quest.getId());
        tag.setByteArray(TAG_NONCE, nonce);
        tag.setByteArray(TAG_SEAL, seal);
        stack.setTagCompound(tag);
        // An item's name is kept as written, so it is written in the
        // server's language.
        stack.setStackDisplayName(StatCollector.translateToLocalFormatted(
                "chat.losttales.quest.command.starter.name",
                LostTalesQuestWords.title(quest)));
        return stack;
    }

    /** Whether the stack carries this world's seal over the quest it names. */
    public static boolean isGenuine(World world, ItemStack stack) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
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
