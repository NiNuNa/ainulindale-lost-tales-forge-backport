package com.ninuna.losttales.quest;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerCatalog;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesMapMarkerDiscoveryPacket;
import com.ninuna.losttales.network.packet.LostTalesQuestSyncPacket;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import com.ninuna.losttales.util.LostTalesDimensionHelper;
import com.ninuna.losttales.world.map.waypoint.LostTalesMapMarkerWaypointUnlockHelper;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
/** Server-side helper methods for basic quest state changes and objective progress. */
public final class LostTalesQuestManager {

    private LostTalesQuestManager() {}

    public static LostTalesQuestPlayerData getPlayerData(EntityPlayer player) {
        return LostTalesQuestPlayerData.get(player);
    }

    public static StartResult startQuest(EntityPlayer player, String questId) {
        return startQuest(player, questId, LostTalesQuestStartSource.COMMAND);
    }

    /**
     * Registers, persists, and starts a server-authored runtime quest.
     *
     * Generated missives use locked start mode so clients cannot start them directly
     * from the journal or from forged item NBT. This method is intended for trusted
     * server-side acceptance paths, such as a validated missive board slot.
     */
    public static StartResult startGeneratedQuest(EntityPlayer player, LostTalesQuestDefinition quest) {
        return startGeneratedQuest(player, quest, 0L);
    }

    public static StartResult startGeneratedQuest(EntityPlayer player, LostTalesQuestDefinition quest, long timeLimitTicks) {
        if (quest == null || quest.getId() == null || quest.getId().length() == 0) {
            return StartResult.UNKNOWN_QUEST;
        }
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null) {
            sendQuestChat(player, EnumChatFormatting.RED + "Quest data is not available.");
            return StartResult.NO_PLAYER_DATA;
        }
        data.rememberDynamicQuestDefinition(quest);
        LostTalesQuestRegistry.registerRuntimeQuest(quest);
        return startQuestInternal(player, quest.getId(), LostTalesQuestStartSource.COMMAND, Math.max(0L, timeLimitTicks));
    }

    public static StartResult startQuest(EntityPlayer player, String questId, LostTalesQuestStartSource source) {
        return startQuestInternal(player, questId, source, 0L);
    }

    private static StartResult startQuestInternal(EntityPlayer player, String questId, LostTalesQuestStartSource source, long timeLimitTicks) {
        LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(questId);
        if (quest == null) {
            sendQuestChat(player, EnumChatFormatting.RED + "Unknown quest: " + questId);
            return StartResult.UNKNOWN_QUEST;
        }

        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null) {
            sendQuestChat(player, EnumChatFormatting.RED + "Quest data is not available.");
            return StartResult.NO_PLAYER_DATA;
        }
        if (data.isQuestActive(questId)) {
            sendQuestChat(player, EnumChatFormatting.YELLOW + "Quest already active: " + EnumChatFormatting.WHITE + quest.getTitle());
            return StartResult.ALREADY_ACTIVE;
        }
        if (data.isQuestCompleted(questId) && !quest.isRepeatable()) {
            sendQuestChat(player, EnumChatFormatting.YELLOW + "Quest already completed: " + EnumChatFormatting.WHITE + quest.getTitle());
            return StartResult.ALREADY_COMPLETED;
        }
        if (!canStartFromSource(quest, source)) {
            sendQuestChat(player, EnumChatFormatting.RED + "This quest cannot be started from here.");
            return StartResult.START_NOT_ALLOWED;
        }
        if (LostTalesConfig.enableQuestPrerequisites) {
            String failureReason = LostTalesQuestPrerequisiteHelper.getFailureReason(quest, player, data);
            if (failureReason != null) {
                sendQuestChat(player, EnumChatFormatting.RED + failureReason);
                return StartResult.REQUIREMENTS_NOT_MET;
            }
        }

        LostTalesQuestStageDefinition firstStage = quest.getFirstStage();
        long acceptedWorldTime = player != null && player.worldObj != null ? player.worldObj.getTotalWorldTime() : 0L;
        long deadlineWorldTime = timeLimitTicks > 0L ? acceptedWorldTime + timeLimitTicks : 0L;
        data.startQuest(questId, firstStage == null ? "" : firstStage.getId(), acceptedWorldTime, deadlineWorldTime);
        if (LostTalesConfig.autoPinQuestOnStart && !data.isQuestPinned(questId)) {
            data.pinQuestId(questId);
        }
        if (LostTalesConfig.autoRevealQuestMarkersOnStart) {
            revealQuestMarkers(player, quest, false);
        }
        sendQuestChat(player, EnumChatFormatting.GOLD + "Quest started: " + EnumChatFormatting.YELLOW + quest.getTitle());
        if (timeLimitTicks > 0L) {
            sendQuestChat(player, EnumChatFormatting.RED + "Time limit: " + formatTicks(timeLimitTicks) + ".");
        }
        playQuestSound(player, "random.orb", 0.35F, 1.0F);

        if (player instanceof EntityPlayerMP) {
            EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
            if (scanCurrentStageGatherObjectives(serverPlayer, quest)) {
                evaluateStageProgress(serverPlayer, questId);
            } else if (quest.getStages().isEmpty() || firstStage == null || firstStage.getObjectives().isEmpty()) {
                evaluateStageProgress(serverPlayer, questId);
            }
        }

        syncToClient(player);
        return StartResult.STARTED;
    }

    public static StartResult startQuestFromItem(EntityPlayerMP player, ItemStack stack) {
        if (player == null || stack == null || !LostTalesConfig.allowQuestItemStarts || !stack.hasTagCompound()) {
            return StartResult.START_NOT_ALLOWED;
        }

        NBTTagCompound tag = stack.getTagCompound();
        String questId = tag.getString("LostTalesQuestId");
        if (questId == null || questId.length() == 0) {
            return StartResult.UNKNOWN_QUEST;
        }

        StartResult result = startQuest(player, questId, LostTalesQuestStartSource.ITEM);
        if (result == StartResult.STARTED && tag.getBoolean("LostTalesQuestConsume") && !player.capabilities.isCreativeMode) {
            stack.stackSize--;
            if (stack.stackSize <= 0) {
                player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            }
            player.inventory.markDirty();
        }
        return result;
    }

    public static boolean completeQuest(EntityPlayer player, String questId) {
        LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(questId);
        if (quest == null) {
            return false;
        }
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        boolean changed = data != null && data.completeQuest(questId);
        if (changed) {
            revealQuestMarkers(player, quest, false);
            grantQuestRewards(player, quest);
            sendQuestChat(player, EnumChatFormatting.GREEN + "Quest completed: " + EnumChatFormatting.YELLOW + quest.getTitle());
            playQuestSound(player, "random.levelup", 0.45F, 1.0F);
            syncToClient(player);
        }
        return changed;
    }

    public static boolean resetQuest(EntityPlayer player, String questId) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        boolean changed = data != null && data.resetQuest(questId);
        if (changed) {
            sendQuestChat(player, EnumChatFormatting.YELLOW + "Quest reset: " + questTitle(questId));
            syncToClient(player);
        }
        return changed;
    }

    public static boolean abandonQuest(EntityPlayer player, String questId) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        boolean changed = data != null && data.abandonQuest(questId);
        if (changed) {
            sendQuestChat(player, EnumChatFormatting.YELLOW + "Quest abandoned: " + questTitle(questId));
            syncToClient(player);
        }
        return changed;
    }


    public static boolean pinQuest(EntityPlayer player, String questId) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null) {
            return false;
        }
        boolean changed = data.setPinnedQuestId(questId);
        if (changed) {
            sendQuestChat(player, EnumChatFormatting.AQUA + "Tracking quest: " + questTitle(questId));
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
            sendQuestChat(player, EnumChatFormatting.AQUA + "Stopped tracking all quests.");
            syncToClient(player);
        }
        return changed;
    }

    public static boolean unpinQuest(EntityPlayer player, String questId) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null || questId == null || questId.length() == 0) {
            return false;
        }
        boolean changed = data.unpinQuestId(questId);
        if (changed) {
            sendQuestChat(player, EnumChatFormatting.AQUA + "Stopped tracking quest: " + questTitle(questId));
            syncToClient(player);
        }
        return changed;
    }

    public static String getPinnedQuestId(EntityPlayer player) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        return data == null ? "" : data.getPinnedQuestId();
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
            sendQuestChat(player, EnumChatFormatting.RED + "Unknown quest: " + questId);
            return false;
        }
        boolean changed = revealQuestMarkers(player, quest, true);
        if (changed) {
            syncToClient(player);
        }
        return changed;
    }

    public static boolean revealMapMarker(EntityPlayer player, String markerId) {
        if (!LostTalesConfig.enableQuestMarkerDiscovery) {
            return false;
        }
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
        if (!LostTalesConfig.enableQuestMarkerDiscovery || player == null || quest == null || target == null || player.worldObj == null) {
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

        boolean changed = false;
        for (String markerId : markerIds) {
            LostTalesMapMarkerDefinition marker = new LostTalesMapMarkerDefinition(
                    markerId,
                    getEntityMarkerName(target, quest),
                    "quest",
                    "blue",
                    target.worldObj == null ? player.worldObj.provider.dimensionId : target.worldObj.provider.dimensionId,
                    target.posX,
                    target.posY,
                    target.posZ,
                    true
            );
            changed |= data.discoverDynamicMarker(marker);
        }
        if (changed) {
            sendQuestChat(player, EnumChatFormatting.AQUA + "Quest giver marker discovered: " + EnumChatFormatting.WHITE + getEntityMarkerName(target, quest));
            if (sync) {
                syncToClient(player);
            }
        }
        return changed;
    }

    public static boolean revealQuestGiverMarker(EntityPlayer player, LostTalesQuestDefinition quest, Block block, int x, int y, int z, boolean sync) {
        if (!LostTalesConfig.enableQuestMarkerDiscovery || player == null || quest == null || block == null || player.worldObj == null) {
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

        String name = getBlockMarkerName(block, quest);
        boolean changed = false;
        for (String markerId : markerIds) {
            LostTalesMapMarkerDefinition marker = new LostTalesMapMarkerDefinition(
                    markerId,
                    name,
                    "quest",
                    "yellow",
                    player.worldObj.provider.dimensionId,
                    x + 0.5D,
                    y + 0.5D,
                    z + 0.5D,
                    true
            );
            changed |= data.discoverDynamicMarker(marker);
        }
        if (changed) {
            sendQuestChat(player, EnumChatFormatting.AQUA + "Quest marker discovered: " + EnumChatFormatting.WHITE + name);
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
            LostTalesMapMarkerWaypointUnlockHelper.lockWaypointForForgottenMarker(player, markerId);
            sendQuestChat(player, EnumChatFormatting.YELLOW + "Map marker forgotten: " + EnumChatFormatting.WHITE + markerId);
            syncToClient(player);
        }
        return changed;
    }

    public static boolean pinMapMarker(EntityPlayer player, String markerId) {
        if (!LostTalesConfig.enableQuestMarkerDiscovery) {
            return false;
        }
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
                sendQuestChat(player, EnumChatFormatting.YELLOW + "Map marker is not discovered yet: " + markerId);
                return false;
            }
        }
        if (bundledMarker == null && !knownDynamicMarker) {
            sendQuestChat(player, EnumChatFormatting.YELLOW + "Tracking experimental marker id: " + markerId);
        }
        boolean changed = data.setPinnedMapMarkerId(markerId);
        if (changed) {
            sendQuestChat(player, EnumChatFormatting.AQUA + "Tracking map marker: " + markerId);
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
            sendQuestChat(player, EnumChatFormatting.AQUA + "Stopped tracking map marker.");
            syncToClient(player);
        }
        return changed;
    }

    /**
     * Recount gather/pickup objectives from the player's current inventory.
     * This is useful after starting a quest when the player already has the requested item.
     */
    public static boolean refreshGatherProgressFromInventory(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return false;
        }

        boolean changed = false;
        for (LostTalesQuestProgress progress : getActiveQuests(player)) {
            LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(progress.getQuestId());
            if (quest != null) {
                boolean questChanged = scanCurrentStageGatherObjectives(player, quest);
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

    public static void handleEntityKilled(EntityPlayerMP player, Entity victim) {
        if (player == null || victim == null || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }

        boolean changed = false;
        for (LostTalesQuestProgress progress : getActiveQuests(player)) {
            LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(progress.getQuestId());
            LostTalesQuestStageDefinition stage = getCurrentStage(quest, progress);
            if (quest == null || stage == null) {
                continue;
            }

            for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
                if (!"kill".equalsIgnoreCase(objective.getType())) {
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

    public static void handleItemPickedUp(EntityPlayerMP player, ItemStack pickedUp) {
        if (player == null || pickedUp == null || pickedUp.getItem() == null || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }

        boolean changed = false;
        int amount = Math.max(1, pickedUp.stackSize);
        for (LostTalesQuestProgress progress : getActiveQuests(player)) {
            LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(progress.getQuestId());
            LostTalesQuestStageDefinition stage = getCurrentStage(quest, progress);
            if (quest == null || stage == null) {
                continue;
            }

            for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
                if (!isGatherObjective(objective)) {
                    continue;
                }
                if (!LostTalesQuestObjectiveMatcher.matchesItem(pickedUp, objective)) {
                    continue;
                }
                changed |= addObjectiveProgressAndEvaluate(player, quest, objective, amount);
            }
        }
        if (changed) {
            syncToClient(player);
        }
    }

    public static void handleItemCrafted(EntityPlayerMP player, ItemStack crafted) {
        if (player == null || crafted == null || crafted.getItem() == null || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }

        boolean changed = false;
        int amount = Math.max(1, crafted.stackSize);
        for (LostTalesQuestProgress progress : getActiveQuests(player)) {
            LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(progress.getQuestId());
            LostTalesQuestStageDefinition stage = getCurrentStage(quest, progress);
            if (quest == null || stage == null) {
                continue;
            }

            for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
                if (!"craft".equalsIgnoreCase(objective.getType())) {
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
        for (LostTalesQuestProgress progress : getActiveQuests(player)) {
            LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(progress.getQuestId());
            LostTalesQuestStageDefinition stage = getCurrentStage(quest, progress);
            if (quest == null || stage == null) {
                continue;
            }

            for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
                if (!isGotoObjective(objective)) {
                    continue;
                }
                if (isAtObjectiveLocation(player, objective)) {
                    changed |= setObjectiveProgressAndEvaluate(player, quest, objective, 1);
                }
            }
        }
        if (changed) {
            syncToClient(player);
        }
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
            if (data.failQuest(questId)) {
                changed = true;
                sendQuestChat(player, EnumChatFormatting.RED + "Quest failed: " + questTitle(questId));
                playQuestSound(player, "random.break", 0.3F, 0.8F);
            }
        }
        return changed;
    }

    /**
     * Revalidates state after login, respawn, or dimension travel and sends one
     * clean snapshot. Keeping this in the manager avoids packet timing differences
     * between old Forge player events.
     */
    public static void refreshPlayerState(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        boolean changed = data != null && data.pruneInvalidReferences();
        changed |= failExpiredQuests(player);
        for (LostTalesQuestProgress progress : getActiveQuests(player)) {
            LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(progress.getQuestId());
            if (quest != null) {
                changed |= scanCurrentStageGatherObjectives(player, quest);
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
        if (!LostTalesConfig.enableQuestMarkerDiscovery || player == null || player.worldObj == null || player.worldObj.isRemote) {
            return false;
        }
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null) {
            return false;
        }

        boolean changed = false;
        for (String markerId : data.getDiscoveredMarkerIds()) {
            if (LostTalesMapMarkerWaypointUnlockHelper.unlockWaypointForDiscoveredMarker(player, LostTalesMapMarkerCatalog.getMarker(markerId))) {
                changed = true;
            }
            LostTalesMapMarkerDefinition dynamicMarker = data.getDynamicMapMarker(markerId);
            if (LostTalesMapMarkerWaypointUnlockHelper.unlockWaypointForDiscoveredMarker(player, dynamicMarker)) {
                changed = true;
            }
        }
        return changed;
    }

    public static boolean discoverNearbyMapMarkers(EntityPlayerMP player, boolean notifyVisibleMarkers) {
        if (!LostTalesConfig.enableQuestMarkerDiscovery || !LostTalesConfig.autoDiscoverNearbyMapMarkers || player == null || player.worldObj == null || player.worldObj.isRemote) {
            return false;
        }
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null) {
            return false;
        }
        boolean changed = false;
        int playerDimension = player.worldObj.provider.dimensionId;
        for (LostTalesMapMarkerDefinition marker : LostTalesMapMarkerCatalog.getMarkers()) {
            if (marker == null || marker.getId() == null || marker.getId().length() == 0 || marker.getDimensionId() != playerDimension || !marker.isDiscoverable() || data.isMarkerDiscovered(marker.getId())) {
                continue;
            }
            double radius = Math.max(1.0D, marker.getDiscoveryRadius());
            double dx = player.posX - marker.getX();
            double dy = player.posY - marker.getY();
            double dz = player.posZ - marker.getZ();
            if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                if (data.discoverMarker(marker.getId())) {
                    changed = true;
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
        if (!LostTalesConfig.enableQuestMarkerDiscovery || !LostTalesConfig.autoDiscoverNearbyMapMarkers || player == null) {
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

        int target = getObjectiveTargetCount(objective);
        int before = data.getObjectiveProgress(quest.getId(), objective.getId());
        if (target > 0 && before >= target) {
            return false;
        }

        int now = data.addObjectiveProgress(quest.getId(), objective.getId(), Math.max(1, amount), target);
        boolean changed = now != before;
        if (changed) {
            if (before < target && now >= target) {
                sendQuestChat(player, EnumChatFormatting.GREEN + "Objective complete: " + EnumChatFormatting.WHITE + objectiveDescription(objective));
                playQuestSound(player, "random.orb", 0.35F, 1.25F);
            }
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
        int target = getObjectiveTargetCount(objective);
        int clamped = target > 0 ? Math.min(value, target) : value;
        if (before == clamped) {
            return false;
        }

        data.setObjectiveProgress(quest.getId(), objective.getId(), clamped);
        if (before < target && clamped >= target) {
            sendQuestChat(player, EnumChatFormatting.GREEN + "Objective complete: " + EnumChatFormatting.WHITE + objectiveDescription(objective));
            playQuestSound(player, "random.orb", 0.35F, 1.25F);
        }
        evaluateStageProgress(player, quest.getId());
        return true;
    }

    public static boolean evaluateStageProgress(EntityPlayerMP player, String questId) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(questId);
        LostTalesQuestProgress progress = data == null ? null : data.getActiveQuest(questId);
        LostTalesQuestStageDefinition stage = getCurrentStage(quest, progress);
        if (data == null || quest == null || progress == null || stage == null) {
            return false;
        }

        for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
            if (objective.isOptional()) {
                continue;
            }
            int target = getObjectiveTargetCount(objective);
            int current = progress.getObjectiveProgress(objective.getId());
            if (current < target) {
                return false;
            }
        }

        List<LostTalesQuestStageDefinition> stages = quest.getStages();
        int stageIndex = getStageIndex(quest, progress);
        int nextStageIndex = stageIndex + 1;
        if (nextStageIndex >= 0 && nextStageIndex < stages.size()) {
            LostTalesQuestStageDefinition nextStage = stages.get(nextStageIndex);
            boolean changed = data.setQuestStage(questId, nextStageIndex, nextStage.getId());
            if (changed) {
                sendQuestChat(player, EnumChatFormatting.AQUA + "Quest advanced: " + EnumChatFormatting.YELLOW + quest.getTitle());
                playQuestSound(player, "random.orb", 0.35F, 1.05F);
                if (scanCurrentStageGatherObjectives(player, quest)) {
                    evaluateStageProgress(player, questId);
                }
            }
            return changed;
        }

        boolean completed = data.completeQuest(questId);
        if (completed) {
            revealQuestMarkers(player, quest, false);
            grantQuestRewards(player, quest);
            sendQuestChat(player, EnumChatFormatting.GREEN + "Quest completed: " + EnumChatFormatting.YELLOW + quest.getTitle());
            playQuestSound(player, "random.levelup", 0.45F, 1.0F);
        }
        return completed;
    }

    private static boolean revealQuestMarkers(EntityPlayer player, LostTalesQuestDefinition quest, boolean notify) {
        if (!LostTalesConfig.enableQuestMarkerDiscovery || player == null || quest == null || quest.getMarkers().isEmpty()) {
            return false;
        }
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null) {
            return false;
        }

        boolean changed = false;
        StringBuilder revealed = new StringBuilder();
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
            sendQuestChat(player, EnumChatFormatting.AQUA + "Map marker hints revealed: " + EnumChatFormatting.WHITE + revealed.toString());
        }
        return changed;
    }


    private static void appendMarkerDisplay(StringBuilder builder, String markerId) {
        if (builder == null) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(", ");
        }
        builder.append(LostTalesMapMarkerCatalog.getDisplayName(markerId));
    }

    private static String getEntityMarkerName(Entity entity, LostTalesQuestDefinition quest) {
        if (entity != null) {
            try {
                String name = entity.getCommandSenderName();
                if (name != null && name.length() > 0) {
                    return name;
                }
            } catch (RuntimeException ignored) {}
            String entityName = EntityList.getEntityString(entity);
            if (entityName != null && entityName.length() > 0) {
                return entityName;
            }
        }
        return quest == null ? "Quest Giver" : quest.getTitle();
    }

    private static String getBlockMarkerName(Block block, LostTalesQuestDefinition quest) {
        if (block != null) {
            try {
                String name = block.getLocalizedName();
                if (name != null && name.length() > 0) {
                    return name;
                }
            } catch (RuntimeException ignored) {}
            Object registeredName = Block.blockRegistry.getNameForObject(block);
            if (registeredName != null) {
                return registeredName.toString();
            }
        }
        return quest == null ? "Quest Marker" : quest.getTitle();
    }

    private static boolean canStartFromSource(LostTalesQuestDefinition quest, LostTalesQuestStartSource source) {
        if (quest == null) {
            return false;
        }
        if (source == LostTalesQuestStartSource.COMMAND) {
            return true;
        }
        if (source == LostTalesQuestStartSource.JOURNAL) {
            return LostTalesConfig.allowQuestJournalStarts && quest.canStartFromJournal();
        }
        if (source == LostTalesQuestStartSource.ITEM) {
            return LostTalesConfig.allowQuestItemStarts && quest.canStartFromItem();
        }
        if (source == LostTalesQuestStartSource.INTERACTION) {
            return LostTalesConfig.allowQuestInteractionStarts && quest.canStartFromInteraction();
        }
        return false;
    }

    private static void grantQuestRewards(EntityPlayer player, LostTalesQuestDefinition quest) {
        if (!LostTalesConfig.enableQuestRewards || !(player instanceof EntityPlayerMP) || quest == null || quest.getRewards().isEmpty()) {
            return;
        }
        LostTalesQuestRewardHelper.grantRewards((EntityPlayerMP) player, quest);
    }

    public static void syncToClient(EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            syncToClient((EntityPlayerMP) player);
        }
    }

    public static void syncToClient(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        LostTalesNetworkHandler.CHANNEL.sendTo(LostTalesQuestSyncPacket.fromPlayerData(data), player);
    }

    private static boolean scanCurrentStageGatherObjectives(EntityPlayerMP player, LostTalesQuestDefinition quest) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        LostTalesQuestProgress progress = data == null || quest == null ? null : data.getActiveQuest(quest.getId());
        LostTalesQuestStageDefinition stage = getCurrentStage(quest, progress);
        if (data == null || progress == null || stage == null) {
            return false;
        }

        boolean changed = false;
        for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
            if (!isGatherObjective(objective)) {
                continue;
            }

            int target = getObjectiveTargetCount(objective);
            int inventoryCount = Math.min(target, countMatchingInventoryItems(player, objective));
            int before = data.getObjectiveProgress(quest.getId(), objective.getId());
            if (inventoryCount > before) {
                data.setObjectiveProgress(quest.getId(), objective.getId(), inventoryCount);
                changed = true;
                if (before < target && inventoryCount >= target) {
                    sendQuestChat(player, EnumChatFormatting.GREEN + "Objective complete: " + EnumChatFormatting.WHITE + objectiveDescription(objective));
                    playQuestSound(player, "random.orb", 0.35F, 1.25F);
                }
            }
        }
        return changed;
    }

    private static int countMatchingInventoryItems(EntityPlayerMP player, LostTalesQuestObjectiveDefinition objective) {
        if (player == null || player.inventory == null || objective == null) {
            return 0;
        }

        int count = 0;
        ItemStack[] inventory = player.inventory.mainInventory;
        for (ItemStack stack : inventory) {
            if (stack != null && stack.stackSize > 0 && LostTalesQuestObjectiveMatcher.matchesItem(stack, objective)) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    private static LostTalesQuestStageDefinition getCurrentStage(LostTalesQuestDefinition quest, LostTalesQuestProgress progress) {
        if (quest == null || progress == null || quest.getStages().isEmpty()) {
            return null;
        }

        for (LostTalesQuestStageDefinition stage : quest.getStages()) {
            if (stage.getId() != null && stage.getId().equals(progress.getStageId())) {
                return stage;
            }
        }

        int index = progress.getStageIndex();
        if (index >= 0 && index < quest.getStages().size()) {
            return quest.getStages().get(index);
        }
        return quest.getFirstStage();
    }

    private static int getStageIndex(LostTalesQuestDefinition quest, LostTalesQuestProgress progress) {
        if (quest == null || progress == null) {
            return -1;
        }

        List<LostTalesQuestStageDefinition> stages = quest.getStages();
        for (int i = 0; i < stages.size(); i++) {
            LostTalesQuestStageDefinition stage = stages.get(i);
            if (stage.getId() != null && stage.getId().equals(progress.getStageId())) {
                return i;
            }
        }
        return progress.getStageIndex();
    }

    private static boolean isGatherObjective(LostTalesQuestObjectiveDefinition objective) {
        String type = objective.getType();
        return "gather".equalsIgnoreCase(type) || "gather_item".equalsIgnoreCase(type) || "pickup".equalsIgnoreCase(type) || "pickup_item".equalsIgnoreCase(type);
    }

    private static int getObjectiveTargetCount(LostTalesQuestObjectiveDefinition objective) {
        if (objective == null) {
            return 1;
        }
        if (isGotoObjective(objective)) {
            return 1;
        }
        return parseInt(objective.getParam("count", "1"), 1);
    }

    private static boolean isGotoObjective(LostTalesQuestObjectiveDefinition objective) {
        if (objective == null || objective.getType() == null) {
            return false;
        }
        String type = objective.getType();
        return "goto".equalsIgnoreCase(type) || "go_to".equalsIgnoreCase(type) || "travel".equalsIgnoreCase(type) || "location".equalsIgnoreCase(type);
    }

    private static boolean isAtObjectiveLocation(EntityPlayerMP player, LostTalesQuestObjectiveDefinition objective) {
        Map<String, String> params = objective.getParams();
        LostTalesMapMarkerDefinition marker = getObjectiveLocationMarker(player, objective);
        boolean hasExplicitCoordinates = params.containsKey("x") || params.containsKey("y") || params.containsKey("z");

        if (marker == null && !hasExplicitCoordinates) {
            return false;
        }

        double x = marker != null ? marker.getX() : parseDouble(params.get("x"), 0.0D);
        double y = marker != null ? marker.getY() : parseDouble(params.get("y"), 0.0D);
        double z = marker != null ? marker.getZ() : parseDouble(params.get("z"), 0.0D);
        double radius = Math.max(0.5D, parseDouble(params.get("radius"), 3.0D));
        int targetDimension = marker != null ? marker.getDimensionId() : LostTalesDimensionHelper.parseDimensionId(params.get("dimension"), player.worldObj.provider.dimensionId);
        if (player.worldObj.provider.dimensionId != targetDimension) {
            return false;
        }

        double dx = player.posX - x;
        double dy = player.posY - y;
        double dz = player.posZ - z;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    private static LostTalesMapMarkerDefinition getObjectiveLocationMarker(EntityPlayerMP player, LostTalesQuestObjectiveDefinition objective) {
        if (player == null || objective == null) {
            return null;
        }
        String markerId = LostTalesQuestMarkerHelper.normalizeMarkerId(firstNonEmpty(
                objective.getParam("marker", ""),
                objective.getParam("markerId", ""),
                objective.getParam("mapMarker", ""),
                objective.getParam("map_marker", ""),
                objective.getParam("targetMarker", ""),
                objective.getParam("target_marker", "")
        ));
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
        double radius = parseDouble(objective.getParam("radius", "0"), 0.0D);
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

    private static String objectiveDescription(LostTalesQuestObjectiveDefinition objective) {
        if (objective == null) {
            return "objective";
        }
        String description = objective.getDescription();
        return description == null || description.length() == 0 ? objective.getId() : description;
    }

    private static String formatTicks(long ticks) {
        if (ticks >= 24000L && ticks % 24000L == 0L) {
            long days = ticks / 24000L;
            return days + " in-game day" + (days == 1L ? "" : "s");
        }
        if (ticks >= 1200L) {
            long minutes = ticks / 1200L;
            return minutes + " in-game minute" + (minutes == 1L ? "" : "s");
        }
        long seconds = Math.max(1L, ticks / 20L);
        return seconds + " second" + (seconds == 1L ? "" : "s");
    }

    private static String questTitle(String questId) {
        LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(questId);
        return quest == null ? questId : quest.getTitle();
    }

    private static void addLotrWaypointForDiscoveredMarker(EntityPlayer player, String markerId) {
        addLotrWaypointForDiscoveredMarker(player, LostTalesMapMarkerCatalog.getMarker(markerId));
    }

    private static void addLotrWaypointForDiscoveredMarker(EntityPlayer player, LostTalesMapMarkerDefinition marker) {
        if (player instanceof EntityPlayerMP) {
            LostTalesMapMarkerWaypointUnlockHelper.unlockWaypointForDiscoveredMarker((EntityPlayerMP) player, marker);
        }
    }

    private static void sendQuestChat(EntityPlayer player, String message) {
        if (!LostTalesConfig.showQuestChatFeedback || player == null || player.worldObj == null || player.worldObj.isRemote || message == null || message.length() == 0) {
            return;
        }
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.DARK_AQUA + "[Lost Tales] " + EnumChatFormatting.RESET + message));
    }

    private static void playQuestSound(EntityPlayer player, String soundName, float volume, float pitch) {
        if (!LostTalesConfig.playQuestSounds || player == null || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }
        player.worldObj.playSoundAtEntity(player, soundName, volume, pitch);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return "";
    }

    public enum StartResult {
        STARTED,
        UNKNOWN_QUEST,
        NO_PLAYER_DATA,
        ALREADY_ACTIVE,
        ALREADY_COMPLETED,
        START_NOT_ALLOWED,
        REQUIREMENTS_NOT_MET
    }
}
