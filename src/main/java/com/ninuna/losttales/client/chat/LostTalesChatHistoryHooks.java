package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.config.LostTalesConfig;

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
}
