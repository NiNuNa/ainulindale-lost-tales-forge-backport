package com.ninuna.losttales.network.server;

import com.ninuna.losttales.DedicatedServerIsolation;
import com.ninuna.losttales.gameplay.item.ThirdPersonItemUsePolicy;
import com.ninuna.losttales.gameplay.projectile.ChargeTierCalculator;
import com.ninuna.losttales.gameplay.projectile.ThirdPersonChargeItemPolicy;
import com.ninuna.losttales.gameplay.projectile.ThirdPersonProjectileItemPolicy;
import com.ninuna.losttales.network.packet.LostTalesThirdPersonAimPacket;
import com.ninuna.losttales.network.packet.LostTalesThirdPersonBlockActionPacket;
import com.ninuna.losttales.network.packet.LostTalesThirdPersonEntityActionPacket;
import com.ninuna.losttales.network.packet.LostTalesChargeTierSyncPacket;
import java.io.IOException;
import org.junit.Test;

/** Guards common packet and authority classes against client-only linkage. */
public final class ThirdPersonDedicatedServerIsolationTest {

    @Test
    public void commonThirdPersonClassesContainNoClientOrLwjglReferences()
            throws Exception {
        Class<?>[] commonClasses = {
                ChargeTierCalculator.class,
                ThirdPersonChargeItemPolicy.class,
                ThirdPersonItemUsePolicy.class,
                ThirdPersonProjectileItemPolicy.class,
                LostTalesThirdPersonAimPacket.class,
                LostTalesThirdPersonBlockActionPacket.class,
                LostTalesThirdPersonEntityActionPacket.class,
                LostTalesChargeTierSyncPacket.class,
                LostTalesChargeService.class,
                LostTalesThirdPersonAimService.class,
                LostTalesThirdPersonBlockActionService.class,
                LostTalesThirdPersonEntityActionService.class,
                LostTalesThirdPersonProjectileAimHandler.class
        };
        DedicatedServerIsolation.assertServerSafe(commonClasses);
    }
}
