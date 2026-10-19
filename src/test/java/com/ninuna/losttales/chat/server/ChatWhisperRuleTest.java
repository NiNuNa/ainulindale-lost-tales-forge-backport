package com.ninuna.losttales.chat.server;

import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * One whisper rule for everyone (CHAT-30): a character's name reaches
 * only while someone plays it or a copy speaks as it, an account whatever
 * it plays, and a character nobody wears is not here.
 */
public final class ChatWhisperRuleTest {
    private static final UUID ALDRIC = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Test
    public void anAccountIsReachedWhateverItPlays() {
        LostTalesChatService.Whispered whispered = LostTalesChatService.decide(null,
                new LostTalesChatService.AddressedIdentity("Steve", null), false, null);
        assertNull(whispered.absent);
        assertEquals("Steve", whispered.addressed.name);
    }

    @Test
    public void aCharacterIsReachedOnlyWhileWorn() {
        LostTalesChatService.AddressedIdentity aldric =
                new LostTalesChatService.AddressedIdentity("Aldric", ALDRIC);
        LostTalesChatService.Whispered worn = LostTalesChatService.decide(null, aldric,
                true, null);
        assertNull(worn.absent);
        assertEquals(ALDRIC, worn.addressed.characterId);
        LostTalesChatService.Whispered notWorn = LostTalesChatService.decide(null, aldric,
                false, null);
        assertEquals("Aldric", notWorn.absent);
        assertNull(notWorn.addressed);
    }

    @Test
    public void aNameNobodyWearsIsNotHereAndAnUnknownOneReachesNobody() {
        assertEquals("Beren", LostTalesChatService.decide(null, null, false, "Beren").absent);
        assertNull(LostTalesChatService.decide(null, null, false, null));
    }
}
