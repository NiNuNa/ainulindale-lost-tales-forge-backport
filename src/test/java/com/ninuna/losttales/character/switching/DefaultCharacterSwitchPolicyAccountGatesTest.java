package com.ninuna.losttales.character.switching;

import com.ninuna.losttales.character.validation.CharacterErrorId;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/**
 * The two halves of the switch policy that are the account's own facts
 * rather than the player's: what refuses a switch before the live entity
 * is looked at, and the cooldown that is asked last. Everything between
 * them reads the live player and is not reachable without a running
 * server, so it is not covered here.
 */
public final class DefaultCharacterSwitchPolicyAccountGatesTest {

    private static final UUID OWNER =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID TARGET =
            UUID.fromString("30000000-0000-0000-0000-000000000003");

    @Test
    public void anAccountWithNothingAgainstItRefusesNothing() {
        assertNull(DefaultCharacterSwitchPolicy.accountRefusal(
                new CharacterSwitchAccountState(OWNER)));
    }

    @Test
    public void aFrozenAccountIsRefused() {
        CharacterSwitchAccountState account = new CharacterSwitchAccountState(OWNER);
        account.setFrozen(true);

        CharacterSwitchPolicyResult result =
                DefaultCharacterSwitchPolicy.accountRefusal(account);

        assertFalse(result.isAllowed());
        assertEquals(CharacterErrorId.SWITCH_ACCOUNT_FROZEN, result.getErrorId());
    }

    /**
     * A death that has not been settled refuses a switch, so a reconnect
     * cannot put a pre-death inventory back after the drops happened.
     */
    @Test
    public void anUnsettledDeathIsRefused() {
        CharacterSwitchAccountState account = new CharacterSwitchAccountState(OWNER);
        account.markDeathPending(1000L);

        CharacterSwitchPolicyResult result =
                DefaultCharacterSwitchPolicy.accountRefusal(account);

        assertFalse(result.isAllowed());
        assertEquals(CharacterErrorId.SWITCH_DEATH_PENDING, result.getErrorId());

        account.clearDeathPending();
        assertNull("settled, and the account stops refusing",
                DefaultCharacterSwitchPolicy.accountRefusal(account));
    }

    /**
     * A journal held for an operator refuses every further switch until
     * it is discarded. The other three statuses describe a switch that
     * either finished or was rolled back, and neither stands in the way.
     */
    @Test
    public void onlyAJournalAwaitingRecoveryRefusesASwitch() {
        assertEquals(CharacterErrorId.SWITCH_RECOVERY_REQUIRED,
                DefaultCharacterSwitchPolicy.accountRefusal(
                        accountHolding(CharacterSwitchTransactionStatus
                                .RECOVERY_REQUIRED)).getErrorId());
        assertNull(DefaultCharacterSwitchPolicy.accountRefusal(
                accountHolding(CharacterSwitchTransactionStatus.PREPARED)));
        assertNull(DefaultCharacterSwitchPolicy.accountRefusal(
                accountHolding(CharacterSwitchTransactionStatus.COMMITTED)));
        assertNull(DefaultCharacterSwitchPolicy.accountRefusal(
                accountHolding(CharacterSwitchTransactionStatus.ABORTED)));
    }

    /** Frozen is asked before the death, and both before the journal. */
    @Test
    public void theAccountsRefusalsAreAskedInOrder() {
        CharacterSwitchAccountState account = accountHolding(
                CharacterSwitchTransactionStatus.RECOVERY_REQUIRED);
        account.markDeathPending(1000L);
        account.setFrozen(true);

        assertEquals("frozen first", CharacterErrorId.SWITCH_ACCOUNT_FROZEN,
                DefaultCharacterSwitchPolicy.accountRefusal(account).getErrorId());

        account.setFrozen(false);
        assertEquals("then the death", CharacterErrorId.SWITCH_DEATH_PENDING,
                DefaultCharacterSwitchPolicy.accountRefusal(account).getErrorId());

        account.clearDeathPending();
        assertEquals("then the journal",
                CharacterErrorId.SWITCH_RECOVERY_REQUIRED,
                DefaultCharacterSwitchPolicy.accountRefusal(account).getErrorId());
    }

    /**
     * A cooldown refusal names the moment it may be tried again, so the
     * client can show a countdown rather than a bare refusal.
     */
    @Test
    public void aCooldownRefusalCarriesTheMomentItLifts() {
        CharacterSwitchAccountState account = new CharacterSwitchAccountState(
                OWNER, 1, 9000L, 0L, 0L, 0L, false, false, 0L, null);

        CharacterSwitchPolicyResult result =
                DefaultCharacterSwitchPolicy.cooldownRefusal(account, 5000L, false);

        assertFalse(result.isAllowed());
        assertEquals(CharacterErrorId.SWITCH_COOLDOWN, result.getErrorId());
        assertEquals(9000L, result.getRetryAtEpochMillis());
    }

    @Test
    public void aCooldownThatHasLiftedRefusesNothing() {
        CharacterSwitchAccountState account = new CharacterSwitchAccountState(
                OWNER, 1, 9000L, 0L, 0L, 0L, false, false, 0L, null);

        assertNull("exactly at the moment it lifts",
                DefaultCharacterSwitchPolicy.cooldownRefusal(account, 9000L, false));
        assertNull("and after it",
                DefaultCharacterSwitchPolicy.cooldownRefusal(account, 9001L, false));
    }

    /** An exempt player is never held by a cooldown, however long it has left. */
    @Test
    public void anExemptPlayerIsNotHeldByTheCooldown() {
        CharacterSwitchAccountState account = new CharacterSwitchAccountState(
                OWNER, 5, Long.MAX_VALUE, 0L, 0L, 0L, false, false, 0L, null);

        assertNull(DefaultCharacterSwitchPolicy.cooldownRefusal(
                account, 0L, true));
    }

    private static CharacterSwitchAccountState accountHolding(
            CharacterSwitchTransactionStatus status) {
        CharacterSwitchAccountState account = new CharacterSwitchAccountState(OWNER);
        account.setTransaction(new CharacterSwitchTransaction(
                UUID.fromString("50000000-0000-0000-0000-000000000005"),
                SOURCE, TARGET, 10L, 11L, 3000L, 7L, 8,
                2, 2000L, 1000L, 1000L, 1000L,
                3, 4000L, 3000L, 3000L, 3000L,
                20L, 21L, status, 0L));
        return account;
    }
}
