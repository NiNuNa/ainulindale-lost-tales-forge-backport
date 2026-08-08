package com.ninuna.losttales.client.mapmarker;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.LOTRConfig;
import lotr.common.world.genlayer.LOTRGenLayerWorld;
import lotr.common.world.map.LOTRRoads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

/**
 * Keeps road-name anchors fixed in world space while the map zooms.
 *
 * <p>The native renderer recalculates its label point interval for every
 * fractional zoom frame. That makes a road name jump between unrelated road
 * points. Fixed anchors plus collision-aware fades make labels travel with
 * the road and only change density through a cross-fade.</p>
 */
final class LostTalesLotrRoadLabelRenderer {
    private static final float LABEL_FADE_PER_SECOND = 4.0F;
    private static final int WORLD_POINTS_PER_MAP_PIXEL = 200;
    private static final int LABEL_PADDING = 8;

    private static Field posXField;
    private static Field posYField;
    private static Field zoomScaleField;
    private static Field zoomExpField;
    private static Field mapWidthField;
    private static Field mapHeightField;
    private static Field mapXMinField;
    private static Field mapXMaxField;
    private static Field mapYMinField;
    private static Field mapYMaxField;
    private static boolean reflectionReady;
    private static boolean reflectionFailed;

    private static final Map<String, Float> labelFade =
            new HashMap<String, Float>();
    private static LOTRGuiMap fadeGui;
    private static long lastFadeNanos;

    private LostTalesLotrRoadLabelRenderer() {}

    static Prepared prepare(LOTRGuiMap gui) {
        if (gui == null || LOTRConfig.osrsMap || !ensureReflection()) {
            return null;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.fontRenderer == null) {
            return null;
        }
        try {
            return new Prepared(
                    gui, minecraft, minecraft.fontRenderer,
                    posXField.getFloat(gui), posYField.getFloat(gui),
                    zoomScaleField.getFloat(gui),
                    zoomExpField.getFloat(gui),
                    mapWidthField.getInt(null), mapHeightField.getInt(null),
                    mapXMinField.getInt(null), mapXMaxField.getInt(null),
                    mapYMinField.getInt(null), mapYMaxField.getInt(null));
        } catch (Throwable ignored) {
            reflectionFailed = true;
            reflectionReady = false;
            return null;
        }
    }

    static void clear(LOTRGuiMap gui) {
        if (gui == null || fadeGui == gui) {
            labelFade.clear();
            fadeGui = null;
            lastFadeNanos = 0L;
        }
    }

    static int calculateAnchorStep(int textWidth, float zoomScale) {
        int paddedWidth = Math.max(1, textWidth * 2 + 100);
        if (!(zoomScale > 0.0F)) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, (int)(paddedWidth
                * (float)WORLD_POINTS_PER_MAP_PIXEL / zoomScale));
    }

    static int calculateLabelCount(
            int pointCount, int textWidth, float zoomScale) {
        if (pointCount <= 1) {
            return 0;
        }
        int step = calculateAnchorStep(textWidth, zoomScale);
        return step == Integer.MAX_VALUE
                ? 0 : Math.max(0, (pointCount - 1) / step);
    }

    /** Base-two low-discrepancy anchors stay fixed as density changes. */
    static double anchorFraction(int ordinal) {
        int value = Math.max(0, ordinal) + 1;
        double fraction = 0.0D;
        double place = 0.5D;
        while (value > 0) {
            fraction += (value & 1) * place;
            value >>= 1;
            place *= 0.5D;
        }
        return fraction;
    }

    private static synchronized boolean ensureReflection() {
        if (reflectionReady) {
            return true;
        }
        if (reflectionFailed) {
            return false;
        }
        try {
            posXField = field("posX");
            posYField = field("posY");
            zoomScaleField = field("zoomScale");
            zoomExpField = field("zoomExp");
            mapWidthField = field("mapWidth");
            mapHeightField = field("mapHeight");
            mapXMinField = field("mapXMin");
            mapXMaxField = field("mapXMax");
            mapYMinField = field("mapYMin");
            mapYMaxField = field("mapYMax");
            reflectionReady = true;
            return true;
        } catch (Throwable ignored) {
            reflectionFailed = true;
            return false;
        }
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = LOTRGuiMap.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    static final class Prepared {
        private final LOTRGuiMap gui;
        private final Minecraft minecraft;
        private final FontRenderer font;
        private final float posX;
        private final float posY;
        private final float zoomScale;
        private final float zoomExp;
        private final int mapWidth;
        private final int mapHeight;
        private final int mapXMin;
        private final int mapXMax;
        private final int mapYMin;
        private final int mapYMax;

        private Prepared(
                LOTRGuiMap gui, Minecraft minecraft, FontRenderer font,
                float posX, float posY, float zoomScale, float zoomExp,
                int mapWidth, int mapHeight,
                int mapXMin, int mapXMax, int mapYMin, int mapYMax) {
            this.gui = gui;
            this.minecraft = minecraft;
            this.font = font;
            this.posX = posX;
            this.posY = posY;
            this.zoomScale = zoomScale;
            this.zoomExp = zoomExp;
            this.mapWidth = mapWidth;
            this.mapHeight = mapHeight;
            this.mapXMin = mapXMin;
            this.mapXMax = mapXMax;
            this.mapYMin = mapYMin;
            this.mapYMax = mapYMax;
        }

        void render() {
            float zoomVisibility = clamp((this.zoomExp + 1.0F) / 4.0F);
            if (zoomVisibility <= 0.0F || !(this.zoomScale > 0.0F)) {
                updateFades(this.gui, new ArrayList<LabelCandidate>(),
                        new HashSet<String>());
                return;
            }
            try {
                List<LabelCandidate> candidates = collectCandidates(
                        zoomVisibility);
                Set<String> selected = selectNonOverlapping(candidates);
                updateFades(this.gui, candidates, selected);
                beginClip();
                try {
                    for (LabelCandidate candidate : candidates) {
                        float fade = easedFade(candidate.key);
                        if (fade > 0.001F) {
                            draw(candidate, zoomVisibility, fade);
                        }
                    }
                } finally {
                    endClip();
                }
            } catch (Throwable ignored) {
                // Road labels are decorative; the already-rendered roads and
                // every other native map feature remain available.
            }
        }

        private List<LabelCandidate> collectCandidates(
                float zoomVisibility) {
            ArrayList<LabelCandidate> candidates =
                    new ArrayList<LabelCandidate>();
            Iterator roads = LOTRRoads.getAllRoadsForDisplay();
            int roadIndex = 0;
            while (roads.hasNext()) {
                LOTRRoads road = (LOTRRoads)roads.next();
                LOTRRoads.RoadPoint[] points = road.roadPoints;
                String name = road.getDisplayName();
                if (points == null || points.length == 0
                        || name == null || name.length() == 0) {
                    roadIndex++;
                    continue;
                }
                int textWidth = this.font.getStringWidth(name);
                int desiredCount = calculateLabelCount(
                        points.length, textWidth, this.zoomScale);
                // Keep the next two stable anchors available so labels which
                // leave the native-equivalent density can fade out cleanly.
                int candidateCount = Math.min(points.length,
                        desiredCount + 2);
                HashSet<Integer> usedPoints = new HashSet<Integer>();
                for (int ordinal = 0;
                     ordinal < candidateCount; ordinal++) {
                    int pointIndex = Math.max(0, Math.min(
                            points.length - 1,
                            (int)Math.round(anchorFraction(ordinal)
                                    * (points.length - 1))));
                    if (!usedPoints.add(Integer.valueOf(pointIndex))) {
                        continue;
                    }
                    LOTRRoads.RoadPoint point = points[pointIndex];
                    float[] position = transform(point.x, point.z);
                    float radius = ((float)textWidth + LABEL_PADDING)
                            * zoomVisibility * 0.55F;
                    if (position[0] < this.mapXMin - radius
                            || position[0] > this.mapXMax + radius
                            || position[1] < this.mapYMin - radius
                            || position[1] > this.mapYMax + radius) {
                        continue;
                    }
                    int tangent = Math.max(1, Math.min(
                            200, points.length / 1000));
                    LOTRRoads.RoadPoint before = points[Math.max(
                            0, pointIndex - tangent)];
                    LOTRRoads.RoadPoint after = points[Math.min(
                            points.length - 1, pointIndex + tangent)];
                    float angle = uprightAngle(
                            after.x - before.x,
                            after.z - before.z,
                            LostTalesLotrMapRotation.degreesOf(this.gui));
                    String key = road.roadName + ':' + roadIndex
                            + ':' + pointIndex;
                    candidates.add(new LabelCandidate(
                            key, name, textWidth,
                            position[0], position[1], angle,
                            Math.max(radius,
                                    this.font.FONT_HEIGHT
                                            * zoomVisibility),
                            ordinal < desiredCount));
                }
                roadIndex++;
            }
            return candidates;
        }

        private Set<String> selectNonOverlapping(
                List<LabelCandidate> candidates) {
            HashSet<String> selected = new HashSet<String>();
            ArrayList<LabelCandidate> accepted =
                    new ArrayList<LabelCandidate>();
            for (LabelCandidate candidate : candidates) {
                if (!candidate.eligible) {
                    continue;
                }
                boolean overlaps = false;
                for (LabelCandidate other : accepted) {
                    float dx = candidate.x - other.x;
                    float dy = candidate.y - other.y;
                    float distance = candidate.collisionRadius
                            + other.collisionRadius + 8.0F;
                    if (dx * dx + dy * dy < distance * distance) {
                        overlaps = true;
                        break;
                    }
                }
                if (!overlaps) {
                    accepted.add(candidate);
                    selected.add(candidate.key);
                }
            }
            return selected;
        }

        /**
         * A road point in screen space, on the sheet as it is actually drawn.
         *
         * <p>This repeats LOTR's own map-space conversion because the anchors
         * are chosen from world coordinates rather than from the points LOTR
         * happens to be drawing, but the turn and the lean are not repeated
         * here: the result goes through the same one place that moves every
         * other position on the map, so a road name cannot come loose from
         * the road under it.</p>
         */
        private float[] transform(double worldX, double worldZ) {
            float mapX = (float)(worldX / LOTRGenLayerWorld.scale)
                    + 810.0F;
            float mapY = (float)(worldZ / LOTRGenLayerWorld.scale)
                    + 730.0F;
            float screenX = (mapX - this.posX) * this.zoomScale
                    + this.mapXMin + this.mapWidth / 2.0F;
            float screenY = (mapY - this.posY) * this.zoomScale
                    + this.mapYMin + this.mapHeight / 2.0F;
            return LostTalesLotrMapRotation.rotate(
                    new float[] {screenX, screenY}, this.gui);
        }

        /**
         * Clips the labels to the map viewport without deciding for the rest
         * of the frame whether clipping is on. LOTR turns the scissor on and
         * off around passes of its own, and this pass runs inside one of
         * them, so what was there is saved and given straight back.
         */
        private void beginClip() {
            ScaledResolution resolution = new ScaledResolution(
                    this.minecraft, this.minecraft.displayWidth,
                    this.minecraft.displayHeight);
            int scale = resolution.getScaleFactor();
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_SCISSOR_BIT
                    | GL11.GL_DEPTH_BUFFER_BIT);
            // The last map pass that was still being depth-tested against
            // whatever the layers before it left in the buffer.
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(
                    this.mapXMin * scale,
                    (this.gui.height - this.mapYMax) * scale,
                    this.mapWidth * scale,
                    this.mapHeight * scale);
        }

        private void endClip() {
            GL11.glPopAttrib();
        }

        private void draw(LabelCandidate candidate,
                          float zoomVisibility, float fade) {
            float alpha = zoomVisibility * 0.8F * fade;
            int alphaInt = Math.max(4,
                    Math.min(255, (int)(alpha * 255.0F)));
            int shadow = alphaInt << 24;
            int white = shadow | LostTalesLotrMapLabelStyle.LABEL_RGB;
            GL11.glPushMatrix();
            GL11.glTranslatef(candidate.x, candidate.y, 0.0F);
            GL11.glScalef(zoomVisibility, zoomVisibility, zoomVisibility);
            GL11.glRotatef(candidate.angle, 0.0F, 0.0F, 1.0F);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            float oldAlpha = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.01F);
            int x = -candidate.textWidth / 2;
            int y = -15;
            this.font.drawString(candidate.text, x + 1, y + 1, shadow);
            this.font.drawString(candidate.text, x, y, white);
            GL11.glAlphaFunc(GL11.GL_GREATER, oldAlpha);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glPopMatrix();
        }
    }

    private static void updateFades(
            LOTRGuiMap gui, List<LabelCandidate> candidates,
            Set<String> selected) {
        long now = System.nanoTime();
        boolean reset = fadeGui != gui;
        float step = reset || lastFadeNanos == 0L
                ? 1.0F
                : Math.min(1.0F,
                        (now - lastFadeNanos) / 1000000000.0F
                                * LABEL_FADE_PER_SECOND);
        if (reset) {
            labelFade.clear();
            fadeGui = gui;
        }
        HashSet<String> visible = new HashSet<String>();
        for (LabelCandidate candidate : candidates) {
            visible.add(candidate.key);
            float target = selected.contains(candidate.key) ? 1.0F : 0.0F;
            Float stored = labelFade.get(candidate.key);
            float current = stored == null
                    ? (reset ? target : 0.0F) : stored.floatValue();
            current += Math.max(-step, Math.min(step, target - current));
            labelFade.put(candidate.key, Float.valueOf(current));
        }
        labelFade.keySet().retainAll(visible);
        lastFadeNanos = now;
    }

    private static float easedFade(String key) {
        Float stored = labelFade.get(key);
        float value = stored == null ? 0.0F : stored.floatValue();
        return value * value * (3.0F - 2.0F * value);
    }

    /**
     * The tangent angle of a road on a map that has been turned.
     *
     * <p>The turn is added before the name is made readable rather than
     * after, so a road that a small turn pushes past vertical flips once and
     * stays the right way up instead of standing on its head.</p>
     */
    static float uprightAngle(double dx, double dz, float mapDegrees) {
        return uprightAngle(
                dx * Math.cos(Math.toRadians(mapDegrees))
                        - dz * Math.sin(Math.toRadians(mapDegrees)),
                dx * Math.sin(Math.toRadians(mapDegrees))
                        + dz * Math.cos(Math.toRadians(mapDegrees)));
    }

    static float uprightAngle(double dx, double dz) {
        float angle = (float)Math.toDegrees(Math.atan2(dz, dx));
        while (angle > 90.0F) {
            angle -= 180.0F;
        }
        while (angle < -90.0F) {
            angle += 180.0F;
        }
        return angle;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static final class LabelCandidate {
        private final String key;
        private final String text;
        private final int textWidth;
        private final float x;
        private final float y;
        private final float angle;
        private final float collisionRadius;
        private final boolean eligible;

        private LabelCandidate(
                String key, String text, int textWidth,
                float x, float y, float angle, float collisionRadius,
                boolean eligible) {
            this.key = key;
            this.text = text;
            this.textWidth = textWidth;
            this.x = x;
            this.y = y;
            this.angle = angle;
            this.collisionRadius = collisionRadius;
            this.eligible = eligible;
        }
    }
}
