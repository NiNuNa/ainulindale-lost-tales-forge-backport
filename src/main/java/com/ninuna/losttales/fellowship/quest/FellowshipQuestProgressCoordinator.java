package com.ninuna.losttales.fellowship.quest;

import com.ninuna.losttales.character.identity.RoleplayCharacterIdentityHook;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.server.FellowshipService;
import com.ninuna.losttales.quest.LostTalesQuestManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

/**
 * Shares kills and arrivals inside one company (FEL-28): one counts for a
 * member only when its maker and the member both travel with that
 * fellowship ({@link #sharingCredit}). Every member is checked again
 * against the live server, and the quest manager checks whether that
 * player has a matching objective; completion and rewards stay its own.
 */
public final class FellowshipQuestProgressCoordinator {

    /** The fellowship each character travels with, by id; null for none. */
    interface TravelledWith {
        UUID of(UUID identityId);
    }

    private static final FellowshipQuestProgressCoordinator INSTANCE =
            new FellowshipQuestProgressCoordinator();
    private static final long DEDUPLICATION_WINDOW_TICKS = 5L;

    private final Map<KillEventKey, Long> recentKillEvents =
            new HashMap<KillEventKey, Long>();

    private FellowshipQuestProgressCoordinator() {}

    public static FellowshipQuestProgressCoordinator getInstance() {
        return INSTANCE;
    }

    /** Forgets the recent kills, as the server starts and stops. */
    public synchronized void clear() {
        this.recentKillEvents.clear();
    }

    public synchronized void handleAuthoritativeKill(EntityPlayerMP creditedPlayer,
                                                       Entity victim) {
        if (creditedPlayer == null || victim == null
                || creditedPlayer.worldObj == null
                || creditedPlayer.worldObj.isRemote
                || victim.worldObj != creditedPlayer.worldObj) {
            return;
        }

        long worldTime = creditedPlayer.worldObj.getTotalWorldTime();
        pruneDeduplicationCache(worldTime);
        KillEventKey eventKey = new KillEventKey(
                creditedPlayer.dimension, victim.getEntityId(), worldTime);
        if (this.recentKillEvents.containsKey(eventKey)) {
            return;
        }
        this.recentKillEvents.put(eventKey, Long.valueOf(worldTime));

        LostTalesQuestManager.handleEntityKilled(creditedPlayer, victim, false);
        if (!LostTalesConfig.enableSharedQuestProgress) {
            return;
        }

        UUID creditedId = RoleplayCharacterIdentityHook.resolveGameplayId(
                creditedPlayer);
        Fellowship fellowship = FellowshipService.getInstance()
                .getTravellingFellowshipForActiveIdentity(creditedPlayer);
        List<FellowshipMember> sharing = sharingCredit(fellowship, creditedId,
                travelledWith(creditedPlayer.worldObj));
        if (sharing.isEmpty()) {
            return;
        }

        double radius = Math.max(1, LostTalesConfig.fellowshipSharedQuestRadius);
        double radiusSq = radius * radius;
        List<?> onlinePlayers = creditedPlayer.mcServer == null
                || creditedPlayer.mcServer.getConfigurationManager() == null
                ? null
                : creditedPlayer.mcServer.getConfigurationManager().playerEntityList;
        if (onlinePlayers == null) {
            return;
        }

        for (FellowshipMember member : sharing) {
            EntityPlayerMP participant = findOnlinePlayer(
                    onlinePlayers, member.getOwnerId());
            if (!isEligibleParticipant(
                    creditedPlayer, participant, member, radiusSq)) {
                continue;
            }
            LostTalesQuestManager.handleEntityKilled(participant, victim, true);
        }
    }

    public synchronized void handleAuthoritativeTravel(
            EntityPlayerMP source) {
        if (source == null || source.worldObj == null
                || source.worldObj.isRemote || !source.isEntityAlive()) {
            return;
        }
        boolean sourceChanged = LostTalesQuestManager.handleTravelProgress(
                source, source, false);
        if (!sourceChanged
                || !LostTalesConfig.enableSharedQuestProgress) {
            return;
        }
        UUID sourceId = RoleplayCharacterIdentityHook.resolveGameplayId(
                source);
        Fellowship fellowship = FellowshipService.getInstance()
                .getTravellingFellowshipForActiveIdentity(source);
        List<FellowshipMember> sharing = sharingCredit(fellowship, sourceId,
                travelledWith(source.worldObj));
        if (sharing.isEmpty()) {
            return;
        }
        double radius = Math.max(1, LostTalesConfig.fellowshipSharedQuestRadius);
        double radiusSq = radius * radius;
        List<?> onlinePlayers = source.mcServer == null
                || source.mcServer.getConfigurationManager() == null
                ? null
                : source.mcServer.getConfigurationManager().playerEntityList;
        if (onlinePlayers == null) {
            return;
        }
        for (FellowshipMember member : sharing) {
            EntityPlayerMP participant = findOnlinePlayer(
                    onlinePlayers, member.getOwnerId());
            if (isEligibleParticipant(source, participant, member,
                    radiusSq)) {
                LostTalesQuestManager.handleTravelProgress(participant, source, true);
            }
        }
    }

    /**
     * The members a kill or an arrival of {@code makerId} counts for besides
     * the maker (FEL-28): those of {@code fellowship} who travel with it,
     * while the maker travels with it too. None while the maker travels
     * with none, or with another.
     */
    static List<FellowshipMember> sharingCredit(Fellowship fellowship, UUID makerId,
                                                TravelledWith travelledWith) {
        List<FellowshipMember> sharing = new ArrayList<FellowshipMember>();
        if (fellowship == null || makerId == null || travelledWith == null
                || !fellowship.containsMember(makerId)) {
            return sharing;
        }
        UUID fellowshipId = fellowship.getFellowshipId();
        if (!fellowshipId.equals(travelledWith.of(makerId))) {
            return sharing;
        }
        for (FellowshipMember member : fellowship.getMembers()) {
            if (member != null && !makerId.equals(member.getIdentityId())
                    && fellowshipId.equals(travelledWith.of(member.getIdentityId()))) {
                sharing.add(member);
            }
        }
        return sharing;
    }

    /** The fellowship each character travels with, as the server's store says now. */
    private static TravelledWith travelledWith(final World world) {
        return new TravelledWith() {
            @Override
            public UUID of(UUID identityId) {
                return FellowshipService.getInstance()
                        .getTravellingFellowshipId(world, identityId);
            }
        };
    }

    private boolean isEligibleParticipant(EntityPlayerMP source,
                                          EntityPlayerMP participant,
                                          FellowshipMember member,
                                          double radiusSq) {
        if (source == null || participant == null || member == null
                || participant.worldObj == null
                || participant.worldObj.isRemote
                || participant.dimension != source.dimension
                || !participant.isEntityAlive()
                || participant.getHealth() <= 0.0F) {
            return false;
        }
        if (!member.getIdentityId().equals(
                RoleplayCharacterIdentityHook.resolveGameplayId(participant))
                || !member.getOwnerId().equals(participant.getUniqueID())) {
            return false;
        }
        return participant.getDistanceSqToEntity(source) <= radiusSq;
    }

    private EntityPlayerMP findOnlinePlayer(List<?> onlinePlayers, UUID ownerId) {
        if (ownerId == null) {
            return null;
        }
        for (Object value : onlinePlayers) {
            if (value instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) value;
                if (ownerId.equals(player.getUniqueID())) {
                    return player;
                }
            }
        }
        return null;
    }

    private void pruneDeduplicationCache(long worldTime) {
        ArrayList<KillEventKey> expired = new ArrayList<KillEventKey>();
        for (Map.Entry<KillEventKey, Long> entry
                : this.recentKillEvents.entrySet()) {
            long recorded = entry.getValue() == null
                    ? Long.MIN_VALUE : entry.getValue().longValue();
            if (worldTime < recorded
                    || worldTime - recorded > DEDUPLICATION_WINDOW_TICKS) {
                expired.add(entry.getKey());
            }
        }
        for (KillEventKey key : expired) {
            this.recentKillEvents.remove(key);
        }
    }

    private static final class KillEventKey {
        private final int dimension;
        private final int entityId;
        private final long worldTime;

        private KillEventKey(int dimension, int entityId, long worldTime) {
            this.dimension = dimension;
            this.entityId = entityId;
            this.worldTime = worldTime;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof KillEventKey)) {
                return false;
            }
            KillEventKey key = (KillEventKey) other;
            return this.dimension == key.dimension
                    && this.entityId == key.entityId
                    && this.worldTime == key.worldTime;
        }

        @Override
        public int hashCode() {
            int result = this.dimension;
            result = 31 * result + this.entityId;
            result = 31 * result
                    + (int) (this.worldTime ^ (this.worldTime >>> 32));
            return result;
        }
    }
}
