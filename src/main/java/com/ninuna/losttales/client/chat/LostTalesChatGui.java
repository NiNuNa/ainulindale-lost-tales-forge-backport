package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatNameSuggester;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.emoji.ChatEmojiParser;
import com.ninuna.losttales.chat.emoji.ChatEmojiSuggester;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiPlayerInfo;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.Achievement;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
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
    private final ChatEmojiPicker emojiPicker = new ChatEmojiPicker();
    private final ChatEmojiSuggestionBox emojiSuggestions =
            new ChatEmojiSuggestionBox();
    private final ChatNameSuggestionBox nameSuggestions =
            new ChatNameSuggestionBox();
    private URI clickedLinkUri;

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
        emojiPicker.tick();
        ChatChannel before = ClientChatChannelState.getSelected();
        ClientChatChannelState.ensureAvailable();
        if (before != ClientChatChannelState.getSelected()) {
            selectorTargetOpen = false;
            updateInputBounds();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (LostTalesConfig.enableChatEmojis) {
            if (emojiPicker.handleKeyTyped(typedChar, keyCode)) {
                return;
            }
            emojiSuggestions.update(this.inputField.getText(),
                    this.inputField.getCursorPosition());
            if (emojiSuggestions.isActive()
                    && handleSuggestionKey(keyCode)) {
                return;
            }
        }
        if (LostTalesConfig.enableChatPings) {
            nameSuggestions.update(this.inputField.getText(),
                    this.inputField.getCursorPosition(),
                    collectMentionNames());
            if (nameSuggestions.isActive()
                    && handleNameSuggestionKey(keyCode)) {
                return;
            }
        }
        if (keyCode == Keyboard.KEY_TAB
                && this.inputField.getText().length() == 0) {
            ClientChatChannelState.cycle();
            setSelectorOpen(false);
            updateInputBounds();
            return;
        }
        super.keyTyped(typedChar, keyCode);
        if (LostTalesConfig.enableChatEmojis) {
            emojiSuggestions.update(this.inputField.getText(),
                    this.inputField.getCursorPosition());
        }
        if (LostTalesConfig.enableChatPings) {
            nameSuggestions.update(this.inputField.getText(),
                    this.inputField.getCursorPosition(),
                    collectMentionNames());
        }
    }

    /** Keys owned by the mention completion list while it is visible. */
    private boolean handleNameSuggestionKey(int keyCode) {
        if (keyCode == Keyboard.KEY_UP) {
            nameSuggestions.moveSelection(-1);
            return true;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            nameSuggestions.moveSelection(1);
            return true;
        }
        if (keyCode == Keyboard.KEY_TAB
                || keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            acceptNameSuggestion(nameSuggestions.getSelected());
            return true;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            nameSuggestions.dismiss();
            return true;
        }
        return false;
    }

    /** Replaces the {@code @prefix} at the cursor with the full mention. */
    private void acceptNameSuggestion(String name) {
        ChatNameSuggester.Query query = nameSuggestions.getQuery();
        if (name == null || query == null) {
            return;
        }
        String text = this.inputField.getText();
        int atIndex = Math.min(query.atIndex, text.length());
        int cursor = Math.max(atIndex, Math.min(
                this.inputField.getCursorPosition(), text.length()));
        String replacement = "@" + name + " ";
        this.inputField.setText(text.substring(0, atIndex)
                + replacement + text.substring(cursor));
        this.inputField.setCursorPosition(Math.min(
                this.inputField.getText().length(),
                atIndex + replacement.length()));
        nameSuggestions.update(this.inputField.getText(),
                this.inputField.getCursorPosition(),
                collectMentionNames());
    }

    /**
     * Online account names plus this player's own identities, so anyone
     * visible in the tab list can be mentioned and self-pings work.
     */
    private List<String> collectMentionNames() {
        List<String> names = new ArrayList<String>();
        if (this.mc.thePlayer == null) {
            return names;
        }
        names.add(this.mc.thePlayer.getCommandSenderName());
        CharacterRosterSnapshot snapshot =
                ClientCharacterRosterCache.getSnapshot();
        CharacterSummary active = snapshot == null
                ? null : snapshot.getActiveCharacter();
        if (active != null) {
            names.add(active.getName());
        }
        if (this.mc.thePlayer.sendQueue != null
                && this.mc.thePlayer.sendQueue.playerInfoList != null) {
            for (Object value
                    : this.mc.thePlayer.sendQueue.playerInfoList) {
                if (value instanceof GuiPlayerInfo) {
                    names.add(((GuiPlayerInfo)value).name);
                }
            }
        }
        return names;
    }

    /** Keys owned by the completion list while it is visible. */
    private boolean handleSuggestionKey(int keyCode) {
        if (keyCode == Keyboard.KEY_UP) {
            emojiSuggestions.moveSelection(-1);
            return true;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            emojiSuggestions.moveSelection(1);
            return true;
        }
        if (keyCode == Keyboard.KEY_TAB
                || keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            acceptSuggestion(emojiSuggestions.getSelected());
            return true;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            emojiSuggestions.dismiss();
            return true;
        }
        return false;
    }

    /** Replaces the {@code :prefix} at the cursor with the full shortcode. */
    private void acceptSuggestion(ChatEmoji emoji) {
        ChatEmojiSuggester.Query query = emojiSuggestions.getQuery();
        if (emoji == null || query == null) {
            return;
        }
        String text = this.inputField.getText();
        int colonIndex = Math.min(query.colonIndex, text.length());
        int cursor = Math.max(colonIndex, Math.min(
                this.inputField.getCursorPosition(), text.length()));
        String shortcode = emoji.getShortcode();
        this.inputField.setText(text.substring(0, colonIndex)
                + shortcode + text.substring(cursor));
        this.inputField.setCursorPosition(Math.min(
                this.inputField.getText().length(),
                colonIndex + shortcode.length()));
        emojiSuggestions.update(this.inputField.getText(),
                this.inputField.getCursorPosition());
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
        if (LostTalesConfig.enableChatEmojis) {
            for (ChatEmojiParser.Segment segment
                    : ChatEmojiParser.split(message)) {
                if (segment.isEmoji()) {
                    ChatEmojiUsageStore.recordUse(segment.getEmoji());
                }
            }
        }
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
            drawChatBody(mouseX, adjustedMouseY);
            drawSelector(mouseX, adjustedMouseY);
            drawIndicator(mouseX, adjustedMouseY);
            if (LostTalesConfig.enableChatEmojis) {
                emojiPicker.draw(this.mc, this.width, this.height,
                        mouseX, adjustedMouseY);
                emojiSuggestions.update(this.inputField.getText(),
                        this.inputField.getCursorPosition());
                emojiSuggestions.draw(this.mc, this.fontRendererObj,
                        this.height, this.inputField.xPosition,
                        mouseX, adjustedMouseY);
            }
            if (LostTalesConfig.enableChatPings) {
                nameSuggestions.update(this.inputField.getText(),
                        this.inputField.getCursorPosition(),
                        collectMentionNames());
                nameSuggestions.draw(this.fontRendererObj, this.height,
                        this.inputField.xPosition, mouseX,
                        adjustedMouseY);
            }
        } finally {
            GL11.glPopMatrix();
        }
        drawCopiedNotice();
        LostTalesChatHoverCard.draw(this.mc, mouseX, mouseY,
                this.width, this.height);
    }

    /**
     * Vanilla {@code GuiChat.drawScreen} with only the input bar colour
     * changed: the palette's plum black at the same half opacity as the
     * message backdrop, with no fade. Hover cards for item, text, and
     * achievement components are reproduced unchanged; GuiScreen's button
     * pass is skipped because this screen registers no buttons.
     */
    private void drawChatBody(int mouseX, int mouseY) {
        drawRect(2, this.height - 14, this.width - 2, this.height - 2,
                LostTalesSkyrimUiStyle.withAlpha(
                        LostTalesSkyrimUiStyle.PLUM_BLACK, 0x80));
        this.inputField.drawTextBox();
        LostTalesChatOverlayRenderer.Hit hovered =
                LostTalesChatOverlayRenderer.hitAt(
                        this.mc, Mouse.getX(), Mouse.getY());
        if (hovered != null && hovered.component.getChatStyle()
                .getChatHoverEvent() != null) {
            drawComponentHoverCard(hovered.component.getChatStyle()
                    .getChatHoverEvent(), mouseX, mouseY);
            GL11.glDisable(GL11.GL_LIGHTING);
        }
    }

    private void drawComponentHoverCard(HoverEvent hoverEvent,
                                        int mouseX, int mouseY) {
        if (hoverEvent.getAction() == HoverEvent.Action.SHOW_ITEM) {
            ItemStack stack = null;
            try {
                NBTBase nbt = JsonToNBT.func_150315_a(
                        hoverEvent.getValue().getUnformattedText());
                if (nbt instanceof NBTTagCompound) {
                    stack = ItemStack.loadItemStackFromNBT(
                            (NBTTagCompound)nbt);
                }
            } catch (NBTException ignored) {
            }
            if (stack != null) {
                this.renderToolTip(stack, mouseX, mouseY);
            } else {
                this.drawCreativeTabHoveringText(
                        EnumChatFormatting.RED + "Invalid Item!",
                        mouseX, mouseY);
            }
        } else if (hoverEvent.getAction() == HoverEvent.Action.SHOW_TEXT) {
            this.func_146283_a(Splitter.on("\n").splitToList(
                    hoverEvent.getValue().getFormattedText()),
                    mouseX, mouseY);
        } else if (hoverEvent.getAction()
                == HoverEvent.Action.SHOW_ACHIEVEMENT) {
            StatBase stat = StatList.func_151177_a(
                    hoverEvent.getValue().getUnformattedText());
            if (stat != null) {
                IChatComponent title = stat.func_150951_e();
                ChatComponentTranslation type =
                        new ChatComponentTranslation("stats.tooltip.type."
                                + (stat.isAchievement()
                                        ? "achievement" : "statistic"));
                type.getChatStyle().setItalic(Boolean.TRUE);
                String description = stat instanceof Achievement
                        ? ((Achievement)stat).getDescription() : null;
                ArrayList<String> lines = Lists.newArrayList(
                        title.getFormattedText(),
                        type.getFormattedText());
                if (description != null) {
                    @SuppressWarnings("unchecked")
                    List<String> wrapped = this.fontRendererObj
                            .listFormattedStringToWidth(description, 150);
                    lines.addAll(wrapped);
                }
                this.func_146283_a(lines, mouseX, mouseY);
            } else {
                this.drawCreativeTabHoveringText(EnumChatFormatting.RED
                        + "Invalid statistic/achievement!",
                        mouseX, mouseY);
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        int adjustedMouseY = Math.round(mouseY - inputEntranceOffset());
        if (LostTalesConfig.enableChatEmojis && button == 0
                && emojiSuggestions.isActive()) {
            ChatEmoji suggested = emojiSuggestions.suggestionAt(
                    this.fontRendererObj, mouseX, adjustedMouseY,
                    this.height, this.inputField.xPosition);
            if (suggested != null) {
                acceptSuggestion(suggested);
                return;
            }
        }
        if (LostTalesConfig.enableChatPings && button == 0
                && nameSuggestions.isActive()) {
            String suggestedName = nameSuggestions.suggestionAt(
                    this.fontRendererObj, mouseX, adjustedMouseY,
                    this.height, this.inputField.xPosition);
            if (suggestedName != null) {
                acceptNameSuggestion(suggestedName);
                return;
            }
        }
        if (LostTalesConfig.enableChatEmojis && emojiPicker.isOpen()
                && emojiPicker.isInsidePanel(mouseX, adjustedMouseY,
                        this.width, this.height)) {
            emojiPicker.mouseClicked(mouseX, adjustedMouseY, button,
                    this.width, this.height);
            if (button == 0) {
                ChatEmoji selected = emojiPicker.emojiAt(
                        mouseX, adjustedMouseY, this.width, this.height);
                if (selected != null) {
                    this.inputField.writeText(selected.getShortcode());
                }
            } else if (button == 1) {
                emojiPicker.toggleFavoriteAt(
                        mouseX, adjustedMouseY, this.width, this.height);
            }
            return;
        }
        if (button == 1 && LostTalesChatClipboard.copy(
                this.mc.ingameGUI.getChatGUI(), this.mc,
                Mouse.getX(), Mouse.getY(),
                LostTalesChatOverlayRenderer.getEntryDisplacement())) {
            this.copiedNoticeNanos = System.nanoTime();
            return;
        }
        if (button == 0 && isInsideIndicator(mouseX, adjustedMouseY)) {
            setSelectorOpen(!selectorTargetOpen);
            emojiPicker.setOpen(false);
            return;
        }
        if (LostTalesConfig.enableChatEmojis && button == 0
                && emojiPicker.isInsideButton(mouseX, adjustedMouseY,
                        this.width, this.height)) {
            emojiPicker.setOpen(!emojiPicker.isOpen());
            setSelectorOpen(false);
            return;
        }
        if (button == 0 && emojiPicker.isOpen()) {
            // Clicks inside the panel were consumed above; anything else
            // closes the picker and is processed normally.
            emojiPicker.setOpen(false);
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
        if (button == 0 && handleComponentClick(
                LostTalesChatOverlayRenderer.hitAt(
                        this.mc, Mouse.getX(), Mouse.getY()))) {
            return;
        }
        // GuiChat's own component handling relies on GuiNewChat's 9px hit
        // testing, which no longer matches the 12px layout; only the input
        // field still needs the vanilla click path.
        this.inputField.mouseClicked(mouseX, adjustedMouseY, button);
    }

    /**
     * Vanilla component-click behaviour resolved against this mod's line
     * layout. The head marker acts as the sender like the visible name
     * does; colour and emote markers are internal metadata and never a
     * user-facing action.
     */
    private boolean handleComponentClick(
            LostTalesChatOverlayRenderer.Hit hit) {
        if (hit == null || !this.mc.gameSettings.chatLinks) {
            return false;
        }
        if (ChatHeadMarker.isMarker(hit.component)) {
            ClickEvent reply = findReplySuggestion(hit.line);
            if (reply != null) {
                this.inputField.setText(reply.getValue());
            }
            return true;
        }
        if (ChatColorMarker.isMarker(hit.component)
                || ChatEmojiMarker.isMarker(hit.component)) {
            return true;
        }
        ClickEvent event =
                hit.component.getChatStyle().getChatClickEvent();
        if (event == null) {
            return false;
        }
        if (isShiftKeyDown()) {
            this.inputField.writeText(
                    hit.component.getUnformattedTextForChat());
            return true;
        }
        if (event.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
            this.inputField.setText(event.getValue());
        } else if (event.getAction() == ClickEvent.Action.RUN_COMMAND) {
            this.func_146403_a(event.getValue());
        } else if (event.getAction() == ClickEvent.Action.OPEN_URL) {
            openChatLink(event.getValue());
        }
        return true;
    }

    /** The line's {@code /msg} suggestion, shared by name and head. */
    private static ClickEvent findReplySuggestion(IChatComponent line) {
        if (line == null) {
            return null;
        }
        for (Object value : line) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            IChatComponent part = (IChatComponent)value;
            ClickEvent event = part.getChatStyle() == null
                    ? null : part.getChatStyle().getChatClickEvent();
            if (event != null
                    && event.getAction() == ClickEvent.Action.SUGGEST_COMMAND
                    && event.getValue() != null
                    && event.getValue().startsWith("/msg ")) {
                return event;
            }
        }
        return null;
    }

    private void openChatLink(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? ""
                    : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return;
            }
            if (this.mc.gameSettings.chatLinksPrompt) {
                this.clickedLinkUri = uri;
                this.mc.displayGuiScreen(
                        new GuiConfirmOpenLink(this, value, 0, false));
            } else {
                browseTo(uri);
            }
        } catch (URISyntaxException ignored) {
        }
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        if (id == 0) {
            if (result && this.clickedLinkUri != null) {
                browseTo(this.clickedLinkUri);
            }
            this.clickedLinkUri = null;
            this.mc.displayGuiScreen(this);
            return;
        }
        super.confirmClicked(result, id);
    }

    /** Vanilla's reflective desktop-browse, minus the private plumbing. */
    private static void browseTo(URI uri) {
        try {
            Class<?> desktop = Class.forName("java.awt.Desktop");
            Object instance = desktop.getMethod("getDesktop").invoke(null);
            desktop.getMethod("browse", URI.class).invoke(instance, uri);
        } catch (Throwable ignored) {
        }
    }

    private void drawIndicator(int mouseX, int mouseY) {
        ChatChannel channel = ClientChatChannelState.getSelected();
        int width = indicatorWidth();
        int top = this.height - 14;
        boolean hovered = isInsideIndicator(mouseX, mouseY);
        drawRect(INDICATOR_X, top, INDICATOR_X + width,
                top + INDICATOR_HEIGHT,
                hovered ? LostTalesChatVisualStyle.SURFACE_HOVER
                        : LostTalesChatVisualStyle.SURFACE);
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
                    | (hovered
                            ? LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB
                            : LostTalesChatVisualStyle.SURFACE_RGB);
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
                (Math.min(220, alpha) << 24)
                        | LostTalesChatVisualStyle.SURFACE_RGB);
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
        int right = LostTalesConfig.enableChatEmojis
                ? ChatEmojiPicker.buttonLeft(this.width) - 3
                : this.width - 2;
        this.inputField.xPosition = left;
        this.inputField.width = Math.max(20, right - left);
    }
}
