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
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

/**
 * The fellowship stores for an operator: status counts them, validate says what
 * no longer stands without changing anything, repair removes it (members,
 * stale invitations, go-here markers), clearcombat forgets the combat
 * markers.
 */
public final class LostTalesCommandFellowshipAdmin extends LostTalesCommandBase {

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
            send(sender, EnumChatFormatting.RED + "Fellowship diagnostics require a running logical server world.");
            return;
        }
        String action = args[0];
        if ("status".equalsIgnoreCase(action) || "dump".equalsIgnoreCase(action)) {
            reportStatus(sender, world);
        } else if ("validate".equalsIgnoreCase(action)) {
            FellowshipIntegrityReport report =
                    FellowshipService.getInstance().inspectIntegrity(world);
            if (report == null) {
                send(sender, EnumChatFormatting.RED + "Fellowship stores cannot be checked: one is unavailable or read-only.");
            } else if (report.isClean()) {
                send(sender, EnumChatFormatting.GREEN + "Fellowship stores are sound; nothing to repair.");
            } else {
                send(sender, EnumChatFormatting.GOLD + "Repair would remove " + describe(report)
                        + ". Run /losttales fellowship repair to remove them.");
            }
            reportStatus(sender, world);
        } else if ("repair".equalsIgnoreCase(action)) {
            FellowshipIntegrityReport report =
                    FellowshipService.getInstance().repairIntegrity(world);
            if (report == null) {
                send(sender, EnumChatFormatting.RED + "Fellowship repair refused: a store is unavailable or read-only.");
            } else {
                send(sender, EnumChatFormatting.GREEN + "Fellowship repair removed " + describe(report)
                        + ". Removed members are kept in the quarantine.");
                if (!report.isClean()) {
                    FellowshipSyncManager.sendStateToEveryone();
                }
            }
            reportStatus(sender, world);
        } else if ("clearcombat".equalsIgnoreCase(action)) {
            LostTalesMobAggroEventHandler.clearAll();
            send(sender, EnumChatFormatting.GREEN + "Cleared transient server combat-marker state.");
        } else {
            sendUsage(sender);
        }
    }

    private static String describe(FellowshipIntegrityReport report) {
        return report.getMembers() + " member(s), "
                + report.getInvitations() + " invitation(s) and "
                + report.getMarkers() + " go-here marker(s)";
    }

    private void reportStatus(ICommandSender sender, World world) {
        try {
            FellowshipWorldData fellowships = FellowshipStorage.get(world);
            FellowshipInvitationWorldData invitations = FellowshipInvitationStorage.get(world);
            FellowshipGoHereMarkerWorldData markers = FellowshipGoHereMarkerStorage.get(world);
            send(sender, EnumChatFormatting.GOLD + "Fellowship storage status:");
            send(sender, EnumChatFormatting.GRAY + "fellowships=" + fellowships.getFellowshipCount()
                    + ", invitations=" + invitations.getInvitationCount()
                    + ", go_here_markers=" + markers.getMarkers().size());
            send(sender, EnumChatFormatting.GRAY + "quarantine: fellowships=" + fellowships.getQuarantinedEntryCount()
                    + ", invitations=" + invitations.getQuarantinedEntryCount()
                    + ", markers=" + markers.getQuarantinedEntryCount());
            send(sender, EnumChatFormatting.GRAY + "read_only_newer_version: fellowships="
                    + fellowships.isReadOnlyForNewerVersion() + ", invitations="
                    + invitations.isReadOnlyForNewerVersion() + ", markers="
                    + markers.isReadOnlyForNewerVersion());
        } catch (RuntimeException exception) {
            send(sender, EnumChatFormatting.RED + "Unable to inspect fellowship storage: " + exception.getClass().getSimpleName());
        }
    }

    private World resolveWorld(ICommandSender sender) {
        if (sender instanceof EntityPlayerMP) {
            return ((EntityPlayerMP) sender).worldObj;
        }
        return sender == null ? null : sender.getEntityWorld();
    }

    private void sendUsage(ICommandSender sender) {
        send(sender, EnumChatFormatting.GRAY + getCommandUsage(sender));
    }

    private void send(ICommandSender sender, String message) {
        if (sender != null) {
            sender.addChatMessage(new ChatComponentText(message));
        }
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args != null && args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "status", "validate", "repair", "clearcombat");
        }
        return null;
    }
}
