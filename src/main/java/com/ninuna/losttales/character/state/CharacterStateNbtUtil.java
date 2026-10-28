package com.ninuna.losttales.character.state;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** What the saved-life parts share: a compound's compressed size, and a clamp. */
public final class CharacterStateNbtUtil {

    private CharacterStateNbtUtil() {}

    public static int compressedSize(NBTTagCompound compound)
            throws CharacterStateValidationException {
        if (compound == null) {
            throw new CharacterStateValidationException("NBT compound is missing");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            CompressedStreamTools.writeCompressed(compound, output);
        } catch (IOException exception) {
            throw new CharacterStateValidationException(
                    "Unable to measure character state", exception);
        }
        return output.size();
    }

    public static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
