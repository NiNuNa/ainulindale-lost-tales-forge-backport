package com.ninuna.losttales.command;

import com.ninuna.losttales.compat.lotr.LotrCommands;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import lotr.common.command.LOTRCommandStrScan;
import net.minecraft.command.CommandBase;

public enum ELostTalesCommand {
    ROOT(new LostTalesCommandRoot());

    private final CommandBase command;

    ELostTalesCommand(CommandBase command) {
        this.command = command;
    }

    /**
     * The commands of a starting server: {@code /losttales}, with LOTR's
     * fellowship commands gone ({@link LotrCommands}), and LOTR's structure
     * scanner, which LOTR keeps for its own builds and the mod's structures
     * are scanned with.
     */
    public static void initAndRegisterCommands(FMLServerStartingEvent event) {
        LotrCommands.removeReplaced();
        for (ELostTalesCommand c : ELostTalesCommand.values()) {
            event.registerServerCommand(c.getCommand());
        }
        event.registerServerCommand(new LOTRCommandStrScan());
    }

    public CommandBase getCommand() {
        return command;
    }
}
