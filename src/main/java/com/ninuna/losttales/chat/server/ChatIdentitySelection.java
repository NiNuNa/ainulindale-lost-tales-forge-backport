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
import com.ninuna.losttales.network.packet.LostTalesChatIdentitySyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.storage.FellowshipStorage;
import com.ninuna.losttales.fellowship.storage.FellowshipWorldData;
import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.permission.LostTalesPermissions;
import java.util.ArrayList;
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
import net.minecraft.util.ChatComponentTranslation;

/** Server-owned chat selections. No gameplay state is changed by selecting one. */
public final class ChatIdentitySelection {
    private static final Map<UUID, UUID> SELECTED = new HashMap<UUID, UUID>();
    private static final Map<UUID, String> LAST_STATE = new HashMap<UUID, String>();
    /** Who has the Narrator's voice taken up; kept only while they may. */
    private static final Set<UUID> NARRATING = new HashSet<UUID>();
    private static int ticks;
    private static boolean storageWarning;

    /**
     * Selects one of the player's characters as the chat identity, or
     * with no id returns to following the played character, and takes
     * the Narrator's voice up over it or puts it down. The account is
     * never chosen: the roleplaying channels fall back to it while no
     * character is held. The voice needs the capability; without it the
     * player is told and the voice stays down.
     */
    public static void select(EntityPlayerMP player, UUID id, boolean narrating) {
        if (id == null) {
            SELECTED.remove(player.getUniqueID());
        } else if (owned(player, id) == null) {
            player.addChatMessage(new ChatComponentTranslation("chat.losttales.identity.unavailable"));
            return;
        } else {
            SELECTED.put(player.getUniqueID(), id);
        }
        if (narrating && !LostTalesPermissions.has(player, LostTalesCapability.CHAT_NARRATE)) {
            player.addChatMessage(new ChatComponentTranslation("chat.losttales.narrator.unavailable"));
            narrating = false;
        }
        if (narrating) {
            NARRATING.add(player.getUniqueID());
        } else {
            NARRATING.remove(player.getUniqueID());
        }
        LostTalesChatService.sendAccess(player);
        RoleplayCharacter character = character(player);
        LostTalesChatService.sendContextHistory(player, ChatChannel.FACTION,
                ChatChannelPolicy.factionOf(character), ChatMessageIds.NONE);
        for (Fellowship fellowship : fellowships(player)) {
            LostTalesChatService.sendContextHistory(player, ChatChannel.FELLOWSHIP,
                    fellowship.getFellowshipId().toString(), ChatMessageIds.NONE);
        }
        // The character spoken as is one the player uses, so it shows a
        // presence, and the one given up may not any more.
        ChatPresenceService.refresh(player);
    }

    public static RoleplayCharacter character(EntityPlayerMP player) {
        UUID owner = player.getUniqueID();
        UUID id = SELECTED.get(owner);
        if (id == null) { return CharacterActiveResolver.get(player); }
        RoleplayCharacter character = owned(player, id);
        if (character != null) { return character; }
        SELECTED.remove(owner);
        return CharacterActiveResolver.get(player);
    }

    /** Whether the player speaks as the Narrator: taken up, and still allowed. */
    public static boolean isNarrating(EntityPlayerMP player) {
        return NARRATING.contains(player.getUniqueID())
                && LostTalesPermissions.has(player, LostTalesCapability.CHAT_NARRATE);
    }

    /** Whether a message's explicit identity still matches who speaks in {@code channel}. */
    static boolean matches(EntityPlayerMP player, ChatChannel channel, int kind, UUID requested) {
        RoleplayCharacter selected = speakerFor(player, channel);
        UUID selectedId = selected == null ? null : selected.getCharacterId();
        return matches(selectedId, kind, requested);
    }

    static boolean matches(UUID selectedId, int kind, UUID requested) {
        if (kind == LostTalesChatSendPacket.IDENTITY_DEFAULT) {
            return true;
        }
        if (kind == LostTalesChatSendPacket.IDENTITY_ACCOUNT) {
            return selectedId == null;
        }
        return kind == LostTalesChatSendPacket.IDENTITY_CHARACTER
                && selectedId != null && selectedId.equals(requested);
    }

    private static RoleplayCharacter owned(EntityPlayerMP player, UUID id) {
        try {
            CharacterRoster roster = CharacterStorage.get(player.worldObj).getRoster(player.getUniqueID());
            return roster == null ? null : roster.getCharacter(id);
        } catch (RuntimeException failure) {
            warn(failure);
            return null;
        }
    }

    public static String key(EntityPlayerMP player) {
        RoleplayCharacter character = character(player);
        return character == null ? "" : character.getCharacterId().toString();
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
     * Who the player speaks as in {@code channel}: the character they play
     * where the channel says so ({@link ChatRolePresentation#speaksAsPlayedCharacter}),
     * else the chat identity.
     */
    public static RoleplayCharacter speakerFor(EntityPlayerMP player, ChatChannel channel) {
        return ChatRolePresentation.speaksAsPlayedCharacter(channel)
                ? played(player) : character(player);
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

    public static int roles(EntityPlayerMP player) {
        RoleplayCharacter character = character(player);
        return ChatAccountRoleResolver.resolve(player, character == null ? null : character.getCharacterId());
    }

    public static void sendState(EntityPlayerMP player) {
        RoleplayCharacter character = character(player);
        UUID id = character == null ? null : character.getCharacterId();
        List<ChatFellowship> fellowships = chatFellowships(player);
        LAST_STATE.put(player.getUniqueID(), signature(player, id, fellowships));
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new LostTalesChatIdentitySyncPacket(id, fellowships, isNarrating(player)),
                player);
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

    private static String signature(EntityPlayerMP player, UUID id,
                                    List<ChatFellowship> fellowships) {
        StringBuilder state = new StringBuilder();
        state.append(id).append(':');
        for (ChatFellowship fellowship : fellowships) {
            state.append(fellowship.getId()).append('/').append(fellowship.getColor())
                    .append('/').append(fellowship.getName()).append(';');
        }
        return state.append(':').append(isNarrating(player)).append(':')
                .append(roles(player)).append(':')
                .append(ChatChannelPolicy.factionOf(character(player))).toString();
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
            RoleplayCharacter character = character(player);
            String state = signature(player, character == null ? null : character.getCharacterId(),
                    chatFellowships(player));
            if (!state.equals(LAST_STATE.get(player.getUniqueID()))) {
                LostTalesChatService.sendAccess(player);
            }
        }
    }

    public static void forget(UUID owner) {
        SELECTED.remove(owner);
        LAST_STATE.remove(owner);
        NARRATING.remove(owner);
    }

    public static void clear() {
        SELECTED.clear();
        LAST_STATE.clear();
        NARRATING.clear();
        ticks = 0;
        storageWarning = false;
    }
}
