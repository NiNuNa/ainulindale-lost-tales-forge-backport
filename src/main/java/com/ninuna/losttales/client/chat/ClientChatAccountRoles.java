package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatAccountRole;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * Which roles the client has seen a name signed with.
 *
 * <p>Every account line the server sends carries the sender's roles, and
 * that is where a name's colour comes from: a mention of an operator is
 * drawn in the operator's red, in the message, in the completion list and
 * in the input bar alike, so one name reads the same everywhere it
 * appears. The local player's own roles come from the chat access the
 * server sends on login, so they are known before that player has said
 * anything.</p>
 *
 * <p>A name this client has not yet seen speak has no roles here and is
 * drawn in the shared mention colour until it does. Nothing is inferred:
 * a role is only ever what the server stated on a line, and the store is
 * cleared with the rest of the chat's session state.</p>
 */
public final class ClientChatAccountRoles {
    /** Names remembered; a long evening on a full server fits easily. */
    private static final int MAX_REMEMBERED = 256;
    private static final LinkedHashMap<String, Integer> ROLES =
            new LinkedHashMap<String, Integer>();

    private ClientChatAccountRoles() {}

    /** Records the roles a line was signed with, under the name it wore. */
    public static synchronized void remember(String name, int mask) {
        if (name == null || name.trim().length() == 0
                || !ChatAccountRole.isValidMask(mask)) {
            return;
        }
        ROLES.put(name.trim().toLowerCase(Locale.ROOT),
                Integer.valueOf(mask));
        while (ROLES.size() > MAX_REMEMBERED) {
            Iterator<String> oldest = ROLES.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /** The roles that name has been seen with; zero for none known. */
    public static synchronized int rolesOf(String name) {
        Integer mask = name == null ? null
                : ROLES.get(name.trim().toLowerCase(Locale.ROOT));
        return mask == null ? 0 : mask.intValue();
    }

    /**
     * The colour the name is drawn in: its highest-precedence role's, or
     * -1 when no role is known for it.
     */
    public static synchronized int colorOf(String name) {
        ChatAccountRole primary = ChatAccountRole.primary(rolesOf(name));
        return primary == ChatAccountRole.NONE ? -1 : primary.getColor();
    }

    public static synchronized void clear() {
        ROLES.clear();
    }
}
