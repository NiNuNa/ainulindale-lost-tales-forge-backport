package com.ninuna.losttales.faction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import lotr.common.fac.LOTRControlZone;
import lotr.common.fac.LOTRFaction;
import lotr.common.fac.LOTRMapRegion;
import lotr.common.world.map.LOTRWaypoint;
import org.junit.Test;

/**
 * The factions' land as LOTR reads it: Tharbad's map region and control
 * zone stand round LOTR's Tharbad (FACTION-01), no two factions share a
 * region, and Arnor is no faction of the mod's (MOD-16).
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
    public void arnorIsNoFactionOfTheMods() {
        for (ELostTalesFaction faction : ELostTalesFaction.values()) {
            assertFalse("ARNOR".equals(faction.name()));
        }
        for (LOTRFaction faction : LOTRFaction.values()) {
            assertFalse("ARNOR".equals(faction.codeName()));
        }
    }
}
