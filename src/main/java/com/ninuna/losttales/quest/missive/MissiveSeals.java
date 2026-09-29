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
 * from any other, with the world's key ({@link MissiveSealWorldData}). Server
 * only: a client holds no key and never checks a seal.
 */
public final class MissiveSeals {
    /** Where a letter keeps its seal, beside its missive. */
    public static final String TAG_SEAL = "LostTalesMissiveSeal";

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

    private static byte[] keyOf(World world) {
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

    private static synchronized void warnUnusable() {
        if (!warnedUnusable) {
            warnedUnusable = true;
            LostTalesLog.warning("The missive seal key in this world cannot be used; "
                    + "no missive letter can be accepted or pinned until it can");
        }
    }
}
