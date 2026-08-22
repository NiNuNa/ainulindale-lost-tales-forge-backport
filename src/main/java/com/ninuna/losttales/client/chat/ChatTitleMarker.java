package com.ninuna.losttales.client.chat;

import java.nio.charset.Charset;
import java.util.Base64;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * Style marker on the {@code , the Gondor Farmer} component that follows
 * a titled sender's name. Carries the exact RGB the title is drawn in and
 * the bare epithet ({@code Gondor Farmer}) so the hover card can name the
 * title without parsing the displayed text. Same mechanism as
 * {@link ChatColorMarker}: it survives vanilla's wrapped-line copies and
 * is never a user-facing action.
 */
final class ChatTitleMarker {
    private static final String PREFIX = "losttales-chat-title:";
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private ChatTitleMarker() {}

    static ChatComponentText apply(ChatComponentText component, int color,
                                   String epithet) {
        if (component != null) {
            component.setChatStyle(component.getChatStyle()
                    .setChatClickEvent(new ClickEvent(
                            ClickEvent.Action.SUGGEST_COMMAND,
                            PREFIX + colorHex(color) + ':'
                                    + encodeText(epithet))));
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
        int separator = value.indexOf(':', PREFIX.length());
        if (separator < 0) {
            return null;
        }
        try {
            int color = Integer.parseInt(
                    value.substring(PREFIX.length(), separator), 16)
                    & 0xFFFFFF;
            return new Data(color, decodeText(
                    value.substring(separator + 1)));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static boolean isMarker(IChatComponent component) {
        return decode(component) != null;
    }

    /** The title colour, or null when this is not a title component. */
    static Integer colorOf(IChatComponent component) {
        Data data = decode(component);
        return data == null ? null : Integer.valueOf(data.color);
    }

    private static String encodeText(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (text == null ? "" : text).getBytes(UTF_8));
    }

    private static String decodeText(String encoded) {
        return new String(Base64.getUrlDecoder().decode(encoded), UTF_8);
    }

    private static String colorHex(int color) {
        String hex = Integer.toHexString(color & 0xFFFFFF);
        StringBuilder result = new StringBuilder(6);
        for (int index = hex.length(); index < 6; index++) {
            result.append('0');
        }
        return result.append(hex).toString();
    }

    static final class Data {
        final int color;
        /** The bare epithet, e.g. {@code Gondor Farmer}. */
        final String epithet;

        private Data(int color, String epithet) {
            this.color = color;
            this.epithet = epithet;
        }
    }
}
