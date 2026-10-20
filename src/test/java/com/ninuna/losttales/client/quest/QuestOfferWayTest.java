package com.ninuna.losttales.client.quest;

import org.junit.Test;

import static org.junit.Assert.assertSame;

/**
 * An offer is never lost to an open screen (QUEST-41): with nothing open
 * the conversation opens on it; with a screen open, or the conversation
 * switched off, it goes to the chat with *Accept* and *Decline*.
 */
public final class QuestOfferWayTest {

    @Test
    public void nothingOpenOpensTheConversation() {
        assertSame(QuestOfferWay.CONVERSATION, QuestOfferWay.of(true, false));
    }

    @Test
    public void anOpenScreenSendsTheOfferToTheChat() {
        assertSame(QuestOfferWay.CHAT, QuestOfferWay.of(true, true));
    }

    @Test
    public void theConversationSwitchedOffSendsTheOfferToTheChat() {
        assertSame(QuestOfferWay.CHAT, QuestOfferWay.of(false, false));
        assertSame(QuestOfferWay.CHAT, QuestOfferWay.of(false, true));
    }
}
