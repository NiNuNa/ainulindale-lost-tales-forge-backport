package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.fellowship.model.FellowshipMark;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiTextField;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

/**
 * Names a fellowship's mark before it is placed: one field, Place and
 * Cancel. It decides nothing; the server checks the name again and places
 * the mark.
 */
@SideOnly(Side.CLIENT)
final class LostTalesMapMarkPrompt {
    private static final int MAX_WIDTH = 260;
    private static final int PANEL_HEIGHT = 82;
    private static final int SCREEN_MARGIN = 8;
    private static final int CONTENT_PADDING = 10;
    private static final int TITLE_TOP = 8;
    private static final int COUNT_TOP = 19;
    private static final int FIELD_TOP = 34;
    private static final int FIELD_HEIGHT = 16;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_BOTTOM_MARGIN = 8;

    enum Action {
        NONE,
        PLACE,
        CANCEL
    }

    private final String fellowshipName;
    private final int marksHeld;
    private final LostTalesUiTextField nameField;

    LostTalesMapMarkPrompt(FontRenderer font, int screenWidth,
                           int screenHeight, String fellowshipName,
                           int marksHeld) {
        this.fellowshipName = fellowshipName == null ? "" : fellowshipName;
        this.marksHeld = marksHeld;
        Layout layout = calculateLayout(screenWidth, screenHeight);
        this.nameField = new LostTalesUiTextField(font,
                layout.field.x, layout.field.y,
                layout.field.width, layout.field.height);
        this.nameField.setEnableBackgroundDrawing(true);
        this.nameField.setMaxStringLength(FellowshipMark.MAX_NAME_LENGTH);
        this.nameField.setFocused(true);
    }

    String getName() {
        return this.nameField.getText() == null
                ? "" : this.nameField.getText().trim();
    }

    boolean canPlace() {
        return FellowshipMark.isValidName(getName());
    }

    void render(int screenWidth, int screenHeight,
                int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.fontRenderer == null) {
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        LostTalesMapPopupAnimation.begin(this);
        Layout layout = calculateLayout(screenWidth, screenHeight);
        int pivotX = layout.x + layout.width / 2;
        int pivotY = layout.y + layout.height / 2;
        int localMouseX = LostTalesMapPopupAnimation.inverseMouseX(
                this, mouseX, pivotX);
        int localMouseY = LostTalesMapPopupAnimation.inverseMouseY(
                this, mouseY, pivotY);
        LostTalesMapChoicePrompt.renderShadeFixed(
                screenWidth, screenHeight, null);
        LostTalesMapPopupAnimation.push(this, pivotX, pivotY);
        try {
            LostTalesSkyrimUiStyle.drawPanel(
                    layout.x, layout.y, layout.width, layout.height);
            drawCentered(font, layout,
                    I18n.format("gui.losttales.map.mark.title",
                            this.fellowshipName),
                    layout.y + TITLE_TOP, LostTalesSkyrimUiStyle.TEXT_BRIGHT);
            drawCentered(font, layout,
                    I18n.format("gui.losttales.map.mark.held",
                            Integer.valueOf(this.marksHeld),
                            Integer.valueOf(FellowshipMark.MAX_PER_FELLOWSHIP)),
                    layout.y + COUNT_TOP, LostTalesSkyrimUiStyle.TEXT_MUTED);
            this.nameField.drawTextBox();
            if (getName().length() == 0) {
                String hint = LostTalesSkyrimUiStyle.trimToWidth(font,
                        I18n.format("gui.losttales.map.mark.name_hint"),
                        Math.max(0, layout.field.width - 10));
                font.drawString(hint, layout.field.x + 5,
                        layout.field.y + (layout.field.height
                                - font.FONT_HEIGHT) / 2 + 1,
                        LostTalesSkyrimUiStyle.TEXT_DIM);
            }
            LostTalesMapChoicePrompt.drawButton(font, layout.place,
                    I18n.format("gui.losttales.map.mark.place"),
                    layout.place.contains(localMouseX, localMouseY),
                    canPlace());
            LostTalesMapChoicePrompt.drawButton(font, layout.cancel,
                    I18n.format("gui.losttales.map.waypoint.cancel"),
                    layout.cancel.contains(localMouseX, localMouseY), true);
        } finally {
            LostTalesMapPopupAnimation.pop();
        }
    }

    Action mouseClicked(int screenWidth, int screenHeight,
                        int mouseX, int mouseY, int button) {
        Layout layout = calculateLayout(screenWidth, screenHeight);
        mouseX = LostTalesMapPopupAnimation.inverseMouseX(
                this, mouseX, layout.x + layout.width / 2);
        mouseY = LostTalesMapPopupAnimation.inverseMouseY(
                this, mouseY, layout.y + layout.height / 2);
        this.nameField.mouseClicked(mouseX, mouseY, button);
        if (button != 0) {
            return Action.NONE;
        }
        if (layout.cancel.contains(mouseX, mouseY)) {
            return Action.CANCEL;
        }
        return layout.place.contains(mouseX, mouseY) && canPlace()
                ? Action.PLACE : Action.NONE;
    }

    /** Whether a click here would land on something: the field, or a button that acts. */
    boolean isPointerOverAction(int screenWidth, int screenHeight,
                                int mouseX, int mouseY) {
        Layout layout = calculateLayout(screenWidth, screenHeight);
        mouseX = LostTalesMapPopupAnimation.inverseMouseX(
                this, mouseX, layout.x + layout.width / 2);
        mouseY = LostTalesMapPopupAnimation.inverseMouseY(
                this, mouseY, layout.y + layout.height / 2);
        return layout.field.contains(mouseX, mouseY)
                || layout.cancel.contains(mouseX, mouseY)
                || layout.place.contains(mouseX, mouseY) && canPlace();
    }

    /** Every key but Return and Escape is typed into the name. */
    Action keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            return Action.CANCEL;
        }
        if (keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            return canPlace() ? Action.PLACE : Action.NONE;
        }
        this.nameField.textboxKeyTyped(typedChar, keyCode);
        return Action.NONE;
    }

    void updateCursor() {
        this.nameField.updateCursorCounter();
    }

    private static void drawCentered(FontRenderer font, Layout layout,
                                     String text, int y, int color) {
        String visible = LostTalesSkyrimUiStyle.trimToWidth(font, text,
                Math.max(0, layout.width - CONTENT_PADDING * 2));
        font.drawStringWithShadow(visible,
                layout.x + (layout.width - font.getStringWidth(visible)) / 2,
                y, color);
    }

    private static Layout calculateLayout(int screenWidth, int screenHeight) {
        int width = Math.min(MAX_WIDTH,
                Math.max(0, screenWidth - SCREEN_MARGIN * 2));
        int height = Math.min(PANEL_HEIGHT,
                Math.max(0, screenHeight - SCREEN_MARGIN * 2));
        int x = Math.max(0, (screenWidth - width) / 2);
        int y = Math.max(0, (screenHeight - height) / 2);
        int contentWidth = Math.max(0, width - CONTENT_PADDING * 2);
        LostTalesMapChoicePrompt.Bounds field = new LostTalesMapChoicePrompt.Bounds(
                x + CONTENT_PADDING, y + Math.min(FIELD_TOP, height),
                contentWidth, Math.min(FIELD_HEIGHT,
                        Math.max(0, height - FIELD_TOP)));
        int buttonY = Math.max(y,
                y + height - BUTTON_BOTTOM_MARGIN - BUTTON_HEIGHT);
        int buttonHeight = Math.max(0,
                y + height - BUTTON_BOTTOM_MARGIN - buttonY);
        int half = contentWidth / 2;
        LostTalesMapChoicePrompt.Bounds place = new LostTalesMapChoicePrompt.Bounds(
                x + CONTENT_PADDING, buttonY, half, buttonHeight);
        LostTalesMapChoicePrompt.Bounds cancel = new LostTalesMapChoicePrompt.Bounds(
                x + CONTENT_PADDING + half, buttonY,
                contentWidth - half, buttonHeight);
        return new Layout(x, y, width, height, field, place, cancel);
    }

    private static final class Layout {
        final int x;
        final int y;
        final int width;
        final int height;
        final LostTalesMapChoicePrompt.Bounds field;
        final LostTalesMapChoicePrompt.Bounds place;
        final LostTalesMapChoicePrompt.Bounds cancel;

        Layout(int x, int y, int width, int height,
               LostTalesMapChoicePrompt.Bounds field,
               LostTalesMapChoicePrompt.Bounds place,
               LostTalesMapChoicePrompt.Bounds cancel) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.field = field;
            this.place = place;
            this.cancel = cancel;
        }
    }
}
