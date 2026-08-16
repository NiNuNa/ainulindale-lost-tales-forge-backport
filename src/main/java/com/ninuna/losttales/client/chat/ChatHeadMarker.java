package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatIdentityType;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.UUID;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.IChatComponent;

/** Invisible style marker carried only by the two spaces reserved for a head. */
final class ChatHeadMarker {
    private static final String PREFIX = "losttales-chat-head:";
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private ChatHeadMarker() {}

    static String encode(UUID senderId, ChatIdentityType identityType,
                         String skinId, String copyText,
                         int titleColor, int nameColor) {
        return PREFIX + senderId + ':'
                + (identityType == ChatIdentityType.ACCOUNT ? 'A' : 'C')
                + ':' + encodeText(skinId)
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
        if (value == null || !value.startsWith(PREFIX)) {
            return null;
        }
        int separator = value.indexOf(':', PREFIX.length());
        if (separator <= PREFIX.length()
                || separator + 2 >= value.length()
                || value.charAt(separator + 2) != ':') {
            return null;
        }
        try {
            UUID senderId = UUID.fromString(
                    value.substring(PREFIX.length(), separator));
            char identity = value.charAt(separator + 1);
            if (identity != 'A' && identity != 'C') {
                return null;
            }
            String[] fields = value.substring(separator + 3)
                    .split(":", -1);
            if (fields.length == 1) {
                // Compatibility with chat lines created by the earlier
                // client-only marker format during the same session.
                return new Data(senderId, identity == 'A', "",
                        decodeText(fields[0]), 0xFFFFFF, 0xFFFFFF);
            }
            if (fields.length != 4) {
                return null;
            }
            return new Data(senderId, identity == 'A',
                    decodeText(fields[0]), decodeText(fields[1]),
                    parseColor(fields[2]), parseColor(fields[3]));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static boolean isMarker(IChatComponent component) {
        return decode(component) != null;
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

    static final class Data {
        final UUID senderId;
        final boolean accountIdentity;
        final String skinId;
        final String copyText;
        final int titleColor;
        final int nameColor;

        private Data(UUID senderId, boolean accountIdentity, String skinId,
                     String copyText, int titleColor, int nameColor) {
            this.senderId = senderId;
            this.accountIdentity = accountIdentity;
            this.skinId = skinId == null ? "" : skinId;
            this.copyText = copyText == null ? "" : copyText;
            this.titleColor = titleColor & 0xFFFFFF;
            this.nameColor = nameColor & 0xFFFFFF;
        }
    }
}
