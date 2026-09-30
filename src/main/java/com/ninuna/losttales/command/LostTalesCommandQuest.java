package com.ninuna.losttales.command;

import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestManager;
import com.ninuna.losttales.quest.LostTalesQuestRegistry;
import com.ninuna.losttales.quest.ServerQuestFiles;
import com.ninuna.losttales.quest.ServerQuestSync;
import com.ninuna.losttales.quest.world.WorldQuestRules;
import com.ninuna.losttales.quest.world.WorldQuestRun;
import com.ninuna.losttales.quest.world.WorldQuests;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import com.ninuna.losttales.permission.LostTalesCapability;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
/**
 * The quest admin command: what the server knows and each player holds,
 * starting, finishing and resetting a player's quests, starter items and
 * markers, reading the server's own quest files again, and starting and
 * stopping world quests. Every answer is a line of the lang file,
 * {@code chat.losttales.quest.command.*}.
 */
public class LostTalesCommandQuest extends LostTalesCommandBase {
    private static final String SAY = "chat.losttales.quest.command.";

    private final String commandPath;

    public LostTalesCommandQuest(String commandName, String commandPath) {
        super(commandName);
        this.commandPath = commandPath;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return commandPrefix() + " <defs|list|scan|starter|start|complete|reset|abandon|pin|unpin|revealmarkers|trackmarker|untrackmarker|reload|world> [id] [player]";
    }

    @Override
    public LostTalesCapability getCapability() {
        return LostTalesCapability.QUEST_ADMIN;
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
                answer(sender, LostTalesQuestManager.refreshGatherProgressFromInventory(player),
                        "scan.done", "scan.nothing", player.getCommandSenderName());
            }
            return;
        }

        if ("unpin".equalsIgnoreCase(action)) {
            if (args.length >= 2 && LostTalesQuestRegistry.getQuest(args[1]) != null) {
                EntityPlayerMP player = getTargetPlayer(sender, args, 2);
                if (player != null) {
                    answer(sender, LostTalesQuestManager.unpinQuest(player, args[1]),
                            "unpin.done", "unpin.nothing", args[1],
                            player.getCommandSenderName());
                }
            } else {
                EntityPlayerMP player = getTargetPlayer(sender, args, 1);
                if (player != null) {
                    answer(sender, LostTalesQuestManager.unpinQuest(player),
                            "unpin_all.done", "unpin_all.nothing",
                            player.getCommandSenderName());
                }
            }
            return;
        }

        if ("untrackmarker".equalsIgnoreCase(action)) {
            EntityPlayerMP player = getTargetPlayer(sender, args, 1);
            if (player != null) {
                answer(sender, LostTalesQuestManager.unpinMapMarker(player),
                        "untrackmarker.done", "untrackmarker.nothing",
                        player.getCommandSenderName());
            }
            return;
        }

        if ("starter".equalsIgnoreCase(action)) {
            giveStarterItem(sender, args);
            return;
        }

        if ("reload".equalsIgnoreCase(action)) {
            reloadServerQuests(sender);
            return;
        }

        if ("world".equalsIgnoreCase(action)) {
            worldQuest(sender, args);
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
        String name = player.getCommandSenderName();

        if ("start".equalsIgnoreCase(action)) {
            startQuest(sender, player, questId);
        } else if ("complete".equalsIgnoreCase(action)) {
            answer(sender, LostTalesQuestManager.completeQuest(player, questId),
                    "complete.done", "complete.failed", questId, name);
        } else if ("reset".equalsIgnoreCase(action)) {
            answer(sender, LostTalesQuestManager.resetQuest(player, questId),
                    "reset.done", "reset.nothing", questId, name);
        } else if ("abandon".equalsIgnoreCase(action)) {
            answer(sender, LostTalesQuestManager.abandonQuest(player, questId),
                    "abandon.done", "abandon.nothing", questId, name);
        } else if ("pin".equalsIgnoreCase(action)) {
            answer(sender, LostTalesQuestManager.pinQuest(player, questId),
                    "pin.done", "pin.failed", questId, name);
        } else if ("revealmarkers".equalsIgnoreCase(action)) {
            answer(sender, LostTalesQuestManager.revealQuestMarkers(player, questId),
                    "reveal.done", "reveal.nothing", questId, name);
        } else if ("trackmarker".equalsIgnoreCase(action)) {
            answer(sender, LostTalesQuestManager.pinMapMarker(player, questId),
                    "trackmarker.done", "trackmarker.failed", questId, name);
        } else {
            sendUsage(sender);
        }
    }

    private void sendDefinitions(ICommandSender sender) {
        Collection<LostTalesQuestDefinition> quests = LostTalesQuestRegistry.getQuests();
        say(sender, "defs.header", Integer.valueOf(quests.size()));
        for (LostTalesQuestDefinition quest : quests) {
            say(sender, "defs.line", quest.getId(), quest.getTitle(),
                    quest.getStartMode(), new ChatComponentTranslation(SAY
                            + (quest.isRepeatable() ? "defs.repeatable" : "defs.once")));
            if (!quest.getPrerequisites().isEmpty()) {
                say(sender, "defs.prerequisites", quest.getPrerequisites().toString());
            }
            if (!quest.getRewards().isEmpty()) {
                say(sender, "defs.rewards", quest.getRewards().toString());
            }
            if (!quest.getInteraction().isEmpty()) {
                say(sender, "defs.interaction", quest.getInteraction().toString());
            }
            if (!quest.getMarkers().isEmpty()) {
                say(sender, "defs.markers", quest.getMarkers().toString());
            }
        }
    }

    private void sendPlayerQuests(ICommandSender sender, EntityPlayerMP player) {
        Collection<LostTalesQuestProgress> active = LostTalesQuestManager.getActiveQuests(player);
        Set<String> completed = LostTalesQuestManager.getCompletedQuestIds(player);
        Set<String> pinnedQuestIds = LostTalesQuestManager.getPinnedQuestIds(player);
        String pinnedMarkerId = LostTalesQuestManager.getPinnedMapMarkerId(player);
        Set<String> discoveredMarkers = LostTalesQuestManager.getDiscoveredMarkerIds(player);

        say(sender, "list.header", player.getCommandSenderName());
        say(sender, "list.tracked", listOrNone(pinnedQuestIds));
        say(sender, "list.marker", pinnedMarkerId.length() == 0
                ? new ChatComponentTranslation(SAY + "list.none") : pinnedMarkerId);
        say(sender, "list.discovered", listOrNone(discoveredMarkers));
        if (active.isEmpty()) {
            say(sender, "list.active", new ChatComponentTranslation(SAY + "list.none"));
        } else {
            say(sender, "list.active.header");
            for (LostTalesQuestProgress progress : active) {
                LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(progress.getQuestId());
                String title = quest == null ? progress.getQuestId() : quest.getTitle();
                say(sender, "list.active.line", progress.getQuestId(), title,
                        progress.getStageId(), progress.getObjectiveProgress().toString());
            }
        }
        say(sender, "list.completed", listOrNone(completed));
    }

    private void startQuest(ICommandSender sender, EntityPlayerMP player, String questId) {
        LostTalesQuestManager.StartResult result = LostTalesQuestManager.startQuest(player, questId);
        String name = player.getCommandSenderName();
        switch (result) {
            case STARTED:
                say(sender, "start.started", questId, name);
                return;
            case UNKNOWN_QUEST:
                sender.addChatMessage(new ChatComponentTranslation(
                        "chat.losttales.quest.unknown", questId));
                return;
            case ALREADY_ACTIVE:
                say(sender, "start.already_active", name, questId);
                return;
            case ALREADY_COMPLETED:
                say(sender, "start.already_completed", name, questId);
                return;
            case RESTART_NOT_ALLOWED:
                say(sender, "start.not_restartable", questId);
                return;
            case START_NOT_ALLOWED:
                say(sender, "start.wrong_source", questId);
                return;
            case REQUIREMENTS_NOT_MET:
                say(sender, "start.requirements", questId);
                return;
            default:
                say(sender, "start.failed", questId, name);
        }
    }

    private void giveStarterItem(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            say(sender, "starter.usage", commandPrefix());
            return;
        }

        String questId = args[1];
        LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(questId);
        if (quest == null) {
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.quest.unknown", questId));
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
        // An item's name is kept as written, so it is written in the
        // server's language.
        stack.setStackDisplayName(StatCollector.translateToLocalFormatted(
                SAY + "starter.name", quest.getTitle()));

        if (!player.inventory.addItemStackToInventory(stack)) {
            EntityItem dropped = player.dropPlayerItemWithRandomChoice(stack, false);
            if (dropped != null) {
                dropped.delayBeforeCanPickup = 0;
            }
        }
        player.inventory.markDirty();

        say(sender, "starter.given", questId, player.getCommandSenderName());
        if (!quest.canStartFromItem()) {
            say(sender, "starter.note", quest.getStartMode());
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
            say(sender, "player.console");
            return null;
        } catch (Exception e) {
            say(sender, "player.unknown", args[playerArgIndex]);
            return null;
        }
    }

    /** Reads the server's own quest files again and sends them to everyone online. */
    private void reloadServerQuests(ICommandSender sender) {
        ServerQuestFiles.Result result = LostTalesQuestRegistry.loadServerQuests();
        ServerQuestSync.sendToAll(MinecraftServer.getServer());
        sender.addChatMessage(new ChatComponentTranslation(
                "chat.losttales.quest.reload.done",
                Integer.valueOf(result.quests.size()),
                Integer.valueOf(result.problems.size())));
    }

    /** {@code world start|stop <id>} and {@code world list}. */
    private void worldQuest(ICommandSender sender, String[] args) {
        MinecraftServer server = MinecraftServer.getServer();
        String verb = args.length >= 2 ? args[1] : "";
        if ("list".equalsIgnoreCase(verb)) {
            listWorldQuests(sender, server);
            return;
        }
        if (args.length < 3 || !"start".equalsIgnoreCase(verb)
                && !"stop".equalsIgnoreCase(verb)) {
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.quest.world.usage", commandPrefix()));
            return;
        }
        String questId = args[2];
        if ("stop".equalsIgnoreCase(verb)) {
            sender.addChatMessage(new ChatComponentTranslation(
                    WorldQuests.stop(server, questId)
                            ? "chat.losttales.quest.world.stopped"
                            : "chat.losttales.quest.world.not_running",
                    questId));
            return;
        }
        WorldQuests.StartResult result = WorldQuests.start(server, questId);
        String key = "chat.losttales.quest.world.start."
                + result.name().toLowerCase(java.util.Locale.ROOT);
        if (result == WorldQuests.StartResult.NOT_READY) {
            java.util.List<String> problems = WorldQuests.problems(
                    LostTalesQuestRegistry.getQuest(questId));
            sender.addChatMessage(new ChatComponentTranslation(key, questId,
                    problems.isEmpty() ? "" : problems.get(0)));
            return;
        }
        sender.addChatMessage(new ChatComponentTranslation(key, questId,
                Integer.valueOf(WorldQuests.MAX_RUNNING)));
    }

    private void listWorldQuests(ICommandSender sender, MinecraftServer server) {
        java.util.List<WorldQuestRun> runs = WorldQuests.runs(server);
        if (runs.isEmpty()) {
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.quest.world.none"));
            return;
        }
        for (WorldQuestRun run : runs) {
            LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(
                    run.getQuestId());
            int reached = 0;
            int goal = 0;
            for (com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition objective
                    : WorldQuestRules.objectives(quest)) {
                reached += Math.min(WorldQuestRules.goal(objective),
                        run.getCount(objective.getId()));
                goal += WorldQuestRules.goal(objective);
            }
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.quest.world.line", run.getQuestId(),
                    new ChatComponentTranslation(
                            "chat.losttales.quest.world.state."
                                    + run.getState().name().toLowerCase(
                                            java.util.Locale.ROOT)),
                    Integer.valueOf(reached), Integer.valueOf(goal),
                    Integer.valueOf(run.getHelpers().size())));
        }
    }

    private void sendUsage(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
        say(sender, "examples");
        String[] examples = {
                "defs",
                "start losttales:tutorial/starter_note",
                "list <player>",
                "scan <player>",
                "starter losttales:tutorial/starter_note",
                "abandon losttales:tutorial/starter_note",
                "pin losttales:tutorial/starter_note",
                "revealmarkers losttales:tutorial/meet_nia",
                "trackmarker losttales:quest_giver_nia"
        };
        for (String example : examples) {
            sender.addChatMessage(new ChatComponentText(commandPrefix() + " " + example));
        }
    }

    private String commandPrefix() {
        return "/" + commandPath;
    }

    /** One of the command's lines, {@code chat.losttales.quest.command.<key>}. */
    private static void say(ICommandSender sender, String key, Object... args) {
        sender.addChatMessage(new ChatComponentTranslation(SAY + key, args));
    }

    /** The line for what happened, or the one for nothing having changed. */
    private static void answer(ICommandSender sender, boolean changed,
                               String doneKey, String nothingKey,
                               Object... args) {
        say(sender, changed ? doneKey : nothingKey, args);
    }

    /** The values joined by commas, or the word for none. */
    private static Object listOrNone(Collection<String> values) {
        if (values.isEmpty()) {
            return new ChatComponentTranslation(SAY + "list.none");
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private boolean isBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value);
    }

    private boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "defs", "list", "scan", "starter", "start", "complete", "reset", "abandon", "pin", "unpin", "revealmarkers", "trackmarker", "untrackmarker", "reload", "world");
        }
        if (args.length == 2 && "world".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "start", "stop", "list");
        }
        if (args.length == 3 && "world".equalsIgnoreCase(args[0])) {
            List<String> ids = new ArrayList<String>();
            for (LostTalesQuestDefinition quest : LostTalesQuestRegistry.getQuests()) {
                if (quest.isWorldQuest()) {
                    ids.add(quest.getId());
                }
            }
            return getListOfStringsMatchingLastWord(args, ids.toArray(new String[ids.size()]));
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
