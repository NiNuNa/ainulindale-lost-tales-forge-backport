package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatMessageIds;
import java.util.ArrayList;
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
        // A game line posted through two bindings: one into a channel
        // Discord named, one through a webhook whose channel it never did.
        links.link(1000L, "111", "-# ↩ header\n", "channel:5", WEBHOOK, "ooc");
        links.link(1000L, "222", "", OTHER_WEBHOOK, OTHER_WEBHOOK, "ooc#2");
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
        assertEquals("333", restored.discordIdOf(2000L, "channel:5"));
        List<DiscordMessageLinks.Copy> copies = restored.copiesOf(1000L);
        assertEquals(2, copies.size());
        assertEquals("-# ↩ header\n", copies.get(0).header);
        assertEquals("ooc", copies.get(0).bindingId);
        assertEquals("", copies.get(0).webhookUrl);
        // The copy whose channel was never learnt comes back under its binding.
        assertEquals("binding:ooc#2", copies.get(1).destination);
        assertEquals("ooc#2", copies.get(1).bindingId);
        assertEquals("", copies.get(1).webhookUrl);
        assertEquals("", restored.copiesOf(2000L).get(0).bindingId);
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
        links.link(2000L, "222", "", WEBHOOK, WEBHOOK, "ooc");
        // A post named by neither a channel nor a binding is left out.
        links.link(3000L, "333", "", OTHER_WEBHOOK, OTHER_WEBHOOK, "");
        // So is one under a binding id that could pass for a URL.
        links.link(4000L, "444", "", OTHER_WEBHOOK, OTHER_WEBHOOK,
                "https://evil.example/x");

        NBTTagCompound written = write(links);
        assertNoWebhook(written);
        assertEquals(2, DiscordMessageLinkNbtCodec.read(written).getEntries().size());
        // The store writes nothing else, attached or after the stop.
        DiscordMessageLinkWorldData data = new DiscordMessageLinkWorldData();
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
        DiscordMessageLinkWorldData data = new DiscordMessageLinkWorldData();
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
        // Kept under a binding it does not name.
        NBTTagCompound unnamedBinding = entryLike(second, 6000L, "600");
        firstCopyOf(unnamedBinding).setString("Destination", "binding:ooc");
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
        rewritten.appendTag(unnamedBinding);
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
        assertEquals("invalid_binding", quarantine.get(3).getString("Reason"));
        assertEquals("missing_copies", quarantine.get(4).getString("Reason"));
        assertEquals("duplicate_discord_id", quarantine.get(5).getString("Reason"));
        assertEquals("duplicate_message", quarantine.get(6).getString("Reason"));
        // Quarantined whole, where it stood.
        assertEquals(3, quarantine.get(1).getInteger("EntryIndex"));
        assertTrue(quarantine.get(1).hasKey("OriginalData", Constants.NBT.TAG_COMPOUND));

        // The quarantine rides along on the next write, and the rest
        // reads back clean.
        NBTTagCompound again = new NBTTagCompound();
        DiscordMessageLinkNbtCodec.write(again, result.getEntries(), quarantine);
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
        DiscordMessageLinkNbtCodec.write(written, many, NO_QUARANTINE);
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
                new DiscordMessageLinks.SavedLink(1000L, copies)), NO_QUARANTINE);
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
    public void aMissingSaveReadsAsNothingKept() {
        DiscordMessageLinkNbtCodec.ReadResult result =
                DiscordMessageLinkNbtCodec.read(null);
        assertFalse(result.isReadOnly());
        assertTrue(result.getEntries().isEmpty());
        assertTrue(result.getQuarantineEntriesCopy().isEmpty());
    }

    private static NBTTagCompound write(DiscordMessageLinks links) {
        NBTTagCompound written = new NBTTagCompound();
        DiscordMessageLinkNbtCodec.write(written, links.snapshot().links,
                NO_QUARANTINE);
        return written;
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
