package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.network.packet.LostTalesChatMembersRequestPacket;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The server keeps the member lists a player has asked for lately so it
 * can send one again the moment it changes: a list stays watched while
 * its client keeps asking, one conversation at a time, and is let go
 * once the asks stop or the player leaves.
 */
public final class ChatMemberWatchesTest {
    private static final UUID PLAYER = UUID.randomUUID();

    @Before
    public void setUp() {
        ChatMemberWatches.clear();
    }

    @After
    public void tearDown() {
        ChatMemberWatches.clear();
    }

    private static LostTalesChatMembersRequestPacket ask(String conversation) {
        return new LostTalesChatMembersRequestPacket(ChatChannel.GLOBAL,
                conversation, "", "", null, null, 0L);
    }

    @Test
    public void anAskedListStaysWatchedWhileItsClientKeepsAsking() {
        ChatMemberWatches.watch(PLAYER, ask("global"), 1L, 1000L);
        assertEquals(1, ChatMemberWatches.watchedAt(PLAYER,
                1000L + ChatMemberWatches.WATCH_MILLIS));
        // Asked again: one watch, from the newer ask.
        ChatMemberWatches.watch(PLAYER, ask("global"), 2L, 9000L);
        assertEquals(1, ChatMemberWatches.watchedAt(PLAYER,
                9000L + ChatMemberWatches.WATCH_MILLIS));
        assertEquals(0, ChatMemberWatches.watchedAt(PLAYER,
                9001L + ChatMemberWatches.WATCH_MILLIS));
    }

    @Test
    public void aPlayerWatchesSoManyListsAtMost() {
        for (int index = 0; index < ChatMemberWatches.MAX_WATCHED + 4; index++) {
            ChatMemberWatches.watch(PLAYER, ask("conversation" + index), 0L,
                    1000L + index);
        }
        assertEquals(ChatMemberWatches.MAX_WATCHED,
                ChatMemberWatches.watchedAt(PLAYER, 2000L));
    }

    @Test
    public void aPlayerWhoLeftWatchesNothing() {
        ChatMemberWatches.watch(PLAYER, ask("global"), 0L, 1000L);
        ChatMemberWatches.forget(PLAYER);
        assertEquals(0, ChatMemberWatches.watchedAt(PLAYER, 1000L));
    }
}
