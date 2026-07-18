package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LostTalesQuestSyncPacketTest {

    @Test
    public void completeSnapshotRoundTrips() {
        LostTalesQuestProgress progress = new LostTalesQuestProgress(
                "losttales:test_quest", 1, "return",
                Collections.singletonMap("collect", 3), 100L, 200L);
        LostTalesMapMarkerDefinition marker =
                new LostTalesMapMarkerDefinition(
                        "runtime:camp", "Camp", "camp", "white",
                        "Camp", false, 0, 12.0D, 64.0D, -7.0D,
                        128.0D, 8.0D, true, true);
        LostTalesQuestObjectiveDefinition objective =
                new LostTalesQuestObjectiveDefinition(
                        "collect", "item", "Collect supplies.", false,
                        Collections.singletonMap("count", "3"));
        LostTalesQuestDefinition quest = new LostTalesQuestDefinition(
                "runtime:test", "Test", "A runtime quest.", false,
                LostTalesQuestDefinition.START_MODE_INTERACTION,
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(),
                Collections.singletonList(
                        new LostTalesQuestStageDefinition(
                                "start", Collections.singletonList(objective))));

        LostTalesQuestSyncPacket original = new LostTalesQuestSyncPacket(
                Collections.singletonList(progress),
                Collections.singleton("losttales:completed"),
                Collections.singleton("losttales:failed"),
                Collections.singleton("losttales:test_quest"),
                Collections.singleton("runtime:camp"),
                "runtime:camp", Collections.singletonList(marker),
                Collections.singletonList(quest));
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);

        LostTalesQuestSyncPacket decoded =
                new LostTalesQuestSyncPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        assertEquals(1, decoded.getActiveQuests().size());
        assertEquals(3, decoded.getActiveQuests().get(0)
                .getObjectiveProgress("collect"));
        assertEquals(Collections.singleton("losttales:completed"),
                decoded.getCompletedQuestIds());
        assertEquals(Collections.singleton("losttales:failed"),
                decoded.getFailedQuestIds());
        assertEquals(Collections.singleton("losttales:test_quest"),
                decoded.getPinnedQuestIds());
        assertEquals("runtime:camp", decoded.getPinnedMapMarkerId());
        assertEquals(1, decoded.getDynamicMapMarkers().size());
        assertEquals(1, decoded.getDynamicQuestDefinitions().size());
    }

    @Test
    public void excessiveActiveQuestCountIsRejected() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(LostTalesQuestSyncPacket.MAX_ACTIVE_QUESTS + 1);

        LostTalesQuestSyncPacket decoded =
                new LostTalesQuestSyncPacket();
        decoded.fromBytes(buffer);

        assertTrue(decoded.isMalformed());
        assertTrue(decoded.getActiveQuests().isEmpty());
    }

    @Test
    public void trailingPayloadIsRejected() {
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesQuestSyncPacket().toBytes(buffer);
        buffer.writeByte(1);

        LostTalesQuestSyncPacket decoded =
                new LostTalesQuestSyncPacket();
        decoded.fromBytes(buffer);

        assertTrue(decoded.isMalformed());
        assertTrue(decoded.getCompletedQuestIds().isEmpty());
    }
}
