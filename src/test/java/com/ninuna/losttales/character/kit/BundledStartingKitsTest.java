package com.ninuna.losttales.character.kit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.progression.ProgressionFiles;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/**
 * The starting kits shipped in the jar (CHAR-40), read the way the server
 * reads them: every file loads with no warning, each of the races has
 * one kit and no faction has one, and every item a kit gives is one LOTR
 * v36.15 or Minecraft registers, read from their bytecode.
 */
public final class BundledStartingKitsTest {

    @Test
    public void lotrAndMinecraftNamesAreReadFromTheirBytecode() throws IOException {
        Set<String> lotr = GameItemNames.lotr();
        Set<String> vanilla = GameItemNames.vanilla();
        assertTrue(lotr.contains("lotr:item.hobbitPipe"));
        assertTrue(lotr.contains("lotr:tile.woodElvenTorch"));
        assertTrue(!lotr.contains("lotr:item.woodElvenTorch"));
        assertTrue(vanilla.contains("minecraft:bread"));
        assertTrue(vanilla.contains("minecraft:torch"));
        assertTrue(!vanilla.contains("minecraft:lembas"));
    }

    @Test
    public void everyBundledKitLoadsWithNoWarning() throws IOException {
        DataFiles.Result<StartingKit> read = read();
        assertEquals(new ArrayList<String>(), read.problems);
        assertEquals(CharacterRaceRegistry.getAll().size(), read.values.size());
    }

    @Test
    public void eachRaceHasOneKitAndNoFactionHasOne() throws IOException {
        Map<String, StartingKit> byRace = new LinkedHashMap<String, StartingKit>();
        for (StartingKit kit : read().values) {
            assertEquals(kit.getId(), "", kit.getFactionId());
            assertTrue(kit.getId(), byRace.put(kit.getRaceId(), kit) == null);
            assertTrue(kit.getId() + " gives food, a light and a tool or weapon",
                    kit.getItems().size() >= 3);
        }
        for (CharacterRaceDefinition race : CharacterRaceRegistry.getAll()) {
            assertTrue(race.getId(), byRace.containsKey(race.getId()));
        }
    }

    @Test
    public void theHobbitKitHoldsBreadAPipeAndPipeweed() throws IOException {
        StartingKit hobbit = null;
        for (StartingKit kit : read().values) {
            if (CharacterRaceRegistry.HOBBIT.equals(kit.getRaceId())) {
                hobbit = kit;
            }
        }
        List<String> items = new ArrayList<String>();
        for (KitItem item : hobbit.getItems()) {
            items.add(item.getItemId());
        }
        assertTrue(items.contains("minecraft:bread"));
        assertTrue(items.contains("lotr:item.hobbitPipe"));
        assertTrue(items.contains("lotr:item.pipeweed"));
    }

    private static DataFiles.Result<StartingKit> read() throws IOException {
        return DataFiles.readBundled(ProgressionFiles.CLASSPATH, StartingKitFiles.INDEX,
                StartingKitFiles.LIST_KEY, StartingKitFiles.kind(registered()));
    }

    /** The six races, no faction, and every item LOTR or Minecraft registers, a stack of 64. */
    static StartingKitFiles.Game registered() throws IOException {
        final Set<String> items = GameItemNames.lotr();
        items.addAll(GameItemNames.vanilla());
        return new StartingKitFiles.Game() {
            @Override
            public boolean hasRace(String raceId) {
                return CharacterRaceRegistry.get(raceId) != null;
            }

            @Override
            public boolean hasFaction(String factionId) {
                return false;
            }

            @Override
            public int stackLimit(String itemId, int damage) {
                return items.contains(itemId) ? 64 : 0;
            }

            @Override
            public boolean wornThrough(String itemId, int damage) {
                return false;
            }
        };
    }
}
