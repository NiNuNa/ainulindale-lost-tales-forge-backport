package com.ninuna.losttales.quest.player;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSource;
import java.util.Arrays;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class LostTalesQuestPlayerMapMarkerIdentityTest {
    @Test
    public void discoveryTrackAndForgetUseLogicalLotrIdentity() {
        LostTalesQuestPlayerData data =
                new LostTalesQuestPlayerData();

        assertTrue(data.discoverMarker(
                "lotr:waypoint:HOBBITON"));
        assertFalse(data.discoverMarker(
                "LOTR:WAYPOINT:hobbiton"));
        assertTrue(data.isMarkerDiscovered(
                "lotr:waypoint:hobbiton"));
        assertEquals(1, data.getDiscoveredMarkerIds().size());
        assertTrue(data.setTrackedMapMarkerId(
                "LOTR:WAYPOINT:hobbiton"));
        assertEquals("lotr:waypoint:HOBBITON",
                data.getTrackedMapMarkerId());

        assertTrue(data.forgetMarker(
                "LOTR:WAYPOINT:hobbiton"));
        assertFalse(data.isMarkerDiscovered(
                "lotr:waypoint:HOBBITON"));
        assertEquals("", data.getTrackedMapMarkerId());
    }

    @Test
    public void giverUpdateRetainsIdAndSupportedFieldsOnly() {
        LostTalesQuestPlayerData data =
                new LostTalesQuestPlayerData();
        LostTalesMapMarkerDefinition first = marker(
                "lotr:waypoint:HOBBITON", "First");
        LostTalesMapMarkerDefinition updated = marker(
                "LOTR:WAYPOINT:hobbiton", "Updated");

        assertTrue(data.discoverGiverMarker(first));
        assertTrue(data.discoverGiverMarker(updated));

        LostTalesMapMarkerDefinition stored =
                data.getGiverMarker(
                        "lotr:waypoint:hobbiton");
        assertNotNull(stored);
        assertEquals("lotr:waypoint:HOBBITON", stored.getId());
        assertEquals("Updated", stored.getName());
        assertEquals("", stored.getDescription());
        assertEquals(LostTalesMapMarkerSource.QUEST_GIVER,
                stored.getSource());
        assertFalse(stored.hasWaystone());
        assertEquals(1, data.getGiverMarkers().size());
        assertEquals(1, data.getDiscoveredMarkerIds().size());

        assertTrue(data.forgetMarker(
                "LOTR:WAYPOINT:hobbiton"));
        assertTrue(data.getGiverMarkers().isEmpty());
    }

    @Test
    public void loadRepairsLogicalDuplicatesWithoutChangingFormat() {
        NBTTagCompound playerData = new NBTTagCompound();
        playerData.setInteger("DataVersion",
                LostTalesQuestPlayerData.CURRENT_DATA_VERSION);
        NBTTagList discovered = new NBTTagList();
        discovered.appendTag(discovered(
                "lotr:waypoint:HOBBITON"));
        discovered.appendTag(discovered(
                "LOTR:WAYPOINT:hobbiton"));
        playerData.setTag("DiscoveredMarkers", discovered);
        NBTTagList dynamic = new NBTTagList();
        dynamic.appendTag(dynamic(
                "lotr:waypoint:HOBBITON", "First"));
        dynamic.appendTag(dynamic(
                "LOTR:WAYPOINT:hobbiton", "Duplicate"));
        playerData.setTag("GiverMarkers", dynamic);
        playerData.setString("TrackedMapMarkerId",
                "LOTR:WAYPOINT:hobbiton");
        NBTTagCompound wrapper = new NBTTagCompound();
        wrapper.setTag(LostTalesQuestPlayerData.PROPERTY_ID,
                playerData);

        LostTalesQuestPlayerData data =
                new LostTalesQuestPlayerData();
        data.loadNBTData(wrapper);

        assertEquals(1, data.getDiscoveredMarkerIds().size());
        assertEquals(1, data.getGiverMarkers().size());
        assertEquals("First", data.getGiverMarker(
                "lotr:waypoint:hobbiton").getName());
        assertEquals("lotr:waypoint:HOBBITON",
                data.getTrackedMapMarkerId());

        NBTTagCompound repairedWrapper = new NBTTagCompound();
        data.saveNBTData(repairedWrapper);
        NBTTagCompound repaired = repairedWrapper.getCompoundTag(
                LostTalesQuestPlayerData.PROPERTY_ID);
        assertEquals(1, repaired.getTagList(
                "DiscoveredMarkers",
                Constants.NBT.TAG_COMPOUND).tagCount());
        assertEquals(1, repaired.getTagList(
                "GiverMarkers",
                Constants.NBT.TAG_COMPOUND).tagCount());
    }

    @Test
    public void customIdsRemainCaseSensitiveAndOversizedIdsAreRejected() {
        LostTalesQuestPlayerData data =
                new LostTalesQuestPlayerData();

        assertTrue(data.discoverMarker("losttales:Town"));
        assertTrue(data.discoverMarker("losttales:town"));
        assertEquals(2, data.getDiscoveredMarkerIds().size());
        char[] oversized = new char[
                LostTalesQuestPlayerData.MAX_IDENTIFIER_CHARACTERS + 1];
        Arrays.fill(oversized, 'a');
        assertFalse(data.discoverMarker(new String(oversized)));
    }

    private static LostTalesMapMarkerDefinition marker(
            String id, String name) {
        return new LostTalesMapMarkerDefinition(
                id, name, "quest", "white", "Quest", "Transient",
                false, 100, 10.0D, 64.0D, 20.0D,
                128.0D, 8.0D, false, true, false,
                LostTalesMapMarkerSource.PLAYER_CREATED,
                true, "losttales:player_placed", 5);
    }

    private static NBTTagCompound discovered(String id) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("MarkerId", id);
        return tag;
    }

    private static NBTTagCompound dynamic(String id, String name) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("MarkerId", id);
        tag.setString("Name", name);
        tag.setString("Icon", "quest");
        tag.setString("Color", "white");
        tag.setString("Category", "Quest");
        tag.setInteger("DimensionId", 100);
        tag.setDouble("X", 10.0D);
        tag.setDouble("Y", 64.0D);
        tag.setDouble("Z", 20.0D);
        tag.setDouble("CompassFadeInRadius", 128.0D);
        tag.setDouble("DiscoveryRadius", 8.0D);
        tag.setBoolean("IsDiscoverable", true);
        return tag;
    }
}
