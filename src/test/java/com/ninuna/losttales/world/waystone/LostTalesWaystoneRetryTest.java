package com.ninuna.losttales.world.waystone;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerNbtCodec;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSource;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerVisibility;
import com.ninuna.losttales.mapmarker.LostTalesWaystoneGenerationState;
import java.util.Collections;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * {@code retry} marks a failed site to be built again: the mark is the
 * record's generation state, saved with it, which the generation handler
 * builds as the chunk loads (WAY-18).
 */
public final class LostTalesWaystoneRetryTest {

    @Test
    public void aFailedSiteIsMarkedToBeBuiltAgain() {
        LostTalesMapMarkerRecord failed = generated(
                LostTalesWaystoneGenerationState.FAILED_OR_BLOCKED);
        LostTalesMapMarkerRecord marked =
                LostTalesWaystonePlacementService.markForRetry(failed);
        assertEquals(LostTalesWaystoneGenerationState.NOT_ATTEMPTED,
                marked.getGenerationState());
        assertTrue(marked.getRevision() > failed.getRevision());

        LostTalesMapMarkerRecord waiting = generated(
                LostTalesWaystoneGenerationState.NOT_ATTEMPTED);
        assertSame("one waiting already stays as it is", waiting,
                LostTalesWaystonePlacementService.markForRetry(waiting));
    }

    @Test
    public void onlyAWaystoneNotStandingIsMarked() {
        assertNull(LostTalesWaystonePlacementService.markForRetry(null));
        assertNull("no waystone", LostTalesWaystonePlacementService.markForRetry(
                LostTalesMapMarkerRecord.builder("losttales:bree",
                        LostTalesMapMarkerSource.CUSTOM_PRESET)
                        .name("Bree").build()));
        assertNull("standing", LostTalesWaystonePlacementService.markForRetry(
                generated(LostTalesWaystoneGenerationState.FAILED_OR_BLOCKED)
                        .withLink(0, 1, 64, 1, UUID.randomUUID())));
        assertNull("a player's", LostTalesWaystonePlacementService.markForRetry(
                LostTalesMapMarkerRecord.createPlayerMarker(
                        "losttales:player/abc", UUID.randomUUID(), 0,
                        1.0D, 64.0D, 1.0D, UUID.randomUUID())));
    }

    /** The mark outlasts a restart: the store saves it with the record. */
    @Test
    public void theMarkIsSaved() {
        LostTalesMapMarkerRecord marked = LostTalesWaystonePlacementService
                .markForRetry(generated(
                        LostTalesWaystoneGenerationState.FAILED_OR_BLOCKED));
        NBTTagCompound saved = new NBTTagCompound();
        LostTalesMapMarkerNbtCodec.write(saved, Collections.singleton(marked),
                Collections.<NBTTagCompound>emptyList());
        assertEquals(LostTalesWaystoneGenerationState.NOT_ATTEMPTED,
                LostTalesMapMarkerNbtCodec.read(saved).getRecords()
                        .get(marked.getId()).getGenerationState());
    }

    private static LostTalesMapMarkerRecord generated(
            LostTalesWaystoneGenerationState state) {
        return LostTalesMapMarkerRecord.builder("losttales:bree",
                LostTalesMapMarkerSource.CUSTOM_PRESET)
                .name("Bree")
                .position(0, 1.0D, 64.0D, 1.0D)
                .waystone(true, "losttales:glowstone_house")
                .visibility(LostTalesMapMarkerVisibility.PUBLIC)
                .generationState(state)
                .build();
    }
}
