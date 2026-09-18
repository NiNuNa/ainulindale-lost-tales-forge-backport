package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.chat.ChatMentionCandidate;
import com.ninuna.losttales.chat.ChatNameSuggester;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/**
 * Live {@code @Name} completion list shown above the chat input, the
 * counterpart of {@link ChatEmojiSuggestionBox} for player mentions.
 * Selection state lives here; applying a completion is the chat screen's
 * job. Candidates are supplied on update, already shaped for the selected
 * channel (account identity in OOC, character identity otherwise), so this
 * box stays free of network and world lookups and never shows the same
 * player twice.
 *
 * <p>Each row wears the face of the player it names, drawn on the same
 * rules every other inline glyph in the chat is drawn by, so the list
 * reads like the lines it completes. A role has no face and is named in
 * its own colour instead; roles come first in the candidate list, so
 * they stand above the players.</p>
 */
final class ChatNameSuggestionBox {
    static final int MAX_ROWS = 8;
    private static final int ROW_HEIGHT = 11;
    private static final int PADDING = 2;
    /** The face's box and the gap after it, shared by every row. */
    private static final int ICON_SIZE = 8;
    /**
     * The icon's whole width: the head and the presence sphere that
     * stands past it, which is one icon.
     */
    private static final int ICON_WIDTH =
            ICON_SIZE + ChatPresenceMark.OVERHANG_X;
    private static final int ICON_GAP = 3;
    /**
     * How far above the input anchor ({@link ChatInputBar#inputAnchor})
     * the box ends: one pixel clear of the bar's top.
     */
    private static final int BOTTOM_MARGIN = 15;

    private List<ChatMentionCandidate> matches = Collections.emptyList();
    private ChatNameSuggester.Query query;
    private int selectedIndex;
    private int dismissedAtIndex = -1;
    private int candidateRevision = -1;

    /**
     * Recomputes the query and matches. {@code revision} identifies the
     * candidate list build so matches are only refiltered when either the
     * typed prefix or the candidate set actually changed.
     */
    void update(String text, int cursor,
                List<ChatMentionCandidate> candidates, int revision) {
        ChatNameSuggester.Query found =
                ChatNameSuggester.findQuery(text, cursor);
        if (found == null) {
            this.query = null;
            this.matches = Collections.emptyList();
            this.dismissedAtIndex = -1;
            return;
        }
        if (found.atIndex != this.dismissedAtIndex) {
            this.dismissedAtIndex = -1;
        }
        boolean changed = this.query == null
                || this.query.atIndex != found.atIndex
                || !this.query.prefix.equals(found.prefix)
                || this.candidateRevision != revision;
        this.query = found;
        if (changed) {
            this.matches = ChatNameSuggester.matches(
                    found.prefix, candidates, MAX_ROWS);
            this.candidateRevision = revision;
            if (this.selectedIndex >= this.matches.size()) {
                this.selectedIndex = 0;
            }
        }
    }

    boolean isActive() {
        return this.query != null && !this.matches.isEmpty()
                && this.query.atIndex != this.dismissedAtIndex;
    }

    ChatMentionCandidate getSelected() {
        return isActive() && this.selectedIndex < this.matches.size()
                ? this.matches.get(this.selectedIndex) : null;
    }

    ChatNameSuggester.Query getQuery() {
        return this.query;
    }

    void moveSelection(int delta) {
        if (!isActive()) {
            return;
        }
        int size = this.matches.size();
        this.selectedIndex =
                ((this.selectedIndex + delta) % size + size) % size;
    }

    /** Hides the current query's list until a new query starts. */
    void dismiss() {
        if (this.query != null) {
            this.dismissedAtIndex = this.query.atIndex;
        }
    }

    boolean contains(FontRenderer font, double mouseX, double mouseY,
                     int screenHeight, int inputX) {
        if (!isActive()) {
            return false;
        }
        int top = boxTop(screenHeight);
        return LostTalesUiHitBox.contains(mouseX, mouseY, inputX, top,
                boxWidth(font), screenHeight - BOTTOM_MARGIN - top);
    }

    /**
     * The row under the point, or -1: the one test the row's highlight,
     * a press and the pointer all ask.
     */
    int rowAt(FontRenderer font, double mouseX, double mouseY,
              int screenHeight, int inputX) {
        if (!contains(font, mouseX, mouseY, screenHeight, inputX)
                || mouseY < boxTop(screenHeight) + PADDING) {
            return -1;
        }
        int row = (int)Math.floor((mouseY - boxTop(screenHeight) - PADDING)
                / (double)ROW_HEIGHT);
        return row >= 0 && row < this.matches.size() ? row : -1;
    }

    /** The suggestion on a row, or null. */
    ChatMentionCandidate at(int row) {
        return row >= 0 && row < this.matches.size()
                ? this.matches.get(row) : null;
    }

    void draw(Minecraft minecraft, FontRenderer font,
              ChatPointerRegions regions, int screenHeight, int inputX,
              double mouseX, double mouseY) {
        if (!isActive()) {
            return;
        }
        int width = boxWidth(font);
        int top = boxTop(screenHeight);
        int bottom = screenHeight - BOTTOM_MARGIN;
        int hoveredRow = rowAt(font, mouseX, mouseY, screenHeight, inputX);
        if (hoveredRow >= 0) {
            this.selectedIndex = hoveredRow;
        }
        regions.add(inputX, top, inputX + width, bottom);
        LostTalesChatVisualStyle.drawPopupList(inputX, top, inputX + width,
                bottom, top + PADDING, ROW_HEIGHT,
                this.selectedIndex < this.matches.size()
                        ? this.selectedIndex : -1);
        for (int row = 0; row < this.matches.size(); row++) {
            int rowTop = top + PADDING + row * ROW_HEIGHT;
            ChatMentionCandidate candidate = this.matches.get(row);
            drawFace(minecraft, candidate, inputX + 4, rowTop + 1);
            LostTalesChatVisualStyle.drawColored(font,
                    "@" + candidate.getDisplayName(),
                    inputX + 4 + ICON_WIDTH + ICON_GAP, rowTop + 2,
                    rowColor(candidate), 255);
        }
    }

    /**
     * The colour a row's name is drawn in: the same resolution a mention
     * of that name gets in a line of the selected channel — a role its
     * own, an account its primary role's, a character its faction's —
     * so the list reads exactly as the sent line will; ivory only for a
     * name nothing resolves.
     */
    private static int rowColor(ChatMentionCandidate candidate) {
        int color = ChatMentionColors.colorOf(candidate,
                ClientChatChannelState.getSelectedChannel());
        return color >= 0 ? color : LostTalesChatVisualStyle.IVORY;
    }

    /**
     * The player's head in the row's icon box, with the chat's shadow
     * under it. A role has none: its colour is what names it.
     */
    private static void drawFace(Minecraft minecraft,
                                 ChatMentionCandidate candidate,
                                 int x, int y) {
        UUID account = accountId(candidate);
        if (minecraft == null || account == null) {
            return;
        }
        int shadow = LostTalesChatVisualStyle.shadowAlpha(255);
        if (shadow > 0) {
            ChatPresenceMark.beginShadowCut(x, y, ICON_SIZE);
            LostTalesSilhouetteRenderState.begin(
                    LostTalesChatVisualStyle.SHADOW);
            try {
                LostTalesCharacterHeadIconRenderer.drawTintedAccountHeadBase(
                        minecraft, account,
                        x + LostTalesChatVisualStyle.SHADOW_OFFSET,
                        y + LostTalesChatVisualStyle.SHADOW_OFFSET,
                        ICON_SIZE, 1.0F, 1.0F, 1.0F, shadow / 255.0F);
            } finally {
                LostTalesSilhouetteRenderState.end();
                ChatPresenceMark.endHeadCut();
            }
        }
        ChatPresenceMark.beginHeadCut(x, y, ICON_SIZE);
        try {
            LostTalesCharacterHeadIconRenderer.drawAccountHead(minecraft,
                    account, x, y, ICON_SIZE, 1.0F, 1.0F);
        } finally {
            ChatPresenceMark.endHeadCut();
        }
        ChatPresenceMark.draw(x, y, ICON_SIZE,
                ClientChatPresence.presenceOf(account, identityOf(candidate)),
                255);
    }

    /** The identity a row shows: the character it names, else the account. */
    private static ChatPresenceIdentity identityOf(
            ChatMentionCandidate candidate) {
        String id = candidate.getCharacterId();
        if (id.length() == 0) {
            return ChatPresenceIdentity.ACCOUNT;
        }
        try {
            return ChatPresenceIdentity.character(UUID.fromString(id));
        } catch (IllegalArgumentException notAnId) {
            return ChatPresenceIdentity.ACCOUNT;
        }
    }

    private static UUID accountId(ChatMentionCandidate candidate) {
        String id = candidate == null ? "" : candidate.getAccountId();
        if (id.length() == 0) {
            return null;
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }

    private int boxWidth(FontRenderer font) {
        int width = 0;
        for (int index = 0; index < this.matches.size(); index++) {
            width = Math.max(width, font.getStringWidth(
                    "@" + this.matches.get(index).getDisplayName()));
        }
        return width + 8 + ICON_WIDTH + ICON_GAP;
    }

    private int boxTop(int screenHeight) {
        return screenHeight - BOTTOM_MARGIN
                - this.matches.size() * ROW_HEIGHT - PADDING * 2;
    }
}
