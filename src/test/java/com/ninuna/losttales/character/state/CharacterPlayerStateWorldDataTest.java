package com.ninuna.losttales.character.state;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The account's own saved state is a record keyed by the owner's UUID in
 * the same manifest as the characters' records; the file format does not
 * distinguish the two. Only this build's versions are read: a file, an
 * account, a record or a snapshot at any other is kept as it is and the
 * store goes read-only.
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
    public void anOlderFileIsPreservedReadOnly() {
        NBTTagCompound saved = savedAccount();
        saved.setInteger("DataVersion",
                CharacterPlayerStateWorldData.CURRENT_DATA_VERSION - 1);
        assertPreservedReadOnly(saved);
    }

    @Test
    public void anOlderSnapshotHoldsTheWholeStoreReadOnly() {
        NBTTagCompound saved = savedAccount();
        firstRecord(saved).getCompoundTag("Current").setInteger("DataVersion",
                CharacterPlayerStateSnapshot.CURRENT_DATA_VERSION - 1);
        assertPreservedReadOnly(saved);
    }

    @Test
    public void anOlderBootstrapVersionHoldsTheWholeStoreReadOnly() {
        NBTTagCompound saved = savedAccount();
        saved.getTagList("Accounts", Constants.NBT.TAG_COMPOUND)
                .getCompoundTagAt(0).setInteger("BootstrapVersion",
                        CharacterPlayerStateAccount.CURRENT_BOOTSTRAP_VERSION - 1);
        assertPreservedReadOnly(saved);
    }

    @Test
    public void aBootstrappedAccountRoundTrips() {
        NBTTagCompound saved = savedAccount();
        saved.getTagList("Accounts", Constants.NBT.TAG_COMPOUND)
                .getCompoundTagAt(0).setInteger("BootstrapVersion",
                        CharacterPlayerStateAccount.CURRENT_BOOTSTRAP_VERSION);
        CharacterPlayerStateWorldData loaded = new CharacterPlayerStateWorldData(
                CharacterPlayerStateWorldData.dataName(OWNER));
        loaded.readFromNBT(saved);

        assertFalse(loaded.isReadOnlyForNewerVersion());
        assertEquals(CharacterPlayerStateAccount.CURRENT_BOOTSTRAP_VERSION,
                loaded.getAccount(OWNER).getBootstrapVersion());
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

    /** A file holding the owner's account with one record, as this build writes it. */
    private static NBTTagCompound savedAccount() {
        CharacterPlayerStateWorldData data = new CharacterPlayerStateWorldData(
                CharacterPlayerStateWorldData.dataName(OWNER));
        CharacterPlayerStateAccount account = data.getOrCreateAccount(OWNER);
        LinkedHashMap<String, NBTTagCompound> components =
                new LinkedHashMap<String, NBTTagCompound>();
        NBTTagCompound inventory = new NBTTagCompound();
        inventory.setInteger("Version", 1);
        components.put("vanilla_inventory", inventory);
        account.putRecord(new CharacterPlayerStateRecord(OWNER,
                new CharacterPlayerStateSnapshot(OWNER, 1L, 5L,
                        CharacterPlayerStateSnapshot.CURRENT_DATA_VERSION,
                        components), null));
        data.saveAccount(account);
        NBTTagCompound written = new NBTTagCompound();
        data.writeToNBT(written);
        return written;
    }

    private static NBTTagCompound firstRecord(NBTTagCompound saved) {
        return saved.getTagList("Accounts", Constants.NBT.TAG_COMPOUND)
                .getCompoundTagAt(0).getTagList("Records",
                        Constants.NBT.TAG_COMPOUND).getCompoundTagAt(0);
    }

    private static void assertPreservedReadOnly(NBTTagCompound saved) {
        NBTTagCompound expected = (NBTTagCompound) saved.copy();
        CharacterPlayerStateWorldData loaded = new CharacterPlayerStateWorldData(
                CharacterPlayerStateWorldData.dataName(OWNER));
        loaded.readFromNBT(saved);
        NBTTagCompound written = new NBTTagCompound();
        loaded.writeToNBT(written);

        assertTrue(loaded.isReadOnlyForNewerVersion());
        assertNull(loaded.getAccount(OWNER));
        assertEquals(expected, written);
    }
}
