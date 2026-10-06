package com.ninuna.losttales.command;

import com.ninuna.losttales.event.LostTalesMobAggroEventHandler;
import com.ninuna.losttales.fellowship.server.FellowshipIntegrityReport;
import com.ninuna.losttales.fellowship.server.FellowshipService;
import com.ninuna.losttales.fellowship.server.FellowshipSyncManager;
import com.ninuna.losttales.fellowship.storage.FellowshipGoHereMarkerStorage;
import com.ninuna.losttales.fellowship.storage.FellowshipGoHereMarkerWorldData;
import com.ninuna.losttales.fellowship.storage.FellowshipInvitationStorage;
import com.ninuna.losttales.fellowship.storage.FellowshipInvitationWorldData;
import com.ninuna.losttales.fellowship.storage.FellowshipStorage;
import com.ninuna.losttales.fellowship.storage.FellowshipWorldData;
import com.ninuna.losttales.permission.LostTalesCapability;
import java.util.List;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

/**
 * The fellowship stores for an operator: status counts them, validate says what
 * no longer stands without changing anything, repair removes it (members,
 * stale invitations, go-here markers), clearcombat forgets the combat
 * markers.
 */
public final class LostTalesCommandFellowshipAdmin extends LostTalesCommandBase {

    /** What the lang key of each of the command's answers begins with. */
    static final String SAY = "chat.losttales.command.fellowship.";

    public LostTalesCommandFellowshipAdmin() {
        super("fellowship");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/losttales fellowship <status|validate|repair|clearcombat>";
    }

    @Override
    public LostTalesCapability getCapability() {
        return LostTalesCapability.FELLOWSHIP_ADMIN;
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
        String action = args[0];
        if ("status".equalsIgnoreCase(action) || "dump".equalsIgnoreCase(action)) {
            reportStatus(sender, world);
        } else if ("validate".equalsIgnoreCase(action)) {
            FellowshipIntegrityReport report =
                    FellowshipService.getInstance().inspectIntegrity(world);
            if (report == null) {
                say(sender, EnumChatFormatting.RED, SAY + "validate.unavailable");
            } else if (report.isClean()) {
                say(sender, EnumChatFormatting.GREEN, SAY + "validate.sound");
            } else {
                say(sender, EnumChatFormatting.GOLD, SAY + "validate.found", describe(report));
            }
            reportStatus(sender, world);
        } else if ("repair".equalsIgnoreCase(action)) {
            FellowshipIntegrityReport report =
                    FellowshipService.getInstance().repairIntegrity(world);
            if (report == null) {
                say(sender, EnumChatFormatting.RED, SAY + "repair.refused");
            } else {
                say(sender, EnumChatFormatting.GREEN, SAY + "repair.done", describe(report));
                if (!report.isClean()) {
                    FellowshipSyncManager.sendStateToEveryone();
                }
            }
            reportStatus(sender, world);
        } else if ("clearcombat".equalsIgnoreCase(action)) {
            LostTalesMobAggroEventHandler.clearAll();
            say(sender, EnumChatFormatting.GREEN, SAY + "clearcombat");
        } else {
            sendUsage(sender);
        }
    }

    private static IChatComponent describe(FellowshipIntegrityReport report) {
        return words(SAY + "counts", Integer.valueOf(report.getMembers()),
                Integer.valueOf(report.getInvitations()),
                Integer.valueOf(report.getMarkers()));
    }

    private void reportStatus(ICommandSender sender, World world) {
        try {
            FellowshipWorldData fellowships = FellowshipStorage.get(world);
            FellowshipInvitationWorldData invitations = FellowshipInvitationStorage.get(world);
            FellowshipGoHereMarkerWorldData markers = FellowshipGoHereMarkerStorage.get(world);
            say(sender, EnumChatFormatting.GOLD, SAY + "status.header");
            say(sender, EnumChatFormatting.GRAY, SAY + "status.counts",
                    Integer.valueOf(fellowships.getFellowshipCount()),
                    Integer.valueOf(invitations.getInvitationCount()),
                    Integer.valueOf(markers.getMarkers().size()));
            say(sender, EnumChatFormatting.GRAY, SAY + "status.quarantine",
                    Integer.valueOf(fellowships.getQuarantinedEntryCount()),
                    Integer.valueOf(invitations.getQuarantinedEntryCount()),
                    Integer.valueOf(markers.getQuarantinedEntryCount()));
            say(sender, EnumChatFormatting.GRAY, SAY + "status.read_only",
                    Boolean.valueOf(fellowships.isReadOnlyForNewerVersion()),
                    Boolean.valueOf(invitations.isReadOnlyForNewerVersion()),
                    Boolean.valueOf(markers.isReadOnlyForNewerVersion()));
        } catch (RuntimeException exception) {
            say(sender, EnumChatFormatting.RED, SAY + "status.failed",
                    exception.getClass().getSimpleName());
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
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args != null && args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "status", "validate", "repair", "clearcombat");
        }
        return null;
    }
}
