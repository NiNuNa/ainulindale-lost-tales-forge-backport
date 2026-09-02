package com.ninuna.losttales.command;

import com.ninuna.losttales.chat.moderation.ChatMuteDurations;
import com.ninuna.losttales.chat.moderation.ChatMuteEntry;
import com.ninuna.losttales.chat.moderation.ChatMuteStorage;
import com.ninuna.losttales.chat.moderation.ChatMuteWorldData;
import com.ninuna.losttales.chat.server.LostTalesChatService;
import com.ninuna.losttales.compat.discord.LostTalesDiscordBridge;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

/**
 * Operator chat moderation: mute an account, lift a mute, list what is
 * in force. Mutes are account-keyed, so no character switch or rename
 * slips one, and they persist with the world. Muting needs the player
 * online — you mute whoever is talking — while unmuting also works by
 * the stored name after they leave.
 */
public final class LostTalesCommandChatModeration extends LostTalesCommandBase {

    public LostTalesCommandChatModeration() {
        super("chat");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/losttales chat <mute|unmute|mutes>";
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
        World world = resolveWorld(sender);
        if (world == null || world.isRemote) {
            send(sender, EnumChatFormatting.RED
                    + "Chat moderation requires a running logical server world.");
            return;
        }
        ChatMuteWorldData mutes;
        try {
            mutes = ChatMuteStorage.get(world);
        } catch (RuntimeException exception) {
            send(sender, EnumChatFormatting.RED
                    + "Unable to open chat mute storage: "
                    + exception.getClass().getSimpleName());
            return;
        }
        if (mutes.isReadOnlyForNewerVersion()) {
            send(sender, EnumChatFormatting.RED
                    + "Chat mutes are read-only: the data was written by a newer version ("
                    + mutes.getUnsupportedDataVersion()
                    + "). Nobody is being silenced.");
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
            send(sender, EnumChatFormatting.GRAY
                    + "/losttales chat mute <player> [30s|15m|2h|7d] [reason]");
            return;
        }
        EntityPlayerMP target = findOnlinePlayer(args[1]);
        DiscordMember member = target == null
                ? DiscordMember.parse(args[1]) : null;
        if (target == null && member == null) {
            send(sender, EnumChatFormatting.RED + "No online player named "
                    + args[1] + ". Muting needs the player online; a "
                    + "Discord member is named discord:<name> after they "
                    + "have written, or discord:<their Discord id>.");
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
        String reason = joinFrom(args, reasonFrom);
        ChatMuteEntry entry = new ChatMuteEntry(
                target != null ? target.getUniqueID() : member.senderId,
                target != null ? target.getCommandSenderName() : member.label,
                sender == null ? "" : sender.getCommandSenderName(),
                reason, now, expiresAt);
        if (!mutes.mute(entry)) {
            send(sender, EnumChatFormatting.RED
                    + "The mute list is full; lift one before adding another.");
            return;
        }
        if (target != null) {
            tellMuted(target, entry, now);
        }
        LostTalesChatService.sendAccessToOperators();
        send(sender, EnumChatFormatting.GREEN + "Muted "
                + entry.getAccountName()
                + (entry.isPermanent() ? " permanently"
                        : " for " + ChatMuteDurations.formatRemaining(
                                expiresAt - now))
                + (reason.length() > 0 ? ": " + reason : "") + ".");
    }

    private void unmute(ICommandSender sender, ChatMuteWorldData mutes,
                        String[] args) {
        if (args.length < 2) {
            send(sender, EnumChatFormatting.GRAY
                    + "/losttales chat unmute <player>");
            return;
        }
        EntityPlayerMP online = findOnlinePlayer(args[1]);
        DiscordMember member = online == null
                ? DiscordMember.parse(args[1]) : null;
        ChatMuteEntry lifted = online != null
                ? mutes.unmute(online.getUniqueID())
                : member != null ? mutes.unmute(member.senderId)
                        : mutes.unmuteByName(args[1]);
        if (lifted == null && member != null) {
            // Muted under a name the member has since changed, or by id
            // and now named: the stored label still finds it.
            lifted = mutes.unmuteByName(args[1]);
        }
        if (lifted == null) {
            send(sender, EnumChatFormatting.RED + args[1] + " is not muted.");
            return;
        }
        LostTalesChatService.sendAccessToOperators();
        send(sender, EnumChatFormatting.GREEN + "Unmuted "
                + lifted.getAccountName() + ".");
        if (online != null) {
            online.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.unmuted"));
        }
    }

    private void list(ICommandSender sender, ChatMuteWorldData mutes) {
        long now = System.currentTimeMillis();
        List<ChatMuteEntry> active = mutes.getActiveMutes(now);
        if (active.isEmpty()) {
            send(sender, EnumChatFormatting.GRAY + "Nobody is muted.");
            return;
        }
        send(sender, EnumChatFormatting.GOLD + "Muted accounts ("
                + active.size() + "):");
        for (ChatMuteEntry mute : active) {
            StringBuilder line = new StringBuilder();
            line.append(EnumChatFormatting.GRAY);
            line.append(mute.getAccountName().length() > 0
                    ? mute.getAccountName() : mute.getAccountId().toString());
            line.append(" — ");
            line.append(mute.isPermanent() ? "permanent"
                    : ChatMuteDurations.formatRemaining(
                            mute.getExpiresAtMillis() - now) + " left");
            if (mute.getReason().length() > 0) {
                line.append(" — ").append(mute.getReason());
            }
            if (mute.getMutedByName().length() > 0) {
                line.append(" (by ").append(mute.getMutedByName()).append(")");
            }
            send(sender, line.toString());
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
        String remaining = ChatMuteDurations.formatRemaining(
                mute.getExpiresAtMillis() - now);
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

    private static String joinFrom(String[] args, int from) {
        if (args.length <= from) {
            return "";
        }
        StringBuilder joined = new StringBuilder();
        for (int index = from; index < args.length; index++) {
            if (joined.length() > 0) {
                joined.append(' ');
            }
            joined.append(args[index]);
        }
        return joined.toString();
    }

    private static EntityPlayerMP findOnlinePlayer(String name) {
        MinecraftServer server = MinecraftServer.getServer();
        String wanted = name == null ? "" : name.trim();
        if (wanted.length() == 0 || server == null
                || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online =
                server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP candidate : online) {
            if (candidate != null && wanted.equalsIgnoreCase(
                    candidate.getCommandSenderName())) {
                return candidate;
            }
        }
        return null;
    }

    private World resolveWorld(ICommandSender sender) {
        if (sender instanceof EntityPlayerMP) {
            return ((EntityPlayerMP) sender).worldObj;
        }
        return sender == null ? null : sender.getEntityWorld();
    }

    private void sendUsage(ICommandSender sender) {
        send(sender, EnumChatFormatting.GRAY + getCommandUsage(sender));
        send(sender, EnumChatFormatting.GRAY
                + "/losttales chat mute <player|discord:name|discord:id> "
                + "[30s|15m|2h|7d] [reason]");
        send(sender, EnumChatFormatting.GRAY
                + "/losttales chat unmute <player|discord:name|discord:id>");
        send(sender, EnumChatFormatting.GRAY + "/losttales chat mutes");
    }

    private void send(ICommandSender sender, String message) {
        if (sender != null) {
            sender.addChatMessage(new ChatComponentText(message));
        }
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
