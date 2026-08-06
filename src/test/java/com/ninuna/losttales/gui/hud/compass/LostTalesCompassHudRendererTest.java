package com.ninuna.losttales.gui.hud.compass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerProvider;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesStaticCompassMarkerProvider;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.Test;

public final class LostTalesCompassHudRendererTest {
    @Test
    public void compassHudAtlasMetadataMatchesBundledSprite()
            throws Exception {
        assertEquals("textures/gui/compass_hud.png",
                LostTalesCompassHudRenderer.COMPASS_HUD_TEXTURE
                        .getResourcePath());
        assertEquals(257,
                LostTalesCompassHudRenderer.COMPASS_HUD_TEXTURE_WIDTH);
        assertEquals(32,
                LostTalesCompassHudRenderer.COMPASS_HUD_TEXTURE_HEIGHT);
        assertEquals(257, LostTalesCompassHudRenderer.COMPASS_WIDTH);
        assertEquals(24, LostTalesCompassHudRenderer.COMPASS_HEIGHT);
        assertEquals(25,
                LostTalesCompassHudRenderer.VERTICAL_ARROW_TEXTURE_V);

        InputStream stream = LostTalesCompassHudRendererTest.class
                .getResourceAsStream(
                        "/assets/losttales/textures/gui/compass_hud.png");
        assertNotNull("Compass HUD sprite is missing", stream);
        try {
            BufferedImage atlas = ImageIO.read(stream);
            assertNotNull("Compass HUD sprite is not a readable PNG", atlas);
            assertEquals(
                    LostTalesCompassHudRenderer.COMPASS_HUD_TEXTURE_WIDTH,
                    atlas.getWidth());
            assertEquals(
                    LostTalesCompassHudRenderer.COMPASS_HUD_TEXTURE_HEIGHT,
                    atlas.getHeight());
            assertEquals(0, alphaAt(atlas, 2,
                    LostTalesCompassHudRenderer.COMPASS_HEIGHT));
            assertTrue(alphaAt(atlas, 2,
                    LostTalesCompassHudRenderer
                            .VERTICAL_ARROW_TEXTURE_V) > 0);
            assertBarArtIsHorizontallyCentred(atlas);
        } finally {
            stream.close();
        }
    }

    /**
     * The compass is drawn at its native size, so the placement box's centre is
     * the ornament's centre only while the art is symmetric within the atlas.
     * A re-export with lopsided padding would put the placement editor's centre
     * snap somewhere the compass does not look centred, which is hard to spot
     * by eye and easy to catch here.
     */
    private static void assertBarArtIsHorizontallyCentred(BufferedImage atlas) {
        int left = -1;
        int right = -1;
        for (int x = 0; x < atlas.getWidth(); x++) {
            boolean opaque = false;
            for (int y = 0; y < LostTalesCompassHudRenderer.COMPASS_HEIGHT; y++) {
                if (alphaAt(atlas, x, y) > 0) {
                    opaque = true;
                    break;
                }
            }
            if (opaque) {
                if (left < 0) {
                    left = x;
                }
                right = x;
            }
        }

        assertTrue("compass bar rows contain no opaque pixels", left >= 0);
        assertEquals(
                "bar art must be symmetric within the atlas so the placement"
                        + " centre matches the compass centre; left margin "
                        + left + " vs right margin "
                        + (atlas.getWidth() - 1 - right),
                left, atlas.getWidth() - 1 - right);
    }

    @Test
    public void eachMarkerDomainHasOneRegisteredProvider() throws Exception {
        Field field = LostTalesCompassHudRenderer.class
                .getDeclaredField("MARKER_PROVIDERS");
        field.setAccessible(true);
        List<?> providers = (List<?>)field.get(null);

        boolean staticMarkersFound = false;
        Set<Class<?>> providerTypes = new HashSet<Class<?>>();
        for (Object provider : providers) {
            assertTrue(provider instanceof LostTalesCompassMarkerProvider);
            assertTrue("Duplicate compass provider type "
                            + provider.getClass().getName(),
                    providerTypes.add(provider.getClass()));
            if (provider instanceof LostTalesStaticCompassMarkerProvider) {
                staticMarkersFound = true;
            }
        }
        assertTrue("Static map marker provider is missing", staticMarkersFound);
        assertEquals(
                "Compass should have one provider per marker domain",
                4, providerTypes.size());
    }

    private static int alphaAt(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }
}
