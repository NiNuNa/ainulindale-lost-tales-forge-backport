package com.ninuna.losttales.world.map.road;

import com.ninuna.losttales.util.LostTalesUtil;
import com.ninuna.losttales.world.map.waypoint.ELostTalesWaypoint;

public enum ELostTalesRoad {
    ODANE_COAST_ROAD("OdaneCoastRoad", new Object[]{
            ELostTalesWaypoint.ODANE_PORT.getWaypoint(),
            new double[]{3096.0D, 822.0D},
            ELostTalesWaypoint.ODANE.getWaypoint(),
            new double[]{3160.0D, 815.0D},
            ELostTalesWaypoint.ODANE_EASTWATCH.getWaypoint()
    }),
    ODANE_MOUNTAIN_ROAD("OdaneMountainRoad", new Object[]{
            ELostTalesWaypoint.ODANE.getWaypoint(),
            new double[]{3138.0D, 760.0D},
            ELostTalesWaypoint.ODANE_MOUNTAINS.getWaypoint()
    }),
    ODANE_SOUTH_ROAD("OdaneSouthRoad", new Object[]{
            ELostTalesWaypoint.ODANE.getWaypoint(),
            new double[]{3102.0D, 842.0D},
            ELostTalesWaypoint.ODANE_PORT.getWaypoint()
    });

    private final String roadName;
    private final Object[] routePoints;

    ELostTalesRoad(String roadName, Object[] routePoints) {
        this.roadName = roadName;
        this.routePoints = routePoints;
    }

    public static void initAndRegisterRoads() {
        for (ELostTalesRoad road : ELostTalesRoad.values()) {
            LostTalesUtil.addRoad(road.roadName, road.routePoints);
        }
    }
}
