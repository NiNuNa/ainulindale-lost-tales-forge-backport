package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelAccess;
import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.chat.ChatEpithet;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatMessageOrigin;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.ChatRolePresentation;
import com.ninuna.losttales.chat.ChatRecipientRule;
import com.ninuna.losttales.chat.moderation.ChatAuditLog;
import com.ninuna.losttales.chat.moderation.ChatMuteDurations;
import com.ninuna.losttales.chat.moderation.ChatMuteEntry;
import com.ninuna.losttales.chat.moderation.ChatMuteStorage;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareReference;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.chat.share.ChatShowcase;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.identity.PlayableIdentityResolver;
import com.ninuna.losttales.character.identity.RoleplayCharacterIdentityHook;
import com.ninuna.losttales.character.server.CharacterActiveResolver;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.compat.discord.DiscordAvatarUrl;
import com.ninuna.losttales.compat.discord.DiscordBridgePolicy;
import com.ninuna.losttales.compat.discord.DiscordMessageSanitizer;
import com.ninuna.losttales.compat.discord.LostTalesDiscordBridge;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerStorage;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerVisibilityPolicy;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatAccessPacket;
import com.ninuna.losttales.network.packet.LostTalesChatConsoleSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatHistorySyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import com.ninuna.losttales.network.packet.LostTalesChatTypingSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatUpdatePacket;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.server.PartyService;
import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.permission.LostTalesPermissions;
import com.ninuna.losttales.util.LostTalesServerPlayers;
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

    /**
     * Validates and distributes one message a player sent, exactly as
     * the request names it:
     * <ul>
     * <li>the share references pair, in order, with the share tokens
     * found in the message; an item slot is re-read from the sender's
     * live inventory and a marker id from world data under the sender's
     * own visibility, and only a match whose real name agrees with the
     * token is attached — tokens that cannot be verified are delivered
     * as the literal text the sender typed;</li>
     * <li>a whisper goes to the sender and the one online account it
     * names, each told who the other party is, and to the identity of
     * that account it names — a conversation is with a person as they
     * present themselves, so one player's characters are separate
     * threads; an unknown name or an identity the account does not
     * hold is refused with a notice;</li>
     * <li>the appearance is the identity the sender asked to speak as:
     * the character being played, the account, or one character of the
     * sender's own roster — never anyone else's. It decides how the
     * line is signed and which faction a faction line speaks to; party
     * membership still follows the <em>active</em> character, and a
     * faction line's readers are the characters being played in that
     * faction;</li>
     * <li>the reply reference is honoured only while the message it
     * names is within the log's reach <em>and</em> was sent to this
     * sender, so naming an id can never quote back a message nobody
     * showed them; one that fails either test is dropped with a
     * notice;</li>
     * <li>the echo nonce is the sender's own name for the message,
     * handed back to them alone so the line they showed the moment
     * they typed it is replaced by the delivered copy.</li>
     * </ul>
     */
    public static void send(EntityPlayerMP sender,
                            LostTalesChatSendPacket request) {
        if (sender == null || request == null) {
            return;
        }
        send(sender, request.getChannel(), request.getMessage(),
                request.getReferences(), request.getTarget(),
                request.getAppearanceKind(),
                request.getAppearanceCharacterId(),
                request.getReplyToMessageId(), request.getTargetIdentity(),
                request.getEchoNonce(), request.getTargetCharacterId());
    }

    private static void send(EntityPlayerMP sender,
                             ChatChannel channel, String message,
                             List<ChatShareReference> references,
                             String target, int appearanceKind,
                             UUID appearanceCharacterId,
                             long replyToMessageId, String targetIdentity,
                             long echoNonce, UUID targetCharacterId) {
        if (sender == null || sender.worldObj == null
                || sender.worldObj.isRemote || channel == null
                || !ChatMessageValidator.isValid(message)) {
            return;
        }
        // A muted account sends nothing anywhere but its own console; the
        // Discord relay sits behind this gate, so nothing leaks out either.
        if (channel.getRecipientRule() != ChatRecipientRule.SELF) {
            ChatMuteEntry mute = activeMute(sender);
            if (mute != null) {
                tellMuted(sender, mute);
                return;
            }
        }
        EntityPlayerMP whisperTarget = null;
        String whisperIdentity = "";
        UUID whisperCharacterId = null;
        if (channel.getRecipientRule() == ChatRecipientRule.WHISPER) {
            whisperTarget = LostTalesServerPlayers.findOnline(target);
            if (whisperTarget == null || whisperTarget == sender) {
                sender.addChatMessage(new ChatComponentTranslation(
                        whisperTarget == sender
                                ? "chat.losttales.whisper.self"
                                : "chat.losttales.whisper.unavailable"));
                return;
            }
            AddressedIdentity addressed = resolveIdentity(whisperTarget,
                    targetIdentity, targetCharacterId);
            if (addressed == null) {
                // Named an identity that account does not hold: nothing
                // to have a conversation with.
                sender.addChatMessage(new ChatComponentTranslation(
                        "chat.losttales.whisper.unavailable"));
                return;
            }
            whisperIdentity = addressed.name;
            whisperCharacterId = addressed.characterId;
        }

        // The roster's word on who the sender is playing, told apart
        // from a store that cannot say: a player on the account is one
        // thing, an unreadable roster another.
        PlayableIdentityResolver.Resolution identity =
                PlayableIdentityResolver.resolve(sender);
        RoleplayCharacter character = identity.isAvailable()
                ? identity.getCharacter() : null;
        // The identity the line is signed with: the one the sender chose
        // for the tab, else the character being played on every channel,
        // else the account. Any owned character may speak anywhere.
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
        } else if (identity.isAvailable()) {
            appearance = character;
        } else if (ChatRolePresentation.isInCharacter(channel)) {
            // The line would wear the active character, and the roster
            // cannot say which: refused rather than quietly spoken as
            // the account where the character is the point.
            noteStorageFailure("the character roster", null);
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.appearance.unavailable"));
            return;
        } else {
            appearance = null;
        }
        Party party = null;
        // A faction line is spoken to the faction of the identity it
        // wears: the worn character's, or none for the account, which the
        // Faction channel then refuses. Its readers are the characters
        // being played in that faction.
        String factionId = ChatChannelPolicy.playedFactionId(appearance);
        if (channel.getAccess() == ChatChannelAccess.PARTY_MEMBERSHIP) {
            // Membership follows the identity being played, the account
            // included; the party service resolves it the same way.
            party = PartyService.getInstance()
                    .getPartyForActiveCharacter(sender);
        }
        String refusal = ChatChannelPolicy.sendRefusal(channel, party,
                RoleplayCharacterIdentityHook.resolveGameplayId(sender), factionId,
                ChatAccountRoleResolver.resolve(sender,
                        character == null ? null : character.getCharacterId()));
        if (refusal != null) {
            if (ChatChannelPolicy.isGateRefusal(refusal)) {
                // The client only offers the tab while the server last said
                // it may; tell it again so a lost role loses the tab.
                sendAccess(sender);
            }
            sender.addChatMessage(new ChatComponentTranslation(refusal));
            return;
        }

        String accountName = sender.getGameProfile() == null
                ? sender.getCommandSenderName()
                : sender.getGameProfile().getName();
        // What makes a line an account line — the head it wears, the
        // skin it is drawn with — is the appearance, not the channel.
        // Whether the sender's roles are tagged on it, and what colours
        // the name, is the channel's: out of character the roles show,
        // in character nobody wears a role (ChatRolePresentation).
        boolean accountLine = appearance == null;
        String identityName = accountLine ? accountName
                : characterNameOrFallback(appearance, accountName);
        LostTalesChatPresentationResolver.Presentation presentation =
                LostTalesChatPresentationResolver.resolve(sender, appearance);
        List<ChatShowcase> showcases =
                resolveShowcases(sender, message, references);
        // The roles the line wears are the account's and the worn
        // character's own: a character-scoped role shows on that
        // character's lines and on nobody else's.
        int roles = ChatRolePresentation.rolesShown(channel,
                ChatAccountRoleResolver.resolve(sender,
                        appearance == null ? null : appearance.getCharacterId()));
        int ivory = ChatRolePresentation.unassignedColor();
        ChatReplyReference reply = ChatMessageIds.NONE == replyToMessageId
                ? ChatReplyReference.NONE
                : ChatHistory.quoteFor(replyToMessageId,
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
                ChatRolePresentation.nameColor(channel, roles, accountLine,
                        presentation.nameColor),
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
                echoNonce,
                // The worn character by its stable id, so a client can
                // key conversations and mentions by it rather than by a
                // name; null for a line worn by the account.
                appearance == null ? null : appearance.getCharacterId());

        FMLLog.info("[losttales/chat/%s] <%s (%s)> %s%s%s",
                channel.getId(), identityName, accountName, message,
                whisperTarget == null ? ""
                        : " -> " + whisperTarget.getCommandSenderName(),
                showcases.isEmpty() ? ""
                        : " [shared: " + showcases.size() + "]");

        // Every accepted line is recorded before it goes anywhere, a
        // whisper included: the audit exists for what the live window
        // cannot reach back to, and a private line is exactly that.
        ChatAuditLog.logMessage(packet.getMessageId(), channel.getId(),
                sender.getUniqueID(), accountName,
                appearance == null ? null : appearance.getCharacterId(),
                identityName,
                whisperTarget == null ? ""
                        : whisperTarget.getCommandSenderName(),
                message);
        if (whisperTarget != null) {
            // Each side is told who the other party is, and the history
            // keeps both tellings: a replay hands each party their own.
            // Each copy also says which of the receiving party's own
            // characters it is held as, and which of the other party's
            // it is with, so the clients keep one thread per pair of
            // identities and a reply is addressed by id.
            UUID wornId = appearance == null ? null : appearance.getCharacterId();
            packet = packet.withConversation(wornId, whisperCharacterId);
            LostTalesChatMessagePacket partnerCopy = withPartner(
                    packet.withoutEcho(), accountName, identityName)
                    .withConversation(whisperCharacterId, wornId);
            LostTalesNetworkHandler.CHANNEL.sendTo(packet, sender);
            LostTalesNetworkHandler.CHANNEL.sendTo(partnerCopy, whisperTarget);
            List<UUID> parties = Arrays.asList(sender.getUniqueID(),
                    whisperTarget.getUniqueID());
            ChatHistory.record(packet.getMessageId(),
                    sender.getUniqueID(), identityName,
                    packet.withoutEcho(), partnerCopy, parties,
                    ChatHistory.Audience.accounts(parties, false));
            return;
        }
        deliver(packet, sender, ChatChannelPolicy.route(sender, channel, party, factionId),
                sender.getUniqueID(), identityName);
        // Out to Discord through the channel's binding, when it has one
        // that posts. Only a player's own line in a channel that may be
        // bridged ever leaves: the policy is asked here, before the
        // bridge is, so no binding can carry a private channel or send
        // a Discord line back. The bridge posts the line under the
        // sender's name — a line spoken as a character carries the title
        // the game shows — with the account's head as the picture, since
        // a character's skin is a resource of the game and not a picture
        // Discord can fetch; emoji shortcodes go as the Unicode emoji
        // Discord renders, share tokens as the text they were typed as.
        // The message's own id and the reply it resolved travel with it,
        // so the post can be linked to its Discord copy and a reply can
        // point at the Discord original.
        if (DiscordBridgePolicy.relaysOutbound(ChatMessageOrigin.PLAYER, channel)) {
            LostTalesDiscordBridge.getInstance().relayToDiscord(channel,
                    factionId,
                    accountLine ? identityName : ChatEpithet.titledName(
                            identityName, presentation.factionName,
                            presentation.title),
                    DiscordAvatarUrl.forPlayer(sender),
                    DiscordMessageSanitizer.outbound(message),
                    packet.getMessageId(), reply);
        }
    }

    /**
     * A message from Discord, delivered by the bridge on the server
     * thread into the channel its binding names — the game's own Discord
     * channel, or any other channel that may be bridged; for Faction
     * chat, the faction the binding is for. It reaches everyone the
     * channel's own rule reaches, under the Discord display name, with
     * the sender id that stands for that member
     * ({@link LostTalesChatMessagePacket#discordSenderId}) — so a client
     * can ignore them, and a mute stored against that id silences them
     * here, silently, exactly as an account's mute does — and is never
     * posted back to Discord: a line of Discord origin never enters the
     * relay, whatever channel it lands in. A Discord member is an
     * external identity and wears no character. The bridge has already
     * sanitised and bounded the text, and resolved the quote when the
     * message answers one — a Discord reply is shown exactly as a
     * player's reply is. The line is recorded like any other, so
     * players can reply to it in turn; its author id is the bridge's
     * own, which no account holds, so nobody can edit or take it back.
     * Answers with the id the message was distributed under, or
     * {@link ChatMessageIds#NONE} when nothing went out, so the bridge
     * can link the line to the Discord message it came from.
     */
    public static long sendFromDiscord(ChatChannel channel, String factionScope,
                                       String displayName, String discordUserId,
                                       String message,
                                       ChatReplyReference reply) {
        MinecraftServer server = MinecraftServer.getServer();
        if (!LostTalesConfig.discordEnabled
                || !DiscordBridgePolicy.acceptsInbound(channel)
                || displayName == null
                || displayName.length() == 0
                || !ChatMessageValidator.isValid(message) || server == null
                || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return ChatMessageIds.NONE;
        }
        UUID senderId = LostTalesChatMessagePacket.discordSenderId(
                discordUserId);
        if (isDiscordSenderMuted(server, senderId)) {
            // Dropped without a word: the member is on Discord, where no
            // notice of ours reaches, and nothing is recorded, so the
            // line cannot be replied to or corrected either.
            FMLLog.info("[losttales/chat/discord] dropped a line from muted "
                    + "member %s", displayName);
            return ChatMessageIds.NONE;
        }
        int ivory = LostTalesColors.rgb(LostTalesColors.HUD_LABEL);
        long messageId = ChatMessageIdAllocator.next();
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                channel, senderId, displayName,
                displayName, "", ivory, ivory, message,
                System.currentTimeMillis(), "", null, "", "", 0, true,
                messageId, reply);
        FMLLog.info("[losttales/chat/%s] <%s (discord)> %s", channel.getId(),
                displayName, message);
        // Routed by the channel's own rule with no sender behind the
        // line: everyone for a global channel, the operators for the
        // staff channel, the faction's members for a faction binding.
        deliver(packet, null, ChatChannelPolicy.route(null, channel, null,
                        factionScope == null ? "" : factionScope),
                LostTalesChatMessagePacket.DISCORD_SENDER_ID, displayName);
        // Recorded under the member's own sender id, the same id a mute
        // names them by, so the audit and the moderation tools agree on
        // who a Discord line is from.
        ChatAuditLog.logDiscordMessage(messageId, channel.getId(), senderId,
                displayName, message);
        return messageId;
    }

    /**
     * Whether a mute is stored against a Discord member's sender id.
     * Mutes live in the overworld's storage like every other; a world
     * that cannot be read silences nobody.
     */
    private static boolean isDiscordSenderMuted(MinecraftServer server,
                                                UUID senderId) {
        try {
            return server.worldServerForDimension(0) != null
                    && ChatMuteStorage.get(server.worldServerForDimension(0))
                            .getActiveMute(senderId,
                                    System.currentTimeMillis()) != null;
        } catch (RuntimeException exception) {
            noteStorageFailure("the mute list", exception);
            return false;
        }
    }

    /**
     * Whether a store's failure has been reported this server session.
     * A world whose chat stores cannot be read degrades — a mute is not
     * enforced, a character name is not resolved — and that is said
     * once at warning level rather than once per line or not at all.
     */
    private static boolean storageFailureLogged;

    private static void noteStorageFailure(String what,
                                           RuntimeException exception) {
        if (storageFailureLogged) {
            return;
        }
        storageFailureLogged = true;
        FMLLog.warning("[%s] Chat could not read %s from world storage; "
                + "the chat runs without it until the server restarts%s",
                LostTalesMetaData.MOD_ID, what,
                exception == null ? "" : ": " + exception.toString());
        console(ChatConsoleEvent.Kind.WARNING, ChatConsoleEvent.Severity.WARNING, "",
                "Chat could not read " + what + " from world storage; the chat runs "
                        + "without it until the server restarts");
    }

    /* ---- the shared operator console ---- */

    /**
     * Records one administrative event and shows it at once to every
     * online player who may read the console
     * ({@link LostTalesCapability#CHAT_CONSOLE_READ}). The capability is
     * asked of each recipient here, and asked again of a joining player
     * before the kept entries are replayed to them; the client is never
     * the one deciding.
     */
    public static void console(ChatConsoleEvent.Kind kind,
                               ChatConsoleEvent.Severity severity, String actor,
                               String text) {
        if (kind == null || severity == null || text == null
                || text.trim().length() == 0) {
            return;
        }
        ChatConsoleEvent event;
        try {
            event = new ChatConsoleEvent(ChatMessageIdAllocator.next(),
                    System.currentTimeMillis(), kind, severity, actor, text);
        } catch (IllegalArgumentException refused) {
            return;
        }
        ChatConsoleStream.record(event);
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return;
        }
        LostTalesChatConsoleSyncPacket packet = new LostTalesChatConsoleSyncPacket(
                Collections.singletonList(event));
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online = server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP player : online) {
            if (player != null && ChatChannelPolicy.readsConsole(player)) {
                LostTalesNetworkHandler.CHANNEL.sendTo(packet, player);
            }
        }
    }

    /**
     * Replays the kept console entries to a player who has just joined,
     * oldest first and in batches, if they may read the console.
     */
    public static void sendConsoleHistory(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote
                || !ChatChannelPolicy.readsConsole(player)) {
            return;
        }
        List<ChatConsoleEvent> events = ChatConsoleStream.replay(0L);
        for (int from = 0; from < events.size();
             from += LostTalesChatConsoleSyncPacket.MAX_EVENTS) {
            LostTalesNetworkHandler.CHANNEL.sendTo(new LostTalesChatConsoleSyncPacket(
                    events.subList(from, Math.min(events.size(),
                            from + LostTalesChatConsoleSyncPacket.MAX_EVENTS))), player);
        }
    }

    /** Cleared with the rest of the server's chat state. */
    public static void clear() {
        storageFailureLogged = false;
    }

    /**
     * A Discord member's own edit, found by the bridge's sweep and
     * delivered on the server thread: the line is rewritten for
     * everyone who was sent it, exactly as a player's edit is. The
     * bridge signed the line with its own author id when it was
     * recorded, which is what allows the rewrite here and what stops
     * any player from making one. Nothing is posted back to Discord —
     * the change came from there.
     */
    public static void editFromDiscord(long messageId, String message) {
        if (!ChatMessageValidator.isValid(message)) {
            return;
        }
        Set<UUID> recipients = ChatHistory.applyEdit(messageId,
                LostTalesChatMessagePacket.DISCORD_SENDER_ID, message);
        if (recipients == null) {
            return;
        }
        FMLLog.info("[losttales/chat/discord] edited message %d: %s",
                Long.valueOf(messageId), message);
        tellRecipients(recipients,
                LostTalesChatUpdatePacket.edited(messageId, message));
    }

    /**
     * A Discord member's own deletion, on the same terms as an edit:
     * the line is taken back from everyone who was sent it.
     */
    public static void deleteFromDiscord(long messageId) {
        Set<UUID> recipients = ChatHistory.remove(messageId,
                LostTalesChatMessagePacket.DISCORD_SENDER_ID);
        if (recipients == null) {
            return;
        }
        FMLLog.info("[losttales/chat/discord] deleted message %d",
                Long.valueOf(messageId));
        tellRecipients(recipients,
                LostTalesChatUpdatePacket.removed(messageId));
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
        // Presence from a muted account is dropped without a notice: a
        // typing indicator promises a message the send would refuse.
        if (activeMute(sender) != null) {
            return;
        }
        RoleplayCharacter character = CharacterActiveResolver.get(sender);
        String accountName = sender.getGameProfile() == null
                ? sender.getCommandSenderName()
                : sender.getGameProfile().getName();
        // Presence carries the default identity, the character being
        // played: the typing packet does not say which appearance the
        // message will wear, and a name shown a moment early is
        // presentation, not fact.
        String identityName = character == null
                ? accountName
                : characterNameOrFallback(character, accountName);
        if (channel.getRecipientRule() == ChatRecipientRule.WHISPER) {
            EntityPlayerMP whisperTarget = LostTalesServerPlayers.findOnline(target);
            if (whisperTarget != null && whisperTarget != sender) {
                LostTalesNetworkHandler.CHANNEL.sendTo(
                        new LostTalesChatTypingSyncPacket(channel,
                                accountName, identityName, typing),
                        whisperTarget);
            }
            return;
        }
        Party party = null;
        String factionId = ChatChannelPolicy.playedFactionId(character);
        if (channel.getAccess() == ChatChannelAccess.PARTY_MEMBERSHIP) {
            party = PartyService.getInstance()
                    .getPartyForActiveCharacter(sender);
        }
        // The same question a send asks, so presence never promises a
        // message the channel would refuse.
        if (ChatChannelPolicy.sendRefusal(channel, party,
                RoleplayCharacterIdentityHook.resolveGameplayId(sender), factionId,
                ChatAccountRoleResolver.resolve(sender,
                        character == null ? null : character.getCharacterId())) != null) {
            return;
        }
        if (typing && DiscordBridgePolicy.relaysOutbound(
                ChatMessageOrigin.PLAYER, channel)) {
            // A bound channel has readers on the other side too, and
            // Discord shows its own indicator when the bot says it is
            // typing. Nothing but presence crosses, and only through a
            // binding that posts.
            LostTalesDiscordBridge.getInstance().relayTyping(channel, factionId);
        }
        LostTalesChatTypingSyncPacket packet = new LostTalesChatTypingSyncPacket(
                channel, "", identityName, typing);
        for (EntityPlayerMP recipient : ChatChannelPolicy.route(
                sender, channel, party, factionId).recipients) {
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
     * A line already carried to Discord is corrected there as well,
     * through the id the bridge kept from its own post.</p>
     */
    public static void edit(EntityPlayerMP editor, long messageId,
                            String message) {
        if (editor == null || editor.worldObj == null
                || editor.worldObj.isRemote
                || !ChatMessageValidator.isValid(message)) {
            return;
        }
        // An edit puts new words in front of the same readers a send
        // would reach, so a mute refuses it the same way. Deleting is
        // still allowed: taking a message back harms nobody.
        ChatMuteEntry mute = activeMute(editor);
        if (mute != null) {
            tellMuted(editor, mute);
            return;
        }
        Set<UUID> recipients = ChatHistory.applyEdit(messageId,
                editor.getUniqueID(), message);
        if (recipients == null) {
            return;
        }
        FMLLog.info("[losttales/chat/edit] <%s> %s",
                editor.getCommandSenderName(), message);
        ChatAuditLog.logEdit(messageId, editor.getUniqueID(),
                editor.getCommandSenderName(), message);
        tellRecipients(recipients,
                LostTalesChatUpdatePacket.edited(messageId, message));
        // A line carried to Discord is corrected there too: the bridge
        // rewrites its own webhook post by the id it kept. A message of
        // any other channel resolves to no post and nothing happens.
        LostTalesDiscordBridge.getInstance().relayEdit(messageId,
                DiscordMessageSanitizer.outbound(message));
    }

    /**
     * Takes one of {@code remover}'s own messages back, on the same
     * terms as {@link #edit}, or — when the remover may moderate the
     * chat ({@link LostTalesCapability#CHAT_MODERATE}) — anyone's message
     * that is still within reach. Everyone who was sent it is told to
     * drop it; nobody else hears that it ever existed. A moderator may
     * remove but never edit another's words: a removal is visibly a
     * removal. The capability is read here, from the server's own
     * permissions, never from the request.
     */
    public static void delete(EntityPlayerMP remover, long messageId) {
        if (remover == null || remover.worldObj == null
                || remover.worldObj.isRemote) {
            return;
        }
        Set<UUID> recipients = ChatHistory.remove(messageId,
                remover.getUniqueID());
        boolean fromDiscord = false;
        if (recipients == null) {
            if (!LostTalesPermissions.has(remover, LostTalesCapability.CHAT_MODERATE)) {
                return;
            }
            ChatHistory.Removal removal =
                    ChatHistory.removeByOperator(messageId);
            if (removal == null) {
                return;
            }
            recipients = removal.recipients;
            fromDiscord = LostTalesChatMessagePacket.DISCORD_SENDER_ID
                    .equals(removal.authorId);
            FMLLog.info("[losttales/chat/delete] moderator <%s> removed "
                    + "message %d of <%s>", remover.getCommandSenderName(),
                    Long.valueOf(messageId), removal.author);
            ChatAuditLog.logModerationDelete(messageId,
                    remover.getUniqueID(), remover.getCommandSenderName(),
                    removal.author);
            console(ChatConsoleEvent.Kind.MODERATION, ChatConsoleEvent.Severity.NOTICE,
                    remover.getCommandSenderName(),
                    "removed a message of " + removal.author);
        } else {
            FMLLog.info("[losttales/chat/delete] <%s> message %d",
                    remover.getCommandSenderName(), Long.valueOf(messageId));
            ChatAuditLog.logDelete(messageId, remover.getUniqueID(),
                    remover.getCommandSenderName());
        }
        tellRecipients(recipients,
                LostTalesChatUpdatePacket.removed(messageId));
        // Taken back from Discord as well, on the same terms as an edit.
        // A line that came from Discord is a member's own message there,
        // which the webhook could not delete anyway: the removal is
        // in-game moderation only, and Discord's moderators keep theirs.
        if (!fromDiscord) {
            LostTalesDiscordBridge.getInstance().relayDelete(messageId);
        }
    }

    /**
     * Sends one update to whichever of the recorded recipients are
     * still online. Anyone who has logged out never hears about it, and
     * needs to hear nothing: the history they are replayed on joining
     * already says what the message says now, or no longer holds it.
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
        // The other party's copy: the same line filed under the sender's
        // identity, without the sender's private echo name.
        return packet.withoutEcho().withPartner(partner, partnerIdentity);
    }

    /** The identity of a whisper's target the sender addressed: a character by id, or the account. */
    private static final class AddressedIdentity {
        final String name;
        final UUID characterId;

        AddressedIdentity(String name, UUID characterId) {
            this.name = name;
            this.characterId = characterId;
        }
    }

    /**
     * The identity of {@code player} the sender addressed: by the
     * character's id when one is given, else by name — their account
     * when the name is empty or is the account's own, a character of
     * their roster when it names one. Null when neither names anything
     * the account holds. Nothing the sender says decides this — the
     * roster is the server's.
     */
    private static AddressedIdentity resolveIdentity(EntityPlayerMP player,
                                                     String identityName,
                                                     UUID characterId) {
        String account = player.getCommandSenderName();
        String named = identityName == null ? "" : identityName.trim();
        if (characterId == null
                && (named.length() == 0 || named.equalsIgnoreCase(account))) {
            return new AddressedIdentity(account, null);
        }
        try {
            CharacterRoster roster = CharacterStorage.get(player.worldObj)
                    .getRoster(player.getUniqueID());
            List<RoleplayCharacter> characters = roster == null
                    ? Collections.<RoleplayCharacter>emptyList()
                    : roster.getCharacters();
            for (RoleplayCharacter character : characters) {
                if (character == null) {
                    continue;
                }
                boolean matches = characterId != null
                        ? characterId.equals(character.getCharacterId())
                        : named.equalsIgnoreCase(character.getName());
                if (matches) {
                    return new AddressedIdentity(character.getName(),
                            character.getCharacterId());
                }
            }
        } catch (RuntimeException exception) {
            noteStorageFailure("the character roster", exception);
        }
        return null;
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
        } catch (RuntimeException exception) {
            noteStorageFailure("the character roster", exception);
            return null;
        }
    }

    /** The mute currently silencing the player's account, or null. */
    private static ChatMuteEntry activeMute(EntityPlayerMP player) {
        return ChatMuteStorage.get(player.worldObj).getActiveMute(
                player.getUniqueID(), System.currentTimeMillis());
    }

    /** Tells a refused sender they are muted, for how long, and why. */
    private static void tellMuted(EntityPlayerMP player, ChatMuteEntry mute) {
        boolean hasReason = mute.getReason().length() > 0;
        if (mute.isPermanent()) {
            player.addChatMessage(hasReason
                    ? new ChatComponentTranslation(
                            "chat.losttales.muted.because", mute.getReason())
                    : new ChatComponentTranslation("chat.losttales.muted"));
            return;
        }
        String remaining = ChatMuteDurations.formatRemaining(
                mute.getExpiresAtMillis() - System.currentTimeMillis());
        player.addChatMessage(hasReason
                ? new ChatComponentTranslation(
                        "chat.losttales.muted.timed.because", remaining,
                        mute.getReason())
                : new ChatComponentTranslation(
                        "chat.losttales.muted.timed", remaining));
    }


    /**
     * Tells one client which channels it may use, which roles it holds,
     * and whether it may moderate the chat. Sent on login and whenever a
     * staff-channel message is refused, so the Admin tab follows the
     * server's view without the client ever deciding it; the roles travel
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
        boolean moderator = LostTalesPermissions.has(player,
                LostTalesCapability.CHAT_MODERATE);
        int roles = ChatChannelPolicy.playedRoles(player);
        ChatChannelGates gates = ChatChannelGates.current();
        // The second flag stays on the wire for older clients, whose
        // separate Discord tab it gates; OOC & Discord exists for
        // everyone, so it is always granted, which keeps that tab open
        // and sends its lines here. The first is the Operator channel's
        // send gate, which is the operator role unless configured
        // otherwise. The mute list, and the moderation flag the client
        // offers its moderation menus on, follow the moderation
        // capability; the settings flag follows its own. The server
        // decides again on every request.
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new LostTalesChatAccessPacket(
                        gates.canSend(roles, ChatChannel.ADMIN), true, roles,
                        roleHolders,
                        moderator ? mutedSenders(player)
                                : Collections.<UUID>emptyList(),
                        ChatRoleCatalog.server().roles(),
                        channelMask(gates, roles, true),
                        channelMask(gates, roles, false),
                        moderator,
                        LostTalesPermissions.has(player,
                                LostTalesCapability.SERVER_CONFIG)),
                player);
    }

    /** One bit per channel ordinal: what the gates let these roles do. */
    private static int channelMask(ChatChannelGates gates, int roles, boolean read) {
        int mask = 0;
        ChatChannel[] channels = ChatChannel.values();
        for (int index = 0; index < channels.length && index < 32; index++) {
            boolean allowed = read ? gates.canRead(roles, channels[index])
                    : gates.canSend(roles, channels[index]);
            if (allowed) {
                mask |= 1 << index;
            }
        }
        return mask;
    }

    /**
     * Every sender id under a mute right now, for a moderator's menus.
     * A store that cannot be read names nobody, which only costs the
     * menu its foreknowledge — the server still answers the command.
     */
    private static List<UUID> mutedSenders(EntityPlayerMP moderator) {
        try {
            List<ChatMuteEntry> active = ChatMuteStorage.get(moderator.worldObj)
                    .getActiveMutes(System.currentTimeMillis());
            List<UUID> ids = new ArrayList<UUID>(active.size());
            for (ChatMuteEntry mute : active) {
                ids.add(mute.getAccountId());
            }
            return ids;
        } catch (RuntimeException exception) {
            noteStorageFailure("the mute list", exception);
            return Collections.emptyList();
        }
    }

    /**
     * Sends every online moderator their access again: what a mute or an
     * unmute calls, so their menus follow the store the moment it
     * changes. Nobody else is sent anything.
     */
    public static void sendAccessToModerators() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return;
        }
        List<LostTalesChatAccessPacket.RoleHolder> holders = roleHolders(null);
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online =
                server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP player : online) {
            if (player != null && LostTalesPermissions.has(player,
                    LostTalesCapability.CHAT_MODERATE)) {
                sendAccess(player, holders);
            }
        }
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
            int mask = ChatChannelPolicy.playedRoles(player);
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
     * Sends one line to everyone it resolved to and records who was
     * sent it, and who may still be shown it. The sender, when there is
     * one, is the only recipient to get the line under their own private
     * name for it; everyone else gets it without. The history is written
     * from the list the message actually went to, so a reply to it is
     * checked against who was sent it rather than against who would be
     * sent one now; {@code party} and {@code factionId} are the
     * membership the line was routed by, which is what a later replay
     * asks of the player it is shown to.
     */
    private static void deliver(LostTalesChatMessagePacket packet,
                                EntityPlayerMP sender,
                                ChatChannelPolicy.Routing routing,
                                UUID authorId, String identityName) {
        LostTalesChatMessagePacket shared = packet.withoutEcho();
        for (EntityPlayerMP recipient : routing.recipients) {
            LostTalesNetworkHandler.CHANNEL.sendTo(
                    sender != null && recipient == sender ? packet : shared,
                    recipient);
        }
        ChatHistory.record(packet.getMessageId(), authorId, identityName,
                packet.withoutEcho(), shared, routing.recipientIds(), routing.audience);
    }

    /**
     * Replays to a player who has just joined the recent messages they
     * are entitled to, oldest first and in batches, exactly as they
     * would have been sent them at the time. Who they are — account,
     * played character and its faction, party, the channels they may
     * read — is read from the live server here and nowhere else, and
     * the history decides message by message against what it recorded
     * when each was sent.
     */
    public static void sendHistory(EntityPlayerMP player) {
        if (player == null || player.worldObj == null
                || player.worldObj.isRemote) {
            return;
        }
        RoleplayCharacter character = CharacterActiveResolver.get(player);
        String factionId = character == null ? ""
                : LotrCharacterAdapter.normalizeFactionId(
                        character.getStartingFactionId());
        Party party = PartyService.getInstance()
                .getPartyForActiveCharacter(player);
        int roles = ChatAccountRoleResolver.resolve(player,
                character == null ? null : character.getCharacterId());
        ChatChannelGates gates = ChatChannelGates.current();
        List<ChatChannel> readable = new ArrayList<ChatChannel>();
        boolean console = ChatChannelPolicy.readsConsole(player);
        for (ChatChannel channel : ChatChannel.values()) {
            if (gates.canRead(roles, channel)
                    && (channel != ChatChannel.CONSOLE || console)) {
                readable.add(channel);
            }
        }
        List<LostTalesChatMessagePacket> lines = ChatHistory.replayFor(
                new ChatHistory.Requester(player.getUniqueID(), factionId,
                        character == null ? 0L : character.getCreationTimestamp(),
                        party == null ? null : party.getPartyId(), readable),
                ChatMessageIds.NONE);
        int packets = 0;
        for (int from = 0; from < lines.size();
             from += LostTalesChatHistorySyncPacket.MAX_MESSAGES) {
            LostTalesNetworkHandler.CHANNEL.sendTo(
                    new LostTalesChatHistorySyncPacket(lines.subList(from,
                            Math.min(lines.size(), from
                                    + LostTalesChatHistorySyncPacket.MAX_MESSAGES))),
                    player);
            packets++;
        }
        // One line per login, so a replay that went missing can be told
        // apart from one that was never sent.
        FMLLog.info("[%s] Replayed %d of %d kept chat lines in %d packets to %s",
                LostTalesMetaData.MOD_ID, lines.size(), ChatHistory.size(),
                packets, player.getCommandSenderName());
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
