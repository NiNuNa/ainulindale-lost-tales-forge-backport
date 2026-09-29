package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/**
 * A quote of a line no server named wears a head only where the server
 * can vouch for it: the sender's own head for a line of theirs, when the
 * quote names the identity the reply is signed as. A quote claiming the
 * Server wears nothing, since only the server's own record can say so.
 */
public final class LostTalesChatServiceQuoteHeadTest {

    private static final UUID SENDER = UUID.randomUUID();

    private static ChatReplyReference quote(String author) {
        return ChatReplyReference.unanchored(author, "the words",
                ChatReplyReference.NO_COLOR);
    }

    @Test
    public void aQuoteClaimingTheServerWearsNoHead() {
        assertNull(LostTalesChatService.vouchedHead(quote("Server"),
                LostTalesChatSendPacket.QUOTE_OTHER, SENDER, "Aldric", false,
                "skin", 0x64B082).getSenderId());
    }

    @Test
    public void theSendersOwnLineWearsTheHeadTheReplyIsSignedWith() {
        ChatReplyReference head = LostTalesChatService.vouchedHead(
                quote("Aldric"), LostTalesChatSendPacket.QUOTE_OWN, SENDER,
                "Aldric", false, "skin", 0x64B082);
        assertEquals(SENDER, head.getSenderId());
        assertFalse(head.isAccountLine());
        assertEquals("skin", head.getSkinId());
        assertEquals(0x64B082, head.getAuthorColor());
    }

    @Test
    public void anythingTheServerCannotVouchForGoesBare() {
        // Somebody else's name on a claim of one's own line.
        assertNull(LostTalesChatService.vouchedHead(quote("Legolas"),
                LostTalesChatSendPacket.QUOTE_OWN, SENDER, "Aldric", false,
                "skin", 0x64B082).getSenderId());
        // Nobody the sender can name.
        assertNull(LostTalesChatService.vouchedHead(quote("Aldric"),
                LostTalesChatSendPacket.QUOTE_OTHER, SENDER, "Aldric", false,
                "skin", 0x64B082).getSenderId());
        // A quote of nothing stays nothing.
        assertFalse(LostTalesChatService.vouchedHead(ChatReplyReference.NONE,
                LostTalesChatSendPacket.QUOTE_OTHER, SENDER, "Aldric", false,
                "skin", 0).exists());
    }
}
