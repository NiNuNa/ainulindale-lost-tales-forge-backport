package com.ninuna.losttales.config.client;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.client.camera.CameraPresetFileStore;
import com.ninuna.losttales.client.motion.MotionLabScreen;
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
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
/**
 * Standard Forge 1.7.10 config screen, reached through the settings hub
 * from the Mods list and the character menu: the client's own settings,
 * and nothing a server decides. The values stay in LostTalesConfig so commands, keybinds, and
 * the GUI all edit one source of truth; dummy grouping nodes make the
 * client category easier to browse without moving values on disk.
 *
 * <p>Every other category of the file belongs to a server and is edited
 * where it can be changed, from {@link LostTalesSettingsHubGui}: the
 * settings of the server this client is on, offered only once that
 * server has said the player is an operator, or in the main menu the
 * local file's server categories — the server this game hosts.</p>
 */
public class LostTalesConfigGui extends GuiConfig {
    private static final int BUTTON_HUD_PLACEMENT = 62100;
    private static final int BUTTON_MOTION_LAB = 62101;

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
        this.buttonList.add(new GuiButton(BUTTON_HUD_PLACEMENT,
                Math.max(4, this.width - 154), 8, 150, 20,
                "HUD Placement Editor"));
        this.buttonList.add(new GuiButton(BUTTON_MOTION_LAB, 4, 8, 100, 20,
                StatCollector.translateToLocal("gui.losttales.motionlab.open")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button != null && button.id == BUTTON_HUD_PLACEMENT) {
            this.mc.displayGuiScreen(new LostTalesHudPlacementGui(this));
            return;
        }
        if (button != null && button.id == BUTTON_MOTION_LAB) {
            this.mc.displayGuiScreen(new MotionLabScreen(this));
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
        // Each option as the mod ships it — the default its button
        // restores, its comment and bounds — and an option that is one
        // of a few words a button stepping through them.
        LostTalesConfig.applyShippedDefinitions(config);
        LostTalesCycleEntry.offerChoices(config.getCategory(
                LostTalesConfig.CATEGORY_CLIENT));

        Configuration cameraConfig =
                LostTalesThirdPersonConfig.createConfiguration();
        if (cameraConfig != null) {
            cameraConfig.load();
            LostTalesThirdPersonConfig.applyGuiMetadata(cameraConfig);
            CameraPresetFileStore.reload();
            Property presetProperty = cameraConfig.get(
                    LostTalesThirdPersonConfig.CATEGORY_CAMERA,
                    "cameraPreset",
                    LostTalesThirdPersonConfig.cameraPreset);
            // Each option as the mod ships it; the preset's words are
            // whichever preset files there are now.
            LostTalesThirdPersonConfig.applyShippedDefinitions(cameraConfig);
            presetProperty.setValidValues(
                    CameraPresetFileStore.getConfigValues());
            LostTalesCycleEntry.offerChoices(cameraConfig.getCategory(
                    LostTalesThirdPersonConfig.CATEGORY_CAMERA));
            elements.add(group(
                    "thirdPersonCamera",
                    "losttales.config.category.client.thirdPersonCamera",
                    new ConfigElement(cameraConfig.getCategory(
                    LostTalesThirdPersonConfig.CATEGORY_CAMERA))
                    .getChildElements()));
        }

        List<IConfigElement> client = new ConfigElement(config.getCategory(LostTalesConfig.CATEGORY_CLIENT)).getChildElements();

        elements.add(group("hud", "losttales.config.category.client.hud", pick(client,
                "showLostTalesHud", "hudPlacementPreset",
                "showCompassHud", "linkShowCompassHud", "compassHudOffsetX", "compassHudOffsetY",
                "showPartyHud", "linkShowPartyHud", "partyHudOffsetX", "partyHudOffsetY",
                "showQuickLootHud", "linkShowQuickLootHud", "quickLootHudOffsetX", "quickLootHudOffsetY",
                "showQuestHud", "linkShowQuestHud", "questHudOffsetX", "questHudOffsetY",
                "notificationHudOffsetX", "notificationHudOffsetY")));
        elements.add(group("compass", "losttales.config.category.client.compass", pick(client,
                "compassHudDisplayRadius", "showStaticCompassMarkers", "showLotrWaypointCompassMarkers",
                "onlyShowUnlockedLotrWaypoints", "showHostileCompassMarkers",
                "hostileCompassMarkerScanRadius", "showHostileMapMarkers", "hostileMapMarkerDisplayRadius")));
        elements.add(group("quickLoot", "losttales.config.category.client.quickLoot", pick(client,
                "quickLootHudMaxRows")));
        elements.add(group("questHud", "losttales.config.category.client.questHud", pick(client,
                "questHudMaxTrackedQuests", "questHudObjectiveLineCount",
                "showQuestHudNotifications", "showNativeLotrQuestTracker",
                "enableQuestDialogue",
                "showWorldQuestMarkers", "showDiscoveredWorldMapMarkers",
                "worldQuestMarkerMaxDistance", "showQuestChatFeedback", "playQuestSounds")));
        // The chat's own Chat Settings window holds every chat option but
        // the history's length, which is a safety bound.
        elements.add(group("chat", "losttales.config.category.client.chat",
                pick(client, "chatHistoryLines")));
        elements.add(group("motion",
                "losttales.config.category.client.motion",
                pick(client, "animations", "animationSpeed",
                        "reducedMotion")));
        elements.add(group("guiBackground",
                "losttales.config.category.client.guiBackground",
                pick(client, "enableGuiBackground", "guiBackgroundOpacity",
                        "guiAlwaysBlur", "enableGuiBackgroundBlur",
                        "guiBlurStrength")));
        List<IConfigElement> leftovers = leftovers(client, elements);
        if (!leftovers.isEmpty()) {
            elements.add(group("other", "losttales.config.category.client.other", leftovers));
        }
        return elements;
    }

    private static IConfigElement group(String name, String langKey, List<IConfigElement> children) {
        return new DummyConfigElement.DummyCategoryElement(
                name, langKey,
                children == null
                        ? new ArrayList<IConfigElement>() : children,
                LostTalesSavingCategoryEntry.class);
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
        // The chat's Chat Settings sets these, and nothing else shows them.
        used.addAll(LostTalesConfig.CHAT_SETTINGS_KEYS);
        for (IConfigElement element : source) {
            if (element != null && !used.contains(element.getName())) {
                result.add(element);
            }
        }
        return result;
    }
}
