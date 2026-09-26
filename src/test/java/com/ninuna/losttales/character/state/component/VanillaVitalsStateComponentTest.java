package com.ninuna.losttales.character.state.component;

import com.ninuna.losttales.character.state.CharacterStateValidationException;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The snapshot carries the breath, the fire and the portal cooldown; one
 * without them, one at another version and out-of-range conditions are
 * refused.
 */
public final class VanillaVitalsStateComponentTest {

    private final VanillaVitalsStateComponent component =
            new VanillaVitalsStateComponent();

    @Test
    public void theDefaultSnapshotIsVersionTwoWithCalmConditions()
            throws CharacterStateValidationException {
        NBTTagCompound state = this.component.createDefault();
        this.component.validate(state);
        assertEquals(2, state.getInteger("Version"));
        assertEquals(300, state.getInteger("Air"));
        assertEquals(0, state.getInteger("Fire"));
        assertEquals(0, state.getInteger("PortalCooldown"));
    }

    @Test(expected = CharacterStateValidationException.class)
    public void anOlderVersionIsRefused()
            throws CharacterStateValidationException {
        NBTTagCompound state = this.component.createDefault();
        state.setInteger("Version", 1);
        this.component.validate(state);
    }

    @Test(expected = CharacterStateValidationException.class)
    public void aSnapshotWithoutItsConditionsIsRefused()
            throws CharacterStateValidationException {
        NBTTagCompound state = this.component.createDefault();
        state.removeTag("Fire");
        this.component.validate(state);
    }

    @Test(expected = CharacterStateValidationException.class)
    public void aVersionAheadOfThisBuildIsRefused()
            throws CharacterStateValidationException {
        NBTTagCompound state = this.component.createDefault();
        state.setInteger("Version", 3);
        this.component.validate(state);
    }

    @Test(expected = CharacterStateValidationException.class)
    public void breathBeyondTheLungsIsRefused()
            throws CharacterStateValidationException {
        NBTTagCompound state = this.component.createDefault();
        state.setInteger("Air", 301);
        this.component.validate(state);
    }

    @Test(expected = CharacterStateValidationException.class)
    public void aNegativePortalCooldownIsRefused()
            throws CharacterStateValidationException {
        NBTTagCompound state = this.component.createDefault();
        state.setInteger("PortalCooldown", -1);
        this.component.validate(state);
    }

    @Test
    public void aBurningSnapshotWithinBoundsValidates()
            throws CharacterStateValidationException {
        NBTTagCompound state = this.component.createDefault();
        state.setInteger("Air", -20);
        state.setInteger("Fire", 160);
        state.setInteger("PortalCooldown", 300);
        this.component.validate(state);
    }
}
