package com.ninuna.losttales.config.client;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.screen.LostTalesHudPlacementGui;
import cpw.mods.fml.client.config.DummyConfigElement;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
/**
 * Standard Forge 1.7.10 config screen opened from the Mods list.
 *
 * The actual values stay in LostTalesConfig so commands, keybinds, and the GUI
 * all edit one source of truth. The GUI uses dummy grouping nodes so the config
 * is easier to browse without moving existing values to new on-disk categories.
 */
public class LostTalesConfigGui extends GuiConfig {
    private static final int BUTTON_HUD_PLACEMENT = 62100;

    public LostTalesConfigGui(GuiScreen parentScreen) {
        super(
                parentScreen,
                getConfigElements(),
                LostTalesMetaData.MOD_ID,
                false,
                false,
                LostTalesMetaData.MOD_NAME + " Config"
        );
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.add(new GuiButton(BUTTON_HUD_PLACEMENT, Math.max(4, this.width - 154), 8, 150, 20, "HUD Placement Preview"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button != null && button.id == BUTTON_HUD_PLACEMENT) {
            this.mc.displayGuiScreen(new LostTalesHudPlacementGui(this));
            return;
        }
        super.actionPerformed(button);
    }

    private static List<IConfigElement> getConfigElements() {
        List<IConfigElement> elements = new ArrayList<IConfigElement>();
        Configuration config = LostTalesConfig.createConfiguration();
        if (config == null) {
            return elements;
        }

        config.load();
        LostTalesConfig.applyGuiMetadata(config);

        List<IConfigElement> client = new ConfigElement(config.getCategory(LostTalesConfig.CATEGORY_CLIENT)).getChildElements();
        List<IConfigElement> quests = new ConfigElement(config.getCategory(LostTalesConfig.CATEGORY_QUESTS)).getChildElements();
        List<IConfigElement> missives = new ConfigElement(config.getCategory(LostTalesConfig.CATEGORY_MISSIVES)).getChildElements();

        elements.add(group("hud", "losttales.config.category.client.hud", pick(client,
                "showLostTalesHud", "hudPlacementPreset",
                "showCompassHud", "linkShowCompassHud", "compassHudOffsetX", "compassHudOffsetY",
                "showQuickLootHud", "linkShowQuickLootHud", "quickLootHudOffsetX", "quickLootHudOffsetY",
                "showQuestHud", "linkShowQuestHud", "questHudOffsetX", "questHudOffsetY")));
        elements.add(group("compass", "losttales.config.category.client.compass", pick(client,
                "compassHudDisplayRadius", "showStaticCompassMarkers", "showLotrWaypointCompassMarkers",
                "onlyShowUnlockedLotrWaypoints", "showHostileCompassMarkers", "onlyShowAggroHostileCompassMarkers",
                "hostileCompassMarkerScanRadius")));
        elements.add(group("quickLoot", "losttales.config.category.client.quickLoot", pick(client,
                "quickLootHudMaxRows")));
        elements.add(group("questHud", "losttales.config.category.client.questHud", pick(client,
                "questHudMaxObjectives", "questHudMaxTrackedQuests", "questHudObjectiveLineCount",
                "showQuestHudNotifications", "showWorldQuestMarkers", "showDiscoveredWorldMapMarkers",
                "worldQuestMarkerMaxDistance", "showQuestChatFeedback", "playQuestSounds")));
        elements.add(group("questRules", "losttales.config.category.quests.rules", quests));
        elements.add(group("missives", "losttales.config.category.missives", missives));

        List<IConfigElement> leftovers = leftovers(client, elements);
        if (!leftovers.isEmpty()) {
            elements.add(group("other", "losttales.config.category.client.other", leftovers));
        }
        return elements;
    }

    private static IConfigElement group(String name, String langKey, List<IConfigElement> children) {
        return new DummyConfigElement.DummyCategoryElement(name, langKey, children == null ? new ArrayList<IConfigElement>() : children);
    }

    private static List<IConfigElement> pick(List<IConfigElement> source, String... names) {
        List<IConfigElement> result = new ArrayList<IConfigElement>();
        if (source == null || names == null) {
            return result;
        }
        for (String name : names) {
            IConfigElement element = find(source, name);
            if (element != null) {
                result.add(element);
            }
        }
        return result;
    }

    private static IConfigElement find(List<IConfigElement> source, String name) {
        if (source == null || name == null) {
            return null;
        }
        for (IConfigElement element : source) {
            if (element != null && name.equals(element.getName())) {
                return element;
            }
        }
        return null;
    }

    private static List<IConfigElement> leftovers(List<IConfigElement> source, List<IConfigElement> groups) {
        List<IConfigElement> result = new ArrayList<IConfigElement>();
        if (source == null) {
            return result;
        }
        Set<String> used = new HashSet<String>();
        if (groups != null) {
            for (IConfigElement group : groups) {
                if (group == null || group.getChildElements() == null) {
                    continue;
                }
                for (Object childObject : group.getChildElements()) {
                    if (!(childObject instanceof IConfigElement)) {
                        continue;
                    }
                    IConfigElement child = (IConfigElement) childObject;
                    if (child.getName() != null) {
                        used.add(child.getName());
                    }
                }
            }
        }
        for (IConfigElement element : source) {
            if (element != null && !used.contains(element.getName())) {
                result.add(element);
            }
        }
        return result;
    }
}
