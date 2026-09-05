package com.ninuna.losttales.character.state.component;

import com.ninuna.losttales.character.state.CharacterStateValidationException;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Version 2 carries the breath, the fire and the portal cooldown; a
 * version 1 snapshot lacks them and still validates as full breath, no
 * fire and no cooldown, while out-of-range conditions are refused.
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

    @Test
    public void aVersionOneSnapshotWithoutTheConditionsStillValidates()
            throws CharacterStateValidationException {
        NBTTagCompound state = this.component.createDefault();
        state.setInteger("Version", 1);
        state.removeTag("Air");
        state.removeTag("Fire");
        state.removeTag("PortalCooldown");
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
