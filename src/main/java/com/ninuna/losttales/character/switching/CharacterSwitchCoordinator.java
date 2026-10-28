package com.ninuna.losttales.character.switching;

import com.ninuna.losttales.character.state.CharacterLastSeen;
import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.identity.PlayableIdentity;
import com.ninuna.losttales.compat.lotr.hired.LotrHiredUnitCustody;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.server.CharacterOperationResult;
import com.ninuna.losttales.character.server.CharacterLotrRecord;
import com.ninuna.losttales.character.server.CharacterRaceGameplayHandler;
import com.ninuna.losttales.character.state.CharacterLiveStatePersistence;
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
import com.ninuna.losttales.util.LostTalesServerPlayers;
import cpw.mods.fml.common.FMLLog;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S40PacketDisconnect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Switches a player between their characters through the switch journal,
 * and writes the played character's saved life at checkpoints, respawns,
 * changes of dimension and logout.
 */
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

    /** Switches the player to one of their own characters. */
    public CharacterOperationResult selectCharacter(EntityPlayerMP player,
                                                    int requestId,
                                                    long expectedRosterRevision,
                                                    UUID characterId) {
        if (!LostTalesServerPlayers.isServerPlayer(player)) {
            return CharacterOperationResult.failure(CharacterErrorId.INVALID_PLAYER, null);
        }
        UUID ownerId = player.getUniqueID();
        if (characterId == null) {
            return CharacterOperationResult.failure(CharacterErrorId.INVALID_CHARACTER_ID, null);
        }
        PlayableIdentity target = PlayableIdentity.character(ownerId, characterId);
        synchronized (getAccountLock(ownerId)) {
            long requestEpoch = CharacterLifecycleStateTracker.captureRequestEpoch(player);
            CharacterOperationResult cached = getCached(ownerId, requestEpoch, requestId);
            if (cached != null) {
                return cached;
            }
            CharacterOperationResult result = selectLocked(
                    player, requestEpoch,
                    expectedRosterRevision, target, false);
            cache(ownerId, requestEpoch, requestId, result);
            return result;
        }
    }

    /**
     * Plays a player who has just made their first character as it: the
     * same journaled switch as any other, held back only by the account
     * and the session ({@link CharacterSwitchPolicy#evaluateFirstCharacter}),
     * and spending no cooldown.
     */
    public CharacterOperationResult joinAs(EntityPlayerMP player, UUID characterId) {
        if (!LostTalesServerPlayers.isServerPlayer(player)) {
            return CharacterOperationResult.failure(CharacterErrorId.INVALID_PLAYER, null);
        }
        UUID ownerId = player.getUniqueID();
        if (characterId == null) {
            return CharacterOperationResult.failure(CharacterErrorId.INVALID_CHARACTER_ID, null);
        }
        synchronized (getAccountLock(ownerId)) {
            long requestEpoch = CharacterLifecycleStateTracker.captureRequestEpoch(player);
            long revision;
            try {
                revision = loadStores(player.worldObj, ownerId).rosters
                        .getOrCreateRoster(ownerId).getRevision();
            } catch (RuntimeException exception) {
                logFailure(player, "join_storage_access", exception);
                return CharacterOperationResult.failure(CharacterErrorId.INTERNAL_ERROR, null);
            }
            return selectLocked(player, requestEpoch, revision,
                    PlayableIdentity.character(ownerId, characterId), true);
        }
    }

    /**
     * At a join or an operator's recover: settles the account's journal
     * against the live player, putting back a switch cut short.
     */
    public CharacterErrorId recover(EntityPlayerMP player) {
        if (!LostTalesServerPlayers.isServerPlayer(player)) {
            return CharacterErrorId.INVALID_PLAYER;
        }
        synchronized (getAccountLock(player.getUniqueID())) {
            try {
                Stores stores = loadStores(player.worldObj, player.getUniqueID());
                CharacterErrorId availability = stores.check(player.getUniqueID());
                if (availability != CharacterErrorId.NONE) {
                    // A store that cannot be written is a reason not to
                    // switch, not a reason not to play. Nothing has touched
                    // the live entity, so the player keeps their own files
                    // and is let in; the caller leaves the session unready,
                    // so every switch is refused until the store is repaired.
                    FMLLog.warning("[%s] Character switching is unavailable for "
                                    + "owner %s: %s",
                            LostTalesMetaData.MOD_ID, player.getUniqueID(),
                            availability.getId());
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
                this.playerStateService.ensureBootstrapped(
                        player, roster, stores.playerStates);
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

    /**
     * At a respawn: writes the live player as the played character's saved
     * life and settles the death. A saved life from before the death never
     * comes back.
     */
    public CharacterErrorId handleRespawn(EntityPlayerMP player) {
        if (!LostTalesServerPlayers.isServerPlayer(player)) {
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
                CharacterSwitchTransaction held = account.getTransaction();
                if (held != null && held.isHeld()) {
                    // The journal names the saved state an operator's discard
                    // puts back, so nothing is captured over it; the death is
                    // settled all the same.
                    account.clearDeathPending();
                    stores.switches.saveAccount(account);
                    CharacterSwitchStorage.flush(player.worldObj);
                    return CharacterErrorId.SWITCH_RECOVERY_REQUIRED;
                }
                this.playerStateService.ensureBootstrapped(
                        player, roster, stores.playerStates);
                CharacterPlayerStateSnapshot snapshot =
                        this.playerStateService.saveLiveState(
                        player, roster, stores.playerStates, false);
                if (snapshot != null) {
                    CharacterErrorId prepareResult =
                            prepareJournalForDurablePlayerSave(
                            roster, account, stores, snapshot, player.worldObj);
                    if (prepareResult != CharacterErrorId.NONE) {
                        return prepareResult;
                    }
                    CharacterLiveStatePersistence.save(player);
                    CharacterPlayerStateStorage.flush(player.worldObj);
                }
                account.clearDeathPending();
                CharacterErrorId journalResult =
                        finalizeJournalAfterDurablePlayerSaveLocked(
                                player.worldObj, roster, account, stores.switches);
                if (journalResult != CharacterErrorId.NONE) {
                    return journalResult;
                }
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

    /** At a change of dimension: writes the played character's saved life from the live player. */
    public CharacterErrorId handleDimensionChange(EntityPlayerMP player) {
        if (!LostTalesServerPlayers.isServerPlayer(player)) {
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
                if (!account.isDeathPending()) {
                    this.playerStateService.ensureBootstrapped(
                            player, roster, stores.playerStates);
                    if (!checkpointActiveStateLocked(
                            player, roster, account, stores)) {
                        return CharacterErrorId.SWITCH_RECOVERY_REQUIRED;
                    }
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

    /**
     * Marks the account's death unsettled on disk, so no journal repair at
     * a later join puts back a life from before the death.
     */
    public void markDeathPending(EntityPlayerMP player) {
        if (!LostTalesServerPlayers.isServerPlayer(player)) {
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

    /** At logout: writes the played character's saved life, unless a death is unsettled. */
    public void saveActiveStateOnLogout(EntityPlayerMP player) {
        if (!LostTalesServerPlayers.isServerPlayer(player)) {
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
                if (account.isDeathPending() || !player.isEntityAlive()
                        || player.isDead || player.getHealth() <= 0.0F) {
                    CharacterSwitchStorage.flush(player.worldObj);
                    return;
                }
                checkpointActiveStateLocked(player, roster, account, stores);
            } catch (CharacterStateValidationException exception) {
                logFailure(player, "logout_player_state", exception);
            } catch (RuntimeException exception) {
                logFailure(player, "logout", exception);
            }
        }
    }

    /**
     * The periodic save of the played character's life. A player in the
     * middle of a switch, a respawn or a change of dimension waits for the
     * next round; false then, and while a freeze or a held journal stops it.
     */
    public boolean checkpointActiveState(EntityPlayerMP player) {
        if (!LostTalesServerPlayers.isServerPlayer(player)) {
            return true;
        }
        CharacterLifecycleStateTracker.Snapshot lifecycle =
                CharacterLifecycleStateTracker.snapshot(player);
        if (!lifecycle.isPresent() || !lifecycle.isReady()
                || lifecycle.isLoggingOut() || lifecycle.isRespawning()
                || lifecycle.isDimensionChanging() || lifecycle.isSwitching()
                || lifecycle.isServerStopping()) {
            return false;
        }
        synchronized (getAccountLock(player.getUniqueID())) {
            try {
                Stores stores = loadStores(player.worldObj, player.getUniqueID());
                if (stores.check(player.getUniqueID()) != CharacterErrorId.NONE) {
                    return true;
                }
                CharacterRoster roster = stores.rosters.getOrCreateRoster(
                        player.getUniqueID());
                CharacterSwitchAccountState account =
                        stores.switches.getOrCreateAccount(player.getUniqueID());
                return checkpointActiveStateLocked(
                        player, roster, account, stores);
            } catch (CharacterStateValidationException exception) {
                logFailure(player, "periodic_checkpoint_player_state", exception);
            } catch (RuntimeException exception) {
                logFailure(player, "periodic_checkpoint", exception);
            }
            return true;
        }
    }

    /** Writes every online player's played character as the server stops. Answers how many. */
    public int checkpointAllOnlinePlayers(MinecraftServer server) {
        if (server == null || server.getConfigurationManager() == null) {
            return 0;
        }
        List<?> online = new ArrayList<Object>(
                server.getConfigurationManager().playerEntityList);
        int saved = 0;
        for (Object value : online) {
            if (!(value instanceof EntityPlayerMP)) {
                continue;
            }
            EntityPlayerMP player = (EntityPlayerMP) value;
            synchronized (getAccountLock(player.getUniqueID())) {
                try {
                    Stores stores = loadStores(
                            player.worldObj, player.getUniqueID());
                    if (stores.check(player.getUniqueID()) != CharacterErrorId.NONE) {
                        continue;
                    }
                    CharacterRoster roster = stores.rosters.getOrCreateRoster(
                            player.getUniqueID());
                    CharacterSwitchAccountState account =
                            stores.switches.getOrCreateAccount(player.getUniqueID());
                    if (checkpointActiveStateLocked(
                            player, roster, account, stores)) {
                        saved++;
                    }
                } catch (CharacterStateValidationException exception) {
                    logFailure(player, "shutdown_checkpoint_player_state", exception);
                } catch (RuntimeException exception) {
                    logFailure(player, "shutdown_checkpoint", exception);
                }
            }
        }
        return saved;
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
                FMLLog.warning("[%s] Could not reset the character switch "
                                + "cooldown for owner %s: %s",
                        LostTalesMetaData.MOD_ID, ownerId, exception.toString());
                return false;
            }
        }
    }

    /**
     * What an operator's discard of an account's switch journal did
     * (CHAR-37), with the character it brings back: its id, null for the
     * ghost, and its name while the roster holds it.
     */
    public static final class JournalDiscard {

        public enum Outcome {
            /** The switch store is read-only, or this owner's entry is quarantined. */
            UNAVAILABLE,
            /** The account holds no journal. */
            NONE,
            /** The player plays the source again; what they held was set aside. */
            RESTORED,
            /** The player is away or dead: the source comes back at their next join. */
            AT_NEXT_JOIN,
            /** The restore cannot be made yet ({@link #getRefusal}); every join tries again. */
            WAITING,
            /** The restore failed once the player was touched: disconnected, held again. */
            FAILED
        }

        private static final JournalDiscard UNAVAILABLE =
                new JournalDiscard(Outcome.UNAVAILABLE, null, null, null);
        private static final JournalDiscard NONE =
                new JournalDiscard(Outcome.NONE, null, null, null);

        private final Outcome outcome;
        private final UUID sourceId;
        private final String sourceName;
        private final CharacterSwitchSourceRestore.Refusal refusal;

        private JournalDiscard(Outcome outcome, UUID sourceId, String sourceName,
                               CharacterSwitchSourceRestore.Refusal refusal) {
            this.outcome = outcome;
            this.sourceId = sourceId;
            this.sourceName = sourceName;
            this.refusal = refusal;
        }

        private static JournalDiscard of(Outcome outcome,
                                         CharacterSwitchTransaction transaction,
                                         CharacterRoster roster,
                                         CharacterSwitchSourceRestore.Refusal refusal) {
            UUID sourceId = transaction.getSourceCharacterId();
            RoleplayCharacter source = roster == null || sourceId == null
                    ? null : roster.getCharacter(sourceId);
            return new JournalDiscard(outcome, sourceId,
                    source == null ? null : source.getName(), refusal);
        }

        public Outcome getOutcome() { return this.outcome; }
        /** The character the switch was leaving; null for the ghost. */
        public UUID getSourceId() { return this.sourceId; }
        /** Its name; null for the ghost and for a character no longer in the roster. */
        public String getSourceName() { return this.sourceName; }
        /** Why a waiting restore cannot be made yet; null otherwise. */
        public CharacterSwitchSourceRestore.Refusal getRefusal() { return this.refusal; }
    }

    /**
     * Discards an account's switch journal (CHAR-37), by owner id, so an
     * account refused at login can be settled while it is offline. The
     * character the switch was leaving comes back exactly as the journal's
     * source generation holds it, and what the player holds then is set
     * aside in their player-state quarantine: at once for {@code online},
     * the account's player when they are alive, else at their next join.
     * The journal waits for its restore, switching off, until it is made.
     */
    public JournalDiscard discardJournal(World world, UUID ownerId,
                                         EntityPlayerMP online) {
        if (world == null || world.isRemote || ownerId == null) {
            return JournalDiscard.UNAVAILABLE;
        }
        synchronized (getAccountLock(ownerId)) {
            CharacterSwitchAccountState state;
            CharacterSwitchTransaction transaction;
            try {
                CharacterSwitchWorldData data = CharacterSwitchStorage.get(world);
                if (data.isReadOnlyForNewerVersion() || data.isOwnerBlocked(ownerId)) {
                    return JournalDiscard.UNAVAILABLE;
                }
                state = data.getAccount(ownerId);
                transaction = state == null ? null : state.getTransaction();
                if (transaction == null) {
                    return JournalDiscard.NONE;
                }
                CharacterSwitchTransactionStatus was = transaction.getStatus();
                CharacterSwitchSourceRestore.markForRestore(
                        state, System.currentTimeMillis());
                data.saveAccount(state);
                CharacterSwitchStorage.flush(world);
                FMLLog.warning("[%s] An operator discarded character switch journal %s "
                                + "of owner %s (status %s, source %s, target %s): the "
                                + "source comes back from state generation %d, and what "
                                + "the player holds then is set aside",
                        LostTalesMetaData.MOD_ID, transaction.getTransactionId(),
                        ownerId, was.getId(),
                        describe(transaction.getSourceCharacterId()),
                        describe(transaction.getTargetCharacterId()),
                        Long.valueOf(transaction.getSourceStateGeneration()));
            } catch (RuntimeException exception) {
                FMLLog.warning("[%s] Could not discard the character switch "
                                + "journal for owner %s: %s",
                        LostTalesMetaData.MOD_ID, ownerId, exception.toString());
                return JournalDiscard.UNAVAILABLE;
            }

            if (online != null && ownerId.equals(online.getUniqueID())
                    && LostTalesServerPlayers.isServerPlayer(online)
                    && online.isEntityAlive() && !online.isDead
                    && online.getHealth() > 0.0F && !state.isDeathPending()) {
                Stores stores;
                try {
                    stores = loadStores(online.worldObj, ownerId);
                } catch (RuntimeException exception) {
                    return refused(ownerId, transaction, null,
                            CharacterSwitchSourceRestore.Refusal.STORE_UNAVAILABLE,
                            exception);
                }
                return restoreSourceLocked(online, stores, state);
            }
            return awaitJoin(world, ownerId, transaction);
        }
    }

    /**
     * The answer for a player away or dead: their next join makes the
     * restore, unless the stores already say why it cannot be made.
     */
    private static JournalDiscard awaitJoin(World world, UUID ownerId,
                                            CharacterSwitchTransaction transaction) {
        CharacterRoster roster = null;
        CharacterSwitchSourceRestore.Refusal refusal;
        try {
            CharacterWorldData rosters = CharacterStorage.get(world);
            CharacterPlayerStateWorldData states =
                    CharacterPlayerStateStorage.get(world, ownerId);
            roster = rosters.getRoster(ownerId);
            refusal = CharacterSwitchSourceRestore.refusal(transaction, roster,
                    states.getAccount(ownerId),
                    !rosters.isReadOnlyForNewerVersion()
                            && !states.isReadOnlyForNewerVersion()
                            && !states.isOwnerBlocked(ownerId),
                    states.getQuarantinedEntryCount()
                            < CharacterPlayerStateWorldData.MAX_QUARANTINED);
        } catch (RuntimeException exception) {
            return refused(ownerId, transaction, roster,
                    CharacterSwitchSourceRestore.Refusal.STORE_UNAVAILABLE, exception);
        }
        if (refusal != null) {
            return refused(ownerId, transaction, roster, refusal, null);
        }
        return JournalDiscard.of(JournalDiscard.Outcome.AT_NEXT_JOIN,
                transaction, roster, null);
    }

    /**
     * Puts the character a journal's switch was leaving back on the live
     * player, exactly as the generation the journal names for it, after
     * setting what the player holds aside in their player-state quarantine
     * (CHAR-37). It goes the way a switch goes: the state applied and the
     * player moved, the roster on the source, its LOTR record, its race and
     * its hired units, every part synchronized, and the player's own files
     * saved before the journal goes. Refused before the player is touched,
     * the journal keeps waiting; failed after, it is held for an operator
     * again and the player is disconnected.
     */
    private JournalDiscard restoreSourceLocked(EntityPlayerMP player,
                                               Stores stores,
                                               CharacterSwitchAccountState account) {
        UUID ownerId = player.getUniqueID();
        World world = player.worldObj;
        CharacterSwitchTransaction transaction = account.getTransaction();
        CharacterRoster roster = stores.rosters.getRoster(ownerId);
        CharacterPlayerStateAccount states = stores.playerStates.getAccount(ownerId);
        CharacterSwitchSourceRestore.Refusal refusal =
                CharacterSwitchSourceRestore.refusal(transaction, roster, states,
                        !stores.rosters.isReadOnlyForNewerVersion()
                                && !stores.playerStates.isReadOnlyForNewerVersion()
                                && !stores.playerStates.isOwnerBlocked(ownerId),
                        stores.playerStates.getQuarantinedEntryCount()
                                < CharacterPlayerStateWorldData.MAX_QUARANTINED);
        if (refusal != null) {
            return refused(ownerId, transaction, roster, refusal, null);
        }

        UUID sourceId = transaction.getSourceCharacterId();
        PlayableIdentity source = PlayableIdentity.of(ownerId, sourceId);
        PlayableIdentity played = PlayableIdentity.fromRoster(roster);
        RoleplayCharacter sourceCharacter = sourceId == null
                ? null : roster.getCharacter(sourceId);
        CharacterPlayerStateSnapshot snapshot;
        try {
            snapshot = this.playerStateService.findGeneration(states,
                    source.getGameplayId(), transaction.getSourceStateGeneration());
        } catch (CharacterStateValidationException exception) {
            return refused(ownerId, transaction, roster,
                    CharacterSwitchSourceRestore.Refusal.STATE_GONE, exception);
        }
        NBTTagCompound held;
        try {
            held = this.playerStateService.captureAsIs(player);
        } catch (Exception exception) {
            return refused(ownerId, transaction, roster,
                    CharacterSwitchSourceRestore.Refusal.STATE_UNREADABLE, exception);
        }
        if (!stores.playerStates.hasQuarantineRoomFor(1)) {
            return refused(ownerId, transaction, roster,
                    CharacterSwitchSourceRestore.Refusal.QUARANTINE_FULL, null);
        }
        try {
            // Kept on disk before the player is touched.
            stores.playerStates.setAside(
                    CharacterPlayerStateWorldData.REASON_SWITCH_MIXED_STATE,
                    CharacterSwitchSourceRestore.setAside(transaction,
                            played.getCharacterId(), System.currentTimeMillis(), held));
            CharacterPlayerStateStorage.flush(world);
        } catch (RuntimeException exception) {
            return refused(ownerId, transaction, roster,
                    CharacterSwitchSourceRestore.Refusal.STORE_UNAVAILABLE, exception);
        }

        try {
            this.playerStateService.apply(player, source, sourceCharacter, snapshot);
            this.playerStateService.transitionLocation(player, snapshot);
            if (!source.equals(played)) {
                CharacterLastSeen.saw(world, played.getGameplayId());
            }
            if (CharacterSwitchSourceRestore.playSource(roster, transaction)) {
                stores.rosters.saveRoster(roster);
            }
            CharacterLotrRecord.refresh(player, stores.rosters, roster, sourceCharacter);
            CharacterRaceGameplayHandler.apply(player, sourceCharacter);
            LotrHiredUnitCustody.settle(player, source, roster);
            this.playerStateService.synchronize(player);
            // The player's own files hold the source before the journal goes.
            CharacterLiveStatePersistence.save(player);
            CharacterSwitchSourceRestore.settle(account);
            stores.switches.saveAccount(account);
            CharacterSwitchStorage.flush(world);
        } catch (Throwable failure) {
            CharacterSwitchSourceRestore.holdAgain(account, System.currentTimeMillis());
            try {
                stores.switches.saveAccount(account);
                CharacterSwitchStorage.flush(world);
            } catch (Throwable journalFailure) {
                // The next login fails closed if the journal cannot be written.
                logFailure(player, "restore_journal", journalFailure);
            }
            disconnectForRecovery(player);
            FMLLog.severe("[%s] Putting %s back from character switch journal %s "
                            + "failed for owner %s: %s. The player was disconnected; "
                            + "the journal is held for an operator again, and what "
                            + "they held is in the player-state quarantine",
                    LostTalesMetaData.MOD_ID, describe(sourceId),
                    transaction.getTransactionId(), ownerId, failure.toString());
            return JournalDiscard.of(JournalDiscard.Outcome.FAILED,
                    transaction, roster, null);
        }
        FMLLog.warning("[%s] Character switch journal %s settled for owner %s: "
                        + "%s is back from state generation %d (the roster played %s); "
                        + "what the player held is set aside in the player-state "
                        + "quarantine (%d of %d entries)",
                LostTalesMetaData.MOD_ID, transaction.getTransactionId(), ownerId,
                describe(sourceId), Long.valueOf(transaction.getSourceStateGeneration()),
                describe(played.getCharacterId()),
                Integer.valueOf(stores.playerStates.getQuarantinedEntryCount()),
                Integer.valueOf(CharacterPlayerStateWorldData.MAX_QUARANTINED));
        return JournalDiscard.of(JournalDiscard.Outcome.RESTORED,
                transaction, roster, null);
    }

    /** A restore that cannot be made yet, said in the log; the journal keeps waiting. */
    private static JournalDiscard refused(UUID ownerId,
                                          CharacterSwitchTransaction transaction,
                                          CharacterRoster roster,
                                          CharacterSwitchSourceRestore.Refusal refusal,
                                          Throwable cause) {
        FMLLog.warning("[%s] Character switch journal %s of owner %s cannot put %s "
                        + "back yet (%s)%s; it is tried again at their next join",
                LostTalesMetaData.MOD_ID, transaction.getTransactionId(), ownerId,
                describe(transaction.getSourceCharacterId()), refusal.getId(),
                cause == null ? "" : ": " + cause);
        return JournalDiscard.of(JournalDiscard.Outcome.WAITING,
                transaction, roster, refusal);
    }

    /** A journal's side for the log: a character's id, or the ghost. */
    private static String describe(UUID characterId) {
        return characterId == null ? "the ghost" : "character " + characterId;
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
                FMLLog.warning("[%s] Could not %s character switching for "
                                + "owner %s: %s",
                        LostTalesMetaData.MOD_ID, frozen ? "freeze" : "thaw",
                        ownerId, exception.toString());
                return false;
            }
        }
    }

    /**
     * Forgets an account that has logged out: its request cache, its
     * components' runtime state and its lock. Every switch runs on the
     * server thread, as does the logout, so nothing holds the lock when
     * it goes; the next login makes a fresh one. Without this the lock
     * map would keep one entry per account ever seen.
     */
    public void clearRuntimeState(UUID ownerId) {
        if (ownerId != null) {
            this.requestCaches.remove(ownerId);
            this.accountLocks.remove(ownerId);
            this.playerStateService.clearRuntimeState(ownerId);
        }
    }

    public void clearAllRuntimeState() {
        this.requestCaches.clear();
        this.accountLocks.clear();
        this.playerStateService.clearAllRuntimeState();
    }

    private CharacterOperationResult selectLocked(EntityPlayerMP player,
                                                    long requestEpoch,
                                                    long expectedRosterRevision,
                                                    PlayableIdentity target,
                                                    boolean firstCharacter) {
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
                roster, target.getCharacterId(), expectedRosterRevision);
        if (!reference.isValid()) {
            return CharacterOperationResult.failure(reference.getErrorId(), roster);
        }
        RoleplayCharacter targetCharacter = roster.getCharacter(target.getCharacterId());
        CharacterSwitchAccountState account =
                stores.switches.getOrCreateAccount(player.getUniqueID());
        CharacterErrorId recovery;
        try {
            recovery = recoverJournalLocked(
                    player, roster, account, stores, false);
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
        if (target.equals(PlayableIdentity.fromRoster(roster))) {
            return CharacterOperationResult.success(false, roster, targetCharacter);
        }

        long safeNow = account.observeClock(System.currentTimeMillis());
        account.applyDecay(safeNow, getDecayDurationsMillis(),
                getCooldownDurationsMillis().length - 1);
        CharacterSwitchPolicyResult policyResult = firstCharacter
                ? this.policy.evaluateFirstCharacter(player, account, safeNow)
                : this.policy.evaluate(player, account, safeNow);
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

        PlayableIdentity source = PlayableIdentity.fromRoster(roster);
        RoleplayCharacter sourceCharacter = roster.getActiveCharacter();
        CharacterSwitchTransaction transaction = null;
        CharacterPlayerStateSnapshot sourceSnapshot = null;
        CharacterPlayerStateSnapshot targetSnapshot;
        boolean playerStateApplied = false;
        boolean rosterChanged = false;
        boolean commitFlushed = false;
        boolean finalizationFlushed = false;
        boolean unitsParked = false;
        try {
            CharacterPlayerStateAccount playerStateAccount =
                    this.playerStateService.ensureBootstrapped(
                            player, roster, stores.playerStates);

            // The source's hired units hold still from here: whichever
            // identity is played next, they are not its to command.
            LotrHiredUnitCustody.park(player, source, roster);
            unitsParked = true;
            // The live state is the source's, whichever identity that is;
            // the ghost of a first switch gets its record from this.
            sourceSnapshot = this.playerStateService.captureOrCreate(
                    player, playerStateAccount, stores.playerStates, source);
            // The source's pledge and title as the player leaves it, while
            // the live LOTR data is still the source's.
            CharacterLotrRecord.refresh(player, stores.rosters, roster,
                    sourceCharacter);
            // A character always has a record once the account is bootstrapped.
            targetSnapshot = this.playerStateService.getCurrent(
                    playerStateAccount, target.getCharacterId());

            // A journal must never reference a generation that has not reached
            // disk. Persist bootstrap/source state first; an interruption here
            // merely leaves an unused valid generation while the source remains
            // active.
            CharacterPlayerStateStorage.flush(player.worldObj);

            CharacterSwitchAccountState.CooldownCommit cooldown =
                    firstCharacter
                            || DefaultCharacterSwitchPolicy.isCooldownExempt(player)
                            ? account.planCooldownExemptSwitch(safeNow)
                            : account.planSuccessfulSwitch(
                                    safeNow, getCooldownDurationsMillis());
            transaction = new CharacterSwitchTransaction(
                    UUID.randomUUID(),
                    source.getCharacterId(),
                    target.getCharacterId(),
                    safeNow,
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
                    sourceSnapshot.getGeneration(),
                    targetSnapshot.getGeneration(),
                    CharacterSwitchTransactionStatus.PREPARED,
                    0L);
            account.setTransaction(transaction);
            stores.switches.saveAccount(account);
            CharacterSwitchStorage.flush(player.worldObj);

            long commitNow = account.observeClock(System.currentTimeMillis());
            CharacterSwitchPolicyResult commitPolicy = firstCharacter
                    ? CharacterSwitchPolicyResult.allowed()
                    : this.policy.evaluateDuringOwnedSwitch(player, account,
                            commitNow);
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
                            roster, target.getCharacterId(), expectedRosterRevision);
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
            this.playerStateService.apply(player, target, targetCharacter, targetSnapshot);
            this.playerStateService.transitionLocation(player, targetSnapshot);
            // Persist the target as it now stands before identity commit.
            targetSnapshot = this.playerStateService.captureOrCreate(
                    player, playerStateAccount, stores.playerStates, target);
            // Commit the normalized target generation before publishing its
            // reference in the transaction journal.
            CharacterPlayerStateStorage.flush(player.worldObj);
            transaction.setTargetStateGeneration(targetSnapshot.getGeneration());
            stores.switches.saveAccount(account);
            CharacterSwitchStorage.flush(player.worldObj);

            CharacterLastSeen.saw(player.worldObj, source.getGameplayId());
            roster.setActiveCharacterId(target.getCharacterId());
            roster.incrementRevision();
            stores.rosters.saveRoster(roster);
            rosterChanged = true;

            account.applyCommittedCooldown(transaction);
            transaction.markCommitted(System.currentTimeMillis());
            stores.switches.saveAccount(account);
            CharacterSwitchStorage.flush(player.worldObj);
            commitFlushed = true;

            // The live LOTR data is the target's now; its record keeps
            // the pledge and the title it holds.
            CharacterLotrRecord.refresh(player, stores.rosters, roster,
                    targetCharacter);
            CharacterRaceGameplayHandler.apply(player, targetCharacter);
            LotrHiredUnitCustody.settle(player, target, roster);
            this.playerStateService.synchronize(player);
            if (!checkpointActiveStateLocked(
                    player, roster, account, stores)) {
                throw new CharacterStateValidationException(
                        "Committed target state could not be durably finalized");
            }
            finalizationFlushed = true;
            FMLLog.info("[%s] Character switch committed: tx=%s owner=%s "
                            + "source=%s target=%s revision=%d sourceState=%d "
                            + "targetState=%d cooldownStage=%d nextAllowedAt=%d",
                    LostTalesMetaData.MOD_ID,
                    transaction.getTransactionId(),
                    player.getUniqueID(),
                    source,
                    target,
                    Long.valueOf(roster.getRevision()),
                    Long.valueOf(transaction.getSourceStateGeneration()),
                    Long.valueOf(transaction.getTargetStateGeneration()),
                    Integer.valueOf(account.getCooldownStage()),
                    Long.valueOf(account.getNextAllowedAt()));
            return CharacterOperationResult.success(true, roster, targetCharacter);
        } catch (Throwable throwable) {
            logFailure(player, transaction == null ? "prepare" :
                    "transaction_" + transaction.getTransactionId(), throwable);
            if (commitFlushed) {
                if (!finalizationFlushed) {
                    disconnectForRecovery(player);
                    FMLLog.severe("[%s] Committed character switch for owner %s "
                                    + "could not complete its durable player checkpoint; "
                                    + "the journal was retained and the player was disconnected",
                            LostTalesMetaData.MOD_ID, player.getUniqueID());
                    return CharacterOperationResult.failure(
                            CharacterErrorId.SWITCH_RECOVERY_REQUIRED, roster);
                }
                try {
                    CharacterRaceGameplayHandler.apply(player, targetCharacter);
                    this.playerStateService.synchronize(player);
                } catch (Throwable resyncFailure) {
                    // The committed stores stay authoritative for login
                    // recovery; the live entity is brought up to date then.
                    logFailure(player, "post_commit_resync", resyncFailure);
                }
                return CharacterOperationResult.success(true, roster, targetCharacter);
            }
            try {
                if (playerStateApplied && sourceSnapshot != null) {
                    // The source generation captured above is exactly the
                    // pre-switch live state, for the ghost as for a
                    // character, so it goes back on before the roster does.
                    this.playerStateService.apply(
                            player, source, sourceCharacter, sourceSnapshot);
                    this.playerStateService.transitionLocation(
                            player, sourceSnapshot);
                    CharacterRaceGameplayHandler.apply(player, sourceCharacter);
                    this.playerStateService.synchronize(player);
                }
                if (rosterChanged
                        && target.equals(PlayableIdentity.fromRoster(roster))) {
                    roster.setActiveCharacterId(source.getCharacterId());
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
                    } catch (Throwable journalFailure) {
                        // The next login fails closed if the journal cannot be written.
                        logFailure(player, "rollback_journal", journalFailure);
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
            // A switch that ends with the source still played, refused late
            // or rolled back, gives the source its parked units back.
            if (unitsParked && source.equals(PlayableIdentity.fromRoster(roster))) {
                LotrHiredUnitCustody.settle(player, source, roster);
            }
            CharacterLifecycleStateTracker.endSwitch(player);
        }
    }

    private boolean checkpointActiveStateLocked(
            EntityPlayerMP player,
            CharacterRoster roster,
            CharacterSwitchAccountState account,
            Stores stores)
            throws CharacterStateValidationException {
        CharacterSwitchTransaction transaction = account.getTransaction();
        if (account.isFrozen() || transaction != null && transaction.isHeld()) {
            return false;
        }
        if (account.isDeathPending() || !player.isEntityAlive()
                || player.isDead || player.getHealth() <= 0.0F) {
            return true;
        }

        CharacterPlayerStateSnapshot snapshot =
                this.playerStateService.saveLiveState(
                        player, roster, stores.playerStates, false);
        if (snapshot == null) {
            return true;
        }

        CharacterErrorId prepared = prepareJournalForDurablePlayerSave(
                roster, account, stores, snapshot, player.worldObj);
        if (prepared != CharacterErrorId.NONE) {
            return false;
        }

        // With a journal, its updated generation is flushed first. Without a
        // journal, keeping the account file ahead of the auxiliary snapshot is
        // preferable because login normally treats the account file as live.
        CharacterLiveStatePersistence.save(player);
        CharacterPlayerStateStorage.flush(player.worldObj);
        return finalizeJournalAfterDurablePlayerSaveLocked(
                player.worldObj, roster, account, stores.switches)
                == CharacterErrorId.NONE;
    }

    private CharacterErrorId prepareJournalForDurablePlayerSave(
            CharacterRoster roster,
            CharacterSwitchAccountState account,
            Stores stores,
            CharacterPlayerStateSnapshot snapshot,
            World world) {
        CharacterSwitchTransaction transaction = account.getTransaction();
        if (transaction == null) {
            return CharacterErrorId.NONE;
        }
        if (account.isFrozen() || transaction.isHeld()) {
            return CharacterErrorId.SWITCH_RECOVERY_REQUIRED;
        }

        // The saved life reaches the disk before the journal names it, so a
        // crash between the two files never leaves the journal naming a
        // generation that is not there.
        CharacterPlayerStateStorage.flush(world);

        // A null source is the ghost of a first switch, so a null active
        // id matches a journal still on its source.
        UUID activeId = roster.getActiveCharacterId();
        if (equalsUuid(activeId, transaction.getTargetCharacterId())) {
            transaction.setTargetStateGeneration(snapshot.getGeneration());
        } else if (equalsUuid(activeId, transaction.getSourceCharacterId())) {
            transaction.setSourceStateGeneration(snapshot.getGeneration());
        } else {
            return requireManualRecoveryLocked(
                    world, account, stores.switches, transaction);
        }
        stores.switches.saveAccount(account);
        CharacterPlayerStateStorage.flush(world);
        return CharacterErrorId.NONE;
    }

    /**
     * Settles the account's journal against the live player. With
     * {@code restoreSource}, a journal an operator discarded puts its
     * source back ({@link #restoreSourceLocked}): at a join and at an
     * operator's recover, never at a switch the player asked for, which
     * the journal refuses until it is made.
     */
    private CharacterErrorId recoverJournalLocked(
            EntityPlayerMP player,
            CharacterRoster roster,
            CharacterSwitchAccountState account,
            Stores stores,
            boolean restoreSource)
            throws CharacterStateValidationException {
        CharacterSwitchTransaction transaction = account.getTransaction();
        if (transaction == null || account.isDeathPending()) {
            return CharacterErrorId.NONE;
        }

        if (transaction.getStatus()
                == CharacterSwitchTransactionStatus.RESTORE_SOURCE) {
            if (!restoreSource || !player.isEntityAlive() || player.isDead
                    || player.getHealth() <= 0.0F) {
                return CharacterErrorId.SWITCH_RECOVERY_REQUIRED;
            }
            return restoreSourceLocked(player, stores, account).getOutcome()
                    == JournalDiscard.Outcome.RESTORED
                    ? CharacterErrorId.NONE
                    : CharacterErrorId.SWITCH_RECOVERY_REQUIRED;
        }

        // A journal held for an operator is never acted on again. The
        // attempt that marked it failed part-way, and repeating it fails
        // the same way and disconnects the player again, on every login.
        // Nothing below this point has touched the live entity, so the
        // player keeps whatever their own files hold and is let in;
        // switching stays unavailable, because the caller only marks the
        // session ready for CharacterErrorId.NONE.
        if (transaction.getStatus()
                == CharacterSwitchTransactionStatus.RECOVERY_REQUIRED) {
            FMLLog.warning("[%s] Owner %s holds character switch journal %s "
                            + "awaiting recovery; switching stays unavailable "
                            + "until an operator discards it",
                    LostTalesMetaData.MOD_ID, player.getUniqueID(),
                    transaction.getTransactionId());
            return CharacterErrorId.SWITCH_RECOVERY_REQUIRED;
        }

        // The generation the journal names is resolved against a
        // bootstrapped player-state account.
        CharacterPlayerStateAccount playerStateAccount =
                this.playerStateService.ensureBootstrapped(
                        player, roster, stores.playerStates);

        // Only a journal whose own status says the switch was still in
        // flight names a generation to put back on. One that was already
        // rolled back names the state the player is on, and re-applying
        // it would rewind them to the moment the attempt failed.
        if (CharacterSwitchRecoveryReconciler
                        .requiresLiveReconciliation(transaction)
                && player.isEntityAlive() && !player.isDead
                && player.getHealth() > 0.0F) {
            UUID activeId = roster.getActiveCharacterId();
            try {
                // Whichever side the roster is on, the ghost included,
                // is restored from the generation the journal names for it.
                long generation;
                if (equalsUuid(activeId,
                        transaction.getTargetCharacterId())) {
                    generation = transaction.getTargetStateGeneration();
                } else if (equalsUuid(activeId,
                        transaction.getSourceCharacterId())) {
                    generation = transaction.getSourceStateGeneration();
                } else {
                    return requireManualRecoveryLocked(
                            player.worldObj, account, stores.switches,
                            transaction);
                }
                PlayableIdentity identity = PlayableIdentity.of(
                        player.getUniqueID(), activeId);
                RoleplayCharacter activeCharacter =
                        roster.getCharacter(activeId);
                CharacterPlayerStateSnapshot snapshot =
                        this.playerStateService.findGeneration(
                                playerStateAccount, identity.getGameplayId(),
                                generation);
                this.playerStateService.apply(
                        player, identity, activeCharacter, snapshot);
                this.playerStateService.transitionLocation(
                        player, snapshot);
                CharacterRaceGameplayHandler.apply(
                        player, activeCharacter);
                this.playerStateService.synchronize(player);
                CharacterLiveStatePersistence.save(player);
            } catch (Throwable failure) {
                // Never allow play to continue after a partial interrupted-switch
                // restore: the live entity holds a mixture of two identities.
                // Preserve the journal, freeze the account, and require an
                // operator to repair or discard it. The status written here is
                // what stops the next login attempting the same restore.
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
        } else if (player.isEntityAlive() && !player.isDead
                && player.getHealth() > 0.0F) {
            // Nothing to put back: a rolled-back journal names the state
            // the player is already on. The live state is made durable so
            // the journal can be cleared below.
            CharacterLiveStatePersistence.save(player);
        }

        return finalizeJournalAfterDurablePlayerSaveLocked(
                player.worldObj, roster, account, stores.switches);
    }

    private CharacterErrorId finalizeJournalAfterDurablePlayerSaveLocked(
                                             World world,
                                             CharacterRoster roster,
                                             CharacterSwitchAccountState account,
                                             CharacterSwitchWorldData switchData) {
        CharacterSwitchRecoveryReconciler.Result result =
                CharacterSwitchRecoveryReconciler
                        .finalizeAfterDurablePlayerSave(
                                roster.getActiveCharacterId(), account,
                                System.currentTimeMillis());
        if (result.isChanged()) {
            switchData.saveAccount(account);
            CharacterSwitchStorage.flush(world);
        }
        // With no journal left, a first switch is done for good: the
        // ghost's record it could roll back to goes. Housekeeping only:
        // a failure leaves the record for the next pass.
        if (account.getTransaction() == null) {
            try {
                if (this.playerStateService.forgetGhostState(roster,
                        CharacterPlayerStateStorage.get(world, roster.getOwnerId()))) {
                    CharacterPlayerStateStorage.flush(world);
                }
            } catch (RuntimeException exception) {
                FMLLog.warning("[%s] Could not forget the first-visit state of "
                                + "owner %s: %s", LostTalesMetaData.MOD_ID,
                        roster.getOwnerId(), exception.toString());
            }
        }
        return result.getErrorId();
    }

    private CharacterErrorId requireManualRecoveryLocked(
            World world,
            CharacterSwitchAccountState account,
            CharacterSwitchWorldData switchData,
            CharacterSwitchTransaction transaction) {
        transaction.markRecoveryRequired(System.currentTimeMillis());
        account.setFrozen(true);
        switchData.saveAccount(account);
        CharacterSwitchStorage.flush(world);
        return CharacterErrorId.SWITCH_RECOVERY_REQUIRED;
    }

    /**
     * Disconnects the player with the reason in their own language: the
     * steps of vanilla's kick, with a translation in place of its plain
     * text.
     */
    private static void disconnectForRecovery(EntityPlayerMP player) {
        try {
            if (player != null && player.playerNetServerHandler != null) {
                final NetworkManager network =
                        player.playerNetServerHandler.netManager;
                final IChatComponent reason = new ChatComponentTranslation(
                        "disconnect.losttales.character_recovery");
                network.scheduleOutboundPacket(new S40PacketDisconnect(reason),
                        new GenericFutureListener[] {
                                new GenericFutureListener<Future<? super Void>>() {
                                    @Override
                                    public void operationComplete(
                                            Future<? super Void> sent) {
                                        network.closeChannel(reason);
                                    }
                                }});
                network.disableAutoRead();
            }
        } catch (Throwable kickFailure) {
            // The durable journal still prevents an unsafe future switch.
            logFailure(player, "recovery_disconnect", kickFailure);
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
                new int[] {3600, 10800, 21600, 43200, 86400});
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
