package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.server.ChatHistory;
import com.ninuna.losttales.chat.server.ChatMessageIdAllocator;
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
 * holds, and the save follows the live map whichever thread changes it.
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
        DiscordMessageLinkWorldData data = new DiscordMessageLinkWorldData(DiscordMessageLinkWorldData.DATA_NAME);
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
        live.link(3000L, "333", "", "channel:5", "");
        NBTTagCompound last = new NBTTagCompound();
        data.writeToNBT(last);
        assertEquals(2, DiscordMessageLinkNbtCodec.read(last).getEntries().size());
    }

    /**
     * The save follows the topic writes the worker records, and the
     * stopping server leaves it the offline topic the bridge wrote as it
     * stopped, whatever the memory does afterwards.
     */
    @Test
    public void theSaveFollowsTheTopicWritesAndKeepsTheStopsOfflineTopic() {
        DiscordTopicMemory memory = new DiscordTopicMemory();
        DiscordMessageLinkWorldData data = new DiscordMessageLinkWorldData(DiscordMessageLinkWorldData.DATA_NAME);
        data.attachTopics(memory);
        assertFalse(data.isDirty());
        DiscordTopicPacing pacing = memory.pacingOf("111");
        pacing.want("Server online • 0/20 players");
        assertFalse("wanted is not written", data.isDirty());
        pacing.posted("Server online • 0/20 players", 1000000L);
        assertTrue(data.isDirty());
        data.writeToNBT(new NBTTagCompound());
        data.setDirty(false);
        assertFalse(data.isDirty());

        // The offline topic the bridge writes as it stops.
        pacing.want("Server offline");
        pacing.posted("Server offline", 2000000L);
        data.detach();
        assertTrue(data.isDirty());
        memory.pacingOf("111").posted("Server online • 1/20 players", 3000000L);
        NBTTagCompound last = new NBTTagCompound();
        data.writeToNBT(last);
        DiscordMessageLinkNbtCodec.ReadResult read = DiscordMessageLinkNbtCodec.read(last);
        assertEquals(1, read.getTopics().size());
        assertEquals("Server offline", read.getTopics().get(0).words);
        assertEquals(2000000L, read.getTopics().get(0).lastWrite);
        assertEquals(1000000L, read.getTopics().get(0).writeBefore);
    }

    /**
     * Without their history the save's links are not taken, yet its
     * topics are: a topic write rewrites the save with the links as they
     * were read.
     */
    @Test
    public void linksWaitingForTheirHistoryAreWrittenBackWithTheTopics() {
        DiscordMessageLinks links = new DiscordMessageLinks();
        links.link(1000L, "111", "", "channel:5", "");
        NBTTagCompound saved = new NBTTagCompound();
        DiscordMessageLinkNbtCodec.write(saved, links.snapshot().links,
                Arrays.asList(new DiscordTopicMemory.SavedTopic("222", "Server offline",
                        1000000L, DiscordTopicMemory.SavedTopic.NO_WRITE)),
                java.util.Collections.<NBTTagCompound>emptyList());
        DiscordMessageLinkWorldData data = new DiscordMessageLinkWorldData(DiscordMessageLinkWorldData.DATA_NAME);
        data.readFromNBT(saved);

        DiscordTopicMemory memory = new DiscordTopicMemory();
        assertEquals(1, memory.restore(data.restoredTopics(), 2000000L));
        data.attachTopics(memory);
        assertFalse("nothing new yet", data.isDirty());
        assertEquals("Server offline", memory.pacingOf("222").posted());
        memory.pacingOf("222").posted("Server online • 0/20 players", 3000000L);
        assertTrue(data.isDirty());
        NBTTagCompound written = new NBTTagCompound();
        data.writeToNBT(written);
        DiscordMessageLinkNbtCodec.ReadResult read = DiscordMessageLinkNbtCodec.read(written);
        assertEquals(1, read.getEntries().size());
        assertEquals(1000L, read.getEntries().get(0).messageId);
        assertEquals("Server online • 0/20 players", read.getTopics().get(0).words);
        assertEquals(1000000L, read.getTopics().get(0).writeBefore);
    }

    /** A save from a newer build takes no topic memory either. */
    @Test(expected = IllegalStateException.class)
    public void aReadOnlySaveTakesNoTopicMemory() {
        NBTTagCompound newer = new NBTTagCompound();
        newer.setInteger("DataVersion", DiscordMessageLinkNbtCodec.CURRENT_ROOT_DATA_VERSION + 1);
        DiscordMessageLinkWorldData data = new DiscordMessageLinkWorldData(DiscordMessageLinkWorldData.DATA_NAME);
        data.readFromNBT(newer);
        data.attachTopics(new DiscordTopicMemory());
    }

    @Test
    public void aRestoreThatLeftSomethingOutIsWrittenBack() {
        DiscordMessageLinkWorldData data = new DiscordMessageLinkWorldData(DiscordMessageLinkWorldData.DATA_NAME);
        data.attach(new DiscordMessageLinks(), true);
        assertTrue(data.isDirty());
    }

    private static long say(String text) {
        long id = ChatMessageIdAllocator.next();
        ChatHistory.record(id, ALICE, "Aldric", null,
                new LostTalesChatMessagePacket(ChatChannel.GLOBAL, ALICE, "Aldric",
                        "alice", "", 0, 0, text, 1000000L, "", null, "", "", 0,
                        false, id, ChatReplyReference.NONE, ""),
                Arrays.asList(ALICE), ChatHistory.Audience.everyone());
        return id;
    }
}
