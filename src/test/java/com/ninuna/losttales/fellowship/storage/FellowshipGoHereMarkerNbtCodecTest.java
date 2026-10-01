package com.ninuna.losttales.fellowship.storage;

import com.ninuna.losttales.fellowship.model.FellowshipGoHereMarker;
import java.util.Collections;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Persistence coverage for fellowship-independent roleplay-character markers. */
public final class FellowshipGoHereMarkerNbtCodecTest {

    @Test
    public void soloMarkerRoundTripsWithoutInventingFellowshipOwnership() {
        UUID characterId = UUID.fromString(
                "30000000-0000-0000-0000-000000000003");
        FellowshipGoHereMarker marker = new FellowshipGoHereMarker(
                null, characterId, 100,
                14540.25D, 65.0D, 777.5D, 99L);
        NBTTagCompound encoded = new NBTTagCompound();

        FellowshipGoHereMarkerNbtCodec.write(
                encoded, Collections.singletonList(marker),
                Collections.<NBTTagCompound>emptyList());
        FellowshipGoHereMarkerNbtCodec.ReadResult result =
                FellowshipGoHereMarkerNbtCodec.read(encoded);

        assertFalse(result.isReadOnly());
        assertEquals(1, result.getMarkers().size());
        FellowshipGoHereMarker decoded = result.getMarkers().get(characterId);
        assertNull(decoded.getFellowshipId());
        assertEquals(marker.getX(), decoded.getX(), 0.0D);
        assertEquals(marker.getZ(), decoded.getZ(), 0.0D);
    }

    /**
     * A marker at another version than this build writes, a fellowship
     * marker included, is not this build's to read: the whole store is
     * kept as it is, read-only.
     */
    @Test
    public void anOlderMarkerHoldsTheWholeStoreReadOnly() {
        FellowshipGoHereMarker marker = new FellowshipGoHereMarker(
                UUID.fromString("20000000-0000-0000-0000-000000000002"),
                UUID.fromString("30000000-0000-0000-0000-000000000003"),
                0, 10.0D, 65.0D, 20.0D, 99L);
        NBTTagCompound encoded = new NBTTagCompound();
        FellowshipGoHereMarkerNbtCodec.write(
                encoded, Collections.singletonList(marker),
                Collections.<NBTTagCompound>emptyList());
        encoded.getTagList("Markers", Constants.NBT.TAG_COMPOUND)
                .getCompoundTagAt(0).setInteger("DataVersion",
                        FellowshipGoHereMarker.CURRENT_DATA_VERSION - 1);

        FellowshipGoHereMarkerNbtCodec.ReadResult result =
                FellowshipGoHereMarkerNbtCodec.read(encoded);

        assertTrue(result.isReadOnly());
        assertEquals(FellowshipGoHereMarker.CURRENT_DATA_VERSION - 1,
                result.getUnsupportedVersion());
        assertTrue(result.getMarkers().isEmpty());
        assertEquals(encoded, result.getOriginalDataCopy());
    }
}
