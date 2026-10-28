package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.sync.CharacterOperationType;
import com.ninuna.losttales.character.switching.CharacterLifecycleStateTracker;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * The safety boundary for the character requests clients send: every
 * request that carries a request id and waits on a
 * {@link CharacterOperationType} answer. A profile read and a gear read
 * ({@code CharacterProfileRequestPacket}, {@code CharacterGearRequestPacket})
 * carry no request id and go through
 * {@link LostTalesServerPacketDispatcher}, which drops a refused one.
 *
 * <p>The gate itself — the per-player request window, the throttled
 * diagnostics — is every family's and is
 * {@link LostTalesRequestRateLimiter}'s. What is this family's own is
 * that a refused request is <em>answered</em>: a character screen is
 * waiting on a reply and would otherwise sit on a spinner, so each
 * refusal carries a {@link CharacterErrorId}. The reply is itself
 * throttled, or a flood of requests would be a flood of answers.</p>
 *
 * <p>Also this family's own is the lifecycle epoch: a request is stamped
 * when it arrives and refused if the player has switched character, died
 * or changed dimension before the server thread reaches it.</p>
 */
public final class CharacterServerPacketDispatcher {

    /** How often a refused request may be answered, per player. */
    private static final long RATE_LIMIT_REPLY_INTERVAL_MILLIS = 1000L;
    /** The budget every character request draws on. */
    private static final LostTalesRequestRateLimiter.RequestType REQUESTS =
            LostTalesRequestRateLimiter.RequestType.CHARACTER_REQUEST;
    /** The budget a lore claim or release draws on besides. */
    private static final LostTalesRequestRateLimiter.RequestType LORE_TRANSFERS =
            LostTalesRequestRateLimiter.RequestType.LORE_TRANSFER;

    private CharacterServerPacketDispatcher() {}

    private static boolean isLoreTransfer(CharacterOperationType operationType) {
        return operationType == CharacterOperationType.LORE_CLAIM
                || operationType == CharacterOperationType.LORE_RELEASE;
    }

    /**
     * A roster read goes through in a session that is not ready: a player
     * held while the server cannot read characters, whose game asks every
     * 30 seconds so the store is tried again (CHAR-01), and one held while
     * their last switch waits for an administrator, whose Characters page
     * still shows their roster (CHAR-45). Nothing is changed by it.
     */
    private static boolean readsInUnreadySession(EntityPlayerMP player,
                                                 long epoch,
                                                 CharacterOperationType operationType) {
        return operationType == CharacterOperationType.REQUEST_ROSTER
                && CharacterLifecycleStateTracker.isRequestInSession(player, epoch);
    }

    /** What a request refused for its session answers: a held journal says so (CHAR-45). */
    private static CharacterErrorId sessionRefusal(EntityPlayerMP player,
                                                   CharacterOperationType operationType) {
        if (CharacterJoin.isHeldForJournal(player)) {
            return CharacterErrorId.SWITCH_JOURNAL_HELD;
        }
        return operationType == CharacterOperationType.SELECT
                ? CharacterErrorId.SWITCH_SESSION_CHANGED
                : CharacterErrorId.INVALID_PLAYER;
    }

    public static EntityPlayerMP getPlayer(MessageContext context) {
        return LostTalesServerPacketDispatcher.getPlayer(context);
    }

    public static void submit(EntityPlayerMP player,
                              int requestId,
                              CharacterOperationType operationType,
                              boolean malformed,
                              String packetName,
                              LostTalesServerTaskQueue.PlayerTask task) {
        if (player == null || operationType == null || task == null) {
            return;
        }
        LostTalesRequestRateLimiter.RequestType spent =
                !LostTalesRequestRateLimiter.allow(player, REQUESTS) ? REQUESTS
                : isLoreTransfer(operationType)
                        && !LostTalesRequestRateLimiter.allow(player, LORE_TRANSFERS)
                        ? LORE_TRANSFERS : null;
        if (spent != null) {
            LostTalesRequestRateLimiter.logRateLimited(player, spent, packetName);
            if (LostTalesRequestRateLimiter.allowReply(player, spent,
                    RATE_LIMIT_REPLY_INTERVAL_MILLIS)) {
                CharacterSyncManager.sendFailure(
                        player, requestId, operationType,
                        CharacterErrorId.RATE_LIMITED);
            }
            return;
        }
        if (malformed) {
            LostTalesRequestRateLimiter.logMalformed(player, packetName);
            CharacterSyncManager.sendFailure(
                    player, requestId, operationType,
                    CharacterErrorId.MALFORMED_REQUEST);
            return;
        }
        final long requestEpoch = CharacterLifecycleStateTracker.captureRequestEpoch(player);
        if (requestEpoch <= 0L) {
            CharacterSyncManager.sendFailure(
                    player, requestId, operationType,
                    CharacterErrorId.INVALID_PLAYER);
            return;
        }
        final LostTalesServerTaskQueue.PlayerTask guardedTask = task;
        final int guardedRequestId = requestId;
        final CharacterOperationType guardedOperationType = operationType;
        if (!LostTalesServerTaskQueue.enqueue(player.getUniqueID(), packetName,
                new LostTalesServerTaskQueue.PlayerTask() {
                    @Override
                    public void run(EntityPlayerMP livePlayer) {
                        if (!CharacterLifecycleStateTracker.isRequestEpochCurrent(
                                livePlayer, requestEpoch)
                                && !readsInUnreadySession(livePlayer,
                                        requestEpoch, guardedOperationType)) {
                            CharacterSyncManager.sendFailure(
                                    livePlayer,
                                    guardedRequestId,
                                    guardedOperationType,
                                    sessionRefusal(livePlayer, guardedOperationType));
                            return;
                        }
                        guardedTask.run(livePlayer);
                    }
                })) {
            LostTalesRequestRateLimiter.logQueueFull(player, packetName);
            CharacterSyncManager.sendFailure(
                    player, requestId, operationType,
                    CharacterErrorId.INTERNAL_ERROR);
        }
    }
}
