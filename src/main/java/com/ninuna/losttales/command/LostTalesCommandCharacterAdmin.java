package com.ninuna.losttales.command;

import com.ninuna.losttales.character.deletion.CharacterDeletionMaintenanceResult;
import com.ninuna.losttales.character.deletion.CharacterDeletionService;
import com.ninuna.losttales.character.deletion.CharacterDeletionStorage;
import com.ninuna.losttales.character.deletion.CharacterDeletionTombstone;
import com.ninuna.losttales.character.deletion.CharacterDeletionWorldData;
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
        return "/losttales character <status|recover|cooldown|freeze|unfreeze|deleted> [player]"
                + " or <restore|rollback|purge> <player> <character-uuid> [confirm]";
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
        } else if ("deleted".equalsIgnoreCase(action)
                || "tombstones".equalsIgnoreCase(action)) {
            reportDeleted(sender, target);
        } else if ("restore".equalsIgnoreCase(action)) {
            UUID characterId = parseCharacterId(sender, args);
            if (characterId == null) {
                return;
            }
            CharacterDeletionMaintenanceResult result =
                    CharacterDeletionService.getInstance().restore(
                            target, characterId);
            reportMaintenanceResult(sender, target, characterId,
                    "restore", result);
        } else if ("rollback".equalsIgnoreCase(action)) {
            UUID characterId = parseCharacterId(sender, args);
            if (characterId == null) {
                return;
            }
            CharacterDeletionMaintenanceResult result =
                    CharacterDeletionService.getInstance().rollbackInactive(
                            target, characterId);
            reportMaintenanceResult(sender, target, characterId,
                    "rollback", result);
        } else if ("purge".equalsIgnoreCase(action)) {
            UUID characterId = parseCharacterId(sender, args);
            if (characterId == null) {
                return;
            }
            if (args.length < 4 || !"confirm".equalsIgnoreCase(args[3])) {
                send(sender, EnumChatFormatting.RED
                        + "Permanent purge requires: /losttales character purge "
                        + target.getCommandSenderName() + " " + characterId
                        + " confirm");
                return;
            }
            CharacterDeletionMaintenanceResult result =
                    CharacterDeletionService.getInstance().purge(
                            target, characterId);
            reportMaintenanceResult(sender, target, characterId,
                    "purge", result);
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
            CharacterDeletionWorldData deletionData =
                    CharacterDeletionStorage.get(world);
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
                    + ", deletionReadOnly="
                    + deletionData.isReadOnlyForNewerVersion()
                    + ", switchOwnerBlocked=" + switchData.isOwnerBlocked(ownerId)
                    + ", playerStateOwnerBlocked="
                    + playerStateData.isOwnerBlocked(ownerId)
                    + ", switchQuarantine=" + switchData.getQuarantinedEntryCount()
                    + ", playerStateQuarantine="
                    + playerStateData.getQuarantinedEntryCount()
                    + ", deletionQuarantine="
                    + deletionData.getQuarantinedEntryCount()
                    + ", recoverableDeletions="
                    + deletionData.getTombstones(ownerId).size());
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

    private void reportDeleted(ICommandSender sender, EntityPlayerMP target) {
        try {
            List<CharacterDeletionTombstone> tombstones =
                    CharacterDeletionService.getInstance().getTombstones(
                            target.worldObj, target.getUniqueID());
            if (tombstones.isEmpty()) {
                send(sender, EnumChatFormatting.GRAY
                        + "No recoverable deletions exist for "
                        + target.getCommandSenderName() + ".");
                return;
            }
            send(sender, EnumChatFormatting.GOLD + "Recoverable deletions for "
                    + target.getCommandSenderName() + ":");
            long now = System.currentTimeMillis();
            for (CharacterDeletionTombstone tombstone : tombstones) {
                String retention = tombstone.isCommitted()
                        ? (tombstone.isPurgeAllowed(now)
                        ? "purge eligible"
                        : "purge in " + formatDuration(
                                tombstone.getPurgeAfter() - now))
                        : "prepared; deletion not committed";
                send(sender, EnumChatFormatting.GRAY
                        + tombstone.getCharacterCopy().getName()
                        + " id=" + tombstone.getCharacterId()
                        + ", slot="
                        + tombstone.getCharacterCopy().getSlotIndex()
                        + ", stateGeneration="
                        + tombstone.getStateGeneration()
                        + ", " + retention);
            }
        } catch (RuntimeException exception) {
            send(sender, EnumChatFormatting.RED
                    + "Unable to inspect recoverable deletions: "
                    + exception.getClass().getSimpleName());
        }
    }

    private UUID parseCharacterId(ICommandSender sender, String[] args) {
        if (args == null || args.length < 3) {
            send(sender, EnumChatFormatting.RED
                    + "Specify an online player and a character UUID.");
            return null;
        }
        try {
            return UUID.fromString(args[2]);
        } catch (IllegalArgumentException exception) {
            send(sender, EnumChatFormatting.RED
                    + "The character UUID is invalid: " + args[2]);
            return null;
        }
    }

    private void reportMaintenanceResult(
            ICommandSender sender,
            EntityPlayerMP target,
            UUID characterId,
            String action,
            CharacterDeletionMaintenanceResult result) {
        if (result == CharacterDeletionMaintenanceResult.SUCCESS) {
            send(sender, EnumChatFormatting.GREEN + "Character " + action
                    + " completed for " + characterId + ".");
            reportStatus(sender, target);
            return;
        }
        if (result == CharacterDeletionMaintenanceResult.RECONCILED) {
            send(sender, EnumChatFormatting.GREEN
                    + "The character already existed; its stale tombstone was removed.");
            return;
        }
        String detail;
        switch (result) {
            case NOT_FOUND:
                detail = "No matching character or tombstone was found.";
                break;
            case STORAGE_READ_ONLY:
                detail = "A required character store is read-only.";
                break;
            case PLAYER_STATE_UNAVAILABLE:
                detail = "The required character player-state generation is unavailable or invalid.";
                break;
            case SLOT_OCCUPIED:
                detail = "The character's original roster slot is occupied.";
                break;
            case CHARACTER_ID_CONFLICT:
                detail = "The character UUID is already present in a roster.";
                break;
            case CHARACTER_ACTIVE:
                detail = "Switch away from the character before rolling back its generation.";
                break;
            case PREVIOUS_GENERATION_UNAVAILABLE:
                detail = "No retained previous generation is available.";
                break;
            case RETENTION_ACTIVE:
                CharacterDeletionTombstone tombstone =
                        CharacterDeletionService.getInstance().getTombstone(
                                target.worldObj, characterId);
                detail = tombstone == null
                        ? "The recovery retention period is still active."
                        : "The recovery retention period remains active for "
                        + formatDuration(tombstone.getPurgeAfter()
                                - System.currentTimeMillis()) + ".";
                break;
            case NOT_COMMITTED:
                detail = "The prepared deletion never committed; restore or retry normal deletion instead.";
                break;
            default:
                detail = "The operation failed internally; inspect the server log.";
                break;
        }
        send(sender, EnumChatFormatting.RED + "Character " + action
                + " failed: " + detail);
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
                    args, "status", "recover", "cooldown", "freeze", "unfreeze",
                    "deleted", "restore", "rollback", "purge");
        }
        if (args != null && args.length == 2) {
            return getListOfStringsMatchingLastWord(args,
                    MinecraftServer.getServer().getAllUsernames());
        }
        if (args != null && args.length == 4
                && "purge".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "confirm");
        }
        return null;
    }
}
