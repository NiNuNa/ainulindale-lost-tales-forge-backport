package com.ninuna.losttales.client.skin;

import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The assets directory field must still exist on this Minecraft build. */
public final class VanillaSkinCacheAccessTest {

    @Test
    public void assetsDirectoryFieldResolves() {
        assertTrue(VanillaSkinCacheAccess.isAvailable());
    }

    @Test
    public void noClientMeansNoCache() {
        assertNull(VanillaSkinCacheAccess.cachedCopy(null, "abcdef"));
    }
}
