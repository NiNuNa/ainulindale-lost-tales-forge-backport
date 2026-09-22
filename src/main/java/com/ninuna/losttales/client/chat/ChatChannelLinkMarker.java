package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatMessageIds;
import java.nio.charset.Charset;
import java.util.Base64;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * A channel named in a line as a link: {@code #Global} in the channel's
 * colour, and on a click the tab it names comes forward. It may also
 * name one line of that tab, which the click then lands on: by this
 * client's own line id for the command a console entry is about, or by
 * the server's message id for a link to a message — {@code #Global/1234}
 * as typed, drawn as {@code #Global >} and a speech bubble, the way a
 * messenger draws a link to a message. Same carrier as every other
 * marker: a click event that survives vanilla's wrapped-chat component
 * copies. The runs of one link share one payload, so they light and
 * answer together.
 */
final class ChatChannelLinkMarker {
    private static final String PREFIX = "losttales-chat-link:";
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    /**
     * The two bold spaces the bubble of a message link is drawn into:
     * exactly the width of an inline emoji's slot.
     */
    static final String ICON_SLOT = "  ";
    /** What stands between the channel's name and the bubble. */
    static final String MESSAGE_SEPARATOR = " > ";

    private ChatChannelLinkMarker() {}

    /**
     * Marks the run as a link to the tab {@code tabId} names, in
     * {@code color}, landing on {@code chatLineId} when it is not zero.
     */
    static ChatComponentText apply(ChatComponentText component, int color,
                                   String tabId, int chatLineId) {
        return apply(component, color, tabId, chatLineId,
                ChatMessageIds.NONE);
    }

    /**
     * Marks the run as a link to the tab {@code tabId} names, landing on
     * the message the server calls {@code messageId} — the form that
     * means the same on every client.
     */
    static ChatComponentText applyMessage(ChatComponentText component,
                                          int color, String tabId,
                                          long messageId) {
        return apply(component, color, tabId, 0, messageId);
    }

    private static ChatComponentText apply(ChatComponentText component,
                                           int color, String tabId,
                                           int chatLineId, long messageId) {
        if (component != null && tabId != null && tabId.length() > 0) {
            String payload = PREFIX + colorHex(color) + ':' + chatLineId + ':'
                    + Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(tabId.getBytes(UTF_8));
            if (ChatMessageIds.isServerId(messageId)) {
                payload += ":" + messageId;
            }
            component.setChatStyle(component.getChatStyle()
                    .setChatClickEvent(new ClickEvent(
                            ClickEvent.Action.SUGGEST_COMMAND, payload)));
        }
        return component;
    }

    /** What the run links to, or null when it is not a channel link. */
    static Data decode(IChatComponent component) {
        String payload = payload(component);
        if (payload == null) {
            return null;
        }
        String[] fields = payload.split(":", 4);
        if (fields.length != 3 && fields.length != 4) {
            return null;
        }
        try {
            int color = Integer.parseInt(fields[0], 16) & 0xFFFFFF;
            int chatLineId = Integer.parseInt(fields[1]);
            String tabId = new String(
                    Base64.getUrlDecoder().decode(fields[2]), UTF_8);
            long messageId = fields.length == 4
                    ? Long.parseLong(fields[3]) : ChatMessageIds.NONE;
            if (fields.length == 4 && !ChatMessageIds.isServerId(messageId)) {
                return null;
            }
            return tabId.length() == 0 ? null
                    : new Data(color, tabId, chatLineId, messageId);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    static boolean isMarker(IChatComponent component) {
        return payload(component) != null;
    }

    /**
     * Whether the run is a piece of a link to a message, read off its
     * payload without decoding it: only such a payload has a fourth
     * field, and the tab's name, in base64, holds no colon.
     */
    static boolean linksMessage(IChatComponent component) {
        String payload = payload(component);
        if (payload == null) {
            return false;
        }
        int colons = 0;
        for (int index = 0; index < payload.length(); index++) {
            if (payload.charAt(index) == ':') {
                colons++;
            }
        }
        return colons == 3;
    }

    /** Whether two runs are pieces of one link: the same payload. */
    static boolean sameLink(IChatComponent one, IChatComponent other) {
        String left = payload(one);
        return left != null && left.equals(payload(other));
    }

    /**
     * Whether the run is the slot a message link's bubble is drawn into:
     * a link to a message whose text is the two reserved spaces, and
     * nothing that is a word.
     */
    static boolean isIconSlot(IChatComponent component) {
        Data data = decode(component);
        if (data == null || !data.linksMessage()) {
            return false;
        }
        String text = LostTalesChatVisualStyle.removeColorCodes(
                component.getUnformattedTextForChat());
        return ICON_SLOT.equals(text);
    }

    /** The link's colour, or null when the run is not a channel link. */
    static Integer colorOf(IChatComponent component) {
        Data data = decode(component);
        return data == null ? null : Integer.valueOf(data.color);
    }

    private static String payload(IChatComponent component) {
        if (component == null || component.getChatStyle() == null) {
            return null;
        }
        ClickEvent event = component.getChatStyle().getChatClickEvent();
        String value = event == null ? null : event.getValue();
        if (event == null
                || event.getAction() != ClickEvent.Action.SUGGEST_COMMAND
                || value == null || !value.startsWith(PREFIX)) {
            return null;
        }
        return value.substring(PREFIX.length());
    }

    private static String colorHex(int color) {
        String hex = Integer.toHexString(color & 0xFFFFFF);
        StringBuilder padded = new StringBuilder(6);
        for (int index = hex.length(); index < 6; index++) {
            padded.append('0');
        }
        return padded.append(hex).toString();
    }

    static final class Data {
        final int color;
        /** The tab the link opens, as {@link ChatTab#id()} writes it. */
        final String tabId;
        /** The line the click lands on, or zero for the tab alone. */
        final int chatLineId;
        /**
         * The message the click lands on by the server's id, or
         * {@link ChatMessageIds#NONE}: the form another client can follow.
         */
        final long messageId;

        private Data(int color, String tabId, int chatLineId,
                     long messageId) {
            this.color = color;
            this.tabId = tabId;
            this.chatLineId = chatLineId;
            this.messageId = messageId;
        }

        /** Whether the link names a message rather than the tab alone. */
        boolean linksMessage() {
            return this.chatLineId != 0 || this.messageId != ChatMessageIds.NONE;
        }
    }
}
