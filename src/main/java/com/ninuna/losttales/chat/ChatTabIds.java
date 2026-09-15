package com.ninuna.losttales.chat;

import java.util.Locale;

/**
 * The grammar of a tab id, the part of it both sides read: the client's
 * {@code ChatTab} writes and parses ids in full, and the server reads
 * only which channel an id names, for the command answers it keeps
 * under the tab they were typed in. A plain channel's id is the channel
 * id, followed by {@code |} and a conversation or an identity; a whisper
 * is {@code whisper:Name...} and an NPC conversation {@code npc:Name},
 * which name no channel. No Minecraft dependency.
 */
public final class ChatTabIds {
    public static final String WHISPER_PREFIX = "whisper:";
    public static final String NPC_PREFIX = "npc:";
    /** Between a tab id's channel and whatever qualifies it. */
    public static final char SEPARATOR = '|';

    private ChatTabIds() {}

    /**
     * The channel a tab id names, or null for a whisper, an NPC
     * conversation or an id naming no channel this build has.
     */
    public static ChatChannel channelOf(String tabId) {
        if (tabId == null) {
            return null;
        }
        String trimmed = tabId.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith(WHISPER_PREFIX) || lower.startsWith(NPC_PREFIX)) {
            return null;
        }
        int separator = trimmed.indexOf(SEPARATOR);
        String channelPart = separator < 0 ? trimmed
                : trimmed.substring(0, separator);
        ChatChannel channel = ChatChannel.fromId(channelPart);
        return channel == ChatChannel.WHISPER ? null : channel;
    }
}
