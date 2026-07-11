package com.ninuna.losttales.character.physics;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/** Aligns Forge's F3+B rendering origin with the actual player AABB. */
public final class CharacterDebugHitboxHook {

    private CharacterDebugHitboxHook() {}

    public static double resolveRenderY(Entity entity, double interpolatedRenderY) {
        if (!(entity instanceof EntityPlayer) || entity.boundingBox == null) {
            return interpolatedRenderY;
        }
        return interpolatedRenderY + entity.boundingBox.minY - entity.posY;
    }
}
