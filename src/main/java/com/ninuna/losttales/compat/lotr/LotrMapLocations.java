package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.util.LostTalesLog;
import lotr.common.LOTRConfig;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Whether a player hides their place on LOTR's map, as LOTR decides it for
 * a player who is not an operator: an admin hidden from the map always
 * hides; LOTR's *Force map locations* hides everyone at 1 and shows
 * everyone at 2; otherwise the player's own *hide my place* decides. A
 * player whose LOTR data is not read yet, or cannot be read, counts as
 * hidden; a failure is said once.
 */
public final class LotrMapLocations {
    /** LOTR's *Force map locations* value that hides everyone. */
    static final int FORCE_HIDDEN = 1;
    /** LOTR's *Force map locations* value that shows everyone. */
    static final int FORCE_SHOWN = 2;

    private static boolean warned;

    private LotrMapLocations() {}

    /** Whether a player with these settings is hidden from LOTR's map. */
    public static boolean hidden(int forceMapLocations, boolean hidesOwnPlace,
                                 boolean adminHidden) {
        if (adminHidden || forceMapLocations == FORCE_HIDDEN) {
            return true;
        }
        return forceMapLocations != FORCE_SHOWN && hidesOwnPlace;
    }

    /** Whether the player is hidden from LOTR's map; true where LOTR cannot be asked. */
    public static boolean hides(EntityPlayer player) {
        if (player == null) {
            return true;
        }
        try {
            // Before LOTR has read its players' data nobody's choice is known.
            if (LOTRLevelData.needsLoad) {
                return true;
            }
            LOTRPlayerData data = LOTRLevelData.getData(player);
            if (data == null) {
                warn("no LOTR data for " + player.getCommandSenderName());
                return true;
            }
            return hidden(LOTRConfig.forceMapLocations, data.getHideMapLocation(),
                    data.getAdminHideMap());
        } catch (RuntimeException failure) {
            warn(failure.toString());
            return true;
        } catch (LinkageError error) {
            warn(error.toString());
            return true;
        }
    }

    private static synchronized void warn(String reason) {
        if (!warned) {
            warned = true;
            LostTalesLog.warning("Whether a player hides on LOTR's map could not be read,"
                    + " so their place is not shared: %s", reason);
        }
    }
}
