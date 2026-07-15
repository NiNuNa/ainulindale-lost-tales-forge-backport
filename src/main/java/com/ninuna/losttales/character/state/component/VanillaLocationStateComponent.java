package com.ninuna.losttales.character.state.component;

import com.ninuna.losttales.character.state.CharacterStateApplyPhase;
import com.ninuna.losttales.character.state.CharacterStateComponent;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import java.util.Set;

/**
 * Character-owned dimension and position. Application is coordinator-only so
 * a malformed snapshot can never invoke an unjournaled dimension transfer.
 */
public final class VanillaLocationStateComponent implements CharacterStateComponent {

    public static final String ID = "vanilla_location";
    public static final String KIND_POSITION = "position";
    public static final String KIND_STARTING_WAYPOINT = "starting_waypoint";
    public static final String KIND_WORLD_SPAWN = "world_spawn";

    private static final int VERSION = 1;
    private static final int MAX_WAYPOINT_ID_LENGTH = 64;
    private static final double MAX_HORIZONTAL_COORDINATE = 30000000.0D;
    private static final String TAG_VERSION = "Version";
    private static final String TAG_KIND = "Kind";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_X = "X";
    private static final String TAG_Y = "Y";
    private static final String TAG_Z = "Z";
    private static final String TAG_YAW = "Yaw";
    private static final String TAG_PITCH = "Pitch";
    private static final String TAG_WAYPOINT_ID = "WaypointId";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public CharacterStateApplyPhase getApplyPhase() {
        return CharacterStateApplyPhase.COORDINATOR_ONLY;
    }

    @Override
    public NBTTagCompound capture(EntityPlayerMP player)
            throws CharacterStateValidationException {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            throw new CharacterStateValidationException(
                    "Character location requires a connected server player");
        }
        NBTTagCompound state = base(KIND_POSITION);
        state.setInteger(TAG_DIMENSION, player.dimension);
        state.setDouble(TAG_X, player.posX);
        state.setDouble(TAG_Y, player.posY);
        state.setDouble(TAG_Z, player.posZ);
        state.setFloat(TAG_YAW, player.rotationYaw);
        state.setFloat(TAG_PITCH, player.rotationPitch);
        validate(state);
        return state;
    }

    @Override
    public NBTTagCompound createDefault() {
        return base(KIND_WORLD_SPAWN);
    }

    public NBTTagCompound createStartingWaypoint(String waypointId) {
        String normalized = LotrCharacterAdapter.normalizeWaypointId(waypointId);
        if (normalized.length() == 0
                || normalized.length() > MAX_WAYPOINT_ID_LENGTH) {
            throw new IllegalArgumentException("Starting waypoint ID is invalid");
        }
        NBTTagCompound state = base(KIND_STARTING_WAYPOINT);
        state.setString(TAG_WAYPOINT_ID, normalized);
        return state;
    }

    @Override
    public void validate(NBTTagCompound state)
            throws CharacterStateValidationException {
        if (state == null || !state.hasKey(TAG_VERSION, Constants.NBT.TAG_INT)
                || state.getInteger(TAG_VERSION) != VERSION
                || !state.hasKey(TAG_KIND, Constants.NBT.TAG_STRING)) {
            throw new CharacterStateValidationException(
                    "Location component version or kind is missing");
        }
        String kind = state.getString(TAG_KIND);
        Set<?> keys = state.func_150296_c();
        if (KIND_POSITION.equals(kind)) {
            if (keys.size() != 8
                    || !state.hasKey(TAG_DIMENSION, Constants.NBT.TAG_INT)
                    || !state.hasKey(TAG_X, Constants.NBT.TAG_DOUBLE)
                    || !state.hasKey(TAG_Y, Constants.NBT.TAG_DOUBLE)
                    || !state.hasKey(TAG_Z, Constants.NBT.TAG_DOUBLE)
                    || !state.hasKey(TAG_YAW, Constants.NBT.TAG_FLOAT)
                    || !state.hasKey(TAG_PITCH, Constants.NBT.TAG_FLOAT)) {
                throw new CharacterStateValidationException(
                        "Stored character position is incomplete");
            }
            requireFinite(state.getDouble(TAG_X), "X");
            requireFinite(state.getDouble(TAG_Y), "Y");
            requireFinite(state.getDouble(TAG_Z), "Z");
            requireFinite(state.getFloat(TAG_YAW), "yaw");
            requireFinite(state.getFloat(TAG_PITCH), "pitch");
            if (state.getDouble(TAG_Y) < -4096.0D
                    || state.getDouble(TAG_Y) > 4096.0D) {
                throw new CharacterStateValidationException(
                        "Stored character Y coordinate is outside the safe range");
            }
            if (Math.abs(state.getDouble(TAG_X)) > MAX_HORIZONTAL_COORDINATE
                    || Math.abs(state.getDouble(TAG_Z))
                    > MAX_HORIZONTAL_COORDINATE) {
                throw new CharacterStateValidationException(
                        "Stored character position is outside the world boundary");
            }
        } else if (KIND_STARTING_WAYPOINT.equals(kind)) {
            if (keys.size() != 3
                    || !state.hasKey(TAG_WAYPOINT_ID, Constants.NBT.TAG_STRING)) {
                throw new CharacterStateValidationException(
                        "Starting-waypoint location is incomplete");
            }
            String waypointId = state.getString(TAG_WAYPOINT_ID);
            if (waypointId.length() == 0
                    || waypointId.length() > MAX_WAYPOINT_ID_LENGTH
                    || !waypointId.equals(
                            LotrCharacterAdapter.normalizeWaypointId(waypointId))) {
                throw new CharacterStateValidationException(
                        "Starting-waypoint location has an invalid identifier");
            }
        } else if (KIND_WORLD_SPAWN.equals(kind)) {
            if (keys.size() != 2) {
                throw new CharacterStateValidationException(
                        "World-spawn location contains unsupported fields");
            }
        } else {
            throw new CharacterStateValidationException(
                    "Unsupported character location kind " + kind);
        }
    }

    @Override
    public void apply(EntityPlayerMP player, NBTTagCompound state)
            throws CharacterStateValidationException {
        throw new CharacterStateValidationException(
                "Location must be applied by the character switch coordinator");
    }

    @Override
    public void synchronize(EntityPlayerMP player) {
        // The transition service sends the authoritative location packet.
    }

    public String getKind(NBTTagCompound state)
            throws CharacterStateValidationException {
        validate(state);
        return state.getString(TAG_KIND);
    }

    public int getDimension(NBTTagCompound state)
            throws CharacterStateValidationException {
        validate(state);
        return state.getInteger(TAG_DIMENSION);
    }

    public double getX(NBTTagCompound state) { return state.getDouble(TAG_X); }
    public double getY(NBTTagCompound state) { return state.getDouble(TAG_Y); }
    public double getZ(NBTTagCompound state) { return state.getDouble(TAG_Z); }
    public float getYaw(NBTTagCompound state) { return state.getFloat(TAG_YAW); }
    public float getPitch(NBTTagCompound state) { return state.getFloat(TAG_PITCH); }
    public String getWaypointId(NBTTagCompound state) {
        return state.getString(TAG_WAYPOINT_ID);
    }

    private static NBTTagCompound base(String kind) {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger(TAG_VERSION, VERSION);
        state.setString(TAG_KIND, kind);
        return state;
    }

    private static void requireFinite(double value, String field)
            throws CharacterStateValidationException {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new CharacterStateValidationException(
                    "Stored character " + field + " is not finite");
        }
    }
}
