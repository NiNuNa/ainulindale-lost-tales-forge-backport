package com.ninuna.losttales.character.lore.transfer;

import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.state.CharacterPlayerStateRecord;
import com.ninuna.losttales.character.state.CharacterPlayerStateSnapshot;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/** A waiting release hands on the life its holder lived meanwhile. */
public final class LoreCharacterHeldLifeTest {

    private static final String GANDALF = "losttales:gandalf";
    private static final UUID OWNER = UUID.fromString(
            "11000000-0000-0000-0000-000000000011");
    private static final UUID OTHER_OWNER = UUID.fromString(
            "44000000-0000-0000-0000-000000000044");
    private static final UUID CHARACTER = UUID.fromString(
            "22000000-0000-0000-0000-000000000022");
    private static final UUID OTHER_CHARACTER = UUID.fromString(
            "55000000-0000-0000-0000-000000000055");

    @Test
    public void newerSavedLifeReplacesTheRequestsCopy() {
        LoreCharacterVaultEntry vault = vault();

        LoreCharacterVaultEntry held = LoreCharacterTransferCoordinator.heldNow(
                vault, null, state(CHARACTER, 9L), 200L);

        assertEquals(9L, held.getPlayerStateCopy().getCurrentGeneration());
        assertEquals("Gandalf", held.getCharacterCopy().getName());
        assertEquals(OWNER, held.getCapturedOwnerId());
        assertEquals(200L, held.getUpdatedAt());
    }

    @Test
    public void currentRecordReplacesTheRequestsCharacter() {
        LoreCharacterVaultEntry held = LoreCharacterTransferCoordinator.heldNow(
                vault(), character(CHARACTER, OWNER, "Mithrandir"),
                state(CHARACTER, 8L), 200L);

        assertEquals("Mithrandir", held.getCharacterCopy().getName());
        assertEquals(8L, held.getPlayerStateCopy().getCurrentGeneration());
    }

    @Test
    public void nothingHeldKeepsTheCopy() {
        LoreCharacterVaultEntry vault = vault();

        assertSame(vault, LoreCharacterTransferCoordinator.heldNow(
                vault, null, null, 200L));
    }

    @Test
    public void anotherCharactersRecordsAreNotTaken() {
        LoreCharacterVaultEntry vault = vault();

        assertSame(vault, LoreCharacterTransferCoordinator.heldNow(
                vault, character(OTHER_CHARACTER, OWNER, "Saruman"),
                state(OTHER_CHARACTER, 9L), 200L));
        assertSame(vault, LoreCharacterTransferCoordinator.heldNow(
                vault, character(CHARACTER, OTHER_OWNER, "Gandalf"),
                null, 200L));
    }

    private static LoreCharacterVaultEntry vault() {
        return new LoreCharacterVaultEntry(GANDALF, OWNER,
                character(CHARACTER, OWNER, "Gandalf"),
                state(CHARACTER, 7L), 100L);
    }

    private static RoleplayCharacter character(UUID id, UUID owner,
                                               String name) {
        String skin = CharacterSkinRegistry.getCompatibleSkins(
                CharacterRaceRegistry.HUMAN,
                CharacterBodyTypeRegistry.MASCULINE).get(0).getId();
        return RoleplayCharacter.builder(id, owner)
                .name(name).race(CharacterRaceRegistry.HUMAN)
                .bodyType(CharacterBodyTypeRegistry.MASCULINE).skin(skin).age(18)
                .startingFaction("lotr:bree").createdAt(1L)
                .profile(CharacterProfile.EMPTY)
                .build();
    }

    private static CharacterPlayerStateRecord state(UUID id, long generation) {
        CharacterPlayerStateSnapshot current =
                new CharacterPlayerStateSnapshot(
                        id, generation, 100L,
                        CharacterPlayerStateSnapshot.CURRENT_DATA_VERSION,
                        Collections.<String, NBTTagCompound>emptyMap());
        return new CharacterPlayerStateRecord(id, current, null);
    }
}
