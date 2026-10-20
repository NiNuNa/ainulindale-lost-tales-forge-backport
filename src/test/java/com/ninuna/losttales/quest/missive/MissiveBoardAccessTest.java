package com.ninuna.losttales.quest.missive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * A missive board's page reaches the board only as quick loot reaches a
 * chest: in the player's world, within reach, in sight, standing, usable,
 * and open to them under LOTR's banners and other mods' protection, each
 * asked only once the one
 * before it holds.
 */
public final class MissiveBoardAccessTest {

    /** A place at the board that answers as told and writes down what it was asked. */
    private static final class Site implements MissiveBoardService.BoardSite {
        final List<String> asked = new ArrayList<String>();
        boolean world = true;
        boolean reach = true;
        boolean sight = true;
        boolean stands = true;
        boolean usable = true;
        boolean banner = true;
        boolean others = true;

        @Override
        public boolean inPlayersWorld() {
            asked.add("world");
            return world;
        }

        @Override
        public boolean withinReach() {
            asked.add("reach");
            return reach;
        }

        @Override
        public boolean inSight() {
            asked.add("sight");
            return sight;
        }

        @Override
        public boolean boardStands() {
            asked.add("stands");
            return stands;
        }

        @Override
        public boolean usable() {
            asked.add("usable");
            return usable;
        }

        @Override
        public boolean bannerAllows() {
            asked.add("banner");
            return banner;
        }

        @Override
        public boolean othersAllow() {
            asked.add("others");
            return others;
        }
    }

    @Test
    public void aBoardInSightAndUnprotectedMayBeUsed() {
        Site site = new Site();
        assertNull(MissiveBoardService.refusal(site));
        assertEquals(Arrays.asList("world", "reach", "sight", "stands",
                "usable", "banner", "others"), site.asked);
    }

    @Test
    public void aBoardOutOfSightIsRefusedBeforeAnythingThereIsRead() {
        Site site = new Site();
        site.sight = false;
        assertEquals("chat.losttales.missive.out_of_sight",
                MissiveBoardService.refusal(site));
        assertEquals(Arrays.asList("world", "reach", "sight"), site.asked);
    }

    @Test
    public void aBoardUnderABannerIsRefused() {
        Site site = new Site();
        site.banner = false;
        assertEquals("chat.losttales.missive.protected",
                MissiveBoardService.refusal(site));
    }

    @Test
    public void aBoardAnotherModProtectsIsRefused() {
        Site site = new Site();
        site.others = false;
        assertEquals("chat.losttales.missive.protected",
                MissiveBoardService.refusal(site));
    }

    @Test
    public void aBoardOutOfReachIsNeverLookedAt() {
        Site site = new Site();
        site.reach = false;
        assertEquals("chat.losttales.missive.too_far",
                MissiveBoardService.refusal(site));
        assertEquals(Arrays.asList("world", "reach"), site.asked);
        Site elsewhere = new Site();
        elsewhere.world = false;
        assertEquals("chat.losttales.missive.board_gone",
                MissiveBoardService.refusal(elsewhere));
        Site gone = new Site();
        gone.stands = false;
        assertEquals("chat.losttales.missive.board_gone",
                MissiveBoardService.refusal(gone));
    }
}
