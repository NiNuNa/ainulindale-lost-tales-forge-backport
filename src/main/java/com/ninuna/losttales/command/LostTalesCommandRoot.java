package com.ninuna.losttales.command;

import com.ninuna.losttales.LostTalesMetaData;
import java.util.Arrays;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

/**
 * The one command Lost Tales registers: {@code /losttales <sub-command>
 * ...}, dispatched to the sub-commands {@link ELostTalesSubCommand}
 * lists. The root needs operator level two; a sub-command may ask for
 * more, and is asked before it runs.
 */
public class LostTalesCommandRoot extends LostTalesCommandBase {

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
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendUsage(sender);
            return;
        }

        ELostTalesSubCommand subCommand = ELostTalesSubCommand.byName(args[0]);
        if (subCommand == null) {
            send(sender, EnumChatFormatting.RED + "Unknown Lost Tales sub-command: " + args[0]);
            sendUsage(sender);
            return;
        }
        // The root's own level is checked by the command handler; each
        // sub-command's is checked here, since the handler never sees
        // the sub-command, so one may ask for more than the root does.
        CommandBase command = subCommand.getCommand();
        if (!command.canCommandSenderUseCommand(sender)) {
            ChatComponentTranslation refusal = new ChatComponentTranslation(
                    "commands.generic.permission");
            refusal.getChatStyle().setColor(EnumChatFormatting.RED);
            sender.addChatMessage(refusal);
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

    private void sendUsage(ICommandSender sender) {
        send(sender, EnumChatFormatting.GOLD + "Lost Tales commands:");
        for (ELostTalesSubCommand subCommand : ELostTalesSubCommand.values()) {
            send(sender, EnumChatFormatting.GRAY + "/" + getCommandName() + " "
                    + subCommand.getUsage());
        }
    }

    private void send(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(message));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args,
                    ELostTalesSubCommand.primaryNames());
        }

        ELostTalesSubCommand subCommand = ELostTalesSubCommand.byName(args[0]);
        if (subCommand == null) {
            return null;
        }
        return subCommand.getCommand().addTabCompletionOptions(sender, shift(args));
    }
}
