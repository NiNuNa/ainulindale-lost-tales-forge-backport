package com.ninuna.losttales.client.chat;

import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.util.ResourceLocation;

/**
 * The @-mention cue as a UI sound rather than a world sound: played through
 * the sound handler with no attenuation, so it is neither positioned at
 * the player entity nor subject to distance, and it only ever plays on the
 * client whose own names were mentioned. Vanilla exposes no public
 * non-positional constructor with a custom volume, hence the subclass.
 */
final class LostTalesChatPingSound extends PositionedSound {
    private static final float VOLUME = 0.4F;

    LostTalesChatPingSound(ResourceLocation sound) {
        super(sound);
        this.volume = VOLUME;
        this.field_147663_c = 1.0F;
        this.repeat = false;
        this.field_147665_h = 0;
        this.field_147666_i = ISound.AttenuationType.NONE;
    }
}
