package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelSuggester;
import com.ninuna.losttales.chat.ChatMessageIds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Whether a Discord copy of a game message is live right now: the one
 * rule every action on a copy asks, on whichever thread it runs. A copy
 * is live while the message's game channel is open to the bridge, as
 * {@link DiscordBridgePolicy} decides for a new line, and — for the
 * Faction channel, its faction — is linked, by a link in force now, to
 * the Discord channel the copy is in, in the direction the action
 * crosses:
 *
 * <ul>
 * <li>To Discord — the bot's reaction put on or taken off, a webhook
 * edit or deletion, a reply header's or a jump link's pointer at the
 * copy — needs a link that posts into that channel.</li>
 * <li>From Discord — a member's reaction, edit or deletion, a Discord
 * reply quoting the message, a Discord jump link to it — needs the
 * link that reads that channel to be one of the message's own game
 * channel.</li>
 * </ul>
 *
 * A link that only reads carries nothing to Discord: the bot never
 * reacts in its channel and no game line links into it. A copy that is
 * not live is left alone on both sides and its link is kept, so link
 * the pair again, or lifting a read gate on the channel, brings
 * everything back. Links are matched by their
 * game channel and their Discord channel, never by id: an id's ordinal
 * moves when the config is reordered. A Discord member's own line is a
 * copy in the channel it was read from. The server's announcements are
 * lines of their channel like any other — a join of OOC, a death of
 * Global — and their embeds live in that channel's Discord channels.
 */
final class DiscordCopyLiveness {
    /** How a copy's destination names the Discord channel it is in. */
    static final String CHANNEL_PREFIX = DiscordMessageLinkNbtCodec.CHANNEL_PREFIX;

    /** Which way an action on a copy crosses the bridge. */
    enum Crossing { TO_DISCORD, FROM_DISCORD }

    /** What the bridge knows of its webhooks. */
    interface Webhooks {
        /** The Discord channel the webhook posts into; empty when not known. */
        String channelOf(String webhookUrl);

        /** Whether posting through the webhook is off for the session. */
        boolean refused(String webhookUrl);
    }

    /** Where the chat says a message was said; the chat history, in the bridge. */
    interface Places {
        /** The channel the message was said in; null for one not kept. */
        ChatChannel channelOf(long messageId);

        /** The faction a Faction line was said to; empty for any other. */
        String factionScopeOf(long messageId);
    }

    /**
     * Webhooks nothing is known of: a sending link is placed by the
     * Discord channel it names alone. What the server thread asks with,
     * since word from Discord needs a reading link, which always
     * names its channel.
     */
    static final Webhooks NO_WEBHOOKS = new Webhooks() {
        @Override
        public String channelOf(String webhookUrl) {
            return "";
        }

        @Override
        public boolean refused(String webhookUrl) {
            return false;
        }
    };

    private DiscordCopyLiveness() {}

    /**
     * Whether a copy of a message said in {@code channel} (for Faction
     * chat, to {@code factionScope}) is live for {@code crossing}. The
     * copy is in {@code discordChannelId}; a copy whose channel was never
     * learnt is known by {@code webhookUrl}, the webhook it went through,
     * which only an action to Discord can go by. False for a message of
     * no known channel, and for one whose channel is closed to the bridge.
     */
    static boolean isLive(DiscordChannelLinks channelLinks, ChatChannel channel,
                          String factionScope, String discordChannelId,
                          String webhookUrl, Crossing crossing,
                          Webhooks webhooks) {
        if (channelLinks == null || channel == null || crossing == null
                || !DiscordBridgePolicy.isOpenToTheBridge(channel)) {
            return false;
        }
        String copyChannel = discordChannelId == null ? "" : discordChannelId;
        String copyWebhook = webhookUrl == null ? "" : webhookUrl;
        Webhooks known = webhooks == null ? NO_WEBHOOKS : webhooks;
        for (DiscordChannelLink channelLink : channelLinks.forGame(channel,
                factionScope)) {
            if (crossing == Crossing.FROM_DISCORD) {
                if (channelLink.readsFromDiscord() && copyChannel.length() > 0
                        && copyChannel.equals(channelLink.getDiscordChannelId())) {
                    return true;
                }
            } else if (postsInto(channelLink, copyChannel, copyWebhook, known)) {
                return true;
            }
        }
        return false;
    }

    /** The same, for a copy the links hold. */
    static boolean isLive(DiscordChannelLinks channelLinks, ChatChannel channel,
                          String factionScope, DiscordMessageLinks.Copy copy,
                          Crossing crossing, Webhooks webhooks) {
        return copy != null && isLive(channelLinks, channel, factionScope,
                channelIdOf(copy.destination), copy.webhookUrl, crossing,
                webhooks);
    }

    /**
     * The copies of a message that are live for {@code crossing}, in the
     * order they were made; empty when none is.
     */
    static List<DiscordMessageLinks.Copy> liveCopies(DiscordMessageLinks links,
                                                     DiscordChannelLinks channelLinks,
                                                     long messageId,
                                                     ChatChannel channel,
                                                     String factionScope,
                                                     Crossing crossing,
                                                     Webhooks webhooks) {
        if (links == null || channel == null) {
            return Collections.emptyList();
        }
        List<DiscordMessageLinks.Copy> live = new ArrayList<DiscordMessageLinks.Copy>(2);
        for (DiscordMessageLinks.Copy copy : links.copiesOf(messageId)) {
            if (isLive(channelLinks, channel, factionScope, copy, crossing,
                    webhooks)) {
                live.add(copy);
            }
        }
        return live;
    }

    /**
     * The game message a word from Discord names — a member's reaction,
     * edit or deletion, the message a Discord reply answers — or
     * {@link ChatMessageIds#NONE}: only a message the bridge links, said
     * in the game channel that {@code discordChannelId}, where the word
     * came from, is read into now.
     */
    static long inboundTarget(DiscordMessageLinks links,
                              DiscordChannelLinks channelLinks, Places places,
                              String discordId, String discordChannelId) {
        if (links == null || places == null) {
            return ChatMessageIds.NONE;
        }
        long target = links.messageIdOf(discordId);
        if (target == ChatMessageIds.NONE) {
            return ChatMessageIds.NONE;
        }
        return isLive(channelLinks, places.channelOf(target),
                places.factionScopeOf(target), discordChannelId, "",
                Crossing.FROM_DISCORD, NO_WEBHOOKS)
                ? target : ChatMessageIds.NONE;
    }

    /**
     * The message a Discord reply from {@code discordChannelId} may
     * quote, or {@link ChatMessageIds#NONE}: a message as
     * {@link #inboundTarget} finds it, with a copy in that very channel.
     */
    static long quotedBy(DiscordMessageLinks links, DiscordChannelLinks channelLinks,
                         Places places, String referencedDiscordId,
                         String discordChannelId) {
        long target = inboundTarget(links, channelLinks, places, referencedDiscordId,
                discordChannelId);
        return target != ChatMessageIds.NONE
                && links.hasCopyIn(target, CHANNEL_PREFIX + discordChannelId)
                ? target : ChatMessageIds.NONE;
    }

    /**
     * The game's {@code #<code name>/<id>} for a Discord jump link to a
     * message in {@code discordChannelId}, or empty: the message is one
     * {@link #inboundTarget} finds, the linked Discord message is its
     * copy in that very channel, and the link is named in the message's
     * own channel. The link's channel is whatever its author typed, so
     * the linked copy must be there for the link to count.
     */
    static String gameLink(DiscordMessageLinks links, DiscordChannelLinks channelLinks,
                           Places places, String discordChannelId,
                           String discordMessageId) {
        long target = inboundTarget(links, channelLinks, places, discordMessageId,
                discordChannelId);
        if (!ChatMessageIds.isServerId(target) || !discordMessageId.equals(
                links.discordIdOf(target, CHANNEL_PREFIX + discordChannelId))) {
            return "";
        }
        String link = ChatChannelSuggester.messageLink(places.channelOf(target),
                places.factionScopeOf(target), target);
        return link == null ? "" : link;
    }

    /**
     * The copy a game jump link to a message points at, for a post going
     * to {@code ownDestination}: the message's copy there when it is live
     * to Discord, else its first live copy, else null.
     */
    static DiscordMessageLinks.Copy jumpTarget(DiscordMessageLinks links,
                                               DiscordChannelLinks channelLinks,
                                               Places places, long messageId,
                                               String ownDestination,
                                               Webhooks webhooks) {
        if (places == null) {
            return null;
        }
        List<DiscordMessageLinks.Copy> live = liveCopies(links, channelLinks, messageId,
                places.channelOf(messageId), places.factionScopeOf(messageId),
                Crossing.TO_DISCORD, webhooks);
        if (live.isEmpty()) {
            return null;
        }
        for (DiscordMessageLinks.Copy copy : live) {
            if (copy.destination.equals(ownDestination)) {
                return copy;
            }
        }
        return live.get(0);
    }

    /**
     * The webhook an in-game edit or deletion of a copy goes through, or
     * empty for none. Only the webhook that made a post may change it,
     * and only through a link of the message's channel that posts
     * into the copy's channel now. A copy posted in this server run knows
     * its webhook; a copy brought back from the save does not, and uses
     * the webhook of such a link that Discord says posts into the
     * copy's channel. A Discord member's own line was made by no webhook.
     * Nothing goes through while the channel is closed to the bridge.
     */
    static String correctionWebhook(DiscordChannelLinks channelLinks,
                                    ChatChannel channel, String factionScope,
                                    DiscordMessageLinks.Copy copy,
                                    Webhooks webhooks) {
        if (channelLinks == null || channel == null || copy == null
                || (copy.channelLinkId.length() == 0 && copy.webhookUrl.length() == 0)
                || !DiscordBridgePolicy.isOpenToTheBridge(channel)) {
            return "";
        }
        String copyChannel = channelIdOf(copy.destination);
        Webhooks known = webhooks == null ? NO_WEBHOOKS : webhooks;
        for (DiscordChannelLink channelLink : channelLinks.forGame(channel,
                factionScope)) {
            if (!postsInto(channelLink, copyChannel, copy.webhookUrl, known)) {
                continue;
            }
            String webhook = channelLink.getWebhookUrl();
            boolean made = copy.webhookUrl.length() > 0
                    ? copy.webhookUrl.equals(webhook)
                    : copyChannel.length() > 0
                            && copyChannel.equals(known.channelOf(webhook));
            if (made) {
                return webhook;
            }
        }
        return "";
    }

    /**
     * Whether a link posts into the copy's channel now: it posts,
     * Discord has not refused its webhook, and the copy is in the channel
     * the link names or the one its webhook posts into. When the
     * copy's channel or the webhook's is not known, the copy is placed
     * by the webhook it went through.
     */
    private static boolean postsInto(DiscordChannelLink channelLink,
                                     String copyChannel, String copyWebhook,
                                     Webhooks webhooks) {
        if (!channelLink.sendsToDiscord() || webhooks.refused(channelLink.getWebhookUrl())) {
            return false;
        }
        if (copyChannel.length() > 0) {
            if (copyChannel.equals(channelLink.getDiscordChannelId())) {
                return true;
            }
            String posts = webhooks.channelOf(channelLink.getWebhookUrl());
            if (posts != null && posts.length() > 0) {
                return copyChannel.equals(posts);
            }
        }
        return copyWebhook.length() > 0
                && copyWebhook.equals(channelLink.getWebhookUrl());
    }

    /** The Discord channel id a destination names, or empty for any other. */
    static String channelIdOf(String destination) {
        return destination != null && destination.startsWith(CHANNEL_PREFIX)
                ? destination.substring(CHANNEL_PREFIX.length()) : "";
    }
}
