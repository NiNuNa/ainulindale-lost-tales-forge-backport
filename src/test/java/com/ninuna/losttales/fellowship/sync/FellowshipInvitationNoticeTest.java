package com.ninuna.losttales.fellowship.sync;

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
public final class FellowshipInvitationNoticeTest {
    private static final UUID INVITATION =
            UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    @Test
    public void eachAnswerCarriesTheInvitation() {
        ChatComponentTranslation line = (ChatComponentTranslation)
                FellowshipInvitationNotice.line("Aldric", "Grey Company", INVITATION);
        assertTrue(FellowshipInvitationNotice.isNotice(line));
        assertEquals("Aldric", line.getFormatArgs()[0]);
        assertEquals("Grey Company", line.getFormatArgs()[1]);
        assertEquals(INVITATION, FellowshipInvitationNotice.invitationIdOf(line));

        FellowshipInvitationNotice.Answer accept = answerOf(line.getFormatArgs()[2]);
        FellowshipInvitationNotice.Answer decline = answerOf(line.getFormatArgs()[3]);
        assertTrue(accept.accept);
        assertFalse(decline.accept);
        assertEquals(INVITATION, accept.invitationId);
        assertEquals(INVITATION, decline.invitationId);
    }

    @Test
    public void anythingElseIsNoAnswer() {
        assertNull(FellowshipInvitationNotice.parse(null));
        assertNull(FellowshipInvitationNotice.parse("/fellowship accept"));
        assertNull(FellowshipInvitationNotice.parse("losttales-fellowship-invitation:maybe:" + INVITATION));
        assertNull(FellowshipInvitationNotice.parse("losttales-fellowship-invitation:accept:not-an-id"));
        assertFalse(FellowshipInvitationNotice.isNotice(new ChatComponentText("Aldric invites you")));
    }

    private static FellowshipInvitationNotice.Answer answerOf(Object argument) {
        IChatComponent word = (IChatComponent)argument;
        return FellowshipInvitationNotice.parse(
                word.getChatStyle().getChatClickEvent().getValue());
    }
}
