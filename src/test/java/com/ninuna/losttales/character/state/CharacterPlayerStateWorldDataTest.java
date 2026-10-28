package com.ninuna.losttales.character.state;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
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

    /**
     * A player's state set aside when an operator put their character back
     * is kept as it was through a save and a load, beside the account, and
     * blocks nobody (CHAR-37).
     */
    @Test
    public void aStateSetAsideIsKeptAndBlocksNobody() {
        CharacterPlayerStateWorldData data = new CharacterPlayerStateWorldData(
                CharacterPlayerStateWorldData.dataName(OWNER));
        data.readFromNBT(savedAccount());
        NBTTagCompound held = new NBTTagCompound();
        held.setLong("SetAsideAt", 7000L);
        held.setTag("Components", new NBTTagCompound());
        held.getCompoundTag("Components").setTag("vanilla_inventory", new NBTTagCompound());

        assertTrue(data.hasQuarantineRoomFor(1));
        data.setAside(CharacterPlayerStateWorldData.REASON_SWITCH_MIXED_STATE, held);
        held.setLong("SetAsideAt", 9L);
        NBTTagCompound written = new NBTTagCompound();
        data.writeToNBT(written);
        CharacterPlayerStateWorldData loaded = new CharacterPlayerStateWorldData(
                CharacterPlayerStateWorldData.dataName(OWNER));
        loaded.readFromNBT(written);

        assertFalse(loaded.isReadOnlyForNewerVersion());
        assertFalse(loaded.isOwnerBlocked(OWNER));
        assertNotNull(loaded.getAccount(OWNER));
        assertEquals(1, loaded.getQuarantinedEntryCount());
        NBTTagCompound entry = loaded.getQuarantinedEntries().get(0);
        assertEquals(CharacterPlayerStateWorldData.REASON_SWITCH_MIXED_STATE,
                entry.getString("Reason"));
        assertEquals("kept as it was set aside", 7000L,
                entry.getCompoundTag("OriginalData").getLong("SetAsideAt"));
        assertTrue(entry.getCompoundTag("OriginalData").getCompoundTag("Components")
                .hasKey("vanilla_inventory"));
    }

    /**
     * With no room left the store closes, read-only, and still writes all
     * it holds: nothing leaves it without being kept.
     */
    @Test
    public void aFullQuarantineClosesTheStoreAndKeepsEverything() {
        CharacterPlayerStateWorldData data = new CharacterPlayerStateWorldData(
                CharacterPlayerStateWorldData.dataName(OWNER));
        data.readFromNBT(savedAccount());
        for (int index = 0; index < CharacterPlayerStateWorldData.MAX_QUARANTINED; index++) {
            assertTrue(data.hasQuarantineRoomFor(1));
            data.setAside(CharacterPlayerStateWorldData.REASON_SWITCH_MIXED_STATE,
                    new NBTTagCompound());
        }

        assertFalse(data.hasQuarantineRoomFor(1));
        assertTrue(data.isReadOnlyForNewerVersion());
        NBTTagCompound written = new NBTTagCompound();
        data.writeToNBT(written);
        assertEquals(CharacterPlayerStateWorldData.MAX_QUARANTINED,
                written.getTagList("Quarantine", Constants.NBT.TAG_COMPOUND).tagCount());
        assertEquals(1, written.getTagList("Accounts", Constants.NBT.TAG_COMPOUND)
                .tagCount());
    }

    @Test(expected = IllegalStateException.class)
    public void aClosedStoreSetsNothingAside() {
        CharacterPlayerStateWorldData data = new CharacterPlayerStateWorldData(
                CharacterPlayerStateWorldData.dataName(OWNER));
        for (int index = 0; index < CharacterPlayerStateWorldData.MAX_QUARANTINED; index++) {
            data.setAside(CharacterPlayerStateWorldData.REASON_SWITCH_MIXED_STATE,
                    new NBTTagCompound());
        }
        data.setAside(CharacterPlayerStateWorldData.REASON_SWITCH_MIXED_STATE,
                new NBTTagCompound());
    }

    /** A file quarantining past the bound is kept as it is and read-only. */
    @Test
    public void aQuarantinePastItsBoundIsPreservedReadOnly() {
        NBTTagCompound saved = savedAccount();
        NBTTagList quarantine = new NBTTagList();
        for (int index = 0; index <= CharacterPlayerStateWorldData.MAX_QUARANTINED; index++) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("Reason", CharacterPlayerStateWorldData.REASON_SWITCH_MIXED_STATE);
            entry.setTag("OriginalData", new NBTTagCompound());
            quarantine.appendTag(entry);
        }
        saved.setTag("Quarantine", quarantine);
        assertPreservedReadOnly(saved);
    }

    /**
     * A stack of an item the game no longer has leaves the saved life as
     * it is read and is kept in the quarantine, one entry for the read,
     * blocking nobody; the player is told of it once (CHAR-46). No item is
     * in the game while the tests run, so any stack is one.
     */
    @Test
    public void aStackTheGameNoLongerHasIsSetAsideAsTheLifeIsRead() {
        NBTTagCompound item = new NBTTagCompound();
        item.setByte("Slot", (byte)0);
        item.setShort("id", (short)31000);
        item.setByte("Count", (byte)3);
        CharacterPlayerStateWorldData loaded = new CharacterPlayerStateWorldData(
                CharacterPlayerStateWorldData.dataName(OWNER));
        loaded.readFromNBT(savedAccountHolding(item));

        assertFalse(loaded.isReadOnlyForNewerVersion());
        assertFalse(loaded.isOwnerBlocked(OWNER));
        assertEquals(0, loaded.getAccount(OWNER).getRecord(OWNER).getCurrent()
                .getComponent("vanilla_inventory")
                .getTagList("Items", Constants.NBT.TAG_COMPOUND).tagCount());
        assertEquals(1, loaded.getQuarantinedEntryCount());
        NBTTagCompound entry = loaded.getQuarantinedEntries().get(0);
        assertEquals(CharacterPlayerStateWorldData.REASON_ITEMS_SET_ASIDE,
                entry.getString("Reason"));
        assertFalse(entry.getBoolean("Told"));

        java.util.List<NBTTagList> untold = loaded.takeUntoldSetAside();
        assertEquals(1, untold.size());
        assertEquals(1, untold.get(0).tagCount());
        NBTTagCompound kept = untold.get(0).getCompoundTagAt(0);
        assertEquals(SavedLifeItems.WHY_UNKNOWN, kept.getString(SavedLifeItems.TAG_WHY));
        assertEquals(3, kept.getCompoundTag(SavedLifeItems.TAG_ITEM).getByte("Count"));
        assertTrue("told once", loaded.takeUntoldSetAside().isEmpty());

        NBTTagCompound written = new NBTTagCompound();
        loaded.writeToNBT(written);
        CharacterPlayerStateWorldData again = new CharacterPlayerStateWorldData(
                CharacterPlayerStateWorldData.dataName(OWNER));
        again.readFromNBT(written);
        assertEquals("nothing more is set aside", 1, again.getQuarantinedEntryCount());
        assertTrue(again.takeUntoldSetAside().isEmpty());
    }

    /** A file holding the owner's account with one record whose inventory holds {@code item}. */
    private static NBTTagCompound savedAccountHolding(NBTTagCompound item) {
        NBTTagCompound saved = savedAccount();
        NBTTagCompound inventory = firstRecord(saved).getCompoundTag("Current")
                .getCompoundTag("Components").getCompoundTag("vanilla_inventory");
        NBTTagList items = new NBTTagList();
        items.appendTag(item);
        inventory.setTag("Items", items);
        return saved;
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
