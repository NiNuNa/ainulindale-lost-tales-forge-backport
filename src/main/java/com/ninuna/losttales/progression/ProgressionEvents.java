package com.ninuna.losttales.progression;
import com.ninuna.losttales.progression.deed.DeedService;

import net.minecraft.entity.player.EntityPlayerMP;

/** What else moves when a character grows: its deeds (PROG-11). */
final class ProgressionEvents {
    private ProgressionEvents() {}

    /** A skill reached new levels, and maybe the character too. */
    static void levelsReached(EntityPlayerMP player, ProgressionState state,
                              ProgressionRules.Gain gain) {
        DeedService.evaluate(player);
    }
}
