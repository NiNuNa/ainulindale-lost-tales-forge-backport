package com.ninuna.losttales.compat.lotr.hired;

import com.ninuna.losttales.character.identity.PlayableIdentity;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import java.util.UUID;

/**
 * Which character hired a unit, kept in the entity's own persistent data
 * so it travels with the unit through chunk saves: the owning account and
 * the character's id. LOTR itself only knows the account. The tag keys on
 * the identity's gameplay id.
 */
public final class LotrHiredUnitTag {

    static final String KEY_ACCOUNT = "losttales:HiredByAccount";
    static final String KEY_CHARACTER = "losttales:HiredByCharacter";

    private final UUID ownerId;
    private final UUID characterId;

    LotrHiredUnitTag(UUID ownerId, UUID characterId) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        this.ownerId = ownerId;
        this.characterId = characterId;
    }

    public static LotrHiredUnitTag of(PlayableIdentity identity) {
        return new LotrHiredUnitTag(identity.getOwnerId(), identity.getCharacterId());
    }

    public UUID getOwnerId() {
        return this.ownerId;
    }

    /** The hiring character, or null for a unit hired before the first character was made. */
    public UUID getCharacterId() {
        return this.characterId;
    }

    /** The identity as one key: its gameplay id, the character's or else the account's. */
    public String identityKey() {
        return PlayableIdentity.gameplayId(this.characterId, this.ownerId).toString();
    }

    /** The key of {@code identity}, or null for none. */
    public static String identityKey(PlayableIdentity identity) {
        return identity == null ? null : identity.getGameplayId().toString();
    }

    /** The tag on the entity, or null when it carries none it can read. */
    public static LotrHiredUnitTag read(Entity entity) {
        return entity == null ? null : read(entity.getEntityData());
    }

    static LotrHiredUnitTag read(NBTTagCompound data) {
        if (data == null || !data.hasKey(KEY_ACCOUNT, Constants.NBT.TAG_STRING)) {
            return null;
        }
        UUID ownerId = parse(data.getString(KEY_ACCOUNT));
        if (ownerId == null) {
            return null;
        }
        UUID characterId = data.hasKey(KEY_CHARACTER, Constants.NBT.TAG_STRING)
                ? parse(data.getString(KEY_CHARACTER)) : null;
        return new LotrHiredUnitTag(ownerId, characterId);
    }

    public void write(Entity entity) {
        if (entity != null) {
            write(entity.getEntityData());
        }
    }

    void write(NBTTagCompound data) {
        data.setString(KEY_ACCOUNT, this.ownerId.toString());
        if (this.characterId == null) {
            data.removeTag(KEY_CHARACTER);
        } else {
            data.setString(KEY_CHARACTER, this.characterId.toString());
        }
    }

    private static UUID parse(String value) {
        if (value == null || value.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
