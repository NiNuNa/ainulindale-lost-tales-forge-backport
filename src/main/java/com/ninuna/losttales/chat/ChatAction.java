package com.ninuna.losttales.chat;

/**
 * An action line: what a character does, typed as {@code /me draws his
 * sword} in an in-character channel and shown as a sentence the
 * speaker's name opens, <em>Aldric draws his sword.</em> The words are
 * the message and the name is the line's own, so an action is a message
 * with a flag and nothing more. Pure rules over the words, asked alike
 * by the client that shows the line at once and the server that keeps
 * it.
 */
public final class ChatAction {
    private ChatAction() {}

    /**
     * The words as the line keeps them: one paragraph, since an action is
     * one sentence and its wrapped rows start at the edge, trimmed, and
     * ended with a full stop when they end on a letter or a digit and a
     * message still has room for one. Empty for no words.
     */
    public static String sentence(String words) {
        String text = ChatMessageValidator.oneLine(words).trim();
        if (text.length() == 0) {
            return "";
        }
        if (Character.isLetterOrDigit(text.charAt(text.length() - 1))) {
            String closed = text + ".";
            if (ChatMessageValidator.isValid(closed)) {
                return closed;
            }
        }
        return text;
    }

    /**
     * Whether the words make an action a line may carry: some words,
     * within a message's length and characters. An empty action is
     * refused like an empty message.
     */
    public static boolean isValid(String words) {
        return ChatMessageValidator.isValid(sentence(words));
    }
}
