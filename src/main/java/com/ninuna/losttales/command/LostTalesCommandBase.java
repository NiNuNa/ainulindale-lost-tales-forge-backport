package com.ninuna.losttales.command;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.permission.LostTalesPermissions;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * Common ground for every Lost Tales command. A command that names a
 * {@link LostTalesCapability} is open to whoever holds it, by operator
 * level or by a role that grants it; one that names none keeps vanilla's
 * own check against {@link #getRequiredPermissionLevel()}.
 *
 * <p>Every command answers through {@link #say}: a lang key and its
 * arguments, which a player's game reads in its own language and a server
 * console in the server's. Each command's keys begin with its own prefix,
 * {@code chat.losttales.command.<sub-command>.}. Only the usage lines,
 * the command's own syntax, are sent as they are ({@link #usage}).</p>
 */
public class LostTalesCommandBase extends CommandBase {

    /** The answer when a command run from the console names no player. */
    static final String PLAYER_REQUIRED = "chat.losttales.command.player_required";

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

    /**
     * Answers the sender with the lang line under {@code key}, in
     * {@code color}. The key is always the code's own: what a player
     * typed goes in only as one of the {@code arguments}, which the line
     * shows as they are and never reads as a pattern.
     */
    static void say(ICommandSender sender, EnumChatFormatting color, String key,
                    Object... arguments) {
        if (sender != null) {
            sender.addChatMessage(line(color, key, arguments));
        }
    }

    /** The lang line under {@code key} in {@code color}, to send or to stand in another line. */
    static IChatComponent line(EnumChatFormatting color, String key, Object... arguments) {
        IChatComponent line = new ChatComponentTranslation(key, arguments);
        line.getChatStyle().setColor(color);
        return line;
    }

    /** Words of the lang file among another line's arguments, in that line's colour. */
    static IChatComponent words(String key, Object... arguments) {
        return new ChatComponentTranslation(key, arguments);
    }

    /** A usage line, in grey: the command's own syntax, the same in every language. */
    static void usage(ICommandSender sender, String syntax) {
        if (sender != null) {
            IChatComponent line = new ChatComponentText(syntax);
            line.getChatStyle().setColor(EnumChatFormatting.GRAY);
            sender.addChatMessage(line);
        }
    }
}
