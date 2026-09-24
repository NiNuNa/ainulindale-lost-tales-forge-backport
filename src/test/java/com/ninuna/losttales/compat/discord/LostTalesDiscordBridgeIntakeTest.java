package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Each worker has an intake of its own. A worker still finishing after a
 * reload keeps only what was queued under its own bindings and never
 * sees what is queued for the next one, and every queued post names its
 * entry by the bindings of the worker that looks it up.
 */
public final class LostTalesDiscordBridgeIntakeTest {
    private static final String HOOK_A = "https://discord.com/api/webhooks/1/a";
    private static final String HOOK_B = "https://discord.com/api/webhooks/2/b";

    private static DiscordChannelBindings bound(String... entries) {
        return DiscordChannelBindings.parse(entries, true, null);
    }

    @Test
    public void aWorkerLeftFinishingNeverSeesTheNextWorkersIntake() {
        LostTalesDiscordBridge bridge = LostTalesDiscordBridge.getInstance();
        try {
            Thread old = bridge.installIdleWorker(bound(
                    "ooc=GAME_TO_DISCORD;webhook=" + HOOK_A,
                    "ooc=GAME_TO_DISCORD;webhook=" + HOOK_B));
            bridge.relayToDiscord(ChatChannel.OOC, "", "Aragorn", "", "first",
                    1000L, null, null, null);
            bridge.relayTyping(ChatChannel.OOC, "");
            assertEquals(Arrays.asList("ooc", "ooc#2"), bridge.queuedPostsOf(old));

            // The list reordered on a reload: "ooc" now posts through
            // HOOK_B, and "ooc#2" only reads.
            Thread next = bridge.installIdleWorker(bound(
                    "ooc=GAME_TO_DISCORD;webhook=" + HOOK_B,
                    "ooc=DISCORD_TO_GAME;channel=5"));
            bridge.relayToDiscord(ChatChannel.OOC, "", "Aragorn", "", "second",
                    1001L, null, null, null);
            bridge.relayTyping(ChatChannel.OOC, "");

            assertEquals("the old worker holds only what was queued under its bindings",
                    Arrays.asList("ooc", "ooc#2"), bridge.queuedPostsOf(old));
            assertEquals(new HashSet<String>(Arrays.asList("ooc", "ooc#2")),
                    bridge.typingOf(old));
            assertEquals("ids come from the bindings of the worker that looks them up",
                    Arrays.asList("ooc"), bridge.queuedPostsOf(next));
            assertEquals(Collections.singleton("ooc"), bridge.typingOf(next));
        } finally {
            bridge.stop();
        }
    }

    @Test
    public void aStopForgetsWhatWasQueuedForTheStoppedWorker() {
        LostTalesDiscordBridge bridge = LostTalesDiscordBridge.getInstance();
        try {
            Thread old = bridge.installIdleWorker(bound(
                    "ooc=GAME_TO_DISCORD;webhook=" + HOOK_A));
            bridge.relayToDiscord(ChatChannel.OOC, "", "Aragorn", "", "line",
                    1000L, null, null, null);
            bridge.relayTyping(ChatChannel.OOC, "");
            bridge.stop();
            assertTrue(bridge.queuedPostsOf(old).isEmpty());
            assertTrue(bridge.typingOf(old).isEmpty());
            // With no worker, nothing is queued anywhere.
            bridge.relayToDiscord(ChatChannel.OOC, "", "Aragorn", "", "late",
                    1001L, null, null, null);
            assertTrue(bridge.queuedPostsOf(old).isEmpty());
        } finally {
            bridge.stop();
        }
    }

    /**
     * A line goes out through every binding that posts it or through
     * none: an intake with room for fewer than all of its posts queues
     * none of them.
     */
    @Test
    public void aLineTheIntakeHasNoRoomForQueuesNoneOfItsPosts() {
        LostTalesDiscordBridge bridge = LostTalesDiscordBridge.getInstance();
        try {
            Thread idle = bridge.installIdleWorker(bound(
                    "ooc=GAME_TO_DISCORD;webhook=" + HOOK_A,
                    "ooc=GAME_TO_DISCORD;webhook=" + HOOK_B));
            // Removals fill the intake to one place short.
            for (int index = 1; index < LostTalesDiscordBridge.MAX_QUEUED_OUTBOUND;
                    index++) {
                bridge.relayDelete(index, ChatChannel.OOC, "");
            }
            bridge.relayToDiscord(ChatChannel.OOC, "", "Aragorn", "", "two posts",
                    5000L, null, null, null);
            assertTrue("neither post of the line is queued",
                    bridge.queuedPostsOf(idle).isEmpty());
        } finally {
            bridge.stop();
        }
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
}
