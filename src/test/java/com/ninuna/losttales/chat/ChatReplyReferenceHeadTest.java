package com.ninuna.losttales.chat;

import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A quote wears the head of the line it quotes: a player's face, an NPC's
 * portrait, or nothing for a quote of nothing, and a quote cut afresh
 * keeps the head of the one it replaces.
 */
public final class ChatReplyReferenceHeadTest {

    @Test
    public void aQuoteOfNothingWearsNoHead() {
        assertSame(ChatReplyReference.NONE, ChatReplyReference.NONE
                .withHead(UUID.randomUUID(), true, ""));
        assertSame(ChatReplyReference.NONE, ChatReplyReference.NONE
                .withNpcHead(UUID.randomUUID(), "lotr:npc.png"));
        assertFalse(ChatReplyReference.NONE.hasHead());
    }

    @Test
    public void anNpcsQuoteWearsItsPortrait() {
        UUID npc = UUID.randomUUID();
        ChatReplyReference quote = ChatReplyReference.of(-5L, "Barliman",
                "Welcome to the Prancing Pony").withNpcHead(npc,
                "lotr:textures/entity/bree.png");
        assertTrue(quote.hasHead());
        assertTrue(quote.isNpcLine());
        assertFalse(quote.isAccountLine());
        assertEquals(npc, quote.getSenderId());
        assertEquals("lotr:textures/entity/bree.png", quote.getSkinId());
    }

    @Test
    public void aFreshCutKeepsTheHeadItReplaces() {
        UUID sender = UUID.randomUUID();
        ChatReplyReference player = ChatReplyReference.of(10L, "Aldric",
                "old words", 0xAB597D).withHead(sender, false, "skin");
        ChatReplyReference recut = ChatReplyReference.of(10L, "Aldric",
                "new words", player.getAuthorColor()).withHeadOf(player);
        assertEquals("new words", recut.getExcerpt());
        assertEquals(0xAB597D, recut.getAuthorColor());
        assertEquals(sender, recut.getSenderId());
        assertEquals("skin", recut.getSkinId());
        assertFalse(recut.isNpcLine());

        ChatReplyReference npc = ChatReplyReference.of(-5L, "Barliman", "hi")
                .withNpcHead(sender, "portrait");
        assertTrue(ChatReplyReference.of(-5L, "Barliman", "hello")
                .withHeadOf(npc).isNpcLine());

        ChatReplyReference bare = ChatReplyReference.of(10L, "Aldric", "x");
        assertNull(bare.withHeadOf(ChatReplyReference.of(10L, "Aldric", "y"))
                .getSenderId());
        assertNull(bare.withHeadOf(null).getSenderId());
    }
}
