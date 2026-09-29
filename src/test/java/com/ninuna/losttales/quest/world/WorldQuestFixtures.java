package com.ninuna.losttales.quest.world;

import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** World quests to test with, built as a quest file would give them. */
public final class WorldQuestFixtures {
    private WorldQuestFixtures() {}

    public static LostTalesQuestDefinition greenway(Map<String, String> world,
            String startMode,
            LostTalesQuestObjectiveDefinition... objectives) {
        List<LostTalesQuestStageDefinition> stages = Collections.singletonList(
                new LostTalesQuestStageDefinition("hold",
                        Arrays.asList(objectives)));
        return new LostTalesQuestDefinition("losttales:world/greenway",
                "Hold the Greenway", "Orcs gather on the road.", false, false,
                startMode, Collections.<String, String>emptyMap(),
                Collections.singletonMap("experience", "50"),
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(), world, stages);
    }

    /** A world quest that can run: seven days, two kills and a craft to reach. */
    public static LostTalesQuestDefinition greenway() {
        return greenway(world("7", null),
                LostTalesQuestDefinition.START_MODE_LOCKED,
                objective("orcs", "kill", "entity", "lotr.Orc", "500"),
                objective("arrows", "craft", "item", "minecraft:arrow", "64"));
    }

    public static Map<String, String> world(String days, String least) {
        Map<String, String> world = new LinkedHashMap<String, String>();
        if (days != null) {
            world.put(WorldQuestRules.DAYS, days);
        }
        if (least != null) {
            world.put(WorldQuestRules.LEAST, least);
        }
        return world;
    }

    public static LostTalesQuestObjectiveDefinition objective(String id,
            String type, String selectorKey, String selector, String count) {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put(selectorKey, selector);
        params.put("count", count);
        return new LostTalesQuestObjectiveDefinition(id, type, id, false,
                params);
    }
}
