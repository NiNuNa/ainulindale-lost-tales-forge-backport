package com.ninuna.losttales.quest;

import com.ninuna.losttales.network.packet.LostTalesQuestOfferPacket;
import com.ninuna.losttales.network.packet.LostTalesQuestSyncPacket;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import com.ninuna.losttales.quest.progress.LostTalesQuestHistoryEntry;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Rumours (QUEST-20): a quest file writes a lead and who tells it; a
 * character keeps the rumours it heard until it takes the quest; the
 * journal is sent them, and the teller's words are said by the player's
 * game.
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
                new StringReader(QUEST), "quests/test.json");
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
        assertTrue(data.hearRumour("losttales:server/old_road"));
        assertFalse("once", data.hearRumour("losttales:server/old_road"));
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
            loaded.hearRumour("losttales:server/q" + index);
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
}
