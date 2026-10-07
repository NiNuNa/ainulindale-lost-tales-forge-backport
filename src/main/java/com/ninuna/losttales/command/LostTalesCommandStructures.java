package com.ninuna.losttales.command;

import com.ninuna.losttales.compat.lotr.structure.LotrStructureBan;
import com.ninuna.losttales.compat.lotr.structure.LotrStructureBanStorage;
import com.ninuna.losttales.compat.lotr.structure.LotrStructureBanWorldData;
import com.ninuna.losttales.compat.lotr.structure.LotrStructureBans;
import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import java.util.List;
import java.util.UUID;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumChatFormatting;

/**
 * Who may spawn LOTR's structures, in place of LOTR's own
 * {@code /banStructures} and {@code /allowStructures}:
 * {@code ban|allow <player|character>} bans an account, as every
 * character it plays, or one character alone, as a role is given; with
 * no name it bans or allows everyone, LOTR's server-wide switch.
 * {@code list} says who is banned.
 */
public final class LostTalesCommandStructures extends LostTalesCommandBase {

    static final String SAY = "chat.losttales.command.structures.";

    public LostTalesCommandStructures() {
        super("structures");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/losttales structures <ban|allow> [player|character]";
    }

    @Override
    public LostTalesCapability getCapability() {
        return LostTalesCapability.STRUCTURES_BAN;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args == null || args.length == 0 || sender.getEntityWorld() == null
                || sender.getEntityWorld().isRemote) {
            sendUsage(sender);
            return;
        }
        String action = args[0];
        if ("list".equalsIgnoreCase(action)) {
            list(sender);
        } else if ("ban".equalsIgnoreCase(action) || "allow".equalsIgnoreCase(action)) {
            boolean ban = "ban".equalsIgnoreCase(action);
            if (args.length == 1) {
                LotrStructureBans.setBannedForEveryone(ban);
                say(sender, EnumChatFormatting.GREEN,
                        ban ? SAY + "ban.everyone" : SAY + "allow.everyone");
            } else {
                change(sender, LostTalesCommandSubject.joinFrom(args, 1), ban);
            }
        } else {
            sendUsage(sender);
        }
    }

    private void change(ICommandSender sender, String name, boolean ban) {
        LostTalesCommandSubject subject = LostTalesCommandSubject.resolve(sender, name);
        if (subject.problem != null) {
            say(sender, EnumChatFormatting.RED, subject.problem, subject.problemArguments);
            return;
        }
        LotrStructureBanWorldData bans = LotrStructureBanStorage.get(sender.getEntityWorld());
        if (bans.isReadOnly()) {
            say(sender, EnumChatFormatting.RED, SAY + "read_only");
            return;
        }
        LotrStructureBan.Kind kind = subject.character != null
                ? LotrStructureBan.Kind.CHARACTER : LotrStructureBan.Kind.ACCOUNT;
        UUID id = subject.character != null ? subject.character : subject.account;
        if (ban) {
            if (bans.find(kind, id) != null) {
                say(sender, EnumChatFormatting.GRAY, SAY + "ban.already", subject.label);
                return;
            }
            if (!bans.ban(new LotrStructureBan(kind, id, name.trim(),
                    sender.getCommandSenderName(), System.currentTimeMillis()))) {
                say(sender, EnumChatFormatting.RED, SAY + "full");
                return;
            }
        } else if (bans.allow(kind, id) == null) {
            say(sender, EnumChatFormatting.GRAY, SAY + "allow.not_banned", subject.label);
            return;
        }
        say(sender, EnumChatFormatting.GREEN, ban ? SAY + "ban.done" : SAY + "allow.done",
                subject.label);
        EntityPlayerMP owner = LostTalesServerPlayers.findOnline(subject.owner);
        if (owner != null && owner != sender) {
            say(owner, EnumChatFormatting.YELLOW, ban ? SAY + "ban.done" : SAY + "allow.done",
                    subject.label);
        }
    }

    /** Whether everyone is banned, then each ban: whom it names and who gave it. */
    private void list(ICommandSender sender) {
        if (LotrStructureBans.bannedForEveryone()) {
            say(sender, EnumChatFormatting.YELLOW, SAY + "list.everyone");
        }
        LotrStructureBanWorldData bans = LotrStructureBanStorage.get(sender.getEntityWorld());
        if (bans.isReadOnly()) {
            say(sender, EnumChatFormatting.RED, SAY + "read_only");
            return;
        }
        List<LotrStructureBan> all = bans.all();
        if (all.isEmpty()) {
            say(sender, EnumChatFormatting.GRAY, SAY + "list.none");
            return;
        }
        for (LotrStructureBan each : all) {
            say(sender, EnumChatFormatting.GRAY, SAY + "list.entry",
                    words(LostTalesCommandSubject.SAY + each.getKind().id(), each.getName()),
                    each.getBannedBy());
        }
    }

    private void sendUsage(ICommandSender sender) {
        usage(sender, getCommandUsage(sender));
        usage(sender, "/losttales structures list");
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args == null) {
            return null;
        }
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "ban", "allow", "list");
        }
        if (args.length == 2 && ("ban".equalsIgnoreCase(args[0])
                || "allow".equalsIgnoreCase(args[0]))) {
            return getListOfStringsMatchingLastWord(args, LostTalesCommandSubject.names(sender));
        }
        return null;
    }
}
