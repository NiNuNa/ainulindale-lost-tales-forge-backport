package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatIdentityType;
import com.ninuna.losttales.chat.ChatMentionCandidate;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatNameSuggester;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareReference;
import com.ninuna.losttales.chat.share.ChatShareSuggester;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.mapmarker.LostTalesLotrMapGui;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiNewChat;
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

/**
 * Vanilla chat input with a compact channel indicator, folder-style channel
 * tabs on top of the history, per-channel message views, and pickers and
 * completion popups for emotes, items, and map markers. Every overlay
 * registers the rectangle it draws in {@link ChatPointerRegions}; hover,
 * tooltip, and click handling consult that record before touching the
 * message stack, so whatever is painted on top is also what owns the
 * pointer.
 */
public final class LostTalesChatGui extends GuiChat {
    private static final int INDICATOR_X = 2;
    private static final int INDICATOR_HEIGHT = 12;
    /** Mention candidates are rebuilt at most this often while typing. */
    private static final long MENTION_REFRESH_NANOS = 500L * 1000000L;
    private static final float NOTICE_LIFETIME_MILLIS = 1400.0F;

    private final long openedAtNanos = System.nanoTime();
    private long noticeNanos;
    private String noticeText = "";
    private final ChatPointerRegions regions = new ChatPointerRegions();
    private final ChatEmojiPicker emojiPicker = new ChatEmojiPicker();
    private final ChatItemPicker itemPicker = new ChatItemPicker();
    private final ChatMapMarkerPicker markerPicker = new ChatMapMarkerPicker();
    private final ChatPickerPanel[] pickers = new ChatPickerPanel[] {
            this.emojiPicker, this.itemPicker, this.markerPicker};
    private final ChatEmojiSuggestionBox emojiSuggestions =
            new ChatEmojiSuggestionBox();
    private final ChatNameSuggestionBox nameSuggestions =
            new ChatNameSuggestionBox();
    private final ChatShareSuggestionBox shareSuggestions =
            new ChatShareSuggestionBox();
    private final ChatChannelTabBar tabBar = new ChatChannelTabBar();
    private URI clickedLinkUri;
    private List<ChatMentionCandidate> mentionCandidates =
            Collections.emptyList();
    private int mentionRevision;
    private long mentionBuiltNanos;
    private ChatChannel mentionChannel;
    private ChatChannel lastSelected;
    /** Refreshed once per tick/frame; tabs and hit tests share it. */
    private List<ChatChannel> availableChannels = Collections.emptyList();
    private boolean openAnimationStarted;

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
        // Share tokens do not count toward the visible limit, so the field
        // must accept the longer raw form; sending re-checks it.
        this.inputField.setMaxStringLength(
                ChatMessageValidator.MAX_RAW_CHARACTERS);
        // Buttons sit right to left: emotes, items, markers.
        int index = 0;
        if (LostTalesConfig.enableChatEmojis) {
            this.emojiPicker.setButtonIndex(index++);
        }
        this.itemPicker.setButtonIndex(index++);
        this.markerPicker.setButtonIndex(index);
        updateInputBounds();
        ClientChatChannelViews.noteView(ClientChatChannelState.getSelected());
        this.lastSelected = ClientChatChannelState.getSelected();
        syncSelection();
        // initGui also runs on resize; the entrance only plays once per
        // opening, timed from the same instant as the input bar's.
        if (!this.openAnimationStarted) {
            this.openAnimationStarted = true;
            ClientChatChannelViews.noteOpened();
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        for (ChatPickerPanel picker : this.pickers) {
            picker.tick();
        }
        ClientChatChannelState.ensureAvailable();
        syncSelection();
    }

    /** Reacts to any selection change, whatever path caused it. */
    private void syncSelection() {
        this.availableChannels = ClientChatChannelState.getAvailableChannels();
        ChatChannel selected = ClientChatChannelState.getSelected();
        if (selected != this.lastSelected) {
            this.lastSelected = selected;
            ClientChatChannelViews.noteView(selected);
            updateInputBounds();
        }
        ClientChatChannelViews.markViewed(selected);
    }

    private ChatPickerPanel openPicker() {
        for (ChatPickerPanel picker : this.pickers) {
            if (picker.isOpen()) {
                return picker;
            }
        }
        return null;
    }

    private void closePickers() {
        for (ChatPickerPanel picker : this.pickers) {
            picker.setOpen(false);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        ChatPickerPanel picker = openPicker();
        if (picker != null && picker.handleKeyTyped(typedChar, keyCode)) {
            return;
        }
        if (LostTalesConfig.enableChatEmojis) {
            emojiSuggestions.update(this.inputField.getText(),
                    this.inputField.getCursorPosition());
            if (emojiSuggestions.isActive()
                    && handleSuggestionKey(keyCode)) {
                return;
            }
        }
        if (LostTalesConfig.enableChatPings) {
            refreshNameSuggestions();
            if (nameSuggestions.isActive()
                    && handleNameSuggestionKey(keyCode)) {
                return;
            }
        }
        refreshShareSuggestions();
        if (shareSuggestions.isActive() && handleShareSuggestionKey(keyCode)) {
            return;
        }
        if (keyCode == Keyboard.KEY_TAB
                && this.inputField.getText().length() == 0) {
            ClientChatChannelState.cycle();
            syncSelection();
            return;
        }
        if ((keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER)
                && refuseUnsendableMessage()) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
        if (LostTalesConfig.enableChatEmojis) {
            emojiSuggestions.update(this.inputField.getText(),
                    this.inputField.getCursorPosition());
        }
        if (LostTalesConfig.enableChatPings) {
            refreshNameSuggestions();
        }
        refreshShareSuggestions();
    }

    /**
     * Vanilla closes the screen as soon as Enter is pressed, which would
     * discard a message that cannot go out; refusing here keeps the text so
     * the player can shorten it or switch channel. Commands keep their own
     * limits.
     */
    private boolean refuseUnsendableMessage() {
        String message = this.inputField.getText().trim();
        if (message.length() == 0 || message.startsWith("/")) {
            return false;
        }
        if (!ClientChatChannelState.canSend(
                ClientChatChannelState.getSelected())) {
            showNotice(StatCollector.translateToLocal(
                    "gui.losttales.chat.global_requires_character"));
            return true;
        }
        if (ChatMessageValidator.isValid(message)) {
            return false;
        }
        showNotice(StatCollector.translateToLocalFormatted(
                "gui.losttales.chat.too_long",
                Integer.valueOf(ChatMessageValidator.visibleLength(message)),
                Integer.valueOf(ChatMessageValidator.MAX_CHARACTERS)));
        return true;
    }

    private void refreshNameSuggestions() {
        nameSuggestions.update(this.inputField.getText(),
                this.inputField.getCursorPosition(),
                mentionCandidates(), this.mentionRevision);
    }

    private void refreshShareSuggestions() {
        shareSuggestions.update(this.inputField.getText(),
                this.inputField.getCursorPosition(), this.mc.thePlayer);
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
    private void acceptNameSuggestion(ChatMentionCandidate candidate) {
        ChatNameSuggester.Query query = nameSuggestions.getQuery();
        if (candidate == null || query == null) {
            return;
        }
        replaceAtCursor(query.atIndex, "@" + candidate.getDisplayName() + " ");
        refreshNameSuggestions();
    }

    /** Keys owned by the share completion list while it is visible. */
    private boolean handleShareSuggestionKey(int keyCode) {
        if (keyCode == Keyboard.KEY_UP) {
            shareSuggestions.moveSelection(-1);
            return true;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            shareSuggestions.moveSelection(1);
            return true;
        }
        if (keyCode == Keyboard.KEY_TAB
                || keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            acceptShareSuggestion(shareSuggestions.getSelected());
            return true;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            shareSuggestions.dismiss();
            return true;
        }
        return false;
    }

    /** Replaces the open share opener at the cursor with a full token. */
    private void acceptShareSuggestion(ChatShareCandidates.Entry entry) {
        ChatShareSuggester.Query query = shareSuggestions.getQuery();
        if (entry == null || query == null) {
            return;
        }
        replaceAtCursor(query.openIndex, entry.token() + " ");
        refreshShareSuggestions();
    }

    /** Replaces the text from {@code from} to the cursor with a completion. */
    private void replaceAtCursor(int from, String replacement) {
        String text = this.inputField.getText();
        int start = Math.max(0, Math.min(from, text.length()));
        int cursor = Math.max(start, Math.min(
                this.inputField.getCursorPosition(), text.length()));
        this.inputField.setText(text.substring(0, start)
                + replacement + text.substring(cursor));
        this.inputField.setCursorPosition(Math.min(
                this.inputField.getText().length(),
                start + replacement.length()));
    }

    /**
     * One candidate per online player, shaped for the selected channel:
     * OOC displays and inserts the account name, role-play channels the
     * active character name; both names remain searchable aliases. The
     * stable key is the player's UUID from the appearance sync where one is
     * known, so an account and its character never appear as two entries.
     * Rebuilt on an interval, not per keystroke or frame.
     */
    private List<ChatMentionCandidate> mentionCandidates() {
        ChatChannel channel = ClientChatChannelState.getSelected();
        long now = System.nanoTime();
        if (channel == this.mentionChannel && this.mentionBuiltNanos != 0L
                && now - this.mentionBuiltNanos < MENTION_REFRESH_NANOS) {
            return this.mentionCandidates;
        }
        this.mentionBuiltNanos = now;
        this.mentionChannel = channel;
        List<ChatMentionCandidate> built = buildMentionCandidates(channel);
        if (!sameCandidates(built, this.mentionCandidates)) {
            this.mentionCandidates = built;
            this.mentionRevision++;
        }
        return this.mentionCandidates;
    }

    private List<ChatMentionCandidate> buildMentionCandidates(
            ChatChannel channel) {
        List<ChatMentionCandidate> result =
                new ArrayList<ChatMentionCandidate>();
        if (this.mc.thePlayer == null) {
            return result;
        }
        boolean accountIdentity =
                channel.getIdentityType() == ChatIdentityType.ACCOUNT;
        Map<String, CharacterAppearance> byAccount =
                new HashMap<String, CharacterAppearance>();
        for (CharacterAppearance appearance
                : ClientCharacterAppearanceCache.snapshot().values()) {
            if (appearance != null && appearance.isPresent()
                    && appearance.getAccountName().length() > 0) {
                byAccount.put(appearance.getAccountName()
                        .toLowerCase(Locale.ROOT), appearance);
            }
        }

        String selfAccount = this.mc.thePlayer.getCommandSenderName();
        CharacterRosterSnapshot snapshot =
                ClientCharacterRosterCache.getSnapshot();
        CharacterSummary active = snapshot == null
                ? null : snapshot.getActiveCharacter();
        String selfCharacter = active == null ? "" : active.getName();
        UUID selfId = this.mc.thePlayer.getUniqueID();
        result.add(candidate(selfId == null ? "self" : selfId.toString(),
                selfAccount, selfCharacter, accountIdentity));

        List<ChatMentionCandidate> others =
                new ArrayList<ChatMentionCandidate>();
        if (this.mc.thePlayer.sendQueue != null
                && this.mc.thePlayer.sendQueue.playerInfoList != null) {
            for (Object value
                    : this.mc.thePlayer.sendQueue.playerInfoList) {
                if (!(value instanceof GuiPlayerInfo)) {
                    continue;
                }
                String account = ((GuiPlayerInfo)value).name;
                if (account == null || account.trim().length() == 0
                        || account.equalsIgnoreCase(selfAccount)) {
                    continue;
                }
                CharacterAppearance appearance = byAccount.get(
                        account.toLowerCase(Locale.ROOT));
                String key = appearance == null
                        ? "account:" + account.toLowerCase(Locale.ROOT)
                        : appearance.getPlayerId().toString();
                others.add(candidate(key, account, appearance == null
                        ? "" : appearance.getCharacterName(),
                        accountIdentity));
            }
        }
        Collections.sort(others, new Comparator<ChatMentionCandidate>() {
            @Override
            public int compare(ChatMentionCandidate left,
                               ChatMentionCandidate right) {
                return left.getDisplayName().compareToIgnoreCase(
                        right.getDisplayName());
            }
        });
        result.addAll(others);
        return result;
    }

    private static ChatMentionCandidate candidate(
            String key, String account, String character,
            boolean accountIdentity) {
        String display = accountIdentity || character == null
                || character.trim().length() == 0 ? account : character;
        return new ChatMentionCandidate(key, display, account, character,
                Arrays.asList(account, character));
    }

    private static boolean sameCandidates(List<ChatMentionCandidate> left,
                                          List<ChatMentionCandidate> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            ChatMentionCandidate a = left.get(index);
            ChatMentionCandidate b = right.get(index);
            if (!a.getKey().equals(b.getKey())
                    || !a.getDisplayName().equals(b.getDisplayName())
                    || !a.getAliases().equals(b.getAliases())) {
                return false;
            }
        }
        return true;
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
        replaceAtCursor(query.colonIndex, emoji.getShortcode());
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
        if (message.length() == 0
                || !ChatMessageValidator.isValid(message)
                || !ClientChatChannelState.canSend(
                        ClientChatChannelState.getSelected())) {
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
                        ClientChatChannelState.getSelected(), message,
                        resolveShareReferences(message)));
    }

    /**
     * Resolves each share token to the reference the server will re-check:
     * an item token to the n-th slot currently holding a stack with that
     * display name, a marker token to the id of the n-th visible marker
     * with that name, scanning in the same order the pickers list them.
     * Only slot indices and ids leave the client.
     */
    private List<ChatShareReference> resolveShareReferences(String message) {
        List<ChatShareTokenParser.Token> tokens =
                ChatShareTokenParser.parse(message);
        if (tokens.isEmpty()) {
            return null;
        }
        List<ChatShareCandidates.ItemEntry> items =
                ChatShareCandidates.items(this.mc.thePlayer);
        List<ChatShareCandidates.MarkerEntry> markers =
                ChatShareCandidates.markers();
        List<ChatShareReference> references =
                new ArrayList<ChatShareReference>(tokens.size());
        for (ChatShareTokenParser.Token token : tokens) {
            ChatShareReference reference =
                    ChatShareReference.unresolved(token.kind);
            if (token.kind == ChatShareKind.ITEM) {
                for (ChatShareCandidates.ItemEntry entry : items) {
                    if (entry.matchesToken(token)) {
                        reference = ChatShareReference.item(entry.slot);
                        break;
                    }
                }
            } else {
                for (ChatShareCandidates.MarkerEntry entry : markers) {
                    if (entry.matchesToken(token)) {
                        reference = ChatShareReference.marker(
                                entry.marker.getId());
                        break;
                    }
                }
            }
            references.add(reference);
        }
        return references;
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        // Vanilla scrolled its own (now unused) offset above; the visible
        // history scrolls per channel view instead, with vanilla's step.
        int step = wheel > 0 ? 1 : -1;
        if (!isShiftKeyDown()) {
            step *= 7;
        }
        try {
            GuiNewChat chat = this.mc.ingameGUI.getChatGUI();
            List<net.minecraft.client.gui.ChatLine> lines =
                    LostTalesChatOverlayRenderer.getViewLines(chat);
            ClientChatChannelViews.scroll(
                    ClientChatChannelState.getSelected(), step,
                    lines.size(),
                    LostTalesChatOverlayRenderer.visibleLineCount(chat));
        } catch (IllegalAccessException ignored) {
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ClientChatChannelState.ensureAvailable();
        syncSelection();
        updateInputBounds();
        this.regions.reset();
        this.itemPicker.refresh(this.mc.thePlayer);
        this.markerPicker.refresh();
        float offsetY = inputEntranceOffset();
        int adjustedMouseY = Math.round(mouseY - offsetY);
        GuiNewChat chat = this.mc.ingameGUI.getChatGUI();
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0.0F, offsetY, 0.0F);
            drawInputBar();
            // Tabs only exist once there is a history to stand on; an empty
            // view keeps Tab and the indicator for switching.
            if (LostTalesChatOverlayRenderer.hasDrawnLines()) {
                this.tabBar.draw(this.fontRendererObj, this.regions,
                        this.availableChannels,
                        ClientChatChannelState.getSelected(),
                        this.width,
                        LostTalesChatOverlayRenderer.historyTop(
                                chat, this.height),
                        mouseX, adjustedMouseY,
                        ClientChatChannelViews.openSample().getOpacity());
            }
            drawIndicator(mouseX, adjustedMouseY);
            for (ChatPickerPanel picker : this.pickers) {
                if (picker == this.emojiPicker
                        && !LostTalesConfig.enableChatEmojis) {
                    continue;
                }
                picker.draw(this.mc, this.regions, this.width, this.height,
                        mouseX, adjustedMouseY);
            }
            if (LostTalesConfig.enableChatEmojis) {
                emojiSuggestions.update(this.inputField.getText(),
                        this.inputField.getCursorPosition());
                emojiSuggestions.draw(this.mc, this.fontRendererObj,
                        this.regions, this.height,
                        this.inputField.xPosition, mouseX, adjustedMouseY);
            }
            if (LostTalesConfig.enableChatPings) {
                refreshNameSuggestions();
                nameSuggestions.draw(this.fontRendererObj, this.regions,
                        this.height, this.inputField.xPosition, mouseX,
                        adjustedMouseY);
            }
            refreshShareSuggestions();
            shareSuggestions.draw(this.mc, this.fontRendererObj,
                    this.regions, this.height, this.inputField.xPosition,
                    mouseX, adjustedMouseY);
            if (!this.regions.contains(mouseX, adjustedMouseY)) {
                // Chat lines live in the HUD pass and never move with the
                // input bar's entrance, so they are hit in raw GUI space.
                drawChatLineHover(mouseX, mouseY, adjustedMouseY);
            }
        } finally {
            GL11.glPopMatrix();
        }
        drawNotice();
        ChatMentionCandidate hoveredCandidate = LostTalesConfig.enableChatPings
                ? nameSuggestions.suggestionAt(this.fontRendererObj, mouseX,
                        adjustedMouseY, this.height,
                        this.inputField.xPosition)
                : null;
        if (hoveredCandidate != null) {
            LostTalesChatHoverCard.drawForCandidate(this.mc,
                    hoveredCandidate, mouseX, mouseY,
                    this.width, this.height);
        } else if (!this.regions.contains(mouseX, adjustedMouseY)) {
            LostTalesChatHoverCard.draw(this.mc, mouseX, mouseY,
                    this.width, this.height);
        }
    }

    /**
     * Vanilla {@code GuiChat.drawScreen}'s input bar with only the colour
     * changed: the palette's plum black at the same half opacity as the
     * message backdrop, with no fade. GuiScreen's button pass is skipped
     * because this screen registers no buttons.
     */
    private void drawInputBar() {
        drawRect(2, this.height - 14, this.width - 2, this.height - 2,
                LostTalesSkyrimUiStyle.withAlpha(
                        LostTalesSkyrimUiStyle.PLUM_BLACK, 0x80));
        this.inputField.drawTextBox();
    }

    /**
     * Hover cards for item, text, and achievement components reproduced
     * from vanilla, plus the tooltips of shared items and markers, drawn
     * after the popups so they layer above everything in the stack.
     */
    private void drawChatLineHover(int mouseX, int hitMouseY,
                                   int drawMouseY) {
        LostTalesChatOverlayRenderer.Hit hovered =
                LostTalesChatOverlayRenderer.hitAt(
                        this.mc, mouseX, hitMouseY);
        if (hovered == null) {
            return;
        }
        ChatShowcaseMarker.Data share =
                ChatShowcaseMarker.decode(hovered.component);
        if (share != null) {
            drawShareTooltip(share, mouseX, drawMouseY);
            return;
        }
        if (hovered.component.getChatStyle().getChatHoverEvent() != null) {
            drawComponentHoverCard(hovered.component.getChatStyle()
                    .getChatHoverEvent(), mouseX, drawMouseY);
            GL11.glDisable(GL11.GL_LIGHTING);
        }
    }

    private void drawShareTooltip(ChatShowcaseMarker.Data share,
                                  int mouseX, int mouseY) {
        if (share.kind == ChatShareKind.ITEM) {
            ItemStack stack = ClientChatShowcaseStore.getItem(share.showcaseId);
            if (stack != null) {
                this.renderToolTip(stack, mouseX, mouseY);
                GL11.glDisable(GL11.GL_LIGHTING);
            }
            return;
        }
        ClientChatShowcaseStore.Marker marker =
                ClientChatShowcaseStore.getMarker(share.showcaseId);
        if (marker == null) {
            return;
        }
        List<String> lines = new ArrayList<String>(4);
        lines.add(marker.name);
        lines.add(EnumChatFormatting.ITALIC + StatCollector.translateToLocal(
                "gui.losttales.chat.marker.category"));
        lines.add("X " + Math.round(marker.x) + "   Z " + Math.round(marker.z));
        lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(
                "gui.losttales.chat.marker.open"));
        this.func_146283_a(lines, mouseX, mouseY);
        GL11.glDisable(GL11.GL_LIGHTING);
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
            ChatMentionCandidate suggested = nameSuggestions.suggestionAt(
                    this.fontRendererObj, mouseX, adjustedMouseY,
                    this.height, this.inputField.xPosition);
            if (suggested != null) {
                acceptNameSuggestion(suggested);
                return;
            }
        }
        if (button == 0 && shareSuggestions.isActive()) {
            ChatShareCandidates.Entry suggested =
                    shareSuggestions.suggestionAt(this.fontRendererObj,
                            mouseX, adjustedMouseY, this.height,
                            this.inputField.xPosition);
            if (suggested != null) {
                acceptShareSuggestion(suggested);
                return;
            }
        }
        ChatPickerPanel picker = openPicker();
        if (picker != null && picker.isInsidePanel(mouseX, adjustedMouseY,
                this.width, this.height)) {
            if (picker.mouseClicked(mouseX, adjustedMouseY, button,
                    this.width, this.height)) {
                return;
            }
            ChatPickerPanel.Entry entry = picker.entryAt(
                    mouseX, adjustedMouseY, this.width, this.height);
            if (button == 0 && entry != null) {
                this.inputField.writeText(picker.insertionText(entry));
                this.inputField.setFocused(true);
            } else if (button == 1 && picker == this.emojiPicker) {
                this.emojiPicker.toggleFavoriteAt(
                        mouseX, adjustedMouseY, this.width, this.height);
            }
            return;
        }
        if (button == 0 && LostTalesChatOverlayRenderer.hasDrawnLines()) {
            ChatChannel tab = this.tabBar.tabAt(this.fontRendererObj,
                    this.availableChannels,
                    ClientChatChannelState.getSelected(), this.width,
                    LostTalesChatOverlayRenderer.historyTop(
                            this.mc.ingameGUI.getChatGUI(), this.height),
                    mouseX, adjustedMouseY);
            if (tab != null) {
                selectChannel(tab);
                return;
            }
        }
        if (button == 0 && isInsideIndicator(mouseX, adjustedMouseY)) {
            selectChannel(ClientChatChannelState.cycle());
            return;
        }
        if (button == 0) {
            for (ChatPickerPanel candidate : this.pickers) {
                if (candidate == this.emojiPicker
                        && !LostTalesConfig.enableChatEmojis) {
                    continue;
                }
                if (candidate.isInsideButton(mouseX, adjustedMouseY,
                        this.width, this.height)) {
                    boolean open = candidate.isOpen();
                    closePickers();
                    candidate.setOpen(!open);
                    return;
                }
            }
        }
        if (button == 0 && picker != null) {
            // Clicks inside the panel were consumed above; anything else
            // closes the picker and is processed normally.
            closePickers();
        }
        if (this.regions.contains(mouseX, adjustedMouseY)) {
            // An overlay owns this spot even when no row was hit; the
            // message stack underneath must not receive the click.
            return;
        }
        if (button == 1 && LostTalesChatClipboard.copy(
                this.mc.ingameGUI.getChatGUI(), this.mc, mouseX, mouseY)) {
            showNotice(StatCollector.translateToLocal(
                    "gui.losttales.chat.copied"));
            return;
        }
        if (button == 0 && handleComponentClick(
                LostTalesChatOverlayRenderer.hitAt(
                        this.mc, mouseX, mouseY))) {
            return;
        }
        // GuiChat's own component handling relies on GuiNewChat's 9px hit
        // testing, which no longer matches the 11px layout; only the input
        // field still needs the vanilla click path.
        this.inputField.mouseClicked(mouseX, adjustedMouseY, button);
    }

    private void selectChannel(ChatChannel channel) {
        ClientChatChannelState.select(channel);
        syncSelection();
        closePickers();
        this.inputField.setFocused(true);
        // Mention candidates are shaped per channel identity.
        this.mentionBuiltNanos = 0L;
    }

    /**
     * Vanilla component-click behaviour resolved against this mod's line
     * layout. The head marker acts as the sender like the visible name
     * does; a shared marker opens the map on its location; colour, emote,
     * and item markers are internal metadata and never a user-facing
     * action.
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
        ChatShowcaseMarker.Data share =
                ChatShowcaseMarker.decode(hit.component);
        if (share != null) {
            if (share.kind == ChatShareKind.MARKER) {
                ClientChatShowcaseStore.Marker marker =
                        ClientChatShowcaseStore.getMarker(share.showcaseId);
                if (marker != null) {
                    LostTalesLotrMapGui.openFocusedOn(marker.id,
                            marker.dimensionId, marker.x, marker.z);
                }
            }
            return true;
        }
        if (ChatColorMarker.isMarker(hit.component)
                || ChatChannelPrefixMarker.isMarker(hit.component)
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

    /**
     * Bare {@code [Channel]} label in the channel's colour with the shared
     * shadow and no backdrop; hovering brightens it to ivory so the cycle
     * affordance is still visible, and a channel the player can only read
     * is drawn muted.
     */
    private void drawIndicator(int mouseX, int mouseY) {
        ChatChannel channel = ClientChatChannelState.getSelected();
        int width = indicatorWidth();
        int top = this.height - 14;
        boolean hovered = isInsideIndicator(mouseX, mouseY);
        String label = indicatorLabel(channel);
        int color = hovered ? LostTalesChatVisualStyle.IVORY
                : ClientChatChannelState.displayColor(channel);
        LostTalesChatVisualStyle.drawColored(this.fontRendererObj,
                label, INDICATOR_X + 3, top + 2, color,
                ClientChatChannelState.canSend(channel) ? 255 : 150);
        this.regions.add(INDICATOR_X, top, INDICATOR_X + width,
                top + INDICATOR_HEIGHT);
    }

    /**
     * Whether an overlay drawn this frame owns the given GUI-space point.
     * Used by the vanilla hit-test hook so third-party chat hover cards
     * (LOTR's achievement card among them) stay silent under popups.
     */
    public boolean isPointerOwnedByOverlay(int mouseX, int mouseY) {
        return this.regions.contains(mouseX,
                Math.round(mouseY - inputEntranceOffset()));
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

    private void showNotice(String text) {
        this.noticeText = text == null ? "" : text;
        this.noticeNanos = System.nanoTime();
    }

    /** Short centred confirmation above the input bar (copy, too long). */
    private void drawNotice() {
        if (this.noticeNanos <= 0L || this.noticeText.length() == 0) {
            return;
        }
        float ageMillis = (System.nanoTime() - this.noticeNanos)
                / 1000000.0F;
        if (ageMillis >= NOTICE_LIFETIME_MILLIS) {
            this.noticeNanos = 0L;
            return;
        }
        float opacity = 1.0F;
        if (LostTalesConfig.enableChatAnimations) {
            opacity = Math.min(1.0F, ageMillis / 100.0F)
                    * Math.min(1.0F,
                            (NOTICE_LIFETIME_MILLIS - ageMillis) / 250.0F);
        }
        int alpha = Math.max(LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA,
                Math.min(255, Math.round(255.0F * opacity)));
        int popupWidth = this.fontRendererObj.getStringWidth(
                this.noticeText) + 10;
        int x = (this.width - popupWidth) / 2;
        int y = this.height - 31 + Math.round((1.0F - opacity) * 3.0F);
        drawRect(x, y, x + popupWidth, y + 13,
                (Math.min(220, alpha) << 24)
                        | LostTalesChatVisualStyle.SURFACE_RGB);
        LostTalesChatVisualStyle.drawPlain(this.fontRendererObj,
                this.noticeText, x + 5, y + 2, alpha);
    }

    private boolean isInsideIndicator(int mouseX, int mouseY) {
        int top = this.height - 14;
        return mouseX >= INDICATOR_X
                && mouseX < INDICATOR_X + indicatorWidth()
                && mouseY >= top && mouseY < top + INDICATOR_HEIGHT;
    }

    private int indicatorWidth() {
        return this.fontRendererObj.getStringWidth(
                indicatorLabel(ClientChatChannelState.getSelected())) + 6;
    }

    private static String indicatorLabel(ChatChannel channel) {
        return "[" + ClientChatChannelState.displayName(channel) + "]";
    }

    private void updateInputBounds() {
        if (this.inputField == null) {
            return;
        }
        int left = INDICATOR_X + indicatorWidth() + 3;
        int right = this.markerPicker.buttonLeft(this.width) - 3;
        this.inputField.xPosition = left;
        this.inputField.width = Math.max(20, right - left);
    }
}
