package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatMentionCandidate;
import com.ninuna.losttales.chat.ChatNameSuggester;
import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

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
    private static final int ICON_GAP = 3;
    /** Matches the tab row's gap above the input row. */
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

    boolean contains(FontRenderer font, int mouseX, int mouseY,
                     int screenHeight, int inputX) {
        if (!isActive()) {
            return false;
        }
        int width = boxWidth(font);
        int top = boxTop(screenHeight);
        return mouseX >= inputX && mouseX < inputX + width
                && mouseY >= top && mouseY < screenHeight - BOTTOM_MARGIN;
    }

    /** The candidate under the mouse, or null. Also used for clicks. */
    ChatMentionCandidate suggestionAt(FontRenderer font, int mouseX,
                                      int mouseY, int screenHeight,
                                      int inputX) {
        if (!contains(font, mouseX, mouseY, screenHeight, inputX)
                || mouseY < boxTop(screenHeight) + PADDING) {
            return null;
        }
        int row = (mouseY - boxTop(screenHeight) - PADDING) / ROW_HEIGHT;
        return row >= 0 && row < this.matches.size()
                ? this.matches.get(row) : null;
    }

    void draw(Minecraft minecraft, FontRenderer font,
              ChatPointerRegions regions, int screenHeight, int inputX,
              int mouseX, int mouseY) {
        if (!isActive()) {
            return;
        }
        int width = boxWidth(font);
        int top = boxTop(screenHeight);
        int bottom = screenHeight - BOTTOM_MARGIN;
        regions.add(inputX, top, inputX + width, bottom);
        Gui.drawRect(inputX, top, inputX + width, bottom,
                LostTalesChatVisualStyle.argb(
                        LostTalesChatVisualStyle.SURFACE_RGB, 0xE0));
        for (int row = 0; row < this.matches.size(); row++) {
            int rowTop = top + PADDING + row * ROW_HEIGHT;
            boolean hovered = mouseX >= inputX
                    && mouseX < inputX + width
                    && mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT;
            if (hovered) {
                this.selectedIndex = row;
            }
            if (row == this.selectedIndex) {
                Gui.drawRect(inputX + 1, rowTop, inputX + width - 1,
                        rowTop + ROW_HEIGHT,
                        LostTalesChatVisualStyle.argb(
                                LostTalesChatVisualStyle
                                        .SURFACE_HIGHLIGHT_RGB, 0xC8));
            }
            ChatMentionCandidate candidate = this.matches.get(row);
            drawFace(minecraft, candidate, inputX + 4, rowTop + 1);
            LostTalesChatVisualStyle.drawColored(font,
                    "@" + candidate.getDisplayName(),
                    inputX + 4 + ICON_SIZE + ICON_GAP, rowTop + 2,
                    candidate.isRole() ? candidate.getRoleColor()
                            : ChatMentionColors.colorOfKnown(
                                    candidate.getDisplayName()), 255);
        }
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
            }
        }
        LostTalesCharacterHeadIconRenderer.drawAccountHead(minecraft,
                account, x, y, ICON_SIZE, 1.0F, 1.0F);
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
        return width + 8 + ICON_SIZE + ICON_GAP;
    }

    private int boxTop(int screenHeight) {
        return screenHeight - BOTTOM_MARGIN
                - this.matches.size() * ROW_HEIGHT - PADDING * 2;
    }
}
