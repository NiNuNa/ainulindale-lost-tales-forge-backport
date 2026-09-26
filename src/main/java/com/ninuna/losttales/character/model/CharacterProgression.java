package com.ninuna.losttales.character.model;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Server-owned progression state for one roleplaying character: its
 * experience, which nothing awards, and a compound of
 * extension data, both kept with the character.
 */
public class CharacterProgression {

    public static final int CURRENT_DATA_VERSION = 1;

    private int dataVersion;
    private long experiencePoints;
    private NBTTagCompound extensionData;

    public CharacterProgression() {
        this(CURRENT_DATA_VERSION, 0L, new NBTTagCompound());
    }

    public CharacterProgression(int dataVersion, long experiencePoints, NBTTagCompound extensionData) {
        this.dataVersion = dataVersion <= 0 ? CURRENT_DATA_VERSION : dataVersion;
        this.experiencePoints = Math.max(0L, experiencePoints);
        this.extensionData = copyCompound(extensionData);
    }

    public int getDataVersion() {
        return this.dataVersion;
    }

    public long getExperiencePoints() {
        return this.experiencePoints;
    }

    /**
     * Returns a defensive copy. Callers cannot mutate persistent state through
     * the returned compound.
     */
    public NBTTagCompound getExtensionDataCopy() {
        return copyCompound(this.extensionData);
    }

    private static NBTTagCompound copyCompound(NBTTagCompound compound) {
        if (compound == null) {
            return new NBTTagCompound();
        }
        return (NBTTagCompound) compound.copy();
    }
}
