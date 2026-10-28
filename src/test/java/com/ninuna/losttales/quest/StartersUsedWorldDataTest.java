package com.ninuna.losttales.quest;

import com.ninuna.losttales.storage.NbtQuarantine;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The world's record of quest starters used (QUEST-34): a starter starts
 * its quest once, by anyone, copies included; the record outlasts a save,
 * refuses past its bound and fails closed on data it cannot read.
 */
public final class StartersUsedWorldDataTest {
    private static StartersUsedWorldData data() {
        return new StartersUsedWorldData(StartersUsedWorldData.DATA_NAME);
    }

    private static StartersUsedWorldData reloaded(StartersUsedWorldData data) {
        NBTTagCompound tag = new NBTTagCompound();
        data.writeToNBT(tag);
        StartersUsedWorldData read = data();
        read.readFromNBT(tag);
        return read;
    }

    private static byte[] nonce(int seed) {
        byte[] nonce = new byte[QuestStarters.NONCE_BYTES];
        for (int index = 0; index < nonce.length; index++) {
            nonce[index] = (byte) (seed >> (index % 4 * 8));
        }
        nonce[nonce.length - 1] = (byte) 0xF7;
        return nonce;
    }

    @Test
    public void aStarterIsUsedOnceAndRemembered() {
        StartersUsedWorldData data = data();
        assertFalse(data.wasUsed(nonce(1)));
        assertTrue(data.use(nonce(1)));
        assertTrue("a copy carries the same nonce", data.wasUsed(nonce(1).clone()));
        StartersUsedWorldData read = reloaded(data);
        assertTrue(read.isUsable());
        assertTrue("a save keeps it", read.wasUsed(nonce(1)));
        assertFalse(read.wasUsed(nonce(2)));
    }

    @Test
    public void aNonceNoStarterCarriesIsRefused() {
        StartersUsedWorldData data = data();
        assertFalse(data.use(null));
        assertFalse(data.use(new byte[3]));
        assertFalse(data.wasUsed(new byte[3]));
        assertEquals(0, data.size());
    }

    /** Past its bound the record refuses a new starter and forgets none it holds. */
    @Test
    public void theRecordRefusesPastItsBound() {
        StartersUsedWorldData data = data();
        for (int index = 0; index < StartersUsedWorldData.MAX_ENTRIES; index++) {
            assertTrue(data.use(nonce(index)));
        }
        assertFalse("a full record takes no more",
                data.use(nonce(StartersUsedWorldData.MAX_ENTRIES)));
        assertEquals(StartersUsedWorldData.MAX_ENTRIES, data.size());
        assertTrue("the oldest is still remembered", data.wasUsed(nonce(0)));
        assertTrue("one used already is used still", data.use(nonce(0)));
    }

    /** Data of another version is kept as it was, and no starter starts anything meanwhile. */
    @Test
    public void unreadableDataFailsClosedAndIsKeptVerbatim() {
        NBTTagCompound newer = new NBTTagCompound();
        newer.setInteger(StartersUsedWorldData.TAG_VERSION,
                StartersUsedWorldData.DATA_VERSION + 1);
        newer.setString("Future", "kept");
        StartersUsedWorldData data = data();
        data.readFromNBT(newer);
        assertFalse(data.isUsable());
        assertFalse(data.use(nonce(1)));
        NBTTagCompound written = new NBTTagCompound();
        data.writeToNBT(written);
        assertEquals(newer, written);
    }

    /**
     * An entry that cannot be read, or a second for one starter, is set
     * aside in the shared quarantine with its reason and place, kept whole,
     * and the rest still read (DATA-10).
     */
    @Test
    public void aBrokenEntryIsQuarantined() {
        StartersUsedWorldData data = data();
        data.use(nonce(1));
        NBTTagCompound tag = new NBTTagCompound();
        data.writeToNBT(tag);
        NBTTagCompound broken = new NBTTagCompound();
        broken.setByteArray(StartersUsedWorldData.TAG_NONCE, new byte[5]);
        NBTTagCompound twice = new NBTTagCompound();
        twice.setByteArray(StartersUsedWorldData.TAG_NONCE, nonce(1));
        tag.getTagList(StartersUsedWorldData.TAG_USED, 10).appendTag(broken);
        tag.getTagList(StartersUsedWorldData.TAG_USED, 10).appendTag(twice);
        StartersUsedWorldData read = data();
        read.readFromNBT(tag);
        assertTrue(read.isUsable());
        assertTrue(read.wasUsed(nonce(1)));
        assertEquals(1, read.size());
        NBTTagCompound again = new NBTTagCompound();
        read.writeToNBT(again);
        List<NBTTagCompound> aside = NbtQuarantine.read(again).getEntries();
        assertEquals(2, aside.size());
        assertEquals("unreadable_nonce", aside.get(0).getString("Reason"));
        assertEquals(1, aside.get(0).getInteger(StartersUsedWorldData.TAG_USED_INDEX));
        assertEquals(broken, aside.get(0).getCompoundTag("OriginalData"));
        assertEquals("duplicate_starter", aside.get(1).getString("Reason"));

        StartersUsedWorldData kept = data();
        kept.readFromNBT(again);
        assertTrue("what was set aside stays aside", kept.isUsable());
        NBTTagCompound third = new NBTTagCompound();
        kept.writeToNBT(third);
        assertEquals(2, NbtQuarantine.read(third).getEntries().size());
    }

    /** The gate asks the record before a starter starts anything, and records it as it starts. */
    @Test
    public void theGateAsksAndRecords() throws IOException {
        String gate = new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/ninuna/losttales/quest/QuestStartGate.java")),
                StandardCharsets.UTF_8);
        assertTrue(gate.contains("StartersUsed.standing(player.worldObj"));
        assertTrue(gate.contains("StartersUsed.record(player.worldObj"));
        assertTrue(gate.indexOf("StartersUsed.record(player.worldObj")
                < gate.indexOf("useUp(player, proof.getStack())"));
    }
}
