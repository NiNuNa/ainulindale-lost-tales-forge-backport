package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.util.LostTalesDimensionHelper;
import com.ninuna.losttales.util.LostTalesLog;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.Constants;

/**
 * Seals the missive letters the server writes, and tells a sealed letter
 * from any other, with the world's key ({@link MissiveSealWorldData}); a
 * quest's starter item is sealed with the same key
 * ({@link com.ninuna.losttales.quest.QuestStarters}). Server only: a client
 * holds no key and never checks a seal.
 */
public final class MissiveSeals {
    /** Where a letter keeps its seal, beside its missive. */
    public static final String TAG_SEAL = "LostTalesMissiveSeal";

    /** Whether the unusable key was logged; cleared with the server's stores. */
    private static boolean warnedUnusable;

    private MissiveSeals() {}

    /** Seals the letter's missive as it stands now; a letter the world cannot seal stays unsealed. */
    public static void seal(World world, ItemStack letter) {
        LostTalesMissiveData missive = LostTalesMissiveNbt.readFromItemStack(letter);
        byte[] key = keyOf(world);
        byte[] seal = key == null ? null : MissiveSeal.sign(key, missive);
        if (seal == null) {
            return;
        }
        if (letter.stackTagCompound == null) {
            letter.stackTagCompound = new NBTTagCompound();
        }
        letter.stackTagCompound.setByteArray(TAG_SEAL, seal);
    }

    /** Whether the letter carries this world's seal over what it says now. */
    public static boolean isGenuine(World world, ItemStack letter) {
        if (letter == null || letter.stackTagCompound == null
                || !letter.stackTagCompound.hasKey(TAG_SEAL, Constants.NBT.TAG_BYTE_ARRAY)) {
            return false;
        }
        return MissiveSeal.verifies(keyOf(world),
                LostTalesMissiveNbt.readFromItemStack(letter),
                letter.stackTagCompound.getByteArray(TAG_SEAL));
    }

    /** The world's sealing key, made on first use; null off the server or where it cannot be used. */
    public static byte[] keyOf(World world) {
        if (world == null || world.isRemote) {
            return null;
        }
        MapStorage storage;
        try {
            WorldServer overworld = LostTalesDimensionHelper.overworld(world);
            storage = overworld.mapStorage;
        } catch (IllegalStateException noOverworld) {
            return null;
        }
        if (storage == null) {
            return null;
        }
        MissiveSealWorldData data = (MissiveSealWorldData) storage.loadData(
                MissiveSealWorldData.class, MissiveSealWorldData.DATA_NAME);
        if (data == null) {
            data = new MissiveSealWorldData(MissiveSealWorldData.DATA_NAME);
            storage.setData(MissiveSealWorldData.DATA_NAME, data);
        }
        if (data.isUnusable()) {
            warnUnusable();
            return null;
        }
        return data.key();
    }

    /** Logs an unusable key again in the next world. */
    public static synchronized void clear() {
        warnedUnusable = false;
    }

    private static synchronized void warnUnusable() {
        if (!warnedUnusable) {
            warnedUnusable = true;
            LostTalesLog.warning("The missive seal key in this world cannot be used; "
                    + "boards post no letters, and no letter or quest starter can be"
                    + " accepted, pinned or made, until it can");
        }
    }
}
