package com.ninuna.losttales.character.state;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent snapshot manifest for all characters owned by one account. */
public final class CharacterPlayerStateAccount {

    public static final int CURRENT_DATA_VERSION = 11;
    public static final int CURRENT_BOOTSTRAP_VERSION = 11;

    private final UUID ownerId;
    private int bootstrapVersion;
    private long bootstrappedAt;
    private final Map<UUID, CharacterPlayerStateRecord> records =
            new LinkedHashMap<UUID, CharacterPlayerStateRecord>();

    public CharacterPlayerStateAccount(UUID ownerId) {
        this(ownerId, 0, 0L, null);
    }

    public CharacterPlayerStateAccount(UUID ownerId,
                                       int bootstrapVersion,
                                       long bootstrappedAt,
                                       Collection<CharacterPlayerStateRecord> records) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        if (bootstrapVersion < 0
                || bootstrapVersion > CURRENT_BOOTSTRAP_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported bootstrap version " + bootstrapVersion);
        }
        this.ownerId = ownerId;
        this.bootstrapVersion = bootstrapVersion;
        this.bootstrappedAt = Math.max(0L, bootstrappedAt);
        if (records != null) {
            for (CharacterPlayerStateRecord record : records) {
                if (record == null || this.records.containsKey(record.getCharacterId())) {
                    throw new IllegalArgumentException("duplicate or null character state record");
                }
                this.records.put(record.getCharacterId(), record);
            }
        }
    }

    public UUID getOwnerId() {
        return this.ownerId;
    }

    public int getBootstrapVersion() {
        return this.bootstrapVersion;
    }

    public long getBootstrappedAt() {
        return this.bootstrappedAt;
    }

    public boolean isBootstrapped() {
        return this.bootstrapVersion >= CURRENT_BOOTSTRAP_VERSION;
    }

    public void markBootstrapped(long timestamp) {
        this.bootstrapVersion = CURRENT_BOOTSTRAP_VERSION;
        this.bootstrappedAt = Math.max(0L, timestamp);
    }

    public CharacterPlayerStateRecord getRecord(UUID characterId) {
        return characterId == null ? null : this.records.get(characterId);
    }

    public void putRecord(CharacterPlayerStateRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        this.records.put(record.getCharacterId(), record);
    }

    public CharacterPlayerStateRecord removeRecord(UUID characterId) {
        return characterId == null ? null : this.records.remove(characterId);
    }

    public Collection<CharacterPlayerStateRecord> getRecords() {
        return Collections.unmodifiableCollection(this.records.values());
    }
}
