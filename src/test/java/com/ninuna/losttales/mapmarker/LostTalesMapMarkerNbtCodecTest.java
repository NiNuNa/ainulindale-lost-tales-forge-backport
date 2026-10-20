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
                        "losttales:player/test", "Aldric", owner,
                        100, 10, 65, 20, token).toBuilder()
                        .visibility(LostTalesMapMarkerVisibility.SHARED)
                        .sharedCharacterIds(new LinkedHashSet<UUID>(
                                Collections.singleton(shared)))
                        .sharedFellowshipIds(new LinkedHashSet<UUID>(
                                Collections.singleton(
                                        sharedFellowship)))
                        .priority(17)
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
        assertEquals("the owner is the placing character", owner,
                decoded.getOwnerCharacterId());
        assertEquals("an unnamed waystone keeps no words", "",
                decoded.getName());
        assertEquals("player:Aldric", decoded.getNamedAfter());
        assertEquals("", decoded.getCategoryName());
        assertEquals("", decoded.getDescription());
        assertEquals(token, decoded.getLinkToken());
        assertEquals(7L, decoded.getRevision());
        assertEquals(17, decoded.getPriority());
        assertEquals(LostTalesMapMarkerVisibility.SHARED,
                decoded.getVisibility());
        assertTrue(decoded.getSharedCharacterIds().contains(shared));
        assertTrue(decoded.getSharedFellowshipIds().contains(
                sharedFellowship));
    }

    /** The owning character is saved under its own key, and no account owner is (WAY-08). */
    @Test
    public void theOwningCharacterIsSavedByItsOwnKey() {
        UUID owner = UUID.fromString(
                "10000000-0000-0000-0000-000000000009");
        LostTalesMapMarkerRecord marker =
                LostTalesMapMarkerRecord.createPlayerMarker(
                        "losttales:player/owner", "Aldric", owner,
                        100, 10, 65, 20, UUID.randomUUID())
                        .withOwner(UUID.fromString(
                                "10000000-0000-0000-0000-00000000000a"));
        NBTTagCompound encoded = new NBTTagCompound();
        LostTalesMapMarkerNbtCodec.write(
                encoded, Collections.singleton(marker),
                Collections.<NBTTagCompound>emptyList());
        NBTTagCompound stored = encoded.getTagList(
                "Markers", Constants.NBT.TAG_COMPOUND).getCompoundTagAt(0);
        assertTrue(stored.hasKey(
                LostTalesMapMarkerNbtCodec.OWNER_CHARACTER + "Most"));
        assertFalse(stored.hasKey("OwnerMost"));
        assertEquals(marker.getOwnerCharacterId(),
                LostTalesMapMarkerNbtCodec.read(encoded).getRecords()
                        .get(marker.getId()).getOwnerCharacterId());
        assertEquals("handed over, it keeps whom it is called after",
                "player:Aldric", marker.getNamedAfter());
    }

    /** Access is saved public or not; shared is read back from the shares alone (WAY-14). */
    @Test
    public void sharedIsReadBackFromTheShares() {
        LostTalesMapMarkerRecord marker =
                LostTalesMapMarkerRecord.createPlayerMarker(
                        "losttales:player/access", "Aldric",
                        UUID.randomUUID(), 100, 10, 65, 20,
                        UUID.randomUUID()).toBuilder()
                        .visibility(LostTalesMapMarkerVisibility.SHARED)
                        .build();
        assertEquals("shared without a share reads private",
                LostTalesMapMarkerVisibility.PRIVATE, marker.getVisibility());
        NBTTagCompound encoded = new NBTTagCompound();
        LostTalesMapMarkerNbtCodec.write(
                encoded, Collections.singleton(marker),
                Collections.<NBTTagCompound>emptyList());
        NBTTagCompound stored = encoded.getTagList(
                "Markers", Constants.NBT.TAG_COMPOUND).getCompoundTagAt(0);
        stored.setString("Visibility", "shared");
        assertEquals(LostTalesMapMarkerVisibility.PRIVATE,
                LostTalesMapMarkerNbtCodec.read(encoded).getRecords()
                        .get(marker.getId()).getVisibility());
    }

    @Test
    public void duplicateIdKeepsHighestRevisionAndQuarantinesOther() {
        UUID owner = UUID.fromString(
                "10000000-0000-0000-0000-000000000001");
        UUID token = UUID.fromString(
                "30000000-0000-0000-0000-000000000003");
        LostTalesMapMarkerRecord oldRecord =
                LostTalesMapMarkerRecord.createPlayerMarker(
                        "losttales:player/test", "Aldric", owner,
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

        assertEquals("WAYMEET", LostTalesMapMarkerIdResolver.resolveLotrWaypointId(
                reread.getRecords().get(marker.getId()).getId()));
    }

    /** Data of an older version is never repaired: it is kept as it is, and the store reads only. */
    @Test
    public void olderRootVersionIsPreservedReadOnly() {
        NBTTagCompound encoded = new NBTTagCompound();
        LostTalesMapMarkerNbtCodec.write(encoded,
                Collections.singleton(preset("losttales:older")),
                Collections.<NBTTagCompound>emptyList());
        encoded.setInteger("DataVersion",
                LostTalesMapMarkerNbtCodec.CURRENT_DATA_VERSION - 1);

        LostTalesMapMarkerNbtCodec.ReadResult result =
                LostTalesMapMarkerNbtCodec.read(encoded);

        assertTrue(result.isReadOnly());
        assertEquals(LostTalesMapMarkerNbtCodec.CURRENT_DATA_VERSION - 1,
                result.getUnsupportedVersion());
        assertTrue(result.getRecords().isEmpty());
        assertNotNull(result.getOriginalCopy());

        NBTTagCompound unversioned = new NBTTagCompound();
        LostTalesMapMarkerNbtCodec.write(unversioned,
                Collections.singleton(preset("losttales:unversioned")),
                Collections.<NBTTagCompound>emptyList());
        unversioned.removeTag("DataVersion");
        assertTrue(LostTalesMapMarkerNbtCodec.read(unversioned).isReadOnly());

        NBTTagCompound listless = new NBTTagCompound();
        listless.setInteger("DataVersion",
                LostTalesMapMarkerNbtCodec.CURRENT_DATA_VERSION);
        assertTrue("a store without its lists reads only",
                LostTalesMapMarkerNbtCodec.read(listless).isReadOnly());
    }

    /**
     * A record of another version, one missing a key the writer writes,
     * one naming a value no record holds, or one with a broken share is
     * quarantined whole; the others load.
     */
    @Test
    public void aRecordOfAnotherShapeIsQuarantinedWhole() {
        String[] breakages = {"version", "priority", "source", "share",
                "saved_missing", "saved_unknown", "saved_twice",
                "saved_not_names"};
        for (String breakage : breakages) {
            NBTTagCompound encoded = new NBTTagCompound();
            LostTalesMapMarkerNbtCodec.write(encoded, java.util.Arrays.asList(
                    preset("losttales:broken"), preset("losttales:whole")),
                    Collections.<NBTTagCompound>emptyList());
            NBTTagCompound broken = encoded.getTagList(
                    "Markers", Constants.NBT.TAG_COMPOUND).getCompoundTagAt(0);
            if ("version".equals(breakage)) {
                broken.setInteger("DataVersion",
                        LostTalesMapMarkerNbtCodec.CURRENT_DATA_VERSION - 1);
            } else if ("priority".equals(breakage)) {
                broken.removeTag("Priority");
            } else if ("source".equals(breakage)) {
                broken.setString("Source", "account_placed");
            } else if ("saved_missing".equals(breakage)) {
                broken.removeTag(LostTalesMapMarkerNbtCodec.SAVED_SETTINGS);
            } else if ("saved_unknown".equals(breakage)) {
                broken.setTag(LostTalesMapMarkerNbtCodec.SAVED_SETTINGS,
                        names("name", "nickname"));
            } else if ("saved_twice".equals(breakage)) {
                broken.setTag(LostTalesMapMarkerNbtCodec.SAVED_SETTINGS,
                        names("icon", "icon"));
            } else if ("saved_not_names".equals(breakage)) {
                NBTTagList compounds = new NBTTagList();
                compounds.appendTag(new NBTTagCompound());
                broken.setTag(LostTalesMapMarkerNbtCodec.SAVED_SETTINGS,
                        compounds);
            } else {
                NBTTagList shares = new NBTTagList();
                shares.appendTag(new NBTTagCompound());
                broken.setTag("SharedCharacters", shares);
            }

            LostTalesMapMarkerNbtCodec.ReadResult result =
                    LostTalesMapMarkerNbtCodec.read(encoded);

            assertFalse(breakage, result.isReadOnly());
            assertTrue(breakage, result.wasRepaired());
            assertEquals(breakage, java.util.Collections.singleton(
                    "losttales:whole"), result.getRecords().keySet());
            assertEquals(breakage, 1, result.getQuarantineCopy().size());
            assertTrue(breakage, result.getQuarantineCopy().get(0).hasKey(
                    "OriginalData", Constants.NBT.TAG_COMPOUND));
        }
    }

    /** The generation state is saved; no message travels with it. */
    @Test
    public void noGenerationMessageIsSaved() {
        NBTTagCompound encoded = new NBTTagCompound();
        LostTalesMapMarkerNbtCodec.write(encoded,
                Collections.singleton(preset("losttales:state")),
                Collections.<NBTTagCompound>emptyList());
        NBTTagCompound stored = encoded.getTagList(
                "Markers", Constants.NBT.TAG_COMPOUND).getCompoundTagAt(0);
        assertTrue(stored.hasKey("GenerationState"));
        assertFalse(stored.hasKey("GenerationMessage"));
    }

    /** The store reads every record, however many there are (WAY-12). */
    @Test
    public void everyRecordIsReadWithNoCap() {
        java.util.List<LostTalesMapMarkerRecord> many =
                new java.util.ArrayList<LostTalesMapMarkerRecord>();
        for (int index = 0; index < 20000; index++) {
            many.add(preset("losttales:many/" + index));
        }
        NBTTagCompound encoded = new NBTTagCompound();
        LostTalesMapMarkerNbtCodec.write(encoded, many,
                Collections.<NBTTagCompound>emptyList());

        LostTalesMapMarkerNbtCodec.ReadResult result =
                LostTalesMapMarkerNbtCodec.read(encoded);

        assertFalse(result.isReadOnly());
        assertFalse(result.wasRepaired());
        assertEquals(20000, result.getRecords().size());
        assertTrue(result.getRecords().containsKey("losttales:many/19999"));
        assertTrue(result.getQuarantineCopy().isEmpty());
    }

    /** The settings saved on a marker's page are kept by name (WAY-16). */
    @Test
    public void savedSettingsRoundTrip() {
        LostTalesMapMarkerRecord marker = preset("losttales:saved").toBuilder()
                .savedSettings(java.util.EnumSet.of(
                        LostTalesMapMarkerSetting.NAME,
                        LostTalesMapMarkerSetting.ACCESS))
                .build();
        NBTTagCompound encoded = new NBTTagCompound();
        LostTalesMapMarkerNbtCodec.write(encoded,
                java.util.Arrays.asList(marker, preset("losttales:plain")),
                Collections.<NBTTagCompound>emptyList());
        NBTTagList names = encoded.getTagList("Markers",
                Constants.NBT.TAG_COMPOUND).getCompoundTagAt(1)
                .getTagList(LostTalesMapMarkerNbtCodec.SAVED_SETTINGS,
                        Constants.NBT.TAG_STRING);
        assertEquals("name", names.getStringTagAt(0));
        assertEquals("access", names.getStringTagAt(1));

        LostTalesMapMarkerNbtCodec.ReadResult result =
                LostTalesMapMarkerNbtCodec.read(encoded);

        assertEquals(java.util.EnumSet.of(LostTalesMapMarkerSetting.NAME,
                        LostTalesMapMarkerSetting.ACCESS),
                result.getRecords().get("losttales:saved").getSavedSettings());
        assertTrue(result.getRecords().get("losttales:plain")
                .getSavedSettings().isEmpty());
        assertFalse(result.wasRepaired());
    }

    private static NBTTagList names(String... names) {
        NBTTagList list = new NBTTagList();
        for (String name : names) {
            list.appendTag(new net.minecraft.nbt.NBTTagString(name));
        }
        return list;
    }

    private static LostTalesMapMarkerRecord preset(String id) {
        return LostTalesMapMarkerRecord.builder(
                id, LostTalesMapMarkerSource.CUSTOM_PRESET)
                .name("Preset")
                .position(100, 1.0D, 64.0D, 2.0D)
                .visibility(LostTalesMapMarkerVisibility.PUBLIC)
                .build();
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
