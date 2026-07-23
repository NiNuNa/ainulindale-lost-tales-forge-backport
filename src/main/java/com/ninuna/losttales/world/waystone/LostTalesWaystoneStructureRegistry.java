package com.ninuna.losttales.world.waystone;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Server-controlled, collision-resistant structure placer registry. */
public final class LostTalesWaystoneStructureRegistry {
    private static final Map<String, LostTalesWaystoneStructurePlacer> PLACERS =
            new LinkedHashMap<String, LostTalesWaystoneStructurePlacer>();
    private static boolean initialized;

    private LostTalesWaystoneStructureRegistry() {}

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            register(new LostTalesGlowstoneHouseWaystonePlacer());
        } catch (RuntimeException exception) {
            warn("Failed to register glowstone-house waystone structure: %s",
                    exception.getMessage());
        }
    }

    public static synchronized void register(
            LostTalesWaystoneStructurePlacer placer) {
        if (placer == null || placer.getId() == null
                || !placer.getId().matches(
                        "[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException(
                    "structure placer id is invalid");
        }
        if (PLACERS.containsKey(placer.getId())) {
            throw new IllegalStateException(
                    "duplicate structure placer: " + placer.getId());
        }
        PLACERS.put(placer.getId(), placer);
    }

    public static synchronized LostTalesWaystoneStructurePlacer get(
            String id) {
        initialize();
        return id == null ? null : PLACERS.get(id.trim().toLowerCase());
    }

    public static synchronized Map<String, LostTalesWaystoneStructurePlacer>
    snapshot() {
        initialize();
        return Collections.unmodifiableMap(
                new LinkedHashMap<String, LostTalesWaystoneStructurePlacer>(
                        PLACERS));
    }

    private static void warn(String format, Object... args) {
        Object[] values = new Object[
                (args == null ? 0 : args.length) + 1];
        values[0] = LostTalesMetaData.MOD_ID;
        if (args != null) {
            System.arraycopy(args, 0, values, 1, args.length);
        }
        try {
            FMLLog.warning("[%s] " + format, values);
        } catch (RuntimeException ignored) {}
    }
}
