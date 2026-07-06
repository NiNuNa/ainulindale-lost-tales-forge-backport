package com.ninuna.losttales.command;

import com.ninuna.losttales.LostTalesMetaData;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

public class LostTalesCommandBase extends CommandBase {

    private final String commandName;

    public LostTalesCommandBase(String commandName) {
        this.commandName = commandName;
    }

    @Override
    public String getCommandName() {
        return this.commandName;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "commands." + LostTalesMetaData.MOD_ID + "." + this.getCommandName() + ".usage";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {}
}