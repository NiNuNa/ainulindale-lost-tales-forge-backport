package com.ninuna.losttales.chat.moderation;

import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ChatAuditLogTest {

    private static final UUID ACCOUNT =
            UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID CHARACTER =
            UUID.fromString("00000000-0000-0000-0000-00000000000c");
    /** 2026-09-01T12:00:00Z. */
    private static final long NOON = 1788264000000L;
    private static final long DAY = 24L * 60L * 60L * 1000L;

    @Test
    public void recordsCarryEveryKnownField() {
        String line = ChatAuditLog.buildLine(NOON, "message", 42L,
                ACCOUNT, "Nils", CHARACTER, "Aldric", "proximity",
                "", "hail and well met");
        assertEquals("{\"at\":\"2026-09-01T12:00:00Z\""
                + ",\"event\":\"message\",\"messageId\":42"
                + ",\"account\":\"" + ACCOUNT + "\""
                + ",\"accountName\":\"Nils\""
                + ",\"characterId\":\"" + CHARACTER + "\""
                + ",\"identity\":\"Aldric\""
                + ",\"channel\":\"proximity\""
                + ",\"text\":\"hail and well met\"}", line);
        // An account line drops the empty character and identity fields;
        // a whisper names its target.
        assertEquals("{\"at\":\"2026-09-01T12:00:00Z\""
                + ",\"event\":\"message\",\"messageId\":43"
                + ",\"account\":\"" + ACCOUNT + "\""
                + ",\"accountName\":\"Nils\""
                + ",\"channel\":\"whisper\""
                + ",\"target\":\"Beren\""
                + ",\"text\":\"psst\"}",
                ChatAuditLog.buildLine(NOON, "message", 43L, ACCOUNT,
                        "Nils", null, "", "whisper", "Beren", "psst"));
        assertEquals("{\"at\":\"2026-09-01T12:00:00Z\""
                + ",\"event\":\"delete\",\"messageId\":42"
                + ",\"account\":\"" + ACCOUNT + "\""
                + ",\"accountName\":\"Nils\""
                + ",\"text\":\"\"}",
                ChatAuditLog.buildLine(NOON, "delete", 42L, ACCOUNT,
                        "Nils", null, "", "", "", ""));
    }

    @Test
    public void textIsEscapedSoALineStaysOneLine() {
        assertEquals("say \\\"hi\\\" \\\\o\\\\ then\\nrun",
                ChatAuditLog.jsonEscape("say \"hi\" \\o\\ then\nrun"));
        assertEquals("tab\\tbell\\u0007", ChatAuditLog.jsonEscape(
                "tab\tbell\u0007"));
        assertEquals("", ChatAuditLog.jsonEscape(null));
    }

    @Test
    public void filesRotateByUtcDayAndExpireByRetention() {
        assertEquals("chat-2026-09-01.jsonl", ChatAuditLog.fileNameFor(NOON));
        // A file is expired once its day lies past the retention window.
        assertTrue(ChatAuditLog.isExpiredFileName("chat-2026-08-01.jsonl",
                NOON, 30));
        assertFalse(ChatAuditLog.isExpiredFileName("chat-2026-08-15.jsonl",
                NOON, 30));
        assertFalse(ChatAuditLog.isExpiredFileName(
                ChatAuditLog.fileNameFor(NOON), NOON, 1));
        assertTrue(ChatAuditLog.isExpiredFileName("chat-2026-08-30.jsonl",
                NOON + 2L * DAY, 1));
        // Files that are not the log's own are never touched, and a
        // retention below the floor still keeps a day.
        assertFalse(ChatAuditLog.isExpiredFileName("latest.log", NOON, 30));
        assertFalse(ChatAuditLog.isExpiredFileName("chat-notadate.jsonl",
                NOON, 30));
        assertFalse(ChatAuditLog.isExpiredFileName(null, NOON, 30));
        assertFalse(ChatAuditLog.isExpiredFileName(
                ChatAuditLog.fileNameFor(NOON), NOON + DAY / 2, 0));
    }
}
