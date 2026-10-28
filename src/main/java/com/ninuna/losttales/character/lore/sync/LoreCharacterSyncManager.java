package com.ninuna.losttales.character.lore.sync;

import com.ninuna.losttales.character.identity.RoleplayCharacterIdentityHook;
import com.ninuna.losttales.character.lore.LoreCharacterDefinition;
import com.ninuna.losttales.character.lore.LoreCharacterRegistry;
import com.ninuna.losttales.character.lore.ownership.LoreCharacterOwnershipRecord;
import com.ninuna.losttales.character.lore.ownership.LoreCharacterOwnershipStorage;
import com.ninuna.losttales.character.lore.ownership.LoreCharacterOwnershipWorldData;
import com.ninuna.losttales.character.lore.transfer.LoreCharacterTransferStorage;
import com.ninuna.losttales.character.lore.transfer.LoreCharacterTransferWorldData;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.PacketDigest;
import com.ninuna.losttales.network.packet.character.LoreCharacterSyncPacket;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds each viewer's own lore list and sends it where it changed since
 * that client was last sent it: what was sent is kept by account, as a
 * digest, until its player leaves. Static state, cleared with the
 * server's other stores.
 */
public final class LoreCharacterSyncManager {

    private static final Map<UUID, byte[]> SENT = new HashMap<UUID, byte[]>();

    private LoreCharacterSyncManager() {}

    /**
     * Sends every online player their lore list after a claim or a release:
     * every list shows who holds each figure.
     */
    public static void broadcast(EntityPlayerMP actor) {
        if (actor == null) return;
        MinecraftServer server = actor.mcServer;
        if (server == null || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            send(actor);
            return;
        }
        for (Object value
                : server.getConfigurationManager().playerEntityList) {
            if (value instanceof EntityPlayerMP) {
                send((EntityPlayerMP)value);
            }
        }
    }

    /** Sends the player their lore list, unless their client holds it as it is. */
    public static void send(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote
                || player.getUniqueID() == null) return;
        LoreCharacterOwnershipWorldData ownership =
                LoreCharacterOwnershipStorage.get(player.worldObj);
        LoreCharacterTransferWorldData transfers =
                LoreCharacterTransferStorage.get(player.worldObj);
        List<LoreCharacterSummary> summaries =
                new ArrayList<LoreCharacterSummary>();
        for (LoreCharacterDefinition definition : LoreCharacterRegistry.getAll()) {
            LoreCharacterOwnershipRecord record = ownership.getRecord(
                    definition.getId());
            boolean claimed = record != null && record.isClaimed();
            boolean owned = claimed && player.getUniqueID().equals(
                    record.getOwnerId());
            String ownerName = claimed
                    ? RoleplayCharacterIdentityHook.resolveGameplayName(
                    record.getOwnerId()) : "";
            LoreCharacterDefinition.Appearance appearance =
                    definition.getAppearance();
            summaries.add(new LoreCharacterSummary(
                    definition.getId(), definition.getName(),
                    definition.getDescription(),
                    appearance == null ? "" : appearance.getRaceId(),
                    appearance != null,
                    claimed, owned,
                    transfers.getTransaction(definition.getId()) != null,
                    ownerName,
                    owned && record != null ? record.getCharacterId() : null,
                    record == null ? 0L : record.getRevision()));
        }
        LoreCharacterSyncPacket list = new LoreCharacterSyncPacket(
                new LoreCharacterSnapshot(summaries, ownership.isReadOnly(),
                        transfers.isReadOnly()));
        byte[] digest = PacketDigest.of(list);
        synchronized (SENT) {
            if (Arrays.equals(digest, SENT.get(player.getUniqueID()))) {
                return;
            }
            SENT.put(player.getUniqueID(), digest);
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(list, player);
    }

    public static void forget(UUID account) {
        synchronized (SENT) {
            SENT.remove(account);
        }
    }

    public static void clear() {
        synchronized (SENT) {
            SENT.clear();
        }
    }
}
