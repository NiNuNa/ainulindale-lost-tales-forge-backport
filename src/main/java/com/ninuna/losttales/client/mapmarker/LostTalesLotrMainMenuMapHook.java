package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import java.lang.reflect.Field;
import lotr.client.gui.LOTRGuiDownloadTerrain;
import lotr.client.gui.LOTRGuiMainMenu;
import net.minecraft.client.gui.GuiScreen;

/**
 * Replaces the private map delegates used by LOTR's decorative menu and
 * Middle-earth loading screens. LOTR Legacy exposes no event between these
 * map and vignette passes.
 */
public final class LostTalesLotrMainMenuMapHook {
    private static Field mainMenuMapGuiField;
    private static Field loadingMapGuiField;
    private static boolean mainMenuLookupFailed;
    private static boolean loadingLookupFailed;

    private LostTalesLotrMainMenuMapHook() {}

    public static void install(GuiScreen screen) {
        if (screen instanceof LOTRGuiMainMenu) {
            if (!mainMenuLookupFailed) {
                try {
                    if (mainMenuMapGuiField == null) {
                        mainMenuMapGuiField = resolveMapGuiField(
                                LOTRGuiMainMenu.class);
                    }
                    mainMenuMapGuiField.set(
                            screen,
                            new LostTalesLotrMenuMapGui(false));
                } catch (ReflectiveOperationException exception) {
                    mainMenuLookupFailed = true;
                    warn("main-menu", exception);
                } catch (RuntimeException exception) {
                    mainMenuLookupFailed = true;
                    warn("main-menu", exception);
                }
            }
            return;
        }
        if (screen instanceof LOTRGuiDownloadTerrain
                && !loadingLookupFailed) {
            try {
                if (loadingMapGuiField == null) {
                    loadingMapGuiField = resolveMapGuiField(
                            LOTRGuiDownloadTerrain.class);
                }
                loadingMapGuiField.set(
                        screen,
                        new LostTalesLotrMenuMapGui(true));
            } catch (ReflectiveOperationException exception) {
                loadingLookupFailed = true;
                warn("Middle-earth loading", exception);
            } catch (RuntimeException exception) {
                loadingLookupFailed = true;
                warn("Middle-earth loading", exception);
            }
        }
    }

    private static Field resolveMapGuiField(Class<?> owner)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField("mapGui");
        field.setAccessible(true);
        return field;
    }

    private static void warn(String screen, Throwable exception) {
        FMLLog.warning(
                "[%s] LOTR %s marker rendering disabled: "
                        + "expected mapGui field was unavailable (%s)",
                LostTalesMetaData.MOD_ID, screen,
                exception == null ? "unknown error"
                        : exception.toString());
    }
}
