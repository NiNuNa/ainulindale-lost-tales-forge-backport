package com.ninuna.losttales.client.chat;

import java.nio.charset.Charset;
import java.util.Base64;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * A channel named in a line as a link: {@code #Global} in the channel's
 * colour, and on a click the tab it names comes forward. It may also
 * name one line of that tab — the command a console entry is about —
 * which the click then lands on. Same carrier as every other marker: a
 * click event that survives vanilla's wrapped-chat component copies.
 */
final class ChatChannelLinkMarker {
    private static final String PREFIX = "losttales-chat-link:";
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private ChatChannelLinkMarker() {}

    /**
     * Marks the run as a link to the tab {@code tabId} names, in
     * {@code color}, landing on {@code chatLineId} when it is not zero.
     */
    static ChatComponentText apply(ChatComponentText component, int color,
                                   String tabId, int chatLineId) {
        if (component != null && tabId != null && tabId.length() > 0) {
            component.setChatStyle(component.getChatStyle()
                    .setChatClickEvent(new ClickEvent(
                            ClickEvent.Action.SUGGEST_COMMAND,
                            PREFIX + colorHex(color) + ':' + chatLineId + ':'
                                    + Base64.getUrlEncoder().withoutPadding()
                                            .encodeToString(
                                                    tabId.getBytes(UTF_8)))));
        }
        return component;
    }

    /** What the run links to, or null when it is not a channel link. */
    static Data decode(IChatComponent component) {
        String payload = payload(component);
        if (payload == null) {
            return null;
        }
        String[] fields = payload.split(":", 3);
        if (fields.length != 3) {
            return null;
        }
        try {
            int color = Integer.parseInt(fields[0], 16) & 0xFFFFFF;
            int chatLineId = Integer.parseInt(fields[1]);
            String tabId = new String(
                    Base64.getUrlDecoder().decode(fields[2]), UTF_8);
            return tabId.length() == 0 ? null
                    : new Data(color, tabId, chatLineId);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    static boolean isMarker(IChatComponent component) {
        return payload(component) != null;
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

        private Data(int color, String tabId, int chatLineId) {
            this.color = color;
            this.tabId = tabId;
            this.chatLineId = chatLineId;
        }
    }
}
