package com.ninuna.losttales.command;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatCodeNames;
import com.ninuna.losttales.chat.ChatRecipientRule;
import com.ninuna.losttales.compat.discord.DiscordBindingEntries;
import com.ninuna.losttales.compat.discord.DiscordBridgePolicy;
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
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * The Discord links, live. {@code list} shows what is linked where;
 * {@code link} hands out a code that an admin of a Discord server types
 * into the channel to link ({@code /link}), where the bot makes the
 * channel's webhook itself, so no webhook address is ever typed or
 * shown; {@code unlink} takes a game channel's links away, or one
 * Discord channel's, and deletes their webhooks. The links are written
 * through the config service, which saves the file and restarts the
 * bridge on it; {@code /losttales config reload} reads a file edited by
 * hand. Every
 * answer is words the sender's game translates, the channels it names
 * among them; the usage lines are the command's own syntax.
 */
public final class LostTalesCommandDiscord extends LostTalesCommandBase {

    private static final String BINDINGS_KEY = "channelBindings";
    /** What the lang key of each of the command's answers begins with. */
    static final String SAY = "chat.losttales.command.discord.";

    public LostTalesCommandDiscord() {
        super("discord");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/losttales discord <list|link|unlink> ...";
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
            say(sender, EnumChatFormatting.RED, SAY + "side_only");
            return;
        }
        String action = args[0];
        if ("list".equalsIgnoreCase(action)) {
            list(sender);
        } else if ("link".equalsIgnoreCase(action)) {
            link(sender, args);
        } else if ("unlink".equalsIgnoreCase(action)) {
            unlink(sender, args);
        } else {
            sendUsage(sender);
        }
    }

    /** Every link, as a line each: the game channel, the Discord channel, the way lines cross. */
    private void list(ICommandSender sender) {
        LostTalesDiscordBridge bridge = LostTalesDiscordBridge.getInstance();
        List<IChatComponent> lines = new ArrayList<IChatComponent>();
        for (String entry : LostTalesConfig.discordChannelBindings) {
            String key = DiscordBindingEntries.keyOf(entry);
            if (key.length() == 0) {
                continue;
            }
            String channel = DiscordBindingEntries.optionOf(entry, "channel");
            DiscordBridgeDirection direction = DiscordBridgeDirection.parse(
                    entry.substring(entry.indexOf('=') + 1).split(";")[0]);
            // Indented under the heading, the indent outside the words.
            IChatComponent line = new ChatComponentText("  ");
            line.getChatStyle().setColor(EnumChatFormatting.WHITE);
            line.appendSibling(words(SAY + "list.entry",
                    LostTalesDiscordBridge.gameChannelComponent(key),
                    channel.length() > 0 ? bridge.discordChannelComponent(channel)
                            : words(SAY + "list.no_channel"),
                    describe(direction)));
            lines.add(line);
        }
        String heading;
        if (bridge.isRunning()) {
            heading = lines.isEmpty() ? SAY + "list.running.none" : SAY + "list.running";
        } else {
            heading = lines.isEmpty() ? SAY + "list.stopped.none" : SAY + "list.stopped";
        }
        say(sender, EnumChatFormatting.GRAY, heading);
        for (IChatComponent line : lines) {
            sender.addChatMessage(line);
        }
    }

    /**
     * Hands out a code for linking a Discord channel to a game channel,
     * to the one who asked alone: whoever types it into a Discord
     * channel with {@code /link} links that channel.
     */
    private void link(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/losttales discord link <channel> "
                    + "[BIDIRECTIONAL|GAME_TO_DISCORD|DISCORD_TO_GAME]");
            return;
        }
        String key = args[1].toLowerCase(Locale.ROOT);
        String refusal = linkRefusal(args[1]);
        if (refusal != null) {
            say(sender, EnumChatFormatting.RED, refusal, args[1]);
            return;
        }
        ChatCodeNames.Named named = ChatCodeNames.parse(key);
        DiscordBridgeDirection direction = args.length > 2
                ? DiscordBridgeDirection.parse(args[2]) : DiscordBridgeDirection.BIDIRECTIONAL;
        if (direction == null || direction == DiscordBridgeDirection.DISABLED) {
            say(sender, EnumChatFormatting.RED, SAY + "unknown_direction", args[2]);
            return;
        }
        if (named.channel.getRecipientRule() == ChatRecipientRule.PROXIMITY
                && direction.readsFromDiscord()) {
            // Nobody on Discord stands near anyone.
            direction = DiscordBridgeDirection.GAME_TO_DISCORD;
        }
        LostTalesDiscordBridge bridge = LostTalesDiscordBridge.getInstance();
        if (!bridge.canPair()) {
            say(sender, EnumChatFormatting.RED, bridge.isGatewayClosedForGood()
                    ? SAY + "not_connected.closed" : SAY + "not_connected.setup");
            return;
        }
        UUID issuer = sender instanceof EntityPlayerMP
                ? ((EntityPlayerMP)sender).getUniqueID() : null;
        String code = DiscordLinkCodes.issue(key, direction, issuer,
                sender.getCommandSenderName(), System.currentTimeMillis());
        say(sender, EnumChatFormatting.GREEN, SAY + "code", code,
                LostTalesDiscordBridge.gameChannelComponent(key), describe(direction),
                String.valueOf(DiscordLinkCodes.LIFETIME_MILLIS / 60000L));
        if (DiscordBridgePolicy.isLimitedInGame(named.channel)) {
            say(sender, EnumChatFormatting.YELLOW, SAY + "limited",
                    LostTalesDiscordBridge.gameChannelComponent(key));
        }
    }

    /**
     * Why the game channel {@code typed} names cannot be linked, as the
     * lang key of the answer, which names what was typed; null when it
     * can. A private channel never leaves the game, and a channel whose
     * gate lets nobody read it would carry nothing.
     */
    static String linkRefusal(String typed) {
        String key = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        ChatCodeNames.Named named = ChatCodeNames.parse(key);
        if (named == null || !named.channel.isBridgeable()) {
            return SAY + "refused.channel";
        }
        if (!DiscordBridgePolicy.isOpenToTheBridge(named.channel)) {
            return SAY + "refused.gate";
        }
        return null;
    }

    /**
     * Takes a game channel's links away, or only the one to a Discord
     * channel given by its id, and deletes their webhooks.
     */
    private void unlink(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/losttales discord unlink <channel> [<Discord channel id>]");
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
            say(sender, EnumChatFormatting.RED, args.length > 2
                    ? SAY + "unlink.nothing_there" : SAY + "unlink.nothing", args[1]);
            return;
        }
        List<String> webhooks = DiscordBindingEntries.webhooksRemoved(before, after);
        LostTalesCommandConfig.report(sender, LostTalesServerConfigService.applyOwned(
                Collections.singletonList(new ServerConfigChange(
                        LostTalesConfig.CATEGORY_DISCORD, BINDINGS_KEY, true, after)),
                Collections.<String>emptySet(), ServerConfigSnapshot.COMMAND_KEYS));
        LostTalesDiscordBridge.getInstance().retireWebhooks(webhooks);
    }

    /** The way lines cross a link, as words the sender's game translates. */
    private static IChatComponent describe(DiscordBridgeDirection direction) {
        return words(directionKey(direction));
    }

    /** The lang key a direction is named by. */
    static String directionKey(DiscordBridgeDirection direction) {
        if (direction == null || direction == DiscordBridgeDirection.DISABLED) {
            return SAY + "direction.off";
        }
        return direction == DiscordBridgeDirection.GAME_TO_DISCORD ? SAY + "direction.to_discord"
                : direction == DiscordBridgeDirection.DISCORD_TO_GAME ? SAY + "direction.to_game"
                : SAY + "direction.both";
    }

    private void sendUsage(ICommandSender sender) {
        usage(sender, getCommandUsage(sender));
        usage(sender, "/losttales discord list");
        usage(sender, "/losttales discord link <channel> [direction]");
        usage(sender, "/losttales discord unlink <channel> [<Discord channel id>]");
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args == null) {
            return null;
        }
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "list", "link", "unlink");
        }
        if (args.length == 2 && ("link".equalsIgnoreCase(args[0])
                || "unlink".equalsIgnoreCase(args[0]))) {
            List<String> keys = new ArrayList<String>();
            for (ChatChannel channel : ChatChannel.values()) {
                if (channel.isBridgeable() && channel != ChatChannel.FACTION) {
                    keys.add(channel.getId());
                }
            }
            keys.addAll(ChatCodeNames.factionCodes());
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
