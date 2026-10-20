package com.ninuna.losttales.character.storage;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterBody;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
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
 * A character record carries no level and no experience and keeps the
 * pledge LOTR last reported, the faction's start, the title, the arm width
 * and the chest as written. A record of another version or another shape
 * is refused whole; nothing is filled in or put right.
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

    /** When the faction took effect is kept; a record whose faction started before the character's making is refused. */
    @Test
    public void theFactionsStartRoundTrips() {
        RoleplayCharacter pledged = character("");
        pledged.setPledgedFactionId("lotr:rohan", 9000L);
        assertEquals(9000L, CharacterNbtCodec.readCharacterRecord(
                CharacterNbtCodec.writeCharacterRecord(pledged), OWNER)
                .getFactionSince());
        NBTTagCompound record = CharacterNbtCodec.writeCharacterRecord(character(""));
        record.setLong("FactionSince", -5L);
        assertRefused(record, "faction_before_making");
    }

    /** A faction never counts from before the character is: a figure claimed again counts from its claim. */
    @Test
    public void aFactionNeverStartsBeforeTheCharacter() {
        RoleplayCharacter claimed = RoleplayCharacter.builder(character(""))
                .factionSince(500L).createdAt(9000L).build();
        assertEquals(9000L, claimed.getFactionSince());
    }

    @Test
    public void aPledgeThatIsNoFactionIdIsRefused() {
        NBTTagCompound record = CharacterNbtCodec.writeCharacterRecord(
                character(""));
        record.setString("PledgedFactionId", "not a faction");
        assertRefused(record, "malformed_pledged_faction");
    }

    @Test
    public void theChosenArmWidthAndChestRoundTrip() {
        RoleplayCharacter chosen = RoleplayCharacter.builder(character(""))
                .bodyType(CharacterBodyTypeRegistry.SLIM)
                .chestType(CharacterChestTypeRegistry.CLASSIC).build();
        RoleplayCharacter loaded = CharacterNbtCodec.readCharacterRecord(
                CharacterNbtCodec.writeCharacterRecord(chosen), OWNER);
        assertEquals(CharacterBodyTypeRegistry.SLIM, loaded.getBodyTypeId());
        assertEquals(CharacterChestTypeRegistry.CLASSIC, loaded.getChestTypeId());
    }

    @Test
    public void theAccountSkinRoundTrips() {
        RoleplayCharacter account = RoleplayCharacter.builder(character(""))
                .skin(CharacterSkinRegistry.ACCOUNT_SKIN_ID).build();
        assertEquals(CharacterSkinRegistry.ACCOUNT_SKIN_ID,
                CharacterNbtCodec.readCharacterRecord(
                        CharacterNbtCodec.writeCharacterRecord(account), OWNER)
                        .getSkinId());
    }

    /** A record without an arm width or a chest, or with one this build does not know, is refused, never given the sex's. */
    @Test
    public void aMissingOrUnknownArmWidthOrChestIsRefused() {
        NBTTagCompound noBody = CharacterNbtCodec.writeCharacterRecord(character(""));
        noBody.removeTag("BodyTypeId");
        assertRefused(noBody, "missing_body_type");
        NBTTagCompound unknownBody = CharacterNbtCodec.writeCharacterRecord(character(""));
        unknownBody.setString("BodyTypeId", "losttales:huge");
        assertRefused(unknownBody, "unknown_body_type");
        NBTTagCompound noChest = CharacterNbtCodec.writeCharacterRecord(character(""));
        noChest.removeTag("ChestTypeId");
        assertRefused(noChest, "missing_chest_type");
        NBTTagCompound unknownChest = CharacterNbtCodec.writeCharacterRecord(character(""));
        unknownChest.setString("ChestTypeId", "losttales:huge");
        assertRefused(unknownChest, "unknown_chest_type");
    }

    /**
     * What a race offers is asked where a character is made or changed, not
     * of its record: races are data a server may change (CHAR-38). A sex the
     * game does not have, and a body none of the six is, are refused.
     */
    @Test
    public void aSexTheRaceNoLongerOffersIsKeptAndAnUnknownOneRefused() {
        NBTTagCompound record = CharacterNbtCodec.writeCharacterRecord(character(""));
        record.setString("GenderId", CharacterGenderRegistry.NON_BINARY);
        assertEquals(CharacterGenderRegistry.NON_BINARY,
                CharacterNbtCodec.readCharacterRecord(record, OWNER).getGenderId());

        NBTTagCompound unknown = CharacterNbtCodec.writeCharacterRecord(character(""));
        unknown.setString("GenderId", "losttales:shapeshifter");
        assertRefused(unknown, "unknown_sex");

        NBTTagCompound noBody = CharacterNbtCodec.writeCharacterRecord(character(""));
        noBody.setString("Body", "giant");
        assertRefused(noBody, "unknown_body");
    }

    /** The body a character's race stood on is kept, so it stands on it once the race is gone. */
    @Test
    public void theBodyRoundTrips() {
        RoleplayCharacter dwarf = RoleplayCharacter.builder(character(""))
                .race("myserver:longbeard").body(CharacterBody.DWARF).build();
        RoleplayCharacter read = CharacterNbtCodec.readCharacterRecord(
                CharacterNbtCodec.writeCharacterRecord(dwarf), OWNER);
        assertEquals("myserver:longbeard", read.getRaceId());
        assertEquals(CharacterBody.DWARF, read.getBody());
    }

    /** A cape is kept by its file's id, there or not (CHAR-39). */
    @Test
    public void aCapeRoundTripsByItsId() {
        RoleplayCharacter caped = character("");
        caped.setCapeSettings(false, "myserver:gone_cape");
        RoleplayCharacter read = CharacterNbtCodec.readCharacterRecord(
                CharacterNbtCodec.writeCharacterRecord(caped), OWNER);
        assertEquals("myserver:gone_cape", read.getCosmeticCapeId());
        assertFalse(read.isMinecraftCapeVisible());
    }

    private static void assertRefused(NBTTagCompound record, String reason) {
        try {
            CharacterNbtCodec.readCharacterRecord(record, OWNER);
            fail("a record not as written is refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains(reason));
        }
    }

    /** The title the character last wore shows while it is not played, so the record keeps it. */
    @Test
    public void theLotrTitleRoundTrips() {
        RoleplayCharacter titled = character("");
        assertTrue(titled.setLotrTitle("Gondor Farmer"));
        assertFalse(titled.setLotrTitle("Gondor Farmer"));
        assertEquals("Gondor Farmer", CharacterNbtCodec.readCharacterRecord(
                CharacterNbtCodec.writeCharacterRecord(titled), OWNER)
                .getLotrTitle());
        assertEquals("", CharacterNbtCodec.readCharacterRecord(
                CharacterNbtCodec.writeCharacterRecord(character("")), OWNER)
                .getLotrTitle());
    }

    /**
     * A title keeps its words only: no colour code, no control character,
     * no more than it may hold. A record holding more is refused.
     */
    @Test
    public void aTitleIsKeptAsWordsOnly() {
        RoleplayCharacter titled = character("");
        titled.setLotrTitle("§6Gondor§r Farmer\n");
        assertEquals("Gondor Farmer", titled.getLotrTitle());
        NBTTagCompound record = CharacterNbtCodec.writeCharacterRecord(
                character(""));
        record.setString("LotrTitle", "§6Gondor§r Farmer\n");
        assertRefused(record, "malformed_lotr_title");
        StringBuilder long_ = new StringBuilder();
        for (int index = 0; index < 100; index++) {
            long_.append('a');
        }
        assertEquals(RoleplayCharacter.MAX_LOTR_TITLE_LENGTH,
                RoleplayCharacter.normalizeTitle(long_.toString()).length());
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
