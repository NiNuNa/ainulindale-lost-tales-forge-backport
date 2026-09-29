package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.storage.NbtTags;
import java.security.SecureRandom;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.util.Constants;

/**
 * The world's secret key for sealing missive letters ({@link MissiveSeal}).
 * It is made from a secure random source the first time a letter is sealed
 * and never leaves the server. A key of another data version, or one that is
 * not whole, is kept as it was and seals nothing, so no letter written with it
 * is ever taken for a forgery by a key made in its place.
 */
public final class MissiveSealWorldData extends WorldSavedData {
    public static final String DATA_NAME = "losttales_missive_seal";
    static final int DATA_VERSION = 1;
    static final String TAG_VERSION = "DataVersion";
    static final String TAG_KEY = "Key";

    private byte[] key;
    private NBTTagCompound unusable;

    public MissiveSealWorldData() {
        this(DATA_NAME);
    }

    public MissiveSealWorldData(String name) {
        super(name);
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound compound) {
        this.key = null;
        this.unusable = null;
        byte[] read = compound.hasKey(TAG_KEY, Constants.NBT.TAG_BYTE_ARRAY)
                ? compound.getByteArray(TAG_KEY) : null;
        if (compound.getInteger(TAG_VERSION) != DATA_VERSION
                || read == null || read.length != MissiveSeal.LENGTH) {
            this.unusable = (NBTTagCompound) compound.copy();
            return;
        }
        this.key = read.clone();
    }

    @Override
    public synchronized void writeToNBT(NBTTagCompound compound) {
        if (this.unusable != null) {
            NbtTags.copyContents(this.unusable, compound);
            return;
        }
        if (this.key != null) {
            compound.setInteger(TAG_VERSION, DATA_VERSION);
            compound.setByteArray(TAG_KEY, this.key.clone());
        }
    }

    /** The key, made now if the world has none yet; null when the stored one cannot be used. */
    public synchronized byte[] key() {
        if (this.unusable != null) {
            return null;
        }
        if (this.key == null) {
            this.key = new byte[MissiveSeal.LENGTH];
            new SecureRandom().nextBytes(this.key);
            markDirty();
        }
        return this.key.clone();
    }

    public synchronized boolean isUnusable() {
        return this.unusable != null;
    }
}
