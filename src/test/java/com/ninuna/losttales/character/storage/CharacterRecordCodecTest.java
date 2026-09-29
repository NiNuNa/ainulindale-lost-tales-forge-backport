package com.ninuna.losttales.character.storage;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A character record carries no level and no experience (R2 a) and keeps
 * the pledge LOTR last reported (R3 a). A record of the layout before
 * either is refused whole, and the store stays read-only rather than
 * guessing: that world needs making anew.
 */
public final class CharacterRecordCodecTest {

    private static final UUID OWNER =
            UUID.fromString("f0000000-0000-0000-0000-00000000000f");
    private static final UUID CHARACTER =
            UUID.fromString("f1000000-0000-0000-0000-00000000001f");

    @Test
    public void aRecordHoldsNeitherALevelNorExperience() {
        NBTTagCompound record = CharacterNbtCodec.writeCharacterRecord(
                character("lotr:rohan"));
        assertFalse(record.hasKey("RoleplayLevel"));
        assertFalse(record.hasKey("Progression"));
        assertFalse(record.hasKey("ExperiencePoints"));
        assertEquals(RoleplayCharacter.CURRENT_DATA_VERSION,
                record.getInteger("DataVersion"));
    }

    @Test
    public void thePledgeRoundTrips() {
        RoleplayCharacter loaded = CharacterNbtCodec.readCharacterRecord(
                CharacterNbtCodec.writeCharacterRecord(character("lotr:rohan")),
                OWNER);
        assertEquals("lotr:rohan", loaded.getPledgedFactionId());
        assertEquals("lotr:rohan", loaded.getFactionId());
        assertEquals("lotr:gondor", loaded.getStartingFactionId());
    }

    /** When the faction took effect is kept, and never reads as before the character's making. */
    @Test
    public void theFactionsStartRoundTrips() {
        RoleplayCharacter pledged = character("");
        pledged.setPledgedFactionId("lotr:rohan", 9000L);
        assertEquals(9000L, CharacterNbtCodec.readCharacterRecord(
                CharacterNbtCodec.writeCharacterRecord(pledged), OWNER)
                .getFactionSince());
        NBTTagCompound record = CharacterNbtCodec.writeCharacterRecord(character(""));
        record.setLong("FactionSince", -5L);
        assertEquals(1L, CharacterNbtCodec.readCharacterRecord(record, OWNER)
                .getFactionSince());
    }

    @Test
    public void aPledgeThatIsNoFactionIdIsCleared() {
        NBTTagCompound record = CharacterNbtCodec.writeCharacterRecord(
                character(""));
        record.setString("PledgedFactionId", "not a faction");
        RoleplayCharacter loaded = CharacterNbtCodec.readCharacterRecord(
                record, OWNER);
        assertEquals("", loaded.getPledgedFactionId());
        assertEquals("lotr:gondor", loaded.getFactionId());
    }

    @Test
    public void aRecordOfTheEarlierLayoutIsRefused() {
        NBTTagCompound record = CharacterNbtCodec.writeCharacterRecord(
                character(""));
        record.setInteger("DataVersion", RoleplayCharacter.CURRENT_DATA_VERSION - 1);
        record.setInteger("RoleplayLevel", 3);
        try {
            CharacterNbtCodec.readCharacterRecord(record, OWNER);
            fail("a record of another version is not read");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("unsupported"));
        }
    }

    @Test
    public void aStoreHoldingTheEarlierLayoutStaysReadOnly() {
        CharacterRoster roster = new CharacterRoster(OWNER);
        assertTrue(roster.addCharacter(character("")));
        NBTTagCompound root = new NBTTagCompound();
        CharacterNbtCodec.write(root, Collections.singletonList(roster), Collections.<NBTTagCompound>emptyList());
        NBTTagList rosters = root.getTagList("Rosters", 10);
        NBTTagCompound stored = rosters.getCompoundTagAt(0)
                .getTagList("Characters", 10).getCompoundTagAt(0);
        stored.setInteger("DataVersion", RoleplayCharacter.CURRENT_DATA_VERSION - 1);

        CharacterNbtCodec.ReadResult read = CharacterNbtCodec.read(root);
        assertTrue(read.isReadOnly());
        assertEquals(RoleplayCharacter.CURRENT_DATA_VERSION - 1,
                read.getUnsupportedVersion());
    }

    private static RoleplayCharacter character(String pledgedFactionId) {
        return RoleplayCharacter.builder(CHARACTER, OWNER)
                .slot(0).name("Aldric").race(CharacterRaceRegistry.HUMAN)
                .gender(CharacterGenderRegistry.MALE)
                .skin("losttales:human_gondor_male_0").age(30)
                .startingFaction("lotr:gondor")
                .pledgedFaction(pledgedFactionId)
                .createdAt(1L)
                .build();
    }
}
