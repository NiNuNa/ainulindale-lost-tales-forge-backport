package com.ninuna.losttales.gui.hud.compass.marker;

import com.ninuna.losttales.client.party.ClientPartyStateCache;
import com.ninuna.losttales.client.party.ClientPartyTrackingCache;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.party.sync.PartyGoHereMarkerSnapshot;
import com.ninuna.losttales.party.sync.PartyStateSnapshot;
import com.ninuna.losttales.party.sync.PartyTrackedMemberSnapshot;
import com.ninuna.losttales.party.sync.PartyTrackingSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;

/** Client-only compass projection of the party members' positions and the go-here markers. */
public final class LostTalesPartyCompassMarkerProvider
        implements LostTalesCompassMarkerProvider {

    @Override
    public List<LostTalesCompassMarker> collectMarkers(
            Minecraft minecraft, float partialTicks) {
        if (minecraft == null || minecraft.theWorld == null
                || minecraft.thePlayer == null) {
            return Collections.emptyList();
        }
        PartyStateSnapshot state = ClientPartyStateCache.getSnapshot();
        PartyTrackingSnapshot tracking =
                ClientPartyTrackingCache.getMatching(state);
        if (tracking == null) {
            return Collections.emptyList();
        }

        int dimensionId = minecraft.thePlayer.dimension;
        ArrayList<LostTalesCompassMarker> result =
                new ArrayList<LostTalesCompassMarker>();
        for (PartyTrackedMemberSnapshot member : tracking.getTrackedMembers()) {
            if (member.getDimensionId() != dimensionId) {
                continue;
            }
            result.add(LostTalesCompassMarker.persistentPositionWithStateKey(
                    "party_member:" + member.getIdentityId(),
                    member.getCharacterName(),
                    ClientPartyTrackingCache.partyIcon(member.getColor()),
                    member.getX(), member.getY(), member.getZ(),
                    true, true,
                    LostTalesConfig.partyCompassMarkerFadeRadius,
                    member.getColor().getTint()));
        }
        for (PartyGoHereMarkerSnapshot marker : tracking.getGoHereMarkers()) {
            if (marker.getDimensionId() != dimensionId) {
                continue;
            }
            result.add(LostTalesCompassMarker.alwaysVisiblePositionWithStateKey(
                    "party_go_here:" + marker.getOwnerIdentityId(),
                    marker.getOwnerCharacterName(),
                    LostTalesCompassMarkerIcon.QUEST,
                    marker.getX(), marker.getY(), marker.getZ(),
                    true, true,
                    marker.getOwnerColor().getTint()));
        }
        return result;
    }
}
