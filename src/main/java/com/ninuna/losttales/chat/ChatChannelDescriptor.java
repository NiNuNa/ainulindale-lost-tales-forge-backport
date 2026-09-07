package com.ninuna.losttales.chat;

/**
 * Everything the chat knows about one channel: the permanent string id,
 * how it presents its senders, how the server routes it, and what a
 * player must hold to use it. The built-in channels each carry one,
 * built by {@link ChatChannel}; the server's gates and the client's tab
 * state read the channel through these facts, so a channel described by
 * data rather than an enum constant needs nothing more to be complete.
 */
public final class ChatChannelDescriptor {
    private final String id;
    private final String displayName;
    private final ChatPresentationMode presentation;
    private final ChatRecipientRule recipientRule;
    private final ChatChannelAccess access;
    private final int displayColor;
    private final boolean bridgeable;
    private final ChatChannelScope scope;

    public ChatChannelDescriptor(String id, String displayName,
                                 ChatPresentationMode presentation,
                                 ChatRecipientRule recipientRule,
                                 ChatChannelAccess access,
                                 int displayColor,
                                 boolean bridgeable) {
        this(id, displayName, presentation, recipientRule, access, displayColor,
                bridgeable, ChatChannelScope.NONE);
    }

    public ChatChannelDescriptor(String id, String displayName,
                                 ChatPresentationMode presentation,
                                 ChatRecipientRule recipientRule,
                                 ChatChannelAccess access,
                                 int displayColor,
                                 boolean bridgeable,
                                 ChatChannelScope scope) {
        if (id == null || id.trim().length() == 0) {
            throw new IllegalArgumentException("id must not be empty");
        }
        if (displayName == null || presentation == null
                || recipientRule == null || access == null) {
            throw new IllegalArgumentException(
                    "channel " + id + " is incompletely described");
        }
        this.id = id.trim();
        this.displayName = clipDisplayName(displayName);
        this.presentation = presentation;
        this.recipientRule = recipientRule;
        this.access = access;
        this.displayColor = displayColor;
        this.bridgeable = bridgeable;
        this.scope = scope == null ? ChatChannelScope.NONE : scope;
    }

    /**
     * The longest a channel's name may be shown as.
     *
     * <p>Every other channel name in the mod is a code constant, but a
     * config may name one, and that name is written into the access
     * packet. A bound here is what keeps a long line in a config file
     * from being a payload no server can encode — and the access packet
     * carries every gate, role and capability a player has, so failing to
     * write it costs that player all of them.</p>
     */
    public static final int MAX_DISPLAY_NAME_LENGTH = 16;

    /** The name as it will be shown, cut to what can be carried. */
    public static String clipDisplayName(String displayName) {
        String trimmed = displayName == null ? "" : displayName.trim();
        return trimmed.length() <= MAX_DISPLAY_NAME_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_DISPLAY_NAME_LENGTH).trim();
    }

    public String getId() { return this.id; }
    public String getDisplayName() { return this.displayName; }
    /** How the channel's lines present their sender; see {@link ChatRolePresentation}. */
    public ChatPresentationMode getPresentation() { return this.presentation; }
    public ChatRecipientRule getRecipientRule() { return this.recipientRule; }
    public ChatChannelAccess getAccess() { return this.access; }
    public int getDisplayColor() { return this.displayColor; }
    /**
     * Whether the server's Discord bridge may carry the channel's lines
     * out of the game or into it. A policy fact, separate from routing
     * and access: what is private stays private however the bridge is
     * configured.
     */
    public boolean isBridgeable() { return this.bridgeable; }

    /**
     * What tells one conversation on the channel from another; see
     * {@link ChatChannelScope}. A scoped channel's tabs carry the
     * identity they are read as.
     */
    public ChatChannelScope getScope() { return this.scope; }

    @Override
    public String toString() {
        return this.id;
    }
}
