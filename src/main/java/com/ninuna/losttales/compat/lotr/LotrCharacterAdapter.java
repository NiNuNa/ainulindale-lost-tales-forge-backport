package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.registry.CharacterFactionCategory;
import com.ninuna.losttales.character.registry.CharacterFactionDefinition;
import com.ninuna.losttales.character.registry.CharacterFactionResolver;
import com.ninuna.losttales.config.LostTalesConfig;
import cpw.mods.fml.common.FMLLog;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;

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
