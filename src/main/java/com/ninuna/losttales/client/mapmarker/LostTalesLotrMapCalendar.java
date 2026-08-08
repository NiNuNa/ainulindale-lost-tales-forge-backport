package com.ninuna.losttales.client.mapmarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lotr.common.LOTRDate;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

/**
 * The Shire date and the hour, as one line for the map's control strip.
 *
 * <p>Both come from state the client already has: LOTR keeps the reckoning in
 * sync with its own packet, and the hour is the world's own time. Nothing is
 * asked of the server for this, and nothing is cached — the strip is drawn
 * once a frame and this is two field reads and a format.</p>
 */
@SideOnly(Side.CLIENT)
final class LostTalesLotrMapCalendar {
    /** Minecraft's day starts at dawn rather than at midnight. */
    private static final int DAWN_HOUR = 6;
    private static final int TICKS_PER_DAY = 24000;
    private static final int TICKS_PER_HOUR = 1000;

    private LostTalesLotrMapCalendar() {
    }

    /**
     * The date and hour, longest form first.
     *
     * <p>Several forms, because the strip has to fit on screens it does not
     * choose: the full one LOTR writes itself, weekday and all; the same date
     * without the weekday; and the hour alone, which is what is left worth
     * saying on a strip with almost no room. The strip takes the first that
     * fits and shows nothing rather than crowding.</p>
     *
     * <p>Never throws: the reckoning is a convenience on a status strip, and
     * a version of LOTR that has moved on should cost the date, not the
     * map.</p>
     */
    static String[] describe() {
        String time = worldTime();
        return distinct(new String[] {
                join(shireDate(true), time),
                join(shireDate(false), time),
                time
        });
    }

    /** Keeps the forms that say something, longest first, without repeats. */
    private static String[] distinct(String[] candidates) {
        String[] kept = new String[candidates.length];
        int size = 0;
        for (int index = 0; index < candidates.length; index++) {
            String candidate = candidates[index];
            if (candidate.length() == 0) {
                continue;
            }
            boolean seen = false;
            for (int other = 0; other < size; other++) {
                seen |= kept[other].equals(candidate);
            }
            if (!seen) {
                kept[size++] = candidate;
            }
        }
        String[] result = new String[size];
        System.arraycopy(kept, 0, result, 0, size);
        return result;
    }

    private static String join(String date, String time) {
        if (date.length() == 0) {
            return time;
        }
        return time.length() == 0 ? date : date + "   " + time;
    }

    private static String shireDate(boolean withWeekday) {
        try {
            LOTRDate.ShireReckoning.Date date =
                    LOTRDate.ShireReckoning.getShireDate();
            if (date == null) {
                return "";
            }
            String name = date.getDateName(withWeekday);
            return name == null ? "" : name;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String worldTime() {
        Minecraft minecraft = Minecraft.getMinecraft();
        World world = minecraft == null ? null : minecraft.theWorld;
        if (world == null) {
            return "";
        }
        try {
            return formatTime(world.getWorldTime());
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Tick zero is dawn, so the clock is offset rather than wrapped. */
    static String formatTime(long worldTime) {
        long ticks = worldTime % TICKS_PER_DAY;
        if (ticks < 0L) {
            ticks += TICKS_PER_DAY;
        }
        int hour = (int)((ticks / TICKS_PER_HOUR + DAWN_HOUR) % 24L);
        int minute = (int)((ticks % TICKS_PER_HOUR) * 60L / TICKS_PER_HOUR);
        return pad(hour) + ":" + pad(minute);
    }

    private static String pad(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }
}
