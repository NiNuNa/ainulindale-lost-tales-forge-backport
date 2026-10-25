package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.client.mapmarker.LostTalesMapCustomWaypoint;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.LOTRDimension;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.world.map.LOTRAbstractWaypoint;
import lotr.common.world.map.LOTRCustomWaypoint;
import net.minecraft.client.Minecraft;

/**
 * The custom waypoints LOTR's client holds for the player: their own and
 * the ones their fellowships share with them, for Find Location (MAP-13).
 * Where LOTR's data cannot be read the list is empty and the map finds
 * its own markers alone.
 */
@SideOnly(Side.CLIENT)
public final class LotrClientCustomWaypoints {
    private static boolean failureLogged;

    private LotrClientCustomWaypoints() {}

    /** The player's own waypoints and those shared with them, in LOTR's order. */
    public static List<LostTalesMapCustomWaypoint> read(Minecraft minecraft) {
        if (minecraft == null || minecraft.thePlayer == null) {
            return Collections.emptyList();
        }
        try {
            LOTRPlayerData data = LOTRLevelData.getData(minecraft.thePlayer);
            if (data == null) {
                return Collections.emptyList();
            }
            int dimension = LOTRDimension.MIDDLE_EARTH.dimensionID;
            List<LostTalesMapCustomWaypoint> waypoints =
                    new ArrayList<LostTalesMapCustomWaypoint>();
            for (LOTRAbstractWaypoint waypoint
                    : data.getAllAvailableWaypoints()) {
                if (!(waypoint instanceof LOTRCustomWaypoint)) {
                    continue;
                }
                LOTRCustomWaypoint custom = (LOTRCustomWaypoint)waypoint;
                boolean shared = custom.isShared();
                waypoints.add(new LostTalesMapCustomWaypoint(custom.getID(),
                        custom.getCodeName(), shared,
                        shared && custom.isSharedHidden(), dimension,
                        custom.getXCoord(), custom.getYCoordSaved(),
                        custom.getZCoord()));
            }
            return waypoints;
        } catch (RuntimeException | LinkageError changed) {
            reportOnce(changed);
            return Collections.emptyList();
        }
    }

    /** Whether LOTR's map shows the shared waypoints the player hid. */
    public static boolean showsHiddenShared() {
        try {
            return LOTRGuiMap.showHiddenSWP;
        } catch (LinkageError changed) {
            reportOnce(changed);
            return false;
        }
    }

    private static void reportOnce(Throwable changed) {
        if (!failureLogged) {
            failureLogged = true;
            FMLLog.warning("[%s] Find Location cannot read LOTR's custom "
                            + "waypoints and lists the mod's markers alone (%s)",
                    LostTalesMetaData.MOD_ID, changed);
        }
    }
}
