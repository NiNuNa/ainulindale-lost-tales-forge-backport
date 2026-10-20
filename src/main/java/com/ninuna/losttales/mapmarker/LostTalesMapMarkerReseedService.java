package com.ninuna.losttales.mapmarker;

import java.util.Set;

/**
 * Puts a marker's file, bundled or the server's own, back on its record
 * (WAY-16). The file gives
 * the place, the structure and every setting nobody saved on the marker's
 * page; a setting saved there stays as saved, and so do the shares, which
 * no file holds. A marker nobody edited takes its file whole. A waystone
 * already standing keeps its block: its link and place stay as they are.
 */
public final class LostTalesMapMarkerReseedService {
    private LostTalesMapMarkerReseedService() {}

    public static LostTalesMapMarkerRecord reseed(
            LostTalesMapMarkerWorldData data,
            LostTalesMapMarkerDefinition definition) {
        if (data == null || definition == null) {
            throw new IllegalArgumentException(
                    "reseed requires marker data and a definition");
        }
        LostTalesMapMarkerRecord replacement = createRecord(
                definition, data.getRecord(definition.getId()));
        data.saveRecord(replacement);
        data.noteSeeded(replacement.getId());
        return replacement;
    }

    static LostTalesMapMarkerRecord createRecord(
            LostTalesMapMarkerDefinition definition,
            LostTalesMapMarkerRecord existing) {
        if (definition == null) {
            throw new IllegalArgumentException(
                    "reseed requires a marker definition");
        }
        LostTalesMapMarkerRecord file =
                LostTalesMapMarkerRecord.fromDefinition(
                        definition, existing == null
                                ? definition.getId()
                                : existing.getId());
        if (existing == null) {
            return file;
        }
        LostTalesMapMarkerRecord.Builder replacement = keepSaved(file, existing)
                .revision(existing.getRevision() + 1L);
        if (!existing.isLinked()) {
            return replacement.build();
        }
        if (!file.hasWaystone()) {
            throw new IllegalStateException(
                    "break the linked waystone before reseeding a marker without waystone intent");
        }
        return replacement
                .position(existing.getLinkedDimensionId(),
                        existing.getLinkedX(), existing.getLinkedY(),
                        existing.getLinkedZ())
                .generationState(
                        LostTalesWaystoneGenerationState.PLACED)
                .link(existing.getLinkedDimensionId(),
                        existing.getLinkedX(), existing.getLinkedY(),
                        existing.getLinkedZ(),
                        existing.getLinkToken())
                .build();
    }

    /**
     * The file's record with every setting {@code existing} saved on its
     * page, and its shares, kept.
     */
    private static LostTalesMapMarkerRecord.Builder keepSaved(
            LostTalesMapMarkerRecord file,
            LostTalesMapMarkerRecord existing) {
        Set<LostTalesMapMarkerSetting> saved = existing.getSavedSettings();
        return file.toBuilder()
                .name(pick(saved, LostTalesMapMarkerSetting.NAME,
                        existing, file).getName())
                .iconName(pick(saved, LostTalesMapMarkerSetting.ICON,
                        existing, file).getIconName())
                .colorName(pick(saved, LostTalesMapMarkerSetting.COLOR,
                        existing, file).getColorName())
                .categoryName(pick(saved, LostTalesMapMarkerSetting.CATEGORY,
                        existing, file).getCategoryName())
                .description(pick(saved,
                        LostTalesMapMarkerSetting.DESCRIPTION,
                        existing, file).getDescription())
                .fastTravel(pick(saved, LostTalesMapMarkerSetting.FAST_TRAVEL,
                        existing, file).hasFastTravel())
                .radii(pick(saved, LostTalesMapMarkerSetting.COMPASS_RADIUS,
                                existing, file).getCompassFadeInRadius(),
                        pick(saved, LostTalesMapMarkerSetting.DISCOVERY_RADIUS,
                                existing, file).getDiscoveryRadius())
                .discovery(pick(saved, LostTalesMapMarkerSetting.HIDDEN,
                                existing, file).isHiddenUntilDiscovered(),
                        pick(saved, LostTalesMapMarkerSetting.DISCOVERABLE,
                                existing, file).isDiscoverable(),
                        pick(saved, LostTalesMapMarkerSetting.REGION,
                                existing, file).requiresRegionUnlock())
                .priority(pick(saved, LostTalesMapMarkerSetting.RELEVANCE,
                        existing, file).getPriority())
                .visibility(pick(saved, LostTalesMapMarkerSetting.ACCESS,
                        existing, file).getVisibility())
                .sharedCharacterIds(existing.getSharedCharacterIds())
                .sharedFellowshipIds(existing.getSharedFellowshipIds())
                .savedSettings(saved);
    }

    /** The record a setting is read from: the saved one where it was saved, else the file's. */
    private static LostTalesMapMarkerRecord pick(
            Set<LostTalesMapMarkerSetting> saved,
            LostTalesMapMarkerSetting setting,
            LostTalesMapMarkerRecord existing,
            LostTalesMapMarkerRecord file) {
        return saved.contains(setting) ? existing : file;
    }
}
