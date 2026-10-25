package com.ninuna.losttales.world.structure.odane;

import com.ninuna.losttales.world.waystone.LostTalesStructureScan;
import com.ninuna.losttales.world.waystone.LostTalesWaystoneHouse;
import java.util.List;

/**
 * The Odane glowstone house as its house files write it (CONTENT-10): the
 * same {@code glowstone_house} a generated waystone stands in (WAY-17), so
 * a server's own file of that name takes the bundled one's place here too.
 * The house covers its scan's width and depth, clears the layers its
 * clearance names above the ground, and its Odane man lives where the
 * waystone would stand.
 */
public final class OdaneHousePlan {
    /** The house file the Odane house is read from. */
    public static final String HOUSE_ID = "losttales:glowstone_house";
    /** How far the ground under the house may rise or fall, in blocks. */
    public static final int MAX_GROUND_STEP = 2;

    /** Finds a house by its id; null where no file of that id loaded. */
    public interface Houses {
        LostTalesWaystoneHouse get(String id);
    }

    private final LostTalesWaystoneHouse house;

    private OdaneHousePlan(LostTalesWaystoneHouse house) {
        this.house = house;
    }

    /** The plan of the Odane house {@code houses} holds; null where there is none to build. */
    public static OdaneHousePlan from(Houses houses) {
        return houses == null ? null : of(houses.get(HOUSE_ID));
    }

    /** The plan of {@code house}; null for no house or one that lays no block. */
    public static OdaneHousePlan of(LostTalesWaystoneHouse house) {
        return house == null || house.getBlocks().isEmpty() ? null : new OdaneHousePlan(house);
    }

    public List<LostTalesStructureScan.Block> getBlocks() { return this.house.getBlocks(); }
    public int getMinX() { return this.house.getMinX(); }
    public int getMaxX() { return this.house.getMaxX(); }
    public int getMinZ() { return this.house.getMinZ(); }
    public int getMaxZ() { return this.house.getMaxZ(); }
    /** The highest layer above the ground cleared to air before the house stands. */
    public int getClearance() { return this.house.getClearance(); }
    /** Where the Odane man lives, from the scan's origin: the waystone's place. */
    public int[] getResident() { return this.house.getWaystone(); }

    /** Whether ground from {@code lowest} to {@code highest} is level enough for the house. */
    public static boolean isLevelEnough(int lowest, int highest) {
        return highest - lowest <= MAX_GROUND_STEP;
    }
}
