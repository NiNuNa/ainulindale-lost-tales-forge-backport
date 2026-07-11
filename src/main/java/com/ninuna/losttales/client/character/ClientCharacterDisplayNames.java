package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.registry.CharacterFactionDefinition;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
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

    public static List<String> getCompatibleFactionIds(String raceId) {
        CharacterRaceDefinition race = CharacterRaceRegistry.get(raceId);
        if (race == null || !LOTR_ADAPTER.isAvailable()) {
            return Collections.emptyList();
        }
        ArrayList<String> ids = new ArrayList<String>();
        for (String id : LOTR_ADAPTER.getPlayableFactionIds()) {
            CharacterFactionDefinition definition = LOTR_ADAPTER.resolve(id);
            if (definition != null && race.isCompatibleWith(definition)) {
                ids.add(definition.getId());
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
        return LOTR_ADAPTER.isAvailable();
    }

    public static String race(String id) {
        return translatedIdentifier("gui.losttales.character.race.", id, "losttales:");
    }

    public static String gender(String id) {
        return translatedIdentifier("gui.losttales.character.gender.", id, "losttales:");
    }

    public static String faction(String id) {
        String displayName = LOTR_ADAPTER.getFactionDisplayName(id);
        return displayName == null || displayName.length() == 0
                ? prettifyIdentifier(id) : displayName;
    }

    public static String error(CharacterErrorId errorId) {
        CharacterErrorId safe = errorId == null ? CharacterErrorId.INTERNAL_ERROR : errorId;
        String key = "gui.losttales.character.error." + safe.getId();
        String translated = I18n.format(key);
        return key.equals(translated) ? prettifyIdentifier(safe.getId()) : translated;
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
