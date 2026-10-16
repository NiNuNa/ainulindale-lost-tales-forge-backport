package com.ninuna.losttales.gui.hud.compass.marker;

import com.ninuna.losttales.client.fellowship.ClientFellowshipMarks;
import com.ninuna.losttales.client.fellowship.ClientFellowshipStateCache;
import com.ninuna.losttales.client.fellowship.ClientFellowshipTrackingCache;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.fellowship.model.FellowshipMark;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerHeightResolver;
import com.ninuna.losttales.fellowship.sync.FellowshipStateSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipTrackedMemberSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipTrackingSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Client-only compass projection of the fellowship travelled with: its
 * members' positions and its marks, and every go-here mark the player
 * sees.
 */
public final class LostTalesFellowshipCompassMarkerProvider
        implements LostTalesCompassMarkerProvider {

    @Override
    public List<LostTalesCompassMarker> collectMarkers(
            Minecraft minecraft, float partialTicks) {
        if (minecraft == null || minecraft.theWorld == null
                || minecraft.thePlayer == null) {
            return Collections.emptyList();
        }
        FellowshipStateSnapshot state = ClientFellowshipStateCache.getSnapshot();
        int dimensionId = minecraft.thePlayer.dimension;
        ArrayList<LostTalesCompassMarker> result =
                new ArrayList<LostTalesCompassMarker>();
        // The marks come with the state: they stand whether or not the
        // members' positions are fresh.
        if (state != null && state.isAvailable()) {
            for (FellowshipMark mark : state.getCompassMarks()) {
                if (mark.getDimensionId() != dimensionId) {
                    continue;
                }
                result.add(LostTalesCompassMarker.alwaysVisiblePositionWithStateKey(
                        mark.getMarkerId(),
                        mark.isGoHere() ? ClientFellowshipMarks.goHereName(state, mark)
                                : mark.getName(),
                        mark.isGoHere() ? LostTalesCompassMarkerIcon.QUEST
                                : LostTalesCompassMarkerIcon.CAMP,
                        mark.getX(),
                        LostTalesMapMarkerHeightResolver.resolveOr(
                                minecraft.theWorld, dimensionId, mark.getX(),
                                LostTalesMapMarkerHeightResolver.AUTOMATIC_Y,
                                mark.getZ(), minecraft.thePlayer.posY),
                        mark.getZ(),
                        true, true,
                        mark.isGoHere()
                                ? ClientFellowshipMarks.goHereColor(state, mark).getTint()
                                : ClientFellowshipMarks.markColor(state,
                                        state.getTravellingFellowship()).getTint()));
            }
        }
        FellowshipTrackingSnapshot tracking =
                ClientFellowshipTrackingCache.getMatching(state);
        if (tracking == null) {
            return result;
        }
        for (FellowshipTrackedMemberSnapshot member : tracking.getTrackedMembers()) {
            if (member.getDimensionId() != dimensionId) {
                continue;
            }
            result.add(LostTalesCompassMarker.persistentPositionWithStateKey(
                    "fellowship_member:" + member.getIdentityId(),
                    member.getCharacterName(),
                    ClientFellowshipTrackingCache.fellowshipIcon(member.getColor()),
                    member.getX(), member.getY(), member.getZ(),
                    true, true,
                    LostTalesConfig.fellowshipCompassMarkerFadeRadius,
                    member.getColor().getTint()));
        }
        return result;
    }
}
