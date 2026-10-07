package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.server.CharacterActiveResolver;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatFellowship;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatRolePresentation;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatIdentityPacket;
import com.ninuna.losttales.network.packet.LostTalesChatIdentitySyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.storage.FellowshipStorage;
import com.ninuna.losttales.fellowship.storage.FellowshipWorldData;
import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.permission.LostTalesPermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * Who each player reads the roleplaying channels as: the character they
 * play, and the characters their chat's copies speak as, each copy its
 * own person. Faction lines reach a player for every faction one of them
 * is in, and a whisper for any of them; each shows as online. Who a line
 * speaks as is named on the line itself and checked against the
 * sender's roster. No gameplay state is changed by any of it.
 */
public final class ChatIdentitySelection {
    /** Most characters a player's copies read as besides the one played. */
    public static final int MAX_READ = LostTalesChatIdentityPacket.MAX_READ;

    /** The characters each player's copies read as besides the one played, as the client last said. */
    private static final Map<UUID, List<UUID>> READING = new HashMap<UUID, List<UUID>>();
    private static final Map<UUID, String> LAST_STATE = new HashMap<UUID, String>();
    private static int ticks;
    private static boolean storageWarning;

    /**
     * The characters the player's copies read as besides the one played:
     * the ones the player owns, at most {@link #MAX_READ}, kept; any other
     * id is dropped. Then the channels and histories that opens are sent.
     */
    public static void read(EntityPlayerMP player, List<UUID> characterIds) {
        List<UUID> kept = new ArrayList<UUID>();
        if (characterIds != null) {
            for (UUID id : characterIds) {
                if (kept.size() >= MAX_READ) {
                    break;
                }
                if (id != null && !kept.contains(id) && owned(player, id) != null) {
                    kept.add(id);
                }
            }
        }
        UUID owner = player.getUniqueID();
        List<UUID> before = READING.get(owner);
        if (kept.isEmpty()) {
            READING.remove(owner);
        } else {
            READING.put(owner, Collections.unmodifiableList(kept));
        }
        LostTalesChatService.sendAccess(player);
        Set<String> factionsBefore = new HashSet<String>();
        if (before != null) {
            for (UUID id : before) {
                RoleplayCharacter character = owned(player, id);
                if (character != null) {
                    factionsBefore.add(ChatChannelPolicy.factionOf(character));
                }
            }
        }
        for (String factionId : readFactions(player)) {
            if (!factionsBefore.contains(factionId)) {
                LostTalesChatService.sendContextHistory(player, ChatChannel.FACTION,
                        factionId, ChatMessageIds.NONE);
            }
        }
        // Every character read as is one the player uses, so it shows a
        // presence, and one given up may not any more.
        ChatPresenceService.refresh(player);
    }

    /** The character the player plays, or null for the account playing as itself. */
    public static RoleplayCharacter played(EntityPlayerMP player) {
        return CharacterActiveResolver.get(player);
    }

    /** The identity the player plays: the character's id, or the account's own. */
    public static UUID playedId(EntityPlayerMP player) {
        RoleplayCharacter played = played(player);
        return played == null ? player.getUniqueID() : played.getCharacterId();
    }

    /**
     * The characters the player's copies read as besides the one played,
     * each still owned; one given up since is left out.
     */
    public static List<RoleplayCharacter> alsoRead(EntityPlayerMP player) {
        List<UUID> ids = READING.get(player.getUniqueID());
        if (ids == null) {
            return Collections.emptyList();
        }
        RoleplayCharacter played = played(player);
        List<RoleplayCharacter> result = new ArrayList<RoleplayCharacter>(ids.size());
        for (UUID id : ids) {
            if (played != null && id.equals(played.getCharacterId())) {
                continue;
            }
            RoleplayCharacter character = owned(player, id);
            if (character != null) {
                result.add(character);
            }
        }
        return result;
    }

    /**
     * The player's identities: their account and every character of
     * theirs, whether played or read now or not. What a reaction of theirs
     * may have been made as.
     */
    public static Set<UUID> identityIds(EntityPlayerMP player) {
        Set<UUID> ids = new HashSet<UUID>();
        ids.add(player.getUniqueID());
        try {
            CharacterRoster roster = CharacterStorage.get(player.worldObj)
                    .getRoster(player.getUniqueID());
            if (roster != null) {
                for (RoleplayCharacter character : roster.getCharacters()) {
                    if (character != null && character.getCharacterId() != null) {
                        ids.add(character.getCharacterId());
                    }
                }
            }
        } catch (RuntimeException failure) {
            warn(failure);
        }
        return ids;
    }

    /** Whether the player plays the character or a copy of theirs reads as it. */
    public static boolean reads(EntityPlayerMP player, UUID characterId) {
        if (characterId == null) {
            return false;
        }
        RoleplayCharacter played = played(player);
        if (played != null && characterId.equals(played.getCharacterId())) {
            return true;
        }
        for (RoleplayCharacter character : alsoRead(player)) {
            if (characterId.equals(character.getCharacterId())) {
                return true;
            }
        }
        return false;
    }

    /** The factions the player reads Faction chat in: the played identity's first, then each one read as. */
    public static Set<String> readFactions(EntityPlayerMP player) {
        Set<String> factions = new LinkedHashSet<String>();
        factions.add(ChatChannelPolicy.factionOf(played(player)));
        for (RoleplayCharacter character : alsoRead(player)) {
            factions.add(ChatChannelPolicy.factionOf(character));
        }
        return factions;
    }

    /** Whether the player reads the faction's chat as one of their identities. */
    public static boolean readsFaction(EntityPlayerMP player, String factionId) {
        return factionId != null && factionId.length() > 0
                && readFactions(player).contains(factionId);
    }

    /**
     * Who a line from the player speaks as in {@code channel}, as the line
     * names it: the character played where the channel says so
     * ({@link ChatRolePresentation#speaksAsPlayedCharacter}) or the line
     * names no one, else the owned character it names. Out of character
     * the account speaks (null in {@link Worn}). Refused in character for
     * the account, a player with no character yet, or a character the
     * player does not own.
     */
    public static Worn worn(EntityPlayerMP player, ChatChannel channel, int kind,
                            UUID characterId) {
        if (!ChatRolePresentation.isInCharacter(channel)) {
            return Worn.as(null);
        }
        RoleplayCharacter named = kind == LostTalesChatSendPacket.IDENTITY_CHARACTER
                && characterId != null ? owned(player, characterId) : null;
        return decide(ChatRolePresentation.speaksAsPlayedCharacter(channel), kind,
                played(player), named);
    }

    /**
     * The answer {@link #worn} gives an in-character line, from whether the
     * channel speaks as the character played, the kind the line names,
     * the character played (null for the account) and the character the
     * line names as the sender's roster holds it (null for one it does
     * not hold).
     */
    static Worn decide(boolean speaksAsPlayed, int kind, RoleplayCharacter played,
                       RoleplayCharacter named) {
        if (speaksAsPlayed || kind == LostTalesChatSendPacket.IDENTITY_DEFAULT) {
            return played == null ? Worn.REFUSED : Worn.as(played);
        }
        return kind != LostTalesChatSendPacket.IDENTITY_CHARACTER || named == null
                ? Worn.REFUSED : Worn.as(named);
    }

    /** Who a line speaks as: a character, or null for the account; or refused. */
    public static final class Worn {
        static final Worn REFUSED = new Worn(null, true);

        /** The character worn; null for the account. */
        public final RoleplayCharacter character;
        /** Whether the line names an identity the player may not speak as. */
        public final boolean refused;

        private Worn(RoleplayCharacter character, boolean refused) {
            this.character = character;
            this.refused = refused;
        }

        static Worn as(RoleplayCharacter character) {
            return new Worn(character, false);
        }
    }

    /** Whether the player may speak as the Narrator: the capability says so. */
    public static boolean mayNarrate(EntityPlayerMP player) {
        return LostTalesPermissions.has(player, LostTalesCapability.CHAT_NARRATE);
    }

    /** The player's own character {@code id}; null for one their roster does not hold. */
    static RoleplayCharacter owned(EntityPlayerMP player, UUID id) {
        try {
            CharacterRoster roster = CharacterStorage.get(player.worldObj).getRoster(player.getUniqueID());
            return roster == null ? null : roster.getCharacter(id);
        } catch (RuntimeException failure) {
            warn(failure);
            return null;
        }
    }

    /**
     * The fellowships of the character the player plays, the one it
     * travels with first, then the others in the order it joined them.
     */
    public static List<Fellowship> fellowships(EntityPlayerMP player) {
        UUID identityId = playedId(player);
        List<Fellowship> result = new ArrayList<Fellowship>();
        try {
            FellowshipWorldData data = FellowshipStorage.get(player.worldObj);
            Fellowship travelling = data.getTravellingFellowship(identityId);
            if (travelling != null) {
                result.add(travelling);
            }
            for (Fellowship fellowship : data.getFellowshipsForIdentity(identityId)) {
                if (travelling == null || !fellowship.getFellowshipId()
                        .equals(travelling.getFellowshipId())) {
                    result.add(fellowship);
                }
            }
        } catch (RuntimeException failure) {
            warn(failure);
            return result;
        }
        List<Fellowship> owned = new ArrayList<Fellowship>(result.size());
        for (Fellowship fellowship : result) {
            FellowshipMember member = fellowship.getMember(identityId);
            if (member != null && player.getUniqueID().equals(member.getOwnerId())) {
                owned.add(fellowship);
            }
        }
        return owned;
    }

    /** One of the played character's fellowships; null for any other id. */
    public static Fellowship fellowship(EntityPlayerMP player, UUID fellowshipId) {
        if (fellowshipId == null) {
            return null;
        }
        for (Fellowship fellowship : fellowships(player)) {
            if (fellowship.getFellowshipId().equals(fellowshipId)) {
                return fellowship;
            }
        }
        return null;
    }

    /** The ids of the played character's fellowships. */
    public static Set<UUID> fellowshipIds(EntityPlayerMP player) {
        Set<UUID> ids = new HashSet<UUID>();
        for (Fellowship fellowship : fellowships(player)) {
            ids.add(fellowship.getFellowshipId());
        }
        return ids;
    }

    private static void warn(RuntimeException failure) {
        if (!storageWarning) {
            storageWarning = true;
            FMLLog.warning("[losttales/chat] Cannot resolve chat membership: %s", failure.toString());
        }
    }

    /**
     * The roles the player reads with: the account's, and those of the
     * character played and of every character read as. What opens a
     * gated channel to read; a line is sent with the roles of the
     * identity it wears.
     */
    public static int roles(EntityPlayerMP player) {
        RoleplayCharacter played = played(player);
        int roles = ChatAccountRoleResolver.resolve(player,
                played == null ? null : played.getCharacterId());
        for (RoleplayCharacter character : alsoRead(player)) {
            roles |= ChatAccountRoleResolver.resolve(player, character.getCharacterId());
        }
        return roles;
    }

    public static void sendState(EntityPlayerMP player) {
        List<ChatFellowship> fellowships = chatFellowships(player);
        List<UUID> read = readIds(player);
        LAST_STATE.put(player.getUniqueID(), signature(player, read, fellowships));
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new LostTalesChatIdentitySyncPacket(read, fellowships), player);
    }

    /** The ids of the characters read as besides the one played, as kept. */
    private static List<UUID> readIds(EntityPlayerMP player) {
        List<UUID> ids = new ArrayList<UUID>();
        for (RoleplayCharacter character : alsoRead(player)) {
            ids.add(character.getCharacterId());
        }
        return ids;
    }

    /** The played character's fellowships as the chat names and colours them. */
    static List<ChatFellowship> chatFellowships(EntityPlayerMP player) {
        UUID played = playedId(player);
        List<ChatFellowship> result = new ArrayList<ChatFellowship>();
        for (Fellowship fellowship : fellowships(player)) {
            FellowshipMember member = fellowship.getMember(played);
            result.add(new ChatFellowship(fellowship.getFellowshipId(), fellowship.getName(),
                    member.getColor().getRgb()));
        }
        return result;
    }

    private static String signature(EntityPlayerMP player, List<UUID> read,
                                    List<ChatFellowship> fellowships) {
        StringBuilder state = new StringBuilder();
        state.append(playedId(player)).append(':').append(read).append(':');
        for (ChatFellowship fellowship : fellowships) {
            state.append(fellowship.getId()).append('/').append(fellowship.getColor())
                    .append('/').append(fellowship.getName()).append(';');
        }
        return state.append(':').append(roles(player)).append(':')
                .append(readFactions(player)).toString();
    }

    /** Membership and role changes refresh the chat without replacing gameplay fellowship data. */
    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks < 20) { return; }
        ticks = 0;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) { return; }
        for (Object value : server.getConfigurationManager().playerEntityList) {
            if (!(value instanceof EntityPlayerMP)) { continue; }
            EntityPlayerMP player = (EntityPlayerMP)value;
            String state = signature(player, readIds(player), chatFellowships(player));
            if (!state.equals(LAST_STATE.get(player.getUniqueID()))) {
                LostTalesChatService.sendAccess(player);
            }
        }
    }

    public static void forget(UUID owner) {
        READING.remove(owner);
        LAST_STATE.remove(owner);
    }

    public static void clear() {
        READING.clear();
        LAST_STATE.clear();
        ticks = 0;
        storageWarning = false;
    }
}
