package com.ninuna.losttales.client.chat;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

/**
 * Folds long messages. Words that take more than five rows show their
 * first four and a Read more under them; a click opens the message, and
 * the Show less under it folds it again.
 *
 * <p>Which messages are open is kept by chat line id, which every view
 * of a message shares, so a message opened in one window is open in
 * all of them, the feed too. It lasts until the lines go. The run
 * carries the id on its click event, like {@link ChatSpoilerMarker}.</p>
 */
final class ChatFoldMarker {
    /** The rows of words a folded message keeps. */
    static final int KEPT_ROWS = 4;
    private static final String PREFIX = "losttales-chat-fold:";
    /** The messages the player has opened, by chat line id. */
    private static final Set<Integer> OPEN = new HashSet<Integer>();

    private ChatFoldMarker() {}

    static boolean isOpen(int chatLineId) {
        return OPEN.contains(Integer.valueOf(chatLineId));
    }

    /**
     * How the message folds when laid out now, or null for a line with
     * no id of its own: vanilla's lines all share id 0.
     */
    static ChatLineWrapper.Fold foldFor(int chatLineId) {
        if (chatLineId == 0) {
            return null;
        }
        boolean open = isOpen(chatLineId);
        return new ChatLineWrapper.Fold(KEPT_ROWS, open,
                toggle(chatLineId, open));
    }

    /** Read more for a folded message, Show less for an open one, in the chat's aside tone. */
    static IChatComponent toggle(int chatLineId, boolean open) {
        ChatComponentText run = new ChatComponentText(
                StatCollector.translateToLocal(open
                        ? "gui.losttales.chat.show_less"
                        : "gui.losttales.chat.read_more"));
        run.getChatStyle().setColor(LostTalesChatPresentation
                .nearestFormatting(LostTalesChatVisualStyle.asideRgb()));
        run.getChatStyle().setChatClickEvent(new ClickEvent(
                ClickEvent.Action.SUGGEST_COMMAND, PREFIX + chatLineId));
        return run;
    }

    static boolean isMarker(IChatComponent part) {
        return lineIdOf(part) != null;
    }

    /** The message the run folds, or null when it is not a fold's run. */
    static Integer lineIdOf(IChatComponent part) {
        ClickEvent event = part == null || part.getChatStyle() == null
                ? null : part.getChatStyle().getChatClickEvent();
        String value = event == null ? null : event.getValue();
        if (event == null || event.getAction() != ClickEvent.Action.SUGGEST_COMMAND
                || value == null || !value.startsWith(PREFIX)) {
            return null;
        }
        try {
            return Integer.valueOf(value.substring(PREFIX.length()));
        } catch (NumberFormatException notOurs) {
            return null;
        }
    }

    /**
     * Opens the run's message or folds it again, and has every view lay
     * it out anew. Answers whether it is open now.
     */
    static boolean flip(int chatLineId) {
        Integer key = Integer.valueOf(chatLineId);
        boolean open = !OPEN.remove(key);
        if (open) {
            OPEN.add(key);
        }
        ChatWindowLines.noteMutated();
        return open;
    }

    static void clear() {
        OPEN.clear();
    }
}
