package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatStatusLine;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.emoji.ChatEmojiParser;
import com.ninuna.losttales.chat.emoji.ChatEmojiShortcodes;
import java.nio.charset.Charset;
import java.util.List;
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
 * {@code :name:}, any other Unicode emoji its Discord name between colons,
 * Discord's block markup is folded into the inline marks the chat reads
 * ({@link #normalizeMarkdown}), line breaks collapse to spaces, control
 * characters, section signs and whatever no font can draw go, and the
 * result is cut to the chat's own length. Outbound, a canonical
 * shortcode becomes the Unicode emoji Discord renders — the mod's own
 * sprites stay literal text — and what Discord would draw and the game
 * does not is broken with a zero-width space
 * ({@link #breakDiscordOnlyMarkup}), so a post reads on Discord as its
 * line read in the game. The mentions a player types are broken the same
 * way, and the webhook pings only the members the server resolved
 * ({@link DiscordMentions}).
 */
public final class DiscordMessageSanitizer {
    private static final Pattern USER_MENTION = Pattern.compile("<@!?(\\d+)>");
    private static final Pattern ROLE_MENTION = Pattern.compile("<@&(\\d+)>");
    private static final Pattern CHANNEL_MENTION = Pattern.compile("<#(\\d+)>");
    private static final Pattern CUSTOM_EMOJI =
            Pattern.compile("<a?:([A-Za-z0-9_]+):\\d+>");
    /** A custom emoji's name as Discord allows it. */
    private static final Pattern CUSTOM_EMOJI_NAME =
            Pattern.compile("[A-Za-z0-9_]{1,32}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    /** A fenced block, with or without a language tag on its first line. */
    private static final Pattern CODE_FENCE =
            Pattern.compile("```(?:[A-Za-z0-9_+-]*\\n)?([\\s\\S]*?)```");
    /** A line's leading header, block quote or subtext mark. */
    private static final Pattern LINE_MARK =
            Pattern.compile("(?m)^[ \\t]*(?:#{1,3} |>>> |> |-# )");
    /** Discord's underscore italics, at word boundaries only. */
    private static final Pattern UNDERSCORE_ITALIC =
            Pattern.compile("(?<![\\w*_])_([^_\\s](?:[^_]*?[^_\\s])?)_(?![\\w_])");
    /** Discord display names are bounded; the chat bounds them again. */
    private static final int MAX_NAME_LENGTH = 32;
    /**
     * The most bytes a name takes as UTF-8: a Discord member's name is
     * their account's name in the chat, and has to fit where an account
     * name goes.
     */
    static final int MAX_NAME_BYTES = 64;

    private static final Charset UTF_8 = Charset.forName("UTF-8");

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
        text = normalizeMarkdown(text);
        text = stripUnsendable(text);
        text = WHITESPACE.matcher(text).replaceAll(" ").trim();
        if (text.length() > ChatMessageValidator.MAX_CHARACTERS) {
            text = text.substring(0, ChatMessageValidator.MAX_CHARACTERS - 3)
                    .trim() + "...";
        }
        return ChatMessageValidator.isValid(text) ? text : "";
    }

    /** The longest file or sticker name the chat shows. */
    static final int MAX_ATTACHED_NAME = 32;

    /**
     * A member's message as the chat shows it, with what it carries
     * besides its words (Nils, 2026-09-28, D3 a): a forward's words behind
     * *[Forwarded]*, each sticker as *[Sticker: name]*, and each file by
     * its name in italics, followed by the message's own link on Discord,
     * where the file is. The address stays in sight: no word stands in for
     * a link. {@code words} and {@code forwarded} are already
     * {@link #inbound}'s; the words give way first when all of it is longer
     * than a chat line, and the link goes before a file name does.
     */
    static String inboundWithAttachments(String words, String forwarded,
                                         List<String> stickers, List<String> files,
                                         String messageLink) {
        StringBuilder extras = new StringBuilder();
        if ((words == null || words.length() == 0) && forwarded != null
                && forwarded.length() > 0) {
            words = "*[Forwarded]* " + forwarded;
        }
        for (String sticker : stickers) {
            String name = attachedName(sticker);
            if (name.length() > 0) {
                extras.append(" *[Sticker: ").append(name).append("]*");
            }
        }
        boolean anyFile = false;
        for (String file : files) {
            String name = attachedName(file);
            if (name.length() > 0) {
                extras.append(" *").append(name).append('*');
                anyFile = true;
            }
        }
        String link = anyFile && messageLink != null ? " " + messageLink : "";
        String tail = extras.toString() + link;
        if (tail.length() > ChatMessageValidator.MAX_CHARACTERS / 2) {
            tail = extras.toString();
        }
        String body = words == null ? "" : words;
        int room = ChatMessageValidator.MAX_CHARACTERS - tail.length();
        if (body.length() > room) {
            body = room > 3 ? body.substring(0, room - 3).trim() + "..." : "";
        }
        String text = (body + tail).trim();
        return ChatMessageValidator.isValid(text) ? text : "";
    }

    /** A file or sticker name as plain words: no marks, no codes, cut short. */
    private static String attachedName(String name) {
        if (name == null) {
            return "";
        }
        String plain = stripUnsendable(name).replaceAll("[*_~|`\\\\\\[\\]]", "").trim();
        return plain.length() > MAX_ATTACHED_NAME
                ? plain.substring(0, MAX_ATTACHED_NAME - 3) + "..." : plain;
    }

    /**
     * A Discord member's custom status as the chat's status line: its
     * emoji first, as its shortcode where the chat has it and else as its
     * Discord name between colons, as a message's emoji are; anything no
     * font can draw left out, and the whole cleaned and cut as a player's
     * line is ({@link ChatStatusLine#clean}); empty for nothing left.
     * {@code emojiName} is the status emoji's name, the character itself
     * for a Unicode one; {@code custom} says it is a server's own emoji.
     */
    public static String inboundStatusLine(String state, String emojiName,
                                           boolean custom) {
        String emoji = "";
        if (emojiName != null && emojiName.length() > 0) {
            if (custom) {
                ChatEmoji known = ChatEmoji.fromInputName(
                        emojiName.toLowerCase(Locale.ROOT));
                emoji = known != null ? known.getShortcode()
                        : CUSTOM_EMOJI_NAME.matcher(emojiName).matches()
                                ? ":" + emojiName + ":" : "";
            } else {
                emoji = stripUnsendable(unicodeToShortcodes(emojiName)).trim();
            }
        }
        String words = state == null ? ""
                : stripUnsendable(unicodeToShortcodes(state)).trim();
        return ChatStatusLine.clean(emoji.length() == 0 ? words
                : words.length() == 0 ? emoji : emoji + " " + words);
    }

    /**
     * Discord's markup as the chat reads it. The inline marks are the
     * same on both sides — {@code **}, {@code *}, {@code __}, {@code ~~},
     * {@code ||}, {@code `} — and pass through untouched; what Discord has
     * and a chat line has no room for is folded: a fenced block becomes
     * an inline code span, a header, a block quote or a subtext mark
     * loses its leading mark, and {@code _italic_} becomes {@code *italic*}.
     * Nothing is escaped or dropped, so what a member typed is still what
     * the line says.
     */
    static String normalizeMarkdown(String text) {
        if (text == null || text.length() == 0) {
            return text == null ? "" : text;
        }
        String folded = text;
        if (folded.indexOf("```") >= 0) {
            Matcher fence = CODE_FENCE.matcher(folded);
            StringBuffer fenced = new StringBuffer();
            while (fence.find()) {
                String inner = fence.group(1).trim().replace('`', '\'');
                fence.appendReplacement(fenced, Matcher.quoteReplacement(
                        inner.length() == 0 ? "" : "`" + inner + "`"));
            }
            fence.appendTail(fenced);
            folded = fenced.toString();
        }
        folded = LINE_MARK.matcher(folded).replaceAll("");
        if (folded.indexOf('_') >= 0) {
            folded = UNDERSCORE_ITALIC.matcher(folded).replaceAll("*$1*");
        }
        return folded;
    }

    /**
     * The Discord text for a game message: every canonical shortcode
     * with a Unicode form becomes that emoji; the mod's own sprites and
     * everything else stay exactly as typed — the chat's marks are
     * Discord's marks.
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
     * A player's text with what Discord would draw and the game does not
     * broken by a zero-width space, which neither side shows: the
     * {@code ](} of a masked link {@code [text](url)}, which would hide
     * the address it leads to, and the mark that opens a line as a
     * heading ({@code #}, {@code ##}, {@code ###}), subtext ({@code -#},
     * which the bridge's own reply header is written in), a block quote
     * ({@code >}, {@code >>>}) or a list item ({@code -}, {@code *},
     * {@code +}, {@code 1.}), each only where Discord would read it so:
     * at a line's start, spaces aside, and followed by a space. The
     * inline marks both sides read alike — {@code **}, {@code *},
     * {@code __}, {@code ~~}, {@code ||} and {@code `} — cross unchanged.
     * A zero-width space rather than a backslash, since no backslash a
     * player types can undo it and a code span shows none.
     */
    static String breakDiscordOnlyMarkup(String text) {
        if (text == null || text.length() == 0) {
            return text == null ? "" : text;
        }
        StringBuilder out = new StringBuilder(text.length() + 8);
        boolean lineStart = true;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (lineStart && !isSpace(character)) {
                lineStart = false;
                if (opensBlock(text, index)) {
                    out.append(DiscordMentions.BREAK);
                }
            }
            out.append(character);
            if (character == ']' && index + 1 < text.length()
                    && text.charAt(index + 1) == '(') {
                out.append(DiscordMentions.BREAK);
            }
            if (character == '\n') {
                lineStart = true;
            }
        }
        return out.toString();
    }

    /**
     * Whether the first mark of a line, at {@code at}, would make Discord
     * draw the line as a heading, subtext, block quote or list item.
     */
    private static boolean opensBlock(String text, int at) {
        char first = text.charAt(at);
        if (first == '#') {
            int end = at;
            while (end < text.length() && text.charAt(end) == '#') {
                end++;
            }
            return end - at <= 3 && spaceAt(text, end);
        }
        if (first == '-') {
            return spaceAt(text, at + 1) || (at + 1 < text.length()
                    && text.charAt(at + 1) == '#' && spaceAt(text, at + 2));
        }
        if (first == '*' || first == '+') {
            return spaceAt(text, at + 1);
        }
        if (first == '>') {
            return spaceAt(text, at + 1)
                    || (text.startsWith(">>>", at) && spaceAt(text, at + 3));
        }
        if (first >= '0' && first <= '9') {
            int end = at;
            while (end < text.length() && text.charAt(end) >= '0'
                    && text.charAt(end) <= '9') {
                end++;
            }
            return end < text.length() && text.charAt(end) == '.'
                    && spaceAt(text, end + 1);
        }
        return false;
    }

    private static boolean spaceAt(String text, int index) {
        return index < text.length() && isSpace(text.charAt(index));
    }

    /**
     * Any kind of space, a line break and a no-break space among them:
     * Discord's parser takes each for the space a mark needs.
     */
    private static boolean isSpace(char character) {
        return Character.isWhitespace(character) || Character.isSpaceChar(character);
    }

    /**
     * Registered Unicode emoji become canonical shortcodes. A form the
     * registry does not carry becomes its Discord name between colons,
     * the longest listed sequence first, so a family or a flag is one
     * name; one the list lacks is left for {@link #stripUnsendable} to
     * drop. A registry emoji a joiner continues is never taken alone, so
     * a half-known sequence never turns into the wrong emoji, and the skin
     * tone after one goes with it.
     */
    private static String unicodeToShortcodes(String text) {
        StringBuilder result = new StringBuilder(text.length());
        int index = 0;
        while (index < text.length()) {
            ChatEmoji.UnicodeMatch match = ChatEmoji.matchUnicode(text, index);
            if (match != null && (index + match.length >= text.length()
                    || text.charAt(index + match.length) != '\u200D')) {
                result.append(match.emoji.getShortcode());
                index = pastSkinTones(text, index + match.length);
                continue;
            }
            ChatEmojiShortcodes.Named named =
                    ChatEmojiShortcodes.namedAt(text, index);
            if (named != null) {
                result.append(':').append(named.name).append(':');
                index += named.length;
                continue;
            }
            result.append(text.charAt(index));
            index++;
        }
        return result.toString();
    }

    /** The index past any skin tone modifiers starting at {@code index}. */
    private static int pastSkinTones(String text, int index) {
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            if (codePoint < 0x1F3FB || codePoint > 0x1F3FF) {
                return index;
            }
            index += Character.charCount(codePoint);
        }
        return index;
    }

    /**
     * An action as Discord shows it: its words in italics under the
     * speaker's name, as the game shows <em>Aldric draws his sword.</em>
     * (Nils, 2026-09-28, C2 a). Empty for no words.
     */
    public static String outboundAction(String message) {
        String words = outbound(message).trim();
        if (words.length() == 0) {
            return "";
        }
        int backslashes = 0;
        for (int at = words.length() - 1; at >= 0 && words.charAt(at) == '\\'; at--) {
            backslashes++;
        }
        // A last backslash would escape the closing mark.
        return "*" + words + (backslashes % 2 == 1 ? "\\" : "") + "*";
    }

    /**
     * The line a webhook post opens with when the game message answers
     * one Discord holds: Discord's small subtext, an arrow, the quoted
     * author in bold and the quoted text, an action's in italics — linked
     * to the original when a jump URL is known, plain when it is not. The
     * closest thing to a native reply a webhook can carry: Discord accepts
     * no {@code message_reference} on a webhook execution, so the header
     * says in markdown what the reply banner would have said.
     */
    public static String replyHeader(String author, String excerpt,
                                     boolean action, String jumpUrl) {
        String name = escapeMarkdown(outbound(author));
        String quoted = escapeMarkdown(outbound(excerpt));
        String body = "**" + name + "**"
                + (quoted.length() == 0 ? ""
                        : action ? " *" + quoted + "*" : " — " + quoted);
        if (jumpUrl != null && jumpUrl.length() > 0) {
            body = "[" + body + "](" + jumpUrl + ")";
        }
        return "-# ↩ " + body + "\n";
    }

    /**
     * The line a webhook post opens with when the game message is a
     * forward: Discord's small subtext, a forward arrow, the conversation
     * the message was said in by its code name, and its author in bold.
     * {@code link} is the game's {@code #code/id}; the id means nothing on
     * Discord and is left off.
     */
    public static String forwardHeader(String author, String link) {
        String place = link == null ? "" : link.trim();
        int slash = place.indexOf('/');
        if (slash >= 0) {
            place = place.substring(0, slash);
        }
        return "-# ↪ Forwarded from " + escapeMarkdown(place) + " · **"
                + escapeMarkdown(outbound(author)) + "**\n";
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

    /**
     * A Discord author's name as the chat shows it, at most
     * {@link #MAX_NAME_LENGTH} characters and {@link #MAX_NAME_BYTES}
     * bytes; empty for nothing usable.
     */
    public static String inboundName(String name) {
        if (name == null) {
            return "";
        }
        String clean = WHITESPACE.matcher(stripUnsendable(name))
                .replaceAll(" ").trim();
        if (clean.length() > MAX_NAME_LENGTH) {
            clean = clean.substring(0, MAX_NAME_LENGTH);
        }
        // What is left has no surrogates, so every character is whole.
        while (clean.getBytes(UTF_8).length > MAX_NAME_BYTES) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean.trim();
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
