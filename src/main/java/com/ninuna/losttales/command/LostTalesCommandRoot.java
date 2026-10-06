package com.ninuna.losttales.command;

import com.ninuna.losttales.LostTalesMetaData;
import java.util.Arrays;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.EnumChatFormatting;

/**
 * Lost Tales' own command: {@code /losttales <sub-command> ...},
 * dispatched to the sub-commands {@link ELostTalesSubCommand} lists
 * (besides it the mod registers LOTR's {@code /strscan}; see
 * {@link ELostTalesCommand}). The root is open to operators of level two and to anyone who
 * may use at least one sub-command through a capability their roles
 * grant; each sub-command is asked for itself before it runs, so the
 * root opening never opens more than the sub-commands do.
 */
public class LostTalesCommandRoot extends LostTalesCommandBase {

    /** What the lang key of each of the root's answers begins with. */
    static final String SAY = "chat.losttales.command.root.";

    public LostTalesCommandRoot() {
        super(LostTalesMetaData.MOD_ID);
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        StringBuilder names = new StringBuilder();
        for (String name : ELostTalesSubCommand.primaryNames()) {
            names.append(names.length() == 0 ? "" : "|").append(name);
        }
        return "/" + getCommandName() + " <" + names + "> ...";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        if (super.canCommandSenderUseCommand(sender)) {
            return true;
        }
        for (ELostTalesSubCommand subCommand : ELostTalesSubCommand.values()) {
            CommandBase command = subCommand.getCommand();
            if (command instanceof LostTalesCommandBase
                    && ((LostTalesCommandBase)command).getCapability() != null
                    && command.canCommandSenderUseCommand(sender)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendUsage(sender);
            return;
        }

        ELostTalesSubCommand subCommand = ELostTalesSubCommand.byName(args[0]);
        if (subCommand == null) {
            say(sender, EnumChatFormatting.RED, SAY + "unknown", args[0]);
            sendUsage(sender);
            return;
        }
        // The root's own level is checked by the command handler; each
        // sub-command's is checked here, since the handler never sees
        // the sub-command, so one may ask for more than the root does.
        CommandBase command = subCommand.getCommand();
        if (!command.canCommandSenderUseCommand(sender)) {
            say(sender, EnumChatFormatting.RED, "commands.generic.permission");
            return;
        }

        command.processCommand(sender, shift(args));
    }

    private String[] shift(String[] args) {
        if (args == null || args.length <= 1) {
            return new String[0];
        }
        return Arrays.copyOfRange(args, 1, args.length);
    }

    /** Lists only what the sender may run; the rest would be refused anyway. */
    private void sendUsage(ICommandSender sender) {
        say(sender, EnumChatFormatting.GOLD, SAY + "header");
        for (ELostTalesSubCommand subCommand : ELostTalesSubCommand.values()) {
            if (subCommand.getCommand().canCommandSenderUseCommand(sender)) {
                usage(sender, "/" + getCommandName() + " " + subCommand.getUsage());
            }
        }
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args,
                    ELostTalesSubCommand.primaryNamesFor(sender));
        }

        ELostTalesSubCommand subCommand = ELostTalesSubCommand.byName(args[0]);
        if (subCommand == null) {
            return null;
        }
        return subCommand.getCommand().addTabCompletionOptions(sender, shift(args));
    }
}
