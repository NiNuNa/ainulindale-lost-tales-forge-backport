package com.ninuna.losttales.chat.share;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ChatShareTokenParserTest {

    @Test
    public void plainTextAndMalformedTokensProduceNoTokens() {
        assertTrue(ChatShareTokenParser.parse(null).isEmpty());
        assertTrue(ChatShareTokenParser.parse("hello [link]").isEmpty());
        assertTrue(ChatShareTokenParser.parse("[i:]").isEmpty());
        assertTrue(ChatShareTokenParser.parse("[m:   ]").isEmpty());
        assertTrue(ChatShareTokenParser.parse("[i:unclosed").isEmpty());
        assertTrue(ChatShareTokenParser.parse("[i:Sword#0]").isEmpty());
        assertTrue(ChatShareTokenParser.parse("[m:Bree#100]").isEmpty());
        StringBuilder overlong = new StringBuilder("[i:");
        for (int index = 0; index <= ChatShareTokenParser.MAX_NAME_LENGTH;
             index++) {
            overlong.append('x');
        }
        overlong.append(']');
        assertTrue(ChatShareTokenParser.parse(overlong.toString()).isEmpty());
    }

    @Test
    public void tokensCarryKindSpanNameAndOrdinal() {
        List<ChatShareTokenParser.Token> tokens = ChatShareTokenParser.parse(
                "meet at [m:Northgate Test City] with [i: Bow #2 ]!");
        assertEquals(2, tokens.size());
        assertEquals(ChatShareKind.MARKER, tokens.get(0).kind);
        assertEquals(8, tokens.get(0).start);
        assertEquals(31, tokens.get(0).end);
        assertEquals("Northgate Test City", tokens.get(0).name);
        assertEquals(1, tokens.get(0).ordinal);
        assertEquals(ChatShareKind.ITEM, tokens.get(1).kind);
        assertEquals("Bow", tokens.get(1).name);
        assertEquals(2, tokens.get(1).ordinal);
        assertEquals("northgate test city", tokens.get(0).normalizedName());
    }

    @Test
    public void laterOpenerBeforeTheCloseStillParses() {
        List<ChatShareTokenParser.Token> tokens = ChatShareTokenParser.parse(
                "[i:broken [m:Shield]");
        assertEquals(1, tokens.size());
        assertEquals(ChatShareKind.MARKER, tokens.get(0).kind);
        assertEquals("Shield", tokens.get(0).name);
        assertEquals(10, tokens.get(0).start);
    }

    @Test
    public void tokenCountIsBoundedAcrossKinds() {
        List<ChatShareTokenParser.Token> tokens = ChatShareTokenParser.parse(
                "[i:a] [m:b] [i:c] [m:d]");
        assertEquals(ChatShareTokenParser.MAX_TOKENS, tokens.size());
        assertEquals("c", tokens.get(2).name);
    }

    @Test
    public void openerDetectionBuildAndNormalize() {
        assertTrue(ChatShareTokenParser.opensShareToken("[i:", 2));
        assertTrue(ChatShareTokenParser.opensShareToken("x [m:s", 4));
        assertFalse(ChatShareTokenParser.opensShareToken(":smile", 0));
        assertFalse(ChatShareTokenParser.opensShareToken("{i:", 2));
        assertEquals("[i:Iron Sword]", ChatShareTokenParser.buildToken(
                ChatShareKind.ITEM, "Iron Sword", 1));
        assertEquals("[m:Bree#3]", ChatShareTokenParser.buildToken(
                ChatShareKind.MARKER, " Bree ", 3));
        assertEquals("iron sword", ChatShareTokenParser.normalizeName(
                "§6Iron§r   Sword "));
        assertEquals("Iron Sword", ChatShareTokenParser.plainName(
                " §6Iron Sword§r "));
        assertEquals("", ChatShareTokenParser.normalizeName(null));
        assertEquals(ChatShareKind.ITEM, ChatShareKind.fromCode('i'));
        assertEquals(ChatShareKind.MARKER, ChatShareKind.fromCode('m'));
        assertEquals(null, ChatShareKind.fromCode('x'));
    }
}
