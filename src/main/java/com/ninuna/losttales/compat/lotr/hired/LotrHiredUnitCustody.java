package com.ninuna.losttales.compat.lotr.hired;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.deletion.CharacterDeletionService;
import com.ninuna.losttales.character.identity.PlayableIdentity;
import com.ninuna.losttales.character.identity.PlayableIdentityResolver;
import com.ninuna.losttales.character.lore.ownership.LoreCharacterOwnershipStorage;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import cpw.mods.fml.common.FMLLog;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;
import java.util.UUID;

/**
 * Keeps every hired unit with the identity that hired it. A unit is tagged
 * when hired ({@link LostTalesLotrHiredUnitHook}) or, failing that, when
 * its owner first switches away from the identity holding it. From then on
 * the unit is released to the owner while that identity is active and
 * parked otherwise (see {@link LotrHiredUnitCustodyRule}); loaded units are
 * settled on every switch and login, and a unit whose chunk loads later is
 * settled as it joins the world. A unit whose character was deleted stays
 * parked, and comes back with the character if it is restored; once the
 * character is purged for good, the unit leaves its owner's service. One
 * whose lore character was released stays parked until its owner claims
 * that figure again. Every pass is best effort and never fails the switch
 * that asked for it.
 */
public final class LotrHiredUnitCustody {

    private static volatile boolean failureLogged;

    private LotrHiredUnitCustody() {}

    /**
     * Before the owner leaves {@code outgoing}: its loaded units, and any
     * untagged unit hired by the owner, are parked.
     */
    public static void park(EntityPlayerMP owner, PlayableIdentity outgoing,
                            CharacterRoster roster) {
        settleLoaded(owner, outgoing, null, roster);
    }

    /** Once the owner is playing as {@code active}: its units are released, the rest parked. */
    public static void settle(EntityPlayerMP owner, PlayableIdentity active,
                              CharacterRoster roster) {
        settleLoaded(owner, active, active, roster);
    }

    /**
     * One unit that has just joined the world, settled against whoever
     * hired it when that player is online; an untagged unit is claimed by
     * the identity its hiring player is playing as.
     */
    public static void settleJoined(Entity entity) {
        try {
            LOTREntityNPC unit = activeHiredUnit(entity);
            if (unit == null || !LotrHiredUnitInfoAccess.isAvailable()) {
                return;
            }
            LotrHiredUnitTag tag = LotrHiredUnitTag.read(unit);
            UUID hiring = LotrHiredUnitInfoAccess.hiringUuid(unit.hiredNPCInfo);
            EntityPlayerMP owner = LostTalesServerPlayers.findOnline(
                    tag == null ? hiring : tag.getOwnerId());
            if (owner == null) {
                return;
            }
            PlayableIdentityResolver.Resolution resolution =
                    PlayableIdentityResolver.resolve(owner);
            if (!resolution.isAvailable()) {
                return;
            }
            if (tag == null) {
                LotrHiredUnitTag.of(resolution.getIdentity()).write(unit);
                return;
            }
            settleUnit(unit, tag, owner.getUniqueID(),
                    LotrHiredUnitTag.identityKey(resolution.getIdentity()),
                    resolution.getRoster());
        } catch (Throwable throwable) {
            logFailure(throwable);
        }
    }

    /**
     * Every loaded unit of the owner in every dimension: untagged ones are
     * claimed by {@code claim}, then each is settled against
     * {@code active} (null parks them all).
     */
    private static void settleLoaded(EntityPlayerMP owner, PlayableIdentity claim,
                                     PlayableIdentity active, CharacterRoster roster) {
        try {
            if (owner == null || !LotrHiredUnitInfoAccess.isAvailable()) {
                return;
            }
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null || server.worldServers == null) {
                return;
            }
            UUID ownerId = owner.getUniqueID();
            String activeKey = LotrHiredUnitTag.identityKey(active);
            for (WorldServer world : server.worldServers) {
                if (world == null) {
                    continue;
                }
                List<?> entities = world.loadedEntityList;
                for (int index = 0; index < entities.size(); index++) {
                    Object value = entities.get(index);
                    LOTREntityNPC unit = value instanceof Entity
                            ? activeHiredUnit((Entity)value) : null;
                    if (unit == null) {
                        continue;
                    }
                    LotrHiredUnitTag tag = LotrHiredUnitTag.read(unit);
                    if (tag == null) {
                        if (claim == null || !ownerId.equals(
                                LotrHiredUnitInfoAccess.hiringUuid(unit.hiredNPCInfo))) {
                            continue;
                        }
                        tag = LotrHiredUnitTag.of(claim);
                        tag.write(unit);
                    } else if (!ownerId.equals(tag.getOwnerId())) {
                        continue;
                    }
                    settleUnit(unit, tag, ownerId, activeKey, roster);
                }
            }
        } catch (Throwable throwable) {
            logFailure(throwable);
        }
    }

    private static void settleUnit(LOTREntityNPC unit, LotrHiredUnitTag tag, UUID ownerId,
                                   String activeKey, CharacterRoster roster) {
        LOTRHiredNPCInfo info = unit.hiredNPCInfo;
        if (tag.getCharacterId() != null && roster != null
                && roster.getCharacter(tag.getCharacterId()) == null
                && isGoneForGood(unit.worldObj, tag.getCharacterId())) {
            // The hiring character was purged: the unit leaves its owner's
            // service. LOTR tells the hiring player so, who is the owner,
            // online while their units are settled.
            LotrHiredUnitInfoAccess.setHiringUuid(info, ownerId);
            info.dismissUnit(false);
            return;
        }
        UUID hiring = LotrHiredUnitInfoAccess.hiringUuid(info);
        LotrHiredUnitCustodyRule.Action action = LotrHiredUnitCustodyRule.decide(
                tag.identityKey(), activeKey, ownerId, hiring);
        if (action != LotrHiredUnitCustodyRule.Action.LEAVE) {
            LotrHiredUnitInfoAccess.setHiringUuid(info,
                    LotrHiredUnitCustodyRule.hiringUuidAfter(action, ownerId, hiring));
        }
    }

    /**
     * Whether a character no roster of its owner holds is gone for good:
     * kept neither among the deleted characters, which a restore brings
     * back, nor as a lore figure, which may be claimed again. A store that
     * cannot say keeps the unit parked.
     */
    private static boolean isGoneForGood(World world, UUID characterId) {
        try {
            return CharacterDeletionService.getInstance()
                    .getTombstone(world, characterId) == null
                    && LoreCharacterOwnershipStorage.get(world)
                            .getRecordByCharacterId(characterId) == null;
        } catch (RuntimeException unreadable) {
            return false;
        }
    }

    /** The entity as an actively hired LOTR unit, or null for anything else. */
    static LOTREntityNPC activeHiredUnit(Entity entity) {
        if (!(entity instanceof LOTREntityNPC) || entity.worldObj == null
                || entity.worldObj.isRemote) {
            return null;
        }
        LOTREntityNPC unit = (LOTREntityNPC)entity;
        return unit.hiredNPCInfo != null && unit.hiredNPCInfo.isActive ? unit : null;
    }

    private static void logFailure(Throwable throwable) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        FMLLog.warning("[%s] Hired unit custody failed once and is best effort: %s",
                LostTalesMetaData.MOD_ID, throwable);
    }
}
