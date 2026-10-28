package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatMessageIds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The bridge's links round-trip through the save with no webhook URL in
 * it; an entry the save cannot vouch for is quarantined rather than
 * followed, the bound holds, and data from a newer build is left alone.
 */
public final class DiscordMessageLinkNbtCodecTest {
    private static final String WEBHOOK =
            "https://discord.com/api/webhooks/123456/SECRET-TOKEN";
    private static final String OTHER_WEBHOOK =
            "https://discord.com/api/webhooks/654321/OTHER-TOKEN";
    private static final List<NBTTagCompound> NO_QUARANTINE =
            Collections.<NBTTagCompound>emptyList();
    private static final List<DiscordTopicMemory.SavedTopic> NO_TOPICS =
            Collections.<DiscordTopicMemory.SavedTopic>emptyList();
    private static final long START = 1000000000L;
    private static final DiscordMessageLinks.MessageIndex EVERYTHING =
            new DiscordMessageLinks.MessageIndex() {
                @Override
                public boolean holds(long messageId) {
                    return true;
                }
            };

    @Test
    public void linksRoundTripWithoutTheirWebhooks() {
        DiscordMessageLinks links = new DiscordMessageLinks();
        // A game line posted through two links into two channels.
        links.link(1000L, "111", "-# ↩ header\n", "channel:5", WEBHOOK, "ooc");
        links.link(1000L, "222", "", "channel:6", OTHER_WEBHOOK, "ooc#2");
        // A Discord member's line, read from a channel.
        links.link(2000L, "333", "", "channel:5", "");

        NBTTagCompound written = write(links);
        DiscordMessageLinkNbtCodec.ReadResult result =
                DiscordMessageLinkNbtCodec.read(written);
        assertFalse(result.isReadOnly());
        assertFalse(result.wasRepaired());
        assertTrue(result.getQuarantineEntriesCopy().isEmpty());
        assertEquals(2, result.getEntries().size());

        DiscordMessageLinks restored = new DiscordMessageLinks();
        assertEquals(2, restored.restore(result.getEntries(), EVERYTHING));
        assertEquals(1000L, restored.messageIdOf("111"));
        assertEquals(1000L, restored.messageIdOf("222"));
        assertEquals(2000L, restored.messageIdOf("333"));
        assertEquals("111", restored.discordIdOf(1000L, "channel:5"));
        assertEquals("222", restored.discordIdOf(1000L, "channel:6"));
        assertEquals("333", restored.discordIdOf(2000L, "channel:5"));
        List<DiscordMessageLinks.Copy> copies = restored.copiesOf(1000L);
        assertEquals(2, copies.size());
        assertEquals("-# ↩ header\n", copies.get(0).header);
        assertEquals("ooc", copies.get(0).channelLinkId);
        assertEquals("", copies.get(0).webhookUrl);
        // Each copy comes back under the channel it is in.
        assertEquals("channel:6", copies.get(1).destination);
        assertEquals("ooc#2", copies.get(1).channelLinkId);
        assertEquals("", copies.get(1).webhookUrl);
        assertEquals("", restored.copiesOf(2000L).get(0).channelLinkId);
        // Written again, the save says exactly the same.
        assertEquals(written, write(restored));
    }

    @Test
    public void noWebhookUrlIsEverWritten() {
        DiscordMessageLinks links = new DiscordMessageLinks();
        // A reply header's jump link names a message, not a webhook, and stays.
        links.link(1000L, "111",
                "-# ↩ [**A** — hi](https://discord.com/channels/1/5/99)\n",
                "channel:5", WEBHOOK, "ooc");
        // A post known by its webhook alone is left out.
        links.link(2000L, "222", "", WEBHOOK, WEBHOOK, "ooc");
        links.link(3000L, "333", "", OTHER_WEBHOOK, OTHER_WEBHOOK, "");
        // A link id that could pass for a URL is not written.
        links.link(4000L, "444", "", "channel:6", OTHER_WEBHOOK,
                "https://evil.example/x");

        NBTTagCompound written = write(links);
        assertNoWebhook(written);
        assertEquals(2, DiscordMessageLinkNbtCodec.read(written).getEntries().size());
        assertFalse(written.toString().contains("evil"));
        // The store writes nothing else, attached or after the stop.
        DiscordMessageLinkWorldData data = new DiscordMessageLinkWorldData(DiscordMessageLinkWorldData.DATA_NAME);
        data.attach(links, false);
        NBTTagCompound attached = new NBTTagCompound();
        data.writeToNBT(attached);
        assertNoWebhook(attached);
        data.detach();
        NBTTagCompound held = new NBTTagCompound();
        data.writeToNBT(held);
        assertNoWebhook(held);
        assertEquals(written, held);
    }

    @Test
    public void newerDataIsKeptVerbatimAndReadOnly() {
        NBTTagCompound newer = new NBTTagCompound();
        newer.setInteger("DataVersion",
                DiscordMessageLinkNbtCodec.CURRENT_ROOT_DATA_VERSION + 1);
        newer.setString("Future", "something this build does not know");
        DiscordMessageLinkNbtCodec.ReadResult result =
                DiscordMessageLinkNbtCodec.read(newer);
        assertTrue(result.isReadOnly());
        assertEquals(DiscordMessageLinkNbtCodec.CURRENT_ROOT_DATA_VERSION + 1,
                result.getUnsupportedVersion());
        assertTrue(result.getEntries().isEmpty());
        assertEquals("something this build does not know",
                result.getOriginalDataCopy().getString("Future"));

        // The store writes it back exactly as it was read, and takes no
        // live map to write from.
        DiscordMessageLinkWorldData data = new DiscordMessageLinkWorldData(DiscordMessageLinkWorldData.DATA_NAME);
        data.readFromNBT(newer);
        assertTrue(data.isReadOnlyForNewerVersion());
        assertTrue(data.restoredLinks().isEmpty());
        NBTTagCompound out = new NBTTagCompound();
        data.writeToNBT(out);
        assertEquals(newer, out);
        try {
            data.attach(new DiscordMessageLinks(), false);
            fail("a read-only store must not take a live map");
        } catch (IllegalStateException expected) {
            // Fails closed.
        }

        // One entry from a newer build makes the whole store read-only too.
        DiscordMessageLinks links = new DiscordMessageLinks();
        links.link(1000L, "111", "", "channel:5", "");
        NBTTagCompound current = write(links);
        current.getTagList("Messages", Constants.NBT.TAG_COMPOUND)
                .getCompoundTagAt(0).setInteger("DataVersion",
                        DiscordMessageLinkNbtCodec.CURRENT_ENTRY_DATA_VERSION + 1);
        assertTrue(DiscordMessageLinkNbtCodec.read(current).isReadOnly());
    }

    @Test
    public void aListOfSomethingElseIsKeptRatherThanReadAsEmpty() {
        NBTTagCompound odd = new NBTTagCompound();
        odd.setInteger("DataVersion",
                DiscordMessageLinkNbtCodec.CURRENT_ROOT_DATA_VERSION);
        NBTTagList strings = new NBTTagList();
        strings.appendTag(new NBTTagString("111"));
        odd.setTag("Messages", strings);
        assertTrue(DiscordMessageLinkNbtCodec.read(odd).isReadOnly());
    }

    @Test
    public void entriesTheSaveCannotVouchForAreQuarantined() {
        DiscordMessageLinks links = new DiscordMessageLinks();
        links.link(1000L, "111", "", "channel:5", WEBHOOK, "ooc");
        links.link(2000L, "222", "", "channel:5", "");
        NBTTagCompound written = write(links);
        NBTTagList entries = written.getTagList("Messages", Constants.NBT.TAG_COMPOUND);
        NBTTagCompound first = entries.getCompoundTagAt(0);
        NBTTagCompound second = entries.getCompoundTagAt(1);

        NBTTagCompound noId = entryLike(second, 0L, "300");
        NBTTagCompound urlDestination = entryLike(second, 4000L, "400");
        firstCopyOf(urlDestination).setString("Destination", WEBHOOK);
        NBTTagCompound badDiscordId = entryLike(second, 5000L, "not-a-snowflake");
        // Kept under a link rather than its channel.
        NBTTagCompound underChannelLink = entryLike(second, 6000L, "600");
        firstCopyOf(underChannelLink).setString("Destination", "link:ooc");
        firstCopyOf(underChannelLink).setString("ChannelLink", "ooc");
        NBTTagCompound noCopies = entryLike(second, 7000L, "700");
        noCopies.setTag("Copies", new NBTTagList());
        // Another message under a Discord id the second already names.
        NBTTagCompound takenDiscordId = entryLike(second, 8000L, "222");
        NBTTagCompound sameMessage = (NBTTagCompound) first.copy();

        NBTTagList rewritten = new NBTTagList();
        rewritten.appendTag(first.copy());
        rewritten.appendTag(second.copy());
        rewritten.appendTag(noId);
        rewritten.appendTag(urlDestination);
        rewritten.appendTag(badDiscordId);
        rewritten.appendTag(underChannelLink);
        rewritten.appendTag(noCopies);
        rewritten.appendTag(takenDiscordId);
        rewritten.appendTag(sameMessage);
        written.setTag("Messages", rewritten);

        DiscordMessageLinkNbtCodec.ReadResult result =
                DiscordMessageLinkNbtCodec.read(written);
        assertFalse(result.isReadOnly());
        assertTrue(result.wasRepaired());
        assertEquals(2, result.getEntries().size());
        List<NBTTagCompound> quarantine = result.getQuarantineEntriesCopy();
        assertEquals(7, quarantine.size());
        assertEquals("invalid_message_id", quarantine.get(0).getString("Reason"));
        assertEquals("invalid_destination", quarantine.get(1).getString("Reason"));
        assertEquals("invalid_discord_id", quarantine.get(2).getString("Reason"));
        assertEquals("invalid_destination", quarantine.get(3).getString("Reason"));
        assertEquals("missing_copies", quarantine.get(4).getString("Reason"));
        assertEquals("duplicate_discord_id", quarantine.get(5).getString("Reason"));
        assertEquals("duplicate_message", quarantine.get(6).getString("Reason"));
        // Quarantined whole, where it stood.
        assertEquals(3, quarantine.get(1).getInteger("EntryIndex"));
        assertTrue(quarantine.get(1).hasKey("OriginalData", Constants.NBT.TAG_COMPOUND));

        // The quarantine rides along on the next write, and the rest
        // reads back clean.
        NBTTagCompound again = new NBTTagCompound();
        DiscordMessageLinkNbtCodec.write(again, result.getEntries(), NO_TOPICS, quarantine);
        DiscordMessageLinkNbtCodec.ReadResult reread =
                DiscordMessageLinkNbtCodec.read(again);
        assertEquals(7, reread.getQuarantineEntriesCopy().size());
        assertEquals(2, reread.getEntries().size());
        assertFalse(reread.wasRepaired());
    }

    @Test
    public void theBoundHoldsAndWhatIsPastItIsQuarantined() {
        int max = DiscordMessageLinks.MAX_LINKS;
        List<DiscordMessageLinks.SavedLink> many =
                new ArrayList<DiscordMessageLinks.SavedLink>();
        for (int index = 0; index < max + 5; index++) {
            many.add(new DiscordMessageLinks.SavedLink(1000L + index,
                    Collections.singletonList(new DiscordMessageLinks.SavedCopy(
                            Long.toString(100000L + index), "channel:5", "", ""))));
        }
        NBTTagCompound written = new NBTTagCompound();
        DiscordMessageLinkNbtCodec.write(written, many, NO_TOPICS, NO_QUARANTINE);
        DiscordMessageLinkNbtCodec.ReadResult result =
                DiscordMessageLinkNbtCodec.read(written);
        assertTrue(result.wasRepaired());
        assertEquals(max, result.getEntries().size());
        assertEquals(5, result.getQuarantineEntriesCopy().size());
        assertEquals("over_capacity",
                result.getQuarantineEntriesCopy().get(0).getString("Reason"));

        // The live map keeps the same bound, the oldest going first.
        DiscordMessageLinks links = new DiscordMessageLinks();
        assertEquals(max, links.restore(many, EVERYTHING));
        assertEquals(ChatMessageIds.NONE, links.messageIdOf("100000"));
        assertEquals(1000L + max + 4,
                links.messageIdOf(Long.toString(100000L + max + 4)));
        assertEquals(max, links.snapshot().links.size());
    }

    /**
     * A message with more copies than a read takes is written with the
     * oldest of them, so the save never holds an entry it would
     * quarantine on the next start.
     */
    @Test
    public void aMessagesCopiesPastTheBoundAreNotWritten() {
        int max = DiscordMessageLinkNbtCodec.MAX_COPIES_PER_ENTRY;
        List<DiscordMessageLinks.SavedCopy> copies =
                new ArrayList<DiscordMessageLinks.SavedCopy>();
        for (int index = 0; index <= max; index++) {
            copies.add(new DiscordMessageLinks.SavedCopy(
                    Long.toString(500L + index), "channel:" + (900L + index),
                    "", ""));
        }
        NBTTagCompound written = new NBTTagCompound();
        DiscordMessageLinkNbtCodec.write(written, Collections.singletonList(
                new DiscordMessageLinks.SavedLink(1000L, copies)), NO_TOPICS, NO_QUARANTINE);
        DiscordMessageLinkNbtCodec.ReadResult result =
                DiscordMessageLinkNbtCodec.read(written);
        assertFalse(result.wasRepaired());
        assertTrue(result.getQuarantineEntriesCopy().isEmpty());
        assertEquals(1, result.getEntries().size());
        List<DiscordMessageLinks.SavedCopy> read =
                result.getEntries().get(0).copies;
        assertEquals(max, read.size());
        assertEquals("500", read.get(0).discordId);
        assertEquals(Long.toString(500L + max - 1), read.get(max - 1).discordId);
    }

    @Test
    public void anEmptyListReadsAsNothingKept() {
        DiscordMessageLinkNbtCodec.ReadResult result =
                DiscordMessageLinkNbtCodec.read(write(new DiscordMessageLinks()));
        assertFalse(result.isReadOnly());
        assertFalse(result.wasRepaired());
        assertTrue(result.getEntries().isEmpty());
        assertTrue(result.getQuarantineEntriesCopy().isEmpty());
    }

    /**
     * A save without its version or its list is data of another shape:
     * kept as it is, the store read-only, never filled in.
     */
    @Test
    public void aSaveOfAnotherShapeIsKeptReadOnly() {
        assertTrue(DiscordMessageLinkNbtCodec.read(new NBTTagCompound()).isReadOnly());
        DiscordMessageLinks links = new DiscordMessageLinks();
        links.link(1000L, "111", "", "channel:5", "");
        NBTTagCompound unversioned = write(links);
        unversioned.removeTag("DataVersion");
        assertTrue(DiscordMessageLinkNbtCodec.read(unversioned).isReadOnly());
        NBTTagCompound older = write(links);
        older.setInteger("DataVersion", 0);
        assertTrue(DiscordMessageLinkNbtCodec.read(older).isReadOnly());
        NBTTagCompound noList = write(links);
        noList.removeTag("Messages");
        DiscordMessageLinkNbtCodec.ReadResult result = DiscordMessageLinkNbtCodec.read(noList);
        assertTrue(result.isReadOnly());
        assertTrue(result.getOriginalDataCopy().hasKey("DataVersion"));
    }

    /** A copy without a field the writer always writes is quarantined with its message. */
    @Test
    public void aCopyMissingAFieldIsQuarantined() {
        for (String field : new String[] {"ChannelLink", "Header"}) {
            DiscordMessageLinks links = new DiscordMessageLinks();
            links.link(1000L, "111", "", "channel:5", "");
            NBTTagCompound written = write(links);
            firstCopyOf(written.getTagList("Messages", Constants.NBT.TAG_COMPOUND)
                    .getCompoundTagAt(0)).removeTag(field);
            DiscordMessageLinkNbtCodec.ReadResult result =
                    DiscordMessageLinkNbtCodec.read(written);
            assertFalse(field, result.isReadOnly());
            assertTrue(field, result.getEntries().isEmpty());
            assertEquals(field, 1, result.getQuarantineEntriesCopy().size());
        }
    }

    /**
     * Each kept topic's last two writes and their words round-trip beside
     * the links, and a memory restored from them paces as the one that
     * wrote them.
     */
    @Test
    public void topicsRoundTripBesideTheLinks() {
        DiscordTopicMemory memory = new DiscordTopicMemory();
        DiscordTopicPacing first = memory.pacingOf("111");
        first.want("Server online • 2/20 players");
        first.posted("Server online • 2/20 players", START - 60000L);
        first.want("Server offline");
        first.posted("Server offline", START);
        DiscordTopicPacing second = memory.pacingOf("222");
        second.want("Server offline");
        second.posted("Server offline", START - 1L);

        DiscordMessageLinks links = new DiscordMessageLinks();
        links.link(1000L, "999", "", "channel:5", "");
        NBTTagCompound written = new NBTTagCompound();
        DiscordMessageLinkNbtCodec.write(written, links.snapshot().links,
                memory.snapshot().topics, NO_QUARANTINE);
        DiscordMessageLinkNbtCodec.ReadResult result = DiscordMessageLinkNbtCodec.read(written);
        assertFalse(result.isReadOnly());
        assertFalse(result.wasRepaired());
        assertEquals(1, result.getEntries().size());
        assertEquals(2, result.getTopics().size());
        DiscordTopicMemory.SavedTopic read = result.getTopics().get(0);
        assertEquals("111", read.channelId);
        assertEquals("Server offline", read.words);
        assertEquals(START, read.lastWrite);
        assertEquals(START - 60000L, read.writeBefore);
        assertEquals(DiscordTopicMemory.SavedTopic.NO_WRITE,
                result.getTopics().get(1).writeBefore);

        DiscordTopicMemory restored = new DiscordTopicMemory();
        assertEquals(2, restored.restore(result.getTopics(), START + 60000L));
        assertEquals("Server offline", restored.pacingOf("111").posted());
        assertEquals(first.nextTurn(DiscordTopicPacing.WINDOW_MILLIS, false),
                restored.pacingOf("111").nextTurn(DiscordTopicPacing.WINDOW_MILLIS, false));
        assertEquals(first.nextTurn(DiscordTopicPacing.WINDOW_MILLIS, true),
                restored.pacingOf("111").nextTurn(DiscordTopicPacing.WINDOW_MILLIS, true));
        // Written again, the save says exactly the same.
        NBTTagCompound again = new NBTTagCompound();
        DiscordMessageLinkNbtCodec.write(again, result.getEntries(),
                restored.snapshot().topics, NO_QUARANTINE);
        assertEquals(written, again);
    }

    /**
     * A save of another shape is kept read-only, never read in part: one
     * without its list of topics, one of the version before the topics,
     * and one whose topics are not compounds.
     */
    @Test
    public void aSaveWithoutItsTopicsIsKeptReadOnly() {
        NBTTagCompound noTopics = writeTopics();
        noTopics.removeTag("Topics");
        assertTrue(DiscordMessageLinkNbtCodec.read(noTopics).isReadOnly());
        NBTTagCompound before = writeTopics();
        before.removeTag("Topics");
        before.setInteger("DataVersion", 1);
        DiscordMessageLinkNbtCodec.ReadResult result = DiscordMessageLinkNbtCodec.read(before);
        assertTrue(result.isReadOnly());
        assertEquals(1, result.getUnsupportedVersion());
        assertTrue(result.getEntries().isEmpty());
        NBTTagCompound strings = writeTopics();
        NBTTagList odd = new NBTTagList();
        odd.appendTag(new NBTTagString("111"));
        strings.setTag("Topics", odd);
        assertTrue(DiscordMessageLinkNbtCodec.read(strings).isReadOnly());
        // A topic from a newer build makes the whole store read-only too.
        NBTTagCompound newer = writeTopics(topic("111", "Server offline", START, 0L));
        newer.getTagList("Topics", Constants.NBT.TAG_COMPOUND).getCompoundTagAt(0)
                .setInteger("DataVersion",
                        DiscordMessageLinkNbtCodec.CURRENT_TOPIC_DATA_VERSION + 1);
        assertTrue(DiscordMessageLinkNbtCodec.read(newer).isReadOnly());
    }

    /** A topic the save cannot vouch for is quarantined whole, where it stood, and the rest read. */
    @Test
    public void topicsTheSaveCannotVouchForAreQuarantined() {
        NBTTagCompound written = writeTopics(topic("111", "Server offline", START, 0L));
        NBTTagCompound good = written.getTagList("Topics", Constants.NBT.TAG_COMPOUND)
                .getCompoundTagAt(0);
        NBTTagList rewritten = new NBTTagList();
        rewritten.appendTag(good.copy());
        NBTTagCompound badChannel = (NBTTagCompound) good.copy();
        badChannel.setString("ChannelId", "https://evil.example/x");
        NBTTagCompound noWords = (NBTTagCompound) good.copy();
        noWords.setString("ChannelId", "222");
        noWords.setString("Words", "");
        NBTTagCompound longWords = (NBTTagCompound) good.copy();
        longWords.setString("ChannelId", "333");
        StringBuilder text = new StringBuilder();
        for (int index = 0; index <= DiscordMessageLinkNbtCodec.MAX_TOPIC_LENGTH; index++) {
            text.append('x');
        }
        longWords.setString("Words", text.toString());
        NBTTagCompound noTime = (NBTTagCompound) good.copy();
        noTime.setString("ChannelId", "444");
        noTime.setLong("LastWrite", 0L);
        NBTTagCompound laterBefore = (NBTTagCompound) good.copy();
        laterBefore.setString("ChannelId", "555");
        laterBefore.setLong("WriteBefore", START + 1L);
        NBTTagCompound missingField = (NBTTagCompound) good.copy();
        missingField.setString("ChannelId", "666");
        missingField.removeTag("WriteBefore");
        NBTTagCompound unversioned = (NBTTagCompound) good.copy();
        unversioned.setString("ChannelId", "777");
        unversioned.removeTag("DataVersion");
        NBTTagCompound sameChannel = (NBTTagCompound) good.copy();
        rewritten.appendTag(badChannel);
        rewritten.appendTag(noWords);
        rewritten.appendTag(longWords);
        rewritten.appendTag(noTime);
        rewritten.appendTag(laterBefore);
        rewritten.appendTag(missingField);
        rewritten.appendTag(unversioned);
        rewritten.appendTag(sameChannel);
        written.setTag("Topics", rewritten);

        DiscordMessageLinkNbtCodec.ReadResult result = DiscordMessageLinkNbtCodec.read(written);
        assertFalse(result.isReadOnly());
        assertTrue(result.wasRepaired());
        assertEquals(1, result.getEntries().size());
        assertEquals(1, result.getTopics().size());
        assertEquals("111", result.getTopics().get(0).channelId);
        List<NBTTagCompound> quarantine = result.getQuarantineEntriesCopy();
        assertEquals(8, quarantine.size());
        assertEquals("invalid_topic_channel", quarantine.get(0).getString("Reason"));
        assertEquals("invalid_topic_words", quarantine.get(1).getString("Reason"));
        assertEquals("invalid_topic_words", quarantine.get(2).getString("Reason"));
        assertEquals("invalid_topic_writes", quarantine.get(3).getString("Reason"));
        assertEquals("invalid_topic_writes", quarantine.get(4).getString("Reason"));
        assertEquals("invalid_topic_writes", quarantine.get(5).getString("Reason"));
        assertEquals("invalid_topic_version", quarantine.get(6).getString("Reason"));
        assertEquals("duplicate_topic_channel", quarantine.get(7).getString("Reason"));
        assertEquals(1, quarantine.get(0).getInteger("TopicIndex"));
        assertFalse(quarantine.get(0).hasKey("EntryIndex"));
        assertTrue(quarantine.get(0).hasKey("OriginalData", Constants.NBT.TAG_COMPOUND));

        // The quarantine rides along on the next write, and the rest
        // reads back clean.
        NBTTagCompound again = new NBTTagCompound();
        DiscordMessageLinkNbtCodec.write(again, result.getEntries(), result.getTopics(),
                quarantine);
        DiscordMessageLinkNbtCodec.ReadResult reread = DiscordMessageLinkNbtCodec.read(again);
        assertFalse(reread.wasRepaired());
        assertEquals(8, reread.getQuarantineEntriesCopy().size());
        assertEquals(1, reread.getTopics().size());
    }

    /** Topics past the bound are quarantined on read, and never written. */
    @Test
    public void theTopicBoundHolds() {
        int max = DiscordMessageLinkNbtCodec.MAX_TOPICS;
        NBTTagCompound written = writeTopics();
        NBTTagList many = new NBTTagList();
        NBTTagCompound one = writeTopics(topic("1000", "Server offline", START, 0L))
                .getTagList("Topics", Constants.NBT.TAG_COMPOUND).getCompoundTagAt(0);
        for (int index = 0; index < max + 2; index++) {
            NBTTagCompound entry = (NBTTagCompound) one.copy();
            entry.setString("ChannelId", Long.toString(1000L + index));
            many.appendTag(entry);
        }
        written.setTag("Topics", many);
        DiscordMessageLinkNbtCodec.ReadResult result = DiscordMessageLinkNbtCodec.read(written);
        assertEquals(max, result.getTopics().size());
        assertEquals(2, result.getQuarantineEntriesCopy().size());
        assertEquals("topics_over_capacity",
                result.getQuarantineEntriesCopy().get(0).getString("Reason"));

        List<DiscordTopicMemory.SavedTopic> topics =
                new ArrayList<DiscordTopicMemory.SavedTopic>();
        for (int index = 0; index < max + 2; index++) {
            topics.add(topic(Long.toString(1000L + index), "Server offline", START, 0L));
        }
        NBTTagCompound bounded = writeTopics(topics.toArray(
                new DiscordTopicMemory.SavedTopic[topics.size()]));
        assertEquals(max, bounded.getTagList("Topics", Constants.NBT.TAG_COMPOUND).tagCount());
        assertFalse(DiscordMessageLinkNbtCodec.read(bounded).wasRepaired());
    }

    /** A topic a read would refuse is not written, and a channel is written once. */
    @Test
    public void topicsAReadWouldRefuseAreNotWritten() {
        NBTTagCompound written = writeTopics(
                topic("111", "Server offline", START, START - 1L),
                topic("111", "Server online", START + 1L, 0L),
                topic("not-a-channel", "Server offline", START, 0L),
                topic("222", "", START, 0L),
                topic("333", "Server offline", 0L, 0L),
                topic("444", "Server offline", START, START + 1L));
        DiscordMessageLinkNbtCodec.ReadResult result = DiscordMessageLinkNbtCodec.read(written);
        assertFalse(result.wasRepaired());
        assertEquals(1, result.getTopics().size());
        assertEquals("Server offline", result.getTopics().get(0).words);
    }

    private static NBTTagCompound write(DiscordMessageLinks links) {
        NBTTagCompound written = new NBTTagCompound();
        DiscordMessageLinkNbtCodec.write(written, links.snapshot().links,
                NO_TOPICS, NO_QUARANTINE);
        return written;
    }

    /** A save holding one link and the topics given. */
    private static NBTTagCompound writeTopics(DiscordTopicMemory.SavedTopic... topics) {
        DiscordMessageLinks links = new DiscordMessageLinks();
        links.link(1000L, "111", "", "channel:5", "");
        NBTTagCompound written = new NBTTagCompound();
        DiscordMessageLinkNbtCodec.write(written, links.snapshot().links,
                Arrays.asList(topics), NO_QUARANTINE);
        return written;
    }

    private static DiscordTopicMemory.SavedTopic topic(String channelId, String words,
                                                       long lastWrite, long writeBefore) {
        return new DiscordTopicMemory.SavedTopic(channelId, words, lastWrite, writeBefore);
    }

    /** A copy of {@code entry} under another message id, its first copy under another Discord id. */
    private static NBTTagCompound entryLike(NBTTagCompound entry, long messageId,
                                            String discordId) {
        NBTTagCompound copy = (NBTTagCompound) entry.copy();
        copy.setLong("MessageId", messageId);
        firstCopyOf(copy).setString("DiscordId", discordId);
        return copy;
    }

    private static NBTTagCompound firstCopyOf(NBTTagCompound entry) {
        return entry.getTagList("Copies", Constants.NBT.TAG_COMPOUND)
                .getCompoundTagAt(0);
    }

    private static void assertNoWebhook(NBTTagCompound tag) {
        String text = tag.toString();
        assertFalse(text, text.contains("api/webhooks"));
        assertFalse(text, text.contains("SECRET-TOKEN"));
        assertFalse(text, text.contains("OTHER-TOKEN"));
        assertFalse(text, text.contains("evil.example"));
    }
}
