package com.ninuna.losttales.compat.minecraft;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Field;

/**
 * Mapping-safe access to the private vanilla item-use state.
 *
 * <p>Some ForgeGradle 1.2 development launches expose the runtime-deobfuscated
 * SRG fields while compiling this mod against MCP names. Keeping both names as
 * strings avoids emitting a fragile Minecraft method or field reference in the
 * mod bytecode.</p>
 */
public final class PlayerItemUseAccess {

    private static final Field ITEM_IN_USE = findField(
            "itemInUse", "field_71074_e");
    private static final Field ITEM_IN_USE_COUNT = findField(
            "itemInUseCount", "field_71072_f");
    private static boolean accessFailureLogged;

    static {
        if (ITEM_IN_USE == null || ITEM_IN_USE_COUNT == null) {
            logAccessFailure(null);
        }
    }

    private PlayerItemUseAccess() {}

    public static ItemStack getItemInUse(EntityPlayer player) {
        if (player == null || ITEM_IN_USE == null) {
            return null;
        }
        try {
            Object value = ITEM_IN_USE.get(player);
            return value instanceof ItemStack ? (ItemStack)value : null;
        } catch (IllegalAccessException exception) {
            logAccessFailure(exception);
            return null;
        } catch (RuntimeException exception) {
            logAccessFailure(exception);
            return null;
        }
    }

    public static int getItemInUseCount(EntityPlayer player) {
        if (player == null || ITEM_IN_USE_COUNT == null) {
            return 0;
        }
        try {
            return Math.max(0, ITEM_IN_USE_COUNT.getInt(player));
        } catch (IllegalAccessException exception) {
            logAccessFailure(exception);
            return 0;
        } catch (RuntimeException exception) {
            logAccessFailure(exception);
            return 0;
        }
    }

    public static boolean isUsingItem(EntityPlayer player) {
        return getItemInUse(player) != null;
    }

    public static boolean isAvailable() {
        return ITEM_IN_USE != null && ITEM_IN_USE_COUNT != null;
    }

    private static Field findField(String mcpName, String srgName) {
        Field field = findDeclaredField(mcpName);
        return field == null ? findDeclaredField(srgName) : field;
    }

    private static Field findDeclaredField(String name) {
        try {
            Field field = EntityPlayer.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException ignored) {
            return null;
        } catch (SecurityException exception) {
            logAccessFailure(exception);
            return null;
        }
    }

    private static synchronized void logAccessFailure(Throwable failure) {
        if (accessFailureLogged) {
            return;
        }
        accessFailureLogged = true;
        FMLLog.warning("[losttales] Vanilla item-use state is unavailable; "
                + "charge-dependent features are disabled safely: %s",
                failure == null ? "unknown mapping" : failure.toString());
    }
}
