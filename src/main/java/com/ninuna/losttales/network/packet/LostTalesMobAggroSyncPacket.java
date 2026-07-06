package com.ninuna.losttales.network.packet;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Server-to-client snapshot of nearby mobs currently targeting the player. */
public class LostTalesMobAggroSyncPacket implements IMessage {
    private static final int MAX_ENTITY_IDS = 4096;

    private final List<Integer> entityIds = new ArrayList<Integer>();

    public LostTalesMobAggroSyncPacket() {}

    public LostTalesMobAggroSyncPacket(Collection<Integer> entityIds) {
        if (entityIds != null) {
            int copied = 0;
            for (Integer entityId : entityIds) {
                if (entityId != null) {
                    this.entityIds.add(entityId);
                    copied++;
                    if (copied >= MAX_ENTITY_IDS) {
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.entityIds.clear();
        int count = buf.readInt();
        if (count < 0) {
            count = 0;
        }
        for (int i = 0; i < count; i++) {
            int entityId = buf.readInt();
            if (i < MAX_ENTITY_IDS) {
                this.entityIds.add(entityId);
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int count = Math.min(this.entityIds.size(), MAX_ENTITY_IDS);
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeInt(this.entityIds.get(i));
        }
    }

    public List<Integer> getEntityIds() {
        return Collections.unmodifiableList(new ArrayList<Integer>(this.entityIds));
    }
}
