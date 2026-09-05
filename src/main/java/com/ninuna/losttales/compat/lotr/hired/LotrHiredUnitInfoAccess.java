package com.ninuna.losttales.compat.lotr.hired;

import cpw.mods.fml.common.FMLLog;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRHiredNPCInfo;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Reads and writes the two private facts of a hired unit LOTR offers no
 * setter for: the UUID it is hired under and the entity the info belongs
 * to. Each field is looked up once and checked for the type expected of
 * it; when either is missing the custody service stands down and says so
 * once, rather than guessing at a field of another shape.
 */
final class LotrHiredUnitInfoAccess {

    private static final Field HIRING_PLAYER_UUID = findField("hiringPlayerUUID", UUID.class);
    private static final Field THE_ENTITY = findField("theEntity", LOTREntityNPC.class);
    private static boolean failureLogged;

    private LotrHiredUnitInfoAccess() {}

    static boolean isAvailable() {
        return HIRING_PLAYER_UUID != null && THE_ENTITY != null;
    }

    /** The UUID the unit is hired under; null when none or unreadable. */
    static UUID hiringUuid(LOTRHiredNPCInfo info) {
        if (info == null || HIRING_PLAYER_UUID == null) {
            return null;
        }
        try {
            Object value = HIRING_PLAYER_UUID.get(info);
            return value instanceof UUID ? (UUID)value : null;
        } catch (IllegalAccessException exception) {
            logFailure(exception);
            return null;
        }
    }

    static boolean setHiringUuid(LOTRHiredNPCInfo info, UUID value) {
        if (info == null || HIRING_PLAYER_UUID == null) {
            return false;
        }
        try {
            HIRING_PLAYER_UUID.set(info, value);
            return true;
        } catch (IllegalAccessException exception) {
            logFailure(exception);
            return false;
        }
    }

    /** The unit the info belongs to; null when unreadable. */
    static LOTREntityNPC entityOf(LOTRHiredNPCInfo info) {
        if (info == null || THE_ENTITY == null) {
            return null;
        }
        try {
            Object value = THE_ENTITY.get(info);
            return value instanceof LOTREntityNPC ? (LOTREntityNPC)value : null;
        } catch (IllegalAccessException exception) {
            logFailure(exception);
            return null;
        }
    }

    private static Field findField(String name, Class<?> type) {
        try {
            Field field = LOTRHiredNPCInfo.class.getDeclaredField(name);
            if (!type.isAssignableFrom(field.getType())) {
                logFailure(new IllegalStateException("LOTRHiredNPCInfo." + name
                        + " is a " + field.getType().getName() + ", not a " + type.getName()));
                return null;
            }
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            logFailure(exception);
            return null;
        } catch (RuntimeException exception) {
            logFailure(exception);
            return null;
        }
    }

    private static synchronized void logFailure(Throwable cause) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        FMLLog.warning("[LostTales] Hired units cannot follow characters: "
                + "LOTRHiredNPCInfo is not the shape this build expects (%s)", cause);
    }
}
