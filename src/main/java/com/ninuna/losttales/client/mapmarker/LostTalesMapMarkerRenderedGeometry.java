package com.ninuna.losttales.client.mapmarker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reusable screen-space geometry for one rendered marker frame.
 *
 * <p>The overlay records the final animated position of every represented
 * marker here before drawing or hit testing it. Bounds include the marker's
 * one-pixel shadow, so consumers do not need to reconstruct sprite geometry
 * from a world position or a grouping radius.</p>
 */
final class LostTalesMapMarkerRenderedGeometry {
    static final float ICON_SHADOW_OFFSET = 1.0F;
    /** Small GUI-pixel allowance around visible artwork for mouse use. */
    static final float INTERACTION_PADDING = 1.0F;
    static final float GROUP_LABEL_GAP = 3.0F;
    /**
     * How far a member may travel out of its fan before it stops counting
     * towards the stack's shape. 0 is gathered on the leader, 1 is fully
     * arrived at its own position.
     */
    static final float FAN_RELEASE_VISIBILITY = 0.5F;
    private static final float MIN_VISIBLE_ALPHA = 0.001F;

    private LostTalesMapMarkerRenderedGeometry() {}

    /**
     * Visible bounds of drawn marker artwork.
     *
     * <p>Extents are measured from the marker's coordinate to the edge of the
     * ink, not to the edge of the atlas cell: every glyph sits inside several
     * pixels of transparent margin. The right and bottom edges also cover the
     * one-pixel drop shadow, which is real ink on screen.</p>
     */
    static float artLeft(float centerX, float artHalfWidth) {
        return centerX - artHalfWidth;
    }

    static float artTop(float centerY, float artAbove) {
        return centerY - artAbove;
    }

    static float artRight(float centerX, float artHalfWidth) {
        return centerX + artHalfWidth + ICON_SHADOW_OFFSET;
    }

    static float artBottom(float centerY, float artBelow) {
        return centerY + artBelow + ICON_SHADOW_OFFSET;
    }

    /** Mutable frame whose records are retained and reused after warm-up. */
    static final class Frame {
        private final ArrayList<RenderedObject> objectPool =
                new ArrayList<RenderedObject>();
        private int objectCount;
        private int candidateCount;
        private int[] objectIndexByCandidate = new int[0];
        private Object guiIdentity;
        private float zoomExp;
        private int guiScale;

        void begin(Object guiIdentity, float zoomExp,
                   int guiScale, int candidateCount) {
            this.guiIdentity = guiIdentity;
            this.zoomExp = zoomExp;
            this.guiScale = Math.max(1, guiScale);
            this.objectCount = 0;
            this.candidateCount = Math.max(0, candidateCount);
            if (this.objectIndexByCandidate.length
                    < this.candidateCount) {
                this.objectIndexByCandidate =
                        new int[this.candidateCount];
            }
            Arrays.fill(this.objectIndexByCandidate, 0,
                    this.candidateCount, -1);
        }

        RenderedObject beginObject(
                String identity, int representativeIndex,
                int representedMemberCount) {
            RenderedObject object;
            if (this.objectCount < this.objectPool.size()) {
                object = this.objectPool.get(this.objectCount);
            } else {
                object = new RenderedObject();
                this.objectPool.add(object);
            }
            object.reset(identity, representativeIndex,
                    representedMemberCount);
            this.objectCount++;
            return object;
        }

        void addMember(
                RenderedObject object,
                int candidateIndex, String markerId,
                float baseCenterX, float baseCenterY,
                float finalCenterX, float finalCenterY,
                float artHalfWidth, float artAbove, float artBelow,
                float visualScale,
                float transitionVisibility, float renderAlpha,
                float fanOffsetX, float fanOffsetY) {
            if (object == null) {
                return;
            }
            Member member = object.addMember(
                    candidateIndex, markerId,
                    baseCenterX, baseCenterY,
                    finalCenterX, finalCenterY,
                    artHalfWidth, artAbove, artBelow, visualScale,
                    transitionVisibility, renderAlpha,
                    fanOffsetX, fanOffsetY);
            if (candidateIndex >= 0
                    && candidateIndex < this.candidateCount) {
                this.objectIndexByCandidate[candidateIndex] =
                        this.objectCount - 1;
            }
            if (candidateIndex == object.representativeIndex) {
                object.representativeMember = member;
            }
        }

        void finishObject(RenderedObject object) {
            if (object != null) {
                object.finish();
            }
        }

        int getObjectCount() {
            return this.objectCount;
        }

        RenderedObject getObject(int index) {
            return index < 0 || index >= this.objectCount
                    ? null : this.objectPool.get(index);
        }

        RenderedObject getObjectForCandidate(int candidateIndex) {
            if (candidateIndex < 0
                    || candidateIndex >= this.candidateCount) {
                return null;
            }
            return getObject(
                    this.objectIndexByCandidate[candidateIndex]);
        }

        Member getMemberForCandidate(int candidateIndex) {
            RenderedObject object =
                    getObjectForCandidate(candidateIndex);
            return object == null
                    ? null : object.getMemberForCandidate(candidateIndex);
        }

        Object getGuiIdentity() {
            return this.guiIdentity;
        }

        float getZoomExp() {
            return this.zoomExp;
        }

        int getGuiScale() {
            return this.guiScale;
        }
    }

    /** One complete visible marker or cluster. */
    static final class RenderedObject {
        private final ArrayList<Member> memberPool =
                new ArrayList<Member>();
        private final Bounds visibleBounds = new Bounds();
        private final Bounds interactionBounds = new Bounds();
        private String identity = "";
        private int representativeIndex = -1;
        private int representedMemberCount;
        private int memberCount;
        private int visibleMemberCount;
        private Member representativeMember;
        private float nameAnchorX;
        private float nameAnchorY;
        private float statusAnchorX;
        private float statusAnchorY;

        private void reset(
                String identity, int representativeIndex,
                int representedMemberCount) {
            this.identity = identity == null ? "" : identity;
            this.representativeIndex = representativeIndex;
            this.representedMemberCount =
                    Math.max(0, representedMemberCount);
            this.memberCount = 0;
            this.visibleMemberCount = 0;
            this.representativeMember = null;
            this.visibleBounds.clear();
            this.interactionBounds.clear();
            this.nameAnchorX = 0.0F;
            this.nameAnchorY = 0.0F;
            this.statusAnchorX = 0.0F;
            this.statusAnchorY = 0.0F;
        }

        private Member addMember(
                int candidateIndex, String markerId,
                float baseCenterX, float baseCenterY,
                float finalCenterX, float finalCenterY,
                float artHalfWidth, float artAbove, float artBelow,
                float visualScale,
                float transitionVisibility, float renderAlpha,
                float fanOffsetX, float fanOffsetY) {
            Member member;
            if (this.memberCount < this.memberPool.size()) {
                member = this.memberPool.get(this.memberCount);
            } else {
                member = new Member();
                this.memberPool.add(member);
            }
            member.reset(candidateIndex, markerId,
                    baseCenterX, baseCenterY,
                    finalCenterX, finalCenterY,
                    artHalfWidth, artAbove, artBelow, visualScale,
                    transitionVisibility, renderAlpha,
                    fanOffsetX, fanOffsetY);
            this.memberCount++;
            if (member.visible) {
                this.visibleMemberCount++;
                // Only sprites still gathered around the leader shape the
                // stack. One that has travelled most of the way to its own
                // position is leaving, and must not drag the stack's target
                // area out after it.
                if (candidateIndex == this.representativeIndex
                        || member.transitionVisibility
                                < FAN_RELEASE_VISIBILITY) {
                    this.visibleBounds.include(member.visibleBounds);
                }
            }
            return member;
        }

        private void finish() {
            if (this.representativeMember != null) {
                this.nameAnchorX =
                        this.representativeMember.centerX;
                this.nameAnchorY =
                        this.representativeMember.centerY;
            }
            if (this.visibleBounds.isValid()) {
                this.interactionBounds.set(this.visibleBounds);
                this.interactionBounds.expand(INTERACTION_PADDING);
                this.statusAnchorX =
                        this.visibleBounds.getCenterX();
                this.statusAnchorY = this.visibleBounds.bottom
                        + GROUP_LABEL_GAP;
            } else if (this.representativeMember != null) {
                this.statusAnchorX =
                        this.representativeMember.centerX;
                this.statusAnchorY =
                        this.representativeMember.centerY;
            }
        }

        String getIdentity() {
            return this.identity;
        }

        int getRepresentativeIndex() {
            return this.representativeIndex;
        }

        int getRepresentedMemberCount() {
            return this.representedMemberCount;
        }

        int getVisibleMemberCount() {
            return this.visibleMemberCount;
        }

        boolean isCluster() {
            return this.representedMemberCount > 1;
        }

        Bounds getVisibleBounds() {
            return this.visibleBounds;
        }

        Bounds getInteractionBounds() {
            return this.interactionBounds;
        }

        Member getRepresentativeMember() {
            return this.representativeMember;
        }

        Member getMemberForCandidate(int candidateIndex) {
            for (int index = 0; index < this.memberCount; index++) {
                Member member = this.memberPool.get(index);
                if (member.candidateIndex == candidateIndex) {
                    return member;
                }
            }
            return null;
        }

        List<Member> getMembers() {
            return this.memberPool.subList(0, this.memberCount);
        }

        /**
         * A stack is one target: the whole rectangle its fan occupies picks
         * it up, including the small gaps between the sprites. Testing each
         * sprite separately let a neighbouring marker win inside those gaps,
         * so the cursor could land "between" a fan and grab something behind
         * it.
         */
        boolean containsInteractionPoint(float x, float y) {
            return this.interactionBounds.contains(x, y);
        }

        double distanceSqToVisibleMemberCenter(float x, float y) {
            double nearest = Double.MAX_VALUE;
            for (int index = 0; index < this.memberCount; index++) {
                Member member = this.memberPool.get(index);
                if (!member.visible) {
                    continue;
                }
                double deltaX = member.centerX - x;
                double deltaY = member.centerY - y;
                nearest = Math.min(nearest,
                        deltaX * deltaX + deltaY * deltaY);
            }
            return nearest;
        }

        float getNameAnchorX() {
            return this.nameAnchorX;
        }

        float getNameAnchorY() {
            return this.nameAnchorY;
        }

        float getStatusAnchorX() {
            return this.statusAnchorX;
        }

        float getStatusAnchorY() {
            return this.statusAnchorY;
        }
    }

    /** Final per-member layout inside a rendered object. */
    static final class Member {
        private final Bounds visibleBounds = new Bounds();
        private int candidateIndex;
        private String markerId = "";
        private float centerX;
        private float centerY;
        private float artHalfWidth;
        private float artAbove;
        private float artBelow;
        private float visualScale;
        private float transitionVisibility;
        private float renderAlpha;
        private float fanOffsetX;
        private float fanOffsetY;
        private float animationOffsetX;
        private float animationOffsetY;
        private boolean visible;

        private void reset(
                int candidateIndex, String markerId,
                float baseCenterX, float baseCenterY,
                float finalCenterX, float finalCenterY,
                float artHalfWidth, float artAbove, float artBelow,
                float visualScale,
                float transitionVisibility, float renderAlpha,
                float fanOffsetX, float fanOffsetY) {
            this.candidateIndex = candidateIndex;
            this.markerId = markerId == null ? "" : markerId;
            this.centerX = finalCenterX;
            this.centerY = finalCenterY;
            this.artHalfWidth = Math.max(0.0F, artHalfWidth);
            this.artAbove = Math.max(0.0F, artAbove);
            this.artBelow = Math.max(0.0F, artBelow);
            this.visualScale = Math.max(0.0F, visualScale);
            this.transitionVisibility = clamp01(
                    transitionVisibility);
            this.renderAlpha = clamp01(renderAlpha);
            this.fanOffsetX = fanOffsetX;
            this.fanOffsetY = fanOffsetY;
            this.animationOffsetX = finalCenterX - baseCenterX;
            this.animationOffsetY = finalCenterY - baseCenterY;
            this.visible = this.artHalfWidth > 0.0F
                    && this.renderAlpha > MIN_VISIBLE_ALPHA;
            if (this.visible) {
                this.visibleBounds.set(
                        artLeft(finalCenterX, this.artHalfWidth),
                        artTop(finalCenterY, this.artAbove),
                        artRight(finalCenterX, this.artHalfWidth),
                        artBottom(finalCenterY, this.artBelow));
            } else {
                this.visibleBounds.clear();
            }
        }

        int getCandidateIndex() {
            return this.candidateIndex;
        }

        String getMarkerId() {
            return this.markerId;
        }

        float getCenterX() {
            return this.centerX;
        }

        float getCenterY() {
            return this.centerY;
        }

        float getArtHalfWidth() {
            return this.artHalfWidth;
        }

        float getArtAbove() {
            return this.artAbove;
        }

        float getArtBelow() {
            return this.artBelow;
        }

        float getVisualScale() {
            return this.visualScale;
        }

        float getTransitionVisibility() {
            return this.transitionVisibility;
        }

        float getRenderAlpha() {
            return this.renderAlpha;
        }

        float getFanOffsetX() {
            return this.fanOffsetX;
        }

        float getFanOffsetY() {
            return this.fanOffsetY;
        }

        float getAnimationOffsetX() {
            return this.animationOffsetX;
        }

        float getAnimationOffsetY() {
            return this.animationOffsetY;
        }

        boolean isVisible() {
            return this.visible;
        }

        Bounds getVisibleBounds() {
            return this.visibleBounds;
        }
    }

    /** Axis-aligned bounds; point containment includes the visible edge. */
    static final class Bounds {
        private float left;
        private float top;
        private float right;
        private float bottom;
        private boolean valid;

        void clear() {
            this.left = 0.0F;
            this.top = 0.0F;
            this.right = 0.0F;
            this.bottom = 0.0F;
            this.valid = false;
        }

        void set(float left, float top, float right, float bottom) {
            this.left = Math.min(left, right);
            this.top = Math.min(top, bottom);
            this.right = Math.max(left, right);
            this.bottom = Math.max(top, bottom);
            this.valid = true;
        }

        void set(Bounds other) {
            if (other == null || !other.valid) {
                clear();
                return;
            }
            set(other.left, other.top, other.right, other.bottom);
        }

        void include(Bounds other) {
            if (other == null || !other.valid) {
                return;
            }
            if (!this.valid) {
                set(other);
                return;
            }
            this.left = Math.min(this.left, other.left);
            this.top = Math.min(this.top, other.top);
            this.right = Math.max(this.right, other.right);
            this.bottom = Math.max(this.bottom, other.bottom);
        }

        void expand(float padding) {
            if (!this.valid) {
                return;
            }
            float amount = Math.max(0.0F, padding);
            this.left -= amount;
            this.top -= amount;
            this.right += amount;
            this.bottom += amount;
        }

        boolean contains(float x, float y) {
            return this.valid && x >= this.left && x <= this.right
                    && y >= this.top && y <= this.bottom;
        }

        boolean contains(float x, float y, float padding) {
            if (!this.valid) {
                return false;
            }
            float amount = Math.max(0.0F, padding);
            return x >= this.left - amount
                    && x <= this.right + amount
                    && y >= this.top - amount
                    && y <= this.bottom + amount;
        }

        boolean isValid() {
            return this.valid;
        }

        float getLeft() {
            return this.left;
        }

        float getTop() {
            return this.top;
        }

        float getRight() {
            return this.right;
        }

        float getBottom() {
            return this.bottom;
        }

        float getWidth() {
            return this.valid ? this.right - this.left : 0.0F;
        }

        float getHeight() {
            return this.valid ? this.bottom - this.top : 0.0F;
        }

        float getCenterX() {
            return this.valid
                    ? (this.left + this.right) * 0.5F : 0.0F;
        }

        float getCenterY() {
            return this.valid
                    ? (this.top + this.bottom) * 0.5F : 0.0F;
        }
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
