package com.ninuna.losttales.character.state.component;

import com.ninuna.losttales.character.state.CharacterStateValidationException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class VanillaSpawnStateComponentTest {

    private final VanillaSpawnStateComponent component =
            new VanillaSpawnStateComponent();

    @Test
    public void emptyPlayerNbtProducesValidatedEmptyState()
            throws CharacterStateValidationException {
        NBTTagCompound state = this.component.fromVanillaPlayerNbt(
                new NBTTagCompound());

        this.component.validate(state);
        assertEquals(0, state.getTagList(
                "Spawns", Constants.NBT.TAG_COMPOUND).tagCount());
    }

    @Test
    public void overworldAndDimensionSpawnsAreCanonicalizedByDimension()
            throws CharacterStateValidationException {
        NBTTagCompound player = overworldSpawn(100, 64, -200, false);
        NBTTagList dimensions = new NBTTagList();
        dimensions.appendTag(dimensionSpawn(7, 700, 80, 701, true));
        dimensions.appendTag(dimensionSpawn(-1, -10, 32, 20, false));
        player.setTag("Spawns", dimensions);

        NBTTagCompound state = this.component.fromVanillaPlayerNbt(player);
        NBTTagList spawns = state.getTagList(
                "Spawns", Constants.NBT.TAG_COMPOUND);

        assertEquals(3, spawns.tagCount());
        assertEquals(-1, spawns.getCompoundTagAt(0).getInteger("Dim"));
        assertEquals(0, spawns.getCompoundTagAt(1).getInteger("Dim"));
        assertEquals(7, spawns.getCompoundTagAt(2).getInteger("Dim"));
        this.component.validate(state);
    }

    @Test(expected = CharacterStateValidationException.class)
    public void duplicateDimensionIsRejected()
            throws CharacterStateValidationException {
        NBTTagCompound player = overworldSpawn(1, 64, 1, false);
        NBTTagList dimensions = new NBTTagList();
        dimensions.appendTag(dimensionSpawn(0, 2, 65, 2, true));
        player.setTag("Spawns", dimensions);

        this.component.fromVanillaPlayerNbt(player);
    }

    @Test(expected = CharacterStateValidationException.class)
    public void incompleteOverworldSpawnIsRejected()
            throws CharacterStateValidationException {
        NBTTagCompound player = new NBTTagCompound();
        player.setInteger("SpawnX", 1);

        this.component.fromVanillaPlayerNbt(player);
    }

    @Test(expected = CharacterStateValidationException.class)
    public void nonCanonicalForcedFlagIsRejected()
            throws CharacterStateValidationException {
        NBTTagCompound player = overworldSpawn(1, 64, 1, false);
        player.setByte("SpawnForced", (byte) 2);

        this.component.fromVanillaPlayerNbt(player);
    }

    @Test(expected = CharacterStateValidationException.class)
    public void wrongDimensionListElementTypeIsRejected()
            throws CharacterStateValidationException {
        NBTTagCompound player = new NBTTagCompound();
        NBTTagList dimensions = new NBTTagList();
        dimensions.appendTag(new NBTTagString("invalid"));
        player.setTag("Spawns", dimensions);

        this.component.fromVanillaPlayerNbt(player);
    }

    @Test(expected = CharacterStateValidationException.class)
    public void unsafeCoordinatesAreRejected()
            throws CharacterStateValidationException {
        this.component.fromVanillaPlayerNbt(
                overworldSpawn(30000001, 64, 0, false));
    }

    private static NBTTagCompound overworldSpawn(
            int x, int y, int z, boolean forced) {
        NBTTagCompound player = new NBTTagCompound();
        player.setInteger("SpawnX", x);
        player.setInteger("SpawnY", y);
        player.setInteger("SpawnZ", z);
        player.setBoolean("SpawnForced", forced);
        return player;
    }

    private static NBTTagCompound dimensionSpawn(
            int dimension, int x, int y, int z, boolean forced) {
        NBTTagCompound spawn = overworldSpawn(x, y, z, forced);
        spawn.setInteger("Dim", dimension);
        return spawn;
    }
}
