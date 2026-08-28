package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatAccountRole;
import java.util.Locale;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * Marks an {@code @mention} inside a message: it carries the mention's
 * exact RGB and whom it reaches — an account, or a role — so the
 * renderer colours it and a hover shows the right card; an account
 * mention also opens the conversation on a click. A mention answers to
 * the pointer the way a sender's name does. Same mechanism as
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

    private ChatMentionMarker() {}

    static ChatComponentText apply(ChatComponentText component, int color,
                                   String account) {
        if (component != null && account != null && account.length() > 0) {
            component.setChatStyle(component.getChatStyle()
                    .setChatClickEvent(new ClickEvent(
                            ClickEvent.Action.SUGGEST_COMMAND,
                            PREFIX + colorHex(color) + ":" + account)));
        }
        return component;
    }

    /** As above for a role mention; the target is the role itself. */
    static ChatComponentText applyRole(ChatComponentText component,
                                       int color, ChatAccountRole role) {
        if (component != null && role != null
                && role != ChatAccountRole.NONE) {
            component.setChatStyle(component.getChatStyle()
                    .setChatClickEvent(new ClickEvent(
                            ClickEvent.Action.SUGGEST_COMMAND,
                            PREFIX + colorHex(color) + ":"
                                    + ROLE_TARGET_PREFIX
                                    + role.name().toLowerCase(Locale.ROOT))));
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
            return new Data(Integer.parseInt(
                    body.substring(0, separator), 16) & 0xFFFFFF,
                    body.substring(separator + 1));
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
     * One mention: its colour and its target — {@link #account} for a
     * player, {@link #role()} for a role mention.
     */
    static final class Data {
        final int color;
        final String account;

        private Data(int color, String account) {
            this.color = color;
            this.account = account;
        }

        /**
         * The targeted role, or null for an account mention. Every real
         * role answers, not only the mentionable ones: a worn-only tag
         * like {@code [Developer]} carries the marker for its card even
         * though nothing can address it.
         */
        ChatAccountRole role() {
            if (!this.account.startsWith(ROLE_TARGET_PREFIX)) {
                return null;
            }
            String name = this.account.substring(
                    ROLE_TARGET_PREFIX.length())
                    .toUpperCase(Locale.ROOT);
            for (ChatAccountRole role : ChatAccountRole.values()) {
                if (role != ChatAccountRole.NONE
                        && role.name().equals(name)) {
                    return role;
                }
            }
            return null;
        }
    }
}
