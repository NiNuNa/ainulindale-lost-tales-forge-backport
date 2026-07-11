package com.ninuna.losttales.core;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

/** Loads the two client rendering/camera corrections before normal mods. */
@IFMLLoadingPlugin.Name("Lost Tales Core")
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.SortingIndex(1002)
@IFMLLoadingPlugin.TransformerExclusions({"com.ninuna.losttales.core"})
public final class LostTalesCorePlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[] {LostTalesClassTransformer.class.getName()};
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        // No launch data is needed.
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
