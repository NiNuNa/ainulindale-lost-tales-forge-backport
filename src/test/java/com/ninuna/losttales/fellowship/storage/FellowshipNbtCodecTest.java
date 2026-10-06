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
 * to the quarantine whole, as does a fellowship or a member that lacks a
 * key this build writes; data at another version keeps the whole store
 * read-only.
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

    /**
     * A member whose name the server did not know is kept with no name,
     * never with a word in the server's language, and read back as it was
     * kept; each reader's game words it.
     */
    @Test
    public void aMemberWithNoKnownNameIsKeptWithoutAWord() {
        FellowshipMember nameless = new FellowshipMember(GUIDE,
                UUID.randomUUID(), null, 2L, FellowshipColor.BLUE);
        assertEquals("", nameless.getCharacterName());
        List<FellowshipMember> members = new ArrayList<FellowshipMember>();
        members.add(new FellowshipMember(LEADER, UUID.randomUUID(), "Aldric",
                1L, FellowshipColor.GREEN));
        members.add(nameless);
        Fellowship fellowship = new Fellowship(FELLOWSHIP, LEADER, members,
                Collections.<UUID>emptyList(), "Grey Company", null,
                EnumSet.allOf(FellowshipSwitch.class), 1L, 3L,
                Fellowship.CURRENT_DATA_VERSION);
        assertTrue(fellowship.refreshMemberIdentity(LEADER,
                fellowship.getMember(LEADER).getOwnerId(), "  "));
        assertEquals("", fellowship.getMember(LEADER).getCharacterName());

        FellowshipNbtCodec.ReadResult read = FellowshipNbtCodec.read(write(fellowship));
        assertTrue(read.getQuarantineEntriesCopy().isEmpty());
        assertEquals("", read.getFellowships().get(FELLOWSHIP)
                .getMember(GUIDE).getCharacterName());
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

    /**
     * Fellowship data at any version but the one this build writes is not
     * this build's to read: the root, a fellowship or a member at another
     * version, or naming none, keeps the whole store as it is, read-only.
     */
    @Test
    public void anotherVersionAnywhereHoldsTheWholeStoreReadOnly() {
        NBTTagCompound olderRoot = write(fellowship("Grey Company"));
        olderRoot.setInteger("DataVersion", FellowshipNbtCodec.CURRENT_ROOT_DATA_VERSION - 1);
        assertReadOnly(olderRoot, FellowshipNbtCodec.CURRENT_ROOT_DATA_VERSION - 1);

        NBTTagCompound unversionedRoot = write(fellowship("Grey Company"));
        unversionedRoot.removeTag("DataVersion");
        assertReadOnly(unversionedRoot, 0);

        NBTTagCompound olderFellowship = write(fellowship("Grey Company"));
        firstFellowship(olderFellowship).setInteger("DataVersion",
                Fellowship.CURRENT_DATA_VERSION - 1);
        assertReadOnly(olderFellowship, Fellowship.CURRENT_DATA_VERSION - 1);

        NBTTagCompound unversionedMember = write(fellowship("Grey Company"));
        member(unversionedMember, 1).removeTag("DataVersion");
        assertReadOnly(unversionedMember, 0);
    }

    /**
     * A member whose stored entry lacks when it joined or the colour it
     * wears goes to the quarantine whole; nothing is made up for it, and
     * the rest of the fellowship stands.
     */
    @Test
    public void aMemberMissingAKeyIsQuarantinedNotFilledIn() {
        NBTTagCompound noJoinedAt = write(fellowship("Grey Company"));
        member(noJoinedAt, 1).removeTag("JoinedAt");
        assertMemberQuarantined(noJoinedAt, "missing_or_invalid_joined_at");

        NBTTagCompound noColour = write(fellowship("Grey Company"));
        member(noColour, 1).removeTag("Color");
        assertMemberQuarantined(noColour, "missing_or_unknown_color");

        NBTTagCompound unknownColour = write(fellowship("Grey Company"));
        member(unknownColour, 1).setString("Color", "mauve");
        assertMemberQuarantined(unknownColour, "missing_or_unknown_color");
    }

    /** A fellowship whose stored entry lacks a key this build writes goes to the quarantine whole. */
    @Test
    public void aFellowshipMissingAKeyIsQuarantinedNotFilledIn() {
        String[] keys = {"CreatedAt", "Revision", "Switches", "Guides"};
        for (String key : keys) {
            NBTTagCompound saved = write(fellowship("Grey Company"));
            firstFellowship(saved).removeTag(key);

            FellowshipNbtCodec.ReadResult read = FellowshipNbtCodec.read(saved);
            assertFalse(key, read.isReadOnly());
            assertNull(key, read.getFellowships().get(FELLOWSHIP));
            assertEquals(key, 1, read.getQuarantineEntriesCopy().size());
        }
    }

    private static void assertReadOnly(NBTTagCompound saved, int version) {
        NBTTagCompound before = (NBTTagCompound) saved.copy();
        FellowshipNbtCodec.ReadResult read = FellowshipNbtCodec.read(saved);
        assertTrue(read.isReadOnly());
        assertEquals(version, read.getUnsupportedVersion());
        assertTrue(read.getFellowships().isEmpty());
        assertEquals("kept as it was", before, read.getOriginalDataCopy());
    }

    private static void assertMemberQuarantined(NBTTagCompound saved, String reason) {
        FellowshipNbtCodec.ReadResult read = FellowshipNbtCodec.read(saved);
        assertFalse(read.isReadOnly());
        assertTrue(read.wasRepaired());
        Fellowship back = read.getFellowships().get(FELLOWSHIP);
        assertEquals(1, back.getMemberCount());
        assertNull(back.getMember(GUIDE));
        assertEquals(1, read.getQuarantineEntriesCopy().size());
        assertEquals(reason, read.getQuarantineEntriesCopy().get(0).getString("Reason"));
    }

    private static NBTTagCompound firstFellowship(NBTTagCompound saved) {
        return saved.getTagList("Fellowships", Constants.NBT.TAG_COMPOUND).getCompoundTagAt(0);
    }

    private static NBTTagCompound member(NBTTagCompound saved, int index) {
        return firstFellowship(saved).getTagList("Members", Constants.NBT.TAG_COMPOUND)
                .getCompoundTagAt(index);
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
