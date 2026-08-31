package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatIdentityType;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.ChatRecipientRule;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareReference;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.chat.share.ChatShowcase;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.server.CharacterActiveResolver;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.compat.discord.DiscordAvatarUrl;
import com.ninuna.losttales.compat.discord.DiscordMessageSanitizer;
import com.ninuna.losttales.compat.discord.LostTalesDiscordBridge;
import com.ninuna.losttales.compat.lotr.LostTalesWaystonePermissionPolicy;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerStorage;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerVisibilityPolicy;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatAccessPacket;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import com.ninuna.losttales.network.packet.LostTalesChatTypingSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatUpdatePacket;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyMember;
import com.ninuna.losttales.party.server.PartyService;
import com.ninuna.losttales.world.map.waypoint.LostTalesWaypointFastTravelPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;

/** Authoritative recipient resolution and presentation snapshot for player chat. */
public final class LostTalesChatService {
    private LostTalesChatService() {}

    public static void send(EntityPlayerMP sender,
                            ChatChannel channel, String message) {
        send(sender, channel, message, null);
    }

    /**
     * Validates and distributes one message. {@code references} pair, in
     * order, with the share tokens found in the message; an item slot is
     * re-read from the sender's live inventory and a marker id from world
     * data under the sender's own visibility, and only a match whose real
     * name agrees with the token is attached. Tokens that cannot be
     * verified are delivered as the literal text the sender typed.
     */
    public static void send(EntityPlayerMP sender,
                            ChatChannel channel, String message,
                            List<ChatShareReference> references) {
        send(sender, channel, message, references, "");
    }

    /**
     * As above, with the account name a whisper is for. A whisper goes
     * to the sender and that one online player, each told who the other
     * party is; an unknown name is refused with a notice.
     */
    public static void send(EntityPlayerMP sender,
                            ChatChannel channel, String message,
                            List<ChatShareReference> references,
                            String target) {
        send(sender, channel, message, references, target,
                LostTalesChatSendPacket.APPEARANCE_DEFAULT, null);
    }

    /**
     * As above, with the identity the sender asked to speak as: the
     * channel's default, the account, or one character of the sender's
     * own roster — never anyone else's; an id the roster does not hold
     * is refused with a notice. The appearance decides how the line is
     * signed and nothing else: recipient resolution still follows the
     * sender's <em>active</em> character, since faction and party are
     * membership, not presentation.
     */
    public static void send(EntityPlayerMP sender,
                            ChatChannel channel, String message,
                            List<ChatShareReference> references,
                            String target, int appearanceKind,
                            UUID appearanceCharacterId) {
        send(sender, channel, message, references, target, appearanceKind,
                appearanceCharacterId, ChatMessageIds.NONE);
    }

    /**
     * As above, with the message this one replies to. The reference is
     * a request: it is honoured only while the message is still within
     * the log's reach <em>and</em> was sent to this sender, so naming an
     * id can never quote back a message nobody showed them. A reference
     * that fails either test is dropped with a notice and the message
     * goes out as an ordinary one.
     */
    public static void send(EntityPlayerMP sender,
                            ChatChannel channel, String message,
                            List<ChatShareReference> references,
                            String target, int appearanceKind,
                            UUID appearanceCharacterId,
                            long replyToMessageId) {
        send(sender, channel, message, references, target, appearanceKind,
                appearanceCharacterId, replyToMessageId, "");
    }

    /**
     * As above, with the identity of the target a whisper is addressed
     * to: a conversation is with a person as they present themselves,
     * not with the account behind them, so one player's characters are
     * separate threads. The account is still who the line is routed to,
     * and it is reached whatever they happen to be playing — a
     * conversation does not stop working because someone switched.
     *
     * <p>The identity is a request: an empty one is the account's own
     * conversation, and any other must be a character that account
     * really holds, or the whisper is refused.</p>
     */
    public static void send(EntityPlayerMP sender,
                            ChatChannel channel, String message,
                            List<ChatShareReference> references,
                            String target, int appearanceKind,
                            UUID appearanceCharacterId,
                            long replyToMessageId, String targetIdentity) {
        send(sender, channel, message, references, target, appearanceKind,
                appearanceCharacterId, replyToMessageId, targetIdentity, 0L);
    }

    /**
     * As above, with the sender's own name for the message. A client
     * that shows its messages the moment they are typed sends one so it
     * can tell which line the delivered copy replaces; it is handed back
     * to the sender alone and means nothing to anyone else.
     */
    public static void send(EntityPlayerMP sender,
                            ChatChannel channel, String message,
                            List<ChatShareReference> references,
                            String target, int appearanceKind,
                            UUID appearanceCharacterId,
                            long replyToMessageId, String targetIdentity,
                            long echoNonce) {
        if (sender == null || sender.worldObj == null
                || sender.worldObj.isRemote || channel == null
                || !ChatMessageValidator.isValid(message)) {
            return;
        }
        EntityPlayerMP whisperTarget = null;
        String whisperIdentity = "";
        if (channel.getRecipientRule() == ChatRecipientRule.WHISPER) {
            whisperTarget = findOnlinePlayer(target);
            if (whisperTarget == null || whisperTarget == sender) {
                sender.addChatMessage(new ChatComponentTranslation(
                        whisperTarget == sender
                                ? "chat.losttales.whisper.self"
                                : "chat.losttales.whisper.unavailable"));
                return;
            }
            whisperIdentity = resolveIdentity(whisperTarget,
                    targetIdentity);
            if (whisperIdentity.length() == 0) {
                // Named an identity that account does not hold: nothing
                // to have a conversation with.
                sender.addChatMessage(new ChatComponentTranslation(
                        "chat.losttales.whisper.unavailable"));
                return;
            }
        }

        RoleplayCharacter character = CharacterActiveResolver.get(sender);
        // The identity the line is signed with. The old rule — a
        // character channel refuses a sender without a character — is
        // gone: with no character to default to, the line simply wears
        // the account, and any owned character may speak anywhere.
        RoleplayCharacter appearance;
        if (appearanceKind == LostTalesChatSendPacket.APPEARANCE_ACCOUNT) {
            appearance = null;
        } else if (appearanceKind
                == LostTalesChatSendPacket.APPEARANCE_CHARACTER) {
            appearance = ownedCharacter(sender, appearanceCharacterId);
            if (appearance == null) {
                sender.addChatMessage(new ChatComponentTranslation(
                        "chat.losttales.appearance.unavailable"));
                return;
            }
        } else {
            appearance = channel.getIdentityType()
                    == ChatIdentityType.CHARACTER ? character : null;
        }
        Party party = null;
        String factionId = character == null ? ""
                : LotrCharacterAdapter.normalizeFactionId(
                        character.getStartingFactionId());
        if (channel.getRecipientRule() == ChatRecipientRule.PARTY) {
            party = PartyService.getInstance()
                    .getPartyForActiveCharacter(sender);
            if (party == null || character == null
                    || !party.containsMember(character.getCharacterId())) {
                sender.addChatMessage(new ChatComponentTranslation(
                        "chat.losttales.channel.party_unavailable"));
                return;
            }
        } else if (channel.getRecipientRule()
                == ChatRecipientRule.FACTION && factionId.length() == 0) {
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.channel.faction_unavailable"));
            return;
        } else if (channel.getRecipientRule()
                == ChatRecipientRule.OPERATORS
                && !LostTalesWaystonePermissionPolicy.isOperator(sender)) {
            // The client only offers the tab while it believes the player
            // is an operator; tell it again so a revoked op loses the tab.
            sendAccess(sender);
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.channel.admin_unavailable"));
            return;
        } else if (channel == ChatChannel.DISCORD
                && !LostTalesConfig.discordEnabled) {
            sendAccess(sender);
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.channel.discord_unavailable"));
            return;
        }

        String accountName = sender.getGameProfile() == null
                ? sender.getCommandSenderName()
                : sender.getGameProfile().getName();
        // Account lines say which roles their sender holds and take the
        // primary role's colour for the name; role-play lines belong to
        // the character, not the account, and carry neither. What makes
        // a line an account line is the appearance it wears, not the
        // channel it goes to.
        boolean accountLine = appearance == null;
        String identityName = accountLine ? accountName
                : characterNameOrFallback(appearance, accountName);
        LostTalesChatPresentationResolver.Presentation presentation =
                LostTalesChatPresentationResolver.resolve(sender, appearance);
        List<ChatShowcase> showcases =
                resolveShowcases(sender, message, references);
        int roles = accountLine ? ChatAccountRoleResolver.resolve(sender) : 0;
        int ivory = LostTalesColors.rgb(LostTalesColors.HUD_LABEL);
        ChatReplyReference reply = ChatMessageIds.NONE == replyToMessageId
                ? ChatReplyReference.NONE
                : ChatMessageLog.quoteFor(replyToMessageId,
                        sender.getUniqueID());
        if (replyToMessageId != ChatMessageIds.NONE && !reply.exists()) {
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.reply.unavailable"));
        }
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                channel, sender.getUniqueID(), identityName,
                accountName,
                accountLine ? "" : presentation.title,
                accountLine ? ivory : presentation.titleColor,
                accountLine ? ChatAccountRole.nameColor(roles)
                        : presentation.nameColor,
                message, System.currentTimeMillis(),
                accountLine ? "" : appearance.getSkinId(),
                showcases,
                accountLine ? "" : presentation.factionName,
                // A whisper names its partner from the start: the packet
                // refuses a partner-less whisper, so the sender's copy is
                // built with the target's name and the target's copy is
                // derived from it below.
                whisperTarget == null ? ""
                        : whisperTarget.getCommandSenderName(),
                roles, accountLine,
                // One id for the message, not one per copy: a whisper is
                // the same message to both parties, and anything naming
                // it later has to name it the same to each of them.
                ChatMessageIdAllocator.next(), reply,
                // The sender's own copy is filed under the identity they
                // addressed; the target's is filed under theirs, below.
                whisperIdentity,
                // Carried on the sender's copy only; stripped below.
                echoNonce);

        FMLLog.info("[losttales/chat/%s] <%s (%s)> %s%s%s",
                channel.getId(), identityName, accountName, message,
                whisperTarget == null ? ""
                        : " -> " + whisperTarget.getCommandSenderName(),
                showcases.isEmpty() ? ""
                        : " [shared: " + showcases.size() + "]");

        if (whisperTarget != null) {
            // Each side is told who the other party is.
            LostTalesNetworkHandler.CHANNEL.sendTo(packet, sender);
            LostTalesNetworkHandler.CHANNEL.sendTo(withPartner(
                    packet.withoutEcho(), accountName, identityName),
                    whisperTarget);
            ChatMessageLog.record(packet.getMessageId(),
                    sender.getUniqueID(), identityName,
                    message, Arrays.asList(sender.getUniqueID(),
                            whisperTarget.getUniqueID()));
            return;
        }
        List<EntityPlayerMP> recipients = resolveRecipients(
                sender, channel, party, factionId);
        List<UUID> recipientIds = new ArrayList<UUID>(recipients.size());
        // Everyone but the sender is sent the line without the sender's
        // private name for it.
        LostTalesChatMessagePacket shared = packet.withoutEcho();
        for (EntityPlayerMP recipient : recipients) {
            LostTalesNetworkHandler.CHANNEL.sendTo(
                    recipient == sender ? packet : shared, recipient);
            recipientIds.add(recipient.getUniqueID());
        }
        // Recorded from the list the message actually went to, so a
        // reply to it can be checked against who was sent it rather
        // than against who would be sent one now.
        ChatMessageLog.record(packet.getMessageId(), sender.getUniqueID(),
                identityName, message, recipientIds);
        if (channel == ChatChannel.DISCORD) {
            // The bridge posts the line under the sender's name with their
            // head as the picture; emoji shortcodes go as the Unicode
            // emoji Discord renders, share tokens as the text they were
            // typed as.
            LostTalesDiscordBridge.getInstance().relay(identityName,
                    DiscordAvatarUrl.of(LostTalesConfig.discordAvatarUrlTemplate,
                            accountName, sender.getUniqueID()),
                    DiscordMessageSanitizer.outbound(message));
        }
    }

    /**
     * A message from Discord, delivered by the bridge on the server
     * thread: it enters the Discord channel for everyone online under
     * the Discord display name, with a fixed sender id no account owns,
     * and is never posted back to Discord. The bridge has already
     * sanitised and bounded the text.
     */
    public static void sendFromDiscord(String displayName, String message) {
        MinecraftServer server = MinecraftServer.getServer();
        if (!LostTalesConfig.discordEnabled || displayName == null
                || displayName.length() == 0
                || !ChatMessageValidator.isValid(message) || server == null
                || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return;
        }
        int ivory = LostTalesColors.rgb(LostTalesColors.HUD_LABEL);
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                ChatChannel.DISCORD,
                LostTalesChatMessagePacket.DISCORD_SENDER_ID, displayName,
                displayName, "", ivory, ivory, message,
                System.currentTimeMillis(), "", null, "", "", 0, true,
                ChatMessageIdAllocator.next());
        FMLLog.info("[losttales/chat/discord] <%s (discord)> %s", displayName,
                message);
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online =
                server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP recipient : online) {
            if (recipient != null) {
                LostTalesNetworkHandler.CHANNEL.sendTo(packet, recipient);
            }
        }
    }

    /**
     * Relays that the player is, or has stopped, typing into a channel
     * to everyone who would receive a message sent there now — never to
     * the sender — under the very same checks a message passes, but
     * silently: presence earns no notices. Off on the server, nothing is
     * relayed at all.
     */
    public static void typing(EntityPlayerMP sender, ChatChannel channel,
                              String target, boolean typing) {
        if (!LostTalesConfig.chatTypingIndicators || sender == null
                || sender.worldObj == null || sender.worldObj.isRemote
                || channel == null
                || channel.getRecipientRule() == ChatRecipientRule.SELF) {
            return;
        }
        RoleplayCharacter character = CharacterActiveResolver.get(sender);
        String accountName = sender.getGameProfile() == null
                ? sender.getCommandSenderName()
                : sender.getGameProfile().getName();
        // Presence carries the channel's default identity: the typing
        // packet does not say which appearance the message will wear,
        // and a name shown a moment early is presentation, not fact.
        String identityName = channel.getIdentityType()
                == ChatIdentityType.ACCOUNT || character == null
                ? accountName
                : characterNameOrFallback(character, accountName);
        if (channel.getRecipientRule() == ChatRecipientRule.WHISPER) {
            EntityPlayerMP whisperTarget = findOnlinePlayer(target);
            if (whisperTarget != null && whisperTarget != sender) {
                LostTalesNetworkHandler.CHANNEL.sendTo(
                        new LostTalesChatTypingSyncPacket(channel,
                                accountName, identityName, typing),
                        whisperTarget);
            }
            return;
        }
        Party party = null;
        String factionId = character == null ? ""
                : LotrCharacterAdapter.normalizeFactionId(
                        character.getStartingFactionId());
        if (channel.getRecipientRule() == ChatRecipientRule.PARTY) {
            party = PartyService.getInstance()
                    .getPartyForActiveCharacter(sender);
            if (party == null || character == null
                    || !party.containsMember(character.getCharacterId())) {
                return;
            }
        } else if (channel.getRecipientRule() == ChatRecipientRule.FACTION
                && factionId.length() == 0) {
            return;
        } else if (channel.getRecipientRule() == ChatRecipientRule.OPERATORS
                && !LostTalesWaystonePermissionPolicy.isOperator(sender)) {
            return;
        }
        if (channel == ChatChannel.DISCORD && typing) {
            // The bridged channel has readers on the other side too, and
            // Discord shows its own indicator when the bot says it is
            // typing. Nothing but presence crosses.
            LostTalesDiscordBridge.getInstance().relayTyping();
        }
        LostTalesChatTypingSyncPacket packet = new LostTalesChatTypingSyncPacket(
                channel, "", identityName, typing);
        for (EntityPlayerMP recipient : resolveRecipients(
                sender, channel, party, factionId)) {
            if (recipient != sender) {
                LostTalesNetworkHandler.CHANNEL.sendTo(packet, recipient);
            }
        }
    }

    /**
     * Rewrites one of {@code editor}'s own messages and tells everyone
     * who was sent it. Silently does nothing when the message is not
     * theirs or has fallen out of the log's reach — the client is told
     * nothing it could learn from, since a refusal that distinguished
     * "not yours" from "no such message" would answer questions about
     * messages the asker never saw.
     *
     * <p>The new text passes the same validator a fresh message does,
     * so an edit is not a way around what a send would have refused.
     * A line already carried to Discord is not re-posted: the bridge
     * writes through a webhook and does not keep what it would need to
     * go back and change it.</p>
     */
    public static void edit(EntityPlayerMP editor, long messageId,
                            String message) {
        if (editor == null || editor.worldObj == null
                || editor.worldObj.isRemote
                || !ChatMessageValidator.isValid(message)) {
            return;
        }
        Set<UUID> recipients = ChatMessageLog.applyEdit(messageId,
                editor.getUniqueID(), message);
        if (recipients == null) {
            return;
        }
        FMLLog.info("[losttales/chat/edit] <%s> %s",
                editor.getCommandSenderName(), message);
        tellRecipients(recipients,
                LostTalesChatUpdatePacket.edited(messageId, message));
    }

    /**
     * Takes one of {@code remover}'s own messages back, on the same
     * terms as {@link #edit}. Everyone who was sent it is told to drop
     * it; nobody else hears that it ever existed.
     */
    public static void delete(EntityPlayerMP remover, long messageId) {
        if (remover == null || remover.worldObj == null
                || remover.worldObj.isRemote) {
            return;
        }
        Set<UUID> recipients = ChatMessageLog.remove(messageId,
                remover.getUniqueID());
        if (recipients == null) {
            return;
        }
        FMLLog.info("[losttales/chat/delete] <%s> message %d",
                remover.getCommandSenderName(), Long.valueOf(messageId));
        tellRecipients(recipients,
                LostTalesChatUpdatePacket.removed(messageId));
    }

    /**
     * Sends one update to whichever of the recorded recipients are
     * still online. Anyone who has logged out simply never hears about
     * it: chat is not replayed on join, so there is no stale line of
     * theirs left to correct.
     */
    private static void tellRecipients(Set<UUID> recipients,
                                       LostTalesChatUpdatePacket update) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online =
                server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP player : online) {
            if (player != null && recipients.contains(player.getUniqueID())) {
                LostTalesNetworkHandler.CHANNEL.sendTo(update, player);
            }
        }
    }

    private static LostTalesChatMessagePacket withPartner(
            LostTalesChatMessagePacket packet, String partner,
            String partnerIdentity) {
        // Rebuilt without an echo name: only withoutEcho() ever carries
        // one on, and this is only ever used for the other party's copy.
        return new LostTalesChatMessagePacket(packet.getChannel(),
                packet.getSenderId(), packet.getIdentityName(),
                packet.getAccountName(), packet.getTitle(),
                packet.getTitleColor(), packet.getNameColor(),
                packet.getMessage(), packet.getTimestampMillis(),
                packet.getSkinId(), packet.getShowcases(),
                packet.getFactionName(), partner, packet.getRoles(),
                packet.isAccountLine(), packet.getMessageId(),
                packet.getReply(), partnerIdentity);
    }

    /**
     * The identity of {@code player} the name stands for: their account
     * when the name is empty or is the account's own, a character of
     * their roster when it names one, and empty when it names neither.
     * Nothing the sender says decides this — the roster is the server's.
     */
    private static String resolveIdentity(EntityPlayerMP player,
                                          String identityName) {
        String account = player.getCommandSenderName();
        String named = identityName == null ? "" : identityName.trim();
        if (named.length() == 0 || named.equalsIgnoreCase(account)) {
            return account;
        }
        try {
            CharacterRoster roster = CharacterStorage.get(player.worldObj)
                    .getRoster(player.getUniqueID());
            List<RoleplayCharacter> characters = roster == null
                    ? Collections.<RoleplayCharacter>emptyList()
                    : roster.getCharacters();
            for (RoleplayCharacter character : characters) {
                if (character != null && named.equalsIgnoreCase(
                        character.getName())) {
                    return character.getName();
                }
            }
        } catch (RuntimeException ignored) {
            return "";
        }
        return "";
    }

    /**
     * A character of the sender's own roster, by id — anyone else's, or
     * an id the roster does not hold, is nobody.
     */
    private static RoleplayCharacter ownedCharacter(EntityPlayerMP sender,
                                                    UUID characterId) {
        if (characterId == null) {
            return null;
        }
        try {
            CharacterRoster roster = CharacterStorage.get(sender.worldObj)
                    .getRoster(sender.getUniqueID());
            return roster == null ? null : roster.getCharacter(characterId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** The online player with that account name, case-insensitively. */
    private static EntityPlayerMP findOnlinePlayer(String name) {
        MinecraftServer server = MinecraftServer.getServer();
        String wanted = name == null ? "" : name.trim();
        if (wanted.length() == 0 || server == null
                || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online =
                server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP candidate : online) {
            if (candidate != null && wanted.equalsIgnoreCase(
                    candidate.getCommandSenderName())) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Tells one client which channels its operator status unlocks, and
     * which roles it holds. Sent on login and whenever a staff-channel
     * message is refused, so the Admin tab follows the server's view of
     * op status without the client ever deciding it; the roles travel
     * with it so the client can notice a mention addressed to one of
     * them. The roster of every online role holder rides along, which
     * is what the role hover card names its members from.
     */
    public static void sendAccess(EntityPlayerMP player) {
        sendAccess(player, roleHolders(null));
    }

    private static void sendAccess(
            EntityPlayerMP player,
            List<LostTalesChatAccessPacket.RoleHolder> roleHolders) {
        if (player == null || player.worldObj == null
                || player.worldObj.isRemote) {
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new LostTalesChatAccessPacket(
                        LostTalesWaystonePermissionPolicy.isOperator(player),
                        LostTalesConfig.discordEnabled,
                        ChatAccountRoleResolver.resolve(player),
                        roleHolders),
                player);
    }

    /**
     * Sends every online player their access, all with one shared role
     * roster: what a join or a leave calls, so each client's role card
     * follows who is actually on. {@code leaving} is left out of the
     * roster — a logging-out player may still be listed while the event
     * runs — and receives nothing.
     */
    public static void sendAccessToAll(EntityPlayerMP leaving) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return;
        }
        List<LostTalesChatAccessPacket.RoleHolder> holders =
                roleHolders(leaving);
        LostTalesChatRoleRosterWatcher.noteBroadcast(signatureOf(holders));
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online =
                server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP recipient : online) {
            if (recipient != null && recipient != leaving) {
                sendAccess(recipient, holders);
            }
        }
    }

    /** Fingerprint of the current roster, for the change watcher. */
    static String roleRosterSignature() {
        return signatureOf(roleHolders(null));
    }

    private static String signatureOf(
            List<LostTalesChatAccessPacket.RoleHolder> holders) {
        List<String> entries = new ArrayList<String>(holders.size());
        for (LostTalesChatAccessPacket.RoleHolder holder : holders) {
            entries.add(holder.getName().toLowerCase(java.util.Locale.ROOT)
                    + ':' + holder.getMask());
        }
        Collections.sort(entries);
        StringBuilder signature = new StringBuilder();
        for (int index = 0; index < entries.size(); index++) {
            signature.append(entries.get(index)).append(';');
        }
        return signature.toString();
    }

    /** Every online account holding a role, {@code excluded} left out. */
    private static List<LostTalesChatAccessPacket.RoleHolder> roleHolders(
            EntityPlayerMP excluded) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return Collections.emptyList();
        }
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online =
                server.getConfigurationManager().playerEntityList;
        List<LostTalesChatAccessPacket.RoleHolder> holders =
                new ArrayList<LostTalesChatAccessPacket.RoleHolder>();
        for (EntityPlayerMP player : online) {
            if (player == null || player == excluded) {
                continue;
            }
            int mask = ChatAccountRoleResolver.resolve(player);
            if (mask != 0) {
                holders.add(new LostTalesChatAccessPacket.RoleHolder(
                        player.getGameProfile() == null
                                ? player.getCommandSenderName()
                                : player.getGameProfile().getName(),
                        mask));
            }
        }
        return holders;
    }

    /**
     * Pairs tokens with references by position and keeps only the things
     * that exist, match the typed name, and fit the wire bound — each on
     * its own, and all of them together against
     * {@link ChatShowcase#MAX_TOTAL_BYTES}, since the whole set is what
     * every recipient of the line is sent. What does not fit stays the
     * text it was typed as. The sender is told once per message and kind
     * when something was dropped; nothing here trusts a reference beyond
     * using it as a lookup key.
     */
    private static List<ChatShowcase> resolveShowcases(
            EntityPlayerMP sender, String message,
            List<ChatShareReference> references) {
        if (references == null || references.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChatShareTokenParser.Token> tokens =
                ChatShareTokenParser.parse(message);
        int count = Math.min(tokens.size(), Math.min(
                references.size(), ChatShareTokenParser.MAX_TOKENS));
        List<ChatShowcase> result = new ArrayList<ChatShowcase>(count);
        boolean itemUnavailable = false;
        boolean itemTooLarge = false;
        boolean markerUnavailable = false;
        boolean overBudget = false;
        int budget = ChatShowcase.MAX_TOTAL_BYTES;
        for (int index = 0; index < count; index++) {
            ChatShareTokenParser.Token token = tokens.get(index);
            ChatShareReference reference = references.get(index);
            if (reference == null || reference.getKind() != token.kind) {
                if (token.kind == ChatShareKind.MARKER) {
                    markerUnavailable = true;
                } else {
                    itemUnavailable = true;
                }
                continue;
            }
            if (token.kind == ChatShareKind.ITEM) {
                ItemStack stack = resolveItem(sender, reference, token);
                if (stack == null) {
                    itemUnavailable = true;
                    continue;
                }
                byte[] encoded = ChatShowcase.encodeStack(stack.copy());
                if (encoded == null) {
                    itemTooLarge = true;
                    continue;
                }
                ChatShowcase item = ChatShowcase.item(index, encoded);
                if (item.serializedBytes() > budget) {
                    overBudget = true;
                    continue;
                }
                budget -= item.serializedBytes();
                result.add(item);
            } else {
                ChatShowcase marker = resolveMarker(
                        sender, reference, token, index);
                if (marker == null) {
                    markerUnavailable = true;
                    continue;
                }
                if (marker.serializedBytes() > budget) {
                    overBudget = true;
                    continue;
                }
                budget -= marker.serializedBytes();
                result.add(marker);
            }
        }
        if (itemUnavailable) {
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.item.unavailable"));
        }
        if (itemTooLarge) {
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.item.too_large"));
        }
        if (markerUnavailable) {
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.marker.unavailable"));
        }
        if (overBudget) {
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.share.too_many"));
        }
        return result;
    }

    private static ItemStack resolveItem(EntityPlayerMP sender,
                                         ChatShareReference reference,
                                         ChatShareTokenParser.Token token) {
        if (sender.inventory == null || !reference.isResolved()) {
            return null;
        }
        ItemStack stack = sender.inventory.getStackInSlot(reference.getSlot());
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0
                || !token.normalizedName().equals(
                        ChatShareTokenParser.normalizeName(
                                stack.getDisplayName()))) {
            return null;
        }
        return stack;
    }

    /**
     * A marker the sender may actually see, by the id the client supplied,
     * with the typed name checked against the record. The public fields go
     * out; ownership, sharing lists, and settings never do.
     */
    private static ChatShowcase resolveMarker(EntityPlayerMP sender,
                                              ChatShareReference reference,
                                              ChatShareTokenParser.Token token,
                                              int tokenIndex) {
        if (!reference.isResolved()) {
            return null;
        }
        try {
            LostTalesMapMarkerRecord record = LostTalesMapMarkerStorage
                    .get(sender.worldObj).getRecord(reference.getMarkerId());
            if (record == null
                    || !LostTalesMapMarkerVisibilityPolicy.canView(
                            record, sender)
                    || !token.normalizedName().equals(
                            ChatShareTokenParser.normalizeName(
                                    record.getName()))) {
                return null;
            }
            if (!LostTalesWaypointFastTravelPolicy.hasVisited(sender, record)) {
                // Only places the sender has actually reached are shared;
                // an undiscovered or region-locked marker stays plain text.
                return null;
            }
            return ChatShowcase.marker(tokenIndex, record.getId(),
                    record.getName(), record.getIconName(),
                    record.getColorName(), record.getDimensionId(),
                    record.getX(), record.getZ());
        } catch (RuntimeException exception) {
            FMLLog.warning("[losttales/chat] Could not resolve shared marker "
                    + "%s for %s: %s", reference.getMarkerId(),
                    sender.getUniqueID(), exception.toString());
            return null;
        }
    }

    private static List<EntityPlayerMP> resolveRecipients(
            EntityPlayerMP sender, ChatChannel channel, Party party,
            String factionId) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return Collections.emptyList();
        }
        List<EntityPlayerMP> result = new ArrayList<EntityPlayerMP>();
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online =
                server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP candidate : online) {
            if (candidate == null || candidate.getUniqueID() == null) {
                continue;
            }
            if (channel.getRecipientRule() == ChatRecipientRule.GLOBAL) {
                result.add(candidate);
            } else if (channel.getRecipientRule() == ChatRecipientRule.SELF
                    || channel.getRecipientRule()
                    == ChatRecipientRule.WHISPER) {
                if (candidate == sender) {
                    result.add(candidate);
                }
            } else if (channel.getRecipientRule()
                    == ChatRecipientRule.OPERATORS) {
                if (LostTalesWaystonePermissionPolicy.isOperator(candidate)) {
                    result.add(candidate);
                }
            } else if (channel.getRecipientRule()
                    == ChatRecipientRule.PROXIMITY) {
                if (candidate.dimension == sender.dimension
                        && candidate.getDistanceSqToEntity(sender)
                        <= proximityDistanceSquared()) {
                    result.add(candidate);
                }
            } else if (channel.getRecipientRule()
                    == ChatRecipientRule.PARTY
                    && isCurrentOnlinePartyMember(candidate, party)) {
                result.add(candidate);
            } else if (channel.getRecipientRule()
                    == ChatRecipientRule.FACTION
                    && isCurrentFactionMember(candidate, factionId)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private static boolean isCurrentOnlinePartyMember(
            EntityPlayerMP player, Party party) {
        if (party == null || player == null) {
            return false;
        }
        RoleplayCharacter active = CharacterActiveResolver.get(player);
        if (active == null) {
            return false;
        }
        PartyMember member = party.getMember(active.getCharacterId());
        return member != null && player.getUniqueID().equals(member.getOwnerId());
    }

    private static boolean isCurrentFactionMember(
            EntityPlayerMP player, String factionId) {
        if (player == null || factionId == null || factionId.length() == 0) {
            return false;
        }
        RoleplayCharacter active = CharacterActiveResolver.get(player);
        return active != null && factionId.equals(
                LotrCharacterAdapter.normalizeFactionId(
                        active.getStartingFactionId()));
    }

    private static double proximityDistanceSquared() {
        double radius = Math.max(1.0D, LostTalesConfig.chatProximityRadius);
        return radius * radius;
    }

    private static String characterNameOrFallback(
            RoleplayCharacter character, String accountName) {
        if (character != null && character.getName() != null
                && character.getName().trim().length() > 0) {
            return character.getName().trim();
        }
        return accountName == null || accountName.length() == 0
                ? "Unknown" : accountName;
    }
}
