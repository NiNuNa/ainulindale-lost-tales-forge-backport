package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;

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
    private long replyToMessageId = ChatMessageIds.NONE;
    private String replyToName = "";
    /**
     * What the answered message said, taken off the line when the reply
     * was started. The server resolves its own quote and this goes
     * unused there; an NPC's conversation has no server, so this is the
     * only record the quote can be built from.
     */
    private String replyToExcerpt = "";
    /**
     * The head the answered line was drawn with — its sender's face, an
     * NPC's portrait, or the mark standing for the server — and the
     * colour its name was drawn in, so the quote wears both on this
     * screen whatever kind of line it answers; null for a line with no
     * sender.
     */
    private ChatHeadMarker.Data replyToHead;
    private ChatTab replyTab;
    private long editingMessageId = ChatMessageIds.NONE;
    private ChatTab editingTab;
    /** The chip drawn this frame, for the click that dismisses it. */
    private double chipLeft;
    private double chipTop;
    private double chipRight;
    private double chipBottom;

    /**
     * Starts answering a message in its tab, the line's {@code head} as
     * it was drawn ({@link LostTalesChatPresentation#headOfLine}); whatever
     * was composed goes.
     */
    void startReply(ChatTab tab, long messageId, String name, String excerpt,
                    ChatHeadMarker.Data head) {
        this.replyToMessageId = messageId;
        this.replyToName = name == null ? "" : name;
        this.replyToExcerpt = excerpt == null ? "" : excerpt;
        this.replyToHead = head;
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

    /**
     * The message a sent line answers; {@link ChatReplyReference#NONE}
     * for none. It wears the answered line's head and name colour, as
     * the quote the server cuts of a line it named does, so the line
     * shown before the server answers already reads as the one it sends
     * back — and a quote that never reaches a server, in an NPC
     * conversation, has them at all.
     */
    ChatReplyReference replyReference() {
        if (!isReplying()) {
            return ChatReplyReference.NONE;
        }
        ChatHeadMarker.Data head = this.replyToHead;
        int color = head == null ? ChatReplyReference.NO_COLOR
                : head.nameColor;
        ChatReplyReference reference =
                this.replyToMessageId != ChatMessageIds.NONE
                        ? ChatReplyReference.of(this.replyToMessageId,
                                this.replyToName, this.replyToExcerpt, color)
                        : ChatReplyReference.unanchored(this.replyToName,
                                this.replyToExcerpt, color);
        if (head == null || head.senderId == null) {
            return reference;
        }
        return head.npcIdentity
                ? reference.withNpcHead(head.senderId, head.skinId)
                : reference.withHead(head.senderId, head.accountIdentity,
                        head.skinId);
    }

    /** Forgets the message being replied to; the chip goes with it. */
    void cancelReply() {
        this.replyToMessageId = ChatMessageIds.NONE;
        this.replyToName = "";
        this.replyToExcerpt = "";
        this.replyToHead = null;
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
    boolean drawChip(FontRenderer font, Window window,
                     ChatFrame frame, LostTalesGuiAnimationSample opening,
                     int mouseX, int mouseY) {
        this.chipRight = 0;
        ChatTab composing = composingTab();
        if (composing == null || !composing.equals(
                ChatFrame.activeTab(window,
                        ChatFrame.visibleTabs(window)))) {
            return false;
        }
        int alpha = Math.round(255.0F * opening.getOpacity());
        if (alpha < 4) {
            return false;
        }
        // The line begins where the messages above it do: past the
        // timestamp column, not across it, riding their origin's exact
        // place as the area slides.
        ChatTrailingStrip strip = ChatTrailingStrip.of(frame, font);
        int x = strip.x;
        int y = strip.y;
        int room = strip.room;
        String label = font.trimStringToWidth(
                isEditing()
                        ? StatCollector.translateToLocal(
                                "gui.losttales.chat.editing")
                        : StatCollector.translateToLocalFormatted(
                                "gui.losttales.chat.message.replying",
                                this.replyToName),
                Math.max(20, room - LostTalesUiSheet.CLOSE.getWidth() - 6));
        int width = font.getStringWidth(label);
        // The cross is the control, so its own box is what answers to
        // the pointer — a little wider than the sprite, so a five-pixel
        // glyph is not a five-pixel target.
        int crossX = x + width + 3;
        int crossY = y + 1;
        this.chipLeft = crossX - 2 + strip.fractionX;
        this.chipTop = crossY - 2 + strip.fractionY;
        this.chipRight = this.chipLeft + LostTalesUiSheet.CLOSE.getWidth() + 4;
        this.chipBottom = this.chipTop + LostTalesUiSheet.CLOSE.getHeight() + 4;
        LostTalesUiSheet cross = chipContains(mouseX, mouseY)
                ? LostTalesUiSheet.CLOSE_HOVER : LostTalesUiSheet.CLOSE;
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(strip.fractionX, strip.fractionY, 0.0F);
            LostTalesUiInk.drawText(font, label, x, y,
                    LostTalesChatVisualStyle.asideRgb(), alpha);
            cross.drawWithShadow(crossX, crossY, alpha);
        } finally {
            GL11.glPopMatrix();
        }
        return true;
    }

    /** Whether the point lies on the chip's cross as drawn this frame. */
    boolean chipContains(double x, double y) {
        return this.chipRight > this.chipLeft
                && x >= this.chipLeft && x < this.chipRight
                && y >= this.chipTop && y < this.chipBottom;
    }
}
