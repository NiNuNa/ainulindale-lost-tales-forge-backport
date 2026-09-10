package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.gui.style.LostTalesColors;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;

/**
 * What the bar is composing besides a fresh message: the message being
 * replied to, or the message being rewritten. Editing and replying are
 * the same gesture aimed at different ends of a message, so they share
 * the strip above the bar and never both hold it — starting one puts
 * the other down. Either belongs to the message in front of you: it is
 * cleared when it is sent, when the tab changes, and on Escape, so it
 * never survives moving away from the message.
 */
final class ChatComposer {
    /**
     * The chat's asides — the reply chip, the typing line, the
     * timestamps — all wear this one quiet tone.
     */
    static final int ASIDE_RGB = LostTalesColors.rgb(LostTalesColors.ROSE_GRAY);

    private long replyToMessageId = ChatMessageIds.NONE;
    private String replyToName = "";
    /**
     * What the answered message said, taken off the line when the reply
     * was started. The server resolves its own quote and this goes
     * unused there; an NPC's conversation has no server, so this is the
     * only record the quote can be built from.
     */
    private String replyToExcerpt = "";
    private ChatTab replyTab;
    private long editingMessageId = ChatMessageIds.NONE;
    private ChatTab editingTab;
    /** The chip drawn this frame, for the click that dismisses it. */
    private int chipLeft;
    private int chipTop;
    private int chipRight;
    private int chipBottom;

    /** Starts answering a message in its tab; whatever was composed goes. */
    void startReply(ChatTab tab, long messageId, String name, String excerpt) {
        this.replyToMessageId = messageId;
        this.replyToName = name == null ? "" : name;
        this.replyToExcerpt = excerpt == null ? "" : excerpt;
        this.replyTab = tab;
    }

    /** Starts rewriting a message in its tab; a reply in progress goes. */
    void startEdit(ChatTab tab, long messageId) {
        cancelReply();
        this.editingMessageId = messageId;
        this.editingTab = tab;
    }

    /**
     * Whether a reply is being composed in the tab now selected: one
     * answering a message the server named, or one quoting a line
     * nobody named by its author and words alone — a client-local id
     * is one of those, anchored here for the jump and quoted by its
     * words to everyone else.
     */
    boolean isReplying() {
        boolean named = ChatMessageIds.isServerId(this.replyToMessageId)
                || this.replyToName.length() > 0;
        return named && this.replyTab != null
                && this.replyTab.equals(ClientChatChannelState.getSelected());
    }

    /** Whether a message is being rewritten in the tab now selected. */
    boolean isEditing() {
        return ChatMessageIds.isServerId(this.editingMessageId)
                && this.editingTab != null
                && this.editingTab.equals(ClientChatChannelState.getSelected());
    }

    /** The message being rewritten; {@link ChatMessageIds#NONE} for none. */
    long editingMessageId() {
        return this.editingMessageId;
    }

    /** The message a sent line answers; {@link ChatReplyReference#NONE} for none. */
    ChatReplyReference replyReference() {
        if (!isReplying()) {
            return ChatReplyReference.NONE;
        }
        return this.replyToMessageId != ChatMessageIds.NONE
                ? ChatReplyReference.of(this.replyToMessageId, this.replyToName,
                        this.replyToExcerpt)
                : ChatReplyReference.unanchored(this.replyToName,
                        this.replyToExcerpt, ChatReplyReference.NO_COLOR);
    }

    /** Forgets the message being replied to; the chip goes with it. */
    void cancelReply() {
        this.replyToMessageId = ChatMessageIds.NONE;
        this.replyToName = "";
        this.replyToExcerpt = "";
        this.replyTab = null;
    }

    /** Forgets the message being rewritten; the field keeps what is in it. */
    void cancelEdit() {
        this.editingMessageId = ChatMessageIds.NONE;
        this.editingTab = null;
    }

    /** A message taken back is no longer being rewritten either. */
    void forgetEditOf(long messageId) {
        if (this.editingMessageId == messageId) {
            cancelEdit();
        }
    }

    /**
     * Puts down whichever of the two the strip is holding. Dropping an
     * edit empties the field and the draft, since what was in them was
     * the message being rewritten.
     */
    void cancelComposing(GuiTextField field) {
        if (isEditing()) {
            cancelEdit();
            field.setText("");
            ClientChatChannelState.setDraft("");
            return;
        }
        cancelReply();
    }

    /**
     * Composing belongs to the tab it started in: a reply or an edit
     * aimed at another tab is dropped when this one is selected rather
     * than carried along.
     */
    void onTabSelected(ChatTab tab) {
        if (this.replyTab != null && !this.replyTab.equals(tab)) {
            cancelReply();
        }
        if (this.editingTab != null && !this.editingTab.equals(tab)) {
            cancelEdit();
        }
    }

    /** The tab whose strip the chip stands in, or null with nothing composed. */
    private ChatTab composingTab() {
        return isEditing() ? this.editingTab : isReplying() ? this.replyTab : null;
    }

    /**
     * Draws what the bar is composing, in the gap between the history
     * and the bar: {@code Replying to Name} or {@code Editing message},
     * with a cross to drop it. It stands where the typing line does and
     * takes the strip while it shows — what you are answering, or
     * correcting, matters more while you are doing it than who else is
     * talking. Returns whether it drew, so the typing line knows to
     * stand down.
     */
    boolean drawChip(FontRenderer font, ChatWindow window,
                     ChatWindowFrame frame, LostTalesGuiAnimationSample opening,
                     int mouseX, int mouseY) {
        this.chipRight = 0;
        ChatTab composing = composingTab();
        if (composing == null || !composing.equals(
                ChatWindowFrame.activeTab(window,
                        ChatWindowFrame.visibleTabs(window)))) {
            return false;
        }
        int alpha = Math.round(255.0F * opening.getOpacity());
        if (alpha < 4) {
            return false;
        }
        // The line begins where the messages above it do: past the
        // timestamp column, not across it.
        ChatTimestampColumn columns = ChatTimestampColumn.current(font);
        int inset = Math.round(columns.messageX() * frame.scale);
        int x = (int)Math.floor(frame.drawnLeft()) + inset;
        int room = (int)Math.round(frame.boxRight - frame.boxLeft)
                - inset - 6;
        int y = (int)Math.floor(frame.drawnBaseline())
                + LostTalesChatOverlayRenderer.LINE_HEIGHT
                - LostTalesChatOverlayRenderer.TEXT_OFFSET;
        String label = font.trimStringToWidth(
                isEditing()
                        ? StatCollector.translateToLocal(
                                "gui.losttales.chat.editing")
                        : StatCollector.translateToLocalFormatted(
                                "gui.losttales.chat.message.replying",
                                this.replyToName),
                Math.max(20, room - ChatIconSheet.CLOSE.getWidth() - 6));
        int width = font.getStringWidth(label);
        // The cross is the control, so its own box is what answers to
        // the pointer — a little wider than the sprite, so a five-pixel
        // glyph is not a five-pixel target.
        int crossX = x + width + 3;
        int crossY = y + 1;
        this.chipLeft = crossX - 2;
        this.chipTop = crossY - 2;
        this.chipRight = crossX + ChatIconSheet.CLOSE.getWidth() + 2;
        this.chipBottom = crossY + ChatIconSheet.CLOSE.getHeight() + 2;
        LostTalesChatVisualStyle.drawColored(font, label, x, y, ASIDE_RGB,
                alpha);
        ChatIconSheet cross = chipContains(mouseX, mouseY)
                ? ChatIconSheet.CLOSE_HOVER : ChatIconSheet.CLOSE;
        cross.drawWithShadow(crossX, crossY, alpha);
        return true;
    }

    /** Whether the point lies on the chip's cross as drawn this frame. */
    boolean chipContains(int x, int y) {
        return this.chipRight > this.chipLeft
                && x >= this.chipLeft && x < this.chipRight
                && y >= this.chipTop && y < this.chipBottom;
    }
}
