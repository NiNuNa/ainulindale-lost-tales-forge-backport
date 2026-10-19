package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMentions;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.ChatPresentationMode;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.compat.discord.DiscordMemberDirectory;
import com.ninuna.losttales.compat.discord.LostTalesDiscordBridge;
import com.ninuna.losttales.network.packet.LostTalesChatMembersPacket;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Whom a line's {@code @names} reach: the members of the conversation it
 * is said in, as its sender's member list shows them — here or absent, a
 * player's account or any of their characters, or a Discord member — each
 * by the whole name after the at-sign ({@link ChatMentions#reached}).
 * The line keeps them as its named players, so every client shows the
 * mention, pings the person it names and opens their card from the same
 * record, whoever is online. The Server is nobody to mention. Which roles
 * a player's line calls is read here too ({@link #rolesOf}).
 */
final class ChatMentionTargets {
    private ChatMentionTargets() {}

    /**
     * Whom {@code message}, said by {@code sender} in {@code channel}'s
     * conversation {@code scope}, mentions: a whisper reaches its two
     * people, as each is in it — the sender as {@code senderAs}, the
     * other end as {@code whisperPartner} — any other conversation its
     * members.
     */
    static List<ChatNamedPlayer> of(EntityPlayerMP sender, ChatNamedPlayer senderAs,
                                    ChatChannel channel, String scope,
                                    ChatNamedPlayer whisperPartner,
                                    String message) {
        if (sender == null || channel == null || message == null
                || message.indexOf('@') < 0) {
            return Collections.emptyList();
        }
        List<ChatNamedPlayer> candidates;
        if (channel == ChatChannel.WHISPER) {
            candidates = new ArrayList<ChatNamedPlayer>(2);
            candidates.add(senderAs);
            if (whisperPartner != null) {
                candidates.add(whisperPartner);
            }
        } else {
            candidates = candidatesOf(
                    ChatMemberDirectory.answerFor(sender, channel, scope).members);
        }
        return ChatMentions.reached(message, candidates,
                ChatNamedPlayer.MAX_PER_LINE);
    }

    /**
     * The mentionable roles {@code message} calls, by id, read by every
     * name the server knows each by ({@link ChatAccountRole#serverMentionNames}):
     * what a client pings its player by when they hold one, whatever
     * language the writer typed the name in.
     */
    static List<String> rolesOf(String message) {
        if (message == null || message.indexOf('@') < 0) {
            return Collections.emptyList();
        }
        Map<String, List<String>> names = new LinkedHashMap<String, List<String>>();
        for (ChatAccountRole role : ChatRoleCatalog.server().roles()) {
            if (role.isMentionable()) {
                names.put(role.getId(), role.serverMentionNames());
            }
        }
        return ChatMentions.reachedRoles(message, names,
                ChatAccountRole.MAX_MENTIONS_PER_LINE);
    }

    /**
     * Whom a Discord member's line in {@code channel} mentions: the same
     * members a player's line there reaches (CHAT-82), as the member list
     * shows them to a player the line reaches, every identity in use and
     * those gone a while included, with the Discord members who can see a
     * Discord channel linked to it; with nobody here to read it, those
     * Discord members alone.
     */
    static List<ChatNamedPlayer> ofDiscordLine(ChatChannel channel,
                                               String factionScope,
                                               Collection<EntityPlayerMP> reached,
                                               String message) {
        if (channel == null || message == null || message.indexOf('@') < 0) {
            return Collections.emptyList();
        }
        if (reached != null) {
            for (EntityPlayerMP player : reached) {
                if (player != null && player.getUniqueID() != null) {
                    return ChatLinePipeline.membersNamed(player, channel,
                            factionScope == null ? "" : factionScope, message, false);
                }
            }
        }
        List<ChatNamedPlayer> candidates = new ArrayList<ChatNamedPlayer>();
        for (DiscordMemberDirectory.Seen seen : LostTalesDiscordBridge.getInstance()
                .membersSeeing(channel, factionScope == null ? "" : factionScope)) {
            UUID senderId = LostTalesChatMessagePacket.discordSenderId(seen.userId);
            if (!LostTalesChatMessagePacket.DISCORD_SENDER_ID.equals(senderId)) {
                candidates.add(ChatNamedPlayer.account(senderId, seen.name));
            }
        }
        return ChatMentions.reached(message, candidates,
                ChatNamedPlayer.MAX_PER_LINE);
    }

    /**
     * The people a member list shows, as a mention may name them:
     * everyone but the Server and an NPC, in the list's own order, so of
     * two sharing a name the one here comes first.
     */
    static List<ChatNamedPlayer> candidatesOf(
            List<LostTalesChatMembersPacket.Member> members) {
        List<ChatNamedPlayer> candidates = new ArrayList<ChatNamedPlayer>(
                members.size());
        for (LostTalesChatMembersPacket.Member member : members) {
            if (member == null || member.isNpc()
                    || member.getPlayerId() == null
                    || LostTalesChatMessagePacket.isSystemSender(member.getPlayerId())) {
                continue;
            }
            ChatNamedPlayer named = new ChatNamedPlayer(member.getPlayerId(),
                    member.getAccount(), member.getCharacterId(),
                    member.getName(), member.getSkinId());
            if (named.isValid()) {
                candidates.add(named);
            }
        }
        return candidates;
    }
}
