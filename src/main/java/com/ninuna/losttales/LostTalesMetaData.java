package com.ninuna.losttales;

public class LostTalesMetaData {

    public static final String
            MOD_ID = "losttales",
            MOD_NAME = "Ainulindalë: Lost Tales",
            MOD_VERSION = "0.1.3 for Minecraft 1.7.10",
            MOD_DEPENDENCIES = "required-after:lotr@[Update v36.15 for Minecraft 1.7.10,); required-after:geckolib3@[3.0.40,)",
            MC_VERSION = "1.7.10",

            CLIENT_PROXY_CLASS = "com.ninuna.losttales.proxy.LostTalesClientProxy",
            SERVER_PROXY_CLASS = "com.ninuna.losttales.proxy.LostTalesServerProxy",
            GUI_FACTORY_CLASS = "com.ninuna.losttales.config.client.LostTalesConfigGuiFactory";
}
