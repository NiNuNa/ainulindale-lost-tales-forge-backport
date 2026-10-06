package com.ninuna.losttales.config.client;

import java.util.Set;
import java.util.TreeSet;
import net.minecraftforge.common.config.Configuration;

/** Every camera option, read as the first load defines the options. */
public final class DefinedCameraOptions {
    private DefinedCameraOptions() {}

    public static Set<String> keys() {
        return new TreeSet<String>(definitions().getCategory(
                LostTalesThirdPersonConfig.CATEGORY_CAMERA).keySet());
    }

    /** The options themselves, each as it is defined. */
    public static Configuration definitions() {
        Configuration definitions = new Configuration();
        LostTalesThirdPersonConfig.defineOptions(definitions);
        return definitions;
    }
}
