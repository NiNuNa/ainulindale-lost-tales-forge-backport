package com.ninuna.losttales.network.server;

import com.ninuna.losttales.DedicatedServerIsolation;
import com.ninuna.losttales.block.custom.LostTalesBlockMissiveBoard;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityMissiveBoard;
import com.ninuna.losttales.item.LostTalesItemMissiveLetter;
import com.ninuna.losttales.network.packet.LostTalesMissiveAcceptPacket;
import com.ninuna.losttales.network.packet.LostTalesMissiveBoardRequestPacket;
import com.ninuna.losttales.network.packet.LostTalesMissiveBoardStatePacket;
import com.ninuna.losttales.network.packet.LostTalesMissiveCodec;
import com.ninuna.losttales.quest.missive.MissiveAcceptance;
import com.ninuna.losttales.quest.missive.MissiveBoardService;
import com.ninuna.losttales.quest.missive.MissiveBoardStateReason;
import com.ninuna.losttales.quest.missive.MissiveBoardWatches;
import com.ninuna.losttales.quest.missive.MissiveNotice;
import com.ninuna.losttales.quest.missive.MissiveSeal;
import com.ninuna.losttales.quest.missive.MissiveSealWorldData;
import com.ninuna.losttales.quest.missive.MissiveSeals;
import org.junit.Test;

/**
 * The missive board's and letter's common classes load on a dedicated
 * server: the pages live on the client, and everything the server and
 * the packets touch names no client class.
 */
public final class MissiveDedicatedServerIsolationTest {

    @Test
    public void commonMissiveClassesContainNoClientOrLwjglReferences()
            throws Exception {
        DedicatedServerIsolation.assertServerSafe(
                LostTalesBlockMissiveBoard.class,
                LostTalesTileEntityMissiveBoard.class,
                LostTalesItemMissiveLetter.class,
                MissiveAcceptance.class,
                MissiveBoardService.class,
                MissiveBoardStateReason.class,
                MissiveBoardWatches.class,
                MissiveNotice.class,
                MissiveSeal.class,
                MissiveSeals.class,
                MissiveSealWorldData.class,
                LostTalesMissiveCodec.class,
                LostTalesMissiveAcceptPacket.class,
                LostTalesMissiveAcceptPacket.Handler.class,
                LostTalesMissiveBoardRequestPacket.class,
                LostTalesMissiveBoardRequestPacket.Handler.class,
                LostTalesMissiveBoardStatePacket.class,
                LostTalesMissiveBoardStatePacket.Handler.class);
    }
}
