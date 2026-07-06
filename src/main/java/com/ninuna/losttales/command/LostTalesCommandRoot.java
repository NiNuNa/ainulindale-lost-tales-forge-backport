package com.ninuna.losttales.command;

import com.ninuna.losttales.LostTalesMetaData;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.Arrays;
import java.util.List;

/**
 * Namespaced command dispatcher so debug/admin commands can be written as
 * /losttales quest ... instead of one flat command per feature.
 */
public class LostTalesCommandRoot extends LostTalesCommandBase {
    private final LostTalesCommandQuest questCommand = new LostTalesCommandQuest("quest", LostTalesMetaData.MOD_ID + " quest");
    private final LostTalesCommandMapMarker mapMarkerCommand = new LostTalesCommandMapMarker("mapmarker", LostTalesMetaData.MOD_ID + " mapmarker");
    private final LostTalesCommandHud hudCommand = new LostTalesCommandHud("hud", LostTalesMetaData.MOD_ID + " hud");
    private final LostTalesCommandSummon summonCommand = new LostTalesCommandSummon("summon");

    public LostTalesCommandRoot() {
        super(LostTalesMetaData.MOD_ID);
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/" + getCommandName() + " <quest|mapmarker|hud|summon> ...";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendUsage(sender);
            return;
        }

        CommandBase command = getSubCommand(args[0]);
        if (command == null) {
            send(sender, EnumChatFormatting.RED + "Unknown Lost Tales sub-command: " + args[0]);
            sendUsage(sender);
            return;
        }

        command.processCommand(sender, shift(args));
    }

    private CommandBase getSubCommand(String name) {
        if (name == null) {
            return null;
        }
        if ("quest".equalsIgnoreCase(name) || "quests".equalsIgnoreCase(name) || "q".equalsIgnoreCase(name)) {
            return questCommand;
        }
        if ("mapmarker".equalsIgnoreCase(name) || "mapmarkers".equalsIgnoreCase(name) || "marker".equalsIgnoreCase(name) || "markers".equalsIgnoreCase(name) || "map_marker".equalsIgnoreCase(name)) {
            return mapMarkerCommand;
        }
        if ("hud".equalsIgnoreCase(name) || "overlay".equalsIgnoreCase(name)) {
            return hudCommand;
        }
        if ("summon".equalsIgnoreCase(name) || "entity".equalsIgnoreCase(name)) {
            return summonCommand;
        }
        return null;
    }

    private String[] shift(String[] args) {
        if (args == null || args.length <= 1) {
            return new String[0];
        }
        return Arrays.copyOfRange(args, 1, args.length);
    }

    private void sendUsage(ICommandSender sender) {
        send(sender, EnumChatFormatting.GOLD + "Lost Tales commands:");
        send(sender, EnumChatFormatting.GRAY + "/" + getCommandName() + " quest <defs|list|start|complete|reset|abandon|pin|unpin|starter|scan>");
        send(sender, EnumChatFormatting.GRAY + "/" + getCommandName() + " mapmarker <known|list|discover|forget|track|untrack>");
        send(sender, EnumChatFormatting.GRAY + "/" + getCommandName() + " hud <status|preset|set|move|toggle>");
        send(sender, EnumChatFormatting.GRAY + "/" + getCommandName() + " summon <entity> [x] [y] [z] [dataTag]");
        send(sender, EnumChatFormatting.DARK_GRAY + "Legacy shortcuts like /losttales_quest still exist for old scripts.");
    }

    private void send(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(message));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "quest", "mapmarker", "hud", "summon");
        }

        CommandBase command = getSubCommand(args[0]);
        if (command == null) {
            return null;
        }
        return command.addTabCompletionOptions(sender, shift(args));
    }
}
