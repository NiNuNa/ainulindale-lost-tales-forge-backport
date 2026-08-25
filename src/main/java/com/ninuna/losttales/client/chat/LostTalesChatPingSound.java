package com.ninuna.losttales.client.chat;

import java.util.Random;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.util.ResourceLocation;

/**
 * The @-mention cue as a UI sound rather than a world sound: played through
 * the sound handler with no attenuation, so it is neither positioned at
 * the player entity nor subject to distance, and it only ever plays on the
 * client whose own names were mentioned. Vanilla exposes no public
 * non-positional constructor with a custom volume, hence the subclass.
 *
 * <p>Each play varies slightly, the way game audio usually humanizes a
 * repeated one-shot: the pitch — which is also the playback speed in this
 * engine — wanders a few percent around one, and the volume a little
 * around its base, so a burst of mentions reads as several soft chimes
 * rather than the same sample stamped out again and again. The ranges are
 * small on purpose; a cue that changes character stops being recognisable
 * as the cue.</p>
 */
final class LostTalesChatPingSound extends PositionedSound {
    private static final float VOLUME = 0.4F;
    /** Pitch wanders this far either side of one. */
    private static final float PITCH_VARIATION = 0.06F;
    /** Volume wanders this share either side of its base. */
    private static final float VOLUME_VARIATION = 0.15F;
    private static final Random VARIATION = new Random();

    LostTalesChatPingSound(ResourceLocation sound) {
        super(sound);
        this.volume = VOLUME * (1.0F + spread() * VOLUME_VARIATION);
        this.field_147663_c = 1.0F + spread() * PITCH_VARIATION;
        this.repeat = false;
        this.field_147665_h = 0;
        this.field_147666_i = ISound.AttenuationType.NONE;
    }

    /** A value in (-1, 1), centre-weighted so extremes stay rare. */
    private static float spread() {
        return (VARIATION.nextFloat() + VARIATION.nextFloat()) - 1.0F;
    }
}
