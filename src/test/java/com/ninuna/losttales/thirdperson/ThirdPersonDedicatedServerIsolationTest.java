package com.ninuna.losttales.thirdperson;

import com.ninuna.losttales.DedicatedServerIsolation;
import com.ninuna.losttales.network.server.LostTalesChargeService;
import com.ninuna.losttales.network.server.LostTalesThirdPersonAimService;
import com.ninuna.losttales.network.server.LostTalesThirdPersonBlockActionService;
import com.ninuna.losttales.network.server.LostTalesThirdPersonEntityActionService;
import com.ninuna.losttales.network.server.LostTalesThirdPersonProjectileAimHandler;
import com.ninuna.losttales.network.packet.LostTalesThirdPersonAimPacket;
import com.ninuna.losttales.network.packet.LostTalesThirdPersonBlockActionPacket;
import com.ninuna.losttales.network.packet.LostTalesThirdPersonEntityActionPacket;
import com.ninuna.losttales.network.packet.LostTalesChargeTierSyncPacket;
import org.junit.Test;

/** Guards common packet and authority classes against client-only linkage. */
public final class ThirdPersonDedicatedServerIsolationTest {

    @Test
    public void commonThirdPersonClassesContainNoClientOrLwjglReferences()
            throws Exception {
        Class<?>[] commonClasses = {
                ChargeTierCalculator.class,
                ChargeItemPolicy.class,
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
