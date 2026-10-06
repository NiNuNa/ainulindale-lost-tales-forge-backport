package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import lotr.client.gui.LOTRGuiMap;

/**
 * The part of LOTR's map it keeps in static fields, which every map would
 * otherwise share: its zoom, its size and edges, its widgets, and the
 * region and factions its legend shows. Each map page keeps its own and
 * puts it in place before its map draws or answers a press
 * ({@link #enter}), so two maps open at once each keep their own zoom.
 * Where a field cannot be found the maps share it, as LOTR's own do.
 */
public final class LostTalesLotrMapStatics {
    private static final String[] NAMES = {
            "fullscreen", "mapWidth", "mapHeight",
            "mapXMin", "mapXMax", "mapYMin", "mapYMax",
            "mapXMin_W", "mapXMax_W", "mapYMin_W", "mapYMax_W",
            "mapWidgets", "zoomPower", "zoomTicksMax",
            "currentRegion", "prevRegion", "currentFactionList"};

    private static Field[] fields;
    private static boolean resolved;
    /** The map whose values stand in LOTR's fields now; null for none. */
    private static LostTalesLotrMapStatics standing;

    private final Object[] values = new Object[NAMES.length];
    private boolean kept;

    /**
     * Puts this map's values in LOTR's fields, keeping the values of the
     * map that stood there before. A map entering for the first time
     * starts from whatever stands there.
     */
    void enter() {
        synchronized (LostTalesLotrMapStatics.class) {
            if (standing == this || !resolve()) {
                return;
            }
            if (standing != null) {
                standing.keep();
            }
            if (this.kept) {
                put();
            }
            standing = this;
        }
    }

    /** The map is gone: its values are kept no longer. */
    void leave() {
        synchronized (LostTalesLotrMapStatics.class) {
            if (standing == this) {
                standing = null;
            }
            this.kept = false;
        }
    }

    /** Leaving the world: no map's values stand any more; LOTR's own fields stay as they are. */
    public static synchronized void clear() {
        standing = null;
    }

    private void keep() {
        try {
            for (int index = 0; index < fields.length; index++) {
                Object value = fields[index].get(null);
                // A list is kept by what it holds: LOTR fills the one list
                // again for each map laid out.
                this.values[index] = value instanceof List
                        ? new ArrayList<Object>((List<?>)value) : value;
            }
            this.kept = true;
        } catch (IllegalAccessException unreadable) {
            this.kept = false;
        }
    }

    @SuppressWarnings("unchecked")
    private void put() {
        try {
            for (int index = 0; index < fields.length; index++) {
                Object value = this.values[index];
                Object current = fields[index].get(null);
                if (value instanceof List && current instanceof List) {
                    List<Object> list = (List<Object>)current;
                    list.clear();
                    list.addAll((List<?>)value);
                } else {
                    fields[index].set(null, value);
                }
            }
        } catch (IllegalAccessException unwritable) {
            FMLLog.warning("[%s] A map's own zoom could not be put back: %s",
                    LostTalesMetaData.MOD_ID, unwritable.toString());
        }
    }

    /** Finds LOTR's fields once; false when any is missing, and the maps share them all. */
    private static boolean resolve() {
        if (resolved) {
            return fields != null;
        }
        resolved = true;
        try {
            Field[] found = new Field[NAMES.length];
            for (int index = 0; index < NAMES.length; index++) {
                Field field = LOTRGuiMap.class.getDeclaredField(NAMES[index]);
                if (!Modifier.isStatic(field.getModifiers())
                        || Modifier.isFinal(field.getModifiers())) {
                    throw new NoSuchFieldException(NAMES[index]);
                }
                field.setAccessible(true);
                found[index] = field;
            }
            fields = found;
        } catch (NoSuchFieldException missing) {
            FMLLog.warning("[%s] LOTR's map has no field %s; maps open at once "
                    + "share their zoom", LostTalesMetaData.MOD_ID,
                    missing.getMessage());
        } catch (SecurityException refused) {
            FMLLog.warning("[%s] LOTR's map fields are out of reach; maps open "
                    + "at once share their zoom", LostTalesMetaData.MOD_ID);
        }
        return fields != null;
    }
}
