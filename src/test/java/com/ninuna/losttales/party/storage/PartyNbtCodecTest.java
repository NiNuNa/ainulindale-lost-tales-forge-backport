package com.ninuna.losttales.party.storage;

import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.model.PartyMember;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A party's name is saved with it; a stored name that is not well formed
 * goes to the quarantine and the party stands without it.
 */
public final class PartyNbtCodecTest {

    private static final UUID PARTY =
            UUID.fromString("50000000-0000-0000-0000-000000000005");

    @Test
    public void theNameIsSavedAndRead() {
        PartyNbtCodec.ReadResult read = PartyNbtCodec.read(
                write(party("The Grey Company")));
        assertFalse(read.isReadOnly());
        assertEquals("The Grey Company", read.getParties().get(PARTY).getName());
        assertTrue(read.getQuarantineEntriesCopy().isEmpty());
    }

    @Test
    public void eightMembersAreSavedAndRead() {
        List<PartyMember> members = new ArrayList<PartyMember>();
        for (int index = 0; index < Party.MAX_MEMBERS; index++) {
            members.add(new PartyMember(UUID.randomUUID(), UUID.randomUUID(),
                    "Member " + index, index, PartyColor.values()[index]));
        }
        Party party = new Party(PARTY, members.get(0).getIdentityId(), members,
                "", 1L, 0L, Party.CURRENT_DATA_VERSION);
        assertEquals(Party.MAX_MEMBERS, PartyNbtCodec.read(write(party))
                .getParties().get(PARTY).getMemberCount());
    }

    @Test
    public void aNameThatIsNotWellFormedIsQuarantined() {
        NBTTagCompound saved = write(party("The Grey Company"));
        NBTTagList parties = saved.getTagList("Parties", Constants.NBT.TAG_COMPOUND);
        parties.getCompoundTagAt(0).setString("Name", "§kHidden Company");

        PartyNbtCodec.ReadResult read = PartyNbtCodec.read(saved);
        assertFalse(read.isReadOnly());
        assertTrue(read.wasRepaired());
        assertEquals("", read.getParties().get(PARTY).getName());
        assertEquals(1, read.getQuarantineEntriesCopy().size());
        assertEquals("invalid_party_name",
                read.getQuarantineEntriesCopy().get(0).getString("Reason"));
    }

    private static Party party(String name) {
        List<PartyMember> members = new ArrayList<PartyMember>();
        UUID leader = UUID.fromString("60000000-0000-0000-0000-000000000006");
        members.add(new PartyMember(leader, UUID.randomUUID(), "Aldric", 1L,
                PartyColor.GREEN));
        return new Party(PARTY, leader, members, name, 1L, 3L,
                Party.CURRENT_DATA_VERSION);
    }

    private static NBTTagCompound write(Party party) {
        NBTTagCompound tag = new NBTTagCompound();
        PartyNbtCodec.write(tag, Collections.singletonList(party),
                Collections.<NBTTagCompound>emptyList());
        return tag;
    }
}
