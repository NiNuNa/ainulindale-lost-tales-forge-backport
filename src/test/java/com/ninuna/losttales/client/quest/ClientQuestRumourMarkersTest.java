package com.ninuna.losttales.client.quest;

import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestDefinitionJsonParser;
import com.ninuna.losttales.quest.QuestRumourPlace;
import com.ninuna.losttales.quest.progress.LostTalesQuestHistoryEntry;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A rumour's place on the map (QUEST-20): marked while its quest is not
 * taken and the place not found; a place found stands as its own marker.
 */
public final class ClientQuestRumourMarkersTest {
    private static final String QUEST_ID = "losttales:server/old_road";
    private static final String QUEST = "{\"id\":\"" + QUEST_ID + "\","
            + "\"title\":\"The Old Road\",\"startMode\":\"interaction\","
            + "\"interaction\":{\"entity\":\"losttales.Nia\"},"
            + "\"rumour\":{\"line\":\"They say the woman by the well needs a hand.\","
            + "\"entity\":\"lotr.BreeMan\",\"marker\":\"losttales:bree_well\"},"
            + "\"stages\":[{\"id\":\"1\",\"objectives\":[{\"id\":\"a\",\"type\":\"gather\","
            + "\"params\":{\"item\":\"minecraft:log\"}}]}]}";

    @After
    public void forget() {
        LostTalesClientQuestProgressStore.clear();
        LostTalesClientQuestDefinitionStore.clearServerSentDefinitions();
    }

    private static void synced(List<LostTalesQuestProgress> running,
                               List<String> discovered) {
        LostTalesClientQuestProgressStore.update(running,
                Collections.<LostTalesQuestHistoryEntry>emptyList(),
                Collections.<String>emptySet(), discovered, "");
    }

    @Test
    public void theMapMarksARumourUntilItsPlaceIsFoundOrItsQuestTaken() {
        LostTalesQuestDefinition quest = LostTalesQuestDefinitionJsonParser.parseQuest(
                new StringReader(QUEST), "quests/old_road.json",
                new java.util.ArrayList<String>());
        LostTalesClientQuestDefinitionStore.addServerQuestDefinitions(true,
                Collections.singletonList(quest));
        QuestRumourPlace well = new QuestRumourPlace(QUEST_ID,
                "losttales:bree_well", "", "", 100, 120.0D, -340.0D);
        synced(Collections.<LostTalesQuestProgress>emptyList(),
                Collections.<String>emptyList());
        LostTalesClientQuestProgressStore.setHeardRumours(
                Collections.singleton(QUEST_ID));
        LostTalesClientQuestProgressStore.setRumourPlaces(
                Collections.singletonList(well));

        List<ClientQuestCatalog.RumourMarker> markers = ClientQuestCatalog.rumourMarkers();
        assertEquals(1, markers.size());
        assertEquals(well, markers.get(0).getPlace());
        assertEquals("The Old Road", markers.get(0).getTitle());

        synced(Collections.<LostTalesQuestProgress>emptyList(),
                Collections.singletonList("losttales:bree_well"));
        assertTrue("found, the place stands as its own marker",
                ClientQuestCatalog.rumourMarkers().isEmpty());

        synced(Collections.singletonList(new LostTalesQuestProgress(QUEST_ID, 0,
                        "1", null, 1L, 0L)),
                Collections.<String>emptyList());
        assertTrue("taken, the rumour is no lead any more",
                ClientQuestCatalog.rumourMarkers().isEmpty());
    }
}
