package com.ninuna.losttales.network.server;

import com.ninuna.losttales.compat.minecraft.PlayerItemUseAccess;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public final class PlayerItemUseAccessTest {

    @Test
    public void resolvesMcpItemUseFieldsInDevelopmentRuntime() {
        assertTrue(PlayerItemUseAccess.isAvailable());
    }
}
