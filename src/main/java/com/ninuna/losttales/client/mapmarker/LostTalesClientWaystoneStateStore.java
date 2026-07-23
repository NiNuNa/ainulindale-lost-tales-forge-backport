package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.network.packet.LostTalesWaystoneStatePacket;
import java.util.HashMap;
import java.util.Map;

/** Latest server-authoritative state for open waystone screens. */
public final class LostTalesClientWaystoneStateStore {
    private static final Map<String, LostTalesWaystoneStatePacket> STATES =
            new HashMap<String, LostTalesWaystoneStatePacket>();

    private LostTalesClientWaystoneStateStore() {}

    public static synchronized void accept(
            LostTalesWaystoneStatePacket packet) {
        if (packet == null || packet.isMalformed()) {
            return;
        }
        STATES.put(key(packet.getDimensionId(),
                packet.getX(), packet.getY(), packet.getZ()), packet);
    }

    public static synchronized LostTalesWaystoneStatePacket get(
            int dimensionId, int x, int y, int z) {
        return STATES.get(key(dimensionId, x, y, z));
    }

    public static synchronized void clear() {
        STATES.clear();
    }

    private static String key(
            int dimensionId, int x, int y, int z) {
        return dimensionId + ":" + x + ":" + y + ":" + z;
    }
}
