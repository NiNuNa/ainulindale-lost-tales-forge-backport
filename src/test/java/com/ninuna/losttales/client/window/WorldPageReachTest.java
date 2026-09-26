package com.ninuna.losttales.client.window;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityMissiveBoard;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityWaystone;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * A world page's tab closes once its player is further than the server's
 * reach from its thing, in another world, or the thing is gone (Q8 a):
 * the same eight blocks the server lets a player use a waystone or a
 * missive board from, measured from the feet to the block's middle.
 */
public final class WorldPageReachTest {
    private static final double REACH_SQ = 64.0D;

    /** A thing at 10, 64, 10 in the overworld; its middle at 10.5, 64.5, 10.5. */
    private static WorldPageReach.Leave at(int dimension, double x, double y,
                                           double z, boolean standing) {
        return WorldPageReach.check(dimension, x, y, z, 0, 10, 64, 10,
                standing, REACH_SQ);
    }

    @Test
    public void theReachIsTheServers() {
        assertEquals(REACH_SQ, LostTalesTileEntityWaystone.REACH_SQ, 0.0D);
        assertEquals(REACH_SQ, LostTalesTileEntityMissiveBoard.REACH_SQ, 0.0D);
    }

    @Test
    public void eightBlocksAwayIsStillAtTheThing() {
        assertNull(at(0, 10.5D, 64.5D, 10.5D, true));
        assertNull("exactly eight blocks", at(0, 18.5D, 64.5D, 10.5D, true));
        assertEquals("a step further", WorldPageReach.Leave.TOO_FAR,
                at(0, 18.6D, 64.5D, 10.5D, true));
        assertEquals("up counts as well", WorldPageReach.Leave.TOO_FAR,
                at(0, 10.5D, 73.0D, 10.5D, true));
    }

    @Test
    public void anotherWorldIsSaidFirstThenTheDistanceThenTheThing() {
        assertEquals(WorldPageReach.Leave.OTHER_WORLD,
                at(-1, 10.5D, 64.5D, 10.5D, false));
        assertEquals("far away, the thing may only be out of the world the "
                + "client holds", WorldPageReach.Leave.TOO_FAR,
                at(0, 100.0D, 64.0D, 100.0D, false));
        assertEquals(WorldPageReach.Leave.GONE,
                at(0, 11.0D, 64.0D, 12.0D, false));
    }

    @Test
    public void eachReasonHasWordsOfEachPagesOwn() {
        assertEquals("gui.losttales.waystone.left.far",
                WorldPageReach.Leave.TOO_FAR.messageKey("waystone"));
        assertEquals("gui.losttales.missive_board.left.world",
                WorldPageReach.Leave.OTHER_WORLD.messageKey("missive_board"));
        assertEquals("gui.losttales.missive_board.left.gone",
                WorldPageReach.Leave.GONE.messageKey("missive_board"));
    }
}
