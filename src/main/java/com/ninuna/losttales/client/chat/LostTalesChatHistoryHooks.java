package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.config.LostTalesConfig;
import net.minecraft.client.gui.GuiNewChat;

/**
 * Called from patched vanilla code: the size vanilla's chat history is
 * trimmed to. {@code GuiNewChat.func_146237_a} keeps at most a hundred
 * messages and a hundred wrapped lines, a literal the coremod replaces
 * with {@link #capacity()}, so a long session stays scrollable in every
 * channel tab. The bound is the config's, kept inside
 * {@link #MIN_CAPACITY} and {@link #MAX_CAPACITY} whatever the file
 * says. Without the patch vanilla's hundred applies and the property is
 * never set.
 */
public final class LostTalesChatHistoryHooks {
    /** Set by the coremod once the capacity patch is in place. */
    public static final String ACTIVE_PROPERTY =
            "losttales.chatHistory.active";
    /** Vanilla's own limit; never less. */
    public static final int MIN_CAPACITY = 100;
    /** Enough for a long evening in every channel; memory stays modest. */
    public static final int MAX_CAPACITY = 5000;

    private LostTalesChatHistoryHooks() {}

    /** Messages, and wrapped lines, the history keeps. */
    public static int capacity() {
        return Math.max(MIN_CAPACITY,
                Math.min(MAX_CAPACITY, LostTalesConfig.chatHistoryLines));
    }

    public static boolean isActive() {
        return Boolean.getBoolean(ACTIVE_PROPERTY);
    }

    /**
     * Vanilla's own line replacement, skipped while the history is
     * being laid out again.
     *
     * <p>{@code GuiNewChat.func_146237_a} opens by deleting whatever
     * carried the same line id, which is how
     * {@code printChatMessageWithOptionalDeletion} replaces a line. That
     * delete also removes the message from the unwrapped history — and
     * {@code refreshChat} walks that very list by index while calling
     * the method for each of its entries, re-adding nothing (its refresh
     * flag says the message is already filed). Vanilla never notices,
     * because vanilla prints with the id zero and the delete never runs;
     * every Lost Tales line carries an id of its own, so a refresh —
     * from a resize, a GUI-scale change or a window being made wider —
     * emptied the history as it walked it.</p>
     *
     * <p>Deleting is right when a line is being replaced and wrong when
     * it is being laid out again, which is exactly what the flag says.</p>
     */
    public static void deleteUnlessRefreshing(GuiNewChat chat, int chatLineId,
                                              boolean refreshing) {
        if (!refreshing && chat != null) {
            chat.deleteChatLine(chatLineId);
        }
    }
}
