package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.chat.ChatNarrator;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.UUID;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.IChatComponent;

/**
 * Invisible style marker carried only by the two spaces reserved for a
 * head. A line keeps its head in the row that names its speaker; laid
 * out for an open window the same marker becomes the row's avatar
 * ({@link #asAvatar}), which takes no room in the row and is drawn in
 * the window's timestamp area instead ({@link ChatAvatar}).
 */
final class ChatHeadMarker {
    /**
     * The mark a Narrator line wears for a head. The pointing hand stands
     * in until a scroll is drawn into the emoji sheet.
     */
    static final ChatEmoji NARRATOR_MARK = ChatEmoji.INDEX_POINTING_AT_THE_VIEWER;

    private static final String PREFIX = "losttales-chat-head:";
    /** The same head standing as its row's avatar. */
    private static final String AVATAR_PREFIX = "losttales-chat-avatar:";
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private ChatHeadMarker() {}

    /**
     * {@code accountLine}: whether the head is the account's rather than
     * a character's; {@code characterId}: the character a character line
     * was said as, which is whose presence the head wears, or null.
     */
    static String encode(UUID senderId, boolean accountLine,
                         UUID characterId, String skinId, String copyText,
                         int titleColor, int nameColor) {
        return PREFIX + senderId + ':'
                + (accountLine ? 'A' : 'C')
                + ':' + (accountLine || characterId == null ? ""
                        : characterId.toString())
                + ':' + encodeText(skinId)
                + ':' + encodeText(copyText)
                + ':' + colorHex(titleColor)
                + ':' + colorHex(nameColor);
    }

    /** NPC variant: the skin field carries the entity's texture path. */
    static String encodeNpc(UUID npcId, String texturePath,
                            String copyText, int titleColor,
                            int nameColor) {
        return PREFIX + npcId + ":N::" + encodeText(texturePath)
                + ':' + encodeText(copyText)
                + ':' + colorHex(titleColor)
                + ':' + colorHex(nameColor);
    }

    static Data decode(IChatComponent component) {
        if (component == null || component.getChatStyle() == null) {
            return null;
        }
        ClickEvent event = component.getChatStyle().getChatClickEvent();
        if (event == null || event.getAction()
                != ClickEvent.Action.SUGGEST_COMMAND) {
            return null;
        }
        String value = event.getValue();
        boolean avatar = value != null && value.startsWith(AVATAR_PREFIX);
        if (value == null || (!avatar && !value.startsWith(PREFIX))) {
            return null;
        }
        int start = avatar ? AVATAR_PREFIX.length() : PREFIX.length();
        int separator = value.indexOf(':', start);
        if (separator <= start
                || separator + 2 >= value.length()
                || value.charAt(separator + 2) != ':') {
            return null;
        }
        try {
            UUID senderId = UUID.fromString(
                    value.substring(start, separator));
            char identity = value.charAt(separator + 1);
            if (identity != 'A' && identity != 'C' && identity != 'N') {
                return null;
            }
            String[] fields = value.substring(separator + 3)
                    .split(":", -1);
            if (fields.length != 5) {
                return null;
            }
            UUID characterId = identity != 'C' || fields[0].length() == 0
                    ? null : UUID.fromString(fields[0]);
            return new Data(senderId, identity == 'A', identity == 'N',
                    characterId, decodeText(fields[1]),
                    decodeText(fields[2]), parseColor(fields[3]),
                    parseColor(fields[4]), false, avatar);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static boolean isMarker(IChatComponent component) {
        return decode(component) != null;
    }

    /**
     * The same head standing as its row's avatar: the run it rides on,
     * with its style and its words, carrying the head's data under the
     * avatar's prefix. A run that is not a line's own head is handed back
     * as it is.
     */
    static IChatComponent asAvatar(IChatComponent head) {
        ClickEvent event = head == null || head.getChatStyle() == null
                ? null : head.getChatStyle().getChatClickEvent();
        String value = event == null ? null : event.getValue();
        if (value == null || !value.startsWith(PREFIX)) {
            return head;
        }
        net.minecraft.util.ChatComponentText avatar =
                new net.minecraft.util.ChatComponentText(
                        head.getUnformattedTextForChat());
        avatar.setChatStyle(head.getChatStyle().createShallowCopy()
                .setChatClickEvent(new ClickEvent(
                        ClickEvent.Action.SUGGEST_COMMAND, AVATAR_PREFIX
                                + value.substring(PREFIX.length()))));
        return avatar;
    }

    /**
     * The head a whole line was signed with — the first head marker among
     * its runs, which is the line's own, since a reply's quote carries
     * its head on the quote's marker — or null for a line with none.
     */
    static Data of(IChatComponent line) {
        if (line == null) {
            return null;
        }
        for (Object value : line) {
            if (value instanceof IChatComponent) {
                Data head = decode((IChatComponent)value);
                if (head != null) {
                    return head;
                }
            }
        }
        return null;
    }

    private static String encodeText(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (value == null ? "" : value).getBytes(UTF_8));
    }

    private static String decodeText(String value) {
        return new String(Base64.getUrlDecoder().decode(value), UTF_8);
    }

    private static String colorHex(int color) {
        String hex = Integer.toHexString(color & 0xFFFFFF);
        StringBuilder padded = new StringBuilder(6);
        for (int index = hex.length(); index < 6; index++) {
            padded.append('0');
        }
        return padded.append(hex).toString();
    }

    private static int parseColor(String value) {
        if (value == null || value.length() != 6) {
            throw new IllegalArgumentException("invalid chat marker color");
        }
        return Integer.parseInt(value, 16) & 0xFFFFFF;
    }

    /**
     * The colours alone, for a wrapped line that carries no head of its
     * own: enough for the renderer to colour the sender's name and title
     * exactly as the line it was wrapped from.
     */
    static Data colorsOnly(int nameColor, int titleColor) {
        return new Data(null, false, false, null, "", "", titleColor,
                nameColor, false, false);
    }

    /**
     * Whether the run is a head marker's own slot or a quote's head
     * slot: either way a face, or the mark standing for one, is drawn
     * in the width the slot declares.
     */
    static Data headOf(IChatComponent component) {
        Data head = decode(component);
        return head != null ? head : ChatReplyMarker.headOf(component);
    }

    static final class Data {
        final UUID senderId;
        final boolean accountIdentity;
        /** NPC lines carry a texture path in {@link #skinId} instead. */
        final boolean npcIdentity;
        /**
         * The character a character line was said as; null for an
         * account line, an NPC's, and a head that does not say.
         */
        final UUID characterId;
        /** Whether this is a reply's quote wearing the quoted sender's head. */
        final boolean quoted;
        /**
         * Whether the head stands as its row's avatar, drawn in an open
         * window's timestamp area rather than in the row.
         */
        final boolean avatar;
        final String skinId;
        final String copyText;
        final int titleColor;
        final int nameColor;

        /**
         * Whether the line's sender is the Discord bridge, not an
         * account: its head slot holds the Discord mark — the 10px
         * emoji drawn 1:1 — and so is declared two pixels wider than a
         * player head's.
         */
        boolean isDiscordSender() {
            return this.accountIdentity
                    && LostTalesChatMessagePacket.isDiscordSender(
                            this.senderId);
        }

        /**
         * Whether the line's sender is the server or the client itself:
         * its head slot holds the console mark, drawn as the Discord
         * mark is.
         */
        boolean isSystemSender() {
            return this.accountIdentity
                    && LostTalesChatMessagePacket.isSystemSender(
                            this.senderId);
        }

        /** Whether the line is the Narrator's: its head slot holds the Narrator's mark. */
        boolean isNarrator() {
            return !this.accountIdentity && ChatNarrator.isNarratorSkin(this.skinId);
        }

        /**
         * The emoji standing where the head would, or null for a
         * sender with a head of their own: the Narrator's mark, the
         * Discord mark for the bridge, the console mark for the server
         * and the client.
         */
        ChatEmoji mark() {
            if (isNarrator()) {
                return NARRATOR_MARK;
            }
            if (isDiscordSender()) {
                return ChatEmoji.DISCORD;
            }
            return isSystemSender() ? ChatEmoji.CONSOLE : null;
        }

        /**
         * A head alone — whose it is and what it is drawn with — for a
         * slot that names no line's sender: a reply's quote wears the
         * quoted sender's head this way.
         */
        static Data head(UUID senderId, boolean accountIdentity,
                         boolean npcIdentity, String skinId) {
            return new Data(senderId, accountIdentity, npcIdentity, null,
                    skinId, "", 0, 0, true, false);
        }

        /**
         * A member's head in a member list: whose it is, the character it
         * shows, and what it is drawn with, wearing that identity's
         * status as a message's head does.
         */
        static Data member(UUID senderId, boolean accountIdentity,
                           UUID characterId, String skinId) {
            return new Data(senderId, accountIdentity, false,
                    accountIdentity ? null : characterId, skinId, "", 0, 0,
                    false, false);
        }

        /**
         * An NPC's head in a member list: its portrait, which wears no
         * sphere, an NPC having no account and no presence.
         */
        static Data npc(UUID npcId, String portrait) {
            return new Data(npcId, false, true, null, portrait, "", 0, 0,
                    false, false);
        }

        private Data(UUID senderId, boolean accountIdentity,
                     boolean npcIdentity, UUID characterId, String skinId,
                     String copyText, int titleColor, int nameColor,
                     boolean quoted, boolean avatar) {
            this.senderId = senderId;
            this.accountIdentity = accountIdentity;
            this.npcIdentity = npcIdentity;
            this.characterId = characterId;
            this.quoted = quoted;
            this.avatar = avatar;
            this.skinId = skinId == null ? "" : skinId;
            this.copyText = copyText == null ? "" : copyText;
            this.titleColor = titleColor & 0xFFFFFF;
            this.nameColor = nameColor & 0xFFFFFF;
        }
    }
}
