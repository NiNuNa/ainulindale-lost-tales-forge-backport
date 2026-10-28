package com.ninuna.losttales.character.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.deletion.CharacterDeletionService;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.sync.CharacterCreationCatalog;
import com.ninuna.losttales.character.sync.CharacterOperationType;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.character.lore.sync.LoreCharacterSyncManager;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.character.CharacterCreationCatalogSyncPacket;
import com.ninuna.losttales.network.packet.character.CharacterOperationResultPacket;
import com.ninuna.losttales.network.packet.character.CharacterRosterSyncPacket;
import com.ninuna.losttales.network.PacketDigest;
import cpw.mods.fml.common.FMLLog;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Sends a player their own roster and the answers to their character
 * requests. Every roster answer brings the creation catalogue and the lore
 * list along, each only where it changed since that client was last sent
 * it: what was sent is kept by account, as a digest, until its player
 * leaves. Static state, cleared with the server's other stores.
 */
public final class CharacterSyncManager {

    public static final int UNSOLICITED_REQUEST_ID = 0;

    private static final Map<UUID, byte[]> CATALOG_SENT = new HashMap<UUID, byte[]>();

    private CharacterSyncManager() {}

    public static void sendResultAndRoster(EntityPlayerMP player,
                                           int requestId,
                                           CharacterOperationType operationType,
                                           CharacterOperationResult result) {
        if (player == null || result == null || operationType == null) {
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new CharacterOperationResultPacket(requestId, operationType, result), player);
        if (result.getRoster() != null) {
            sendRoster(player, requestId, result.getRoster());
        }
    }

    public static void sendFailure(EntityPlayerMP player,
                                   int requestId,
                                   CharacterOperationType operationType,
                                   CharacterErrorId errorId) {
        if (player == null || operationType == null) {
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new CharacterOperationResultPacket(
                        requestId, operationType, errorId),
                player
        );
    }

    public static boolean sendRoster(EntityPlayerMP player,
                                     int requestId,
                                     CharacterRoster roster) {
        if (player == null || roster == null || player.getUniqueID() == null) {
            return false;
        }
        if (!player.getUniqueID().equals(roster.getOwnerId())) {
            FMLLog.warning("[%s] Refused to send roster owned by %s to player %s",
                    LostTalesMetaData.MOD_ID, roster.getOwnerId(), player.getUniqueID());
            return false;
        }
        // The deleted characters the owner may still restore go with it,
        // and whether the player is held for their last switch (CHAR-45).
        CharacterRosterSnapshot snapshot = CharacterRosterSnapshot.fromRoster(
                roster, CharacterDeletionService.getInstance().deletedOf(
                        player.worldObj, roster.getOwnerId()))
                .withJournalHeld(CharacterJoin.isHeldForJournal(player));
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new CharacterRosterSyncPacket(requestId, snapshot), player);
        sendCreationCatalog(player);
        LoreCharacterSyncManager.send(player);
        return true;
    }

    /**
     * Tells a player the server cannot read their characters right now: an
     * empty roster that says so, which keeps them in the creator as a ghost
     * (CHAR-01).
     */
    public static void sendUnreadable(EntityPlayerMP player, int requestId) {
        if (player == null || player.getUniqueID() == null) {
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(new CharacterRosterSyncPacket(requestId,
                CharacterRosterSnapshot.unreadable(player.getUniqueID())), player);
    }

    /** Sends the player the creation catalog, unless their client holds it as it is. */
    public static void sendCreationCatalog(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote
                || player.getUniqueID() == null) {
            return;
        }
        CharacterCreationCatalogSyncPacket catalog = new CharacterCreationCatalogSyncPacket(
                CharacterCreationCatalog.fromServer());
        byte[] digest = PacketDigest.of(catalog);
        synchronized (CATALOG_SENT) {
            if (Arrays.equals(digest, CATALOG_SENT.get(player.getUniqueID()))) {
                return;
            }
            CATALOG_SENT.put(player.getUniqueID(), digest);
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(catalog, player);
    }

    /** The player left: the next client of theirs is sent the catalog and the lore list whole. */
    public static void forget(UUID account) {
        synchronized (CATALOG_SENT) {
            CATALOG_SENT.remove(account);
        }
        LoreCharacterSyncManager.forget(account);
    }

    public static void clear() {
        synchronized (CATALOG_SENT) {
            CATALOG_SENT.clear();
        }
        LoreCharacterSyncManager.clear();
    }
}
