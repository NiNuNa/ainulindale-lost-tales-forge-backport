package com.ninuna.losttales.sound;

import net.minecraft.block.Block;

public enum ELostTalesBlockSoundType {
    CERAMIC(new LostTalesBlockSoundTypeBase("ceramic", 1.0F, 1.0F)),

    // Kept as a separate sound key so clay-like blocks can be tuned later without
    // touching block code. sounds.json currently aliases this to the bundled
    // ceramic samples to avoid missing sound warnings in 1.7.10.
    CLAY(new LostTalesBlockSoundTypeBase("clay", 1.0F, 1.0F));

    private final Block.SoundType soundType;

    ELostTalesBlockSoundType(Block.SoundType soundType) {
        this.soundType = soundType;
    }

    public Block.SoundType getSoundType() {
        return soundType;
    }
}