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
 * Gives every player in your world the size of the race they play, as the
 * server does. The game sends no width or height, so the size comes from
 * each player's look as the server sent it.
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
                appearance == null || !appearance.hasCharacter()
                        ? CharacterRaceGameplayRegistry.DEFAULT
                        : LotrRaceProfileAdapter.getInstance().resolve(
                                player.worldObj, appearance.getRaceId());

        boolean hasRace = appearance != null && appearance.hasCharacter();
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
