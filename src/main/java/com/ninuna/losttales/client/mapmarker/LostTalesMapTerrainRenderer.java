package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.world.map.waypoint.LostTalesMapCoordinateHelper;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.world.genlayer.LOTRGenLayerWorld;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * Reusable close-map geometry built from client-known terrain snapshots.
 *
 * <p>Each visible cached chunk gets at most one display list. At most two
 * bounded LOD lists are compiled in a frame, block artwork comes from
 * Minecraft's existing atlas,
 * and missing or not-yet-compiled tiles leave the normal LOTR map visible.
 * No world or chunk access occurs here.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesMapTerrainRenderer {
    static final int MAX_MESHES = 1024;
    static final int MAX_MESH_COMPILES_PER_FRAME = 2;
    static final int RELIEF_BASE_Y = 64;
    static final int MIN_RELIEF = -32;
    static final int MAX_RELIEF = 128;
    /** Keeps GUI-space elevation meaningful without exploding at close zoom. */
    static final float TERRAIN_HEIGHT_SCALE = 0.12F;
    static final float FULL_DETAIL_BLOCK_PIXELS = 10.0F;
    static final float MEDIUM_DETAIL_BLOCK_PIXELS = 5.5F;
    static final float FINE_DETAIL_BLOCK_PIXELS = 0.75F;
    static final long MESH_FADE_NANOS = 250000000L;
    private static final float LOG_TWO = (float)Math.log(2.0D);
    private static final int FALLBACK_SURFACE_COLOR = 0x777777;
    private static final float EAST_FACE_SHADE = 0.58F;
    private static final float SOUTH_FACE_SHADE = 0.72F;
    private static final float MIN_VISIBLE_ALPHA = 0.001F;

    private static final LostTalesMapTerrainCache.TerrainTile[] TILE_BUFFER =
            new LostTalesMapTerrainCache.TerrainTile[
                    LostTalesMapTerrainCache.MAX_CACHED_TILES];
    private static final float[] VISIBLE_COVERAGE = new float[2];
    private static final Mesh[] MESHES = new Mesh[MAX_MESHES];
    private static WorldClient activeWorld;
    private static int activeDimension = Integer.MIN_VALUE;
    private static boolean activeSepia;
    private static int meshCount;
    private static long frameSequence;
    private static boolean renderingDisabled;
    private static boolean failureLogged;

    private LostTalesMapTerrainRenderer() {}

    static void render(
            LOTRGuiMap gui, boolean sepia,
            float mapScale, float posX, float posY,
            float mapXMin, float mapXMax, float mapYMin, float mapYMax,
            int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax) {
        if (renderingDisabled || !(gui instanceof LostTalesLotrMapGui)
                || !LostTalesLotrMapLayout.isFullscreenLayoutActive(gui)) {
            return;
        }
        float terrainAlpha = LostTalesMapTerrainTransition.terrainAlpha(
                zoomExponent(mapScale));
        if (!(terrainAlpha > MIN_VISIBLE_ALPHA)) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.theWorld == null) {
            return;
        }
        try {
            renderKnownTiles(gui, minecraft.theWorld, sepia, terrainAlpha,
                    mapScale, posX, posY,
                    mapXMin, mapXMax, mapYMin, mapYMax,
                    viewportXMin, viewportXMax,
                    viewportYMin, viewportYMax);
        } catch (Throwable failure) {
            disableAfterFailure(failure);
        }
    }

    private static void renderKnownTiles(
            LOTRGuiMap gui, WorldClient world,
            boolean sepia, float terrainAlpha,
            float mapScale, float posX, float posY,
            float mapXMin, float mapXMax, float mapYMin, float mapYMax,
            int viewportXMin, int viewportXMax,
            int viewportYMin, int viewportYMax) {
        int dimension = world.provider.dimensionId;
        ensureContext(world, dimension, sepia);
        int tileCount = LostTalesMapTerrainCache.copyTiles(
                dimension, TILE_BUFFER);
        if (tileCount <= 0) {
            return;
        }

        frameSequence++;
        long nowNanos = System.nanoTime();
        int compileBudget = MAX_MESH_COMPILES_PER_FRAME;
        float centerX = viewportXMin
                + (viewportXMax - viewportXMin) / 2;
        float centerY = viewportYMin
                + (viewportYMax - viewportYMin) / 2;
        LostTalesLotrMapRotation.visibleCoverage(
                viewportXMax - viewportXMin,
                viewportYMax - viewportYMin,
                LostTalesLotrMapRotation.degreesOf(gui),
                LostTalesLotrMapRotation.leanOf(gui),
                VISIBLE_COVERAGE);
        float visibleMinX = Math.max(mapXMin,
                centerX - VISIBLE_COVERAGE[0] * 0.5F);
        float visibleMaxX = Math.min(mapXMax,
                centerX + VISIBLE_COVERAGE[0] * 0.5F);
        float visibleMinY = Math.max(mapYMin,
                centerY - VISIBLE_COVERAGE[1] * 0.5F);
        float visibleMaxY = Math.min(mapYMax,
                centerY + VISIBLE_COVERAGE[1] * 0.5F);
        float blockScale = mapScale / LOTRGenLayerWorld.scale;
        int sampleStep = sampleStep(stableLodBlockScale(mapScale));
        float tileSize = blockScale * LostTalesMapTerrainCache.TILE_WIDTH;
        float reliefMargin = projectedReliefMargin(
                blockScale, LostTalesLotrMapRotation.leanSine(gui));

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LIGHTING_BIT | GL11.GL_POLYGON_BIT
                | GL11.GL_TEXTURE_BIT);
        try {
            prepareRenderState(terrainAlpha, !sepia);
            if (!sepia) {
                Minecraft.getMinecraft().getTextureManager().bindTexture(
                        TextureMap.locationBlocksTexture);
            }
            float appliedAlpha = terrainAlpha;
            for (int index = 0; index < tileCount; index++) {
                LostTalesMapTerrainCache.TerrainTile tile = TILE_BUFFER[index];
                float originX = centerX + ((float)
                        LostTalesMapCoordinateHelper
                                .worldToRenderedMapImageX(
                                        tile.chunkX * 16.0D) - posX)
                        * mapScale;
                float originY = centerY + ((float)
                        LostTalesMapCoordinateHelper
                                .worldToRenderedMapImageZ(
                                        tile.chunkZ * 16.0D) - posY)
                        * mapScale;
                if (!isTileVisible(originX, originY, tileSize,
                        reliefMargin,
                        visibleMinX, visibleMaxX,
                        visibleMinY, visibleMaxY)) {
                    continue;
                }

                LostTalesMapTerrainCache.TerrainTile east =
                        LostTalesMapTerrainCache.get(
                                dimension, tile.chunkX + 1, tile.chunkZ);
                LostTalesMapTerrainCache.TerrainTile south =
                        LostTalesMapTerrainCache.get(
                                dimension, tile.chunkX, tile.chunkZ + 1);
                Mesh mesh = findMesh(tile.chunkX, tile.chunkZ);
                if (mesh == null || !mesh.matches(
                        tile, east, south, sampleStep, sepia)) {
                    if (compileBudget > 0) {
                        mesh = compileMesh(
                                mesh, tile, east, south,
                                sampleStep, sepia);
                        compileBudget--;
                    }
                }
                if (mesh == null || mesh.listId <= 0) {
                    continue;
                }
                float meshAlpha = terrainAlpha
                        * meshAvailabilityAlpha(
                                mesh.firstReadyNanos, nowNanos);
                if (!(meshAlpha > MIN_VISIBLE_ALPHA)) {
                    continue;
                }
                if (meshAlpha != appliedAlpha) {
                    applyBlendAlpha(meshAlpha);
                    appliedAlpha = meshAlpha;
                }
                mesh.lastUsedFrame = frameSequence;
                drawMesh(mesh, originX, originY, blockScale);
            }
        } finally {
            // Terrain uses depth internally, but every later map pass is UI
            // ordered. Leaving terrain depth behind lets it reject marker,
            // tooltip, compass, and control-strip pixels drawn afterwards.
            GL11.glDepthMask(true);
            GL11.glClearDepth(1.0D);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glPopAttrib();
        }
    }

    private static void prepareRenderState(float alpha, boolean textured) {
        if (textured) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        } else {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
        }
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glClearDepth(1.0D);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        applyBlendAlpha(alpha);
    }

    private static void applyBlendAlpha(float alpha) {
        if (alpha < 0.999F) {
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendColor(1.0F, 1.0F, 1.0F, alpha);
            GL11.glBlendFunc(GL11.GL_CONSTANT_ALPHA,
                    GL11.GL_ONE_MINUS_CONSTANT_ALPHA);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
    }

    private static void drawMesh(
            Mesh mesh, float originX, float originY,
            float blockScale) {
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(originX, originY, 0.0F);
            GL11.glScalef(blockScale, blockScale,
                    blockScale * TERRAIN_HEIGHT_SCALE);
            GL11.glCallList(mesh.listId);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static Mesh compileMesh(
            Mesh existing, LostTalesMapTerrainCache.TerrainTile tile,
            LostTalesMapTerrainCache.TerrainTile east,
            LostTalesMapTerrainCache.TerrainTile south,
            int sampleStep, boolean sepia) {
        Mesh mesh = existing;
        if (mesh == null) {
            mesh = obtainMeshSlot();
            if (mesh == null) {
                return null;
            }
        }
        int listId = GLAllocation.generateDisplayLists(1);
        if (listId <= 0) {
            throw new IllegalStateException(
                    "OpenGL did not allocate a terrain display list");
        }
        boolean listOpen = false;
        try {
            GL11.glNewList(listId, GL11.GL_COMPILE);
            listOpen = true;
            buildGeometry(tile, east, south, sampleStep, sepia);
            GL11.glEndList();
            listOpen = false;
        } catch (Throwable failure) {
            if (listOpen) {
                try {
                    GL11.glEndList();
                } catch (Throwable ignored) {
                    // Preserve the original compilation failure.
                }
            }
            safeDeleteList(listId);
            throw failure;
        }

        if (existing != null) {
            safeDeleteList(mesh.listId);
        }
        mesh.set(tile, east, south,
                sampleStep, sepia, listId, frameSequence);
        return mesh;
    }

    private static void buildGeometry(
            LostTalesMapTerrainCache.TerrainTile tile,
            LostTalesMapTerrainCache.TerrainTile east,
            LostTalesMapTerrainCache.TerrainTile south,
            int sampleStep, boolean sepia) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        for (int localZ = 0;
             localZ < LostTalesMapTerrainCache.TILE_WIDTH;
             localZ += sampleStep) {
            for (int localX = 0;
                 localX < LostTalesMapTerrainCache.TILE_WIDTH;
                 localX += sampleStep) {
                int sample = sampleIndex(
                        tile, localX, localZ, sampleStep);
                int height = sampleHeight(tile, sample);
                int relief = reliefHeight(height);
                int color = sampleSurfaceColor(tile, sample, sepia);
                Block block = sampleBlock(tile, sample);
                int metadata = sampleMetadata(tile, sample);
                int cellWidth = cellExtent(localX, sampleStep);
                int cellDepth = cellExtent(localZ, sampleStep);
                int westHeight = localX == 0
                        ? height : sampleHeight(tile,
                                localX - sampleStep, localZ, sampleStep);
                int northHeight = localZ == 0
                        ? height : sampleHeight(tile,
                                localX, localZ - sampleStep, sampleStep);
                float topShade = topShade(
                        height, westHeight, northHeight);
                tessellator.setColorOpaque_I(
                        shadeColor(color, topShade));
                addTop(tessellator, localX, localZ,
                        cellWidth, cellDepth, relief,
                        iconFor(block, 1, metadata));

                int eastHeight;
                int eastColor;
                Block eastBlock;
                int eastMetadata;
                if (localX + sampleStep
                        < LostTalesMapTerrainCache.TILE_WIDTH) {
                    int eastSample = sampleIndex(tile,
                            localX + sampleStep, localZ, sampleStep);
                    eastHeight = sampleHeight(tile, eastSample);
                    eastColor = sampleSurfaceColor(
                            tile, eastSample, sepia);
                    eastBlock = sampleBlock(tile, eastSample);
                    eastMetadata = sampleMetadata(tile, eastSample);
                } else if (east != null) {
                    int eastSample = sampleIndex(
                            east, 0, localZ, sampleStep);
                    eastHeight = sampleHeight(east, eastSample);
                    eastColor = sampleSurfaceColor(
                            east, eastSample, sepia);
                    eastBlock = sampleBlock(east, eastSample);
                    eastMetadata = sampleMetadata(east, eastSample);
                } else {
                    eastHeight = height;
                    eastColor = color;
                    eastBlock = block;
                    eastMetadata = metadata;
                }
                boolean currentFacesEast = height >= eastHeight;
                addEastFace(tessellator, localX, localZ,
                        cellWidth, cellDepth,
                        relief, reliefHeight(eastHeight),
                        shadeColor(currentFacesEast
                                ? color : eastColor, EAST_FACE_SHADE),
                        iconFor(currentFacesEast ? block : eastBlock,
                                5, currentFacesEast
                                        ? metadata : eastMetadata));

                int southHeight;
                int southColor;
                Block southBlock;
                int southMetadata;
                if (localZ + sampleStep
                        < LostTalesMapTerrainCache.TILE_WIDTH) {
                    int southSample = sampleIndex(tile,
                            localX, localZ + sampleStep, sampleStep);
                    southHeight = sampleHeight(tile, southSample);
                    southColor = sampleSurfaceColor(
                            tile, southSample, sepia);
                    southBlock = sampleBlock(tile, southSample);
                    southMetadata = sampleMetadata(tile, southSample);
                } else if (south != null) {
                    int southSample = sampleIndex(
                            south, localX, 0, sampleStep);
                    southHeight = sampleHeight(south, southSample);
                    southColor = sampleSurfaceColor(
                            south, southSample, sepia);
                    southBlock = sampleBlock(south, southSample);
                    southMetadata = sampleMetadata(south, southSample);
                } else {
                    southHeight = height;
                    southColor = color;
                    southBlock = block;
                    southMetadata = metadata;
                }
                boolean currentFacesSouth = height >= southHeight;
                addSouthFace(tessellator, localX, localZ,
                        cellWidth, cellDepth,
                        relief, reliefHeight(southHeight),
                        shadeColor(currentFacesSouth
                                ? color : southColor, SOUTH_FACE_SHADE),
                        iconFor(currentFacesSouth ? block : southBlock,
                                3, currentFacesSouth
                                        ? metadata : southMetadata));
            }
        }
        tessellator.draw();
    }

    private static void addTop(
            Tessellator tessellator, int x, int y,
            int cellWidth, int cellDepth,
            int relief, IIcon icon) {
        double uMin = icon.getMinU();
        double uMax = icon.getMaxU();
        double vMin = icon.getMinV();
        double vMax = icon.getMaxV();
        tessellator.addVertexWithUV(x, y, relief, uMin, vMin);
        tessellator.addVertexWithUV(
                x, y + cellDepth, relief, uMin, vMax);
        tessellator.addVertexWithUV(
                x + cellWidth, y + cellDepth, relief, uMax, vMax);
        tessellator.addVertexWithUV(
                x + cellWidth, y, relief, uMax, vMin);
    }

    private static void addEastFace(
            Tessellator tessellator, int x, int y,
            int cellWidth, int cellDepth, int relief,
            int neighborRelief, int color, IIcon icon) {
        if (relief == neighborRelief) {
            return;
        }
        int low = Math.min(relief, neighborRelief);
        int high = Math.max(relief, neighborRelief);
        tessellator.setColorOpaque_I(color);
        double uMin = icon.getMinU();
        double uMax = icon.getMaxU();
        double vMin = icon.getMinV();
        double vMax = icon.getMaxV();
        tessellator.addVertexWithUV(
                x + cellWidth, y, low, uMin, vMax);
        tessellator.addVertexWithUV(
                x + cellWidth, y + cellDepth, low, uMax, vMax);
        tessellator.addVertexWithUV(
                x + cellWidth, y + cellDepth, high, uMax, vMin);
        tessellator.addVertexWithUV(
                x + cellWidth, y, high, uMin, vMin);
    }

    private static void addSouthFace(
            Tessellator tessellator, int x, int y,
            int cellWidth, int cellDepth, int relief,
            int neighborRelief, int color, IIcon icon) {
        if (relief == neighborRelief) {
            return;
        }
        int low = Math.min(relief, neighborRelief);
        int high = Math.max(relief, neighborRelief);
        tessellator.setColorOpaque_I(color);
        double uMin = icon.getMinU();
        double uMax = icon.getMaxU();
        double vMin = icon.getMinV();
        double vMax = icon.getMaxV();
        tessellator.addVertexWithUV(
                x, y + cellDepth, low, uMin, vMax);
        tessellator.addVertexWithUV(
                x + cellWidth, y + cellDepth, low, uMax, vMax);
        tessellator.addVertexWithUV(
                x + cellWidth, y + cellDepth, high, uMax, vMin);
        tessellator.addVertexWithUV(
                x, y + cellDepth, high, uMin, vMin);
    }

    private static int sampleHeight(
            LostTalesMapTerrainCache.TerrainTile tile,
            int startX, int startZ, int sampleStep) {
        return sampleHeight(tile,
                sampleIndex(tile, startX, startZ, sampleStep));
    }

    private static int sampleIndex(
            LostTalesMapTerrainCache.TerrainTile tile,
            int startX, int startZ, int sampleStep) {
        int highest = Integer.MIN_VALUE;
        int selected = startZ * LostTalesMapTerrainCache.TILE_WIDTH
                + startX;
        int endX = Math.min(LostTalesMapTerrainCache.TILE_WIDTH,
                startX + sampleStep);
        int endZ = Math.min(LostTalesMapTerrainCache.TILE_WIDTH,
                startZ + sampleStep);
        for (int z = startZ; z < endZ; z++) {
            for (int x = startX; x < endX; x++) {
                int height = tile.heightAt(x, z);
                if (height > highest) {
                    highest = height;
                    selected = z * LostTalesMapTerrainCache.TILE_WIDTH + x;
                }
            }
        }
        return selected;
    }

    private static int sampleHeight(
            LostTalesMapTerrainCache.TerrainTile tile, int sample) {
        return tile.heightAt(
                sample % LostTalesMapTerrainCache.TILE_WIDTH,
                sample / LostTalesMapTerrainCache.TILE_WIDTH);
    }

    private static int sampleSurfaceColor(
            LostTalesMapTerrainCache.TerrainTile tile,
            int sample, boolean sepia) {
        int x = sample % LostTalesMapTerrainCache.TILE_WIDTH;
        int z = sample / LostTalesMapTerrainCache.TILE_WIDTH;
        if (sepia) {
            return surfaceColor(tile.colorAt(x, z), true);
        }
        int color = tile.renderColorAt(x, z);
        return color == 0 ? 0x00FFFFFF : color;
    }

    private static Block sampleBlock(
            LostTalesMapTerrainCache.TerrainTile tile, int sample) {
        int x = sample % LostTalesMapTerrainCache.TILE_WIDTH;
        int z = sample / LostTalesMapTerrainCache.TILE_WIDTH;
        Block block = Block.getBlockById(tile.blockIdAt(x, z));
        return block == null || block == Blocks.air ? Blocks.stone : block;
    }

    private static int sampleMetadata(
            LostTalesMapTerrainCache.TerrainTile tile, int sample) {
        return tile.metadataAt(
                sample % LostTalesMapTerrainCache.TILE_WIDTH,
                sample / LostTalesMapTerrainCache.TILE_WIDTH);
    }

    private static IIcon iconFor(Block block, int side, int metadata) {
        try {
            IIcon icon = block == null
                    ? null : block.getIcon(side, metadata);
            if (icon != null) {
                return icon;
            }
        } catch (Throwable ignored) {
            // A custom block without an inventory icon should not discard the
            // whole client-known terrain tile.
        }
        return Blocks.stone.getIcon(side, 0);
    }

    private static Mesh findMesh(int chunkX, int chunkZ) {
        for (int index = 0; index < meshCount; index++) {
            Mesh mesh = MESHES[index];
            if (mesh.chunkX == chunkX && mesh.chunkZ == chunkZ) {
                return mesh;
            }
        }
        return null;
    }

    private static Mesh obtainMeshSlot() {
        if (meshCount < MAX_MESHES) {
            Mesh mesh = new Mesh();
            MESHES[meshCount++] = mesh;
            return mesh;
        }
        Mesh oldest = null;
        for (int index = 0; index < meshCount; index++) {
            Mesh candidate = MESHES[index];
            // Never evict a tile used in this or the immediately preceding
            // frame. Doing that when a viewport exceeded the mesh cap caused
            // an endless eviction/recompile wave and visible checkerboarding.
            if (candidate.lastUsedFrame >= frameSequence - 1L) {
                continue;
            }
            if (oldest == null
                    || candidate.lastUsedFrame < oldest.lastUsedFrame) {
                oldest = candidate;
            }
        }
        if (oldest == null) {
            return null;
        }
        safeDeleteList(oldest.listId);
        return oldest;
    }

    private static void ensureContext(
            WorldClient world, int dimension, boolean sepia) {
        if (activeWorld == world && activeDimension == dimension
                && activeSepia == sepia) {
            return;
        }
        releaseMeshes();
        activeWorld = world;
        activeDimension = dimension;
        activeSepia = sepia;
    }

    /** Releases dimension-bound GPU state at every client lifecycle boundary. */
    public static void clear() {
        releaseMeshes();
        for (int index = 0; index < TILE_BUFFER.length; index++) {
            TILE_BUFFER[index] = null;
        }
        activeWorld = null;
        activeDimension = Integer.MIN_VALUE;
        activeSepia = false;
        renderingDisabled = false;
        failureLogged = false;
    }

    private static void releaseMeshes() {
        for (int index = 0; index < meshCount; index++) {
            safeDeleteList(MESHES[index].listId);
            MESHES[index] = null;
        }
        meshCount = 0;
        frameSequence = 0L;
    }

    private static void safeDeleteList(int listId) {
        if (listId <= 0) {
            return;
        }
        try {
            GLAllocation.deleteDisplayLists(listId);
        } catch (Throwable ignored) {
            // A lost GL context has already discarded the resource.
        }
    }

    private static void disableAfterFailure(Throwable failure) {
        releaseMeshes();
        renderingDisabled = true;
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        FMLLog.warning(
                "[%s] Close-map terrain rendering was disabled for this "
                        + "client session; the normal LOTR map remains "
                        + "available (%s)",
                LostTalesMetaData.MOD_ID,
                failure == null ? "unknown error" : failure.toString());
    }

    static float zoomExponent(float scale) {
        if (!(scale > 0.0F) || Float.isInfinite(scale)) {
            return Float.NaN;
        }
        return (float)Math.log(scale) / LOG_TWO;
    }

    static int reliefHeight(int surfaceY) {
        return Math.max(MIN_RELIEF, Math.min(MAX_RELIEF,
                surfaceY + 1 - RELIEF_BASE_Y));
    }

    static float projectedReliefMargin(float blockScale, float leanSine) {
        return blockScale * MAX_RELIEF * TERRAIN_HEIGHT_SCALE
                * Math.abs(leanSine) + 2.0F;
    }

    static int sampleStep(float blockPixels) {
        if (blockPixels >= FULL_DETAIL_BLOCK_PIXELS) {
            return 1;
        }
        if (blockPixels >= MEDIUM_DETAIL_BLOCK_PIXELS) {
            return 2;
        }
        if (blockPixels >= FINE_DETAIL_BLOCK_PIXELS) {
            return 3;
        }
        return 4;
    }

    static float stableLodBlockScale(float mapScale) {
        float settledMaximum = (float)Math.pow(2.0D,
                LostTalesLotrMapGui.SMOOTH_ZOOM_MAX);
        return Math.min(mapScale, settledMaximum)
                / LOTRGenLayerWorld.scale;
    }

    static int cellExtent(int start, int sampleStep) {
        return Math.min(sampleStep,
                LostTalesMapTerrainCache.TILE_WIDTH - start);
    }

    static boolean isTileVisible(
            float originX, float originY, float tileSize,
            float reliefMargin,
            float minX, float maxX, float minY, float maxY) {
        float tileMinX = Math.min(originX, originX + tileSize);
        float tileMaxX = Math.max(originX, originX + tileSize);
        tileMinX -= reliefMargin;
        tileMaxX += reliefMargin;
        float tileMinY = Math.min(originY, originY + tileSize)
                - reliefMargin;
        float tileMaxY = Math.max(originY, originY + tileSize)
                + reliefMargin;
        return tileMaxX >= minX && tileMinX <= maxX
                && tileMaxY >= minY && tileMinY <= maxY;
    }

    static float topShade(int height, int westHeight, int northHeight) {
        // Light comes from the north-west. A higher west or north neighbour
        // darkens this sample like short ambient terrain shadow; exposed
        // north-west edges brighten. The clamp keeps extreme tree/building
        // height changes readable rather than black or blown out.
        float slope = (height - westHeight + height - northHeight) * 0.035F;
        return Math.max(0.62F, Math.min(1.15F, 0.94F + slope));
    }

    static float meshAvailabilityAlpha(long readyNanos, long nowNanos) {
        long age = nowNanos - readyNanos;
        if (age <= 0L) {
            return 0.0F;
        }
        if (age >= MESH_FADE_NANOS) {
            return 1.0F;
        }
        float progress = age / (float)MESH_FADE_NANOS;
        return progress * progress * (3.0F - 2.0F * progress);
    }

    static int shadeColor(int rgb, float factor) {
        int red = Math.min(255, Math.max(0,
                Math.round(((rgb >> 16) & 255) * factor)));
        int green = Math.min(255, Math.max(0,
                Math.round(((rgb >> 8) & 255) * factor)));
        int blue = Math.min(255, Math.max(0,
                Math.round((rgb & 255) * factor)));
        return red << 16 | green << 8 | blue;
    }

    private static int surfaceColor(int rgb, boolean sepia) {
        int color = rgb == 0
                ? FALLBACK_SURFACE_COLOR : rgb & 0x00FFFFFF;
        return sepia ? sepiaColor(color) : color;
    }

    static int sepiaColor(int rgb) {
        int red = rgb >> 16 & 255;
        int green = rgb >> 8 & 255;
        int blue = rgb & 255;
        int luminance = Math.round(red * 0.299F
                + green * 0.587F + blue * 0.114F);
        return Math.min(255, Math.round(luminance * 1.08F)) << 16
                | Math.min(255, Math.round(luminance * 0.91F)) << 8
                | Math.min(255, Math.round(luminance * 0.68F));
    }

    private static final class Mesh {
        private int chunkX;
        private int chunkZ;
        private LostTalesMapTerrainCache.TerrainTile tile;
        private LostTalesMapTerrainCache.TerrainTile east;
        private LostTalesMapTerrainCache.TerrainTile south;
        private int sampleStep;
        private boolean sepia;
        private int listId;
        private long lastUsedFrame;
        private long firstReadyNanos;

        private boolean matches(
                LostTalesMapTerrainCache.TerrainTile tile,
                LostTalesMapTerrainCache.TerrainTile east,
                LostTalesMapTerrainCache.TerrainTile south,
                int sampleStep, boolean sepia) {
            return this.tile == tile
                    && this.east == east
                    && this.south == south
                    && this.sampleStep == sampleStep
                    && this.sepia == sepia;
        }

        private void set(
                LostTalesMapTerrainCache.TerrainTile tile,
                LostTalesMapTerrainCache.TerrainTile east,
                LostTalesMapTerrainCache.TerrainTile south,
                int sampleStep, boolean sepia,
                int listId, long frame) {
            boolean newlyVisible = this.listId <= 0
                    || this.chunkX != tile.chunkX
                    || this.chunkZ != tile.chunkZ;
            this.chunkX = tile.chunkX;
            this.chunkZ = tile.chunkZ;
            this.tile = tile;
            this.east = east;
            this.south = south;
            this.sampleStep = sampleStep;
            this.sepia = sepia;
            this.listId = listId;
            this.lastUsedFrame = frame;
            if (newlyVisible) {
                this.firstReadyNanos = System.nanoTime();
            }
        }
    }
}
