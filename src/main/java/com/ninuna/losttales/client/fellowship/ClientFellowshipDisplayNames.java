package com.ninuna.losttales.client.fellowship;

import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.server.FellowshipErrorId;
import com.ninuna.losttales.fellowship.sync.FellowshipOperationType;
import net.minecraft.client.resources.I18n;

/** Client-only localization helper for fellowship identifiers and operation results. */
public final class ClientFellowshipDisplayNames {

    private ClientFellowshipDisplayNames() {}

    public static String error(FellowshipErrorId errorId) {
        FellowshipErrorId safe = errorId == null
                ? FellowshipErrorId.INTERNAL_ERROR : errorId;
        String key = "gui.losttales.fellowship.error." + safe.getId();
        String translated = I18n.format(key);
        return key.equals(translated)
                ? prettifyIdentifier(safe.getId()) : translated;
    }

    public static String operationSuccess(FellowshipOperationType operationType) {
        FellowshipOperationType safe = operationType == null
                ? FellowshipOperationType.UNKNOWN : operationType;
        String key = "gui.losttales.fellowship.success." + safe.getId();
        String translated = I18n.format(key);
        return key.equals(translated)
                ? I18n.format("gui.losttales.fellowship.success.generic")
                : translated;
    }

    public static String color(FellowshipColor color) {
        if (color == null) {
            return I18n.format("gui.losttales.fellowship.unknown");
        }
        String key = "gui.losttales.fellowship.color." + color.getId();
        String translated = I18n.format(key);
        return key.equals(translated)
                ? prettifyIdentifier(color.getId()) : translated;
    }

    private static String prettifyIdentifier(String id) {
        if (id == null || id.length() == 0) {
            return I18n.format("gui.losttales.fellowship.unknown");
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
