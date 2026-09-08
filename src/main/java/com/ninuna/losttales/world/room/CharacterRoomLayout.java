package com.ninuna.losttales.world.room;

/**
 * The shape of the character room: a sealed, lit box around the origin
 * that the character creator is entered in from the main menu.
 *
 * <p>Pure geometry, answered block by block, so the chunk generator can
 * ask about any position without knowing where chunk borders fall and a
 * test can walk the whole box. The room is small enough to lie within the
 * four chunks that meet at the origin, so a world made of it holds four
 * chunks of blocks and nothing else.</p>
 *
 * <p>Interior blocks are air; the floor is the block under them, the walls
 * ring them, and the ceiling closes the box with lights set into it on a
 * grid, so the room is lit from within and no daylight is needed.</p>
 */
public final class CharacterRoomLayout {

    /** What a position in the room is made of; NONE is air, inside or out. */
    public enum Material {
        NONE, FLOOR, WALL, CEILING, LIGHT
    }

    /** The interior reaches this far from the origin along x and z. */
    public static final int INNER_HALF = 11;
    /** The walls stand one block beyond the interior. */
    public static final int OUTER_HALF = INNER_HALF + 1;
    /** The block the player stands on. */
    public static final int FLOOR_Y = 63;
    /** Interior height in blocks, floor to ceiling. */
    public static final int INNER_HEIGHT = 7;
    public static final int CEILING_Y = FLOOR_Y + INNER_HEIGHT + 1;
    /** The lowest and highest block the room occupies. */
    public static final int MIN_Y = FLOOR_Y;
    public static final int MAX_Y = CEILING_Y;
    /** Ceiling lights sit on every multiple of this along x and z. */
    public static final int LIGHT_SPACING = 6;

    /** Where the player stands when the room opens: its middle, on the floor. */
    public static final int SPAWN_X = 0;
    public static final int SPAWN_Y = FLOOR_Y + 1;
    public static final int SPAWN_Z = 0;

    private CharacterRoomLayout() {}

    /** What stands at that position, or NONE for air anywhere. */
    public static Material materialAt(int x, int y, int z) {
        if (Math.abs(x) > OUTER_HALF || Math.abs(z) > OUTER_HALF
                || y < MIN_Y || y > MAX_Y) {
            return Material.NONE;
        }
        if (y == FLOOR_Y) {
            return Material.FLOOR;
        }
        if (y == CEILING_Y) {
            return isLightPosition(x, z) ? Material.LIGHT : Material.CEILING;
        }
        if (Math.abs(x) == OUTER_HALF || Math.abs(z) == OUTER_HALF) {
            return Material.WALL;
        }
        return Material.NONE;
    }

    /** Whether that position is open air inside the box. */
    public static boolean isInside(int x, int y, int z) {
        return Math.abs(x) <= INNER_HALF && Math.abs(z) <= INNER_HALF
                && y > FLOOR_Y && y < CEILING_Y;
    }

    /** Whether any block of the room falls within that chunk. */
    public static boolean touchesChunk(int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        return minX <= OUTER_HALF && minX + 15 >= -OUTER_HALF
                && minZ <= OUTER_HALF && minZ + 15 >= -OUTER_HALF;
    }

    private static boolean isLightPosition(int x, int z) {
        return Math.abs(x) < OUTER_HALF && Math.abs(z) < OUTER_HALF
                && x % LIGHT_SPACING == 0 && z % LIGHT_SPACING == 0;
    }
}
