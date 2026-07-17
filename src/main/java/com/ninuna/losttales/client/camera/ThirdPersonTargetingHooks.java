package com.ninuna.losttales.client.camera;

import com.ninuna.losttales.config.client.LostTalesThirdPersonConfig;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.util.MovingObjectPosition;

/** Post-vanilla mouse-over hook installed narrowly into EntityRenderer. */
public final class ThirdPersonTargetingHooks {
    public static final String ACTIVE_PROPERTY =
            "losttales.thirdPersonTargetingTransformer.active";

    private static boolean failureLogged;

    private ThirdPersonTargetingHooks() {}

    public static void resolveMouseOver(float partialTicks) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!Boolean.getBoolean(ACTIVE_PROPERTY)
                || !LostTalesThirdPersonConfig.enableCameraIntentTargeting
                || !ThirdPersonCameraRuntime.shouldUseCamera(
                minecraft, minecraft == null
                ? null : minecraft.renderViewEntity)) {
            return;
        }
        CameraRenderFrame frame =
                ThirdPersonCameraController.getRenderFrame();
        if (frame == null) {
            return;
        }
        try {
            MovingObjectPosition target =
                    ThirdPersonCameraTargetingSolver.resolve(
                    minecraft, frame, partialTicks);
            if (target == null) {
                return;
            }
            minecraft.objectMouseOver = target;
            minecraft.pointedEntity = target.typeOfHit
                    == MovingObjectPosition.MovingObjectType.ENTITY
                    && (target.entityHit instanceof EntityLivingBase
                    || target.entityHit instanceof EntityItemFrame)
                    ? target.entityHit : null;
        } catch (Throwable throwable) {
            if (!failureLogged) {
                failureLogged = true;
                FMLLog.warning(
                        "[losttales] Third-person camera targeting failed; "
                        + "vanilla targeting remains active: %s",
                        throwable.toString());
            }
        }
    }

    static void resetDiagnostics() {
        failureLogged = false;
    }
}
