package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import lotr.common.LOTRReflection;
import lotr.common.command.LOTRCommandFellowship;
import lotr.common.command.LOTRCommandFellowshipMessage;

/**
 * LOTR's commands that Lost Tales takes away, as LOTR itself takes away
 * vanilla's {@code /time} and {@code /msg}: gone from the command list,
 * from {@code /help} and from completion. {@code /fellowship} made LOTR
 * fellowships beside the mod's own, which nobody could answer or leave;
 * {@code /fmsg} (and {@code /fchat}) said words to a fellowship, which its
 * own conversation page does. Run each time the server starts, after LOTR
 * has registered its commands; a LOTR without them keeps them.
 */
public final class LotrCommands {
    private LotrCommands() {}

    public static void removeReplaced() {
        try {
            LOTRReflection.removeCommand(LOTRCommandFellowship.class);
            LOTRReflection.removeCommand(LOTRCommandFellowshipMessage.class);
        } catch (LinkageError incompatible) {
            FMLLog.warning("[%s] LOTR's fellowship commands could not be removed: %s",
                    LostTalesMetaData.MOD_ID, incompatible.toString());
        }
    }
}
