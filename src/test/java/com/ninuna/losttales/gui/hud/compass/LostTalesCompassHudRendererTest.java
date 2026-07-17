package com.ninuna.losttales.gui.hud.compass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerProvider;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesStaticCompassMarkerProvider;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public final class LostTalesCompassHudRendererTest {
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
}
