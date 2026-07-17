package com.ninuna.losttales.config.client;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.client.camera.CameraPresetFileStore;
import com.ninuna.losttales.client.camera.ThirdPersonCameraRuntime;
import com.ninuna.losttales.config.LostTalesConfig;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
/**
 * Keeps the static legacy config values synced after the Forge Mod List config
 * GUI writes changes to disk.
 */
public class LostTalesConfigGuiEventHandler {
    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event != null && LostTalesMetaData.MOD_ID.equals(event.modID)) {
            LostTalesConfig.savePendingGuiConfiguration();
            LostTalesThirdPersonConfig.savePendingGuiConfiguration();
            LostTalesConfig.reload();
            LostTalesThirdPersonConfig.reload();
            CameraPresetFileStore.reload();
            ThirdPersonCameraRuntime.resetSession();
        }
    }
}
