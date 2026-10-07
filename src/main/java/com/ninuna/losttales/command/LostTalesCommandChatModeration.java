package com.ninuna.losttales.command;

import com.ninuna.losttales.chat.moderation.ChatMuteDurations;
import com.ninuna.losttales.chat.moderation.ChatMuteEntry;
import com.ninuna.losttales.chat.moderation.ChatMuteStorage;
import com.ninuna.losttales.chat.moderation.ChatMuteWorldData;
import com.ninuna.losttales.chat.server.ChatAbsentReader;
import com.ninuna.losttales.chat.server.LostTalesChatService;
import com.ninuna.losttales.compat.discord.LostTalesDiscordBridge;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.permission.LostTalesPermissions;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import java.util.List;
import java.util.UUID;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

/**
 * Chat moderation for whoever holds {@code chat.moderate}: mute an
 * account, lift a mute, list what is in force. Mutes are account-keyed,
 * so no character switch or rename slips one, and they persist with the
 * world. Muting needs the player
 * online — you mute whoever is talking — while unmuting also works by
 * the stored name after they leave. Both are only for someone who holds
 * every capability the account holds ({@link #withheldPower}).
 */
public final class LostTalesCommandChatModeration extends LostTalesCommandBase {

    /** What the lang key of each of the command's answers begins with. */
    static final String SAY = "chat.losttales.command.chat.";

    public LostTalesCommandChatModeration() {
        super("chat");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/losttales chat <mute|unmute|mutes>";
    }


    @Override
    public LostTalesCapability getCapability() {
        return LostTalesCapability.CHAT_MODERATE;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args == null || args.length == 0) {
            sendUsage(sender);
            return;
        }
        World world = resolveWorld(sender);
        if (world == null || world.isRemote) {
            say(sender, EnumChatFormatting.RED, SAY + "no_world");
            return;
        }
        ChatMuteWorldData mutes;
        try {
            mutes = ChatMuteStorage.get(world);
        } catch (RuntimeException exception) {
            say(sender, EnumChatFormatting.RED, SAY + "storage_failed",
                    exception.getClass().getSimpleName());
            return;
        }
        if (mutes.isReadOnlyForNewerVersion()) {
            say(sender, EnumChatFormatting.RED, SAY + "read_only",
                    Integer.valueOf(mutes.getUnsupportedDataVersion()));
            return;
        }
        String action = args[0];
        if ("mute".equalsIgnoreCase(action)) {
            mute(sender, mutes, args);
        } else if ("unmute".equalsIgnoreCase(action)) {
            unmute(sender, mutes, args);
        } else if ("mutes".equalsIgnoreCase(action)
                || "list".equalsIgnoreCase(action)) {
            list(sender, mutes);
        } else {
            sendUsage(sender);
        }
    }

    private void mute(ICommandSender sender, ChatMuteWorldData mutes,
                      String[] args) {
        if (args.length < 2) {
            usage(sender, "/losttales chat mute <player> [30s|15m|2h|7d] [reason]");
            return;
        }
        EntityPlayerMP target = LostTalesServerPlayers.findOnline(args[1]);
        DiscordMember member = target == null
                ? DiscordMember.parse(args[1]) : null;
        if (target == null && member == null) {
            say(sender, EnumChatFormatting.RED, SAY + "mute.not_online", args[1]);
            return;
        }
        if (target != null && target == sender) {
            say(sender, EnumChatFormatting.RED, SAY + "mute.self");
            return;
        }
        String withheld = target == null ? null : withheldPower(sender, target);
        if (withheld != null) {
            say(sender, EnumChatFormatting.RED, SAY + "mute.withheld",
                    target.getCommandSenderName(), withheld);
            return;
        }
        long now = System.currentTimeMillis();
        int reasonFrom = 2;
        long expiresAt = ChatMuteEntry.EXPIRES_NEVER;
        if (args.length > 2) {
            long duration = ChatMuteDurations.parse(args[2]);
            if (duration != ChatMuteDurations.NOT_A_DURATION) {
                expiresAt = now + duration;
                reasonFrom = 3;
            }
        }
        String reason = LostTalesCommandSubject.joinFrom(args, reasonFrom);
        ChatMuteEntry entry = new ChatMuteEntry(
                target != null ? target.getUniqueID() : member.senderId,
                target != null ? target.getCommandSenderName() : member.label,
                sender == null ? "" : sender.getCommandSenderName(),
                reason, now, expiresAt);
        if (!mutes.mute(entry)) {
            say(sender, EnumChatFormatting.RED, SAY + "mute.full");
            return;
        }
        if (target != null) {
            tellMuted(target, entry, now);
        }
        LostTalesChatService.sendAccessToModerators();
        boolean hasReason = reason.length() > 0;
        if (entry.isPermanent()) {
            say(sender, EnumChatFormatting.GREEN, hasReason
                    ? SAY + "muted.permanent.because" : SAY + "muted.permanent",
                    entry.getAccountName(), reason);
        } else {
            say(sender, EnumChatFormatting.GREEN, hasReason
                    ? SAY + "muted.timed.because" : SAY + "muted.timed",
                    entry.getAccountName(),
                    ChatMuteDurations.remaining(expiresAt - now).component(),
                    reason);
        }
    }

    /**
     * The first capability {@code target} holds that {@code sender} does
     * not, or null when they hold nothing more. A mute silences someone
     * who could otherwise lift it or overrule the one who set it, so only
     * someone who can do all they can may mute them, as a role is only
     * handed on by someone who could do all it allows. The server console
     * and an operator hold everything.
     */
    static String withheldPower(ICommandSender sender, EntityPlayerMP target) {
        for (LostTalesCapability capability : LostTalesCapability.all()) {
            if (LostTalesPermissions.has(target, capability)
                    && !LostTalesPermissions.has(sender, capability)) {
                return capability.getId();
            }
        }
        return null;
    }

    /**
     * As above for an account whose player is not here, its capabilities
     * read from the server's operator list and roles; null for no account
     * to ask, which holds nothing.
     */
    static String withheldPower(ICommandSender sender, ChatAbsentReader target) {
        if (target == null) {
            return null;
        }
        for (LostTalesCapability capability : LostTalesCapability.all()) {
            if (target.holds(capability)
                    && !LostTalesPermissions.has(sender, capability)) {
                return capability.getId();
            }
        }
        return null;
    }

    private void unmute(ICommandSender sender, ChatMuteWorldData mutes,
                        String[] args) {
        if (args.length < 2) {
            usage(sender, "/losttales chat unmute <player>");
            return;
        }
        EntityPlayerMP online = LostTalesServerPlayers.findOnline(args[1]);
        DiscordMember member = online == null
                ? DiscordMember.parse(args[1]) : null;
        ChatMuteEntry stored = online != null
                ? mutes.find(online.getUniqueID())
                : member != null ? mutes.find(member.senderId)
                        : mutes.findByName(args[1]);
        if (stored == null && member != null) {
            // Muted under a name the member has since changed, or by id
            // and now named: the stored label still finds it.
            stored = mutes.findByName(args[1]);
        }
        if (stored == null) {
            say(sender, EnumChatFormatting.RED, SAY + "unmute.not_muted", args[1]);
            return;
        }
        if (sender instanceof EntityPlayerMP && stored.getAccountId().equals(
                ((EntityPlayerMP)sender).getUniqueID())) {
            // As nobody mutes themselves, nobody lifts their own mute.
            say(sender, EnumChatFormatting.RED, SAY + "unmute.self");
            return;
        }
        // Lifting a mute overrules whoever set it, so it asks what muting
        // asks: someone who can do all the account can. An account whose
        // player is away is asked of the server's own lists.
        String withheld = online != null ? withheldPower(sender, online)
                : withheldPower(sender, ChatAbsentReader.of(
                        stored.getAccountId(), stored.getAccountName()));
        if (withheld != null) {
            say(sender, EnumChatFormatting.RED, SAY + "unmute.withheld",
                    stored.getAccountName(), withheld);
            return;
        }
        ChatMuteEntry lifted = mutes.unmute(stored.getAccountId());
        if (lifted == null) {
            say(sender, EnumChatFormatting.RED, SAY + "unmute.not_muted", args[1]);
            return;
        }
        LostTalesChatService.sendAccessToModerators();
        say(sender, EnumChatFormatting.GREEN, SAY + "unmuted", lifted.getAccountName());
        if (online != null) {
            online.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.unmuted"));
        }
    }

    private void list(ICommandSender sender, ChatMuteWorldData mutes) {
        long now = System.currentTimeMillis();
        List<ChatMuteEntry> active = mutes.getActiveMutes(now);
        if (active.isEmpty()) {
            say(sender, EnumChatFormatting.GRAY, SAY + "mutes.none");
            return;
        }
        say(sender, EnumChatFormatting.GOLD, SAY + "mutes.header",
                Integer.valueOf(active.size()));
        for (ChatMuteEntry mute : active) {
            String name = mute.getAccountName().length() > 0
                    ? mute.getAccountName() : mute.getAccountId().toString();
            Object left = mute.isPermanent() ? words(SAY + "mutes.permanent")
                    : words(SAY + "mutes.left", ChatMuteDurations.remaining(
                            mute.getExpiresAtMillis() - now).component());
            boolean hasReason = mute.getReason().length() > 0;
            boolean hasMuter = mute.getMutedByName().length() > 0;
            if (hasReason && hasMuter) {
                say(sender, EnumChatFormatting.GRAY, SAY + "mutes.entry.because.by", name,
                        left, mute.getReason(), mute.getMutedByName());
            } else if (hasReason) {
                say(sender, EnumChatFormatting.GRAY, SAY + "mutes.entry.because", name,
                        left, mute.getReason());
            } else if (hasMuter) {
                say(sender, EnumChatFormatting.GRAY, SAY + "mutes.entry.by", name, left,
                        mute.getMutedByName());
            } else {
                say(sender, EnumChatFormatting.GRAY, SAY + "mutes.entry", name, left);
            }
        }
    }

    /** The same notice a refused send shows, so the wording is one. */
    private void tellMuted(EntityPlayerMP target, ChatMuteEntry mute,
                           long now) {
        boolean hasReason = mute.getReason().length() > 0;
        if (mute.isPermanent()) {
            target.addChatMessage(hasReason
                    ? new ChatComponentTranslation(
                            "chat.losttales.muted.because", mute.getReason())
                    : new ChatComponentTranslation("chat.losttales.muted"));
            return;
        }
        IChatComponent remaining = ChatMuteDurations.remaining(
                mute.getExpiresAtMillis() - now).component();
        target.addChatMessage(hasReason
                ? new ChatComponentTranslation(
                        "chat.losttales.muted.timed.because", remaining,
                        mute.getReason())
                : new ChatComponentTranslation(
                        "chat.losttales.muted.timed", remaining));
    }

    /**
     * A Discord member named on the command line, {@code discord:Name}
     * for a member whose line the bridge relayed this session or
     * {@code discord:123456789012345678} for one named by Discord id;
     * null for anything else. The sender id is the one the member's
     * lines carry, so the mute stored against it is the one the chat
     * service checks.
     */
    static final class DiscordMember {
        static final String PREFIX = "discord:";
        final UUID senderId;
        /** {@code discord:Name}, as the mute list shows it. */
        final String label;

        private DiscordMember(UUID senderId, String label) {
            this.senderId = senderId;
            this.label = label;
        }

        static DiscordMember parse(String argument) {
            String value = argument == null ? "" : argument.trim();
            if (value.length() <= PREFIX.length() || !value.substring(0,
                    PREFIX.length()).equalsIgnoreCase(PREFIX)) {
                return null;
            }
            String who = value.substring(PREFIX.length()).trim();
            String userId = who;
            if (!isDiscordId(who)) {
                userId = LostTalesDiscordBridge.getInstance()
                        .findDiscordUserId(who);
                if (userId.length() == 0) {
                    return null;
                }
            }
            return new DiscordMember(
                    LostTalesChatMessagePacket.discordSenderId(userId),
                    PREFIX + who);
        }

        private static boolean isDiscordId(String value) {
            if (value.length() < 15 || value.length() > 20) {
                return false;
            }
            for (int index = 0; index < value.length(); index++) {
                if (!Character.isDigit(value.charAt(index))) {
                    return false;
                }
            }
            return true;
        }
    }




    private World resolveWorld(ICommandSender sender) {
        if (sender instanceof EntityPlayerMP) {
            return ((EntityPlayerMP) sender).worldObj;
        }
        return sender == null ? null : sender.getEntityWorld();
    }

    private void sendUsage(ICommandSender sender) {
        usage(sender, getCommandUsage(sender));
        usage(sender, "/losttales chat mute <player|discord:name|discord:id> "
                + "[30s|15m|2h|7d] [reason]");
        usage(sender, "/losttales chat unmute <player|discord:name|discord:id>");
        usage(sender, "/losttales chat mutes");
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args == null) {
            return null;
        }
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args,
                    "mute", "unmute", "mutes");
        }
        if (args.length == 2 && ("mute".equalsIgnoreCase(args[0])
                || "unmute".equalsIgnoreCase(args[0]))) {
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null) {
                return getListOfStringsMatchingLastWord(args,
                        server.getAllUsernames());
            }
        }
        if (args.length == 3 && "mute".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args,
                    "30s", "15m", "1h", "12h", "1d", "7d");
        }
        return null;
    }
}
