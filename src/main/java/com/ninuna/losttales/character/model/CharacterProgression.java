package com.ninuna.losttales.character.model;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Server-owned progression state for one roleplaying character.
 *
 * The initial character feature does not award experience yet. Experience is
 * nevertheless stored here so later progression rules can be added without
 * changing character identity or replacing the roster format.
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
     * Intended for future server-side progression services only.
     */
    public void setExperiencePoints(long experiencePoints) {
        this.experiencePoints = Math.max(0L, experiencePoints);
    }

    /**
     * Returns a defensive copy. Callers cannot mutate persistent state through
     * the returned compound.
     */
    public NBTTagCompound getExtensionDataCopy() {
        return copyCompound(this.extensionData);
    }

    /**
     * Intended for storage migration and future server-side progression code.
     */
    public void setExtensionData(NBTTagCompound extensionData) {
        this.extensionData = copyCompound(extensionData);
    }

    private static NBTTagCompound copyCompound(NBTTagCompound compound) {
        if (compound == null) {
            return new NBTTagCompound();
        }
        return (NBTTagCompound) compound.copy();
    }
}
