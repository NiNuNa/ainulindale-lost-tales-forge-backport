package com.ninuna.losttales.command;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.compat.discord.DiscordBindingEntries;
import com.ninuna.losttales.compat.discord.DiscordBridgeDirection;
import com.ninuna.losttales.compat.discord.DiscordChannelBinding;
import com.ninuna.losttales.compat.discord.LostTalesDiscordBridge;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.config.server.LostTalesServerConfigService;
import com.ninuna.losttales.config.server.ServerConfigChange;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.EnumChatFormatting;

/**
 * The Discord bindings, live: list what the bridge is running with, bind
 * a game channel to a Discord channel and webhook, unbind one, or reload
 * the bridge from the file. A bind or unbind edits the
 * {@code channelBindings} list through the config service, which writes
 * the file and restarts the bridge on it — no server restart.
 */
public final class LostTalesCommandDiscord extends LostTalesCommandBase {

    private static final String BINDINGS_KEY = "channelBindings";

    public LostTalesCommandDiscord() {
        super("discord");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/losttales discord <list|bind|unbind|reload> ...";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args == null || args.length == 0) {
            sendUsage(sender);
            return;
        }
        if (FMLCommonHandler.instance().getEffectiveSide() != Side.SERVER) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.RED
                    + "The Discord bridge runs on the logical server only.");
            return;
        }
        String action = args[0];
        if ("list".equalsIgnoreCase(action)) {
            list(sender);
        } else if ("bind".equalsIgnoreCase(action)) {
            bind(sender, args);
        } else if ("unbind".equalsIgnoreCase(action)) {
            unbind(sender, args);
        } else if ("reload".equalsIgnoreCase(action)) {
            LostTalesDiscordBridge.getInstance().start();
            LostTalesCommandConfig.send(sender, EnumChatFormatting.GREEN
                    + "Discord bridge restarted from the config file"
                    + (LostTalesDiscordBridge.getInstance().isRunning()
                            ? "." : "; it is off or has nothing to do."));
        } else {
            sendUsage(sender);
        }
    }

    private void list(ICommandSender sender) {
        LostTalesDiscordBridge bridge = LostTalesDiscordBridge.getInstance();
        LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY + "Bridge "
                + (bridge.isRunning() ? "running" : "stopped") + "; configured entries:");
        for (String entry : LostTalesConfig.discordChannelBindings) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.WHITE + "  " + redact(entry));
        }
        List<DiscordChannelBinding> live = bridge.bindings().all();
        if (!live.isEmpty()) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY + "Live bindings: "
                    + bridge.bindings().describeForLog());
        }
    }

    private void bind(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY
                    + "/losttales discord bind <channel|faction:<id>> "
                    + "<DISABLED|GAME_TO_DISCORD|DISCORD_TO_GAME|BIDIRECTIONAL> "
                    + "[channel=<discord channel id>] [webhook=<url>]");
            return;
        }
        String key = args[1].toLowerCase(Locale.ROOT);
        if (!isGameChannelKey(key)) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.RED
                    + "Unknown game channel " + args[1] + ".");
            return;
        }
        DiscordBridgeDirection direction = DiscordBridgeDirection.parse(args[2]);
        if (direction == null) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.RED
                    + "Unknown direction " + args[2] + ".");
            return;
        }
        String channelId = "";
        String webhook = "";
        for (int index = 3; index < args.length; index++) {
            String option = args[index];
            if (option.toLowerCase(Locale.ROOT).startsWith("channel=")) {
                channelId = option.substring("channel=".length());
            } else if (option.toLowerCase(Locale.ROOT).startsWith("webhook=")) {
                webhook = option.substring("webhook=".length());
            } else {
                LostTalesCommandConfig.send(sender, EnumChatFormatting.RED
                        + "Unknown option " + option + ".");
                return;
            }
        }
        List<String> entries = DiscordBindingEntries.upsert(
                LostTalesConfig.discordChannelBindings, key, direction, channelId, webhook);
        LostTalesCommandConfig.report(sender, LostTalesServerConfigService.apply(
                Collections.singletonList(new ServerConfigChange(
                        LostTalesConfig.CATEGORY_DISCORD, BINDINGS_KEY, true, entries))));
    }

    private void unbind(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY
                    + "/losttales discord unbind <channel|faction:<id>>");
            return;
        }
        String key = args[1].toLowerCase(Locale.ROOT);
        if (!DiscordBindingEntries.contains(LostTalesConfig.discordChannelBindings, key)) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.RED
                    + "No binding for " + args[1] + ".");
            return;
        }
        List<String> entries = DiscordBindingEntries.remove(
                LostTalesConfig.discordChannelBindings, key);
        LostTalesCommandConfig.report(sender, LostTalesServerConfigService.apply(
                Collections.singletonList(new ServerConfigChange(
                        LostTalesConfig.CATEGORY_DISCORD, BINDINGS_KEY, true, entries))));
    }

    /** A bridgeable channel's id, or {@code faction:<id>} for a faction scope. */
    static boolean isGameChannelKey(String key) {
        if (key.startsWith("faction:")) {
            return key.length() > "faction:".length();
        }
        ChatChannel channel = ChatChannel.fromId(key);
        return channel != null && channel.isBridgeable();
    }

    /** A webhook URL carries its token; the list shows only that one is set. */
    public static String redact(String entry) {
        int webhook = entry.toLowerCase(Locale.ROOT).indexOf("webhook=");
        if (webhook < 0) {
            return entry;
        }
        int end = entry.indexOf(';', webhook);
        String value = end < 0 ? entry.substring(webhook + "webhook=".length())
                : entry.substring(webhook + "webhook=".length(), end);
        String shown = value.trim().length() == 0 ? "" : "(set)";
        return entry.substring(0, webhook) + "webhook=" + shown
                + (end < 0 ? "" : entry.substring(end));
    }

    private void sendUsage(ICommandSender sender) {
        LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY + getCommandUsage(sender));
        LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY + "/losttales discord list");
        LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY
                + "/losttales discord bind <channel|faction:<id>> <direction> "
                + "[channel=<id>] [webhook=<url>]");
        LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY
                + "/losttales discord unbind <channel|faction:<id>>");
        LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY + "/losttales discord reload");
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args == null) {
            return null;
        }
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "list", "bind", "unbind", "reload");
        }
        if (args.length == 2 && ("bind".equalsIgnoreCase(args[0])
                || "unbind".equalsIgnoreCase(args[0]))) {
            List<String> keys = new ArrayList<String>();
            for (ChatChannel channel : ChatChannel.values()) {
                if (channel.isBridgeable()) {
                    keys.add(channel.getId());
                }
            }
            return getListOfStringsMatchingLastWord(args, keys.toArray(new String[keys.size()]));
        }
        if (args.length == 3 && "bind".equalsIgnoreCase(args[0])) {
            List<String> names = new ArrayList<String>();
            for (DiscordBridgeDirection direction : DiscordBridgeDirection.values()) {
                names.add(direction.name());
            }
            return getListOfStringsMatchingLastWord(args, names.toArray(new String[names.size()]));
        }
        return null;
    }
}
