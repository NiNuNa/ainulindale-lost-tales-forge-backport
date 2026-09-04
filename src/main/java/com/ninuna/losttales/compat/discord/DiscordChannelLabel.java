package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;

/**
 * The heading a game line's Discord card carries: a mark and the
 * channel's name — a globe before Global, a speech balloon before OOC
 * — or, for Faction chat, the shield before the faction's own name, so
 * a Discord channel that several game channels share still says at a
 * glance where each line was spoken. The mark is a Unicode emoji Discord
 * renders itself; a channel that never crosses the bridge has its plain
 * name here and no mark.
 */
public final class DiscordChannelLabel {

    private DiscordChannelLabel() {
    }

    /**
     * The label of a line spoken in {@code channel}; {@code factionName}
     * is the sender's faction, read for Faction chat only and falling
     * back to the channel's own name when unknown.
     */
    public static String of(ChatChannel channel, String factionName) {
        if (channel == null) {
            return "";
        }
        String faction = factionName == null ? "" : factionName.trim();
        String name = channel == ChatChannel.FACTION && faction.length() > 0
                ? faction : channel.getDisplayName();
        String mark = markOf(channel);
        return mark.length() == 0 ? name : mark + " " + name;
    }

    /** The emoji before a channel's name; empty for a channel without one. */
    static String markOf(ChatChannel channel) {
        switch (channel) {
            case ALL:
                return "\uD83C\uDF0D";
            case PROXIMITY:
                return "\uD83D\uDCCD";
            case FACTION:
                return "\uD83D\uDEE1\uFE0F";
            case OOC:
                return "\uD83D\uDCAC";
            case ADMIN:
                return "\uD83D\uDEE0\uFE0F";
            case DISCORD:
                return "\uD83D\uDD17";
            default:
                return "";
        }
    }
}
