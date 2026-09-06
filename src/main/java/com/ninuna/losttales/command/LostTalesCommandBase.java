package com.ninuna.losttales.command;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.permission.LostTalesPermissions;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

/**
 * Common ground for every Lost Tales command. A command that names a
 * {@link LostTalesCapability} is open to whoever holds it, by operator
 * level or by a role that grants it; one that names none keeps vanilla's
 * own check against {@link #getRequiredPermissionLevel()}.
 */
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

    /**
     * The capability that opens the command, or null for a command that
     * is the operators' alone through its permission level.
     */
    public LostTalesCapability getCapability() {
        return null;
    }

    /**
     * The operator level that holds the command without any role: the
     * capability's own, so the two cannot say different things, and
     * vanilla's for a command that names none.
     */
    @Override
    public int getRequiredPermissionLevel() {
        LostTalesCapability capability = getCapability();
        return capability == null ? super.getRequiredPermissionLevel()
                : capability.getRequiredOpLevel();
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        LostTalesCapability capability = getCapability();
        if (capability == null) {
            return super.canCommandSenderUseCommand(sender);
        }
        return LostTalesPermissions.has(sender, capability);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {}
}
