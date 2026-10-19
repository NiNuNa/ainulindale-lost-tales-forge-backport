package com.ninuna.losttales.quest.world;

import com.ninuna.losttales.character.deletion.CharacterDeletionStorage;
import com.ninuna.losttales.character.deletion.CharacterDeletionTombstone;
import com.ninuna.losttales.character.identity.RoleplayCharacterIdentityHook;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesWorldQuestSyncPacket;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestDefinitionValidator;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveMatcher;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveType;
import com.ninuna.losttales.quest.LostTalesQuestRegistry;
import com.ninuna.losttales.quest.LostTalesQuestRewardHelper;
import com.ninuna.losttales.quest.LostTalesQuestRules;
import com.ninuna.losttales.quest.LostTalesQuestWords;
import com.ninuna.losttales.quest.ServerQuestSync;
import com.ninuna.losttales.util.LostTalesLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * The world quests as they run on the server: an operator starts one, every
 * kill and craft its objectives name adds to the whole server's count, and
 * it succeeds once every count reaches its goal or fails when its days run
 * out. Only players count: a machine acting as one (a mob grinder, an
 * autocrafter) adds nothing. A part is counted per identity; each account
 * is paid once, by the one of its identities that added most of those that
 * added at least the quest's {@code least} ({@link WorldQuestPayees}). The
 * reward is paid at once if the player is there, alive, as that identity,
 * else the next time they are. The store is saved
 * the moment a reward is paid, so a crash can lose a reward but never pay
 * it twice. What the players see is sent once a second while anything
 * changed, and to a player whenever the identity they play does.
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
    /** Sweeps between two looks at the schedules: a minute. */
    private static final int SCHEDULE_SWEEPS = 60;
    /** Sweeps since the schedules were last looked at; they are looked at once a minute. */
    private static int sweepsSinceSchedules = SCHEDULE_SWEEPS;
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
        if (!problems(quest).isEmpty()) {
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
        announce(server, quest);
        return StartResult.STARTED;
    }

    /**
     * Says in OOC that a world quest has begun, to everyone online
     * (QUEST-36): out-of-character news for the whole server, as a join
     * is. Every missive board shows its notice too, read by each player's
     * game from the world quests it is sent.
     */
    private static void announce(MinecraftServer server,
                                 LostTalesQuestDefinition quest) {
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }
        server.getConfigurationManager().sendChatMsg(
                new net.minecraft.util.ChatComponentTranslation(
                        com.ninuna.losttales.chat.ChatSystemLineClassifier
                                .WORLD_QUEST_BEGUN_KEY,
                        LostTalesQuestWords.titleComponent(quest),
                        Integer.valueOf(WorldQuestRules.days(quest))));
    }

    /**
     * What keeps a world quest from running, in words for the operator:
     * every warning its file raises, the world quest's own rules among
     * them. Empty for one that can run.
     */
    public static List<String> problems(LostTalesQuestDefinition quest) {
        return LostTalesQuestDefinitionValidator.describeWarnings(
                java.util.Collections.singletonList(quest));
    }

    /** Ends a running quest without paying anyone; answers whether one ran. */
    public static boolean stop(MinecraftServer server, String questId) {
        WorldQuestWorldData data = data(server);
        boolean stopped = data != null && data.end(questId,
                WorldQuestRun.State.STOPPED, now(server));
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
        if (player == null || player instanceof FakePlayer || victim == null) {
            return;
        }
        count(player, LostTalesQuestObjectiveType.KILL, victim, null, 1);
    }

    /**
     * The player touched somebody: every running world quest's delivery
     * that names them takes what the player carries of its items, up to
     * what its count still lacks (QUEST-36). Answers whether anything was
     * handed in.
     */
    public static boolean handleDelivery(EntityPlayerMP player, Entity target) {
        return target != null && deliver(player, target);
    }

    /** As above, at a missive board: every running delivery handed in at a board. */
    public static boolean deliverAtBoard(EntityPlayerMP player) {
        return deliver(player, null);
    }

    /**
     * Whether a running world quest's delivery is handed in at a board and
     * takes something the player carries: what a board's Hand In asks.
     */
    public static boolean hasBoardDelivery(EntityPlayerMP player) {
        MinecraftServer server = MinecraftServer.getServer();
        WorldQuestWorldData data = data(server);
        if (player == null || data == null || data.runningCount() == 0) {
            return false;
        }
        for (String questId : data.runningQuestIds(now(server))) {
            for (LostTalesQuestObjectiveDefinition objective
                    : WorldQuestRules.objectives(LostTalesQuestRegistry.getQuest(questId))) {
                if (LostTalesQuestObjectiveType.DELIVER.is(objective)
                        && LostTalesQuestDefinitionValidator.isBoardDelivery(
                                objective.getParams())
                        && carried(player, objective) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean deliver(EntityPlayerMP player, Entity target) {
        if (player == null || player instanceof FakePlayer) {
            return false;
        }
        MinecraftServer server = MinecraftServer.getServer();
        WorldQuestWorldData data = data(server);
        if (data == null || data.runningCount() == 0) {
            return false;
        }
        UUID helper = RoleplayCharacterIdentityHook.resolveGameplayId(player);
        boolean any = false;
        for (String questId : data.runningQuestIds(now(server))) {
            LostTalesQuestDefinition quest =
                    LostTalesQuestRegistry.getQuest(questId);
            if (quest == null || !quest.isWorldQuest()) {
                continue;
            }
            boolean added = false;
            for (LostTalesQuestObjectiveDefinition objective
                    : WorldQuestRules.objectives(quest)) {
                boolean atBoard = LostTalesQuestDefinitionValidator
                        .isBoardDelivery(objective.getParams());
                if (!LostTalesQuestObjectiveType.DELIVER.is(objective)
                        || (target == null) != atBoard
                        || target != null && !LostTalesQuestObjectiveMatcher
                                .matchesEntity(target, objective, player)) {
                    continue;
                }
                int lacking = WorldQuestRules.goal(objective)
                        - data.count(questId, objective.getId());
                int taken = take(player, objective, lacking);
                if (taken > 0 && data.add(questId, objective.getId(), taken,
                        WorldQuestRules.goal(objective), helper) > 0) {
                    added = true;
                }
            }
            if (added) {
                any = true;
                markChanged();
                completeIfDone(server, data, quest);
            }
        }
        return any;
    }

    /** How many of a delivery's items the player carries. */
    private static int carried(EntityPlayerMP player,
                               LostTalesQuestObjectiveDefinition objective) {
        int carried = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack != null && LostTalesQuestObjectiveMatcher.matchesItem(stack, objective)) {
                carried += stack.stackSize;
            }
        }
        return carried;
    }

    /**
     * Takes up to {@code most} of a delivery's items out of the player's
     * inventory, and tells their client; answers how many were taken.
     */
    private static int take(EntityPlayerMP player,
                            LostTalesQuestObjectiveDefinition objective,
                            int most) {
        int taken = 0;
        ItemStack[] inventory = player.inventory.mainInventory;
        for (int slot = 0; slot < inventory.length && taken < most; slot++) {
            ItemStack stack = inventory[slot];
            if (stack == null || !LostTalesQuestObjectiveMatcher.matchesItem(stack, objective)) {
                continue;
            }
            int part = Math.min(stack.stackSize, most - taken);
            stack.stackSize -= part;
            taken += part;
            if (stack.stackSize <= 0) {
                inventory[slot] = null;
            }
        }
        if (taken > 0) {
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
        }
        return taken;
    }

    /** A craft by {@code player}: it counts for every running quest whose craft objective names what was made. */
    public static void handleCraft(EntityPlayerMP player, ItemStack crafted) {
        if (player == null || player instanceof FakePlayer || crafted == null
                || crafted.getItem() == null) {
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
        for (String questId : data.runningQuestIds(now(server))) {
            LostTalesQuestDefinition quest =
                    LostTalesQuestRegistry.getQuest(questId);
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
                                objective, player)
                        : LostTalesQuestObjectiveMatcher.matchesItem(crafted,
                                objective);
                if (matches && data.add(questId, objective.getId(), amount,
                        WorldQuestRules.goal(objective), helper) > 0) {
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
        for (LostTalesQuestObjectiveDefinition objective
                : WorldQuestRules.objectives(quest)) {
            if (data.count(quest.getId(), objective.getId())
                    < WorldQuestRules.goal(objective)) {
                return;
            }
        }
        if (data.end(quest.getId(), WorldQuestRun.State.COMPLETED, now(server),
                WorldQuestRules.least(quest), accounts(server),
                !LostTalesQuestRules.podiumRewards(quest).isEmpty())) {
            payEveryoneHere(server, data);
        }
    }

    /**
     * Which account an identity belongs to, from the characters the world
     * keeps: a character's owner, a deleted one's too, since it may be
     * restored; else the identity itself, an account's own.
     */
    private static WorldQuestPayees.Accounts accounts(MinecraftServer server) {
        final WorldServer overworld = server == null ? null
                : server.worldServerForDimension(0);
        return new WorldQuestPayees.Accounts() {
            @Override
            public UUID accountOf(UUID identity) {
                if (overworld == null || identity == null) {
                    return identity;
                }
                try {
                    RoleplayCharacter character = CharacterStorage.get(overworld)
                            .findCharacter(identity);
                    if (character != null) {
                        return character.getOwnerId();
                    }
                    CharacterDeletionTombstone deleted = CharacterDeletionStorage
                            .get(overworld).getTombstone(identity);
                    return deleted != null ? deleted.getOwnerId() : identity;
                } catch (RuntimeException unreadable) {
                    LostTalesLog.warning("A world quest could not tell whose"
                            + " character %s is, so it is paid as an account"
                            + " of its own: %s", identity, unreadable);
                    return identity;
                }
            }
        };
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
        if (mayBePaid(player) && data.hasRewards(identity)) {
            pay(server, player, data.takeRewards(identity));
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
            if (mayBePaid(player) && data.hasRewards(identity)) {
                pay(server, player, data.takeRewards(identity));
            }
        }
    }

    /**
     * Whether items and experience given now reach the player: not on the
     * death screen, where they would stay with the body.
     */
    private static boolean mayBePaid(EntityPlayerMP player) {
        return !(player instanceof FakePlayer) && !player.isDead
                && player.getHealth() > 0.0F;
    }

    /**
     * Saves the store, the rewards already taken out of it, then pays
     * each: a crash in between loses them, but never pays them twice.
     */
    private static void pay(MinecraftServer server, EntityPlayerMP player,
                            List<String> questIds) {
        if (questIds.isEmpty()) {
            return;
        }
        WorldServer overworld = server == null ? null
                : server.worldServerForDimension(0);
        if (overworld != null && overworld.mapStorage != null) {
            overworld.mapStorage.saveAllData();
        }
        for (String waiting : questIds) {
            boolean podium = waiting.startsWith(WorldQuestRules.PODIUM_REWARD_PREFIX);
            String questId = podium ? waiting.substring(
                    WorldQuestRules.PODIUM_REWARD_PREFIX.length()) : waiting;
            LostTalesQuestDefinition quest =
                    LostTalesQuestRegistry.getQuest(questId);
            if (quest == null) {
                LostTalesLog.warning("A world quest's reward waited for %s, but"
                        + " the quest %s is gone; nothing was paid",
                        player.getCommandSenderName(), questId);
                continue;
            }
            if (podium) {
                LostTalesQuestRewardHelper.grantRewardBlock(player,
                        LostTalesQuestRules.podiumRewards(quest));
            } else {
                LostTalesQuestRewardHelper.grantRewards(player, quest, null, -1);
            }
        }
    }

    /** Sends the player every run kept and their part in each. */
    public static void syncTo(EntityPlayerMP player) {
        if (player != null) {
            MinecraftServer server = MinecraftServer.getServer();
            List<WorldQuestRun> runs = runs(server);
            syncTo(player, runs, podiums(server, runs));
        }
    }

    /**
     * Each run's podium as everyone is shown it (QUEST-36): its best
     * helpers, one per account, by their characters' names, best first.
     * A helper who played an account of their own is left out of the
     * names; the podium still pays them.
     */
    private static Map<String, WorldQuestView> podiums(MinecraftServer server,
                                                       List<WorldQuestRun> runs) {
        Map<String, WorldQuestView> podiums = new HashMap<String, WorldQuestView>();
        WorldServer overworld = server == null ? null
                : server.worldServerForDimension(0);
        if (overworld == null) {
            return podiums;
        }
        WorldQuestPayees.Accounts accounts = accounts(server);
        for (WorldQuestRun run : runs) {
            LostTalesQuestDefinition quest =
                    LostTalesQuestRegistry.getQuest(run.getQuestId());
            if (quest == null || run.getHelpers().isEmpty()) {
                continue;
            }
            List<String> names = new ArrayList<String>();
            List<Integer> parts = new ArrayList<Integer>();
            for (UUID best : WorldQuestPayees.podium(run.getHelpers(),
                    WorldQuestRules.least(quest), accounts,
                    WorldQuestRules.PODIUM_PLACES)) {
                RoleplayCharacter character;
                try {
                    character = CharacterStorage.get(overworld).findCharacter(best);
                } catch (RuntimeException unreadable) {
                    character = null;
                }
                if (character != null) {
                    names.add(character.getName());
                    parts.add(Integer.valueOf(run.getHelped(best)));
                }
            }
            podiums.put(run.getQuestId(), WorldQuestView.of(run, null, names, parts));
        }
        return podiums;
    }

    /**
     * Sends the player {@code runs}, one copy of the store shared by
     * everyone sent it this sweep, each run's quest file first where their
     * client was not sent it yet.
     */
    private static void syncTo(EntityPlayerMP player, List<WorldQuestRun> runs,
                               Map<String, WorldQuestView> podiums) {
        UUID identity = RoleplayCharacterIdentityHook.resolveGameplayId(player);
        synchronized (WorldQuests.class) {
            SENT_AS.put(player.getUniqueID(), identity);
        }
        List<String> questIds = new ArrayList<String>();
        for (WorldQuestRun run : runs) {
            questIds.add(run.getQuestId());
        }
        ServerQuestSync.reveal(player, questIds);
        LostTalesNetworkHandler.CHANNEL.sendTo(
                LostTalesWorldQuestSyncPacket.of(runs, identity, podiums), player);
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
        boolean lookAtSchedules;
        synchronized (WorldQuests.class) {
            lookAtSchedules = ++sweepsSinceSchedules >= SCHEDULE_SWEEPS;
            if (lookAtSchedules) {
                sweepsSinceSchedules = 0;
            }
        }
        if (lookAtSchedules) {
            WorldQuestSchedules.check(server, System.currentTimeMillis());
        }
        long now = now(server);
        for (WorldQuestRun run : data.runs()) {
            if (run.isRunning() && now >= run.getEndsAt()
                    && data.end(run.getQuestId(), WorldQuestRun.State.FAILED,
                            now)) {
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
        List<WorldQuestRun> runs = data.runs();
        Map<String, WorldQuestView> podiums = podiums(server, runs);
        for (Object each : server.getConfigurationManager().playerEntityList) {
            if (each instanceof EntityPlayerMP) {
                syncTo((EntityPlayerMP)each, runs, podiums);
            }
        }
    }

    /** Forgets the session as the server starts and stops; the runs stay in the world. */
    public static synchronized void clear() {
        changed = false;
        ticks = 0;
        // The schedules are looked at on the first sweep of a server.
        sweepsSinceSchedules = SCHEDULE_SWEEPS;
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
