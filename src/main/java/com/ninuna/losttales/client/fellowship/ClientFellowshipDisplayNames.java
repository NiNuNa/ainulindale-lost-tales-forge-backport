package com.ninuna.losttales.client.fellowship;

import com.ninuna.losttales.client.chat.ChatAccountAside;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.server.FellowshipErrorId;
import com.ninuna.losttales.fellowship.sync.FellowshipOperationType;
import net.minecraft.client.resources.I18n;

/**
 * The words this game shows for a fellowship request's refusal or answer,
 * a member's colour, and a character's name with its account.
 */
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

    /**
     * A character's name as the fellowship page and the Inbox show it: the
     * account behind it after it, {@code Aldric (Steve)}, while this
     * player's Show Accounts is on (CHAR-26, FEL-31), else the name alone.
     */
    public static String named(String characterName, String account) {
        return named(characterName, account, LostTalesConfig.chatShowAccounts);
    }

    /** The same, with the setting given. */
    public static String named(String characterName, String account, boolean showAccounts) {
        String name = characterName == null ? "" : characterName;
        return showAccounts ? name + ChatAccountAside.suffix(name, account, true) : name;
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
