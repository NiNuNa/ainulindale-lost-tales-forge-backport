package com.ninuna.losttales.config.client;

import com.ninuna.losttales.config.server.LostTalesServerConfigService;
import com.ninuna.losttales.config.server.ServerConfigChange;
import com.ninuna.losttales.config.server.ServerConfigEntry;
import com.ninuna.losttales.config.server.ServerConfigSnapshot;
import cpw.mods.fml.client.config.DummyConfigElement;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/**
 * Forge's own config screen over the server's settings. The snapshot the
 * server sent becomes an in-memory configuration, one category per
 * child screen, so every value is edited with the editor Forge gives its
 * type. Done does not touch a file: it collects what changed and hands it
 * to the server, whose answer the loading screen shows. The mod id is
 * not the mod's own, so Forge's config-changed event does not reload the
 * client's config from this screen.
 */
public final class LostTalesServerConfigGui extends GuiConfig {

    private static final String SCREEN_ID = "losttales.server";
    private static final int BUTTON_DONE = 2000;

    private final GuiScreen returnTo;
    private final List<ServerConfigEntry> snapshot;
    private final Configuration edited;
    /** Whether the settings are the local file's rather than a server's. */
    private final boolean local;

    public LostTalesServerConfigGui(GuiScreen parent, List<ServerConfigEntry> snapshot) {
        this(parent, snapshot, false);
    }

    public LostTalesServerConfigGui(GuiScreen parent, List<ServerConfigEntry> snapshot,
                                    boolean local) {
        this(parent, snapshot, ServerConfigSnapshot.toConfiguration(snapshot), local);
    }

    private LostTalesServerConfigGui(GuiScreen parent, List<ServerConfigEntry> snapshot,
                                     Configuration edited, boolean local) {
        super(parent, elementsOf(edited), SCREEN_ID, false, false,
                I18n.format(local ? "gui.losttales.server_settings.hosting_title"
                        : "gui.losttales.server_settings.title"),
                I18n.format(local ? "gui.losttales.server_settings.hosting_subtitle"
                        : "gui.losttales.server_settings.subtitle"));
        this.returnTo = parent;
        this.snapshot = snapshot;
        this.edited = edited;
        this.local = local;
    }

    private static List<IConfigElement> elementsOf(Configuration config) {
        List<IConfigElement> elements = new ArrayList<IConfigElement>();
        for (String category : new TreeSet<String>(config.getCategoryNames())) {
            List<IConfigElement> children =
                    new ConfigElement(config.getCategory(category)).getChildElements();
            elements.add(new DummyConfigElement.DummyCategoryElement(category,
                    config.getCategory(category).getLanguagekey(), children));
        }
        return elements;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || button.id != BUTTON_DONE) {
            super.actionPerformed(button);
            return;
        }
        if (this.entryList != null && this.entryList.hasChangedEntry(true)) {
            this.entryList.saveConfigElements();
        }
        List<ServerConfigChange> changes = changes();
        if (changes.isEmpty()) {
            this.mc.displayGuiScreen(this.returnTo);
            return;
        }
        if (this.local) {
            // The local file is the server here; it is written directly.
            this.mc.displayGuiScreen(LostTalesServerConfigLoadingGui.showing(this.returnTo,
                    LostTalesServerConfigService.apply(changes)));
            return;
        }
        this.mc.displayGuiScreen(new LostTalesServerConfigLoadingGui(this.returnTo, changes));
    }

    /** Every entry whose edited value differs from the snapshot's. */
    List<ServerConfigChange> changes() {
        List<ServerConfigChange> changes = new ArrayList<ServerConfigChange>();
        for (ServerConfigEntry entry : this.snapshot) {
            if (!this.edited.hasCategory(entry.getCategory())) {
                continue;
            }
            Property property = this.edited.getCategory(entry.getCategory()).get(entry.getKey());
            if (property == null) {
                continue;
            }
            if (entry.isList()) {
                List<String> values = Arrays.asList(property.getStringList());
                if (!values.equals(entry.getValues())) {
                    changes.add(new ServerConfigChange(entry.getCategory(), entry.getKey(),
                            true, values));
                }
            } else if (!property.getString().equals(entry.getValue())) {
                changes.add(new ServerConfigChange(entry.getCategory(), entry.getKey(),
                        property.getString()));
            }
        }
        return changes;
    }
}
