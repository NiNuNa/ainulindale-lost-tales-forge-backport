package com.ninuna.losttales.fellowship.server;

import java.util.UUID;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * After a declined or taken-back invitation, nobody of that fellowship may
 * invite that account again for a minute, whoever sent the first; another
 * fellowship may.
 */
public final class FellowshipDeclinesTest {
    private static final UUID GREY_COMPANY = UUID.fromString(
            "00000000-0000-0000-0000-0000000000a1");
    private static final UUID RANGERS = UUID.fromString(
            "00000000-0000-0000-0000-0000000000a2");
    private static final UUID TARGET = UUID.fromString(
            "00000000-0000-0000-0000-0000000000b1");

    @After
    public void cleanUp() {
        FellowshipDeclines.clear();
    }

    @Test
    public void theWholeFellowshipWaitsAfterADecline() {
        long now = 1000000L;
        assertFalse(FellowshipDeclines.mustWait(GREY_COMPANY, TARGET, now));
        FellowshipDeclines.startWait(GREY_COMPANY, TARGET, now);
        assertTrue("every guide of it waits",
                FellowshipDeclines.mustWait(GREY_COMPANY, TARGET, now + 1L));
        assertFalse("another fellowship does not",
                FellowshipDeclines.mustWait(RANGERS, TARGET, now + 1L));
        assertFalse("a minute later it may invite again",
                FellowshipDeclines.mustWait(GREY_COMPANY, TARGET,
                        now + FellowshipDeclines.WAIT_MILLIS));
    }
}
