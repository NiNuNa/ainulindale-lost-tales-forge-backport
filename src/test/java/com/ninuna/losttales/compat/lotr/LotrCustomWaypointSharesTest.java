package com.ninuna.losttales.compat.lotr;

import lotr.common.world.map.LOTRCustomWaypoint;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A character's saved waypoints say which fellowships each is shared
 * with: read as the shares to make again once its account is back in a
 * fellowship, and as the copies a fellowship's members are handed while
 * nobody plays the character.
 */
public final class LotrCustomWaypointSharesTest {
    private static final UUID COMPANY = UUID.fromString("d1000000-0000-0000-0000-00000000001d");
    private static final UUID OTHER = UUID.fromString("d2000000-0000-0000-0000-00000000002d");

    private static NBTTagCompound waypoint(int id, String name, UUID... fellowships) {
        NBTTagCompound waypoint = new NBTTagCompound();
        waypoint.setString("Name", name);
        waypoint.setDouble("XMap", 810.5D);
        waypoint.setDouble("YMap", 720.25D);
        waypoint.setInteger("XCoord", 1200);
        waypoint.setInteger("YCoord", 70);
        waypoint.setInteger("ZCoord", -340);
        waypoint.setInteger("ID", id);
        if (fellowships.length > 0) {
            NBTTagList shared = new NBTTagList();
            for (UUID fellowship : fellowships) {
                shared.appendTag(new NBTTagString(fellowship.toString()));
            }
            waypoint.setTag("SharedFellowships", shared);
        }
        return waypoint;
    }

    private static NBTTagCompound state(NBTTagCompound... waypoints) {
        NBTTagCompound state = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound waypoint : waypoints) {
            list.appendTag(waypoint);
        }
        state.setTag("CustomWaypoints", list);
        state.setTag("CWPUses", new NBTTagList());
        return state;
    }

    @Test
    public void theSharesOfASavedCharacterAreReadByWaypoint() {
        NBTTagCompound state = state(waypoint(20000, "Bree Camp", COMPANY, OTHER),
                waypoint(20003, "Hidden Hollow"), waypoint(20004, "Ford", COMPANY));
        Map<Integer, Set<UUID>> shares = LotrCustomWaypointStateAdapter.sharesIn(state);
        assertEquals(2, shares.size());
        assertEquals(2, shares.get(Integer.valueOf(20000)).size());
        assertTrue(shares.get(Integer.valueOf(20004)).contains(COMPANY));

        List<LOTRCustomWaypoint> copies =
                LotrCustomWaypointStateAdapter.sharedWith(state, COMPANY);
        assertEquals(2, copies.size());
        LOTRCustomWaypoint camp = copies.get(0);
        assertEquals("Bree Camp", camp.getCodeName());
        assertEquals(20000, camp.getID());
        assertEquals(1200, camp.getXCoord());
        assertEquals(70, camp.getYCoordSaved());
        assertEquals(-340, camp.getZCoord());
        assertEquals(810.5D, camp.getX(), 0.0D);
        assertEquals(1, LotrCustomWaypointStateAdapter.sharedWith(state, OTHER).size());
    }

    /** A state that does not read as a character's waypoints hands out nothing. */
    @Test
    public void anUnreadableStateHandsOutNothing() {
        NBTTagCompound broken = state(waypoint(20000, "Bree Camp", COMPANY),
                waypoint(20000, "Twice", COMPANY));
        assertEquals(Collections.<LOTRCustomWaypoint>emptyList(),
                LotrCustomWaypointStateAdapter.sharedWith(broken, COMPANY));
        assertTrue(LotrCustomWaypointStateAdapter.sharedWith(null, COMPANY).isEmpty());
    }
}
