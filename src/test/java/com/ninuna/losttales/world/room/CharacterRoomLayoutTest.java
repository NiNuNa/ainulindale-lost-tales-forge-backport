package com.ninuna.losttales.world.room;

import com.ninuna.losttales.world.room.CharacterRoomLayout.Material;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CharacterRoomLayoutTest {

    private static final int OUTER = CharacterRoomLayout.OUTER_HALF;
    private static final int FLOOR = CharacterRoomLayout.FLOOR_Y;
    private static final int CEILING = CharacterRoomLayout.CEILING_Y;

    @Test
    public void floorCoversTheWholeFootprint() {
        for (int x = -OUTER; x <= OUTER; x++) {
            for (int z = -OUTER; z <= OUTER; z++) {
                assertEquals(Material.FLOOR,
                        CharacterRoomLayout.materialAt(x, FLOOR, z));
            }
        }
    }

    @Test
    public void ceilingClosesTheBoxWithLightsOnAGrid() {
        int lights = 0;
        for (int x = -OUTER; x <= OUTER; x++) {
            for (int z = -OUTER; z <= OUTER; z++) {
                Material material = CharacterRoomLayout.materialAt(x, CEILING, z);
                assertTrue(material == Material.CEILING || material == Material.LIGHT);
                if (material == Material.LIGHT) {
                    lights++;
                }
            }
        }
        assertEquals(9, lights);
        assertEquals(Material.LIGHT, CharacterRoomLayout.materialAt(0, CEILING, 0));
        assertEquals(Material.LIGHT, CharacterRoomLayout.materialAt(6, CEILING, -6));
        assertEquals(Material.CEILING, CharacterRoomLayout.materialAt(3, CEILING, 0));
        // The lamp grid stops short of the walls.
        assertEquals(Material.CEILING, CharacterRoomLayout.materialAt(OUTER, CEILING, 0));
    }

    @Test
    public void wallsRingTheInteriorAndInteriorIsAir() {
        for (int y = FLOOR + 1; y < CEILING; y++) {
            for (int x = -OUTER; x <= OUTER; x++) {
                for (int z = -OUTER; z <= OUTER; z++) {
                    boolean onEdge = Math.abs(x) == OUTER || Math.abs(z) == OUTER;
                    Material expected = onEdge ? Material.WALL : Material.NONE;
                    assertEquals(x + "," + y + "," + z, expected,
                            CharacterRoomLayout.materialAt(x, y, z));
                    assertEquals(!onEdge, CharacterRoomLayout.isInside(x, y, z));
                }
            }
        }
    }

    @Test
    public void nothingStandsOutsideTheBox() {
        assertEquals(Material.NONE, CharacterRoomLayout.materialAt(OUTER + 1, FLOOR, 0));
        assertEquals(Material.NONE, CharacterRoomLayout.materialAt(0, FLOOR, -OUTER - 1));
        assertEquals(Material.NONE, CharacterRoomLayout.materialAt(0, FLOOR - 1, 0));
        assertEquals(Material.NONE, CharacterRoomLayout.materialAt(0, CEILING + 1, 0));
        assertFalse(CharacterRoomLayout.isInside(0, FLOOR, 0));
        assertFalse(CharacterRoomLayout.isInside(0, CEILING, 0));
    }

    @Test
    public void spawnStandsOnTheFloorInsideTheRoom() {
        assertTrue(CharacterRoomLayout.isInside(CharacterRoomLayout.SPAWN_X,
                CharacterRoomLayout.SPAWN_Y, CharacterRoomLayout.SPAWN_Z));
        assertTrue(CharacterRoomLayout.isInside(CharacterRoomLayout.SPAWN_X,
                CharacterRoomLayout.SPAWN_Y + 1, CharacterRoomLayout.SPAWN_Z));
        assertEquals(Material.FLOOR, CharacterRoomLayout.materialAt(
                CharacterRoomLayout.SPAWN_X, CharacterRoomLayout.SPAWN_Y - 1,
                CharacterRoomLayout.SPAWN_Z));
    }

    @Test
    public void roomLiesWithinTheFourChunksAtTheOrigin() {
        assertTrue(CharacterRoomLayout.touchesChunk(0, 0));
        assertTrue(CharacterRoomLayout.touchesChunk(-1, 0));
        assertTrue(CharacterRoomLayout.touchesChunk(0, -1));
        assertTrue(CharacterRoomLayout.touchesChunk(-1, -1));
        assertFalse(CharacterRoomLayout.touchesChunk(1, 0));
        assertFalse(CharacterRoomLayout.touchesChunk(0, 1));
        assertFalse(CharacterRoomLayout.touchesChunk(-2, 0));
        assertFalse(CharacterRoomLayout.touchesChunk(0, -2));
        assertFalse(CharacterRoomLayout.touchesChunk(1, 1));
        for (int chunkX = -3; chunkX <= 3; chunkX++) {
            for (int chunkZ = -3; chunkZ <= 3; chunkZ++) {
                if (CharacterRoomLayout.touchesChunk(chunkX, chunkZ)) {
                    continue;
                }
                for (int localX = 0; localX < 16; localX++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        for (int y = CharacterRoomLayout.MIN_Y; y <= CharacterRoomLayout.MAX_Y; y++) {
                            assertEquals(Material.NONE, CharacterRoomLayout.materialAt(
                                    (chunkX << 4) + localX, y, (chunkZ << 4) + localZ));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void boxIsSealed() {
        // Every interior air block has something other than air on the
        // far side of every face it shares with the outside.
        for (int x = -OUTER; x <= OUTER; x++) {
            for (int z = -OUTER; z <= OUTER; z++) {
                for (int y = FLOOR; y <= CEILING; y++) {
                    if (!CharacterRoomLayout.isInside(x, y, z)) {
                        continue;
                    }
                    assertSolidBeyondInterior(x + 1, y, z);
                    assertSolidBeyondInterior(x - 1, y, z);
                    assertSolidBeyondInterior(x, y + 1, z);
                    assertSolidBeyondInterior(x, y - 1, z);
                    assertSolidBeyondInterior(x, y, z + 1);
                    assertSolidBeyondInterior(x, y, z - 1);
                }
            }
        }
    }

    private static void assertSolidBeyondInterior(int x, int y, int z) {
        if (CharacterRoomLayout.isInside(x, y, z)) {
            return;
        }
        assertTrue(x + "," + y + "," + z,
                CharacterRoomLayout.materialAt(x, y, z) != Material.NONE);
    }
}
