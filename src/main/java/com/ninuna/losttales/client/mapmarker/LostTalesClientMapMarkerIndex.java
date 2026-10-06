package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerIdentity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lotr.common.LOTRDimension;

/**
 * Atomic client index for persistent and map-only marker projections.
 * Quest markers replace world records with the same logical identity. Fellowship
 * markers are map-only and never replace either persistent authority.
 */
final class LostTalesClientMapMarkerIndex {
    private List<LostTalesMapMarkerData> worldMarkers =
            Collections.emptyList();
    private List<LostTalesMapMarkerData> questMarkers =
            Collections.emptyList();
    private volatile Snapshot persistentSnapshot = Snapshot.empty();
    private volatile Snapshot mapSnapshot = Snapshot.empty();
    private Collection<LostTalesMapMarkerData> indexedFellowshipMarkers;

    public synchronized void replaceWorldMarkers(
            Collection<LostTalesMapMarkerData> markers) {
        this.worldMarkers = immutableCopy(markers);
        rebuildPersistentSnapshot();
    }

    public synchronized void replaceQuestMarkers(
            Collection<LostTalesMapMarkerData> markers) {
        this.questMarkers = immutableCopy(markers);
        rebuildPersistentSnapshot();
    }

    public synchronized void replacePersistentMarkers(
            Collection<LostTalesMapMarkerData> world,
            Collection<LostTalesMapMarkerData> quest) {
        this.worldMarkers = immutableCopy(world);
        this.questMarkers = immutableCopy(quest);
        rebuildPersistentSnapshot();
    }

    public Snapshot getPersistentSnapshot() {
        return this.persistentSnapshot;
    }

    public synchronized Snapshot getMapSnapshot(
            Collection<LostTalesMapMarkerData> fellowshipMarkers) {
        Collection<LostTalesMapMarkerData> safeFellowship = fellowshipMarkers == null
                ? Collections.<LostTalesMapMarkerData>emptyList()
                : fellowshipMarkers;
        if (this.indexedFellowshipMarkers != safeFellowship) {
            this.indexedFellowshipMarkers = safeFellowship;
            this.mapSnapshot = Snapshot.createMap(
                    this.persistentSnapshot,
                    immutableCopy(safeFellowship));
        }
        return this.mapSnapshot;
    }

    static Snapshot createDecorativeSnapshot(
            Collection<LostTalesMapMarkerData> markers) {
        return Snapshot.createPersistent(
                immutableCopy(markers),
                Collections.<LostTalesMapMarkerData>emptyList());
    }

    private void rebuildPersistentSnapshot() {
        this.persistentSnapshot = Snapshot.createPersistent(
                this.worldMarkers, this.questMarkers);
        this.indexedFellowshipMarkers = null;
        this.mapSnapshot = this.persistentSnapshot;
    }

    private static List<LostTalesMapMarkerData> immutableCopy(
            Collection<LostTalesMapMarkerData> markers) {
        if (markers == null || markers.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<LostTalesMapMarkerData> copy =
                new ArrayList<LostTalesMapMarkerData>(markers.size());
        for (LostTalesMapMarkerData marker : markers) {
            if (marker != null && marker.getId() != null
                    && marker.getId().trim().length() > 0) {
                copy.add(marker);
            }
        }
        return copy.isEmpty()
                ? Collections.<LostTalesMapMarkerData>emptyList()
                : Collections.unmodifiableList(copy);
    }

    static final class Snapshot {
        private final List<LostTalesMapMarkerData> allMarkers;
        private final Map<String, LostTalesMapMarkerData>
                markersByCanonicalKey;
        private final Map<String, LostTalesMapMarkerData> fastTravelByCode;
        private final Map<String, LostTalesMapMarkerData>
                fastTravelByPosition;
        private final Map<String, LostTalesMapMarkerData> fastTravelByName;

        private Snapshot(
                List<LostTalesMapMarkerData> allMarkers,
                Map<String, LostTalesMapMarkerData> markersByCanonicalKey,
                Map<String, LostTalesMapMarkerData> fastTravelByCode,
                Map<String, LostTalesMapMarkerData> fastTravelByPosition,
                Map<String, LostTalesMapMarkerData> fastTravelByName) {
            this.allMarkers = allMarkers;
            this.markersByCanonicalKey = markersByCanonicalKey;
            this.fastTravelByCode = fastTravelByCode;
            this.fastTravelByPosition = fastTravelByPosition;
            this.fastTravelByName = fastTravelByName;
        }

        static Snapshot empty() {
            return createPersistent(
                    Collections.<LostTalesMapMarkerData>emptyList(),
                    Collections.<LostTalesMapMarkerData>emptyList());
        }

        static Snapshot createPersistent(
                List<LostTalesMapMarkerData> world,
                List<LostTalesMapMarkerData> quest) {
            LinkedHashMap<String, LostTalesMapMarkerData> merged =
                    new LinkedHashMap<String, LostTalesMapMarkerData>();
            addReplacing(merged, world);
            addReplacing(merged, quest);
            return createFromMerged(merged);
        }

        static Snapshot createMap(
                Snapshot persistent,
                List<LostTalesMapMarkerData> fellowship) {
            LinkedHashMap<String, LostTalesMapMarkerData> merged =
                    new LinkedHashMap<String, LostTalesMapMarkerData>();
            if (persistent != null) {
                for (LostTalesMapMarkerData marker
                        : persistent.allMarkers) {
                    LostTalesMapMarkerIdentity identity = identity(marker);
                    if (identity != null) {
                        merged.put(identity.getCanonicalKey(), marker);
                    }
                }
            }
            addMissing(merged, fellowship);
            return createFromMerged(merged);
        }

        List<LostTalesMapMarkerData> getAllMarkers() {
            return this.allMarkers;
        }

        LostTalesMapMarkerData findById(String markerId) {
            LostTalesMapMarkerIdentity identity = createLookupIdentity(
                    markerId);
            return identity == null ? null
                    : this.markersByCanonicalKey.get(
                            identity.getCanonicalKey());
        }

        LostTalesMapMarkerData findMappedWaypointMarker(
                String waypointCode, String waypointDisplay,
                int worldX, int worldZ) {
            LostTalesMapMarkerData marker = this.fastTravelByCode.get(
                    normalizeLookupKey(waypointCode));
            if (marker != null) {
                return marker;
            }
            marker = this.fastTravelByPosition.get(
                    coordinateKey(worldX, worldZ));
            if (marker != null) {
                return marker;
            }
            marker = this.fastTravelByName.get(
                    normalizeLookupKey(waypointCode));
            return marker != null ? marker
                    : this.fastTravelByName.get(
                            normalizeLookupKey(waypointDisplay));
        }

        private static Snapshot createFromMerged(
                LinkedHashMap<String, LostTalesMapMarkerData> merged) {
            ArrayList<LostTalesMapMarkerData> markers =
                    new ArrayList<LostTalesMapMarkerData>(merged.values());
            LinkedHashMap<String, LostTalesMapMarkerData> byCode =
                    new LinkedHashMap<String, LostTalesMapMarkerData>();
            LinkedHashMap<String, LostTalesMapMarkerData> byPosition =
                    new LinkedHashMap<String, LostTalesMapMarkerData>();
            LinkedHashMap<String, LostTalesMapMarkerData> byName =
                    new LinkedHashMap<String, LostTalesMapMarkerData>();
            for (LostTalesMapMarkerData marker : markers) {
                if (!isWaypointMappingCandidate(marker)) {
                    continue;
                }
                String code = normalizeLookupKey(
                        marker.getLotrWaypointId());
                if (code.length() > 0) {
                    putFirst(byCode, code, marker);
                } else {
                    putFirst(byPosition, coordinateKey(
                            (int)Math.round(marker.getX()),
                            (int)Math.round(marker.getZ())), marker);
                    putFirst(byName, normalizeLookupKey(
                            marker.getGivenName()), marker);
                }
            }
            return new Snapshot(
                    Collections.unmodifiableList(markers),
                    Collections.unmodifiableMap(
                            new LinkedHashMap<String,
                                    LostTalesMapMarkerData>(merged)),
                    Collections.unmodifiableMap(byCode),
                    Collections.unmodifiableMap(byPosition),
                    Collections.unmodifiableMap(byName));
        }

        private static void addReplacing(
                Map<String, LostTalesMapMarkerData> destination,
                List<LostTalesMapMarkerData> source) {
            if (source == null) {
                return;
            }
            for (LostTalesMapMarkerData marker : source) {
                LostTalesMapMarkerIdentity identity = identity(marker);
                if (identity != null) {
                    destination.put(
                            identity.getCanonicalKey(), marker);
                }
            }
        }

        private static void addMissing(
                Map<String, LostTalesMapMarkerData> destination,
                List<LostTalesMapMarkerData> source) {
            if (source == null) {
                return;
            }
            for (LostTalesMapMarkerData marker : source) {
                LostTalesMapMarkerIdentity identity = identity(marker);
                if (identity != null
                        && !destination.containsKey(
                                identity.getCanonicalKey())) {
                    destination.put(
                            identity.getCanonicalKey(), marker);
                }
            }
        }

        private static LostTalesMapMarkerIdentity identity(
                LostTalesMapMarkerData marker) {
            if (marker == null || marker.getId() == null
                    || marker.getId().trim().length() == 0) {
                return null;
            }
            return LostTalesMapMarkerIdentity.create(marker.getId());
        }

        private static LostTalesMapMarkerIdentity createLookupIdentity(
                String markerId) {
            if (markerId == null || markerId.trim().length() == 0) {
                return null;
            }
            return LostTalesMapMarkerIdentity.create(markerId);
        }

        private static boolean isWaypointMappingCandidate(
                LostTalesMapMarkerData marker) {
            return marker != null && marker.getDimensionId()
                    == LOTRDimension.MIDDLE_EARTH.dimensionID;
        }

        private static void putFirst(
                Map<String, LostTalesMapMarkerData> map,
                String key, LostTalesMapMarkerData marker) {
            if (key != null && key.length() > 0
                    && !map.containsKey(key)) {
                map.put(key, marker);
            }
        }

        private static String normalizeLookupKey(String value) {
            String normalized = value == null ? ""
                    : value.trim().toUpperCase(Locale.ROOT);
            StringBuilder builder =
                    new StringBuilder(normalized.length());
            for (int index = 0; index < normalized.length(); index++) {
                char character = normalized.charAt(index);
                if (character >= 'A' && character <= 'Z'
                        || character >= '0' && character <= '9') {
                    builder.append(character);
                } else if (builder.length() > 0
                        && builder.charAt(builder.length() - 1) != '_') {
                    builder.append('_');
                }
            }
            while (builder.length() > 0
                    && builder.charAt(builder.length() - 1) == '_') {
                builder.deleteCharAt(builder.length() - 1);
            }
            return builder.toString();
        }

        private static String coordinateKey(int x, int z) {
            return x + ":" + z;
        }
    }
}
