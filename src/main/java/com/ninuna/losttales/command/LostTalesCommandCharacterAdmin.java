package com.ninuna.losttales.command;

import com.ninuna.losttales.character.deletion.CharacterDeletionMaintenanceResult;
import com.ninuna.losttales.character.deletion.CharacterDeletionService;
import com.ninuna.losttales.character.deletion.CharacterDeletionStorage;
import com.ninuna.losttales.character.deletion.CharacterDeletionTombstone;
import com.ninuna.losttales.character.deletion.CharacterDeletionWorldData;
import com.ninuna.losttales.character.lore.LoreCharacterDefinition;
import com.ninuna.losttales.character.lore.LoreCharacterRegistry;
import com.ninuna.losttales.character.lore.ownership.LoreCharacterOwnershipRecord;
import com.ninuna.losttales.character.lore.ownership.LoreCharacterOwnershipStorage;
import com.ninuna.losttales.character.lore.ownership.LoreCharacterOwnershipWorldData;
import com.ninuna.losttales.character.lore.transfer.LoreCharacterTransferCoordinator;
import com.ninuna.losttales.character.lore.transfer.LoreCharacterTransferRecord;
import com.ninuna.losttales.character.lore.transfer.LoreCharacterTransferStorage;
import com.ninuna.losttales.character.lore.transfer.LoreCharacterTransferWorldData;
import com.ninuna.losttales.character.lore.transfer.LoreCharacterVaultEntry;
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
import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.util.LostTalesDuration;
import java.util.List;
import java.util.UUID;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

/**
 * Operator-only switch-state diagnostics and recovery controls. The
 * status lines name each store's fields as the code does and show their
 * values as they are: ids, numbers, true and false.
 */
public final class LostTalesCommandCharacterAdmin extends LostTalesCommandBase {

    /** What the lang key of each of the command's answers begins with. */
    static final String SAY = "chat.losttales.command.character.";

    public LostTalesCommandCharacterAdmin() {
        super("character");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/losttales character <status|recover|cooldown|freeze|unfreeze|deleted> [player]"
                + " or <restore|rollback|purge> <player> <character-uuid> [confirm]"
                + " or discard-journal <player|account-uuid>"
                + " or lore <status|recover|inspect> [lore-character-id]";
    }

    @Override
    public LostTalesCapability getCapability() {
        return LostTalesCapability.CHARACTER_ADMIN;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args == null || args.length == 0) {
            sendUsage(sender);
            return;
        }
        if ("lore".equalsIgnoreCase(args[0])) {
            processLoreCommand(sender, args);
            return;
        }
        // Answered before a player is resolved: the account this repairs is
        // usually the one that cannot stay connected.
        if ("discard-journal".equalsIgnoreCase(args[0])) {
            processDiscardJournal(sender, args);
            return;
        }
        EntityPlayerMP target = resolveTarget(sender, args.length > 1 ? args[1] : null);
        if (target == null) {
            say(sender, EnumChatFormatting.RED, PLAYER_REQUIRED);
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
                say(sender, EnumChatFormatting.GREEN, SAY + "recover.done");
            } else {
                say(sender, EnumChatFormatting.RED, SAY + "recover.failed", result.getId());
            }
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
                    SAY + "restore.done", SAY + "restore.failed", result);
        } else if ("rollback".equalsIgnoreCase(action)) {
            UUID characterId = parseCharacterId(sender, args);
            if (characterId == null) {
                return;
            }
            CharacterDeletionMaintenanceResult result =
                    CharacterDeletionService.getInstance().rollbackInactive(
                            target, characterId);
            reportMaintenanceResult(sender, target, characterId,
                    SAY + "rollback.done", SAY + "rollback.failed", result);
        } else if ("purge".equalsIgnoreCase(action)) {
            UUID characterId = parseCharacterId(sender, args);
            if (characterId == null) {
                return;
            }
            if (args.length < 4 || !"confirm".equalsIgnoreCase(args[3])) {
                say(sender, EnumChatFormatting.RED, SAY + "purge.confirm",
                        target.getCommandSenderName(), characterId);
                return;
            }
            CharacterDeletionMaintenanceResult result =
                    CharacterDeletionService.getInstance().purge(
                            target, characterId);
            reportMaintenanceResult(sender, target, characterId,
                    SAY + "purge.done", SAY + "purge.failed", result);
        } else if ("cooldown".equalsIgnoreCase(action)
                || "resetcooldown".equalsIgnoreCase(action)) {
            boolean reset = CharacterSwitchCoordinator.getInstance().resetCooldown(
                    target.worldObj, target.getUniqueID());
            if (reset) {
                say(sender, EnumChatFormatting.GREEN, SAY + "cooldown.done");
            } else {
                say(sender, EnumChatFormatting.RED, SAY + "cooldown.failed");
            }
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

    /**
     * Discards one account's switch journal and thaws the account, naming
     * the account by an online player's name or by its UUID — the id the
     * server log prints beside every switch failure — so an account that
     * is disconnected or refused at login can be repaired while offline.
     */
    private void processDiscardJournal(ICommandSender sender, String[] args) {
        MinecraftServer server = MinecraftServer.getServer();
        World world = server == null ? null : server.worldServerForDimension(0);
        if (world == null) {
            say(sender, EnumChatFormatting.RED, SAY + "no_overworld");
            return;
        }
        if (args.length < 2) {
            say(sender, EnumChatFormatting.RED, SAY + "journal.name");
            return;
        }
        EntityPlayerMP online = resolveTarget(sender, args[1]);
        UUID ownerId;
        if (online != null) {
            ownerId = online.getUniqueID();
        } else {
            try {
                ownerId = UUID.fromString(args[1]);
            } catch (IllegalArgumentException exception) {
                say(sender, EnumChatFormatting.RED, SAY + "journal.unknown", args[1]);
                return;
            }
        }

        CharacterSwitchCoordinator.JournalDiscard outcome =
                CharacterSwitchCoordinator.getInstance().discardJournal(world, ownerId);
        if (outcome == CharacterSwitchCoordinator.JournalDiscard.NONE) {
            say(sender, EnumChatFormatting.YELLOW, SAY + "journal.none");
            return;
        }
        if (outcome == CharacterSwitchCoordinator.JournalDiscard.UNAVAILABLE) {
            say(sender, EnumChatFormatting.RED, SAY + "journal.refused");
            return;
        }
        say(sender, EnumChatFormatting.GREEN, SAY + "journal.discarded", ownerId);
        if (online != null) {
            // Already connected: switching becomes available again without
            // making them reconnect.
            CharacterLifecycleStateTracker.markReady(online);
            reportStatus(sender, online);
        } else {
            say(sender, EnumChatFormatting.GRAY, SAY + "journal.next_join");
        }
    }

    private void processLoreCommand(ICommandSender sender, String[] args) {
        MinecraftServer server = MinecraftServer.getServer();
        World world = server == null ? null : server.worldServerForDimension(0);
        if (world == null) {
            say(sender, EnumChatFormatting.RED, SAY + "no_overworld");
            return;
        }
        String action = args.length > 1 ? args[1] : "status";
        if ("status".equalsIgnoreCase(action)
                || "list".equalsIgnoreCase(action)) {
            reportLoreStatus(sender, world);
            return;
        }
        if ("recover".equalsIgnoreCase(action)) {
            LoreCharacterTransferCoordinator.getInstance().recoverAll(world);
            say(sender, EnumChatFormatting.GREEN, SAY + "lore.recovered");
            reportLoreStatus(sender, world);
            return;
        }
        if ("inspect".equalsIgnoreCase(action) && args.length > 2) {
            reportLoreCharacter(sender, world, args[2]);
            return;
        }
        usage(sender, "/losttales character lore <status|recover|inspect>"
                + " [lore-character-id]");
    }

    private void reportLoreStatus(ICommandSender sender, World world) {
        try {
            LoreCharacterOwnershipWorldData ownership =
                    LoreCharacterOwnershipStorage.get(world);
            LoreCharacterTransferWorldData transfers =
                    LoreCharacterTransferStorage.get(world);
            int configured = 0;
            for (LoreCharacterDefinition definition
                    : LoreCharacterRegistry.getAll()) {
                if (definition.hasAppearance()) configured++;
            }
            int claimed = 0;
            for (LoreCharacterOwnershipRecord record : ownership.getRecords()) {
                if (record.isClaimed()) claimed++;
            }
            say(sender, EnumChatFormatting.GOLD, SAY + "lore.status.header");
            say(sender, EnumChatFormatting.GRAY, SAY + "lore.status.counts",
                    Integer.valueOf(LoreCharacterRegistry.getAll().size()),
                    Integer.valueOf(configured),
                    Integer.valueOf(LoreCharacterRegistry.getLoadErrors().size()),
                    Integer.valueOf(ownership.getRecordCount()),
                    Integer.valueOf(claimed),
                    Integer.valueOf(transfers.getVaultEntryCount()),
                    Integer.valueOf(transfers.getTransactions().size()));
            say(sender, EnumChatFormatting.GRAY, SAY + "lore.status.stores",
                    Boolean.valueOf(ownership.isReadOnly()),
                    reason(ownership.getReadOnlyReason()),
                    Boolean.valueOf(transfers.isReadOnly()),
                    reason(transfers.getReadOnlyReason()));
            for (LoreCharacterTransferRecord transaction
                    : transfers.getTransactions()) {
                say(sender, EnumChatFormatting.YELLOW, SAY + "lore.status.pending",
                        transaction.getLoreCharacterId(), transaction.getType(),
                        transaction.getStep(), transaction.getCharacterId());
            }
        } catch (RuntimeException exception) {
            say(sender, EnumChatFormatting.RED, SAY + "lore.status.failed",
                    exception.getClass().getSimpleName());
        }
    }

    private void reportLoreCharacter(
            ICommandSender sender, World world, String loreId) {
        try {
            LoreCharacterDefinition definition = LoreCharacterRegistry.get(loreId);
            LoreCharacterOwnershipWorldData ownership =
                    LoreCharacterOwnershipStorage.get(world);
            LoreCharacterTransferWorldData transfers =
                    LoreCharacterTransferStorage.get(world);
            if (definition == null) {
                say(sender, EnumChatFormatting.RED, SAY + "lore.unknown", loreId);
                return;
            }
            LoreCharacterOwnershipRecord owner = ownership.getRecord(
                    definition.getId());
            LoreCharacterVaultEntry vault = transfers.getVaultEntry(
                    definition.getId());
            LoreCharacterTransferRecord transaction = transfers.getTransaction(
                    definition.getId());
            say(sender, EnumChatFormatting.GOLD, SAY + "lore.header",
                    definition.getName(), definition.getId());
            say(sender, EnumChatFormatting.GRAY, SAY + "lore.ownership",
                    Boolean.valueOf(definition.hasAppearance()),
                    Boolean.valueOf(owner != null && owner.isClaimed()),
                    owner == null ? words(SAY + "none") : owner.getOwnerId(),
                    owner == null ? words(SAY + "none") : owner.getCharacterId(),
                    Long.valueOf(owner == null ? 0L : owner.getRevision()));
            Object transfer = transaction == null ? words(SAY + "none")
                    : words(SAY + "lore.transfer", transaction.getType(),
                            transaction.getStep(), transaction.getTransactionId());
            if (vault == null) {
                say(sender, EnumChatFormatting.GRAY, SAY + "lore.state", Boolean.FALSE,
                        transfer);
            } else {
                say(sender, EnumChatFormatting.GRAY, SAY + "lore.state.retained", Boolean.TRUE,
                        Long.valueOf(vault.getPlayerStateCopy().getCurrentGeneration()),
                        Long.valueOf(vault.getUpdatedAt()), transfer);
            }
        } catch (RuntimeException exception) {
            say(sender, EnumChatFormatting.RED, SAY + "lore.failed",
                    exception.getClass().getSimpleName());
        }
    }

    /** A store's read-only reason in brackets after its flag, or nothing. */
    private static String reason(String value) {
        return value == null || value.length() == 0 ? "" : " (" + value + ")";
    }

    private void setFrozen(ICommandSender sender, EntityPlayerMP target, boolean frozen) {
        boolean changed = CharacterSwitchCoordinator.getInstance().setFrozen(
                target.worldObj, target.getUniqueID(), frozen);
        if (!changed) {
            say(sender, EnumChatFormatting.RED, SAY + "freeze.failed");
        } else {
            say(sender, EnumChatFormatting.GREEN, frozen ? SAY + "freeze.done"
                    : SAY + "unfreeze.done");
        }
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

            say(sender, EnumChatFormatting.GOLD, SAY + "status.header",
                    target.getCommandSenderName());
            say(sender, EnumChatFormatting.GRAY, SAY + "status.owner", ownerId,
                    roster == null || roster.getActiveCharacterId() == null
                            ? words(SAY + "status.account") : roster.getActiveCharacterId(),
                    Long.valueOf(roster == null ? -1L : roster.getRevision()));
            say(sender, EnumChatFormatting.GRAY, SAY + "status.stores",
                    Boolean.valueOf(characterData.isReadOnlyForNewerVersion()),
                    Boolean.valueOf(switchData.isReadOnlyForNewerVersion()),
                    Boolean.valueOf(playerStateData.isReadOnlyForNewerVersion()),
                    Boolean.valueOf(deletionData.isReadOnlyForNewerVersion()),
                    Boolean.valueOf(switchData.isOwnerBlocked(ownerId)),
                    Boolean.valueOf(playerStateData.isOwnerBlocked(ownerId)),
                    Integer.valueOf(switchData.getQuarantinedEntryCount()),
                    Integer.valueOf(playerStateData.getQuarantinedEntryCount()),
                    Integer.valueOf(deletionData.getQuarantinedEntryCount()),
                    Integer.valueOf(deletionData.getTombstones(ownerId).size()));
            if (playerState == null) {
                say(sender, EnumChatFormatting.GRAY, SAY + "status.player_state.none");
            } else {
                CharacterPlayerStateRecord activeRecord = playerState.getRecord(
                        roster == null ? ownerId : roster.getActiveGameplayId());
                say(sender, EnumChatFormatting.GRAY, SAY + "status.player_state",
                        Integer.valueOf(playerState.getBootstrapVersion()),
                        Integer.valueOf(playerState.getRecords().size()),
                        Long.valueOf(activeRecord == null ? -1L
                                : activeRecord.getCurrentGeneration()));
            }
            if (state == null) {
                say(sender, EnumChatFormatting.GRAY, SAY + "status.no_manifest");
                return;
            }
            long remaining = Math.max(0L,
                    state.getNextAllowedAt() - System.currentTimeMillis());
            say(sender, EnumChatFormatting.GRAY, SAY + "status.cooldown",
                    Integer.valueOf(state.getCooldownStage()), formatDuration(remaining),
                    Long.valueOf(state.getNextAllowedAt()), Boolean.valueOf(state.isFrozen()),
                    Boolean.valueOf(state.isDeathPending()),
                    Long.valueOf(state.getDeathPendingAt()));
            CharacterSwitchTransaction transaction = state.getTransaction();
            if (transaction == null) {
                say(sender, EnumChatFormatting.GRAY, SAY + "status.journal.none");
            } else {
                say(sender, EnumChatFormatting.GRAY, SAY + "status.journal",
                        transaction.getStatus().getId(), transaction.getTransactionId(),
                        transaction.getSourceCharacterId(),
                        transaction.getTargetCharacterId(),
                        Long.valueOf(transaction.getSourceStateGeneration()),
                        Long.valueOf(transaction.getTargetStateGeneration()),
                        Long.valueOf(transaction.getPreparedAt()),
                        Long.valueOf(transaction.getCompletedAt()));
            }
        } catch (RuntimeException exception) {
            say(sender, EnumChatFormatting.RED, SAY + "status.failed",
                    exception.getClass().getSimpleName());
        }
    }

    private void reportDeleted(ICommandSender sender, EntityPlayerMP target) {
        try {
            List<CharacterDeletionTombstone> tombstones =
                    CharacterDeletionService.getInstance().getTombstones(
                            target.worldObj, target.getUniqueID());
            if (tombstones.isEmpty()) {
                say(sender, EnumChatFormatting.GRAY, SAY + "deleted.none",
                        target.getCommandSenderName());
                return;
            }
            say(sender, EnumChatFormatting.GOLD, SAY + "deleted.header",
                    target.getCommandSenderName());
            long now = System.currentTimeMillis();
            for (CharacterDeletionTombstone tombstone : tombstones) {
                IChatComponent retention = tombstone.isCommitted()
                        ? (tombstone.isPurgeAllowed(now)
                        ? words(SAY + "deleted.purge_eligible")
                        : words(SAY + "deleted.purge_in", formatDuration(
                                tombstone.getPurgeAfter() - now)))
                        : words(SAY + "deleted.not_committed");
                say(sender, EnumChatFormatting.GRAY, SAY + "deleted.entry",
                        tombstone.getCharacterCopy().getName(),
                        tombstone.getCharacterId(),
                        Integer.valueOf(tombstone.getCharacterCopy().getSlotIndex()),
                        Long.valueOf(tombstone.getStateGeneration()), retention);
            }
        } catch (RuntimeException exception) {
            say(sender, EnumChatFormatting.RED, SAY + "deleted.failed",
                    exception.getClass().getSimpleName());
        }
    }

    private UUID parseCharacterId(ICommandSender sender, String[] args) {
        if (args == null || args.length < 3) {
            say(sender, EnumChatFormatting.RED, SAY + "id.missing");
            return null;
        }
        try {
            return UUID.fromString(args[2]);
        } catch (IllegalArgumentException exception) {
            say(sender, EnumChatFormatting.RED, SAY + "id.invalid", args[2]);
            return null;
        }
    }

    /**
     * The answer to a restore, rollback or purge: {@code doneKey} with the
     * character's id, or {@code failedKey} with why.
     */
    private void reportMaintenanceResult(
            ICommandSender sender,
            EntityPlayerMP target,
            UUID characterId,
            String doneKey,
            String failedKey,
            CharacterDeletionMaintenanceResult result) {
        if (result == CharacterDeletionMaintenanceResult.SUCCESS) {
            say(sender, EnumChatFormatting.GREEN, doneKey, characterId);
            reportStatus(sender, target);
            return;
        }
        if (result == CharacterDeletionMaintenanceResult.RECONCILED) {
            say(sender, EnumChatFormatting.GREEN, SAY + "reconciled");
            return;
        }
        IChatComponent detail;
        switch (result) {
            case NOT_FOUND:
                detail = words(SAY + "failure.not_found");
                break;
            case STORAGE_READ_ONLY:
                detail = words(SAY + "failure.read_only");
                break;
            case PLAYER_STATE_UNAVAILABLE:
                detail = words(SAY + "failure.player_state");
                break;
            case SLOT_OCCUPIED:
                detail = words(SAY + "failure.slot_occupied");
                break;
            case NAME_TAKEN:
                detail = words(SAY + "failure.name_taken");
                break;
            case CHARACTER_ID_CONFLICT:
                detail = words(SAY + "failure.id_conflict");
                break;
            case CHARACTER_ACTIVE:
                detail = words(SAY + "failure.active");
                break;
            case PREVIOUS_GENERATION_UNAVAILABLE:
                detail = words(SAY + "failure.no_previous");
                break;
            case RETENTION_ACTIVE:
                CharacterDeletionTombstone tombstone =
                        CharacterDeletionService.getInstance().getTombstone(
                                target.worldObj, characterId);
                detail = tombstone == null
                        ? words(SAY + "failure.retention")
                        : words(SAY + "failure.retention.for", formatDuration(
                                tombstone.getPurgeAfter() - System.currentTimeMillis()));
                break;
            case NOT_COMMITTED:
                detail = words(SAY + "failure.not_committed");
                break;
            default:
                detail = words(SAY + "failure.internal");
                break;
        }
        say(sender, EnumChatFormatting.RED, failedKey, detail);
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

    /** A wait as the reader's game words it: every part from the largest there is. */
    private static IChatComponent formatDuration(long millis) {
        long totalSeconds = (Math.max(0L, millis) + 999L) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return LostTalesDuration.of(hours, LostTalesDuration.Unit.HOURS)
                    .and(minutes, LostTalesDuration.Unit.MINUTES)
                    .and(seconds, LostTalesDuration.Unit.SECONDS).component();
        }
        if (minutes > 0L) {
            return LostTalesDuration.of(minutes, LostTalesDuration.Unit.MINUTES)
                    .and(seconds, LostTalesDuration.Unit.SECONDS).component();
        }
        return LostTalesDuration.of(seconds, LostTalesDuration.Unit.SECONDS)
                .component();
    }

    private void sendUsage(ICommandSender sender) {
        usage(sender, getCommandUsage(sender));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args != null && args.length == 1) {
            return getListOfStringsMatchingLastWord(
                    args, "status", "recover", "cooldown", "freeze", "unfreeze",
                    "deleted", "restore", "rollback", "purge", "discard-journal",
                    "lore");
        }
        if (args != null && args.length == 2
                && "lore".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(
                    args, "status", "recover", "inspect");
        }
        if (args != null && args.length == 3
                && "lore".equalsIgnoreCase(args[0])
                && "inspect".equalsIgnoreCase(args[1])) {
            java.util.ArrayList<String> ids = new java.util.ArrayList<String>();
            for (LoreCharacterDefinition definition
                    : LoreCharacterRegistry.getAll()) {
                ids.add(definition.getId());
            }
            return getListOfStringsMatchingLastWord(
                    args, ids.toArray(new String[ids.size()]));
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
