package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.fac.LOTRFaction;
import org.junit.Test;
import sun.misc.Unsafe;

public final class LostTalesLotrMapGuiTest {
    @Test
    public void factionControlZoneModeSurvivesReplacement()
            throws Exception {
        LOTRGuiMap original = allocate(LOTRGuiMap.class);
        field("controlZoneFaction").set(
                original, LOTRFaction.ISENGARD);
        field("hasControlZones").setBoolean(original, true);
        LostTalesLotrMapGui replacement =
                allocate(LostTalesLotrMapGui.class);

        assertTrue(LostTalesLotrMapGui.copyInitialMode(
                original, replacement));

        assertSame(LOTRFaction.ISENGARD,
                field("controlZoneFaction").get(replacement));
        assertTrue(field("hasControlZones").getBoolean(replacement));
    }

    @Test
    public void conquestGridModeSurvivesReplacement()
            throws Exception {
        LOTRGuiMap original = allocate(LOTRGuiMap.class);
        field("isConquestGrid").setBoolean(original, true);
        LostTalesLotrMapGui replacement =
                allocate(LostTalesLotrMapGui.class);

        assertTrue(LostTalesLotrMapGui.copyInitialMode(
                original, replacement));

        assertTrue(field("isConquestGrid").getBoolean(replacement));
    }

    @Test
    public void smoothZoomIsBoundedAndApproachesTargetWithoutOvershoot() {
        assertEquals(LostTalesLotrMapGui.SMOOTH_ZOOM_MIN,
                LostTalesLotrMapGui.clampSmoothZoom(-20.0F), 0.0F);
        assertEquals(LostTalesLotrMapGui.SMOOTH_ZOOM_MAX,
                LostTalesLotrMapGui.clampSmoothZoom(20.0F), 0.0F);

        float zoom = 3.0F;
        for (int tick = 0; tick < 40; tick++) {
            float next = LostTalesLotrMapGui.advanceSmoothZoom(zoom, 4.0F);
            assertTrue(next >= zoom);
            assertTrue(next <= 4.0F);
            zoom = next;
        }
        assertEquals(4.0F, zoom, 0.001F);
    }

    private static Field field(String name) throws Exception {
        Field field = LOTRGuiMap.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe)unsafeField.get(null);
        return type.cast(unsafe.allocateInstance(type));
    }
}
