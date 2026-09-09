package com.ninuna.losttales.client.character.room;

import com.ninuna.losttales.client.character.CharacterTemplateStore;
import com.ninuna.losttales.client.character.LostTalesClientAccount;
import com.ninuna.losttales.client.gui.LostTalesButton;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

/**
 * The room menu: what the player can do from the character room once
 * the creator has been closed. Opened with the use key, and in place of
 * the pause menu.
 *
 * <p>Four things: open the creator again, go on to the singleplayer or
 * multiplayer menu, or return to the main menu. The play menus are
 * offered on the same rule as the main menu's own Singleplayer and
 * Multiplayer: only once the account's template is a character that can
 * be played, which the menu reads afresh each time it opens, so a save
 * made moments ago counts.</p>
 */
public final class CharacterRoomMenuGui extends GuiScreen {

    private static final int BUTTON_CHANGE = 1;
    private static final int BUTTON_SINGLEPLAYER = 2;
    private static final int BUTTON_MULTIPLAYER = 3;
    private static final int BUTTON_RETURN = 4;

    /** The menu's controls are the main menu's size, so they read as one family. */
    static final int BUTTON_WIDTH = 200;
    static final int BUTTON_HEIGHT = 20;
    static final int BUTTON_GAP = 4;
    /** Between the play controls and the rest, as the main menu spaces its rows. */
    static final int GROUP_GAP = BUTTON_GAP + 12;
    /** The panel's padding around the controls. */
    static final int PANEL_PADDING = 10;
    /** Room for the header above the controls. */
    static final int HEADER_HEIGHT = 30;
    /** Room for the note under a withheld play control. */
    static final int NOTE_HEIGHT = 12;

    private boolean templateSetUp;

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.templateSetUp = CharacterTemplateStore.load(
                LostTalesClientAccount.templateId()).isSetUp();
        int[] rows = rowTops(this.height, this.templateSetUp);
        int x = (this.width - BUTTON_WIDTH) / 2;
        this.buttonList.add(new LostTalesButton(BUTTON_CHANGE, x, rows[0],
                BUTTON_WIDTH, BUTTON_HEIGHT,
                I18n.format("gui.losttales.character.room.menu.change")));
        LostTalesButton singleplayer = new LostTalesButton(BUTTON_SINGLEPLAYER,
                x, rows[1], BUTTON_WIDTH, BUTTON_HEIGHT,
                I18n.format("menu.singleplayer"));
        LostTalesButton multiplayer = new LostTalesButton(BUTTON_MULTIPLAYER,
                x, rows[2], BUTTON_WIDTH, BUTTON_HEIGHT,
                I18n.format("menu.multiplayer"));
        singleplayer.enabled = this.templateSetUp;
        multiplayer.enabled = this.templateSetUp;
        this.buttonList.add(singleplayer);
        this.buttonList.add(multiplayer);
        this.buttonList.add(new LostTalesButton(BUTTON_RETURN, x, rows[3],
                BUTTON_WIDTH, BUTTON_HEIGHT,
                I18n.format("gui.losttales.character.room.menu.return")));
    }

    /**
     * The top of each row, in order: change, singleplayer, multiplayer,
     * return. The block of rows is centred on the screen, with room
     * above for the header and, when the play controls are withheld, a
     * line under them saying why.
     */
    static int[] rowTops(int screenHeight, boolean templateSetUp) {
        int note = templateSetUp ? 0 : NOTE_HEIGHT;
        int rowsHeight = BUTTON_HEIGHT * 4 + BUTTON_GAP * 2 + GROUP_GAP + note;
        int top = (screenHeight - (HEADER_HEIGHT + rowsHeight)) / 2 + HEADER_HEIGHT;
        int[] rows = new int[4];
        rows[0] = top;
        rows[1] = rows[0] + BUTTON_HEIGHT + BUTTON_GAP;
        rows[2] = rows[1] + BUTTON_HEIGHT + BUTTON_GAP;
        rows[3] = rows[2] + BUTTON_HEIGHT + note + GROUP_GAP;
        return rows;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled || this.mc == null) {
            return;
        }
        switch (button.id) {
            case BUTTON_CHANGE:
                CharacterRoomSession.openCreator(this.mc);
                return;
            case BUTTON_SINGLEPLAYER:
                this.mc.displayGuiScreen(new CharacterRoomLeaveScreen(
                        CharacterRoomLauncher.Destination.SINGLEPLAYER));
                return;
            case BUTTON_MULTIPLAYER:
                this.mc.displayGuiScreen(new CharacterRoomLeaveScreen(
                        CharacterRoomLauncher.Destination.MULTIPLAYER));
                return;
            case BUTTON_RETURN:
                this.mc.displayGuiScreen(new CharacterRoomLeaveScreen(
                        CharacterRoomLauncher.Destination.MAIN_MENU));
                return;
            default:
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        LostTalesSkyrimUiStyle.drawScreenShade(this.width, this.height);
        int[] rows = rowTops(this.height, this.templateSetUp);
        int panelWidth = BUTTON_WIDTH + PANEL_PADDING * 2;
        int panelTop = rows[0] - HEADER_HEIGHT - PANEL_PADDING;
        int panelBottom = rows[3] + BUTTON_HEIGHT + PANEL_PADDING;
        LostTalesSkyrimUiStyle.drawPanel((this.width - panelWidth) / 2, panelTop,
                panelWidth, panelBottom - panelTop);
        LostTalesSkyrimUiStyle.drawCenteredHeader(this.fontRendererObj,
                I18n.format("gui.losttales.character.room.menu.title"),
                I18n.format("gui.losttales.character.room.menu.subtitle"),
                this.width, rows[0] - HEADER_HEIGHT);
        if (!this.templateSetUp) {
            LostTalesSkyrimUiStyle.beginContent();
            drawCenteredString(this.fontRendererObj,
                    I18n.format("gui.losttales.character.room.menu.locked"),
                    this.width / 2, rows[2] + BUTTON_HEIGHT + 2,
                    LostTalesSkyrimUiStyle.TEXT_MUTED);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
