package com.ninuna.losttales.world.map.waypoint;

import com.ninuna.losttales.faction.ELostTalesFaction;
import com.ninuna.losttales.util.LostTalesUtil;
import lotr.common.world.map.LOTRWaypoint;

public enum ELostTalesWaypoint {
    SUN_ELVES(LostTalesUtil.addWaypoint("SUN_ELVES", Region.SUN_ELVES.getRegion(), ELostTalesFaction.SUN_ELVES.getFaction(), 2504, 1514, true)),
    MOON_ELVES(LostTalesUtil.addWaypoint("MOON_ELVES", Region.MOON_ELVES.getRegion(), ELostTalesFaction.MOON_ELVES.getFaction(), 1568, 535, true)),
    MOON_ELVES_2(LostTalesUtil.addWaypoint("MOON_ELVES_2", Region.MOON_ELVES.getRegion(), ELostTalesFaction.MOON_ELVES.getFaction(), 1585, 531, true)),
    ODANE(LostTalesUtil.addWaypoint("ODANE", Region.ODANE.getRegion(), ELostTalesFaction.ODANE.getFaction(), 3132, 790, false)),
    ODANE_MOUNTAINS(LostTalesUtil.addWaypoint("ODANE_MOUNTAINS", Region.ODANE.getRegion(), ELostTalesFaction.ODANE.getFaction(), 3142, 735, false)),
    ODANE_PORT(LostTalesUtil.addWaypoint("ODANE_PORT", Region.ODANE.getRegion(), ELostTalesFaction.ODANE.getFaction(), 3070, 850, false)),
    ODANE_EASTWATCH(LostTalesUtil.addWaypoint("ODANE_EASTWATCH", Region.ODANE.getRegion(), ELostTalesFaction.ODANE.getFaction(), 3177, 832, false));

    private final LOTRWaypoint waypoint;

    ELostTalesWaypoint(LOTRWaypoint waypoint) {
        this.waypoint = waypoint;
    }

    public LOTRWaypoint getWaypoint() {
        return waypoint;
    }

    public enum Region {
        MOON_ELVES(LostTalesUtil.addWaypointRegion("MOON_ELVES")),
        SUN_ELVES(LostTalesUtil.addWaypointRegion("SUN_ELVES")),
        ODANE(LostTalesUtil.addWaypointRegion("ODANE"));

        final private LOTRWaypoint.Region region;

        Region(LOTRWaypoint.Region region) {
            this.region = region;
        }

        public LOTRWaypoint.Region getRegion() {
            return region;
        }
    }
}
