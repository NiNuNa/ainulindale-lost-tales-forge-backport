package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatMentionCandidate;
import java.util.ArrayList;
import java.util.List;

/**
 * The mentions in text being typed: an {@code @} opening a word and the
 * longest name after it that the {@code @} list offers — a person's shown
 * name, account or character, or a role's name — whatever its case, with
 * no name's character right after it. So {@code @Aldric of Bree} is one
 * mention before {@code @Aldric} is, and a name the list does not offer
 * is no mention. Pure: the field asks, and a test can ask the same.
 */
final class ChatInputMentions {
    /** One mention found: its span in the text and whom it names. */
    static final class Found {
        final int start;
        final int end;
        final ChatMentionCandidate candidate;

        Found(int start, int end, ChatMentionCandidate candidate) {
            this.start = start;
            this.end = end;
            this.candidate = candidate;
        }
    }

    private ChatInputMentions() {}

    /** Every mention in {@code text}, in order, none overlapping. */
    static List<Found> find(String text, List<ChatMentionCandidate> candidates) {
        List<Found> found = new ArrayList<Found>();
        if (text == null || candidates == null || candidates.isEmpty()) {
            return found;
        }
        int at = text.indexOf('@');
        while (at >= 0) {
            Found hit = at(text, at, candidates);
            if (hit != null) {
                found.add(hit);
            }
            at = text.indexOf('@', hit == null ? at + 1 : hit.end);
        }
        return found;
    }

    /** The mention whose {@code @} stands at {@code at}, or null. */
    static Found at(String text, int at, List<ChatMentionCandidate> candidates) {
        if (at > 0 && isNameCharacter(text.charAt(at - 1))) {
            return null;
        }
        ChatMentionCandidate best = null;
        int bestLength = 0;
        for (int index = 0; index < candidates.size(); index++) {
            ChatMentionCandidate candidate = candidates.get(index);
            if (candidate == null) {
                continue;
            }
            int length = nameLength(text, at + 1, candidate.getDisplayName());
            for (String alias : candidate.getAliases()) {
                length = Math.max(length, nameLength(text, at + 1, alias));
            }
            if (length > bestLength) {
                best = candidate;
                bestLength = length;
            }
        }
        return best == null ? null : new Found(at, at + 1 + bestLength, best);
    }

    /**
     * How long {@code name} is where the text reads it whole from
     * {@code from}, with no name's character right after it; 0 where it
     * does not.
     */
    private static int nameLength(String text, int from, String name) {
        String wanted = name == null ? "" : name.trim();
        if (wanted.length() == 0
                || !text.regionMatches(true, from, wanted, 0, wanted.length())) {
            return 0;
        }
        int end = from + wanted.length();
        return end < text.length() && isNameCharacter(text.charAt(end))
                ? 0 : wanted.length();
    }

    private static boolean isNameCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }
}
