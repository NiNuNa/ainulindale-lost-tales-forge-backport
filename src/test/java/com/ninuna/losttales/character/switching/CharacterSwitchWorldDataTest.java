package com.ninuna.losttales.character.switching;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The switch journal names its two identities by character id, and the
 * account is the absence of one on either side. It always names the
 * player-state generation of each side; one without them is malformed
 * and quarantined. A file, an account or a journal at any other version
 * than this build writes is kept as it is and the store goes read-only.
 */
public final class CharacterSwitchWorldDataTest {

    private static final UUID OWNER =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID TARGET =
            UUID.fromString("30000000-0000-0000-0000-000000000003");

    @Test
    public void aJournalWhoseTargetIsTheAccountRoundTrips() {
        CharacterSwitchWorldData data = new CharacterSwitchWorldData(
                CharacterSwitchWorldData.DATA_NAME);
        CharacterSwitchAccountState state = data.getOrCreateAccount(OWNER);
        state.setTransaction(CharacterSwitchRecoveryReconcilerTest.transaction(
                SOURCE, null, 20L, CharacterSwitchTransactionStatus.PREPARED));
        data.saveAccount(state);

        CharacterSwitchWorldData loaded = reload(data);

        assertFalse(loaded.isReadOnlyForNewerVersion());
        assertFalse(loaded.isOwnerBlocked(OWNER));
        CharacterSwitchTransaction transaction =
                loaded.getAccount(OWNER).getTransaction();
        assertNotNull(transaction);
        assertEquals(SOURCE, transaction.getSourceCharacterId());
        assertNull(transaction.getTargetCharacterId());
        assertEquals(20L, transaction.getSourceStateGeneration());
        assertEquals(21L, transaction.getTargetStateGeneration());
        assertEquals(CharacterSwitchTransactionStatus.PREPARED,
                transaction.getStatus());
    }

    @Test
    public void aJournalWhoseSourceIsTheAccountRoundTrips() {
        CharacterSwitchWorldData data = new CharacterSwitchWorldData(
                CharacterSwitchWorldData.DATA_NAME);
        CharacterSwitchAccountState state = data.getOrCreateAccount(OWNER);
        state.setTransaction(CharacterSwitchRecoveryReconcilerTest.transaction(
                null, TARGET, 20L, CharacterSwitchTransactionStatus.COMMITTED));
        data.saveAccount(state);

        CharacterSwitchTransaction transaction =
                reload(data).getAccount(OWNER).getTransaction();

        assertNull(transaction.getSourceCharacterId());
        assertEquals(TARGET, transaction.getTargetCharacterId());
        assertEquals(20L, transaction.getSourceStateGeneration());
        assertEquals(21L, transaction.getTargetStateGeneration());
    }

    @Test
    public void aJournalWithoutItsGenerationsIsQuarantinedAndBlocksTheOwner() {
        NBTTagCompound written = savedJournal();
        journalOf(written).removeTag("SourceStateGeneration");

        CharacterSwitchWorldData loaded = new CharacterSwitchWorldData(
                CharacterSwitchWorldData.DATA_NAME);
        loaded.readFromNBT(written);

        assertFalse(loaded.isReadOnlyForNewerVersion());
        assertNull(loaded.getAccount(OWNER));
        assertTrue(loaded.isOwnerBlocked(OWNER));
        assertEquals(1, loaded.getQuarantinedEntryCount());
    }

    @Test
    public void anOlderJournalIsPreservedReadOnly() {
        NBTTagCompound written = savedJournal();
        journalOf(written).setInteger("DataVersion",
                CharacterSwitchTransaction.CURRENT_DATA_VERSION - 1);
        assertPreservedReadOnly(written);
    }

    @Test
    public void anOlderAccountIsPreservedReadOnly() {
        NBTTagCompound written = savedJournal();
        written.getTagList("Accounts", Constants.NBT.TAG_COMPOUND)
                .getCompoundTagAt(0).setInteger("DataVersion",
                        CharacterSwitchAccountState.CURRENT_DATA_VERSION - 1);
        assertPreservedReadOnly(written);
    }

    @Test
    public void anUnversionedFileIsPreservedReadOnly() {
        NBTTagCompound written = savedJournal();
        written.removeTag("DataVersion");
        assertPreservedReadOnly(written);
    }

    @Test
    public void aNewerFileIsPreservedReadOnly() {
        NBTTagCompound newer = new NBTTagCompound();
        newer.setInteger("DataVersion", CharacterSwitchWorldData.CURRENT_DATA_VERSION + 1);
        newer.setString("Future", "kept");

        CharacterSwitchWorldData loaded = new CharacterSwitchWorldData(
                CharacterSwitchWorldData.DATA_NAME);
        loaded.readFromNBT(newer);
        NBTTagCompound written = new NBTTagCompound();
        loaded.writeToNBT(written);

        assertTrue(loaded.isReadOnlyForNewerVersion());
        assertEquals(newer, written);
    }

    /** A file holding one account with a journal from a character to the account. */
    private static NBTTagCompound savedJournal() {
        CharacterSwitchWorldData data = new CharacterSwitchWorldData(
                CharacterSwitchWorldData.DATA_NAME);
        CharacterSwitchAccountState state = data.getOrCreateAccount(OWNER);
        state.setTransaction(CharacterSwitchRecoveryReconcilerTest.transaction(
                SOURCE, null, 20L, CharacterSwitchTransactionStatus.PREPARED));
        data.saveAccount(state);
        NBTTagCompound written = new NBTTagCompound();
        data.writeToNBT(written);
        return written;
    }

    private static NBTTagCompound journalOf(NBTTagCompound written) {
        NBTTagList accounts = written.getTagList("Accounts", Constants.NBT.TAG_COMPOUND);
        return accounts.getCompoundTagAt(0).getCompoundTag("Transaction");
    }

    private static void assertPreservedReadOnly(NBTTagCompound saved) {
        NBTTagCompound expected = (NBTTagCompound) saved.copy();
        CharacterSwitchWorldData loaded = new CharacterSwitchWorldData(
                CharacterSwitchWorldData.DATA_NAME);
        loaded.readFromNBT(saved);
        NBTTagCompound rewritten = new NBTTagCompound();
        loaded.writeToNBT(rewritten);

        assertTrue(loaded.isReadOnlyForNewerVersion());
        assertNull(loaded.getAccount(OWNER));
        assertEquals(expected, rewritten);
    }

    private static CharacterSwitchWorldData reload(CharacterSwitchWorldData data) {
        NBTTagCompound written = new NBTTagCompound();
        data.writeToNBT(written);
        CharacterSwitchWorldData loaded = new CharacterSwitchWorldData(
                CharacterSwitchWorldData.DATA_NAME);
        loaded.readFromNBT(written);
        return loaded;
    }
}
