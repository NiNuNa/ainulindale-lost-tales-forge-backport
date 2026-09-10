package com.ninuna.losttales.client.chat;

import java.nio.charset.Charset;
import java.util.Base64;
import java.util.UUID;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * The quote a reply opens with: its colour, and the message it quotes.
 *
 * <p>Both ride one marker because a component carries one click event
 * and the quote needs both — the muted tone it is drawn in, and the id
 * of the message it points at, which is what a click on it resolves
 * through {@link ClientChatMessageIds} to find the original among the
 * lines on screen. Same carrier as every other marker: a click event
 * that survives vanilla's wrapped-chat component copies.</p>
 */
final class ChatReplyMarker {
    private static final String PREFIX = "losttales-chat-reply:";
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    /** What the quote's opening run carries where a head would carry a sender. */
    private static final String BUBBLE = "bubble";
    /**
     * The slot the quote opens with: the chat's speech bubble and the
     * gap after it, the gap the typing line keeps after the same bubble.
     */
    static final int ICON_SLOT_WIDTH =
            ChatIconSheet.SPEECH_BUBBLE.getWidth() + 3;

    private ChatReplyMarker() {}

    /** Marks a run of the quote, in its colour, for {@code messageId}. */
    static ChatComponentText apply(ChatComponentText component, int color,
                                   long messageId) {
        if (component != null) {
            component.setChatStyle(component.getChatStyle()
                    .setChatClickEvent(new ClickEvent(
                            ClickEvent.Action.SUGGEST_COMMAND,
                            PREFIX + colorHex(color) + ':' + messageId)));
        }
        return component;
    }

    /**
     * Marks the quote's head slot: a run of the quote like any other —
     * it answers the click the quote answers and lights with it — that
     * also says whose head stands in it, so the slot is drawn with the
     * quoted sender's face, or the mark that stands for the server or
     * the bridge, exactly as the quoted line's own slot was. The head
     * rides the quote's marker rather than a head marker of its own,
     * since a head marker names the line's sender, and this is not the
     * line's sender.
     */
    static ChatComponentText applyHead(ChatComponentText component,
                                       int color, long messageId,
                                       UUID senderId, boolean accountLine,
                                       boolean npc, String skinId) {
        if (component != null && senderId != null) {
            component.setChatStyle(component.getChatStyle()
                    .setChatClickEvent(new ClickEvent(
                            ClickEvent.Action.SUGGEST_COMMAND,
                            PREFIX + colorHex(color) + ':' + messageId
                                    + ':' + senderId + ':'
                                    + (npc ? 'N' : accountLine ? 'A' : 'C')
                                    + ':' + Base64.getUrlEncoder()
                                            .withoutPadding().encodeToString(
                                                    (skinId == null ? ""
                                                            : skinId)
                                                            .getBytes(UTF_8)))));
        }
        return component;
    }

    /**
     * Marks the quote's opening slot: the speech bubble, a run of the
     * quote like any other, so it answers the quote's click. It draws
     * nothing of its own text; its width is declared.
     */
    static ChatComponentText applyIcon(ChatComponentText component,
                                       int color, long messageId) {
        if (component != null) {
            component.setChatStyle(component.getChatStyle()
                    .setChatClickEvent(new ClickEvent(
                            ClickEvent.Action.SUGGEST_COMMAND,
                            PREFIX + colorHex(color) + ':' + messageId
                                    + ':' + BUBBLE)));
        }
        return component;
    }

    /** Whether the run is a quote's bubble slot. */
    static boolean isIconSlot(IChatComponent component) {
        String payload = payload(component);
        if (payload == null) {
            return false;
        }
        String[] fields = payload.split(":", -1);
        return fields.length == 3 && BUBBLE.equals(fields[2]);
    }

    /**
     * The head a quote run's slot holds, or null for every other run,
     * the quote's own words included. Read as a head marker's data, so
     * the slot is measured and drawn by the one rule every head is.
     */
    static ChatHeadMarker.Data headOf(IChatComponent component) {
        String payload = payload(component);
        if (payload == null) {
            return null;
        }
        String[] fields = payload.split(":", -1);
        if (fields.length != 5) {
            return null;
        }
        try {
            UUID senderId = UUID.fromString(fields[2]);
            char identity = fields[3].length() == 1 ? fields[3].charAt(0) : '?';
            if (identity != 'A' && identity != 'C' && identity != 'N') {
                return null;
            }
            String skinId = new String(
                    Base64.getUrlDecoder().decode(fields[4]), UTF_8);
            return ChatHeadMarker.Data.head(senderId, identity == 'A',
                    identity == 'N', skinId);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** The quoted message's id, or 0 when this is not a quote run. */
    static long messageIdOf(IChatComponent component) {
        String payload = payload(component);
        if (payload == null) {
            return 0L;
        }
        int separator = payload.indexOf(':');
        if (separator < 0) {
            return 0L;
        }
        int end = payload.indexOf(':', separator + 1);
        try {
            return Long.parseLong(payload.substring(separator + 1,
                    end < 0 ? payload.length() : end));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    /** The quote's colour, or null when this is not a quote run. */
    static Integer colorOf(IChatComponent component) {
        String payload = payload(component);
        if (payload == null) {
            return null;
        }
        int separator = payload.indexOf(':');
        if (separator < 0) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(
                    payload.substring(0, separator), 16) & 0xFFFFFF);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static boolean isMarker(IChatComponent component) {
        return payload(component) != null;
    }

    private static String payload(IChatComponent component) {
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
        return value.substring(PREFIX.length());
    }

    private static String colorHex(int color) {
        String hex = Integer.toHexString(color & 0xFFFFFF);
        StringBuilder result = new StringBuilder(6);
        for (int index = hex.length(); index < 6; index++) {
            result.append('0');
        }
        return result.append(hex).toString();
    }
}
