package com.ninuna.losttales.client.skin;

import org.junit.Test;

import static org.junit.Assert.assertNull;

/** Without a client there is no cache to read. */
public final class VanillaSkinCacheAccessTest {

    @Test
    public void noClientMeansNoCache() {
        assertNull(VanillaSkinCacheAccess.cachedCopy(null, "abcdef"));
    }
}
