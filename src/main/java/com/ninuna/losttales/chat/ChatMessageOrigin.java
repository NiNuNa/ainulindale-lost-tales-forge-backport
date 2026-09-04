package com.ninuna.losttales.chat;

/**
 * Where a chat line entered the server: typed by a player, or carried in
 * by the Discord bridge. The bridge posts lines of the first kind only,
 * so a line that came from Discord can never be sent back to it.
 */
public enum ChatMessageOrigin {
    PLAYER,
    DISCORD
}
