package com.ninuna.losttales.quest;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The quests made while the game runs stay in the registry only while a
 * quest log holds them, and never take a bundled quest's id.
 */
public final class LostTalesQuestRegistryMissiveTest {

    @After
    public void cleanUp() {
        LostTalesQuestRegistry.clearMissives();
    }

    @Test
    public void onlyTheMissivesALogStillHoldsAreKept() {
        assertTrue(LostTalesQuestRegistry.registerMissive(
                missive("losttales:missive_a")));
        assertTrue(LostTalesQuestRegistry.registerMissive(
                missive("losttales:missive_b")));
        assertTrue(LostTalesQuestRegistry.registerMissive(
                missive("losttales:missive_c")));
        LostTalesQuestRegistry.retainMissives(new HashSet<String>(
                Arrays.asList("losttales:missive_b", "losttales:unknown")));
        assertNull(LostTalesQuestRegistry.getQuest("losttales:missive_a"));
        assertNotNull(LostTalesQuestRegistry.getQuest("losttales:missive_b"));
        assertNull(LostTalesQuestRegistry.getQuest("losttales:missive_c"));
        assertFalse(LostTalesQuestRegistry.getQuests().contains(null));
        LostTalesQuestRegistry.retainMissives(
                Collections.<String>emptySet());
        assertNull(LostTalesQuestRegistry.getQuest("losttales:missive_b"));
    }

    @Test
    public void aBundledQuestIsNeverDroppedWithTheMissives() {
        LostTalesQuestDefinition bundled =
                LostTalesQuestRegistry.getQuests().iterator().next();
        assertFalse("a missive never takes a bundled quest's id",
                LostTalesQuestRegistry.registerMissive(
                        missive(bundled.getId())));
        LostTalesQuestRegistry.retainMissives(
                Collections.<String>emptySet());
        assertNotNull(LostTalesQuestRegistry.getQuest(bundled.getId()));
    }

    @Test
    public void aTouchLooksOnlyAtTheQuestsATouchMayStart() {
        LostTalesQuestDefinition atStone = touched("losttales:at_stone",
                Collections.singletonMap("block", "minecraft:stone"));
        LostTalesQuestDefinition byGoblin = touched("losttales:by_goblin",
                Collections.singletonMap("entity", "lotr.GundabadOrc"));
        assertTrue(LostTalesQuestRegistry.registerMissive(atStone));
        assertTrue(LostTalesQuestRegistry.registerMissive(byGoblin));
        assertTrue(LostTalesQuestRegistry.registerMissive(
                missive("losttales:missive_a")));
        assertTrue(LostTalesQuestRegistry.getBlockGivenQuests().contains(atStone));
        assertFalse(LostTalesQuestRegistry.getBlockGivenQuests().contains(byGoblin));
        assertTrue(LostTalesQuestRegistry.getCreatureGivenQuests().contains(byGoblin));
        assertFalse(LostTalesQuestRegistry.getCreatureGivenQuests().contains(atStone));
        for (LostTalesQuestDefinition quest : LostTalesQuestRegistry.getBlockGivenQuests()) {
            assertFalse("a missive is never looked at for a touch",
                    "losttales:missive_a".equals(quest.getId()));
        }
        LostTalesQuestRegistry.clearMissives();
        assertFalse(LostTalesQuestRegistry.getBlockGivenQuests().contains(atStone));
    }

    private static LostTalesQuestDefinition touched(String id,
            java.util.Map<String, String> interaction) {
        return new LostTalesQuestDefinition(id, "Q", "", false, false,
                LostTalesQuestDefinition.START_MODE_INTERACTION,
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(), interaction,
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(),
                Collections.<LostTalesQuestStageDefinition>emptyList(), false,
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap());
    }

    private static LostTalesQuestDefinition missive(String id) {
        return new LostTalesQuestDefinition(id, "Q", "", false, false,
                LostTalesQuestDefinition.START_MODE_LOCKED,
                null, null, null, null, null, null, null,
                Collections.<LostTalesQuestStageDefinition>emptyList(), false,
                null, null);
    }
}
