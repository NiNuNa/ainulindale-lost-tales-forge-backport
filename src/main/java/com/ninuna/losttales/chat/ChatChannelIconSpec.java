package com.ninuna.losttales.chat;

import java.util.Locale;

/**
 * What a channel wears before its name when a server chooses it, and a
 * role over its members: one of the chat's own emoji, or an item's icon.
 * Written in the channels and roles files as {@code emoji:<name>} — or
 * the bare name — or {@code item:<id>}, with {@code @<damage>} after an
 * item that needs one, and carried to clients as that same text. Each side parses the text for itself, and text that
 * names nothing is refused rather than guessed at; whether the emoji or
 * the item exists is the drawing side's question, since a client may
 * lack the mod an item comes from.
 */
public final class ChatChannelIconSpec {
    public enum Kind { EMOJI, ITEM }

    /** The longest text an entry may be, and the wire's bound on it. */
    public static final int MAX_TEXT_LENGTH = 96;
    /** An item's damage value is a short in a stack and on the wire. */
    public static final int MAX_META = Short.MAX_VALUE;

    private static final String ITEM_PREFIX = "item:";
    private static final String EMOJI_PREFIX = "emoji:";

    private final Kind kind;
    /** The emoji's canonical name, or the item's registry id. */
    private final String name;
    private final int meta;

    private ChatChannelIconSpec(Kind kind, String name, int meta) {
        this.kind = kind;
        this.name = name;
        this.meta = meta;
    }

    /** The text as an icon, or null for text that reads as none. */
    public static ChatChannelIconSpec parse(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() == 0 || trimmed.length() > MAX_TEXT_LENGTH) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith(ITEM_PREFIX)) {
            String id = trimmed.substring(ITEM_PREFIX.length()).trim();
            int meta = 0;
            int at = id.lastIndexOf('@');
            if (at >= 0) {
                String damage = id.substring(at + 1).trim();
                id = id.substring(0, at).trim();
                try {
                    meta = Integer.parseInt(damage);
                } catch (NumberFormatException notANumber) {
                    return null;
                }
                if (meta < 0 || meta > MAX_META) {
                    return null;
                }
            }
            return isItemId(id) ? new ChatChannelIconSpec(Kind.ITEM, id, meta)
                    : null;
        }
        String name = lower.startsWith(EMOJI_PREFIX)
                ? lower.substring(EMOJI_PREFIX.length()).trim() : lower;
        return isEmojiName(name) ? new ChatChannelIconSpec(Kind.EMOJI, name, 0)
                : null;
    }

    /** The text the icon is written and carried as; parses back to itself. */
    public String toText() {
        if (this.kind == Kind.ITEM) {
            return ITEM_PREFIX + this.name
                    + (this.meta == 0 ? "" : "@" + this.meta);
        }
        return EMOJI_PREFIX + this.name;
    }

    public Kind getKind() { return this.kind; }
    /** The emoji's canonical name, or the item's registry id. */
    public String getName() { return this.name; }
    /** The item's damage value; zero for an emoji. */
    public int getMeta() { return this.meta; }

    /** A registry id: a namespace, a colon and a path, in the registry's own characters. */
    private static boolean isItemId(String id) {
        if (id.length() == 0 || id.startsWith(":") || id.endsWith(":")) {
            return false;
        }
        for (int index = 0; index < id.length(); index++) {
            char character = id.charAt(index);
            if ((character < 'a' || character > 'z')
                    && (character < 'A' || character > 'Z')
                    && (character < '0' || character > '9')
                    && character != '_' && character != '.'
                    && character != '-' && character != ':') {
                return false;
            }
        }
        return true;
    }

    /** An emoji's canonical name: what stands between the colons of a shortcode. */
    private static boolean isEmojiName(String name) {
        if (name.length() == 0) {
            return false;
        }
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if ((character < 'a' || character > 'z')
                    && (character < '0' || character > '9')
                    && character != '_' && character != '+'
                    && character != '-') {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChatChannelIconSpec)) {
            return false;
        }
        ChatChannelIconSpec that = (ChatChannelIconSpec)other;
        return this.kind == that.kind && this.meta == that.meta
                && this.name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return (this.kind.hashCode() * 31 + this.name.hashCode()) * 31
                + this.meta;
    }

    @Override
    public String toString() {
        return toText();
    }
}
