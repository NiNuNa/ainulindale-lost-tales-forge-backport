package com.ninuna.losttales.client.mapmarker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure, deterministic screen-space grouping used by the LOTR map overlay. */
final class LostTalesMapMarkerGrouping {
    /**
     * Require substantial artwork overlap before a group forms. Square
     * bounds made diagonally separated icons merge while they still looked
     * far apart, so grouping uses a circular visible-footprint estimate.
     */
    static final float VISIBLE_RADIUS_SCALE = 0.25F;
    /** A small release margin prevents single-pixel zoom jitter. */
    static final float SPLIT_GAP = 0.75F;

    private LostTalesMapMarkerGrouping() {}

    static Result group(List<Entry> entries,
                        Map<String, String> previousMembership) {
        if (entries == null || entries.isEmpty()) {
            return Result.empty();
        }
        int count = entries.size();
        ArrayList<Integer> ordered = new ArrayList<Integer>(count);
        for (int index = 0; index < count; index++) {
            ordered.add(Integer.valueOf(index));
        }
        Collections.sort(ordered, new Comparator<Integer>() {
            @Override
            public int compare(Integer left, Integer right) {
                return LostTalesMapMarkerGrouping.compare(
                        entries.get(left.intValue()),
                        entries.get(right.intValue()));
            }
        });

        boolean[] assigned = new boolean[count];
        ArrayList<Group> groups = new ArrayList<Group>();
        LinkedHashMap<String, String> membership =
                new LinkedHashMap<String, String>();
        for (Integer representativeValue : ordered) {
            int representative = representativeValue.intValue();
            if (assigned[representative]) {
                continue;
            }
            assigned[representative] = true;
            Entry anchor = entries.get(representative);
            ArrayList<Integer> members = new ArrayList<Integer>();
            members.add(Integer.valueOf(representative));
            for (Integer candidateValue : ordered) {
                int candidate = candidateValue.intValue();
                if (assigned[candidate]) {
                    continue;
                }
                Entry other = entries.get(candidate);
                float gap = wereGrouped(
                        anchor.id, other.id, previousMembership)
                        ? SPLIT_GAP : 0.0F;
                if (overlaps(anchor, other, gap)) {
                    assigned[candidate] = true;
                    members.add(Integer.valueOf(candidate));
                }
            }
            groups.add(new Group(representative, members));
            if (members.size() > 1) {
                for (Integer member : members) {
                    membership.put(entries.get(member.intValue()).id,
                            anchor.id);
                }
            }
        }
        return new Result(groups, membership);
    }

    static Result ungroup(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return Result.empty();
        }
        ArrayList<Group> groups = new ArrayList<Group>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            groups.add(new Group(index,
                    Collections.singletonList(Integer.valueOf(index))));
        }
        return new Result(groups, Collections.<String, String>emptyMap());
    }

    /**
     * Back-to-front order: low relevance first, then reverse alphabetical.
     * Drawing in this order leaves the same marker selected by
     * {@link #compare(Entry, Entry)} visibly on top.
     */
    static List<Integer> bottomToTop(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<Integer> ordered =
                new ArrayList<Integer>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            ordered.add(Integer.valueOf(index));
        }
        Collections.sort(ordered, new Comparator<Integer>() {
            @Override
            public int compare(Integer left, Integer right) {
                return -LostTalesMapMarkerGrouping.compare(
                        entries.get(left.intValue()),
                        entries.get(right.intValue()));
            }
        });
        return ordered;
    }

    /**
     * Visual-only travel between a group anchor and the marker's real screen
     * position. The caller supplies the same eased visibility used to fade
     * the marker, keeping opacity and motion perfectly synchronized.
     */
    static float transitionCoordinate(
            float groupAnchor, float markerPosition, float visibility) {
        float clamped = Math.max(0.0F, Math.min(1.0F, visibility));
        return groupAnchor
                + (markerPosition - groupAnchor) * clamped;
    }

    private static boolean wereGrouped(String first, String second,
                                       Map<String, String> membership) {
        if (membership == null || first == null || second == null) {
            return false;
        }
        String firstGroup = membership.get(first);
        return firstGroup != null && firstGroup.equals(membership.get(second));
    }

    private static boolean overlaps(Entry first, Entry second, float gap) {
        float firstX = (first.left + first.right) * 0.5F;
        float firstY = (first.top + first.bottom) * 0.5F;
        float secondX = (second.left + second.right) * 0.5F;
        float secondY = (second.top + second.bottom) * 0.5F;
        float firstRadius = Math.min(
                first.right - first.left,
                first.bottom - first.top) * 0.5F;
        float secondRadius = Math.min(
                second.right - second.left,
                second.bottom - second.top) * 0.5F;
        float threshold = (firstRadius + secondRadius)
                * VISIBLE_RADIUS_SCALE + gap;
        float deltaX = firstX - secondX;
        float deltaY = firstY - secondY;
        return deltaX * deltaX + deltaY * deltaY
                <= threshold * threshold;
    }

    /** Higher relevance, then locale-independent name, then stable ID. */
    static int compare(Entry left, Entry right) {
        if (left.relevanceRank != right.relevanceRank) {
            return left.relevanceRank > right.relevanceRank ? -1 : 1;
        }
        int name = left.displayName.compareToIgnoreCase(right.displayName);
        if (name != 0) {
            return name;
        }
        name = left.displayName.compareTo(right.displayName);
        if (name != 0) {
            return name;
        }
        return left.id.compareTo(right.id);
    }

    static final class Entry {
        private final String id;
        private final String displayName;
        private final int relevanceRank;
        private final float left;
        private final float top;
        private final float right;
        private final float bottom;

        Entry(String id, String displayName, int relevanceRank,
              float left, float top, float right, float bottom) {
            this.id = normalize(id);
            String normalizedName = normalize(displayName);
            this.displayName = normalizedName.length() == 0
                    ? this.id : normalizedName;
            this.relevanceRank = relevanceRank;
            this.left = Math.min(left, right);
            this.top = Math.min(top, bottom);
            this.right = Math.max(left, right);
            this.bottom = Math.max(top, bottom);
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim();
        }
    }

    static final class Group {
        private final int representativeIndex;
        private final List<Integer> memberIndices;
        private final String additionalLabel;

        private Group(int representativeIndex, List<Integer> memberIndices) {
            this.representativeIndex = representativeIndex;
            this.memberIndices = Collections.unmodifiableList(
                    new ArrayList<Integer>(memberIndices));
            int additional = memberIndices.size() - 1;
            this.additionalLabel = additional > 0
                    ? "+" + additional + (additional == 1
                            ? " more" : " more") : "";
        }

        int getRepresentativeIndex() { return this.representativeIndex; }
        List<Integer> getMemberIndices() { return this.memberIndices; }
        int size() { return this.memberIndices.size(); }
        String getAdditionalLabel() { return this.additionalLabel; }
    }

    static final class Result {
        private static final Result EMPTY = new Result(
                Collections.<Group>emptyList(),
                Collections.<String, String>emptyMap());
        private final List<Group> groups;
        private final Map<String, String> membership;

        private Result(List<Group> groups, Map<String, String> membership) {
            this.groups = Collections.unmodifiableList(
                    new ArrayList<Group>(groups));
            this.membership = Collections.unmodifiableMap(
                    new LinkedHashMap<String, String>(membership));
        }

        static Result empty() { return EMPTY; }
        List<Group> getGroups() { return this.groups; }
        Map<String, String> getMembership() { return this.membership; }
    }
}
