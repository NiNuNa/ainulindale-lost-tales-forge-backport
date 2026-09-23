package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMentions;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.ChatPresentationMode;
import com.ninuna.losttales.compat.discord.DiscordMemberDirectory;
import com.ninuna.losttales.compat.discord.LostTalesDiscordBridge;
import com.ninuna.losttales.network.packet.LostTalesChatMembersPacket;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Whom a line's {@code @names} reach: the members of the conversation it
 * is said in, as its sender's member list shows them — here or absent, a
 * player's account or any of their characters, or a Discord member — each
 * by the whole name after the at-sign ({@link ChatMentions#reached}).
 * The line keeps them as its named players, so every client shows the
 * mention, pings the person it names and opens their card from the same
 * record, whoever is online. The Server is nobody to mention.
 */
final class ChatMentionTargets {
    private ChatMentionTargets() {}

    /**
     * Whom {@code message}, said by {@code sender} in {@code channel},
     * mentions: a whisper reaches its two people, any other conversation
     * its members.
     */
    static List<ChatNamedPlayer> of(EntityPlayerMP sender, ChatChannel channel,
                                    EntityPlayerMP whisperTarget,
                                    String message) {
        if (sender == null || channel == null || message == null
                || message.indexOf('@') < 0) {
            return Collections.emptyList();
        }
        List<ChatNamedPlayer> candidates;
        if (channel == ChatChannel.WHISPER) {
            candidates = new ArrayList<ChatNamedPlayer>(2);
            candidates.add(LostTalesServerBroadcastHook.namedPlayer(sender));
            if (whisperTarget != null) {
                candidates.add(LostTalesServerBroadcastHook.namedPlayer(whisperTarget));
            }
        } else {
            candidates = candidatesOf(
                    ChatMemberDirectory.answerFor(sender, channel).members);
        }
        return ChatMentions.reached(message, candidates,
                ChatNamedPlayer.MAX_PER_LINE);
    }

    /**
     * Whom a Discord member's line in {@code channel} mentions: the
     * players it reaches, as the channel shows them, and the Discord
     * members who can see a Discord channel linked to it.
     */
    static List<ChatNamedPlayer> ofDiscordLine(ChatChannel channel,
                                               String factionScope,
                                               Collection<EntityPlayerMP> reached,
                                               String message) {
        if (channel == null || message == null || message.indexOf('@') < 0) {
            return Collections.emptyList();
        }
        boolean asAccounts = channel.getPresentation()
                == ChatPresentationMode.OUT_OF_CHARACTER;
        List<ChatNamedPlayer> candidates = new ArrayList<ChatNamedPlayer>();
        if (reached != null) {
            for (EntityPlayerMP player : reached) {
                if (player != null && player.getUniqueID() != null) {
                    candidates.add(asAccounts
                            ? LostTalesServerBroadcastHook.namedAccount(player)
                            : LostTalesServerBroadcastHook.namedPlayer(player));
                }
            }
        }
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
