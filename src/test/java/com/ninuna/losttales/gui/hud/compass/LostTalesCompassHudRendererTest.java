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
        assertEquals(256,
                LostTalesCompassHudRenderer.COMPASS_HUD_TEXTURE_WIDTH);
        assertEquals(30,
                LostTalesCompassHudRenderer.COMPASS_HUD_TEXTURE_HEIGHT);
        assertEquals(256, LostTalesCompassHudRenderer.COMPASS_WIDTH);
        assertEquals(22, LostTalesCompassHudRenderer.COMPASS_HEIGHT);
        assertEquals(23,
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
        } finally {
            stream.close();
        }
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
