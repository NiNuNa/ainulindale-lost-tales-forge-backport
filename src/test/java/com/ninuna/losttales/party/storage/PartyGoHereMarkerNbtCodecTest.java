package com.ninuna.losttales.party.storage;

import com.ninuna.losttales.party.model.PartyGoHereMarker;
import java.util.Collections;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

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
}
