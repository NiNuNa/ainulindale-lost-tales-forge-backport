package com.ninuna.losttales.character.state;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The account's own saved state is a record keyed by the owner's UUID in
 * the same manifest as the characters' records; the file format does not
 * distinguish the two.
 */
public final class CharacterPlayerStateWorldDataTest {

    private static final UUID OWNER =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    public void anAccountRecordKeyedByTheOwnerRoundTrips() {
        CharacterPlayerStateWorldData data = new CharacterPlayerStateWorldData(
                CharacterPlayerStateWorldData.dataName(OWNER));
        CharacterPlayerStateAccount account = data.getOrCreateAccount(OWNER);
        LinkedHashMap<String, NBTTagCompound> components =
                new LinkedHashMap<String, NBTTagCompound>();
        NBTTagCompound inventory = new NBTTagCompound();
        inventory.setInteger("Version", 1);
        components.put("vanilla_inventory", inventory);
        CharacterPlayerStateSnapshot snapshot = new CharacterPlayerStateSnapshot(
                OWNER, 1L, 5L, CharacterPlayerStateSnapshot.CURRENT_DATA_VERSION,
                components);
        account.putRecord(new CharacterPlayerStateRecord(OWNER, snapshot, null));
        data.saveAccount(account);

        NBTTagCompound written = new NBTTagCompound();
        data.writeToNBT(written);
        CharacterPlayerStateWorldData loaded = new CharacterPlayerStateWorldData(
                CharacterPlayerStateWorldData.dataName(OWNER));
        loaded.readFromNBT(written);

        assertFalse(loaded.isReadOnlyForNewerVersion());
        assertEquals(CharacterPlayerStateWorldData.CURRENT_DATA_VERSION,
                written.getInteger("DataVersion"));
        CharacterPlayerStateRecord record = loaded.getAccount(OWNER).getRecord(OWNER);
        assertNotNull(record);
        assertEquals(1L, record.getCurrentGeneration());
        assertEquals(1, record.getCurrent().getComponent("vanilla_inventory")
                .getInteger("Version"));
    }

    @Test
    public void aNewerFileIsPreservedReadOnly() {
        NBTTagCompound newer = new NBTTagCompound();
        newer.setInteger("DataVersion",
                CharacterPlayerStateWorldData.CURRENT_DATA_VERSION + 1);
        newer.setString("Future", "kept");

        CharacterPlayerStateWorldData loaded = new CharacterPlayerStateWorldData(
                CharacterPlayerStateWorldData.dataName(OWNER));
        loaded.readFromNBT(newer);
        NBTTagCompound written = new NBTTagCompound();
        loaded.writeToNBT(written);

        assertTrue(loaded.isReadOnlyForNewerVersion());
        assertEquals(newer, written);
    }
}
