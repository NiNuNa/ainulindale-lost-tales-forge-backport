package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.cape.CharacterCapeDefinition;
import com.ninuna.losttales.character.registry.CharacterFactionDefinition;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinDefinition;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.sync.CharacterCreationCatalog;
import com.ninuna.losttales.character.sync.CharacterOperationFeedback;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import net.minecraft.client.resources.I18n;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Client-only display and option helpers for synchronized character identifiers. */
public final class ClientCharacterDisplayNames {

    private static final LotrCharacterAdapter LOTR_ADAPTER = LotrCharacterAdapter.getInstance();

    private ClientCharacterDisplayNames() {}

    public static List<String> getRaceIds() {
        ArrayList<String> ids = new ArrayList<String>();
        for (CharacterRaceDefinition definition : CharacterRaceRegistry.getAll()) {
            ids.add(definition.getId());
        }
        return Collections.unmodifiableList(ids);
    }

    public static List<String> getGenderIds() {
        return Collections.unmodifiableList(new ArrayList<String>(CharacterGenderRegistry.getAll()));
    }

    public static List<String> getCompatibleGenderIds(String raceId) {
        CharacterRaceDefinition race = CharacterRaceRegistry.get(raceId);
        if (race == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(
                new ArrayList<String>(race.getAllowedGenderIds()));
    }

    public static List<String> getCompatibleSkinIds(String raceId, String genderId) {
        ArrayList<String> ids = new ArrayList<String>();
        for (CharacterSkinDefinition definition
                : CharacterSkinRegistry.getCompatibleSkins(raceId, genderId)) {
            ids.add(definition.getId());
        }
        return Collections.unmodifiableList(ids);
    }

    public static List<String> getCompatibleFactionIds(String raceId) {
        CharacterRaceDefinition race = CharacterRaceRegistry.get(raceId);
        if (race == null) {
            return Collections.emptyList();
        }
        CharacterCreationCatalog catalog = ClientCharacterCreationCatalogCache.get();
        ArrayList<String> ids = new ArrayList<String>();
        if (catalog != null) {
            ids.addAll(catalog.getFactionIds(raceId));
        } else if (LOTR_ADAPTER.isAvailable()) {
            // Local fallback is used only before the first server snapshot.
            for (String id : LOTR_ADAPTER.getPlayableFactionIds()) {
                CharacterFactionDefinition definition = LOTR_ADAPTER.resolve(id);
                if (definition != null && race.isCompatibleWith(definition)) {
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

    public static boolean isLotrIntegrationAvailable() {
        CharacterCreationCatalog catalog = ClientCharacterCreationCatalogCache.get();
        return catalog == null ? LOTR_ADAPTER.isAvailable() : catalog.isLotrAvailable();
    }

    public static String race(String id) {
        return translatedIdentifier("gui.losttales.character.race.", id, "losttales:");
    }

    public static String gender(String id) {
        return translatedIdentifier("gui.losttales.character.gender.", id, "losttales:");
    }

    public static String skin(String id) {
        CharacterSkinDefinition definition = CharacterSkinRegistry.get(id);
        if (definition == null) {
            return prettifyIdentifier(id);
        }
        String groupKey = "gui.losttales.character.skin_group."
                + definition.getDisplayGroupId();
        String group = I18n.format(groupKey);
        if (groupKey.equals(group)) {
            group = prettifyIdentifier(definition.getDisplayGroupId());
        }
        return I18n.format("gui.losttales.character.skin_value",
                group, Integer.valueOf(definition.getVariantIndex() + 1));
    }

    public static String faction(String id) {
        String displayName = LOTR_ADAPTER.getFactionDisplayName(id);
        return displayName == null || displayName.length() == 0
                ? prettifyIdentifier(id) : displayName;
    }

    public static String cape(int cosmeticCapeId) {
        if (cosmeticCapeId == CharacterCapeCatalog.NONE_ID) {
            return I18n.format("gui.losttales.character.cape.none");
        }
        CharacterCapeDefinition definition = CharacterCapeCatalog.get(cosmeticCapeId);
        if (definition == null) {
            return I18n.format("gui.losttales.character.unknown");
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
        return base + " " + (minutes > 0L
                ? minutes + "m " + seconds + "s"
                : seconds + "s");
    }

    public static String operationSuccess(String operationId) {
        String key = "gui.losttales.character.success." + operationId;
        String translated = I18n.format(key);
        return key.equals(translated) ? I18n.format("gui.losttales.character.success.generic") : translated;
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
