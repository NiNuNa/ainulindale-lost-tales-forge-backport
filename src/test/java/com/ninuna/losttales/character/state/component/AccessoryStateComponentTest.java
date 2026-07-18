package com.ninuna.losttales.character.state.component;

import com.ninuna.losttales.character.state.CharacterStateValidationException;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class AccessoryStateComponentTest {

    @Test
    public void emptyDefaultStateIsValid() throws Exception {
        AccessoryStateComponent component = new AccessoryStateComponent();
        NBTTagCompound state = component.createDefault();

        component.validate(state);

        assertEquals(1, state.getInteger("Version"));
    }

    @Test(expected = CharacterStateValidationException.class)
    public void unknownComponentVersionFailsClosed() throws Exception {
        AccessoryStateComponent component = new AccessoryStateComponent();
        NBTTagCompound state = component.createDefault();
        state.setInteger("Version", 2);

        component.validate(state);
    }
}
