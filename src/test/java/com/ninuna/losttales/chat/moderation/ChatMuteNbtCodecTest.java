package com.ninuna.losttales.chat.moderation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ChatMuteNbtCodecTest {

    private static final UUID ACCOUNT_A =
            UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ACCOUNT_B =
            UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    public void roundTripKeepsEveryField() {
        ChatMuteEntry permanent = new ChatMuteEntry(ACCOUNT_A, "Aldric",
                "Operator", "spamming the square", 1000L,
                ChatMuteEntry.EXPIRES_NEVER);
        ChatMuteEntry timed = new ChatMuteEntry(ACCOUNT_B, "Beren",
                "", "", 2000L, 90000L);
        NBTTagCompound written = new NBTTagCompound();
        ChatMuteNbtCodec.write(written, Arrays.asList(permanent, timed),
                Collections.<NBTTagCompound>emptyList());

        ChatMuteNbtCodec.ReadResult result = ChatMuteNbtCodec.read(written);
        assertFalse(result.isReadOnly());
        assertFalse(result.wasRepaired());
        assertEquals(2, result.getMutes().size());
        ChatMuteEntry readBack = result.getMutes().get(ACCOUNT_A);
        assertEquals("Aldric", readBack.getAccountName());
        assertEquals("Operator", readBack.getMutedByName());
        assertEquals("spamming the square", readBack.getReason());
        assertEquals(1000L, readBack.getIssuedAtMillis());
        assertTrue(readBack.isPermanent());
        ChatMuteEntry timedBack = result.getMutes().get(ACCOUNT_B);
        assertEquals(90000L, timedBack.getExpiresAtMillis());
        assertFalse(timedBack.isPermanent());
        assertTrue(timedBack.isExpired(90000L));
        assertFalse(timedBack.isExpired(89999L));
    }

    @Test
    public void emptyCompoundReadsAsEmptyAndRepaired() {
        ChatMuteNbtCodec.ReadResult result =
                ChatMuteNbtCodec.read(new NBTTagCompound());
        assertFalse(result.isReadOnly());
        assertTrue(result.wasRepaired());
        assertTrue(result.getMutes().isEmpty());
    }

    @Test
    public void newerRootVersionIsReadOnlyAndPreservedVerbatim() {
        NBTTagCompound newer = new NBTTagCompound();
        newer.setInteger("DataVersion",
                ChatMuteNbtCodec.CURRENT_ROOT_DATA_VERSION + 1);
        newer.setString("FutureField", "kept");

        ChatMuteNbtCodec.ReadResult result = ChatMuteNbtCodec.read(newer);
        assertTrue(result.isReadOnly());
        assertEquals(ChatMuteNbtCodec.CURRENT_ROOT_DATA_VERSION + 1,
                result.getUnsupportedVersion());
        assertEquals("kept",
                result.getOriginalDataCopy().getString("FutureField"));
    }

    @Test
    public void newerVersionSurvivesARoundTripThroughTheStore() {
        NBTTagCompound newer = new NBTTagCompound();
        newer.setInteger("DataVersion",
                ChatMuteNbtCodec.CURRENT_ROOT_DATA_VERSION + 1);
        newer.setString("FutureField", "kept");

        ChatMuteWorldData store = new ChatMuteWorldData();
        store.readFromNBT(newer);
        assertTrue(store.isReadOnlyForNewerVersion());
        assertNull(store.getActiveMute(ACCOUNT_A, 0L));
        assertTrue(store.getActiveMutes(0L).isEmpty());

        NBTTagCompound writtenBack = new NBTTagCompound();
        store.writeToNBT(writtenBack);
        assertEquals(ChatMuteNbtCodec.CURRENT_ROOT_DATA_VERSION + 1,
                writtenBack.getInteger("DataVersion"));
        assertEquals("kept", writtenBack.getString("FutureField"));
    }

    @Test
    public void malformedEntryIsQuarantinedNeverDropped() {
        ChatMuteEntry valid = new ChatMuteEntry(ACCOUNT_A, "Aldric",
                "Operator", "", 1000L, ChatMuteEntry.EXPIRES_NEVER);
        NBTTagCompound written = new NBTTagCompound();
        ChatMuteNbtCodec.write(written, Arrays.asList(valid),
                Collections.<NBTTagCompound>emptyList());
        NBTTagCompound broken = new NBTTagCompound();
        broken.setInteger("DataVersion", ChatMuteEntry.CURRENT_DATA_VERSION);
        // No account UUID at all.
        written.getTagList("Mutes", Constants.NBT.TAG_COMPOUND)
                .appendTag(broken);

        ChatMuteNbtCodec.ReadResult result = ChatMuteNbtCodec.read(written);
        assertFalse(result.isReadOnly());
        assertTrue(result.wasRepaired());
        assertEquals(1, result.getMutes().size());
        assertEquals(1, result.getQuarantineEntriesCopy().size());
        assertEquals("missing_account",
                result.getQuarantineEntriesCopy().get(0).getString("Reason"));
    }

    @Test
    public void duplicateAccountKeepsTheLatestIssuedMute() {
        ChatMuteEntry older = new ChatMuteEntry(ACCOUNT_A, "Aldric",
                "OpOne", "first", 1000L, ChatMuteEntry.EXPIRES_NEVER);
        ChatMuteEntry newer = new ChatMuteEntry(ACCOUNT_A, "Aldric",
                "OpTwo", "second", 2000L, ChatMuteEntry.EXPIRES_NEVER);
        NBTTagCompound written = new NBTTagCompound();
        ChatMuteNbtCodec.write(written, Arrays.asList(older, newer),
                Collections.<NBTTagCompound>emptyList());

        ChatMuteNbtCodec.ReadResult result = ChatMuteNbtCodec.read(written);
        assertTrue(result.wasRepaired());
        assertEquals(1, result.getMutes().size());
        assertEquals("second", result.getMutes().get(ACCOUNT_A).getReason());
        assertEquals(1, result.getQuarantineEntriesCopy().size());
        assertEquals("duplicate_account_mute",
                result.getQuarantineEntriesCopy().get(0).getString("Reason"));
    }

    @Test
    public void entriesPastTheCapacityBoundAreQuarantined() {
        List<ChatMuteEntry> mutes = new ArrayList<ChatMuteEntry>();
        for (int index = 0; index < ChatMuteNbtCodec.MAX_MUTES + 3; index++) {
            mutes.add(new ChatMuteEntry(new UUID(0L, index + 1L),
                    "Player" + index, "", "", index,
                    ChatMuteEntry.EXPIRES_NEVER));
        }
        NBTTagCompound written = new NBTTagCompound();
        ChatMuteNbtCodec.write(written, mutes,
                Collections.<NBTTagCompound>emptyList());

        ChatMuteNbtCodec.ReadResult result = ChatMuteNbtCodec.read(written);
        assertFalse(result.isReadOnly());
        assertTrue(result.wasRepaired());
        assertEquals(ChatMuteNbtCodec.MAX_MUTES, result.getMutes().size());
        assertEquals(3, result.getQuarantineEntriesCopy().size());
    }

    @Test
    public void storeExpiresLazilyAndRefusesPastTheCap() {
        ChatMuteWorldData store = new ChatMuteWorldData();
        store.readFromNBT(new NBTTagCompound());
        ChatMuteEntry timed = new ChatMuteEntry(ACCOUNT_A, "Aldric",
                "Operator", "", 0L, 5000L);
        assertTrue(store.mute(timed));
        assertEquals(timed, store.getActiveMute(ACCOUNT_A, 4999L));
        // Expiry drops the entry the moment a check passes it.
        assertNull(store.getActiveMute(ACCOUNT_A, 5000L));
        assertEquals(0, store.getMuteCount());

        for (int index = 0; index < ChatMuteNbtCodec.MAX_MUTES; index++) {
            assertTrue(store.mute(new ChatMuteEntry(new UUID(1L, index + 1L),
                    "Player" + index, "", "", index,
                    ChatMuteEntry.EXPIRES_NEVER)));
        }
        assertFalse(store.mute(new ChatMuteEntry(ACCOUNT_B, "Beren",
                "", "", 0L, ChatMuteEntry.EXPIRES_NEVER)));
        // Replacing an existing mute is never a capacity question.
        assertTrue(store.mute(new ChatMuteEntry(new UUID(1L, 1L),
                "Player0", "", "renewed", 99L, ChatMuteEntry.EXPIRES_NEVER)));
        // Unmuting by the stored name works after the player left.
        ChatMuteEntry lifted = store.unmuteByName("player0");
        assertEquals("renewed", lifted.getReason());
    }
}
