package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.server.CharacterActiveResolver;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatIdentitySyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyMember;
import com.ninuna.losttales.party.storage.PartyStorage;
import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.permission.LostTalesPermissions;
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
        Party party = party(player);
        if (party != null) {
            LostTalesChatService.sendContextHistory(player, ChatChannel.PARTY,
                    party.getPartyId().toString(), ChatMessageIds.NONE);
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

    /** Whether a message's explicit identity still matches the selection. */
    static boolean matches(EntityPlayerMP player, int kind, UUID requested) {
        RoleplayCharacter selected = character(player);
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

    public static UUID identityId(EntityPlayerMP player) {
        RoleplayCharacter character = character(player);
        return character == null ? player.getUniqueID() : character.getCharacterId();
    }

    public static Party party(EntityPlayerMP player) { return partyFor(player, identityId(player)); }

    static Party partyFor(EntityPlayerMP player, UUID identityId) {
        try {
            Party party = PartyStorage.get(player.worldObj).getPartyForCharacter(identityId);
            PartyMember member = party == null ? null : party.getMember(identityId);
            return member != null && player.getUniqueID().equals(member.getOwnerId()) ? party : null;
        } catch (RuntimeException failure) {
            warn(failure);
            return null;
        }
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
        Party party = party(player);
        PartyMember member = party == null ? null : party.getMember(identityId(player));
        int color = member == null ? ChatChannel.PARTY.getDisplayColor() : member.getColor().getRgb();
        UUID id = character == null ? null : character.getCharacterId();
        UUID partyId = party == null ? null : party.getPartyId();
        String leader = leaderName(party);
        LAST_STATE.put(player.getUniqueID(), signature(player, id, partyId, color, leader));
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new LostTalesChatIdentitySyncPacket(id, partyId, color, leader,
                        isNarrating(player)), player);
    }

    /** The leader's character name, which names the party's tab; empty without a party. */
    static String leaderName(Party party) {
        PartyMember leader = party == null ? null : party.getLeader();
        return leader == null || leader.getCharacterName() == null ? "" : leader.getCharacterName();
    }

    private static String signature(EntityPlayerMP player, UUID id, UUID partyId, int color,
                                    String leader) {
        return id + ":" + partyId + ":" + color + ":" + leader + ":" + isNarrating(player)
                + ":" + roles(player) + ":"
                + ChatChannelPolicy.factionOf(character(player));
    }

    /** Membership and role changes refresh the chat without replacing gameplay party data. */
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
            Party party = party(player);
            PartyMember member = party == null ? null : party.getMember(identityId(player));
            String state = signature(player, character == null ? null : character.getCharacterId(),
                    party == null ? null : party.getPartyId(), member == null
                            ? ChatChannel.PARTY.getDisplayColor() : member.getColor().getRgb(),
                    leaderName(party));
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
