package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * Marks an {@code @mention} inside a message: it carries the mention's
 * exact RGB and whom it reaches — a player, or a role — so the renderer
 * colours it and a click opens the right card. A player mention also
 * carries the player as the server recorded them with the line, when it
 * did: their id, the identity they were playing and its skin, so the
 * card still opens once they have gone. A mention answers to the
 * pointer the way a sender's name does. Same mechanism as
 * {@link ChatColorMarker}: the click event is the carrier, and it
 * survives vanilla's wrapped-chat component copies. Every name rides in
 * base64, as a channel link's tab does, so a name holding any character
 * at all — a Discord nickname may hold anything — reads back whole.
 */
final class ChatMentionMarker {
    private static final String PREFIX = "losttales-chat-mention:";
    private static final String PLAYER = "p";
    private static final String ROLE = "r";
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private ChatMentionMarker() {}

    /**
     * A player mention; {@code recorded} is the player as the line names
     * them, or null where nobody could place them.
     */
    static ChatComponentText apply(ChatComponentText component, int color,
                                   String account, ChatNamedPlayer recorded) {
        if (component != null && account != null && account.length() > 0) {
            component.setChatStyle(component.getChatStyle()
                    .setChatClickEvent(new ClickEvent(
                            ClickEvent.Action.SUGGEST_COMMAND,
                            PREFIX + colorHex(color) + ":" + PLAYER + ":"
                                    + encode(account) + recordOf(recorded))));
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
                            PREFIX + colorHex(color) + ":" + ROLE + ":"
                                    + encode(role.getId()))));
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
        String[] fields = value.substring(PREFIX.length()).split(":", -1);
        if (fields.length < 3 || fields[0].length() != 6) {
            return null;
        }
        try {
            int color = Integer.parseInt(fields[0], 16) & 0xFFFFFF;
            String target = decodeText(fields[2]);
            if (target.length() == 0) {
                return null;
            }
            if (ROLE.equals(fields[1]) && fields.length == 3) {
                return new Data(color, target, null, true);
            }
            if (!PLAYER.equals(fields[1])) {
                return null;
            }
            if (fields.length == 3) {
                return new Data(color, target, null, false);
            }
            if (fields.length != 7) {
                return null;
            }
            UUID playerId = UUID.fromString(fields[3]);
            UUID characterId = fields[4].length() == 0 ? null
                    : UUID.fromString(fields[4]);
            return new Data(color, target, new ChatNamedPlayer(playerId,
                    target, characterId, decodeText(fields[6]),
                    decodeText(fields[5])), false);
        } catch (IllegalArgumentException malformed) {
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
        return ":" + recorded.getPlayerId() + ":"
                + (recorded.getCharacterId() == null ? ""
                        : recorded.getCharacterId().toString())
                + ":" + encode(recorded.getSkinId())
                + ":" + encode(recorded.getIdentityName());
    }

    private static String encode(String text) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(text.getBytes(UTF_8));
    }

    private static String decodeText(String field) {
        return new String(Base64.getUrlDecoder().decode(field), UTF_8);
    }

    /**
     * One mention: its colour and its target — {@link #account} for a
     * player, {@link #role()} for a role mention — and the player as the
     * line recorded them, when it did.
     */
    static final class Data {
        final int color;
        /** The player's account, or the role's id for a role mention. */
        final String account;
        /**
         * The player as the server recorded them with the line: what the
         * card shows when this client cannot place the account any more.
         * Null for a role and for a line that recorded nobody.
         */
        final ChatNamedPlayer recorded;
        private final boolean role;

        private Data(int color, String account, ChatNamedPlayer recorded,
                     boolean role) {
            this.color = color;
            this.account = account;
            this.recorded = recorded;
            this.role = role;
        }

        /** Whether the mention is of a role rather than of a player. */
        boolean isRole() {
            return this.role;
        }

        /**
         * The targeted role, or null for a player mention. Every real
         * role answers, not only the mentionable ones: a worn-only role
         * like the Lost Tales Team mark carries the marker for its card
         * even though nothing can address it.
         */
        ChatAccountRole role() {
            if (!this.role) {
                return null;
            }
            ChatAccountRole found = ChatAccountRole.byId(
                    this.account.toLowerCase(Locale.ROOT));
            return found.isNone() ? null : found;
        }
    }
}
