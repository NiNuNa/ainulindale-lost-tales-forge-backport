package com.ninuna.losttales.character.storage;

import com.ninuna.losttales.character.model.CharacterRoster;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldSavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * World-level authoritative store for roleplaying character rosters.
 *
 * Instances are owned by dimension-zero MapStorage. Access and mutation are
 * expected to occur on the logical server thread.
 */
public class CharacterWorldData extends WorldSavedData {

    public static final String DATA_NAME = "losttales_characters";

    private final Map<UUID, CharacterRoster> rosters = new LinkedHashMap<UUID, CharacterRoster>();
    private final List<NBTTagCompound> quarantinedEntries = new ArrayList<NBTTagCompound>();
    private boolean readOnlyForNewerVersion;
    private int unsupportedDataVersion = -1;
    private NBTTagCompound preservedNewerData;

    public CharacterWorldData() {
        this(DATA_NAME);
    }

    public CharacterWorldData(String name) {
        super(name);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        this.rosters.clear();
        this.quarantinedEntries.clear();
        this.readOnlyForNewerVersion = false;
        this.unsupportedDataVersion = -1;
        this.preservedNewerData = null;

        CharacterNbtCodec.ReadResult result = CharacterNbtCodec.read(compound);
        if (result.isReadOnly()) {
            this.readOnlyForNewerVersion = true;
            this.unsupportedDataVersion = result.getUnsupportedVersion();
            this.preservedNewerData = result.getOriginalDataCopy();
            return;
        }

        this.rosters.putAll(result.getRosters());
        this.quarantinedEntries.addAll(result.getQuarantinedEntriesCopy());
        if (result.wasRepaired()) {
            markDirty();
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound compound) {
        if (this.readOnlyForNewerVersion && this.preservedNewerData != null) {
            copyTagContents(this.preservedNewerData, compound);
            return;
        }
        CharacterNbtCodec.write(compound, this.rosters.values(), this.quarantinedEntries);
    }

    public boolean isReadOnlyForNewerVersion() {
        return this.readOnlyForNewerVersion;
    }

    public int getUnsupportedDataVersion() {
        return this.unsupportedDataVersion;
    }

    public CharacterRoster getRoster(UUID ownerId) {
        return ownerId == null ? null : this.rosters.get(ownerId);
    }

    public CharacterRoster getOrCreateRoster(UUID ownerId) {
        ensureWritable();
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }

        CharacterRoster roster = this.rosters.get(ownerId);
        if (roster == null) {
            roster = new CharacterRoster(ownerId);
            this.rosters.put(ownerId, roster);
            markDirty();
        }
        return roster;
    }

    /**
     * Stores or reaffirms a roster after an authoritative mutation and marks
     * the world data dirty so normal server saves persist it.
     */
    public void saveRoster(CharacterRoster roster) {
        ensureWritable();
        if (roster == null) {
            throw new IllegalArgumentException("roster must not be null");
        }
        this.rosters.put(roster.getOwnerId(), roster);
        markDirty();
    }

    public CharacterRoster removeRoster(UUID ownerId) {
        ensureWritable();
        CharacterRoster removed = ownerId == null ? null : this.rosters.remove(ownerId);
        if (removed != null) {
            markDirty();
        }
        return removed;
    }

    public int getRosterCount() {
        return this.rosters.size();
    }

    public Collection<CharacterRoster> getRosters() {
        return Collections.unmodifiableCollection(this.rosters.values());
    }

    public int getQuarantinedEntryCount() {
        return this.quarantinedEntries.size();
    }

    public List<NBTTagCompound> getQuarantinedEntriesCopy() {
        ArrayList<NBTTagCompound> copies = new ArrayList<NBTTagCompound>();
        for (NBTTagCompound entry : this.quarantinedEntries) {
            if (entry != null) {
                copies.add((NBTTagCompound) entry.copy());
            }
        }
        return Collections.unmodifiableList(copies);
    }

    private void ensureWritable() {
        if (this.readOnlyForNewerVersion) {
            throw new IllegalStateException(
                    "Character data is read-only because its format is malformed or uses unsupported version "
                            + this.unsupportedDataVersion
            );
        }
    }

    private static void copyTagContents(NBTTagCompound source, NBTTagCompound destination) {
        Set<?> keySet = source.func_150296_c();
        for (Object keyObject : keySet) {
            if (!(keyObject instanceof String)) {
                continue;
            }
            String key = (String) keyObject;
            NBTBase value = source.getTag(key);
            if (value != null) {
                destination.setTag(key, value.copy());
            }
        }
    }
}
