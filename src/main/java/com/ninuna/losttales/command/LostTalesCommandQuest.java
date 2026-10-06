package com.ninuna.losttales.command;

import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestWords;
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
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
/**
 * The quest admin command: what the server knows and each player holds,
 * starting, finishing and resetting a player's quests, starter items and
 * markers, reading the server's own quest files again, and starting and
 * stopping world quests. Every answer is a line of the lang file,
 * {@code chat.losttales.quest.command.*} and the quest lines it shares
 * with the game ({@code chat.losttales.quest.*}), in the chat's own white.
 */
public class LostTalesCommandQuest extends LostTalesCommandBase {
    /** What the lang key of each of the command's own answers begins with. */
    static final String SAY = "chat.losttales.quest.command.";
    /** The colour every answer is in. */
    private static final EnumChatFormatting WHITE = EnumChatFormatting.WHITE;

    private final String commandPath;

    public LostTalesCommandQuest(String commandName, String commandPath) {
        super(commandName);
        this.commandPath = commandPath;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return commandPrefix() + " <defs|list|scan|starter|start|complete|reset|abandon|pin|unpin|revealmarkers|reload|world> [id] [player]";
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
                say(sender, WHITE, LostTalesQuestManager.refreshGatherProgressFromInventory(player)
                        ? SAY + "scan.done" : SAY + "scan.nothing",
                        player.getCommandSenderName());
            }
            return;
        }

        if ("unpin".equalsIgnoreCase(action)) {
            if (args.length >= 2 && LostTalesQuestRegistry.getQuest(args[1]) != null) {
                EntityPlayerMP player = getTargetPlayer(sender, args, 2);
                if (player != null) {
                    say(sender, WHITE, LostTalesQuestManager.unpinQuest(player, args[1])
                            ? SAY + "unpin.done" : SAY + "unpin.nothing", args[1],
                            player.getCommandSenderName());
                }
            } else {
                EntityPlayerMP player = getTargetPlayer(sender, args, 1);
                if (player != null) {
                    say(sender, WHITE, LostTalesQuestManager.unpinQuest(player)
                            ? SAY + "unpin_all.done" : SAY + "unpin_all.nothing",
                            player.getCommandSenderName());
                }
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
            say(sender, WHITE, LostTalesQuestManager.completeQuest(player, questId)
                    ? SAY + "complete.done" : SAY + "complete.failed", questId, name);
        } else if ("reset".equalsIgnoreCase(action)) {
            say(sender, WHITE, LostTalesQuestManager.resetQuest(player, questId)
                    ? SAY + "reset.done" : SAY + "reset.nothing", questId, name);
        } else if ("abandon".equalsIgnoreCase(action)) {
            say(sender, WHITE, LostTalesQuestManager.abandonQuest(player, questId)
                    ? SAY + "abandon.done" : SAY + "abandon.nothing", questId, name);
        } else if ("pin".equalsIgnoreCase(action)) {
            say(sender, WHITE, LostTalesQuestManager.pinQuest(player, questId)
                    ? SAY + "pin.done" : SAY + "pin.failed", questId, name);
        } else if ("revealmarkers".equalsIgnoreCase(action)) {
            say(sender, WHITE, LostTalesQuestManager.revealQuestMarkers(player, questId)
                    ? SAY + "reveal.done" : SAY + "reveal.nothing", questId, name);
        } else {
            sendUsage(sender);
        }
    }

    private void sendDefinitions(ICommandSender sender) {
        Collection<LostTalesQuestDefinition> quests = LostTalesQuestRegistry.getQuests();
        say(sender, WHITE, SAY + "defs.header", Integer.valueOf(quests.size()));
        for (LostTalesQuestDefinition quest : quests) {
            say(sender, WHITE, SAY + "defs.line", quest.getId(),
                    LostTalesQuestWords.titleComponent(quest),
                    quest.getStartMode(), words(quest.isRepeatable()
                            ? SAY + "defs.repeatable" : SAY + "defs.once"));
            if (!quest.getPrerequisites().isEmpty()) {
                say(sender, WHITE, SAY + "defs.prerequisites",
                        quest.getPrerequisites().toString());
            }
            if (!quest.getRewards().isEmpty()) {
                say(sender, WHITE, SAY + "defs.rewards", quest.getRewards().toString());
            }
            if (!quest.getInteraction().isEmpty()) {
                say(sender, WHITE, SAY + "defs.interaction", quest.getInteraction().toString());
            }
            if (!quest.getMarkers().isEmpty()) {
                say(sender, WHITE, SAY + "defs.markers", quest.getMarkers().toString());
            }
        }
    }

    private void sendPlayerQuests(ICommandSender sender, EntityPlayerMP player) {
        Collection<LostTalesQuestProgress> active = LostTalesQuestManager.getActiveQuests(player);
        Set<String> completed = LostTalesQuestManager.getCompletedQuestIds(player);
        Set<String> pinnedQuestIds = LostTalesQuestManager.getPinnedQuestIds(player);
        String pinnedMarkerId = LostTalesQuestManager.getPinnedMapMarkerId(player);
        Set<String> discoveredMarkers = LostTalesQuestManager.getDiscoveredMarkerIds(player);

        say(sender, WHITE, SAY + "list.header", player.getCommandSenderName());
        say(sender, WHITE, SAY + "list.tracked", listOrNone(pinnedQuestIds));
        say(sender, WHITE, SAY + "list.marker", pinnedMarkerId.length() == 0
                ? words(SAY + "list.none") : pinnedMarkerId);
        say(sender, WHITE, SAY + "list.discovered", listOrNone(discoveredMarkers));
        if (active.isEmpty()) {
            say(sender, WHITE, SAY + "list.active", words(SAY + "list.none"));
        } else {
            say(sender, WHITE, SAY + "list.active.header");
            for (LostTalesQuestProgress progress : active) {
                LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(progress.getQuestId());
                Object title = quest == null ? progress.getQuestId()
                        : LostTalesQuestWords.titleComponent(quest);
                say(sender, WHITE, SAY + "list.active.line", progress.getQuestId(), title,
                        progress.getStageId(), progress.getObjectiveProgress().toString());
            }
        }
        say(sender, WHITE, SAY + "list.completed", listOrNone(completed));
    }

    private void startQuest(ICommandSender sender, EntityPlayerMP player, String questId) {
        LostTalesQuestManager.StartResult result = LostTalesQuestManager.startQuest(player, questId);
        String name = player.getCommandSenderName();
        switch (result) {
            case STARTED:
                say(sender, WHITE, SAY + "start.started", questId, name);
                return;
            case UNKNOWN_QUEST:
                say(sender, WHITE, "chat.losttales.quest.unknown", questId);
                return;
            case ALREADY_ACTIVE:
                say(sender, WHITE, SAY + "start.already_active", name, questId);
                return;
            case ALREADY_COMPLETED:
                say(sender, WHITE, SAY + "start.already_completed", name, questId);
                return;
            case RESTART_NOT_ALLOWED:
                say(sender, WHITE, SAY + "start.not_restartable", questId);
                return;
            case START_NOT_ALLOWED:
                say(sender, WHITE, SAY + "start.wrong_source", questId);
                return;
            case REQUIREMENTS_NOT_MET:
                say(sender, WHITE, SAY + "start.requirements", questId);
                return;
            default:
                say(sender, WHITE, SAY + "start.failed", questId, name);
        }
    }

    private void giveStarterItem(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            say(sender, WHITE, SAY + "starter.usage", commandPrefix());
            return;
        }

        String questId = args[1];
        LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(questId);
        if (quest == null) {
            say(sender, WHITE, "chat.losttales.quest.unknown", questId);
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
                SAY + "starter.name", LostTalesQuestWords.title(quest)));

        if (!player.inventory.addItemStackToInventory(stack)) {
            EntityItem dropped = player.dropPlayerItemWithRandomChoice(stack, false);
            if (dropped != null) {
                dropped.delayBeforeCanPickup = 0;
            }
        }
        player.inventory.markDirty();

        say(sender, WHITE, SAY + "starter.given", questId, player.getCommandSenderName());
        if (!quest.canStartFromItem()) {
            say(sender, WHITE, SAY + "starter.note", quest.getStartMode());
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
            say(sender, WHITE, SAY + "player.console");
            return null;
        } catch (Exception e) {
            say(sender, WHITE, SAY + "player.unknown", args[playerArgIndex]);
            return null;
        }
    }

    /** Reads the server's own quest files again and sends them to everyone online. */
    private void reloadServerQuests(ICommandSender sender) {
        ServerQuestFiles.Result result = LostTalesQuestRegistry.loadServerQuests();
        ServerQuestSync.sendToAll(MinecraftServer.getServer());
        say(sender, WHITE, "chat.losttales.quest.reload.done",
                Integer.valueOf(result.quests.size()),
                Integer.valueOf(result.problems.size()));
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
            say(sender, WHITE, "chat.losttales.quest.world.usage", commandPrefix());
            return;
        }
        String questId = args[2];
        if ("stop".equalsIgnoreCase(verb)) {
            say(sender, WHITE, WorldQuests.stop(server, questId)
                    ? "chat.losttales.quest.world.stopped"
                    : "chat.losttales.quest.world.not_running", questId);
            return;
        }
        WorldQuests.StartResult result = WorldQuests.start(server, questId);
        String key = "chat.losttales.quest.world.start."
                + result.name().toLowerCase(java.util.Locale.ROOT);
        if (result == WorldQuests.StartResult.NOT_READY) {
            // The quest file's first problem, as the server log reads it.
            java.util.List<String> problems = WorldQuests.problems(
                    LostTalesQuestRegistry.getQuest(questId));
            say(sender, WHITE, key, questId, problems.isEmpty() ? "" : problems.get(0));
            return;
        }
        say(sender, WHITE, key, questId, Integer.valueOf(WorldQuests.MAX_RUNNING));
    }

    private void listWorldQuests(ICommandSender sender, MinecraftServer server) {
        java.util.List<WorldQuestRun> runs = WorldQuests.runs(server);
        if (runs.isEmpty()) {
            say(sender, WHITE, "chat.losttales.quest.world.none");
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
            say(sender, WHITE, "chat.losttales.quest.world.line", run.getQuestId(),
                    words("chat.losttales.quest.world.state."
                            + run.getState().name().toLowerCase(java.util.Locale.ROOT)),
                    Integer.valueOf(reached), Integer.valueOf(goal),
                    Integer.valueOf(run.getHelpers().size()));
        }
    }

    private void sendUsage(ICommandSender sender) {
        usage(sender, getCommandUsage(sender));
        say(sender, EnumChatFormatting.GRAY, SAY + "examples");
        String[] examples = {
                "defs",
                "start losttales:tutorial/starter_note",
                "list <player>",
                "scan <player>",
                "starter losttales:tutorial/starter_note",
                "abandon losttales:tutorial/starter_note",
                "pin losttales:tutorial/starter_note",
                "revealmarkers losttales:tutorial/meet_nia"
        };
        for (String example : examples) {
            usage(sender, commandPrefix() + " " + example);
        }
    }

    private String commandPrefix() {
        return "/" + commandPath;
    }

    /** The values joined by commas, or the word for none. */
    private static Object listOrNone(Collection<String> values) {
        if (values.isEmpty()) {
            return words(SAY + "list.none");
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
            return getListOfStringsMatchingLastWord(args, "defs", "list", "scan", "starter", "start", "complete", "reset", "abandon", "pin", "unpin", "revealmarkers", "reload", "world");
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
