package com.ninuna.losttales.accessory.player;

import com.ninuna.losttales.DedicatedServerIsolation;
import com.ninuna.losttales.accessory.effect.AccessoryEffectService;
import org.junit.Test;

import java.io.IOException;

/** Guards authoritative accessory classes against physical-client linkage. */
public final class AccessoryDedicatedServerIsolationTest {

    @Test
    public void commonAccessoryClassesContainNoClientOrLwjglReferences()
            throws IOException {
        Class<?>[] commonClasses = {
                AccessoryEquipService.class,
                AccessoryEffectService.class,
                AccessoryInventory.class,
                AccessoryInventorySyncManager.class,
                AccessoryPlayerData.class
        };
        DedicatedServerIsolation.assertServerSafe(commonClasses);
    }
}
