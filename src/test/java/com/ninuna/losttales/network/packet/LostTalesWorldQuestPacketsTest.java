package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.world.WorldQuestFixtures;
import com.ninuna.losttales.quest.world.WorldQuestRun;
import com.ninuna.losttales.quest.world.WorldQuestView;
import com.ninuna.losttales.quest.world.WorldQuestWorldData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The server's own quests and the world quests on the wire: a definition
 * keeps its world and dialogue blocks, an empty list still clears the
 * client's, each player is sent only their own part, and a payload of any
 * other shape is refused whole.
 */
public final class LostTalesWorldQuestPacketsTest {
    private final UUID aldric = UUID.fromString(
            "00000000-0000-0000-0000-00000000a1d1");

    @Test
    public void aServerQuestKeepsItsWorldAndDialogueBlocks() {
        LostTalesQuestDefinition quest = WorldQuestFixtures.greenway();
        List<LostTalesServerQuestSyncPacket> packets =
                LostTalesServerQuestSyncPacket.packetsFor(
                        Collections.singletonList(quest));
        assertEquals(1, packets.size());
        ByteBuf buffer = Unpooled.buffer();
        packets.get(0).toBytes(buffer);
        LostTalesServerQuestSyncPacket decoded =
                new LostTalesServerQuestSyncPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.isFirst());
        LostTalesQuestDefinition read = decoded.getQuests().get(0);
        assertTrue(read.isWorldQuest());
        assertEquals("7", read.getWorld().get("days"));
        assertEquals(2, read.getStages().get(0).getObjectives().size());
    }

    @Test
    public void anEmptyListStillClearsTheClients() {
        List<LostTalesServerQuestSyncPacket> packets =
                LostTalesServerQuestSyncPacket.packetsFor(
                        Collections.<LostTalesQuestDefinition>emptyList());
        assertEquals(1, packets.size());
        assertTrue(packets.get(0).isFirst());
        assertTrue(packets.get(0).getQuests().isEmpty());
    }

    @Test
    public void eachPlayerIsSentTheirOwnPart() {
        WorldQuestWorldData data = new WorldQuestWorldData();
        data.start("losttales:world/greenway", 0L, 1000L);
        data.add("losttales:world/greenway", "orcs", 4, 500, this.aldric);
        data.add("losttales:world/greenway", "orcs", 6, 500,
                UUID.randomUUID());
        LostTalesWorldQuestSyncPacket packet = LostTalesWorldQuestSyncPacket
                .of(data.runs(), this.aldric);
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesWorldQuestSyncPacket decoded =
                new LostTalesWorldQuestSyncPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        WorldQuestView view = decoded.getViews().get(0);
        assertEquals(WorldQuestRun.State.RUNNING, view.getState());
        assertEquals(10, view.getCount("orcs"));
        assertEquals(2, view.getHelpers());
        assertEquals(4, view.getMine());
        assertEquals(1000L, view.getEndsAt());
    }

    @Test
    public void aPayloadOfAnyOtherShapeIsRefused() {
        ByteBuf unknownState = Unpooled.buffer();
        unknownState.writeInt(1);
        LostTalesPacketCodec.writeUtf8String(unknownState, "x", 256);
        unknownState.writeByte(9);
        unknownState.writeLong(0L);
        unknownState.writeLong(0L);
        unknownState.writeInt(0);
        unknownState.writeInt(0);
        unknownState.writeInt(0);
        LostTalesWorldQuestSyncPacket decoded =
                new LostTalesWorldQuestSyncPacket();
        decoded.fromBytes(unknownState);
        assertTrue(decoded.isMalformed());
        assertTrue(decoded.getViews().isEmpty());

        ByteBuf trailing = Unpooled.buffer();
        LostTalesWorldQuestSyncPacket.of(
                Collections.<WorldQuestRun>emptyList(), this.aldric)
                .toBytes(trailing);
        trailing.writeByte(1);
        LostTalesWorldQuestSyncPacket extra =
                new LostTalesWorldQuestSyncPacket();
        extra.fromBytes(trailing);
        assertTrue(extra.isMalformed());
    }
}
