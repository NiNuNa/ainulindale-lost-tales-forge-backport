package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatMessageIds;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.IChatComponent;

/**
 * Marks the spoiler runs of a message so a click can reveal them: each
 * run of {@code ||spoiler||} text carries which message it belongs to
 * and which of that message's spoilers it is, and a reveal is recorded
 * against that pair rather than against any one built component — the
 * open window's line, the closed feed's copy and a grouped rebuild all
 * name the same spoiler, so revealing it anywhere reveals it
 * everywhere. The renderer keeps drawing the run obfuscated until the
 * pair is in the revealed set, and a reveal changes nothing the wrapper
 * measured, since an obfuscated glyph already takes its own glyph's
 * width. Same carrier as {@link ChatMentionMarker}: the click event
 * survives vanilla's wrapped-chat component copies.
 *
 * <p>Only runs whose style says obfuscated are spoilers — a player's
 * own {@code &k} lives inside the text, never on the style — and a run
 * already carrying a click event (a mention inside a spoiler) keeps it:
 * that run stays obfuscated for good, which loses nothing worth having.
 * Reveals are one-way and last the session; what was seen cannot be
 * unseen, and the set goes with the rest of the chat's session state on
 * disconnect.</p>
 */
final class ChatSpoilerMarker {
    private static final String PREFIX = "losttales-chat-spoiler:";
    /** Spoilers this player has revealed, as {@code messageId:index}. */
    private static final Set<String> REVEALED = new HashSet<String>();

    private ChatSpoilerMarker() {}

    /**
     * Tags the spoiler runs among {@code siblings} from {@code from}
     * on, numbering each unbroken stretch of them as one spoiler of
     * {@code messageId}. Only a message the server named is tagged: a
     * pending echo's spoiler stays covered until the confirmed copy —
     * built with the real id — replaces it, and a client-only line
     * (an NPC's speech) has no name two builds would agree on.
     */
    static void mark(List<?> siblings, int from, long messageId) {
        if (!ChatMessageIds.isServerId(messageId) || siblings == null) {
            return;
        }
        int index = -1;
        boolean inSpoiler = false;
        for (int at = Math.max(0, from); at < siblings.size(); at++) {
            Object value = siblings.get(at);
            IChatComponent part = value instanceof IChatComponent
                    ? (IChatComponent)value : null;
            if (part == null || part.getChatStyle() == null
                    || !part.getChatStyle().getObfuscated()) {
                inSpoiler = false;
                continue;
            }
            if (!inSpoiler) {
                inSpoiler = true;
                index++;
            }
            if (part.getChatStyle().getChatClickEvent() != null) {
                continue;
            }
            part.getChatStyle().setChatClickEvent(new ClickEvent(
                    ClickEvent.Action.SUGGEST_COMMAND,
                    PREFIX + messageId + ":" + index));
        }
    }

    /** Whether the run is a spoiler a click could reveal or has revealed. */
    static boolean isMarker(IChatComponent component) {
        return keyOf(component) != null;
    }

    /** Whether the run's spoiler has been revealed by this player. */
    static boolean isRevealed(IChatComponent component) {
        String key = keyOf(component);
        return key != null && REVEALED.contains(key);
    }

    /**
     * Reveals the run's spoiler; a run that is no spoiler, or one
     * already revealed, changes nothing. The next frame draws every
     * copy of it plainly — nothing is re-laid-out, since the reveal
     * only changes which glyphs the same width is spent on.
     */
    static void reveal(IChatComponent component) {
        String key = keyOf(component);
        if (key != null) {
            REVEALED.add(key);
        }
    }

    /** Forgets every reveal; leaving a server takes the history anyway. */
    static void clear() {
        REVEALED.clear();
    }

    private static String keyOf(IChatComponent component) {
        if (component == null || component.getChatStyle() == null) {
            return null;
        }
        ClickEvent event = component.getChatStyle().getChatClickEvent();
        return event != null
                && event.getAction() == ClickEvent.Action.SUGGEST_COMMAND
                && event.getValue() != null
                && event.getValue().startsWith(PREFIX)
                ? event.getValue().substring(PREFIX.length()) : null;
    }
}
