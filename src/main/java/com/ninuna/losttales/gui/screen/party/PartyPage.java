package com.ninuna.losttales.gui.screen.party;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.party.ClientPartyDisplayNames;
import com.ninuna.losttales.client.party.ClientPartyStateCache;
import com.ninuna.losttales.client.party.ClientPartyTrackingCache;
import com.ninuna.losttales.client.party.PartyClientRequestManager;
import com.ninuna.losttales.client.window.BarItem;
import com.ninuna.losttales.client.window.PageContent;
import com.ninuna.losttales.client.window.PageSearch;
import com.ninuna.losttales.client.window.WindowBar;
import com.ninuna.losttales.client.window.WindowPages;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiClip;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.server.PartyErrorId;
import com.ninuna.losttales.party.sync.PartyInvitationSnapshot;
import com.ninuna.losttales.party.sync.PartyInviteTargetSnapshot;
import com.ninuna.losttales.party.sync.PartyMemberSnapshot;
import com.ninuna.losttales.party.sync.PartyOperationFeedback;
import com.ninuna.losttales.party.sync.PartySnapshot;
import com.ninuna.losttales.party.sync.PartyStateSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * The party, a page a window holds (U5 a): one list, as a member list is,
 * with its members under a heading, then the invitations to the player
 * and those the party sent. The window holds the rest: the search in its
 * tool strip's well narrows the list, the colour the player wears in the
 * party is chosen behind its cog, and its input bar holds the invite
 * field, whose list offers who can be invited, then what can be done
 * about the row picked, the go-here marker, and Leave (Disband for the
 * leader) last in red.
 *
 * <p>It draws only synchronized snapshots and never changes the party on
 * its own; every action is checked again by the server. An action that
 * cannot be undone asks first, in a question inside the window.</p>
 */
public final class PartyPage extends PageContent {
    /** The code name the page is registered and remembered under. */
    public static final String PAGE_ID = "party";

    private static final int MARGIN = 8;
    private static final int ROW_HEIGHT = 14;
    private static final int HEADING_HEIGHT = 15;
    private static final int NOTE_LINE = 10;
    /** The member's colour, a small square before the name. */
    private static final int CHIP = 6;
    private static final int NAME_X = CHIP + 5;
    /** The longest name the invite field takes. */
    private static final int MAX_NAME = 32;

    /** The bar's items: their ids, which the page is told when one is pressed. */
    private static final String INVITE = "invite";
    private static final String CREATE = "create";
    private static final String MAKE_LEADER = "make_leader";
    private static final String REMOVE = "remove";
    private static final String ACCEPT = "accept";
    private static final String DECLINE = "decline";
    private static final String CANCEL_INVITE = "cancel_invite";
    private static final String GO_HERE = "go_here";
    private static final String LEAVE = "leave";
    private static final String DISBAND = "disband";
    private static final String REFRESH = "refresh";

    /** What a row of the list is. */
    private enum Kind { HEADING, NOTE, MEMBER, INCOMING, OUTGOING }

    private final Minecraft mc = Minecraft.getMinecraft();
    private FontRenderer font;
    private int width = -1;
    private int height = -1;

    private int pendingRequestId;
    private boolean initialRequestSent;
    private String statusMessage = "";
    private boolean statusError;
    private UUID knownActiveCharacterId;

    /** The row picked: what kind, and whose id; null for none. */
    private Kind pickedKind;
    private UUID pickedId;
    /** The row under the pointer this frame; null for none. */
    private Row hovered;
    private int scroll;
    /** The words in the window's well; empty while its search is closed. */
    private String query = "";

    /** The invite field on the bar, made the first time the bar is asked for. */
    private GuiTextField inviteField;
    private boolean inviteFocused;
    /** Which of the players the field offers is chosen. */
    private int offered;

    /** One row of the list, as drawn and as hit. */
    private static final class Row {
        final Kind kind;
        final String text;
        final PartyMemberSnapshot member;
        final PartyInvitationSnapshot invitation;
        final int height;

        Row(Kind kind, String text, PartyMemberSnapshot member,
            PartyInvitationSnapshot invitation, int height) {
            this.kind = kind;
            this.text = text;
            this.member = member;
            this.invitation = invitation;
            this.height = height;
        }

        UUID id() {
            if (this.member != null) {
                return this.member.getCharacterId();
            }
            return this.invitation == null ? null
                    : this.invitation.getInvitationId();
        }

        boolean pickable() {
            return this.kind == Kind.MEMBER || this.kind == Kind.INCOMING
                    || this.kind == Kind.OUTGOING;
        }
    }

    /* ---- The list ---- */

    /**
     * The list's rows, the search held: the members under their heading
     * (a note where the character is in no party), then the invitations
     * to the player, then those the party sent, each under a heading only
     * while it has any.
     */
    private List<Row> rows(PartyStateSnapshot snapshot) {
        List<Row> rows = new ArrayList<Row>();
        if (snapshot == null || !snapshot.isAvailable() || this.font == null) {
            return rows;
        }
        PageSearch search = PageSearch.of(this.query);
        PartySnapshot party = snapshot.getParty();
        if (party == null) {
            addNote(rows, I18n.format("gui.losttales.party.no_party")
                    + " " + I18n.format("gui.losttales.party.no_party_detail"));
        } else {
            rows.add(heading(I18n.format("gui.losttales.party.heading.members",
                    Integer.valueOf(party.getMemberCount()))));
            for (PartyMemberSnapshot member : party.getMembers()) {
                if (search.matches(member.getCharacterName())) {
                    rows.add(new Row(Kind.MEMBER, member.getCharacterName(),
                            member, null, ROW_HEIGHT));
                }
            }
        }
        addInvitations(rows, search, snapshot.getIncomingInvitations(),
                Kind.INCOMING, "gui.losttales.party.heading.incoming");
        addInvitations(rows, search, snapshot.getOutgoingInvitations(),
                Kind.OUTGOING, "gui.losttales.party.heading.outgoing");
        if (snapshot.isIncomingTruncated() || snapshot.isOutgoingTruncated()) {
            addNote(rows, I18n.format(
                    "gui.losttales.party.invitation_list_truncated"));
        }
        if (this.query.length() > 0 && found(rows) == 0) {
            addNote(rows, I18n.format("gui.losttales.party.search.none"));
        }
        return rows;
    }

    private void addInvitations(List<Row> rows, PageSearch search,
                                List<PartyInvitationSnapshot> invitations,
                                Kind kind, String headingKey) {
        List<Row> shown = new ArrayList<Row>();
        for (PartyInvitationSnapshot invitation : invitations) {
            String name = kind == Kind.INCOMING
                    ? invitation.getInvitingCharacterName()
                    : invitation.getTargetCharacterName();
            if (search.matches(invitation.getInvitingCharacterName(),
                    invitation.getTargetCharacterName())) {
                shown.add(new Row(kind, name, null, invitation, ROW_HEIGHT));
            }
        }
        if (!shown.isEmpty()) {
            rows.add(heading(I18n.format(headingKey,
                    Integer.valueOf(shown.size()))));
            rows.addAll(shown);
        }
    }

    private static Row heading(String text) {
        return new Row(Kind.HEADING, text, null, null, HEADING_HEIGHT);
    }

    /** A note in the aside tone, as many lines as it wraps to. */
    private void addNote(List<Row> rows, String text) {
        int lines = this.font.listFormattedStringToWidth(text,
                Math.max(1, this.width - 2 * MARGIN)).size();
        rows.add(new Row(Kind.NOTE, text, null, null,
                Math.max(1, lines) * NOTE_LINE + 4));
    }

    private static int found(List<Row> rows) {
        int count = 0;
        for (Row row : rows) {
            if (row.pickable()) {
                count++;
            }
        }
        return count;
    }

    private static int contentHeight(List<Row> rows) {
        int total = 0;
        for (Row row : rows) {
            total += row.height;
        }
        return total;
    }

    /** The room the list has: the page less its margins and the status line. */
    private int listHeight() {
        return Math.max(ROW_HEIGHT, this.height - 2 * MARGIN - NOTE_LINE);
    }

    /* ---- Life ---- */

    @Override
    public void tick() {
        if (this.width < 0) {
            return;
        }
        handlePendingOperation();
        synchronizeSelection();
        if (this.inviteField != null) {
            this.inviteField.updateCursorCounter();
        }
    }

    private void handlePendingOperation() {
        if (this.pendingRequestId == 0
                || ClientPartyStateCache.isRequestPending(this.pendingRequestId)) {
            return;
        }
        int completedRequestId = this.pendingRequestId;
        this.pendingRequestId = 0;
        PartyOperationFeedback feedback =
                ClientPartyStateCache.getOperation(completedRequestId);
        if (feedback == null) {
            return;
        }
        ClientPartyStateCache.clearOperation(completedRequestId);
        if (feedback.isSuccessful()) {
            this.statusMessage = ClientPartyDisplayNames.operationSuccess(
                    feedback.getOperationType());
            this.statusError = false;
        } else {
            this.statusMessage = ClientPartyDisplayNames.error(
                    feedback.getErrorId());
            this.statusError = true;
        }
    }

    /**
     * Keeps the pick on a row that stands: the player's own member row
     * when their character changes or the pick is gone, else the first
     * invitation to them, else nothing.
     */
    private void synchronizeSelection() {
        PartyStateSnapshot snapshot = getSnapshot();
        UUID active = snapshot == null ? null : snapshot.getActiveCharacterId();
        if (active != null && !active.equals(this.knownActiveCharacterId)) {
            this.knownActiveCharacterId = active;
            this.pickedKind = null;
            this.scroll = 0;
            this.statusMessage = "";
        }
        if (snapshot == null) {
            return;
        }
        if (picked(rows(snapshot)) != null) {
            return;
        }
        PartySnapshot party = snapshot.getParty();
        if (party != null && party.getMember(active) != null) {
            pick(Kind.MEMBER, active);
        } else if (!snapshot.getIncomingInvitations().isEmpty()) {
            pick(Kind.INCOMING, snapshot.getIncomingInvitations().get(0)
                    .getInvitationId());
        } else {
            this.pickedKind = null;
            this.pickedId = null;
        }
    }

    private void pick(Kind kind, UUID id) {
        this.pickedKind = kind;
        this.pickedId = id;
    }

    /** The row picked among these; null while none of them is. */
    private Row picked(List<Row> rows) {
        for (Row row : rows) {
            if (row.pickable() && row.kind == this.pickedKind
                    && row.id() != null && row.id().equals(this.pickedId)) {
                return row;
            }
        }
        return null;
    }

    private void beginRequest(int requestId, boolean showWorkingStatus) {
        this.pendingRequestId = requestId;
        if (showWorkingStatus) {
            this.statusMessage = I18n.format("gui.losttales.party.working");
            this.statusError = false;
        }
    }

    private boolean isPending() {
        return this.pendingRequestId != 0
                && ClientPartyStateCache.isRequestPending(this.pendingRequestId);
    }

    /* ---- Drawing ---- */

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     float partialTicks, int alpha) {
        this.font = minecraft.fontRenderer;
        this.width = (int)Math.floor(box.width);
        this.height = (int)Math.floor(box.height);
        if (!this.initialRequestSent && this.pendingRequestId == 0) {
            this.initialRequestSent = true;
            beginRequest(PartyClientRequestManager.requestState(), false);
        }
        int mouseX = pageX(box, pointerX);
        int mouseY = pageY(box, pointerY);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef((float)box.left, (float)box.top, 0.0F);
            drawPage(mouseX, mouseY);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** A screen x in the page's own space; far off for a pointer away. */
    private static int pageX(LostTalesUiHitBox box, double x) {
        return Double.isNaN(x) ? Integer.MIN_VALUE / 2
                : (int)Math.floor(x - box.left);
    }

    private static int pageY(LostTalesUiHitBox box, double y) {
        return Double.isNaN(y) ? Integer.MIN_VALUE / 2
                : (int)Math.floor(y - box.top);
    }

    private void drawPage(int mouseX, int mouseY) {
        PartyStateSnapshot snapshot = getSnapshot();
        if (snapshot == null || !snapshot.isAvailable()) {
            drawCentred(unavailableMessage(snapshot));
            return;
        }
        List<Row> rows = rows(snapshot);
        clampScroll(rows);
        this.hovered = rowAt(rows, mouseX, mouseY);
        Row picked = picked(rows);
        boolean clipped = LostTalesUiClip.beginLocal(this.mc, 0, MARGIN,
                this.width, MARGIN + listHeight());
        try {
            int y = MARGIN - this.scroll;
            for (Row row : rows) {
                drawRow(snapshot, row, y, row == picked, row == this.hovered);
                y += row.height;
            }
        } finally {
            LostTalesUiClip.end(clipped);
        }
        if (this.statusMessage.length() > 0) {
            LostTalesUiInk.drawText(this.font,
                    this.font.trimStringToWidth(this.statusMessage,
                            this.width - 2 * MARGIN),
                    MARGIN, this.height - MARGIN - NOTE_LINE + 2,
                    this.statusError ? LostTalesColors.rgb(LostTalesColors.RED)
                            : WindowStyle.asideRgb(), 0xFF);
        }
    }

    private static String unavailableMessage(PartyStateSnapshot snapshot) {
        if (snapshot != null) {
            return ClientPartyDisplayNames.error(snapshot.getStateErrorId());
        }
        return ClientPartyStateCache.getState()
                == ClientPartyStateCache.SyncState.ERROR
                ? ClientPartyDisplayNames.error(PartyErrorId.INTERNAL_ERROR)
                : I18n.format("gui.losttales.party.loading");
    }

    /** A message alone in the middle of the page, in the aside tone. */
    private void drawCentred(String text) {
        List<?> lines = this.font.listFormattedStringToWidth(text,
                Math.max(1, this.width - 2 * MARGIN));
        int y = (this.height - lines.size() * NOTE_LINE) / 2;
        for (Object line : lines) {
            String each = String.valueOf(line);
            LostTalesUiInk.drawText(this.font, each,
                    (this.width - this.font.getStringWidth(each)) / 2, y,
                    WindowStyle.asideRgb(), 0xFF);
            y += NOTE_LINE;
        }
    }

    private void drawRow(PartyStateSnapshot snapshot, Row row, int y,
                         boolean picked, boolean hovered) {
        int left = MARGIN;
        int right = this.width - MARGIN;
        if (row.kind == Kind.HEADING) {
            String name = LostTalesSkyrimUiStyle.uppercase(row.text);
            int top = y + LostTalesUiInk.centredStart(HEADING_HEIGHT, 7);
            LostTalesUiInk.drawText(this.font, name, left, top,
                    LostTalesColors.rgb(LostTalesColors.TEXT), 0xFF);
            int ruleLeft = left + this.font.getStringWidth(name) + 5;
            if (ruleLeft < right) {
                Gui.drawRect(ruleLeft, top + 3, right, top + 4,
                        LostTalesColors.BORDER_DIM);
            }
            return;
        }
        if (row.kind == Kind.NOTE) {
            int lineY = y + 2;
            for (Object line : this.font.listFormattedStringToWidth(row.text,
                    Math.max(1, right - left))) {
                LostTalesUiInk.drawText(this.font, String.valueOf(line), left,
                        lineY, WindowStyle.asideRgb(), 0xFF);
                lineY += NOTE_LINE;
            }
            return;
        }
        if (picked || hovered) {
            Gui.drawRect(left - 2, y, right, y + ROW_HEIGHT, picked
                    ? LostTalesColors.withAlpha(LostTalesColors.PLUM_GRAY, 0xB4)
                    : LostTalesColors.withAlpha(LostTalesColors.PLUM_DARK, 0x72));
        }
        int textTop = y + LostTalesUiInk.centredStart(ROW_HEIGHT, 7);
        String aside = asideOf(snapshot, row);
        int asideWidth = aside.length() == 0 ? 0
                : this.font.getStringWidth(aside) + 4;
        if (row.kind == Kind.MEMBER) {
            int chipTop = y + (ROW_HEIGHT - CHIP) / 2;
            PartyColor colour = row.member.getColor();
            LostTalesUiInk.fillRect(left + 1, chipTop + 1, left + CHIP + 1,
                    chipTop + CHIP + 1, LostTalesUiInk.argb(
                            LostTalesUiInk.SHADOW, 0xFF));
            LostTalesUiInk.fillRect(left, chipTop, left + CHIP,
                    chipTop + CHIP, LostTalesUiInk.argb(colour == null
                            ? LostTalesUiInk.IVORY : colour.getRgb(), 0xFF));
        }
        int nameX = row.kind == Kind.MEMBER ? left + NAME_X : left;
        LostTalesUiInk.drawText(this.font, this.font.trimStringToWidth(
                        row.text, Math.max(0, right - nameX - asideWidth)),
                nameX, textTop, picked ? LostTalesUiInk.IVORY
                        : LostTalesColors.rgb(LostTalesColors.TEXT), 0xFF);
        if (aside.length() > 0) {
            boolean expired = row.invitation != null
                    && row.invitation.isExpired(System.currentTimeMillis());
            LostTalesUiInk.drawText(this.font, aside,
                    right - this.font.getStringWidth(aside), textTop,
                    expired ? LostTalesColors.rgb(LostTalesColors.RED)
                            : WindowStyle.asideRgb(), 0xFF);
        }
    }

    /** What stands at a row's right: Leader and You on a member, the time left on an invitation. */
    private static String asideOf(PartyStateSnapshot snapshot, Row row) {
        if (row.kind == Kind.MEMBER) {
            List<String> words = new ArrayList<String>(2);
            PartySnapshot party = snapshot.getParty();
            if (party != null && party.isLeader(row.member.getCharacterId())) {
                words.add(I18n.format("gui.losttales.party.leader"));
            }
            if (row.member.getCharacterId().equals(
                    snapshot.getActiveCharacterId())) {
                words.add(I18n.format("gui.losttales.party.you"));
            }
            StringBuilder aside = new StringBuilder();
            for (String word : words) {
                aside.append(aside.length() == 0 ? "" : ", ").append(word);
            }
            return aside.toString();
        }
        return row.invitation == null ? ""
                : formatExpiration(row.invitation, System.currentTimeMillis());
    }

    /* ---- The pointer ---- */

    /** The row under a point in the page's own space; null off every row. */
    private Row rowAt(List<Row> rows, int x, int y) {
        if (x < MARGIN - 2 || x >= this.width - MARGIN || y < MARGIN
                || y >= MARGIN + listHeight()) {
            return null;
        }
        int top = MARGIN - this.scroll;
        for (Row row : rows) {
            if (y >= top && y < top + row.height) {
                return row.pickable() ? row : null;
            }
            top += row.height;
        }
        return null;
    }

    @Override
    public boolean mousePressed(Minecraft minecraft, LostTalesUiHitBox box,
                                double x, double y, int button) {
        if (this.width < 0) {
            return false;
        }
        releaseField();
        Row row = rowAt(rows(getSnapshot()), pageX(box, x), pageY(box, y));
        if (button == 0 && row != null) {
            pick(row.kind, row.id());
            return true;
        }
        return false;
    }

    @Override
    public boolean acts(LostTalesUiHitBox box, double x, double y) {
        return this.width >= 0 && rowAt(rows(getSnapshot()), pageX(box, x),
                pageY(box, y)) != null;
    }

    /** The wheel moves the list a row a turn. */
    @Override
    public boolean scroll(LostTalesUiHitBox box, double x, double y,
                          int lines) {
        if (lines == 0 || this.width < 0) {
            return false;
        }
        this.scroll += lines * ROW_HEIGHT;
        clampScroll(rows(getSnapshot()));
        return true;
    }

    private void clampScroll(List<Row> rows) {
        int most = Math.max(0, contentHeight(rows) - listHeight());
        this.scroll = Math.max(0, Math.min(this.scroll, most));
    }

    /** Keeps the picked row in view. */
    private void scrollToPicked(List<Row> rows) {
        int top = 0;
        for (Row row : rows) {
            if (row.pickable() && row.kind == this.pickedKind
                    && row.id().equals(this.pickedId)) {
                if (top < this.scroll) {
                    this.scroll = top;
                } else if (top + row.height > this.scroll + listHeight()) {
                    this.scroll = top + row.height - listHeight();
                }
                return;
            }
            top += row.height;
        }
    }

    /* ---- The keys ---- */

    /** While the invite field is typed in it keeps every key, the pages' keys among them. */
    @Override
    public boolean holdsKeys() {
        return this.inviteFocused;
    }

    @Override
    public void focusChanged(boolean hasKeys) {
        if (!hasKeys) {
            releaseField();
        }
    }

    /**
     * The invite field's keys while it is typed in: the arrows walk the
     * players it offers, Tab writes the chosen one's name, Return invites
     * them, Escape leaves the field. Otherwise the arrows walk the list's
     * rows and R asks the server again.
     */
    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (this.width < 0) {
            return false;
        }
        if (this.inviteFocused && this.inviteField != null) {
            return typeInField(typedChar, keyCode);
        }
        if (keyCode == Keyboard.KEY_R && this.pendingRequestId == 0) {
            beginRequest(PartyClientRequestManager.requestState(), false);
            return true;
        }
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
            walkRows(keyCode == Keyboard.KEY_UP ? -1 : 1);
            return true;
        }
        return false;
    }

    private boolean typeInField(char typedChar, int keyCode) {
        List<PartyInviteTargetSnapshot> targets = offeredTargets();
        if (keyCode == Keyboard.KEY_ESCAPE) {
            releaseField();
            return true;
        }
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
            if (!targets.isEmpty()) {
                int step = keyCode == Keyboard.KEY_UP ? -1 : 1;
                this.offered = ((this.offered + step) % targets.size()
                        + targets.size()) % targets.size();
            }
            return true;
        }
        PartyInviteTargetSnapshot chosen = this.offered >= 0
                && this.offered < targets.size() ? targets.get(this.offered)
                : null;
        if (keyCode == Keyboard.KEY_TAB) {
            if (chosen != null) {
                this.inviteField.setText(chosen.getCharacterName());
                this.offered = 0;
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (chosen != null) {
                invite(chosen);
            }
            return true;
        }
        String before = this.inviteField.getText();
        this.inviteField.textboxKeyTyped(typedChar, keyCode);
        if (!before.equals(this.inviteField.getText())) {
            this.offered = 0;
        }
        return true;
    }

    /** Moves the pick to the row before or after it among those shown. */
    private void walkRows(int step) {
        List<Row> rows = rows(getSnapshot());
        List<Row> pickable = new ArrayList<Row>();
        for (Row row : rows) {
            if (row.pickable()) {
                pickable.add(row);
            }
        }
        if (pickable.isEmpty()) {
            return;
        }
        int at = pickable.indexOf(picked(rows));
        int next = at < 0 ? 0 : Math.max(0, Math.min(pickable.size() - 1,
                at + step));
        pick(pickable.get(next).kind, pickable.get(next).id());
        scrollToPicked(rows);
    }

    private void releaseField() {
        this.inviteFocused = false;
        if (this.inviteField != null) {
            this.inviteField.setFocused(false);
        }
    }

    /* ---- The window's strip ---- */

    /**
     * The party's tab wears the colour the player wears in the party; the
     * Party channel's while they are in none.
     */
    @Override
    public int tone() {
        PartyMemberSnapshot member = ownMember(getSnapshot());
        return member == null || member.getColor() == null
                ? ChatChannel.PARTY.getDisplayColor()
                : member.getColor().getRgb();
    }

    @Override
    public String choicesHeading() {
        return "gui.losttales.party.menu.colour";
    }

    /** The colours the player can wear in the party, theirs marked; one another member wears is greyed. */
    @Override
    public List<Choice> choices() {
        PartyStateSnapshot snapshot = getSnapshot();
        PartySnapshot party = snapshot == null ? null : snapshot.getParty();
        PartyMemberSnapshot own = ownMember(snapshot);
        List<Choice> choices = new ArrayList<Choice>();
        for (PartyColor colour : PartyColor.values()) {
            String why = party == null
                    ? I18n.format("gui.losttales.party.no_party")
                    : isColorInUseByAnother(party,
                            snapshot.getActiveCharacterId(), colour)
                    ? I18n.format("gui.losttales.party.colour.taken") : "";
            choices.add(new Choice(colour.name(),
                    ClientPartyDisplayNames.color(colour),
                    own != null && own.getColor() == colour, why));
        }
        return choices;
    }

    @Override
    public void choose(String id) {
        PartyStateSnapshot snapshot = getSnapshot();
        PartySnapshot party = snapshot == null ? null : snapshot.getParty();
        if (party == null || this.pendingRequestId != 0) {
            return;
        }
        for (PartyColor colour : PartyColor.values()) {
            if (colour.name().equals(id)
                    && !isColorInUseByAnother(party,
                            snapshot.getActiveCharacterId(), colour)) {
                beginRequest(PartyClientRequestManager.setColor(
                        snapshot.getActiveCharacterId(), party.getPartyId(),
                        party.getRevision(), colour), true);
            }
        }
    }

    @Override
    public String searchPrompt() {
        return I18n.format("gui.losttales.party.search");
    }

    /** New words read the list from its top, the first row found picked. */
    @Override
    public void search(String words) {
        String typed = words == null ? "" : words.trim();
        if (typed.equals(this.query)) {
            return;
        }
        this.query = typed;
        this.scroll = 0;
        if (typed.length() > 0) {
            for (Row row : rows(getSnapshot())) {
                if (row.pickable()) {
                    pick(row.kind, row.id());
                    break;
                }
            }
        }
    }

    @Override
    public int found() {
        return this.query.length() == 0 ? -1 : found(rows(getSnapshot()));
    }

    /** The arrows walk the rows found; Return gives the page the keys. */
    @Override
    public boolean searchKey(int keyCode) {
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
            walkRows(keyCode == Keyboard.KEY_UP ? -1 : 1);
            return true;
        }
        return keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER;
    }

    /* ---- The window's bar ---- */

    /**
     * The invite field, then what can be done about the row picked, the
     * go-here marker, and Leave (Disband for the leader) last in red; with
     * no party, Create Party in the field's place. While the server is
     * not answering, Refresh alone.
     */
    @Override
    public List<BarItem> barItems() {
        PartyStateSnapshot snapshot = getSnapshot();
        List<BarItem> items = new ArrayList<BarItem>(6);
        if (snapshot == null || !snapshot.isAvailable()) {
            String refresh = I18n.format("gui.losttales.party.refresh");
            BarItem item = BarItem.button(REFRESH, refresh,
                    new ItemStack(Items.clock))
                    .tip(WindowBar.withKey(refresh, Keyboard.KEY_R));
            items.add(isPending() ? item.unavailable(
                    I18n.format("gui.losttales.party.working")) : item);
            return items;
        }
        PartySnapshot party = snapshot.getParty();
        UUID active = snapshot.getActiveCharacterId();
        boolean leader = party != null && party.isLeader(active);
        String busy = isPending() ? I18n.format("gui.losttales.party.working")
                : "";
        if (party == null) {
            String create = I18n.format("gui.losttales.party.create");
            items.add(orBusy(BarItem.button(CREATE, create,
                    LostTalesUiSheet.PLUS, LostTalesUiSheet.PLUS_HOVER)
                    .tip(create), busy));
        } else {
            items.add(inviteItem(snapshot, party, leader));
        }
        Row picked = picked(rows(snapshot));
        if (picked != null && picked.kind == Kind.MEMBER
                && !picked.member.getCharacterId().equals(active)) {
            String notLeader = leader ? busy
                    : I18n.format("gui.losttales.party.error.not_leader");
            String makeLeader = I18n.format("gui.losttales.party.make_leader");
            items.add(orBusy(BarItem.button(MAKE_LEADER, makeLeader,
                    new ItemStack(Items.golden_helmet)).tip(makeLeader),
                    notLeader));
            String remove = I18n.format("gui.losttales.party.remove");
            items.add(orBusy(BarItem.button(REMOVE, remove,
                    LostTalesUiSheet.CLOSE, LostTalesUiSheet.CLOSE_HOVER)
                    .tip(remove), notLeader));
        } else if (picked != null && picked.kind == Kind.INCOMING) {
            String accept = I18n.format("gui.losttales.party.accept");
            items.add(orBusy(BarItem.button(ACCEPT, accept,
                    LostTalesUiSheet.SEND, LostTalesUiSheet.SEND_HOVER)
                    .tip(accept), busy));
            String decline = I18n.format("gui.losttales.party.decline");
            items.add(orBusy(BarItem.button(DECLINE, decline,
                    LostTalesUiSheet.CLOSE, LostTalesUiSheet.CLOSE_HOVER)
                    .tip(decline), busy));
        } else if (picked != null && picked.kind == Kind.OUTGOING) {
            String cancel = I18n.format("gui.losttales.party.cancel_invite");
            items.add(orBusy(BarItem.button(CANCEL_INVITE, cancel,
                    LostTalesUiSheet.CLOSE, LostTalesUiSheet.CLOSE_HOVER)
                    .tip(cancel), leader ? busy : I18n.format(
                            "gui.losttales.party.error.not_leader")));
        }
        if (party != null) {
            String goHere = I18n.format(ClientPartyTrackingCache
                    .hasLocalGoHereMarker(snapshot)
                    ? "gui.losttales.party.remove_go_here"
                    : "gui.losttales.party.set_go_here");
            items.add(orBusy(BarItem.button(GO_HERE, goHere,
                    LostTalesUiSheet.MAP_MARKER,
                    LostTalesUiSheet.MAP_MARKER_HOVER).tip(goHere), busy));
            String ending = I18n.format(leader ? "gui.losttales.party.disband"
                    : "gui.losttales.party.leave");
            items.add(orBusy(BarItem.button(leader ? DISBAND : LEAVE, ending,
                    new ItemStack(Items.wooden_door)).tip(ending).ending(),
                    busy));
        }
        return items;
    }

    /**
     * The invite field: its list offers who can be invited while it is
     * typed in, narrowed by what is typed. Greyed, its hint saying why,
     * for anyone but the leader of a party with room.
     */
    private BarItem inviteItem(PartyStateSnapshot snapshot, PartySnapshot party,
                               boolean leader) {
        if (this.inviteField == null) {
            WindowScreen screen = WindowScreen.current();
            if (screen == null) {
                return BarItem.words("");
            }
            this.inviteField = screen.makeField();
            this.inviteField.setMaxStringLength(MAX_NAME);
            this.inviteField.setEnableBackgroundDrawing(false);
        }
        String why = !leader
                ? I18n.format("gui.losttales.party.leader_invites_only")
                : party.isFull() ? I18n.format("gui.losttales.party.full")
                : snapshot.getInviteTargets().isEmpty()
                        ? I18n.format("gui.losttales.party.invite.nobody") : "";
        BarItem field = BarItem.field(INVITE, this.inviteField,
                why.length() > 0 ? why
                        : I18n.format("gui.losttales.party.invite.hint"));
        if (why.length() > 0) {
            releaseField();
            return field.unavailable(why);
        }
        if (!this.inviteFocused) {
            return field;
        }
        List<String> names = new ArrayList<String>();
        for (PartyInviteTargetSnapshot target : offeredTargets()) {
            names.add(target.getCharacterName().equals(target.getPlayerName())
                    ? target.getCharacterName()
                    : target.getCharacterName() + " (" + target.getPlayerName()
                            + ")");
        }
        return field.offers(names, names.isEmpty() ? -1
                : Math.min(this.offered, names.size() - 1));
    }

    /** The players the field offers: those who can be invited whose names hold what is typed. */
    private List<PartyInviteTargetSnapshot> offeredTargets() {
        PartyStateSnapshot snapshot = getSnapshot();
        if (snapshot == null || this.inviteField == null) {
            return Collections.emptyList();
        }
        PageSearch search = PageSearch.of(this.inviteField.getText());
        List<PartyInviteTargetSnapshot> shown =
                new ArrayList<PartyInviteTargetSnapshot>();
        for (PartyInviteTargetSnapshot target : snapshot.getInviteTargets()) {
            if (search.matches(target.getCharacterName(),
                    target.getPlayerName())) {
                shown.add(target);
            }
        }
        return shown;
    }

    private static BarItem orBusy(BarItem item, String why) {
        return why.length() == 0 ? item : item.unavailable(why);
    }

    @Override
    public void barPressed(String id, int offer) {
        PartyStateSnapshot snapshot = getSnapshot();
        if (REFRESH.equals(id)) {
            this.statusMessage = "";
            beginRequest(PartyClientRequestManager.requestState(), false);
            return;
        }
        if (snapshot == null || this.pendingRequestId != 0) {
            return;
        }
        if (INVITE.equals(id)) {
            List<PartyInviteTargetSnapshot> targets = offeredTargets();
            if (offer >= 0 && offer < targets.size()) {
                invite(targets.get(offer));
                return;
            }
            this.inviteFocused = true;
            this.inviteField.setFocused(true);
            this.offered = 0;
            return;
        }
        PartySnapshot party = snapshot.getParty();
        UUID active = snapshot.getActiveCharacterId();
        if (CREATE.equals(id)) {
            beginRequest(PartyClientRequestManager.createParty(active), true);
            return;
        }
        Row picked = picked(rows(snapshot));
        if (picked != null && picked.invitation != null) {
            UUID invitation = picked.invitation.getInvitationId();
            if (ACCEPT.equals(id)) {
                beginRequest(PartyClientRequestManager.acceptInvitation(
                        active, invitation), true);
            } else if (DECLINE.equals(id)) {
                beginRequest(PartyClientRequestManager.declineInvitation(
                        active, invitation), true);
            } else if (CANCEL_INVITE.equals(id) && party != null) {
                beginRequest(PartyClientRequestManager.cancelInvitation(active,
                        party.getPartyId(), party.getRevision(), invitation),
                        true);
            }
        }
        if (party == null) {
            return;
        }
        if (GO_HERE.equals(id)) {
            beginRequest(ClientPartyTrackingCache.hasLocalGoHereMarker(snapshot)
                    ? PartyClientRequestManager.removeGoHereMarker(active,
                            party.getPartyId(), party.getRevision())
                    : PartyClientRequestManager.setGoHereMarker(active,
                            party.getPartyId(), party.getRevision()), true);
        } else if (LEAVE.equals(id) || DISBAND.equals(id)) {
            askAndDo(LEAVE.equals(id) ? "leave" : "disband", "",
                    LEAVE.equals(id) ? "gui.losttales.party.leave"
                            : "gui.losttales.party.disband", id, party, active,
                    null);
        } else if (picked != null && picked.member != null
                && (MAKE_LEADER.equals(id) || REMOVE.equals(id))) {
            askAndDo(MAKE_LEADER.equals(id) ? "transfer" : "remove",
                    picked.member.getCharacterName(),
                    MAKE_LEADER.equals(id) ? "gui.losttales.party.make_leader"
                            : "gui.losttales.party.remove", id, party, active,
                    picked.member.getCharacterId());
        }
    }

    /**
     * Asks before an action that cannot be undone, in a question inside the
     * window; on the player's yes it is sent with the party as it stood
     * when asked, so a party changed since is refused by the server.
     */
    private void askAndDo(String question, String name, String confirmKey,
                          final String action, PartySnapshot party,
                          final UUID active, final UUID target) {
        WindowScreen screen = WindowScreen.current();
        if (screen == null) {
            return;
        }
        final UUID partyId = party.getPartyId();
        final long revision = party.getRevision();
        screen.ask(WindowPages.tab(PAGE_ID),
                I18n.format("gui.losttales.party.confirm." + question
                        + ".title", name),
                I18n.format("gui.losttales.party.confirm." + question
                        + ".detail"),
                I18n.format(confirmKey),
                new Runnable() {
                    @Override
                    public void run() {
                        if (LEAVE.equals(action)) {
                            beginRequest(PartyClientRequestManager.leaveParty(
                                    active, partyId, revision), true);
                        } else if (DISBAND.equals(action)) {
                            beginRequest(PartyClientRequestManager.disbandParty(
                                    active, partyId, revision), true);
                        } else if (REMOVE.equals(action)) {
                            beginRequest(PartyClientRequestManager.removeMember(
                                    active, partyId, revision, target), true);
                        } else if (MAKE_LEADER.equals(action)) {
                            beginRequest(PartyClientRequestManager
                                    .transferLeadership(active, partyId,
                                            revision, target), true);
                        }
                    }
                });
    }

    private void invite(PartyInviteTargetSnapshot target) {
        PartyStateSnapshot snapshot = getSnapshot();
        PartySnapshot party = snapshot == null ? null : snapshot.getParty();
        if (party == null || target == null || this.pendingRequestId != 0) {
            return;
        }
        beginRequest(PartyClientRequestManager.invitePlayer(
                snapshot.getActiveCharacterId(), party.getPartyId(),
                party.getRevision(), target.getOwnerId()), true);
        this.inviteField.setText("");
        this.offered = 0;
    }

    /* ---- The snapshot ---- */

    private static PartyStateSnapshot getSnapshot() {
        return ClientPartyStateCache.getState()
                == ClientPartyStateCache.SyncState.READY
                ? ClientPartyStateCache.getSnapshot() : null;
    }

    private static PartyMemberSnapshot ownMember(PartyStateSnapshot snapshot) {
        PartySnapshot party = snapshot == null ? null : snapshot.getParty();
        return party == null ? null
                : party.getMember(snapshot.getActiveCharacterId());
    }

    private static boolean isColorInUseByAnother(
            PartySnapshot party, UUID localCharacterId, PartyColor color) {
        if (party == null || color == null) {
            return false;
        }
        for (PartyMemberSnapshot member : party.getMembers()) {
            if (member.getColor() == color
                    && !member.getCharacterId().equals(localCharacterId)) {
                return true;
            }
        }
        return false;
    }

    private static String formatExpiration(
            PartyInvitationSnapshot invitation, long now) {
        long remaining = Math.max(0L, invitation.getExpiresAt() - now);
        long seconds = (remaining + 999L) / 1000L;
        long minutes = seconds / 60L;
        seconds %= 60L;
        if (minutes > 0L) {
            return I18n.format("gui.losttales.party.expires_minutes",
                    Long.valueOf(minutes), Long.valueOf(seconds));
        }
        return I18n.format("gui.losttales.party.expires_seconds",
                Long.valueOf(seconds));
    }
}
