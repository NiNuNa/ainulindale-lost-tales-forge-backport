package com.ninuna.losttales.character.lore.transfer;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.lore.LoreCharacterDefinition;
import com.ninuna.losttales.character.lore.LoreCharacterRegistry;
import com.ninuna.losttales.character.lore.ownership.LoreCharacterOwnershipRecord;
import com.ninuna.losttales.character.lore.ownership.LoreCharacterOwnershipResult;
import com.ninuna.losttales.character.lore.ownership.LoreCharacterOwnershipStorage;
import com.ninuna.losttales.character.lore.ownership.LoreCharacterOwnershipWorldData;
import com.ninuna.losttales.character.model.CharacterProgression;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterFactionDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.server.CharacterOperationResult;
import com.ninuna.losttales.character.state.CharacterPlayerStateAccount;
import com.ninuna.losttales.character.state.CharacterPlayerStateRecord;
import com.ninuna.losttales.character.state.CharacterPlayerStateService;
import com.ninuna.losttales.character.state.CharacterPlayerStateStorage;
import com.ninuna.losttales.character.state.CharacterPlayerStateWorldData;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.character.validation.CharacterValidationResult;
import com.ninuna.losttales.character.validation.CharacterValidator;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.character.switching.CharacterSwitchCoordinator;
import com.ninuna.losttales.party.server.PartyOperationResult;
import com.ninuna.losttales.party.server.PartyService;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Server-thread transaction coordinator for unique lore-character claims and
 * releases. A full metadata/state copy is flushed before any roster access is
 * changed, and each later phase is idempotent for startup/login recovery.
 */
public final class LoreCharacterTransferCoordinator {

    private static final LoreCharacterTransferCoordinator INSTANCE =
            new LoreCharacterTransferCoordinator();

    private LoreCharacterTransferCoordinator() {}

    public static LoreCharacterTransferCoordinator getInstance() {
        return INSTANCE;
    }

    public synchronized CharacterOperationResult claim(
            EntityPlayerMP player,
            String loreCharacterId,
            long expectedOwnershipRevision,
            long expectedRosterRevision,
            int slotIndex) {
        CharacterErrorId basic = validateManagement(player);
        if (basic != CharacterErrorId.NONE) return failure(basic, null);

        try {
            Stores stores = Stores.open(player.worldObj);
            CharacterRoster roster = stores.characters.getOrCreateRoster(
                    player.getUniqueID());
            CharacterErrorId storageError = stores.validateWritable(
                    player.getUniqueID());
            if (storageError != CharacterErrorId.NONE) {
                return failure(storageError, roster);
            }
            if (roster.getRevision() != expectedRosterRevision) {
                return failure(CharacterErrorId.STALE_ROSTER, roster);
            }
            if (!CharacterRoster.isValidSlotIndex(slotIndex)) {
                return failure(CharacterErrorId.INVALID_SLOT, roster);
            }
            if (slotIndex >= roster.getUnlockedSlotCount()) {
                return failure(CharacterErrorId.SLOT_HIDDEN, roster);
            }
            if (roster.getCharacterAtSlot(slotIndex) != null) {
                return failure(CharacterErrorId.SLOT_OCCUPIED, roster);
            }
            if (stores.transfers.getTransaction(loreCharacterId) != null) {
                return failure(
                        CharacterErrorId.LORE_CHARACTER_TRANSFER_IN_PROGRESS,
                        roster);
            }

            LoreCharacterDefinition definition = LoreCharacterRegistry.get(
                    loreCharacterId);
            if (definition == null) {
                return failure(CharacterErrorId.LORE_CHARACTER_UNKNOWN, roster);
            }
            if (!definition.hasAppearance()
                    || !LoreCharacterRegistry.getLoadErrors().isEmpty()) {
                return failure(
                        CharacterErrorId.LORE_CHARACTER_DEFINITION_INCOMPLETE,
                        roster);
            }
            LoreCharacterOwnershipRecord ownership =
                    stores.ownership.getRecord(definition.getId());
            long actualRevision = ownership == null ? 0L
                    : ownership.getRevision();
            if (actualRevision != expectedOwnershipRevision) {
                return failure(
                        CharacterErrorId.LORE_CHARACTER_STALE_OWNERSHIP,
                        roster);
            }
            if (ownership != null && ownership.isClaimed()) {
                return failure(player.getUniqueID().equals(ownership.getOwnerId())
                        ? CharacterErrorId.LORE_CHARACTER_ALREADY_OWNED
                        : CharacterErrorId.LORE_CHARACTER_UNAVAILABLE, roster);
            }

            CharacterPlayerStateAccount account =
                    CharacterPlayerStateService.getInstance().ensureBootstrapped(
                            player, roster, stores.playerState, null);
            LoreCharacterVaultEntry vault = stores.transfers.getVaultEntry(
                    definition.getId());
            UUID characterId = ownership != null
                    ? ownership.getCharacterId()
                    : vault != null ? vault.getCharacterId() : uniqueId(stores);
            if (characterId == null
                    || ownership != null && vault != null
                    && !ownership.getCharacterId().equals(vault.getCharacterId())) {
                return failure(CharacterErrorId.LORE_CHARACTER_STATE_UNAVAILABLE,
                        roster);
            }

            RoleplayCharacter character;
            CharacterPlayerStateRecord state;
            if (vault == null) {
                character = createInitialCharacter(
                        definition, characterId, player.getUniqueID(), slotIndex);
                if (character == null) {
                    return failure(
                            CharacterErrorId.LORE_CHARACTER_DEFINITION_INCOMPLETE,
                            roster);
                }
                state = CharacterPlayerStateService.getInstance()
                        .createDefaultRecord(character);
            } else {
                character = rebind(vault.getCharacterCopy(),
                        player.getUniqueID(), slotIndex);
                state = vault.getPlayerStateCopy();
            }
            if (account.getRecord(characterId) != null) {
                return failure(CharacterErrorId.LORE_CHARACTER_STATE_UNAVAILABLE,
                        roster);
            }

            stores.transfers.saveVault(new LoreCharacterVaultEntry(
                    definition.getId(), player.getUniqueID(), character, state,
                    System.currentTimeMillis()));
            LoreCharacterTransferRecord transaction =
                    new LoreCharacterTransferRecord(
                            UUID.randomUUID(),
                            LoreCharacterTransferRecord.Type.CLAIM,
                            definition.getId(), characterId, null,
                            player.getUniqueID(), slotIndex,
                            expectedOwnershipRevision, 0,
                            System.currentTimeMillis());
            stores.transfers.begin(transaction);
            LoreCharacterTransferStorage.flush(player.worldObj);

            CharacterErrorId recovered = recover(stores, transaction);
            if (recovered != CharacterErrorId.NONE) {
                return failure(recovered, roster);
            }
            CharacterRoster committed = stores.characters.getRoster(
                    player.getUniqueID());
            if (committed != null && characterId.equals(
                    committed.getActiveCharacterId())) {
                CharacterErrorId initialization =
                        CharacterSwitchCoordinator.getInstance()
                                .initializeNewActiveCharacter(player, characterId);
                if (initialization != CharacterErrorId.NONE) {
                    FMLLog.warning("[%s] Newly claimed first lore character "
                                    + "%s remains pending initialization: %s",
                            LostTalesMetaData.MOD_ID, characterId,
                            initialization.getId());
                }
            }
            return CharacterOperationResult.success(true, committed,
                    committed == null ? null
                            : committed.getCharacter(characterId));
        } catch (CharacterStateValidationException exception) {
            log("claim_state", player, loreCharacterId, exception);
            return failure(CharacterErrorId.LORE_CHARACTER_STATE_UNAVAILABLE,
                    safeRoster(player));
        } catch (RuntimeException exception) {
            log("claim", player, loreCharacterId, exception);
            return failure(CharacterErrorId.INTERNAL_ERROR, safeRoster(player));
        }
    }

    public synchronized CharacterOperationResult release(
            EntityPlayerMP player,
            String loreCharacterId,
            long expectedOwnershipRevision,
            long expectedRosterRevision) {
        CharacterErrorId basic = validateManagement(player);
        if (basic != CharacterErrorId.NONE) return failure(basic, null);
        try {
            Stores stores = Stores.open(player.worldObj);
            CharacterRoster roster = stores.characters.getOrCreateRoster(
                    player.getUniqueID());
            CharacterErrorId storageError = stores.validateWritable(
                    player.getUniqueID());
            if (storageError != CharacterErrorId.NONE) {
                return failure(storageError, roster);
            }
            if (roster.getRevision() != expectedRosterRevision) {
                return failure(CharacterErrorId.STALE_ROSTER, roster);
            }
            if (stores.transfers.getTransaction(loreCharacterId) != null) {
                return failure(
                        CharacterErrorId.LORE_CHARACTER_TRANSFER_IN_PROGRESS,
                        roster);
            }
            LoreCharacterOwnershipRecord ownership =
                    stores.ownership.getRecord(loreCharacterId);
            if (ownership == null || !ownership.isClaimed()
                    || !player.getUniqueID().equals(ownership.getOwnerId())) {
                return failure(CharacterErrorId.LORE_CHARACTER_NOT_OWNED,
                        roster);
            }
            if (ownership.getRevision() != expectedOwnershipRevision) {
                return failure(
                        CharacterErrorId.LORE_CHARACTER_STALE_OWNERSHIP,
                        roster);
            }
            RoleplayCharacter character = roster.getCharacter(
                    ownership.getCharacterId());
            if (character == null) {
                return failure(CharacterErrorId.LORE_CHARACTER_STATE_UNAVAILABLE,
                        roster);
            }
            if (character.getCharacterId().equals(roster.getActiveCharacterId())) {
                return failure(CharacterErrorId.LORE_CHARACTER_ACTIVE, roster);
            }
            CharacterPlayerStateAccount account =
                    CharacterPlayerStateService.getInstance().ensureBootstrapped(
                            player, roster, stores.playerState, null);
            CharacterPlayerStateRecord state = account.getRecord(
                    character.getCharacterId());
            if (state == null) {
                return failure(CharacterErrorId.LORE_CHARACTER_STATE_UNAVAILABLE,
                        roster);
            }
            CharacterPlayerStateService.getInstance().validateSnapshot(
                    state.getCurrent());

            stores.transfers.saveVault(new LoreCharacterVaultEntry(
                    ownership.getLoreCharacterId(), player.getUniqueID(),
                    character, state, System.currentTimeMillis()));
            LoreCharacterTransferRecord transaction =
                    new LoreCharacterTransferRecord(
                            UUID.randomUUID(),
                            LoreCharacterTransferRecord.Type.RELEASE,
                            ownership.getLoreCharacterId(),
                            ownership.getCharacterId(), player.getUniqueID(),
                            null, -1, expectedOwnershipRevision, 0,
                            System.currentTimeMillis());
            stores.transfers.begin(transaction);
            LoreCharacterTransferStorage.flush(player.worldObj);

            CharacterErrorId recovered = recover(stores, transaction);
            CharacterRoster committed = stores.characters.getRoster(
                    player.getUniqueID());
            return recovered == CharacterErrorId.NONE
                    ? CharacterOperationResult.success(true, committed, character)
                    : failure(recovered, committed);
        } catch (CharacterStateValidationException exception) {
            log("release_state", player, loreCharacterId, exception);
            return failure(CharacterErrorId.LORE_CHARACTER_STATE_UNAVAILABLE,
                    safeRoster(player));
        } catch (RuntimeException exception) {
            log("release", player, loreCharacterId, exception);
            return failure(CharacterErrorId.INTERNAL_ERROR, safeRoster(player));
        }
    }

    /** Completes all durable operations, including operations for offline owners. */
    public synchronized void recoverAll(World world) {
        if (world == null || world.isRemote) return;
        Stores stores;
        try {
            stores = Stores.open(world);
        } catch (RuntimeException exception) {
            log("startup_open", null, "", exception);
            return;
        }
        List<LoreCharacterTransferRecord> pending =
                new ArrayList<LoreCharacterTransferRecord>(
                        stores.transfers.getTransactions());
        for (LoreCharacterTransferRecord transaction : pending) {
            try {
                CharacterErrorId error = recover(stores, transaction);
                if (error != CharacterErrorId.NONE) {
                    FMLLog.warning("[%s] Lore-character recovery remains "
                                    + "pending for %s: %s",
                            LostTalesMetaData.MOD_ID,
                            transaction.getLoreCharacterId(), error.getId());
                }
            } catch (RuntimeException exception) {
                log("startup_recover", null,
                        transaction.getLoreCharacterId(), exception);
            }
        }
    }

    public synchronized void recoverForPlayer(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }
        recoverAll(player.worldObj);
    }

    private CharacterErrorId recover(
            Stores stores, LoreCharacterTransferRecord initial) {
        LoreCharacterTransferRecord transaction =
                stores.transfers.getTransaction(initial.getLoreCharacterId());
        if (transaction == null) return CharacterErrorId.NONE;
        UUID affectedOwner = transaction.getType()
                == LoreCharacterTransferRecord.Type.CLAIM
                ? transaction.getTargetOwnerId()
                : transaction.getSourceOwnerId();
        CharacterErrorId storageError = stores.validateWritable(affectedOwner);
        if (storageError != CharacterErrorId.NONE) return storageError;
        LoreCharacterVaultEntry vault = stores.transfers.getVaultEntry(
                transaction.getLoreCharacterId());
        if (vault == null || !vault.getCharacterId().equals(
                transaction.getCharacterId())) {
            return CharacterErrorId.LORE_CHARACTER_STATE_UNAVAILABLE;
        }
        return transaction.getType() == LoreCharacterTransferRecord.Type.CLAIM
                ? recoverClaim(stores, transaction, vault)
                : recoverRelease(stores, transaction, vault);
    }

    private CharacterErrorId recoverClaim(
            Stores stores,
            LoreCharacterTransferRecord transaction,
            LoreCharacterVaultEntry vault) {
        World world = stores.world;
        if (transaction.getStep() == 0) {
            LoreCharacterOwnershipRecord current = stores.ownership.getRecord(
                    transaction.getLoreCharacterId());
            if (current == null || !current.isClaimed()) {
                LoreCharacterOwnershipResult result = stores.ownership.tryClaim(
                        transaction.getLoreCharacterId(),
                        transaction.getTargetOwnerId(),
                        transaction.getCharacterId(),
                        transaction.getExpectedOwnershipRevision(),
                        System.currentTimeMillis());
                if (result.getStatus()
                        != LoreCharacterOwnershipResult.Status.CLAIMED) {
                    return mapOwnership(result.getStatus());
                }
            } else if (!transaction.getTargetOwnerId().equals(current.getOwnerId())
                    || !transaction.getCharacterId().equals(
                    current.getCharacterId())) {
                return CharacterErrorId.LORE_CHARACTER_UNAVAILABLE;
            }
            LoreCharacterOwnershipStorage.flush(world);
            transaction = stores.transfers.advance(
                    transaction.getLoreCharacterId(),
                    transaction.getTransactionId(), 0);
            LoreCharacterTransferStorage.flush(world);
        }
        if (transaction.getStep() == 1) {
            CharacterPlayerStateAccount account =
                    stores.playerState(transaction.getTargetOwnerId())
                            .getOrCreateAccount(transaction.getTargetOwnerId());
            CharacterPlayerStateRecord existing = account.getRecord(
                    transaction.getCharacterId());
            if (existing == null) {
                account.putRecord(vault.getPlayerStateCopy());
                stores.playerState(transaction.getTargetOwnerId())
                        .saveAccount(account);
            } else if (existing.getCurrentGeneration()
                    != vault.getPlayerStateCopy().getCurrentGeneration()) {
                return CharacterErrorId.LORE_CHARACTER_STATE_UNAVAILABLE;
            }
            CharacterPlayerStateStorage.flush(world);
            transaction = stores.transfers.advance(
                    transaction.getLoreCharacterId(),
                    transaction.getTransactionId(), 1);
            LoreCharacterTransferStorage.flush(world);
        }
        if (transaction.getStep() == 2) {
            CharacterRoster roster = stores.characters.getOrCreateRoster(
                    transaction.getTargetOwnerId());
            RoleplayCharacter existing = roster.getCharacter(
                    transaction.getCharacterId());
            if (existing == null) {
                if (roster.getCharacterAtSlot(transaction.getTargetSlot()) != null) {
                    return CharacterErrorId.SLOT_OCCUPIED;
                }
                RoleplayCharacter character = rebind(
                        vault.getCharacterCopy(),
                        transaction.getTargetOwnerId(),
                        transaction.getTargetSlot());
                if (!roster.addCharacter(character)) {
                    return CharacterErrorId.SLOT_OCCUPIED;
                }
                if (roster.getActiveCharacterId() == null
                        && roster.getCharacterCount() == 1) {
                    roster.setActiveCharacterId(character.getCharacterId());
                }
                roster.incrementRevision();
                stores.characters.saveRoster(roster);
            }
            LoreCharacterTransferStorage.flush(world);
            transaction = stores.transfers.advance(
                    transaction.getLoreCharacterId(),
                    transaction.getTransactionId(), 2);
            LoreCharacterTransferStorage.flush(world);
        }
        stores.transfers.complete(transaction.getLoreCharacterId(),
                transaction.getTransactionId());
        LoreCharacterTransferStorage.flush(world);
        return CharacterErrorId.NONE;
    }

    private CharacterErrorId recoverRelease(
            Stores stores,
            LoreCharacterTransferRecord transaction,
            LoreCharacterVaultEntry vault) {
        World world = stores.world;
        if (transaction.getStep() == 0) {
            CharacterRoster roster = stores.characters.getOrCreateRoster(
                    transaction.getSourceOwnerId());
            RoleplayCharacter character = roster.getCharacter(
                    transaction.getCharacterId());
            if (character != null) {
                if (transaction.getCharacterId().equals(
                        roster.getActiveCharacterId())) {
                    return CharacterErrorId.LORE_CHARACTER_ACTIVE;
                }
                PartyOperationResult cleanup = PartyService.getInstance()
                        .removeCharacterForDeletion(world, character);
                if (!cleanup.isSuccessful()) {
                    return CharacterErrorId.PARTY_CLEANUP_FAILED;
                }
                roster.removeCharacter(transaction.getCharacterId());
                roster.incrementRevision();
                stores.characters.saveRoster(roster);
            }
            LoreCharacterTransferStorage.flush(world);
            transaction = stores.transfers.advance(
                    transaction.getLoreCharacterId(),
                    transaction.getTransactionId(), 0);
            LoreCharacterTransferStorage.flush(world);
        }
        if (transaction.getStep() == 1) {
            CharacterPlayerStateWorldData stateData = stores.playerState(
                    transaction.getSourceOwnerId());
            CharacterPlayerStateAccount account = stateData.getAccount(
                    transaction.getSourceOwnerId());
            if (account != null && account.removeRecord(
                    transaction.getCharacterId()) != null) {
                stateData.saveAccount(account);
            }
            CharacterPlayerStateStorage.flush(world);
            transaction = stores.transfers.advance(
                    transaction.getLoreCharacterId(),
                    transaction.getTransactionId(), 1);
            LoreCharacterTransferStorage.flush(world);
        }
        if (transaction.getStep() == 2) {
            LoreCharacterOwnershipRecord current = stores.ownership.getRecord(
                    transaction.getLoreCharacterId());
            if (current != null && current.isClaimed()) {
                LoreCharacterOwnershipResult result = stores.ownership.tryRelease(
                        transaction.getLoreCharacterId(),
                        transaction.getSourceOwnerId(),
                        transaction.getExpectedOwnershipRevision(),
                        System.currentTimeMillis());
                if (result.getStatus()
                        != LoreCharacterOwnershipResult.Status.RELEASED) {
                    return mapOwnership(result.getStatus());
                }
            } else if (current == null
                    || !transaction.getCharacterId().equals(
                    current.getCharacterId())) {
                return CharacterErrorId.LORE_CHARACTER_STATE_UNAVAILABLE;
            }
            LoreCharacterOwnershipStorage.flush(world);
            transaction = stores.transfers.advance(
                    transaction.getLoreCharacterId(),
                    transaction.getTransactionId(), 2);
            LoreCharacterTransferStorage.flush(world);
        }
        stores.transfers.complete(transaction.getLoreCharacterId(),
                transaction.getTransactionId());
        LoreCharacterTransferStorage.flush(world);
        return CharacterErrorId.NONE;
    }

    private static RoleplayCharacter createInitialCharacter(
            LoreCharacterDefinition definition,
            UUID characterId,
            UUID ownerId,
            int slotIndex) {
        LoreCharacterDefinition.Appearance appearance =
                definition.getAppearance();
        String faction = chooseFaction(appearance);
        if (faction == null) return null;
        String waypoint = LotrCharacterAdapter.getInstance()
                .resolveStartingWaypointId(faction, "", false);
        return new RoleplayCharacter(
                characterId, ownerId, slotIndex, definition.getName(),
                appearance.getRaceId(), appearance.getGenderId(),
                appearance.getSkinId(), 18, faction,
                RoleplayCharacter.INITIAL_ROLEPLAY_LEVEL,
                new CharacterProgression(), System.currentTimeMillis(),
                RoleplayCharacter.CURRENT_DATA_VERSION,
                RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID,
                waypoint == null ? "" : waypoint,
                false, definition.getDescription());
    }

    private static String chooseFaction(
            LoreCharacterDefinition.Appearance appearance) {
        if (appearance == null) return null;
        CharacterRaceDefinition race = CharacterRaceRegistry.get(
                appearance.getRaceId());
        if (race == null) return null;
        LotrCharacterAdapter adapter = LotrCharacterAdapter.getInstance();
        List<String> ids = adapter.getPlayableFactionIds();
        String hint = factionHint(appearance);
        String fallback = null;
        for (String id : ids) {
            CharacterFactionDefinition faction = adapter.resolve(id);
            if (faction == null || !race.isCompatibleWith(faction)) continue;
            if (fallback == null) fallback = id;
            String compact = id.toLowerCase(Locale.ROOT)
                    .replace("_", "").replace("-", "");
            if (hint.length() > 0 && compact.contains(hint)) return id;
        }
        return fallback;
    }

    private static String factionHint(
            LoreCharacterDefinition.Appearance appearance) {
        String skin = appearance.getSkinId().toLowerCase(Locale.ROOT);
        String race = appearance.getRaceId();
        if (skin.contains("rohan")) return "rohan";
        if (skin.contains("gondor")) return "gondor";
        if (skin.contains("bree")) return "bree";
        if (CharacterRaceRegistry.HOBBIT.equals(race)) return "hobbit";
        if (CharacterRaceRegistry.ELF.equals(race)) return "highelf";
        if (CharacterRaceRegistry.DWARF.equals(race)) return "durinsfolk";
        if (CharacterRaceRegistry.ORC.equals(race)
                || CharacterRaceRegistry.URUK.equals(race)) return "mordor";
        if (CharacterRaceRegistry.HALF_TROLL.equals(race)) return "halftroll";
        return "bree";
    }

    private static RoleplayCharacter rebind(
            RoleplayCharacter source, UUID ownerId, int slotIndex) {
        return new RoleplayCharacter(
                source.getCharacterId(), ownerId, slotIndex, source.getName(),
                source.getRaceId(), source.getGenderId(), source.getSkinId(),
                source.getAge(), source.getStartingFactionId(),
                source.getRoleplayLevel(), source.getProgression(),
                source.getCreationTimestamp(),
                RoleplayCharacter.CURRENT_DATA_VERSION,
                source.isMinecraftCapeVisible(), source.getCosmeticCapeId(),
                source.getStartingWaypointId(),
                source.hasUnconventionalSettings(), source.getDescription());
    }

    private static UUID uniqueId(Stores stores) {
        for (int attempt = 0; attempt < 16; attempt++) {
            UUID id = UUID.randomUUID();
            if (stores.characters.findCharacter(id) == null
                    && stores.ownership.getRecordByCharacterId(id) == null) {
                return id;
            }
        }
        return null;
    }

    private static CharacterErrorId mapOwnership(
            LoreCharacterOwnershipResult.Status status) {
        if (status == LoreCharacterOwnershipResult.Status.STORAGE_READ_ONLY) {
            return CharacterErrorId.LORE_CHARACTER_OWNERSHIP_STORAGE_READ_ONLY;
        }
        if (status == LoreCharacterOwnershipResult.Status.STALE_REVISION) {
            return CharacterErrorId.LORE_CHARACTER_STALE_OWNERSHIP;
        }
        if (status == LoreCharacterOwnershipResult.Status.ALREADY_CLAIMED
                || status == LoreCharacterOwnershipResult.Status.ALREADY_OWNED_BY_REQUESTER) {
            return CharacterErrorId.LORE_CHARACTER_UNAVAILABLE;
        }
        if (status == LoreCharacterOwnershipResult.Status.UNKNOWN_LORE_CHARACTER) {
            return CharacterErrorId.LORE_CHARACTER_UNKNOWN;
        }
        if (status == LoreCharacterOwnershipResult.Status.APPEARANCE_NOT_CONFIGURED
                || status == LoreCharacterOwnershipResult.Status.DEFINITION_REGISTRY_INVALID) {
            return CharacterErrorId.LORE_CHARACTER_DEFINITION_INCOMPLETE;
        }
        return CharacterErrorId.INTERNAL_ERROR;
    }

    private static CharacterErrorId validateManagement(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return CharacterErrorId.INVALID_PLAYER;
        }
        CharacterValidationResult result =
                CharacterValidator.validatePlayerCanManage(player);
        return result.isValid() ? CharacterErrorId.NONE : result.getErrorId();
    }

    private static CharacterOperationResult failure(
            CharacterErrorId error, CharacterRoster roster) {
        return CharacterOperationResult.failure(error, roster);
    }

    private static CharacterRoster safeRoster(EntityPlayerMP player) {
        try {
            return player == null || player.worldObj == null ? null
                    : CharacterStorage.get(player.worldObj).getRoster(
                    player.getUniqueID());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void log(String phase, EntityPlayerMP player,
                            String loreId, Throwable throwable) {
        try {
            FMLLog.warning("[%s] Lore-character transfer phase %s failed "
                            + "for owner %s, identity %s: %s",
                    LostTalesMetaData.MOD_ID, phase,
                    player == null ? "offline" : player.getUniqueID(),
                    loreId, throwable == null ? "unknown" : throwable.toString());
        } catch (Throwable ignored) {}
    }

    private static final class Stores {
        private final World world;
        private final CharacterWorldData characters;
        private final LoreCharacterOwnershipWorldData ownership;
        private final LoreCharacterTransferWorldData transfers;
        private final java.util.Map<UUID, CharacterPlayerStateWorldData> states =
                new java.util.HashMap<UUID, CharacterPlayerStateWorldData>();
        private CharacterPlayerStateWorldData playerState;

        private Stores(World world) {
            this.world = world;
            this.characters = CharacterStorage.get(world);
            this.ownership = LoreCharacterOwnershipStorage.get(world);
            this.transfers = LoreCharacterTransferStorage.get(world);
        }

        private static Stores open(World world) { return new Stores(world); }

        private CharacterPlayerStateWorldData playerState(UUID ownerId) {
            CharacterPlayerStateWorldData data = this.states.get(ownerId);
            if (data == null) {
                data = CharacterPlayerStateStorage.get(this.world, ownerId);
                this.states.put(ownerId, data);
            }
            return data;
        }

        private CharacterErrorId validateWritable(UUID ownerId) {
            if (this.characters.isReadOnlyForNewerVersion()) {
                return CharacterErrorId.STORAGE_READ_ONLY;
            }
            if (this.ownership.isReadOnly()) {
                return CharacterErrorId.LORE_CHARACTER_OWNERSHIP_STORAGE_READ_ONLY;
            }
            if (this.transfers.isReadOnly()) {
                return CharacterErrorId.LORE_CHARACTER_TRANSFER_STORAGE_READ_ONLY;
            }
            this.playerState = playerState(ownerId);
            if (this.playerState.isReadOnlyForNewerVersion()
                    || this.playerState.isOwnerBlocked(ownerId)) {
                return CharacterErrorId.LORE_CHARACTER_STATE_UNAVAILABLE;
            }
            return CharacterErrorId.NONE;
        }
    }
}
