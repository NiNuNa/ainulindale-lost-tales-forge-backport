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
 * account is the absence of one on either side. A version-3 journal may
 * leave the target out; older journals always named a character, so one
 * without a target is still malformed and quarantined.
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
        assertTrue(transaction.hasPlayerStateGenerations());
    }

    @Test
    public void anOlderJournalWithoutATargetIsQuarantinedAndBlocksTheOwner() {
        CharacterSwitchWorldData data = new CharacterSwitchWorldData(
                CharacterSwitchWorldData.DATA_NAME);
        CharacterSwitchAccountState state = data.getOrCreateAccount(OWNER);
        state.setTransaction(CharacterSwitchRecoveryReconcilerTest.transaction(
                SOURCE, null, 20L, CharacterSwitchTransactionStatus.PREPARED));
        data.saveAccount(state);
        NBTTagCompound written = new NBTTagCompound();
        data.writeToNBT(written);
        NBTTagList accounts = written.getTagList("Accounts", Constants.NBT.TAG_COMPOUND);
        accounts.getCompoundTagAt(0).getCompoundTag("Transaction")
                .setInteger("DataVersion", 2);

        CharacterSwitchWorldData loaded = new CharacterSwitchWorldData(
                CharacterSwitchWorldData.DATA_NAME);
        loaded.readFromNBT(written);

        assertFalse(loaded.isReadOnlyForNewerVersion());
        assertNull(loaded.getAccount(OWNER));
        assertTrue(loaded.isOwnerBlocked(OWNER));
        assertEquals(1, loaded.getQuarantinedEntryCount());
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

    private static CharacterSwitchWorldData reload(CharacterSwitchWorldData data) {
        NBTTagCompound written = new NBTTagCompound();
        data.writeToNBT(written);
        CharacterSwitchWorldData loaded = new CharacterSwitchWorldData(
                CharacterSwitchWorldData.DATA_NAME);
        loaded.readFromNBT(written);
        return loaded;
    }
}
