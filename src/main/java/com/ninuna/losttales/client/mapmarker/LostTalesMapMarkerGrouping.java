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
     * Group at roughly sixty percent artwork overlap. Square bounds made
     * diagonally separated icons merge while they still looked far apart, so
     * grouping uses a circular visible-footprint estimate.
     */
    static final float VISIBLE_RADIUS_SCALE = 0.40F;
    /** A small release margin prevents single-pixel zoom jitter. */
    static final float SPLIT_GAP = 0.75F;
    static final float COMPANION_OFFSET_X = 4.0F;
    static final float COMPANION_OFFSET_Y = -2.0F;
    private static final float HORIZONTAL_FOOTPRINT_SCALE = 1.20F;

    private LostTalesMapMarkerGrouping() {}

    static Result group(List<Entry> entries,
                        Map<String, String> previousMembership) {
        return group(entries, previousMembership,
                VISIBLE_RADIUS_SCALE, false);
    }

    static Result group(List<Entry> entries,
                        Map<String, String> previousMembership,
                        float visibleRadiusScale,
                        boolean preservePreviousGroups) {
        if (entries == null || entries.isEmpty()) {
            return Result.empty();
        }
        final float radiusScale = Math.max(0.0F,
                Math.min(1.0F, visibleRadiusScale));
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
        if (preservePreviousGroups) {
            return groupAtomicStacks(entries, ordered,
                    previousMembership, radiusScale);
        }
        if (previousMembership != null
                && !previousMembership.isEmpty()) {
            return splitPreviousGroups(entries, ordered,
                    previousMembership, radiusScale);
        }

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
                if (overlaps(anchor, other, gap, radiusScale)) {
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

    /**
     * Zooming in may split an existing stack, but it must not move a marker
     * sideways into a different stack. Keeping the previous lineages
     * independent makes zoom-in the exact inverse of zoom-out merging.
     */
    private static Result splitPreviousGroups(
            List<Entry> entries, List<Integer> ordered,
            Map<String, String> previousMembership,
            float radiusScale) {
        LinkedHashMap<String, ArrayList<Integer>> membersByLineage =
                new LinkedHashMap<String, ArrayList<Integer>>();
        for (Integer indexValue : ordered) {
            Entry entry = entries.get(indexValue.intValue());
            String key = previousGroupKey(entry.id, previousMembership);
            ArrayList<Integer> members = membersByLineage.get(key);
            if (members == null) {
                members = new ArrayList<Integer>();
                membersByLineage.put(key, members);
            }
            members.add(indexValue);
        }

        ArrayList<Group> groups = new ArrayList<Group>();
        LinkedHashMap<String, String> membership =
                new LinkedHashMap<String, String>();
        for (ArrayList<Integer> lineage : membersByLineage.values()) {
            // Partition only inside the previous lineage. A large stack can
            // therefore peel into smaller child stacks as the map expands,
            // without any child jumping sideways into a neighbouring stack.
            boolean[] assigned = new boolean[entries.size()];
            for (Integer representativeValue : lineage) {
                int representative = representativeValue.intValue();
                if (assigned[representative]) {
                    continue;
                }
                assigned[representative] = true;
                Entry anchor = entries.get(representative);
                ArrayList<Integer> retained = new ArrayList<Integer>();
                retained.add(representativeValue);
                for (Integer memberValue : lineage) {
                    int member = memberValue.intValue();
                    if (assigned[member]) {
                        continue;
                    }
                    if (overlaps(anchor, entries.get(member),
                            SPLIT_GAP, radiusScale)) {
                        assigned[member] = true;
                        retained.add(memberValue);
                    }
                }
                addGroup(entries, groups, membership, retained);
            }
        }
        sortGroupsByRepresentative(entries, groups);
        return new Result(groups, membership);
    }

    private static void addGroup(
            List<Entry> entries, List<Group> groups,
            Map<String, String> membership,
            ArrayList<Integer> members) {
        sortByRelevance(entries, members);
        int representative = members.get(0).intValue();
        groups.add(new Group(representative, members));
        if (members.size() <= 1) {
            return;
        }
        String representativeId = entries.get(representative).id;
        for (Integer member : members) {
            membership.put(entries.get(member.intValue()).id,
                    representativeId);
        }
    }

    private static void sortGroupsByRepresentative(
            final List<Entry> entries, List<Group> groups) {
        Collections.sort(groups, new Comparator<Group>() {
            @Override
            public int compare(Group left, Group right) {
                return LostTalesMapMarkerGrouping.compare(
                        entries.get(left.representativeIndex),
                        entries.get(right.representativeIndex));
            }
        });
    }

    private static Result groupAtomicStacks(
            List<Entry> entries, List<Integer> ordered,
            Map<String, String> previousMembership,
            float radiusScale) {
        LinkedHashMap<String, ArrayList<Integer>> membersByStack =
                new LinkedHashMap<String, ArrayList<Integer>>();
        for (Integer indexValue : ordered) {
            Entry entry = entries.get(indexValue.intValue());
            String key = previousGroupKey(entry.id, previousMembership);
            ArrayList<Integer> members = membersByStack.get(key);
            if (members == null) {
                members = new ArrayList<Integer>();
                membersByStack.put(key, members);
            }
            members.add(indexValue);
        }

        ArrayList<AtomicStack> stacks = new ArrayList<AtomicStack>();
        for (ArrayList<Integer> members : membersByStack.values()) {
            stacks.add(new AtomicStack(entries, members));
        }
        boolean[] assigned = new boolean[stacks.size()];
        ArrayList<Group> groups = new ArrayList<Group>();
        LinkedHashMap<String, String> membership =
                new LinkedHashMap<String, String>();
        for (int stackIndex = 0; stackIndex < stacks.size(); stackIndex++) {
            if (assigned[stackIndex]) {
                continue;
            }
            assigned[stackIndex] = true;
            AtomicStack anchor = stacks.get(stackIndex);
            ArrayList<Integer> members =
                    new ArrayList<Integer>(anchor.memberIndices);
            for (int candidateIndex = 0;
                 candidateIndex < stacks.size(); candidateIndex++) {
                if (assigned[candidateIndex]) {
                    continue;
                }
                AtomicStack candidate = stacks.get(candidateIndex);
                if (stacksOverlap(anchor, candidate, radiusScale)) {
                    assigned[candidateIndex] = true;
                    members.addAll(candidate.memberIndices);
                }
            }
            sortByRelevance(entries, members);
            int representative = members.get(0).intValue();
            String representativeId = entries.get(representative).id;
            groups.add(new Group(representative, members));
            if (members.size() > 1) {
                for (Integer member : members) {
                    membership.put(entries.get(member.intValue()).id,
                            representativeId);
                }
            }
        }
        return new Result(groups, membership);
    }

    private static void sortByRelevance(
            final List<Entry> entries, List<Integer> indices) {
        Collections.sort(indices, new Comparator<Integer>() {
            @Override
            public int compare(Integer left, Integer right) {
                return LostTalesMapMarkerGrouping.compare(
                        entries.get(left.intValue()),
                        entries.get(right.intValue()));
            }
        });
    }

    private static boolean stacksOverlap(
            AtomicStack first, AtomicStack second,
            float radiusScale) {
        for (Entry firstIcon : first.visibleIcons) {
            for (Entry secondIcon : second.visibleIcons) {
                if (overlaps(firstIcon, secondIcon,
                        0.0F, radiusScale)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String previousGroupKey(
            String markerId, Map<String, String> previousMembership) {
        if (markerId == null) {
            return "";
        }
        String group = previousMembership == null
                ? null : previousMembership.get(markerId);
        return group == null ? markerId : group;
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

    private static boolean overlaps(
            Entry first, Entry second, float gap,
            float visibleRadiusScale) {
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
                * visibleRadiusScale + gap;
        float deltaX = (firstX - secondX)
                / HORIZONTAL_FOOTPRINT_SCALE;
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

    private static final class AtomicStack {
        private final List<Integer> memberIndices;
        private final List<Entry> visibleIcons;

        private AtomicStack(List<Entry> entries,
                            List<Integer> memberIndices) {
            this.memberIndices = Collections.unmodifiableList(
                    new ArrayList<Integer>(memberIndices));
            Entry representative = entries.get(
                    memberIndices.get(0).intValue());
            ArrayList<Entry> icons = new ArrayList<Entry>(3);
            icons.add(representative);
            if (memberIndices.size() > 1) {
                icons.add(representative.shifted(
                        -COMPANION_OFFSET_X, COMPANION_OFFSET_Y));
            }
            if (memberIndices.size() > 2) {
                icons.add(representative.shifted(
                        COMPANION_OFFSET_X, COMPANION_OFFSET_Y));
            }
            this.visibleIcons = Collections.unmodifiableList(icons);
        }
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

        private Entry shifted(float offsetX, float offsetY) {
            return new Entry(this.id, this.displayName,
                    this.relevanceRank,
                    this.left + offsetX, this.top + offsetY,
                    this.right + offsetX, this.bottom + offsetY);
        }

        Entry withScaledSpacing(float scale) {
            if (scale == 1.0F) {
                return this;
            }
            float centerX = (this.left + this.right) * 0.5F;
            float centerY = (this.top + this.bottom) * 0.5F;
            float halfWidth = (this.right - this.left) * 0.5F;
            float halfHeight = (this.bottom - this.top) * 0.5F;
            float scaledCenterX = centerX * scale;
            float scaledCenterY = centerY * scale;
            return new Entry(this.id, this.displayName,
                    this.relevanceRank,
                    scaledCenterX - halfWidth,
                    scaledCenterY - halfHeight,
                    scaledCenterX + halfWidth,
                    scaledCenterY + halfHeight);
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

        int getCompanionSide(int candidateIndex) {
            if (this.memberIndices.size() > 1
                    && this.memberIndices.get(1).intValue()
                            == candidateIndex) {
                return -1;
            }
            if (this.memberIndices.size() > 2
                    && this.memberIndices.get(2).intValue()
                            == candidateIndex) {
                return 1;
            }
            return 0;
        }
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
