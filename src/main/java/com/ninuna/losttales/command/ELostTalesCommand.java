package com.ninuna.losttales.command;

import cpw.mods.fml.common.event.FMLServerStartingEvent;
import lotr.common.command.LOTRCommandStrScan;
import net.minecraft.command.CommandBase;

public enum ELostTalesCommand {
    ROOT(new LostTalesCommandRoot());

    private final CommandBase command;

    ELostTalesCommand(CommandBase command) {
        this.command = command;
    }

    public static void initAndRegisterCommands(FMLServerStartingEvent event) {
        for (ELostTalesCommand c : ELostTalesCommand.values()) {
            event.registerServerCommand(c.getCommand());
        }
        event.registerServerCommand(new LOTRCommandStrScan());
    }

    public CommandBase getCommand() {
        return command;
    }
}
