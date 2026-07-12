package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.physics.CharacterEntitySizeHelper;
import com.ninuna.losttales.character.physics.CharacterRaceDimensions;
import com.ninuna.losttales.character.physics.CharacterRaceEntityData;
import com.ninuna.losttales.character.physics.CharacterPlayerEyeHeightHelper;
import com.ninuna.losttales.character.registry.CharacterRaceGameplayProfile;
import com.ninuna.losttales.character.registry.CharacterRaceGameplayRegistry;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.compat.lotr.LotrRaceProfileAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import java.util.List;

/**
 * Mirrors server-authoritative race dimensions on client-side player entities.
 * Width and height are not sent through vanilla entity metadata in 1.7.10, so
 * remote players use the synchronized active-character appearance cache.
 */
public final class ClientCharacterRacePhysics {

    private ClientCharacterRacePhysics() {}

    public static void apply(EntityPlayer player) {
        if (player == null || player.worldObj == null || !player.worldObj.isRemote
                || player.getUniqueID() == null) {
            return;
        }

        CharacterAppearance appearance =
                ClientCharacterAppearanceCache.getAuthoritative(player.getUniqueID());
        CharacterRaceGameplayProfile profile =
                appearance == null || !appearance.isPresent()
                        ? CharacterRaceGameplayRegistry.DEFAULT
                        : LotrRaceProfileAdapter.getInstance().resolve(
                                player.worldObj, appearance.getRaceId());

        boolean hasRace = appearance != null && appearance.isPresent();
        CharacterRaceDimensions dimensions =
                CharacterRaceDimensions.fromProfile(
                        hasRace ? appearance.getRaceId() : "", profile);

        // Vanilla uses a special sleeping body layout. Preserve it and reapply
        // the race dimensions on the first tick after waking.
        if (!player.isPlayerSleeping()) {
            CharacterEntitySizeHelper.apply(player, dimensions);
        }
        CharacterRaceEntityData.write(player, dimensions);
        CharacterPlayerEyeHeightHelper.apply(player, dimensions, hasRace);
    }

    public static void applyAll(Minecraft minecraft) {
        if (minecraft == null || minecraft.theWorld == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<EntityPlayer> players = minecraft.theWorld.playerEntities;
        for (EntityPlayer player : players) {
            apply(player);
        }
    }
}
