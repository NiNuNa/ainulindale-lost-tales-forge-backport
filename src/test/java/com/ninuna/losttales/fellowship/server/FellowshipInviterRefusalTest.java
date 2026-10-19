package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.util.EnglishWords;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Every refusal of an invitation says its own reason to whoever invited
 * (FEL-17): the refusals kept by account each have words of their own.
 */
public final class FellowshipInviterRefusalTest {

    private static final FellowshipErrorId[] BY_ACCOUNT = {
            FellowshipErrorId.ACCOUNT_ALREADY_IN_FELLOWSHIP,
            FellowshipErrorId.RECENTLY_DECLINED,
            FellowshipErrorId.TARGET_INVITATIONS_FULL
    };

    @Test
    public void eachRefusalKeptByAccountSaysItsOwnReason() {
        Set<String> words = new HashSet<String>();
        for (FellowshipErrorId refusal : BY_ACCOUNT) {
            words.add(EnglishWords.INSTANCE.format(
                    "gui.losttales.fellowship.error." + refusal.getId()));
        }
        assertEquals(BY_ACCOUNT.length, words.size());
    }
}
