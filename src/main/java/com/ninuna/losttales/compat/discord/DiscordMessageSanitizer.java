package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.emoji.ChatEmojiParser;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a Discord message into something the chat accepts, and a chat
 * message into something Discord shows faithfully, both through the one
 * emoji registry. Inbound, Discord's markup is spelled out — {@code <@id>}
 * becomes {@code @name} from the message's own mention list, {@code <#id>}
 * a {@code #channel}, a custom {@code <:name:id>} its {@code :name:} —
 * registered Unicode emoji and alias shortcodes become their canonical
 * {@code :name:}, line breaks collapse to spaces, control characters,
 * section signs and whatever emoji the registry does not carry go, and
 * the result is cut to the chat's own length. Outbound, a canonical
 * shortcode becomes the Unicode emoji Discord renders — the mod's own
 * sprites stay literal text — and nothing else is rewritten: the webhook
 * is told to ping nobody instead.
 */
public final class DiscordMessageSanitizer {
    private static final Pattern USER_MENTION = Pattern.compile("<@!?(\\d+)>");
    private static final Pattern ROLE_MENTION = Pattern.compile("<@&(\\d+)>");
    private static final Pattern CHANNEL_MENTION = Pattern.compile("<#(\\d+)>");
    private static final Pattern CUSTOM_EMOJI =
            Pattern.compile("<a?:([A-Za-z0-9_]+):\\d+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    /** Discord display names are bounded; the chat bounds them again. */
    private static final int MAX_NAME_LENGTH = 32;

    private DiscordMessageSanitizer() {}

    /**
     * The chat text for a Discord message, or empty when nothing
     * sayable is left (an attachment-only post, an empty line).
     */
    public static String inbound(String content, Map<String, String> mentionNames) {
        if (content == null) {
            return "";
        }
        String text = replaceAll(USER_MENTION, content, "@", mentionNames, "user");
        text = replaceAll(ROLE_MENTION, text, "@", null, "role");
        text = replaceAll(CHANNEL_MENTION, text, "#", null, "channel");
        // A custom emoji whose name the registry knows — canonically or
        // as an alias — becomes that emoji; any other stays its name.
        Matcher emoji = CUSTOM_EMOJI.matcher(text);
        StringBuffer emojis = new StringBuffer();
        while (emoji.find()) {
            ChatEmoji known = ChatEmoji.fromInputName(
                    emoji.group(1).toLowerCase(Locale.ROOT));
            emoji.appendReplacement(emojis, Matcher.quoteReplacement(
                    known != null ? known.getShortcode()
                            : ":" + emoji.group(1) + ":"));
        }
        emoji.appendTail(emojis);
        text = ChatEmojiParser.normalizeAliases(emojis.toString());
        text = unicodeToShortcodes(text);
        text = stripUnsendable(text);
        text = WHITESPACE.matcher(text).replaceAll(" ").trim();
        if (text.length() > ChatMessageValidator.MAX_CHARACTERS) {
            text = text.substring(0, ChatMessageValidator.MAX_CHARACTERS - 3)
                    .trim() + "...";
        }
        return ChatMessageValidator.isValid(text) ? text : "";
    }

    /**
     * The Discord text for a game message: every canonical shortcode
     * with a Unicode form becomes that emoji; the mod's own sprites and
     * everything else stay exactly as typed.
     */
    public static String outbound(String message) {
        if (message == null || message.indexOf(':') < 0) {
            return message == null ? "" : message;
        }
        StringBuilder result = new StringBuilder(message.length());
        for (ChatEmojiParser.Segment segment
                : ChatEmojiParser.split(message)) {
            if (segment.isEmoji()
                    && segment.getEmoji().getUnicode().length() > 0) {
                result.append(segment.getEmoji().getUnicode());
            } else if (segment.isEmoji()) {
                result.append(segment.getEmoji().getShortcode());
            } else {
                result.append(segment.getText());
            }
        }
        return result.toString();
    }

    /**
     * Registered Unicode emoji become canonical shortcodes. A form the
     * registry does not carry — a ZWJ sequence continuing past a match
     * included — is left for {@link #stripUnsendable} to drop, so a
     * half-known sequence never turns into the wrong emoji.
     */
    private static String unicodeToShortcodes(String text) {
        StringBuilder result = null;
        int index = 0;
        while (index < text.length()) {
            ChatEmoji.UnicodeMatch match = ChatEmoji.matchUnicode(text, index);
            if (match != null && (index + match.length >= text.length()
                    || text.charAt(index + match.length) != '\u200D')) {
                if (result == null) {
                    result = new StringBuilder(text.length());
                    result.append(text, 0, index);
                }
                result.append(match.emoji.getShortcode());
                index += match.length;
                continue;
            }
            if (result != null) {
                result.append(text.charAt(index));
            }
            index++;
        }
        return result == null ? text : result.toString();
    }

    /**
     * The line a webhook post opens with when the game message answers
     * one Discord holds: Discord's small subtext, an arrow, the quoted
     * author in bold and the quoted text — linked to the original when
     * a jump URL is known, plain when it is not. The closest thing to a
     * native reply a webhook can carry: Discord accepts no
     * {@code message_reference} on a webhook execution, so the header
     * says in markdown what the reply banner would have said.
     */
    public static String replyHeader(String author, String excerpt,
                                     String jumpUrl) {
        String name = escapeMarkdown(outbound(author));
        String quoted = escapeMarkdown(outbound(excerpt));
        String body = "**" + name + "**"
                + (quoted.length() == 0 ? "" : " — " + quoted);
        if (jumpUrl != null && jumpUrl.length() > 0) {
            body = "[" + body + "](" + jumpUrl + ")";
        }
        return "-# ↩ " + body + "\n";
    }

    /**
     * Backslash-escapes every character Discord's markdown gives meaning
     * to, the link brackets included, so a name or a quote reads as the
     * text it is wherever the bridge writes it.
     */
    public static String escapeMarkdown(String text) {
        String value = text == null ? "" : text.trim();
        StringBuilder escaped = new StringBuilder(value.length() + 4);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\' || character == '*' || character == '_'
                    || character == '~' || character == '`' || character == '|'
                    || character == '>' || character == '@' || character == '#'
                    || character == '[' || character == ']'
                    || character == '(' || character == ')') {
                escaped.append('\\');
            }
            escaped.append(character);
        }
        return escaped.toString();
    }

    /** A Discord author's name as the chat shows it; empty for nothing usable. */
    public static String inboundName(String name) {
        if (name == null) {
            return "";
        }
        String clean = WHITESPACE.matcher(stripUnsendable(name))
                .replaceAll(" ").trim();
        return clean.length() > MAX_NAME_LENGTH
                ? clean.substring(0, MAX_NAME_LENGTH).trim() : clean;
    }

    private static String replaceAll(Pattern pattern, String text,
                                     String prefix, Map<String, String> names,
                                     String fallback) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String name = names == null ? null : names.get(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                    prefix + (name == null || name.length() == 0
                            ? fallback : name)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Drops formatting codes (the section sign and the letter after it),
     * control characters other than whitespace, and what remains of
     * emoji the registry does not carry: surrogates, joiners, and
     * variation selectors, none of which the chat's font can show.
     */
    private static String stripUnsendable(String text) {
        StringBuilder kept = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '§') {
                index++;
                continue;
            }
            if (Character.isISOControl(character)
                    && !Character.isWhitespace(character)) {
                continue;
            }
            if (Character.isSurrogate(character) || character == '\u200D'
                    || character == '\uFE0F') {
                continue;
            }
            kept.append(character);
        }
        return kept.toString();
    }
}
