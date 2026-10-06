package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatStatusLine;
import com.ninuna.losttales.chat.ChatTranslatedWords;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.emoji.ChatEmojiParser;
import com.ninuna.losttales.chat.emoji.ChatEmojiShortcodes;
import com.ninuna.losttales.util.LostTalesWords;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a Discord message into something the chat accepts, and a chat
 * message into something Discord shows faithfully, both through the one
 * emoji registry. Inbound, Discord's markup is spelled out — {@code <@id>}
 * becomes {@code @name} from the message's own mention list, a role's
 * {@code <@&id>} {@code @**Moderators**} and a channel's {@code <#id>}
 * {@code #**general**} by the names their server gives them
 * ({@link #inbound}), a custom {@code <:name:id>} its {@code :name:} —
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
    /** A mention of a member ({@code <@id>}, {@code <@!id>}), a role ({@code <@&id>}) or a channel ({@code <#id>}). */
    private static final Pattern MENTION = Pattern.compile("<(@!?|@&|#)(\\d+)>");
    private static final Pattern CUSTOM_EMOJI =
            Pattern.compile("<a?:([A-Za-z0-9_]+):\\d+>");
    /** A custom emoji's name as Discord allows it. */
    private static final Pattern CUSTOM_EMOJI_NAME =
            Pattern.compile("[A-Za-z0-9_]{1,32}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    /** Whitespace on a line: everything but a line break. */
    private static final Pattern SPACES = Pattern.compile("[^\\S\\n\\r]+");
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
    /** The most characters a Discord message holds. */
    static final int MAX_POST_CHARACTERS = 2000;

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private DiscordMessageSanitizer() {}

    /** The lang key of the word a mention of a member stands for when the message does not name them. */
    static final String UNKNOWN_USER = "chat.losttales.discord.unknown_user";
    /** The lang key of the word a mention of a role stands for. */
    static final String UNKNOWN_ROLE = "chat.losttales.discord.unknown_role";
    /** The lang key of the word a mention of a channel stands for. */
    static final String UNKNOWN_CHANNEL = "chat.losttales.discord.unknown_channel";
    /** The lang key of the mark a forward's words follow, which each game translates. */
    static final String FORWARDED = ChatTranslatedWords.PREFIX + "discord_forwarded";
    /** The lang key of a sticker's mark, its name the argument, which each game translates. */
    static final String STICKER = ChatTranslatedWords.PREFIX + "discord_sticker";

    /**
     * What the bridge knows of the names in the Discord server a message
     * was said in, by id: the server's own roles and channels only, empty
     * for one it has not heard of or one in another server.
     */
    public interface Places {
        /** Knows no names. */
        Places NONE = new Places() {
            @Override
            public String roleName(String roleId) {
                return "";
            }

            @Override
            public String channelName(String channelId) {
                return "";
            }
        };

        String roleName(String roleId);

        String channelName(String channelId);
    }

    /** The longest role or channel name a mention shows. */
    static final int MAX_PLACE_NAME = 32;
    /**
     * What a role's or channel's name loses: the chat's marks, and the
     * signs that open a mention, a channel link, a Discord code, a shared
     * thing's token, an emoji's shortcode or a web address.
     */
    private static final String PLACE_NAME_SIGNS = "*_~|`\\[]@#<>:";

    /**
     * The chat text for a Discord message, or empty when nothing
     * sayable is left (an attachment-only post, an empty line). A member's
     * mention reads as their name, {@code @Frodo}. A role's or a channel's
     * reads as the name {@code places} gives it, in bold behind its
     * {@code @} or {@code #}: {@code @**Moderators**}, {@code #**general**}.
     * The marks right after the sign keep it from reading as a mention of
     * anyone in the game or a link to a game channel, both of which need
     * a name's own character there, and the name is plain words
     * ({@link #placeName}). A mention whose name is not known reads as
     * {@code @user}, {@code @role} or {@code #channel} in {@code words},
     * the server's: it stands inside the member's own words. Each mention
     * is read once, so a name put in is never read as a mention again.
     */
    public static String inbound(String content, Map<String, String> mentionNames,
                                 Places places, LostTalesWords words) {
        if (content == null) {
            return "";
        }
        Places named = places == null ? Places.NONE : places;
        Matcher mention = MENTION.matcher(content);
        StringBuffer mentioned = new StringBuffer();
        while (mention.find()) {
            String kind = mention.group(1);
            String id = mention.group(2);
            String said;
            if ("@&".equals(kind)) {
                String name = placeName(named.roleName(id));
                said = name.length() > 0 ? "@**" + name + "**"
                        : "@" + words.format(UNKNOWN_ROLE);
            } else if ("#".equals(kind)) {
                String name = placeName(named.channelName(id));
                said = name.length() > 0 ? "#**" + name + "**"
                        : "#" + words.format(UNKNOWN_CHANNEL);
            } else {
                String name = mentionNames == null ? null : mentionNames.get(id);
                said = "@" + (name == null || name.length() == 0
                        ? words.format(UNKNOWN_USER) : name);
            }
            mention.appendReplacement(mentioned, Matcher.quoteReplacement(said));
        }
        mention.appendTail(mentioned);
        String text = mentioned.toString();
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
        // Discord's lines are the chat's paragraphs, eight at most.
        text = ChatMessageValidator.paragraphs(
                SPACES.matcher(text).replaceAll(" "));
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
     * besides its words: a forward's words behind *[Forwarded]*, each
     * sticker as *[Sticker: name]*, and each file by its name in italics,
     * followed by the message's own link on Discord, where the file is.
     * The address stays in sight: no word stands in for a link.
     * {@code said} and {@code forwarded} are already {@link #inbound}'s;
     * the words give way first when all of it is longer than a chat line,
     * and the link goes before a file name does. The two marks are the
     * lang file's: the line's text has them in {@code words}, the
     * server's, and its pieces keep them as marks each game translates.
     */
    static DiscordInboundLine inboundWithAttachments(LostTalesWords words,
                                                     String said, String forwarded,
                                                     List<String> stickers,
                                                     List<String> files,
                                                     String messageLink) {
        List<DiscordInboundLine.Piece> body = new ArrayList<DiscordInboundLine.Piece>();
        if ((said == null || said.length() == 0) && forwarded != null
                && forwarded.length() > 0) {
            body.add(DiscordInboundLine.Piece.mark(words, FORWARDED));
            body.add(DiscordInboundLine.Piece.plain(" " + forwarded));
        } else if (said != null) {
            body.add(DiscordInboundLine.Piece.plain(said));
        }
        List<DiscordInboundLine.Piece> extras = new ArrayList<DiscordInboundLine.Piece>();
        for (String sticker : stickers) {
            String name = attachedName(sticker);
            if (name.length() > 0) {
                extras.add(DiscordInboundLine.Piece.plain(" "));
                extras.add(DiscordInboundLine.Piece.mark(words, STICKER, name));
            }
        }
        boolean anyFile = false;
        for (String file : files) {
            String name = attachedName(file);
            if (name.length() > 0) {
                extras.add(DiscordInboundLine.Piece.plain(" *" + name + "*"));
                anyFile = true;
            }
        }
        List<DiscordInboundLine.Piece> tail =
                new ArrayList<DiscordInboundLine.Piece>(extras);
        if (anyFile && messageLink != null) {
            tail.add(DiscordInboundLine.Piece.plain(" " + messageLink));
        }
        if (DiscordInboundLine.length(tail) > ChatMessageValidator.MAX_CHARACTERS / 2) {
            tail = extras;
        }
        int room = ChatMessageValidator.MAX_CHARACTERS - DiscordInboundLine.length(tail);
        if (DiscordInboundLine.length(body) > room) {
            List<DiscordInboundLine.Piece> cut = new ArrayList<DiscordInboundLine.Piece>();
            if (room > 3) {
                cut.addAll(DiscordInboundLine.trim(
                        DiscordInboundLine.slice(body, 0, room - 3)));
                cut.add(DiscordInboundLine.Piece.plain("..."));
            }
            body = cut;
        }
        body.addAll(tail);
        DiscordInboundLine line = new DiscordInboundLine(DiscordInboundLine.trim(body));
        return ChatMessageValidator.isValid(line.getText()) ? line
                : DiscordInboundLine.EMPTY;
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
                // A block of code is one span of the chat's inline code.
                String inner = WHITESPACE.matcher(fence.group(1).trim())
                        .replaceAll(" ").replace('`', '\'');
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
     * A post cut to what Discord takes, ending on "..." and never inside
     * a mention, channel or emoji in Discord's own brackets, nor between
     * the halves of a character. A long role-play turn naming many
     * members can pass the limit once each name is written as Discord's
     * id.
     */
    static String fitted(String content) {
        if (content == null || content.length() <= MAX_POST_CHARACTERS) {
            return content == null ? "" : content;
        }
        int end = MAX_POST_CHARACTERS - 3;
        int open = content.lastIndexOf('<', end - 1);
        if (open >= 0 && content.indexOf('>', open) >= end) {
            end = open;
        }
        if (end > 0 && Character.isHighSurrogate(content.charAt(end - 1))) {
            end--;
        }
        return content.substring(0, end) + "...";
    }

    /**
     * The action mark an action's post opens with. The backslash makes
     * Discord show the asterisk as it is, so the line is no list item
     * and the mark opens no italics.
     */
    static final String ACTION_MARK = "\\* ";

    /**
     * An action as Discord shows it, the way the game tells it under the
     * Narrator's name: the action mark, then the sentence the speaker's
     * name opens, in italics. {@code /me draws his sword.} said as Aldric
     * posts as {@code \* *Aldric draws his sword.*}. The name is escaped,
     * so it reads as written; the words cross as a line's do, and each
     * paragraph after the first is in italics of its own. Empty for no
     * words.
     */
    public static String outboundAction(String speaker, String message) {
        String words = outbound(message).trim();
        if (words.length() == 0) {
            return "";
        }
        String name = escapeMarkdown(outbound(speaker));
        StringBuilder action = new StringBuilder(
                words.length() + name.length() + 8);
        for (String paragraph : words.split("\n")) {
            String said = paragraph.trim();
            if (said.length() == 0) {
                continue;
            }
            if (action.length() == 0) {
                action.append(ACTION_MARK);
                if (name.length() > 0) {
                    said = name + " " + said;
                }
            } else {
                action.append('\n');
            }
            action.append(italic(said));
        }
        return action.toString();
    }

    /** Words in Discord's italics. */
    private static String italic(String words) {
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
     * forward: Discord's small subtext, a forward arrow, and in the
     * server's words the conversation the message was said in by its code
     * name and its author in bold. {@code link} is the game's
     * {@code #code/id}; the id means nothing on Discord and is left off.
     */
    public static String forwardHeader(LostTalesWords words, String author,
                                       String link) {
        String place = link == null ? "" : link.trim();
        int slash = place.indexOf('/');
        if (slash >= 0) {
            place = place.substring(0, slash);
        }
        return "-# ↪ " + words.format("chat.losttales.discord.forwarded",
                escapeMarkdown(place), escapeMarkdown(outbound(author))) + "\n";
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

    /**
     * A role's or channel's name as a mention of it shows it: plain words,
     * as a file's name is ({@link #attachedName}), and besides without a
     * sign that opens a mention, a channel link, a code, a token, an emoji
     * or a web address ({@link #PLACE_NAME_SIGNS}), so it holds none of
     * them. Spaces of every kind become one space, and the name is cut to
     * {@link #MAX_PLACE_NAME} characters; empty for nothing left.
     */
    static String placeName(String name) {
        if (name == null) {
            return "";
        }
        String kept = stripUnsendable(name);
        StringBuilder plain = new StringBuilder(kept.length());
        for (int index = 0; index < kept.length(); index++) {
            char character = kept.charAt(index);
            if (PLACE_NAME_SIGNS.indexOf(character) >= 0
                    || Character.getType(character) == Character.FORMAT) {
                continue;
            }
            if (isSpace(character)) {
                if (plain.length() > 0 && plain.charAt(plain.length() - 1) != ' ') {
                    plain.append(' ');
                }
                continue;
            }
            plain.append(character);
        }
        String words = plain.toString().trim();
        return words.length() > MAX_PLACE_NAME
                ? words.substring(0, MAX_PLACE_NAME).trim() : words;
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
