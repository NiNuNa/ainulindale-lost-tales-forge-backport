package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.registry.CharacterFactionCategory;
import com.ninuna.losttales.character.registry.CharacterFactionDefinition;
import com.ninuna.losttales.character.registry.CharacterFactionResolver;
import com.ninuna.losttales.config.LostTalesConfig;
import cpw.mods.fml.common.FMLLog;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRDimension;
import lotr.common.LOTRPlayerData;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Public-API-only LOTR Legacy adapter for roleplaying-character metadata.
 *
 * This is the isolated character-system boundary for LOTR Legacy APIs.
 * Transient fast-travel state remains here for switch safety. Character-owned
 * progression capture and application are isolated in
 * {@link LotrProgressionStateAdapter}; metadata resolution stays in this class.
 */
public final class LotrCharacterAdapter implements CharacterFactionResolver {

    public static final String ID_PREFIX = "lotr:";
    private static final int MAX_STABLE_ID_LENGTH = 64;

    private static final LotrCharacterAdapter INSTANCE = new LotrCharacterAdapter();

    private final Map<String, CharacterFactionDefinition> resolved =
            new HashMap<String, CharacterFactionDefinition>();
    private final Map<String, LOTRFaction> factionsById =
            new HashMap<String, LOTRFaction>();
    private final Set<String> unresolved = new HashSet<String>();
    private List<String> playableFactionIds = Collections.emptyList();
    private boolean initialized;
    private boolean available;
    private String unavailableReason = "not_initialized";

    public static LotrCharacterAdapter getInstance() {
        return INSTANCE;
    }

    /**
     * Builds a canonical faction catalogue directly from LOTRFaction.values().
     * No enum-name round trip is required, so factions registered by compatible
     * add-ons are not discarded merely because LOTRFaction.forName does not know
     * about the extension's code-name convention.
     */
    public synchronized void initialize() {
        if (this.initialized) {
            return;
        }
        this.initialized = true;
        this.resolved.clear();
        this.factionsById.clear();
        this.unresolved.clear();

        try {
            LOTRFaction[] factions = LOTRFaction.values();
            if (factions == null || factions.length == 0) {
                markUnavailable("no_lotr_factions", null);
                return;
            }

            ArrayList<String> playable = new ArrayList<String>();
            for (LOTRFaction faction : factions) {
                if (faction == null) {
                    continue;
                }
                String canonicalId = canonicalId(faction);
                if (canonicalId.length() == 0) {
                    FMLLog.warning("[%s] Skipping LOTR faction with blank code name: %s",
                            LostTalesMetaData.MOD_ID, String.valueOf(faction));
                    continue;
                }
                if (this.factionsById.containsKey(canonicalId)) {
                    FMLLog.warning("[%s] Skipping duplicate LOTR faction ID %s",
                            LostTalesMetaData.MOD_ID, canonicalId);
                    continue;
                }

                CharacterFactionDefinition definition = createDefinition(
                        faction, canonicalId);
                this.factionsById.put(canonicalId, faction);
                this.resolved.put(canonicalId, definition);
                if (definition.isPlayable()) {
                    playable.add(canonicalId);
                }
            }

            if (this.resolved.isEmpty()) {
                markUnavailable("no_resolvable_lotr_factions", null);
                return;
            }

            Collections.sort(playable);
            this.playableFactionIds = Collections.unmodifiableList(playable);
            this.available = true;
            this.unavailableReason = "";
            FMLLog.info("[%s] LOTR character integration ready: %d factions, %d configured playable starting factions",
                    LostTalesMetaData.MOD_ID, this.resolved.size(), playable.size());
            logExpectedElfFaction("lotr:lothlorien");
        } catch (LinkageError error) {
            markUnavailable("incompatible_lotr_api", error);
        } catch (RuntimeException exception) {
            markUnavailable("lotr_initialization_failed", exception);
        }
    }

    @Override
    public synchronized boolean isAvailable() {
        ensureInitialized();
        return this.available;
    }

    @Override
    public synchronized String getUnavailableReason() {
        ensureInitialized();
        return this.unavailableReason;
    }

    @Override
    public synchronized CharacterFactionDefinition resolve(String factionId) {
        ensureInitialized();
        if (!this.available) {
            return null;
        }

        String normalizedId = normalizeFactionId(factionId);
        if (normalizedId.length() == 0) {
            return null;
        }
        CharacterFactionDefinition cached = this.resolved.get(normalizedId);
        if (cached != null) {
            return cached;
        }
        if (this.unresolved.contains(normalizedId)) {
            return null;
        }

        LOTRFaction faction = findFaction(normalizedId);
        if (faction == null) {
            this.unresolved.add(normalizedId);
            return null;
        }

        String canonicalId = canonicalId(faction);
        CharacterFactionDefinition definition = createDefinition(faction, canonicalId);
        this.factionsById.put(canonicalId, faction);
        this.resolved.put(normalizedId, definition);
        this.resolved.put(canonicalId, definition);
        return definition;
    }

    /** Returns canonical IDs only; callers cannot mutate the cached catalogue. */
    public synchronized List<String> getPlayableFactionIds() {
        ensureInitialized();
        return this.available ? this.playableFactionIds : Collections.<String>emptyList();
    }

    @Override
    public synchronized List<String> getStartingWaypointIds(String factionId) {
        LOTRFaction faction = resolveFactionForState(factionId);
        if (faction == null) {
            return Collections.emptyList();
        }
        ArrayList<String> ids = new ArrayList<String>();
        try {
            for (LOTRWaypoint waypoint : LOTRWaypoint.values()) {
                if (waypoint == null || waypoint.faction != faction) {
                    continue;
                }
                String id = canonicalWaypointId(waypoint);
                if (id.length() > 0 && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        } catch (LinkageError error) {
            markUnavailable("incompatible_lotr_waypoint_api", error);
            return Collections.emptyList();
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] Failed to enumerate starting waypoints for %s: %s",
                    LostTalesMetaData.MOD_ID, factionId, exception.toString());
            return Collections.emptyList();
        }
        Collections.sort(ids);
        return Collections.unmodifiableList(ids);
    }

    @Override
    public synchronized String resolveStartingWaypointId(String factionId,
                                                         String waypointId) {
        return resolveStartingWaypointId(factionId, waypointId, false);
    }

    @Override
    public synchronized String resolveStartingWaypointId(String factionId,
                                                         String waypointId,
                                                         boolean allowAnyRegion) {
        LOTRFaction faction = resolveFactionForState(factionId);
        if (faction == null) {
            return null;
        }
        List<String> eligible = allowAnyRegion
                ? getAllStartingWaypointIds() : getStartingWaypointIds(factionId);
        if (eligible.isEmpty()) {
            return null;
        }
        String normalized = normalizeWaypointId(waypointId);
        if (normalized.length() == 0) {
            return eligible.get(0);
        }
        LOTRWaypoint waypoint = findWaypoint(normalized);
        return waypoint != null
                && (allowAnyRegion ? !waypoint.isHidden()
                        : waypoint.faction == faction)
                ? canonicalWaypointId(waypoint) : null;
    }

    /** All public static waypoints, for explicitly unconventional starts. */
    public synchronized List<String> getAllStartingWaypointIds() {
        ArrayList<String> ids = new ArrayList<String>();
        try {
            for (LOTRWaypoint waypoint : LOTRWaypoint.values()) {
                if (waypoint == null || waypoint.isHidden()) {
                    continue;
                }
                String id = canonicalWaypointId(waypoint);
                if (id.length() > 0 && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        } catch (LinkageError error) {
            markUnavailable("incompatible_lotr_waypoint_api", error);
            return Collections.emptyList();
        } catch (RuntimeException exception) {
            return Collections.emptyList();
        }
        Collections.sort(ids);
        return Collections.unmodifiableList(ids);
    }

    /** Localized client presentation for a server-synchronized stable ID. */
    public synchronized String getStartingWaypointDisplayName(String waypointId) {
        LOTRWaypoint waypoint = findWaypoint(normalizeWaypointId(waypointId));
        if (waypoint == null) {
            return null;
        }
        try {
            return waypoint.getDisplayName();
        } catch (LinkageError error) {
            markUnavailable("incompatible_lotr_waypoint_api", error);
            return null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public synchronized int getMiddleEarthDimensionId() {
        try {
            return LOTRDimension.MIDDLE_EARTH.dimensionID;
        } catch (LinkageError error) {
            markUnavailable("incompatible_lotr_dimension_api", error);
            throw new IllegalStateException(
                    "LOTR Middle-earth dimension is unavailable", error);
        }
    }

    /** Resolves coordinates only after the caller has loaded the target chunk. */
    public synchronized LotrStartingWaypointLocation resolveStartingWaypointLocation(
            String waypointId, World targetWorld) {
        LOTRWaypoint waypoint = findWaypoint(normalizeWaypointId(waypointId));
        int dimensionId = getMiddleEarthDimensionId();
        if (waypoint == null || targetWorld == null
                || targetWorld.provider == null
                || targetWorld.provider.dimensionId != dimensionId) {
            return null;
        }
        try {
            int x = waypoint.getXCoord();
            int z = waypoint.getZCoord();
            int y = waypoint.getYCoord(targetWorld, x, z);
            return new LotrStartingWaypointLocation(
                    dimensionId, x + 0.5D, y, z + 0.5D);
        } catch (LinkageError error) {
            markUnavailable("incompatible_lotr_waypoint_api", error);
            throw new IllegalStateException(
                    "LOTR waypoint location API is unavailable", error);
        }
    }

    /**
     * Returns LOTR's localized faction name, or null when the identifier cannot
     * be resolved. Presentation code must retain its stable-ID fallback.
     */
    public synchronized String getFactionDisplayName(String factionId) {
        ensureInitialized();
        if (!this.available) {
            return null;
        }
        String normalizedId = normalizeFactionId(factionId);
        if (normalizedId.length() == 0) {
            return null;
        }
        try {
            LOTRFaction faction = findFaction(normalizedId);
            return faction == null ? null : faction.factionName();
        } catch (LinkageError error) {
            markUnavailable("incompatible_lotr_api", error);
            return null;
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] Failed to obtain LOTR faction display name for %s: %s",
                    LostTalesMetaData.MOD_ID, normalizedId, exception.toString());
            return null;
        }
    }

    /**
     * Resolves a stable add-on faction ID to LOTR's live faction object.
     * Package-private so character-state adapters can use the public LOTR API
     * without exposing LOTR implementation types to the rest of the mod.
     */
    synchronized LOTRFaction resolveFactionForState(String factionId) {
        ensureInitialized();
        if (!this.available) {
            return null;
        }
        String normalizedId = normalizeFactionId(factionId);
        return normalizedId.length() == 0 ? null : findFaction(normalizedId);
    }

    /** Public-API-only transient fast-travel guard for character switching. */
    public boolean isFastTravelActive(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return false;
        }
        try {
            LOTRPlayerData data = LOTRLevelData.getData(player);
            return data != null
                    && (data.getTargetFTWaypoint() != null || data.getTicksUntilFT() > 0);
        } catch (LinkageError error) {
            FMLLog.warning("[%s] Unable to inspect LOTR fast-travel state: %s",
                    LostTalesMetaData.MOD_ID, error.toString());
            return true;
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] Unable to inspect LOTR fast-travel state for %s: %s",
                    LostTalesMetaData.MOD_ID, player.getUniqueID(), exception.toString());
            return true;
        }
    }

    /** Forces the public LOTR player-data cache to its per-player save file. */
    public void savePlayerData(EntityPlayerMP player) {
        if (player == null || player.getUniqueID() == null
                || player.worldObj == null || player.worldObj.isRemote) {
            throw new IllegalArgumentException(
                    "LOTR player data can only be saved for a server player");
        }
        try {
            LOTRPlayerData data = LOTRLevelData.getData(player);
            if (data == null) {
                throw new IllegalStateException("LOTR player data is unavailable");
            }
            data.markDirty();
            LOTRLevelData.saveData(player.getUniqueID());
        } catch (LinkageError error) {
            throw new IllegalStateException(
                    "Unable to save LOTR player data with the v36.15 API", error);
        }
    }

    public static String normalizeFactionId(String factionId) {
        if (factionId == null) {
            return "";
        }
        String normalized = factionId.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith(ID_PREFIX)
                || normalized.length() == ID_PREFIX.length()) {
            return "";
        }
        return normalized;
    }

    public static String normalizeWaypointId(String waypointId) {
        if (waypointId == null) {
            return "";
        }
        String normalized = waypointId.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith(ID_PREFIX)
                || normalized.length() == ID_PREFIX.length()) {
            return "";
        }
        return normalized;
    }

    private void ensureInitialized() {
        if (!this.initialized) {
            initialize();
        }
    }

    private LOTRFaction findFaction(String normalizedId) {
        LOTRFaction cached = this.factionsById.get(normalizedId);
        if (cached != null) {
            return cached;
        }

        // Re-scan values first. This supports factions appended after the first
        // catalogue build and avoids assuming enum-name == code-name.
        try {
            for (LOTRFaction faction : LOTRFaction.values()) {
                if (faction != null && normalizedId.equals(canonicalId(faction))) {
                    this.factionsById.put(normalizedId, faction);
                    return faction;
                }
            }
        } catch (LinkageError error) {
            markUnavailable("incompatible_lotr_api", error);
            return null;
        } catch (RuntimeException exception) {
            this.unresolved.add(normalizedId);
            FMLLog.warning("[%s] Failed to resolve LOTR starting faction %s: %s",
                    LostTalesMetaData.MOD_ID, normalizedId, exception.toString());
            return null;
        }
        return null;
    }

    private LOTRWaypoint findWaypoint(String normalizedId) {
        if (normalizedId == null || normalizedId.length() == 0) {
            return null;
        }
        try {
            for (LOTRWaypoint waypoint : LOTRWaypoint.values()) {
                if (waypoint != null
                        && normalizedId.equals(canonicalWaypointId(waypoint))) {
                    return waypoint;
                }
            }
        } catch (LinkageError error) {
            markUnavailable("incompatible_lotr_waypoint_api", error);
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] Failed to resolve LOTR waypoint %s: %s",
                    LostTalesMetaData.MOD_ID, normalizedId, exception.toString());
        }
        return null;
    }

    private static String canonicalWaypointId(LOTRWaypoint waypoint) {
        if (waypoint == null || waypoint.getCodeName() == null) {
            return "";
        }
        String codeName = waypoint.getCodeName().trim().toLowerCase(Locale.ROOT);
        String id = codeName.length() == 0 ? "" : ID_PREFIX + codeName;
        return id.length() > MAX_STABLE_ID_LENGTH ? "" : id;
    }

    private static String canonicalId(LOTRFaction faction) {
        if (faction == null) {
            return "";
        }
        String codeName = faction.codeName();
        if (codeName == null || codeName.trim().length() == 0) {
            return "";
        }
        return ID_PREFIX + codeName.trim().toLowerCase(Locale.ROOT);
    }

    private static CharacterFactionDefinition createDefinition(
            LOTRFaction faction, String canonicalId) {
        boolean playable = faction.isPlayableAlignmentFaction()
                && isAllowedByConfiguration(canonicalId);
        return new CharacterFactionDefinition(
                canonicalId,
                playable,
                mapCategories(faction));
    }

    private static boolean isAllowedByConfiguration(String canonicalId) {
        Set<String> denied = normalizeConfiguredIds(
                LostTalesConfig.deniedStartingFactionIds);
        if (denied.contains(canonicalId)) {
            return false;
        }
        Set<String> allowed = normalizeConfiguredIds(
                LostTalesConfig.allowedStartingFactionIds);
        return allowed.isEmpty() || allowed.contains(canonicalId);
    }

    private static Set<String> normalizeConfiguredIds(String[] ids) {
        if (ids == null || ids.length == 0) {
            return Collections.emptySet();
        }
        HashSet<String> normalized = new HashSet<String>();
        for (String id : ids) {
            String value = normalizeFactionId(id);
            if (value.length() > 0) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private void logExpectedElfFaction(String id) {
        CharacterFactionDefinition definition = this.resolved.get(id);
        if (definition == null) {
            FMLLog.warning("[%s] Expected LOTR faction %s was not present in LOTRFaction.values()",
                    LostTalesMetaData.MOD_ID, id);
        } else if (!definition.hasCategory(CharacterFactionCategory.ELF)) {
            FMLLog.warning("[%s] LOTR faction %s is present but is not categorized as ELF",
                    LostTalesMetaData.MOD_ID, id);
        } else if (!definition.isPlayable()) {
            FMLLog.info("[%s] LOTR faction %s is an ELF faction but is excluded by LOTR playability or Lost Tales configuration",
                    LostTalesMetaData.MOD_ID, id);
        }
    }

    private void markUnavailable(String reason, Throwable cause) {
        this.available = false;
        this.unavailableReason = reason == null ? "unknown" : reason;
        this.playableFactionIds = Collections.emptyList();
        this.resolved.clear();
        this.factionsById.clear();
        this.unresolved.clear();
        String causeText = cause == null ? "no additional cause" : cause.toString();
        FMLLog.severe("[%s] LOTR character integration is unavailable (%s; %s). "
                        + "Character creation is disabled, but existing rosters and active-character "
                        + "selection remain available. Expected the public LOTR Legacy Update v36.15 faction API.",
                LostTalesMetaData.MOD_ID, this.unavailableReason, causeText);
    }

    private static Set<CharacterFactionCategory> mapCategories(LOTRFaction faction) {
        EnumSet<CharacterFactionCategory> categories =
                EnumSet.noneOf(CharacterFactionCategory.class);
        if (faction.isOfType(LOTRFaction.FactionType.TYPE_FREE)) {
            categories.add(CharacterFactionCategory.FREE);
        }
        if (faction.isOfType(LOTRFaction.FactionType.TYPE_MAN)) {
            categories.add(CharacterFactionCategory.HUMAN);
        }
        if (faction.isOfType(LOTRFaction.FactionType.TYPE_ELF)) {
            categories.add(CharacterFactionCategory.ELF);
        }
        if (faction.isOfType(LOTRFaction.FactionType.TYPE_DWARF)) {
            categories.add(CharacterFactionCategory.DWARF);
        }
        if (faction.isOfType(LOTRFaction.FactionType.TYPE_ORC)) {
            categories.add(CharacterFactionCategory.ORC);
        }
        if (faction.isOfType(LOTRFaction.FactionType.TYPE_TROLL)) {
            categories.add(CharacterFactionCategory.TROLL);
        }
        if (faction.isOfType(LOTRFaction.FactionType.TYPE_TREE)) {
            categories.add(CharacterFactionCategory.TREE);
        }
        return categories;
    }
}
