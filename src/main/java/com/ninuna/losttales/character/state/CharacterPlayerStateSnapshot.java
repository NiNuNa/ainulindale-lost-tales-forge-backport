package com.ninuna.losttales.character.state;

import net.minecraft.nbt.NBTTagCompound;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Immutable generation of allowlisted character-owned player state. */
public final class CharacterPlayerStateSnapshot {

    public static final int CURRENT_DATA_VERSION = 6;

    private final UUID characterId;
    private final long generation;
    private final long capturedAt;
    private final int dataVersion;
    private final Map<String, NBTTagCompound> components;

    public CharacterPlayerStateSnapshot(UUID characterId,
                                        long generation,
                                        long capturedAt,
                                        int dataVersion,
                                        Map<String, NBTTagCompound> components) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId must not be null");
        }
        if (generation <= 0L) {
            throw new IllegalArgumentException("generation must be positive");
        }
        if (dataVersion < 0 || dataVersion > CURRENT_DATA_VERSION) {
            throw new IllegalArgumentException("unsupported snapshot version " + dataVersion);
        }
        this.characterId = characterId;
        this.generation = generation;
        this.capturedAt = Math.max(0L, capturedAt);
        this.dataVersion = dataVersion;
        this.components = deepCopy(components);
    }

    public UUID getCharacterId() {
        return this.characterId;
    }

    public long getGeneration() {
        return this.generation;
    }

    public long getCapturedAt() {
        return this.capturedAt;
    }

    public int getDataVersion() {
        return this.dataVersion;
    }

    public NBTTagCompound getComponent(String id) {
        NBTTagCompound component = this.components.get(id);
        return component == null ? null : (NBTTagCompound) component.copy();
    }

    public Map<String, NBTTagCompound> copyComponents() {
        return deepCopy(this.components);
    }

    public Map<String, NBTTagCompound> getComponentsView() {
        return Collections.unmodifiableMap(deepCopy(this.components));
    }

    private static Map<String, NBTTagCompound> deepCopy(
            Map<String, NBTTagCompound> source) {
        LinkedHashMap<String, NBTTagCompound> copy =
                new LinkedHashMap<String, NBTTagCompound>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<String, NBTTagCompound> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            copy.put(entry.getKey(), (NBTTagCompound) entry.getValue().copy());
        }
        return copy;
    }
}
