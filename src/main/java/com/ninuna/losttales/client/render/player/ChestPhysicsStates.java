package com.ninuna.losttales.client.render.player;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * One {@link ChestPhysics} per rendered entity, stepped from the entity's own
 * tick counter the first time it is drawn in a tick. Keys are weak, so an
 * entity that leaves the world takes its state with it. Client render
 * thread only.
 */
public final class ChestPhysicsStates {

    /** Ticks caught up at most when an entity was not drawn for a while. */
    private static final int MAX_CATCH_UP_TICKS = 4;

    private static final Map<Entity, State> STATES = new WeakHashMap<Entity, State>();

    private ChestPhysicsStates() {}

    /**
     * The physics of an entity, advanced to its current tick.
     *
     * @param limbSwing       the biped's limb swing position
     * @param limbSwingAmount the biped's limb swing amount
     * @param bounce          strength from the config, 0 to 1
     */
    static ChestPhysics advance(EntityLivingBase entity, float limbSwing,
                                float limbSwingAmount, float bounce) {
        State state = STATES.get(entity);
        if (state == null) {
            state = new State(entity.ticksExisted, entity.isSneaking());
            STATES.put(entity, state);
        }
        int elapsed = entity.ticksExisted - state.lastTick;
        if (elapsed <= 0) {
            return state.physics;
        }
        if (elapsed > MAX_CATCH_UP_TICKS) {
            elapsed = MAX_CATCH_UP_TICKS;
        }
        float verticalMotion = (float)(entity.posY - entity.prevPosY);
        float walkCycle = MathHelper.cos(limbSwing * 0.6662F + (float)Math.PI)
                * limbSwingAmount;
        float turnDegrees = MathHelper.wrapAngleTo180_float(
                entity.renderYawOffset - entity.prevRenderYawOffset);
        boolean sneaking = entity.isSneaking();
        int sneakChange = sneaking == state.sneaking ? 0 : sneaking ? 1 : -1;
        state.sneaking = sneaking;
        for (int tick = 0; tick < elapsed; tick++) {
            // Only the first step carries the impulses; the rest settle.
            state.physics.tick(verticalMotion, walkCycle, turnDegrees,
                    tick == 0 ? sneakChange : 0, bounce);
            verticalMotion = 0.0F;
            turnDegrees = 0.0F;
        }
        state.lastTick = entity.ticksExisted;
        return state.physics;
    }

    public static void clear() {
        STATES.clear();
    }

    private static final class State {
        private final ChestPhysics physics = new ChestPhysics();
        private int lastTick;
        private boolean sneaking;

        private State(int tick, boolean sneaking) {
            this.lastTick = tick;
            this.sneaking = sneaking;
        }
    }
}
