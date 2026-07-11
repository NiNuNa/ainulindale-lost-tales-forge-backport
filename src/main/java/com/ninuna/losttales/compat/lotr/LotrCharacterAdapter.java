package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.registry.CharacterFactionCategory;
import com.ninuna.losttales.character.registry.CharacterFactionDefinition;
import com.ninuna.losttales.character.registry.CharacterFactionResolver;
import cpw.mods.fml.common.FMLLog;
import lotr.common.fac.LOTRFaction;

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
 * This is the only character-system class that talks to LOTR faction classes.
 * It deliberately does not read or mutate LOTRPlayerData: alignment, pledges,
 * miniquests, titles, waypoints, fellowships and other LOTR progression remain
 * owned by the Minecraft account in the initial character-system release.
 */
public final class LotrCharacterAdapter implements CharacterFactionResolver {

    public static final String ID_PREFIX = "lotr:";

    private static final LotrCharacterAdapter INSTANCE = new LotrCharacterAdapter();

    private final Map<String, CharacterFactionDefinition> resolved =
            new HashMap<String, CharacterFactionDefinition>();
    private final Set<String> unresolved = new HashSet<String>();
    private List<String> playableFactionIds = Collections.emptyList();
    private boolean initialized;
    private boolean available;
    private String unavailableReason = "not_initialized";

    public static LotrCharacterAdapter getInstance() {
        return INSTANCE;
    }

    /**
     * Verifies the exact public faction API used by the character system and
     * builds an immutable starting-faction catalogue. Safe to call repeatedly.
     */
    public synchronized void initialize() {
        if (this.initialized) {
            return;
        }
        this.initialized = true;
        this.resolved.clear();
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
                String codeName = faction.codeName();
                if (codeName == null || codeName.trim().length() == 0) {
                    markUnavailable("blank_faction_code_name", null);
                    return;
                }

                String canonicalId = ID_PREFIX + codeName.toLowerCase(Locale.ROOT);
                LOTRFaction roundTrip = LOTRFaction.forName(codeName.toUpperCase(Locale.ROOT));
                if (roundTrip == null) {
                    markUnavailable("faction_lookup_failed:" + canonicalId, null);
                    return;
                }

                CharacterFactionDefinition definition = createDefinition(faction, canonicalId);
                this.resolved.put(canonicalId, definition);
                if (definition.isPlayable()) {
                    playable.add(canonicalId);
                }
            }

            if (playable.isEmpty()) {
                markUnavailable("no_playable_lotr_factions", null);
                return;
            }

            Collections.sort(playable);
            this.playableFactionIds = Collections.unmodifiableList(playable);
            this.available = true;
            this.unavailableReason = "";
            FMLLog.info("[%s] LOTR character integration ready: %d factions, %d playable starting factions",
                    LostTalesMetaData.MOD_ID, factions.length, playable.size());
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

        String canonicalId = ID_PREFIX + faction.codeName().toLowerCase(Locale.ROOT);
        CharacterFactionDefinition definition = createDefinition(faction, canonicalId);
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

    public static String normalizeFactionId(String factionId) {
        if (factionId == null) {
            return "";
        }
        String normalized = factionId.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith(ID_PREFIX) || normalized.length() == ID_PREFIX.length()) {
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
        String codeName = normalizedId.substring(ID_PREFIX.length()).toUpperCase(Locale.ROOT);
        try {
            return LOTRFaction.forName(codeName);
        } catch (LinkageError error) {
            markUnavailable("incompatible_lotr_api", error);
            return null;
        } catch (RuntimeException exception) {
            this.unresolved.add(normalizedId);
            FMLLog.warning("[%s] Failed to resolve LOTR starting faction %s: %s",
                    LostTalesMetaData.MOD_ID, normalizedId, exception.toString());
            return null;
        }
    }

    private static CharacterFactionDefinition createDefinition(LOTRFaction faction,
                                                                 String canonicalId) {
        return new CharacterFactionDefinition(
                canonicalId,
                faction.allowPlayer && faction.isPlayableAlignmentFaction(),
                mapCategories(faction)
        );
    }

    private void markUnavailable(String reason, Throwable cause) {
        this.available = false;
        this.unavailableReason = reason == null ? "unknown" : reason;
        this.playableFactionIds = Collections.emptyList();
        this.resolved.clear();
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
