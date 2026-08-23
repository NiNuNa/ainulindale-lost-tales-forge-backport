package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatMessageValidator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a Discord message into something the chat accepts, and a chat
 * message into something Discord shows faithfully. Inbound, Discord's
 * markup is spelled out — {@code <@id>} becomes {@code @name} from the
 * message's own mention list, {@code <#id>} a {@code #channel},
 * {@code <:smile:id>} its {@code :smile:} — line breaks collapse to
 * spaces, control characters and section signs go, and the result is
 * cut to the chat's own length. Outbound, nothing is rewritten: the
 * webhook is told to ping nobody instead.
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
        Matcher emoji = CUSTOM_EMOJI.matcher(text);
        StringBuffer emojis = new StringBuffer();
        while (emoji.find()) {
            emoji.appendReplacement(emojis,
                    Matcher.quoteReplacement(":" + emoji.group(1) + ":"));
        }
        emoji.appendTail(emojis);
        text = stripUnsendable(emojis.toString());
        text = WHITESPACE.matcher(text).replaceAll(" ").trim();
        if (text.length() > ChatMessageValidator.MAX_CHARACTERS) {
            text = text.substring(0, ChatMessageValidator.MAX_CHARACTERS - 3)
                    .trim() + "...";
        }
        return ChatMessageValidator.isValid(text) ? text : "";
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
     * Drops formatting codes (the section sign and the letter after it)
     * and control characters other than whitespace.
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
            kept.append(character);
        }
        return kept.toString();
    }
}
