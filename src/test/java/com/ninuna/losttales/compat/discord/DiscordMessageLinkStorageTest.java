package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.server.ChatHistory;
import com.ninuna.losttales.chat.server.ChatMessageIdAllocator;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The links come back only for messages the restored history still
 * holds, the save follows the live map whichever thread changes it, and
 * a reload keeps the links while the next world starts with none.
 */
public final class DiscordMessageLinkStorageTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Before
    public void setUp() {
        ChatHistory.clear();
        ChatMessageIdAllocator.reset();
    }

    @After
    public void tearDown() {
        ChatHistory.clear();
        ChatMessageIdAllocator.reset();
    }

    @Test
    public void onlyLinksToMessagesTheHistoryStillHoldsComeBack() {
        long kept = say("hail");
        long removed = say("taken back");
        ChatHistory.remove(removed, ALICE);
        // An id the history never held, as a message said in a run whose
        // history was not saved would have.
        long never = ChatMessageIdAllocator.next();
        DiscordMessageLinks links = new DiscordMessageLinks();
        links.link(kept, "111", "", "channel:5", "");
        links.link(removed, "222", "", "channel:5", "");
        links.link(never, "333", "", "channel:5", "");

        DiscordMessageLinks restored = new DiscordMessageLinks();
        assertEquals(1, restored.restore(links.snapshot().links,
                DiscordMessageLinkStorage.HISTORY));
        assertEquals(kept, restored.messageIdOf("111"));
        assertEquals(ChatMessageIds.NONE, restored.messageIdOf("222"));
        assertEquals(ChatMessageIds.NONE, restored.messageIdOf("333"));
    }

    @Test
    public void theSaveFollowsTheLiveLinksWhicheverThreadChangesThem() {
        DiscordMessageLinks live = new DiscordMessageLinks();
        DiscordMessageLinkWorldData data = new DiscordMessageLinkWorldData();
        data.attach(live, false);
        // Nothing to write for a world whose bridge links nothing.
        assertFalse(data.isDirty());
        live.link(1000L, "111", "", "channel:5", "");
        assertTrue(data.isDirty());
        data.writeToNBT(new NBTTagCompound());
        data.setDirty(false);
        assertFalse(data.isDirty());
        // A link the worker makes between a write and the save clearing
        // its flag is not lost: the store stays dirty until a write
        // takes it.
        data.writeToNBT(new NBTTagCompound());
        live.link(2000L, "222", "", "channel:5", "");
        data.setDirty(false);
        assertTrue(data.isDirty());
        // The stopping server leaves the last state to be written,
        // whatever the live map does afterwards.
        data.detach();
        assertTrue(data.isDirty());
        live.clear();
        live.link(3000L, "333", "", "channel:5", "");
        NBTTagCompound last = new NBTTagCompound();
        data.writeToNBT(last);
        assertEquals(2, DiscordMessageLinkNbtCodec.read(last).getEntries().size());
    }

    @Test
    public void aRestoreThatLeftSomethingOutIsWrittenBack() {
        DiscordMessageLinkWorldData data = new DiscordMessageLinkWorldData();
        data.attach(new DiscordMessageLinks(), true);
        assertTrue(data.isDirty());
    }

    @Test
    public void aReloadKeepsTheLinksAndTheNextWorldStartsWithNone() {
        LostTalesDiscordBridge bridge = LostTalesDiscordBridge.getInstance();
        boolean enabled = LostTalesConfig.discordEnabled;
        try {
            LostTalesConfig.discordEnabled = false;
            bridge.restoreLinks(null);
            bridge.links().link(1000L, "111", "", "channel:5", "");
            // What /losttales discord reload and a live settings change do.
            bridge.start();
            bridge.stop();
            bridge.start();
            assertEquals(1000L, bridge.links().messageIdOf("111"));
            // The stopping server leaves an empty map behind it,
            bridge.releaseLinks();
            assertEquals(ChatMessageIds.NONE, bridge.links().messageIdOf("111"));
            // and the next server's start begins from a map of its own.
            bridge.links().link(2000L, "222", "", "channel:5", "");
            bridge.restoreLinks(null);
            assertEquals(0, bridge.links().size());
        } finally {
            LostTalesConfig.discordEnabled = enabled;
            bridge.releaseLinks();
        }
    }

    private static long say(String text) {
        long id = ChatMessageIdAllocator.next();
        ChatHistory.record(id, ALICE, "Aldric", null,
                new LostTalesChatMessagePacket(ChatChannel.ALL, ALICE, "Aldric",
                        "alice", "", 0, 0, text, 1000000L, "", null, "", "", 0,
                        false, id, ChatReplyReference.NONE, ""),
                Arrays.asList(ALICE), ChatHistory.Audience.everyone());
        return id;
    }
}
