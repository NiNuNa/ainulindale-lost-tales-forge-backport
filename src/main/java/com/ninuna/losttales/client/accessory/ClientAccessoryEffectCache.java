package com.ninuna.losttales.client.accessory;

import com.ninuna.losttales.accessory.AccessoryBootstrap;
import com.ninuna.losttales.accessory.AccessoryCompatibilityRegistry;
import com.ninuna.losttales.accessory.AccessoryDefinition;
import com.ninuna.losttales.network.packet.AccessoryEffectSyncPacket;
import net.minecraft.entity.player.EntityPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-only projection of server-derived equipped accessory effects. */
public final class ClientAccessoryEffectCache {

    private static final Map<UUID, State> STATES =
            new HashMap<UUID, State>();

    private ClientAccessoryEffectCache() {}

    public static synchronized void accept(AccessoryEffectSyncPacket packet) {
        if (packet == null || packet.isMalformed()
                || packet.getPlayerId() == null) {
            return;
        }
        State current = STATES.get(packet.getPlayerId());
        if (current != null && packet.getSequence() < current.sequence) {
            return;
        }
        STATES.put(packet.getPlayerId(), new State(
                packet.getEntityId(), packet.getSequence(),
                packet.getDefinitionId()));
    }

    public static synchronized AccessoryDefinition getDefinition(
            EntityPlayer player) {
        if (player == null || player.getUniqueID() == null) {
            return null;
        }
        State state = STATES.get(player.getUniqueID());
        if (state == null || state.entityId != player.getEntityId()
                || state.definitionId.length() == 0) {
            return null;
        }
        return AccessoryCompatibilityRegistry.getInstance()
                .getDefinition(state.definitionId);
    }

    public static boolean isConcealed(EntityPlayer player) {
        AccessoryDefinition definition = getDefinition(player);
        return definition != null
                && AccessoryBootstrap.CONCEALED_PUBLIC_EFFECT_ID.equals(
                definition.getPublicEffectId());
    }

    public static synchronized void clear() {
        STATES.clear();
    }

    private static final class State {
        private final int entityId;
        private final long sequence;
        private final String definitionId;

        private State(int entityId, long sequence, String definitionId) {
            this.entityId = entityId;
            this.sequence = sequence;
            this.definitionId = definitionId == null ? "" : definitionId;
        }
    }
}
