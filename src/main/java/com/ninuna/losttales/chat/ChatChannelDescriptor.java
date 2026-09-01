package com.ninuna.losttales.chat;

/**
 * Everything the chat knows about one channel: the permanent string id,
 * how it presents, the identity its lines wear, how the server routes
 * it, and what a player must hold to use it. The built-in channels each
 * carry one, built by {@link ChatChannel}; the server's gates and the
 * client's tab state read the channel through these facts, so a channel
 * described by data rather than an enum constant needs nothing more to
 * be complete.
 */
public final class ChatChannelDescriptor {
    private final String id;
    private final String displayName;
    private final ChatIdentityType identityType;
    private final ChatRecipientRule recipientRule;
    private final ChatChannelAccess access;
    private final int displayColor;

    public ChatChannelDescriptor(String id, String displayName,
                                 ChatIdentityType identityType,
                                 ChatRecipientRule recipientRule,
                                 ChatChannelAccess access,
                                 int displayColor) {
        if (id == null || id.trim().length() == 0) {
            throw new IllegalArgumentException("id must not be empty");
        }
        if (displayName == null || identityType == null
                || recipientRule == null || access == null) {
            throw new IllegalArgumentException(
                    "channel " + id + " is incompletely described");
        }
        this.id = id.trim();
        this.displayName = displayName;
        this.identityType = identityType;
        this.recipientRule = recipientRule;
        this.access = access;
        this.displayColor = displayColor;
    }

    public String getId() { return this.id; }
    public String getDisplayName() { return this.displayName; }
    public ChatIdentityType getIdentityType() { return this.identityType; }
    public ChatRecipientRule getRecipientRule() { return this.recipientRule; }
    public ChatChannelAccess getAccess() { return this.access; }
    public int getDisplayColor() { return this.displayColor; }

    @Override
    public String toString() {
        return this.id;
    }
}
