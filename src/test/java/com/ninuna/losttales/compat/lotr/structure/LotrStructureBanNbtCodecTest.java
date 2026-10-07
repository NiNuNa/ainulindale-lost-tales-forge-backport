package com.ninuna.losttales.compat.lotr.structure;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The structure bans as the world saves them: on an account, which
 * reaches every character it plays, or on one character. Only this
 * build's version is read; a ban that cannot be read, or one too many,
 * is set aside, never dropped.
 */
public final class LotrStructureBanNbtCodecTest {

    private static final UUID ACCOUNT = UUID.fromString("c1000000-0000-0000-0000-00000000001c");
    private static final UUID CHARACTER = UUID.fromString("c2000000-0000-0000-0000-00000000002c");
    private static final UUID OTHER = UUID.fromString("c3000000-0000-0000-0000-00000000003c");

    private static NBTTagCompound saved(LotrStructureBan... bans) {
        NBTTagCompound root = new NBTTagCompound();
        LotrStructureBanNbtCodec.write(root, Arrays.asList(bans),
                Collections.<NBTTagCompound>emptyList());
        return root;
    }

    @Test
    public void bansOnAccountsAndCharactersRoundTrip() {
        LotrStructureBanWorldData data = new LotrStructureBanWorldData(
                LotrStructureBanWorldData.DATA_NAME);
        data.readFromNBT(saved(
                new LotrStructureBan(LotrStructureBan.Kind.ACCOUNT, ACCOUNT, "Nils", "Server", 5L),
                new LotrStructureBan(LotrStructureBan.Kind.CHARACTER, CHARACTER, "Aldric", "Nils", 6L)));
        assertFalse(data.isReadOnly());
        // An account's ban reaches every character it plays.
        assertTrue(data.isBanned(ACCOUNT, OTHER));
        assertTrue(data.isBanned(ACCOUNT, null));
        // A character's reaches that character alone, whoever plays it.
        assertTrue(data.isBanned(OTHER, CHARACTER));
        assertFalse(data.isBanned(OTHER, OTHER));
        assertFalse(data.isBanned(OTHER, null));
        LotrStructureBan aldric = data.find(LotrStructureBan.Kind.CHARACTER, CHARACTER);
        assertEquals("Aldric", aldric.getName());
        assertEquals("Nils", aldric.getBannedBy());
        assertEquals(6L, aldric.getBannedAtMillis());

        NBTTagCompound again = new NBTTagCompound();
        data.writeToNBT(again);
        LotrStructureBanWorldData read = new LotrStructureBanWorldData(
                LotrStructureBanWorldData.DATA_NAME);
        read.readFromNBT(again);
        assertEquals(2, read.all().size());
        assertNotNull(read.allow(LotrStructureBan.Kind.ACCOUNT, ACCOUNT));
        assertFalse(read.isBanned(ACCOUNT, OTHER));
        assertNull(read.allow(LotrStructureBan.Kind.ACCOUNT, ACCOUNT));
    }

    @Test
    public void aSecondBanOnTheSameOneIsRefused() {
        LotrStructureBanWorldData data = new LotrStructureBanWorldData(
                LotrStructureBanWorldData.DATA_NAME);
        assertTrue(data.ban(new LotrStructureBan(LotrStructureBan.Kind.ACCOUNT, ACCOUNT,
                "Nils", "Server", 1L)));
        assertFalse(data.ban(new LotrStructureBan(LotrStructureBan.Kind.ACCOUNT, ACCOUNT,
                "Nils", "Server", 2L)));
        // The same id as a character is a different ban.
        assertTrue(data.ban(new LotrStructureBan(LotrStructureBan.Kind.CHARACTER, ACCOUNT,
                "Odd", "Server", 3L)));
    }

    @Test
    public void anotherVersionIsKeptAsItIsAndBansNobody() {
        NBTTagCompound root = saved(new LotrStructureBan(
                LotrStructureBan.Kind.ACCOUNT, ACCOUNT, "Nils", "Server", 5L));
        NBTTagCompound newer = (NBTTagCompound) root.copy();
        newer.setInteger("DataVersion", 2);
        NBTTagCompound unnamed = (NBTTagCompound) root.copy();
        unnamed.removeTag("DataVersion");
        for (NBTTagCompound kept : Arrays.asList(newer, unnamed)) {
            LotrStructureBanWorldData data = new LotrStructureBanWorldData(
                    LotrStructureBanWorldData.DATA_NAME);
            data.readFromNBT((NBTTagCompound) kept.copy());
            assertTrue(data.isReadOnly());
            assertFalse(data.isBanned(ACCOUNT, null));
            NBTTagCompound written = new NBTTagCompound();
            data.writeToNBT(written);
            assertEquals(kept, written);
            try {
                data.allow(LotrStructureBan.Kind.ACCOUNT, ACCOUNT);
                throw new AssertionError("a read-only store took a change");
            } catch (IllegalStateException expected) {
                // Read-only.
            }
        }
    }

    @Test
    public void unreadableAndRepeatedBansAreSetAside() {
        NBTTagCompound root = saved(
                new LotrStructureBan(LotrStructureBan.Kind.ACCOUNT, ACCOUNT, "Nils", "Server", 5L),
                new LotrStructureBan(LotrStructureBan.Kind.ACCOUNT, ACCOUNT, "Nils", "Server", 6L),
                new LotrStructureBan(LotrStructureBan.Kind.CHARACTER, CHARACTER, "Aldric", "Nils", 7L));
        NBTTagList bans = root.getTagList("Bans", Constants.NBT.TAG_COMPOUND);
        bans.getCompoundTagAt(2).setString("Kind", "guild");
        LotrStructureBanNbtCodec.ReadResult result = LotrStructureBanNbtCodec.read(root);
        assertFalse(result.isReadOnly());
        assertTrue(result.wasRepaired());
        assertEquals(1, result.getBans().size());
        assertEquals(2, result.getQuarantined().size());
        assertEquals("duplicate_ban", result.getQuarantined().get(0).getString("Reason"));
        assertEquals("malformed_ban", result.getQuarantined().get(1).getString("Reason"));
    }

    @Test
    public void bansPastTheBoundAreSetAside() {
        LotrStructureBan[] many = new LotrStructureBan[LotrStructureBanNbtCodec.MAX_BANS + 1];
        for (int index = 0; index < many.length; index++) {
            many[index] = new LotrStructureBan(LotrStructureBan.Kind.CHARACTER,
                    new UUID(1L, index), "C" + index, "Server", index);
        }
        LotrStructureBanNbtCodec.ReadResult result = LotrStructureBanNbtCodec.read(saved(many));
        assertEquals(LotrStructureBanNbtCodec.MAX_BANS, result.getBans().size());
        assertEquals(1, result.getQuarantined().size());
        assertEquals("over_capacity", result.getQuarantined().get(0).getString("Reason"));
    }
}
