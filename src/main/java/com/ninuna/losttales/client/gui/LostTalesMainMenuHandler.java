package com.ninuna.losttales.client.gui;

import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.character.CharacterTemplate;
import com.ninuna.losttales.client.character.CharacterTemplateStore;
import com.ninuna.losttales.client.character.LostTalesClientAccount;
import com.ninuna.losttales.client.gui.tooltip.LostTalesTooltipSmoothing;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.gui.screen.character.LostTalesCharacterCreationGui;
import com.ninuna.losttales.gui.style.LostTalesButtonStyle;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.lwjgl.opengl.GL11;

import java.lang.ref.WeakReference;
import java.util.UUID;

/**
 * Styles the main menu's standard controls and adds the account's
 * default-character template button before any world is open.
 *
 * <p>The menu is not gated: Singleplayer and Multiplayer stay where they
 * are and do what they always did. A world makes the account's default
 * character whether or not a template was ever written, so a player who
 * ignores this button loses nothing but the chance to say who they start
 * as.</p>
 *
 * <p>Keyed on {@link GuiMainMenu} rather than an exact class: LOTR
 * replaces the menu with a subclass of it, and that subclass is the one
 * every real installation shows. It also drives a second screen through
 * the same initialisation, so only the screen Minecraft is actually
 * showing is touched.</p>
 *
 * <p>The button is placed from the menu's own Singleplayer and
 * Multiplayer buttons, read after the menu has finished building them.
 * LOTR moves the whole column down and swaps every button for one of its
 * own in {@code initGui}, and this runs afterwards, so what is measured
 * is where the buttons ended up.</p>
 */
public final class LostTalesMainMenuHandler {

    /** Past every button vanilla and LOTR give the menu. */
    private static final int BUTTON_ID = 0x105774;
    /** Vanilla's ids for the two buttons the character button spans. */
    private static final int SINGLEPLAYER_ID = 1;
    private static final int MULTIPLAYER_ID = 2;

    /** Between the pointer and the label it brings up. */
    private static final int LABEL_GAP = 6;
    private static final int LABEL_PADDING = 4;

    /**
     * The button last added, and the menu it was added to.
     *
     * <p>{@code GuiScreen.buttonList} is not visible from here, so the
     * label pass cannot look the button up again and is handed it
     * instead. Both are held weakly: a menu that has closed must be
     * collectable, and on LOTR's menu it owns a whole map screen.</p>
     */
    private WeakReference<GuiScreen> labelledScreen;
    private WeakReference<LostTalesCharacterMenuButton> labelledButton;

    @SubscribeEvent
    public void onMainMenuInitialised(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event == null || !(event.gui instanceof GuiMainMenu)) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.currentScreen != event.gui) {
            // The menu draws a second screen behind itself through the
            // same path; that one is scenery and owns no buttons of ours.
            return;
        }
        styleMenuButtons(event);
        MainMenuButtonLayout.arrange(event.buttonList);
        // The character frame spans the final, uniformly spaced rows.
        GuiButton singleplayer = findButton(event, SINGLEPLAYER_ID);
        GuiButton multiplayer = findButton(event, MULTIPLAYER_ID);
        UUID account = LostTalesClientAccount.id();
        if (account == null) {
            // Nothing to keep a template under, so nothing to offer.
            return;
        }
        LostTalesCharacterMenuButton existing = labelledButtonFor(event.gui);
        if (existing != null && event.buttonList.contains(existing)) {
            return;
        }
        if (singleplayer == null || multiplayer == null
                || !singleplayer.visible || !multiplayer.visible) {
            // A demo world's menu, or one a mod has rebuilt: there is no
            // column to sit beside, so nothing is added.
            return;
        }
        CharacterMenuButtonPlacement placement =
                CharacterMenuButtonPlacement.beside(event.gui.width,
                        Math.min(singleplayer.xPosition, multiplayer.xPosition),
                        Math.max(singleplayer.xPosition + singleplayer.width,
                                multiplayer.xPosition + multiplayer.width),
                        Math.min(singleplayer.yPosition, multiplayer.yPosition),
                        Math.max(singleplayer.yPosition + singleplayer.height,
                                multiplayer.yPosition + multiplayer.height));
        if (placement == null) {
            return;
        }
        CharacterTemplate template = CharacterTemplateStore.load(account);
        LostTalesCharacterHeadIconRenderer.rememberAccountSkin(minecraft,
                account, LostTalesClientAccount.name());
        LostTalesCharacterMenuButton button = new LostTalesCharacterMenuButton(
                BUTTON_ID, placement.getX(), placement.getY(),
                placement.getHeight(), account, appearanceOf(account, template),
                I18n.format("gui.losttales.character.template.button"));
        event.buttonList.add(button);
        this.labelledScreen = new WeakReference<GuiScreen>(event.gui);
        this.labelledButton =
                new WeakReference<LostTalesCharacterMenuButton>(button);
    }

    /** Standard menu actions are dispatched by id in GuiMainMenu. */
    private static void styleMenuButtons(GuiScreenEvent.InitGuiEvent.Post event) {
        for (int index = 0; index < event.buttonList.size(); index++) {
            Object value = event.buttonList.get(index);
            if (!(value instanceof GuiButton) || value instanceof LostTalesButton) {
                continue;
            }
            GuiButton original = (GuiButton) value;
            if (original.width < LostTalesButtonStyle.MIN_SIZE
                    || original.height < LostTalesButtonStyle.MIN_SIZE) {
                continue;
            }
            switch (original.id) {
                case 0: // Options
                case 1: // Singleplayer
                case 2: // Multiplayer
                case 4: // Quit
                case 5: // Language
                case 6: // Forge mod list
                case 11: // Play demo
                case 12: // Reset demo
                case 14: // Realms
                    break;
                default:
                    continue;
            }
            LostTalesButton replacement = original.id == 5
                    ? new LostTalesLanguageButton(original.id, original.xPosition,
                            original.yPosition, original.width, MainMenuButtonLayout.HEIGHT,
                            original.displayString)
                    : new LostTalesButton(original.id, original.xPosition,
                            original.yPosition, original.width, MainMenuButtonLayout.HEIGHT,
                            original.displayString);
            replacement.enabled = original.enabled;
            replacement.visible = original.visible;
            replacement.packedFGColour = original.packedFGColour;
            event.buttonList.set(index, replacement);
        }
    }

    /**
     * The button is too narrow for its own name, so the name is drawn
     * beside the pointer instead. Drawn after the screen so the menu's
     * own panels cannot land on top of it.
     */
    @SubscribeEvent
    public void onMainMenuDrawn(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event == null || !(event.gui instanceof GuiMainMenu)) {
            return;
        }
        LostTalesCharacterMenuButton button = labelledButtonFor(event.gui);
        if (button == null || !button.isHovered()) {
            return;
        }
        FontRenderer font = Minecraft.getMinecraft() == null
                ? null : Minecraft.getMinecraft().fontRenderer;
        if (font == null) {
            return;
        }
        String label = button.displayString;
        int width = font.getStringWidth(label) + LABEL_PADDING * 2;
        int height = font.FONT_HEIGHT + LABEL_PADDING * 2;
        int x = Math.min(event.mouseX + LABEL_GAP,
                event.gui.width - width - 2);
        int y = Math.max(2, event.mouseY - height - LABEL_GAP);
        // On the pointer rather than on the interface grid, the same way
        // every other tooltip is placed.
        LostTalesTooltipSmoothing.begin(event.mouseX, event.mouseY);
        // A tooltip is in front of whatever it is over, and the button it
        // belongs to holds a model with depth of its own. Vanilla's own
        // drawHoveringText drops the depth test for the same reason; this
        // label is not drawn through it, so it drops it here.
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        try {
            LostTalesSkyrimUiStyle.drawPanel(x, y, width, height);
            LostTalesSkyrimUiStyle.beginContent();
            font.drawStringWithShadow(label, x + LABEL_PADDING,
                    y + LABEL_PADDING, LostTalesSkyrimUiStyle.TEXT_BRIGHT);
        } finally {
            if (depthTest) {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
            }
            LostTalesTooltipSmoothing.end();
        }
    }

    @SubscribeEvent
    public void onMainMenuButton(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event == null || !(event.gui instanceof GuiMainMenu)
                || event.button == null || event.button.id != BUTTON_ID) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        event.setCanceled(true);
        minecraft.displayGuiScreen(
                LostTalesCharacterCreationGui.forTemplate(event.gui));
    }

    /**
     * The template as an appearance the body model can be built from. A
     * template with nothing chosen resolves to the account's own look,
     * which is what a world would make its default character as.
     */
    private static CharacterAppearance appearanceOf(UUID account,
                                                    CharacterTemplate template) {
        String raceId = template.getRaceId().length() > 0
                ? template.getRaceId() : CharacterRaceRegistry.HUMAN;
        String genderId = template.getGenderId().length() > 0
                ? template.getGenderId()
                : CharacterRaceRegistry.normalizeGenderForRace(
                        raceId, CharacterGenderRegistry.MALE);
        String skinId = template.getSkinId().length() > 0
                ? template.getSkinId()
                : CharacterSkinRegistry.getDefaultSkinId(raceId, genderId, account);
        return new CharacterAppearance(account, raceId, genderId, skinId,
                template.getBodyTypeId(), template.getChestTypeId());
    }

    /** The button this handler added to that screen, while it is still open. */
    private LostTalesCharacterMenuButton labelledButtonFor(GuiScreen screen) {
        if (this.labelledScreen == null || this.labelledButton == null
                || this.labelledScreen.get() != screen) {
            return null;
        }
        return this.labelledButton.get();
    }

    private static GuiButton findButton(GuiScreenEvent.InitGuiEvent event,
                                        int id) {
        for (Object button : event.buttonList) {
            if (button instanceof GuiButton && ((GuiButton) button).id == id) {
                return (GuiButton) button;
            }
        }
        return null;
    }
}
