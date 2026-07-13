package com.ninuna.losttales.command;

import com.ninuna.losttales.event.LostTalesMobAggroEventHandler;
import com.ninuna.losttales.party.server.PartyService;
import com.ninuna.losttales.party.storage.PartyGoHereMarkerStorage;
import com.ninuna.losttales.party.storage.PartyGoHereMarkerWorldData;
import com.ninuna.losttales.party.storage.PartyInvitationStorage;
import com.ninuna.losttales.party.storage.PartyInvitationWorldData;
import com.ninuna.losttales.party.storage.PartyStorage;
import com.ninuna.losttales.party.storage.PartyWorldData;
import java.util.List;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

/** Operator-only diagnostics and conservative repair hooks for party state. */
public final class LostTalesCommandPartyAdmin extends LostTalesCommandBase {

    public LostTalesCommandPartyAdmin() {
        super("party");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/losttales party <status|validate|repair|clearcombat>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args == null || args.length == 0) {
            sendUsage(sender);
            return;
        }
        World world = resolveWorld(sender);
        if (world == null || world.isRemote) {
            send(sender, EnumChatFormatting.RED + "Party diagnostics require a running logical server world.");
            return;
        }
        String action = args[0];
        if ("status".equalsIgnoreCase(action) || "dump".equalsIgnoreCase(action)) {
            reportStatus(sender, world);
        } else if ("validate".equalsIgnoreCase(action)) {
            boolean valid = PartyService.getInstance().ensureIntegrity(world);
            send(sender, (valid ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
                    + "Party integrity validation " + (valid ? "completed." : "failed closed; inspect read-only/newer-version data."));
            reportStatus(sender, world);
        } else if ("repair".equalsIgnoreCase(action)) {
            boolean valid = PartyService.getInstance().ensureIntegrity(world);
            int removed = valid ? PartyService.getInstance().pruneInvalidInvitations(world) : -1;
            if (!valid || removed < 0) {
                send(sender, EnumChatFormatting.RED + "Party repair refused because one or more stores are unavailable or read-only.");
            } else {
                send(sender, EnumChatFormatting.GREEN + "Party repair completed; removed " + removed
                        + " stale invitation/marker reference(s). Structural party repairs are quarantined by the existing loader.");
            }
            reportStatus(sender, world);
        } else if ("clearcombat".equalsIgnoreCase(action)) {
            LostTalesMobAggroEventHandler.clearAll();
            send(sender, EnumChatFormatting.GREEN + "Cleared transient server combat-marker state.");
        } else {
            sendUsage(sender);
        }
    }

    private void reportStatus(ICommandSender sender, World world) {
        try {
            PartyWorldData parties = PartyStorage.get(world);
            PartyInvitationWorldData invitations = PartyInvitationStorage.get(world);
            PartyGoHereMarkerWorldData markers = PartyGoHereMarkerStorage.get(world);
            send(sender, EnumChatFormatting.GOLD + "Party storage status:");
            send(sender, EnumChatFormatting.GRAY + "parties=" + parties.getPartyCount()
                    + ", invitations=" + invitations.getInvitationCount()
                    + ", go_here_markers=" + markers.getMarkers().size());
            send(sender, EnumChatFormatting.GRAY + "quarantine: parties=" + parties.getQuarantinedEntryCount()
                    + ", invitations=" + invitations.getQuarantinedEntryCount()
                    + ", markers=" + markers.getQuarantinedEntryCount());
            send(sender, EnumChatFormatting.GRAY + "read_only_newer_version: parties="
                    + parties.isReadOnlyForNewerVersion() + ", invitations="
                    + invitations.isReadOnlyForNewerVersion() + ", markers="
                    + markers.isReadOnlyForNewerVersion());
        } catch (RuntimeException exception) {
            send(sender, EnumChatFormatting.RED + "Unable to inspect party storage: " + exception.getClass().getSimpleName());
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
