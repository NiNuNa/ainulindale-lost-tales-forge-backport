package com.ninuna.losttales.compat.discord;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * What a restart of the bridge carries over from the worker it stops: the
 * channels whose topic must be told the server is offline, and where each
 * channel still read had got to.
 */
public final class LostTalesDiscordBridgeRestartTest {
    private static final String HOOK_A = "https://discord.com/api/webhooks/1/a";
    private static final String HOOK_B = "https://discord.com/api/webhooks/2/b";

    private static DiscordChannelBindings bound(String... entries) {
        return DiscordChannelBindings.parse(entries, true, null);
    }

    /**
     * A channel whose topic the running worker keeps and the next start
     * does not is left to be told the server is offline: one whose entry
     * is switched off, one no entry names any more, and every one when
     * the next start keeps no topic.
     */
    @Test
    public void aChannelWhoseTopicIsNoLongerKeptIsLeftForTheOfflineTopic() {
        List<String> kept = Arrays.asList("111", "222", "333");
        DiscordChannelBindings next = bound(
                "ooc=BIDIRECTIONAL;channel=111;webhook=" + HOOK_A,
                "global=DISABLED;channel=222;webhook=" + HOOK_B);
        assertEquals(Arrays.asList("222", "333"),
                LostTalesDiscordBridge.topicsLeft(kept, next, true));
        assertEquals("no topic kept at all", kept,
                LostTalesDiscordBridge.topicsLeft(kept, next, false));
        assertEquals("the bridge switched off", kept,
                LostTalesDiscordBridge.topicsLeft(kept, DiscordChannelBindings.EMPTY,
                        false));
        DiscordChannelBindings same = bound(
                "ooc=BIDIRECTIONAL;channel=111;webhook=" + HOOK_A,
                "global=GAME_TO_DISCORD;channel=222;webhook=" + HOOK_B,
                "proximity=GAME_TO_DISCORD;channel=333;webhook=" + HOOK_B + "-p");
        assertTrue(LostTalesDiscordBridge.topicsLeft(kept, same, true).isEmpty());
    }

    /** A restart reads on from where it got to, but only in channels read both before and after. */
    @Test
    public void aRestartReadsOnOnlyWhereItReadBefore() {
        java.util.Map<String, String> seen = new java.util.HashMap<String, String>();
        seen.put("5", "100");
        seen.put("6", "200");
        seen.put("7", "300");
        LostTalesDiscordBridge.keepSeenOfChannelsStillRead(seen,
                bound("ooc=DISCORD_TO_GAME;channel=5", "global=DISCORD_TO_GAME;channel=6"),
                bound("ooc=DISCORD_TO_GAME;channel=5", "global=DISCORD_TO_GAME;channel=7"));
        assertEquals(Collections.singletonMap("5", "100"), seen);
        LostTalesDiscordBridge.keepSeenOfChannelsStillRead(seen,
                DiscordChannelBindings.EMPTY, bound("ooc=DISCORD_TO_GAME;channel=5"));
        assertTrue("a start after a stop reads from the newest line", seen.isEmpty());
    }
}
