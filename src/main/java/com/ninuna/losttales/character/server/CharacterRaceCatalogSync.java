package com.ninuna.losttales.character.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.character.CharacterRaceCatalogPacket;
import cpw.mods.fml.common.FMLLog;
import java.util.ArrayList;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Sends a joining player the server's races and capes (CHAR-38, CHAR-39),
 * before anything that names them: the roster, the looks, the creator.
 */
public final class CharacterRaceCatalogSync {
    private CharacterRaceCatalogSync() {}

    public static void send(EntityPlayerMP player) {
        if (player == null || player.playerNetServerHandler == null) {
            return;
        }
        try {
            LostTalesNetworkHandler.CHANNEL.sendTo(new CharacterRaceCatalogPacket(
                    new ArrayList<CharacterRaceDefinition>(CharacterRaceRegistry.getAll()),
                    CharacterCapeCatalog.getDefinitions()), player);
        } catch (RuntimeException exception) {
            // The player's game keeps the bundled six and draws a server
            // race as its body's own until the next join.
            FMLLog.warning("[%s] Could not send the races and capes to %s: %s",
                    LostTalesMetaData.MOD_ID, player.getUniqueID(), exception.toString());
        }
    }
}
