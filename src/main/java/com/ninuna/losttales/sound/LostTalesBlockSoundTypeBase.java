package com.ninuna.losttales.sound;

import com.ninuna.losttales.LostTalesMetaData;
import net.minecraft.block.Block;

public class LostTalesBlockSoundTypeBase extends Block.SoundType {

    public LostTalesBlockSoundTypeBase(String soundName, float volume, float frequency) {
        super(soundName, volume, frequency);
    }

    @Override
    public String getBreakSound() {
        return LostTalesMetaData.MOD_ID + ":break." + this.soundName;
    }

    @Override
    public String getStepResourcePath() {
        return LostTalesMetaData.MOD_ID + ":step." + this.soundName;
    }

    @Override
    public String func_150496_b() {
        return LostTalesMetaData.MOD_ID + ":place." + this.soundName;
    }
}