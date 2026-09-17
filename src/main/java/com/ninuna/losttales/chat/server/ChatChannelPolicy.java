package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatRoleCatalog;
import java.util.LinkedHashMap;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.CharacterKind;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.server.CharacterActiveResolver;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelAccess;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatChannelScope;
import com.ninuna.losttales.chat.ChatRecipientRule;
import com.ninuna.losttales.chat.ChatRolePresentation;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyMember;
import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.permission.LostTalesPermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * The server's one answer to who may use a channel, who a line in it
 * reaches, and who may be shown that line later. Every channel is
 * decided from its descriptor — its access, its routing rule and the
 * gate the config put on it — never from which channel it is, so a
 * channel added later is routed by the same code. A send, a typing
 * notice, a delivery and a history replay all ask here, so they cannot
 * drift apart.
 */
public final class ChatChannelPolicy {

    /** Who a line reaches now, and who may be shown it after the fact. */
    public static final class Routing {
        public final List<EntityPlayerMP> recipients;
        public final ChatHistory.Audience audience;

        Routing(List<EntityPlayerMP> recipients, ChatHistory.Audience audience) {
            this.recipients = Collections.unmodifiableList(recipients);
            this.audience = audience;
        }

        /** The recipients' account ids, in delivery order. */
        public List<UUID> recipientIds() {
            List<UUID> ids = new ArrayList<UUID>(this.recipients.size());
            for (EntityPlayerMP recipient : this.recipients) {
                ids.add(recipient.getUniqueID());
            }
            return ids;
        }
    }

    private ChatChannelPolicy() {}

    /**
     * Why the channel refuses a send, as the notice the sender is told,
     * or null when it may be sent into. Membership first — a party line
     * needs a party, a faction line a faction — then the role gate,
     * which a channel may ask besides.
     *
     * @param roles    the sender's roles as the selected chat identity
     * @param operator whether the sender holds the server's operator level,
     *                 which is what reaches a staff channel the config
     *                 names no gate for; see {@link #staffOnly}
     * @param consoleReader whether the sender holds
     *                 {@code chat.console.read}, which is what reaches
     *                 the server's console; see {@link #readsConsole}
     */
    public static String sendRefusal(ChatChannel channel, Party party, UUID gameplayId,
                                     String factionId, int roles, boolean operator,
                                     boolean consoleReader) {
        if (channel == null) {
            return "chat.losttales.channel.role_unavailable";
        }
        if (channel.getAccess() == ChatChannelAccess.PARTY_MEMBERSHIP
                && (party == null || gameplayId == null || !party.containsMember(gameplayId))) {
            return "chat.losttales.channel.party_unavailable";
        }
        if (channel.getAccess() == ChatChannelAccess.CHARACTER_FACTION
                && (factionId == null || factionId.length() == 0)) {
            return "chat.losttales.channel.faction_unavailable";
        }
        if (staffOnly(channel, ChatChannelGates.current())) {
            return operator ? null : "chat.losttales.channel.role_unavailable";
        }
        if (channel.getRecipientRule() == ChatRecipientRule.CONSOLE_READERS
                && !consoleReader) {
            return "chat.losttales.channel.role_unavailable";
        }
        if (!ChatChannelGates.current().canSend(roles, channel)) {
            return "chat.losttales.channel.role_unavailable";
        }
        return null;
    }

    /**
     * Whether the channel is staff talk the config says nothing about. A
     * channel whose routing rule is {@link ChatRecipientRule#OPERATORS}
     * reaches everyone the gate admits, so with no entry at all it would
     * reach everyone online — a staff channel opened by a line missing
     * from a file rather than by a decision. Missing is not the same as
     * open: an entry naming {@code any} on both sides is a server saying
     * it wants the channel open, and is honoured.
     */
    public static boolean staffOnly(ChatChannel channel, ChatChannelGates gates) {
        return channel != null
                && channel.getRecipientRule() == ChatRecipientRule.OPERATORS
                && !gates.hasEntry(channel);
    }

    /**
     * Whether the channel is the server's own console, which a
     * capability opens rather than a role gate.
     */
    public static boolean isServerConsole(ChatChannel channel) {
        return channel != null
                && channel.getRecipientRule() == ChatRecipientRule.CONSOLE_READERS;
    }

    /** Whether the player may read the channel, the staff floor included. */
    public static boolean canRead(EntityPlayerMP player, ChatChannel channel,
                                  int roles) {
        ChatChannelGates gates = ChatChannelGates.current();
        if (staffOnly(channel, gates)) {
            return LostTalesPermissions.isOperator(player);
        }
        if (isServerConsole(channel) && !readsConsole(player)) {
            return false;
        }
        return gates.canRead(ChatRolePresentation.isInCharacter(channel)
                ? roles : ChatAccountRoleResolver.resolve(player, null), channel);
    }

    /** Whether the player may send into the channel, the staff floor included. */
    public static boolean canSend(EntityPlayerMP player, ChatChannel channel,
                                  int roles) {
        ChatChannelGates gates = ChatChannelGates.current();
        if (staffOnly(channel, gates)) {
            return LostTalesPermissions.isOperator(player);
        }
        if (isServerConsole(channel) && !readsConsole(player)) {
            return false;
        }
        return gates.canSend(ChatRolePresentation.isInCharacter(channel)
                ? roles : ChatAccountRoleResolver.resolve(player, null), channel);
    }

    /** Whether the refusal is the role gate's, so the client's tabs should be told again. */
    public static boolean isGateRefusal(String refusal) {
        return "chat.losttales.channel.role_unavailable".equals(refusal);
    }

    /**
     * Everyone online a line in the channel reaches, and the audience it
     * is recorded with. A sender of null is a line with nobody behind it
     * on this server, a Discord member's: it has no place to be near and
     * no console of its own. Whispers are not routed here; they are
     * delivered to their two parties before any rule is asked.
     *
     * @param party     the sender's party, for a party line
     * @param factionId the faction the line is spoken to, normalized
     */
    public static Routing route(EntityPlayerMP sender, ChatChannel channel, Party party,
                                String factionId) {
        List<EntityPlayerMP> recipients = new ArrayList<EntityPlayerMP>();
        List<EntityPlayerMP> online = onlinePlayers();
        ChatChannelGates gates = ChatChannelGates.current();
        boolean gated = gates.isGated(channel);
        boolean staffOnly = staffOnly(channel, gates);
        ChatRecipientRule rule = channel.getRecipientRule();
        double proximity = proximityDistanceSquared();
        for (EntityPlayerMP candidate : online) {
            if (candidate == null || candidate.getUniqueID() == null) {
                continue;
            }
            if (staffOnly) {
                if (!LostTalesPermissions.isOperator(candidate)) {
                    continue;
                }
            } else if (gated && !canRead(candidate, channel, ChatIdentitySelection.roles(candidate))) {
                continue;
            }
            boolean reached;
            switch (rule) {
                case GLOBAL:
                case OPERATORS:
                    reached = true;
                    break;
                case SELF:
                    reached = candidate == sender;
                    break;
                case CONSOLE_READERS:
                    reached = readsConsole(candidate);
                    break;
                case PROXIMITY:
                    reached = sender != null && candidate.dimension == sender.dimension
                            && candidate.getDistanceSqToEntity(sender) <= proximity;
                    break;
                case PARTY:
                    reached = isCurrentOnlinePartyMember(candidate, party);
                    break;
                case FACTION:
                    reached = factionId != null && factionId.length() > 0
                            && factionId.equals(factionOf(ChatIdentitySelection.character(candidate)));
                    break;
                default:
                    reached = false;
                    break;
            }
            if (reached) {
                recipients.add(candidate);
            }
        }
        Routing routing = new Routing(recipients, null);
        return new Routing(recipients,
                audienceFor(channel, party, factionId, routing.recipientIds()));
    }

    /**
     * Who may be shown the line after the fact, from how it was routed.
     * An open world-wide channel reaches everyone, later joiners
     * included. A channel whose read side asks for a role — the Operator
     * channel, an open channel the config gates — reaches whoever may
     * read it at the moment of asking, so a role granted afterwards
     * opens everything said before, as a Discord channel shows its past
     * to whoever is let in. A party line reaches the accounts of the
     * party's members then, while they are still in it; a faction line
     * the characters of the faction then and now; everything else —
     * proximity, whispers, a private channel's line — exactly who was
     * sent it, since where a player stood cannot be asked again and a
     * note to oneself is nobody else's.
     */
    public static ChatHistory.Audience audienceFor(ChatChannel channel, Party party,
                                                   String factionId,
                                                   List<UUID> recipientIds) {
        ChatChannelGates.Gate gate = ChatChannelGates.current().gateOf(channel);
        boolean readGated = gate.isReadClosed() || !gate.getReadRoles().isEmpty();
        switch (channel.getRecipientRule()) {
            case GLOBAL:
                return readGated
                        ? ChatHistory.Audience.readers()
                        : ChatHistory.Audience.everyone();
            case OPERATORS:
            // The server's console opens to whoever may read it when it
            // is asked, so a capability granted afterwards shows the
            // stream's past as a Discord channel shows its own.
            case CONSOLE_READERS:
                return ChatHistory.Audience.readers();
            case PARTY:
                List<UUID> owners = new ArrayList<UUID>();
                if (party != null) {
                    for (PartyMember member : party.getMembers()) {
                        if (member != null && member.getOwnerId() != null) {
                            owners.add(member.getOwnerId());
                        }
                    }
                }
                return ChatHistory.Audience.party(
                        party == null ? null : party.getPartyId(), owners);
            case FACTION:
                return ChatHistory.Audience.faction(factionId, readGated);
            case SELF:
                return ChatHistory.Audience.accounts(recipientIds, false);
            default:
                return ChatHistory.Audience.accounts(recipientIds, false);
        }
    }

    /**
     * The roles the player holds as the identity being played: the
     * account's, and those assigned to the active character. What gates
     * are passed with and what the access packet reports; a capability
     * is never granted through a character-scoped role, so
     * {@link LostTalesPermissions} asks the account alone.
     */
    public static int playedRoles(EntityPlayerMP player) {
        RoleplayCharacter active = CharacterActiveResolver.get(player);
        return ChatAccountRoleResolver.resolve(player,
                active == null ? null : active.getCharacterId());
    }

    /** Whether the player may read the Server Console. */
    public static boolean readsConsole(EntityPlayerMP player) {
        return LostTalesPermissions.has(player, LostTalesCapability.CHAT_CONSOLE_READ);
    }

    /**
     * Which conversation of the channel a line belongs to: the faction it
     * is spoken to, the party it is spoken in, or nothing at all for a
     * channel that is only ever one conversation. The one place the scope
     * of a line is decided, so the sender's copy, the recipients' and the
     * history all name the same conversation.
     */
    public static String scopeValueOf(ChatChannel channel, Party party,
                                      String factionId) {
        if (channel == null) {
            return "";
        }
        if (channel.getScope() == ChatChannelScope.FACTION) {
            return factionId == null ? "" : factionId;
        }
        if (channel.getScope() == ChatChannelScope.PARTY) {
            return party == null || party.getPartyId() == null
                    ? "" : party.getPartyId().toString();
        }
        return "";
    }

    /**
     * The faction the selected identity speaks and reads Faction chat in:
     * the character's own, or Unaligned for the account and for a
     * character created without one. Never empty.
     */
    public static String factionOf(RoleplayCharacter character) {
        return LotrCharacterAdapter.factionIdOrUnaligned(
                character == null ? "" : character.getStartingFactionId());
    }

    private static boolean isCurrentOnlinePartyMember(EntityPlayerMP player, Party party) {
        if (party == null || player == null) {
            return false;
        }
        PartyMember member = party.getMember(
                ChatIdentitySelection.identityId(player));
        return member != null && player.getUniqueID().equals(member.getOwnerId());
    }

    /**
     * The selected identity's faction, with the time its Faction history
     * starts: the character's creation, or for the account the creation
     * of its default character, which is when the account first joined.
     */
    public static Map<String, Long> selectedFactions(EntityPlayerMP player) {
        Map<String, Long> owned = new HashMap<String, Long>();
        RoleplayCharacter character = ChatIdentitySelection.character(player);
        owned.put(factionOf(character), Long.valueOf(character != null
                ? character.getCreationTimestamp() : accountSince(player)));
        return owned;
    }

    /**
     * When the account's default character was made, or 0 when the roster
     * cannot be read: the account has been able to read Unaligned talk
     * since then.
     */
    private static long accountSince(EntityPlayerMP player) {
        for (RoleplayCharacter character : charactersOf(player)) {
            if (character != null && character.getKind() == CharacterKind.DEFAULT) {
                return character.getCreationTimestamp();
            }
        }
        return 0L;
    }

    /**
     * The account's characters, or none when the roster cannot be read.
     * A store that cannot answer reaches nobody rather than everybody.
     */
    private static List<RoleplayCharacter> charactersOf(EntityPlayerMP player) {
        if (player == null || player.worldObj == null) {
            return Collections.emptyList();
        }
        try {
            CharacterRoster roster = CharacterStorage.get(player.worldObj)
                    .getRoster(player.getUniqueID());
            return roster == null ? Collections.<RoleplayCharacter>emptyList()
                    : roster.getCharacters();
        } catch (RuntimeException unreadable) {
            return Collections.emptyList();
        }
    }

    /**
     * The roles assigned to each of the player's own characters apart
     * from the account's, by character id; a character with none is left
     * out. What the access packet tells the client, so a line signed as
     * any of the player's characters wears that character's roles and no
     * other's.
     */
    public static Map<UUID, Integer> ownCharacterRoles(EntityPlayerMP player) {
        Map<UUID, Integer> roles = new LinkedHashMap<UUID, Integer>();
        ChatRoleCatalog catalog = ChatRoleCatalog.server();
        for (RoleplayCharacter character : charactersOf(player)) {
            UUID id = character == null ? null : character.getCharacterId();
            int mask = id == null ? 0
                    : ChatAccountRoleResolver.assignedMask(catalog, null, id);
            if (mask != 0) {
                roles.put(id, Integer.valueOf(mask));
            }
        }
        return roles;
    }

    private static double proximityDistanceSquared() {
        double radius = Math.max(1.0D, LostTalesConfig.chatProximityRadius);
        return radius * radius;
    }

    @SuppressWarnings("unchecked")
    private static List<EntityPlayerMP> onlinePlayers() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return Collections.emptyList();
        }
        return server.getConfigurationManager().playerEntityList;
    }
}
