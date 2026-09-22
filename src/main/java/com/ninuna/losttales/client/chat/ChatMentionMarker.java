package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * Marks an {@code @mention} inside a message: it carries the mention's
 * exact RGB and whom it reaches — an account, or a role — so the
 * renderer colours it and a click opens the right card. A player
 * mention also carries the player as the server recorded them with the
 * line, when it did: their id, the identity they were playing and its
 * skin, so the card still opens once they have gone. A mention answers
 * to the pointer the way a sender's name does. Same mechanism as
 * {@link ChatColorMarker}: the click event is the carrier, and it
 * survives vanilla's wrapped-chat component copies.
 */
final class ChatMentionMarker {
    private static final String PREFIX = "losttales-chat-mention:";
    /**
     * Marks the target as a role rather than an account. Safe as a
     * discriminator: an account name never holds a colon, so no account
     * can collide with it.
     */
    private static final String ROLE_TARGET_PREFIX = "role:";
    /**
     * Opens the recorded player after the account, and parts their
     * fields. An account name, a UUID and a skin id never hold it; the
     * identity's name, which might, comes last.
     */
    private static final char RECORD_SEPARATOR = '|';

    private ChatMentionMarker() {}

    /**
     * A player mention; {@code recorded} is the player as the line names
     * them, or null where the line recorded nobody.
     */
    static ChatComponentText apply(ChatComponentText component, int color,
                                   String account, ChatNamedPlayer recorded) {
        if (component != null && account != null && account.length() > 0) {
            component.setChatStyle(component.getChatStyle()
                    .setChatClickEvent(new ClickEvent(
                            ClickEvent.Action.SUGGEST_COMMAND,
                            PREFIX + colorHex(color) + ":" + account
                                    + recordOf(recorded))));
        }
        return component;
    }

    /** As above for a role mention; the target is the role itself. */
    static ChatComponentText applyRole(ChatComponentText component,
                                       int color, ChatAccountRole role) {
        if (component != null && role != null && !role.isNone()) {
            component.setChatStyle(component.getChatStyle()
                    .setChatClickEvent(new ClickEvent(
                            ClickEvent.Action.SUGGEST_COMMAND,
                            PREFIX + colorHex(color) + ":"
                                    + ROLE_TARGET_PREFIX + role.getId())));
        }
        return component;
    }

    static Data decode(IChatComponent component) {
        if (component == null || component.getChatStyle() == null) {
            return null;
        }
        ClickEvent event = component.getChatStyle().getChatClickEvent();
        String value = event == null ? null : event.getValue();
        if (event == null || event.getAction()
                != ClickEvent.Action.SUGGEST_COMMAND
                || value == null || !value.startsWith(PREFIX)) {
            return null;
        }
        String body = value.substring(PREFIX.length());
        int separator = body.indexOf(':');
        if (separator != 6 || body.length() <= 7) {
            return null;
        }
        try {
            int color = Integer.parseInt(body.substring(0, separator), 16)
                    & 0xFFFFFF;
            String target = body.substring(separator + 1);
            int record = target.indexOf(RECORD_SEPARATOR);
            if (record < 0) {
                return new Data(color, target, null);
            }
            String account = target.substring(0, record);
            return new Data(color, account,
                    parseRecord(account, target.substring(record + 1)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** The mention's RGB, for the renderer's colour resolution. */
    static Integer colorOf(IChatComponent component) {
        Data data = decode(component);
        return data == null ? null : Integer.valueOf(data.color);
    }

    private static String colorHex(int color) {
        String hex = Integer.toHexString(color & 0xFFFFFF);
        StringBuilder result = new StringBuilder(6);
        for (int index = hex.length(); index < 6; index++) {
            result.append('0');
        }
        return result.append(hex).toString();
    }

    /**
     * The recorded player as the marker carries them after the account:
     * their id, the character's id (empty for the account), its skin and
     * the identity's name. Empty for nobody, and for a record with no id,
     * which could draw no head.
     */
    private static String recordOf(ChatNamedPlayer recorded) {
        if (recorded == null || recorded.getPlayerId() == null) {
            return "";
        }
        return RECORD_SEPARATOR + recorded.getPlayerId().toString()
                + RECORD_SEPARATOR + (recorded.getCharacterId() == null ? ""
                        : recorded.getCharacterId().toString())
                + RECORD_SEPARATOR + recorded.getSkinId().replace(
                        RECORD_SEPARATOR, ' ')
                + RECORD_SEPARATOR + recorded.getIdentityName();
    }

    /** The recorded player read back, or null for anything malformed. */
    private static ChatNamedPlayer parseRecord(String account, String text) {
        String[] fields = text.split("\\|", 4);
        if (fields.length != 4) {
            return null;
        }
        try {
            UUID playerId = UUID.fromString(fields[0]);
            UUID characterId = fields[1].length() == 0 ? null
                    : UUID.fromString(fields[1]);
            return new ChatNamedPlayer(playerId, account, characterId,
                    fields[3], fields[2]);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /**
     * One mention: its colour and its target — {@link #account} for a
     * player, {@link #role()} for a role mention — and the player as the
     * line recorded them, when it did.
     */
    static final class Data {
        final int color;
        final String account;
        /**
         * The player as the server recorded them with the line: what the
         * card shows when this client cannot place the account any more.
         * Null for a role and for a line that recorded nobody.
         */
        final ChatNamedPlayer recorded;

        private Data(int color, String account, ChatNamedPlayer recorded) {
            this.color = color;
            this.account = account;
            this.recorded = recorded;
        }

        /** Whether the mention is of a role rather than of a player. */
        boolean isRole() {
            return this.account.startsWith(ROLE_TARGET_PREFIX);
        }

        /**
         * The targeted role, or null for an account mention. Every real
         * role answers, not only the mentionable ones: a worn-only role
         * like the Lost Tales Team mark carries the marker for its card
         * even though nothing can address it.
         */
        ChatAccountRole role() {
            if (!this.account.startsWith(ROLE_TARGET_PREFIX)) {
                return null;
            }
            ChatAccountRole role = ChatAccountRole.byId(
                    this.account.substring(ROLE_TARGET_PREFIX.length())
                            .toLowerCase(Locale.ROOT));
            return role.isNone() ? null : role;
        }
    }
}
