package com.ninuna.losttales.chat.server;

import java.util.UUID;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** A player reports a message once, and five messages in any ten minutes. */
public final class ChatReportsTest {
    private static final UUID STEVE = new UUID(0L, 1L);
    private static final UUID ALEX = new UUID(0L, 2L);
    private static final long NOW = 1757522000000L;

    @After
    public void forget() {
        ChatReports.clear();
    }

    @Test
    public void aMessageIsReportedOncePerPlayer() {
        assertNull(ChatReports.admit(STEVE, 1001L, NOW));
        assertEquals("chat.losttales.report.already",
                ChatReports.admit(STEVE, 1001L, NOW + 1000L));
        assertNull("another player may report it too",
                ChatReports.admit(ALEX, 1001L, NOW + 1000L));
    }

    @Test
    public void fiveReportsStandInAnyTenMinutes() {
        for (int index = 0; index < ChatReports.MAX_PER_WINDOW; index++) {
            assertNull(ChatReports.admit(STEVE, 2000L + index, NOW + index));
        }
        assertEquals("chat.losttales.report.too_many",
                ChatReports.admit(STEVE, 2100L, NOW + 60000L));
        assertEquals("a refused report is not counted as reported",
                null, ChatReports.admit(STEVE, 2100L,
                        NOW + ChatReports.WINDOW_MILLIS));
    }

    @Test
    public void whatAPlayerReportedIsBounded() {
        long at = NOW;
        for (int index = 0; index <= ChatReports.MAX_REMEMBERED; index++) {
            at += ChatReports.WINDOW_MILLIS;
            assertNull(ChatReports.admit(STEVE, 3000L + index, at));
        }
        assertNull("the oldest is forgotten once too many are kept",
                ChatReports.admit(STEVE, 3000L, at + ChatReports.WINDOW_MILLIS));
    }
}
