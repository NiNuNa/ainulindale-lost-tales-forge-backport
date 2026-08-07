package com.ninuna.losttales.client.mapmarker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure, deterministic screen-space grouping used by the LOTR map overlay.
 *
 * <p>Grouping is decided from the same rectangles the overlay draws. A marker
 * joins the group it overlaps by {@link #JOIN_OVERLAP_RATIO} of the smaller
 * artwork's area, and leaves it again below {@link #LEAVE_OVERLAP_RATIO}.
 * That narrow gap is what stops a marker resting on the threshold from
 * joining and leaving on alternating frames.</p>
 *
 * <p>An existing stack is carried forward as a <em>unit</em> rather than
 * rebuilt from its markers. A unit keeps the leader it had, loses a member
 * only when that member falls below the leave threshold against that leader,
 * and merges into another unit whole. Zooming out therefore only ever brings
 * stacks together, and zooming in is the only thing that takes one apart —
 * which is what makes the two directions inverses of each other.</p>
 *
 * <p>The fan a group is drawn as — its representative plus up to
 * {@link #MAX_FAN_MEMBERS} - 1 companions, with the rest condensed into the
 * "+X more" label — is presentation only and deliberately plays no part in
 * these decisions. Letting it widen the test kept markers grouped well past
 * the point where they had visibly separated.</p>
 *
 * <p>Overlap alone is not enough: a marker also has to be allowed to share a
 * stack, which {@link GroupingCategory} decides. Every grouping decision is
 * therefore one overlap test plus one compatibility test against a real
 * member of the group, never against the fan or any other render state.</p>
 */
final class LostTalesMapMarkerGrouping {
    /** Overlap of the smaller visible area required to join a group. */
    static final float JOIN_OVERLAP_RATIO = 0.25F;
    /**
     * Overlap below which a member leaves the group it is already in. Kept
     * just under {@link #JOIN_OVERLAP_RATIO} so a marker resting on the
     * threshold cannot join and leave on alternating frames.
     */
    static final float LEAVE_OVERLAP_RATIO = 0.22F;
    /** Companions are drawn smaller than the marker leading the stack. */
    static final float COMPANION_SCALE = 0.9F;
    /** Representative plus the two companions drawn beside it. */
    static final int MAX_FAN_MEMBERS = 3;
    /**
     * Sideways bow of a member's travel path, as a share of its own length.
     * A slight curve makes two markers crossing each other readable as two
     * separate movements rather than one smear.
     */
    static final float TRANSITION_ARC_RATIO = 0.14F;
    /** Ceiling on that bow, in GUI pixels, so long travels stay restrained. */
    static final float TRANSITION_ARC_MAX_PIXELS = 4.0F;

    private LostTalesMapMarkerGrouping() {}

    /**
     * Which markers are allowed to share a stack.
     *
     * <p>One constant per map-legend filter, so the rule the player toggles
     * and the rule grouping applies cannot drift apart. Markers only ever
     * group with their own category, and an unrecognised category groups with
     * nothing: an unclassified marker draws on its own rather than silently
     * merging into a stack it may not belong to.</p>
     *
     * <p>Hostile icons are drawn by the overlay's separate transient-enemy
     * pass and never become grouping candidates, so they cannot share a stack
     * with anything.</p>
     */
    enum GroupingCategory {
        LOCATION("locations", true),
        PLAYER_WAYSTONE("player_waystones", true),
        PARTY("party", true),
        /**
         * A player places their own waypoints deliberately, often several
         * close together and each meaning something different, so they stay
         * individually readable at every zoom — as do objective markers.
         */
        PERSONAL_WAYPOINT("personal_waypoints", false),
        SHARED_WAYPOINT("shared_waypoints", false),
        QUEST("quests", false),
        UNKNOWN("", false);

        private final String legendCategoryId;
        private final boolean groupingEligible;

        GroupingCategory(String legendCategoryId,
                         boolean groupingEligible) {
            this.legendCategoryId = legendCategoryId;
            this.groupingEligible = groupingEligible;
        }

        /** Maps a {@code LostTalesMapLegendRegistry} category id. */
        static GroupingCategory forLegendCategory(String categoryId) {
            String normalized = categoryId == null
                    ? "" : categoryId.trim();
            if (normalized.length() != 0) {
                for (GroupingCategory category : values()) {
                    if (category.legendCategoryId.equals(normalized)) {
                        return category;
                    }
                }
            }
            return UNKNOWN;
        }

        boolean isGroupingEligible() {
            return this.groupingEligible;
        }

        boolean canGroupWith(GroupingCategory other) {
            return other != null && this.groupingEligible
                    && other.groupingEligible && this == other;
        }
    }

    /**
     * Clusters markers into stacks, in four steps.
     *
     * <ol>
     *   <li>Every marker is placed in the stack it is already part of. This
     *       is the only step that can take a stack apart, and it does so one
     *       member at a time, as each falls below the leave threshold against
     *       that stack's leader.</li>
     *   <li>Stacks are ordered by their leaders, most relevant first.</li>
     *   <li>The stacks nothing else already covers become the roots.</li>
     *   <li>Every remaining stack merges, whole, into the root its leader
     *       overlaps most.</li>
     * </ol>
     *
     * <p>Because steps 3 and 4 compare leaders and never individual members,
     * a line of markers each merely touching its neighbour still cannot
     * collapse onto one end, and a stack sitting between two others joins the
     * one it is actually on rather than the one that ranks higher. Because
     * step 4 moves a stack as one, two stacks that meet become one stack
     * instead of trading members or leaving one behind.</p>
     *
     * <p>The previous membership is the only history involved, and it decides
     * both which markers are already a stack and which threshold applies to
     * each pair. Every decision is traceable to real, active markers: a fan,
     * a group anchor, or any other render state plays no part.</p>
     */
    static Result group(List<Entry> entries,
                        Map<String, String> previousLinks) {
        if (entries == null || entries.isEmpty()) {
            return Result.empty();
        }
        List<Integer> ordered = sortedIndices(entries);
        Map<String, Integer> indexById = indexById(entries);
        Map<String, String> previousLeaders = leadersOf(previousLinks);
        int[] linkOf = surviveLinks(
                entries, ordered, indexById, previousLinks);
        List<Integer> roots = mergeRoots(
                entries, rootsOf(ordered, linkOf), linkOf, previousLeaders);
        return buildResult(entries, ordered, roots, linkOf);
    }

    private static Map<String, Integer> indexById(List<Entry> entries) {
        Map<String, Integer> indexById = new LinkedHashMap<String, Integer>(
                Math.max(4, entries.size() * 2));
        for (int index = 0; index < entries.size(); index++) {
            indexById.put(entries.get(index).id, Integer.valueOf(index));
        }
        return indexById;
    }

    /**
     * Keeps every link whose two markers are still overlapping, and breaks
     * the rest.
     *
     * <p>This is the whole leaving rule: a marker is held by the one marker
     * it came to rest on, and by nothing else. Holding it for as long as it
     * touched <em>any</em> member of its stack kept markers grouped well past
     * the point where they had visibly parted, and then released the lot of
     * them at once when the last of those incidental overlaps gave way.</p>
     *
     * <p>A marker whose link has broken becomes a root, and anything still
     * linked to it comes along, so a stack always comes apart into stacks.</p>
     */
    private static int[] surviveLinks(
            List<Entry> entries, List<Integer> ordered,
            Map<String, Integer> indexById,
            Map<String, String> previousLinks) {
        int[] linkOf = new int[entries.size()];
        Arrays.fill(linkOf, -1);
        if (previousLinks == null || previousLinks.isEmpty()) {
            return linkOf;
        }
        for (Integer candidateValue : ordered) {
            int index = candidateValue.intValue();
            Entry candidate = entries.get(index);
            String anchorId = previousLinks.get(candidate.id);
            if (anchorId == null || anchorId.equals(candidate.id)) {
                continue;
            }
            Integer anchorIndex = indexById.get(anchorId);
            if (anchorIndex == null) {
                continue;
            }
            Entry anchor = entries.get(anchorIndex.intValue());
            if (candidate.canGroupWith(anchor)
                    && overlap(anchor, candidate)
                            >= LEAVE_OVERLAP_RATIO) {
                linkOf[index] = anchorIndex.intValue();
            }
        }
        return linkOf;
    }

    /** Markers holding no surviving link, most relevant first. */
    private static List<Integer> rootsOf(
            List<Integer> ordered, int[] linkOf) {
        List<Integer> roots = new ArrayList<Integer>();
        for (Integer candidateValue : ordered) {
            if (linkOf[candidateValue.intValue()] < 0) {
                roots.add(candidateValue);
            }
        }
        return roots;
    }

    /**
     * Merges stacks that have come to rest on one another.
     *
     * <p>A stack joins another only by its own root landing on that stack's
     * root — the marker it is actually drawn as. A stack that has swallowed
     * several others covers the map wherever its markers really are, and
     * letting it reach that far had stacks that were plainly far apart
     * swallowing one another.</p>
     *
     * <p>The joining root links exactly as any other marker does, so the
     * merged stack comes apart again along the same seam.</p>
     *
     * @return the roots that survived, most relevant first
     */
    private static List<Integer> mergeRoots(
            List<Entry> entries, List<Integer> roots, int[] linkOf,
            Map<String, String> previousLeaders) {
        // Which stacks survive is settled before anything is linked, so a
        // stack can then be handed to the one it is actually sitting on
        // rather than to whichever happened to be considered first.
        boolean[] isSettled = new boolean[entries.size()];
        List<Integer> settled = new ArrayList<Integer>(roots.size());
        for (Integer rootValue : roots) {
            Entry root = entries.get(rootValue.intValue());
            boolean covered = false;
            for (Integer settledValue : settled) {
                Entry other = entries.get(settledValue.intValue());
                if (root.canGroupWith(other)
                        && overlap(other, root) >= threshold(
                        other.id, root.id, previousLeaders)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                isSettled[rootValue.intValue()] = true;
                settled.add(rootValue);
            }
        }
        for (Integer rootValue : roots) {
            int index = rootValue.intValue();
            if (isSettled[index]) {
                continue;
            }
            Entry root = entries.get(index);
            int chosen = -1;
            float chosenOverlap = 0.0F;
            for (Integer settledValue : settled) {
                Entry other = entries.get(settledValue.intValue());
                if (!root.canGroupWith(other)) {
                    continue;
                }
                float covered = overlap(other, root);
                // Strictly greater, so an exact tie keeps the more relevant
                // stack, which settled first.
                if (covered > chosenOverlap && covered >= threshold(
                        other.id, root.id, previousLeaders)) {
                    chosenOverlap = covered;
                    chosen = settledValue.intValue();
                }
            }
            // A stack is only ever unsettled because a settled one covered
            // it, so this cannot normally fail. Leading itself is the safe
            // answer if it ever does: a marker must never fall out of every
            // stack and stop being drawn.
            if (chosen < 0) {
                isSettled[index] = true;
                settled.add(rootValue);
            } else {
                linkOf[index] = chosen;
            }
        }
        return settled;
    }

    /** The marker leading each marker's stack, from a link map. */
    private static Map<String, String> leadersOf(
            Map<String, String> links) {
        if (links == null || links.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashMap<String, String> leaders =
                new LinkedHashMap<String, String>(links.size() * 2);
        for (Map.Entry<String, String> link : links.entrySet()) {
            String leader = link.getKey();
            // Bounded walk: a link map is a forest, but never loop on input
            // that has been through a version this mod did not write.
            for (int steps = 0; steps < links.size(); steps++) {
                String next = links.get(leader);
                if (next == null || next.equals(leader)) {
                    break;
                }
                leader = next;
            }
            leaders.put(link.getKey(), leader);
        }
        return leaders;
    }

    private static int rootIndex(int[] linkOf, int index) {
        int root = index;
        for (int steps = 0; steps < linkOf.length; steps++) {
            if (linkOf[root] < 0) {
                return root;
            }
            root = linkOf[root];
        }
        return index;
    }

    /**
     * A marker already sharing a group with this leader holds on a little
     * longer than it took to bring them together, so one resting on the
     * threshold cannot join and leave on alternating frames.
     */
    private static float threshold(
            String leaderId, String candidateId,
            Map<String, String> previousMembership) {
        if (previousMembership == null || previousMembership.isEmpty()) {
            return JOIN_OVERLAP_RATIO;
        }
        String candidateGroup = previousMembership.get(candidateId);
        if (candidateGroup == null) {
            return JOIN_OVERLAP_RATIO;
        }
        return candidateGroup.equals(leaderId)
                || candidateGroup.equals(previousMembership.get(leaderId))
                ? LEAVE_OVERLAP_RATIO : JOIN_OVERLAP_RATIO;
    }

    private static float overlap(Entry first, Entry second) {
        return overlapRatio(
                first.left, first.top, first.right, first.bottom,
                second.left, second.top, second.right, second.bottom);
    }

    /**
     * Materialises one stack per root: the root first, then every other
     * marker hanging off it in relevance order.
     */
    private static Result buildResult(
            List<Entry> entries, List<Integer> ordered,
            List<Integer> roots, int[] linkOf) {
        int[] groupOfRoot = new int[entries.size()];
        Arrays.fill(groupOfRoot, -1);
        List<List<Integer>> groupMembers =
                new ArrayList<List<Integer>>(roots.size());
        for (Integer rootValue : roots) {
            groupOfRoot[rootValue.intValue()] = groupMembers.size();
            List<Integer> members = new ArrayList<Integer>();
            members.add(rootValue);
            groupMembers.add(members);
        }
        for (Integer candidateValue : ordered) {
            int index = candidateValue.intValue();
            int root = rootIndex(linkOf, index);
            if (root == index) {
                continue;
            }
            groupMembers.get(groupOfRoot[root]).add(candidateValue);
        }

        List<Group> groups = new ArrayList<Group>(groupMembers.size());
        LinkedHashMap<String, String> links =
                new LinkedHashMap<String, String>();
        LinkedHashMap<String, String> membership =
                new LinkedHashMap<String, String>();
        for (List<Integer> members : groupMembers) {
            int representative = members.get(0).intValue();
            groups.add(new Group(representative, members));
            if (members.size() <= 1) {
                continue;
            }
            String representativeId = entries.get(representative).id;
            for (Integer member : members) {
                int index = member.intValue();
                int anchor = linkOf[index] < 0 ? index : linkOf[index];
                links.put(entries.get(index).id,
                        entries.get(anchor).id);
                membership.put(entries.get(index).id, representativeId);
            }
        }
        return new Result(groups, links, membership);
    }

    private static List<Integer> sortedIndices(final List<Entry> entries) {
        List<Integer> ordered = new ArrayList<Integer>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            ordered.add(Integer.valueOf(index));
        }
        sortByRelevance(entries, ordered);
        return ordered;
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

    static Result ungroup(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return Result.empty();
        }
        ArrayList<Group> groups = new ArrayList<Group>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            groups.add(new Group(index,
                    Collections.singletonList(Integer.valueOf(index))));
        }
        return new Result(groups,
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap());
    }

    /**
     * Back-to-front order over whole stacks: low relevance first, then
     * reverse alphabetical. Drawing in this order leaves the marker selected
     * by {@link #compare(Entry, Entry)} visibly on top.
     *
     * <p>A stack is one item in this order and is ranked by its most relevant
     * member, not by the marker leading it. Ranking by the leader would sink a
     * whole fan below a marker one of its own members outranks, and ordering
     * members individually let unrelated markers be drawn between the sprites
     * of one fan. A lone marker is a stack of one, so individual markers keep
     * exactly the order they had.</p>
     *
     * @return indices into {@code groups}, least relevant first
     */
    static List<Integer> groupsBottomToTop(
            final List<Entry> entries, final List<Group> groups) {
        if (entries == null || entries.isEmpty()
                || groups == null || groups.isEmpty()) {
            return Collections.emptyList();
        }
        final int[] rankedBy = new int[groups.size()];
        for (int index = 0; index < groups.size(); index++) {
            rankedBy[index] = mostRelevantMember(
                    entries, groups.get(index));
        }
        ArrayList<Integer> ordered =
                new ArrayList<Integer>(groups.size());
        for (int index = 0; index < groups.size(); index++) {
            ordered.add(Integer.valueOf(index));
        }
        Collections.sort(ordered, new Comparator<Integer>() {
            @Override
            public int compare(Integer left, Integer right) {
                return -LostTalesMapMarkerGrouping.compare(
                        entries.get(rankedBy[left.intValue()]),
                        entries.get(rankedBy[right.intValue()]));
            }
        });
        return ordered;
    }

    /**
     * The member a stack is ranked by. Usually the marker leading it, but a
     * marker can join a stack led by something it outranks when it overlaps
     * that one more.
     */
    private static int mostRelevantMember(
            List<Entry> entries, Group group) {
        int best = group.getRepresentativeIndex();
        for (Integer memberValue : group.getMemberIndices()) {
            int member = memberValue.intValue();
            if (compare(entries.get(member), entries.get(best)) < 0) {
                best = member;
            }
        }
        return best;
    }

    /**
     * The same marker as it would be drawn at a different zoom.
     *
     * <p>Icons are drawn at a fixed screen size, so zooming moves a marker's
     * centre away from {@code centerX}/{@code centerY} without changing the
     * rectangle it covers. That is what lets a candidate zoom be handed to
     * the real clustering rule instead of being approximated.</p>
     */
    static Entry scaledAbout(
            Entry entry, float centerX, float centerY, float factor) {
        float halfWidth = (entry.right - entry.left) * 0.5F;
        float halfHeight = (entry.bottom - entry.top) * 0.5F;
        float scaledCenterX = centerX
                + ((entry.left + entry.right) * 0.5F - centerX) * factor;
        float scaledCenterY = centerY
                + ((entry.top + entry.bottom) * 0.5F - centerY) * factor;
        return new Entry(entry.id, entry.displayName,
                entry.relevanceRank, entry.category,
                scaledCenterX - halfWidth, scaledCenterY - halfHeight,
                scaledCenterX + halfWidth, scaledCenterY + halfHeight);
    }

    /**
     * Visual-only travel between a group anchor and the marker's real screen
     * position, bowed gently to one side.
     *
     * <p>The caller supplies the same eased visibility used to fade the
     * marker, so opacity and motion stay synchronized, and both endpoints are
     * passed in every frame — the position is solved from them rather than
     * accumulated, so an interrupted or reversed transition simply continues
     * from wherever it had got to and nothing can drift.</p>
     *
     * <p>Both axes are solved together because the curve belongs to the path
     * rather than to either coordinate: the bow is perpendicular to the
     * straight line and peaks halfway along it. The travel vector is always
     * rotated the same way, so the path is deterministic.</p>
     *
     * @param result two floats written with the screen position
     */
    static void transitionPoint(
            float anchorX, float anchorY,
            float markerX, float markerY,
            float visibility, float[] result) {
        float eased = Math.max(0.0F, Math.min(1.0F, visibility));
        float deltaX = markerX - anchorX;
        float deltaY = markerY - anchorY;
        float x = anchorX + deltaX * eased;
        float y = anchorY + deltaY * eased;
        float length = (float)Math.sqrt(
                deltaX * deltaX + deltaY * deltaY);
        if (length > 0.0F) {
            // 4t(1-t) is one at the midpoint and zero at both ends, so the
            // bow can never move where the marker starts or finishes.
            float bow = Math.min(length * TRANSITION_ARC_RATIO,
                    TRANSITION_ARC_MAX_PIXELS)
                    * 4.0F * eased * (1.0F - eased);
            x += -deltaY / length * bow;
            y += deltaX / length * bow;
        }
        result[0] = x;
        result[1] = y;
    }

    /**
     * Intersection area divided by the smaller rectangle's area. Using the
     * smaller area means a marker joins a wide cluster as soon as the cluster
     * covers most of the marker, which is what the eye reads as "hidden
     * behind".
     */
    static float overlapRatio(
            float firstLeft, float firstTop,
            float firstRight, float firstBottom,
            float secondLeft, float secondTop,
            float secondRight, float secondBottom) {
        float width = Math.min(firstRight, secondRight)
                - Math.max(firstLeft, secondLeft);
        float height = Math.min(firstBottom, secondBottom)
                - Math.max(firstTop, secondTop);
        if (width <= 0.0F || height <= 0.0F) {
            return 0.0F;
        }
        float firstArea = (firstRight - firstLeft)
                * (firstBottom - firstTop);
        float secondArea = (secondRight - secondLeft)
                * (secondBottom - secondTop);
        float smaller = Math.min(firstArea, secondArea);
        return smaller <= 0.0F ? 0.0F : width * height / smaller;
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

    /**
     * One active marker as grouping sees it: a stable identity, the rectangle
     * its own icon covers on screen, and the category it may share a stack
     * with. Nothing here describes a fan, a group anchor or any other render
     * state, so a group can never be mistaken for an extra marker.
     */
    static final class Entry {
        private final String id;
        private final String displayName;
        private final int relevanceRank;
        private final GroupingCategory category;
        private final float left;
        private final float top;
        private final float right;
        private final float bottom;

        Entry(String id, String displayName, int relevanceRank,
              GroupingCategory category,
              float left, float top, float right, float bottom) {
            this.id = normalize(id);
            String normalizedName = normalize(displayName);
            this.displayName = normalizedName.length() == 0
                    ? this.id : normalizedName;
            this.relevanceRank = relevanceRank;
            this.category = category == null
                    ? GroupingCategory.UNKNOWN : category;
            this.left = Math.min(left, right);
            this.top = Math.min(top, bottom);
            this.right = Math.max(left, right);
            this.bottom = Math.max(top, bottom);
        }

        String getId() {
            return this.id;
        }

        int getRelevanceRank() {
            return this.relevanceRank;
        }

        GroupingCategory getGroupingCategory() {
            return this.category;
        }

        float getLeft() {
            return this.left;
        }

        float getTop() {
            return this.top;
        }

        boolean isGroupingEligible() {
            return this.category.isGroupingEligible();
        }

        boolean canGroupWith(Entry other) {
            return other != null
                    && this.category.canGroupWith(other.category);
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim();
        }
    }

    static final class Group {
        private final int representativeIndex;
        private final List<Integer> memberIndices;

        private Group(int representativeIndex, List<Integer> memberIndices) {
            this.representativeIndex = representativeIndex;
            this.memberIndices = Collections.unmodifiableList(
                    new ArrayList<Integer>(memberIndices));
        }

        int getRepresentativeIndex() { return this.representativeIndex; }
        List<Integer> getMemberIndices() { return this.memberIndices; }
        int size() { return this.memberIndices.size(); }

        /** Members the fan cannot show, summarised by the "+X more" label. */
        int getCondensedMemberCount() {
            return Math.max(0, this.memberIndices.size() - MAX_FAN_MEMBERS);
        }

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
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap());
        private final List<Group> groups;
        private final Map<String, String> links;
        private final Map<String, String> membership;

        private Result(List<Group> groups, Map<String, String> links,
                       Map<String, String> membership) {
            this.groups = Collections.unmodifiableList(
                    new ArrayList<Group>(groups));
            this.links = Collections.unmodifiableMap(
                    new LinkedHashMap<String, String>(links));
            this.membership = Collections.unmodifiableMap(
                    new LinkedHashMap<String, String>(membership));
        }

        static Result empty() { return EMPTY; }
        List<Group> getGroups() { return this.groups; }

        /**
         * The one marker each marker came to rest on, which is what holds it
         * in its stack. A stack's own leader maps to itself. This is what a
         * later decision must be given back, because it is the only record of
         * how each stack was actually built.
         */
        Map<String, String> getLinks() { return this.links; }

        /** The marker leading each grouped marker's stack. */
        Map<String, String> getMembership() { return this.membership; }
    }
}
