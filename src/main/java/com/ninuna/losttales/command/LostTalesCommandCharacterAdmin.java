package com.ninuna.losttales.command;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.state.CharacterPlayerStateAccount;
import com.ninuna.losttales.character.state.CharacterPlayerStateRecord;
import com.ninuna.losttales.character.state.CharacterPlayerStateStorage;
import com.ninuna.losttales.character.state.CharacterPlayerStateWorldData;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.character.switching.CharacterLifecycleStateTracker;
import com.ninuna.losttales.character.switching.CharacterSwitchAccountState;
import com.ninuna.losttales.character.switching.CharacterSwitchCoordinator;
import com.ninuna.losttales.character.switching.CharacterSwitchStorage;
import com.ninuna.losttales.character.switching.CharacterSwitchTransaction;
import com.ninuna.losttales.character.switching.CharacterSwitchWorldData;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import java.util.List;
import java.util.UUID;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

/** Operator-only switch-state diagnostics and recovery controls. */
public final class LostTalesCommandCharacterAdmin extends LostTalesCommandBase {

    public LostTalesCommandCharacterAdmin() {
        super("character");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/losttales character <status|recover|cooldown|freeze|unfreeze> [player]";
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
        EntityPlayerMP target = resolveTarget(sender, args.length > 1 ? args[1] : null);
        if (target == null) {
            send(sender, EnumChatFormatting.RED
                    + "Specify an online player when running this command from the console.");
            return;
        }
        String action = args[0];
        if ("status".equalsIgnoreCase(action) || "inspect".equalsIgnoreCase(action)) {
            reportStatus(sender, target);
        } else if ("recover".equalsIgnoreCase(action)) {
            CharacterSwitchCoordinator coordinator =
                    CharacterSwitchCoordinator.getInstance();
            CharacterErrorId result = coordinator.recover(target);
            if (result == CharacterErrorId.SWITCH_DEATH_PENDING
                    && target.isEntityAlive() && !target.isDead
                    && target.getHealth() > 0.0F) {
                // Explicit operator recovery for a missed respawn event. The
                // currently live post-respawn state becomes authoritative; a
                // pre-death snapshot is never restored.
                result = coordinator.handleRespawn(target);
            }
            if (result == CharacterErrorId.NONE) {
                CharacterLifecycleStateTracker.markReady(target);
            }
            send(sender, result == CharacterErrorId.NONE
                    ? EnumChatFormatting.GREEN + "Character switch state reconciled."
                    : EnumChatFormatting.RED + "Recovery did not complete: " + result.getId());
            reportStatus(sender, target);
        } else if ("cooldown".equalsIgnoreCase(action)
                || "resetcooldown".equalsIgnoreCase(action)) {
            boolean reset = CharacterSwitchCoordinator.getInstance().resetCooldown(
                    target.worldObj, target.getUniqueID());
            send(sender, reset
                    ? EnumChatFormatting.GREEN + "Character switch cooldown reset."
                    : EnumChatFormatting.RED + "Unable to reset character switch cooldown.");
            reportStatus(sender, target);
        } else if ("freeze".equalsIgnoreCase(action)) {
            setFrozen(sender, target, true);
        } else if ("unfreeze".equalsIgnoreCase(action)
                || "thaw".equalsIgnoreCase(action)) {
            setFrozen(sender, target, false);
        } else {
            sendUsage(sender);
        }
    }

    private void setFrozen(ICommandSender sender, EntityPlayerMP target, boolean frozen) {
        boolean changed = CharacterSwitchCoordinator.getInstance().setFrozen(
                target.worldObj, target.getUniqueID(), frozen);
        send(sender, changed
                ? EnumChatFormatting.GREEN + "Character switching "
                        + (frozen ? "frozen." : "unfrozen.")
                : EnumChatFormatting.RED + "Unable to update character switch freeze state.");
        reportStatus(sender, target);
    }

    private void reportStatus(ICommandSender sender, EntityPlayerMP target) {
        try {
            World world = target.worldObj;
            UUID ownerId = target.getUniqueID();
            CharacterWorldData characterData = CharacterStorage.get(world);
            CharacterSwitchWorldData switchData = CharacterSwitchStorage.get(world);
            CharacterPlayerStateWorldData playerStateData =
                    CharacterPlayerStateStorage.get(world, ownerId);
            CharacterRoster roster = characterData.getRoster(ownerId);
            CharacterSwitchAccountState state = switchData.getAccount(ownerId);
            CharacterPlayerStateAccount playerState =
                    playerStateData.getAccount(ownerId);

            send(sender, EnumChatFormatting.GOLD + "Character switch status for "
                    + target.getCommandSenderName() + ":");
            send(sender, EnumChatFormatting.GRAY + "owner=" + ownerId
                    + ", active=" + (roster == null ? "none" : roster.getActiveCharacterId())
                    + ", rosterRevision=" + (roster == null ? -1L : roster.getRevision()));
            send(sender, EnumChatFormatting.GRAY + "stores: rosterReadOnly="
                    + characterData.isReadOnlyForNewerVersion()
                    + ", switchReadOnly=" + switchData.isReadOnlyForNewerVersion()
                    + ", playerStateReadOnly="
                    + playerStateData.isReadOnlyForNewerVersion()
                    + ", switchOwnerBlocked=" + switchData.isOwnerBlocked(ownerId)
                    + ", playerStateOwnerBlocked="
                    + playerStateData.isOwnerBlocked(ownerId)
                    + ", switchQuarantine=" + switchData.getQuarantinedEntryCount()
                    + ", playerStateQuarantine="
                    + playerStateData.getQuarantinedEntryCount());
            if (playerState == null) {
                send(sender, EnumChatFormatting.GRAY
                        + "playerState=not bootstrapped");
            } else {
                CharacterPlayerStateRecord activeRecord = roster == null
                        ? null : playerState.getRecord(roster.getActiveCharacterId());
                send(sender, EnumChatFormatting.GRAY + "playerStateBootstrap="
                        + playerState.getBootstrapVersion()
                        + ", records=" + playerState.getRecords().size()
                        + ", activeGeneration="
                        + (activeRecord == null ? -1L
                        : activeRecord.getCurrentGeneration()));
            }
            if (state == null) {
                send(sender, EnumChatFormatting.GRAY + "No switch manifest has been created yet.");
                return;
            }
            long remaining = Math.max(0L,
                    state.getNextAllowedAt() - System.currentTimeMillis());
            send(sender, EnumChatFormatting.GRAY + "cooldownStage="
                    + state.getCooldownStage()
                    + ", remaining=" + formatDuration(remaining)
                    + ", nextAllowedAt=" + state.getNextAllowedAt()
                    + ", frozen=" + state.isFrozen()
                    + ", deathPending=" + state.isDeathPending()
                    + ", deathPendingAt=" + state.getDeathPendingAt());
            CharacterSwitchTransaction transaction = state.getTransaction();
            if (transaction == null) {
                send(sender, EnumChatFormatting.GRAY + "journal=none");
            } else {
                send(sender, EnumChatFormatting.GRAY + "journal="
                        + transaction.getStatus().getId()
                        + ", tx=" + transaction.getTransactionId()
                        + ", source=" + transaction.getSourceCharacterId()
                        + ", target=" + transaction.getTargetCharacterId()
                        + ", sourceState="
                        + transaction.getSourceStateGeneration()
                        + ", targetState="
                        + transaction.getTargetStateGeneration()
                        + ", preparedAt=" + transaction.getPreparedAt()
                        + ", completedAt=" + transaction.getCompletedAt());
            }
        } catch (RuntimeException exception) {
            send(sender, EnumChatFormatting.RED + "Unable to inspect character state: "
                    + exception.getClass().getSimpleName());
        }
    }

    private EntityPlayerMP resolveTarget(ICommandSender sender, String playerName) {
        if (playerName != null && playerName.length() > 0) {
            try {
                return getPlayer(sender, playerName);
            } catch (RuntimeException exception) {
                return null;
            }
        }
        return sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
    }

    private static String formatDuration(long millis) {
        long totalSeconds = (Math.max(0L, millis) + 999L) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m " + seconds + "s";
        }
        if (minutes > 0L) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
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
            return getListOfStringsMatchingLastWord(
                    args, "status", "recover", "cooldown", "freeze", "unfreeze");
        }
        if (args != null && args.length == 2) {
            return getListOfStringsMatchingLastWord(args,
                    MinecraftServer.getServer().getAllUsernames());
        }
        return null;
    }
}
