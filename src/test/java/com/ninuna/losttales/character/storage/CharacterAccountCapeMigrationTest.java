package com.ninuna.losttales.character.storage;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The account's cape settings are two roster keys: a roster without them
 * wears the defaults, a chosen cape survives a round trip, and an unknown
 * cape id is repaired to none.
 */
public final class CharacterAccountCapeMigrationTest {

    private static final UUID OWNER = UUID.fromString(
            "92000000-0000-0000-0000-000000000029");

    @Test
    public void aRosterWithoutTheKeysWearsTheDefaults() {
        NBTTagCompound root = new NBTTagCompound();
        CharacterNbtCodec.write(root, Collections.singletonList(new CharacterRoster(OWNER)));
        NBTTagCompound roster = rosterTag(root);
        roster.removeTag("AccountShowMinecraftCape");
        roster.removeTag("AccountCosmeticCapeId");

        CharacterRoster read = readRoster(root);
        assertEquals(RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                read.isAccountMinecraftCapeVisible());
        assertEquals(RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID,
                read.getAccountCosmeticCapeId());
    }

    @Test
    public void aChosenAccountCapeSurvivesTheRoundTrip() {
        CharacterRoster roster = new CharacterRoster(OWNER);
        assertTrue(roster.setAccountCapeSettings(false, CharacterCapeCatalog.TOWER_GUARD));
        assertFalse(roster.setAccountCapeSettings(false, CharacterCapeCatalog.TOWER_GUARD));
        NBTTagCompound root = new NBTTagCompound();
        CharacterNbtCodec.write(root, Collections.singletonList(roster));

        CharacterRoster read = readRoster(root);
        assertFalse(read.isAccountMinecraftCapeVisible());
        assertEquals(CharacterCapeCatalog.TOWER_GUARD, read.getAccountCosmeticCapeId());
    }

    @Test
    public void anUnknownAccountCapeIsRepairedToNone() {
        NBTTagCompound root = new NBTTagCompound();
        CharacterNbtCodec.write(root, Collections.singletonList(new CharacterRoster(OWNER)));
        rosterTag(root).setInteger("AccountCosmeticCapeId", 60000);

        CharacterNbtCodec.ReadResult result = CharacterNbtCodec.read(root);
        assertTrue(result.wasRepaired());
        assertEquals(CharacterCapeCatalog.NONE_ID,
                result.getRosters().get(OWNER).getAccountCosmeticCapeId());
    }

    private static NBTTagCompound rosterTag(NBTTagCompound root) {
        NBTTagList rosters = root.getTagList("Rosters", 10);
        assertEquals(1, rosters.tagCount());
        return rosters.getCompoundTagAt(0);
    }

    private static CharacterRoster readRoster(NBTTagCompound root) {
        CharacterNbtCodec.ReadResult result = CharacterNbtCodec.read(root);
        assertFalse(result.isReadOnly());
        CharacterRoster roster = result.getRosters().get(OWNER);
        assertNotNull(roster);
        return roster;
    }
}
