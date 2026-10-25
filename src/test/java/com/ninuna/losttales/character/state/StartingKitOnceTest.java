package com.ninuna.losttales.character.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

/**
 * A character is given its starting kits once (CHAR-40): only a character
 * with no saved state gets a first one, and every later bootstrap, after
 * a switch away and back or a rejoin, finds its state and leaves it be.
 */
public final class StartingKitOnceTest {
    private static final UUID OWNER = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID FIRST = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID SECOND = UUID.fromString("20000000-0000-0000-0000-000000000003");

    @Test
    public void onlyACharacterWithNoStateGetsAFirstOne() {
        CharacterRoster roster = new CharacterRoster(OWNER);
        assertTrue(roster.addCharacter(character(FIRST, 0, "Bilbo")));
        assertTrue(roster.addCharacter(character(SECOND, 1, "Lobelia")));
        CharacterPlayerStateAccount account = new CharacterPlayerStateAccount(OWNER);
        account.putRecord(record(FIRST));

        assertEquals(Arrays.asList(SECOND), ids(roster, account));

        // Made once, the second's state stays: no later bootstrap starts it again.
        account.putRecord(record(SECOND));
        assertTrue(CharacterPlayerStateService.withoutState(roster, account).isEmpty());
    }

    private static java.util.List<UUID> ids(CharacterRoster roster,
                                            CharacterPlayerStateAccount account) {
        java.util.List<UUID> ids = new java.util.ArrayList<UUID>();
        for (RoleplayCharacter character : CharacterPlayerStateService.withoutState(roster, account)) {
            ids.add(character.getCharacterId());
        }
        return ids;
    }

    private static CharacterPlayerStateRecord record(UUID characterId) {
        LinkedHashMap<String, NBTTagCompound> components =
                new LinkedHashMap<String, NBTTagCompound>();
        NBTTagCompound inventory = new NBTTagCompound();
        inventory.setInteger("Version", 1);
        components.put("vanilla_inventory", inventory);
        CharacterPlayerStateSnapshot snapshot = new CharacterPlayerStateSnapshot(characterId,
                1L, 5L, CharacterPlayerStateSnapshot.CURRENT_DATA_VERSION, components);
        return new CharacterPlayerStateRecord(characterId, snapshot, null);
    }

    private static RoleplayCharacter character(UUID id, int slot, String name) {
        return RoleplayCharacter.builder(id, OWNER)
                .slot(slot).name(name).race(CharacterRaceRegistry.HOBBIT)
                .bodyType(CharacterBodyTypeRegistry.MASCULINE)
                .skin("losttales:hobbit_shire_masculine_0").age(50)
                .startingFaction("lotr:hobbit")
                .createdAt(1L)
                .build();
    }
}
