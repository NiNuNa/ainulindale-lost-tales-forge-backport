package com.ninuna.losttales.character.state;

import com.ninuna.losttales.character.identity.PlayableIdentity;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.server.CharacterRaceGameplayHandler;
import com.ninuna.losttales.character.state.component.LostTalesQuestStateComponent;
import com.ninuna.losttales.character.state.component.AccessoryStateComponent;
import com.ninuna.losttales.character.state.component.LotrCharacterDetailsStateComponent;
import com.ninuna.losttales.character.state.component.LotrCustomWaypointStateComponent;
import com.ninuna.losttales.character.state.component.LotrFastTravelRegionStateComponent;
import com.ninuna.losttales.character.state.component.LotrProgressionStateComponent;
import com.ninuna.losttales.character.state.component.LotrQuestStateComponent;
import com.ninuna.losttales.character.state.component.LotrWaypointUseStateComponent;
import com.ninuna.losttales.character.state.component.VanillaInventoryStateComponent;
import com.ninuna.losttales.character.state.component.VanillaEnderChestStateComponent;
import com.ninuna.losttales.character.state.component.VanillaPotionStateComponent;
import com.ninuna.losttales.character.state.component.VanillaSpawnStateComponent;
import com.ninuna.losttales.character.state.component.VanillaStatisticsStateComponent;
import com.ninuna.losttales.character.state.component.VanillaVitalsStateComponent;
import com.ninuna.losttales.character.state.component.VanillaLocationStateComponent;
import com.ninuna.losttales.character.switching.CharacterLocationTransitionService;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Captures, validates, migrates, persists, applies, and synchronizes character state. */
public final class CharacterPlayerStateService {

    private static final CharacterPlayerStateService INSTANCE =
            new CharacterPlayerStateService();

    private final List<CharacterStateComponent> components;
    private final Map<String, CharacterStateComponent> componentsById;
    /**
     * The account bootstrap version each component first appeared in.
     * A retained generation of an account bootstrapped before that
     * version lacks the component, and the migration fills it in.
     */
    private final Map<String, Integer> introducedAt;
    private final VanillaStatisticsStateComponent statisticsComponent;
    private final VanillaLocationStateComponent locationComponent;
    private final CharacterLocationTransitionService locationTransitionService;
    private final LotrCustomWaypointStateComponent lotrCustomWaypointComponent;
    private final LotrProgressionStateComponent lotrProgressionComponent;

    private CharacterPlayerStateService() {
        ArrayList<CharacterStateComponent> registered =
                new ArrayList<CharacterStateComponent>();
        LinkedHashMap<String, Integer> introduced =
                new LinkedHashMap<String, Integer>();
        this.statisticsComponent = new VanillaStatisticsStateComponent();
        this.locationComponent = new VanillaLocationStateComponent();
        this.locationTransitionService =
                new CharacterLocationTransitionService(this.locationComponent);
        this.lotrCustomWaypointComponent = new LotrCustomWaypointStateComponent();
        this.lotrProgressionComponent = new LotrProgressionStateComponent();
        // Registration order is apply order within a phase. The version
        // is the account bootstrap version the component arrived with;
        // a new component takes the next one and bumps
        // CharacterPlayerStateAccount.CURRENT_BOOTSTRAP_VERSION.
        register(registered, introduced, new VanillaInventoryStateComponent(), 1);
        register(registered, introduced, new AccessoryStateComponent(), 12);
        register(registered, introduced, new VanillaEnderChestStateComponent(), 9);
        register(registered, introduced, this.locationComponent, 10);
        register(registered, introduced, new VanillaSpawnStateComponent(), 11);
        register(registered, introduced, new VanillaPotionStateComponent(), 1);
        register(registered, introduced, this.statisticsComponent, 2);
        register(registered, introduced, new LostTalesQuestStateComponent(), 3);
        register(registered, introduced, new LotrFastTravelRegionStateComponent(), 6);
        register(registered, introduced, new LotrWaypointUseStateComponent(), 7);
        register(registered, introduced, this.lotrCustomWaypointComponent, 7);
        register(registered, introduced, this.lotrProgressionComponent, 4);
        register(registered, introduced, new LotrCharacterDetailsStateComponent(), 8);
        register(registered, introduced, new LotrQuestStateComponent(), 5);
        register(registered, introduced, new VanillaVitalsStateComponent(), 1);
        this.components = Collections.unmodifiableList(registered);
        this.introducedAt = Collections.unmodifiableMap(introduced);

        LinkedHashMap<String, CharacterStateComponent> byId =
                new LinkedHashMap<String, CharacterStateComponent>();
        for (CharacterStateComponent component : registered) {
            if (component == null || component.getId() == null
                    || byId.put(component.getId(), component) != null) {
                throw new IllegalStateException(
                        "Duplicate or invalid character state component registration");
            }
        }
        this.componentsById = Collections.unmodifiableMap(byId);
    }

    private static void register(List<CharacterStateComponent> registered,
                                 Map<String, Integer> introduced,
                                 CharacterStateComponent component,
                                 int bootstrapVersion) {
        if (bootstrapVersion < 1 || bootstrapVersion
                > CharacterPlayerStateAccount.CURRENT_BOOTSTRAP_VERSION) {
            throw new IllegalStateException(
                    "Character state component " + component.getId()
                            + " names a bootstrap version outside the known range");
        }
        registered.add(component);
        introduced.put(component.getId(), Integer.valueOf(bootstrapVersion));
    }

    public static CharacterPlayerStateService getInstance() {
        return INSTANCE;
    }

    /**
     * Bootstrap and schema migration for one account's saved identities. A
     * roster seen for the first time gets a record for every character: the
     * active character's is the live state the account file holds, every
     * other character's is clean defaults. With no active character the live
     * state is the account's own and stays where it is. Characters created
     * later start from defaults too. The account itself has no record until
     * the first capture that needs one: while the account is being played
     * the vanilla player file already is its state.
     */
    public CharacterPlayerStateAccount ensureBootstrapped(
            EntityPlayerMP player,
            CharacterRoster roster,
            CharacterPlayerStateWorldData data)
            throws CharacterStateValidationException {
        requirePlayerRoster(player, roster);
        if (data == null || data.isReadOnlyForNewerVersion()
                || data.isOwnerBlocked(player.getUniqueID())) {
            throw new CharacterStateValidationException(
                    "Character player-state storage is unavailable");
        }
        CharacterPlayerStateAccount account =
                data.getOrCreateAccount(player.getUniqueID());
        if (account.getBootstrapVersion()
                > CharacterPlayerStateAccount.CURRENT_BOOTSTRAP_VERSION) {
            throw new CharacterStateValidationException(
                    "Character player-state bootstrap version is newer than this server");
        }

        boolean changed = false;
        long now = System.currentTimeMillis();
        // The live state is imported into the active character's record; on
        // the account it is imported nowhere, since it is already the
        // account's and is captured when the account is first left.
        UUID importTarget = null;
        if (account.getBootstrapVersion()
                < CharacterPlayerStateAccount.CURRENT_BOOTSTRAP_VERSION) {
            importTarget = roster.getActiveCharacterId();
        }

        if (account.getBootstrapVersion() == 0) {
            Map<String, NBTTagCompound> liveState = importTarget == null
                    ? null : captureComponents(player);
            ArrayList<CharacterPlayerStateRecord> initialized =
                    new ArrayList<CharacterPlayerStateRecord>();
            for (RoleplayCharacter character : roster.getCharacters()) {
                Map<String, NBTTagCompound> initial =
                        character.getCharacterId().equals(importTarget)
                                ? liveState : createDefaultComponents(character);
                CharacterPlayerStateSnapshot snapshot = createSnapshot(
                        character.getCharacterId(), 1L, now, initial);
                initialized.add(new CharacterPlayerStateRecord(
                        character.getCharacterId(), snapshot, null));
            }
            // Publish only after every candidate record has validated. A failed
            // bootstrap may leave the empty account shell created by storage,
            // but it must never leave a partially initialized character set.
            for (CharacterPlayerStateRecord record : initialized) {
                account.putRecord(record);
            }
            account.markBootstrapped(now);
            changed = true;
        } else if (account.getBootstrapVersion()
                < CharacterPlayerStateAccount.CURRENT_BOOTSTRAP_VERSION) {
            migrateLegacySnapshots(player, roster, account, importTarget);
            account.markBootstrapped(now);
            changed = true;
        }

        // A character created after bootstrap starts from a clean save of
        // its own: defaults, its starting faction's progression and its
        // starting waypoint as the place its first switch lands it.
        for (RoleplayCharacter character : roster.getCharacters()) {
            if (account.getRecord(character.getCharacterId()) != null) {
                continue;
            }
            CharacterPlayerStateSnapshot snapshot = createSnapshot(
                    character.getCharacterId(), 1L, now,
                    createDefaultComponents(character));
            account.putRecord(new CharacterPlayerStateRecord(
                    character.getCharacterId(), snapshot, null));
            changed = true;
        }

        if (changed) {
            data.saveAccount(account);
        }
        return account;
    }

    /**
     * Captures the live player as the identity's next generation. An account
     * captured for the first time gets its record from this capture; a
     * character always has one once the account is bootstrapped.
     */
    public CharacterPlayerStateSnapshot captureOrCreate(
            EntityPlayerMP player,
            CharacterPlayerStateAccount account,
            CharacterPlayerStateWorldData data,
            PlayableIdentity identity)
            throws CharacterStateValidationException {
        requireAccount(player, account, data);
        requireIdentityOwner(player, identity);
        UUID gameplayId = identity.getGameplayId();
        CharacterPlayerStateRecord record = account.getRecord(gameplayId);
        Map<String, NBTTagCompound> components = captureComponents(player);
        if (record == null) {
            if (!identity.isAccount()) {
                throw new CharacterStateValidationException(
                        "No player-state record exists for source character " + gameplayId);
            }
            CharacterPlayerStateSnapshot first = createSnapshot(
                    gameplayId, 1L, System.currentTimeMillis(), components);
            account.putRecord(new CharacterPlayerStateRecord(gameplayId, first, null));
            data.saveAccount(account);
            return first;
        }
        CharacterPlayerStateSnapshot snapshot = record.createNext(
                System.currentTimeMillis(), components);
        // Validate the candidate before mutating the generation pointers. An
        // oversized or malformed capture must leave the last known-good state
        // fully authoritative.
        validateSnapshot(snapshot);
        record.commit(snapshot);
        data.saveAccount(account);
        return snapshot;
    }

    /**
     * Builds a validated, detached first generation for a character that is
     * not yet present in an account roster. Lore-character claims journal this
     * record before publishing ownership or roster access.
     */
    public CharacterPlayerStateRecord createDefaultRecord(
            RoleplayCharacter character)
            throws CharacterStateValidationException {
        if (character == null) {
            throw new CharacterStateValidationException(
                    "Character metadata is required for default state");
        }
        CharacterPlayerStateSnapshot snapshot = createSnapshot(
                character.getCharacterId(),
                1L,
                Math.max(1L, System.currentTimeMillis()),
                createDefaultComponents(character));
        return new CharacterPlayerStateRecord(
                character.getCharacterId(), snapshot, null);
    }

    public CharacterPlayerStateSnapshot getCurrent(
            CharacterPlayerStateAccount account, UUID characterId)
            throws CharacterStateValidationException {
        if (account == null || characterId == null) {
            throw new CharacterStateValidationException(
                    "Character state account or character ID is missing");
        }
        CharacterPlayerStateRecord record = account.getRecord(characterId);
        if (record == null || record.getCurrent() == null) {
            throw new CharacterStateValidationException(
                    "No current player-state snapshot exists for " + characterId);
        }
        validateSnapshot(record.getCurrent());
        return record.getCurrent();
    }

    /**
     * The identity's current snapshot. An account with no record yet — one
     * that has never been played since its characters were made, or whose
     * state its first character took over before the account had a save of
     * its own — starts from fresh defaults at the world spawn, saved here so
     * a journal can reference the generation.
     */
    public CharacterPlayerStateSnapshot getOrCreateCurrent(
            CharacterPlayerStateAccount account,
            CharacterPlayerStateWorldData data,
            PlayableIdentity identity)
            throws CharacterStateValidationException {
        if (account == null || data == null || identity == null
                || !account.getOwnerId().equals(identity.getOwnerId())) {
            throw new CharacterStateValidationException(
                    "Character state account or identity is missing");
        }
        UUID gameplayId = identity.getGameplayId();
        if (identity.isAccount() && account.getRecord(gameplayId) == null) {
            CharacterPlayerStateSnapshot defaults = createSnapshot(
                    gameplayId, 1L, Math.max(1L, System.currentTimeMillis()),
                    createDefaultComponents(null));
            account.putRecord(new CharacterPlayerStateRecord(gameplayId, defaults, null));
            data.saveAccount(account);
            return defaults;
        }
        return getCurrent(account, gameplayId);
    }

    public CharacterPlayerStateSnapshot findGeneration(
            CharacterPlayerStateAccount account,
            UUID characterId,
            long generation)
            throws CharacterStateValidationException {
        if (account == null || characterId == null || generation <= 0L) {
            throw new CharacterStateValidationException(
                    "Snapshot generation reference is incomplete");
        }
        CharacterPlayerStateRecord record = account.getRecord(characterId);
        CharacterPlayerStateSnapshot snapshot = record == null
                ? null : record.find(generation);
        if (snapshot == null) {
            throw new CharacterStateValidationException(
                    "Snapshot generation " + generation + " is unavailable for "
                            + characterId);
        }
        validateSnapshot(snapshot);
        return snapshot;
    }

    public void apply(EntityPlayerMP player,
                      RoleplayCharacter character,
                      CharacterPlayerStateSnapshot snapshot)
            throws CharacterStateValidationException {
        if (player == null || character == null) {
            throw new CharacterStateValidationException(
                    "Target character and snapshot do not match");
        }
        apply(player, PlayableIdentity.character(player.getUniqueID(),
                character.getCharacterId()), character, snapshot);
    }

    /**
     * Puts the snapshot on the live player as the given identity. The
     * character is the identity's own, or null for the account, whose race
     * attributes are vanilla's.
     */
    public void apply(EntityPlayerMP player,
                      PlayableIdentity identity,
                      RoleplayCharacter character,
                      CharacterPlayerStateSnapshot snapshot)
            throws CharacterStateValidationException {
        if (player == null || identity == null || snapshot == null
                || !identity.getGameplayId().equals(snapshot.getCharacterId())
                || (character == null) != identity.isAccount()
                || character != null
                && !character.getCharacterId().equals(identity.getCharacterId())) {
            throw new CharacterStateValidationException(
                    "Target identity and snapshot do not match");
        }
        requireIdentityOwner(player, identity);
        validateSnapshot(snapshot);
        applyPhase(player, snapshot, CharacterStateApplyPhase.BEFORE_ATTRIBUTES);
        CharacterRaceGameplayHandler.applyProvisional(player, character);
        applyPhase(player, snapshot, CharacterStateApplyPhase.AFTER_ATTRIBUTES);
    }

    public void synchronize(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        for (CharacterStateComponent component : this.components) {
            component.synchronize(player);
        }
    }

    public void clearRuntimeState(UUID ownerId) {
        this.statisticsComponent.clearRuntimeState(ownerId);
        this.lotrCustomWaypointComponent.clearRuntimeState(ownerId);
    }

    public void clearAllRuntimeState() {
        this.statisticsComponent.clearAllRuntimeState();
        this.lotrCustomWaypointComponent.clearAllRuntimeState();
    }

    /** Captures the live player for whichever identity the roster says is being played. */
    public CharacterPlayerStateSnapshot saveLiveState(
            EntityPlayerMP player,
            CharacterRoster roster,
            CharacterPlayerStateWorldData data,
            boolean flush)
            throws CharacterStateValidationException {
        requirePlayerRoster(player, roster);
        if (!player.isEntityAlive() || player.isDead
                || player.getHealth() <= 0.0F) {
            return null;
        }
        CharacterPlayerStateAccount account = ensureBootstrapped(
                player, roster, data);
        CharacterPlayerStateSnapshot snapshot = captureOrCreate(
                player, account, data, PlayableIdentity.fromRoster(roster));
        if (flush) {
            CharacterPlayerStateStorage.flush(player.worldObj);
        }
        return snapshot;
    }

    public void validateSnapshot(CharacterPlayerStateSnapshot snapshot)
            throws CharacterStateValidationException {
        if (snapshot == null
                || snapshot.getDataVersion()
                != CharacterPlayerStateSnapshot.CURRENT_DATA_VERSION) {
            throw new CharacterStateValidationException(
                    "Unsupported or missing character snapshot");
        }
        Map<String, NBTTagCompound> stored = snapshot.getComponentsView();
        if (stored.size() != this.componentsById.size()) {
            throw new CharacterStateValidationException(
                    "Character snapshot component set is incomplete or unsupported");
        }
        for (CharacterStateComponent component : this.components) {
            NBTTagCompound state = stored.get(component.getId());
            if (state == null) {
                throw new CharacterStateValidationException(
                        "Missing character-state component " + component.getId());
            }
            component.validate((NBTTagCompound) state.copy());
        }
        for (String id : stored.keySet()) {
            if (!this.componentsById.containsKey(id)) {
                throw new CharacterStateValidationException(
                        "Unsupported character-state component " + id);
            }
        }
        int size = CharacterStateNbtUtil.compressedSize(toSizeCompound(snapshot));
        int maximum = Math.max(65536,
                LostTalesConfig.characterStateMaxSnapshotBytes);
        if (size > maximum) {
            throw new CharacterStateValidationException(
                    "Character snapshot exceeds the configured size limit: "
                            + size + " > " + maximum);
        }
    }

    /**
     * Upgrades retained generations without changing transaction
     * references: every component the account was bootstrapped before
     * is filled in on each generation — with the live player's own
     * state for the character the live state is imported into, and
     * with the character's defaults for every other record — and every
     * generation is re-versioned to the current snapshot version. The
     * account is left untouched until every generation has passed
     * component and size validation, since older world-data roots are
     * already dirty and could otherwise persist a half migration.
     */
    private void migrateLegacySnapshots(EntityPlayerMP player,
                                        CharacterRoster roster,
                                        CharacterPlayerStateAccount account,
                                        UUID importTarget)
            throws CharacterStateValidationException {
        List<CharacterStateComponent> missing =
                componentsIntroducedAfter(account.getBootstrapVersion());
        Map<String, NBTTagCompound> imported = null;
        if (importTarget != null && !missing.isEmpty()) {
            imported = new LinkedHashMap<String, NBTTagCompound>();
            for (CharacterStateComponent component : missing) {
                NBTTagCompound state = component.capture(player);
                component.validate(state);
                imported.put(component.getId(), state);
            }
        }
        ArrayList<CharacterPlayerStateRecord> existing =
                new ArrayList<CharacterPlayerStateRecord>(account.getRecords());
        ArrayList<CharacterPlayerStateRecord> migrated =
                new ArrayList<CharacterPlayerStateRecord>(existing.size());
        for (CharacterPlayerStateRecord record : existing) {
            Map<String, NBTTagCompound> fill;
            if (record.getCharacterId().equals(importTarget)) {
                fill = imported;
            } else if (missing.isEmpty()) {
                fill = Collections.emptyMap();
            } else {
                fill = createDefaultComponents(
                        roster.getCharacter(record.getCharacterId()));
            }
            CharacterPlayerStateSnapshot current = migrateLegacySnapshot(
                    record.getCurrent(), missing, fill);
            CharacterPlayerStateSnapshot previous = record.getPrevious() == null
                    ? null : migrateLegacySnapshot(
                            record.getPrevious(), missing, fill);
            migrated.add(new CharacterPlayerStateRecord(
                    record.getCharacterId(), current, previous));
        }
        for (CharacterPlayerStateRecord record : migrated) {
            account.putRecord(record);
        }
    }

    /** The components an account bootstrapped at {@code version} has never held. */
    private List<CharacterStateComponent> componentsIntroducedAfter(int version) {
        ArrayList<CharacterStateComponent> missing =
                new ArrayList<CharacterStateComponent>();
        for (CharacterStateComponent component : this.components) {
            if (this.introducedAt.get(component.getId()).intValue() > version) {
                missing.add(component);
            }
        }
        return missing;
    }

    /**
     * One generation brought to the current version: each component it
     * lacks is filled from {@code fill} when the account had never held
     * it, and is a fault otherwise; each it holds is validated. The
     * whole is validated as a snapshot before it is returned.
     */
    private CharacterPlayerStateSnapshot migrateLegacySnapshot(
            CharacterPlayerStateSnapshot snapshot,
            List<CharacterStateComponent> missing,
            Map<String, NBTTagCompound> fill)
            throws CharacterStateValidationException {
        if (snapshot == null || snapshot.getDataVersion() <= 0
                || snapshot.getDataVersion()
                > CharacterPlayerStateSnapshot.CURRENT_DATA_VERSION) {
            throw new CharacterStateValidationException(
                    "Unsupported legacy character snapshot version");
        }
        Map<String, NBTTagCompound> migrated = snapshot.copyComponents();
        for (CharacterStateComponent component : this.components) {
            NBTTagCompound stored = migrated.get(component.getId());
            if (stored != null) {
                component.validate(stored);
                continue;
            }
            NBTTagCompound value = missing.contains(component) && fill != null
                    ? fill.get(component.getId()) : null;
            if (value == null) {
                throw new CharacterStateValidationException(
                        "Legacy migration state for " + component.getId()
                                + " is unavailable");
            }
            migrated.put(component.getId(), (NBTTagCompound)value.copy());
        }
        CharacterPlayerStateSnapshot upgraded = new CharacterPlayerStateSnapshot(
                snapshot.getCharacterId(),
                snapshot.getGeneration(),
                snapshot.getCapturedAt(),
                CharacterPlayerStateSnapshot.CURRENT_DATA_VERSION,
                migrated);
        validateSnapshot(upgraded);
        return upgraded;
    }

    private void applyPhase(EntityPlayerMP player,
                            CharacterPlayerStateSnapshot snapshot,
                            CharacterStateApplyPhase phase)
            throws CharacterStateValidationException {
        for (CharacterStateComponent component : this.components) {
            if (component.getApplyPhase() != phase) {
                continue;
            }
            NBTTagCompound state = snapshot.getComponent(component.getId());
            component.apply(player, state);
        }
    }

    private Map<String, NBTTagCompound> captureComponents(EntityPlayerMP player)
            throws CharacterStateValidationException {
        LinkedHashMap<String, NBTTagCompound> captured =
                new LinkedHashMap<String, NBTTagCompound>();
        for (CharacterStateComponent component : this.components) {
            NBTTagCompound state = component.capture(player);
            component.validate(state);
            captured.put(component.getId(), (NBTTagCompound) state.copy());
        }
        CharacterPlayerStateSnapshot probe = createSnapshot(
                UUID.randomUUID(), 1L, System.currentTimeMillis(), captured);
        validateSnapshot(probe);
        return captured;
    }

    private Map<String, NBTTagCompound> createDefaultComponents(
            RoleplayCharacter character)
            throws CharacterStateValidationException {
        LinkedHashMap<String, NBTTagCompound> defaults =
                new LinkedHashMap<String, NBTTagCompound>();
        for (CharacterStateComponent component : this.components) {
            NBTTagCompound state;
            if (character != null && component == this.lotrProgressionComponent) {
                // A character made in the roster names the faction it starts
                // with, and starts with alignment for it. Unaligned is
                // nobody's chosen side: it is the default character's, the
                // identity that was already being played, so its progression
                // is whatever that player had, and clean progression is the
                // right blank for it.
                state = isUnaligned(character)
                        ? this.lotrProgressionComponent.createDefault()
                        : this.lotrProgressionComponent.createDefault(
                                character.getStartingFactionId());
            } else if (component == this.locationComponent) {
                state = createInitialLocation(character);
            } else {
                state = component.createDefault();
            }
            component.validate(state);
            defaults.put(component.getId(), (NBTTagCompound) state.copy());
        }
        return defaults;
    }

    public void transitionLocation(EntityPlayerMP player,
                                   CharacterPlayerStateSnapshot snapshot)
            throws CharacterStateValidationException {
        validateSnapshot(snapshot);
        this.locationTransitionService.transition(
                player, snapshot.getComponent(VanillaLocationStateComponent.ID));
    }

    /** Whether the character's faction is Unaligned, or none, which is the same. */
    private static boolean isUnaligned(RoleplayCharacter character) {
        return LotrCharacterAdapter.UNALIGNED_FACTION_ID.equals(
                LotrCharacterAdapter.factionIdOrUnaligned(
                        character.getStartingFactionId()));
    }

    private NBTTagCompound createInitialLocation(RoleplayCharacter character) {
        if (character == null) {
            return this.locationComponent.createDefault();
        }
        String waypointId = character.getStartingWaypointId();
        // Unaligned has no starting place of its own; such a character
        // starts where the world's default puts it.
        if (waypointId.length() == 0 && !isUnaligned(character)) {
            waypointId = LotrCharacterAdapter.getInstance()
                    .resolveStartingWaypointId(
                            character.getStartingFactionId(), "",
                            character.hasUnconventionalSettings());
        }
        return waypointId == null || waypointId.length() == 0
                ? this.locationComponent.createDefault()
                : this.locationComponent.createStartingWaypoint(waypointId);
    }

    private CharacterPlayerStateSnapshot createSnapshot(
            UUID characterId,
            long generation,
            long timestamp,
            Map<String, NBTTagCompound> components)
            throws CharacterStateValidationException {
        CharacterPlayerStateSnapshot snapshot = new CharacterPlayerStateSnapshot(
                characterId,
                generation,
                timestamp,
                CharacterPlayerStateSnapshot.CURRENT_DATA_VERSION,
                components);
        validateSnapshot(snapshot);
        return snapshot;
    }

    private static NBTTagCompound toSizeCompound(
            CharacterPlayerStateSnapshot snapshot) {
        NBTTagCompound root = new NBTTagCompound();
        root.setLong("CharacterUUIDMost",
                snapshot.getCharacterId().getMostSignificantBits());
        root.setLong("CharacterUUIDLeast",
                snapshot.getCharacterId().getLeastSignificantBits());
        root.setLong("Generation", snapshot.getGeneration());
        NBTTagCompound components = new NBTTagCompound();
        for (Map.Entry<String, NBTTagCompound> entry
                : snapshot.getComponentsView().entrySet()) {
            components.setTag(entry.getKey(), entry.getValue().copy());
        }
        root.setTag("Components", components);
        return root;
    }

    private static void requirePlayerRoster(EntityPlayerMP player,
                                            CharacterRoster roster)
            throws CharacterStateValidationException {
        if (player == null || player.worldObj == null || player.worldObj.isRemote
                || player.getUniqueID() == null || roster == null
                || !player.getUniqueID().equals(roster.getOwnerId())) {
            throw new CharacterStateValidationException(
                    "Player and character roster are unavailable or do not match");
        }
    }

    private static void requireIdentityOwner(EntityPlayerMP player,
                                             PlayableIdentity identity)
            throws CharacterStateValidationException {
        if (player == null || identity == null
                || !identity.getOwnerId().equals(player.getUniqueID())) {
            throw new CharacterStateValidationException(
                    "Identity does not belong to the player");
        }
    }

    private static void requireAccount(EntityPlayerMP player,
                                       CharacterPlayerStateAccount account,
                                       CharacterPlayerStateWorldData data)
            throws CharacterStateValidationException {
        if (player == null || player.getUniqueID() == null || account == null
                || data == null || data.isReadOnlyForNewerVersion()
                || data.isOwnerBlocked(player.getUniqueID())
                || !player.getUniqueID().equals(account.getOwnerId())) {
            throw new CharacterStateValidationException(
                    "Character player-state account is unavailable or does not match");
        }
    }
}
