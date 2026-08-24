package com.ninuna.losttales.user;

import com.ninuna.losttales.chat.ChatAccountRole;

/**
 * What a recognized Lost Tales user is recognized <em>as</em>. Recognition
 * is cosmetic: it decides credits, chat roles and, later, other
 * decorations, and nothing here is ever consulted for a permission,
 * an operator check or a gameplay decision.
 */
public enum ELostTalesUserRecognition {
    /** Listed for credits only; no chat role. */
    NONE(ChatAccountRole.NONE),
    /** A member of the Lost Tales team; wears the Developer chat role. */
    DEVELOPER(ChatAccountRole.DEVELOPER);

    private final ChatAccountRole chatRole;

    ELostTalesUserRecognition(ChatAccountRole chatRole) {
        this.chatRole = chatRole;
    }

    /** The cosmetic chat role this recognition shows, or {@link ChatAccountRole#NONE}. */
    public ChatAccountRole getChatRole() {
        return this.chatRole;
    }
}
