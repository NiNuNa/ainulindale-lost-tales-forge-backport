package com.ninuna.losttales.gui.hud.compass.marker;

import com.ninuna.losttales.client.fellowship.ClientFellowshipStateCache;
import com.ninuna.losttales.client.fellowship.ClientFellowshipTrackingCache;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.fellowship.sync.FellowshipGoHereMarkerSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipStateSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipTrackedMemberSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipTrackingSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;

/** Client-only compass projection of the fellowship members' positions and the go-here markers. */
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
        FellowshipTrackingSnapshot tracking =
                ClientFellowshipTrackingCache.getMatching(state);
        if (tracking == null) {
            return Collections.emptyList();
        }

        int dimensionId = minecraft.thePlayer.dimension;
        ArrayList<LostTalesCompassMarker> result =
                new ArrayList<LostTalesCompassMarker>();
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
