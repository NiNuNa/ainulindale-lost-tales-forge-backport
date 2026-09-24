package com.ninuna.losttales.party.sync;

import java.util.UUID;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** An invitation's Accept and Decline carry its id, and nothing else reads as an answer. */
public final class PartyInvitationNoticeTest {
    private static final UUID INVITATION =
            UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    @Test
    public void eachAnswerCarriesTheInvitation() {
        ChatComponentTranslation line = (ChatComponentTranslation)
                PartyInvitationNotice.line("Aldric", INVITATION);
        assertTrue(PartyInvitationNotice.isNotice(line));
        assertEquals("Aldric", line.getFormatArgs()[0]);

        PartyInvitationNotice.Answer accept = answerOf(line.getFormatArgs()[1]);
        PartyInvitationNotice.Answer decline = answerOf(line.getFormatArgs()[2]);
        assertTrue(accept.accept);
        assertFalse(decline.accept);
        assertEquals(INVITATION, accept.invitationId);
        assertEquals(INVITATION, decline.invitationId);
    }

    @Test
    public void anythingElseIsNoAnswer() {
        assertNull(PartyInvitationNotice.parse(null));
        assertNull(PartyInvitationNotice.parse("/party accept"));
        assertNull(PartyInvitationNotice.parse("losttales-party-invitation:maybe:" + INVITATION));
        assertNull(PartyInvitationNotice.parse("losttales-party-invitation:accept:not-an-id"));
        assertFalse(PartyInvitationNotice.isNotice(new ChatComponentText("Aldric invites you")));
    }

    private static PartyInvitationNotice.Answer answerOf(Object argument) {
        IChatComponent word = (IChatComponent)argument;
        return PartyInvitationNotice.parse(
                word.getChatStyle().getChatClickEvent().getValue());
    }
}
