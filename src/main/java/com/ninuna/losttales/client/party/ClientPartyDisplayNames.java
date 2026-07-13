package com.ninuna.losttales.client.party;

import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.server.PartyErrorId;
import com.ninuna.losttales.party.sync.PartyOperationType;
import net.minecraft.client.resources.I18n;

/** Client-only localization helper for party identifiers and operation results. */
public final class ClientPartyDisplayNames {

    private ClientPartyDisplayNames() {}

    public static String error(PartyErrorId errorId) {
        PartyErrorId safe = errorId == null
                ? PartyErrorId.INTERNAL_ERROR : errorId;
        String key = "gui.losttales.party.error." + safe.getId();
        String translated = I18n.format(key);
        return key.equals(translated)
                ? prettifyIdentifier(safe.getId()) : translated;
    }

    public static String operationSuccess(PartyOperationType operationType) {
        PartyOperationType safe = operationType == null
                ? PartyOperationType.UNKNOWN : operationType;
        String key = "gui.losttales.party.success." + safe.getId();
        String translated = I18n.format(key);
        return key.equals(translated)
                ? I18n.format("gui.losttales.party.success.generic")
                : translated;
    }

    public static String color(PartyColor color) {
        if (color == null) {
            return I18n.format("gui.losttales.party.unknown");
        }
        String key = "gui.losttales.party.color." + color.getId();
        String translated = I18n.format(key);
        return key.equals(translated)
                ? prettifyIdentifier(color.getId()) : translated;
    }

    private static String prettifyIdentifier(String id) {
        if (id == null || id.length() == 0) {
            return I18n.format("gui.losttales.party.unknown");
        }
        String value = id.replace('_', ' ').trim();
        StringBuilder result = new StringBuilder(value.length());
        boolean upper = true;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
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
