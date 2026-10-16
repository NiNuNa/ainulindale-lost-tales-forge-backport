package com.ninuna.losttales.character.server;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Accounts show to everyone while Show Accounts is on, and to staff alone while it is off. */
public final class AccountVisibilityTest {

    @Test
    public void staffSeeAccountsWhateverTheOption() {
        assertTrue(AccountVisibility.decide(true, false));
        assertTrue(AccountVisibility.decide(true, true));
        assertTrue(AccountVisibility.decide(false, true));
        assertFalse(AccountVisibility.decide(false, false));
    }
}
