package com.ninuna.losttales.command;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatRecipientRule;
import com.ninuna.losttales.compat.discord.DiscordBindingEntries;
import com.ninuna.losttales.compat.discord.DiscordBridgeDirection;
import com.ninuna.losttales.compat.discord.DiscordLinkCodes;
import com.ninuna.losttales.compat.discord.LostTalesDiscordBridge;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.config.server.LostTalesServerConfigService;
import com.ninuna.losttales.config.server.ServerConfigChange;
import com.ninuna.losttales.config.server.ServerConfigSnapshot;
import com.ninuna.losttales.permission.LostTalesCapability;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumChatFormatting;

/**
 * The Discord links, live. {@code list} shows what is linked where;
 * {@code link} hands out a code that an admin of a Discord server types
 * into the channel to link ({@code /link}), where the bot makes the
 * channel's webhook itself, so no webhook address is ever typed or
 * shown; {@code unlink} takes a game channel's links away, or one
 * Discord channel's, and deletes their webhooks; {@code reload} restarts
 * the bridge from the file. The links are written through the config
 * service, which saves the file and restarts the bridge on it.
 */
public final class LostTalesCommandDiscord extends LostTalesCommandBase {

    private static final String BINDINGS_KEY = "channelBindings";

    public LostTalesCommandDiscord() {
        super("discord");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/losttales discord <list|link|unlink|reload> ...";
    }

    @Override
    public LostTalesCapability getCapability() {
        return LostTalesCapability.SERVER_CONFIG;
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
        } else if ("link".equalsIgnoreCase(action)) {
            link(sender, args);
        } else if ("unlink".equalsIgnoreCase(action)) {
            unlink(sender, args);
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

    /** Every link, as a line each: the game channel, the Discord channel, the way lines cross. */
    private void list(ICommandSender sender) {
        LostTalesDiscordBridge bridge = LostTalesDiscordBridge.getInstance();
        List<String> lines = new ArrayList<String>();
        for (String entry : LostTalesConfig.discordChannelBindings) {
            String key = DiscordBindingEntries.keyOf(entry);
            if (key.length() == 0) {
                continue;
            }
            String channel = DiscordBindingEntries.optionOf(entry, "channel");
            DiscordBridgeDirection direction = DiscordBridgeDirection.parse(
                    entry.substring(entry.indexOf('=') + 1).split(";")[0]);
            lines.add(LostTalesDiscordBridge.gameChannelName(key) + " - "
                    + (channel.length() > 0 ? bridge.describeDiscordChannel(channel)
                            : "a webhook naming no channel")
                    + ", " + describe(direction));
        }
        LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY + "Discord bridge "
                + (bridge.isRunning() ? "running" : "stopped") + "; "
                + (lines.isEmpty() ? "nothing is linked." : "linked:"));
        for (String line : lines) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.WHITE + "  " + line);
        }
    }

    /**
     * Hands out a code for linking a Discord channel to a game channel,
     * to the one who asked alone: whoever types it into a Discord
     * channel with {@code /link} links that channel.
     */
    private void link(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY
                    + "/losttales discord link <channel|faction:<id>> "
                    + "[BIDIRECTIONAL|GAME_TO_DISCORD|DISCORD_TO_GAME]");
            return;
        }
        String key = args[1].toLowerCase(Locale.ROOT);
        if (!isGameChannelKey(key)) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.RED
                    + "Cannot link " + args[1] + ": name a channel that may reach Discord,"
                    + " or a faction as faction:<id>.");
            return;
        }
        DiscordBridgeDirection direction = args.length > 2
                ? DiscordBridgeDirection.parse(args[2]) : DiscordBridgeDirection.BIDIRECTIONAL;
        if (direction == null || direction == DiscordBridgeDirection.DISABLED) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.RED
                    + "Unknown direction " + args[2] + ".");
            return;
        }
        ChatChannel channel = ChatChannel.fromId(key);
        if (channel != null && channel.getRecipientRule() == ChatRecipientRule.PROXIMITY
                && direction.readsFromDiscord()) {
            // Nobody on Discord stands near anyone.
            direction = DiscordBridgeDirection.GAME_TO_DISCORD;
        }
        if (!LostTalesDiscordBridge.getInstance().canPair()) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.RED
                    + "Linking needs the Discord bot connected with its slash commands:"
                    + " set discord.enabled, discord.botToken, discord.gateway and"
                    + " discord.slashCommands.");
            return;
        }
        UUID issuer = sender instanceof EntityPlayerMP
                ? ((EntityPlayerMP)sender).getUniqueID() : null;
        String code = DiscordLinkCodes.issue(key, direction, issuer,
                sender.getCommandSenderName(), System.currentTimeMillis());
        LostTalesCommandConfig.send(sender, EnumChatFormatting.GREEN
                + "Type /link code:" + code + " in the Discord channel to link it to "
                + LostTalesDiscordBridge.gameChannelName(key) + ", "
                + describe(direction) + ". The code works once, for "
                + DiscordLinkCodes.LIFETIME_MILLIS / 60000L + " minutes, and needs a"
                + " member who may manage that channel's webhooks.");
    }

    /**
     * Takes a game channel's links away, or only the one to a Discord
     * channel given by its id, and deletes their webhooks.
     */
    private void unlink(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY
                    + "/losttales discord unlink <channel|faction:<id>> [<Discord channel id>]");
            return;
        }
        String key = args[1].toLowerCase(Locale.ROOT);
        String[] before = LostTalesConfig.discordChannelBindings;
        List<String> after;
        if (args.length > 2) {
            List<String> ofKey = new ArrayList<String>();
            for (String entry : before) {
                if (!key.equalsIgnoreCase(DiscordBindingEntries.keyOf(entry))
                        || !args[2].equals(DiscordBindingEntries.optionOf(entry, "channel"))) {
                    ofKey.add(entry);
                }
            }
            after = ofKey;
        } else {
            after = DiscordBindingEntries.removeKey(before, key);
        }
        if (after.size() == before.length) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.RED
                    + "Nothing of " + args[1] + " is linked"
                    + (args.length > 2 ? " to that Discord channel." : "."));
            return;
        }
        List<String> webhooks = DiscordBindingEntries.webhooksRemoved(before, after);
        LostTalesCommandConfig.report(sender, LostTalesServerConfigService.applyOwned(
                Collections.singletonList(new ServerConfigChange(
                        LostTalesConfig.CATEGORY_DISCORD, BINDINGS_KEY, true, after)),
                Collections.<String>emptySet(), ServerConfigSnapshot.COMMAND_KEYS));
        LostTalesDiscordBridge.getInstance().retireWebhooks(webhooks);
    }

    /** A bridgeable channel's id, or {@code faction:<id>} for one faction's chat. */
    static boolean isGameChannelKey(String key) {
        if (key.startsWith("faction:")) {
            return key.length() > "faction:".length();
        }
        ChatChannel channel = ChatChannel.fromId(key);
        return channel != null && channel.isBridgeable()
                && channel != ChatChannel.FACTION;
    }

    private static String describe(DiscordBridgeDirection direction) {
        if (direction == null || direction == DiscordBridgeDirection.DISABLED) {
            return "switched off";
        }
        return direction == DiscordBridgeDirection.GAME_TO_DISCORD ? "game to Discord"
                : direction == DiscordBridgeDirection.DISCORD_TO_GAME ? "Discord to game"
                : "both ways";
    }

    private void sendUsage(ICommandSender sender) {
        LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY + getCommandUsage(sender));
        LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY + "/losttales discord list");
        LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY
                + "/losttales discord link <channel|faction:<id>> [direction]");
        LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY
                + "/losttales discord unlink <channel|faction:<id>> [<Discord channel id>]");
        LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY + "/losttales discord reload");
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args == null) {
            return null;
        }
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "list", "link", "unlink", "reload");
        }
        if (args.length == 2 && ("link".equalsIgnoreCase(args[0])
                || "unlink".equalsIgnoreCase(args[0]))) {
            List<String> keys = new ArrayList<String>();
            for (ChatChannel channel : ChatChannel.values()) {
                if (channel.isBridgeable() && channel != ChatChannel.FACTION) {
                    keys.add(channel.getId());
                }
            }
            keys.add("faction:");
            return getListOfStringsMatchingLastWord(args, keys.toArray(new String[keys.size()]));
        }
        if (args.length == 3 && "link".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args,
                    DiscordBridgeDirection.BIDIRECTIONAL.name(),
                    DiscordBridgeDirection.GAME_TO_DISCORD.name(),
                    DiscordBridgeDirection.DISCORD_TO_GAME.name());
        }
        return null;
    }
}
