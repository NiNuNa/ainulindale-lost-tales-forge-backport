package com.ninuna.losttales.fellowship.storage;

import com.ninuna.losttales.fellowship.model.FellowshipInvitation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Invitations read back as written, and the codec fails closed: data of
 * another version keeps the store read-only, an invitation missing a key it
 * needs goes to the quarantine rather than being filled in, and so does one
 * past the most a fellowship has open or the most waiting for one account.
 */
public final class FellowshipInvitationNbtCodecTest {

    private static final UUID INVITATION = UUID.fromString(
            "c1000000-0000-0000-0000-00000000001c");

    private static NBTTagCompound written() {
        return writtenWith(Collections.<NBTTagCompound>emptyList());
    }

    private static NBTTagCompound writtenWith(List<NBTTagCompound> quarantine) {
        FellowshipInvitation invitation = new FellowshipInvitation(INVITATION,
                UUID.fromString("c2000000-0000-0000-0000-00000000002c"),
                UUID.fromString("c3000000-0000-0000-0000-00000000003c"),
                UUID.fromString("c4000000-0000-0000-0000-00000000004c"),
                "Aldric",
                UUID.fromString("c5000000-0000-0000-0000-00000000005c"),
                UUID.fromString("c6000000-0000-0000-0000-00000000006c"),
                "Beren", 1000L, 2000L);
        NBTTagCompound root = new NBTTagCompound();
        FellowshipInvitationNbtCodec.write(root,
                Collections.singletonList(invitation), quarantine);
        return root;
    }

    @Test
    public void anInvitationReadsBackAsWritten() {
        FellowshipInvitationNbtCodec.ReadResult read =
                FellowshipInvitationNbtCodec.read(written());
        assertFalse(read.isReadOnly());
        assertFalse(read.wasRepaired());
        assertEquals("Beren", read.getInvitations().get(INVITATION)
                .getTargetCharacterName());
    }

    @Test
    public void anotherVersionKeepsTheStoreReadOnly() {
        NBTTagCompound older = written();
        older.removeTag("DataVersion");
        assertTrue(FellowshipInvitationNbtCodec.read(older).isReadOnly());
        NBTTagCompound entry = written();
        NBTTagList list = entry.getTagList("Invitations", 10);
        list.getCompoundTagAt(0).setInteger("DataVersion", 0);
        assertTrue(FellowshipInvitationNbtCodec.read(entry).isReadOnly());
    }

    /** The oldest invitations of a fellowship stand; one past its cap goes to the quarantine. */
    @Test
    public void anInvitationPastAFellowshipsCapIsQuarantined() {
        UUID fellowship = new UUID(2L, 2L);
        List<FellowshipInvitation> invitations = new ArrayList<FellowshipInvitation>();
        for (int index = 0; index <= FellowshipInvitation.MAX_PER_FELLOWSHIP; index++) {
            invitations.add(invitation(index, fellowship, new UUID(500L, index)));
        }
        FellowshipInvitationNbtCodec.ReadResult read =
                FellowshipInvitationNbtCodec.read(written(invitations));

        assertEquals(FellowshipInvitation.MAX_PER_FELLOWSHIP, read.getInvitations().size());
        assertTrue(read.wasRepaired());
        assertEquals("too_many_invitations_for_fellowship",
                read.getQuarantineEntriesCopy().get(0).getString("Reason"));
        assertTrue("the newest goes", !read.getInvitations().containsKey(
                new UUID(900L, FellowshipInvitation.MAX_PER_FELLOWSHIP)));
    }

    /** One account has at most so many invitations waiting, from every fellowship together. */
    @Test
    public void anInvitationPastAnAccountsCapIsQuarantined() {
        UUID account = new UUID(6L, 6L);
        List<FellowshipInvitation> invitations = new ArrayList<FellowshipInvitation>();
        for (int index = 0; index <= FellowshipInvitation.MAX_PER_TARGET_ACCOUNT; index++) {
            invitations.add(invitation(index, new UUID(300L, index), account));
        }
        FellowshipInvitationNbtCodec.ReadResult read =
                FellowshipInvitationNbtCodec.read(written(invitations));

        assertEquals(FellowshipInvitation.MAX_PER_TARGET_ACCOUNT, read.getInvitations().size());
        assertEquals("too_many_invitations_for_account",
                read.getQuarantineEntriesCopy().get(0).getString("Reason"));
    }

    private static FellowshipInvitation invitation(int index, UUID fellowship, UUID targetAccount) {
        return new FellowshipInvitation(new UUID(900L, index), fellowship,
                new UUID(100L, 1L), new UUID(100L, 2L), "Aldric",
                new UUID(700L, index), targetAccount, "Beren", 1000L + index, 90000L);
    }

    private static NBTTagCompound written(List<FellowshipInvitation> invitations) {
        NBTTagCompound root = new NBTTagCompound();
        FellowshipInvitationNbtCodec.write(root, invitations,
                Collections.<NBTTagCompound>emptyList());
        return root;
    }

    /**
     * A save whose quarantine would hold more than its bound is kept as it
     * is, read-only; a store whose quarantine is full closes before it
     * takes another invitation out.
     */
    @Test
    public void aQuarantinePastItsBoundKeepsTheStoreReadOnly() {
        List<NBTTagCompound> full = new ArrayList<NBTTagCompound>();
        for (int index = 0; index < FellowshipNbtCodec.MAX_QUARANTINED; index++) {
            full.add(FellowshipInvitationNbtCodec.createQuarantineEntry(
                    "missing_target_character", new UUID(800L, index), null, null));
        }
        NBTTagCompound atBound = writtenWith(full);
        assertFalse(FellowshipInvitationNbtCodec.read(atBound).isReadOnly());

        NBTTagCompound pastBound = (NBTTagCompound) atBound.copy();
        pastBound.getTagList("Invitations", 10).getCompoundTagAt(0)
                .removeTag("TargetCharacterName");
        FellowshipInvitationNbtCodec.ReadResult past =
                FellowshipInvitationNbtCodec.read(pastBound);
        assertTrue(past.isReadOnly());

        FellowshipInvitationWorldData data = new FellowshipInvitationWorldData(
                FellowshipInvitationWorldData.DATA_NAME);
        data.readFromNBT(atBound);
        assertFalse(data.hasQuarantineRoomFor(1));
        assertTrue("closed", data.isReadOnlyForNewerVersion());
        assertEquals(1, data.getInvitationCount());
    }

    @Test
    public void anInvitationWithoutItsNamesIsQuarantined() {
        NBTTagCompound root = written();
        root.getTagList("Invitations", 10).getCompoundTagAt(0)
                .removeTag("TargetCharacterName");
        FellowshipInvitationNbtCodec.ReadResult read =
                FellowshipInvitationNbtCodec.read(root);
        assertFalse(read.isReadOnly());
        assertTrue(read.getInvitations().isEmpty());
        assertEquals(1, read.getQuarantineEntriesCopy().size());
    }
}
