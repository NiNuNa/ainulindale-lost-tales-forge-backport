package com.ninuna.losttales.gui.hud.compass.marker;

import com.ninuna.losttales.client.fellowship.ClientFellowshipStateCache;
import com.ninuna.losttales.client.fellowship.ClientFellowshipTrackingCache;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.fellowship.model.FellowshipMark;
import com.ninuna.losttales.fellowship.sync.FellowshipGoHereMarkerSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipSnapshot;
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
 * members' positions, its go-here markers and its marks.
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
        // The marks of the fellowship travelled with, from its state: they
        // stand whether or not the members' positions are fresh.
        FellowshipSnapshot travelling = state == null || !state.isAvailable()
                ? null : state.getTravellingFellowship();
        if (travelling != null) {
            String tint = ClientFellowshipTrackingCache.markColor(state,
                    travelling).getTint();
            for (FellowshipMark mark : travelling.getMarks()) {
                if (mark.getDimensionId() != dimensionId) {
                    continue;
                }
                result.add(LostTalesCompassMarker.alwaysVisiblePositionWithStateKey(
                        mark.getMarkerId(),
                        mark.getName(),
                        LostTalesCompassMarkerIcon.CAMP,
                        mark.getX(),
                        LostTalesMapMarkerHeightResolver.resolveOr(
                                minecraft.theWorld, dimensionId, mark.getX(),
                                LostTalesMapMarkerHeightResolver.AUTOMATIC_Y,
                                mark.getZ(), minecraft.thePlayer.posY),
                        mark.getZ(),
                        true, true, tint));
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
        for (FellowshipGoHereMarkerSnapshot marker : tracking.getGoHereMarkers()) {
            if (marker.getDimensionId() != dimensionId) {
                continue;
            }
            result.add(LostTalesCompassMarker.alwaysVisiblePositionWithStateKey(
                    "fellowship_go_here:" + marker.getOwnerIdentityId(),
                    marker.getOwnerCharacterName(),
                    LostTalesCompassMarkerIcon.QUEST,
                    marker.getX(), marker.getY(), marker.getZ(),
                    true, true,
                    marker.getOwnerColor().getTint()));
        }
        return result;
    }
}
