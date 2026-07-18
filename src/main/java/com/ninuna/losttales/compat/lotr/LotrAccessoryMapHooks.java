package com.ninuna.losttales.compat.lotr;

import com.mojang.authlib.GameProfile;
import com.ninuna.losttales.accessory.effect.AccessoryEffectService;
import lotr.common.LOTRLevelData;
import lotr.common.network.LOTRPacketUpdatePlayerLocations;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** Keeps concealed players out of LOTR's server-authored map-location packet. */
public final class LotrAccessoryMapHooks {

    private LotrAccessoryMapHooks() {}

    public static void addPlayerLocationIfVisible(
            LOTRPacketUpdatePlayerLocations packet,
            GameProfile profile, double x, double z) {
        if (packet != null && profile != null && profile.getId() != null
                && !AccessoryEffectService.isConcealed(profile.getId())) {
            packet.addPlayerLocation(profile, x, z);
        }
    }

    public static void refreshPlayerLocations(World world) {
        if (world == null || world.isRemote || world.playerEntities == null) {
            return;
        }
        List<?> recipients = new ArrayList<Object>(world.playerEntities);
        for (Object value : recipients) {
            if (value instanceof EntityPlayer) {
                LOTRLevelData.sendPlayerLocationsToPlayer(
                        (EntityPlayer)value, world);
            }
        }
    }
}
