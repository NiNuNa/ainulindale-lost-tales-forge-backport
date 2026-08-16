package com.ninuna.losttales.client.gui.inventory;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class LostTalesSmoothInventoryHooksTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void movementStartsAtSourceAndSettlesAtDestination() {
        assertEquals(0.0F,
                LostTalesSmoothInventoryHooks.ease(0.0F), EPSILON);
        assertEquals(1.0F,
                LostTalesSmoothInventoryHooks.ease(1.0F), EPSILON);
    }

    @Test
    public void movementUsesRestrainedBackOutFollowThrough() {
        float halfway = LostTalesSmoothInventoryHooks.ease(0.5F);
        assertTrue(halfway > 0.5F);
        assertTrue(halfway < 1.1F);
    }

    @Test
    public void onlyShiftClickAndHotbarKeysTriggerTravel() {
        assertTrue(LostTalesSmoothInventoryHooks.isTransferMode(1));
        assertTrue(LostTalesSmoothInventoryHooks.isTransferMode(2));
        assertTrue(!LostTalesSmoothInventoryHooks.isTransferMode(0));
        assertTrue(!LostTalesSmoothInventoryHooks.isTransferMode(5));
    }

    @Test
    public void bulkReordersAreSuppressed() {
        assertTrue(!LostTalesSmoothInventoryHooks.isBulkChange(12));
        assertTrue(LostTalesSmoothInventoryHooks.isBulkChange(13));
    }
}
