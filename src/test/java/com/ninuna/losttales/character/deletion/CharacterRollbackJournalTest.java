package com.ninuna.losttales.character.deletion;

import com.ninuna.losttales.character.validation.CharacterErrorId;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** An operator's rollback waits while the account's switch journal is held. */
public final class CharacterRollbackJournalTest {

    @Test
    public void heldJournalRefusesTheRollback() {
        assertEquals(CharacterDeletionMaintenanceResult.JOURNAL_HELD,
                CharacterDeletionService.rollbackRefusal(
                        CharacterErrorId.SWITCH_JOURNAL_HELD));
    }

    @Test
    public void unreadableSwitchStoreRefusesAsReadOnly() {
        assertEquals(CharacterDeletionMaintenanceResult.STORAGE_READ_ONLY,
                CharacterDeletionService.rollbackRefusal(
                        CharacterErrorId.SWITCH_STORAGE_READ_ONLY));
    }

    @Test
    public void noJournalLetsTheRollbackGoOn() {
        assertEquals(CharacterDeletionMaintenanceResult.SUCCESS,
                CharacterDeletionService.rollbackRefusal(
                        CharacterErrorId.NONE));
    }
}
