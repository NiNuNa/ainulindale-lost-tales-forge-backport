package com.ninuna.losttales.mapmarker;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class LostTalesMapMarkerNbtCodecTest {

    @Test
    public void playerMarkerRoundTripsWithOwnershipSharingAndLink() {
        UUID owner = UUID.fromString(
                "10000000-0000-0000-0000-000000000001");
        UUID shared = UUID.fromString(
                "20000000-0000-0000-0000-000000000002");
        UUID sharedFellowship = UUID.fromString(
                "25000000-0000-0000-0000-000000000025");
        UUID token = UUID.fromString(
                "30000000-0000-0000-0000-000000000003");
        LostTalesMapMarkerRecord marker =
                LostTalesMapMarkerRecord.createPlayerMarker(
                        "losttales:player/test", "A Waystone", owner,
                        100, 10, 65, 20, token).toBuilder()
                        .visibility(LostTalesMapMarkerVisibility.SHARED)
                        .sharedPlayerIds(new LinkedHashSet<UUID>(
                                Collections.singleton(shared)))
                        .sharedFellowshipIds(new LinkedHashSet<UUID>(
                                Collections.singleton(
                                        sharedFellowship)))
                        .revision(7L)
                        .build();
        NBTTagCompound encoded = new NBTTagCompound();

        LostTalesMapMarkerNbtCodec.write(
                encoded, Collections.singleton(marker),
                Collections.<NBTTagCompound>emptyList());
        LostTalesMapMarkerNbtCodec.ReadResult result =
                LostTalesMapMarkerNbtCodec.read(encoded);

        assertFalse(result.isReadOnly());
        LostTalesMapMarkerRecord decoded =
                result.getRecords().get(marker.getId());
        assertNotNull(decoded);
        assertEquals(owner, decoded.getOwnerPlayerId());
        assertEquals(token, decoded.getLinkToken());
        assertEquals(7L, decoded.getRevision());
        assertEquals(LostTalesMapMarkerVisibility.SHARED,
                decoded.getVisibility());
        assertTrue(decoded.getSharedPlayerIds().contains(shared));
        assertTrue(decoded.getSharedFellowshipIds().contains(
                sharedFellowship));
    }

    @Test
    public void duplicateIdKeepsHighestRevisionAndQuarantinesOther() {
        UUID owner = UUID.fromString(
                "10000000-0000-0000-0000-000000000001");
        UUID token = UUID.fromString(
                "30000000-0000-0000-0000-000000000003");
        LostTalesMapMarkerRecord oldRecord =
                LostTalesMapMarkerRecord.createPlayerMarker(
                        "losttales:player/test", "Old", owner,
                        0, 1, 64, 1, token);
        LostTalesMapMarkerRecord newRecord = oldRecord.toBuilder()
                .name("New")
                .revision(5L)
                .build();
        NBTTagCompound encoded = new NBTTagCompound();

        LostTalesMapMarkerNbtCodec.write(
                encoded, Arrays.asList(oldRecord, newRecord),
                Collections.<NBTTagCompound>emptyList());
        LostTalesMapMarkerNbtCodec.ReadResult result =
                LostTalesMapMarkerNbtCodec.read(encoded);

        assertEquals("New", result.getRecords()
                .get(oldRecord.getId()).getName());
        assertEquals(1, result.getQuarantineCopy().size());
        assertTrue(result.wasRepaired());
    }

    @Test
    public void lotrWaypointIdentityIsDerivedInsteadOfStored() {
        LostTalesMapMarkerRecord marker =
                LostTalesMapMarkerRecord.builder(
                        "lotr:waypoint:waymeet",
                        LostTalesMapMarkerSource.LOTR_ADAPTER)
                        .name("Waymeet")
                        .fastTravel(true)
                        .position(100, 1, 64, 1)
                        .radii(128.0D, 8.0D)
                        .visibility(LostTalesMapMarkerVisibility.PUBLIC)
                        .build();
        NBTTagCompound encoded = new NBTTagCompound();
        LostTalesMapMarkerNbtCodec.write(
                encoded, Collections.singleton(marker),
                Collections.<NBTTagCompound>emptyList());
        NBTTagList markers = encoded.getTagList(
                "Markers", Constants.NBT.TAG_COMPOUND);
        NBTTagCompound stored = markers.getCompoundTagAt(0);

        assertFalse(stored.hasKey("LotrWaypointId"));
        assertFalse(stored.hasKey("FastTravelCode"));

        LostTalesMapMarkerNbtCodec.ReadResult reread =
                LostTalesMapMarkerNbtCodec.read(encoded);

        assertEquals("WAYMEET", reread.getRecords()
                .get(marker.getId()).getLotrWaypointId());
    }

    @Test
    public void newerRootVersionIsPreservedReadOnly() {
        NBTTagCompound encoded = new NBTTagCompound();
        encoded.setInteger("DataVersion",
                LostTalesMapMarkerNbtCodec.CURRENT_DATA_VERSION + 1);

        LostTalesMapMarkerNbtCodec.ReadResult result =
                LostTalesMapMarkerNbtCodec.read(encoded);

        assertTrue(result.isReadOnly());
        assertNotNull(result.getOriginalCopy());
    }
}
