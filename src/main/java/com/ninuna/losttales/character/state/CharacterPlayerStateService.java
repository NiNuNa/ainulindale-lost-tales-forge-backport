package com.ninuna.losttales.character.state;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.server.CharacterRaceGameplayHandler;
import com.ninuna.losttales.character.state.component.LostTalesQuestStateComponent;
import com.ninuna.losttales.character.state.component.LotrFastTravelRegionStateComponent;
import com.ninuna.losttales.character.state.component.LotrProgressionStateComponent;
import com.ninuna.losttales.character.state.component.LotrQuestStateComponent;
import com.ninuna.losttales.character.state.component.VanillaInventoryStateComponent;
import com.ninuna.losttales.character.state.component.VanillaPotionStateComponent;
import com.ninuna.losttales.character.state.component.VanillaStatisticsStateComponent;
import com.ninuna.losttales.character.state.component.VanillaVitalsStateComponent;
import com.ninuna.losttales.config.LostTalesConfig;
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
    private final VanillaStatisticsStateComponent statisticsComponent;
    private final LostTalesQuestStateComponent questComponent;
    private final LotrFastTravelRegionStateComponent lotrRegionComponent;
    private final LotrProgressionStateComponent lotrProgressionComponent;
    private final LotrQuestStateComponent lotrQuestComponent;

    private CharacterPlayerStateService() {
        ArrayList<CharacterStateComponent> registered =
                new ArrayList<CharacterStateComponent>();
        registered.add(new VanillaInventoryStateComponent());
        registered.add(new VanillaPotionStateComponent());
        this.statisticsComponent = new VanillaStatisticsStateComponent();
        registered.add(this.statisticsComponent);
        this.questComponent = new LostTalesQuestStateComponent();
        registered.add(this.questComponent);
        this.lotrRegionComponent = new LotrFastTravelRegionStateComponent();
        registered.add(this.lotrRegionComponent);
        this.lotrProgressionComponent = new LotrProgressionStateComponent();
        registered.add(this.lotrProgressionComponent);
        this.lotrQuestComponent = new LotrQuestStateComponent();
        registered.add(this.lotrQuestComponent);
        registered.add(new VanillaVitalsStateComponent());
        this.components = Collections.unmodifiableList(registered);

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

    public static CharacterPlayerStateService getInstance() {
        return INSTANCE;
    }

    /**
     * One-time import and schema migration. The live account state is assigned
     * only to the active character (or the explicitly selected import target
     * when no active ID exists). Every other character receives clean defaults
     * for newly introduced character-owned components.
     */
    public CharacterPlayerStateAccount ensureBootstrapped(
            EntityPlayerMP player,
            CharacterRoster roster,
            CharacterPlayerStateWorldData data,
            UUID selectedImportTarget)
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
        UUID importTarget = null;
        if (account.getBootstrapVersion()
                < CharacterPlayerStateAccount.CURRENT_BOOTSTRAP_VERSION) {
            importTarget = resolveImportTarget(roster, selectedImportTarget);
        }

        if (account.getBootstrapVersion() == 0) {
            Map<String, NBTTagCompound> liveState = importTarget == null
                    ? null : captureComponents(player);
            Map<String, NBTTagCompound> defaultState = createDefaultComponents();
            ArrayList<CharacterPlayerStateRecord> initialized =
                    new ArrayList<CharacterPlayerStateRecord>();
            for (RoleplayCharacter character : roster.getCharacters()) {
                Map<String, NBTTagCompound> initial =
                        character.getCharacterId().equals(importTarget)
                                ? liveState : defaultState;
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
            migrateLegacySnapshots(player, account, importTarget);
            account.markBootstrapped(now);
            changed = true;
        }

        // Characters created after bootstrap normally start with a clean
        // independent save. The one exception is a newly created first/active
        // character: the live account state must become that character's save
        // rather than being silently replaced with defaults on its first switch.
        UUID activeId = roster.getActiveCharacterId();
        for (RoleplayCharacter character : roster.getCharacters()) {
            if (account.getRecord(character.getCharacterId()) != null) {
                continue;
            }
            Map<String, NBTTagCompound> initialState =
                    character.getCharacterId().equals(activeId)
                            ? captureComponents(player)
                            : createDefaultComponents();
            CharacterPlayerStateSnapshot snapshot = createSnapshot(
                    character.getCharacterId(), 1L, now, initialState);
            account.putRecord(new CharacterPlayerStateRecord(
                    character.getCharacterId(), snapshot, null));
            changed = true;
        }

        if (changed) {
            data.saveAccount(account);
        }
        return account;
    }

    public CharacterPlayerStateSnapshot captureAndAppend(
            EntityPlayerMP player,
            CharacterPlayerStateAccount account,
            CharacterPlayerStateWorldData data,
            UUID characterId)
            throws CharacterStateValidationException {
        if (characterId == null) {
            return null;
        }
        requireAccount(player, account, data);
        CharacterPlayerStateRecord record = account.getRecord(characterId);
        if (record == null) {
            throw new CharacterStateValidationException(
                    "No player-state record exists for source character " + characterId);
        }
        Map<String, NBTTagCompound> components = captureComponents(player);
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
        if (player == null || character == null || snapshot == null
                || !character.getCharacterId().equals(snapshot.getCharacterId())) {
            throw new CharacterStateValidationException(
                    "Target character and snapshot do not match");
        }
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
    }

    public void clearAllRuntimeState() {
        this.statisticsComponent.clearAllRuntimeState();
    }

    public void saveActiveLiveState(EntityPlayerMP player,
                                    CharacterRoster roster,
                                    CharacterPlayerStateWorldData data,
                                    boolean flush)
            throws CharacterStateValidationException {
        requirePlayerRoster(player, roster);
        UUID activeId = roster.getActiveCharacterId();
        if (activeId == null || !player.isEntityAlive() || player.isDead
                || player.getHealth() <= 0.0F) {
            return;
        }
        CharacterPlayerStateAccount account = ensureBootstrapped(
                player, roster, data, null);
        captureAndAppend(player, account, data, activeId);
        if (flush) {
            CharacterPlayerStateStorage.flush(player.worldObj);
        }
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

    private UUID resolveImportTarget(CharacterRoster roster,
                                     UUID selectedImportTarget)
            throws CharacterStateValidationException {
        UUID importTarget = roster.getActiveCharacterId();
        if (importTarget == null) {
            importTarget = selectedImportTarget;
        }
        if (roster.getCharacterCount() > 0
                && (importTarget == null
                || roster.getCharacter(importTarget) == null)) {
            throw new CharacterStateValidationException(
                    "An existing character must be selected as the legacy import target");
        }
        return importTarget;
    }

    /** Upgrades retained generations without changing transaction references. */
    private void migrateLegacySnapshots(EntityPlayerMP player,
                                        CharacterPlayerStateAccount account,
                                        UUID importTarget)
            throws CharacterStateValidationException {
        boolean migrateStatistics = account.getBootstrapVersion() < 2;
        boolean migrateQuests = account.getBootstrapVersion() < 3;
        boolean migrateLotrProgression = account.getBootstrapVersion() < 4;
        boolean migrateLotrQuests = account.getBootstrapVersion() < 5;
        boolean migrateLotrRegions = account.getBootstrapVersion() < 6;

        NBTTagCompound importedStatistics = !migrateStatistics || importTarget == null
                ? null : this.statisticsComponent.capture(player);
        NBTTagCompound defaultStatistics = !migrateStatistics
                ? null : this.statisticsComponent.createDefault();
        if (defaultStatistics != null) {
            this.statisticsComponent.validate(defaultStatistics);
        }

        NBTTagCompound importedQuests = !migrateQuests || importTarget == null
                ? null : this.questComponent.capture(player);
        NBTTagCompound defaultQuests = !migrateQuests
                ? null : this.questComponent.createDefault();
        if (defaultQuests != null) {
            this.questComponent.validate(defaultQuests);
        }

        NBTTagCompound importedLotrProgression =
                !migrateLotrProgression || importTarget == null
                        ? null : this.lotrProgressionComponent.capture(player);
        NBTTagCompound defaultLotrProgression = !migrateLotrProgression
                ? null : this.lotrProgressionComponent.createDefault();
        if (defaultLotrProgression != null) {
            this.lotrProgressionComponent.validate(defaultLotrProgression);
        }

        NBTTagCompound importedLotrQuests =
                !migrateLotrQuests || importTarget == null
                        ? null : this.lotrQuestComponent.capture(player);
        NBTTagCompound defaultLotrQuests = !migrateLotrQuests
                ? null : this.lotrQuestComponent.createDefault();
        if (defaultLotrQuests != null) {
            this.lotrQuestComponent.validate(defaultLotrQuests);
        }

        NBTTagCompound importedLotrRegions =
                !migrateLotrRegions || importTarget == null
                        ? null : this.lotrRegionComponent.capture(player);
        NBTTagCompound defaultLotrRegions = !migrateLotrRegions
                ? null : this.lotrRegionComponent.createDefault();
        if (defaultLotrRegions != null) {
            this.lotrRegionComponent.validate(defaultLotrRegions);
        }

        ArrayList<CharacterPlayerStateRecord> existing =
                new ArrayList<CharacterPlayerStateRecord>(account.getRecords());
        ArrayList<CharacterPlayerStateRecord> migrated =
                new ArrayList<CharacterPlayerStateRecord>(existing.size());
        for (CharacterPlayerStateRecord record : existing) {
            NBTTagCompound statistics = record.getCharacterId().equals(importTarget)
                    ? importedStatistics : defaultStatistics;
            NBTTagCompound quests = record.getCharacterId().equals(importTarget)
                    ? importedQuests : defaultQuests;
            NBTTagCompound lotrProgression =
                    record.getCharacterId().equals(importTarget)
                            ? importedLotrProgression : defaultLotrProgression;
            NBTTagCompound lotrQuests =
                    record.getCharacterId().equals(importTarget)
                            ? importedLotrQuests : defaultLotrQuests;
            NBTTagCompound lotrRegions =
                    record.getCharacterId().equals(importTarget)
                            ? importedLotrRegions : defaultLotrRegions;
            CharacterPlayerStateSnapshot current = migrateLegacySnapshot(
                    record.getCurrent(), statistics, quests, lotrProgression,
                    lotrQuests, lotrRegions);
            CharacterPlayerStateSnapshot previous = record.getPrevious() == null
                    ? null : migrateLegacySnapshot(
                            record.getPrevious(), statistics, quests,
                            lotrProgression, lotrQuests, lotrRegions);
            migrated.add(new CharacterPlayerStateRecord(
                    record.getCharacterId(), current, previous));
        }
        // Keep the account unchanged until every retained generation has passed
        // component and size validation. This matters because older world-data
        // roots are already dirty and could otherwise persist a half migration.
        for (CharacterPlayerStateRecord record : migrated) {
            account.putRecord(record);
        }
    }

    private CharacterPlayerStateSnapshot migrateLegacySnapshot(
            CharacterPlayerStateSnapshot snapshot,
            NBTTagCompound statistics,
            NBTTagCompound quests,
            NBTTagCompound lotrProgression,
            NBTTagCompound lotrQuests,
            NBTTagCompound lotrRegions)
            throws CharacterStateValidationException {
        if (snapshot == null || snapshot.getDataVersion() <= 0
                || snapshot.getDataVersion()
                > CharacterPlayerStateSnapshot.CURRENT_DATA_VERSION) {
            throw new CharacterStateValidationException(
                    "Unsupported legacy character snapshot version");
        }
        Map<String, NBTTagCompound> migrated = snapshot.copyComponents();
        NBTTagCompound existingStatistics = migrated.get(
                VanillaStatisticsStateComponent.ID);
        if (existingStatistics == null) {
            if (statistics == null) {
                throw new CharacterStateValidationException(
                        "Legacy statistics migration state is unavailable");
            }
            migrated.put(VanillaStatisticsStateComponent.ID,
                    (NBTTagCompound) statistics.copy());
        } else {
            this.statisticsComponent.validate(existingStatistics);
        }
        NBTTagCompound existingQuests = migrated.get(
                LostTalesQuestStateComponent.ID);
        if (existingQuests == null) {
            if (quests == null) {
                throw new CharacterStateValidationException(
                        "Legacy Lost Tales quest migration state is unavailable");
            }
            migrated.put(LostTalesQuestStateComponent.ID,
                    (NBTTagCompound) quests.copy());
        } else {
            this.questComponent.validate(existingQuests);
        }
        NBTTagCompound existingLotrProgression = migrated.get(
                LotrProgressionStateComponent.ID);
        if (existingLotrProgression == null) {
            if (lotrProgression == null) {
                throw new CharacterStateValidationException(
                        "Legacy LOTR progression migration state is unavailable");
            }
            migrated.put(LotrProgressionStateComponent.ID,
                    (NBTTagCompound) lotrProgression.copy());
        } else {
            this.lotrProgressionComponent.validate(existingLotrProgression);
        }
        NBTTagCompound existingLotrQuests = migrated.get(
                LotrQuestStateComponent.ID);
        if (existingLotrQuests == null) {
            if (lotrQuests == null) {
                throw new CharacterStateValidationException(
                        "Legacy LOTR quest migration state is unavailable");
            }
            migrated.put(LotrQuestStateComponent.ID,
                    (NBTTagCompound) lotrQuests.copy());
        } else {
            this.lotrQuestComponent.validate(existingLotrQuests);
        }
        NBTTagCompound existingLotrRegions = migrated.get(
                LotrFastTravelRegionStateComponent.ID);
        if (existingLotrRegions == null) {
            if (lotrRegions == null) {
                throw new CharacterStateValidationException(
                        "Legacy LOTR region migration state is unavailable");
            }
            migrated.put(LotrFastTravelRegionStateComponent.ID,
                    (NBTTagCompound)lotrRegions.copy());
        } else {
            this.lotrRegionComponent.validate(existingLotrRegions);
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

    private Map<String, NBTTagCompound> createDefaultComponents()
            throws CharacterStateValidationException {
        LinkedHashMap<String, NBTTagCompound> defaults =
                new LinkedHashMap<String, NBTTagCompound>();
        for (CharacterStateComponent component : this.components) {
            NBTTagCompound state = component.createDefault();
            component.validate(state);
            defaults.put(component.getId(), (NBTTagCompound) state.copy());
        }
        return defaults;
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
