package com.ninuna.losttales.party.quest;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.server.CharacterActiveResolver;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyMember;
import com.ninuna.losttales.party.server.PartyService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Server-authoritative, conservative party quest event distributor.
 *
 * Only kill events are shared in this stage. Every participant is revalidated
 * against live server state, and each backing quest adapter independently
 * checks whether that player possesses a matching active objective.
 */
public final class PartyQuestProgressCoordinator {

    private static final PartyQuestProgressCoordinator INSTANCE =
            new PartyQuestProgressCoordinator();
    private static final long DEDUPLICATION_WINDOW_TICKS = 5L;

    private final List<PartyQuestCompatibilityAdapter> adapters =
            new ArrayList<PartyQuestCompatibilityAdapter>();
    private final Map<KillEventKey, Long> recentKillEvents =
            new HashMap<KillEventKey, Long>();

    private PartyQuestProgressCoordinator() {
        this.adapters.add(new LostTalesPartyQuestCompatibilityAdapter());
        this.adapters.add(new LotrLegacyPartyQuestCompatibilityAdapter());
    }

    public static PartyQuestProgressCoordinator getInstance() {
        return INSTANCE;
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

        applyAdapters(creditedPlayer, victim);
        if (!LostTalesConfig.enablePartySharedQuestKillProgress) {
            return;
        }

        RoleplayCharacter creditedCharacter =
                CharacterActiveResolver.get(creditedPlayer);
        Party party = PartyService.getInstance()
                .getPartyForActiveCharacter(creditedPlayer);
        if (creditedCharacter == null || party == null
                || !party.containsMember(creditedCharacter.getCharacterId())) {
            return;
        }

        double radius = Math.max(1, LostTalesConfig.partySharedQuestRadius);
        double radiusSq = radius * radius;
        List<?> onlinePlayers = creditedPlayer.mcServer == null
                || creditedPlayer.mcServer.getConfigurationManager() == null
                ? null
                : creditedPlayer.mcServer.getConfigurationManager().playerEntityList;
        if (onlinePlayers == null) {
            return;
        }

        for (PartyMember member : party.getMembers()) {
            if (member == null
                    || creditedCharacter.getCharacterId().equals(
                            member.getCharacterId())) {
                continue;
            }
            EntityPlayerMP participant = findOnlinePlayer(
                    onlinePlayers, member.getOwnerId());
            if (!isEligibleParticipant(
                    creditedPlayer, participant, member, radiusSq)) {
                continue;
            }
            applyAdapters(participant, victim);
        }
    }

    private void applyAdapters(EntityPlayerMP participant, Entity victim) {
        for (PartyQuestCompatibilityAdapter adapter : this.adapters) {
            if (adapter != null && adapter.isAvailable()) {
                adapter.applySharedKillProgress(participant, victim);
            }
        }
    }

    private boolean isEligibleParticipant(EntityPlayerMP source,
                                          EntityPlayerMP participant,
                                          PartyMember member,
                                          double radiusSq) {
        if (source == null || participant == null || member == null
                || participant.worldObj == null
                || participant.worldObj.isRemote
                || participant.dimension != source.dimension
                || !participant.isEntityAlive()
                || participant.getHealth() <= 0.0F) {
            return false;
        }
        RoleplayCharacter active = CharacterActiveResolver.get(participant);
        if (active == null
                || !member.getCharacterId().equals(active.getCharacterId())
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
