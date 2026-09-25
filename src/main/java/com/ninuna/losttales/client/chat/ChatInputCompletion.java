package com.ninuna.losttales.client.chat;

import com.google.common.collect.ObjectArrays;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelSuggester;
import com.ninuna.losttales.chat.ChatMentionCandidate;
import com.ninuna.losttales.chat.ChatNameSuggester;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.emoji.ChatEmojiSuggester;
import com.ninuna.losttales.chat.share.ChatShareSuggester;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.network.packet.LostTalesChatMembersPacket;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiPlayerInfo;
import net.minecraft.network.play.client.C14PacketTabComplete;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.ClientCommandHandler;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.input.Keyboard;

/**
 * Everything that offers to finish what is being typed into the chat's
 * field: the emoji, mention and share completion lists that open above
 * the input as a shortcode, an {@code @} or a share opener is typed;
 * command tab completion, owned here rather than left to vanilla so the
 * candidate list is filed under the tab that was selected when Tab was
 * pressed instead of falling into the console as an untracked line;
 * and the tokens the pickers and the lists insert. The screen hands it
 * the keys, the clicks and the frame; it hands the screen back whether
 * it took them.
 */
final class ChatInputCompletion implements ChatInputField.MentionSource {
    /** What a suggestion list's keys ask for. */
    private static final int KEY_NONE = 0;
    private static final int KEY_UP = 1;
    private static final int KEY_DOWN = 2;
    private static final int KEY_ACCEPT = 3;
    private static final int KEY_DISMISS = 4;

    /** Mention candidates are rebuilt at most this often while typing. */
    private static final long MENTION_REFRESH_NANOS = 500L * 1000000L;

    private final ChatNoticeSink notices;
    private Minecraft mc;
    private FontRenderer font;
    private ChatPointerRegions regions;
    private ChatInputField field;

    private final ChatEmojiSuggestionBox emojiSuggestions =
            new ChatEmojiSuggestionBox();
    private final ChatNameSuggestionBox nameSuggestions =
            new ChatNameSuggestionBox();
    private final ChatChannelSuggestionBox channelSuggestions =
            new ChatChannelSuggestionBox();
    private final ChatShareSuggestionBox shareSuggestions =
            new ChatShareSuggestionBox();
    /**
     * The states of command completion mirror {@code GuiChat}'s private
     * completion fields, which a subclass cannot reach.
     */
    private final List<String> completionCandidates = new ArrayList<String>();
    /** Index of the candidate standing in the field; -1 for none yet. */
    private int completionCycleIndex = -1;
    /** Set while further Tabs walk {@link #completionCandidates}. */
    private boolean completionCycling;
    /** Set between sending a completion request and its answer. */
    private boolean completionWaiting;
    /** The tab whose input the pending or walked completion belongs to. */
    private ChatTab completionTab;
    /** The candidates as a popup over the input, never a chat line. */
    private final ChatCommandSuggestionBox commandSuggestions =
            new ChatCommandSuggestionBox();

    private List<ChatMentionCandidate> mentionCandidates =
            Collections.emptyList();
    private int mentionRevision;
    private long mentionBuiltNanos;
    private ChatTab mentionTab;

    ChatInputCompletion(ChatNoticeSink notices) {
        this.notices = notices;
    }

    /**
     * Takes the field the screen just built; called from {@code initGui},
     * which also runs on every resize, when vanilla has replaced the
     * field. The lists and the walk keep their state across it.
     */
    void bind(Minecraft mc, FontRenderer font, ChatPointerRegions regions,
              ChatInputField field) {
        this.mc = mc;
        this.font = font;
        this.regions = regions;
        this.field = field;
    }

    /* ---- Keys ---- */

    /**
     * The first thing every key does: vanilla's own rule is that any key
     * ends a pending completion request, and while the candidate popup
     * is open Up and Down walk it and Escape only closes it. True when
     * the key was the popup's and is spent.
     */
    boolean handleCommandPopupKey(int keyCode) {
        this.completionWaiting = false;
        if (!this.commandSuggestions.isActive()) {
            return false;
        }
        if (keyCode == Keyboard.KEY_UP) {
            stepCompletion(-1);
            return true;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            stepCompletion(1);
            return true;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            dismissCompletion();
            return true;
        }
        return false;
    }

    /** Any key but Tab ends the walk through the candidates. */
    void onKeyNotTab() {
        this.completionCycling = false;
        this.commandSuggestions.clear();
    }

    /**
     * Offers the key to whichever suggestion list is open — the emoji
     * list first, then the mention list, then the share list, each
     * refreshed against the field before it is asked — and answers
     * whether one took it. Up and Down move the selection, Tab and
     * Enter accept it, Escape closes the list; every other key passes.
     */
    boolean handleSuggestionKey(int keyCode) {
        if (LostTalesConfig.enableChatEmojis) {
            this.emojiSuggestions.update(this.field.getText(),
                    this.field.getCursorPosition());
            if (this.emojiSuggestions.isActive()
                    && serveEmojiKey(suggestionAction(keyCode))) {
                return true;
            }
        }
        if (LostTalesConfig.enableChatPings) {
            refreshNameSuggestions();
            if (this.nameSuggestions.isActive()
                    && serveNameKey(suggestionAction(keyCode))) {
                return true;
            }
        }
        refreshChannelSuggestions();
        if (this.channelSuggestions.isActive()
                && serveChannelKey(suggestionAction(keyCode))) {
            return true;
        }
        refreshShareSuggestions();
        return this.shareSuggestions.isActive()
                && serveShareKey(suggestionAction(keyCode));
    }

    private boolean serveChannelKey(int action) {
        switch (action) {
            case KEY_UP:
                this.channelSuggestions.moveSelection(-1);
                return true;
            case KEY_DOWN:
                this.channelSuggestions.moveSelection(1);
                return true;
            case KEY_ACCEPT:
                acceptChannelSuggestion(this.channelSuggestions.getSelected());
                return true;
            case KEY_DISMISS:
                this.channelSuggestions.dismiss();
                return true;
            default:
                return false;
        }
    }

    /** What a key means to an open suggestion list. */
    static int suggestionAction(int keyCode) {
        if (keyCode == Keyboard.KEY_UP) {
            return KEY_UP;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            return KEY_DOWN;
        }
        if (keyCode == Keyboard.KEY_TAB || keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            return KEY_ACCEPT;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            return KEY_DISMISS;
        }
        return KEY_NONE;
    }

    private boolean serveEmojiKey(int action) {
        switch (action) {
            case KEY_UP:
                this.emojiSuggestions.moveSelection(-1);
                return true;
            case KEY_DOWN:
                this.emojiSuggestions.moveSelection(1);
                return true;
            case KEY_ACCEPT:
                acceptSuggestion(this.emojiSuggestions.getSelected());
                return true;
            case KEY_DISMISS:
                this.emojiSuggestions.dismiss();
                return true;
            default:
                return false;
        }
    }

    private boolean serveNameKey(int action) {
        switch (action) {
            case KEY_UP:
                this.nameSuggestions.moveSelection(-1);
                return true;
            case KEY_DOWN:
                this.nameSuggestions.moveSelection(1);
                return true;
            case KEY_ACCEPT:
                acceptNameSuggestion(this.nameSuggestions.getSelected());
                return true;
            case KEY_DISMISS:
                this.nameSuggestions.dismiss();
                return true;
            default:
                return false;
        }
    }

    private boolean serveShareKey(int action) {
        switch (action) {
            case KEY_UP:
                this.shareSuggestions.moveSelection(-1);
                return true;
            case KEY_DOWN:
                this.shareSuggestions.moveSelection(1);
                return true;
            case KEY_ACCEPT:
                acceptShareSuggestion(this.shareSuggestions.getSelected());
                return true;
            case KEY_DISMISS:
                this.shareSuggestions.dismiss();
                return true;
            default:
                return false;
        }
    }

    /** Brings every list up to date with what the field now holds. */
    void refreshAfterTyping() {
        if (LostTalesConfig.enableChatEmojis) {
            this.emojiSuggestions.update(this.field.getText(),
                    this.field.getCursorPosition());
        }
        if (LostTalesConfig.enableChatPings) {
            refreshNameSuggestions();
        }
        refreshChannelSuggestions();
        refreshShareSuggestions();
    }

    /**
     * The channels a {@code #} may name: every channel the player can
     * read, in the order the tabs stand in.
     */
    private void refreshChannelSuggestions() {
        List<ChatChannel> channels = new ArrayList<ChatChannel>();
        for (ChatChannel channel : ChatChannel.presentationOrder()) {
            if (ClientChatChannelState.isAvailable(channel)) {
                channels.add(channel);
            }
        }
        this.channelSuggestions.update(this.field.getText(),
                this.field.getCursorPosition(), channels);
    }

    private void refreshNameSuggestions() {
        this.nameSuggestions.update(this.field.getText(),
                this.field.getCursorPosition(),
                mentionCandidates(), this.mentionRevision);
    }

    private void refreshShareSuggestions() {
        this.shareSuggestions.update(this.field.getText(),
                this.field.getCursorPosition(), this.mc.thePlayer);
    }

    /* ---- Command completion ---- */

    /**
     * Tab in a non-empty field: walk an answered candidate list, or ask
     * for one. The request path is vanilla's — client commands complete
     * locally through {@code ClientCommandHandler}, everything else is
     * asked of the server — but the answer comes back to
     * {@link #onServerCompletions}, which files the candidate list under
     * the tab the request was typed in.
     */
    void completeInput() {
        if (this.completionCycling && !this.completionCandidates.isEmpty()) {
            if (ClientChatChannelState.getSelected().equals(
                    this.completionTab)) {
                insertCompletion((this.completionCycleIndex + 1)
                        % this.completionCandidates.size());
                return;
            }
            // The walked list belongs to another tab's input; the field
            // now holds this tab's draft, so start over on it.
            dismissCompletion();
        }
        String beforeCursor = this.field.getText().substring(0,
                this.field.getCursorPosition());
        if (beforeCursor.length() < 1 || this.mc.thePlayer == null
                || this.mc.thePlayer.sendQueue == null) {
            return;
        }
        int wordStart = this.field.func_146197_a(-1,
                this.field.getCursorPosition(), false);
        ClientCommandHandler.instance.autoComplete(beforeCursor,
                this.field.getText().substring(wordStart)
                        .toLowerCase(Locale.ROOT));
        this.mc.thePlayer.sendQueue.addToSendQueue(
                new C14PacketTabComplete(beforeCursor));
        this.completionWaiting = true;
        this.completionTab = ClientChatChannelState.getSelected();
    }

    /**
     * The completion answer. Inserts the candidates' common prefix, or —
     * when the word already is that prefix — starts walking the list and
     * shows it as a popup over the input. An answer arriving after the
     * selection moved is dropped: the field holds another tab's draft,
     * and splicing into that would corrupt it.
     */
    void onServerCompletions(String[] serverCompletions) {
        if (!this.completionWaiting || serverCompletions == null) {
            return;
        }
        this.completionWaiting = false;
        this.completionCycling = false;
        this.completionCandidates.clear();
        this.completionCycleIndex = -1;
        this.commandSuggestions.clear();
        ChatTab owner = this.completionTab;
        if (owner == null
                || !owner.equals(ClientChatChannelState.getSelected())) {
            return;
        }
        String[] merged = serverCompletions;
        String[] client = ClientCommandHandler.instance.latestAutoComplete;
        if (client != null) {
            merged = ObjectArrays.concat(client, serverCompletions,
                    String.class);
        }
        for (String candidate : merged) {
            if (candidate != null && candidate.length() > 0) {
                this.completionCandidates.add(candidate);
            }
        }
        // Vanilla's shape: the prefix is the server candidates' alone.
        String word = this.field.getText().substring(
                this.field.func_146197_a(-1,
                        this.field.getCursorPosition(), false));
        String prefix = EnumChatFormatting.getTextWithoutFormattingCodes(
                StringUtils.getCommonPrefix(serverCompletions));
        if (prefix != null && prefix.length() > 0
                && !word.equalsIgnoreCase(prefix)) {
            this.field.deleteFromCursor(
                    this.field.func_146197_a(-1,
                            this.field.getCursorPosition(), false)
                            - this.field.getCursorPosition());
            this.field.writeText(prefix);
            // The prefix leaves several ways forward; the popup shows
            // them, nothing highlighted until the walk starts.
            if (this.completionCandidates.size() > 1) {
                this.commandSuggestions.show(this.completionCandidates);
            }
        } else if (!this.completionCandidates.isEmpty()) {
            this.completionCycling = true;
            if (this.completionCandidates.size() > 1) {
                this.commandSuggestions.show(this.completionCandidates);
            }
            insertCompletion(0);
        }
    }

    /** Replaces the word at the cursor with the indexed candidate. */
    private void insertCompletion(int index) {
        this.field.deleteFromCursor(
                this.field.func_146197_a(-1,
                        this.field.getCursorPosition(), false)
                        - this.field.getCursorPosition());
        this.field.writeText(
                EnumChatFormatting.getTextWithoutFormattingCodes(
                        this.completionCandidates.get(index)));
        this.completionCycleIndex = index;
        this.commandSuggestions.setSelected(index);
    }

    /** Up or Down while the popup is open: walk the list either way. */
    private void stepCompletion(int delta) {
        if (this.completionCandidates.isEmpty()) {
            return;
        }
        this.completionCycling = true;
        int size = this.completionCandidates.size();
        int index = this.completionCycleIndex < 0
                ? (delta > 0 ? 0 : size - 1)
                : ((this.completionCycleIndex + delta) % size + size) % size;
        insertCompletion(index);
    }

    /** Closes the popup and ends the walk; the field keeps its text. */
    void dismissCompletion() {
        this.completionCycling = false;
        this.commandSuggestions.clear();
    }

    /** The mention candidates are shaped per channel identity. */
    void invalidateMentionCandidates() {
        this.mentionBuiltNanos = 0L;
    }

    /* ---- Frame ---- */

    /**
     * Draws whichever lists are open above the input, each brought up
     * to date with the field first. Called inside the bar's own
     * transform; {@code anchor} is the bar's input anchor
     * ({@link ChatInputBar#inputAnchor}, fourteen pixels below its top)
     * and {@code inputX} the field's left edge in that space.
     */
    void draw(int anchor, int inputX, double mouseX, double mouseY) {
        if (LostTalesConfig.enableChatEmojis) {
            this.emojiSuggestions.update(this.field.getText(),
                    this.field.getCursorPosition());
            this.emojiSuggestions.draw(this.mc, this.font, this.regions,
                    anchor, inputX, mouseX, mouseY);
        }
        if (LostTalesConfig.enableChatPings) {
            refreshNameSuggestions();
            this.nameSuggestions.draw(this.mc, this.font, this.regions,
                    anchor, inputX, mouseX, mouseY);
        }
        refreshChannelSuggestions();
        this.channelSuggestions.draw(this.mc, this.font, this.regions,
                anchor, inputX, mouseX, mouseY);
        refreshShareSuggestions();
        this.shareSuggestions.draw(this.mc, this.font, this.regions, anchor,
                inputX, mouseX, mouseY);
        this.commandSuggestions.draw(this.font, this.regions, anchor, inputX,
                mouseX, mouseY);
    }

    /**
     * Which open list a point is on, and which of its rows; a row of -1
     * is the list's own padding.
     */
    static final class Slot {
        static final int EMOJI = 0;
        static final int NAME = 1;
        static final int CHANNEL = 2;
        static final int SHARE = 3;
        static final int COMMAND = 4;

        final int box;
        final int row;

        Slot(int box, int row) {
            this.box = box;
            this.row = row;
        }
    }

    /**
     * The open list under a point in the bar's space, and its row, asked
     * in the order a press accepts one, so what lights is what a press
     * takes. Null off every list.
     */
    Slot slotAt(double x, double y, int anchor, int inputX) {
        if (LostTalesConfig.enableChatEmojis && this.emojiSuggestions.contains(
                this.font, x, y, anchor, inputX)) {
            return new Slot(Slot.EMOJI, this.emojiSuggestions.rowAt(
                    this.font, x, y, anchor, inputX));
        }
        if (LostTalesConfig.enableChatPings && this.nameSuggestions.contains(
                this.font, x, y, anchor, inputX)) {
            return new Slot(Slot.NAME, this.nameSuggestions.rowAt(
                    this.font, x, y, anchor, inputX));
        }
        if (this.channelSuggestions.contains(this.font, x, y, anchor, inputX)) {
            return new Slot(Slot.CHANNEL, this.channelSuggestions.rowAt(
                    this.font, x, y, anchor, inputX));
        }
        if (this.shareSuggestions.contains(this.font, x, y, anchor, inputX)) {
            return new Slot(Slot.SHARE, this.shareSuggestions.rowAt(
                    this.font, x, y, anchor, inputX));
        }
        if (this.commandSuggestions.contains(this.font, x, y, anchor, inputX)) {
            return new Slot(Slot.COMMAND, this.commandSuggestions.candidateAt(
                    this.font, x, y, anchor, inputX));
        }
        return null;
    }

    /** The mention candidate a slot names, for its hover card, or null. */
    ChatMentionCandidate mentionAt(Slot slot) {
        return slot != null && slot.box == Slot.NAME
                && LostTalesConfig.enableChatPings
                ? this.nameSuggestions.at(slot.row) : null;
    }

    /**
     * Accepts the row a slot names; true when one was taken, so the
     * screen looks no further.
     */
    boolean accept(Slot slot) {
        if (slot == null || slot.row < 0) {
            return false;
        }
        switch (slot.box) {
            case Slot.EMOJI: {
                ChatEmoji emoji = this.emojiSuggestions.at(slot.row);
                if (emoji == null) {
                    return false;
                }
                acceptSuggestion(emoji);
                return true;
            }
            case Slot.NAME: {
                ChatMentionCandidate candidate =
                        this.nameSuggestions.at(slot.row);
                if (candidate == null) {
                    return false;
                }
                acceptNameSuggestion(candidate);
                return true;
            }
            case Slot.CHANNEL: {
                ChatChannel channel = this.channelSuggestions.at(slot.row);
                if (channel == null) {
                    return false;
                }
                acceptChannelSuggestion(channel);
                return true;
            }
            case Slot.SHARE: {
                ChatShareCandidates.Entry entry =
                        this.shareSuggestions.at(slot.row);
                if (entry == null) {
                    return false;
                }
                acceptShareSuggestion(entry);
                return true;
            }
            case Slot.COMMAND:
                this.completionCycling = true;
                insertCompletion(slot.row);
                return true;
            default:
                return false;
        }
    }

    /* ---- Insertions ---- */

    /**
     * Replaces the {@code :prefix} at the cursor with the full
     * shortcode, a space behind it like every other inserted token, so
     * the next word stands apart from the emoji.
     */
    private void acceptSuggestion(ChatEmoji emoji) {
        ChatEmojiSuggester.Query query = this.emojiSuggestions.getQuery();
        if (emoji == null || query == null) {
            return;
        }
        replaceAtCursor(query.colonIndex, emoji.getShortcode() + " ");
        this.emojiSuggestions.update(this.field.getText(),
                this.field.getCursorPosition());
    }

    /** Replaces the {@code @prefix} at the cursor with the full mention. */
    private void acceptNameSuggestion(ChatMentionCandidate candidate) {
        ChatNameSuggester.Query query = this.nameSuggestions.getQuery();
        if (candidate == null || query == null) {
            return;
        }
        replaceAtCursor(query.atIndex, "@" + candidate.getDisplayName() + " ");
        refreshNameSuggestions();
    }

    /** Replaces the {@code #prefix} at the cursor with the channel's code name. */
    private void acceptChannelSuggestion(ChatChannel channel) {
        ChatChannelSuggester.Query query = this.channelSuggestions.getQuery();
        String token = channel == null ? null : ChatChannelSuggester.token(
                channel, ClientChatChannelState.scopeKeyRead(ChatChannel.FACTION));
        if (token == null || query == null) {
            return;
        }
        replaceAtCursor(query.hashIndex, token + " ");
        refreshChannelSuggestions();
    }

    /** Replaces the open share opener at the cursor with a full token. */
    private void acceptShareSuggestion(ChatShareCandidates.Entry entry) {
        ChatShareSuggester.Query query = this.shareSuggestions.getQuery();
        if (entry == null || query == null
                || refusesAnotherShareToken(entry.token())) {
            return;
        }
        replaceAtCursor(query.openIndex, entry.token() + " ");
        refreshShareSuggestions();
    }

    /**
     * The share-token ceiling is a wall like the character counter's:
     * the server attaches at most {@link ChatShareTokenParser#MAX_TOKENS}
     * showcases to a message and delivers anything beyond them as the
     * literal text, so a pick that would become dead text is refused
     * with a notice instead of inserted. Only complete tokens count —
     * the opener being completed is not one yet — and only share tokens
     * are walled; an emoji is no showcase.
     */
    private boolean refusesAnotherShareToken(String insertion) {
        if (insertion == null
                || ChatShareTokenParser.parse(insertion).isEmpty()) {
            return false;
        }
        if (ChatShareTokenParser.parse(this.field.getText()).size()
                < ChatShareTokenParser.MAX_TOKENS) {
            return false;
        }
        this.notices.showNotice(StatCollector.translateToLocalFormatted(
                "gui.losttales.chat.too_many_shares",
                Integer.valueOf(ChatShareTokenParser.MAX_TOKENS)));
        return true;
    }

    /**
     * Inserts a token picked from a menu — an emoji, an item or a
     * marker share — as a word of its own: a space is put before it
     * unless the text before the cursor is empty or already ends in one,
     * and one follows it, so two picks in a row never run together and
     * the next typed word stands apart.
     */
    void insertToken(String token) {
        String word = token == null ? "" : token.trim();
        if (word.length() == 0 || refusesAnotherShareToken(word)) {
            return;
        }
        String text = this.field.getText();
        int cursor = Math.max(0, Math.min(
                this.field.getCursorPosition(), text.length()));
        boolean separated = cursor == 0
                || Character.isWhitespace(text.charAt(cursor - 1));
        this.field.writeText((separated ? "" : " ") + word + " ");
    }

    /** Replaces the text from {@code from} to the cursor with a completion. */
    private void replaceAtCursor(int from, String replacement) {
        String text = this.field.getText();
        int start = Math.max(0, Math.min(from, text.length()));
        int cursor = Math.max(start, Math.min(
                this.field.getCursorPosition(), text.length()));
        this.field.setText(text.substring(0, start)
                + replacement + text.substring(cursor));
        this.field.setCursorPosition(Math.min(
                this.field.getText().length(),
                start + replacement.length()));
    }

    /* ---- Mention candidates ---- */

    /** The names the {@code @} list offers now, which the field's pings show. */
    @Override
    public List<ChatMentionCandidate> candidates() {
        return mentionCandidates();
    }

    /**
     * One candidate per person the conversation in front has, as its
     * member list shows them, rebuilt on an interval rather than per
     * keystroke or frame.
     */
    private List<ChatMentionCandidate> mentionCandidates() {
        ChatTab tab = ClientChatChannelState.getSelected();
        long now = System.nanoTime();
        if (tab != null && tab.equals(this.mentionTab)
                && this.mentionBuiltNanos != 0L
                && now - this.mentionBuiltNanos < MENTION_REFRESH_NANOS) {
            return this.mentionCandidates;
        }
        this.mentionBuiltNanos = now;
        this.mentionTab = tab;
        List<ChatMentionCandidate> built = buildMentionCandidates(tab);
        if (!sameCandidates(built, this.mentionCandidates)) {
            this.mentionCandidates = built;
            this.mentionRevision++;
        }
        return this.mentionCandidates;
    }

    /**
     * The conversation's member list, the live player list and the
     * appearance cache, handed to the pure builder. A list not asked for
     * yet is asked for here, so the names come in while the player types.
     */
    private List<ChatMentionCandidate> buildMentionCandidates(ChatTab tab) {
        if (this.mc.thePlayer == null) {
            return new ArrayList<ChatMentionCandidate>();
        }
        List<LostTalesChatMembersPacket.Member> members =
                Collections.<LostTalesChatMembersPacket.Member>emptyList();
        if (tab != null) {
            ClientChatMembers.requestIfDue(tab);
            ClientChatMembers.Answer answer = ClientChatMembers.of(tab);
            if (answer != null) {
                members = answer.members;
            }
        }
        Map<String, CharacterAppearance> byAccount =
                new HashMap<String, CharacterAppearance>();
        for (CharacterAppearance appearance
                : ClientCharacterAppearanceCache.snapshot().values()) {
            if (appearance != null && appearance.hasCharacter()
                    && appearance.getAccountName().length() > 0) {
                byAccount.put(appearance.getAccountName()
                        .toLowerCase(Locale.ROOT), appearance);
            }
        }
        CharacterRosterSnapshot snapshot =
                ClientCharacterRosterCache.getSnapshot();
        CharacterSummary active = snapshot == null
                ? null : snapshot.getActiveCharacter();
        List<String> online = new ArrayList<String>();
        if (this.mc.thePlayer.sendQueue != null
                && this.mc.thePlayer.sendQueue.playerInfoList != null) {
            for (Object value : this.mc.thePlayer.sendQueue.playerInfoList) {
                if (value instanceof GuiPlayerInfo) {
                    online.add(((GuiPlayerInfo)value).name);
                }
            }
        }
        return mentionCandidatesFor(
                this.mc.thePlayer.getUniqueID(),
                this.mc.thePlayer.getCommandSenderName(),
                active == null ? "" : active.getName(),
                active == null ? null : active.getCharacterId(),
                members, online, byAccount);
    }

    /**
     * The candidates for a conversation: the mentionable roles first,
     * since addressing a whole group is never buried under a list of
     * names; then the player themself; then everyone else the
     * conversation's member list shows here — and anyone online it has
     * not reached yet — alphabetical; then those it shows absent, players
     * and Discord members alike, alphabetical, since a mention reaches
     * them too. The Server and an NPC are nobody to mention, and neither
     * are the player's own other characters. A player is displayed and
     * inserted by the name the conversation knows them by — their
     * character's, or their account's — and both names remain searchable
     * aliases. The stable key is the player's id, with the character's
     * where they are listed as one, so an account and its character never
     * appear as two entries; the ids ride along, so the row is coloured
     * and faced by the synced appearance rather than by a name.
     */
    static List<ChatMentionCandidate> mentionCandidatesFor(
            UUID selfId, String selfAccount,
            String selfCharacter, UUID selfCharacterId,
            List<LostTalesChatMembersPacket.Member> members,
            List<String> onlineAccounts,
            Map<String, CharacterAppearance> appearancesByAccount) {
        List<ChatMentionCandidate> result =
                new ArrayList<ChatMentionCandidate>();
        for (ChatAccountRole role : ChatAccountRole.mentionable()) {
            String name = role.getDisplayName();
            if (name.length() > 0 && !name.equals(role.getNameKey())) {
                result.add(ChatMentionCandidate.role(
                        "role:" + role.getId(), name, role.getColor()));
            }
        }
        result.add(candidate(selfId == null ? "self" : selfId.toString(),
                selfAccount, selfCharacter,
                selfId == null ? "" : selfId.toString(),
                selfCharacterId == null ? "" : selfCharacterId.toString()));
        List<ChatMentionCandidate> here = new ArrayList<ChatMentionCandidate>();
        List<ChatMentionCandidate> absent = new ArrayList<ChatMentionCandidate>();
        Set<String> listedAccounts = new HashSet<String>();
        for (LostTalesChatMembersPacket.Member member : members) {
            if (member == null || member.isNpc()
                    || member.getPlayerId() == null
                    || member.getPlayerId().equals(selfId)
                    || LostTalesChatMessagePacket.isSystemSender(
                            member.getPlayerId())) {
                continue;
            }
            listedAccounts.add(member.getAccount().toLowerCase(Locale.ROOT));
            String character = member.getCharacterId() == null ? ""
                    : member.getName();
            (member.isOnline() ? here : absent).add(candidate(
                    member.getPlayerId() + (member.getCharacterId() == null
                            ? "" : ":" + member.getCharacterId()),
                    member.getAccount(), character,
                    member.getPlayerId().toString(),
                    member.getCharacterId() == null ? ""
                            : member.getCharacterId().toString()));
        }
        for (String account : onlineAccounts) {
            if (account == null || account.trim().length() == 0
                    || account.equalsIgnoreCase(selfAccount)
                    || listedAccounts.contains(account.toLowerCase(Locale.ROOT))) {
                continue;
            }
            CharacterAppearance appearance = appearancesByAccount.get(
                    account.toLowerCase(Locale.ROOT));
            String key = appearance == null
                    ? "account:" + account.toLowerCase(Locale.ROOT)
                    : appearance.getPlayerId().toString();
            here.add(candidate(key, account, appearance == null
                    ? "" : appearance.getCharacterName(),
                    appearance == null ? ""
                            : appearance.getPlayerId().toString(),
                    appearance == null || appearance.getCharacterId() == null
                            ? "" : appearance.getCharacterId().toString()));
        }
        Collections.sort(here, BY_DISPLAY_NAME);
        Collections.sort(absent, BY_DISPLAY_NAME);
        result.addAll(here);
        result.addAll(absent);
        return result;
    }

    private static final Comparator<ChatMentionCandidate> BY_DISPLAY_NAME =
            new Comparator<ChatMentionCandidate>() {
                @Override
                public int compare(ChatMentionCandidate left,
                                   ChatMentionCandidate right) {
                    return left.getDisplayName().compareToIgnoreCase(
                            right.getDisplayName());
                }
            };

    private static ChatMentionCandidate candidate(
            String key, String account, String character,
            String accountId, String characterId) {
        String display = character == null
                || character.trim().length() == 0 ? account : character;
        return ChatMentionCandidate.player(key, display, account, character,
                accountId, characterId, Arrays.asList(account, character));
    }

    static boolean sameCandidates(List<ChatMentionCandidate> left,
                                  List<ChatMentionCandidate> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            ChatMentionCandidate a = left.get(index);
            ChatMentionCandidate b = right.get(index);
            if (!a.getKey().equals(b.getKey())
                    || !a.getDisplayName().equals(b.getDisplayName())
                    || !a.getCharacterId().equals(b.getCharacterId())
                    || !a.getAliases().equals(b.getAliases())) {
                return false;
            }
        }
        return true;
    }
}
