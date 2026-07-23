package com.ninuna.losttales.mapmarker;

/**
 * Replaces JSON-owned marker state without orphaning an existing physical
 * waystone. Linked block coordinates and tokens remain authoritative.
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
        return replacement;
    }

    static LostTalesMapMarkerRecord createRecord(
            LostTalesMapMarkerDefinition definition,
            LostTalesMapMarkerRecord existing) {
        if (definition == null) {
            throw new IllegalArgumentException(
                    "reseed requires a marker definition");
        }
        LostTalesMapMarkerRecord replacement =
                LostTalesMapMarkerRecord.fromDefinition(definition);
        if (existing == null) {
            return replacement;
        }
        if (!existing.isLinked()) {
            return replacement.toBuilder()
                    .revision(existing.getRevision() + 1L)
                    .build();
        }
        if (!replacement.hasWaystone()) {
            throw new IllegalStateException(
                    "break the linked waystone before reseeding a marker without waystone intent");
        }
        return replacement.toBuilder()
                .position(existing.getLinkedDimensionId(),
                        existing.getLinkedX(), existing.getLinkedY(),
                        existing.getLinkedZ())
                .generationState(
                        LostTalesWaystoneGenerationState.PLACED, "")
                .link(existing.getLinkedDimensionId(),
                        existing.getLinkedX(), existing.getLinkedY(),
                        existing.getLinkedZ(),
                        existing.getLinkToken())
                .revision(existing.getRevision() + 1L)
                .build();
    }
}
