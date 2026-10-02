package com.ninuna.losttales.fellowship.storage;

import com.ninuna.losttales.fellowship.model.FellowshipInvitation;
import java.util.Collections;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Invitations read back as written, and the codec fails closed: data of
 * another version keeps the store read-only, and an invitation missing a
 * key it needs goes to the quarantine rather than being filled in.
 */
public final class FellowshipInvitationNbtCodecTest {

    private static final UUID INVITATION = UUID.fromString(
            "c1000000-0000-0000-0000-00000000001c");

    private static NBTTagCompound written() {
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
                Collections.singletonList(invitation),
                Collections.<NBTTagCompound>emptyList());
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
