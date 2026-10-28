package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Every chat line passes one ordered path whatever its source (CHAT-82),
 * each rule kept once: the words rule, who calls roles and who is held to
 * a budget here.
 */
public final class ChatLinePipelineTest {

    @Test
    public void theWordsRuleIsOneForEverySource() {
        assertEquals("draws his sword.", ChatLinePipeline.words("draws his sword", true));
        assertEquals("hail", ChatLinePipeline.words("hail", false));
        assertNull(ChatLinePipeline.words("", false));
        assertNull(ChatLinePipeline.words("   ", true));
        assertNull(ChatLinePipeline.words("two\u0000words", false));
    }

    @Test
    public void onlyAPlayersOwnLineCallsRoles() {
        assertTrue(ChatLinePipeline.callsRoles(ChatLinePipeline.Source.PLAYER, false));
        assertTrue(ChatLinePipeline.callsRoles(ChatLinePipeline.Source.NARRATOR, false));
        assertFalse(ChatLinePipeline.callsRoles(ChatLinePipeline.Source.PLAYER, true));
        assertFalse(ChatLinePipeline.callsRoles(ChatLinePipeline.Source.DISCORD, false));
        assertFalse(ChatLinePipeline.callsRoles(ChatLinePipeline.Source.SERVER, false));
    }

    /** A player's lines were counted at the door and the Server has no budget; a Discord member's is asked here. */
    @Test
    public void aDiscordMemberIsHeldToAPlayersBudgetHere() {
        UUID member = UUID.randomUUID();
        assertTrue(ChatLinePipeline.withinBudget(ChatLinePipeline.Source.PLAYER, member,
                LostTalesRequestRateLimiter.RequestType.CHAT_MESSAGE, "line"));
        assertTrue(ChatLinePipeline.withinBudget(ChatLinePipeline.Source.SERVER, null,
                LostTalesRequestRateLimiter.RequestType.CHAT_MESSAGE, "line"));
        int allowed = 0;
        for (int index = 0; index < 100; index++) {
            if (ChatLinePipeline.withinBudget(ChatLinePipeline.Source.DISCORD, member,
                    LostTalesRequestRateLimiter.RequestType.CHAT_MESSAGE, "line")) {
                allowed++;
            }
        }
        assertTrue("some pass", allowed > 0);
        assertTrue("not all of a flood", allowed < 100);
    }

    @Test
    public void nobodyIsMutedWithoutAServer() {
        assertNull(ChatLinePipeline.muteOf(UUID.randomUUID()));
        assertNull(ChatLinePipeline.muteOf(null));
    }
}
