package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Public-API-only adapter for the first character-owned LOTR progression slice.
 *
 * <p>The allowlist is deliberately narrower than LOTRPlayerData.save(). Apply
 * starts with the live account blob and overlays only these keys, preserving
 * preferences, moderation state, fellowships, miniquest records,
 * quest data and transient transport/travel state. Shield, alcohol, and
 * last-death state are handled by a separate character-details component.</p>
 */
public final class LotrProgressionStateAdapter {

    public static final float STARTING_FACTION_ALIGNMENT = 100.0F;

    private static final UUID DETACHED_PLAYER_ID =
            UUID.fromString("42685bb9-b4e8-47b9-8991-9b4ed0e3a3d1");

    private static final String TAG_ALIGNMENT_MAP = "AlignmentMap";
    private static final String TAG_FACTION_DATA = "FactionData";
    private static final String TAG_TAKEN_ALIGNMENT_REWARDS =
            "TakenAlignmentRewards";
    private static final String TAG_PLEDGE_FACTION = "PledgeFac";
    private static final String TAG_PLEDGE_KILL_COOLDOWN = "PledgeKillCD";
    private static final String TAG_PLEDGE_BREAK_COOLDOWN = "PledgeBreakCD";
    private static final String TAG_PLEDGE_BREAK_COOLDOWN_START =
            "PledgeBreakCDStart";
    private static final String TAG_BROKEN_PLEDGE_FACTION = "BrokenPledgeFac";
    private static final String TAG_PRE_35_ALIGNMENT = "Pre35Align";
    private static final String TAG_CHOSEN_35_ALIGNMENT = "Chosen35Align";
    private static final String TAG_ACHIEVEMENTS = "Achievements";
    private static final String TAG_PLAYER_TITLE = "PlayerTitle";
    private static final String TAG_PLAYER_TITLE_COLOR = "PlayerTitleColor";
    private static final String TAG_FEMININE_RANK_OVERRIDE = "FemRankOverride";

    private static final String TAG_FACTION = "Faction";
    private static final String TAG_ALIGNMENT_FLOAT = "AlignF";
    private static final String TAG_NPC_KILLS = "NPCKill";
    private static final String TAG_ENEMY_KILLS = "EnemyKill";
    private static final String TAG_TRADES = "Trades";
    private static final String TAG_HIRED = "Hired";
    private static final String TAG_MINI_QUESTS = "MiniQuests";
    private static final String TAG_CONQUEST = "Conquest";
    private static final String TAG_CONQUEST_HORN = "ConquestHorn";
    private static final String TAG_CATEGORY = "Category";
    private static final String TAG_ID = "ID";

    private static final int MAX_FACTIONS = 512;
    private static final int MAX_ACHIEVEMENTS = 4096;
    private static final int MAX_IDENTIFIER_LENGTH = 128;
    private static final float MAX_ABSOLUTE_PROGRESS = 1000000000.0F;

    private static final Set<String> CHARACTER_KEYS = setOf(
            TAG_ALIGNMENT_MAP,
            TAG_FACTION_DATA,
            TAG_TAKEN_ALIGNMENT_REWARDS,
            TAG_PLEDGE_FACTION,
            TAG_PLEDGE_KILL_COOLDOWN,
            TAG_PLEDGE_BREAK_COOLDOWN,
            TAG_PLEDGE_BREAK_COOLDOWN_START,
            TAG_BROKEN_PLEDGE_FACTION,
            TAG_PRE_35_ALIGNMENT,
            TAG_CHOSEN_35_ALIGNMENT,
            TAG_ACHIEVEMENTS,
            TAG_PLAYER_TITLE,
            TAG_PLAYER_TITLE_COLOR,
            TAG_FEMININE_RANK_OVERRIDE);

    private static final Set<String> REQUIRED_CHARACTER_KEYS = setOf(
            TAG_ALIGNMENT_MAP,
            TAG_FACTION_DATA,
            TAG_TAKEN_ALIGNMENT_REWARDS,
            TAG_PLEDGE_KILL_COOLDOWN,
            TAG_PLEDGE_BREAK_COOLDOWN,
            TAG_PLEDGE_BREAK_COOLDOWN_START,
            TAG_PRE_35_ALIGNMENT,
            TAG_CHOSEN_35_ALIGNMENT,
            TAG_ACHIEVEMENTS,
            TAG_FEMININE_RANK_OVERRIDE);

    private static final Set<String> ALIGNMENT_ENTRY_KEYS = setOf(
            TAG_FACTION, TAG_ALIGNMENT_FLOAT);
    private static final Set<String> FACTION_DATA_REQUIRED_KEYS = setOf(
            TAG_FACTION,
            TAG_NPC_KILLS,
            TAG_ENEMY_KILLS,
            TAG_TRADES,
            TAG_HIRED,
            TAG_MINI_QUESTS,
            TAG_CONQUEST_HORN);
    private static final Set<String> FACTION_DATA_KEYS = setOf(
            TAG_FACTION,
            TAG_NPC_KILLS,
            TAG_ENEMY_KILLS,
            TAG_TRADES,
            TAG_HIRED,
            TAG_MINI_QUESTS,
            TAG_CONQUEST,
            TAG_CONQUEST_HORN);
    private static final Set<String> FACTION_ONLY_ENTRY_KEYS =
            setOf(TAG_FACTION);
    private static final Set<String> ACHIEVEMENT_ENTRY_KEYS =
            setOf(TAG_CATEGORY, TAG_ID);

    public NBTTagCompound capture(EntityPlayerMP player) {
        LOTRPlayerData data = requireLiveData(player);
        try {
            return normalizeListOrder(extractCharacterState(save(data)));
        } catch (LinkageError error) {
            throw incompatible("Unable to capture LOTR progression", error);
        } catch (RuntimeException exception) {
            throw incompatible("Unable to capture LOTR progression", exception);
        }
    }

    public NBTTagCompound createDefault() {
        try {
            LOTRPlayerData detached = new LOTRPlayerData(DETACHED_PLAYER_ID);
            return normalizeListOrder(extractCharacterState(save(detached)));
        } catch (LinkageError error) {
            throw incompatible("Unable to create default LOTR progression", error);
        } catch (RuntimeException exception) {
            throw incompatible("Unable to create default LOTR progression", exception);
        }
    }

    /**
     * Creates clean progression with exactly 100 alignment for the selected
     * starting faction. This intentionally does not pledge the character or
     * unlock any alignment rewards, regions, or fast-travel waypoints.
     */
    public NBTTagCompound createDefault(String startingFactionId) {
        try {
            LOTRFaction faction = LotrCharacterAdapter.getInstance()
                    .resolveFactionForState(startingFactionId);
            if (faction == null) {
                throw new IllegalArgumentException(
                        "Starting faction cannot be resolved: " + startingFactionId);
            }
            return createDefault(faction);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (LinkageError error) {
            throw incompatible("Unable to create starting-faction progression", error);
        } catch (RuntimeException exception) {
            throw incompatible("Unable to create starting-faction progression", exception);
        }
    }

    /** Package-visible seam for testing the LOTR public API without FML bootstrap. */
    NBTTagCompound createDefault(LOTRFaction faction) {
        if (faction == null) {
            throw new IllegalArgumentException("Starting faction is missing");
        }
        LOTRPlayerData detached = new LOTRPlayerData(DETACHED_PLAYER_ID);
        NBTTagCompound full = save(detached);
        NBTTagList alignments = full.getTagList(
                TAG_ALIGNMENT_MAP, Constants.NBT.TAG_COMPOUND);
        NBTTagList seeded = new NBTTagList();
        String factionCodeName = faction.codeName();
        for (int index = 0; index < alignments.tagCount(); index++) {
            NBTTagCompound entry = alignments.getCompoundTagAt(index);
            if (!factionCodeName.equals(entry.getString(TAG_FACTION))) {
                seeded.appendTag(entry.copy());
            }
        }
        NBTTagCompound alignment = new NBTTagCompound();
        alignment.setString(TAG_FACTION, factionCodeName);
        alignment.setFloat(TAG_ALIGNMENT_FLOAT, STARTING_FACTION_ALIGNMENT);
        seeded.appendTag(alignment);
        full.setTag(TAG_ALIGNMENT_MAP, seeded);
        // LOTR's public setAlignment method requires an online player. Loading
        // the minimal map and saving it back lets LOTR canonicalize detached
        // creation state without firing live-player side effects.
        detached.load(full);
        return normalizeListOrder(extractCharacterState(save(detached)));
    }

    /** Validates both the explicit v36.15 shape and LOTR's public load/save round trip. */
    public void validate(NBTTagCompound progression) {
        validateShape(progression);
        try {
            NBTTagCompound full = save(new LOTRPlayerData(DETACHED_PLAYER_ID));
            overlayCharacterState(full, progression);
            LOTRPlayerData detached = new LOTRPlayerData(DETACHED_PLAYER_ID);
            detached.load(full);
            NBTTagCompound canonical = normalizeListOrder(
                    extractCharacterState(save(detached)));
            NBTTagCompound normalizedInput = normalizeListOrder(progression);
            if (!canonical.equals(normalizedInput)) {
                throw new IllegalArgumentException(
                        "LOTR progression contains unknown or non-canonical values");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (LinkageError error) {
            throw incompatible("LOTR progression API is incompatible", error);
        } catch (RuntimeException exception) {
            throw incompatible("LOTR progression could not be validated", exception);
        }
    }

    /** Replaces only allowlisted character fields and persists the live LOTR cache. */
    public void apply(EntityPlayerMP player, NBTTagCompound progression) {
        validate(progression);
        LOTRPlayerData data = requireLiveData(player);
        if (LotrCharacterAdapter.getInstance().isFastTravelActive(player)) {
            throw new IllegalStateException(
                    "LOTR progression cannot be applied during fast travel");
        }
        try {
            NBTTagCompound full = save(data);
            overlayCharacterState(full, progression);
            data.load(full);
            data.markDirty();
            LOTRLevelData.saveData(player.getUniqueID());
        } catch (LinkageError error) {
            throw incompatible("Unable to apply LOTR progression", error);
        } catch (RuntimeException exception) {
            throw incompatible("Unable to apply LOTR progression", exception);
        }
    }

    public void synchronize(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }
        try {
            LOTRPlayerData data = LOTRLevelData.getData(player);
            if (data == null) {
                throw new IllegalStateException("LOTR player data is unavailable");
            }
            // Loading is intentionally side-effect-free before commit. Replay
            // the current title through the public setter only in this final
            // synchronization phase so account-owned fellowship views learn
            // the newly committed character title as well.
            data.setPlayerTitle(data.getPlayerTitle());
            LOTRLevelData.sendPlayerData(player);
        } catch (LinkageError error) {
            FMLLog.warning("[%s] Unable to synchronize LOTR progression for %s: %s",
                    LostTalesMetaData.MOD_ID,
                    player.getUniqueID(),
                    error.toString());
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] Unable to synchronize LOTR progression for %s: %s",
                    LostTalesMetaData.MOD_ID,
                    player.getUniqueID(),
                    exception.toString());
        }
    }

    private static LOTRPlayerData requireLiveData(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote
                || player.getUniqueID() == null) {
            throw new IllegalStateException(
                    "LOTR progression requires a connected server player");
        }
        try {
            LOTRPlayerData data = LOTRLevelData.getData(player);
            if (data == null) {
                throw new IllegalStateException("LOTR player data is unavailable");
            }
            return data;
        } catch (LinkageError error) {
            throw incompatible("LOTR player data API is incompatible", error);
        }
    }

    private static NBTTagCompound save(LOTRPlayerData data) {
        NBTTagCompound full = new NBTTagCompound();
        data.save(full);
        return full;
    }

    private static NBTTagCompound extractCharacterState(NBTTagCompound full) {
        NBTTagCompound progression = new NBTTagCompound();
        for (String key : CHARACTER_KEYS) {
            if (full.hasKey(key)) {
                progression.setTag(key, full.getTag(key).copy());
            }
        }
        return progression;
    }

    private static void overlayCharacterState(NBTTagCompound full,
                                              NBTTagCompound progression) {
        for (String key : CHARACTER_KEYS) {
            full.removeTag(key);
        }
        for (Object keyObject : progression.func_150296_c()) {
            String key = (String) keyObject;
            full.setTag(key, progression.getTag(key).copy());
        }
    }

    private static void validateShape(NBTTagCompound progression) {
        if (progression == null) {
            throw new IllegalArgumentException("LOTR progression is missing");
        }
        validateKeys(progression, REQUIRED_CHARACTER_KEYS, CHARACTER_KEYS,
                "LOTR progression root");
        validateCompoundList(progression, TAG_ALIGNMENT_MAP, MAX_FACTIONS,
                new EntryValidator() {
                    @Override
                    public String validate(NBTTagCompound entry) {
                        validateKeys(entry, ALIGNMENT_ENTRY_KEYS,
                                ALIGNMENT_ENTRY_KEYS, "LOTR alignment entry");
                        String faction = requireIdentifier(entry, TAG_FACTION);
                        requireFiniteFloat(entry, TAG_ALIGNMENT_FLOAT, true);
                        return faction;
                    }
                });
        validateCompoundList(progression, TAG_FACTION_DATA, MAX_FACTIONS,
                new EntryValidator() {
                    @Override
                    public String validate(NBTTagCompound entry) {
                        validateKeys(entry, FACTION_DATA_REQUIRED_KEYS,
                                FACTION_DATA_KEYS, "LOTR faction-data entry");
                        String faction = requireIdentifier(entry, TAG_FACTION);
                        requireNonNegativeInteger(entry, TAG_NPC_KILLS);
                        requireNonNegativeInteger(entry, TAG_ENEMY_KILLS);
                        requireNonNegativeInteger(entry, TAG_TRADES);
                        requireNonNegativeInteger(entry, TAG_HIRED);
                        requireNonNegativeInteger(entry, TAG_MINI_QUESTS);
                        requireBoolean(entry, TAG_CONQUEST_HORN);
                        if (entry.hasKey(TAG_CONQUEST)) {
                            requireFiniteFloat(entry, TAG_CONQUEST, false);
                        }
                        return faction;
                    }
                });
        validateCompoundList(progression, TAG_TAKEN_ALIGNMENT_REWARDS,
                MAX_FACTIONS, new EntryValidator() {
                    @Override
                    public String validate(NBTTagCompound entry) {
                        validateKeys(entry, FACTION_ONLY_ENTRY_KEYS,
                                FACTION_ONLY_ENTRY_KEYS,
                                "LOTR alignment-reward entry");
                        return requireIdentifier(entry, TAG_FACTION);
                    }
                });
        validateCompoundList(progression, TAG_ACHIEVEMENTS, MAX_ACHIEVEMENTS,
                new EntryValidator() {
                    @Override
                    public String validate(NBTTagCompound entry) {
                        validateKeys(entry, ACHIEVEMENT_ENTRY_KEYS,
                                ACHIEVEMENT_ENTRY_KEYS,
                                "LOTR achievement entry");
                        String category = requireIdentifier(entry, TAG_CATEGORY);
                        requireNonNegativeInteger(entry, TAG_ID);
                        return category + ':' + entry.getInteger(TAG_ID);
                    }
                });

        requireNonNegativeInteger(progression, TAG_PLEDGE_KILL_COOLDOWN);
        requireNonNegativeInteger(progression, TAG_PLEDGE_BREAK_COOLDOWN);
        requireNonNegativeInteger(progression, TAG_PLEDGE_BREAK_COOLDOWN_START);
        requireBoolean(progression, TAG_PRE_35_ALIGNMENT);
        requireBoolean(progression, TAG_CHOSEN_35_ALIGNMENT);
        requireBoolean(progression, TAG_FEMININE_RANK_OVERRIDE);
        if (progression.hasKey(TAG_PLEDGE_FACTION)) {
            requireIdentifier(progression, TAG_PLEDGE_FACTION);
        }
        if (progression.hasKey(TAG_BROKEN_PLEDGE_FACTION)) {
            requireIdentifier(progression, TAG_BROKEN_PLEDGE_FACTION);
        }
        boolean hasTitle = progression.hasKey(TAG_PLAYER_TITLE);
        boolean hasTitleColor = progression.hasKey(TAG_PLAYER_TITLE_COLOR);
        if (hasTitle != hasTitleColor) {
            throw new IllegalArgumentException(
                    "LOTR player title and title color must be stored together");
        }
        if (hasTitle) {
            requireIdentifier(progression, TAG_PLAYER_TITLE);
            requireInteger(progression, TAG_PLAYER_TITLE_COLOR);
        }
    }

    private static void validateCompoundList(NBTTagCompound root,
                                             String key,
                                             int maximum,
                                             EntryValidator validator) {
        if (!root.hasKey(key, Constants.NBT.TAG_LIST)) {
            throw new IllegalArgumentException(key + " must be an NBT list");
        }
        NBTTagList list = (NBTTagList) root.getTag(key);
        if (list.tagCount() > maximum
                || list.tagCount() > 0
                && list.func_150303_d() != Constants.NBT.TAG_COMPOUND) {
            throw new IllegalArgumentException(key + " is oversized or malformed");
        }
        HashSet<String> unique = new HashSet<String>();
        for (int index = 0; index < list.tagCount(); index++) {
            String identity = validator.validate(list.getCompoundTagAt(index));
            if (!unique.add(identity)) {
                throw new IllegalArgumentException(
                        key + " contains duplicate entry " + identity);
            }
        }
    }

    private static void validateKeys(NBTTagCompound compound,
                                     Set<String> required,
                                     Set<String> allowed,
                                     String description) {
        Set<?> keys = compound.func_150296_c();
        for (Object keyObject : keys) {
            if (!(keyObject instanceof String)
                    || !allowed.contains((String) keyObject)) {
                throw new IllegalArgumentException(
                        description + " contains an unsupported field");
            }
        }
        for (String requiredKey : required) {
            if (!keys.contains(requiredKey)) {
                throw new IllegalArgumentException(
                        description + " is missing " + requiredKey);
            }
        }
    }

    private static String requireIdentifier(NBTTagCompound compound, String key) {
        if (!compound.hasKey(key, Constants.NBT.TAG_STRING)) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        String value = compound.getString(key);
        if (value.length() == 0 || value.length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(key + " has an invalid length");
        }
        return value;
    }

    private static void requireNonNegativeInteger(NBTTagCompound compound,
                                                  String key) {
        requireInteger(compound, key);
        if (compound.getInteger(key) < 0) {
            throw new IllegalArgumentException(key + " must not be negative");
        }
    }

    private static void requireInteger(NBTTagCompound compound, String key) {
        if (!compound.hasKey(key, Constants.NBT.TAG_INT)) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
    }

    private static void requireBoolean(NBTTagCompound compound, String key) {
        if (!compound.hasKey(key, Constants.NBT.TAG_BYTE)) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        byte value = compound.getByte(key);
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException(key + " must be a canonical boolean");
        }
    }

    private static void requireFiniteFloat(NBTTagCompound compound,
                                           String key,
                                           boolean allowNegative) {
        if (!compound.hasKey(key, Constants.NBT.TAG_FLOAT)) {
            throw new IllegalArgumentException(key + " must be a float");
        }
        float value = compound.getFloat(key);
        if (Float.isNaN(value) || Float.isInfinite(value)
                || Math.abs(value) > MAX_ABSOLUTE_PROGRESS
                || !allowNegative && value < 0.0F) {
            throw new IllegalArgumentException(key + " is outside the supported range");
        }
    }

    private static NBTTagCompound normalizeListOrder(NBTTagCompound source) {
        NBTTagCompound normalized = (NBTTagCompound) source.copy();
        sortList(normalized, TAG_ALIGNMENT_MAP, TAG_FACTION, null);
        sortList(normalized, TAG_FACTION_DATA, TAG_FACTION, null);
        sortList(normalized, TAG_TAKEN_ALIGNMENT_REWARDS, TAG_FACTION, null);
        sortList(normalized, TAG_ACHIEVEMENTS, TAG_CATEGORY, TAG_ID);
        return normalized;
    }

    private static void sortList(NBTTagCompound root,
                                 String listKey,
                                 final String textKey,
                                 final String integerKey) {
        if (!root.hasKey(listKey, Constants.NBT.TAG_LIST)) {
            return;
        }
        NBTTagList original = (NBTTagList) root.getTag(listKey);
        ArrayList<NBTTagCompound> entries = new ArrayList<NBTTagCompound>();
        for (int index = 0; index < original.tagCount(); index++) {
            entries.add((NBTTagCompound) original.getCompoundTagAt(index).copy());
        }
        Collections.sort(entries, new Comparator<NBTTagCompound>() {
            @Override
            public int compare(NBTTagCompound left, NBTTagCompound right) {
                int textComparison = left.getString(textKey).compareTo(
                        right.getString(textKey));
                if (textComparison != 0 || integerKey == null) {
                    return textComparison;
                }
                int leftValue = left.getInteger(integerKey);
                int rightValue = right.getInteger(integerKey);
                return leftValue < rightValue ? -1 : leftValue == rightValue ? 0 : 1;
            }
        });
        NBTTagList sorted = new NBTTagList();
        for (NBTTagCompound entry : entries) {
            sorted.appendTag(entry);
        }
        root.setTag(listKey, sorted);
    }

    private static Set<String> setOf(String... values) {
        return Collections.unmodifiableSet(
                new LinkedHashSet<String>(Arrays.asList(values)));
    }

    private static IllegalStateException incompatible(String message,
                                                      Throwable cause) {
        return new IllegalStateException(message, cause);
    }

    private interface EntryValidator {
        String validate(NBTTagCompound entry);
    }
}
