package com.ninuna.losttales.character.switching;

import com.ninuna.losttales.character.state.CharacterStateValidationException;
import com.ninuna.losttales.character.state.component.VanillaLocationStateComponent;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.compat.lotr.LotrStartingWaypointLocation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.world.Teleporter;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;

/** Performs validated, lifecycle-owned character position transitions. */
public final class CharacterLocationTransitionService {

    private static final int SAFE_Y_SEARCH = 24;

    private final VanillaLocationStateComponent component;

    public CharacterLocationTransitionService(
            VanillaLocationStateComponent component) {
        if (component == null) {
            throw new IllegalArgumentException("component must not be null");
        }
        this.component = component;
    }

    public boolean isPendingInitialLocation(NBTTagCompound state)
            throws CharacterStateValidationException {
        String kind = this.component.getKind(state);
        return VanillaLocationStateComponent.KIND_STARTING_WAYPOINT.equals(kind)
                || VanillaLocationStateComponent.KIND_WORLD_SPAWN.equals(kind);
    }

    public void transition(EntityPlayerMP player, NBTTagCompound state)
            throws CharacterStateValidationException {
        if (player == null || player.mcServer == null || player.worldObj == null
                || player.worldObj.isRemote) {
            throw new CharacterStateValidationException(
                    "Character location requires a connected server player");
        }
        this.component.validate(state);
        Target target = resolveTarget(player, state);
        move(player, target);
    }

    private Target resolveTarget(EntityPlayerMP player, NBTTagCompound state)
            throws CharacterStateValidationException {
        String kind = this.component.getKind(state);
        if (VanillaLocationStateComponent.KIND_POSITION.equals(kind)) {
            WorldServer world = requireWorld(
                    player.mcServer, this.component.getDimension(state));
            loadChunk(world, this.component.getX(state), this.component.getZ(state));
            return new Target(world,
                    this.component.getX(state), this.component.getY(state),
                    this.component.getZ(state), this.component.getYaw(state),
                    this.component.getPitch(state), false);
        }
        if (VanillaLocationStateComponent.KIND_STARTING_WAYPOINT.equals(kind)) {
            LotrCharacterAdapter adapter = LotrCharacterAdapter.getInstance();
            WorldServer world = requireWorld(
                    player.mcServer, adapter.getMiddleEarthDimensionId());
            LotrStartingWaypointLocation location =
                    adapter.resolveStartingWaypointLocation(
                            this.component.getWaypointId(state), world);
            if (location == null) {
                throw new CharacterStateValidationException(
                        "Starting waypoint could not be resolved");
            }
            loadChunk(world, location.getX(), location.getZ());
            location = adapter.resolveStartingWaypointLocation(
                    this.component.getWaypointId(state), world);
            if (location == null) {
                throw new CharacterStateValidationException(
                        "Starting waypoint became unavailable");
            }
            return new Target(world, location.getX(), location.getY(),
                    location.getZ(), player.rotationYaw, player.rotationPitch, true);
        }

        WorldServer world = requireWorld(player.mcServer, 0);
        ChunkCoordinates spawn = world.getSpawnPoint();
        double x = spawn.posX + 0.5D;
        double z = spawn.posZ + 0.5D;
        loadChunk(world, x, z);
        return new Target(world, x, spawn.posY, z,
                player.rotationYaw, player.rotationPitch, true);
    }

    private void move(EntityPlayerMP player, Target target)
            throws CharacterStateValidationException {
        double y = target.findSafeGround
                ? findSafeY(player, target.world, target.x, target.y, target.z)
                : target.y;
        boolean crossDimension = player.dimension
                != target.world.provider.dimensionId;
        boolean owned = false;
        try {
            if (crossDimension) {
                owned = CharacterLifecycleStateTracker
                        .beginOwnedDimensionTransition(player);
                if (!owned) {
                    throw new CharacterStateValidationException(
                            "Character dimension transition guard is unavailable");
                }
                player.mcServer.getConfigurationManager().transferPlayerToDimension(
                        player, target.world.provider.dimensionId,
                        new FixedTeleporter(target.world, target.x, y, target.z,
                                target.yaw, target.pitch));
            } else if (player.playerNetServerHandler != null) {
                player.playerNetServerHandler.setPlayerLocation(
                        target.x, y, target.z, target.yaw, target.pitch);
            } else {
                player.setLocationAndAngles(
                        target.x, y, target.z, target.yaw, target.pitch);
            }
            player.motionX = 0.0D;
            player.motionY = 0.0D;
            player.motionZ = 0.0D;
            player.fallDistance = 0.0F;
        } catch (CharacterStateValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CharacterStateValidationException(
                    "Character location transition failed", exception);
        } finally {
            if (owned) {
                CharacterLifecycleStateTracker.endOwnedDimensionTransition(player);
            }
        }
    }

    private static double findSafeY(EntityPlayerMP player,
                                    WorldServer world,
                                    double x, double preferredY, double z)
            throws CharacterStateValidationException {
        int base = MathHelper.floor_double(preferredY);
        for (int distance = 0; distance <= SAFE_Y_SEARCH; distance++) {
            int above = base + distance;
            if (isSafe(player, world, x, above, z)) {
                return above;
            }
            int below = base - distance;
            if (distance > 0 && isSafe(player, world, x, below, z)) {
                return below;
            }
        }
        throw new CharacterStateValidationException(
                "No safe standing position exists near the starting waypoint");
    }

    private static boolean isSafe(EntityPlayerMP player, WorldServer world,
                                  double x, int y, double z) {
        if (y < 1 || y + Math.ceil(player.height) >= world.getActualHeight()) {
            return false;
        }
        int blockX = MathHelper.floor_double(x);
        int blockZ = MathHelper.floor_double(z);
        if (!World.doesBlockHaveSolidTopSurface(
                world, blockX, y - 1, blockZ)) {
            return false;
        }
        double halfWidth = Math.max(0.3D, player.width / 2.0D);
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(
                x - halfWidth, y, z - halfWidth,
                x + halfWidth, y + Math.max(1.8D, player.height), z + halfWidth);
        List collisions = world.getCollidingBoundingBoxes(player, box);
        return collisions == null || collisions.isEmpty();
    }

    private static WorldServer requireWorld(MinecraftServer server, int dimension)
            throws CharacterStateValidationException {
        WorldServer world = server == null ? null
                : server.worldServerForDimension(dimension);
        if (world == null || world.provider == null
                || world.provider.dimensionId != dimension) {
            throw new CharacterStateValidationException(
                    "Character dimension " + dimension + " is unavailable");
        }
        return world;
    }

    private static void loadChunk(WorldServer world, double x, double z) {
        world.getChunkProvider().loadChunk(
                MathHelper.floor_double(x) >> 4,
                MathHelper.floor_double(z) >> 4);
    }

    private static final class Target {
        private final WorldServer world;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        private final boolean findSafeGround;

        private Target(WorldServer world, double x, double y, double z,
                       float yaw, float pitch, boolean findSafeGround) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.findSafeGround = findSafeGround;
        }
    }

    private static final class FixedTeleporter extends Teleporter {
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;

        private FixedTeleporter(WorldServer world, double x, double y, double z,
                                float yaw, float pitch) {
            super(world);
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        @Override
        public void placeInPortal(Entity entity, double oldX, double oldY,
                                  double oldZ, float oldYaw) {
            entity.setLocationAndAngles(
                    this.x, this.y, this.z, this.yaw, this.pitch);
            entity.motionX = 0.0D;
            entity.motionY = 0.0D;
            entity.motionZ = 0.0D;
        }

        @Override
        public boolean placeInExistingPortal(Entity entity, double oldX,
                                             double oldY, double oldZ,
                                             float oldYaw) {
            placeInPortal(entity, oldX, oldY, oldZ, oldYaw);
            return true;
        }

        @Override
        public boolean makePortal(Entity entity) {
            return false;
        }
    }
}
