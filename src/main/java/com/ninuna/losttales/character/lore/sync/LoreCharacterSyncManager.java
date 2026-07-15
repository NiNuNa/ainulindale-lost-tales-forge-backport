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
import com.ninuna.losttales.network.packet.character.LoreCharacterSyncPacket;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/** Builds a fresh viewer-specific ownership snapshot after every roster sync. */
public final class LoreCharacterSyncManager {

    private LoreCharacterSyncManager() {}

    /**
     * Refreshes the viewer-specific ownership projection for every connected
     * player after a claim or release commits. Lore ownership is server-wide,
     * so limiting this update to the actor would leave other open menus stale.
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

    public static void send(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return;
        LoreCharacterOwnershipWorldData ownership =
                LoreCharacterOwnershipStorage.get(player.worldObj);
        LoreCharacterTransferWorldData transfers =
                LoreCharacterTransferStorage.get(player.worldObj);
        List<LoreCharacterSummary> summaries =
                new ArrayList<LoreCharacterSummary>();
        boolean definitionsValid = LoreCharacterRegistry.getLoadErrors().isEmpty();
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
                    appearance == null ? "" : appearance.getGenderId(),
                    appearance == null ? "" : appearance.getModelId(),
                    appearance == null ? "" : appearance.getSkinId(),
                    definitionsValid && appearance != null,
                    claimed, owned,
                    transfers.getTransaction(definition.getId()) != null,
                    ownerName,
                    owned && record != null ? record.getCharacterId() : null,
                    record == null ? 0L : record.getRevision()));
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new LoreCharacterSyncPacket(new LoreCharacterSnapshot(
                        summaries, ownership.isReadOnly(),
                        transfers.isReadOnly())), player);
    }
}
