package com.ninuna.losttales.client.chat;

import net.minecraft.event.ClickEvent;
import net.minecraft.util.IChatComponent;

/**
 * How a line says which of its parts is the sender's name.
 *
 * <p>It says so the way vanilla does, and not with a marker of its own:
 * {@code EntityPlayer.func_145748_c_} puts a {@code SUGGEST_COMMAND}
 * click reading {@code /msg <name>&#32;} on every player name it builds,
 * and Lost Tales lines mirror that shape on purpose. A death message, an
 * achievement, another mod's line — anything vanilla built the name of —
 * is then read by exactly the same test, which is why the shape is
 * matched rather than replaced.</p>
 *
 * <p>Four places asked the question and two answered it, each with its
 * own edge behaviour: one omitted the action check, one assumed the
 * prefix was there. Everything that asks now asks here.</p>
 */
final class ChatSenderSpan {

    /** Vanilla's own prefix; not a verb this mod chose. */
    static final String WHISPER_PREFIX = "/msg ";

    private ChatSenderSpan() {}

    /** Whether the component is the part of a line naming its sender. */
    static boolean isSenderName(IChatComponent part) {
        return suggestionOf(part) != null;
    }

    /**
     * The whisper suggestion on the component, or null when it carries
     * none: the click has to be a suggestion, and it has to be a whisper.
     */
    static ClickEvent suggestionOf(IChatComponent part) {
        ClickEvent click = part == null || part.getChatStyle() == null
                ? null : part.getChatStyle().getChatClickEvent();
        return click != null
                && click.getAction() == ClickEvent.Action.SUGGEST_COMMAND
                && click.getValue() != null
                && click.getValue().startsWith(WHISPER_PREFIX)
                ? click : null;
    }

    /** The first part of the line naming its sender, or null. */
    static ClickEvent findSuggestion(IChatComponent line) {
        if (line == null) {
            return null;
        }
        for (Object value : line) {
            if (value instanceof IChatComponent) {
                ClickEvent found = suggestionOf((IChatComponent)value);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * The account a suggestion names, or an empty string when it names
     * none. Vanilla writes a trailing space after the name and nothing
     * else; a suggestion carrying more than the name is cut at the first
     * space rather than read as one long name.
     */
    static String accountOf(String suggestion) {
        if (suggestion == null || !suggestion.startsWith(WHISPER_PREFIX)) {
            return "";
        }
        String account = suggestion.substring(WHISPER_PREFIX.length()).trim();
        int space = account.indexOf(' ');
        return space < 0 ? account : account.substring(0, space);
    }

    /** The account the component's own suggestion names; empty for none. */
    static String accountOf(IChatComponent part) {
        ClickEvent click = suggestionOf(part);
        return click == null ? "" : accountOf(click.getValue());
    }

    /** The suggestion a line puts on the name of {@code accountName}. */
    static String suggestionFor(String accountName) {
        return WHISPER_PREFIX + (accountName == null ? "" : accountName) + " ";
    }
}
