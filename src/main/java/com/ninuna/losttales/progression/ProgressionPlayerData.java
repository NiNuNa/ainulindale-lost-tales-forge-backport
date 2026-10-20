package com.ninuna.losttales.progression;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.progression.perk.PerkIndex;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.IExtendedEntityProperties;
import net.minecraftforge.common.util.Constants;

/**
 * The played character's progression, kept with the player
 * ({@link IExtendedEntityProperties}) and copied across death, which takes
 * none of it (PROG-10). A character switch swaps the whole of it
 * ({@link ProgressionStateComponent}). A save it cannot read is kept
 * whole and nothing changes it.
 */
public final class ProgressionPlayerData implements IExtendedEntityProperties {
    public static final String PROPERTY_ID = "LostTalesProgression";

    private ProgressionState state = new ProgressionState();
    private boolean readOnly;
    private NBTBase preserved;
    /** Whether the client's copy is behind; never saved. */
    private boolean dirty = true;
    /** The perks taken, by effect, for the catalog revision it was built for; never saved. */
    private PerkIndex perkIndex;
    private int perkIndexRevision = -1;

    public static ProgressionPlayerData get(EntityPlayer player) {
        if (player == null) {
            return null;
        }
        IExtendedEntityProperties properties = player.getExtendedProperties(PROPERTY_ID);
        if (properties instanceof ProgressionPlayerData) {
            return (ProgressionPlayerData) properties;
        }
        ProgressionPlayerData data = new ProgressionPlayerData();
        player.registerExtendedProperties(PROPERTY_ID, data);
        return data;
    }

    @Override
    public void saveNBTData(NBTTagCompound compound) {
        if (compound == null) {
            return;
        }
        if (this.readOnly) {
            if (this.preserved != null) {
                compound.setTag(PROPERTY_ID, this.preserved.copy());
            }
            return;
        }
        compound.setTag(PROPERTY_ID, ProgressionStateNbt.write(this.state));
    }

    @Override
    public void loadNBTData(NBTTagCompound compound) {
        this.state = new ProgressionState();
        this.readOnly = false;
        this.preserved = null;
        this.dirty = true;
        this.perkIndex = null;
        if (compound == null || !compound.hasKey(PROPERTY_ID)) {
            return;
        }
        if (!compound.hasKey(PROPERTY_ID, Constants.NBT.TAG_COMPOUND)) {
            keepUnread(compound.getTag(PROPERTY_ID), "it is not a compound");
            return;
        }
        NBTTagCompound saved = compound.getCompoundTag(PROPERTY_ID);
        try {
            this.state = ProgressionStateNbt.read(saved);
        } catch (RuntimeException unreadable) {
            this.state = new ProgressionState();
            keepUnread(saved, unreadable.getMessage());
        }
    }

    @Override
    public void init(Entity entity, World world) {
        // The data needs nothing from the player it is attached to.
    }

    /** Whether the save could not be read and is kept whole, unchanged. */
    public boolean isReadOnly() {
        return this.readOnly;
    }

    /** A copy of the state; change it through {@link #replace}. */
    public ProgressionState state() {
        return this.state.copy();
    }

    /** The state itself, read without copying; never changed by the caller. */
    ProgressionState view() {
        return this.state;
    }

    /** Puts a changed state in place; refused while the save is read-only. */
    public boolean replace(ProgressionState changed) {
        if (this.readOnly || changed == null) {
            return false;
        }
        this.state = changed.copy();
        this.dirty = true;
        this.perkIndex = null;
        return true;
    }

    /** The perks the character took, by effect, as the skills in force know them. */
    public PerkIndex perkIndex() {
        int revision = ProgressionCatalog.revision();
        if (this.perkIndex == null || this.perkIndexRevision != revision) {
            this.perkIndex = this.readOnly ? PerkIndex.EMPTY
                    : PerkIndex.of(this.state, ProgressionCatalog.skillsById());
            this.perkIndexRevision = revision;
        }
        return this.perkIndex;
    }

    /** Whether the client's copy is behind; asking forgets it. */
    public boolean takeDirty() {
        boolean was = this.dirty;
        this.dirty = false;
        return was;
    }

    public void markDirty() {
        this.dirty = true;
    }

    /** The character's state as its snapshot holds it. */
    public NBTTagCompound writeCharacterState() {
        if (this.readOnly) {
            throw new IllegalStateException("progression cannot be read");
        }
        return ProgressionStateNbt.write(this.state);
    }

    /** Puts a character's saved state in place, as a switch does. */
    public void replaceCharacterState(NBTTagCompound saved) {
        ProgressionState read = ProgressionStateNbt.read(saved);
        this.state = read;
        this.readOnly = false;
        this.preserved = null;
        this.dirty = true;
        this.perkIndex = null;
    }

    /** Takes the old player's whole progression, as a death or a return from the End does. */
    public void copyFrom(ProgressionPlayerData old) {
        if (old == null) {
            return;
        }
        this.state = old.state.copy();
        this.readOnly = old.readOnly;
        this.preserved = old.preserved == null ? null : old.preserved.copy();
        this.dirty = true;
        this.perkIndex = null;
    }

    private void keepUnread(NBTBase saved, String why) {
        this.readOnly = true;
        this.preserved = saved == null ? null : saved.copy();
        FMLLog.warning("[%s] A player's progression cannot be read (%s); it is kept"
                        + " as saved and nothing changes it",
                LostTalesMetaData.MOD_ID, why);
    }
}
