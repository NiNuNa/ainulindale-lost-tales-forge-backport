package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The kept history round-trips through the save exactly, its audiences
 * included; a line the save cannot vouch for is quarantined rather than
 * shown, and data from a newer build is left alone.
 */
public final class ChatHistoryNbtCodecTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID PARTY = UUID.fromString("00000000-0000-0000-0000-0000000000cc");
    private static final List<ChatChannel> EVERY_CHANNEL =
            Arrays.asList(ChatChannel.values());

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
    public void everyKindOfLineRoundTripsWithItsAudience() {
        long global = ChatMessageIdAllocator.next();
        ChatHistory.record(global, ALICE, "Aldric", null,
                line(global, ChatChannel.ALL, ALICE, "hail", ""),
                Arrays.asList(ALICE, BOB), ChatHistory.Audience.everyone());
        long whisper = ChatMessageIdAllocator.next();
        LostTalesChatMessagePacket own = line(whisper, ChatChannel.WHISPER, ALICE,
                "the vault code is 4417", "bob");
        ChatHistory.record(whisper, ALICE, "Aldric", own,
                own.withPartner("alice", "Aldric"),
                Arrays.asList(ALICE, BOB),
                ChatHistory.Audience.accounts(Arrays.asList(ALICE, BOB), false));
        long party = ChatMessageIdAllocator.next();
        ChatHistory.record(party, BOB, "Beren", null,
                line(party, ChatChannel.PARTY, BOB, "form up", ""),
                Arrays.asList(ALICE, BOB),
                ChatHistory.Audience.party(PARTY, Arrays.asList(ALICE, BOB)));
        long faction = ChatMessageIdAllocator.next();
        ChatHistory.record(faction, ALICE, "Aldric", null,
                line(faction, ChatChannel.FACTION, ALICE, "the gate holds", "")
                        .withScope("gondor"),
                Arrays.asList(ALICE),
                ChatHistory.Audience.faction("gondor", Arrays.asList(ALICE), true));
        List<ChatHistory.Entry> before = ChatHistory.snapshot();

        NBTTagCompound written = new NBTTagCompound();
        ChatHistoryNbtCodec.write(written, before,
                Collections.<NBTTagCompound>emptyList());
        ChatHistoryNbtCodec.ReadResult result = ChatHistoryNbtCodec.read(written);
        assertFalse(result.isReadOnly());
        assertFalse(result.wasRepaired());
        assertTrue(result.getQuarantineEntriesCopy().isEmpty());
        assertEquals(4, result.getEntries().size());

        // Restored into a clean history, the same questions get the same
        // answers as before the trip.
        ChatHistory.clear();
        ChatMessageIdAllocator.reset();
        assertEquals(4, ChatHistory.restore(result.getEntries()));
        assertEquals(4, ChatHistory.size());
        // The allocator has moved past the newest kept id.
        assertTrue(ChatMessageIdAllocator.next() > faction);

        List<LostTalesChatMessagePacket> bobSees = ChatHistory.replayFor(
                new ChatHistory.Requester(BOB, "", 0L, PARTY, EVERY_CHANNEL), 0L);
        assertEquals(3, bobSees.size());
        assertEquals("hail", bobSees.get(0).getMessage());
        // Bob is handed the partner's copy of the whisper, not Alice's own.
        assertEquals("the vault code is 4417", bobSees.get(1).getMessage());
        assertEquals("alice", bobSees.get(1).getPartner());
        assertEquals("form up", bobSees.get(2).getMessage());
        // Alice, out of the party and its faction line's gate, is handed her
        // own copies: the faction line is hers by a character made in time.
        List<LostTalesChatMessagePacket> aliceSees = ChatHistory.replayFor(
                new ChatHistory.Requester(ALICE, "gondor", 0L, null, EVERY_CHANNEL), 0L);
        assertEquals(3, aliceSees.size());
        // Alice's own copy of the whisper, the one naming Bob as its partner.
        assertEquals("bob", aliceSees.get(1).getPartner());
        assertEquals("the gate holds", aliceSees.get(2).getMessage());
        // A stranger with nothing: the open line alone.
        assertEquals(1, ChatHistory.replayFor(new ChatHistory.Requester(
                UUID.randomUUID(), "", 0L, null, EVERY_CHANNEL), 0L).size());
        // The whisper still quotes back into its own conversation only.
        assertTrue(ChatHistory.quoteFor(whisper, BOB, ChatChannel.WHISPER, "").exists());
        assertFalse(ChatHistory.quoteFor(whisper, BOB, ChatChannel.ALL, "").exists());
    }

    @Test
    public void aLineTheSaveCannotVouchForIsQuarantined() {
        long id = ChatMessageIdAllocator.next();
        ChatHistory.record(id, ALICE, "Aldric", null,
                line(id, ChatChannel.ALL, ALICE, "hail", ""),
                Arrays.asList(ALICE), ChatHistory.Audience.everyone());
        NBTTagCompound written = new NBTTagCompound();
        ChatHistoryNbtCodec.write(written, ChatHistory.snapshot(),
                Collections.<NBTTagCompound>emptyList());
        NBTTagList entries = written.getTagList("Entries", Constants.NBT.TAG_COMPOUND);
        NBTTagCompound good = entries.getCompoundTagAt(0);

        // The same line again under another id: the bytes name the first.
        NBTTagCompound wrongId = (NBTTagCompound)good.copy();
        wrongId.setLong("MessageId", id + 1);
        // A line whose bytes decode to nothing.
        NBTTagCompound garbage = (NBTTagCompound)good.copy();
        garbage.setLong("MessageId", id + 2);
        garbage.setByteArray("ForOthers", new byte[] {1, 2, 3});
        // A line with no audience at all, under the good line's own id:
        // the line itself is sound, so it is the audience that fails it.
        NBTTagCompound noAudience = (NBTTagCompound)good.copy();
        noAudience.removeTag("Audience");
        // The good line twice.
        NBTTagList rewritten = new NBTTagList();
        rewritten.appendTag(good.copy());
        rewritten.appendTag(wrongId);
        rewritten.appendTag(garbage);
        rewritten.appendTag(noAudience);
        rewritten.appendTag(good.copy());
        written.setTag("Entries", rewritten);

        ChatHistoryNbtCodec.ReadResult result = ChatHistoryNbtCodec.read(written);
        assertFalse(result.isReadOnly());
        assertTrue(result.wasRepaired());
        assertEquals(1, result.getEntries().size());
        assertEquals(4, result.getQuarantineEntriesCopy().size());
        assertEquals("invalid_line",
                result.getQuarantineEntriesCopy().get(0).getString("Reason"));
        assertEquals("invalid_line",
                result.getQuarantineEntriesCopy().get(1).getString("Reason"));
        assertEquals("missing_audience",
                result.getQuarantineEntriesCopy().get(2).getString("Reason"));
        assertEquals("duplicate_message",
                result.getQuarantineEntriesCopy().get(3).getString("Reason"));
        // The quarantine rides along verbatim on the next write.
        NBTTagCompound again = new NBTTagCompound();
        ChatHistoryNbtCodec.write(again, result.getEntries(),
                result.getQuarantineEntriesCopy());
        assertEquals(4, ChatHistoryNbtCodec.read(again)
                .getQuarantineEntriesCopy().size());
    }

    @Test
    public void newerDataIsPreservedAndReadOnly() {
        NBTTagCompound written = new NBTTagCompound();
        written.setInteger("DataVersion", ChatHistoryNbtCodec.CURRENT_ROOT_DATA_VERSION + 1);
        written.setString("Future", "something this build does not know");
        ChatHistoryNbtCodec.ReadResult result = ChatHistoryNbtCodec.read(written);
        assertTrue(result.isReadOnly());
        assertEquals(ChatHistoryNbtCodec.CURRENT_ROOT_DATA_VERSION + 1,
                result.getUnsupportedVersion());
        assertEquals("something this build does not know",
                result.getOriginalDataCopy().getString("Future"));
        assertTrue(result.getEntries().isEmpty());

        // An entry from a newer build makes the whole store read-only too.
        long id = ChatMessageIdAllocator.next();
        ChatHistory.record(id, ALICE, "Aldric", null,
                line(id, ChatChannel.ALL, ALICE, "hail", ""),
                Arrays.asList(ALICE), ChatHistory.Audience.everyone());
        NBTTagCompound current = new NBTTagCompound();
        ChatHistoryNbtCodec.write(current, ChatHistory.snapshot(),
                Collections.<NBTTagCompound>emptyList());
        current.getTagList("Entries", Constants.NBT.TAG_COMPOUND)
                .getCompoundTagAt(0).setInteger("DataVersion",
                        ChatHistoryNbtCodec.CURRENT_ENTRY_DATA_VERSION + 1);
        assertTrue(ChatHistoryNbtCodec.read(current).isReadOnly());
    }

    @Test
    public void anEmptySaveReadsAsNothingKept() {
        ChatHistoryNbtCodec.ReadResult result = ChatHistoryNbtCodec.read(null);
        assertFalse(result.isReadOnly());
        assertTrue(result.getEntries().isEmpty());
        assertNull(result.getOriginalDataCopy());
        assertEquals(0, ChatHistory.restore(result.getEntries()));
    }

    @Test
    public void reactionsRoundTripAndOnlyAReactedLineWearsTheNewerLayout() {
        long plain = ChatMessageIdAllocator.next();
        ChatHistory.record(plain, ALICE, "Aldric", null,
                line(plain, ChatChannel.ALL, ALICE, "hail", ""),
                Arrays.asList(ALICE, BOB), ChatHistory.Audience.everyone());
        long reacted = ChatMessageIdAllocator.next();
        ChatHistory.record(reacted, ALICE, "Aldric", null,
                line(reacted, ChatChannel.ALL, ALICE, "well met", ""),
                Arrays.asList(ALICE, BOB), ChatHistory.Audience.everyone());
        ChatHistory.Requester bob = new ChatHistory.Requester(BOB, "", 0L,
                null, EVERY_CHANNEL);
        ChatHistory.react(reacted, bob, BOB, "Beren", "smile", true);
        ChatHistory.react(reacted, null,
                LostTalesChatMessagePacket.discordSenderId("42"), "Nils",
                "joy", true);

        NBTTagCompound written = new NBTTagCompound();
        ChatHistoryNbtCodec.write(written, ChatHistory.snapshot(),
                Collections.<NBTTagCompound>emptyList());
        NBTTagList entries = written.getTagList("Entries",
                Constants.NBT.TAG_COMPOUND);
        assertEquals(1, entries.getCompoundTagAt(0).getInteger("DataVersion"));
        assertEquals(ChatHistoryNbtCodec.CURRENT_ENTRY_DATA_VERSION,
                entries.getCompoundTagAt(1).getInteger("DataVersion"));

        ChatHistoryNbtCodec.ReadResult result = ChatHistoryNbtCodec.read(written);
        assertFalse(result.isReadOnly());
        assertTrue(result.getQuarantineEntriesCopy().isEmpty());
        ChatHistory.clear();
        ChatHistory.restore(result.getEntries());
        assertTrue(ChatHistory.reactionsFor(reacted, BOB).find("smile").mine);
        assertEquals("Nils",
                ChatHistory.reactionsFor(reacted, BOB).find("joy").names.get(0));
        assertTrue(ChatHistory.reactionsFor(plain, BOB).isEmpty());
    }

    @Test
    public void unreadableReactionsQuarantineTheLineWhole() {
        long reacted = ChatMessageIdAllocator.next();
        ChatHistory.record(reacted, ALICE, "Aldric", null,
                line(reacted, ChatChannel.ALL, ALICE, "well met", ""),
                Arrays.asList(ALICE, BOB), ChatHistory.Audience.everyone());
        ChatHistory.react(reacted, new ChatHistory.Requester(BOB, "", 0L,
                null, EVERY_CHANNEL), BOB, "Beren", "smile", true);
        NBTTagCompound written = new NBTTagCompound();
        ChatHistoryNbtCodec.write(written, ChatHistory.snapshot(),
                Collections.<NBTTagCompound>emptyList());
        written.getTagList("Entries", Constants.NBT.TAG_COMPOUND)
                .getCompoundTagAt(0).getTagList("Reactions",
                        Constants.NBT.TAG_COMPOUND)
                .getCompoundTagAt(0).setString("Emoji", "not_an_emoji");
        ChatHistoryNbtCodec.ReadResult result = ChatHistoryNbtCodec.read(written);
        assertTrue(result.getEntries().isEmpty());
        assertEquals(1, result.getQuarantineEntriesCopy().size());
    }

    private static LostTalesChatMessagePacket line(long id, ChatChannel channel,
                                                   UUID author, String text,
                                                   String partner) {
        return new LostTalesChatMessagePacket(channel, author, "Aldric", "alice", "",
                0, 0, text, 1000000L, "", null, "", partner, 0, false, id,
                ChatReplyReference.NONE, "");
    }
}
