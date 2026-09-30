package com.ninuna.losttales.config;

import java.util.Set;
import java.util.TreeSet;
import net.minecraftforge.common.config.Configuration;

/** The key of every client option, read as the first load defines the options. */
public final class DefinedClientOptions {
    private DefinedClientOptions() {}

    public static Set<String> keys() {
        Configuration definitions = new Configuration();
        LostTalesConfig.defineOptions(definitions);
        return new TreeSet<String>(definitions.getCategory(
                LostTalesConfig.CATEGORY_CLIENT).keySet());
    }
}
