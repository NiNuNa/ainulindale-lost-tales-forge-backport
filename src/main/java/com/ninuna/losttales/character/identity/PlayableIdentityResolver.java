package com.ninuna.losttales.character.identity;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Reads which identity a server player is playing as, telling storage
 * trouble apart from "on the account": a player with no roster yet, or a
 * roster with no active character, is on the account; an unreadable or
 * read-only store is an error and never mistaken for either.
 */
public final class PlayableIdentityResolver {

    private PlayableIdentityResolver() {}

    public static Resolution resolve(EntityPlayerMP player) {
        if (player == null || player.getUniqueID() == null
                || player.worldObj == null) {
            return Resolution.failure(CharacterErrorId.INVALID_PLAYER);
        }
        if (player.worldObj.isRemote) {
            return Resolution.failure(CharacterErrorId.CLIENT_SIDE_REQUEST);
        }
        try {
            CharacterWorldData data = CharacterStorage.get(player.worldObj);
            if (data.isReadOnlyForNewerVersion()) {
                return Resolution.failure(CharacterErrorId.STORAGE_READ_ONLY);
            }
            CharacterRoster roster = data.getRoster(player.getUniqueID());
            if (roster == null) {
                return new Resolution(CharacterErrorId.NONE,
                        PlayableIdentity.account(player.getUniqueID()),
                        null, null, data);
            }
            return new Resolution(CharacterErrorId.NONE,
                    PlayableIdentity.fromRoster(roster),
                    roster.getActiveCharacter(), roster, data);
        } catch (RuntimeException exception) {
            return Resolution.failure(CharacterErrorId.INTERNAL_ERROR);
        }
    }

    /** The name the identity goes by: the character's, else the account's. */
    public static String displayName(Resolution resolution, EntityPlayerMP player) {
        RoleplayCharacter character = resolution == null ? null
                : resolution.getCharacter();
        if (character != null && character.getName() != null
                && character.getName().trim().length() > 0) {
            return character.getName();
        }
        return player == null ? "" : player.getCommandSenderName();
    }

    /** What the store said about one player. */
    public static final class Resolution {
        private final CharacterErrorId error;
        private final PlayableIdentity identity;
        private final RoleplayCharacter character;
        private final CharacterRoster roster;
        private final CharacterWorldData data;

        private Resolution(CharacterErrorId error, PlayableIdentity identity,
                           RoleplayCharacter character, CharacterRoster roster,
                           CharacterWorldData data) {
            this.error = error;
            this.identity = identity;
            this.character = character;
            this.roster = roster;
            this.data = data;
        }

        private static Resolution failure(CharacterErrorId error) {
            return new Resolution(error, null, null, null, null);
        }

        public boolean isAvailable() {
            return this.error == CharacterErrorId.NONE && this.identity != null;
        }

        public CharacterErrorId getError() {
            return this.error;
        }

        /** Null unless available. */
        public PlayableIdentity getIdentity() {
            return this.identity;
        }

        /** The active character; null for the account or when unavailable. */
        public RoleplayCharacter getCharacter() {
            return this.character;
        }

        /** Null when the player has no roster yet or the store is unavailable. */
        public CharacterRoster getRoster() {
            return this.roster;
        }

        public CharacterWorldData getData() {
            return this.data;
        }
    }
}
