package com.ninuna.losttales.character.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.sync.CharacterOperationType;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.party.server.PartySyncManager;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

/** Executes queued character requests on the logical server thread. */
public final class CharacterNetworkRequestHandler {

    private CharacterNetworkRequestHandler() {}

    public static void handleRosterRequest(final EntityPlayerMP player, final int requestId) {
        execute(player, requestId, CharacterOperationType.REQUEST_ROSTER, new Operation() {
            @Override
            public CharacterOperationResult run() {
                return CharacterService.getInstance().getRoster(player);
            }
        });
    }

    public static void handleCreateRequest(final EntityPlayerMP player, final int requestId,
                                           final CharacterCreationRequest request) {
        execute(player, requestId, CharacterOperationType.CREATE, new Operation() {
            @Override
            public CharacterOperationResult run() {
                return CharacterService.getInstance().createCharacter(player, request);
            }
        });
    }

    public static void handleSelectRequest(final EntityPlayerMP player, final int requestId,
                                           final long expectedRosterRevision,
                                           final UUID characterId) {
        execute(player, requestId, CharacterOperationType.SELECT, new Operation() {
            @Override
            public CharacterOperationResult run() {
                return CharacterService.getInstance().selectCharacter(
                        player, expectedRosterRevision, characterId);
            }
        });
    }

    public static void handleDeleteRequest(final EntityPlayerMP player, final int requestId,
                                           final long expectedRosterRevision,
                                           final UUID characterId) {
        PartySyncManager.AudienceSnapshot affectedAudience =
                PartySyncManager.captureCharacterRelationsAudience(
                        player == null ? null : player.worldObj, characterId);
        execute(player, requestId, CharacterOperationType.DELETE, new Operation() {
            @Override
            public CharacterOperationResult run() {
                return CharacterService.getInstance().deleteCharacter(
                        player, expectedRosterRevision, characterId);
            }
        }, affectedAudience);
    }

    public static void handleCapeUpdateRequest(final EntityPlayerMP player,
                                               final int requestId,
                                               final long expectedRosterRevision,
                                               final UUID characterId,
                                               final boolean showMinecraftCape,
                                               final int cosmeticCapeId) {
        execute(player, requestId, CharacterOperationType.CAPE_UPDATE, new Operation() {
            @Override
            public CharacterOperationResult run() {
                return CharacterService.getInstance().updateCapeSettings(
                        player,
                        expectedRosterRevision,
                        characterId,
                        showMinecraftCape,
                        cosmeticCapeId);
            }
        });
    }

    private static void execute(EntityPlayerMP player,
                                int requestId,
                                CharacterOperationType operationType,
                                Operation operation) {
        execute(player, requestId, operationType, operation, null);
    }

    private static void execute(EntityPlayerMP player,
                                int requestId,
                                CharacterOperationType operationType,
                                Operation operation,
                                PartySyncManager.AudienceSnapshot affectedAudience) {
        try {
            CharacterOperationResult result = operation.run();
            CharacterSyncManager.sendResultAndRoster(
                    player, requestId, operationType, result);
            if (result.isSuccessful() && result.wasChanged()
                    && result.getRoster() != null
                    && operationType != CharacterOperationType.REQUEST_ROSTER) {
                // Apply the authoritative selection before broadcasting it so
                // the selecting player cannot spend up to ten ticks with stale
                // dimensions, eye height, or attributes.
                if (operationType != CharacterOperationType.CAPE_UPDATE) {
                    CharacterRaceGameplayHandler.apply(player);
                }
                CharacterAppearanceSyncManager.broadcastPlayer(
                        player, result.getRoster());
                if (operationType != CharacterOperationType.CAPE_UPDATE) {
                    PartySyncManager.sendState(
                            player, PartySyncManager.UNSOLICITED_REQUEST_ID);
                    PartySyncManager.sendStateToAudience(
                            affectedAudience, player.getUniqueID());
                }
            }
        } catch (Throwable throwable) {
            FMLLog.warning("[%s] Character %s request failed for player %s: %s",
                    LostTalesMetaData.MOD_ID,
                    operationType.getId(),
                    player == null ? "unknown" : player.getUniqueID(),
                    throwable.toString());
            CharacterSyncManager.sendFailure(
                    player, requestId, operationType,
                    CharacterErrorId.INTERNAL_ERROR, -1L);
        }
    }

    private interface Operation {
        CharacterOperationResult run();
    }
}
