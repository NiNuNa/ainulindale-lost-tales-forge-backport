package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.lore.sync.LoreCharacterSummary;
import com.ninuna.losttales.character.cape.CharacterCapeDefinition;
import com.ninuna.losttales.character.registry.CharacterBodyModelDefinition;
import com.ninuna.losttales.character.registry.CharacterBodyModelRegistry;
import com.ninuna.losttales.character.registry.CharacterArmsRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeDefinition;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterFactionDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinDefinition;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.sync.CharacterCreationCatalog;
import com.ninuna.losttales.character.sync.CharacterOperationFeedback;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.util.LostTalesDuration;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.StatCollector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Client-only display and option helpers for synchronized character identifiers. */
public final class ClientCharacterDisplayNames {

    private static final LotrCharacterAdapter LOTR_ADAPTER = LotrCharacterAdapter.getInstance();
    private static final String LOST_TALES_NAMESPACE = "losttales:";

    private ClientCharacterDisplayNames() {}

    /**
     * The races the creation form offers: every race of the list the server
     * sent (the bundled six before one arrives), none a stand-in.
     */
    public static List<String> getRaceIds() {
        ArrayList<String> ids = new ArrayList<String>();
        for (CharacterRaceDefinition definition : CharacterRaceRegistry.getOffered()) {
            ids.add(definition.getId());
        }
        return Collections.unmodifiableList(ids);
    }

    public static List<String> getCompatibleBodyTypeIds(String raceId) {
        CharacterRaceDefinition race = CharacterRaceRegistry.get(raceId);
        if (race == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(
                new ArrayList<String>(race.getAllowedBodyTypeIds()));
    }

    public static List<String> getCompatibleSkinIds(String raceId, String bodyTypeId) {
        ArrayList<String> ids = new ArrayList<String>();
        for (CharacterSkinDefinition definition
                : CharacterSkinRegistry.getCompatibleSkins(raceId, bodyTypeId)) {
            ids.add(definition.getId());
        }
        return Collections.unmodifiableList(ids);
    }

    public static List<String> getFactionIds(String raceId,
                                             boolean unconventionalSettings) {
        CharacterRaceDefinition race = CharacterRaceRegistry.get(raceId);
        if (race == null) {
            return Collections.emptyList();
        }
        CharacterCreationCatalog catalog = ClientCharacterCreationCatalogCache.get();
        ArrayList<String> ids = new ArrayList<String>();
        if (catalog != null) {
            ids.addAll(unconventionalSettings
                    ? catalog.getAllPlayableFactionIds()
                    : catalog.getFactionIds(raceId));
        } else if (LOTR_ADAPTER.isAvailable()) {
            // Local fallback is used only before the first server snapshot.
            for (String id : LOTR_ADAPTER.getPlayableFactionIds()) {
                CharacterFactionDefinition definition = LOTR_ADAPTER.resolve(id);
                if (definition != null && (unconventionalSettings
                        || race.isCompatibleWith(definition))) {
                    ids.add(definition.getId());
                }
            }
        }
        Collections.sort(ids, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                return faction(left).compareToIgnoreCase(faction(right));
            }
        });
        return Collections.unmodifiableList(ids);
    }

    public static List<String> getStartingWaypointIds(String factionId,
                                                      boolean unconventionalSettings) {
        CharacterCreationCatalog catalog = ClientCharacterCreationCatalogCache.get();
        ArrayList<String> ids = new ArrayList<String>();
        if (catalog != null) {
            ids.addAll(unconventionalSettings
                    ? catalog.getAllWaypointIds()
                    : catalog.getWaypointIds(factionId));
        } else if (LOTR_ADAPTER.isAvailable()) {
            ids.addAll(unconventionalSettings
                    ? LOTR_ADAPTER.getAllStartingWaypointIds()
                    : LOTR_ADAPTER.getStartingWaypointIds(factionId));
        }
        Collections.sort(ids, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                return waypoint(left).compareToIgnoreCase(waypoint(right));
            }
        });
        return Collections.unmodifiableList(ids);
    }

    public static boolean isLotrIntegrationAvailable() {
        CharacterCreationCatalog catalog = ClientCharacterCreationCatalogCache.get();
        return catalog == null ? LOTR_ADAPTER.isAvailable() : catalog.isLotrAvailable();
    }

    /** A race's words: a server's as its file writes them, a bundled one's from the lang file. */
    public static String race(String id) {
        CharacterRaceDefinition race = CharacterRaceRegistry.get(id);
        if (race != null && race.getName().length() > 0) {
            return race.getName();
        }
        return translatedIdentifier("gui.losttales.character.race.", id, "losttales:");
    }

    public static String bodyType(String id) {
        return translatedIdentifier("gui.losttales.character.body_type.", id, "losttales:");
    }

    public static String skin(String id) {
        CharacterSkinDefinition definition = CharacterSkinRegistry.get(id);
        if (definition == null) {
            return prettifyIdentifier(id);
        }
        String groupKey = "gui.losttales.character.skin_group."
                + definition.getDisplayGroupId();
        String group = definition.getDisplayGroupName().length() > 0
                ? definition.getDisplayGroupName() : I18n.format(groupKey);
        if (groupKey.equals(group)) {
            group = prettifyIdentifier(definition.getDisplayGroupId());
        }
        if (definition.isAccountSkin()) {
            // One entry, not a numbered variant.
            return group;
        }
        return I18n.format("gui.losttales.character.skin_value",
                group, Integer.valueOf(definition.getVariantIndex() + 1));
    }

    public static List<String> getArmsIds() {
        return Collections.unmodifiableList(
                new ArrayList<String>(CharacterArmsRegistry.getAll()));
    }

    /** True when the skin's body offers a choice of arms. */
    public static boolean hasArmsChoice(String skinId) {
        CharacterSkinDefinition definition = CharacterSkinRegistry.get(skinId);
        CharacterBodyModelDefinition model = definition == null
                ? null : CharacterBodyModelRegistry.get(definition.getModelId());
        return model != null && model.supportsArms();
    }

    public static String arms(String id) {
        return translatedIdentifier("gui.losttales.character.arms.", id, "losttales:");
    }

    public static List<String> getChestTypeIds() {
        ArrayList<String> ids = new ArrayList<String>();
        for (CharacterChestTypeDefinition definition : CharacterChestTypeRegistry.getAll()) {
            ids.add(definition.getId());
        }
        return Collections.unmodifiableList(ids);
    }

    /** True when the skin's body can carry a chest. */
    public static boolean hasChestChoice(String skinId) {
        CharacterSkinDefinition definition = CharacterSkinRegistry.get(skinId);
        CharacterBodyModelDefinition model = definition == null
                ? null : CharacterBodyModelRegistry.get(definition.getModelId());
        return model != null && model.hasChestVariant();
    }

    public static String chestType(String id) {
        return translatedIdentifier("gui.losttales.character.chest.", id, "losttales:");
    }

    public static String faction(String id) {
        String displayName = LOTR_ADAPTER.getFactionDisplayName(id);
        return displayName == null || displayName.length() == 0
                ? prettifyIdentifier(id) : displayName;
    }

    public static String waypoint(String id) {
        String displayName = LOTR_ADAPTER.getStartingWaypointDisplayName(id);
        return displayName == null || displayName.length() == 0
                ? prettifyIdentifier(id) : displayName;
    }

    /** A cape's words: a server's as its file writes them, a bundled one's from the lang file. */
    public static String cape(String cosmeticCapeId) {
        if (CharacterCapeCatalog.NONE_ID.equals(cosmeticCapeId)) {
            return I18n.format("gui.losttales.character.cape.none");
        }
        CharacterCapeDefinition definition = CharacterCapeCatalog.get(cosmeticCapeId);
        if (definition == null) {
            return I18n.format("gui.losttales.character.unknown");
        }
        if (definition.getName().length() > 0) {
            return definition.getName();
        }
        String key = definition.getTranslationKey();
        String translated = I18n.format(key);
        return key.equals(translated)
                ? prettifyIdentifier(definition.getId())
                : translated;
    }

    public static String error(CharacterErrorId errorId) {
        CharacterErrorId safe = errorId == null ? CharacterErrorId.INTERNAL_ERROR : errorId;
        String key = "gui.losttales.character.error." + safe.getId();
        String translated = I18n.format(key);
        return key.equals(translated) ? prettifyIdentifier(safe.getId()) : translated;
    }

    public static String error(CharacterOperationFeedback feedback) {
        if (feedback == null) {
            return error(CharacterErrorId.INTERNAL_ERROR);
        }
        String base = error(feedback.getErrorId());
        long remainingMillis = feedback.getRetryAfterMillis();
        if (remainingMillis < 0L) {
            return base;
        }
        long totalSeconds = (remainingMillis + 999L) / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        LostTalesDuration wait = minutes > 0L
                ? LostTalesDuration.of(minutes, LostTalesDuration.Unit.MINUTES)
                        .and(seconds, LostTalesDuration.Unit.SECONDS)
                : LostTalesDuration.of(seconds, LostTalesDuration.Unit.SECONDS);
        return I18n.format("gui.losttales.character.error.wait", base,
                wait.write());
    }

    public static String operationSuccess(String operationId) {
        String key = "gui.losttales.character.success." + operationId;
        String translated = I18n.format(key);
        return key.equals(translated) ? I18n.format("gui.losttales.character.success.generic") : translated;
    }

    /**
     * A lore character's description in the game's language: the lang
     * line under its id where one exists, else the words its file gives it.
     */
    public static String loreDescription(LoreCharacterSummary character) {
        if (character == null) {
            return "";
        }
        String key = loreKey(character, "description");
        return StatCollector.canTranslate(key)
                ? StatCollector.translateToLocal(key) : character.getDescription();
    }

    /**
     * A lore character's name as the lore window's card shows it: a titled
     * one (Arthur, Mayor of Bree) in the game's language, its title worded
     * by the lang line under its id; any other as its file gives it.
     * Everywhere else the name is the character's identity and reads alike
     * for everyone.
     */
    public static String loreCardName(LoreCharacterSummary character) {
        if (character == null) {
            return "";
        }
        String key = loreKey(character, "name");
        return StatCollector.canTranslate(key)
                ? StatCollector.translateToLocal(key) : character.getName();
    }

    private static String loreKey(LoreCharacterSummary character, String field) {
        String id = character.getId();
        return "gui.losttales.character.lore."
                + (id.startsWith(LOST_TALES_NAMESPACE)
                        ? id.substring(LOST_TALES_NAMESPACE.length()) : id)
                + "." + field;
    }

    private static String translatedIdentifier(String prefix, String id, String namespace) {
        String path = id == null ? "" : id;
        if (path.startsWith(namespace)) {
            path = path.substring(namespace.length());
        }
        String key = prefix + path;
        String translated = I18n.format(key);
        return key.equals(translated) ? prettifyIdentifier(id) : translated;
    }

    private static String prettifyIdentifier(String id) {
        if (id == null || id.length() == 0) {
            return I18n.format("gui.losttales.character.unknown");
        }
        int colon = id.indexOf(':');
        String value = colon >= 0 ? id.substring(colon + 1) : id;
        value = value.replace('_', ' ').trim();
        StringBuilder result = new StringBuilder(value.length());
        boolean upper = true;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (upper && Character.isLetter(character)) {
                result.append(Character.toUpperCase(character));
                upper = false;
            } else {
                result.append(character);
            }
            if (character == ' ') {
                upper = true;
            }
        }
        return result.toString();
    }
}
