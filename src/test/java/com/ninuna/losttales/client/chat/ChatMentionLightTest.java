package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.Collections;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A mention lights only in the copies whose voice it names (CHAT-02); a
 * line naming every voice, the account out of character or a role, lights
 * in every copy.
 */
public final class ChatMentionLightTest {
    private static final UUID BEREN = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final ConversationPage GLOBAL = ConversationPage.of(ChatChannel.GLOBAL);

    @After
    public void forget() {
        LostTalesChatPresentation.clear();
    }

    @Test
    public void aMentionOfAnotherVoiceLightsNoCopySpeakingAsThePlayedOne() {
        LostTalesChatPresentation.markPinged(11,
                Collections.singleton(ConversationPage.ownerKeyOf(BEREN)));
        assertTrue("it still mentions the player", LostTalesChatPresentation.isPingedLine(11));
        assertFalse(LostTalesChatPresentation.isPingedIn(11, GLOBAL));
    }

    @Test
    public void aMentionOfTheVoiceLightsTheCopy() {
        // With no character played the copy speaks as the account's key.
        LostTalesChatPresentation.markPinged(12,
                Collections.singleton(ConversationPage.ownerKeyOf(null)));
        assertTrue(LostTalesChatPresentation.isPingedIn(12, GLOBAL));
    }

    @Test
    public void aLineNamingEveryVoiceLightsEveryCopy() {
        LostTalesChatPresentation.markPinged(13);
        assertTrue(LostTalesChatPresentation.isPingedIn(13, GLOBAL));
        assertTrue(LostTalesChatPresentation.isPingedIn(13, GLOBAL.withInstance(2)));
        assertFalse(LostTalesChatPresentation.isPingedIn(14, GLOBAL));
    }
}
