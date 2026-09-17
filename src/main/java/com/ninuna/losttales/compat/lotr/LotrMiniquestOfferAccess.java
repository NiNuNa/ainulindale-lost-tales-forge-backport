package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.lang.reflect.Field;
import lotr.client.gui.LOTRGuiMiniquestOffer;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.quest.LOTRMiniQuest;

/**
 * What a LOTR quest offer is about, read off the screen LOTR opened.
 *
 * <p>{@code LOTRGuiMiniquestOffer} is handed its quest and the NPC
 * offering it and keeps both privately, so the only way to present the
 * same offer in this mod's own screen is to ask that screen what it is
 * about. Both fields are resolved once and their types verified rather
 * than trusted by name; an offer that cannot be read is left to LOTR's
 * own screen, which is why {@link #isAvailable} exists and why nothing
 * here throws.</p>
 */
@SideOnly(Side.CLIENT)
public final class LotrMiniquestOfferAccess {

    private static final Field QUEST = find("theMiniQuest", LOTRMiniQuest.class);
    private static final Field NPC = find("theNPC", LOTREntityNPC.class);
    private static boolean failureLogged;

    private LotrMiniquestOfferAccess() {}

    /** Whether an offer can be read at all. */
    public static boolean isAvailable() {
        return QUEST != null && NPC != null;
    }

    /** The quest the screen is offering, or null. */
    public static LOTRMiniQuest quest(LOTRGuiMiniquestOffer offer) {
        Object value = read(QUEST, offer);
        return value instanceof LOTRMiniQuest ? (LOTRMiniQuest)value : null;
    }

    /** Who is offering it, or null. */
    public static LOTREntityNPC npc(LOTRGuiMiniquestOffer offer) {
        Object value = read(NPC, offer);
        return value instanceof LOTREntityNPC ? (LOTREntityNPC)value : null;
    }

    private static Object read(Field field, LOTRGuiMiniquestOffer offer) {
        if (field == null || offer == null) {
            return null;
        }
        try {
            return field.get(offer);
        } catch (IllegalAccessException unreadable) {
            logFailure(unreadable);
            return null;
        } catch (RuntimeException unreadable) {
            logFailure(unreadable);
            return null;
        }
    }

    private static Field find(String name, Class<?> type) {
        try {
            Field field = LOTRGuiMiniquestOffer.class.getDeclaredField(name);
            if (field.getType() != type) {
                return null;
            }
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException missing) {
            return null;
        } catch (SecurityException refused) {
            return null;
        }
    }

    private static synchronized void logFailure(Throwable failure) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        FMLLog.warning("[%s] A LOTR quest offer could not be read "
                + "(LOTRGuiMiniquestOffer.theMiniQuest/theNPC), so its own "
                + "offer screen is shown instead of the Lost Tales one (%s)",
                LostTalesMetaData.MOD_ID,
                failure == null ? "no such field" : failure.toString());
    }
}
