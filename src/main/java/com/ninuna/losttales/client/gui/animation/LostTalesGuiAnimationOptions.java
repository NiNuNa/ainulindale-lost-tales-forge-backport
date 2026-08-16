package com.ninuna.losttales.client.gui.animation;

/** Optional screen hook; returning {@link LostTalesGuiAnimationProfile#NONE} opts out. */
public interface LostTalesGuiAnimationOptions {
    LostTalesGuiAnimationProfile getLostTalesGuiAnimationProfile();
}
