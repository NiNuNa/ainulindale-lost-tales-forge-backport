package com.ninuna.losttales.character.switching;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.server.CharacterOperationResult;
import com.ninuna.losttales.character.server.CharacterRaceGameplayHandler;
import com.ninuna.losttales.character.state.CharacterPlayerStateAccount;
import com.ninuna.losttales.character.state.CharacterPlayerStateService;
import com.ninuna.losttales.character.state.CharacterPlayerStateSnapshot;
import com.ninuna.losttales.character.state.CharacterPlayerStateStorage;
import com.ninuna.losttales.character.state.CharacterPlayerStateWorldData;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.character.validation.CharacterValidationResult;
import com.ninuna.losttales.character.validation.CharacterValidator;
import com.ninuna.losttales.config.LostTalesConfig;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Transaction coordinator for active-character identity and versioned player state. */
public final class CharacterSwitchCoordinator {

    private static final int MAX_CACHED_REQUESTS = 32;
    private static final CharacterSwitchCoordinator INSTANCE =
            new CharacterSwitchCoordinator(new DefaultCharacterSwitchPolicy());

    private final CharacterSwitchPolicy policy;
    private final CharacterPlayerStateService playerStateService =
            CharacterPlayerStateService.getInstance();
    private final ConcurrentMap<UUID, Object> accountLocks =
            new ConcurrentHashMap<UUID, Object>();
    private final ConcurrentMap<UUID, RequestCache> requestCaches =
            new ConcurrentHashMap<UUID, RequestCache>();

    public CharacterSwitchCoordinator(CharacterSwitchPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        this.policy = policy;
    }

    public static CharacterSwitchCoordinator getInstance() {
        return INSTANCE;
    }

    public CharacterOperationResult selectCharacter(EntityPlayerMP player,
                                                      int requestId,
                                                      long expectedRosterRevision,
                                                      UUID targetCharacterId) {
        if (!isServerPlayer(player)) {
            return CharacterOperationResult.failure(CharacterErrorId.INVALID_PLAYER, null);
        }
        UUID ownerId = player.getUniqueID();
        synchronized (getAccountLock(ownerId)) {
            long requestEpoch = CharacterLifecycleStateTracker.captureRequestEpoch(player);
            CharacterOperationResult cached = getCached(ownerId, requestEpoch, requestId);
            if (cached != null) {
                return cached;
            }
            CharacterOperationResult result = selectLocked(
                    player, requestId, requestEpoch,
                    expectedRosterRevision, targetCharacterId);
            cache(ownerId, requestEpoch, requestId, result);
            return result;
        }
    }

    /** Login/admin recovery: reconcile journals and restore an interrupted switch. */
    public CharacterErrorId recover(EntityPlayerMP player) {
        if (!isServerPlayer(player)) {
            return CharacterErrorId.INVALID_PLAYER;
        }
        synchronized (getAccountLock(player.getUniqueID())) {
            try {
                Stores stores = loadStores(player.worldObj, player.getUniqueID());
                CharacterErrorId availability = stores.check(player.getUniqueID());
                if (availability != CharacterErrorId.NONE) {
                    return availability;
                }
                CharacterRoster roster = stores.rosters.getOrCreateRoster(
                        player.getUniqueID());
                CharacterSwitchAccountState account =
                        stores.switches.getOrCreateAccount(player.getUniqueID());

                if (!player.isEntityAlive() || player.isDead
                        || player.getHealth() <= 0.0F) {
                    account.markDeathPending(System.currentTimeMillis());
                    stores.switches.saveAccount(account);
                }

                CharacterErrorId journalResult = recoverJournalLocked(
                        player, roster, account, stores, true);
                if (journalResult != CharacterErrorId.NONE) {
                    return journalResult;
                }
                if (account.isDeathPending()) {
                    CharacterSwitchStorage.flush(player.worldObj);
                    return CharacterErrorId.SWITCH_DEATH_PENDING;
                }
                if (roster.getCharacterCount() > 0
                        && roster.getActiveCharacterId() == null) {
                    CharacterSwitchStorage.flush(player.worldObj);
                    return CharacterErrorId.SWITCH_STATE_IMPORT_REQUIRED;
                }
                this.playerStateService.ensureBootstrapped(
                        player, roster, stores.playerStates, null);
                CharacterSwitchStorage.flush(player.worldObj);
                return CharacterErrorId.NONE;
            } catch (CharacterStateValidationException exception) {
                logFailure(player, "recovery_player_state", exception);
                return CharacterErrorId.SWITCH_PLAYER_STATE_INVALID;
            } catch (RuntimeException exception) {
                logFailure(player, "recovery", exception);
                return CharacterErrorId.INTERNAL_ERROR;
            }
        }
    }

    /** Respawn finalization stores the post-death vanilla state; it never restores pre-death data. */
    public CharacterErrorId handleRespawn(EntityPlayerMP player) {
        if (!isServerPlayer(player)) {
            return CharacterErrorId.INVALID_PLAYER;
        }
        synchronized (getAccountLock(player.getUniqueID())) {
            try {
                Stores stores = loadStores(player.worldObj, player.getUniqueID());
                CharacterErrorId availability = stores.check(player.getUniqueID());
                if (availability != CharacterErrorId.NONE) {
                    return availability;
                }
                CharacterRoster roster = stores.rosters.getOrCreateRoster(
                        player.getUniqueID());
                CharacterSwitchAccountState account =
                        stores.switches.getOrCreateAccount(player.getUniqueID());
                CharacterErrorId journalResult = recoverJournalLocked(
                        player, roster, account, stores, false);
                if (journalResult != CharacterErrorId.NONE) {
                    return journalResult;
                }
                if (roster.getCharacterCount() > 0
                        && roster.getActiveCharacterId() == null) {
                    return CharacterErrorId.SWITCH_STATE_IMPORT_REQUIRED;
                }
                this.playerStateService.ensureBootstrapped(
                        player, roster, stores.playerStates, null);
                this.playerStateService.saveActiveLiveState(
                        player, roster, stores.playerStates, false);
                account.clearDeathPending();
                stores.switches.saveAccount(account);
                CharacterSwitchStorage.flush(player.worldObj);
                CharacterRaceGameplayHandler.apply(player);
                this.playerStateService.synchronize(player);
                return CharacterErrorId.NONE;
            } catch (CharacterStateValidationException exception) {
                logFailure(player, "respawn_player_state", exception);
                return CharacterErrorId.SWITCH_PLAYER_STATE_INVALID;
            } catch (RuntimeException exception) {
                logFailure(player, "respawn", exception);
                return CharacterErrorId.INTERNAL_ERROR;
            }
        }
    }

    /** Dimension replacement reconciles metadata but keeps the live entity state. */
    public CharacterErrorId handleDimensionChange(EntityPlayerMP player) {
        if (!isServerPlayer(player)) {
            return CharacterErrorId.INVALID_PLAYER;
        }
        synchronized (getAccountLock(player.getUniqueID())) {
            try {
                Stores stores = loadStores(player.worldObj, player.getUniqueID());
                CharacterErrorId availability = stores.check(player.getUniqueID());
                if (availability != CharacterErrorId.NONE) {
                    return availability;
                }
                CharacterRoster roster = stores.rosters.getOrCreateRoster(
                        player.getUniqueID());
                CharacterSwitchAccountState account =
                        stores.switches.getOrCreateAccount(player.getUniqueID());
                CharacterErrorId journalResult = recoverJournalLocked(
                        player, roster, account, stores, false);
                if (journalResult != CharacterErrorId.NONE) {
                    return journalResult;
                }
                if (!account.isDeathPending()
                        && (roster.getCharacterCount() == 0
                        || roster.getActiveCharacterId() != null)) {
                    this.playerStateService.ensureBootstrapped(
                            player, roster, stores.playerStates, null);
                }
                CharacterSwitchStorage.flush(player.worldObj);
                return CharacterErrorId.NONE;
            } catch (CharacterStateValidationException exception) {
                logFailure(player, "dimension_player_state", exception);
                return CharacterErrorId.SWITCH_PLAYER_STATE_INVALID;
            } catch (RuntimeException exception) {
                logFailure(player, "dimension", exception);
                return CharacterErrorId.INTERNAL_ERROR;
            }
        }
    }

    /** Durable death marker prevents a reconnect from restoring pre-drop state. */
    public void markDeathPending(EntityPlayerMP player) {
        if (!isServerPlayer(player)) {
            return;
        }
        synchronized (getAccountLock(player.getUniqueID())) {
            try {
                CharacterSwitchWorldData data = CharacterSwitchStorage.get(player.worldObj);
                if (data.isReadOnlyForNewerVersion()
                        || data.isOwnerBlocked(player.getUniqueID())) {
                    return;
                }
                CharacterSwitchAccountState account =
                        data.getOrCreateAccount(player.getUniqueID());
                account.markDeathPending(System.currentTimeMillis());
                data.saveAccount(account);
                CharacterSwitchStorage.flush(player.worldObj);
            } catch (RuntimeException exception) {
                logFailure(player, "death_marker", exception);
            }
        }
    }

    /** Captures the active character on clean logout, except during unresolved death. */
    public void saveActiveStateOnLogout(EntityPlayerMP player) {
        if (!isServerPlayer(player)) {
            return;
        }
        synchronized (getAccountLock(player.getUniqueID())) {
            try {
                Stores stores = loadStores(player.worldObj, player.getUniqueID());
                if (stores.check(player.getUniqueID()) != CharacterErrorId.NONE) {
                    return;
                }
                CharacterRoster roster = stores.rosters.getOrCreateRoster(
                        player.getUniqueID());
                CharacterSwitchAccountState account =
                        stores.switches.getOrCreateAccount(player.getUniqueID());
                recoverJournalLocked(player, roster, account, stores, false);
                if (account.isDeathPending() || !player.isEntityAlive()
                        || player.isDead || player.getHealth() <= 0.0F
                        || roster.getActiveCharacterId() == null) {
                    CharacterSwitchStorage.flush(player.worldObj);
                    return;
                }
                this.playerStateService.saveActiveLiveState(
                        player, roster, stores.playerStates, false);
                CharacterSwitchStorage.flush(player.worldObj);
            } catch (CharacterStateValidationException exception) {
                logFailure(player, "logout_player_state", exception);
            } catch (RuntimeException exception) {
                logFailure(player, "logout", exception);
            }
        }
    }

    public CharacterSwitchAccountState getAccountState(World world, UUID ownerId) {
        if (world == null || world.isRemote || ownerId == null) {
            return null;
        }
        synchronized (getAccountLock(ownerId)) {
            CharacterSwitchWorldData data = CharacterSwitchStorage.get(world);
            return data.isReadOnlyForNewerVersion() || data.isOwnerBlocked(ownerId)
                    ? null : data.getOrCreateAccount(ownerId);
        }
    }

    public boolean resetCooldown(World world, UUID ownerId) {
        if (world == null || world.isRemote || ownerId == null) {
            return false;
        }
        synchronized (getAccountLock(ownerId)) {
            try {
                CharacterSwitchWorldData data = CharacterSwitchStorage.get(world);
                if (data.isReadOnlyForNewerVersion() || data.isOwnerBlocked(ownerId)) {
                    return false;
                }
                CharacterSwitchAccountState state = data.getOrCreateAccount(ownerId);
                long safeNow = state.observeClock(System.currentTimeMillis());
                state.resetCooldown(safeNow);
                data.saveAccount(state);
                CharacterSwitchStorage.flush(world);
                return true;
            } catch (RuntimeException exception) {
                return false;
            }
        }
    }

    public boolean setFrozen(World world, UUID ownerId, boolean frozen) {
        if (world == null || world.isRemote || ownerId == null) {
            return false;
        }
        synchronized (getAccountLock(ownerId)) {
            try {
                CharacterSwitchWorldData data = CharacterSwitchStorage.get(world);
                if (data.isReadOnlyForNewerVersion() || data.isOwnerBlocked(ownerId)) {
                    return false;
                }
                CharacterSwitchAccountState state = data.getOrCreateAccount(ownerId);
                state.setFrozen(frozen);
                data.saveAccount(state);
                CharacterSwitchStorage.flush(world);
                return true;
            } catch (RuntimeException exception) {
                return false;
            }
        }
    }

    public void clearRuntimeState(UUID ownerId) {
        if (ownerId != null) {
            this.requestCaches.remove(ownerId);
            this.playerStateService.clearRuntimeState(ownerId);
        }
    }

    public void clearAllRuntimeState() {
        this.requestCaches.clear();
        this.accountLocks.clear();
        this.playerStateService.clearAllRuntimeState();
    }

    private CharacterOperationResult selectLocked(EntityPlayerMP player,
                                                    int requestId,
                                                    long requestEpoch,
                                                    long expectedRosterRevision,
                                                    UUID targetCharacterId) {
        Stores stores;
        try {
            stores = loadStores(player.worldObj, player.getUniqueID());
        } catch (RuntimeException exception) {
            logFailure(player, "storage_access", exception);
            return CharacterOperationResult.failure(CharacterErrorId.INTERNAL_ERROR, null);
        }
        CharacterErrorId availability = stores.check(player.getUniqueID());
        if (availability != CharacterErrorId.NONE) {
            return CharacterOperationResult.failure(availability, null);
        }

        CharacterRoster roster = stores.rosters.getOrCreateRoster(player.getUniqueID());
        CharacterValidationResult reference = CharacterValidator.validateCharacterReference(
                roster, targetCharacterId, expectedRosterRevision);
        if (!reference.isValid()) {
            return CharacterOperationResult.failure(reference.getErrorId(), roster);
        }
        RoleplayCharacter target = roster.getCharacter(targetCharacterId);
        CharacterSwitchAccountState account =
                stores.switches.getOrCreateAccount(player.getUniqueID());
        CharacterErrorId recovery;
        try {
            recovery = recoverJournalLocked(
                    player, roster, account, stores, true);
        } catch (CharacterStateValidationException exception) {
            logFailure(player, "select_recovery_player_state", exception);
            return CharacterOperationResult.failure(
                    CharacterErrorId.SWITCH_PLAYER_STATE_INVALID, roster);
        } catch (RuntimeException exception) {
            logFailure(player, "select_recovery", exception);
            return CharacterOperationResult.failure(
                    CharacterErrorId.INTERNAL_ERROR, roster);
        }
        if (recovery != CharacterErrorId.NONE) {
            return CharacterOperationResult.failure(recovery, roster);
        }
        if (targetCharacterId.equals(roster.getActiveCharacterId())) {
            return CharacterOperationResult.success(false, roster, target);
        }

        long safeNow = account.observeClock(System.currentTimeMillis());
        account.applyDecay(safeNow, getDecayDurationsMillis(),
                getCooldownDurationsMillis().length - 1);
        CharacterSwitchPolicyResult policyResult =
                this.policy.evaluate(player, account, safeNow);
        if (!policyResult.isAllowed()) {
            stores.switches.saveAccount(account);
            return CharacterOperationResult.failure(
                    policyResult.getErrorId(), roster,
                    policyResult.getRetryAtEpochMillis());
        }
        if (!CharacterLifecycleStateTracker.isRequestEpochCurrent(player, requestEpoch)) {
            return CharacterOperationResult.failure(
                    CharacterErrorId.SWITCH_SESSION_CHANGED, roster);
        }
        if (!CharacterLifecycleStateTracker.beginSwitch(player)) {
            return CharacterOperationResult.failure(
                    CharacterErrorId.SWITCH_ALREADY_IN_PROGRESS, roster);
        }

        UUID sourceCharacterId = roster.getActiveCharacterId();
        RoleplayCharacter sourceCharacter = roster.getActiveCharacter();
        CharacterSwitchTransaction transaction = null;
        CharacterPlayerStateSnapshot sourceSnapshot = null;
        CharacterPlayerStateSnapshot targetSnapshot = null;
        CharacterPlayerStateSnapshot preApplyTargetSnapshot = null;
        boolean playerStateApplied = false;
        boolean rosterChanged = false;
        boolean commitFlushed = false;
        try {
            CharacterPlayerStateAccount playerStateAccount =
                    this.playerStateService.ensureBootstrapped(
                            player, roster, stores.playerStates, targetCharacterId);

            if (sourceCharacter != null) {
                if (!CharacterRaceGameplayHandler.prepareEquipmentForCharacterSwitch(
                        player, sourceCharacter)) {
                    return CharacterOperationResult.failure(
                            CharacterErrorId.SWITCH_PLAYER_STATE_INVALID, roster);
                }
                // Armor normalization may have moved stacks between the armor
                // and main inventories. Synchronize before any later validation
                // can fail so the client never retains a stale pre-switch view.
                this.playerStateService.synchronize(player);
                sourceSnapshot = this.playerStateService.captureAndAppend(
                        player, playerStateAccount, stores.playerStates,
                        sourceCharacterId);
            }
            targetSnapshot = this.playerStateService.getCurrent(
                    playerStateAccount, targetCharacterId);
            preApplyTargetSnapshot = targetSnapshot;

            // A journal must never reference a generation that has not reached
            // disk. Persist bootstrap/source state first; an interruption here
            // merely leaves an unused valid generation while the source remains
            // active.
            CharacterPlayerStateStorage.flush(player.worldObj);

            CharacterSwitchAccountState.CooldownCommit cooldown =
                    DefaultCharacterSwitchPolicy.isCooldownExempt(player)
                            ? account.planCooldownExemptSwitch(safeNow)
                            : account.planSuccessfulSwitch(
                                    safeNow, getCooldownDurationsMillis());
            long targetRevision = roster.getRevision() == Long.MAX_VALUE
                    ? Long.MAX_VALUE : roster.getRevision() + 1L;
            transaction = new CharacterSwitchTransaction(
                    UUID.randomUUID(),
                    sourceCharacterId,
                    targetCharacterId,
                    roster.getRevision(),
                    targetRevision,
                    safeNow,
                    requestEpoch,
                    requestId,
                    account.getCooldownStage(),
                    account.getNextAllowedAt(),
                    account.getLastSuccessfulSwitchAt(),
                    account.getDecayAnchorAt(),
                    account.getLastObservedWallClock(),
                    cooldown.getStage(),
                    cooldown.getNextAllowedAt(),
                    cooldown.getLastSuccessfulSwitchAt(),
                    cooldown.getDecayAnchorAt(),
                    cooldown.getLastObservedWallClock(),
                    sourceSnapshot == null ? -1L : sourceSnapshot.getGeneration(),
                    targetSnapshot.getGeneration(),
                    CharacterSwitchTransactionStatus.PREPARED,
                    0L);
            account.setTransaction(transaction);
            stores.switches.saveAccount(account);
            CharacterSwitchStorage.flush(player.worldObj);

            CharacterSwitchPolicyResult commitPolicy =
                    this.policy.evaluateDuringOwnedSwitch(player, account,
                            account.observeClock(System.currentTimeMillis()));
            if (!commitPolicy.isAllowed()) {
                transaction.markAborted(System.currentTimeMillis());
                stores.switches.saveAccount(account);
                CharacterSwitchStorage.flush(player.worldObj);
                return CharacterOperationResult.failure(
                        commitPolicy.getErrorId(), roster,
                        commitPolicy.getRetryAtEpochMillis());
            }
            CharacterValidationResult finalReference =
                    CharacterValidator.validateCharacterReference(
                            roster, targetCharacterId, expectedRosterRevision);
            if (!finalReference.isValid()) {
                transaction.markAborted(System.currentTimeMillis());
                stores.switches.saveAccount(account);
                CharacterSwitchStorage.flush(player.worldObj);
                return CharacterOperationResult.failure(
                        finalReference.getErrorId(), roster);
            }
            if (!CharacterLifecycleStateTracker.isRequestEpochCurrent(player, requestEpoch)) {
                transaction.markAborted(System.currentTimeMillis());
                stores.switches.saveAccount(account);
                CharacterSwitchStorage.flush(player.worldObj);
                return CharacterOperationResult.failure(
                        CharacterErrorId.SWITCH_SESSION_CHANGED, roster);
            }

            // Set the guard before mutating the live entity. Component apply
            // code validates first, but any unexpected mod callback failure
            // after the first mutation must still restore the source snapshot.
            playerStateApplied = true;
            this.playerStateService.apply(player, target, targetSnapshot);
            if (!CharacterRaceGameplayHandler.prepareEquipmentForCharacterSwitch(
                    player, target)) {
                throw new CharacterStateValidationException(
                        "Target armor cannot be normalized without dropping items");
            }
            // Persist any deterministic equipment normalization before identity commit.
            targetSnapshot = this.playerStateService.captureAndAppend(
                    player, playerStateAccount, stores.playerStates,
                    targetCharacterId);
            // Commit the normalized target generation before publishing its
            // reference in the transaction journal.
            CharacterPlayerStateStorage.flush(player.worldObj);
            transaction.setTargetStateGeneration(targetSnapshot.getGeneration());
            stores.switches.saveAccount(account);
            CharacterSwitchStorage.flush(player.worldObj);

            roster.setActiveCharacterId(targetCharacterId);
            roster.incrementRevision();
            stores.rosters.saveRoster(roster);
            rosterChanged = true;

            account.applyCommittedCooldown(transaction);
            transaction.markCommitted(System.currentTimeMillis());
            stores.switches.saveAccount(account);
            CharacterSwitchStorage.flush(player.worldObj);
            commitFlushed = true;

            CharacterRaceGameplayHandler.apply(player, target);
            this.playerStateService.synchronize(player);
            FMLLog.info("[%s] Character switch committed: tx=%s owner=%s "
                            + "source=%s target=%s revision=%d sourceState=%d "
                            + "targetState=%d cooldownStage=%d nextAllowedAt=%d",
                    LostTalesMetaData.MOD_ID,
                    transaction.getTransactionId(),
                    player.getUniqueID(),
                    String.valueOf(sourceCharacterId),
                    targetCharacterId,
                    Long.valueOf(roster.getRevision()),
                    Long.valueOf(transaction.getSourceStateGeneration()),
                    Long.valueOf(transaction.getTargetStateGeneration()),
                    Integer.valueOf(account.getCooldownStage()),
                    Long.valueOf(account.getNextAllowedAt()));
            return CharacterOperationResult.success(true, roster, target);
        } catch (Throwable throwable) {
            logFailure(player, transaction == null ? "prepare" :
                    "transaction_" + transaction.getTransactionId(), throwable);
            if (commitFlushed) {
                try {
                    CharacterRaceGameplayHandler.apply(player, target);
                    this.playerStateService.synchronize(player);
                } catch (Throwable ignored) {
                    // The committed stores remain authoritative for login recovery.
                }
                return CharacterOperationResult.success(true, roster, target);
            }
            try {
                if (playerStateApplied && sourceSnapshot != null
                        && sourceCharacter != null) {
                    this.playerStateService.apply(
                            player, sourceCharacter, sourceSnapshot);
                    if (!CharacterRaceGameplayHandler.prepareEquipmentForCharacterSwitch(
                            player, sourceCharacter)) {
                        throw new CharacterStateValidationException(
                                "Source equipment could not be restored safely");
                    }
                    CharacterRaceGameplayHandler.apply(player, sourceCharacter);
                    this.playerStateService.synchronize(player);
                } else if (playerStateApplied && sourceCharacter == null) {
                    // First legacy import has no prior character identity. Its
                    // original target generation is also the exact pre-switch
                    // live state, so reapply it before removing provisional race
                    // modifiers and any equipment normalization.
                    if (preApplyTargetSnapshot == null) {
                        throw new CharacterStateValidationException(
                                "Legacy import rollback snapshot is unavailable");
                    }
                    this.playerStateService.apply(
                            player, target, preApplyTargetSnapshot);
                    CharacterRaceGameplayHandler.apply(
                            player, (RoleplayCharacter) null);
                    this.playerStateService.synchronize(player);
                }
                if (rosterChanged
                        && targetCharacterId.equals(roster.getActiveCharacterId())) {
                    roster.setActiveCharacterId(sourceCharacterId);
                    roster.incrementRevision();
                    stores.rosters.saveRoster(roster);
                }
                if (transaction != null) {
                    account.restorePreviousCooldown(transaction);
                    transaction.markAborted(System.currentTimeMillis());
                    account.setTransaction(transaction);
                    stores.switches.saveAccount(account);
                }
                CharacterSwitchStorage.flush(player.worldObj);
            } catch (Throwable rollbackFailure) {
                if (transaction != null) {
                    transaction.markRecoveryRequired(System.currentTimeMillis());
                    account.setFrozen(true);
                    account.setTransaction(transaction);
                    try {
                        stores.switches.saveAccount(account);
                        CharacterSwitchStorage.flush(player.worldObj);
                    } catch (Throwable ignored) {
                        // The next login fails closed if the journal cannot be written.
                    }
                }
                disconnectForRecovery(player);
                FMLLog.severe("[%s] Character switch rollback failed for owner %s: %s",
                        LostTalesMetaData.MOD_ID, player.getUniqueID(),
                        rollbackFailure.toString());
            }
            CharacterErrorId error = throwable instanceof CharacterStateValidationException
                    ? CharacterErrorId.SWITCH_PLAYER_STATE_INVALID
                    : CharacterErrorId.INTERNAL_ERROR;
            return CharacterOperationResult.failure(error, roster);
        } finally {
            CharacterLifecycleStateTracker.endSwitch(player);
        }
    }

    private CharacterErrorId recoverJournalLocked(
            EntityPlayerMP player,
            CharacterRoster roster,
            CharacterSwitchAccountState account,
            Stores stores,
            boolean restorePlayerState)
            throws CharacterStateValidationException {
        CharacterSwitchTransaction transaction = account.getTransaction();
        if (transaction == null) {
            return CharacterErrorId.NONE;
        }

        CharacterPlayerStateAccount playerStateAccount = null;
        if (transaction.hasPlayerStateGenerations()) {
            // Migrate older snapshot schemas before resolving a generation that
            // may be referenced by an interrupted player-state journal. Generation
            // identifiers are preserved by the migration.
            playerStateAccount = this.playerStateService.ensureBootstrapped(
                    player, roster, stores.playerStates,
                    transaction.getTargetCharacterId());
        }

        if (restorePlayerState && transaction.hasPlayerStateGenerations()
                && !account.isDeathPending()
                && player.isEntityAlive() && !player.isDead
                && player.getHealth() > 0.0F) {
            UUID activeId = roster.getActiveCharacterId();
            long generation;
            if (equalsUuid(activeId, transaction.getTargetCharacterId())) {
                generation = transaction.getTargetStateGeneration();
            } else if (equalsUuid(activeId, transaction.getSourceCharacterId())) {
                generation = transaction.getSourceStateGeneration();
            } else {
                transaction.markRecoveryRequired(System.currentTimeMillis());
                account.setFrozen(true);
                stores.switches.saveAccount(account);
                CharacterSwitchStorage.flush(player.worldObj);
                return CharacterErrorId.SWITCH_RECOVERY_REQUIRED;
            }
            RoleplayCharacter activeCharacter = roster.getCharacter(activeId);
            CharacterPlayerStateSnapshot snapshot =
                    this.playerStateService.findGeneration(
                            playerStateAccount, activeId, generation);
            try {
                this.playerStateService.apply(player, activeCharacter, snapshot);
                if (!CharacterRaceGameplayHandler.prepareEquipmentForCharacterSwitch(
                        player, activeCharacter)) {
                    throw new CharacterStateValidationException(
                            "Recovered equipment cannot be normalized safely");
                }
                CharacterRaceGameplayHandler.apply(player, activeCharacter);
                this.playerStateService.synchronize(player);
            } catch (Throwable failure) {
                // Never allow play to continue after a partial interrupted-switch
                // restore. Preserve the journal, freeze the account, and require
                // an operator to repair or retry the authoritative generation.
                transaction.markRecoveryRequired(System.currentTimeMillis());
                account.setFrozen(true);
                stores.switches.saveAccount(account);
                CharacterSwitchStorage.flush(player.worldObj);
                disconnectForRecovery(player);
                if (failure instanceof CharacterStateValidationException) {
                    throw (CharacterStateValidationException) failure;
                }
                throw new CharacterStateValidationException(
                        "Interrupted character state could not be restored", failure);
            }
        }

        return reconcileLocked(player.worldObj, roster, account,
                stores.switches, System.currentTimeMillis());
    }

    private CharacterErrorId reconcileLocked(World world,
                                             CharacterRoster roster,
                                             CharacterSwitchAccountState account,
                                             CharacterSwitchWorldData switchData,
                                             long now) {
        CharacterSwitchTransaction transaction = account.getTransaction();
        if (transaction == null) {
            return CharacterErrorId.NONE;
        }
        UUID active = roster.getActiveCharacterId();
        boolean targetActive = equalsUuid(active, transaction.getTargetCharacterId());
        boolean sourceActive = equalsUuid(active, transaction.getSourceCharacterId());
        boolean changed = false;

        if (transaction.getStatus() == CharacterSwitchTransactionStatus.ABORTED) {
            if (sourceActive) {
                account.setTransaction(null);
            } else {
                transaction.markRecoveryRequired(now);
                account.setFrozen(true);
            }
            changed = true;
        } else if (targetActive) {
            if (transaction.getStatus() == CharacterSwitchTransactionStatus.COMMITTED) {
                account.setTransaction(null);
            } else {
                account.applyCommittedCooldown(transaction);
                transaction.markCommitted(now);
            }
            changed = true;
        } else if (sourceActive) {
            account.restorePreviousCooldown(transaction);
            transaction.markAborted(now);
            changed = true;
        } else {
            transaction.markRecoveryRequired(now);
            account.setFrozen(true);
            changed = true;
        }
        if (changed) {
            switchData.saveAccount(account);
            CharacterSwitchStorage.flush(world);
        }
        return transaction.getStatus() == CharacterSwitchTransactionStatus.RECOVERY_REQUIRED
                ? CharacterErrorId.SWITCH_RECOVERY_REQUIRED
                : CharacterErrorId.NONE;
    }

    private static void disconnectForRecovery(EntityPlayerMP player) {
        try {
            if (player != null && player.playerNetServerHandler != null) {
                player.playerNetServerHandler.kickPlayerFromServer(
                        "Character state recovery failed. Contact a server administrator.");
            }
        } catch (Throwable ignored) {
            // The frozen persistent manifest still rejects future switches.
        }
    }

    private Stores loadStores(World world, UUID ownerId) {
        return new Stores(
                CharacterStorage.get(world),
                CharacterSwitchStorage.get(world),
                CharacterPlayerStateStorage.get(world, ownerId));
    }

    private Object getAccountLock(UUID ownerId) {
        Object existing = this.accountLocks.get(ownerId);
        if (existing != null) {
            return existing;
        }
        Object created = new Object();
        Object previous = this.accountLocks.putIfAbsent(ownerId, created);
        return previous == null ? created : previous;
    }

    private CharacterOperationResult getCached(UUID ownerId, long epoch, int requestId) {
        RequestCache cache = this.requestCaches.get(ownerId);
        return cache == null ? null : cache.get(epoch, requestId);
    }

    private void cache(UUID ownerId, long epoch, int requestId,
                       CharacterOperationResult result) {
        if (result == null || epoch <= 0L) {
            return;
        }
        RequestCache cache = this.requestCaches.get(ownerId);
        if (cache == null) {
            RequestCache created = new RequestCache();
            RequestCache previous = this.requestCaches.putIfAbsent(ownerId, created);
            cache = previous == null ? created : previous;
        }
        cache.put(epoch, requestId, result);
    }

    private static long[] getCooldownDurationsMillis() {
        return secondsToMillis(LostTalesConfig.characterSwitchCooldownSeconds,
                new int[] {60, 180, 300, 900, 1800, 3600});
    }

    private static long[] getDecayDurationsMillis() {
        return secondsToMillis(LostTalesConfig.characterSwitchDecaySeconds,
                new int[] {0, 3600, 10800, 21600, 43200, 86400});
    }

    private static long[] secondsToMillis(int[] configured, int[] fallback) {
        int[] source = configured == null || configured.length == 0
                ? fallback : configured;
        long[] result = new long[source.length];
        for (int index = 0; index < source.length; index++) {
            long seconds = Math.max(0, source[index]);
            result[index] = seconds > Long.MAX_VALUE / 1000L
                    ? Long.MAX_VALUE : seconds * 1000L;
        }
        return result;
    }

    private static boolean equalsUuid(UUID left, UUID right) {
        return left == null ? right == null : left.equals(right);
    }

    private static boolean isServerPlayer(EntityPlayerMP player) {
        return player != null && player.getUniqueID() != null
                && player.worldObj != null && !player.worldObj.isRemote;
    }

    private static void logFailure(EntityPlayerMP player, String phase,
                                   Throwable throwable) {
        FMLLog.warning("[%s] Character switch failed for owner %s during %s: %s",
                LostTalesMetaData.MOD_ID,
                player == null ? "unknown" : player.getUniqueID(),
                phase,
                throwable == null ? "unknown" : throwable.toString());
    }

    private static final class Stores {
        private final CharacterWorldData rosters;
        private final CharacterSwitchWorldData switches;
        private final CharacterPlayerStateWorldData playerStates;

        private Stores(CharacterWorldData rosters,
                       CharacterSwitchWorldData switches,
                       CharacterPlayerStateWorldData playerStates) {
            this.rosters = rosters;
            this.switches = switches;
            this.playerStates = playerStates;
        }

        private CharacterErrorId check(UUID ownerId) {
            if (this.rosters.isReadOnlyForNewerVersion()) {
                return CharacterErrorId.STORAGE_READ_ONLY;
            }
            if (this.switches.isReadOnlyForNewerVersion()) {
                return CharacterErrorId.SWITCH_STORAGE_READ_ONLY;
            }
            if (this.playerStates.isReadOnlyForNewerVersion()) {
                return CharacterErrorId.SWITCH_PLAYER_STATE_STORAGE_READ_ONLY;
            }
            if (this.switches.isOwnerBlocked(ownerId)) {
                return CharacterErrorId.SWITCH_RECOVERY_REQUIRED;
            }
            if (this.playerStates.isOwnerBlocked(ownerId)) {
                return CharacterErrorId.SWITCH_PLAYER_STATE_INVALID;
            }
            return CharacterErrorId.NONE;
        }
    }

    private static final class RequestCache {
        private long epoch = -1L;
        private final LinkedHashMap<Integer, CharacterOperationResult> results =
                new LinkedHashMap<Integer, CharacterOperationResult>();

        private synchronized CharacterOperationResult get(long requestEpoch, int requestId) {
            if (this.epoch != requestEpoch) {
                return null;
            }
            return this.results.get(Integer.valueOf(requestId));
        }

        private synchronized void put(long requestEpoch, int requestId,
                                      CharacterOperationResult result) {
            if (this.epoch != requestEpoch) {
                this.epoch = requestEpoch;
                this.results.clear();
            }
            this.results.put(Integer.valueOf(requestId), result);
            while (this.results.size() > MAX_CACHED_REQUESTS) {
                Integer first = this.results.keySet().iterator().next();
                this.results.remove(first);
            }
        }
    }
}
