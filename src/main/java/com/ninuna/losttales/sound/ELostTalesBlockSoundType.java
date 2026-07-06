package com.ninuna.losttales.sound;

import net.minecraft.block.Block;

public enum ELostTalesBlockSoundType {
    CERAMIC(new LostTalesBlockSoundTypeBase("ceramic", 1.0F, 1.0F)),
    CLAY(new LostTalesBlockSoundTypeBase("clay", 1.0F, 1.0F));

    private final Block.SoundType soundType;

    ELostTalesBlockSoundType(Block.SoundType soundType) {
        this.soundType = soundType;
    }

    public Block.SoundType getSoundType() {
        return soundType;
    }
}