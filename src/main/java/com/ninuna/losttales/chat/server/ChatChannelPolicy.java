package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.character.identity.RoleplayCharacterIdentityHook;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.server.CharacterActiveResolver;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelAccess;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatRecipientRule;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyMember;
import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.permission.LostTalesPermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
     * @param roles the sender's roles as the identity being played
     */
    public static String sendRefusal(ChatChannel channel, Party party, UUID gameplayId,
                                     String factionId, int roles) {
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
        if (!ChatChannelGates.current().canSend(roles, channel)) {
            return "chat.losttales.channel.role_unavailable";
        }
        return null;
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
        ChatRecipientRule rule = channel.getRecipientRule();
        // A line typed in a private channel by someone who may read the
        // shared console is staff talk and reaches every reader of it;
        // typed by anyone else it is their own note and reaches them alone.
        boolean staffTalk = rule == ChatRecipientRule.SELF && sender != null
                && readsConsole(sender);
        double proximity = proximityDistanceSquared();
        for (EntityPlayerMP candidate : online) {
            if (candidate == null || candidate.getUniqueID() == null) {
                continue;
            }
            if (gated && !gates.canRead(playedRoles(candidate), channel)) {
                continue;
            }
            boolean reached;
            switch (rule) {
                case GLOBAL:
                case OPERATORS:
                    reached = true;
                    break;
                case SELF:
                    reached = candidate == sender || (staffTalk && readsConsole(candidate));
                    break;
                case PROXIMITY:
                    reached = sender != null && candidate.dimension == sender.dimension
                            && candidate.getDistanceSqToEntity(sender) <= proximity;
                    break;
                case PARTY:
                    reached = isCurrentOnlinePartyMember(candidate, party);
                    break;
                case FACTION:
                    reached = isCurrentFactionMember(candidate, factionId);
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
     * included. A channel whose read side asks for a role reaches only
     * those who were sent the line and may still read it, so a role
     * granted afterwards opens nothing said before. A party line reaches
     * the accounts of the party's members then, while they are still in
     * it; a faction line the characters of the faction then and now; a
     * private channel's line stays with who was sent it and may still
     * read the channel; everything else — proximity above all — exactly
     * who was sent it.
     */
    public static ChatHistory.Audience audienceFor(ChatChannel channel, Party party,
                                                   String factionId,
                                                   List<UUID> recipientIds) {
        ChatChannelGates.Gate gate = ChatChannelGates.current().gateOf(channel);
        boolean readGated = gate.isReadClosed() || !gate.getReadRoles().isEmpty();
        switch (channel.getRecipientRule()) {
            case GLOBAL:
                return readGated
                        ? ChatHistory.Audience.accounts(recipientIds, true)
                        : ChatHistory.Audience.everyone();
            case OPERATORS:
                return ChatHistory.Audience.accounts(recipientIds, true);
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
                return ChatHistory.Audience.faction(factionId, recipientIds, readGated);
            case SELF:
                return ChatHistory.Audience.accounts(recipientIds, true);
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

    /** Whether the player may read the shared operator console. */
    public static boolean readsConsole(EntityPlayerMP player) {
        return LostTalesPermissions.has(player, LostTalesCapability.CHAT_CONSOLE_READ);
    }

    /** The played character's normalized faction id, or empty for none. */
    public static String playedFactionId(RoleplayCharacter character) {
        return character == null ? ""
                : LotrCharacterAdapter.normalizeFactionId(character.getStartingFactionId());
    }

    private static boolean isCurrentOnlinePartyMember(EntityPlayerMP player, Party party) {
        if (party == null || player == null) {
            return false;
        }
        PartyMember member = party.getMember(
                RoleplayCharacterIdentityHook.resolveGameplayId(player));
        return member != null && player.getUniqueID().equals(member.getOwnerId());
    }

    private static boolean isCurrentFactionMember(EntityPlayerMP player, String factionId) {
        if (player == null || factionId == null || factionId.length() == 0) {
            return false;
        }
        return factionId.equals(playedFactionId(CharacterActiveResolver.get(player)));
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
