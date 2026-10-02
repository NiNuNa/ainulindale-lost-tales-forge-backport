package com.ninuna.losttales.gui.screen.fellowship;

import com.ninuna.losttales.client.window.PageKeys;
import com.ninuna.losttales.client.window.OptionGlyph;
import com.ninuna.losttales.client.window.PageOption;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.fellowship.ClientFellowshipDisplayNames;
import com.ninuna.losttales.client.fellowship.ClientFellowshipIcons;
import com.ninuna.losttales.client.fellowship.ClientFellowshipStateCache;
import com.ninuna.losttales.client.fellowship.ClientFellowshipTrackingCache;
import com.ninuna.losttales.client.fellowship.FellowshipClientRequestManager;
import com.ninuna.losttales.client.window.BarItem;
import com.ninuna.losttales.client.window.PageContent;
import com.ninuna.losttales.client.window.PageSearch;
import com.ninuna.losttales.client.window.WindowPages;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiClip;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipSwitch;
import com.ninuna.losttales.fellowship.server.FellowshipErrorId;
import com.ninuna.losttales.fellowship.sync.FellowshipInvitationSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipInviteTargetSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipMemberPresence;
import com.ninuna.losttales.fellowship.sync.FellowshipMemberSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipOperationFeedback;
import com.ninuna.losttales.fellowship.sync.FellowshipSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipStateSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
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
 * The fellowships of the character played, a page a window holds: one
 * list, as a member list is, with the invitations to the player first,
 * then each fellowship under its name with its members and the invitations
 * it sent. A row picked picks its fellowship, the travelling one while
 * nothing is picked, and the rest of the window acts on it: the search in
 * its tool strip's well narrows the list, the strip holds the colour the
 * player wears in it and its three switches, and the input bar holds the
 * invite field, then what can be done about the row picked and the
 * fellowship, and Leave (Disband for the leader) last in red.
 *
 * <p>It draws only synchronized snapshots and never changes a fellowship
 * on its own; every action is checked again by the server. The state
 * comes by itself: the server sends it whenever it changes, and the page
 * asks for it when it comes on screen, when the invite field opens, and
 * every few seconds while the server has not answered. An action that
 * cannot be undone asks first, in a question inside the window.</p>
 */
public final class FellowshipPage extends PageContent {
    /** The code name the page is registered and remembered under. */
    public static final String PAGE_ID = "fellowship";

    private static final int MARGIN = 8;
    private static final int ROW_HEIGHT = 14;
    private static final int HEADING_HEIGHT = 15;
    private static final int NOTE_LINE = 10;
    /** The member's colour, a small square before the name. */
    private static final int CHIP = 6;
    private static final int NAME_X = CHIP + 5;
    /** The longest name the invite field takes. */
    private static final int MAX_NAME = 32;
    /** Ticks between asks while the server has not answered with the state. */
    private static final int RETRY_TICKS = 100;

    /** The bar's items: their ids, which the page is told when one is pressed. */
    private static final String INVITE = "invite";
    private static final String CREATE = "create";
    private static final String MAKE_LEADER = "make_leader";
    private static final String GUIDE = "guide";
    private static final String REMOVE = "remove";
    private static final String ACCEPT = "accept";
    private static final String DECLINE = "decline";
    private static final String CANCEL_INVITE = "cancel_invite";
    private static final String TRAVEL = "travel";
    private static final String ICON = "icon";
    private static final String GO_HERE = "go_here";
    private static final String LEAVE = "leave";
    private static final String DISBAND = "disband";
    private static final String NAME = "name";
    private static final String NAME_FIELD = "name_field";
    /** The strip's switches: the switch's own id follows. */
    private static final String SWITCH = "switch:";

    /**
     * The switches' glyphs, patterns until their artwork is painted:
     * crossed blades, a shield, an eye.
     */
    private static final OptionGlyph NO_FIGHTING_GLYPH = OptionGlyph.pattern(
            "#...#",
            ".#.#.",
            "..#..",
            ".#.#.",
            "#...#");
    private static final OptionGlyph NO_HIRED_HARM_GLYPH = OptionGlyph.pattern(
            "#####",
            "#...#",
            "#...#",
            ".#.#.",
            "..#..");
    private static final OptionGlyph SHOWN_ON_MAP_GLYPH = OptionGlyph.pattern(
            ".###.",
            "#.#.#",
            ".###.");

    /** What a row of the list is. */
    private enum Kind { HEADING, FELLOWSHIP, NOTE, MEMBER, INCOMING, OUTGOING }

    /** What the name field is typed for. */
    private enum Naming { NONE, CREATE, RENAME }

    private final Minecraft mc = Minecraft.getMinecraft();
    private FontRenderer font;
    private int width = -1;
    private int height = -1;

    private int pendingRequestId;
    /** The page's last ask for the state; 0 before it has asked. */
    private int stateRequestId;
    /** Ticks since the page last asked, while the state is not there. */
    private int ticksSinceAsked;
    private UUID knownActiveIdentityId;

    /** The row picked: what kind, in which fellowship, and whose id; null kind for none. */
    private Kind pickedKind;
    private UUID pickedFellowshipId;
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
    /** The name field, which stands in the invite field's place while a name is typed. */
    private GuiTextField nameField;
    private Naming naming = Naming.NONE;
    private boolean nameFocused;

    /** One row of the list, as drawn and as hit. */
    private static final class Row {
        final Kind kind;
        final String text;
        /** The fellowship the row belongs to; null for an invitation to the player, a heading or a note. */
        final FellowshipSnapshot fellowship;
        final FellowshipMemberSnapshot member;
        final FellowshipInvitationSnapshot invitation;
        final int height;

        Row(Kind kind, String text, FellowshipSnapshot fellowship,
            FellowshipMemberSnapshot member,
            FellowshipInvitationSnapshot invitation, int height) {
            this.kind = kind;
            this.text = text;
            this.fellowship = fellowship;
            this.member = member;
            this.invitation = invitation;
            this.height = height;
        }

        UUID fellowshipId() {
            return this.fellowship == null ? null : this.fellowship.getFellowshipId();
        }

        UUID id() {
            if (this.member != null) {
                return this.member.getIdentityId();
            }
            if (this.invitation != null) {
                return this.invitation.getInvitationId();
            }
            return fellowshipId();
        }

        boolean pickable() {
            return this.kind != Kind.HEADING && this.kind != Kind.NOTE;
        }

        boolean is(Kind kind, UUID fellowshipId, UUID id) {
            return this.kind == kind && same(fellowshipId(), fellowshipId)
                    && same(id(), id);
        }

        private static boolean same(UUID left, UUID right) {
            return left == null ? right == null : left.equals(right);
        }
    }

    /* ---- The list ---- */

    /**
     * The list's rows, the search held: the invitations to the player
     * under a heading while there are any, then each fellowship under its
     * name with its members and the invitations it sent; a note where the
     * character is in no fellowship.
     */
    private List<Row> rows(FellowshipStateSnapshot snapshot) {
        List<Row> rows = new ArrayList<Row>();
        if (snapshot == null || !snapshot.isAvailable() || this.font == null) {
            return rows;
        }
        PageSearch search = PageSearch.of(this.query);
        List<Row> incoming = new ArrayList<Row>();
        for (FellowshipInvitationSnapshot invitation : snapshot.getIncomingInvitations()) {
            if (search.matches(invitation.getFellowshipName(),
                    invitation.getInvitingCharacterName())) {
                incoming.add(new Row(Kind.INCOMING, I18n.format(
                        "gui.losttales.fellowship.invitation.row",
                        invitation.getFellowshipName(),
                        invitation.getInvitingCharacterName()),
                        null, null, invitation, ROW_HEIGHT));
            }
        }
        if (!incoming.isEmpty()) {
            rows.add(heading(I18n.format("gui.losttales.fellowship.heading.incoming",
                    Integer.valueOf(incoming.size()))));
            rows.addAll(incoming);
        }
        if (snapshot.getFellowships().isEmpty()) {
            FellowshipErrorId refusal = snapshot.getCreateRefusal();
            addNote(rows, I18n.format("gui.losttales.fellowship.no_fellowship") + " "
                    + (refusal == FellowshipErrorId.NONE
                    ? I18n.format("gui.losttales.fellowship.no_fellowship_detail")
                    : ClientFellowshipDisplayNames.error(refusal)));
        }
        for (FellowshipSnapshot fellowship : snapshot.getFellowships()) {
            boolean named = search.matches(fellowship.getName());
            List<Row> inside = new ArrayList<Row>();
            for (FellowshipMemberSnapshot member : fellowship.getMembers()) {
                if (named || search.matches(member.getCharacterName())) {
                    inside.add(new Row(Kind.MEMBER, member.getCharacterName(),
                            fellowship, member, null, ROW_HEIGHT));
                }
            }
            for (FellowshipInvitationSnapshot invitation : snapshot.getOutgoingInvitations()) {
                if (invitation.getFellowshipId().equals(fellowship.getFellowshipId())
                        && (named || search.matches(invitation.getTargetCharacterName()))) {
                    inside.add(new Row(Kind.OUTGOING, I18n.format(
                            "gui.losttales.fellowship.invited",
                            invitation.getTargetCharacterName()),
                            fellowship, null, invitation, ROW_HEIGHT));
                }
            }
            if (named || !inside.isEmpty()) {
                rows.add(new Row(Kind.FELLOWSHIP, I18n.format(
                        "gui.losttales.fellowship.heading.fellowship",
                        fellowship.getName(),
                        Integer.valueOf(fellowship.getMemberCount()),
                        Integer.valueOf(snapshot.getMemberLimit())),
                        fellowship, null, null, HEADING_HEIGHT));
                rows.addAll(inside);
            }
        }
        if (snapshot.isIncomingTruncated() || snapshot.isOutgoingTruncated()) {
            addNote(rows, I18n.format(
                    "gui.losttales.fellowship.invitation_list_truncated"));
        }
        if (this.query.length() > 0 && found(rows) == 0) {
            addNote(rows, I18n.format("gui.losttales.fellowship.search.none"));
        }
        return rows;
    }

    private static Row heading(String text) {
        return new Row(Kind.HEADING, text, null, null, null, HEADING_HEIGHT);
    }

    /** A note in the aside tone, as many lines as it wraps to. */
    private void addNote(List<Row> rows, String text) {
        int lines = this.font.listFormattedStringToWidth(text,
                Math.max(1, this.width - 2 * MARGIN)).size();
        rows.add(new Row(Kind.NOTE, text, null, null, null,
                Math.max(1, lines) * NOTE_LINE + 4));
    }

    private static int found(List<Row> rows) {
        int count = 0;
        for (Row row : rows) {
            if (row.kind == Kind.MEMBER || row.kind == Kind.INCOMING
                    || row.kind == Kind.OUTGOING) {
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

    /** The room the list has: the page less its margins. */
    private int listHeight() {
        return Math.max(ROW_HEIGHT, this.height - 2 * MARGIN);
    }

    /* ---- Life ---- */

    @Override
    public void tick() {
        if (this.width < 0) {
            return;
        }
        handlePendingOperation();
        synchronizeSelection();
        if (getSnapshot() == null) {
            this.ticksSinceAsked++;
            if (this.ticksSinceAsked >= RETRY_TICKS) {
                askForState();
            }
        }
        if (this.inviteField != null) {
            this.inviteField.updateCursorCounter();
        }
        if (this.nameField != null) {
            this.nameField.updateCursorCounter();
        }
    }

    /** The page on screen asks for the state, so what it shows is fresh. */
    @Override
    public void shown() {
        if (this.width >= 0) {
            askForState();
        }
    }

    /** Asks the server for the state, unless the page's last ask is still out. */
    private void askForState() {
        if (this.stateRequestId != 0
                && ClientFellowshipStateCache.isRequestPending(this.stateRequestId)) {
            return;
        }
        this.ticksSinceAsked = 0;
        this.stateRequestId = FellowshipClientRequestManager.requestState();
    }

    private void handlePendingOperation() {
        if (this.pendingRequestId == 0
                || ClientFellowshipStateCache.isRequestPending(this.pendingRequestId)) {
            return;
        }
        int completedRequestId = this.pendingRequestId;
        this.pendingRequestId = 0;
        FellowshipOperationFeedback feedback =
                ClientFellowshipStateCache.getOperation(completedRequestId);
        if (feedback == null) {
            return;
        }
        ClientFellowshipStateCache.clearOperation(completedRequestId);
        // The answer stands on the page, over its bar (W2).
        if (feedback.isSuccessful()) {
            sayDone(ClientFellowshipDisplayNames.operationSuccess(
                    feedback.getOperationType()));
        } else {
            sayRefused(ClientFellowshipDisplayNames.error(feedback.getErrorId()));
        }
    }

    /**
     * Keeps the pick on a row that stands: the travelling fellowship when
     * the character changes or the pick is gone, else the first invitation
     * to the player, else nothing.
     */
    private void synchronizeSelection() {
        FellowshipStateSnapshot snapshot = getSnapshot();
        UUID active = snapshot == null ? null : snapshot.getActiveIdentityId();
        if (active != null && !active.equals(this.knownActiveIdentityId)) {
            this.knownActiveIdentityId = active;
            this.pickedKind = null;
            this.scroll = 0;
            stopNaming();
            clearAnswer();
        }
        if (snapshot == null || picked(rows(snapshot)) != null) {
            return;
        }
        FellowshipSnapshot travelling = snapshot.getTravellingFellowship();
        if (travelling != null) {
            pick(Kind.FELLOWSHIP, travelling.getFellowshipId(),
                    travelling.getFellowshipId());
        } else if (!snapshot.getIncomingInvitations().isEmpty()) {
            pick(Kind.INCOMING, null, snapshot.getIncomingInvitations().get(0)
                    .getInvitationId());
        } else {
            this.pickedKind = null;
            this.pickedFellowshipId = null;
            this.pickedId = null;
        }
    }

    private void pick(Kind kind, UUID fellowshipId, UUID id) {
        this.pickedKind = kind;
        this.pickedFellowshipId = fellowshipId;
        this.pickedId = id;
    }

    /** The row picked among these; null while none of them is. */
    private Row picked(List<Row> rows) {
        if (this.pickedKind == null) {
            return null;
        }
        for (Row row : rows) {
            if (row.pickable() && row.is(this.pickedKind, this.pickedFellowshipId,
                    this.pickedId)) {
                return row;
            }
        }
        return null;
    }

    /** The fellowship the picked row belongs to, else the travelling one; null for none. */
    private FellowshipSnapshot current(FellowshipStateSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        FellowshipSnapshot picked = snapshot.getFellowship(this.pickedFellowshipId);
        return picked != null ? picked : snapshot.getTravellingFellowship();
    }

    private void beginRequest(int requestId, boolean showWorkingStatus) {
        this.pendingRequestId = requestId;
        if (showWorkingStatus) {
            sayWorking(I18n.format("gui.losttales.fellowship.working"));
        }
    }

    private boolean isPending() {
        return this.pendingRequestId != 0
                && ClientFellowshipStateCache.isRequestPending(this.pendingRequestId);
    }

    /* ---- Drawing ---- */

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     float partialTicks, int alpha) {
        this.font = minecraft.fontRenderer;
        this.width = (int)Math.floor(box.width);
        this.height = (int)Math.floor(box.height);
        if (this.stateRequestId == 0) {
            askForState();
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
        FellowshipStateSnapshot snapshot = getSnapshot();
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
    }

    /**
     * What the page says while it has no state: the server's reason and
     * that it asks again, or that the state is on its way.
     */
    private static String unavailableMessage(FellowshipStateSnapshot snapshot) {
        if (snapshot != null) {
            return ClientFellowshipDisplayNames.error(snapshot.getStateErrorId())
                    + " " + I18n.format("gui.losttales.fellowship.asking_again");
        }
        return ClientFellowshipStateCache.getState()
                == ClientFellowshipStateCache.SyncState.ERROR
                ? ClientFellowshipDisplayNames.error(FellowshipErrorId.INTERNAL_ERROR)
                        + " " + I18n.format("gui.losttales.fellowship.asking_again")
                : I18n.format("gui.losttales.fellowship.loading");
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

    private void drawRow(FellowshipStateSnapshot snapshot, Row row, int y,
                         boolean picked, boolean hovered) {
        int left = MARGIN;
        int right = this.width - MARGIN;
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
            Gui.drawRect(left - 2, y, right, y + row.height, picked
                    ? LostTalesColors.withAlpha(LostTalesColors.PLUM_GRAY, 0xB4)
                    : LostTalesColors.withAlpha(LostTalesColors.PLUM_DARK, 0x72));
        }
        String aside = asideOf(snapshot, row);
        int asideWidth = aside.length() == 0 ? 0
                : this.font.getStringWidth(aside) + 4;
        if (row.kind == Kind.HEADING || row.kind == Kind.FELLOWSHIP) {
            String name = LostTalesSkyrimUiStyle.uppercase(row.text);
            int top = y + LostTalesUiInk.centredStart(HEADING_HEIGHT, 7);
            name = this.font.trimStringToWidth(name,
                    Math.max(0, right - left - asideWidth));
            LostTalesUiInk.drawText(this.font, name, left, top, picked
                    ? LostTalesUiInk.IVORY : LostTalesColors.rgb(LostTalesColors.TEXT), 0xFF);
            int ruleLeft = left + this.font.getStringWidth(name) + 5;
            int ruleRight = right - asideWidth;
            if (ruleLeft < ruleRight) {
                Gui.drawRect(ruleLeft, top + 3, ruleRight, top + 4,
                        LostTalesColors.BORDER_DIM);
            }
            if (aside.length() > 0) {
                LostTalesUiInk.drawText(this.font, aside,
                        right - this.font.getStringWidth(aside), top,
                        WindowStyle.asideRgb(), 0xFF);
            }
            return;
        }
        int textTop = y + LostTalesUiInk.centredStart(ROW_HEIGHT, 7);
        boolean away = row.member != null
                && row.member.getPresence() != FellowshipMemberPresence.HERE;
        if (row.kind == Kind.MEMBER) {
            int chipTop = y + (ROW_HEIGHT - CHIP) / 2;
            FellowshipColor colour = row.member.getColor();
            LostTalesUiInk.fillRect(left + 1, chipTop + 1, left + CHIP + 1,
                    chipTop + CHIP + 1, LostTalesUiInk.argb(
                            LostTalesUiInk.SHADOW, 0xFF));
            LostTalesUiInk.fillRect(left, chipTop, left + CHIP,
                    chipTop + CHIP, LostTalesUiInk.argb(colour == null
                            ? LostTalesUiInk.IVORY : colour.getRgb(), 0xFF));
        }
        int nameX = row.kind == Kind.MEMBER ? left + NAME_X : left;
        int nameRgb = picked ? LostTalesUiInk.IVORY
                : away || row.kind == Kind.OUTGOING ? WindowStyle.asideRgb()
                : LostTalesColors.rgb(LostTalesColors.TEXT);
        LostTalesUiInk.drawText(this.font, this.font.trimStringToWidth(
                        row.text, Math.max(0, right - nameX - asideWidth)),
                nameX, textTop, nameRgb, 0xFF);
        if (aside.length() > 0) {
            boolean expired = row.invitation != null
                    && row.invitation.isExpired(System.currentTimeMillis());
            LostTalesUiInk.drawText(this.font, aside,
                    right - this.font.getStringWidth(aside), textTop,
                    expired ? LostTalesColors.rgb(LostTalesColors.RED)
                            : WindowStyle.asideRgb(), 0xFF);
        }
    }

    /**
     * What stands at a row's right: Travelling on the fellowship travelled
     * with; Leader or Guide, You, and Away or the character played instead
     * on a member; the time left on an invitation.
     */
    private static String asideOf(FellowshipStateSnapshot snapshot, Row row) {
        if (row.kind == Kind.FELLOWSHIP) {
            FellowshipSnapshot travelling = snapshot.getTravellingFellowship();
            return travelling != null && travelling.getFellowshipId()
                    .equals(row.fellowshipId())
                    ? I18n.format("gui.losttales.fellowship.travelling") : "";
        }
        if (row.kind == Kind.MEMBER) {
            List<String> words = new ArrayList<String>(3);
            UUID identityId = row.member.getIdentityId();
            if (row.fellowship.isLeader(identityId)) {
                words.add(I18n.format("gui.losttales.fellowship.leader"));
            } else if (row.fellowship.isGuide(identityId)) {
                words.add(I18n.format("gui.losttales.fellowship.guide"));
            }
            if (identityId.equals(snapshot.getActiveIdentityId())) {
                words.add(I18n.format("gui.losttales.fellowship.you"));
            } else if (row.member.getPresence() == FellowshipMemberPresence.AWAY) {
                words.add(I18n.format("gui.losttales.fellowship.away"));
            } else if (row.member.getPresence() == FellowshipMemberPresence.ELSEWHERE) {
                words.add(row.member.getElsewhereName().length() == 0
                        ? I18n.format("gui.losttales.fellowship.elsewhere")
                        : I18n.format("gui.losttales.fellowship.playing_as",
                                row.member.getElsewhereName()));
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
            pickRow(row);
            return true;
        }
        return false;
    }

    /** Picks a row; one in another fellowship ends a rename. */
    private void pickRow(Row row) {
        if (this.naming == Naming.RENAME && row.fellowshipId() != null
                && !row.fellowshipId().equals(this.pickedFellowshipId)) {
            stopNaming();
        }
        pick(row.kind, row.fellowshipId(), row.id());
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
        Row picked = picked(rows);
        int top = 0;
        for (Row row : rows) {
            if (row == picked) {
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

    /** While a field of the bar is typed in it keeps every key, the pages' keys among them. */
    @Override
    public boolean holdsKeys() {
        return this.inviteFocused || this.nameFocused;
    }

    @Override
    public void focusChanged(boolean hasKeys) {
        if (!hasKeys) {
            releaseField();
        }
    }

    /** The keys the fellowship answers to, for its help. */
    @Override
    public List<PageKeys.Area> keyAreas() {
        return PageKeys.pageArea("gui.losttales.page.fellowship",
                PageKeys.pageKey(PAGE_ID, "pick",
                        Keyboard.KEY_UP, PageKeys.OR, Keyboard.KEY_DOWN),
                PageKeys.pageKey(PAGE_ID, "offered",
                        Keyboard.KEY_UP, PageKeys.OR, Keyboard.KEY_DOWN),
                PageKeys.pageKey(PAGE_ID, "complete", Keyboard.KEY_TAB),
                PageKeys.pageKey(PAGE_ID, "invite", Keyboard.KEY_RETURN),
                PageKeys.pageKey(PAGE_ID, "name", Keyboard.KEY_RETURN),
                PageKeys.pageKey(PAGE_ID, "leave_field", Keyboard.KEY_ESCAPE),
                PageKeys.pageKey(PAGE_ID, "wheel", PageKeys.WHEEL));
    }

    /**
     * The invite field's keys while it is typed in: the arrows walk the
     * players it offers, Tab writes the chosen one's name, Return invites
     * them, Escape leaves the field. The name field's: Return makes the
     * fellowship or saves its name, Escape goes back to the invite field.
     * Otherwise the arrows walk the list's rows.
     */
    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (this.width < 0) {
            return false;
        }
        if (this.nameFocused && this.nameField != null) {
            return typeInName(typedChar, keyCode);
        }
        if (this.inviteFocused && this.inviteField != null) {
            return typeInField(typedChar, keyCode);
        }
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
            walkRows(keyCode == Keyboard.KEY_UP ? -1 : 1);
            return true;
        }
        return false;
    }

    private boolean typeInField(char typedChar, int keyCode) {
        List<FellowshipInviteTargetSnapshot> targets = offeredTargets();
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
        FellowshipInviteTargetSnapshot chosen = this.offered >= 0
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

    private boolean typeInName(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            stopNaming();
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            saveName();
            return true;
        }
        this.nameField.textboxKeyTyped(typedChar, keyCode);
        return true;
    }

    /**
     * Sends the name typed: a new fellowship's, or the picked fellowship's
     * as it stood when the leader typed it. Then back to the invite field.
     */
    private void saveName() {
        FellowshipStateSnapshot snapshot = getSnapshot();
        if (snapshot == null || this.pendingRequestId != 0) {
            return;
        }
        String name = this.nameField.getText().trim();
        if (this.naming == Naming.CREATE) {
            beginRequest(FellowshipClientRequestManager.createFellowship(
                    snapshot.getActiveIdentityId(), name), true);
        } else {
            FellowshipSnapshot fellowship = current(snapshot);
            if (fellowship == null) {
                return;
            }
            beginRequest(FellowshipClientRequestManager.renameFellowship(
                    snapshot.getActiveIdentityId(), fellowship.getFellowshipId(),
                    fellowship.getRevision(), name), true);
        }
        stopNaming();
    }

    /** The name field takes the invite field's place, filled with {@code name}. */
    private void startNaming(Naming why, String name) {
        releaseField();
        this.naming = why;
        this.nameFocused = true;
        this.nameField.setText(name);
        this.nameField.setFocused(true);
        this.nameField.setCursorPositionEnd();
    }

    private void stopNaming() {
        this.naming = Naming.NONE;
        this.nameFocused = false;
        if (this.nameField != null) {
            this.nameField.setFocused(false);
        }
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
        pickRow(pickable.get(next));
        scrollToPicked(rows);
    }

    private void releaseField() {
        this.inviteFocused = false;
        if (this.inviteField != null) {
            this.inviteField.setFocused(false);
        }
        this.nameFocused = false;
        if (this.nameField != null) {
            this.nameField.setFocused(false);
        }
    }

    /* ---- The window's strip ---- */

    /**
     * The page's tab wears the colour the player wears in the fellowship
     * they travel with; the Fellowship channel's while they are in none.
     */
    @Override
    public int tone() {
        FellowshipStateSnapshot snapshot = getSnapshot();
        FellowshipMemberSnapshot member = ownMember(snapshot,
                snapshot == null ? null : snapshot.getTravellingFellowship());
        return member == null || member.getColor() == null
                ? ChatChannel.FELLOWSHIP.getDisplayColor()
                : member.getColor().getRgb();
    }

    /**
     * For the picked fellowship: the colours the player can wear in it,
     * each a chip, theirs chosen, one another member wears greyed; then its
     * three switches, greyed for whoever may not set them.
     */
    @Override
    public List<PageOption> options() {
        FellowshipStateSnapshot snapshot = getSnapshot();
        FellowshipSnapshot fellowship = current(snapshot);
        FellowshipMemberSnapshot own = ownMember(snapshot, fellowship);
        String none = fellowship == null
                ? I18n.format("gui.losttales.fellowship.no_fellowship") : "";
        List<PageOption> options = new ArrayList<PageOption>();
        for (FellowshipColor colour : FellowshipColor.values()) {
            String why = none.length() > 0 ? none
                    : !isColorAvailable(fellowship, snapshot.getActiveIdentityId(), colour)
                    ? I18n.format("gui.losttales.fellowship.colour.taken") : "";
            options.add(PageOption.choice(colour.name(),
                    ClientFellowshipDisplayNames.color(colour),
                    own != null && own.getColor() == colour,
                    OptionGlyph.chip(colour.getRgb())).unavailable(why)
                    .inGroup("colours", "gui.losttales.fellowship.menu.colour"));
        }
        for (FellowshipSwitch fellowshipSwitch : FellowshipSwitch.values()) {
            String why = none.length() > 0 ? none : whyNotSet(snapshot, fellowship,
                    fellowshipSwitch);
            options.add(PageOption.toggle(SWITCH + fellowshipSwitch.getId(),
                    I18n.format("gui.losttales.fellowship.switch."
                            + fellowshipSwitch.getId()),
                    fellowship != null && fellowship.isOn(fellowshipSwitch),
                    glyphOf(fellowshipSwitch)).unavailable(why)
                    .inGroup("switches", "gui.losttales.fellowship.menu.switches"));
        }
        return options;
    }

    /** Why the player may not set a switch: the map's is the leader's, the others the leader's and the guides'. */
    private static String whyNotSet(FellowshipStateSnapshot snapshot,
                                    FellowshipSnapshot fellowship,
                                    FellowshipSwitch fellowshipSwitch) {
        UUID active = snapshot.getActiveIdentityId();
        if (fellowshipSwitch.isLeaderOnly()) {
            return fellowship.isLeader(active) ? ""
                    : ClientFellowshipDisplayNames.error(FellowshipErrorId.NOT_LEADER);
        }
        return fellowship.canManage(active) ? ""
                : ClientFellowshipDisplayNames.error(FellowshipErrorId.NOT_LEADER_OR_GUIDE);
    }

    private static OptionGlyph glyphOf(FellowshipSwitch fellowshipSwitch) {
        switch (fellowshipSwitch) {
            case NO_FIGHTING:
                return NO_FIGHTING_GLYPH;
            case NO_HIRED_HARM:
                return NO_HIRED_HARM_GLYPH;
            default:
                return SHOWN_ON_MAP_GLYPH;
        }
    }

    @Override
    public boolean takeOption(String id) {
        FellowshipStateSnapshot snapshot = getSnapshot();
        FellowshipSnapshot fellowship = current(snapshot);
        if (fellowship == null || this.pendingRequestId != 0) {
            return true;
        }
        UUID active = snapshot.getActiveIdentityId();
        for (FellowshipColor colour : FellowshipColor.values()) {
            if (colour.name().equals(id)
                    && isColorAvailable(fellowship, active, colour)) {
                beginRequest(FellowshipClientRequestManager.setColor(active,
                        fellowship.getFellowshipId(), fellowship.getRevision(), colour), true);
            }
        }
        for (FellowshipSwitch fellowshipSwitch : FellowshipSwitch.values()) {
            if ((SWITCH + fellowshipSwitch.getId()).equals(id)
                    && whyNotSet(snapshot, fellowship, fellowshipSwitch).length() == 0) {
                beginRequest(FellowshipClientRequestManager.setSwitch(active,
                        fellowship.getFellowshipId(), fellowship.getRevision(),
                        fellowshipSwitch, !fellowship.isOn(fellowshipSwitch)), true);
            }
        }
        return true;
    }

    @Override
    public String searchPrompt() {
        return I18n.format("gui.losttales.fellowship.search");
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
                    pickRow(row);
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
     * The invite field (the name field while a name is typed), Create
     * Fellowship, the leader's Name Fellowship, then what can be done about
     * the row picked, Travel With Fellowship, the icon, the go-here marker,
     * and Leave (Disband for the leader) last in red. Bare while the page
     * has no state.
     */
    @Override
    public List<BarItem> barItems() {
        FellowshipStateSnapshot snapshot = getSnapshot();
        List<BarItem> items = new ArrayList<BarItem>(10);
        if (snapshot == null) {
            return items;
        }
        FellowshipSnapshot fellowship = current(snapshot);
        UUID active = snapshot.getActiveIdentityId();
        boolean leader = fellowship != null && fellowship.isLeader(active);
        boolean manages = fellowship != null && fellowship.canManage(active);
        String busy = isPending() ? I18n.format("gui.losttales.fellowship.working")
                : "";
        if (this.naming == Naming.RENAME && !leader) {
            stopNaming();
        }
        if (this.naming != Naming.NONE) {
            items.add(nameItem());
        } else if (fellowship != null) {
            items.add(inviteItem(snapshot, fellowship, manages));
        }
        String create = I18n.format("gui.losttales.fellowship.create");
        FellowshipErrorId refusal = snapshot.getCreateRefusal();
        items.add(orBusy(BarItem.button(CREATE, create,
                LostTalesUiSheet.PLUS, LostTalesUiSheet.PLUS_ADD)
                .tip(create).lit(this.naming == Naming.CREATE),
                refusal == FellowshipErrorId.NONE ? busy
                        : ClientFellowshipDisplayNames.error(refusal)));
        if (leader) {
            String name = I18n.format("gui.losttales.fellowship.name");
            items.add(orBusy(BarItem.button(NAME, name,
                    LostTalesUiSheet.DRAFT, LostTalesUiSheet.DRAFT_HOVER)
                    .tip(name).lit(this.naming == Naming.RENAME), busy));
        }
        addPickedItems(items, snapshot, picked(rows(snapshot)), leader, manages, busy);
        if (fellowship == null) {
            return items;
        }
        boolean travelling = fellowship.getFellowshipId().equals(
                snapshot.getTravellingFellowship().getFellowshipId());
        String travel = I18n.format("gui.losttales.fellowship.travel");
        items.add(orBusy(BarItem.button(TRAVEL, travel, new ItemStack(Items.compass))
                .tip(travel).lit(travelling), travelling
                ? I18n.format("gui.losttales.fellowship.travelling_already") : busy));
        String icon = I18n.format("gui.losttales.fellowship.icon");
        ItemStack worn = ClientFellowshipIcons.stackOf(fellowship.getIcon());
        items.add(orBusy(BarItem.button(ICON, icon,
                worn != null ? worn : new ItemStack(Items.item_frame))
                .tip(icon), manages ? busy
                : ClientFellowshipDisplayNames.error(FellowshipErrorId.NOT_LEADER_OR_GUIDE)));
        String goHere = I18n.format(ClientFellowshipTrackingCache
                .hasLocalGoHereMarker(snapshot)
                ? "gui.losttales.fellowship.remove_go_here"
                : "gui.losttales.fellowship.set_go_here");
        items.add(orBusy(BarItem.button(GO_HERE, goHere,
                LostTalesUiSheet.MAP_MARKER,
                LostTalesUiSheet.MAP_MARKER_HOVER).tip(goHere), busy));
        String ending = I18n.format(leader ? "gui.losttales.fellowship.disband"
                : "gui.losttales.fellowship.leave");
        items.add(orBusy(BarItem.button(leader ? DISBAND : LEAVE, ending,
                new ItemStack(Items.wooden_door)).tip(ending).ending(),
                busy));
        return items;
    }

    /**
     * What can be done about the row picked: a member other than the player
     * made leader, made a guide or no guide, or removed; an invitation to
     * the player answered; one the fellowship sent taken back.
     */
    private void addPickedItems(List<BarItem> items, FellowshipStateSnapshot snapshot,
                                Row picked, boolean leader, boolean manages,
                                String busy) {
        if (picked == null) {
            return;
        }
        String notLeader = leader ? busy
                : ClientFellowshipDisplayNames.error(FellowshipErrorId.NOT_LEADER);
        String notManager = manages ? busy
                : ClientFellowshipDisplayNames.error(FellowshipErrorId.NOT_LEADER_OR_GUIDE);
        if (picked.kind == Kind.MEMBER
                && !picked.member.getIdentityId().equals(snapshot.getActiveIdentityId())) {
            UUID member = picked.member.getIdentityId();
            boolean memberLeads = picked.fellowship.isLeader(member);
            String makeLeader = I18n.format("gui.losttales.fellowship.make_leader");
            items.add(orBusy(BarItem.button(MAKE_LEADER, makeLeader,
                    new ItemStack(Items.golden_helmet)).tip(makeLeader), notLeader));
            if (!memberLeads) {
                boolean guide = picked.fellowship.isGuide(member);
                String guideWords = I18n.format(guide
                        ? "gui.losttales.fellowship.unmake_guide"
                        : "gui.losttales.fellowship.make_guide");
                items.add(orBusy(BarItem.button(GUIDE, guideWords,
                        new ItemStack(Items.iron_helmet)).tip(guideWords).lit(guide),
                        notLeader));
            }
            String remove = I18n.format("gui.losttales.fellowship.remove");
            items.add(orBusy(BarItem.button(REMOVE, remove,
                    LostTalesUiSheet.CLOSE, LostTalesUiSheet.CLOSE_HOVER)
                    .tip(remove), memberLeads ? ClientFellowshipDisplayNames.error(
                            FellowshipErrorId.CANNOT_REMOVE_LEADER) : notManager));
        } else if (picked.kind == Kind.INCOMING) {
            String accept = I18n.format("gui.losttales.fellowship.accept");
            items.add(orBusy(BarItem.button(ACCEPT, accept,
                    LostTalesUiSheet.SEND, LostTalesUiSheet.SEND_HOVER)
                    .tip(accept), busy));
            String decline = I18n.format("gui.losttales.fellowship.decline");
            items.add(orBusy(BarItem.button(DECLINE, decline,
                    LostTalesUiSheet.CLOSE, LostTalesUiSheet.CLOSE_HOVER)
                    .tip(decline), busy));
        } else if (picked.kind == Kind.OUTGOING) {
            String cancel = I18n.format("gui.losttales.fellowship.cancel_invite");
            items.add(orBusy(BarItem.button(CANCEL_INVITE, cancel,
                    LostTalesUiSheet.CLOSE, LostTalesUiSheet.CLOSE_HOVER)
                    .tip(cancel), notManager));
        }
    }

    /**
     * The invite field: its list offers who can be invited into the picked
     * fellowship while it is typed in, narrowed by what is typed. Greyed,
     * its hint saying why, for anyone but its leader and guides, and while
     * it is full.
     */
    private BarItem inviteItem(FellowshipStateSnapshot snapshot, FellowshipSnapshot fellowship,
                               boolean manages) {
        if (this.inviteField == null) {
            WindowScreen screen = WindowScreen.current();
            if (screen == null) {
                return BarItem.words("");
            }
            this.inviteField = screen.makeField();
            this.inviteField.setMaxStringLength(MAX_NAME);
            this.inviteField.setEnableBackgroundDrawing(false);
        }
        String why = !manages
                ? I18n.format("gui.losttales.fellowship.leader_invites_only")
                : snapshot.isFull(fellowship) ? I18n.format(
                        "gui.losttales.fellowship.full",
                        Integer.valueOf(snapshot.getMemberLimit()))
                : snapshot.getInviteTargets(fellowship).isEmpty()
                        ? I18n.format("gui.losttales.fellowship.invite.nobody") : "";
        BarItem field = BarItem.field(INVITE, this.inviteField,
                why.length() > 0 ? why
                        : I18n.format("gui.losttales.fellowship.invite.hint",
                                fellowship.getName()));
        if (why.length() > 0) {
            releaseField();
            return field.unavailable(why);
        }
        if (!this.inviteFocused) {
            return field;
        }
        List<String> names = new ArrayList<String>();
        for (FellowshipInviteTargetSnapshot target : offeredTargets()) {
            names.add(target.getCharacterName().equals(target.getPlayerName())
                    ? target.getCharacterName()
                    : target.getCharacterName() + " (" + target.getPlayerName()
                            + ")");
        }
        return field.offers(names, names.isEmpty() ? -1
                : Math.min(this.offered, names.size() - 1));
    }

    /** The name field, made the first time a name is typed. */
    private BarItem nameItem() {
        if (this.nameField == null) {
            WindowScreen screen = WindowScreen.current();
            if (screen == null) {
                return BarItem.words("");
            }
            this.nameField = screen.makeField();
            this.nameField.setMaxStringLength(Fellowship.MAX_NAME_LENGTH);
            this.nameField.setEnableBackgroundDrawing(false);
        }
        return BarItem.field(NAME_FIELD, this.nameField, I18n.format(
                this.naming == Naming.CREATE ? "gui.losttales.fellowship.create.hint"
                        : "gui.losttales.fellowship.name.hint"));
    }

    /** The players the field offers: those the picked fellowship may invite whose names hold what is typed. */
    private List<FellowshipInviteTargetSnapshot> offeredTargets() {
        FellowshipStateSnapshot snapshot = getSnapshot();
        FellowshipSnapshot fellowship = current(snapshot);
        if (fellowship == null || this.inviteField == null) {
            return Collections.emptyList();
        }
        PageSearch search = PageSearch.of(this.inviteField.getText());
        List<FellowshipInviteTargetSnapshot> shown =
                new ArrayList<FellowshipInviteTargetSnapshot>();
        for (FellowshipInviteTargetSnapshot target : snapshot.getInviteTargets(fellowship)) {
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
        FellowshipStateSnapshot snapshot = getSnapshot();
        if (snapshot == null || this.pendingRequestId != 0) {
            return;
        }
        if (INVITE.equals(id)) {
            List<FellowshipInviteTargetSnapshot> targets = offeredTargets();
            if (offer >= 0 && offer < targets.size()) {
                invite(targets.get(offer));
                return;
            }
            // Who is online changes while nothing about the fellowship does.
            if (!this.inviteFocused) {
                askForState();
            }
            this.inviteFocused = true;
            this.inviteField.setFocused(true);
            this.offered = 0;
            return;
        }
        if (NAME_FIELD.equals(id) && this.nameField != null) {
            this.nameFocused = true;
            this.nameField.setFocused(true);
            return;
        }
        FellowshipSnapshot fellowship = current(snapshot);
        if (CREATE.equals(id) || NAME.equals(id)) {
            Naming why = CREATE.equals(id) ? Naming.CREATE : Naming.RENAME;
            if (this.naming == why) {
                stopNaming();
                return;
            }
            nameItem();
            if (this.nameField != null && (why == Naming.CREATE || fellowship != null)) {
                startNaming(why, why == Naming.CREATE ? "" : fellowship.getName());
            }
            return;
        }
        UUID active = snapshot.getActiveIdentityId();
        Row picked = picked(rows(snapshot));
        if (picked != null && picked.invitation != null) {
            UUID invitation = picked.invitation.getInvitationId();
            if (ACCEPT.equals(id)) {
                beginRequest(FellowshipClientRequestManager.acceptInvitation(
                        active, invitation), true);
            } else if (DECLINE.equals(id)) {
                beginRequest(FellowshipClientRequestManager.declineInvitation(
                        active, invitation), true);
            } else if (CANCEL_INVITE.equals(id) && picked.fellowship != null) {
                beginRequest(FellowshipClientRequestManager.cancelInvitation(active,
                        picked.fellowship.getFellowshipId(),
                        picked.fellowship.getRevision(), invitation), true);
            }
        }
        if (fellowship == null) {
            return;
        }
        UUID fellowshipId = fellowship.getFellowshipId();
        long revision = fellowship.getRevision();
        if (TRAVEL.equals(id)) {
            beginRequest(FellowshipClientRequestManager.setTravelling(
                    active, fellowshipId, revision), true);
        } else if (ICON.equals(id)) {
            beginRequest(FellowshipClientRequestManager.setIcon(
                    active, fellowshipId, revision), true);
        } else if (GO_HERE.equals(id)) {
            beginRequest(ClientFellowshipTrackingCache.hasLocalGoHereMarker(snapshot)
                    ? FellowshipClientRequestManager.removeGoHereMarker(active)
                    : FellowshipClientRequestManager.setGoHereMarker(active), true);
        } else if (LEAVE.equals(id) || DISBAND.equals(id)) {
            askAndDo(LEAVE.equals(id) ? "leave" : "disband", fellowship.getName(),
                    LEAVE.equals(id) ? "gui.losttales.fellowship.leave"
                            : "gui.losttales.fellowship.disband", id, fellowship, active,
                    null);
        } else if (picked != null && picked.member != null
                && fellowshipId.equals(picked.fellowshipId())) {
            UUID member = picked.member.getIdentityId();
            if (GUIDE.equals(id)) {
                beginRequest(FellowshipClientRequestManager.setGuide(active,
                        fellowshipId, revision, member,
                        !fellowship.isGuide(member)), true);
            } else if (MAKE_LEADER.equals(id) || REMOVE.equals(id)) {
                askAndDo(MAKE_LEADER.equals(id) ? "transfer" : "remove",
                        picked.member.getCharacterName(),
                        MAKE_LEADER.equals(id) ? "gui.losttales.fellowship.make_leader"
                                : "gui.losttales.fellowship.remove", id, fellowship,
                        active, member);
            }
        }
    }

    /**
     * Asks before an action that cannot be undone, in a question inside the
     * window; on the player's yes it is sent with the fellowship as it stood
     * when asked, so a fellowship changed since is refused by the server.
     */
    private void askAndDo(String question, String name, String confirmKey,
                          final String action, FellowshipSnapshot fellowship,
                          final UUID active, final UUID target) {
        WindowScreen screen = WindowScreen.current();
        if (screen == null) {
            return;
        }
        final UUID fellowshipId = fellowship.getFellowshipId();
        final long revision = fellowship.getRevision();
        screen.ask(WindowPages.tab(PAGE_ID),
                I18n.format("gui.losttales.fellowship.confirm." + question
                        + ".title", name),
                I18n.format("gui.losttales.fellowship.confirm." + question
                        + ".detail"),
                I18n.format(confirmKey),
                new Runnable() {
                    @Override
                    public void run() {
                        if (LEAVE.equals(action)) {
                            beginRequest(FellowshipClientRequestManager.leaveFellowship(
                                    active, fellowshipId, revision), true);
                        } else if (DISBAND.equals(action)) {
                            beginRequest(FellowshipClientRequestManager.disbandFellowship(
                                    active, fellowshipId, revision), true);
                        } else if (REMOVE.equals(action)) {
                            beginRequest(FellowshipClientRequestManager.removeMember(
                                    active, fellowshipId, revision, target), true);
                        } else if (MAKE_LEADER.equals(action)) {
                            beginRequest(FellowshipClientRequestManager
                                    .transferLeadership(active, fellowshipId,
                                            revision, target), true);
                        }
                    }
                });
    }

    private void invite(FellowshipInviteTargetSnapshot target) {
        FellowshipStateSnapshot snapshot = getSnapshot();
        FellowshipSnapshot fellowship = current(snapshot);
        if (fellowship == null || target == null || this.pendingRequestId != 0) {
            return;
        }
        beginRequest(FellowshipClientRequestManager.invitePlayer(
                snapshot.getActiveIdentityId(), fellowship.getFellowshipId(),
                fellowship.getRevision(), target.getOwnerId()), true);
        this.inviteField.setText("");
        this.offered = 0;
    }

    /* ---- The snapshot ---- */

    private static FellowshipStateSnapshot getSnapshot() {
        return ClientFellowshipStateCache.getState()
                == ClientFellowshipStateCache.SyncState.READY
                ? ClientFellowshipStateCache.getSnapshot() : null;
    }

    private static FellowshipMemberSnapshot ownMember(FellowshipStateSnapshot snapshot,
                                                      FellowshipSnapshot fellowship) {
        return snapshot == null || fellowship == null ? null
                : fellowship.getMember(snapshot.getActiveIdentityId());
    }

    /**
     * Whether the player may wear a colour in a fellowship: one no other
     * member wears, or any once every colour is worn by another, as the
     * server rules.
     */
    private static boolean isColorAvailable(FellowshipSnapshot fellowship,
                                            UUID localIdentityId, FellowshipColor color) {
        Set<FellowshipColor> wornByOthers = EnumSet.noneOf(FellowshipColor.class);
        for (FellowshipMemberSnapshot member : fellowship.getMembers()) {
            if (!member.getIdentityId().equals(localIdentityId)) {
                wornByOthers.add(member.getColor());
            }
        }
        return !wornByOthers.contains(color)
                || wornByOthers.size() >= FellowshipColor.values().length;
    }

    private static String formatExpiration(
            FellowshipInvitationSnapshot invitation, long now) {
        long remaining = Math.max(0L, invitation.getExpiresAt() - now);
        long seconds = (remaining + 999L) / 1000L;
        long minutes = seconds / 60L;
        seconds %= 60L;
        if (minutes > 0L) {
            return I18n.format("gui.losttales.fellowship.expires_minutes",
                    Long.valueOf(minutes), Long.valueOf(seconds));
        }
        return I18n.format("gui.losttales.fellowship.expires_seconds",
                Long.valueOf(seconds));
    }
}
