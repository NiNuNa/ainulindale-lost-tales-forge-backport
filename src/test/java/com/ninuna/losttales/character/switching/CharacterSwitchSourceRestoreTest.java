package com.ninuna.losttales.character.switching;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.state.CharacterPlayerStateAccount;
import com.ninuna.losttales.character.state.CharacterPlayerStateRecord;
import com.ninuna.losttales.character.state.CharacterPlayerStateSnapshot;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.storage.NbtTags;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * An operator's discard of a switch journal brings back the character the
 * switch was leaving, exactly as the generation the journal names for it,
 * and sets what the player held aside (CHAR-37). These are the decisions
 * the coordinator takes from the stores alone; putting the state on a
 * live player needs a running server and is not covered here.
 */
public final class CharacterSwitchSourceRestoreTest {

    private static final UUID OWNER =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID TARGET =
            UUID.fromString("30000000-0000-0000-0000-000000000003");

    /**
     * The whole decision path of a failed switch whose rollback failed too:
     * held for an operator, discarded, refused by nothing, put back on the
     * source, and the account free to switch again.
     */
    @Test
    public void aFailedRollbackDiscardedPutsTheSourceBackAndFreesTheAccount() {
        CharacterSwitchTransaction transaction = transaction(SOURCE,
                CharacterSwitchTransactionStatus.RECOVERY_REQUIRED);
        CharacterSwitchAccountState account = account(transaction);
        account.setFrozen(true);
        // The rollback failed after the roster had moved.
        CharacterRoster roster = roster(TARGET);
        long revision = roster.getRevision();

        CharacterSwitchSourceRestore.markForRestore(account, 6000L);
        assertEquals(CharacterSwitchTransactionStatus.RESTORE_SOURCE,
                transaction.getStatus());
        assertEquals("a held journal refuses a switch",
                CharacterErrorId.SWITCH_ACCOUNT_FROZEN,
                DefaultCharacterSwitchPolicy.accountRefusal(account).getErrorId());
        assertEquals("nothing settles it by itself",
                CharacterSwitchRecoveryReconciler.Action.HOLD,
                CharacterSwitchRecoveryReconciler.decide(TARGET, transaction));
        assertFalse("no generation is put back by the login's reconciliation",
                CharacterSwitchRecoveryReconciler.requiresLiveReconciliation(transaction));

        assertNull(CharacterSwitchSourceRestore.refusal(transaction, roster,
                states(SOURCE, 20L, 19L), true, true));
        assertTrue(CharacterSwitchSourceRestore.playSource(roster, transaction));
        CharacterSwitchSourceRestore.settle(account);

        assertEquals(SOURCE, roster.getActiveCharacterId());
        assertEquals(revision + 1L, roster.getRevision());
        assertNull(account.getTransaction());
        assertFalse(account.isFrozen());
        assertEquals("the cooldown as before the switch", 2, account.getCooldownStage());
        assertEquals(2000L, account.getNextAllowedAt());
        assertNull(DefaultCharacterSwitchPolicy.accountRefusal(account));
    }

    @Test
    public void aDiscardedJournalIsHeldAndFrozenWhateverItsStatus() {
        for (CharacterSwitchTransactionStatus status
                : CharacterSwitchTransactionStatus.values()) {
            CharacterSwitchTransaction transaction = transaction(SOURCE, status);
            CharacterSwitchAccountState account = account(transaction);

            CharacterSwitchSourceRestore.markForRestore(account, 6000L);

            assertEquals(status.getId(), CharacterSwitchTransactionStatus.RESTORE_SOURCE,
                    transaction.getStatus());
            assertTrue(status.getId(), transaction.isHeld());
            assertTrue(status.getId(), account.isFrozen());
            assertSame(transaction, account.getTransaction());
        }
    }

    @Test(expected = IllegalStateException.class)
    public void anAccountWithoutAJournalHasNothingToMark() {
        CharacterSwitchSourceRestore.markForRestore(account(null), 6000L);
    }

    @Test
    public void theGenerationBeforeTheCurrentOneStillComesBack() {
        assertNull(CharacterSwitchSourceRestore.refusal(
                transaction(SOURCE, CharacterSwitchTransactionStatus.RESTORE_SOURCE),
                roster(TARGET), states(SOURCE, 21L, 20L), true, true));
    }

    /** A first switch's journal names the ghost, whose record is keyed by the owner. */
    @Test
    public void theGhostOfAFirstSwitchComesBack() {
        CharacterSwitchTransaction transaction = transaction(null,
                CharacterSwitchTransactionStatus.RESTORE_SOURCE);
        CharacterSwitchAccountState account = account(transaction);
        CharacterRoster roster = roster(TARGET);

        assertNull(CharacterSwitchSourceRestore.refusal(transaction, roster,
                states(OWNER, 20L, 0L), true, true));
        assertTrue(CharacterSwitchSourceRestore.playSource(roster, transaction));
        CharacterSwitchSourceRestore.settle(account);

        assertNull("the ghost plays no character", roster.getActiveCharacterId());
        assertNull(account.getTransaction());
    }

    @Test
    public void aRosterAlreadyOnTheSourceStaysAtItsRevision() {
        CharacterSwitchTransaction transaction = transaction(SOURCE,
                CharacterSwitchTransactionStatus.RESTORE_SOURCE);
        CharacterRoster roster = roster(SOURCE);
        long revision = roster.getRevision();

        assertFalse(CharacterSwitchSourceRestore.playSource(roster, transaction));
        assertEquals(SOURCE, roster.getActiveCharacterId());
        assertEquals(revision, roster.getRevision());
    }

    @Test
    public void aSourceNoLongerInTheRosterIsRefused() {
        CharacterRoster roster = new CharacterRoster(OWNER);
        roster.addCharacter(character(TARGET, 1));
        assertEquals(CharacterSwitchSourceRestore.Refusal.SOURCE_GONE,
                CharacterSwitchSourceRestore.refusal(
                        transaction(SOURCE, CharacterSwitchTransactionStatus.RESTORE_SOURCE),
                        roster, states(SOURCE, 20L, 0L), true, true));
        assertEquals(CharacterSwitchSourceRestore.Refusal.SOURCE_GONE,
                CharacterSwitchSourceRestore.refusal(
                        transaction(SOURCE, CharacterSwitchTransactionStatus.RESTORE_SOURCE),
                        null, states(SOURCE, 20L, 0L), true, true));
    }

    @Test
    public void aGenerationNoLongerKeptIsRefused() {
        CharacterSwitchTransaction transaction = transaction(SOURCE,
                CharacterSwitchTransactionStatus.RESTORE_SOURCE);
        assertEquals("written over twice",
                CharacterSwitchSourceRestore.Refusal.STATE_GONE,
                CharacterSwitchSourceRestore.refusal(transaction, roster(TARGET),
                        states(SOURCE, 22L, 21L), true, true));
        assertEquals("no record at all",
                CharacterSwitchSourceRestore.Refusal.STATE_GONE,
                CharacterSwitchSourceRestore.refusal(transaction, roster(TARGET),
                        states(TARGET, 20L, 0L), true, true));
        assertEquals("no account in the store",
                CharacterSwitchSourceRestore.Refusal.STATE_GONE,
                CharacterSwitchSourceRestore.refusal(transaction, roster(TARGET),
                        null, true, true));
    }

    @Test
    public void storesThatCannotBeWrittenAndAFullQuarantineAreRefused() {
        CharacterSwitchTransaction transaction = transaction(SOURCE,
                CharacterSwitchTransactionStatus.RESTORE_SOURCE);
        assertEquals(CharacterSwitchSourceRestore.Refusal.STORE_UNAVAILABLE,
                CharacterSwitchSourceRestore.refusal(transaction, roster(TARGET),
                        states(SOURCE, 20L, 0L), false, true));
        assertEquals(CharacterSwitchSourceRestore.Refusal.QUARANTINE_FULL,
                CharacterSwitchSourceRestore.refusal(transaction, roster(TARGET),
                        states(SOURCE, 20L, 0L), true, false));
    }

    /** What is set aside names its journal and whom the roster played, with every part kept. */
    @Test
    public void theMixedStateIsKeptWithItsJournal() {
        CharacterSwitchTransaction transaction = transaction(SOURCE,
                CharacterSwitchTransactionStatus.RESTORE_SOURCE);
        NBTTagCompound components = new NBTTagCompound();
        NBTTagCompound inventory = new NBTTagCompound();
        inventory.setInteger("Version", 1);
        components.setTag("vanilla_inventory", inventory);

        NBTTagCompound kept = CharacterSwitchSourceRestore.setAside(
                transaction, TARGET, 7000L, components);
        inventory.setInteger("Version", 9);

        assertEquals(transaction.getTransactionId(),
                NbtTags.readUuid(kept, "TransactionUUID"));
        assertEquals(SOURCE, NbtTags.readUuid(kept, "SourceCharacterUUID"));
        assertEquals(TARGET, NbtTags.readUuid(kept, "TargetCharacterUUID"));
        assertEquals(TARGET, NbtTags.readUuid(kept, "PlayedCharacterUUID"));
        assertEquals(20L, kept.getLong("SourceStateGeneration"));
        assertEquals(7000L, kept.getLong("SetAsideAt"));
        assertEquals("a copy, not the live compound", 1, kept.getCompoundTag("Components")
                .getCompoundTag("vanilla_inventory").getInteger("Version"));
    }

    @Test
    public void theGhostIsLeftOutOfWhatIsSetAside() {
        NBTTagCompound kept = CharacterSwitchSourceRestore.setAside(
                transaction(null, CharacterSwitchTransactionStatus.RESTORE_SOURCE),
                null, 7000L, new NBTTagCompound());

        assertNull(NbtTags.readUuid(kept, "SourceCharacterUUID"));
        assertNull(NbtTags.readUuid(kept, "PlayedCharacterUUID"));
        assertFalse(kept.hasKey("BlockedOwnerUUIDMost"));
    }

    /** Failed once the player was touched: held for an operator again, never settled. */
    @Test
    public void aRestoreThatFailsPartWayIsHeldForAnOperatorAgain() {
        CharacterSwitchTransaction transaction = transaction(SOURCE,
                CharacterSwitchTransactionStatus.RESTORE_SOURCE);
        CharacterSwitchAccountState account = account(transaction);

        CharacterSwitchSourceRestore.holdAgain(account, 8000L);

        assertSame(transaction, account.getTransaction());
        assertEquals(CharacterSwitchTransactionStatus.RECOVERY_REQUIRED,
                transaction.getStatus());
        assertTrue(account.isFrozen());
        assertEquals(CharacterSwitchRecoveryReconciler.Action.HOLD,
                CharacterSwitchRecoveryReconciler.decide(SOURCE, transaction));
    }

    /**
     * While a journal is held, no character of the account is deleted or
     * released: the one it names has to be there when it is settled. A
     * journal that settles by itself stands in nobody's way.
     */
    @Test
    public void onlyAHeldJournalKeepsCharactersFromBeingRemoved() {
        for (CharacterSwitchTransactionStatus status
                : CharacterSwitchTransactionStatus.values()) {
            CharacterErrorId expected = status
                    == CharacterSwitchTransactionStatus.RECOVERY_REQUIRED
                    || status == CharacterSwitchTransactionStatus.RESTORE_SOURCE
                    ? CharacterErrorId.SWITCH_JOURNAL_HELD : CharacterErrorId.NONE;
            assertEquals(status.getId(), expected,
                    CharacterSwitchSourceRestore.characterRemovalRefusal(
                            false, false, account(transaction(SOURCE, status))));
        }
        assertEquals("no journal", CharacterErrorId.NONE,
                CharacterSwitchSourceRestore.characterRemovalRefusal(
                        false, false, account(null)));
        assertEquals("no switch state at all", CharacterErrorId.NONE,
                CharacterSwitchSourceRestore.characterRemovalRefusal(false, false, null));
    }

    /** A switch store that cannot say whether a journal is held refuses as well. */
    @Test
    public void aSwitchStoreThatCannotSayRefusesRemoval() {
        assertEquals(CharacterErrorId.SWITCH_STORAGE_READ_ONLY,
                CharacterSwitchSourceRestore.characterRemovalRefusal(true, false, null));
        assertEquals("the owner's entry quarantined",
                CharacterErrorId.SWITCH_JOURNAL_HELD,
                CharacterSwitchSourceRestore.characterRemovalRefusal(false, true, null));
    }

    private static CharacterSwitchTransaction transaction(
            UUID source, CharacterSwitchTransactionStatus status) {
        return CharacterSwitchRecoveryReconcilerTest.transaction(
                source, TARGET, 20L, status);
    }

    private static CharacterSwitchAccountState account(
            CharacterSwitchTransaction transaction) {
        return new CharacterSwitchAccountState(OWNER, 2, 2000L, 1000L, 1000L,
                1000L, false, false, 0L, transaction);
    }

    /** The owner's roster holding the source and the target, playing {@code played}. */
    private static CharacterRoster roster(UUID played) {
        CharacterRoster roster = new CharacterRoster(OWNER);
        roster.addCharacter(character(SOURCE, 0));
        roster.addCharacter(character(TARGET, 1));
        roster.setActiveCharacterId(played);
        return roster;
    }

    private static RoleplayCharacter character(UUID id, int slot) {
        return RoleplayCharacter.builder(id, OWNER)
                .slot(slot)
                .name("Restore " + slot)
                .race(CharacterRaceRegistry.HUMAN)
                .gender(CharacterGenderRegistry.MALE)
                .skin("losttales:human_bree_male_0")
                .build();
    }

    /** One record for {@code gameplayId}: its current generation and, above 0, the previous. */
    private static CharacterPlayerStateAccount states(UUID gameplayId,
                                                      long current, long previous) {
        CharacterPlayerStateRecord record = new CharacterPlayerStateRecord(gameplayId,
                snapshot(gameplayId, current),
                previous > 0L ? snapshot(gameplayId, previous) : null);
        return new CharacterPlayerStateAccount(OWNER,
                CharacterPlayerStateAccount.CURRENT_BOOTSTRAP_VERSION, 1L,
                Arrays.asList(record));
    }

    private static CharacterPlayerStateSnapshot snapshot(UUID gameplayId, long generation) {
        return new CharacterPlayerStateSnapshot(gameplayId, generation, 5L,
                CharacterPlayerStateSnapshot.CURRENT_DATA_VERSION,
                new LinkedHashMap<String, NBTTagCompound>());
    }
}
