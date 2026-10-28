package com.ninuna.losttales.util;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import org.apache.logging.log4j.Level;

/**
 * The mod's warnings and errors in the game log, each led by the mod id. Writing one
 * never fails its caller: outside a running game, as in a standalone
 * repair tool, FML's logger is not set up, and the line is dropped.
 */
public final class LostTalesLog {

    private LostTalesLog() {}

    /** Logs {@code [losttales] } and the formatted message as a warning. */
    public static void warning(String format, Object... args) {
        log(Level.WARN, null, format, args);
    }

    /** Logs {@code [losttales] } and the formatted message as an error, with the fault's trace. */
    public static void error(Throwable fault, String format, Object... args) {
        log(Level.ERROR, fault, format, args);
    }

    private static void log(Level level, Throwable fault, String format,
                            Object... args) {
        Object[] values = new Object[(args == null ? 0 : args.length) + 1];
        values[0] = LostTalesMetaData.MOD_ID;
        if (args != null) {
            System.arraycopy(args, 0, values, 1, args.length);
        }
        try {
            if (fault == null) {
                FMLLog.log(level, "[%s] " + format, values);
            } else {
                FMLLog.log(level, fault, "[%s] " + format, values);
            }
        } catch (RuntimeException unavailable) {
            // FML's logger is not set up outside a running game.
        } catch (LinkageError unavailable) {
            // Nor are FML's classes there for a standalone tool.
        }
    }
}
