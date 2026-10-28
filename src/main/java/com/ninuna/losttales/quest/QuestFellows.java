package com.ninuna.losttales.quest;

import com.ninuna.losttales.character.identity.RoleplayCharacterIdentityHook;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.quest.FellowshipQuestProgressCoordinator;
import com.ninuna.losttales.fellowship.server.FellowshipService;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesQuestFellowsPacket;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * Tells each player how the members of their fellowships stand in the
 * quests they run too (QUEST-25): the members playing now, as the
 * character in the fellowship, at the player's stage with their counts or
 * past it, and whether each shares the player's credit, travelling with
 * the same fellowship (FEL-28), with whether the server shares progress at
 * all. Sent whenever a quest log changes, or the fellowship a player
 * travels with, to its player and to everyone it is shown to, and only
 * when what they were sent changes.
 *
 * <p>What each player was last sent, and whose logs it came from, is kept
 * by account until they leave: static state, cleared with the server's
 * other stores.</p>
 */
public final class QuestFellows {
    /** The most counts one player is sent: quests times members. */
    public static final int MAX_COUNTS = 128;

    private static final Map<UUID, Sent> SENT = new HashMap<UUID, Sent>();

    private QuestFellows() {}

    /**
     * A member playing now: the character's name, its quest log, and
     * whether it travels with the fellowship the player travels with.
     */
    static final class Fellow {
        final String name;
        final LostTalesQuestPlayerData data;
        final boolean travelling;

        Fellow(String name, LostTalesQuestPlayerData data, boolean travelling) {
            this.name = name;
            this.data = data;
            this.travelling = travelling;
        }
    }

    private static final class Sent {
        final List<QuestFellowCount> counts;
        final boolean shared;
        final Set<UUID> from;

        Sent(List<QuestFellowCount> counts, boolean shared, Set<UUID> from) {
            this.counts = counts;
            this.shared = shared;
            this.from = from;
        }

        /** Whether these are the counts and the sharing a player was sent. */
        boolean same(List<QuestFellowCount> others, boolean sharedNow) {
            return this.shared == sharedNow && this.counts.equals(others);
        }
    }

    /**
     * Where each fellow stands in the quests {@code own} runs: at the same
     * stage, their counts; past it on their own path, done; anywhere else,
     * not shown. Each says whether the fellow travels with the player. In
     * the order the log and the fellows come, at most {@link #MAX_COUNTS}.
     */
    static List<QuestFellowCount> countsFor(LostTalesQuestPlayerData own,
                                            List<Fellow> fellows) {
        List<QuestFellowCount> counts = new ArrayList<QuestFellowCount>();
        if (own == null || fellows == null || fellows.isEmpty()) {
            return counts;
        }
        for (LostTalesQuestProgress mine : own.getActiveQuests()) {
            for (Fellow fellow : fellows) {
                LostTalesQuestProgress theirs = fellow == null || fellow.data == null
                        ? null : fellow.data.getActiveQuest(mine.getQuestId());
                if (theirs == null) {
                    continue;
                }
                QuestFellowCount count;
                if (mine.getStageId().equals(theirs.getStageId())) {
                    count = new QuestFellowCount(mine.getQuestId(), fellow.name,
                            false, fellow.travelling, theirs.getObjectiveProgress());
                } else if (theirs.getStagePath().contains(mine.getStageId())) {
                    count = new QuestFellowCount(mine.getQuestId(), fellow.name,
                            true, fellow.travelling, null);
                } else {
                    continue;
                }
                if (counts.size() >= MAX_COUNTS) {
                    return counts;
                }
                counts.add(count);
            }
        }
        return counts;
    }

    /**
     * Brings the player, the members of their fellowships playing now, and
     * everyone last sent this player's counts up to date: what a change to
     * the player's log, or the character they play, can change.
     */
    public static void refreshAround(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }
        Set<EntityPlayerMP> told = new HashSet<EntityPlayerMP>();
        told.add(player);
        Map<UUID, Online> fellows = fellowsOf(player);
        for (Online fellow : fellows.values()) {
            told.add(fellow.player);
        }
        told.addAll(shownTo(player.getUniqueID(), null));
        for (EntityPlayerMP reader : told) {
            send(reader, reader == player ? fellows : fellowsOf(reader));
        }
    }

    /**
     * The player leaves: everyone shown their counts is sent the counts
     * without them, and what the player was sent is forgotten.
     */
    public static void leaving(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        UUID account = player.getUniqueID();
        synchronized (QuestFellows.class) {
            SENT.remove(account);
        }
        for (EntityPlayerMP reader : shownTo(account, player)) {
            Map<UUID, Online> fellows = fellowsOf(reader);
            fellows.remove(account);
            send(reader, fellows);
        }
    }

    public static synchronized void clear() {
        SENT.clear();
    }

    private static void send(EntityPlayerMP reader, Map<UUID, Online> fellows) {
        List<Fellow> each = new ArrayList<Fellow>();
        for (Online fellow : fellows.values()) {
            LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(fellow.player);
            if (data != null) {
                each.add(new Fellow(fellow.name, data, fellow.travelling));
            }
        }
        List<QuestFellowCount> counts = countsFor(
                LostTalesQuestPlayerData.get(reader), each);
        // With sharing off the journal dims every member (QUEST-25).
        boolean shared = LostTalesConfig.enableSharedQuestProgress;
        UUID account = reader.getUniqueID();
        synchronized (QuestFellows.class) {
            Sent before = SENT.get(account);
            if (before == null ? counts.isEmpty() : before.same(counts, shared)) {
                if (before != null) {
                    SENT.put(account, new Sent(before.counts, before.shared,
                            new HashSet<UUID>(fellows.keySet())));
                }
                return;
            }
            SENT.put(account, new Sent(counts, shared,
                    new HashSet<UUID>(fellows.keySet())));
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new LostTalesQuestFellowsPacket(counts, shared), reader);
    }

    /** The online players last sent counts that came from {@code account}'s log. */
    private static List<EntityPlayerMP> shownTo(UUID account, EntityPlayerMP leaving) {
        List<UUID> readers = new ArrayList<UUID>();
        synchronized (QuestFellows.class) {
            for (Map.Entry<UUID, Sent> sent : SENT.entrySet()) {
                if (sent.getValue().from.contains(account)) {
                    readers.add(sent.getKey());
                }
            }
        }
        List<EntityPlayerMP> online = new ArrayList<EntityPlayerMP>();
        for (EntityPlayerMP player : onlinePlayers()) {
            if (player != leaving && readers.contains(player.getUniqueID())) {
                online.add(player);
            }
        }
        return online;
    }

    /**
     * A member playing now, under the character's name in the fellowship,
     * and whether it shares the player's credit.
     */
    private static final class Online {
        final EntityPlayerMP player;
        final String name;
        final boolean travelling;

        Online(EntityPlayerMP player, String name, boolean travelling) {
            this.player = player;
            this.name = name;
            this.travelling = travelling;
        }
    }

    /**
     * The members of the fellowships of the character the player plays,
     * playing as that member now, by account; never the player's own
     * account. Each knows whether it travels with the fellowship the
     * player travels with, as kills and arrivals are shared (FEL-28).
     */
    private static Map<UUID, Online> fellowsOf(EntityPlayerMP player) {
        Map<UUID, Online> fellows = new LinkedHashMap<UUID, Online>();
        List<Fellowship> fellowships = FellowshipService.getInstance()
                .getFellowshipsForActiveIdentity(player);
        if (fellowships == null || fellowships.isEmpty()) {
            return fellows;
        }
        Set<UUID> travelling = FellowshipQuestProgressCoordinator
                .sharingCreditWith(player);
        Map<UUID, EntityPlayerMP> online = new HashMap<UUID, EntityPlayerMP>();
        for (EntityPlayerMP other : onlinePlayers()) {
            online.put(other.getUniqueID(), other);
        }
        for (Fellowship fellowship : fellowships) {
            for (FellowshipMember member : fellowship.getMembers()) {
                EntityPlayerMP other = member == null ? null
                        : online.get(member.getOwnerId());
                if (other == null || other == player
                        || other.getUniqueID().equals(player.getUniqueID())
                        || !member.getIdentityId().equals(
                                RoleplayCharacterIdentityHook.resolveGameplayId(other))) {
                    continue;
                }
                if (!fellows.containsKey(other.getUniqueID())) {
                    fellows.put(other.getUniqueID(),
                            new Online(other, member.getCharacterName(),
                                    travelling.contains(member.getIdentityId())));
                }
            }
        }
        return fellows;
    }

    private static List<EntityPlayerMP> onlinePlayers() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return Collections.emptyList();
        }
        List<EntityPlayerMP> players = new ArrayList<EntityPlayerMP>();
        for (Object value : server.getConfigurationManager().playerEntityList) {
            if (value instanceof EntityPlayerMP) {
                players.add((EntityPlayerMP) value);
            }
        }
        return players;
    }
}
