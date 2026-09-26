package com.ninuna.losttales.party.storage;

import com.ninuna.losttales.party.model.PartyGoHereMarker;
import java.util.Collections;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Persistence coverage for party-independent roleplay-character markers. */
public final class PartyGoHereMarkerNbtCodecTest {

    @Test
    public void soloMarkerRoundTripsWithoutInventingPartyOwnership() {
        UUID characterId = UUID.fromString(
                "30000000-0000-0000-0000-000000000003");
        PartyGoHereMarker marker = new PartyGoHereMarker(
                null, characterId, 100,
                14540.25D, 65.0D, 777.5D, 99L);
        NBTTagCompound encoded = new NBTTagCompound();

        PartyGoHereMarkerNbtCodec.write(
                encoded, Collections.singletonList(marker),
                Collections.<NBTTagCompound>emptyList());
        PartyGoHereMarkerNbtCodec.ReadResult result =
                PartyGoHereMarkerNbtCodec.read(encoded);

        assertFalse(result.isReadOnly());
        assertEquals(1, result.getMarkers().size());
        PartyGoHereMarker decoded = result.getMarkers().get(characterId);
        assertNull(decoded.getPartyId());
        assertEquals(marker.getX(), decoded.getX(), 0.0D);
        assertEquals(marker.getZ(), decoded.getZ(), 0.0D);
    }

    /**
     * A marker at another version than this build writes, a party
     * marker included, is not this build's to read: the whole store is
     * kept as it is, read-only.
     */
    @Test
    public void anOlderMarkerHoldsTheWholeStoreReadOnly() {
        PartyGoHereMarker marker = new PartyGoHereMarker(
                UUID.fromString("20000000-0000-0000-0000-000000000002"),
                UUID.fromString("30000000-0000-0000-0000-000000000003"),
                0, 10.0D, 65.0D, 20.0D, 99L);
        NBTTagCompound encoded = new NBTTagCompound();
        PartyGoHereMarkerNbtCodec.write(
                encoded, Collections.singletonList(marker),
                Collections.<NBTTagCompound>emptyList());
        encoded.getTagList("Markers", Constants.NBT.TAG_COMPOUND)
                .getCompoundTagAt(0).setInteger("DataVersion",
                        PartyGoHereMarker.CURRENT_DATA_VERSION - 1);

        PartyGoHereMarkerNbtCodec.ReadResult result =
                PartyGoHereMarkerNbtCodec.read(encoded);

        assertTrue(result.isReadOnly());
        assertEquals(PartyGoHereMarker.CURRENT_DATA_VERSION - 1,
                result.getUnsupportedVersion());
        assertTrue(result.getMarkers().isEmpty());
        assertEquals(encoded, result.getOriginalDataCopy());
    }
}
