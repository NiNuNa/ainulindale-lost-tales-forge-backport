package com.ninuna.losttales.quest.world;

import com.ninuna.losttales.character.identity.RoleplayCharacterIdentityHook;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesWorldQuestSyncPacket;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveMatcher;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveType;
import com.ninuna.losttales.quest.LostTalesQuestRegistry;
import com.ninuna.losttales.quest.LostTalesQuestRewardHelper;
import com.ninuna.losttales.util.LostTalesLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

/**
 * The world quests as they run on the server: an operator starts one, every
 * kill and craft its objectives name adds to the whole server's count, and
 * it succeeds once every count reaches its goal or fails when its days run
 * out. Everyone who added at least the quest's {@code least} is paid its
 * rewards, at once if they are there as the identity that helped, else the
 * next time they play it. What the players see is sent once a second while
 * anything changed, and to a player whenever the identity they play does.
 */
public final class WorldQuests {
    /** The most world quests running at once. */
    public static final int MAX_RUNNING = 8;
    /** Server ticks between two looks at deadlines and at what to send. */
    static final int SWEEP_TICKS = 20;

    /** Why a start was refused, or that it went. */
    public enum StartResult {
        STARTED,
        UNKNOWN_QUEST,
        NOT_A_WORLD_QUEST,
        NOT_READY,
        ALREADY_RUNNING,
        TOO_MANY,
        READ_ONLY
    }

    private static boolean changed;
    private static int ticks;
    /** The identity each player was last sent their part for. */
    private static final Map<UUID, UUID> SENT_AS = new HashMap<UUID, UUID>();

    /** Starts the quest for the whole server, from now for its days. */
    public static StartResult start(MinecraftServer server, String questId) {
        LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(questId);
        if (quest == null) {
            return StartResult.UNKNOWN_QUEST;
        }
        if (!quest.isWorldQuest()) {
            return StartResult.NOT_A_WORLD_QUEST;
        }
        if (!WorldQuestRules.problems(quest).isEmpty()) {
            return StartResult.NOT_READY;
        }
        WorldQuestWorldData data = data(server);
        if (data == null || data.isReadOnly()) {
            return StartResult.READ_ONLY;
        }
        WorldQuestRun kept = data.run(questId);
        if (kept != null && kept.isRunning()) {
            return StartResult.ALREADY_RUNNING;
        }
        if (data.runningCount() >= MAX_RUNNING) {
            return StartResult.TOO_MANY;
        }
        long now = now(server);
        long endsAt = now + WorldQuestRules.days(quest)
                * WorldQuestRules.TICKS_PER_DAY;
        if (!data.start(questId, now, endsAt)) {
            return StartResult.TOO_MANY;
        }
        markChanged();
        return StartResult.STARTED;
    }

    /** Ends a running quest without paying anyone; answers whether one ran. */
    public static boolean stop(MinecraftServer server, String questId) {
        WorldQuestWorldData data = data(server);
        boolean stopped = data != null && data.end(questId,
                WorldQuestRun.State.STOPPED, now(server), Integer.MAX_VALUE);
        if (stopped) {
            markChanged();
        }
        return stopped;
    }

    /** Every run kept, running or ended; empty while there is no world. */
    public static List<WorldQuestRun> runs(MinecraftServer server) {
        WorldQuestWorldData data = data(server);
        return data == null ? java.util.Collections.<WorldQuestRun>emptyList()
                : data.runs();
    }

    /** A kill by {@code player}: it counts for every running quest whose kill objective names the victim. */
    public static void handleKill(EntityPlayerMP player, Entity victim) {
        if (player == null || victim == null) {
            return;
        }
        count(player, LostTalesQuestObjectiveType.KILL, victim, null, 1);
    }

    /** A craft by {@code player}: it counts for every running quest whose craft objective names what was made. */
    public static void handleCraft(EntityPlayerMP player, ItemStack crafted) {
        if (player == null || crafted == null || crafted.getItem() == null) {
            return;
        }
        count(player, LostTalesQuestObjectiveType.CRAFT, null, crafted,
                Math.max(1, crafted.stackSize));
    }

    private static void count(EntityPlayerMP player,
                              LostTalesQuestObjectiveType type, Entity victim,
                              ItemStack crafted, int amount) {
        MinecraftServer server = MinecraftServer.getServer();
        WorldQuestWorldData data = data(server);
        if (data == null || data.runningCount() == 0) {
            return;
        }
        UUID helper = RoleplayCharacterIdentityHook.resolveGameplayId(player);
        long now = now(server);
        for (WorldQuestRun run : data.runs()) {
            if (!run.isRunning() || now >= run.getEndsAt()) {
                continue;
            }
            LostTalesQuestDefinition quest =
                    LostTalesQuestRegistry.getQuest(run.getQuestId());
            if (quest == null || !quest.isWorldQuest()) {
                continue;
            }
            boolean added = false;
            for (LostTalesQuestObjectiveDefinition objective
                    : WorldQuestRules.objectives(quest)) {
                if (!type.is(objective)) {
                    continue;
                }
                boolean matches = victim != null
                        ? LostTalesQuestObjectiveMatcher.matchesEntity(victim,
                                objective)
                        : LostTalesQuestObjectiveMatcher.matchesItem(crafted,
                                objective);
                if (matches && data.add(run.getQuestId(), objective.getId(),
                        amount, WorldQuestRules.goal(objective), helper) > 0) {
                    added = true;
                }
            }
            if (added) {
                markChanged();
                completeIfDone(server, data, quest);
            }
        }
    }

    /** Ends the quest as done once every objective's count reached its goal, and pays who is here. */
    private static void completeIfDone(MinecraftServer server,
                                       WorldQuestWorldData data,
                                       LostTalesQuestDefinition quest) {
        WorldQuestRun run = data.run(quest.getId());
        if (run == null || !run.isRunning()) {
            return;
        }
        for (LostTalesQuestObjectiveDefinition objective
                : WorldQuestRules.objectives(quest)) {
            if (run.getCount(objective.getId())
                    < WorldQuestRules.goal(objective)) {
                return;
            }
        }
        if (data.end(quest.getId(), WorldQuestRun.State.COMPLETED, now(server),
                WorldQuestRules.least(quest))) {
            payEveryoneHere(server, data);
        }
    }

    /**
     * Once a second, from the player tick: pays the rewards waiting for
     * the identity the player plays, and sends the player their part
     * afresh once they play another identity.
     */
    public static void handlePlayerTick(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        WorldQuestWorldData data = data(server);
        if (data == null) {
            return;
        }
        UUID identity = RoleplayCharacterIdentityHook.resolveGameplayId(player);
        if (data.hasRewards(identity)) {
            pay(player, data.takeRewards(identity));
        }
        UUID sent;
        synchronized (WorldQuests.class) {
            sent = SENT_AS.get(player.getUniqueID());
        }
        if (identity != null && !identity.equals(sent)) {
            syncTo(player);
        }
    }

    private static void payEveryoneHere(MinecraftServer server,
                                        WorldQuestWorldData data) {
        for (Object each : server.getConfigurationManager().playerEntityList) {
            if (!(each instanceof EntityPlayerMP)) {
                continue;
            }
            EntityPlayerMP player = (EntityPlayerMP)each;
            UUID identity = RoleplayCharacterIdentityHook.resolveGameplayId(
                    player);
            if (data.hasRewards(identity)) {
                pay(player, data.takeRewards(identity));
            }
        }
    }

    private static void pay(EntityPlayerMP player, Set<String> questIds) {
        for (String questId : questIds) {
            LostTalesQuestDefinition quest =
                    LostTalesQuestRegistry.getQuest(questId);
            if (quest == null) {
                LostTalesLog.warning("A world quest's reward waited for %s, but"
                        + " the quest %s is gone; nothing was paid",
                        player.getCommandSenderName(), questId);
                continue;
            }
            LostTalesQuestRewardHelper.grantRewards(player, quest, null);
        }
    }

    /** Sends the player every run kept and their part in each. */
    public static void syncTo(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        UUID identity = RoleplayCharacterIdentityHook.resolveGameplayId(player);
        synchronized (WorldQuests.class) {
            SENT_AS.put(player.getUniqueID(), identity);
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(
                LostTalesWorldQuestSyncPacket.of(runs(server), identity),
                player);
    }

    /** Forgets what was sent to a player who left. */
    public static synchronized void forget(UUID playerId) {
        SENT_AS.remove(playerId);
    }

    private static synchronized void markChanged() {
        changed = true;
    }

    /** Once a second: fails what ran out of days, and sends every player what changed. */
    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        synchronized (WorldQuests.class) {
            if (++ticks < SWEEP_TICKS) {
                return;
            }
            ticks = 0;
        }
        MinecraftServer server = MinecraftServer.getServer();
        WorldQuestWorldData data = data(server);
        if (data == null) {
            return;
        }
        long now = now(server);
        for (WorldQuestRun run : data.runs()) {
            if (run.isRunning() && now >= run.getEndsAt()
                    && data.end(run.getQuestId(), WorldQuestRun.State.FAILED,
                            now, Integer.MAX_VALUE)) {
                markChanged();
            }
        }
        boolean send;
        synchronized (WorldQuests.class) {
            send = changed;
            changed = false;
        }
        if (!send) {
            return;
        }
        for (Object each : server.getConfigurationManager().playerEntityList) {
            if (each instanceof EntityPlayerMP) {
                syncTo((EntityPlayerMP)each);
            }
        }
    }

    /** Forgets the session as the server starts and stops; the runs stay in the world. */
    public static synchronized void clear() {
        changed = false;
        ticks = 0;
        SENT_AS.clear();
    }

    private static WorldQuestWorldData data(MinecraftServer server) {
        WorldServer overworld = server == null ? null
                : server.worldServerForDimension(0);
        return overworld == null ? null : WorldQuestStorage.get(overworld);
    }

    private static long now(MinecraftServer server) {
        WorldServer overworld = server == null ? null
                : server.worldServerForDimension(0);
        return overworld == null ? 0L : overworld.getTotalWorldTime();
    }
}
