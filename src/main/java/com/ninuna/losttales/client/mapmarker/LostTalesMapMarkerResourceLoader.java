package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerCatalog;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.util.LostTalesLog;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

/**
 * Reads the bundled marker files through the client's resource packs,
 * with the server's own reader and checks
 * ({@link LostTalesMapMarkerCatalog#readBundled}): a pack may change a
 * marker, and the client keeps only what a server would keep of it. A
 * server's own markers never come from here; its snapshots bring them.
 */
final class LostTalesMapMarkerResourceLoader {
    private LostTalesMapMarkerResourceLoader() {}

    static List<LostTalesMapMarkerData> loadSharedMarkers(IResourceManager resourceManager) {
        List<LostTalesMapMarkerData> markers = new ArrayList<LostTalesMapMarkerData>();
        if (resourceManager == null) {
            return markers;
        }
        List<String> problems = new ArrayList<String>();
        for (LostTalesMapMarkerDefinition marker : LostTalesMapMarkerCatalog.readBundled(
                source(resourceManager), problems)) {
            markers.add(toClientMarker(marker));
        }
        for (String problem : problems) {
            LostTalesLog.warning("Map marker file left out: %s", problem);
        }
        return markers;
    }

    /** The mod's files as the resource packs give them; none for one no pack has. */
    static DataFiles.Source source(final IResourceManager resourceManager) {
        return new DataFiles.Source() {
            @Override
            public Reader open(String path) throws IOException {
                try {
                    return new InputStreamReader(resourceManager.getResource(
                            new ResourceLocation(LostTalesMetaData.MOD_ID, path))
                            .getInputStream(), StandardCharsets.UTF_8);
                } catch (FileNotFoundException missing) {
                    return null;
                }
            }
        };
    }

    /** A bundled marker as the client draws it, its words read in the game's language. */
    private static LostTalesMapMarkerData toClientMarker(
            LostTalesMapMarkerDefinition marker) {
        return new LostTalesMapMarkerData(marker.getId(), marker.getName(),
                marker.getIconName(), marker.getColorName(),
                marker.getCategoryName(), marker.getDescription(),
                marker.hasFastTravel(), marker.getDimensionId(),
                marker.getX(), marker.getY(), marker.getZ(),
                marker.getCompassFadeInRadius(), marker.getDiscoveryRadius(),
                marker.isHiddenUntilDiscovered(), marker.isDiscoverable(),
                marker.requiresRegionUnlock(), marker.hasWaystone(),
                marker.getPriority(), marker.getSource());
    }
}
