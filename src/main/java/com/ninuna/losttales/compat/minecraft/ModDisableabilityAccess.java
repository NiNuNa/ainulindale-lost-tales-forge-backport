package com.ninuna.losttales.compat.minecraft;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.FMLModContainer;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

import java.lang.reflect.Field;

/**
 * Marks this mod as one the Mods list cannot disable. FML gives every
 * {@code @Mod} a Disable button that works after a restart; a coremod
 * that patches the game cannot be switched off that way without leaving
 * its patches behind, so the button is greyed out as it is for the
 * loader's own containers. FML keeps the answer in a private field of
 * its container with no setter, so it is set once here, after the
 * field's shape has been checked; when it is not what this build
 * expects, the button is left as FML made it and the log says so.
 */
public final class ModDisableabilityAccess {

    private ModDisableabilityAccess() {}

    public static void markNeverDisableable() {
        ModContainer container = Loader.instance().getIndexedModList().get(LostTalesMetaData.MOD_ID);
        if (!(container instanceof FMLModContainer)) {
            return;
        }
        try {
            Field field = FMLModContainer.class.getDeclaredField("disableability");
            if (field.getType() != ModContainer.Disableable.class) {
                throw new IllegalStateException("disableability is a "
                        + field.getType().getName());
            }
            field.setAccessible(true);
            field.set(container, ModContainer.Disableable.NEVER);
        } catch (NoSuchFieldException exception) {
            note(exception);
        } catch (IllegalAccessException exception) {
            note(exception);
        } catch (RuntimeException exception) {
            note(exception);
        }
    }

    private static void note(Exception exception) {
        FMLLog.info("[%s] The Mods list's Disable button could not be greyed out: %s",
                LostTalesMetaData.MOD_ID, exception.toString());
    }
}
