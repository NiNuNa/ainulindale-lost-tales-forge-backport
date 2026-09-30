package com.ninuna.losttales.character.lore.transfer;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.lore.LoreCharacterDefinition;
import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

/**
 * A lore character claimed again takes its profile and age from its lore
 * file; everything it lived through — its record's pledge and capes,
 * with its saved state beside it — comes from the last holder.
 */
public final class LoreCharacterRebindTest {

    private static final UUID LAST_HOLDER =
            UUID.fromString("c5000000-0000-0000-0000-00000000005c");
    private static final UUID NEXT_HOLDER =
            UUID.fromString("c6000000-0000-0000-0000-00000000006c");
    private static final UUID CHARACTER =
            UUID.fromString("c7000000-0000-0000-0000-00000000007c");

    private static final CharacterProfile FILE_PROFILE = CharacterProfile.EMPTY
            .withSection(CharacterProfile.Section.HISTORY, "Ring-bearer.")
            .withFact(CharacterProfile.Fact.HOME, "Bag End");

    @Test
    public void theProfileAndAgeComeFromTheLoreFile() {
        RoleplayCharacter claimed = LoreCharacterTransferCoordinator.rebind(
                lived(), definition(), NEXT_HOLDER, 2, 500L);
        assertEquals(FILE_PROFILE, claimed.getProfile());
        assertEquals(50, claimed.getAge());
    }

    @Test
    public void whatItLivedThroughComesFromTheLastHolder() {
        RoleplayCharacter claimed = LoreCharacterTransferCoordinator.rebind(
                lived(), definition(), NEXT_HOLDER, 2, 500L);
        assertEquals(CHARACTER, claimed.getCharacterId());
        assertEquals(NEXT_HOLDER, claimed.getOwnerId());
        assertEquals(2, claimed.getSlotIndex());
        assertEquals(500L, claimed.getCreationTimestamp());
        assertEquals("lotr:dale", claimed.getPledgedFactionId());
        assertEquals(CharacterCapeCatalog.RANGER, claimed.getCosmeticCapeId());
        assertEquals("losttales:hobbit_shire_male_0", claimed.getSkinId());
    }

    @Test
    public void aLoreFileGoneSinceLeavesTheRecordsOwn() {
        RoleplayCharacter claimed = LoreCharacterTransferCoordinator.rebind(
                lived(), null, NEXT_HOLDER, 2, 500L);
        assertEquals("Written by the last holder.",
                claimed.getProfile().section(CharacterProfile.Section.HISTORY));
        assertEquals(33, claimed.getAge());
    }

    private static LoreCharacterDefinition definition() {
        return new LoreCharacterDefinition("losttales:frodo", "Frodo Baggins", "The Ring-bearer.", 50,
                new LoreCharacterDefinition.Appearance(CharacterRaceRegistry.HOBBIT,
                        CharacterGenderRegistry.MALE, "lotr:hobbit",
                        "losttales:hobbit_shire_male_0"),
                FILE_PROFILE);
    }

    private static RoleplayCharacter lived() {
        return RoleplayCharacter.builder(CHARACTER, LAST_HOLDER)
                .slot(4).name("Frodo Baggins").race(CharacterRaceRegistry.HOBBIT)
                .gender(CharacterGenderRegistry.MALE)
                .skin("losttales:hobbit_shire_male_0").age(33)
                .startingFaction("lotr:hobbit").pledgedFaction("lotr:dale")
                .cosmeticCape(CharacterCapeCatalog.RANGER)
                .profile(CharacterProfile.EMPTY.withSection(
                        CharacterProfile.Section.HISTORY,
                        "Written by the last holder."))
                .createdAt(100L)
                .build();
    }
}
