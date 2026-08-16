package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import java.util.List;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Vanilla chat input with a compact channel indicator and upward selector. */
public final class LostTalesChatGui extends GuiChat {
    private static final int INDICATOR_X = 2;
    private static final int INDICATOR_HEIGHT = 12;
    private static final int OPTION_HEIGHT = 12;

    private boolean selectorTargetOpen;
    private long selectorTransitionNanos;
    private final long openedAtNanos = System.nanoTime();
    private long copiedNoticeNanos;

    public LostTalesChatGui(String defaultText) {
        super(defaultText == null ? "" : defaultText);
    }

    @Override
    public void initGui() {
        ClientChatChannelState.ensureAvailable();
        super.initGui();
        this.inputField.setTextColor(LostTalesChatVisualStyle.IVORY);
        this.inputField.setDisabledTextColour(
                LostTalesChatVisualStyle.SHADOW);
        updateInputBounds();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        ChatChannel before = ClientChatChannelState.getSelected();
        ClientChatChannelState.ensureAvailable();
        if (before != ClientChatChannelState.getSelected()) {
            selectorTargetOpen = false;
            updateInputBounds();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_TAB
                && this.inputField.getText().length() == 0) {
            ClientChatChannelState.cycle();
            setSelectorOpen(false);
            updateInputBounds();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void func_146403_a(String text) {
        String message = text == null ? "" : text.trim();
        if (message.startsWith("/")) {
            super.func_146403_a(message);
            return;
        }
        if (message.length() == 0) {
            return;
        }
        this.mc.ingameGUI.getChatGUI().addToSentMessages(message);
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new LostTalesChatSendPacket(
                        ClientChatChannelState.getSelected(), message));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ClientChatChannelState.ensureAvailable();
        updateInputBounds();
        float offsetY = inputEntranceOffset();
        int adjustedMouseY = Math.round(mouseY - offsetY);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0.0F, offsetY, 0.0F);
            super.drawScreen(mouseX, adjustedMouseY, partialTicks);
            drawSelector(mouseX, adjustedMouseY);
            drawIndicator(mouseX, adjustedMouseY);
        } finally {
            GL11.glPopMatrix();
        }
        drawCopiedNotice();
        LostTalesChatHoverCard.draw(this.mc, mouseX, mouseY,
                this.width, this.height);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        int adjustedMouseY = Math.round(mouseY - inputEntranceOffset());
        if (button == 1 && LostTalesChatClipboard.copy(
                this.mc.ingameGUI.getChatGUI(), this.mc,
                Mouse.getX(), Mouse.getY(),
                LostTalesChatOverlayRenderer.getEntryDisplacement())) {
            this.copiedNoticeNanos = System.nanoTime();
            return;
        }
        IChatComponent component = this.mc.ingameGUI.getChatGUI()
                .func_146236_a(Mouse.getX(), Mouse.getY());
        if (button == 0 && isInsideIndicator(mouseX, adjustedMouseY)) {
            setSelectorOpen(!selectorTargetOpen);
            return;
        }
        if (button == 0 && selectorTargetOpen) {
            ChatChannel clicked = channelAt(mouseX, adjustedMouseY);
            if (clicked != null) {
                ClientChatChannelState.select(clicked);
                setSelectorOpen(false);
                updateInputBounds();
                this.inputField.setFocused(true);
                return;
            }
            setSelectorOpen(false);
        }
        if (ChatHeadMarker.isMarker(component)
                || ChatColorMarker.isMarker(component)) {
            return;
        }
        super.mouseClicked(mouseX, adjustedMouseY, button);
    }

    private void drawIndicator(int mouseX, int mouseY) {
        ChatChannel channel = ClientChatChannelState.getSelected();
        int width = indicatorWidth();
        int top = this.height - 14;
        boolean hovered = isInsideIndicator(mouseX, mouseY);
        drawRect(INDICATOR_X, top, INDICATOR_X + width,
                top + INDICATOR_HEIGHT,
                hovered ? 0xCC303030 : 0xB0181818);
        drawRect(INDICATOR_X, top, INDICATOR_X + 2,
                top + INDICATOR_HEIGHT,
                0xFF000000
                        | ClientChatChannelState.displayColor(channel));
        String label = indicatorLabel(channel);
        LostTalesChatVisualStyle.drawPlain(this.fontRendererObj,
                label, INDICATOR_X + 5, top + 2, 255);
    }

    private void drawSelector(int mouseX, int mouseY) {
        float progress = selectorProgress();
        if (progress <= 0.0F) {
            return;
        }
        List<ChatChannel> channels =
                ClientChatChannelState.getAvailableChannels();
        int width = selectorWidth(channels);
        int bottom = this.height - 15;
        for (int index = 0; index < channels.size(); index++) {
            ChatChannel channel = channels.get(index);
            float optionProgress = LostTalesChatMotion.stagger(
                    progress, index);
            int optionSlide = Math.round(
                    (1.0F - optionProgress) * 5.0F);
            int optionBottom = bottom - index * OPTION_HEIGHT
                    + optionSlide;
            int optionTop = optionBottom - OPTION_HEIGHT;
            boolean hovered = selectorTargetOpen
                    && mouseX >= INDICATOR_X
                    && mouseX < INDICATOR_X + width
                    && mouseY >= optionTop && mouseY < optionBottom;
            int optionAlpha = Math.max(0, Math.min(255,
                    Math.round(220.0F * optionProgress)));
            int background = (optionAlpha << 24)
                    | (hovered ? 0x383838 : 0x181818);
            drawRect(INDICATOR_X, optionTop,
                    INDICATOR_X + width, optionBottom, background);
            drawRect(INDICATOR_X, optionTop,
                    INDICATOR_X + 2, optionBottom,
                    (optionAlpha << 24)
                            | ClientChatChannelState.displayColor(channel));
            int textAlpha = Math.max(4, Math.min(255,
                    Math.round(255.0F * optionProgress)));
            LostTalesChatVisualStyle.drawPlain(this.fontRendererObj,
                    channel.getDisplayName(), INDICATOR_X + 5,
                    optionTop + 2, textAlpha);
        }
    }

    private ChatChannel channelAt(int mouseX, int mouseY) {
        List<ChatChannel> channels =
                ClientChatChannelState.getAvailableChannels();
        int width = selectorWidth(channels);
        if (mouseX < INDICATOR_X || mouseX >= INDICATOR_X + width) {
            return null;
        }
        int bottom = this.height - 15;
        for (int index = 0; index < channels.size(); index++) {
            int optionBottom = bottom - index * OPTION_HEIGHT;
            if (mouseY >= optionBottom - OPTION_HEIGHT
                    && mouseY < optionBottom) {
                return channels.get(index);
            }
        }
        return null;
    }

    private void setSelectorOpen(boolean open) {
        if (this.selectorTargetOpen != open) {
            this.selectorTargetOpen = open;
            this.selectorTransitionNanos = System.nanoTime();
        }
    }

    private float selectorProgress() {
        if (!LostTalesConfig.enableChatAnimations) {
            return selectorTargetOpen ? 1.0F : 0.0F;
        }
        long duration = Math.max(1,
                LostTalesConfig.chatSelectorAnimationDurationMillis)
                * 1000000L;
        float elapsed = Math.min(1.0F,
                (System.nanoTime() - selectorTransitionNanos)
                        / (float)duration);
        float eased = LostTalesChatMotion.menuProgress(elapsed);
        return selectorTargetOpen ? eased : 1.0F - eased;
    }

    private float inputEntranceOffset() {
        if (!LostTalesConfig.enableChatAnimations) {
            return 0.0F;
        }
        long duration = Math.max(1,
                LostTalesConfig.chatInputAnimationDurationMillis)
                * 1000000L;
        float progress = Math.max(0.0F, Math.min(1.0F,
                (System.nanoTime() - openedAtNanos) / (float)duration));
        return LostTalesChatMotion.inputOffset(progress);
    }

    private void drawCopiedNotice() {
        if (copiedNoticeNanos <= 0L) {
            return;
        }
        float ageMillis = (System.nanoTime() - copiedNoticeNanos)
                / 1000000.0F;
        float lifetime = 1400.0F;
        if (ageMillis >= lifetime) {
            copiedNoticeNanos = 0L;
            return;
        }
        float opacity = 1.0F;
        if (LostTalesConfig.enableChatAnimations) {
            opacity = Math.min(1.0F, ageMillis / 100.0F)
                    * Math.min(1.0F, (lifetime - ageMillis) / 250.0F);
        }
        int alpha = Math.max(4, Math.min(255,
                Math.round(255.0F * opacity)));
        String message = StatCollector.translateToLocal(
                "gui.losttales.chat.copied");
        int popupWidth = this.fontRendererObj.getStringWidth(message) + 10;
        int x = (this.width - popupWidth) / 2;
        int y = this.height - 31
                + Math.round((1.0F - opacity) * 3.0F);
        drawRect(x, y, x + popupWidth, y + 13,
                (Math.min(220, alpha) << 24) | 0x181818);
        LostTalesChatVisualStyle.drawPlain(this.fontRendererObj,
                message, x + 5, y + 2, alpha);
    }

    private boolean isInsideIndicator(int mouseX, int mouseY) {
        int top = this.height - 14;
        return mouseX >= INDICATOR_X
                && mouseX < INDICATOR_X + indicatorWidth()
                && mouseY >= top && mouseY < top + INDICATOR_HEIGHT;
    }

    private int indicatorWidth() {
        return this.fontRendererObj.getStringWidth(
                indicatorLabel(ClientChatChannelState.getSelected())) + 10;
    }

    private static String indicatorLabel(ChatChannel channel) {
        return "[" + channel.getDisplayName() + "]";
    }

    private int selectorWidth(List<ChatChannel> channels) {
        int width = indicatorWidth();
        for (ChatChannel channel : channels) {
            width = Math.max(width,
                    this.fontRendererObj.getStringWidth(
                            channel.getDisplayName()) + 10);
        }
        return width;
    }

    private void updateInputBounds() {
        if (this.inputField == null) {
            return;
        }
        int left = INDICATOR_X + indicatorWidth() + 3;
        this.inputField.xPosition = left;
        this.inputField.width = Math.max(20, this.width - left - 2);
    }
}
