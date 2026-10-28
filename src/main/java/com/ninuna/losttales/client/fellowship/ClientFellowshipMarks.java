package com.ninuna.losttales.client.fellowship;

import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.mapmarker.LostTalesMapMarkerData;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipMark;
import com.ninuna.losttales.fellowship.model.FellowshipNames;
import com.ninuna.losttales.fellowship.sync.FellowshipMemberSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipStateSnapshot;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerHeightResolver;
import net.minecraft.util.StatCollector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The marks the fellowship state carries, as the map shows them: each
 * fellowship's marks, a camp in the colour this player wears in it, and
 * the go-here marks of the player and of the members of the fellowship
 * travelled with, each in its owner's colour. Built again whenever another
 * state arrives.
 */
public final class ClientFellowshipMarks {

    private static FellowshipStateSnapshot builtFrom;
    private static List<LostTalesMapMarkerData> mapMarkers = Collections.emptyList();

    private ClientFellowshipMarks() {}

    /** The marks on the map, the same list while the state stays the same. */
    public static synchronized List<LostTalesMapMarkerData> getMapMarkers() {
        FellowshipStateSnapshot state = ClientFellowshipStateCache.getSnapshot();
        if (state != builtFrom) {
            builtFrom = state;
            mapMarkers = build(state);
        }
        return mapMarkers;
    }

    /** A mark's marker on the map by its id; null while the state holds no such mark. */
    public static synchronized LostTalesMapMarkerData markMarker(String markerId) {
        if (markerId == null) {
            return null;
        }
        for (LostTalesMapMarkerData marker : getMapMarkers()) {
            if (markerId.equals(marker.getId())) {
                return marker;
            }
        }
        return null;
    }

    /** The go-here mark of the character played; null for none. */
    public static FellowshipMark ownGoHere(FellowshipStateSnapshot state) {
        return state == null || !state.isAvailable() ? null
                : state.getGoHereOf(state.getActiveIdentityId());
    }

    public static synchronized void clear() {
        builtFrom = null;
        mapMarkers = Collections.emptyList();
    }

    /**
     * The colour this player wears in a fellowship, which its marks wear
     * on the map and in its conversation's links; null for a fellowship
     * the character played is not in.
     */
    public static String markTint(UUID fellowshipId) {
        FellowshipStateSnapshot state = ClientFellowshipStateCache.getSnapshot();
        FellowshipSnapshot fellowship = state == null || !state.isAvailable()
                || fellowshipId == null ? null : state.getFellowship(fellowshipId);
        return fellowship == null ? null : markColor(state, fellowship).getTint();
    }

    /**
     * The colour a fellowship's marks wear for this player: the colour they
     * wear in it, as the Fellowships page does.
     */
    public static FellowshipColor markColor(FellowshipStateSnapshot state,
                                            FellowshipSnapshot fellowship) {
        FellowshipMemberSnapshot own = state == null || fellowship == null
                ? null : fellowship.getMember(state.getActiveIdentityId());
        return own == null ? FellowshipColor.BLUE : own.getColor();
    }

    /** The colour a go-here mark wears: its owner's in the fellowship travelled with, else green. */
    public static FellowshipColor goHereColor(FellowshipStateSnapshot state, FellowshipMark mark) {
        FellowshipMemberSnapshot owner = ownerOf(state, mark);
        return owner == null ? FellowshipColor.GREEN : owner.getColor();
    }

    /** The name a go-here mark goes by: its owner's, or the word for an unknown one. */
    public static String goHereName(FellowshipStateSnapshot state, FellowshipMark mark) {
        FellowshipMemberSnapshot owner = ownerOf(state, mark);
        if (owner != null) {
            return owner.getCharacterName();
        }
        CharacterRosterSnapshot roster = ClientCharacterRosterCache.getSnapshot();
        CharacterSummary active = roster == null ? null : roster.getActiveCharacter();
        return FellowshipNames.shown(active != null
                && active.getCharacterId().equals(mark.getPlacedBy()) ? active.getName() : "");
    }

    private static FellowshipMemberSnapshot ownerOf(FellowshipStateSnapshot state,
                                                    FellowshipMark mark) {
        FellowshipSnapshot travelling = state == null || !state.isAvailable()
                ? null : state.getTravellingFellowship();
        return travelling == null ? null : travelling.getMember(mark.getPlacedBy());
    }

    private static List<LostTalesMapMarkerData> build(FellowshipStateSnapshot state) {
        if (state == null || !state.isAvailable() || state.getMarks().isEmpty()) {
            return Collections.emptyList();
        }
        List<LostTalesMapMarkerData> markers = new ArrayList<LostTalesMapMarkerData>();
        for (FellowshipMark mark : state.getMarks()) {
            markers.add(mark.isGoHere() ? goHereMark(state, mark)
                    : fellowshipMarker(state, mark));
        }
        return Collections.unmodifiableList(markers);
    }

    private static LostTalesMapMarkerData goHereMark(FellowshipStateSnapshot state,
                                                       FellowshipMark mark) {
        return new LostTalesMapMarkerData(
                mark.getMarkerId(),
                goHereName(state, mark),
                LostTalesCompassMarkerIcon.QUEST.name(),
                goHereColor(state, mark).getTint(),
                "Go Here",
                StatCollector.translateToLocal("gui.losttales.map.go_here.description"),
                false,
                mark.getDimensionId(),
                mark.getX(), LostTalesMapMarkerHeightResolver.AUTOMATIC_Y, mark.getZ(),
                0.0D,
                1.0D,
                false,
                false);
    }

    /** A fellowship's mark: its name, a camp, and whose it is and who placed it under the pointer. */
    private static LostTalesMapMarkerData fellowshipMarker(FellowshipStateSnapshot state,
                                                           FellowshipMark mark) {
        FellowshipSnapshot fellowship = state.getFellowship(mark.getFellowshipId());
        FellowshipMemberSnapshot placer = fellowship.getMember(mark.getPlacedBy());
        return new LostTalesMapMarkerData(
                mark.getMarkerId(),
                mark.getName(),
                LostTalesCompassMarkerIcon.CAMP.name(),
                markColor(state, fellowship).getTint(),
                "Fellowship Mark",
                placer == null
                        ? StatCollector.translateToLocalFormatted(
                                "gui.losttales.map.fellowship_mark.of",
                                fellowship.getName())
                        : StatCollector.translateToLocalFormatted(
                                "gui.losttales.map.fellowship_mark.placed",
                                fellowship.getName(),
                                placer.getCharacterName()),
                false,
                mark.getDimensionId(),
                mark.getX(), LostTalesMapMarkerHeightResolver.AUTOMATIC_Y, mark.getZ(),
                0.0D,
                1.0D,
                false,
                false);
    }
}
