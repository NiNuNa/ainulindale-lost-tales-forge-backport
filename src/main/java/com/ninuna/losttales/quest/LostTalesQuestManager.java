package com.ninuna.losttales.quest;

import com.ninuna.losttales.compat.lotr.LotrNpcNames;
import com.ninuna.losttales.compat.lotr.LotrQuestReference;
import com.ninuna.losttales.compat.lotr.LotrQuestJournalAdapter;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerCatalog;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerNamedAfter;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerNames;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSource;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerStorage;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerVisibilityPolicy;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerWorldData;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesMapMarkerDiscoveryPacket;
import com.ninuna.losttales.network.packet.LostTalesQuestSyncPacket;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import com.ninuna.losttales.quest.progress.LostTalesQuestHistoryEntry;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import com.ninuna.losttales.world.map.waypoint.LostTalesMapMarkerWaypointUnlockHelper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
/** Server-side helper methods for basic quest state changes and objective progress. */
public final class LostTalesQuestManager {

    private LostTalesQuestManager() {}

    /**
     * Drops the missives no player online holds in their quest log from the
     * quests the server knows; {@code leaving} is a player on the way out,
     * whose log counts no longer. A log names its own again as it is read,
     * so a missive dropped here comes back with the character that holds
     * it.
     */
    public static void forgetUnheldMissives(EntityPlayer leaving) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }
        Set<String> held = new HashSet<String>();
        for (Object each
                : server.getConfigurationManager().playerEntityList) {
            if (each == leaving || !(each instanceof EntityPlayer)) {
                continue;
            }
            LostTalesQuestPlayerData data =
                    LostTalesQuestPlayerData.get((EntityPlayer)each);
            if (data == null) {
                continue;
            }
            for (LostTalesQuestDefinition quest
                    : data.getDynamicQuestDefinitions()) {
                held.add(quest.getId());
            }
        }
        LostTalesQuestRegistry.retainRuntimeQuests(held);
    }

    /**
     * Begins a quest {@link QuestStartGate} let through: its first stage,
     * a deadline {@code timeLimitTicks} on where it has one, tracked and its
     * markers revealed as the server's settings say, and its gathering
     * counted at once.
     */
    static void begin(EntityPlayerMP player, LostTalesQuestDefinition quest,
                      long timeLimitTicks) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null || quest == null) {
            return;
        }
        String questId = quest.getId();
        LostTalesQuestStageDefinition firstStage = quest.getFirstStage();
        long acceptedWorldTime = player.worldObj.getTotalWorldTime();
        long deadlineWorldTime = timeLimitTicks > 0L
                ? acceptedWorldTime + timeLimitTicks : 0L;
        data.startQuest(questId, firstStage == null ? "" : firstStage.getId(),
                acceptedWorldTime, deadlineWorldTime);
        if (LostTalesConfig.autoPinQuestOnStart && !data.isQuestPinned(questId)) {
            data.pinQuestId(questId);
        }
        if (LostTalesConfig.autoRevealQuestMarkersOnStart) {
            revealQuestMarkers(player, quest, false);
        }
        if (scanProgressibleGatherObjectives(player, quest)
                || firstStage == null || firstStage.getObjectives().isEmpty()) {
            evaluateStageProgress(player, questId);
        }
        syncToClient(player);
    }

    public static boolean completeQuest(EntityPlayer player, String questId) {
        LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(questId);
        if (quest == null) {
            return false;
        }
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        LostTalesQuestProgress progress = data == null ? null
                : data.getActiveQuest(questId);
        long worldTime = player == null || player.worldObj == null ? 0L
                : player.worldObj.getTotalWorldTime();
        boolean changed = data != null && data.completeQuest(questId,
                buildCompletionOutcome(quest, progress), worldTime,
                getCompletedOptionalObjectiveIds(quest, progress));
        if (changed) {
            revealQuestMarkers(player, quest, false);
            grantQuestRewards(player, quest, progress);
            syncToClient(player);
        }
        return changed;
    }

    public static boolean resetQuest(EntityPlayer player, String questId) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        boolean changed = data != null && data.resetQuest(questId);
        if (changed) {
            sendQuestChat(player, "chat.losttales.quest.note.reset", questTitle(questId));
            syncToClient(player);
        }
        return changed;
    }

    public static boolean abandonQuest(EntityPlayer player, String questId) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        long worldTime = player == null || player.worldObj == null ? 0L
                : player.worldObj.getTotalWorldTime();
        boolean changed = data != null && data.abandonQuest(questId,
                "gui.losttales.quest.reason.abandoned", worldTime);
        if (changed) {
            syncToClient(player);
        }
        return changed;
    }


    /**
     * A player giving up a quest of their own, asked for from the
     * journal. The id off the wire names nothing on its own: the quest
     * has to be one this player is on right now. A LOTR quest is given
     * up the way LOTR's own quest book gives it up. Answers whether
     * anything changed.
     */
    public static boolean abandonOwnQuest(EntityPlayerMP player,
                                          String questId) {
        if (player == null || questId == null || questId.length() == 0
                || player.worldObj == null || player.worldObj.isRemote) {
            return false;
        }
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null) {
            return false;
        }
        if (LotrQuestReference.isLotrQuest(questId)) {
            boolean abandoned = LotrQuestJournalAdapter.abandon(player,
                    questId, data);
            if (abandoned) {
                syncToClient(player);
            }
            return abandoned;
        }
        return data.isQuestActive(questId) && abandonQuest(player, questId);
    }

    /**
     * A player clearing a finished or failed LOTR quest out of their
     * History, asked for from the journal. A Lost Tales quest's History
     * stays: it decides whether the quest may be taken again. Answers
     * whether anything changed.
     */
    public static boolean clearFinishedQuest(EntityPlayerMP player,
                                             String questId) {
        if (player == null || player.worldObj == null
                || player.worldObj.isRemote
                || !LotrQuestReference.isLotrQuest(questId)) {
            return false;
        }
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        boolean cleared = data != null
                && LotrQuestJournalAdapter.clear(player, questId, data);
        if (cleared) {
            syncToClient(player);
        }
        return cleared;
    }

    /**
     * Giving over what a quest asked for, in conversation. Each talk or
     * delivery the quest waits on is answered by the person it names,
     * standing beside the player and in sight, whoever else stands
     * nearer; only that quest's objectives move, and the items are
     * counted before any are taken, as they are on an ordinary hand-in.
     */
    public static boolean handInFromConversation(EntityPlayerMP player,
                                                 String questId) {
        LostTalesQuestDefinition quest = conversationQuest(player, questId);
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        LostTalesQuestProgress progress = quest == null || data == null
                ? null : data.getActiveQuest(quest.getId());
        if (progress == null) {
            return false;
        }
        for (LostTalesQuestObjectiveDefinition objective
                : LostTalesQuestObjectiveSelection
                .getProgressibleObjectives(quest, progress)) {
            if (!LostTalesQuestObjectiveType.of(objective).isNpcVisit()
                    || LostTalesQuestObjectiveSelection.isComplete(
                            progress, objective)) {
                continue;
            }
            Entity recipient = nearbyNamed(player, objective.getParams());
            if (recipient != null
                    && handleTalkedTo(player, recipient, quest.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The quest a conversation is about, or null when this player
     * cannot be having it: an unknown quest, a LOTR one, or one whose
     * giver is nowhere near or out of sight.
     */
    private static LostTalesQuestDefinition conversationQuest(
            EntityPlayerMP player, String questId) {
        if (player == null || questId == null || questId.length() == 0
                || player.worldObj == null || player.worldObj.isRemote
                || LotrQuestReference.isLotrQuest(questId)) {
            return null;
        }
        LostTalesQuestDefinition quest =
                LostTalesQuestRegistry.getQuest(questId);
        return quest != null && nearbyQuestGiver(player, quest) != null
                ? quest : null;
    }

    /**
     * Somebody the quest names, as its giver or as the one a talk or a
     * delivery goes to, standing within reach of the player and in their
     * sight, or null. Reach is a little past an interaction's, so a
     * conversation can only be had with somebody who could have been
     * spoken to.
     */
    private static Entity nearbyQuestGiver(EntityPlayerMP player,
                                           LostTalesQuestDefinition quest) {
        Entity giver = nearbyNamed(player, quest.getInteraction());
        if (giver != null) {
            return giver;
        }
        for (LostTalesQuestStageDefinition stage : quest.getStages()) {
            for (LostTalesQuestObjectiveDefinition objective
                    : stage.getObjectives()) {
                if (!LostTalesQuestObjectiveType.of(objective).isNpcVisit()) {
                    continue;
                }
                Entity named = nearbyNamed(player, objective.getParams());
                if (named != null) {
                    return named;
                }
            }
        }
        return null;
    }

    /**
     * Somebody the params name ({@link LostTalesQuestObjectiveMatcher})
     * within conversation reach of the player and in their line of sight,
     * or null.
     */
    private static Entity nearbyNamed(EntityPlayerMP player,
                                      Map<String, String> params) {
        if (!LostTalesQuestObjectiveMatcher.namesWhom(params)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<Entity> near = player.worldObj.getEntitiesWithinAABBExcludingEntity(
                player, player.boundingBox.expand(QuestStartGate.GIVER_REACH,
                        QuestStartGate.GIVER_REACH, QuestStartGate.GIVER_REACH));
        for (Entity candidate : near) {
            if (LostTalesQuestObjectiveMatcher.matchesEntity(candidate, params)
                    && player.canEntityBeSeen(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public static boolean pinQuest(EntityPlayer player, String questId) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null) {
            return false;
        }
        boolean changed = LotrQuestReference.isLotrQuest(questId)
                ? LotrQuestJournalAdapter.pin(player, questId, data)
                : data.pinQuestId(questId);
        if (changed) {
            sendQuestChat(player, "chat.losttales.quest.note.tracking", questTitle(questId));
            syncToClient(player);
        }
        return changed;
    }

    public static boolean unpinQuest(EntityPlayer player) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null) {
            return false;
        }
        boolean changed = data.clearPinnedQuestId();
        if (changed) {
            LotrQuestJournalAdapter.clearNativeTracking(player);
            sendQuestChat(player, "chat.losttales.quest.note.untracked_all");
            syncToClient(player);
        }
        return changed;
    }

    public static boolean unpinQuest(EntityPlayer player, String questId) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null || questId == null || questId.length() == 0) {
            return false;
        }
        boolean changed = LotrQuestReference.isLotrQuest(questId)
                ? LotrQuestJournalAdapter.unpin(player, questId, data)
                : data.unpinQuestId(questId);
        if (changed) {
            sendQuestChat(player, "chat.losttales.quest.note.untracked", questTitle(questId));
            syncToClient(player);
        }
        return changed;
    }

    public static Set<String> getPinnedQuestIds(EntityPlayer player) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        return data == null ? Collections.<String>emptySet() : data.getPinnedQuestIds();
    }

    public static Set<String> getDiscoveredMarkerIds(EntityPlayer player) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        return data == null ? Collections.<String>emptySet() : data.getDiscoveredMarkerIds();
    }

    public static String getPinnedMapMarkerId(EntityPlayer player) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        return data == null ? "" : data.getPinnedMapMarkerId();
    }

    public static Collection<LostTalesMapMarkerDefinition> getDynamicMapMarkers(EntityPlayer player) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        return data == null ? Collections.<LostTalesMapMarkerDefinition>emptyList() : data.getDynamicMapMarkers();
    }

    public static boolean revealQuestMarkers(EntityPlayer player, String questId) {
        LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(questId);
        if (quest == null) {
            sendQuestChat(player, "chat.losttales.quest.unknown", questId);
            return false;
        }
        boolean changed = revealQuestMarkers(player, quest, true);
        if (changed) {
            syncToClient(player);
        }
        return changed;
    }

    public static boolean revealMapMarker(EntityPlayer player, String markerId) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null || markerId == null || markerId.trim().length() == 0) {
            return false;
        }
        markerId = LostTalesQuestMarkerHelper.normalizeMarkerId(markerId);
        boolean changed = data.discoverMarker(markerId);
        if (changed) {
            addLotrWaypointForDiscoveredMarker(player, markerId);
            syncToClient(player);
        }
        return changed;
    }


    public static boolean revealQuestGiverMarker(EntityPlayer player, LostTalesQuestDefinition quest, Entity target, boolean sync) {
        if (player == null || quest == null || target == null || player.worldObj == null) {
            return false;
        }
        List<String> markerIds = LostTalesQuestMarkerHelper.collectDynamicQuestGiverMarkerIds(quest);
        if (markerIds.isEmpty()) {
            return false;
        }

        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null) {
            return false;
        }

        String name = getEntityMarkerName(target);
        String namedAfter = name.length() > 0 ? ""
                : LostTalesMapMarkerNamedAfter.entity(EntityList.getEntityString(target));
        if (name.length() == 0 && namedAfter.length() == 0) {
            name = LostTalesQuestWords.title(quest);
        }
        boolean changed = false;
        for (String markerId : markerIds) {
            LostTalesMapMarkerDefinition marker = giverMarker(markerId, name,
                    namedAfter, "blue",
                    target.worldObj == null ? player.worldObj.provider.dimensionId : target.worldObj.provider.dimensionId,
                    target.posX, target.posY, target.posZ);
            changed |= data.discoverDynamicMarker(marker);
        }
        if (changed) {
            sendQuestChat(player, "chat.losttales.quest.note.marked",
                    LostTalesMapMarkerNames.component(markerIds.get(0), name,
                            namedAfter));
            if (sync) {
                syncToClient(player);
            }
        }
        return changed;
    }

    public static boolean revealQuestGiverMarker(EntityPlayer player, LostTalesQuestDefinition quest, Block block, int x, int y, int z, boolean sync) {
        if (player == null || quest == null || block == null || player.worldObj == null) {
            return false;
        }
        List<String> markerIds = LostTalesQuestMarkerHelper.collectDynamicQuestGiverMarkerIds(quest);
        if (markerIds.isEmpty()) {
            return false;
        }

        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null) {
            return false;
        }

        Object registeredName = Block.blockRegistry.getNameForObject(block);
        String namedAfter = registeredName == null ? ""
                : LostTalesMapMarkerNamedAfter.block(registeredName.toString());
        String name = namedAfter.length() > 0 ? "" : LostTalesQuestWords.title(quest);
        boolean changed = false;
        for (String markerId : markerIds) {
            LostTalesMapMarkerDefinition marker = giverMarker(markerId, name,
                    namedAfter, "yellow", player.worldObj.provider.dimensionId,
                    x + 0.5D, y + 0.5D, z + 0.5D);
            changed |= data.discoverDynamicMarker(marker);
        }
        if (changed) {
            sendQuestChat(player, "chat.losttales.quest.note.marked",
                    LostTalesMapMarkerNames.component(markerIds.get(0), name,
                            namedAfter));
            if (sync) {
                syncToClient(player);
            }
        }
        return changed;
    }

    public static boolean forgetMapMarker(EntityPlayer player, String markerId) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null || markerId == null || markerId.trim().length() == 0) {
            return false;
        }
        markerId = LostTalesQuestMarkerHelper.normalizeMarkerId(markerId);
        boolean changed = data.forgetMarker(markerId);
        if (changed) {
            if (player instanceof EntityPlayerMP) {
                ensureLotrWaypointsForDiscoveredMapMarkers((EntityPlayerMP) player);
            }
            sendQuestChat(player, "chat.losttales.quest.note.unmarked", markerId);
            syncToClient(player);
        }
        return changed;
    }

    public static boolean pinMapMarker(EntityPlayer player, String markerId) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null || markerId == null || markerId.trim().length() == 0) {
            return false;
        }
        markerId = LostTalesQuestMarkerHelper.normalizeMarkerId(markerId);
        LostTalesMapMarkerDefinition bundledMarker = LostTalesMapMarkerCatalog.getMarker(markerId);
        boolean knownDynamicMarker = data.getDynamicMapMarker(markerId) != null;
        if (!data.isMarkerDiscovered(markerId)) {
            if (bundledMarker != null && LostTalesMapMarkerCatalog.isVisibleByDefault(markerId)) {
                if (bundledMarker.isDiscoverable() && data.discoverMarker(markerId)) {
                    addLotrWaypointForDiscoveredMarker(player, bundledMarker);
                }
            } else {
                sendQuestChat(player, "chat.losttales.quest.marker_unknown", markerId);
                return false;
            }
        }
        if (bundledMarker == null && !knownDynamicMarker) {
            sendQuestChat(player, "chat.losttales.quest.note.tracking_marker_id", markerId);
        }
        boolean changed = data.setPinnedMapMarkerId(markerId);
        if (changed) {
            sendQuestChat(player, "chat.losttales.quest.note.tracking_marker", markerId);
            syncToClient(player);
        }
        return changed;
    }

    public static boolean unpinMapMarker(EntityPlayer player) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null) {
            return false;
        }
        boolean changed = data.clearPinnedMapMarkerId();
        if (changed) {
            sendQuestChat(player, "chat.losttales.quest.note.untracked_marker");
            syncToClient(player);
        }
        return changed;
    }

    /**
     * Counts every running gather objective again from what the player
     * holds, as an operator's scan asks; answers whether any moved.
     */
    public static boolean refreshGatherProgressFromInventory(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return false;
        }

        boolean changed = false;
        for (LostTalesQuestProgress progress : getActiveQuests(player)) {
            LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(progress.getQuestId());
            if (quest != null) {
                boolean questChanged = scanProgressibleGatherObjectives(player, quest);
                if (questChanged) {
                    evaluateStageProgress(player, quest.getId());
                    changed = true;
                }
            }
        }

        if (changed) {
            syncToClient(player);
        }
        return changed;
    }

    public static Collection<LostTalesQuestProgress> getActiveQuests(EntityPlayer player) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        return data == null ? Collections.<LostTalesQuestProgress>emptyList() : data.getActiveQuests();
    }

    public static Set<String> getCompletedQuestIds(EntityPlayer player) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        return data == null ? Collections.<String>emptySet() : data.getCompletedQuestIds();
    }

    public static void handleEntityKilled(EntityPlayerMP player, Entity victim,
            boolean shared) {
        if (player == null || victim == null || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }

        boolean changed = false;
        for (LostTalesQuestProgress progress : getActiveQuests(player)) {
            LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(progress.getQuestId());
            if (quest == null) {
                continue;
            }

            for (LostTalesQuestObjectiveDefinition objective
                    : LostTalesQuestObjectiveSelection
                    .getProgressibleObjectives(quest, progress)) {
                if (!LostTalesQuestObjectiveType.KILL.is(objective)) {
                    continue;
                }
                if (shared && !allowsFellowshipSharing(objective)) {
                    continue;
                }
                if (!LostTalesQuestObjectiveMatcher.matchesEntity(victim, objective)) {
                    continue;
                }
                if (!isWithinObjectiveRadius(player, victim, objective)) {
                    continue;
                }
                changed |= addObjectiveProgressAndEvaluate(player, quest, objective, 1);
            }
        }
        if (changed) {
            syncToClient(player);
        }
    }

    /**
     * An item reached the player: picked up, now in the inventory, or
     * made, now in the inventory or on the cursor. Every gather objective
     * that names it counts again what the player holds
     * ({@link GatherCount}).
     */
    public static void handleItemReceived(EntityPlayerMP player, ItemStack received) {
        if (player == null || received == null || received.getItem() == null || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }
        if (countGathered(player, received)) {
            syncToClient(player);
        }
    }

    /** Counts again every gather objective that names the item; answers whether any moved. */
    private static boolean countGathered(EntityPlayerMP player, ItemStack received) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null) {
            return false;
        }
        boolean changed = false;
        for (LostTalesQuestProgress progress : getActiveQuests(player)) {
            LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(progress.getQuestId());
            if (quest == null) {
                continue;
            }

            for (LostTalesQuestObjectiveDefinition objective
                    : LostTalesQuestObjectiveSelection
                    .getProgressibleObjectives(quest, progress)) {
                if (!isGatherObjective(objective)
                        || !LostTalesQuestObjectiveMatcher.matchesItem(received, objective)) {
                    continue;
                }
                int target = LostTalesQuestObjectiveTextHelper.getObjectiveTargetCount(objective);
                int before = data.getObjectiveProgress(quest.getId(), objective.getId());
                changed |= setObjectiveProgressAndEvaluate(player, quest, objective,
                        GatherCount.counted(GatherCount.takes(objective), before,
                                countHeld(player, objective), target));
            }
        }
        return changed;
    }

    /**
     * The player has come to somebody. Finishes every talk objective
     * that names them and hands over what every delivery objective asks
     * for, on the active quests of the character being played. Answers
     * whether anything changed, so the caller can tell a plain
     * interaction from one the quest system answered.
     *
     * <p>A delivery is all or nothing ({@link GatherCount#take}): the
     * items are taken only once the whole amount is there, so a
     * half-finished hand-over can never eat the items. The items leave the
     * world with the recipient; nothing is given back. Each objective is
     * asked separately, so two deliveries to the same person each take
     * their own items.</p>
     */
    public static boolean handleTalkedTo(EntityPlayerMP player, Entity target) {
        return handleTalkedTo(player, target, null);
    }

    /**
     * As above for one quest alone, named by {@code onlyQuestId}; null
     * answers every quest the player is on, which is what an ordinary
     * interaction does.
     */
    public static boolean handleTalkedTo(EntityPlayerMP player, Entity target,
                                         String onlyQuestId) {
        if (player == null || target == null || player.worldObj == null
                || player.worldObj.isRemote) {
            return false;
        }
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null) {
            return false;
        }
        boolean changed = false;
        boolean answered = false;
        for (LostTalesQuestProgress progress : getActiveQuests(player)) {
            LostTalesQuestDefinition quest =
                    LostTalesQuestRegistry.getQuest(progress.getQuestId());
            if (quest == null || (onlyQuestId != null
                    && !onlyQuestId.equals(quest.getId()))) {
                continue;
            }
            // A quest with a written hand-in is given over in
            // conversation; touching its giver opens the talk instead of
            // quietly taking the items.
            if (onlyQuestId == null
                    && LostTalesQuestDialogue.of(quest).isHandedIn()) {
                continue;
            }
            for (LostTalesQuestObjectiveDefinition objective
                    : LostTalesQuestObjectiveSelection
                    .getProgressibleObjectives(quest, progress)) {
                LostTalesQuestObjectiveType type =
                        LostTalesQuestObjectiveType.of(objective);
                if (!type.isNpcVisit()
                        || LostTalesQuestObjectiveSelection.isComplete(
                                progress, objective)
                        || !LostTalesQuestObjectiveMatcher.matchesEntity(target, objective)
                        || !isWithinObjectiveRadius(player, target, objective)) {
                    continue;
                }
                answered = true;
                if (type == LostTalesQuestObjectiveType.TALK) {
                    changed |= addObjectiveProgressAndEvaluate(player, quest,
                            objective, 1);
                    continue;
                }
                if (!handOver(player, Collections.singletonList(objective)).whole) {
                    sendQuestChat(player, "chat.losttales.quest.missing_items",
                            LostTalesQuestWords.titleComponent(quest));
                    continue;
                }
                // The items are gone, so the objective is done and the
                // client is told, whatever the stored value was.
                setObjectiveProgressAndEvaluate(player, quest, objective,
                        LostTalesQuestObjectiveTextHelper.getObjectiveTargetCount(objective));
                changed = true;
            }
        }
        if (changed) {
            syncToClient(player);
        }
        return answered;
    }

    /**
     * Takes what each of {@code objectives} asks for out of the player's
     * inventory and cursor, all or nothing ({@link GatherCount#take}), and
     * tells the player's client. Never called inside an inventory's own
     * handling of a click: a craft is counted there.
     */
    private static GatherCount.Taking handOver(EntityPlayerMP player,
            List<LostTalesQuestObjectiveDefinition> objectives) {
        GatherCount.Check[] checks = new GatherCount.Check[objectives.size()];
        int[] amounts = new int[objectives.size()];
        for (int index = 0; index < checks.length; index++) {
            final LostTalesQuestObjectiveDefinition objective = objectives.get(index);
            checks[index] = new GatherCount.Check() {
                @Override
                public boolean matches(ItemStack stack) {
                    return LostTalesQuestObjectiveMatcher.matchesItem(stack, objective);
                }
            };
            amounts[index] = LostTalesQuestObjectiveTextHelper
                    .getObjectiveTargetCount(objective);
        }
        GatherCount.Taking taking = GatherCount.take(
                player.inventory.mainInventory, player.inventory.getItemStack(),
                checks, amounts);
        if (!taking.whole) {
            return taking;
        }
        ItemStack[] inventory = player.inventory.mainInventory;
        for (int slot = 0; slot < inventory.length; slot++) {
            if (!ItemStack.areItemStacksEqual(inventory[slot],
                    taking.inventory[slot])) {
                inventory[slot] = taking.inventory[slot];
            }
        }
        if (!ItemStack.areItemStacksEqual(player.inventory.getItemStack(),
                taking.cursor)) {
            player.inventory.setItemStack(taking.cursor);
            player.updateHeldItem();
        }
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
        if (player.openContainer != null
                && player.openContainer != player.inventoryContainer) {
            player.openContainer.detectAndSendChanges();
        }
        return taking;
    }

    /**
     * The player made something: every craft objective naming it counts
     * what was made, and every gather objective naming it counts again
     * what is held, the new items included.
     */
    public static void handleItemCrafted(EntityPlayerMP player, ItemStack crafted) {
        if (player == null || crafted == null || crafted.getItem() == null || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }
        boolean changed = countGathered(player, crafted);
        int amount = Math.max(1, crafted.stackSize);
        for (LostTalesQuestProgress progress : getActiveQuests(player)) {
            LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(progress.getQuestId());
            if (quest == null) {
                continue;
            }

            for (LostTalesQuestObjectiveDefinition objective
                    : LostTalesQuestObjectiveSelection
                    .getProgressibleObjectives(quest, progress)) {
                if (!LostTalesQuestObjectiveType.CRAFT.is(objective)) {
                    continue;
                }
                if (!LostTalesQuestObjectiveMatcher.matchesItem(crafted, objective)) {
                    continue;
                }
                changed |= addObjectiveProgressAndEvaluate(player, quest, objective, amount);
            }
        }
        if (changed) {
            syncToClient(player);
        }
    }

    public static void handlePlayerTick(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }

        boolean changed = failExpiredQuests(player);
        if (shouldScanMarkerDiscovery(player)) {
            changed |= discoverNearbyMapMarkers(player, false);
        }
        changed |= ensureLotrWaypointsForDiscoveredMapMarkers(player);
        if (changed) {
            syncToClient(player);
        }
    }

    public static boolean handleTravelProgress(EntityPlayerMP player,
            Entity source, boolean shared) {
        if (player == null || source == null || player.worldObj == null
                || player.worldObj.isRemote || source.worldObj == null
                || source.worldObj.provider.dimensionId
                != player.worldObj.provider.dimensionId) {
            return false;
        }
        boolean changed = false;
        for (LostTalesQuestProgress progress : getActiveQuests(player)) {
            LostTalesQuestDefinition quest =
                    LostTalesQuestRegistry.getQuest(progress.getQuestId());
            if (quest == null) {
                continue;
            }
            for (LostTalesQuestObjectiveDefinition objective
                    : LostTalesQuestObjectiveSelection
                    .getProgressibleObjectives(quest, progress)) {
                if (!isGotoObjective(objective)
                        || shared && !allowsFellowshipSharing(objective)) {
                    continue;
                }
                if (isAtObjectiveLocation(player, source, objective)) {
                    changed |= setObjectiveProgressAndEvaluate(
                            player, quest, objective, 1);
                }
            }
        }
        if (changed) {
            syncToClient(player);
        }
        return changed;
    }

    public static boolean failExpiredQuests(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return false;
        }
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null) {
            return false;
        }

        long worldTime = player.worldObj.getTotalWorldTime();
        boolean changed = false;
        for (LostTalesQuestProgress progress : getActiveQuests(player)) {
            if (progress == null || !progress.isExpired(worldTime)) {
                continue;
            }
            String questId = progress.getQuestId();
            if (data.failQuest(questId, "gui.losttales.quest.reason.expired", worldTime)) {
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Checks the quest log again after login, respawn or a change of
     * world, and sends it whole once: stale references pruned, missives
     * past what the log keeps forgotten, expired quests failed, gather
     * objectives counted again.
     */
    public static void refreshPlayerState(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        boolean changed = data != null && data.pruneInvalidReferences();
        changed |= data != null && data.forgetEndedMissives();
        changed |= failExpiredQuests(player);
        for (LostTalesQuestProgress progress : getActiveQuests(player)) {
            LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(progress.getQuestId());
            if (quest != null) {
                changed |= scanProgressibleGatherObjectives(player, quest);
            }
        }
        changed |= discoverNearbyMapMarkers(player, false);
        changed |= ensureLotrWaypointsForDiscoveredMapMarkers(player);
        if (changed) {
            for (LostTalesQuestProgress progress : getActiveQuests(player)) {
                evaluateStageProgress(player, progress.getQuestId());
            }
        }
        syncToClient(player);
    }

    public static boolean ensureLotrWaypointsForDiscoveredMapMarkers(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return false;
        }
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null) {
            return false;
        }

        boolean changed =
                LostTalesMapMarkerWaypointUnlockHelper.reconcileBundledWaypointRegions(
                        player, data.getDiscoveredMarkerIds());
        for (String markerId : data.getDiscoveredMarkerIds()) {
            LostTalesMapMarkerDefinition marker =
                    data.getDynamicMapMarker(markerId);
            if (marker == null) {
                LostTalesMapMarkerRecord record =
                        LostTalesMapMarkerStorage.get(player.worldObj)
                                .getRecord(markerId);
                marker = record != null
                        ? record.toDefinition()
                        : LostTalesMapMarkerCatalog.getMarker(markerId);
            }
            changed |= LostTalesMapMarkerWaypointUnlockHelper
                    .unlockWaypointForDiscoveredMarker(player, marker);
        }
        return changed;
    }

    public static boolean discoverNearbyMapMarkers(EntityPlayerMP player, boolean notifyVisibleMarkers) {
        if (!LostTalesConfig.autoDiscoverNearbyMapMarkers || player == null || player.worldObj == null || player.worldObj.isRemote) {
            return false;
        }
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null) {
            return false;
        }
        boolean changed = false;
        int playerDimension = player.worldObj.provider.dimensionId;
        LostTalesMapMarkerWorldData markerData =
                LostTalesMapMarkerStorage.get(player.worldObj);
        for (LostTalesMapMarkerRecord record
                : markerData.getDiscoveryCandidates(
                        playerDimension, player.posX, player.posZ)) {
            if (record == null
                    || !LostTalesMapMarkerVisibilityPolicy.canView(
                            record, player)
                    || !record.isDiscoverable()
                    || data.isMarkerDiscovered(record.getId())) {
                continue;
            }
            double radius = Math.max(
                    1.0D, record.getDiscoveryRadius());
            double dx = player.posX - record.getX();
            double markerY = record.getEffectiveY(
                    player.worldObj, player.posY);
            double dy = player.posY - markerY;
            double dz = player.posZ - record.getZ();
            if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                if (data.discoverMarker(record.getId())) {
                    changed = true;
                    LostTalesMapMarkerDefinition marker =
                            record.toDefinition();
                    addLotrWaypointForDiscoveredMarker(player, marker);
                    sendMapMarkerDiscoveryNotification(player, marker);
                }
            }
        }
        return changed;
    }

    private static void sendMapMarkerDiscoveryNotification(EntityPlayerMP player, LostTalesMapMarkerDefinition marker) {
        if (player == null || marker == null || !marker.isDiscoverable()) {
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(new LostTalesMapMarkerDiscoveryPacket(marker.getId(), marker.getName()), player);
    }

    private static boolean shouldScanMarkerDiscovery(EntityPlayerMP player) {
        if (!LostTalesConfig.autoDiscoverNearbyMapMarkers || player == null) {
            return false;
        }
        int interval = Math.max(20, LostTalesConfig.mapMarkerDiscoveryScanIntervalTicks);
        return player.ticksExisted % interval == 0;
    }

    private static boolean addObjectiveProgressAndEvaluate(EntityPlayerMP player, LostTalesQuestDefinition quest, LostTalesQuestObjectiveDefinition objective, int amount) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null || quest == null || objective == null) {
            return false;
        }

        int target = LostTalesQuestObjectiveTextHelper.getObjectiveTargetCount(objective);
        int before = data.getObjectiveProgress(quest.getId(), objective.getId());
        if (before >= target) {
            return false;
        }

        int now = data.addObjectiveProgress(quest.getId(), objective.getId(), Math.max(1, amount), target);
        boolean changed = now != before;
        if (changed) {
            evaluateStageProgress(player, quest.getId());
        }
        return changed;
    }

    private static boolean setObjectiveProgressAndEvaluate(EntityPlayerMP player, LostTalesQuestDefinition quest, LostTalesQuestObjectiveDefinition objective, int value) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null || quest == null || objective == null) {
            return false;
        }

        int before = data.getObjectiveProgress(quest.getId(), objective.getId());
        int target = LostTalesQuestObjectiveTextHelper.getObjectiveTargetCount(objective);
        int clamped = Math.min(value, target);
        if (before == clamped) {
            return false;
        }

        data.setObjectiveProgress(quest.getId(), objective.getId(), clamped);
        evaluateStageProgress(player, quest.getId());
        return true;
    }

    /**
     * Hands over what the stages marked due ask for, from the player's
     * tick, outside any inventory's own handling of a click: each quest
     * whose stage is still full gives its taking gather objectives' items
     * and moves on. Where the items are no longer all there, each such
     * objective counts what was there and the player is told once.
     */
    public static void settleHandOvers(EntityPlayerMP player) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null || !data.hasHandOversDue()) {
            return;
        }
        boolean changed = false;
        for (String questId : data.takeHandOversDue()) {
            changed |= advance(player, questId, true);
        }
        if (changed) {
            syncToClient(player);
        }
    }

    /**
     * Moves the quest on once every required objective of its stage is
     * full: to the next stage, or done. Answers whether anything changed.
     * A stage with gather objectives that take only marks its hand-over
     * due ({@link #settleHandOvers}).
     */
    public static boolean evaluateStageProgress(EntityPlayerMP player, String questId) {
        return advance(player, questId, false);
    }

    /**
     * {@link #evaluateStageProgress}, handing over the taking gather
     * objectives' items first where {@code mayTake} says the player's
     * inventory may be changed now.
     */
    private static boolean advance(EntityPlayerMP player, String questId,
                                   boolean mayTake) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(questId);
        LostTalesQuestProgress progress = data == null ? null : data.getActiveQuest(questId);
        LostTalesQuestStageDefinition stage = LostTalesQuestObjectiveSelection
                .getCurrentStage(quest, progress);
        if (data == null || quest == null || progress == null || stage == null) {
            return false;
        }

        for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
            if (objective.isOptional()) {
                continue;
            }
            int target = LostTalesQuestObjectiveTextHelper.getObjectiveTargetCount(objective);
            int current = progress.getObjectiveProgress(objective.getId());
            if (current < target) {
                return false;
            }
        }

        List<LostTalesQuestObjectiveDefinition> taking =
                new ArrayList<LostTalesQuestObjectiveDefinition>();
        for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
            if (GatherCount.takes(objective)) {
                taking.add(objective);
            }
        }
        if (!taking.isEmpty()) {
            if (!mayTake) {
                data.markHandOverDue(questId);
                return false;
            }
            GatherCount.Taking taken = handOver(player, taking);
            if (!taken.whole) {
                for (int index = 0; index < taking.size(); index++) {
                    data.setObjectiveProgress(questId, taking.get(index).getId(),
                            taken.got[index]);
                }
                sendQuestChat(player, "chat.losttales.quest.missing_items",
                        LostTalesQuestWords.titleComponent(quest));
                return true;
            }
        }

        List<LostTalesQuestStageDefinition> stages = quest.getStages();
        int stageIndex = LostTalesQuestObjectiveSelection
                .getCurrentStageIndex(quest, progress);
        int nextStageIndex = stageIndex + 1;
        if (nextStageIndex >= 0 && nextStageIndex < stages.size()) {
            LostTalesQuestStageDefinition nextStage = stages.get(nextStageIndex);
            boolean changed = data.setQuestStage(questId, nextStageIndex, nextStage.getId());
            if (changed) {
                if (scanProgressibleGatherObjectives(player, quest)) {
                    evaluateStageProgress(player, questId);
                }
            }
            return changed;
        }

        LostTalesQuestProgress completedProgress = progress.copy();
        long worldTime = player.worldObj == null ? 0L
                : player.worldObj.getTotalWorldTime();
        boolean completed = data.completeQuest(questId,
                buildCompletionOutcome(quest, completedProgress), worldTime,
                getCompletedOptionalObjectiveIds(quest,
                        completedProgress));
        if (completed) {
            revealQuestMarkers(player, quest, false);
            grantQuestRewards(player, quest, completedProgress);
        }
        return completed;
    }

    private static boolean revealQuestMarkers(EntityPlayer player, LostTalesQuestDefinition quest, boolean notify) {
        if (player == null || quest == null || quest.getMarkers().isEmpty()) {
            return false;
        }
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null) {
            return false;
        }

        boolean changed = false;
        IChatComponent revealed = new ChatComponentText("");
        for (String markerId : LostTalesQuestMarkerHelper.collectStaticQuestMarkerIds(quest)) {
            if (data.discoverMarker(markerId)) {
                addLotrWaypointForDiscoveredMarker(player, markerId);
                appendMarkerDisplay(revealed, markerId);
                changed = true;
            }
        }
        for (String markerId : LostTalesQuestMarkerHelper.collectDynamicQuestGiverMarkerIds(quest)) {
            LostTalesMapMarkerDefinition dynamicMarker = data.getDynamicMapMarker(markerId);
            if (dynamicMarker != null && data.discoverMarker(markerId)) {
                addLotrWaypointForDiscoveredMarker(player, dynamicMarker);
                appendMarkerDisplay(revealed, markerId);
                changed = true;
            }
        }

        if (changed && notify) {
            sendQuestChat(player, "chat.losttales.quest.note.hints", revealed);
        }
        return changed;
    }


    /** Adds a marker by its id and name, each reader's game wording the name. */
    private static void appendMarkerDisplay(IChatComponent revealed, String markerId) {
        if (revealed == null) {
            return;
        }
        if (!revealed.getSiblings().isEmpty()) {
            revealed.appendSibling(new ChatComponentText(", "));
        }
        revealed.appendSibling(LostTalesMapMarkerCatalog.getDisplayName(markerId));
    }

    /**
     * A giver's own name: the name tag someone gave it, else the personal
     * name LOTR gave an NPC; empty for a giver known only by its kind,
     * which its marker is called after so each game names it.
     */
    private static String getEntityMarkerName(Entity entity) {
        if (entity instanceof EntityLiving) {
            try {
                EntityLiving living = (EntityLiving)entity;
                if (living.hasCustomNameTag()) {
                    return living.getCustomNameTag().trim();
                }
            } catch (RuntimeException otherModsName) {
                // Another mod's creature may fail to say its name tag.
            }
        }
        return LotrNpcNames.personalName(entity);
    }

    /**
     * A quest giver's marker: called by {@code name} where the giver has
     * one of its own, else after its kind or its block ({@code namedAfter}).
     */
    private static LostTalesMapMarkerDefinition giverMarker(String markerId,
            String name, String namedAfter, String color, int dimensionId,
            double x, double y, double z) {
        return new LostTalesMapMarkerDefinition(markerId, name, "quest", color,
                "", "", false, dimensionId, x, y, z, 128.0D, 8.0D, true, true,
                false, LostTalesMapMarkerSource.QUEST_DYNAMIC, false, "", 0,
                namedAfter);
    }

    private static void grantQuestRewards(EntityPlayer player,
            LostTalesQuestDefinition quest,
            LostTalesQuestProgress progress) {
        if (!LostTalesConfig.enableQuestRewards
                || !(player instanceof EntityPlayerMP) || quest == null) {
            return;
        }
        LostTalesQuestRewardHelper.grantRewards(
                (EntityPlayerMP) player, quest, progress);
    }

    /**
     * A finished quest's outcome line for the History: the {@code outcome}
     * each optional objective it achieved writes, in order. An optional
     * objective with no outcome adds nothing; the journal ticks it among
     * the quest's objectives.
     */
    private static String buildCompletionOutcome(
            LostTalesQuestDefinition quest,
            LostTalesQuestProgress progress) {
        if (quest == null || progress == null) {
            return "";
        }
        StringBuilder outcome = new StringBuilder();
        for (LostTalesQuestStageDefinition stage : quest.getStages()) {
            for (LostTalesQuestObjectiveDefinition objective
                    : stage.getObjectives()) {
                if (!objective.isOptional()
                        || progress.getObjectiveProgress(objective.getId())
                        < LostTalesQuestObjectiveTextHelper.getObjectiveTargetCount(objective)) {
                    continue;
                }
                String detail = LostTalesQuestParams.value(
                        objective.getParams(), "outcome");
                if (detail.length() == 0) {
                    continue;
                }
                if (outcome.length() > 0) {
                    outcome.append(" ");
                }
                outcome.append(detail);
            }
        }
        return outcome.toString();
    }

    private static Set<String> getCompletedOptionalObjectiveIds(
            LostTalesQuestDefinition quest,
            LostTalesQuestProgress progress) {
        LinkedHashSet<String> completed = new LinkedHashSet<String>();
        if (quest == null || progress == null) {
            return completed;
        }
        for (LostTalesQuestStageDefinition stage : quest.getStages()) {
            for (LostTalesQuestObjectiveDefinition objective
                    : stage.getObjectives()) {
                if (objective.isOptional()
                        && progress.getObjectiveProgress(objective.getId())
                        >= LostTalesQuestObjectiveTextHelper.getObjectiveTargetCount(objective)) {
                    completed.add(objective.getId());
                }
            }
        }
        return completed;
    }

    public static void syncToClient(EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            syncToClient((EntityPlayerMP) player);
        }
    }

    /**
     * Sends the player their quest log, the server quests it names first
     * where their client was not sent them yet ({@link ServerQuestSync}).
     */
    public static void syncToClient(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        LotrQuestJournalAdapter.prune(player, data);
        ServerQuestSync.revealLog(player, data);
        LostTalesNetworkHandler.CHANNEL.sendTo(LostTalesQuestSyncPacket.fromPlayerData(data), player);
    }

    private static boolean scanProgressibleGatherObjectives(EntityPlayerMP player, LostTalesQuestDefinition quest) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        LostTalesQuestProgress progress = data == null || quest == null ? null : data.getActiveQuest(quest.getId());
        if (data == null || progress == null) {
            return false;
        }

        boolean changed = false;
        for (LostTalesQuestObjectiveDefinition objective
                : LostTalesQuestObjectiveSelection
                .getProgressibleObjectives(quest, progress)) {
            if (!isGatherObjective(objective)) {
                continue;
            }

            int target = LostTalesQuestObjectiveTextHelper.getObjectiveTargetCount(objective);
            int before = data.getObjectiveProgress(quest.getId(), objective.getId());
            int counted = GatherCount.counted(GatherCount.takes(objective), before,
                    countHeld(player, objective), target);
            if (counted != before) {
                data.setObjectiveProgress(quest.getId(), objective.getId(), counted);
                changed = true;
            }
        }
        return changed;
    }

    /** What the player holds of the objective's items: the main inventory and the cursor ({@link GatherCount}). */
    private static int countHeld(EntityPlayerMP player,
                                 final LostTalesQuestObjectiveDefinition objective) {
        if (player == null || player.inventory == null || objective == null) {
            return 0;
        }
        return GatherCount.held(player.inventory.mainInventory,
                player.inventory.getItemStack(), new GatherCount.Check() {
                    @Override
                    public boolean matches(ItemStack stack) {
                        return LostTalesQuestObjectiveMatcher.matchesItem(stack, objective);
                    }
                });
    }

    private static boolean isGatherObjective(LostTalesQuestObjectiveDefinition objective) {
        return LostTalesQuestObjectiveType.GATHER.is(objective);
    }

    private static boolean isGotoObjective(LostTalesQuestObjectiveDefinition objective) {
        return LostTalesQuestObjectiveType.GOTO.is(objective);
    }

    private static boolean isAtObjectiveLocation(EntityPlayerMP player,
            Entity source, LostTalesQuestObjectiveDefinition objective) {
        if (player == null || source == null || source.worldObj == null) {
            return false;
        }
        Map<String, String> params = objective.getParams();
        LostTalesMapMarkerDefinition marker = getObjectiveLocationMarker(player, objective);
        LostTalesQuestParams.Location location = marker != null ? null
                : LostTalesQuestParams.location(params, source.worldObj.provider.dimensionId);
        if (marker == null && location == null) {
            return false;
        }

        double x = marker != null ? marker.getX() : location.getX();
        double y = marker != null ? marker.getY() : location.getY();
        double z = marker != null ? marker.getZ() : location.getZ();
        double radius = Math.max(0.5D, LostTalesQuestParams.parseDouble(params.get("radius"), 3.0D));
        int targetDimension = marker != null ? marker.getDimensionId() : location.getDimensionId();
        if (source.worldObj.provider.dimensionId != targetDimension) {
            return false;
        }

        double dx = source.posX - x;
        double dy = source.posY - y;
        double dz = source.posZ - z;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    /** Whether a fellowship member's kill or arrival counts for this objective: yes, unless its {@code fellowshipShared} says false. */
    private static boolean allowsFellowshipSharing(
            LostTalesQuestObjectiveDefinition objective) {
        if (objective == null) {
            return false;
        }
        String value = LostTalesQuestParams.value(objective.getParams(),
                "fellowshipShared");
        return value.length() == 0 || Boolean.parseBoolean(value);
    }

    private static LostTalesMapMarkerDefinition getObjectiveLocationMarker(EntityPlayerMP player, LostTalesQuestObjectiveDefinition objective) {
        if (player == null || objective == null) {
            return null;
        }
        String markerId = LostTalesQuestMarkerHelper.normalizeMarkerId(
                objective.getParam("marker", ""));
        if (markerId.length() == 0) {
            return null;
        }

        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        LostTalesMapMarkerDefinition dynamic = data == null ? null : data.getDynamicMapMarker(markerId);
        if (dynamic != null) {
            return dynamic;
        }
        return LostTalesMapMarkerCatalog.getMarker(markerId);
    }

    private static boolean isWithinObjectiveRadius(EntityPlayerMP player, Entity victim, LostTalesQuestObjectiveDefinition objective) {
        double radius = LostTalesQuestParams.parseDouble(objective.getParam("radius", "0"), 0.0D);
        if (radius <= 0.0D || player == null || victim == null) {
            return true;
        }
        if (player.worldObj == null || victim.worldObj == null || player.worldObj.provider.dimensionId != victim.worldObj.provider.dimensionId) {
            return false;
        }
        double dx = player.posX - victim.posX;
        double dy = player.posY - victim.posY;
        double dz = player.posZ - victim.posZ;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    /**
     * A quest as a chat line names it: its title, each reader's game
     * wording a bundled quest's and a missive's, or "a Middle-earth quest"
     * for one of LOTR's.
     */
    private static Object questTitle(String questId) {
        if (LotrQuestReference.isLotrQuest(questId)) {
            return new ChatComponentTranslation(
                    "chat.losttales.quest.middle_earth_quest");
        }
        LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(questId);
        return quest == null ? questId : LostTalesQuestWords.titleComponent(quest);
    }

    private static void addLotrWaypointForDiscoveredMarker(EntityPlayer player, String markerId) {
        addLotrWaypointForDiscoveredMarker(player, LostTalesMapMarkerCatalog.getMarker(markerId));
    }

    private static void addLotrWaypointForDiscoveredMarker(EntityPlayer player, LostTalesMapMarkerDefinition marker) {
        if (player instanceof EntityPlayerMP) {
            LostTalesMapMarkerWaypointUnlockHelper.unlockWaypointForDiscoveredMarker((EntityPlayerMP) player, marker);
        }
    }

    /**
     * A quest's word to one player, said by the Server in their Client
     * Console. Whether a {@code chat.losttales.quest.note.*} line shows is
     * the player's own setting, read on their side; refusals always do.
     */
    private static void sendQuestChat(EntityPlayer player, String key,
                                      Object... args) {
        if (player == null || player.worldObj == null
                || player.worldObj.isRemote) {
            return;
        }
        player.addChatMessage(new ChatComponentTranslation(key, args));
    }

}
