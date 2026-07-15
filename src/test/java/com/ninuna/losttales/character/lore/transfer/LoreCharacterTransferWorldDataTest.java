package com.ninuna.losttales.character.lore.transfer;

import com.ninuna.losttales.character.model.CharacterProgression;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.state.CharacterPlayerStateRecord;
import com.ninuna.losttales.character.state.CharacterPlayerStateSnapshot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class LoreCharacterTransferWorldDataTest {

    private static final String GANDALF = "losttales:gandalf";
    private static final UUID OWNER = UUID.fromString(
            "11000000-0000-0000-0000-000000000011");
    private static final UUID CHARACTER = UUID.fromString(
            "22000000-0000-0000-0000-000000000022");

    @Test
    public void vaultAndPendingClaimRoundTripWithoutLosingState() {
        LoreCharacterTransferWorldData source = data();
        source.saveVault(vault());
        LoreCharacterTransferRecord transaction = transaction();
        source.begin(transaction);
        source.advance(GANDALF, transaction.getTransactionId(), 0);

        NBTTagCompound serialized = new NBTTagCompound();
        source.writeToNBT(serialized);
        LoreCharacterTransferWorldData restored = data();
        restored.readFromNBT(serialized);

        assertFalse(restored.isReadOnly());
        LoreCharacterVaultEntry entry = restored.getVaultEntry(GANDALF);
        assertNotNull(entry);
        assertEquals(CHARACTER, entry.getCharacterId());
        assertEquals(7L, entry.getPlayerStateCopy().getCurrentGeneration());
        assertEquals(1, restored.getTransaction(GANDALF).getStep());
    }

    @Test
    public void completedTransferRemovesJournalButRetainsVault() {
        LoreCharacterTransferWorldData data = data();
        data.saveVault(vault());
        LoreCharacterTransferRecord transaction = transaction();
        data.begin(transaction);
        data.advance(GANDALF, transaction.getTransactionId(), 0);
        data.advance(GANDALF, transaction.getTransactionId(), 1);
        data.advance(GANDALF, transaction.getTransactionId(), 2);
        data.complete(GANDALF, transaction.getTransactionId());

        assertNotNull(data.getVaultEntry(GANDALF));
        assertTrue(data.getTransactions().isEmpty());
    }

    @Test(expected = IllegalStateException.class)
    public void staleTransferStepCannotAdvance() {
        LoreCharacterTransferWorldData data = data();
        data.saveVault(vault());
        LoreCharacterTransferRecord transaction = transaction();
        data.begin(transaction);
        data.advance(GANDALF, transaction.getTransactionId(), 1);
    }

    @Test
    public void duplicateVaultIdentityMakesStoreReadOnlyAndPreservesInput() {
        LoreCharacterTransferWorldData source = data();
        source.saveVault(vault());
        NBTTagCompound serialized = new NBTTagCompound();
        source.writeToNBT(serialized);
        NBTTagList entries = serialized.getTagList(
                "Vault", Constants.NBT.TAG_COMPOUND);
        entries.appendTag(entries.getCompoundTagAt(0).copy());

        LoreCharacterTransferWorldData restored = data();
        restored.readFromNBT(serialized);
        assertTrue(restored.isReadOnly());

        NBTTagCompound preserved = new NBTTagCompound();
        restored.writeToNBT(preserved);
        assertEquals(2, preserved.getTagList(
                "Vault", Constants.NBT.TAG_COMPOUND).tagCount());
    }

    private static LoreCharacterTransferWorldData data() {
        return new LoreCharacterTransferWorldData("test_lore_transfers");
    }

    private static LoreCharacterVaultEntry vault() {
        return new LoreCharacterVaultEntry(
                GANDALF, OWNER, character(), state(), 100L);
    }

    private static LoreCharacterTransferRecord transaction() {
        return new LoreCharacterTransferRecord(
                UUID.fromString("33000000-0000-0000-0000-000000000033"),
                LoreCharacterTransferRecord.Type.CLAIM,
                GANDALF, CHARACTER, null, OWNER, 0, 0L, 0, 100L);
    }

    private static RoleplayCharacter character() {
        String skin = CharacterSkinRegistry.getCompatibleSkins(
                CharacterRaceRegistry.HUMAN,
                CharacterGenderRegistry.MALE).get(0).getId();
        return new RoleplayCharacter(
                CHARACTER, OWNER, 0, "Gandalf",
                CharacterRaceRegistry.HUMAN,
                CharacterGenderRegistry.MALE,
                skin, 18, "lotr:bree", 1,
                new CharacterProgression(), 1L,
                RoleplayCharacter.CURRENT_DATA_VERSION,
                true, 0, "", false,
                "A wandering wizard.");
    }

    private static CharacterPlayerStateRecord state() {
        CharacterPlayerStateSnapshot current =
                new CharacterPlayerStateSnapshot(
                        CHARACTER, 7L, 100L,
                        CharacterPlayerStateSnapshot.CURRENT_DATA_VERSION,
                        Collections.<String, NBTTagCompound>emptyMap());
        return new CharacterPlayerStateRecord(CHARACTER, current, null);
    }
}
