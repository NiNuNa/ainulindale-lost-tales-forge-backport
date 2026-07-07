package com.ninuna.losttales.command;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestManager;
import com.ninuna.losttales.quest.LostTalesQuestRegistry;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
/**
 * Small server-side debug/admin command for the first quest runtime stage.
 *
 * This is intentionally simple: it lets pack developers verify that quest definitions
 * load on the server and that player quest NBT survives saves before objective logic
 * and client sync are added.
 */
public class LostTalesCommandQuest extends LostTalesCommandBase {

    private final String commandPath;

    public LostTalesCommandQuest() {
        this(LostTalesMetaData.MOD_ID + "_quest", LostTalesMetaData.MOD_ID + "_quest");
    }

    public LostTalesCommandQuest(String commandName, String commandPath) {
        super(commandName);
        this.commandPath = commandPath;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return commandPrefix() + " <defs|list|scan|starter|start|complete|reset|abandon|pin|unpin|revealmarkers|trackmarker|untrackmarker> [id] [player]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return;
        }

        String action = args[0];
        if ("defs".equalsIgnoreCase(action)) {
            sendDefinitions(sender);
            return;
        }

        if ("list".equalsIgnoreCase(action)) {
            EntityPlayerMP player = getTargetPlayer(sender, args, 1);
            if (player != null) {
                sendPlayerQuests(sender, player);
            }
            return;
        }

        if ("scan".equalsIgnoreCase(action)) {
            EntityPlayerMP player = getTargetPlayer(sender, args, 1);
            if (player != null) {
                scanInventory(sender, player);
            }
            return;
        }

        if ("unpin".equalsIgnoreCase(action)) {
            if (args.length >= 2 && LostTalesQuestRegistry.getQuest(args[1]) != null) {
                EntityPlayerMP player = getTargetPlayer(sender, args, 2);
                if (player != null) {
                    unpinQuest(sender, player, args[1]);
                }
            } else {
                EntityPlayerMP player = getTargetPlayer(sender, args, 1);
                if (player != null) {
                    unpinQuest(sender, player);
                }
            }
            return;
        }

        if ("untrackmarker".equalsIgnoreCase(action)) {
            EntityPlayerMP player = getTargetPlayer(sender, args, 1);
            if (player != null) {
                untrackMarker(sender, player);
            }
            return;
        }

        if ("starter".equalsIgnoreCase(action)) {
            giveStarterItem(sender, args);
            return;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return;
        }

        String questId = args[1];
        EntityPlayerMP player = getTargetPlayer(sender, args, 2);
        if (player == null) {
            return;
        }

        if ("start".equalsIgnoreCase(action)) {
            startQuest(sender, player, questId);
        } else if ("complete".equalsIgnoreCase(action)) {
            completeQuest(sender, player, questId);
        } else if ("reset".equalsIgnoreCase(action)) {
            resetQuest(sender, player, questId);
        } else if ("abandon".equalsIgnoreCase(action)) {
            abandonQuest(sender, player, questId);
        } else if ("pin".equalsIgnoreCase(action)) {
            pinQuest(sender, player, questId);
        } else if ("revealmarkers".equalsIgnoreCase(action)) {
            revealMarkers(sender, player, questId);
        } else if ("trackmarker".equalsIgnoreCase(action)) {
            trackMarker(sender, player, questId);
        } else {
            sendUsage(sender);
        }
    }

    private void sendDefinitions(ICommandSender sender) {
        Collection<LostTalesQuestDefinition> quests = LostTalesQuestRegistry.getQuests();
        send(sender, EnumChatFormatting.GOLD + "Loaded Lost Tales quest definitions: " + quests.size());
        for (LostTalesQuestDefinition quest : quests) {
            String flags = " [start=" + quest.getStartMode() + (quest.isRepeatable() ? ", repeatable" : ", one-time") + "]";
            send(sender, EnumChatFormatting.YELLOW + quest.getId() + EnumChatFormatting.GRAY + " - " + quest.getTitle() + flags);
            if (!quest.getPrerequisites().isEmpty()) {
                send(sender, EnumChatFormatting.DARK_GRAY + "  prerequisites: " + quest.getPrerequisites().toString());
            }
            if (!quest.getRewards().isEmpty()) {
                send(sender, EnumChatFormatting.DARK_GRAY + "  rewards: " + quest.getRewards().toString());
            }
            if (!quest.getInteraction().isEmpty()) {
                send(sender, EnumChatFormatting.DARK_GRAY + "  interaction: " + quest.getInteraction().toString());
            }
            if (!quest.getMarkers().isEmpty()) {
                send(sender, EnumChatFormatting.DARK_GRAY + "  markers: " + quest.getMarkers().toString());
            }
        }
    }

    private void sendPlayerQuests(ICommandSender sender, EntityPlayerMP player) {
        Collection<LostTalesQuestProgress> active = LostTalesQuestManager.getActiveQuests(player);
        Set<String> completed = LostTalesQuestManager.getCompletedQuestIds(player);
        Set<String> pinnedQuestIds = LostTalesQuestManager.getPinnedQuestIds(player);
        String pinnedMarkerId = LostTalesQuestManager.getPinnedMapMarkerId(player);
        Set<String> discoveredMarkers = LostTalesQuestManager.getDiscoveredMarkerIds(player);

        send(sender, EnumChatFormatting.GOLD + "Quest state for " + player.getCommandSenderName() + ":");
        send(sender, EnumChatFormatting.GRAY + "Tracked quests: " + (pinnedQuestIds.isEmpty() ? "none" : join(pinnedQuestIds)));
        send(sender, EnumChatFormatting.GRAY + "Tracked marker: " + (pinnedMarkerId.length() == 0 ? "none" : pinnedMarkerId));
        send(sender, EnumChatFormatting.GRAY + "Discovered markers: " + (discoveredMarkers.isEmpty() ? "none" : join(discoveredMarkers)));
        if (active.isEmpty()) {
            send(sender, EnumChatFormatting.GRAY + "Active: none");
        } else {
            send(sender, EnumChatFormatting.GRAY + "Active:");
            for (LostTalesQuestProgress progress : active) {
                LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(progress.getQuestId());
                String title = quest == null ? progress.getQuestId() : quest.getTitle();
                String progressText = progress.getObjectiveProgress().isEmpty() ? "" : ", objectives " + progress.getObjectiveProgress().toString();
                send(sender, EnumChatFormatting.YELLOW + "- " + progress.getQuestId() + EnumChatFormatting.GRAY + " (" + title + ", stage " + progress.getStageId() + progressText + ")");
            }
        }

        if (completed.isEmpty()) {
            send(sender, EnumChatFormatting.GRAY + "Completed: none");
        } else {
            send(sender, EnumChatFormatting.GRAY + "Completed: " + join(completed));
        }
    }

    private void startQuest(ICommandSender sender, EntityPlayerMP player, String questId) {
        LostTalesQuestManager.StartResult result = LostTalesQuestManager.startQuest(player, questId);
        if (result == LostTalesQuestManager.StartResult.STARTED) {
            send(sender, EnumChatFormatting.GREEN + "Started quest " + questId + " for " + player.getCommandSenderName() + ".");
        } else if (result == LostTalesQuestManager.StartResult.UNKNOWN_QUEST) {
            send(sender, EnumChatFormatting.RED + "Unknown quest: " + questId);
        } else if (result == LostTalesQuestManager.StartResult.ALREADY_ACTIVE) {
            send(sender, EnumChatFormatting.YELLOW + player.getCommandSenderName() + " already has this quest active.");
        } else if (result == LostTalesQuestManager.StartResult.ALREADY_COMPLETED) {
            send(sender, EnumChatFormatting.YELLOW + player.getCommandSenderName() + " has already completed this one-time quest.");
        } else if (result == LostTalesQuestManager.StartResult.START_NOT_ALLOWED) {
            send(sender, EnumChatFormatting.RED + "This quest cannot be started from that source.");
        } else if (result == LostTalesQuestManager.StartResult.REQUIREMENTS_NOT_MET) {
            send(sender, EnumChatFormatting.RED + "Quest prerequisites are not met.");
        } else {
            send(sender, EnumChatFormatting.RED + "Could not start quest for " + player.getCommandSenderName() + ".");
        }
    }

    private void completeQuest(ICommandSender sender, EntityPlayerMP player, String questId) {
        if (LostTalesQuestManager.completeQuest(player, questId)) {
            send(sender, EnumChatFormatting.GREEN + "Completed quest " + questId + " for " + player.getCommandSenderName() + ".");
        } else {
            send(sender, EnumChatFormatting.RED + "Could not complete quest " + questId + ". Is the ID valid?");
        }
    }

    private void resetQuest(ICommandSender sender, EntityPlayerMP player, String questId) {
        if (LostTalesQuestManager.resetQuest(player, questId)) {
            send(sender, EnumChatFormatting.GREEN + "Reset quest " + questId + " for " + player.getCommandSenderName() + ".");
        } else {
            send(sender, EnumChatFormatting.YELLOW + player.getCommandSenderName() + " had no saved state for " + questId + ".");
        }
    }

    private void abandonQuest(ICommandSender sender, EntityPlayerMP player, String questId) {
        if (LostTalesQuestManager.abandonQuest(player, questId)) {
            send(sender, EnumChatFormatting.GREEN + "Abandoned quest " + questId + " for " + player.getCommandSenderName() + ".");
        } else {
            send(sender, EnumChatFormatting.YELLOW + player.getCommandSenderName() + " does not have " + questId + " active.");
        }
    }

    private void scanInventory(ICommandSender sender, EntityPlayerMP player) {
        if (LostTalesQuestManager.refreshGatherProgressFromInventory(player)) {
            send(sender, EnumChatFormatting.GREEN + "Refreshed gather-objective progress from " + player.getCommandSenderName() + "'s inventory.");
        } else {
            send(sender, EnumChatFormatting.YELLOW + "No gather-objective progress changed for " + player.getCommandSenderName() + ".");
        }
    }

    private void pinQuest(ICommandSender sender, EntityPlayerMP player, String questId) {
        if (LostTalesQuestManager.pinQuest(player, questId)) {
            send(sender, EnumChatFormatting.GREEN + "Tracking quest " + questId + " for " + player.getCommandSenderName() + ".");
        } else {
            send(sender, EnumChatFormatting.YELLOW + "Could not track " + questId + ". Is it active for that player?");
        }
    }

    private void unpinQuest(ICommandSender sender, EntityPlayerMP player) {
        if (LostTalesQuestManager.unpinQuest(player)) {
            send(sender, EnumChatFormatting.GREEN + "Stopped tracking all quests for " + player.getCommandSenderName() + ".");
        } else {
            send(sender, EnumChatFormatting.YELLOW + player.getCommandSenderName() + " had no tracked quests.");
        }
    }

    private void unpinQuest(ICommandSender sender, EntityPlayerMP player, String questId) {
        if (LostTalesQuestManager.unpinQuest(player, questId)) {
            send(sender, EnumChatFormatting.GREEN + "Stopped tracking quest " + questId + " for " + player.getCommandSenderName() + ".");
        } else {
            send(sender, EnumChatFormatting.YELLOW + player.getCommandSenderName() + " was not tracking " + questId + ".");
        }
    }

    private void revealMarkers(ICommandSender sender, EntityPlayerMP player, String questId) {
        if (LostTalesQuestManager.revealQuestMarkers(player, questId)) {
            send(sender, EnumChatFormatting.GREEN + "Revealed marker hints from " + questId + " for " + player.getCommandSenderName() + ".");
        } else {
            send(sender, EnumChatFormatting.YELLOW + "No new marker hints were revealed for " + player.getCommandSenderName() + ".");
        }
    }

    private void trackMarker(ICommandSender sender, EntityPlayerMP player, String markerId) {
        if (LostTalesQuestManager.pinMapMarker(player, markerId)) {
            send(sender, EnumChatFormatting.GREEN + "Tracking marker " + markerId + " for " + player.getCommandSenderName() + ".");
        } else {
            send(sender, EnumChatFormatting.YELLOW + "Could not track marker " + markerId + ". It may not be discovered yet.");
        }
    }

    private void untrackMarker(ICommandSender sender, EntityPlayerMP player) {
        if (LostTalesQuestManager.unpinMapMarker(player)) {
            send(sender, EnumChatFormatting.GREEN + "Stopped tracking marker for " + player.getCommandSenderName() + ".");
        } else {
            send(sender, EnumChatFormatting.YELLOW + player.getCommandSenderName() + " had no tracked marker.");
        }
    }

    private void giveStarterItem(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, EnumChatFormatting.RED + "Usage: " + commandPrefix() + " starter <questId> [player] [consume]");
            return;
        }

        String questId = args[1];
        LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(questId);
        if (quest == null) {
            send(sender, EnumChatFormatting.RED + "Unknown quest: " + questId);
            return;
        }

        EntityPlayerMP player;
        boolean consume = false;
        if (args.length > 2 && isBoolean(args[2])) {
            player = getTargetPlayer(sender, new String[] { args[0], questId }, 2);
            consume = parseBoolean(args[2]);
        } else {
            player = getTargetPlayer(sender, args, 2);
            if (args.length > 3) {
                consume = parseBoolean(args[3]);
            }
        }
        if (player == null) {
            return;
        }

        ItemStack stack = new ItemStack(Items.paper);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("LostTalesQuestId", questId);
        tag.setBoolean("LostTalesQuestConsume", consume);
        stack.setTagCompound(tag);
        stack.setStackDisplayName("Quest Starter: " + quest.getTitle());

        if (!player.inventory.addItemStackToInventory(stack)) {
            EntityItem dropped = player.dropPlayerItemWithRandomChoice(stack, false);
            if (dropped != null) {
                dropped.delayBeforeCanPickup = 0;
            }
        }
        player.inventory.markDirty();

        send(sender, EnumChatFormatting.GREEN + "Gave quest starter for " + questId + " to " + player.getCommandSenderName() + ".");
        if (!quest.canStartFromItem()) {
            send(sender, EnumChatFormatting.YELLOW + "Note: this quest has startMode='" + quest.getStartMode() + "', so the starter item will not start it unless the quest uses item/any.");
        }
    }

    private EntityPlayerMP getTargetPlayer(ICommandSender sender, String[] args, int playerArgIndex) {
        try {
            if (args.length > playerArgIndex) {
                return getPlayer(sender, args[playerArgIndex]);
            }
            if (sender instanceof EntityPlayerMP) {
                return (EntityPlayerMP) sender;
            }
            send(sender, EnumChatFormatting.RED + "Console must specify a player.");
            return null;
        } catch (Exception e) {
            send(sender, EnumChatFormatting.RED + "Could not find player: " + args[playerArgIndex]);
            return null;
        }
    }

    private void sendUsage(ICommandSender sender) {
        send(sender, EnumChatFormatting.GRAY + getCommandUsage(sender));
        send(sender, EnumChatFormatting.GRAY + "Examples:");
        send(sender, EnumChatFormatting.GRAY + commandPrefix() + " defs");
        send(sender, EnumChatFormatting.GRAY + commandPrefix() + " start losttales:test_quest");
        send(sender, EnumChatFormatting.GRAY + commandPrefix() + " list <player>");
        send(sender, EnumChatFormatting.GRAY + commandPrefix() + " scan <player>");
        send(sender, EnumChatFormatting.GRAY + commandPrefix() + " starter losttales:tutorial/starter_note");
        send(sender, EnumChatFormatting.GRAY + commandPrefix() + " abandon losttales:test_quest");
        send(sender, EnumChatFormatting.GRAY + commandPrefix() + " pin losttales:test_quest");
        send(sender, EnumChatFormatting.GRAY + commandPrefix() + " revealmarkers losttales:tutorial/meet_nia");
        send(sender, EnumChatFormatting.GRAY + commandPrefix() + " trackmarker losttales:quest_giver_nia");
    }

    private String commandPrefix() {
        return "/" + commandPath;
    }

    private void send(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(message));
    }

    private boolean isBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value);
    }

    private boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    private String join(Collection<String> values) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String value : values) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(value);
            first = false;
        }
        return builder.toString();
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "defs", "list", "scan", "starter", "start", "complete", "reset", "abandon", "pin", "unpin", "revealmarkers", "trackmarker", "untrackmarker");
        }
        if (args.length == 2 && ("starter".equalsIgnoreCase(args[0]) || "start".equalsIgnoreCase(args[0]) || "complete".equalsIgnoreCase(args[0]) || "reset".equalsIgnoreCase(args[0]) || "abandon".equalsIgnoreCase(args[0]) || "pin".equalsIgnoreCase(args[0]) || "revealmarkers".equalsIgnoreCase(args[0]))) {
            List<String> ids = new ArrayList<String>();
            for (LostTalesQuestDefinition quest : LostTalesQuestRegistry.getQuests()) {
                ids.add(quest.getId());
            }
            return getListOfStringsMatchingLastWord(args, ids.toArray(new String[ids.size()]));
        }
        return null;
    }
}
