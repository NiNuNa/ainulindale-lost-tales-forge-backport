package com.ninuna.losttales.fellowship.storage;

import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipIcon;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.model.FellowshipSwitch;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A fellowship is saved whole: its name, guides, icon and switches, and
 * which fellowship each character travels with and which LOTR fellowship
 * stands for each. A fellowship whose stored name is not well formed goes
 * to the quarantine whole.
 */
public final class FellowshipNbtCodecTest {

    private static final UUID FELLOWSHIP =
            UUID.fromString("50000000-0000-0000-0000-000000000005");
    private static final UUID LEADER =
            UUID.fromString("60000000-0000-0000-0000-000000000006");
    private static final UUID GUIDE =
            UUID.fromString("70000000-0000-0000-0000-000000000007");

    @Test
    public void everythingAboutAFellowshipIsSavedAndRead() {
        Fellowship fellowship = fellowship("The Grey Company");
        fellowship.setGuide(GUIDE, true);
        fellowship.setIcon(new FellowshipIcon("minecraft:iron_sword", 3));
        fellowship.setSwitch(FellowshipSwitch.SHOWN_ON_MAP, false);

        FellowshipNbtCodec.ReadResult read = FellowshipNbtCodec.read(write(fellowship));
        assertFalse(read.isReadOnly());
        assertTrue(read.getQuarantineEntriesCopy().isEmpty());
        Fellowship back = read.getFellowships().get(FELLOWSHIP);
        assertEquals("The Grey Company", back.getName());
        assertTrue(back.isGuide(GUIDE));
        assertEquals(new FellowshipIcon("minecraft:iron_sword", 3), back.getIcon());
        assertTrue(back.isOn(FellowshipSwitch.NO_FIGHTING));
        assertFalse(back.isOn(FellowshipSwitch.SHOWN_ON_MAP));
    }

    @Test
    public void whoTravelsWithWhichAndTheMirrorsAreSavedAndRead() {
        UUID mirror = UUID.randomUUID();
        Map<UUID, UUID> travelling = new HashMap<UUID, UUID>();
        travelling.put(GUIDE, FELLOWSHIP);
        Map<UUID, UUID> mirrors = new HashMap<UUID, UUID>();
        mirrors.put(FELLOWSHIP, mirror);
        NBTTagCompound tag = new NBTTagCompound();
        FellowshipNbtCodec.write(tag, Collections.singletonList(fellowship("Grey Company")),
                travelling, mirrors, Collections.<NBTTagCompound>emptyList());

        FellowshipNbtCodec.ReadResult read = FellowshipNbtCodec.read(tag);
        assertEquals(FELLOWSHIP, read.getTravelling().get(GUIDE));
        assertEquals(mirror, read.getMirrors().get(FELLOWSHIP));
    }

    @Test
    public void fiftyMembersAreSavedAndRead() {
        List<FellowshipMember> members = new ArrayList<FellowshipMember>();
        FellowshipColor[] colours = FellowshipColor.values();
        for (int index = 0; index < Fellowship.MAX_MEMBERS; index++) {
            members.add(new FellowshipMember(UUID.randomUUID(), UUID.randomUUID(),
                    "Member " + index, index, colours[index % colours.length]));
        }
        Fellowship fellowship = new Fellowship(FELLOWSHIP, members.get(0).getIdentityId(),
                members, Collections.<UUID>emptyList(), "Grey Company", null,
                EnumSet.allOf(FellowshipSwitch.class), 1L, 0L,
                Fellowship.CURRENT_DATA_VERSION);
        assertEquals(Fellowship.MAX_MEMBERS, FellowshipNbtCodec.read(write(fellowship))
                .getFellowships().get(FELLOWSHIP).getMemberCount());
    }

    @Test
    public void aFellowshipWhoseNameIsNotWellFormedIsQuarantined() {
        NBTTagCompound saved = write(fellowship("The Grey Company"));
        NBTTagList fellowships = saved.getTagList("Fellowships", Constants.NBT.TAG_COMPOUND);
        fellowships.getCompoundTagAt(0).setString("Name", "§kHidden Company");

        FellowshipNbtCodec.ReadResult read = FellowshipNbtCodec.read(saved);
        assertFalse(read.isReadOnly());
        assertTrue(read.wasRepaired());
        assertNull(read.getFellowships().get(FELLOWSHIP));
        assertEquals(1, read.getQuarantineEntriesCopy().size());
        assertEquals("invalid_fellowship_name",
                read.getQuarantineEntriesCopy().get(0).getString("Reason"));
    }

    private static Fellowship fellowship(String name) {
        List<FellowshipMember> members = new ArrayList<FellowshipMember>();
        members.add(new FellowshipMember(LEADER, UUID.randomUUID(), "Aldric", 1L,
                FellowshipColor.GREEN));
        members.add(new FellowshipMember(GUIDE, UUID.randomUUID(), "Beren", 2L,
                FellowshipColor.BLUE));
        return new Fellowship(FELLOWSHIP, LEADER, members, Collections.<UUID>emptyList(),
                name, null, EnumSet.allOf(FellowshipSwitch.class), 1L, 3L,
                Fellowship.CURRENT_DATA_VERSION);
    }

    private static NBTTagCompound write(Fellowship fellowship) {
        NBTTagCompound tag = new NBTTagCompound();
        FellowshipNbtCodec.write(tag, Collections.singletonList(fellowship),
                Collections.<UUID, UUID>emptyMap(), Collections.<UUID, UUID>emptyMap(),
                Collections.<NBTTagCompound>emptyList());
        return tag;
    }
}
