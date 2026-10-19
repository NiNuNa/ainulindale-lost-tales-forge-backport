package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.sync.FellowshipMemberPresence;
import com.ninuna.losttales.fellowship.sync.FellowshipSnapshot;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who is online and as whom, read once for a round of snapshots: each
 * online account with the identity it plays and that character's name.
 */
final class FellowshipOnlineView implements FellowshipSnapshot.Presence {

    /** One online account. */
    static final class Online {
        final EntityPlayerMP player;
        /** Who the account is in the world: its active character, else the account itself. */
        final UUID gameplayId;
        /** The active character's name; empty while the first is being made. */
        final String characterName;

        private Online(EntityPlayerMP player, UUID gameplayId, String characterName) {
            this.player = player;
            this.gameplayId = gameplayId;
            this.characterName = characterName;
        }
    }

    private final Map<UUID, Online> byOwner;

    private FellowshipOnlineView(Map<UUID, Online> byOwner) {
        this.byOwner = byOwner;
    }

    /** Everyone online now; nobody without a server or character data. */
    static FellowshipOnlineView collect(CharacterWorldData characterData) {
        Map<UUID, Online> byOwner = new HashMap<UUID, Online>();
        MinecraftServer server = MinecraftServer.getServer();
        List<?> players = server == null || server.getConfigurationManager() == null
                ? null : server.getConfigurationManager().playerEntityList;
        if (players != null && characterData != null) {
            for (Object value : players) {
                if (!(value instanceof EntityPlayerMP)
                        || !LostTalesServerPlayers.isServerPlayer((EntityPlayerMP) value)) {
                    continue;
                }
                EntityPlayerMP player = (EntityPlayerMP) value;
                UUID ownerId = player.getUniqueID();
                CharacterRoster roster = characterData.getRoster(ownerId);
                RoleplayCharacter active = roster == null ? null : roster.getActiveCharacter();
                byOwner.put(ownerId, active != null && ownerId.equals(active.getOwnerId())
                        ? new Online(player, active.getCharacterId(), active.getName())
                        : new Online(player, ownerId, ""));
            }
        }
        return new FellowshipOnlineView(byOwner);
    }

    /** Nobody online: for a view that cannot be read. */
    static FellowshipOnlineView none() {
        return new FellowshipOnlineView(Collections.<UUID, Online>emptyMap());
    }

    Online get(UUID ownerId) {
        return ownerId == null ? null : this.byOwner.get(ownerId);
    }

    Collection<Online> all() {
        return this.byOwner.values();
    }

    @Override
    public FellowshipMemberPresence of(FellowshipMember member) {
        Online online = get(member.getOwnerId());
        if (online == null) {
            return FellowshipMemberPresence.AWAY;
        }
        return member.getIdentityId().equals(online.gameplayId)
                ? FellowshipMemberPresence.HERE : FellowshipMemberPresence.ELSEWHERE;
    }

    @Override
    public String elsewhereName(FellowshipMember member) {
        Online online = get(member.getOwnerId());
        return online == null || member.getIdentityId().equals(online.gameplayId)
                ? "" : online.characterName;
    }
}
