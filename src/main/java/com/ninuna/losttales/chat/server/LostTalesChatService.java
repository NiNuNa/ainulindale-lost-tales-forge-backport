package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.chat.ChatAction;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatCommandText;
import com.ninuna.losttales.chat.ChatFellowship;
import com.ninuna.losttales.chat.ChatChannelAccess;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatChannelIconCatalog;
import com.ninuna.losttales.chat.ChatChannelScope;
import com.ninuna.losttales.chat.ChatChannelSuggester;
import com.ninuna.losttales.chat.ChatCodeNames;
import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.ChatRolePresentation;
import com.ninuna.losttales.chat.ChatRecipientRule;
import com.ninuna.losttales.chat.ChatSpeechRange;
import com.ninuna.losttales.chat.ChatTypedText;
import com.ninuna.losttales.chat.emoji.ChatForeignEmoji;
import com.ninuna.losttales.chat.moderation.ChatAuditLog;
import com.ninuna.losttales.chat.moderation.ChatMuteDurations;
import com.ninuna.losttales.chat.moderation.ChatMuteEntry;
import com.ninuna.losttales.chat.moderation.ChatMuteStorage;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareReference;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.chat.share.ChatShowcase;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.server.CharacterJoin;
import com.ninuna.losttales.character.server.KnownAccounts;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.state.CharacterLastSeen;
import com.ninuna.losttales.character.identity.PlayableIdentity;
import com.ninuna.losttales.character.identity.PlayableIdentityResolver;
import com.ninuna.losttales.character.server.CharacterActiveResolver;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.character.validation.CharacterNames;
import com.ninuna.losttales.compat.discord.DiscordAvatarUrl;
import com.ninuna.losttales.compat.discord.DiscordBridgePolicy;
import com.ninuna.losttales.compat.discord.DiscordMessageSanitizer;
import com.ninuna.losttales.compat.discord.DiscordPostName;
import com.ninuna.losttales.compat.discord.LostTalesDiscordBridge;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerStorage;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerVisibilityPolicy;
import com.ninuna.losttales.mapmarker.LostTalesWaystonePlacers;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatAccessPacket;
import com.ninuna.losttales.network.packet.LostTalesChatConsoleSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatHistorySyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.network.packet.LostTalesChatReactionSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import com.ninuna.losttales.network.packet.LostTalesChatTypingSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatUpdatePacket;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.quest.LostTalesQuestShareResolver;
import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.permission.LostTalesPermissionCatalog;
import com.ninuna.losttales.permission.LostTalesPermissions;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import com.ninuna.losttales.world.map.waypoint.LostTalesWaypointFastTravelPolicy;
import com.ninuna.losttales.chat.profanity.ChatProfanityCatalog;
import com.ninuna.losttales.chat.ChatNarrator;
import com.ninuna.losttales.chat.ChatUnseenVoice;
import com.ninuna.losttales.accessory.effect.AccessoryEffectService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

/**
 * The chat on the server: lines, whispers, typing, edits, deletions and
 * reactions, the Server Log and the replays. Who a line reaches and how
 * it reads is decided here from the server's own state, never the client's.
 */
public final class LostTalesChatService {
    private LostTalesChatService() {}

    /**
     * Validates and distributes one message a player sent, exactly as
     * the request names it:
     * <ul>
     * <li>the share references pair, in order, with the share tokens
     * found in the message; an item slot is re-read from the sender's
     * live inventory, a marker id from world data under the sender's
     * own visibility and visits, and a quest from the sender's own
     * running quests. The name typed in a token never decides what is
     * attached; every reader sees the thing's own name. A token that
     * cannot be verified is delivered as the literal text the sender
     * typed;</li>
     * <li>a whisper goes to the sender and the one online account it
     * names, each told who the other party is, and to the identity of
     * that account it names — a conversation is with a person as they
     * present themselves, so one player's characters are separate
     * threads; an unknown name or an identity the account does not
     * hold is refused with a notice;</li>
     * <li>the identity is the one the line names, the one the copy it
     * was typed in speaks as: one of the sender's own characters — never
     * anyone else's — or the character played. It signs the line and
     * decides which faction a faction line speaks to, whose readers are
     * those who read as a character in that faction. Proximity and
     * Fellowship speak as the character played, and the fellowship is
     * that character's. A line signed as the Narrator needs the
     * capability;</li>
     * <li>the reply reference is honoured only while the message it
     * names is within the log's reach <em>and</em> this sender may read
     * it now ({@link ChatHistory#mayRead}), so naming an id can never
     * quote back a message they may not see; one that fails either test
     * is dropped with a notice;</li>
     * <li>the echo nonce is the sender's own name for the message,
     * handed back to them alone so the line they showed the moment
     * they typed it is replaced by the delivered copy;</li>
     * <li>an action ({@code /me}) is taken only in an in-character
     * channel, its words checked as a message's and ended with a full
     * stop ({@link ChatAction#sentence}); a forward is an action exactly
     * when the message it carries on is one.</li>
     * </ul>
     */
    public static void send(EntityPlayerMP sender,
                            LostTalesChatSendPacket request) {
        if (sender == null || request == null) {
            return;
        }
        ChatHistory.Forwardable forward = null;
        if (request.getForwardOf() != ChatMessageIds.NONE) {
            // A forward carries on a message the sender may read, in the
            // words, author and place the server holds; everything else
            // about the line is decided as for one typed there.
            forward = ChatHistory.forwardable(request.getForwardOf(),
                    requesterFor(sender));
            if (forward == null) {
                sender.addChatMessage(new ChatComponentTranslation(
                        "chat.losttales.forward.unavailable"));
                return;
            }
        }
        send(sender, request.getChannel(),
                forward == null ? request.getMessage() : forward.text,
                request.getReferences(), request.getTarget(),
                request.getIdentityKind(),
                request.getIdentityCharacterId(),
                request.isNarrating(),
                request.getReplyToMessageId(), request.getTargetIdentity(),
                request.getEchoNonce(), request.getTargetCharacterId(),
                request.quotesUnkept(), forward,
                forward == null ? request.isAction() : forward.action,
                forward == null ? request.getTyped() : "",
                request.getRange());
    }

    private static void send(EntityPlayerMP sender,
                             ChatChannel channel, String message,
                             List<ChatShareReference> references,
                             String target, int identityKind,
                             UUID identityCharacterId, boolean narrating,
                             long replyToMessageId, String requestedIdentity,
                             long echoNonce, UUID targetCharacterId,
                             boolean quotesUnkept,
                             ChatHistory.Forwardable forward,
                             boolean action, String typed,
                             ChatSpeechRange askedRange) {
        String targetIdentity = requestedIdentity == null ? "" : requestedIdentity;
        if (sender == null || sender.worldObj == null
                || sender.worldObj.isRemote || channel == null || message == null) {
            return;
        }
        // The line passes the pipeline's stages in their order
        // (ChatLinePipeline): the gate first.
        ChatLinePipeline.Source source = narrating
                && ChatRolePresentation.isInCharacter(channel)
                ? ChatLinePipeline.Source.NARRATOR : ChatLinePipeline.Source.PLAYER;
        if (action && forward == null && !ChatAction.takesActions(channel)) {
            // In a console /me is the game's own command, which never
            // arrives here; a request that says otherwise is not this
            // client's to make.
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.action.console"));
            return;
        }
        // A whisper is for a person the world knows, here or away, as the
        // identity of theirs it names (CHAT-31): one away reads it the next
        // time they read as that identity, from the history.
        Whispered whispered = null;
        EntityPlayerMP whisperTarget = null;
        String whisperIdentity = "";
        UUID whisperCharacterId = null;
        if (channel.getRecipientRule() == ChatRecipientRule.WHISPER) {
            whispered = addressWhisper(sender, target, targetIdentity,
                    targetCharacterId);
            if (whispered == null
                    || whispered.account.equals(sender.getUniqueID())) {
                // A name no account or character of this world goes by
                // reaches nobody, and nobody whispers to themselves.
                sender.addChatMessage(new ChatComponentTranslation(
                        whispered != null
                                ? "chat.losttales.whisper.self"
                                : "chat.losttales.whisper.unavailable"));
                return;
            }
            if (!whispered.mayRead(channel)) {
                // One who may not read whispers: nothing to talk with.
                sender.addChatMessage(new ChatComponentTranslation(
                        "chat.losttales.whisper.unavailable"));
                return;
            }
            whisperTarget = whispered.online;
            whisperIdentity = whispered.addressed.name;
            whisperCharacterId = whispered.addressed.characterId;
        }

        // The line names who it speaks as: the copy it was typed in. A
        // character the sender does not own is refused rather than sent
        // as someone else.
        boolean roleplaying = ChatRolePresentation.isInCharacter(channel);
        // Nobody speaks in character before they have a character, nor
        // while a held switch journal keeps them a ghost (CHAR-45).
        String notInCharacter = roleplaying ? CharacterJoin.inCharacterRefusal(sender) : null;
        if (notInCharacter != null) {
            sender.addChatMessage(new ChatComponentTranslation(notInCharacter));
            return;
        }
        ChatIdentitySelection.Worn named = ChatIdentitySelection.worn(sender,
                channel, identityKind, identityCharacterId);
        if (roleplaying && (!PlayableIdentityResolver.resolve(sender).isAvailable()
                || named.refused)) {
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.identity.unavailable"));
            return;
        }
        if (roleplaying && narrating && !ChatIdentitySelection.mayNarrate(sender)) {
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.narrator.unavailable"));
            return;
        }
        Speaking speaking = speaking(sender, channel, target, named.character);
        RoleplayCharacter worn = speaking.worn;
        boolean narrator = roleplaying && narrating;
        Fellowship fellowship = speaking.fellowship;
        String factionId = speaking.factionId;
        int wornRoles = speaking.roles;
        String refusal = speaking.refusal;
        if (refusal != null) {
            if (ChatChannelPolicy.isGateRefusal(refusal)) {
                // The client only offers the page while the server last said
                // it may; tell it again so a lost role loses the page.
                sendAccess(sender);
            }
            sender.addChatMessage(new ChatComponentTranslation(refusal));
            return;
        }
        // The mute: a muted account sends nothing anywhere but its own
        // console; the Discord relay sits behind it, so nothing leaks out
        // either.
        if (channel.getRecipientRule() != ChatRecipientRule.SELF) {
            ChatMuteEntry mute = activeMute(sender);
            if (mute != null) {
                tellMuted(sender, mute);
                return;
            }
        }
        // The rate limit was asked at the door (CHAT_MESSAGE). Then the
        // words: an action is kept as the sentence it reads as, and an
        // empty one is refused like an empty message.
        message = forward != null ? message
                : ChatLinePipeline.words(message, action);
        if (message == null || !ChatMessageValidator.isValid(message)) {
            return;
        }
        if (roleplaying) {
            CharacterLastSeen.saw(sender.worldObj,
                    worn == null ? sender.getUniqueID() : worn.getCharacterId());
        }
        // A line heard nearby carries as far as its range; every other
        // line is talk, which reaches whoever its channel reaches.
        ChatSpeechRange range = ChatSpeechRange.forChannel(channel, askedRange);

        String accountName = sender.getGameProfile() == null
                ? sender.getCommandSenderName()
                : sender.getGameProfile().getName();
        // What makes a line an account line — the head it wears, the
        // skin it is drawn with — is the worn identity, not the channel.
        // Whether the sender's roles are tagged on it, and what colours
        // the name, is the channel's: out of character the roles show,
        // in character nobody wears a role (ChatRolePresentation).
        // The Narrator is nobody's account, whatever the sender plays.
        boolean accountLine = worn == null && !narrator;
        String identityName = narrator ? ChatNarrator.NAME
                : accountLine ? accountName
                : characterNameOrFallback(worn, accountName);
        LostTalesChatPresentationResolver.Presentation presentation =
                LostTalesChatPresentationResolver.resolve(sender, worn);
        // The Narrator's voice over the worn character: its name, its
        // parchment, its mark, no title and no faction; the line is
        // still the worn character's for routing and for the record.
        String skinId = narrator ? ChatNarrator.SKIN_ID
                : accountLine ? "" : worn.getSkinId();
        String title = accountLine || narrator ? "" : presentation.title;
        String titleFaction = accountLine || narrator ? "" : presentation.factionId;
        // A forward shares what its message shared; a line of the
        // sender's own shares what the server finds on them now.
        List<ChatShowcase> showcases = forward != null ? forward.showcases
                : resolveShowcases(sender, message, references);
        // The roles the line wears are the account's and the worn
        // character's own: a character-scoped role shows on that
        // character's lines and on nobody else's.
        int roles = ChatRolePresentation.rolesShown(channel, wornRoles);
        int ivory = ChatRolePresentation.unassignedColor();
        int nameColor = narrator ? ChatNarrator.color()
                : ChatRolePresentation.nameColor(channel, roles, accountLine,
                        presentation.nameColor);
        // A quote or a forward shows its line again only where everyone
        // reading could already read it: a whisper quoted into Global would
        // carry its words to everyone online (ChatHistory.mayShowTo).
        String replyScope = ChatChannelPolicy.scopeValueOf(
                channel, fellowship, factionId);
        ChatHistory.Requester reader = requesterFor(sender);
        ChatHistory.Requester partner = whispered == null ? null
                : whisperTarget != null ? requesterFor(whisperTarget)
                : whispered.absentRequester(sender.worldObj);
        ChatReplyReference reply;
        if (forward != null) {
            if (!ChatHistory.mayShowTo(forward.messageId, channel, replyScope,
                    reader, partner)) {
                sender.addChatMessage(new ChatComponentTranslation(
                        "chat.losttales.forward.private"));
                return;
            }
            reply = forward.reference;
        } else if (replyToMessageId != ChatMessageIds.NONE) {
            // A line heard nearby may be answered where it was said; its
            // quote then reaches only those who heard it (deliver).
            reply = ChatHistory.quoteFor(replyToMessageId, reader, channel,
                    replyScope);
            if (reply.exists() && !ChatHistory.mayQuoteIn(replyToMessageId,
                    channel, replyScope, reader, partner)) {
                reply = ChatReplyReference.NONE;
            }
            if (!reply.exists() && channel == ChatChannel.SERVER_LOG) {
                reply = consoleQuote(sender, replyToMessageId);
            }
            if (!reply.exists()) {
                sender.addChatMessage(new ChatComponentTranslation(
                        "chat.losttales.reply.unavailable"));
            }
        } else if (quotesUnkept) {
            // A quote of a line no server named or holds: a line of the
            // client's own, a command's echo. Who said it and what it said
            // would be only the sender's word, so nothing of it is taken:
            // it shows as a message no longer kept, with no author.
            reply = ChatReplyReference.UNKEPT;
        } else {
            reply = ChatReplyReference.NONE;
        }
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                channel, sender.getUniqueID(), identityName,
                accountName,
                title,
                accountLine || narrator ? ivory : presentation.titleColor,
                nameColor,
                message, System.currentTimeMillis(),
                skinId,
                showcases,
                titleFaction,
                // A whisper names its partner from the start: the packet
                // refuses a partner-less whisper, so the sender's copy is
                // built with the target's name and the target's copy is
                // derived from it below.
                whispered == null ? "" : whispered.accountName,
                roles, accountLine,
                // One id for the message, not one per copy: a whisper is
                // the same message to both people, and anything naming
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
                worn == null ? null : worn.getCharacterId())
                // An action is what the speaker did: the words stay the
                // message and the line says what they are.
                .withAction(action)
                // A character a copy speaks as while another is played is
                // here, but not in the world (CHAT-05); the Narrator is
                // nobody's body.
                .withNotInWorld(!narrator && notPlayed(sender, worn))
                // Said nearby while the One Ring conceals the speaker: an
                // unseen voice to every reader but the speaker and staff
                // (CHAT-84).
                .withUnseen(ChatUnseenVoice.applies(channel,
                        AccessoryEffectService.isConcealed(sender), narrator))
                // How far it carries where it is heard nearby (CHAT-37).
                .withSpeechRange(range)
                // Which conversation of a scoped channel this is: the
                // faction it was spoken to, or the fellowship it was spoken
                // in. The client files it under that conversation's page
                // and shows it under no other.
                .withScope(replyScope)
                // Whom its @names reach among the conversation's members,
                // here or gone, players or Discord members: kept with the
                // line, so every client shows and pings the same people
                // from the one record, live and in every replay.
                // A forward reaches nobody by name: the names in it were
                // the original's to call.
                .withNamedPlayers(forward != null
                        ? Collections.<ChatNamedPlayer>emptyList()
                        : ChatMentionTargets.of(sender,
                                LostTalesServerBroadcastHook.namedAs(sender, worn),
                                channel, replyScope,
                                whispered == null ? null : whispered.named(),
                                message))
                // Which roles its @names call, read in every language the
                // server knows: a client pings its player by these.
                .withMentionedRoles(ChatLinePipeline.rolesOf(source,
                        forward != null, message))
                // What the sender typed, kept with their own copy so they
                // can edit the line later (CHAT-83); every other copy
                // leaves it off (withoutEcho).
                .withTyped(typed);

        FMLLog.info(action ? "[losttales/chat/%s] * %s (%s) %s%s%s"
                        : "[losttales/chat/%s] <%s (%s)> %s%s%s",
                logName(channel, replyScope), identityName, accountName,
                ChatMessageValidator.logged(message),
                whispered == null ? "" : " -> " + whispered.accountName,
                showcases.isEmpty() ? ""
                        : " [shared: " + showcases.size() + "]");

        // Every accepted line is recorded before it goes anywhere, a
        // whisper included: the audit exists for what the live window
        // cannot reach back to, and a private line is exactly that.
        ChatAuditLog.logMessage(packet.getMessageId(),
                logName(channel, replyScope), sender.getUniqueID(), accountName,
                worn == null ? null : worn.getCharacterId(),
                identityName,
                whispered == null ? "" : whispered.accountName,
                message, action);
        if (whispered != null) {
            // Each side is told who the other party is, and the history
            // keeps both tellings: a replay hands each side its own.
            // Each copy also says which of the receiving side's own
            // characters it is held as, and which of the other party's
            // it is with, so the clients keep one thread per pair of
            // identities and a reply is addressed by id.
            UUID wornId = worn == null ? null : worn.getCharacterId();
            packet = packet.withConversation(wornId, whisperCharacterId);
            // A Narrator line reaches the other party signed by the
            // Narrator alone, filed under the conversation all the same.
            LostTalesChatMessagePacket partnerCopy = withPartner(
                    packet.withoutEcho(), accountName, identityName)
                    .withConversation(whisperCharacterId, wornId)
                    .narratedForOthers();
            // A forward of an unseen voice's line names the voice to
            // whichever of the two may not know who spoke.
            LostTalesNetworkHandler.CHANNEL.sendTo(ChatReaderView.forReader(
                    packet, sender.getUniqueID(), moderates(sender), true),
                    sender);
            // Someone away is told nothing now: the history keeps the line
            // for them, and their join replay brings it (CHAT-31). Nothing
            // tells the sender whether they are here, so Invisible stays so.
            if (whisperTarget != null) {
                LostTalesNetworkHandler.CHANNEL.sendTo(ChatReaderView.forReader(
                        partnerCopy, whisperTarget.getUniqueID(),
                        moderates(whisperTarget), true), whisperTarget);
            }
            List<UUID> pair = Arrays.asList(sender.getUniqueID(),
                    whispered.account);
            ChatHistory.record(packet.getMessageId(),
                    sender.getUniqueID(), identityName,
                    packet.withoutEcho(), partnerCopy, pair,
                    ChatHistory.Audience.accounts(pair, false));
            if (narrator) {
                noteNarration(accountName, channel, replyScope,
                        packet.getMessageId(), null);
            }
            return;
        }
        deliver(packet, sender, ChatChannelPolicy.route(sender, channel,
                fellowship, factionId, range), sender.getUniqueID(), identityName);
        if (narrator) {
            noteNarration(accountName, channel, replyScope,
                    packet.getMessageId(),
                    fellowship == null ? null : fellowship.getName());
        }
        // Out to Discord through the channel's link, when it has one
        // that posts. Only a player's own line in a channel that may be
        // bridged ever leaves: the policy is asked here, before the
        // bridge is, so no link can carry a private channel or send
        // a Discord line back. The bridge posts the line under the
        // sender's name — a line spoken as a character carries the title
        // the game shows — with the account's head as the picture, since
        // a character's skin is a resource of the game and not a picture
        // Discord can fetch; emoji shortcodes go as the Unicode emoji
        // Discord renders, share tokens as the text they were typed as.
        // The Narrator tells its own lines and every action, as in the
        // game: they go under its name alone, an action as the sentence
        // its speaker's name opens; a forwarded action stays under its
        // forwarder's name, opened by its author's.
        // The message's own id and the reply it resolved travel with it,
        // so the post can be linked to its Discord copy and a reply can
        // point at the Discord original; so does the sender's id, since
        // the sender alone is told when the post is slow or will not
        // arrive.
        if (DiscordBridgePolicy.relaysOutbound(channel)) {
            boolean forwarded = reply != null && reply.isForward();
            boolean told = narrator || (packet.isAction() && !forwarded);
            LostTalesDiscordBridge.getInstance().relayToDiscord(channel,
                    factionId,
                    // Worded for each linked channel in its own language.
                    told ? DiscordPostName.NARRATOR
                            : accountLine ? DiscordPostName.plain(identityName)
                            : presentation.discordName(identityName),
                    // The Narrator is nobody's account: its post, an action
                    // among them, wears the webhook's own picture, never
                    // the narrator's head.
                    told ? "" : DiscordAvatarUrl.forPlayer(sender),
                    packet.isAction() ? DiscordMessageSanitizer.outboundAction(
                            forwarded ? reply.getAuthor() : identityName, message)
                            : DiscordMessageSanitizer.outbound(message),
                    packet.getMessageId(), reply, sender.getUniqueID(),
                    // A forward pings nobody on Discord either.
                    forwarded ? Collections.<ChatNamedPlayer>emptyList()
                            : packet.getNamedPlayers());
        }
    }

    /**
     * The game's own {@code /me}, typed in a console or by anything but the
     * chat's bar: an action in OOC Chat under the account (CHAT-37), never
     * in Global, through the very send a typed line takes.
     */
    public static void actOutOfCharacter(EntityPlayerMP sender, String words) {
        send(sender, ChatChannel.OOC, words,
                Collections.<ChatShareReference>emptyList(), "",
                LostTalesChatSendPacket.IDENTITY_ACCOUNT, null, false,
                ChatMessageIds.NONE, "", 0L, null, false, null, true,
                ChatTypedText.ACTION_PREFIX + words, ChatSpeechRange.TALK);
    }

    /**
     * A message from Discord, delivered by the bridge on the server
     * thread into the channel its link names — the game's own Discord
     * channel, or any other channel that may be bridged; for Faction
     * chat, the faction the link is for. It reaches everyone the
     * channel's own rule reaches, under the Discord display name, with
     * the sender id that stands for that member
     * ({@link LostTalesChatMessagePacket#discordSenderId}) — so a client
     * can ignore them, and a mute stored against that id silences them
     * here, silently, exactly as an account's mute does. The member is
     * held to the budget a player's lines have, and their mentions to the
     * pings a player may make on Discord
     * ({@link LostTalesDiscordBridge#pingsFromDiscord}). Nothing here
     * posts it to Discord: the bridge itself carries it on to the game
     * channel's other Discord channels, never back to its own. A Discord
     * member is an
     * external identity and wears no character. The bridge has already
     * sanitised and bounded the text, and resolved the quote when the
     * message answers one — a Discord reply is shown exactly as a
     * player's reply is. The line is recorded like any other, so
     * players can reply to it in turn; its author id is the bridge's
     * own, which no account holds, so no player edits it or takes it
     * back as its author. The member's own edit or deletion on Discord
     * follows it here ({@link #editFromDiscord}, {@link #deleteFromDiscord}),
     * and a moderator who may read it can remove it ({@link #delete}).
     * {@code message} is the line in the server's words, what the
     * history, the logs and the audit keep; {@code bodyJson} is the same
     * line with its marks as words each game translates, empty for a line
     * without one. Answers with the id the message was distributed under,
     * or {@link ChatMessageIds#NONE} when nothing went out, so the bridge
     * can link the line to the Discord message it came from.
     */
    public static long sendFromDiscord(ChatChannel channel, String factionScope,
                                       String displayName, String guildName,
                                       String discordUserId, String message,
                                       String bodyJson,
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
        if (!discordMemberWithinBudget(senderId,
                LostTalesRequestRateLimiter.RequestType.CHAT_MESSAGE, "Discord line")) {
            return ChatMessageIds.NONE;
        }
        int ivory = LostTalesColors.rgb(LostTalesColors.HUD_LABEL);
        long messageId = ChatMessageIdAllocator.next();
        // Routed by the channel's own rule with no sender behind the
        // line: everyone for a global channel, the operators for the
        // staff channel, the faction's members for a faction link.
        ChatChannelPolicy.Routing routing = ChatChannelPolicy.route(null,
                channel, null, factionScope == null ? "" : factionScope);
        // The member's Discord server stands where a character's title
        // does, in the tone of what is said about a line: Nils, of The
        // Shire.
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                channel, senderId, displayName,
                displayName, guildName == null ? "" : guildName,
                LostTalesColors.rgb(LostTalesColors.ROSE_GRAY), ivory, message,
                System.currentTimeMillis(), "", null, "", "", 0, true,
                messageId, reply)
                .withScope(factionScope == null ? "" : factionScope)
                .withServerBody(bodyJson, LostTalesDiscordBridge.getInstance()
                        .pingsFromDiscord(senderId, ChatMentionTargets.ofDiscordLine(
                                channel, factionScope, routing.recipients, message)));
        FMLLog.info("[losttales/chat/%s] <%s (discord)> %s",
                logName(channel, factionScope), displayName,
                ChatMessageValidator.logged(message));
        deliver(packet, null, routing,
                LostTalesChatMessagePacket.DISCORD_SENDER_ID, displayName);
        // Recorded under the member's own sender id, the same id a mute
        // names them by, so the audit and the moderation tools agree on
        // who a Discord line is from.
        ChatAuditLog.logDiscordMessage(messageId, logName(channel, factionScope),
                senderId, displayName, message);
        return messageId;
    }

    /**
     * Says a line as the Server in a fellowship's conversation: to the
     * members playing now, and kept for the members as their own lines are.
     * It names nobody, so it pings nobody. {@code bodyJson} is the line as
     * words each game translates, empty for none, {@code message} the
     * server's own words for it; {@code showcases} are the server's own
     * links under the line's tokens.
     */
    public static long sayToFellowship(Fellowship fellowship, String message,
                                       String bodyJson,
                                       List<ChatShowcase> showcases) {
        MinecraftServer server = MinecraftServer.getServer();
        if (fellowship == null || !ChatMessageValidator.isValid(message)
                || server == null || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return ChatMessageIds.NONE;
        }
        String scope = fellowship.getFellowshipId().toString();
        long messageId = ChatMessageIdAllocator.next();
        ChatChannelPolicy.Routing routing = ChatChannelPolicy.route(null,
                ChatChannel.FELLOWSHIP, fellowship, "");
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                ChatChannel.FELLOWSHIP, LostTalesChatMessagePacket.SERVER_SENDER_ID,
                LostTalesServerBroadcastHook.SERVER_NAME,
                LostTalesServerBroadcastHook.SERVER_NAME, "",
                LostTalesColors.rgb(LostTalesColors.HUD_LABEL),
                ChatChannel.CONSOLE.getDisplayColor(), message,
                System.currentTimeMillis(), "", showcases, "", "", 0, true,
                messageId, ChatReplyReference.NONE)
                .withScope(scope)
                .withServerBody(bodyJson,
                        Collections.<ChatNamedPlayer>emptyList());
        FMLLog.info("[losttales/chat/%s] <%s> %s",
                logName(ChatChannel.FELLOWSHIP, scope),
                LostTalesServerBroadcastHook.SERVER_NAME,
                ChatMessageValidator.logged(message));
        deliver(packet, null, routing, LostTalesChatMessagePacket.SERVER_SENDER_ID,
                LostTalesServerBroadcastHook.SERVER_NAME);
        return messageId;
    }

    /**
     * A Discord member is typing, or has stopped, in the channel a linked
     * Discord channel is read into: everyone their line would reach is
     * told, as a player's typing is told. Never
     * for a muted member, nor while the server has typing switched off.
     */
    public static void typingFromDiscord(ChatChannel channel, String factionScope,
                                         String displayName, String discordUserId,
                                         boolean typing) {
        MinecraftServer server = MinecraftServer.getServer();
        if (!LostTalesConfig.chatTypingIndicators || !LostTalesConfig.discordEnabled
                || !DiscordBridgePolicy.acceptsInbound(channel)
                || displayName == null || displayName.length() == 0
                || server == null || server.getConfigurationManager() == null
                || isDiscordSenderMuted(server,
                        LostTalesChatMessagePacket.discordSenderId(discordUserId))
                || !discordMemberWithinBudget(
                        LostTalesChatMessagePacket.discordSenderId(discordUserId),
                        LostTalesRequestRateLimiter.RequestType.CHAT_TYPING,
                        "Discord typing")) {
            return;
        }
        String scope = factionScope == null ? "" : factionScope;
        ChatChannelPolicy.Routing routing = ChatChannelPolicy.route(null, channel, null, scope);
        String scopeValue = ChatChannelPolicy.scopeValueOf(channel, null, scope);
        for (EntityPlayerMP recipient : routing.recipients) {
            LostTalesNetworkHandler.CHANNEL.sendTo(new LostTalesChatTypingSyncPacket(
                    channel, "", displayName, false, typing, scopeValue, ""),
                    recipient);
        }
    }

    /**
     * What the logs call a line's conversation: its code name, so a
     * faction's line is logged under the faction ({@code gondor}).
     */
    private static String logName(ChatChannel channel, String scope) {
        String name = ChatCodeNames.of(channel,
                channel == ChatChannel.FACTION ? scope : "");
        return name == null ? channel.getId() : name;
    }

    /**
     * Whether a Discord member is still inside the budget a player's
     * request of {@code type} has: a member is held to what a player is
     * held to, by the sender id the chat signs them with. Past it what
     * they do is dropped without a word, as a muted member's is, and the
     * server's log says so now and then.
     */
    static boolean discordMemberWithinBudget(UUID senderId,
                                             LostTalesRequestRateLimiter.RequestType type,
                                             String what) {
        return ChatLinePipeline.withinBudget(ChatLinePipeline.Source.DISCORD,
                senderId, type, what);
    }

    /**
     * Whether a mute is stored against a Discord member's sender id.
     * Mutes live in the overworld's storage like every other; a world
     * that cannot be read silences nobody.
     */
    private static boolean isDiscordSenderMuted(MinecraftServer server,
                                                UUID senderId) {
        return ChatLinePipeline.muteOf(senderId) != null;
    }

    /**
     * Whether a store's failure has been reported this server session.
     * A world whose chat stores cannot be read degrades — a mute is not
     * enforced, a character name is not resolved — and that is said
     * once at warning level rather than once per line or not at all.
     */
    private static boolean storageFailureLogged;

    static void noteStorageFailure(String what,
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

    /* ---- the Server Log ---- */

    /**
     * Records one administrative event and shows it at once to every
     * online player who may read the console
     * ({@link LostTalesCapability#CHAT_SERVER_LOG_READ}). The capability is
     * asked of each recipient here, and asked again of a joining player
     * before the kept entries are replayed to them; the client is never
     * the one deciding.
     */
    public static void console(ChatConsoleEvent.Kind kind,
                               ChatConsoleEvent.Severity severity, String actor,
                               String text) {
        console(kind, severity, actor, text, "");
    }

    /**
     * As above for a command, with the page the actor typed it in as
     * their client reported it ({@link ChatCommandContexts}); empty
     * when nothing was reported.
     */
    public static void console(ChatConsoleEvent.Kind kind,
                               ChatConsoleEvent.Severity severity, String actor,
                               String text, String context) {
        console(kind, severity, actor, text, context, null);
    }

    /**
     * As above for a report ({@link ChatConsoleEvent.Kind#REPORT}), with
     * what was reported.
     */
    public static void console(ChatConsoleEvent.Kind kind,
                               ChatConsoleEvent.Severity severity, String actor,
                               String text, String context,
                               ChatConsoleEvent.Report report) {
        console(kind, severity, actor, text, context, report, "");
    }

    /**
     * As above, {@code staffText} the entry as staff holding
     * {@code chat.moderate} read it where it names the speaker an unseen
     * voice hid (CHAT-84); every other reader is sent {@code text}.
     */
    static void console(ChatConsoleEvent.Kind kind,
                        ChatConsoleEvent.Severity severity, String actor,
                        String text, String context,
                        ChatConsoleEvent.Report report, String staffText) {
        if (kind == null || severity == null || text == null
                || text.trim().length() == 0) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        ChatConsoleEvent event;
        try {
            event = new ChatConsoleEvent(ChatMessageIdAllocator.next(),
                    System.currentTimeMillis(), kind, severity, actor, text,
                    context, actorIdentity(server, actor), report)
                    .withStaffText(staffText);
        } catch (IllegalArgumentException refused) {
            return;
        }
        ChatConsoleStream.record(event);
        if (server == null || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return;
        }
        // Staff read who an unseen voice was; every other reader the voice.
        LostTalesChatConsoleSyncPacket forStaff = new LostTalesChatConsoleSyncPacket(
                Collections.singletonList(event.forReader(true)));
        LostTalesChatConsoleSyncPacket forOthers = new LostTalesChatConsoleSyncPacket(
                Collections.singletonList(event.forReader(false)));
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online = server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP player : online) {
            if (player != null && ChatChannelPolicy.readsConsole(player)) {
                LostTalesNetworkHandler.CHANNEL.sendTo(
                        moderates(player) ? forStaff : forOthers, player);
            }
        }
    }

    /** The Server Log's kept entries as {@code player} is sent them ({@link ChatConsoleEvent#forReader}). */
    private static List<ChatConsoleEvent> consoleFor(EntityPlayerMP player,
                                                     List<ChatConsoleEvent> events) {
        boolean staff = moderates(player);
        List<ChatConsoleEvent> shown = new ArrayList<ChatConsoleEvent>(events.size());
        for (ChatConsoleEvent event : events) {
            shown.add(event.forReader(staff));
        }
        return shown;
    }

    /**
     * The account a console entry names as its actor, as the server
     * knows it right now: an actor does what the entry records while
     * they are on the server, so they are found among those online.
     * Null for the Server, for nobody, and for a name nobody online
     * carries.
     */
    private static ChatNamedPlayer actorIdentity(MinecraftServer server,
                                                 String actor) {
        String account = actor == null ? "" : actor.trim();
        if (server == null || account.length() == 0
                || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online = server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP player : online) {
            if (player != null && player.getGameProfile() != null
                    && account.equalsIgnoreCase(player.getGameProfile().getName())) {
                return LostTalesServerBroadcastHook.namedAccount(player);
            }
        }
        return null;
    }

    /**
     * Tells the Server Log who narrated a line, and where: one entry
     * per Narrator line, its actor the narrating account, its words the
     * link to the line and, for a fellowship's, the fellowship's name.
     * Every other copy of the line names nobody, so the console's readers
     * are the only ones who learn it. A whisper is named by its link
     * alone, never by the other party.
     */
    static void noteNarration(String account, ChatChannel channel,
                              String scope, long messageId,
                              String fellowshipName) {
        String link = ChatChannelSuggester.messageLink(channel, scope,
                messageId);
        String where = link != null ? link
                : "#" + logName(channel, scope);
        String fellowship = fellowshipName == null ? ""
                : fellowshipName.trim();
        console(ChatConsoleEvent.Kind.MODERATION, ChatConsoleEvent.Severity.INFO,
                account, "narrated " + where
                        + (fellowship.length() == 0 ? "" : " in " + fellowship));
    }

    /** Cleared with the rest of the server's chat state. */
    public static void clear() {
        storageFailureLogged = false;
    }

    /**
     * A Discord member's own edit, found by the bridge's sweep and
     * delivered on the server thread: the line is rewritten and told to
     * everyone who was sent it and may still read it, exactly as a
     * player's edit is, and in
     * the other Discord channels the line was carried on to. The bridge
     * signed the line with its own author id when it was recorded, which
     * is what allows the rewrite here and what stops any player from
     * making one. The member's own message is theirs and is not touched.
     * {@code message} and {@code bodyJson} are the new words as
     * {@link #sendFromDiscord} takes them.
     */
    public static void editFromDiscord(long messageId, String message,
                                       String bodyJson) {
        MinecraftServer server = MinecraftServer.getServer();
        if (!ChatMessageValidator.isValid(message) || server == null) {
            return;
        }
        ChatNamedPlayer author = ChatHistory.authorOf(messageId);
        // An edit puts new words in front of the readers, so a mute
        // refuses it, as it refuses a player's.
        if (author == null || isDiscordSenderMuted(server, author.getPlayerId())
                || !discordMemberWithinBudget(author.getPlayerId(),
                        LostTalesRequestRateLimiter.RequestType.CHAT_REVISION,
                        "Discord edit")) {
            return;
        }
        ChatChannel channel = ChatHistory.channelOf(messageId);
        List<ChatNamedPlayer> named = LostTalesDiscordBridge.getInstance()
                .pingsFromDiscord(author.getPlayerId(), ChatMentionTargets.ofDiscordLine(
                        channel, ChatHistory.factionScopeOf(messageId),
                        onlineOf(ChatHistory.recipientsOf(messageId)), message));
        String body = bodyJson == null ? "" : bodyJson;
        Set<UUID> recipients = ChatHistory.applyEdit(messageId,
                LostTalesChatMessagePacket.DISCORD_SENDER_ID, message, body, named,
                Collections.<String>emptyList());
        if (recipients == null) {
            return;
        }
        // The readers are told the component the kept line holds now,
        // which is none where the history could not keep the one given.
        body = ChatHistory.bodyOf(messageId);
        FMLLog.info("[losttales/chat/discord] edited message %d: %s",
                Long.valueOf(messageId), ChatMessageValidator.logged(message));
        ChatAuditLog.logDiscordEdit(messageId, author.getPlayerId(),
                author.getAccount(), message);
        tellReaders(messageId, recipients,
                LostTalesChatUpdatePacket.edited(messageId, message, body, named,
                        Collections.<String>emptyList()));
        LostTalesDiscordBridge.getInstance().relayEdit(messageId, message);
    }

    /**
     * A Discord member's own deletion, on the same terms as an edit:
     * the line is taken back from everyone who was sent it, and from the
     * other Discord channels it was carried on to.
     */
    public static void deleteFromDiscord(long messageId) {
        ChatChannel saidIn = ChatHistory.channelOf(messageId);
        String saidToFaction = ChatHistory.factionScopeOf(messageId);
        ChatNamedPlayer author = ChatHistory.authorOf(messageId);
        Set<UUID> recipients = ChatHistory.remove(messageId,
                LostTalesChatMessagePacket.DISCORD_SENDER_ID);
        if (recipients == null) {
            return;
        }
        FMLLog.info("[losttales/chat/discord] deleted message %d",
                Long.valueOf(messageId));
        if (author != null) {
            ChatAuditLog.logDiscordDelete(messageId, author.getPlayerId(),
                    author.getAccount());
        }
        tellRecipients(recipients,
                LostTalesChatUpdatePacket.removed(messageId));
        LostTalesDiscordBridge.getInstance().relayDelete(messageId, saidIn,
                saidToFaction);
    }

    /**
     * Relays that the player is, or has stopped, typing into a channel
     * to everyone who would receive a message sent there now — never to
     * the sender — under the very same checks a message passes, but
     * silently: presence earns no notices. Off on the server, nothing is
     * relayed at all. The notice names the typist by account and by the
     * character they type as, so an ignore follows them as it follows
     * their lines (CHAT-61); the Narrator names nobody. Nearby it reaches
     * whoever {@code askedRange} reaches (CHAT-37), and a typist the One
     * Ring conceals is an unseen voice to all but staff (CHAT-84).
     */
    public static void typing(EntityPlayerMP sender, ChatChannel channel,
                              String target, boolean typing, int identityKind,
                              UUID identityCharacterId, boolean narrating,
                              String targetIdentity, UUID targetCharacterId,
                              ChatSpeechRange askedRange) {
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
        boolean roleplaying = ChatRolePresentation.isInCharacter(channel);
        ChatIdentitySelection.Worn named = ChatIdentitySelection.worn(sender,
                channel, identityKind, identityCharacterId);
        if (roleplaying && (!PlayableIdentityResolver.resolve(sender).isAvailable()
                || named.refused
                || (narrating && !ChatIdentitySelection.mayNarrate(sender)))) {
            return;
        }
        Speaking speaking = speaking(sender, channel, target, named.character);
        RoleplayCharacter worn = speaking.worn;
        String accountName = sender.getGameProfile() == null
                ? sender.getCommandSenderName()
                : sender.getGameProfile().getName();
        narrating = roleplaying && narrating;
        String identityName = narrating ? ChatNarrator.NAME
                : worn == null ? accountName
                : characterNameOrFallback(worn, accountName);
        // Who types, by id: the account and the character worn; the
        // Narrator is nobody's (CHAT-11).
        UUID typistAccount = narrating ? null : sender.getUniqueID();
        UUID typistCharacter = narrating || worn == null ? null
                : worn.getCharacterId();
        if (channel.getRecipientRule() == ChatRecipientRule.WHISPER) {
            Whispered whispered = addressWhisper(sender, target, targetIdentity,
                    targetCharacterId);
            EntityPlayerMP whisperTarget = whispered == null ? null
                    : whispered.online;
            if (whisperTarget != null && whisperTarget != sender
                    && whispered.mayRead(channel)
                    && ChatChannelPolicy.canSend(sender, channel,
                            ChatIdentitySelection.roles(sender))) {
                String recipientKey = whispered.addressed.characterId == null ? ""
                        : whispered.addressed.characterId.toString();
                // Typing shows where the whisper will land: with the
                // identity addressed, which shows it while it is read.
                LostTalesNetworkHandler.CHANNEL.sendTo(
                        new LostTalesChatTypingSyncPacket(channel, accountName,
                                identityName, narrating, typing, "", recipientKey)
                                .from(typistAccount, typistCharacter),
                        whisperTarget);
            }
            return;
        }
        // The same answer a send gets, so presence never promises a
        // message the channel would refuse.
        if (speaking.refusal != null) {
            return;
        }
        Fellowship fellowship = speaking.fellowship;
        String factionId = speaking.factionId;
        if (typing && DiscordBridgePolicy.relaysOutbound(channel)) {
            // A bound channel has readers on the other side too, and
            // Discord shows its own indicator when the bot says it is
            // typing. Nothing but presence crosses, and only through a
            // link that posts.
            LostTalesDiscordBridge.getInstance().relayTyping(channel, factionId);
        }
        String scope = ChatChannelPolicy.scopeValueOf(channel, fellowship,
                factionId);
        LostTalesChatTypingSyncPacket asTypist = new LostTalesChatTypingSyncPacket(
                channel, "", identityName, narrating, typing, scope, "")
                .from(typistAccount, typistCharacter);
        // Nearby, a typist the One Ring conceals is an unseen voice to all
        // but staff (CHAT-84), as their line will be.
        LostTalesChatTypingSyncPacket masked = ChatUnseenVoice.applies(channel,
                AccessoryEffectService.isConcealed(sender), narrating)
                ? LostTalesChatTypingSyncPacket.unseenVoice(channel, typing, scope)
                : null;
        for (EntityPlayerMP recipient : ChatChannelPolicy.route(
                sender, channel, fellowship, factionId,
                ChatSpeechRange.forChannel(channel, askedRange)).recipients) {
            if (recipient != sender) {
                LostTalesNetworkHandler.CHANNEL.sendTo(
                        masked != null && !moderates(recipient) ? masked : asTypist,
                        recipient);
            }
        }
    }

    /**
     * Who a line speaks as in a channel, where it goes and whether it
     * may: the one answer a send and its typing share.
     */
    private static final class Speaking {
        /** The character the line wears; null for the account. */
        final RoleplayCharacter worn;
        /** The fellowship a fellowship line goes to, one of the played character's; null elsewhere. */
        final Fellowship fellowship;
        /** The faction a faction line is spoken to: the worn character's, or Unaligned. */
        final String factionId;
        /** The roles held as the identity worn: the account's and the worn character's. */
        final int roles;
        /** Why the channel refuses the line; null when it takes it. */
        final String refusal;

        Speaking(RoleplayCharacter worn, Fellowship fellowship, String factionId, int roles,
                 String refusal) {
            this.worn = worn;
            this.fellowship = fellowship;
            this.factionId = factionId;
            this.roles = roles;
            this.refusal = refusal;
        }
    }

    /**
     * How {@code sender} speaks in {@code channel} as {@code named} (null
     * for the account), to the fellowship {@code target} names for a
     * fellowship line. The gate is asked with the roles the line will
     * show, so a character-scoped role opens the channel for the lines
     * that wear it and for no others.
     */
    private static Speaking speaking(EntityPlayerMP sender, ChatChannel channel,
                                     String target, RoleplayCharacter named) {
        RoleplayCharacter worn = ChatRolePresentation.isInCharacter(channel)
                ? named : null;
        Fellowship fellowship = channel.getAccess() == ChatChannelAccess.FELLOWSHIP_MEMBERSHIP
                ? ChatIdentitySelection.fellowship(sender, ChatFellowship.idOf(target))
                : null;
        String factionId = ChatChannelPolicy.factionOf(worn);
        int roles = ChatAccountRoleResolver.resolve(sender,
                worn == null ? null : worn.getCharacterId());
        return new Speaking(worn, fellowship, factionId, roles,
                ChatChannelPolicy.sendRefusal(channel, fellowship,
                        ChatIdentitySelection.playedId(sender), roles,
                        LostTalesPermissions.isOperator(sender),
                        ChatChannelPolicy.readsConsole(sender)));
    }

    /**
     * Rewrites one of {@code editor}'s own messages and tells everyone
     * who was sent it and may still read it. Silently does nothing when
     * the message is not theirs or has fallen out of the log's reach —
     * the client is told nothing it could learn from, since a refusal
     * that distinguished "not yours" from "no such message" would answer
     * questions about messages the asker never saw.
     *
     * <p>The new text passes the same validator a fresh message does,
     * and the author has to be able to send in that conversation now
     * ({@link #editRefusal}), so an edit is not a way around what a send
     * would have refused. A line already carried to Discord is corrected
     * there as well, through the id the bridge kept from its own post.</p>
     */
    public static void edit(EntityPlayerMP editor, long messageId,
                            String message) {
        edit(editor, messageId, message, "");
    }

    /** As above, keeping {@code typed}, what the editor typed for the new words. */
    public static void edit(EntityPlayerMP editor, long messageId,
                            String message, String typed) {
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
        ChatNamedPlayer author = ChatHistory.authorOf(messageId);
        ChatChannel channel = ChatHistory.channelOf(messageId);
        if (author == null || channel == null
                || !editor.getUniqueID().equals(author.getPlayerId())) {
            return;
        }
        // The new words reach the conversation's readers as a send's do,
        // so the author has to be able to speak there now: past its gate,
        // still in its fellowship, still speaking to its faction.
        // The edit is judged as the identity the line wore, while the
        // editor still holds it, else as the character played.
        String scope = ChatHistory.scopeOf(messageId);
        RoleplayCharacter wore = author.getCharacterId() == null ? null
                : ChatIdentitySelection.owned(editor, author.getCharacterId());
        Speaking speaking = speaking(editor, channel, scope,
                wore != null || author.getCharacterId() == null ? wore
                        : ChatIdentitySelection.played(editor));
        String refusal = editRefusal(channel, scope, speaking.refusal,
                ChatIdentitySelection.readsFaction(editor, scope) ? scope
                        : speaking.factionId);
        if (refusal != null) {
            if (ChatChannelPolicy.isGateRefusal(refusal)) {
                sendAccess(editor);
            }
            editor.addChatMessage(new ChatComponentTranslation(refusal));
            return;
        }
        if (ChatHistory.isAction(messageId)) {
            // An action edited stays an action, its words ended as a
            // fresh one's are.
            message = ChatAction.sentence(message);
        }
        // The new words name whom they name now: a name added is lit, one
        // taken out no longer counts. Nobody is chimed for an edit.
        List<ChatNamedPlayer> named = ChatMentionTargets.of(editor,
                LostTalesServerBroadcastHook.namedAs(editor, speaking.worn),
                channel, scope,
                // The other party as the identity the whisper is with, here
                // or away, as a sent line names them (CHAT-82).
                channel == ChatChannel.WHISPER ? whisperPartnerOf(messageId, editor)
                        : null,
                message);
        List<String> roles = ChatMentionTargets.rolesOf(message);
        Set<UUID> recipients = ChatHistory.applyEdit(messageId,
                editor.getUniqueID(), message, "", named, roles, typed);
        if (recipients == null) {
            return;
        }
        FMLLog.info("[losttales/chat/edit] <%s> %s",
                editor.getCommandSenderName(),
                ChatMessageValidator.logged(message));
        ChatAuditLog.logEdit(messageId, editor.getUniqueID(),
                editor.getCommandSenderName(), message);
        LostTalesChatUpdatePacket update = LostTalesChatUpdatePacket.edited(
                messageId, message, "", named, roles);
        tellReaders(messageId, recipients, update, editor, update.withTyped(typed));
        // A line carried to Discord is corrected there too: the bridge
        // rewrites its own webhook post by the id it kept. A message of
        // any other channel resolves to no post and nothing happens.
        LostTalesDiscordBridge.getInstance().relayEdit(messageId,
                ChatHistory.isAction(messageId)
                        ? DiscordMessageSanitizer.outboundAction(
                                author.getIdentityName(), message)
                        : DiscordMessageSanitizer.outbound(message));
    }

    /** The notice an edit of a faction's line is refused with once its author speaks to another faction. */
    static final String EDIT_FACTION_LEFT = "chat.losttales.edit.faction_left";

    /**
     * Why an edit of a line said in {@code channel}'s conversation
     * {@code lineScope} is refused, as the notice the editor is told, or
     * null when it may go ahead: whatever a send there would be refused
     * with now ({@code sendRefusal}, which asks the gate and the
     * fellowship the line's scope names), and on a faction's channel a
     * faction other than the one the editor's line would be spoken to
     * now ({@code factionId}).
     */
    static String editRefusal(ChatChannel channel, String lineScope,
                              String sendRefusal, String factionId) {
        if (sendRefusal != null) {
            return sendRefusal;
        }
        if (channel != null && channel.getScope() == ChatChannelScope.FACTION
                && !(lineScope == null ? "" : lineScope).equals(factionId)) {
            return EDIT_FACTION_LEFT;
        }
        return null;
    }

    /**
     * Takes one of {@code remover}'s own messages back, wherever they
     * may speak now and silently when it is not theirs, as an
     * {@link #edit} is silent, or — when the remover may moderate the
     * chat ({@link LostTalesCapability#CHAT_MODERATE}) — anyone's message
     * that is still within reach and that the moderator may read now
     * ({@link ChatHistory#removeByOperator}). Everyone who was sent it is told to
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
        // Where the message was said, asked before it is forgotten: the
        // bridge takes a Discord copy back only while that channel is
        // still bound to the copy's Discord channel.
        ChatChannel saidIn = ChatHistory.channelOf(messageId);
        String saidToFaction = ChatHistory.factionScopeOf(messageId);
        Set<UUID> recipients = ChatHistory.remove(messageId,
                remover.getUniqueID());
        if (recipients == null) {
            if (!LostTalesPermissions.has(remover, LostTalesCapability.CHAT_MODERATE)) {
                return;
            }
            ChatHistory.Removal removal =
                    ChatHistory.removeByOperator(messageId, requesterFor(remover));
            if (removal == null) {
                return;
            }
            recipients = removal.recipients;
            FMLLog.info("[losttales/chat/delete] moderator <%s> removed "
                    + "message %d of <%s>", remover.getCommandSenderName(),
                    Long.valueOf(messageId), removal.staffAuthor);
            ChatAuditLog.logModerationDelete(messageId,
                    remover.getUniqueID(), remover.getCommandSenderName(),
                    removal.staffAuthor);
            // An unseen voice's speaker is named to staff alone (CHAT-84).
            console(ChatConsoleEvent.Kind.MODERATION, ChatConsoleEvent.Severity.NOTICE,
                    remover.getCommandSenderName(),
                    "removed a message of " + removal.author, "", null,
                    "removed a message of " + removal.staffAuthor);
        } else {
            FMLLog.info("[losttales/chat/delete] <%s> message %d",
                    remover.getCommandSenderName(), Long.valueOf(messageId));
            ChatAuditLog.logDelete(messageId, remover.getUniqueID(),
                    remover.getCommandSenderName());
        }
        tellRecipients(recipients,
                LostTalesChatUpdatePacket.removed(messageId));
        // Taken back from Discord as well, on the same terms as an edit:
        // every copy a webhook of ours made. A Discord member's own
        // message was made by none, so it stays for Discord's moderators.
        LostTalesDiscordBridge.getInstance().relayDelete(messageId,
                saidIn, saidToFaction);
    }

    /* ---- Reactions ---- */

    /**
     * Adds or takes back a player's reaction to a kept message and tells
     * every reader what the reactions are now. The server decides all of
     * it from its own record: whether the player may read the message —
     * exactly what a reply may quote — and who they react as, the
     * identity the copy they reacted in speaks as, named as a line of
     * theirs there would be signed ({@code identityKind} and
     * {@code identityCharacterId}, checked as a line's are). Each identity
     * reacts on its own: a player's two copies speaking as two characters
     * are two reactions. A reaction
     * puts something in front of the readers, so a mute refuses one as
     * it refuses an edit. The bridge's own reaction on each Discord copy
     * stands for everyone who reacted elsewhere, the players among them
     * ({@link #relayReaction}). {@code emojiName} is a reaction key: a player may
     * react with a foreign emoji only where the message already carries
     * it, which {@link ChatReactions} decides.
     */
    public static void react(EntityPlayerMP player, long messageId,
                             String emojiName, boolean add, int identityKind,
                             UUID identityCharacterId) {
        if (player == null || player.worldObj == null
                || player.worldObj.isRemote) {
            return;
        }
        if (!ChatForeignEmoji.isReactionKey(emojiName)) {
            return;
        }
        ChatChannel channel = ChatHistory.channelOf(messageId);
        boolean consoleEntry = channel == null
                && ChatConsoleStream.find(messageId) != null
                && ChatChannelPolicy.readsConsole(player);
        if (channel == null && !consoleEntry) {
            return;
        }
        ChatMuteEntry mute = activeMute(player);
        if (mute != null) {
            tellMuted(player, mute);
            return;
        }
        if (consoleEntry) {
            // An entry of the Server Log takes reactions from whoever
            // reads the console, and they stay in the console: nothing of
            // it crosses to Discord.
            if (ChatConsoleStream.react(messageId, player.getUniqueID(),
                    reactorName(player, ChatChannel.SERVER_LOG, null), emojiName,
                    add)) {
                tellConsoleReactions(messageId);
            }
            return;
        }
        ChatIdentitySelection.Worn worn = ChatIdentitySelection.worn(player,
                channel, identityKind, identityCharacterId);
        // Only an identity in use reacts: the character played or one a
        // copy reads as, never any other character of the roster.
        if (worn.refused || (worn.character != null
                && !ChatIdentitySelection.reads(player,
                        worn.character.getCharacterId()))) {
            return;
        }
        RoleplayCharacter as = worn.character;
        // A reaction to a line heard nearby while the One Ring conceals
        // the reactor is the unseen voice's to everyone but them and staff
        // (CHAT-84).
        ChatHistory.ReactionChange change = ChatHistory.react(messageId,
                requesterFor(player),
                as == null ? player.getUniqueID() : as.getCharacterId(),
                reactorName(player, channel, as), emojiName, add,
                ChatUnseenVoice.applies(channel,
                        AccessoryEffectService.isConcealed(player), false));
        if (change == null) {
            return;
        }
        tellReactions(messageId, change.readers);
        relayReaction(messageId, change);
    }

    /**
     * A Discord member's reaction to a message that crossed the bridge,
     * delivered on the server thread: kept under the sender id the
     * bridge signs that member with, with the Discord channel
     * {@code originChannelId} they reacted in, told to every reader, and
     * carried on to the message's copies in the other linked Discord
     * channels as the bridge's own reaction. {@code emoji}
     * is a reaction key, foreign for an emoji the registry lacks, or null
     * for a custom emoji Discord sent without a name. {@code emojiId} is
     * a custom emoji's id, empty for a Unicode one: a custom emoji is
     * matched by it, whatever it is called now
     * ({@link ChatHistory#reactFromDiscord}).
     */
    public static void reactFromDiscord(long messageId, String discordUserId,
                                        String name, String emoji,
                                        String emojiId, String originChannelId,
                                        boolean add) {
        boolean named = ChatForeignEmoji.isReactionKey(emoji);
        boolean byId = ChatForeignEmoji.isCustomId(emojiId);
        MinecraftServer server = MinecraftServer.getServer();
        if (!(named || byId) || discordUserId == null
                || discordUserId.length() == 0 || server == null) {
            return;
        }
        UUID senderId = LostTalesChatMessagePacket.discordSenderId(discordUserId);
        // A reaction puts something in front of the readers, so a mute
        // refuses one, as it refuses a player's.
        if (isDiscordSenderMuted(server, senderId)
                || !discordMemberWithinBudget(senderId,
                        LostTalesRequestRateLimiter.RequestType.CHAT_REACTION,
                        "Discord reaction")) {
            return;
        }
        ChatHistory.ReactionChange change = ChatHistory.reactFromDiscord(
                messageId,
                senderId,
                name, named ? emoji : null, byId ? emojiId : "",
                originChannelId, add);
        if (change != null) {
            tellReactions(messageId, change.readers);
            relayReaction(messageId, change);
        }
    }

    /**
     * The Discord members' reactions with one emoji to a message that
     * crossed the bridge, as Discord lists them in the Discord channel
     * {@code originChannelId} once the bridge's live connection is back:
     * no reader hears a reaction while it is down. {@code reactors} is
     * each member's Discord id and name; each one the message lacks gains
     * a reaction, unless muted, and with {@code complete} each one made
     * in that channel whom Discord no longer lists is taken back, a muted
     * member's too, since Discord has it no more. {@code emoji} and
     * {@code emojiId} are as {@link #reactFromDiscord} takes them. The
     * bridge bounds how many it asks for, so no member's reaction budget
     * is spent. Told to every reader and carried on as one change.
     */
    public static void setDiscordReactions(long messageId, String emoji,
                                           String emojiId, String originChannelId,
                                           java.util.Map<String, String> reactors,
                                           boolean complete) {
        boolean named = ChatForeignEmoji.isReactionKey(emoji);
        boolean byId = ChatForeignEmoji.isCustomId(emojiId);
        MinecraftServer server = MinecraftServer.getServer();
        if (!(named || byId) || reactors == null || originChannelId == null
                || originChannelId.length() == 0 || server == null) {
            return;
        }
        java.util.Map<UUID, String> listed = new java.util.LinkedHashMap<UUID, String>();
        Set<UUID> muted = new HashSet<UUID>();
        for (java.util.Map.Entry<String, String> member : reactors.entrySet()) {
            if (member.getKey() == null || member.getKey().length() == 0) {
                continue;
            }
            UUID senderId = LostTalesChatMessagePacket.discordSenderId(member.getKey());
            listed.put(senderId, member.getValue());
            if (isDiscordSenderMuted(server, senderId)) {
                muted.add(senderId);
            }
        }
        ChatHistory.ReactionChange change = ChatHistory.setDiscordReactions(messageId,
                named ? emoji : null, byId ? emojiId : "", originChannelId, listed,
                muted, complete);
        if (change != null) {
            tellReactions(messageId, change.readers);
            relayReaction(messageId, change);
        }
    }

    /**
     * Discord took the members' reactions off a message that crossed the
     * bridge in the Discord channel {@code originChannelId} — with one
     * emoji, or with all when {@code emoji} is null and {@code emojiId}
     * empty. A custom emoji is cleared by its id from every key it is
     * kept under. The players' own reactions stay, and so do those made
     * in the other linked Discord channels; the bridge's reaction on
     * their copies follows what is left.
     */
    public static void clearDiscordReactions(long messageId, String emoji,
                                             String emojiId,
                                             String originChannelId) {
        ChatHistory.ReactionClear clear = ChatHistory.clearDiscordReactions(
                messageId, emoji, emojiId, originChannelId);
        if (clear == null) {
            return;
        }
        tellReactions(messageId, clear.readers);
        Set<String> relayed = new HashSet<String>();
        for (ChatHistory.ReactionChange change : clear.changes) {
            // Two keys of one custom emoji are one reaction on Discord.
            if (relayed.add(ChatForeignEmoji.discordForm(change.emoji))) {
                relayReaction(messageId, change);
            }
        }
    }

    /**
     * Carries a change in who stands behind an emoji to the bridge, whose
     * own reaction on each Discord copy follows it: on the copy in a
     * Discord channel it stands for everyone who reacted anywhere else.
     */
    private static void relayReaction(long messageId,
                                      ChatHistory.ReactionChange change) {
        if (!change.before.equals(change.after)) {
            LostTalesDiscordBridge.getInstance().relayReaction(messageId,
                    change.emoji, change.before, change.after);
        }
    }

    /**
     * Each online reader who may still read the line is sent the
     * reactions as they are shown them ({@link #onlineReaders}).
     */
    private static void tellReactions(long messageId, Set<UUID> readers) {
        for (EntityPlayerMP player : onlineReaders(messageId, readers)) {
            LostTalesNetworkHandler.CHANNEL.sendTo(
                    new LostTalesChatReactionSyncPacket(messageId,
                            ChatHistory.reactionsFor(messageId,
                                    ChatIdentitySelection.identityIds(player),
                                    moderates(player))),
                    player);
        }
    }

    /** Sends an edit of a kept line to its online readers who may still read it. */
    private static void tellReaders(long messageId, Set<UUID> readers,
                                    LostTalesChatUpdatePacket update) {
        tellReaders(messageId, readers, update, null, update);
    }

    /** As above, {@code editor} told {@code forEditor}, which keeps what they typed. */
    private static void tellReaders(long messageId, Set<UUID> readers,
                                    LostTalesChatUpdatePacket update,
                                    EntityPlayerMP editor,
                                    LostTalesChatUpdatePacket forEditor) {
        for (EntityPlayerMP player : onlineReaders(messageId, readers)) {
            LostTalesNetworkHandler.CHANNEL.sendTo(
                    player == editor ? forEditor : update, player);
        }
    }

    /**
     * The players among {@code readers} who are online and may read the
     * kept line now ({@link ChatHistory#mayRead}): someone shown a line of
     * a gated channel, a faction or a fellowship who has since lost it
     * is sent none of its edits and reactions. A line said to the
     * accounts that heard it asks nothing more of them, so they are not
     * asked.
     */
    private static List<EntityPlayerMP> onlineReaders(long messageId,
                                                      Set<UUID> readers) {
        List<EntityPlayerMP> told = new ArrayList<EntityPlayerMP>();
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null
                || readers == null) {
            return told;
        }
        boolean asked = ChatHistory.asksCurrentAccess(messageId);
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online =
                server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP player : online) {
            if (player != null && readers.contains(player.getUniqueID())
                    && (!asked || ChatHistory.mayRead(messageId,
                            requesterFor(player)))) {
                told.add(player);
            }
        }
        return told;
    }

    /**
     * The Server Log's kept entries, sent to a player who has come to
     * read it while online — made an operator, given a role — as the ones
     * a staff member is sent on joining: history to them, reactions and
     * all. Entries the client already shows are shown once.
     */
    static void sendConsoleHistory(EntityPlayerMP player) {
        List<ChatConsoleEvent> events = consoleFor(player,
                ChatConsoleStream.replay(0L));
        for (IMessage packet : ChatLoginReplay.packets(
                Collections.<LostTalesChatMessagePacket>emptyList(), events,
                ChatMessageIdAllocator.next())) {
            LostTalesNetworkHandler.CHANNEL.sendTo(packet, player);
        }
        for (ChatConsoleEvent event : events) {
            if (!ChatConsoleStream.reactionsFor(event.getId(),
                    player.getUniqueID()).isEmpty()) {
                sendConsoleReactions(player, event.getId());
            }
        }
    }

    /** Every console reader online is sent an entry's reactions as they are shown them. */
    private static void tellConsoleReactions(long entryId) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online =
                server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP player : online) {
            if (player != null && ChatChannelPolicy.readsConsole(player)) {
                sendConsoleReactions(player, entryId);
            }
        }
    }

    private static void sendConsoleReactions(EntityPlayerMP player, long entryId) {
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new LostTalesChatReactionSyncPacket(entryId,
                        ChatConsoleStream.reactionsFor(entryId,
                                player.getUniqueID())),
                player);
    }

    /**
     * The quote of an entry of the Server Log, for a reply said in the
     * console by someone who reads it: the Server's word, in the console's
     * colour and with its head, and what the entry says. None where there
     * is no such entry or the sender cannot read the console.
     */
    private static ChatReplyReference consoleQuote(EntityPlayerMP sender,
                                                   long entryId) {
        ChatConsoleEvent entry = ChatConsoleStream.find(entryId);
        if (entry == null || !ChatChannelPolicy.readsConsole(sender)) {
            return ChatReplyReference.NONE;
        }
        String actor = entry.getActor().trim();
        boolean byServer = actor.length() == 0
                || LostTalesServerBroadcastHook.SERVER_NAME.equalsIgnoreCase(actor);
        String words = entry.getKind() == ChatConsoleEvent.Kind.COMMAND
                ? actor + " used " + entry.getText()
                : byServer ? entry.getText() : actor + " " + entry.getText();
        return ChatReplyReference.of(entryId,
                LostTalesServerBroadcastHook.SERVER_NAME,
                ChatReplyReference.excerptOf(words),
                ChatChannel.SERVER_LOG.getDisplayColor())
                .withHead(LostTalesChatMessagePacket.SERVER_SENDER_ID, true, "");
    }

    /**
     * What the server knows of a player asking about a kept message, read
     * live: each faction from when an identity reading it came to it, and
     * each fellowship of the character played from when it joined
     * (FEL-29), since a fellowship's conversation is read as that
     * character alone (FEL-22).
     */
    public static ChatHistory.Requester requesterFor(EntityPlayerMP player) {
        return new ChatHistory.Requester(player.getUniqueID(),
                ChatChannelPolicy.selectedFactions(player),
                Fellowship.joinings(ChatIdentitySelection.fellowships(player),
                        ChatIdentitySelection.playedId(player)),
                readableChannels(player),
                ChatIdentitySelection.identityIds(player))
                .moderating(moderates(player));
    }

    /** Whether a player holds {@code chat.moderate}: staff read an unseen voice as its speaker. */
    private static boolean moderates(EntityPlayerMP player) {
        return LostTalesPermissions.has(player, LostTalesCapability.CHAT_MODERATE);
    }

    /**
     * The name a player reacts as in a channel: the character {@code as}
     * where lines are signed in character, the account where they are not
     * or for none — the identity their own line there would wear.
     */
    private static String reactorName(EntityPlayerMP player,
                                      ChatChannel channel,
                                      RoleplayCharacter as) {
        String account = player.getGameProfile() == null
                ? player.getCommandSenderName()
                : player.getGameProfile().getName();
        return as != null && ChatRolePresentation.isInCharacter(channel)
                ? characterNameOrFallback(as, account) : account;
    }

    /** Whether {@code worn} is a character of the sender's that they do not play now. */
    static boolean notPlayed(EntityPlayerMP sender, RoleplayCharacter worn) {
        if (worn == null) {
            return false;
        }
        RoleplayCharacter played = ChatIdentitySelection.played(sender);
        return played == null
                || !worn.getCharacterId().equals(played.getCharacterId());
    }

    /** The players among {@code accounts} who are online now. */
    private static List<EntityPlayerMP> onlineOf(Set<UUID> accounts) {
        List<EntityPlayerMP> online = new ArrayList<EntityPlayerMP>();
        for (UUID account : accounts) {
            EntityPlayerMP player = LostTalesServerPlayers.findOnline(account);
            if (player != null) {
                online.add(player);
            }
        }
        return online;
    }

    /**
     * The other person of a kept whisper, here or away, as the identity
     * the conversation is with; null for none the server knows.
     */
    private static ChatNamedPlayer whisperPartnerOf(long messageId,
                                                    EntityPlayerMP one) {
        for (UUID account : ChatHistory.recipientsOf(messageId)) {
            if (account.equals(one.getUniqueID())) {
                continue;
            }
            EntityPlayerMP online = LostTalesServerPlayers.findOnline(account);
            String name = online != null ? online.getCommandSenderName()
                    : KnownAccounts.nameOf(account);
            if (name.length() == 0) {
                return null;
            }
            AddressedIdentity addressed = resolveIdentity(one.worldObj, account,
                    name, "", ChatHistory.partnerCharacterOf(messageId));
            return new Whispered(account, name, online, addressed == null
                    ? new AddressedIdentity(name, null) : addressed).named();
        }
        return null;
    }

    /**
     * Sends a removal to whichever of the recorded recipients are still
     * online, whether or not they may still read the line: taking it
     * away shows nobody anything. Anyone who has logged out never hears
     * about it, and needs to hear nothing: the history they are replayed
     * on joining no longer holds it.
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
    static final class AddressedIdentity {
        final String name;
        final UUID characterId;
        /** The character addressed as its roster holds it; null for the account. */
        final RoleplayCharacter character;

        AddressedIdentity(String name, UUID characterId) {
            this(name, characterId, null);
        }

        AddressedIdentity(String name, UUID characterId,
                          RoleplayCharacter character) {
            this.name = name;
            this.characterId = characterId;
            this.character = character;
        }
    }

    /**
     * The identity of the account {@code owner}, known as
     * {@code accountName}, the sender addressed: by the character's id
     * when one is given, else by name — the account when the name is
     * empty or is the account's own, a character of its roster when it
     * names one. Null when neither names anything the account holds.
     * Nothing the sender says decides this: the roster is the server's.
     */
    private static AddressedIdentity resolveIdentity(World world, UUID owner,
                                                     String accountName,
                                                     String identityName,
                                                     UUID characterId) {
        String named = identityName == null ? "" : identityName.trim();
        if (characterId == null
                && (named.length() == 0 || named.equalsIgnoreCase(accountName))) {
            return new AddressedIdentity(accountName, null);
        }
        try {
            CharacterRoster roster = CharacterStorage.get(world).getRoster(owner);
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
                            character.getCharacterId(), character);
                }
            }
        } catch (RuntimeException exception) {
            noteStorageFailure("the character roster", exception);
        }
        return null;
    }

    /**
     * Whom a whisper is for (CHAT-31): a person the world knows, here or
     * away, and the identity of theirs it is addressed to. One away is
     * sent nothing now; the history keeps the line for them.
     */
    static final class Whispered {
        /** The person's account. */
        final UUID account;
        /** The account's name, as the conversation is filed under it. */
        final String accountName;
        /** The person's player while they are here; null while they are away. */
        final EntityPlayerMP online;
        final AddressedIdentity addressed;

        Whispered(UUID account, String accountName, EntityPlayerMP online,
                  AddressedIdentity addressed) {
            this.account = account;
            this.accountName = accountName;
            this.online = online;
            this.addressed = addressed;
        }

        /**
         * Whether the person may read {@code channel}: asked of the player
         * while they are here, and of the server's own lists while they
         * are away ({@link ChatAbsentReader}), a banned account reading
         * nothing.
         */
        boolean mayRead(ChatChannel channel) {
            if (this.online != null) {
                return ChatChannelPolicy.canRead(this.online, channel,
                        ChatIdentitySelection.roles(this.online));
            }
            ChatAbsentReader reader = ChatAbsentReader.of(this.account,
                    this.accountName);
            return reader != null && reader.mayJoin()
                    && ChatChannelPolicy.canRead(reader, channel,
                            reader.accountRoles(), ChatChannelGates.current());
        }

        /** The person as a mention reaches them: the identity addressed. */
        ChatNamedPlayer named() {
            RoleplayCharacter character = this.addressed.character;
            return character == null
                    ? ChatNamedPlayer.account(this.account, this.accountName)
                    : new ChatNamedPlayer(this.account, this.accountName,
                            character.getCharacterId(),
                            PlayableIdentity.displayName(character,
                                    this.accountName),
                            character.getSkinId());
        }

        /**
         * The person away, as a reply or a forward into the whisper asks
         * whether both could read a line: their account and characters,
         * the channels the server's lists let them read, and no faction
         * or fellowship, which only a character in play reads.
         */
        ChatHistory.Requester absentRequester(World world) {
            ChatAbsentReader reader = ChatAbsentReader.of(this.account,
                    this.accountName);
            List<ChatChannel> readable = new ArrayList<ChatChannel>();
            Set<UUID> identities = new HashSet<UUID>();
            if (reader != null && reader.mayJoin()) {
                ChatChannelGates gates = ChatChannelGates.current();
                for (ChatChannel channel : ChatChannel.values()) {
                    if (!channel.isScoped() && ChatChannelPolicy.canRead(reader,
                            channel, reader.accountRoles(), gates)) {
                        readable.add(channel);
                    }
                }
            }
            try {
                CharacterRoster roster = CharacterStorage.get(world)
                        .getRoster(this.account);
                if (roster != null) {
                    for (RoleplayCharacter character : roster.getCharacters()) {
                        if (character != null) {
                            identities.add(character.getCharacterId());
                        }
                    }
                }
            } catch (RuntimeException exception) {
                noteStorageFailure("the character roster", exception);
            }
            return new ChatHistory.Requester(this.account,
                    Collections.<String, Long>emptyMap(),
                    Collections.<UUID, Long>emptyMap(), readable, identities);
        }
    }

    /**
     * Whom a whisper is for, the same answer for a line and for its
     * typing, whatever the sender is shown; null for a name no account or
     * character of this world goes by. An account online by the name is
     * reached as the identity of it the whisper names; else whoever
     * presents themselves under the name now, played or spoken as by a
     * copy; else an account the world knows by the name, here or away;
     * else the one character of the world's rosters that goes by it, its
     * owner here or away.
     */
    private static Whispered addressWhisper(EntityPlayerMP sender, String target,
                                            String targetIdentity,
                                            UUID targetCharacterId) {
        String typed = target == null ? "" : target.trim();
        if (typed.length() == 0 || sender.worldObj == null) {
            return null;
        }
        EntityPlayerMP online = LostTalesServerPlayers.findOnline(typed);
        if (online == null) {
            online = findOnlinePlayingAs(typed);
        }
        UUID account;
        String accountName;
        if (online != null) {
            account = online.getUniqueID();
            accountName = online.getCommandSenderName();
        } else {
            account = KnownAccounts.ownerNamed(sender.worldObj, typed);
            if (account == null) {
                RoleplayCharacter character = namedCharacter(sender, typed);
                account = character == null ? null : character.getOwnerId();
            }
            if (account == null) {
                return null;
            }
            accountName = KnownAccounts.nameOf(account);
            if (accountName.length() == 0) {
                return null;
            }
            // A character's owner may be here, playing another.
            online = LostTalesServerPlayers.findOnline(account);
        }
        AddressedIdentity addressed = resolveIdentity(sender.worldObj, account,
                accountName, addressedAs(accountName, typed, targetIdentity,
                        targetCharacterId), targetCharacterId);
        return addressed == null ? null
                : new Whispered(account, accountName, online, addressed);
    }

    /**
     * The one character of the world's rosters that goes by {@code name};
     * null for a name no character goes by, or two do.
     */
    private static RoleplayCharacter namedCharacter(EntityPlayerMP sender,
                                                    String name) {
        try {
            return CharacterNames.named(
                    CharacterStorage.get(sender.worldObj).getRosters(), name);
        } catch (RuntimeException exception) {
            noteStorageFailure("the character roster", exception);
            return null;
        }
    }

    /**
     * The identity a whisper is addressed to: the one it names, else, for
     * a person found by a name that is not their account's, that name.
     */
    static String addressedAs(String accountName, String target,
                              String targetIdentity, UUID targetCharacterId) {
        String named = targetIdentity == null ? "" : targetIdentity;
        String typed = target == null ? "" : target.trim();
        if (named.length() == 0 && targetCharacterId == null
                && !accountName.equalsIgnoreCase(typed)) {
            return typed;
        }
        return named;
    }

    /**
     * The online player presenting themselves under the name: as the
     * character they play, or one a copy of their chat speaks as; null for
     * none. A conversation is with someone as they present themselves,
     * and a name a player is not wearing names nobody to talk to. The
     * first match wins, which is the same rule the account lookup keeps.
     */
    private static EntityPlayerMP findOnlinePlayingAs(String characterName) {
        String named = characterName == null ? "" : characterName.trim();
        if (named.length() == 0) {
            return null;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return null;
        }
        for (Object candidate : server.getConfigurationManager().playerEntityList) {
            if (!(candidate instanceof EntityPlayerMP)) {
                continue;
            }
            EntityPlayerMP player = (EntityPlayerMP)candidate;
            RoleplayCharacter played = CharacterActiveResolver.get(player);
            if (played != null && named.equalsIgnoreCase(played.getName())) {
                return player;
            }
            for (RoleplayCharacter read : ChatIdentitySelection.alsoRead(player)) {
                if (named.equalsIgnoreCase(read.getName())) {
                    return player;
                }
            }
        }
        return null;
    }

    /** Whether the player's account is under a mute now; nothing is said to them. */
    public static boolean isMuted(EntityPlayerMP player) {
        return player != null && player.worldObj != null
                && !player.worldObj.isRemote && ChatLinePipeline.isMuted(player);
    }

    /**
     * Whether a muted account is refused a way to speak that does not
     * pass through {@link #send} ({@link ChatSpeechGate}); a refused
     * player is told, as a refused line is.
     */
    public static boolean refuseIfMuted(EntityPlayerMP player) {
        if (player == null || player.worldObj == null
                || player.worldObj.isRemote) {
            return false;
        }
        ChatMuteEntry mute = activeMute(player);
        if (mute == null) {
            return false;
        }
        tellMuted(player, mute);
        return true;
    }

    /** The mute standing against the player's account now; the pipeline's one mute rule. */
    private static ChatMuteEntry activeMute(EntityPlayerMP player) {
        return ChatLinePipeline.muteOf(player.getUniqueID());
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
        IChatComponent remaining = ChatMuteDurations.remaining(
                mute.getExpiresAtMillis() - System.currentTimeMillis())
                .component();
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
     * staff-channel message is refused, so the Operator page follows the
     * server's view without the client ever deciding it; the roles travel
     * with it so the client can notice a mention addressed to one of
     * them. The roster of every online role holder rides along, which
     * is what the role card names its members from.
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
        int roles = ChatIdentitySelection.roles(player);
        // The account's own roles apart from the played character's: what
        // the client signs an account line with and every character of
        // the account wears. Capabilities come through fewer of them:
        // only roles assigned to the account or given by operator level.
        int accountRoles = ChatAccountRoleResolver.resolve(player, null);
        // The mute list, and the moderation flag the client offers its
        // moderation menus on, follow the moderation capability; the
        // settings flag follows its own. The server decides again on
        // every request.
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new LostTalesChatAccessPacket(
                        roles,
                        roleHolders,
                        moderator ? mutedSenders(player)
                                : Collections.<UUID>emptyList(),
                        ChatRoleCatalog.server().roles(),
                        openChannelIds(player, roles, true),
                        openChannelIds(player, roles, false),
                        moderator,
                        LostTalesPermissions.has(player,
                                LostTalesCapability.SERVER_CONFIG),
                        heldCapabilityIds(player,
                                ChatAccountRoleResolver.grantingMask(player)),
                        accountRoles,
                        ChatChannelPolicy.ownCharacterRoles(player),
                        ChatSpeechRange.TALK.blocks(),
                        ChatChannelIconCatalog.current(),
                        ChatProfanityCatalog.serverWords())
                        .withSpeechRanges(ChatSpeechRange.WHISPER.blocks(),
                                ChatSpeechRange.SHOUT.blocks())
                        .withPrivateCommands(ChatCommandText.serverNames())
                        .withDiscordLinks(linksReadBy(
                                LostTalesDiscordBridge.getInstance().links(),
                                readerOf(player, roles)))
                        .withDiscordStatuses(LostTalesDiscordBridge.getInstance()
                                .followsMemberStatuses())
                        // The audit log keeps whispers too, and a player
                        // is told so where they whisper (CHAT-85).
                        .withWhispersLogged(LostTalesConfig.chatAuditLogEnabled),
                player);
        ChatIdentitySelection.sendState(player);
    }

    /** Whether someone may read a conversation: a channel, and a faction's for a faction's chat. */
    interface ConversationReader {
        boolean reads(ChatChannel channel, String factionScope);
    }

    /**
     * The keys of the Discord links of the conversations {@code reader}
     * may read: a link to a channel they may not read, or to another
     * faction's chat, is none of their business.
     */
    static List<String> linksReadBy(List<LostTalesDiscordBridge.Link> links,
                                    ConversationReader reader) {
        List<String> keys = new ArrayList<String>();
        for (LostTalesDiscordBridge.Link link : links) {
            if (link != null && link.channel != null
                    && reader.reads(link.channel, link.factionScope)) {
                keys.add(link.key);
            }
        }
        return keys;
    }

    /** The player as a reader of conversations, as the router judges them. */
    private static ConversationReader readerOf(final EntityPlayerMP player,
                                               final int roles) {
        return new ConversationReader() {
            @Override
            public boolean reads(ChatChannel channel, String factionScope) {
                return ChatChannelPolicy.canRead(player, channel, roles)
                        && (factionScope.length() == 0
                                || ChatIdentitySelection.readsFaction(player,
                                        factionScope));
            }
        };
    }

    /**
     * Every capability the player holds, by id, for the client's menus.
     * Asked of the permissions in force at this moment, like every other
     * answer here, and never trusted back: the server decides again on
     * the request the menu makes.
     */
    private static List<String> heldCapabilityIds(EntityPlayerMP player,
                                                  int grantingRoles) {
        // The granting roles are resolved once by the caller, not once
        // per capability: resolving reads the catalogue.
        ChatRoleCatalog roles = ChatRoleCatalog.server();
        LostTalesPermissionCatalog permissions = LostTalesPermissionCatalog.current();
        List<String> held = new ArrayList<String>();
        for (LostTalesCapability capability : LostTalesCapability.all()) {
            if (LostTalesPermissions.decide(
                    player.canCommandSenderUseCommand(capability.getRequiredOpLevel(),
                            LostTalesPermissions.NODE),
                    grantingRoles, capability, roles, permissions)) {
                held.add(capability.getId());
            }
        }
        return held;
    }

    /**
     * The ids of the channels the gates let this player read or send
     * into, asked through {@link ChatChannelPolicy} so the answer the
     * client is told is the one the send path and the router will give.
     */
    private static List<String> openChannelIds(EntityPlayerMP player, int roles,
                                               boolean read) {
        List<String> open = new ArrayList<String>();
        for (ChatChannel channel : ChatChannel.values()) {
            boolean allowed = read
                    ? ChatChannelPolicy.canRead(player, channel, roles)
                    : ChatChannelPolicy.canSend(player, channel, roles);
            if (allowed) {
                open.add(channel.getId());
            }
        }
        return open;
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
                    + ':' + holder.getMask() + ':' + holder.getAccountMask()
                    + ':' + holder.getCharacterId());
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
                // The account's own roles apart, and the character it
                // plays, so a client credits a character's own role to
                // that character rather than to the account.
                RoleplayCharacter active = CharacterActiveResolver.get(player);
                holders.add(new LostTalesChatAccessPacket.RoleHolder(
                        player.getGameProfile() == null
                                ? player.getCommandSenderName()
                                : player.getGameProfile().getName(),
                        mask, ChatAccountRoleResolver.resolve(player) & mask,
                        active == null ? null : active.getCharacterId()));
            }
        }
        return holders;
    }

    /**
     * Pairs tokens with references by position and keeps only the things
     * that exist, are the sender's to share, and fit the wire bound — each on
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
        boolean questUnavailable = false;
        boolean overBudget = false;
        int budget = ChatShowcase.MAX_TOTAL_BYTES;
        for (int index = 0; index < count; index++) {
            ChatShareTokenParser.Token token = tokens.get(index);
            ChatShareReference reference = references.get(index);
            if (reference == null || reference.getKind() != token.kind) {
                if (token.kind == ChatShareKind.MARKER) {
                    markerUnavailable = true;
                } else if (token.kind == ChatShareKind.QUEST) {
                    questUnavailable = true;
                } else {
                    itemUnavailable = true;
                }
                continue;
            }
            if (token.kind == ChatShareKind.ITEM) {
                ItemStack stack = itemInSlot(sender.inventory, reference);
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
            } else if (token.kind == ChatShareKind.MARKER) {
                ChatShowcase marker = resolveMarker(sender, reference, index);
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
            } else {
                ChatShowcase quest = LostTalesQuestShareResolver.resolve(
                        sender, reference.getQuestReference(), index);
                if (quest == null) {
                    questUnavailable = true;
                    continue;
                }
                if (quest.serializedBytes() > budget) {
                    overBudget = true;
                    continue;
                }
                budget -= quest.serializedBytes();
                result.add(quest);
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
        if (questUnavailable) {
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.quest.unavailable"));
        }
        if (overBudget) {
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.share.too_many"));
        }
        return result;
    }

    /**
     * The stack in the slot the sender's client named; null for an empty
     * or unnamed slot. The name typed in the token is the label the
     * sender's own game showed, in its own language, so it never decides
     * what is shared; every reader sees the stack's own name
     * ({@link ChatShowcase}).
     */
    static ItemStack itemInSlot(InventoryPlayer inventory,
                                ChatShareReference reference) {
        if (inventory == null || reference == null
                || reference.getKind() != ChatShareKind.ITEM
                || !reference.isResolved()) {
            return null;
        }
        ItemStack stack = inventory.getStackInSlot(reference.getSlot());
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0) {
            return null;
        }
        return stack;
    }

    /**
     * Sends one line to everyone it resolved to and records who was
     * sent it, and who may still be shown it. The sender, when there is
     * one, is the only recipient to get the line under their own private
     * name for it; everyone else gets it without, and a Narrator line
     * signed by the Narrator alone ({@link
     * LostTalesChatMessagePacket#narratedForOthers}). Each reader is sent
     * the line as they may read it ({@link ChatReaderView}): a reply's
     * quote of a line heard nearby only if they heard that line, and an
     * unseen voice named only to its speaker and to staff. A line heard
     * nearby reads at the size its reader's distance from the speaker
     * gives it ({@link ChatSpeechRange#stepFor}), measured here once; the
     * speaker reads their own at the usual size. The history keeps the
     * line unmasked, the readers the quote went to, and who read it
     * larger or smaller. The history is written
     * from the list the message actually went to, so a reply to it is
     * checked against who was sent it rather than against who would be
     * sent one now; {@code fellowship} and {@code factionId} are the
     * membership the line was routed by, which is what a later replay
     * asks of the player it is shown to.
     */
    private static void deliver(LostTalesChatMessagePacket packet,
                                EntityPlayerMP sender,
                                ChatChannelPolicy.Routing routing,
                                UUID authorId, String identityName) {
        LostTalesChatMessagePacket shared = packet.withoutEcho()
                .narratedForOthers();
        ChatReplyReference quote = packet.getReply();
        boolean nearbyQuote = ChatHistory.quotesNearby(quote);
        boolean unseen = packet.isUnseen() || quote.isUnseen();
        Set<UUID> quoteReaders = nearbyQuote ? new HashSet<UUID>() : null;
        boolean nearby = sender != null
                && ChatSpeechRange.appliesTo(packet.getChannel());
        int rangeBlocks = packet.getSpeechRange().blocks();
        Set<UUID> nearReaders = new HashSet<UUID>();
        Set<UUID> farReaders = new HashSet<UUID>();
        for (EntityPlayerMP recipient : routing.recipients) {
            LostTalesChatMessagePacket copy =
                    sender != null && recipient == sender ? packet : shared;
            if (nearby && recipient != sender) {
                int step = ChatSpeechRange.stepFor(Math.sqrt(
                        recipient.getDistanceSqToEntity(sender)), rangeBlocks);
                copy = copy.withDistanceStep(step);
                if (step == ChatSpeechRange.NEAR_STEP) {
                    nearReaders.add(recipient.getUniqueID());
                } else if (step == ChatSpeechRange.FAR_STEP) {
                    farReaders.add(recipient.getUniqueID());
                }
            }
            if (nearbyQuote || unseen) {
                boolean heard = !nearbyQuote || ChatHistory.showsQuoteTo(
                        quote, requesterFor(recipient));
                if (heard && quoteReaders != null) {
                    quoteReaders.add(recipient.getUniqueID());
                }
                copy = ChatReaderView.forReader(copy, recipient.getUniqueID(),
                        unseen && moderates(recipient), heard);
            }
            LostTalesNetworkHandler.CHANNEL.sendTo(copy, recipient);
        }
        ChatHistory.record(packet.getMessageId(), authorId, identityName,
                packet.withoutEcho(), shared, routing.recipientIds(), routing.audience,
                quoteReaders, nearReaders, farReaders);
    }

    /** The channels the player may read right now. */
    private static List<ChatChannel> readableChannels(EntityPlayerMP player) {
        int roles = ChatIdentitySelection.roles(player);
        List<ChatChannel> readable = new ArrayList<ChatChannel>();
        for (ChatChannel channel : ChatChannel.values()) {
            if (ChatChannelPolicy.canRead(player, channel, roles)) {
                readable.add(channel);
            }
        }
        return readable;
    }

    /**
     * Answers a client asking what was said in one conversation of a
     * channel that has more than one — a faction's talk — before it was
     * shown any of it. The selected chat identity decides what may be
     * shown, re-read from the live roster and fellowship store. A conversation
     * that identity does not belong to answers with nothing.
     * Only what is newer than the client already holds is sent, so
     * asking twice never shows a line twice.
     */
    public static void sendContextHistory(EntityPlayerMP player, ChatChannel channel,
                                          String scopeValue, long sinceMessageId) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote
                || channel == null || !channel.isScoped()
                || scopeValue == null || scopeValue.length() == 0) {
            return;
        }
        ChatHistory.Requester reader = requesterFor(player);
        if (!isIn(reader, channel, scopeValue)) {
            return;
        }
        List<LostTalesChatMessagePacket> lines = sendable(ChatHistory.replayForContext(
                reader, channel, scopeValue, sinceMessageId));
        if (!sendHistory(player, lines)) {
            return;
        }
        FMLLog.info("[losttales/chat/history] replayed %d lines of %s/%s to %s",
                Integer.valueOf(lines.size()), channel.getId(), scopeValue,
                player.getCommandSenderName());
    }

    /**
     * Answers a player scrolled to the top of a page with the page of the
     * channel before the oldest line they hold, newest first and in
     * batches. Who they are is read from the live server here, as for
     * the login replay, and a conversation the requester is not in
     * answers with nothing whatever the request names. An empty page is
     * answered with nothing at all; the client stops asking once a page
     * comes back short.
     */
    public static void sendOlderHistory(EntityPlayerMP player, ChatChannel channel,
                                        String scopeValue, long beforeMessageId) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote
                || channel == null || !ChatMessageIds.isServerId(beforeMessageId)) {
            return;
        }
        String scope = scopeValue == null ? "" : scopeValue.trim();
        // A whisper names its conversation by the page's id, a scoped
        // channel by the conversation, a plain one none. A whisper's
        // audience is the two accounts, which decides line by line below.
        if (channel == ChatChannel.WHISPER ? scope.length() == 0
                : channel.isScoped() == (scope.length() == 0)) {
            return;
        }
        ChatHistory.Requester reader = requesterFor(player);
        if (!readsConversation(reader, channel, scope)) {
            return;
        }
        List<LostTalesChatMessagePacket> lines = sendable(ChatHistory.replayBefore(
                reader, channel, scope, beforeMessageId));
        if (!sendHistory(player, lines)) {
            return;
        }
        FMLLog.info("[losttales/chat/history] replayed %d older lines of %s%s to %s",
                Integer.valueOf(lines.size()), channel.getId(),
                scope.length() == 0 ? "" : "/" + scope,
                player.getCommandSenderName());
    }

    /**
     * Whether the reader may read {@code channel}'s conversation
     * {@code scope} now: the channel's read rule lets them in, and on a
     * channel that holds several conversations they belong to the one
     * {@code scope} names. A scoped channel with no conversation named is
     * nothing they read. What a request for older lines and a command's
     * answer filed under its page are both asked.
     */
    static boolean readsConversation(ChatHistory.Requester reader,
                                     ChatChannel channel, String scope) {
        if (reader == null || channel == null
                || !reader.readableChannels.contains(channel.getId())) {
            return false;
        }
        if (!channel.isScoped()) {
            return true;
        }
        return scope != null && scope.length() > 0 && isIn(reader, channel, scope);
    }

    /**
     * Whether the reader belongs to the conversation of a scoped channel
     * that {@code scope} names: one of their fellowships, or their chat
     * identity's faction. A conversation they are not in answers with nothing,
     * whatever the request names.
     */
    private static boolean isIn(ChatHistory.Requester reader, ChatChannel channel,
                                String scope) {
        if (channel.getScope() == ChatChannelScope.FELLOWSHIP) {
            UUID fellowshipId = ChatFellowship.idOf(scope);
            return reader.inFellowship(fellowshipId);
        }
        return reader.ownedFactions.containsKey(scope);
    }

    /** Sends history lines in packets of what one holds; false for none to send. */
    private static boolean sendHistory(EntityPlayerMP player,
                                       List<LostTalesChatMessagePacket> lines) {
        if (lines.isEmpty()) {
            return false;
        }
        for (int from = 0; from < lines.size();
             from += LostTalesChatHistorySyncPacket.MAX_MESSAGES) {
            LostTalesNetworkHandler.CHANNEL.sendTo(
                    new LostTalesChatHistorySyncPacket(lines.subList(from,
                            Math.min(lines.size(), from
                                    + LostTalesChatHistorySyncPacket.MAX_MESSAGES))),
                    player);
        }
        return true;
    }

    /**
     * The kept lines that can still be sent: one built under a channel or
     * a role the server no longer has is left out with a warning rather
     * than handed to the encoder, whose exception would end the
     * connection.
     */
    private static List<LostTalesChatMessagePacket> sendable(
            List<LostTalesChatMessagePacket> lines) {
        List<LostTalesChatMessagePacket> kept =
                new ArrayList<LostTalesChatMessagePacket>(lines.size());
        int dropped = 0;
        for (LostTalesChatMessagePacket line : lines) {
            if (line != null && line.isWellFormed()) {
                kept.add(line);
            } else {
                dropped++;
            }
        }
        if (dropped > 0) {
            FMLLog.warning("[%s] %d kept chat lines can no longer be sent and were left out of a replay",
                    LostTalesMetaData.MOD_ID, Integer.valueOf(dropped));
        }
        return kept;
    }

    /**
     * Catches a player who has just joined up on what was said and done
     * while they were away, in the order it happened: the recent
     * messages they are entitled to, exactly as they would have been
     * sent them at the time, and — if they may read the console — its
     * kept entries, merged into one stream by id
     * ({@link ChatLoginReplay}). Who they are — account, the factions
     * their characters are in, fellowship, the channels they may read — is
     * read from the live server here and nowhere else, and the history
     * decides message by message against what it recorded when each was
     * sent. Everything before their own join line is history to them;
     * the line itself, and whatever was said after it while the other
     * login handlers ran, is not. With no join line to go by — another
     * mod may silence the game's — they arrive now.
     */
    public static void sendLoginReplay(EntityPlayerMP player) {
        if (player == null || player.worldObj == null
                || player.worldObj.isRemote) {
            return;
        }
        long arrivalId = LostTalesServerBroadcastHook.takeJoinLine(
                player.getCommandSenderName());
        if (ChatMessageIds.isServerId(arrivalId)) {
            // The join line names its player from the moment it goes out
            // (ChatArrivals); where the game's reading of their saved
            // data went unseen, it names their account now, as the
            // out-of-character line it is, for everyone shown it later.
            ChatHistory.namePlayer(arrivalId,
                    LostTalesServerBroadcastHook.namedAccount(player));
        } else {
            arrivalId = ChatMessageIdAllocator.next();
        }
        List<LostTalesChatMessagePacket> lines = sendable(ChatHistory.replayFor(
                requesterFor(player), ChatMessageIds.NONE));
        List<ChatConsoleEvent> events = ChatChannelPolicy.readsConsole(player)
                ? consoleFor(player, ChatConsoleStream.replay(0L))
                : Collections.<ChatConsoleEvent>emptyList();
        List<IMessage> packets = ChatLoginReplay.packets(lines, events,
                arrivalId);
        for (IMessage packet : packets) {
            LostTalesNetworkHandler.CHANNEL.sendTo(packet, player);
        }
        // The reactions on the entries replayed, after them, as a
        // message's come with it.
        for (ChatConsoleEvent event : events) {
            if (!ChatConsoleStream.reactionsFor(event.getId(),
                    player.getUniqueID()).isEmpty()) {
                sendConsoleReactions(player, event.getId());
            }
        }
        // One line per login, so a replay that went missing can be told
        // apart from one that was never sent.
        FMLLog.info("[%s] Replayed %d of %d kept chat lines and %d console entries in %d packets to %s",
                LostTalesMetaData.MOD_ID, lines.size(), ChatHistory.size(),
                events.size(), packets.size(), player.getCommandSenderName());
    }

    /**
     * A marker the sender may actually see and has visited, by the id the
     * client supplied; the typed name is only the sender's label. The
     * public fields go out, with what an unnamed marker is called after;
     * ownership, sharing lists, and settings never do.
     */
    private static ChatShowcase resolveMarker(EntityPlayerMP sender,
                                              ChatShareReference reference,
                                              int tokenIndex) {
        if (!reference.isResolved()) {
            return null;
        }
        try {
            LostTalesMapMarkerRecord record = LostTalesMapMarkerStorage
                    .get(sender.worldObj).getRecord(reference.getMarkerId());
            // The marker is the one the sender's client named by its id;
            // the typed name is the label the sender's game showed, and
            // every reader's game names the marker itself.
            if (record == null
                    || !LostTalesMapMarkerVisibilityPolicy.canView(
                            record, sender)) {
                return null;
            }
            if (!LostTalesWaypointFastTravelPolicy.hasVisited(sender, record)) {
                // Only places the sender has actually reached are shared;
                // an undiscovered or region-locked marker stays plain text.
                return null;
            }
            return ChatShowcase.marker(tokenIndex, record.getId(),
                    record.getName(), LostTalesWaystonePlacers.shown(
                            sender.worldObj, record.getNamedAfter()),
                    record.getIconName(), record.getColorName(),
                    record.getDimensionId(), record.getX(), record.getZ());
        } catch (RuntimeException exception) {
            FMLLog.warning("[losttales/chat] Could not resolve shared marker "
                    + "%s for %s: %s", reference.getMarkerId(),
                    sender.getUniqueID(), exception.toString());
            return null;
        }
    }

    /**
     * The name a line is signed with: the character's, else the account's.
     * An account with no name signs nothing, and the line packet refuses a
     * line signed by nobody, so no word stands in for it.
     */
    private static String characterNameOrFallback(
            RoleplayCharacter character, String accountName) {
        return PlayableIdentity.displayName(character, accountName);
    }
}
