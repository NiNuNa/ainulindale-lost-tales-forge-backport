package com.ninuna.losttales.faction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.util.LostTalesLangFile;
import java.util.HashMap;
import java.util.Map;
import lotr.common.fac.LOTRControlZone;
import lotr.common.fac.LOTRFaction;
import lotr.common.fac.LOTRMapRegion;
import lotr.common.world.map.LOTRWaypoint;
import org.junit.Test;

/**
 * The factions' land as LOTR reads it: Tharbad's map region and control
 * zone stand round LOTR's Tharbad (FACTION-01), no two factions share a
 * region, and Arnor is no faction of the mod's (MOD-16). A rank is
 * feminine wherever its lang line is (FACTION-03).
 */
public final class ELostTalesFactionTest {

    @Test
    public void tharbadsRegionStandsRoundLotrsTharbad() {
        LOTRMapRegion region = ELostTalesFaction.THARBAD.getFactionMapRegion();

        assertEquals((int) LOTRWaypoint.THARBAD.getX(), region.mapX);
        assertEquals((int) LOTRWaypoint.THARBAD.getY(), region.mapY);
        assertEquals(50, region.radius);
    }

    @Test
    public void noTwoFactionsShareARegion() {
        ELostTalesFaction[] factions = ELostTalesFaction.values();
        for (int i = 0; i < factions.length; i++) {
            for (int j = i + 1; j < factions.length; j++) {
                LOTRMapRegion a = factions[i].getFactionMapRegion();
                LOTRMapRegion b = factions[j].getFactionMapRegion();
                assertFalse(factions[i] + " and " + factions[j] + " share a region",
                        a.mapX == b.mapX && a.mapY == b.mapY);
            }
        }
    }

    @Test
    public void tharbadHoldsAZoneRoundLotrsTharbad() {
        ELostTalesFaction.setUpFactionControlZones();

        LOTRControlZone found = null;
        for (LOTRControlZone zone
                : ELostTalesFaction.THARBAD.getFaction().getControlZones()) {
            if (zone.mapX == LOTRWaypoint.THARBAD.getX()
                    && zone.mapY == LOTRWaypoint.THARBAD.getY()) {
                found = zone;
            }
        }
        assertNotNull("Tharbad holds no zone at LOTR's Tharbad", found);
        assertEquals(50, found.radius);
    }

    @Test
    public void aRankIsFeminineWhereverItsLineIs() {
        Map<String, String> lines = new HashMap<String, String>();
        lines.put("lotr.faction.TESTERS.rank.c", "Warden");
        lines.put("lotr.faction.TESTERS.rank.c_fm", "Wardeness");

        assertTrue(ELostTalesFaction.hasFeminineRank(lines, "TESTERS", "c"));
        assertFalse(ELostTalesFaction.hasFeminineRank(lines, "TESTERS", "d"));
        assertFalse(ELostTalesFaction.hasFeminineRank(lines, "OTHERS", "c"));
    }

    /** The shipped lines: the feminine ranks they hold, each with its full title, so none shows a raw key. */
    @Test
    public void everyShippedFeminineRankHasItsFullTitle() {
        Map<String, String> english = LostTalesLangFile.english();
        int feminine = 0;
        for (ELostTalesFaction faction : ELostTalesFaction.values()) {
            for (String rank : new String[] {"a", "b", "c", "d", "e", "f", "g", "h"}) {
                String key = ELostTalesFaction.rankKey(faction.name(), rank);
                assertTrue(key, english.containsKey(key));
                if (ELostTalesFaction.hasFeminineRank(english, faction.name(), rank)) {
                    feminine++;
                    assertTrue(key + "_fm.f", english.containsKey(key + "_fm.f"));
                }
            }
        }
        assertTrue(ELostTalesFaction.hasFeminineRank(english, "LOSSOTH", "b"));
        assertTrue(ELostTalesFaction.hasFeminineRank(english, "BLUE_GOBLINS", "h"));
        assertFalse(ELostTalesFaction.hasFeminineRank(english, "LOSSOTH", "a"));
        assertEquals(7, feminine);
        for (String key : english.keySet()) {
            if (key.startsWith("lotr.faction.") && key.endsWith("_fm")) {
                assertTrue(key + " belongs to no rank of a faction of the mod's",
                        english.containsKey(key.substring(0, key.length() - 3)));
            }
        }
    }

    @Test
    public void arnorIsNoFactionOfTheMods() {
        for (ELostTalesFaction faction : ELostTalesFaction.values()) {
            assertFalse("ARNOR".equals(faction.name()));
        }
        for (LOTRFaction faction : LOTRFaction.values()) {
            assertFalse("ARNOR".equals(faction.codeName()));
        }
    }
}
