package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatAction;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.emoji.ChatEmojiParser;
import com.ninuna.losttales.chat.share.ChatQuestCard;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareReference;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.chat.share.ChatShowcase;
import com.ninuna.losttales.client.mapmarker.LostTalesMapMarkerData;
import com.ninuna.losttales.client.quest.ClientQuestEntry;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatEditPacket;
import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import com.ninuna.losttales.network.packet.LostTalesChatTypingPacket;
import com.ninuna.losttales.quest.LostTalesQuestShareResolver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

/**
 * What the chat screen tells the server: the messages it sends — a
 * fresh line, a whisper, an edit, the player's half of an NPC
 * conversation — and the heartbeat that says the player is typing. A
 * line is shown the moment it is sent, before the server answers, and
 * the showcases it carries are resolved here from the player's own
 * inventory and marker cache; the server resolves its own for the copy
 * it sends back.
 */
final class ChatOutbox {
    /** How often a typing player repeats itself to the server. */
    static final long TYPING_HEARTBEAT_NANOS = 2500L * 1000000L;
    /**
     * How long after the last keystroke a player still counts as
     * typing. Typing is what somebody is <em>doing</em>, not what their
     * input bar happens to hold: a message left half-written while they
     * walk away said "typing" for as long as they were gone. Shorter
     * than the receiving side's own time to live, so the explicit stop
     * always arrives first.
     */
    static final long TYPING_IDLE_NANOS = 4000L * 1000000L;

    private final ChatComposer composer;
    private final ChatNoticeSink notices;
    private Minecraft mc;
    /** The tab the server last heard this player typing into, or null. */
    private ConversationPage typingTab;
    private long typingSentNanos;
    /** The field's text as the typing check last saw it, and when. */
    private String typedText = "";
    private long typedNanos;

    ChatOutbox(ChatComposer composer, ChatNoticeSink notices) {
        this.composer = composer;
        this.notices = notices;
    }

    void bind(Minecraft mc) {
        this.mc = mc;
    }

    /* ---- Sending ---- */

    /**
     * Sends a message typed into {@code tab}; the caller has checked it
     * is valid and may go there. A message being rewritten goes back to
     * the server as a correction to the line it came from and the bar
     * returns to composing from scratch; a line in an NPC conversation
     * is shown here as if whispered, since nobody is on the other end;
     * anything else is shown at once and sent, answering whatever the
     * composer holds.
     */
    void sendMessage(ConversationPage tab, String message) {
        ClientChatIdentitySelection.update();
        // What goes out may have its emoticons converted; the history
        // the caller recorded keeps the raw text.
        String outgoing = ChatInputRules.outgoingMessage(message);
        if (this.composer.isEditing()) {
            long edited = this.composer.editingMessageId();
            this.composer.cancelEdit();
            LostTalesNetworkHandler.CHANNEL.sendToServer(
                    new LostTalesChatEditPacket(edited, outgoing));
            return;
        }
        if (tab.isNpc()) {
            LostTalesChatPresentation.echoToNpc(tab, outgoing,
                    resolveLocalShowcases(outgoing),
                    this.composer.replyReference());
            this.composer.cancelReply();
            return;
        }
        recordEmojiUse(outgoing);
        sendToTab(tab, outgoing, this.composer.replyReference(), false);
        // Answered: the next message is a message of its own.
        this.composer.cancelReply();
    }

    /**
     * Sends an action typed into {@code tab} ({@code /me draws his
     * sword}), its words ended as the sentence they read as, the way a
     * message is sent: shown at once and answering whatever the composer
     * holds. A message being rewritten takes the words and stays what it
     * was; in an NPC conversation the action is shown here, as a line
     * there is.
     */
    void sendAction(ConversationPage tab, String words) {
        ClientChatIdentitySelection.update();
        String outgoing = ChatAction.sentence(
                ChatInputRules.outgoingMessage(words));
        if (this.composer.isEditing()) {
            long edited = this.composer.editingMessageId();
            this.composer.cancelEdit();
            LostTalesNetworkHandler.CHANNEL.sendToServer(
                    new LostTalesChatEditPacket(edited, outgoing));
            return;
        }
        if (tab.isNpc()) {
            LostTalesChatPresentation.echoToNpc(tab, outgoing,
                    resolveLocalShowcases(outgoing),
                    this.composer.replyReference(), true);
            this.composer.cancelReply();
            return;
        }
        recordEmojiUse(outgoing);
        sendToTab(tab, outgoing, this.composer.replyReference(), true);
        this.composer.cancelReply();
    }

    /** Counts the emoji a line goes out with, for Frequently Used. */
    private static void recordEmojiUse(String outgoing) {
        if (!LostTalesConfig.enableChatEmojis) {
            return;
        }
        for (ChatEmojiParser.Segment segment
                : ChatEmojiParser.split(outgoing)) {
            if (segment.isEmoji()) {
                ChatEmojiUsageStore.recordUse(segment.getEmoji());
            }
        }
    }

    /**
     * The text after the name of a {@code /msg}, sent into the whisper
     * tab the name opened: only another way of saying it, so it is
     * shown before it is sent exactly as a message typed into the tab
     * is. A body too long to send is refused with the same notice the
     * bar gives, rather than dropped; an empty one only opens the tab.
     */
    void sendWhisper(ConversationPage tab, String text) {
        if (text.length() == 0) {
            return;
        }
        if (!ChatMessageValidator.isValid(text)) {
            this.notices.showNotice(StatCollector.translateToLocalFormatted(
                    "gui.losttales.chat.too_long",
                    Integer.valueOf(ChatMessageValidator.visibleLength(text)),
                    Integer.valueOf(ChatMessageValidator.MAX_CHARACTERS)));
            return;
        }
        sendToTab(tab, ChatInputRules.outgoingMessage(text),
                ChatReplyReference.NONE, false);
    }

    /**
     * Forwards the server's message {@code messageId} into {@code tab},
     * addressed as a line typed there would be, and says where it went.
     * The server builds the line from its own record, so nothing is shown
     * before it arrives.
     */
    void forward(ConversationPage tab, long messageId) {
        if (tab == null || !ChatMessageIds.isServerId(messageId)) {
            return;
        }
        ClientChatIdentitySelection.update();
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                LostTalesChatSendPacket.forward(tab.getChannel(),
                        tab.target(), ClientChatIdentities.wireKind(tab),
                        ClientChatIdentities.wireCharacterId(tab),
                        tab.isWhisper() ? tab.getPartnerIdentity() : "",
                        tab.isWhisper()
                                ? ClientChatChannelState.partnerCharacterIdOf(tab)
                                : null,
                        messageId));
        this.notices.showNotice(StatCollector.translateToLocalFormatted(
                "gui.losttales.chat.forwarded", tab.title()));
    }

    /**
     * Shows the line at once, named so the copy that comes back replaces
     * it rather than arriving underneath it, and sends it with the
     * references the server re-checks; {@code action} sends the words as
     * an action.
     */
    private void sendToTab(ConversationPage tab, String outgoing,
                           ChatReplyReference reply, boolean action) {
        if (tab == null) {
            return;
        }
        ClientChatIdentitySelection.update();
        // Only a message the server named travels as its id. A line no
        // server holds a record of is quoted as a message no longer kept:
        // its author and words are this screen's word alone, and the
        // line shown at once reads as the server will send it back.
        boolean named = ChatMessageIds.isServerId(reply.getMessageId());
        boolean unkept = !named && reply.exists();
        long echoNonce = LostTalesChatPresentation.echoPending(tab, outgoing,
                resolveLocalShowcases(outgoing),
                unkept ? ChatReplyReference.UNKEPT : reply, action);
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new LostTalesChatSendPacket(tab.getChannel(), outgoing,
                        resolveShareReferences(outgoing), tab.target(),
                        ClientChatIdentities.wireKind(tab),
                        ClientChatIdentities.wireCharacterId(tab),
                        named ? reply.getMessageId() : ChatMessageIds.NONE,
                        tab.isWhisper() ? tab.getPartnerIdentity() : "",
                        echoNonce,
                        tab.isWhisper()
                                ? ClientChatChannelState.partnerCharacterIdOf(tab)
                                : null,
                        unkept, action)
                        .narrating(ClientChatIdentities.wireNarrating(tab)));
    }

    /**
     * The things a message shares, resolved from this client alone for its
     * own echo: the stack, marker or quest each token names in this game's
     * words, as the references sent with the message name them by slot
     * and id.
     */
    private List<ChatShowcase> resolveLocalShowcases(String message) {
        List<ChatShareTokenParser.Token> tokens =
                ChatShareTokenParser.parse(message);
        if (tokens.isEmpty() || this.mc.thePlayer == null) {
            return null;
        }
        return resolveLocalShowcases(tokens,
                ChatShareCandidates.items(this.mc.thePlayer),
                ChatShareCandidates.markers(), ChatShareCandidates.quests());
    }

    static List<ChatShowcase> resolveLocalShowcases(
            List<ChatShareTokenParser.Token> tokens,
            List<ChatShareCandidates.ItemEntry> items,
            List<ChatShareCandidates.MarkerEntry> markers,
            List<ChatShareCandidates.QuestEntry> quests) {
        List<ChatShowcase> showcases = new ArrayList<ChatShowcase>();
        int index = 0;
        for (ChatShareTokenParser.Token token : tokens) {
            if (index >= ChatShareTokenParser.MAX_TOKENS) {
                break;
            }
            if (token.kind == ChatShareKind.ITEM) {
                for (ChatShareCandidates.ItemEntry entry : items) {
                    if (!entry.matchesToken(token)) {
                        continue;
                    }
                    byte[] encoded = ChatShowcase.encodeStack(
                            entry.stack.copy());
                    if (encoded != null) {
                        showcases.add(ChatShowcase.item(index, encoded));
                    }
                    break;
                }
            } else if (token.kind == ChatShareKind.MARKER) {
                for (ChatShareCandidates.MarkerEntry entry : markers) {
                    if (!entry.matchesToken(token)) {
                        continue;
                    }
                    LostTalesMapMarkerData marker = entry.marker;
                    // The words the marker was given travel; each reader's
                    // game names a bundled marker in its own language, and
                    // one with no name of its own after what it is called after.
                    ChatShowcase showcase = ChatShowcase.marker(index,
                            marker.getId(), marker.getGivenName(),
                            marker.getNamedAfter(),
                            marker.getIconName(), marker.getColorName(),
                            marker.getDimensionId(), marker.getX(),
                            marker.getZ());
                    if (showcase != null) {
                        showcases.add(showcase);
                    }
                    break;
                }
            } else {
                for (ChatShareCandidates.QuestEntry entry : quests) {
                    if (!entry.matchesToken(token)) continue;
                    ChatQuestCard card = localCard(entry.quest);
                    if (card != null) {
                        showcases.add(ChatShowcase.quest(index,
                                entry.quest.getReference(), card,
                                entry.quest.getSource()
                                        == ClientQuestEntry.Source.LOST_TALES));
                    }
                    break;
                }
            }
            index++;
        }
        return showcases.isEmpty() ? null : showcases;
    }

    /**
     * The card a quest of this client's own journal stands for until the
     * server's line replaces the echo: a Lost Tales quest's as the server
     * makes it, a LOTR quest's from the words its journal entry shows.
     */
    private static ChatQuestCard localCard(ClientQuestEntry quest) {
        try {
            if (quest.getSource() == ClientQuestEntry.Source.LOST_TALES
                    && quest.getLostTalesDefinition() != null) {
                return LostTalesQuestShareResolver.card(
                        quest.getLostTalesDefinition(),
                        quest.getLostTalesProgress());
            }
            String line = quest.getObjectives().isEmpty() ? ""
                    : quest.getObjectives().get(0).getText();
            List<ChatQuestCard.Objective> objectives = line.length() == 0
                    ? Collections.<ChatQuestCard.Objective>emptyList()
                    : Collections.singletonList(new ChatQuestCard.Objective(
                            "", "", Collections.<String, String>emptyMap(), 0, 0,
                            false, LostTalesQuestShareResolver.fitBytes(line,
                                    ChatQuestCard.MAX_OBJECTIVE_TEXT_BYTES)));
            return new ChatQuestCard(ChatQuestCard.Source.LOTR,
                    LostTalesQuestShareResolver.fitBytes(quest.getTitle(),
                            ChatQuestCard.MAX_TITLE_BYTES),
                    LostTalesQuestShareResolver.fitBytes(quest.getCategory(),
                            ChatQuestCard.MAX_CATEGORY_BYTES),
                    objectives, Collections.singletonMap(
                            ChatQuestCard.LOTR_PAYMENT, ""));
        } catch (IllegalArgumentException unfit) {
            return null;
        }
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
        return resolveShareReferences(tokens,
                ChatShareCandidates.items(this.mc.thePlayer),
                ChatShareCandidates.markers(), ChatShareCandidates.quests());
    }

    static List<ChatShareReference> resolveShareReferences(
            List<ChatShareTokenParser.Token> tokens,
            List<ChatShareCandidates.ItemEntry> items,
            List<ChatShareCandidates.MarkerEntry> markers,
            List<ChatShareCandidates.QuestEntry> quests) {
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
            } else if (token.kind == ChatShareKind.MARKER) {
                for (ChatShareCandidates.MarkerEntry entry : markers) {
                    if (entry.matchesToken(token)) {
                        reference = ChatShareReference.marker(
                                entry.marker.getId());
                        break;
                    }
                }
            } else {
                for (ChatShareCandidates.QuestEntry entry : quests) {
                    if (entry.matchesToken(token)) {
                        reference = ChatShareReference.quest(
                                entry.quest.getReference());
                        break;
                    }
                }
            }
            references.add(reference);
        }
        return references;
    }

    /* ---- Typing ---- */

    /**
     * Tells the server, once every heartbeat while the field holds a
     * message, that this player is typing into the selected tab — and
     * that they have stopped when the field empties, the tab changes,
     * the message goes out or the screen closes. Commands are not
     * messages and say nothing; an NPC conversation has nobody to tell.
     */
    void updateTyping(String text, ConversationPage selected) {
        long now = System.nanoTime();
        if (!text.equals(this.typedText)) {
            this.typedText = text;
            this.typedNanos = now;
        }
        if (!isTyping(text, selected, now - this.typedNanos)) {
            stopTyping();
            return;
        }
        if (selected.equals(this.typingTab)
                && now - this.typingSentNanos < TYPING_HEARTBEAT_NANOS) {
            return;
        }
        if (this.typingTab != null && !selected.equals(this.typingTab)) {
            sendTyping(this.typingTab, false);
        }
        sendTyping(selected, true);
        this.typingTab = selected;
        this.typingSentNanos = now;
    }

    /**
     * Whether the field's text, {@code sinceKeystrokeNanos} after it
     * last changed, counts as typing into {@code selected}. An action
     * being written is typing; any other command is not.
     */
    static boolean isTyping(String text, ConversationPage selected,
                            long sinceKeystrokeNanos) {
        return LostTalesConfig.sendChatTypingStatus
                && text.trim().length() > 0
                && sinceKeystrokeNanos < TYPING_IDLE_NANOS
                && (!ChatInputRules.isCommand(text)
                        || ChatInputRules.isAction(text,
                                selected.getChannel()))
                && !selected.isNpc()
                && ClientChatChannelState.canSend(selected);
    }

    void stopTyping() {
        if (this.typingTab != null) {
            sendTyping(this.typingTab, false);
            this.typingTab = null;
        }
    }

    private static void sendTyping(ConversationPage tab, boolean typing) {
        if (tab == null) {
            return;
        }
        // The identity the message will be sent as, stated the same way
        // the send states it, so presence goes where the message will.
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new LostTalesChatTypingPacket(tab.getChannel(),
                        tab.target(), typing,
                        ClientChatIdentities.wireKind(tab),
                        ClientChatIdentities.wireCharacterId(tab),
                        ClientChatIdentities.wireNarrating(tab),
                        tab.isWhisper() ? tab.getPartnerIdentity() : "",
                        tab.isWhisper() ? ClientChatChannelState.partnerCharacterIdOf(tab) : null));
    }
}
