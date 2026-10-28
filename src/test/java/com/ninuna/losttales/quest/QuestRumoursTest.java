package com.ninuna.losttales.quest;

import com.ninuna.losttales.network.packet.LostTalesQuestOfferPacket;
import com.ninuna.losttales.network.packet.LostTalesQuestSyncPacket;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import com.ninuna.losttales.quest.progress.LostTalesQuestHistoryEntry;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Rumours (QUEST-20): a quest file writes a lead and who tells it; a
 * character keeps the rumours it heard until it takes the quest; the
 * journal is sent them with the places they point to, which they discover
 * nothing of, and the teller's words are said by the player's game.
 */
public final class QuestRumoursTest {
    private static final String QUEST = "{\"id\":\"losttales:server/old_road\","
            + "\"startMode\":\"interaction\",\"interaction\":{\"entity\":\"losttales.Nia\"},"
            + "\"rumour\":{\"line\":\"They say the woman by the well needs a hand.\","
            + "\"entity\":\"lotr.BreeMan,lotr.BreeInnkeeper\",\"marker\":\"losttales:bree_well\"},"
            + "\"stages\":[{\"id\":\"1\",\"objectives\":[{\"id\":\"a\",\"type\":\"gather\","
            + "\"params\":{\"item\":\"minecraft:log\"}}]}]}";

    private static List<String> warnings(String json) {
        List<String> unread = new ArrayList<String>();
        LostTalesQuestDefinition quest = LostTalesQuestDefinitionJsonParser.parseQuest(
                new StringReader(json), "quests/test.json", unread);
        return LostTalesQuestDefinitionValidator.warningsOf(quest, unread);
    }

    @Test
    public void aRumourIsWrittenWithItsTellers() {
        assertEquals(Collections.<String>emptyList(), warnings(QUEST));
        LostTalesQuestDefinition quest = LostTalesQuestDefinitionJsonParser.parseQuest(
                new StringReader(QUEST), "quests/test.json",
                new ArrayList<String>());
        assertEquals("They say the woman by the well needs a hand.",
                LostTalesQuestWords.rumourLine(quest));
        assertEquals("lotr.BreeMan,lotr.BreeInnkeeper",
                LostTalesQuestRules.rumour(quest).get("entity"));
        List<String> found = warnings(QUEST.replace(
                "\"entity\":\"lotr.BreeMan,lotr.BreeInnkeeper\",", "\"teller\":\"x\","));
        assertEquals(found.toString(), 2, found.size());
        assertTrue(found.get(0).contains("rumour names 'teller'"));
        assertTrue(found.get(1).contains("names no 'entity' or 'group' to tell it"));
    }

    @Test
    public void aCharacterKeepsItsRumoursUntilItTakesTheQuest() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        assertTrue(data.hearRumour("losttales:server/old_road", ""));
        assertFalse("once", data.hearRumour("losttales:server/old_road", ""));
        assertTrue(data.getQuestIdsInLog().contains("losttales:server/old_road"));
        NBTTagCompound wrapper = new NBTTagCompound();
        data.saveNBTData(wrapper);
        LostTalesQuestPlayerData loaded = new LostTalesQuestPlayerData();
        loaded.loadNBTData(wrapper);
        assertEquals(Collections.singleton("losttales:server/old_road"),
                loaded.getHeardRumours());
        LostTalesQuestPlayerData.validateCharacterState(loaded.writeCharacterState());
        loaded.startQuest("losttales:server/old_road", "1", 1L, 0L);
        assertTrue("taken, it is no lead any more", loaded.getHeardRumours().isEmpty());
        for (int index = 0; index <= LostTalesQuestPlayerData.MAX_RUMOURS; index++) {
            loaded.hearRumour("losttales:server/q" + index, "");
        }
        assertEquals(LostTalesQuestPlayerData.MAX_RUMOURS, loaded.getHeardRumours().size());
        assertFalse("the oldest goes", loaded.getHeardRumours().contains("losttales:server/q0"));
    }

    @Test
    public void rumoursTravelToTheJournalAndTheTellersWordsToo() {
        LostTalesQuestSyncPacket sent = new LostTalesQuestSyncPacket(
                Collections.<LostTalesQuestProgress>emptyList(),
                Collections.<LostTalesQuestHistoryEntry>emptyList(),
                Collections.<String>emptySet(), Collections.<String>emptySet(), "",
                null, null).withHeardRumours(Arrays.asList("losttales:server/old_road"));
        ByteBuf buffer = Unpooled.buffer();
        sent.toBytes(buffer);
        LostTalesQuestSyncPacket read = new LostTalesQuestSyncPacket();
        read.fromBytes(buffer);
        assertFalse(read.isMalformed());
        assertEquals(Collections.singleton("losttales:server/old_road"), read.getHeardRumours());

        ByteBuf told = Unpooled.buffer();
        new LostTalesQuestOfferPacket(7, "losttales:server/old_road", true).toBytes(told);
        LostTalesQuestOfferPacket rumour = new LostTalesQuestOfferPacket();
        rumour.fromBytes(told);
        assertFalse(rumour.isMalformed());
        assertTrue(rumour.isRumour());
    }

    @Test
    public void aRumourKeepsThePlaceItPointsToAndDiscoversNothing() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        assertTrue(data.hearRumour("losttales:server/old_road", " losttales:bree_well "));
        assertEquals("losttales:bree_well",
                data.getRumourMarkers().get("losttales:server/old_road"));
        assertTrue("the place is not found by hearing of it",
                data.getDiscoveredMarkerIds().isEmpty());
        assertFalse(data.isMarkerDiscovered("losttales:bree_well"));
        assertTrue(data.hearRumour("losttales:server/no_place", ""));
        assertEquals("", data.getRumourMarkers().get("losttales:server/no_place"));

        NBTTagCompound wrapper = new NBTTagCompound();
        data.saveNBTData(wrapper);
        LostTalesQuestPlayerData loaded = new LostTalesQuestPlayerData();
        loaded.loadNBTData(wrapper);
        assertFalse(loaded.isReadOnlyForNewerVersion());
        assertEquals(data.getRumourMarkers(), loaded.getRumourMarkers());
        assertTrue(loaded.getDiscoveredMarkerIds().isEmpty());
        LostTalesQuestPlayerData.validateCharacterState(loaded.writeCharacterState());

        StringBuilder farMarker = new StringBuilder("losttales:");
        while (farMarker.length() <= 300) {
            farMarker.append('a');
        }
        assertTrue(loaded.hearRumour("losttales:server/far", farMarker.toString()));
        assertEquals("a marker past the bound points nowhere", "",
                loaded.getRumourMarkers().get("losttales:server/far"));
        loaded.startQuest("losttales:server/old_road", "1", 1L, 0L);
        assertFalse("taken, its place goes with it",
                loaded.getRumourMarkers().containsKey("losttales:server/old_road"));
    }

    @Test
    public void tellingARumourRevealsNoMarker() throws IOException {
        String gate = new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/ninuna/losttales/quest/QuestStartGate.java")),
                StandardCharsets.UTF_8);
        assertFalse("a rumour discovers nothing (QUEST-20)",
                gate.contains("revealMapMarker") || gate.contains("discoverMarker"));
        assertTrue(gate.contains("data.hearRumour(id,"));
    }

    private static LostTalesQuestSyncPacket withRumours(String... questIds) {
        return new LostTalesQuestSyncPacket(
                Collections.<LostTalesQuestProgress>emptyList(),
                Collections.<LostTalesQuestHistoryEntry>emptyList(),
                Collections.<String>emptySet(), Collections.<String>emptySet(), "",
                null, null).withHeardRumours(Arrays.asList(questIds));
    }

    private static QuestRumourPlace well(double x) {
        return new QuestRumourPlace("losttales:server/old_road",
                "losttales:bree_well", "", "", 100, x, -340.0D);
    }

    @Test
    public void thePlacesTravelWithTheirRumoursOnly() {
        LostTalesQuestSyncPacket sent = withRumours("losttales:server/old_road")
                .withRumourPlaces(Arrays.asList(well(120.0D),
                        new QuestRumourPlace("losttales:server/unheard",
                                "losttales:bree_well", "", "", 100, 1.0D, 1.0D),
                        new QuestRumourPlace("losttales:server/old_road",
                                "losttales:bree_well", "", "", 100, Double.NaN, 1.0D)));
        assertEquals("only a rumour the log holds, once, at a real place",
                Collections.singletonList(well(120.0D)), sent.getRumourPlaces());
        ByteBuf buffer = Unpooled.buffer();
        sent.toBytes(buffer);
        LostTalesQuestSyncPacket read = new LostTalesQuestSyncPacket();
        read.fromBytes(buffer);
        assertFalse(read.isMalformed());
        assertEquals(Collections.singletonList(well(120.0D)), read.getRumourPlaces());
        assertEquals(sent.getRumourPlaces(),
                sent.withoutObjectiveCounts().getRumourPlaces());

        ByteBuf cut = Unpooled.buffer();
        sent.toBytes(cut);
        cut.writerIndex(cut.writerIndex() - 9);
        LostTalesQuestSyncPacket broken = new LostTalesQuestSyncPacket();
        broken.fromBytes(cut);
        assertTrue(broken.isMalformed());
        assertTrue("a log that fails to read keeps no place",
                broken.getRumourPlaces().isEmpty());

        assertTrue("a rumour taken leaves its place behind",
                sent.withHeardRumours(Collections.<String>emptyList())
                        .getRumourPlaces().isEmpty());
    }

    @Test
    public void aPlaceMovedReachesTheClientWhole() {
        QuestLogSync.Sent before = QuestLogSync.step(null,
                withRumours("losttales:server/old_road")
                        .withRumourPlaces(Collections.singletonList(well(120.0D))),
                false).sent;
        QuestLogSync.Step same = QuestLogSync.step(before,
                withRumours("losttales:server/old_road")
                        .withRumourPlaces(Collections.singletonList(well(120.0D))),
                false);
        assertNull(same.whole);
        QuestLogSync.Step moved = QuestLogSync.step(before,
                withRumours("losttales:server/old_road")
                        .withRumourPlaces(Collections.singletonList(well(180.0D))),
                false);
        assertNotNull(moved.whole);
    }

    private static LostTalesQuestDefinition parsed(String json) {
        return LostTalesQuestDefinitionJsonParser.parseQuest(new StringReader(json),
                "quests/test.json", new ArrayList<String>());
    }

    /**
     * A rumour that names no marker points to the first place its quest
     * names (QUEST-20): its markers but its giver's, then its objectives'
     * markers stage by stage, never a marker its giver's names.
     */
    @Test
    public void aRumourWithNoMarkerTakesTheFirstPlaceItsQuestNames() {
        LostTalesQuestDefinition quest = parsed("{\"id\":\"losttales:server/old_road\","
                + "\"startMode\":\"interaction\",\"interaction\":{\"entity\":\"losttales.Nia\"},"
                + "\"markers\":{\"giver\":\"losttales:nia_hut\",\"waymark\":\"losttales:waymark, losttales:ford\"},"
                + "\"stages\":[{\"id\":\"1\",\"objectives\":[{\"id\":\"a\",\"type\":\"goto\","
                + "\"params\":{\"marker\":\"losttales:nia_hut\"}},{\"id\":\"b\",\"type\":\"goto\","
                + "\"params\":{\"marker\":\"losttales:old_bridge\"}}]}]}");
        assertEquals(Arrays.asList("losttales:waymark", "losttales:ford",
                "losttales:old_bridge"), QuestRumourPlaces.questMarkers(quest));
        assertNull("a creature's quest with no place written points to no block",
                QuestRumourPlaces.blockPlace(quest, 0));

        LostTalesQuestDefinition objectivesOnly = parsed("{\"id\":\"losttales:server/ford\","
                + "\"startMode\":\"interaction\",\"interaction\":{\"entity\":\"losttales.Nia\"},"
                + "\"stages\":[{\"id\":\"1\",\"objectives\":[{\"id\":\"a\",\"type\":\"goto\","
                + "\"params\":{\"marker\":\"losttales:ford\"}}]}]}");
        assertEquals(Collections.singletonList("losttales:ford"),
                QuestRumourPlaces.questMarkers(objectivesOnly));
        assertTrue(QuestRumourPlaces.questMarkers(null).isEmpty());
    }

    /** With no marker at all, a quest a block gives points to where its block stands. */
    @Test
    public void aBlocksQuestPointsToItsBlock() {
        LostTalesQuestDefinition quest = parsed("{\"id\":\"losttales:server/cauldron\","
                + "\"startMode\":\"interaction\",\"interaction\":{\"block\":\"minecraft:cauldron\","
                + "\"x\":\"120\",\"y\":\"70\",\"z\":\"-340\",\"dimension\":\"100\"},"
                + "\"stages\":[{\"id\":\"1\",\"objectives\":[{\"id\":\"a\",\"type\":\"gather\","
                + "\"params\":{\"item\":\"minecraft:log\"}}]}]}");
        assertTrue(QuestRumourPlaces.questMarkers(quest).isEmpty());
        QuestRumourPlace place = QuestRumourPlaces.blockPlace(quest, 0);
        assertNotNull(place);
        assertFalse("a bare place bears no marker", place.isMarker());
        assertTrue(place.isValid());
        assertEquals(100, place.getDimensionId());
        assertEquals(120.5D, place.getX(), 0.0D);
        assertEquals(-339.5D, place.getZ(), 0.0D);
        assertEquals("a bare place has no name", "", place.shownName());

        QuestRumourPlace here = QuestRumourPlaces.blockPlace(parsed(
                "{\"id\":\"losttales:server/cauldron\",\"startMode\":\"interaction\","
                + "\"interaction\":{\"block\":\"minecraft:cauldron\",\"x\":\"3\",\"z\":\"4\"},"
                + "\"stages\":[{\"id\":\"1\",\"objectives\":[{\"id\":\"a\",\"type\":\"gather\","
                + "\"params\":{\"item\":\"minecraft:log\"}}]}]}"), 7);
        assertEquals("bare coordinates stand where the player is", 7, here.getDimensionId());
        assertNull("a block with no place written points nowhere",
                QuestRumourPlaces.blockPlace(parsed("{\"id\":\"losttales:server/cauldron\","
                        + "\"startMode\":\"interaction\","
                        + "\"interaction\":{\"block\":\"minecraft:cauldron\"},"
                        + "\"stages\":[{\"id\":\"1\",\"objectives\":[{\"id\":\"a\","
                        + "\"type\":\"gather\",\"params\":{\"item\":\"minecraft:log\"}}]}]}"), 0));
    }

    /** A bare place travels to the journal as a marker's does; a name without a marker does not. */
    @Test
    public void aBarePlaceTravelsWithItsRumour() {
        QuestRumourPlace bare = new QuestRumourPlace("losttales:server/old_road", "", "", "",
                100, 120.5D, -339.5D);
        assertFalse("a name needs a marker to bear it", new QuestRumourPlace(
                "losttales:server/old_road", "", "The Well", "", 100, 1.0D, 1.0D).isValid());
        LostTalesQuestSyncPacket sent = withRumours("losttales:server/old_road")
                .withRumourPlaces(Collections.singletonList(bare));
        ByteBuf buffer = Unpooled.buffer();
        sent.toBytes(buffer);
        LostTalesQuestSyncPacket read = new LostTalesQuestSyncPacket();
        read.fromBytes(buffer);
        assertFalse(read.isMalformed());
        assertEquals(Collections.singletonList(bare), read.getRumourPlaces());
    }
}
