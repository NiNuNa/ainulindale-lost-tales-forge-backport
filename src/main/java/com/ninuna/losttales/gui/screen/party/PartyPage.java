package com.ninuna.losttales.gui.screen.party;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.window.PageContent;
import com.ninuna.losttales.client.window.PageSearch;
import com.ninuna.losttales.client.party.ClientPartyDisplayNames;
import com.ninuna.losttales.client.party.ClientPartyStateCache;
import com.ninuna.losttales.client.party.ClientPartyTrackingCache;
import com.ninuna.losttales.client.party.PartyClientRequestManager;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
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
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * The party, a page a chat window holds: its members, the invitations and
 * the players who can be invited, each a list with what can be done about
 * the one chosen. The window's search narrows every list to the names
 * that hold its words. It draws only synchronized snapshots and never
 * changes the party on its own; every action is checked again by the
 * server. An action that cannot be undone asks first, inside the page.
 */
public final class PartyPage extends PageContent {
    /** The code name the page is registered and remembered under. */
    public static final String PAGE_ID = "party";

    private static final int BUTTON_REFRESH = 2;
    private static final int BUTTON_CONFIRM = 40;
    private static final int BUTTON_CANCEL = 41;
    /** Where the tab buttons stand below the page's top. */
    private static final int TABS_TOP = 4;
    private static final int BUTTON_TAB_MEMBERS = 10;
    private static final int BUTTON_TAB_INVITATIONS = 11;
    private static final int BUTTON_TAB_INVITE = 12;
    private static final int BUTTON_ACTION_ONE = 20;
    private static final int BUTTON_ACTION_TWO = 21;
    private static final int BUTTON_ACTION_THREE = 22;
    private static final int BUTTON_ACTION_FOUR = 23;
    private static final int BUTTON_COLOR_GREEN = 30;
    private static final int BUTTON_COLOR_BLUE = 33;

    private static final int ROW_HEIGHT = 30;

    private enum Tab {
        MEMBERS,
        INVITATIONS,
        INVITE
    }

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Gui gui = new Gui();
    private final List<GuiButton> buttonList = new ArrayList<GuiButton>();
    private FontRenderer fontRendererObj;
    /** The page's size as it was last laid out; -1 before the first. */
    private int width = -1;
    private int height = -1;
    /** An action waiting on the player's answer; null while none asks. */
    private Runnable confirming;
    private String confirmTitle = "";
    private String confirmDetail = "";
    private GuiButton confirmButton;
    private GuiButton cancelButton;

    private Tab tab = Tab.MEMBERS;
    private int pendingRequestId;
    private boolean initialRequestSent;
    private String statusMessage = "";
    private boolean statusError;
    private UUID knownActiveCharacterId;

    private UUID selectedMemberCharacterId;
    private UUID selectedInvitationId;
    private boolean selectedInvitationIncoming;
    private UUID selectedInviteOwnerId;

    private int membersScroll;
    private int invitationsScroll;
    private int inviteTargetsScroll;
    /** The words in the window's well; empty while its search is closed. */
    private String query = "";

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;
    private int detailsX;
    private int detailsY;
    private int detailsWidth;
    private int detailsHeight;

    private GuiButton membersTabButton;
    private GuiButton invitationsTabButton;
    private GuiButton inviteTabButton;
    private GuiButton refreshButton;
    private GuiButton actionOneButton;
    private GuiButton actionTwoButton;
    private GuiButton actionThreeButton;
    private GuiButton actionFourButton;
    private final GuiButton[] colorButtons = new GuiButton[PartyColor.values().length];

    /**
     * Lays the page out for a box {@code width} by {@code height}: the tab
     * buttons along its top, the list and what is chosen in it in one
     * panel, the actions along its foot. Laid out again whenever the box
     * changes size.
     */
    private void layOutPage(int width, int height) {
        this.width = width;
        this.height = height;
        this.buttonList.clear();
        calculateLayout();

        int tabWidth = Math.min(132, Math.max(80, (this.panelWidth - 20) / 3));
        int tabsTotal = tabWidth * 3;
        int tabsX = this.width / 2 - tabsTotal / 2;
        this.membersTabButton = new GuiButton(BUTTON_TAB_MEMBERS,
                tabsX, TABS_TOP, tabWidth, 20,
                I18n.format("gui.losttales.party.tab.members"));
        this.invitationsTabButton = new GuiButton(BUTTON_TAB_INVITATIONS,
                tabsX + tabWidth, TABS_TOP, tabWidth, 20,
                I18n.format("gui.losttales.party.tab.invitations"));
        this.inviteTabButton = new GuiButton(BUTTON_TAB_INVITE,
                tabsX + tabWidth * 2, TABS_TOP, tabWidth, 20,
                I18n.format("gui.losttales.party.tab.invite"));
        this.buttonList.add(this.membersTabButton);
        this.buttonList.add(this.invitationsTabButton);
        this.buttonList.add(this.inviteTabButton);

        int gap = 6;
        int footerWidth = Math.max(52, (this.width - 16 - gap * 2) / 3);
        int footerY = this.height - 28;
        this.refreshButton = new GuiButton(BUTTON_REFRESH,
                8, footerY, footerWidth, 20,
                I18n.format("gui.losttales.party.refresh"));
        this.buttonList.add(this.refreshButton);

        int actionThreeX = 8 + footerWidth + gap;
        int actionFourX = 8 + (footerWidth + gap) * 2;
        this.actionThreeButton = new GuiButton(BUTTON_ACTION_THREE,
                actionThreeX, footerY, footerWidth, 20, "");
        this.actionFourButton = new GuiButton(BUTTON_ACTION_FOUR,
                actionFourX, footerY, footerWidth, 20, "");
        this.buttonList.add(this.actionThreeButton);
        this.buttonList.add(this.actionFourButton);

        int upperY = this.height - 52;
        this.actionOneButton = new GuiButton(BUTTON_ACTION_ONE,
                actionThreeX, upperY, footerWidth, 20, "");
        this.actionTwoButton = new GuiButton(BUTTON_ACTION_TWO,
                actionFourX, upperY, footerWidth, 20, "");
        this.buttonList.add(this.actionOneButton);
        this.buttonList.add(this.actionTwoButton);

        int colorGap = 4;
        int colorWidth = Math.max(24,
                (this.detailsWidth - colorGap * 3) / 4);
        int colorY = this.detailsY + this.detailsHeight - 22;
        PartyColor[] colors = PartyColor.values();
        for (int index = 0; index < colors.length; index++) {
            GuiButton button = new GuiButton(BUTTON_COLOR_GREEN + index,
                    this.detailsX + index * (colorWidth + colorGap),
                    colorY, colorWidth, 20,
                    ClientPartyDisplayNames.color(colors[index]));
            this.colorButtons[index] = button;
            this.buttonList.add(button);
        }

        int confirmY = this.panelY + this.panelHeight / 2 + 12;
        this.confirmButton = new GuiButton(BUTTON_CONFIRM,
                this.width / 2 - 102, confirmY, 100, 20,
                I18n.format("gui.losttales.party.confirm"));
        this.cancelButton = new GuiButton(BUTTON_CANCEL,
                this.width / 2 + 2, confirmY, 100, 20,
                I18n.format("gui.cancel"));

        if (!this.initialRequestSent && this.pendingRequestId == 0) {
            this.initialRequestSent = true;
            beginRequest(PartyClientRequestManager.requestState(), false);
        }
        synchronizeSelection();
        updateButtons();
    }

    private void calculateLayout() {
        this.panelWidth = Math.min(680, Math.max(280, this.width - 20));
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = TABS_TOP + 24;
        int panelBottom = Math.max(this.panelY + 90, this.height - 72);
        this.panelHeight = panelBottom - this.panelY;

        int padding = 10;
        int innerWidth = this.panelWidth - padding * 2;
        this.listWidth = Math.max(118, innerWidth * 56 / 100);
        this.detailsWidth = innerWidth - this.listWidth - 10;
        if (this.detailsWidth < 110) {
            this.detailsWidth = 110;
            this.listWidth = innerWidth - this.detailsWidth - 10;
        }
        this.listX = this.panelX + padding;
        this.listY = this.panelY + 22;
        this.listHeight = Math.max(48, this.panelHeight - 34);
        this.detailsX = this.listX + this.listWidth + 10;
        this.detailsY = this.listY;
        this.detailsHeight = this.listHeight;
    }

    @Override
    public void tick() {
        if (this.width < 0) {
            return;
        }
        handlePendingOperation();
        synchronizeSelection();
        updateButtons();
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

    private void synchronizeSelection() {
        PartyStateSnapshot snapshot = getSnapshot();
        UUID activeCharacterId = snapshot == null
                ? null : snapshot.getActiveCharacterId();
        if (activeCharacterId != null
                && !activeCharacterId.equals(this.knownActiveCharacterId)) {
            this.knownActiveCharacterId = activeCharacterId;
            this.selectedMemberCharacterId = activeCharacterId;
            this.selectedInvitationId = null;
            this.selectedInviteOwnerId = null;
            this.membersScroll = 0;
            this.invitationsScroll = 0;
            this.inviteTargetsScroll = 0;
            this.statusMessage = "";
        }
        if (snapshot == null) {
            return;
        }

        PartySnapshot party = snapshot.getParty();
        if (party == null) {
            this.selectedMemberCharacterId = null;
        } else if (this.selectedMemberCharacterId == null
                || party.getMember(this.selectedMemberCharacterId) == null) {
            this.selectedMemberCharacterId = snapshot.getActiveCharacterId();
            if (party.getMember(this.selectedMemberCharacterId) == null
                    && !party.getMembers().isEmpty()) {
                this.selectedMemberCharacterId =
                        party.getMembers().get(0).getCharacterId();
            }
        }

        List<InvitationEntry> invitationEntries = getInvitationEntries(snapshot);
        if (findInvitation(invitationEntries, this.selectedInvitationId,
                this.selectedInvitationIncoming) == null) {
            if (invitationEntries.isEmpty()) {
                this.selectedInvitationId = null;
            } else {
                InvitationEntry first = invitationEntries.get(0);
                this.selectedInvitationId = first.invitation.getInvitationId();
                this.selectedInvitationIncoming = first.incoming;
            }
        }

        if (findInviteTarget(snapshot.getInviteTargets(),
                this.selectedInviteOwnerId) == null) {
            this.selectedInviteOwnerId = snapshot.getInviteTargets().isEmpty()
                    ? null : snapshot.getInviteTargets().get(0).getOwnerId();
        }
        clampScrollOffsets(snapshot);
    }

    private void updateButtons() {
        PartyStateSnapshot snapshot = getSnapshot();
        boolean pending = this.pendingRequestId != 0
                && ClientPartyStateCache.isRequestPending(this.pendingRequestId);
        PartySnapshot party = snapshot == null ? null : snapshot.getParty();
        UUID activeCharacterId = snapshot == null
                ? null : snapshot.getActiveCharacterId();
        boolean leader = party != null && party.isLeader(activeCharacterId);

        this.membersTabButton.enabled = this.tab != Tab.MEMBERS;
        this.invitationsTabButton.enabled = this.tab != Tab.INVITATIONS;
        this.inviteTabButton.enabled = this.tab != Tab.INVITE;
        this.refreshButton.enabled = !pending;

        hideActionButtons();
        hideColorButtons();
        if (snapshot == null || !snapshot.isAvailable()) {
            return;
        }

        if (this.tab == Tab.MEMBERS) {
            if (party == null) {
                configureButton(this.actionOneButton,
                        I18n.format("gui.losttales.party.create"), !pending);
                return;
            }
            configureButton(this.actionOneButton,
                    I18n.format("gui.losttales.party.leave"), !pending);
            if (leader) {
                configureButton(this.actionTwoButton,
                        I18n.format("gui.losttales.party.disband"), !pending);
            }
            PartyMemberSnapshot selected = party.getMember(
                    this.selectedMemberCharacterId);
            if (selected != null
                    && selected.getCharacterId().equals(activeCharacterId)) {
                configureButton(this.actionThreeButton,
                        I18n.format("gui.losttales.party.set_go_here"),
                        !pending);
                configureButton(this.actionFourButton,
                        I18n.format("gui.losttales.party.remove_go_here"),
                        !pending && ClientPartyTrackingCache
                        .hasLocalGoHereMarker(snapshot));
            } else if (leader && selected != null) {
                configureButton(this.actionThreeButton,
                        I18n.format("gui.losttales.party.remove"), !pending);
                configureButton(this.actionFourButton,
                        I18n.format("gui.losttales.party.transfer"), !pending);
            }
            PartyMemberSnapshot localMember = party.getMember(activeCharacterId);
            for (int index = 0; index < this.colorButtons.length; index++) {
                PartyColor color = PartyColor.values()[index];
                GuiButton button = this.colorButtons[index];
                button.visible = true;
                boolean current = localMember != null
                        && localMember.getColor() == color;
                boolean inUse = isColorInUseByAnother(
                        party, activeCharacterId, color);
                button.enabled = !pending && !current && !inUse;
                button.displayString = ClientPartyDisplayNames.color(color);
            }
            return;
        }

        if (this.tab == Tab.INVITATIONS) {
            InvitationEntry selected = findInvitation(
                    getInvitationEntries(snapshot),
                    this.selectedInvitationId,
                    this.selectedInvitationIncoming);
            if (selected == null) {
                return;
            }
            if (selected.incoming) {
                configureButton(this.actionOneButton,
                        I18n.format("gui.losttales.party.accept"), !pending);
                configureButton(this.actionTwoButton,
                        I18n.format("gui.losttales.party.decline"), !pending);
            } else if (leader) {
                configureButton(this.actionOneButton,
                        I18n.format("gui.losttales.party.cancel_invite"),
                        !pending);
            }
            return;
        }

        PartyInviteTargetSnapshot target = findInviteTarget(
                snapshot.getInviteTargets(), this.selectedInviteOwnerId);
        boolean canInvite = leader && party != null && !party.isFull()
                && target != null && !pending;
        configureButton(this.actionOneButton,
                I18n.format("gui.losttales.party.invite"), canInvite);
    }

    private void hideActionButtons() {
        configureButton(this.actionOneButton, "", false);
        configureButton(this.actionTwoButton, "", false);
        configureButton(this.actionThreeButton, "", false);
        configureButton(this.actionFourButton, "", false);
        this.actionOneButton.visible = false;
        this.actionTwoButton.visible = false;
        this.actionThreeButton.visible = false;
        this.actionFourButton.visible = false;
    }

    private void hideColorButtons() {
        for (GuiButton button : this.colorButtons) {
            if (button != null) {
                button.visible = false;
                button.enabled = false;
            }
        }
    }

    private static void configureButton(GuiButton button,
                                        String label,
                                        boolean enabled) {
        if (button == null) {
            return;
        }
        button.visible = true;
        button.enabled = enabled;
        button.displayString = label == null ? "" : label;
    }

    private void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_CONFIRM || button.id == BUTTON_CANCEL) {
            Runnable action = this.confirming;
            this.confirming = null;
            if (button.id == BUTTON_CONFIRM && action != null) {
                action.run();
            }
            return;
        }
        if (button.id == BUTTON_REFRESH) {
            this.statusMessage = "";
            beginRequest(PartyClientRequestManager.requestState(), false);
            return;
        }
        if (button.id == BUTTON_TAB_MEMBERS) {
            this.tab = Tab.MEMBERS;
            updateButtons();
            return;
        }
        if (button.id == BUTTON_TAB_INVITATIONS) {
            this.tab = Tab.INVITATIONS;
            updateButtons();
            return;
        }
        if (button.id == BUTTON_TAB_INVITE) {
            this.tab = Tab.INVITE;
            updateButtons();
            return;
        }
        if (button.id >= BUTTON_COLOR_GREEN
                && button.id <= BUTTON_COLOR_BLUE) {
            PartyColor color = PartyColor.fromNetworkId(
                    button.id - BUTTON_COLOR_GREEN);
            setColor(color);
            return;
        }
        if (button.id == BUTTON_ACTION_ONE
                || button.id == BUTTON_ACTION_TWO
                || button.id == BUTTON_ACTION_THREE
                || button.id == BUTTON_ACTION_FOUR) {
            handleContextAction(button.id);
        }
    }

    private void handleContextAction(int buttonId) {
        PartyStateSnapshot snapshot = getSnapshot();
        if (snapshot == null || this.pendingRequestId != 0) {
            return;
        }
        PartySnapshot party = snapshot.getParty();
        if (this.tab == Tab.MEMBERS) {
            if (party == null && buttonId == BUTTON_ACTION_ONE) {
                beginRequest(PartyClientRequestManager.createParty(
                        snapshot.getActiveCharacterId()), true);
                return;
            }
            if (party == null) {
                return;
            }
            final UUID expectedActiveCharacterId =
                    snapshot.getActiveCharacterId();
            final UUID expectedPartyId = party.getPartyId();
            final long expectedPartyRevision = party.getRevision();
            if (buttonId == BUTTON_ACTION_ONE) {
                confirm(I18n.format("gui.losttales.party.confirm.leave.title"),
                        I18n.format("gui.losttales.party.confirm.leave.detail"),
                        new Runnable() {
                            @Override
                            public void run() {
                                leaveParty(expectedActiveCharacterId,
                                        expectedPartyId,
                                        expectedPartyRevision);
                            }
                        });
                return;
            }
            if (buttonId == BUTTON_ACTION_TWO) {
                confirm(I18n.format("gui.losttales.party.confirm.disband.title"),
                        I18n.format("gui.losttales.party.confirm.disband.detail"),
                        new Runnable() {
                            @Override
                            public void run() {
                                disbandParty(expectedActiveCharacterId,
                                        expectedPartyId,
                                        expectedPartyRevision);
                            }
                        });
                return;
            }
            final UUID targetCharacterId = this.selectedMemberCharacterId;
            PartyMemberSnapshot target = party.getMember(targetCharacterId);
            if (target == null) {
                return;
            }
            boolean localSelected = targetCharacterId.equals(
                    expectedActiveCharacterId);
            if (localSelected && buttonId == BUTTON_ACTION_THREE) {
                beginRequest(PartyClientRequestManager.setGoHereMarker(
                        expectedActiveCharacterId,
                        expectedPartyId,
                        expectedPartyRevision), true);
                return;
            }
            if (localSelected && buttonId == BUTTON_ACTION_FOUR) {
                beginRequest(PartyClientRequestManager.removeGoHereMarker(
                        expectedActiveCharacterId,
                        expectedPartyId,
                        expectedPartyRevision), true);
                return;
            }
            if (buttonId == BUTTON_ACTION_THREE) {
                confirm(I18n.format("gui.losttales.party.confirm.remove.title"),
                        I18n.format("gui.losttales.party.confirm.remove.detail",
                                target.getCharacterName()),
                        new Runnable() {
                            @Override
                            public void run() {
                                removeMember(expectedActiveCharacterId,
                                        expectedPartyId,
                                        expectedPartyRevision,
                                        targetCharacterId);
                            }
                        });
                return;
            }
            if (buttonId == BUTTON_ACTION_FOUR) {
                confirm(I18n.format("gui.losttales.party.confirm.transfer.title"),
                        I18n.format("gui.losttales.party.confirm.transfer.detail",
                                target.getCharacterName()),
                        new Runnable() {
                            @Override
                            public void run() {
                                transferLeadership(
                                        expectedActiveCharacterId,
                                        expectedPartyId,
                                        expectedPartyRevision,
                                        targetCharacterId);
                            }
                        });
            }
            return;
        }

        if (this.tab == Tab.INVITATIONS) {
            InvitationEntry selected = findInvitation(
                    getInvitationEntries(snapshot),
                    this.selectedInvitationId,
                    this.selectedInvitationIncoming);
            if (selected == null) {
                return;
            }
            UUID invitationId = selected.invitation.getInvitationId();
            if (selected.incoming && buttonId == BUTTON_ACTION_ONE) {
                beginRequest(PartyClientRequestManager.acceptInvitation(
                        snapshot.getActiveCharacterId(), invitationId), true);
            } else if (selected.incoming && buttonId == BUTTON_ACTION_TWO) {
                beginRequest(PartyClientRequestManager.declineInvitation(
                        snapshot.getActiveCharacterId(), invitationId), true);
            } else if (!selected.incoming && buttonId == BUTTON_ACTION_ONE
                    && party != null) {
                beginRequest(PartyClientRequestManager.cancelInvitation(
                        snapshot.getActiveCharacterId(),
                        party.getPartyId(),
                        party.getRevision(),
                        invitationId), true);
            }
            return;
        }

        if (buttonId == BUTTON_ACTION_ONE && party != null) {
            PartyInviteTargetSnapshot target = findInviteTarget(
                    snapshot.getInviteTargets(), this.selectedInviteOwnerId);
            if (target != null) {
                beginRequest(PartyClientRequestManager.invitePlayer(
                        snapshot.getActiveCharacterId(),
                        party.getPartyId(),
                        party.getRevision(),
                        target.getOwnerId()), true);
            }
        }
    }

    /** Asks before {@code action}, inside the page: nothing else answers until the player does. */
    private void confirm(String title, String detail, Runnable action) {
        this.confirmTitle = title == null ? "" : title;
        this.confirmDetail = detail == null ? "" : detail;
        this.confirming = action;
    }

    private void leaveParty(UUID expectedActiveCharacterId,
                            UUID expectedPartyId,
                            long expectedPartyRevision) {
        beginRequest(PartyClientRequestManager.leaveParty(
                expectedActiveCharacterId,
                expectedPartyId,
                expectedPartyRevision), true);
    }

    private void disbandParty(UUID expectedActiveCharacterId,
                              UUID expectedPartyId,
                              long expectedPartyRevision) {
        beginRequest(PartyClientRequestManager.disbandParty(
                expectedActiveCharacterId,
                expectedPartyId,
                expectedPartyRevision), true);
    }

    private void removeMember(UUID expectedActiveCharacterId,
                              UUID expectedPartyId,
                              long expectedPartyRevision,
                              UUID targetCharacterId) {
        if (targetCharacterId != null) {
            beginRequest(PartyClientRequestManager.removeMember(
                    expectedActiveCharacterId,
                    expectedPartyId,
                    expectedPartyRevision,
                    targetCharacterId), true);
        }
    }

    private void transferLeadership(UUID expectedActiveCharacterId,
                                    UUID expectedPartyId,
                                    long expectedPartyRevision,
                                    UUID targetCharacterId) {
        if (targetCharacterId != null) {
            beginRequest(PartyClientRequestManager.transferLeadership(
                    expectedActiveCharacterId,
                    expectedPartyId,
                    expectedPartyRevision,
                    targetCharacterId), true);
        }
    }

    private void setColor(PartyColor color) {
        PartyStateSnapshot snapshot = getSnapshot();
        PartySnapshot party = snapshot == null ? null : snapshot.getParty();
        if (party != null && color != null && this.pendingRequestId == 0) {
            beginRequest(PartyClientRequestManager.setColor(
                    snapshot.getActiveCharacterId(),
                    party.getPartyId(),
                    party.getRevision(),
                    color), true);
        }
    }

    private void beginRequest(int requestId, boolean showWorkingStatus) {
        this.pendingRequestId = requestId;
        if (showWorkingStatus) {
            this.statusMessage = I18n.format("gui.losttales.party.working");
            this.statusError = false;
        }
        updateButtons();
    }

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     float partialTicks, int alpha) {
        this.fontRendererObj = minecraft.fontRenderer;
        int boxWidth = (int)Math.floor(box.width);
        int boxHeight = (int)Math.floor(box.height);
        if (boxWidth != this.width || boxHeight != this.height) {
            layOutPage(boxWidth, boxHeight);
        }
        int mouseX = pageX(box, pointerX);
        int mouseY = pageY(box, pointerY);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef((float)box.left, (float)box.top, 0.0F);
            drawPage(this.confirming == null ? mouseX : Integer.MIN_VALUE / 2,
                    this.confirming == null ? mouseY : Integer.MIN_VALUE / 2);
            if (this.confirming != null) {
                drawConfirmation(mouseX, mouseY);
            }
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

    /** The question an action waits on, over the panel, with its two answers. */
    private void drawConfirmation(int mouseX, int mouseY) {
        int panelWidth = Math.min(420, this.panelWidth - 20);
        int panelHeight = 84;
        int x = (this.width - panelWidth) / 2;
        int y = this.panelY + this.panelHeight / 2 - 44;
        LostTalesSkyrimUiStyle.drawPanel(x, y, panelWidth, panelHeight);
        this.gui.drawCenteredString(this.fontRendererObj,
                LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj,
                        this.confirmTitle, panelWidth - 24),
                this.width / 2, y + 12, LostTalesSkyrimUiStyle.TEXT_BRIGHT);
        this.gui.drawCenteredString(this.fontRendererObj,
                LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj,
                        this.confirmDetail, panelWidth - 24),
                this.width / 2, y + 30, LostTalesSkyrimUiStyle.TEXT_MUTED);
        this.confirmButton.drawButton(this.mc, mouseX, mouseY);
        this.cancelButton.drawButton(this.mc, mouseX, mouseY);
    }

    private void drawPage(int mouseX, int mouseY) {
        LostTalesSkyrimUiStyle.drawPanel(
                this.panelX, this.panelY, this.panelWidth, this.panelHeight);

        PartyStateSnapshot snapshot = ClientPartyStateCache.getSnapshot();
        if (snapshot == null) {
            String message = ClientPartyStateCache.getState()
                    == ClientPartyStateCache.SyncState.ERROR
                    ? ClientPartyDisplayNames.error(
                    PartyErrorId.INTERNAL_ERROR)
                    : I18n.format("gui.losttales.party.loading");
            drawUnavailable(message);
        } else if (!snapshot.isAvailable()) {
            drawUnavailable(ClientPartyDisplayNames.error(
                    snapshot.getStateErrorId()));
        } else if (this.tab == Tab.MEMBERS) {
            drawMembers(snapshot, mouseX, mouseY);
        } else if (this.tab == Tab.INVITATIONS) {
            drawInvitations(snapshot, mouseX, mouseY);
        } else {
            drawInviteTargets(snapshot, mouseX, mouseY);
        }

        if (this.statusMessage.length() > 0) {
            this.gui.drawCenteredString(this.fontRendererObj,
                    LostTalesSkyrimUiStyle.trimToWidth(
                            this.fontRendererObj, this.statusMessage,
                            this.width - 24),
                    this.width / 2,
                    this.height - 67,
                    this.statusError
                            ? LostTalesSkyrimUiStyle.RED
                            : LostTalesSkyrimUiStyle.GREEN);
        }
        for (GuiButton button : this.buttonList) {
            button.drawButton(this.mc, mouseX, mouseY);
        }
    }

    private void drawUnavailable(String message) {
        LostTalesSkyrimUiStyle.drawSectionHeader(this.fontRendererObj,
                I18n.format("gui.losttales.party.status"),
                this.listX, this.panelY + 10,
                this.panelWidth - 20);
        this.fontRendererObj.drawStringWithShadow(
                LostTalesSkyrimUiStyle.trimToWidth(
                        this.fontRendererObj, message, this.panelWidth - 30),
                this.listX, this.panelY + 34,
                LostTalesSkyrimUiStyle.TEXT_MUTED);
    }

    private void drawMembers(PartyStateSnapshot snapshot,
                             int mouseX, int mouseY) {
        LostTalesSkyrimUiStyle.drawSectionHeader(this.fontRendererObj,
                I18n.format("gui.losttales.party.members"),
                this.listX, this.panelY + 8, this.listWidth);
        LostTalesSkyrimUiStyle.drawSectionHeader(this.fontRendererObj,
                I18n.format("gui.losttales.party.member_details"),
                this.detailsX, this.panelY + 8, this.detailsWidth);

        PartySnapshot party = snapshot.getParty();
        if (party == null) {
            drawWrapped(I18n.format("gui.losttales.party.no_party"),
                    this.listX, this.listY + 8, this.listWidth,
                    LostTalesSkyrimUiStyle.TEXT_MUTED, 5);
            drawWrapped(I18n.format("gui.losttales.party.no_party_detail"),
                    this.detailsX, this.detailsY + 8, this.detailsWidth,
                    LostTalesSkyrimUiStyle.TEXT_MUTED, 7);
            return;
        }

        List<PartyMemberSnapshot> members = shownMembers(party);
        if (members.isEmpty()) {
            drawNoneFound();
        }
        int visibleRows = getVisibleRowCount();
        int hoveredIndex = rowAt(snapshot, mouseX, mouseY);
        for (int visible = 0; visible < visibleRows; visible++) {
            int index = this.membersScroll + visible;
            if (index >= members.size()) {
                break;
            }
            PartyMemberSnapshot member = members.get(index);
            int rowY = this.listY + visible * ROW_HEIGHT;
            boolean selected = member.getCharacterId().equals(
                    this.selectedMemberCharacterId);
            boolean hovered = index == hoveredIndex;
            LostTalesSkyrimUiStyle.drawSelectionRow(
                    this.listX, rowY, this.listWidth,
                    ROW_HEIGHT - 2, selected, hovered);
            int textX = this.listX + (selected ? 16 : 6);
            String suffix = party.isLeader(member.getCharacterId())
                    ? "  " + I18n.format("gui.losttales.party.leader_marker")
                    : "";
            if (member.getCharacterId().equals(snapshot.getActiveCharacterId())) {
                suffix += "  " + I18n.format("gui.losttales.party.you_marker");
            }
            this.fontRendererObj.drawStringWithShadow(
                    LostTalesSkyrimUiStyle.trimToWidth(
                            this.fontRendererObj,
                            member.getCharacterName() + suffix,
                            this.listWidth - (textX - this.listX) - 6),
                    textX, rowY + 5,
                    selected ? LostTalesSkyrimUiStyle.TEXT_BRIGHT
                            : LostTalesSkyrimUiStyle.TEXT);
            this.fontRendererObj.drawStringWithShadow(
                    ClientPartyDisplayNames.color(member.getColor()),
                    textX, rowY + 16,
                    getColorTextColor(member.getColor()));
        }

        PartyMemberSnapshot selected = party.getMember(
                this.selectedMemberCharacterId);
        if (selected == null) {
            return;
        }
        int lineY = this.detailsY + 8;
        lineY = drawDetailLine(I18n.format("gui.losttales.party.character"),
                selected.getCharacterName(), lineY);
        lineY = drawDetailLine(I18n.format("gui.losttales.party.role"),
                party.isLeader(selected.getCharacterId())
                        ? I18n.format("gui.losttales.party.leader")
                        : I18n.format("gui.losttales.party.member"), lineY);
        lineY = drawDetailLine(I18n.format("gui.losttales.party.color"),
                ClientPartyDisplayNames.color(selected.getColor()), lineY);
        drawDetailLine(I18n.format("gui.losttales.party.party_size"),
                party.getMemberCount() + "/4", lineY);

        if (selected.getCharacterId().equals(snapshot.getActiveCharacterId())) {
            this.fontRendererObj.drawStringWithShadow(
                    I18n.format("gui.losttales.party.choose_color"),
                    this.detailsX,
                    this.detailsY + this.detailsHeight - 34,
                    LostTalesSkyrimUiStyle.TEXT_MUTED);
        }
    }

    private void drawInvitations(PartyStateSnapshot snapshot,
                                 int mouseX, int mouseY) {
        LostTalesSkyrimUiStyle.drawSectionHeader(this.fontRendererObj,
                I18n.format("gui.losttales.party.invitations"),
                this.listX, this.panelY + 8, this.listWidth);
        LostTalesSkyrimUiStyle.drawSectionHeader(this.fontRendererObj,
                I18n.format("gui.losttales.party.invitation_details"),
                this.detailsX, this.panelY + 8, this.detailsWidth);

        List<InvitationEntry> entries = getInvitationEntries(snapshot);
        if (entries.isEmpty()) {
            drawWrapped(I18n.format("gui.losttales.party.no_invitations"),
                    this.listX, this.listY + 8, this.listWidth,
                    LostTalesSkyrimUiStyle.TEXT_MUTED, 5);
            return;
        }
        List<InvitationEntry> shown = shownInvitations(snapshot);
        if (shown.isEmpty()) {
            drawNoneFound();
        }
        int visibleRows = getVisibleRowCount();
        int hoveredIndex = rowAt(snapshot, mouseX, mouseY);
        long now = System.currentTimeMillis();
        for (int visible = 0; visible < visibleRows; visible++) {
            int index = this.invitationsScroll + visible;
            if (index >= shown.size()) {
                break;
            }
            InvitationEntry entry = shown.get(index);
            PartyInvitationSnapshot invitation = entry.invitation;
            int rowY = this.listY + visible * ROW_HEIGHT;
            boolean selected = invitation.getInvitationId().equals(
                    this.selectedInvitationId)
                    && entry.incoming == this.selectedInvitationIncoming;
            boolean hovered = index == hoveredIndex;
            LostTalesSkyrimUiStyle.drawSelectionRow(
                    this.listX, rowY, this.listWidth,
                    ROW_HEIGHT - 2, selected, hovered);
            int textX = this.listX + (selected ? 16 : 6);
            String direction = entry.incoming
                    ? I18n.format("gui.losttales.party.incoming")
                    : I18n.format("gui.losttales.party.outgoing");
            String name = entry.incoming
                    ? invitation.getInvitingCharacterName()
                    : invitation.getTargetCharacterName();
            this.fontRendererObj.drawStringWithShadow(
                    LostTalesSkyrimUiStyle.trimToWidth(
                            this.fontRendererObj,
                            direction + ": " + name,
                            this.listWidth - (textX - this.listX) - 6),
                    textX, rowY + 5,
                    selected ? LostTalesSkyrimUiStyle.TEXT_BRIGHT
                            : LostTalesSkyrimUiStyle.TEXT);
            this.fontRendererObj.drawStringWithShadow(
                    formatExpiration(invitation, now),
                    textX, rowY + 16,
                    invitation.isExpired(now)
                            ? LostTalesSkyrimUiStyle.RED
                            : LostTalesSkyrimUiStyle.TEXT_MUTED);
        }

        InvitationEntry selected = findInvitation(entries,
                this.selectedInvitationId,
                this.selectedInvitationIncoming);
        if (selected == null) {
            return;
        }
        PartyInvitationSnapshot invitation = selected.invitation;
        int lineY = this.detailsY + 8;
        lineY = drawDetailLine(I18n.format("gui.losttales.party.direction"),
                selected.incoming
                        ? I18n.format("gui.losttales.party.incoming")
                        : I18n.format("gui.losttales.party.outgoing"), lineY);
        lineY = drawDetailLine(I18n.format("gui.losttales.party.from"),
                invitation.getInvitingCharacterName(), lineY);
        lineY = drawDetailLine(I18n.format("gui.losttales.party.to"),
                invitation.getTargetCharacterName(), lineY);
        drawDetailLine(I18n.format("gui.losttales.party.expires"),
                formatExpiration(invitation, now), lineY);

        if (snapshot.isIncomingTruncated()
                || snapshot.isOutgoingTruncated()) {
            drawWrapped(I18n.format("gui.losttales.party.invitation_list_truncated"),
                    this.detailsX, this.detailsY + this.detailsHeight - 34,
                    this.detailsWidth, LostTalesSkyrimUiStyle.GOLD, 2);
        }
    }

    private void drawInviteTargets(PartyStateSnapshot snapshot,
                                   int mouseX, int mouseY) {
        LostTalesSkyrimUiStyle.drawSectionHeader(this.fontRendererObj,
                I18n.format("gui.losttales.party.eligible_players"),
                this.listX, this.panelY + 8, this.listWidth);
        LostTalesSkyrimUiStyle.drawSectionHeader(this.fontRendererObj,
                I18n.format("gui.losttales.party.invite_details"),
                this.detailsX, this.panelY + 8, this.detailsWidth);

        PartySnapshot party = snapshot.getParty();
        if (party == null) {
            drawWrapped(I18n.format("gui.losttales.party.create_before_invite"),
                    this.listX, this.listY + 8, this.listWidth,
                    LostTalesSkyrimUiStyle.TEXT_MUTED, 5);
            return;
        }
        if (!party.isLeader(snapshot.getActiveCharacterId())) {
            drawWrapped(I18n.format("gui.losttales.party.leader_invites_only"),
                    this.listX, this.listY + 8, this.listWidth,
                    LostTalesSkyrimUiStyle.TEXT_MUTED, 5);
            return;
        }
        if (party.isFull()) {
            drawWrapped(I18n.format("gui.losttales.party.full"),
                    this.listX, this.listY + 8, this.listWidth,
                    LostTalesSkyrimUiStyle.GOLD, 5);
            return;
        }

        List<PartyInviteTargetSnapshot> targets = snapshot.getInviteTargets();
        if (targets.isEmpty()) {
            drawWrapped(I18n.format("gui.losttales.party.no_eligible_players"),
                    this.listX, this.listY + 8, this.listWidth,
                    LostTalesSkyrimUiStyle.TEXT_MUTED, 5);
            return;
        }
        List<PartyInviteTargetSnapshot> shown = shownInviteTargets(snapshot);
        if (shown.isEmpty()) {
            drawNoneFound();
        }
        int visibleRows = getVisibleRowCount();
        int hoveredIndex = rowAt(snapshot, mouseX, mouseY);
        for (int visible = 0; visible < visibleRows; visible++) {
            int index = this.inviteTargetsScroll + visible;
            if (index >= shown.size()) {
                break;
            }
            PartyInviteTargetSnapshot target = shown.get(index);
            int rowY = this.listY + visible * ROW_HEIGHT;
            boolean selected = target.getOwnerId().equals(
                    this.selectedInviteOwnerId);
            boolean hovered = index == hoveredIndex;
            LostTalesSkyrimUiStyle.drawSelectionRow(
                    this.listX, rowY, this.listWidth,
                    ROW_HEIGHT - 2, selected, hovered);
            int textX = this.listX + (selected ? 16 : 6);
            this.fontRendererObj.drawStringWithShadow(
                    LostTalesSkyrimUiStyle.trimToWidth(
                            this.fontRendererObj,
                            target.getCharacterName(),
                            this.listWidth - (textX - this.listX) - 6),
                    textX, rowY + 5,
                    selected ? LostTalesSkyrimUiStyle.TEXT_BRIGHT
                            : LostTalesSkyrimUiStyle.TEXT);
            this.fontRendererObj.drawStringWithShadow(
                    LostTalesSkyrimUiStyle.trimToWidth(
                            this.fontRendererObj,
                            target.getPlayerName(),
                            this.listWidth - (textX - this.listX) - 6),
                    textX, rowY + 16,
                    LostTalesSkyrimUiStyle.TEXT_MUTED);
        }

        PartyInviteTargetSnapshot selected = findInviteTarget(
                targets, this.selectedInviteOwnerId);
        if (selected != null) {
            int lineY = this.detailsY + 8;
            lineY = drawDetailLine(I18n.format("gui.losttales.party.character"),
                    selected.getCharacterName(), lineY);
            drawDetailLine(I18n.format("gui.losttales.party.player"),
                    selected.getPlayerName(), lineY);
        }
        if (snapshot.isInviteTargetsTruncated()) {
            drawWrapped(I18n.format("gui.losttales.party.player_list_truncated"),
                    this.detailsX, this.detailsY + this.detailsHeight - 34,
                    this.detailsWidth, LostTalesSkyrimUiStyle.GOLD, 2);
        }
    }

    /** Said in a list the search has left empty. */
    private void drawNoneFound() {
        drawWrapped(I18n.format("gui.losttales.party.search.none"),
                this.listX, this.listY + 8, this.listWidth,
                LostTalesSkyrimUiStyle.TEXT_MUTED, 5);
    }

    private int drawDetailLine(String label, String value, int y) {
        String safeLabel = label == null ? "" : label;
        String safeValue = value == null ? "" : value;
        this.fontRendererObj.drawStringWithShadow(safeLabel + ":",
                this.detailsX, y, LostTalesSkyrimUiStyle.TEXT_MUTED);
        int valueX = this.detailsX
                + this.fontRendererObj.getStringWidth(safeLabel + ": ");
        this.fontRendererObj.drawStringWithShadow(
                LostTalesSkyrimUiStyle.trimToWidth(
                        this.fontRendererObj, safeValue,
                        this.detailsX + this.detailsWidth - valueX),
                valueX, y, LostTalesSkyrimUiStyle.TEXT_BRIGHT);
        return y + 13;
    }

    private void drawWrapped(String text, int x, int y, int width,
                             int color, int maximumLines) {
        List<?> lines = this.fontRendererObj.listFormattedStringToWidth(
                text == null ? "" : text, Math.max(1, width));
        int count = 0;
        for (Object value : lines) {
            if (count >= maximumLines) {
                break;
            }
            this.fontRendererObj.drawStringWithShadow(
                    String.valueOf(value), x, y + count * 11, color);
            count++;
        }
    }

    @Override
    public boolean mousePressed(Minecraft minecraft, LostTalesUiHitBox box,
                                double x, double y, int button) {
        if (this.width < 0) {
            return false;
        }
        int mouseX = pageX(box, x);
        int mouseY = pageY(box, y);
        if (this.confirming != null) {
            if (button == 0) {
                pressButton(this.confirmButton, mouseX, mouseY);
                pressButton(this.cancelButton, mouseX, mouseY);
            }
            return true;
        }
        if (button == 0 && isInside(mouseX, mouseY,
                this.listX, this.listY, this.listWidth, this.listHeight)) {
            PartyStateSnapshot snapshot = getSnapshot();
            if (snapshot != null) {
                int index = rowAt(snapshot, mouseX, mouseY);
                if (index >= 0) {
                    selectRow(snapshot, index);
                }
                updateButtons();
            }
            return true;
        }
        if (button == 0) {
            for (GuiButton candidate : new ArrayList<GuiButton>(this.buttonList)) {
                if (pressButton(candidate, mouseX, mouseY)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A button pressed where it stands: it clicks and acts; answers whether it did. */
    private boolean pressButton(GuiButton button, int mouseX, int mouseY) {
        if (button == null || !button.mousePressed(this.mc, mouseX, mouseY)) {
            return false;
        }
        button.func_146113_a(this.mc.getSoundHandler());
        actionPerformed(button);
        return true;
    }

    /**
     * The same hit tests a click takes, in the same order: the list takes
     * every click inside it, so only a row counts there. The point is the
     * one the screen's own draw and clicks see. While the party cache is
     * not ready, rows still drawn from its snapshot light under the
     * pointer but take no click, so they keep the arrow.
     */
    @Override
    public boolean acts(LostTalesUiHitBox box, double pointerX,
                        double pointerY) {
        if (this.width < 0) {
            return false;
        }
        int x = pageX(box, pointerX);
        int y = pageY(box, pointerY);
        if (this.confirming != null) {
            return overEnabled(this.confirmButton, x, y)
                    || overEnabled(this.cancelButton, x, y);
        }
        if (isInside(x, y, this.listX, this.listY,
                this.listWidth, this.listHeight)) {
            return rowAt(getSnapshot(), x, y) >= 0;
        }
        for (GuiButton button : this.buttonList) {
            if (overEnabled(button, x, y)) {
                return true;
            }
        }
        return false;
    }

    private static boolean overEnabled(GuiButton button, int x, int y) {
        return button != null && button.visible && button.enabled
                && isInside(x, y, button.xPosition, button.yPosition,
                        button.width, button.height);
    }

    /**
     * The index, in the open tab's list, of the row under the point, or -1.
     * Only a drawn row answers: none while the tab shows a message in place
     * of its list, and neither the gap under a row nor the space below the
     * last one.
     */
    private int rowAt(PartyStateSnapshot snapshot, int mouseX, int mouseY) {
        if (!isInside(mouseX, mouseY, this.listX, this.listY,
                this.listWidth, this.listHeight)) {
            return -1;
        }
        int row = (mouseY - this.listY) / ROW_HEIGHT;
        if (row >= getVisibleRowCount()
                || mouseY - this.listY - row * ROW_HEIGHT >= ROW_HEIGHT - 2) {
            return -1;
        }
        int index = getTabScroll() + row;
        return index < getShownRowCount(snapshot) ? index : -1;
    }

    private int getTabScroll() {
        if (this.tab == Tab.MEMBERS) {
            return this.membersScroll;
        }
        if (this.tab == Tab.INVITATIONS) {
            return this.invitationsScroll;
        }
        return this.inviteTargetsScroll;
    }

    /**
     * How many entries the open tab lists, the search's words held. None
     * when it shows a message in their place; the conditions are the
     * early returns of drawMembers, drawInvitations and drawInviteTargets.
     */
    private int getShownRowCount(PartyStateSnapshot snapshot) {
        if (snapshot == null || !snapshot.isAvailable()) {
            return 0;
        }
        PartySnapshot party = snapshot.getParty();
        if (this.tab == Tab.MEMBERS) {
            return party == null ? 0 : shownMembers(party).size();
        }
        if (this.tab == Tab.INVITATIONS) {
            return shownInvitations(snapshot).size();
        }
        if (party == null || !party.isLeader(snapshot.getActiveCharacterId())
                || party.isFull()) {
            return 0;
        }
        return shownInviteTargets(snapshot).size();
    }

    /** Selects the open tab's entry at an index {@link #rowAt} returned. */
    private void selectRow(PartyStateSnapshot snapshot, int index) {
        if (this.tab == Tab.MEMBERS) {
            this.selectedMemberCharacterId = shownMembers(snapshot.getParty())
                    .get(index).getCharacterId();
        } else if (this.tab == Tab.INVITATIONS) {
            InvitationEntry entry = shownInvitations(snapshot).get(index);
            this.selectedInvitationId = entry.invitation.getInvitationId();
            this.selectedInvitationIncoming = entry.incoming;
        } else {
            this.selectedInviteOwnerId = shownInviteTargets(snapshot)
                    .get(index).getOwnerId();
        }
    }

    /** Where the open tab's chosen entry stands among those shown; -1 for none. */
    private int selectedShownIndex(PartyStateSnapshot snapshot) {
        if (this.tab == Tab.MEMBERS) {
            List<PartyMemberSnapshot> members = shownMembers(snapshot.getParty());
            for (int index = 0; index < members.size(); index++) {
                if (members.get(index).getCharacterId().equals(
                        this.selectedMemberCharacterId)) {
                    return index;
                }
            }
        } else if (this.tab == Tab.INVITATIONS) {
            List<InvitationEntry> entries = shownInvitations(snapshot);
            for (int index = 0; index < entries.size(); index++) {
                InvitationEntry entry = entries.get(index);
                if (entry.incoming == this.selectedInvitationIncoming
                        && entry.invitation.getInvitationId().equals(
                                this.selectedInvitationId)) {
                    return index;
                }
            }
        } else {
            List<PartyInviteTargetSnapshot> targets =
                    shownInviteTargets(snapshot);
            for (int index = 0; index < targets.size(); index++) {
                if (targets.get(index).getOwnerId().equals(
                        this.selectedInviteOwnerId)) {
                    return index;
                }
            }
        }
        return -1;
    }

    /* ---- The window's search ---- */

    /** The members whose names hold the search's words, in the party's order. */
    private List<PartyMemberSnapshot> shownMembers(PartySnapshot party) {
        if (party == null) {
            return Collections.emptyList();
        }
        PageSearch search = PageSearch.of(this.query);
        List<PartyMemberSnapshot> shown = new ArrayList<PartyMemberSnapshot>();
        for (PartyMemberSnapshot member : party.getMembers()) {
            if (search.matches(member.getCharacterName())) {
                shown.add(member);
            }
        }
        return shown;
    }

    /** The invitations whose either name holds the search's words. */
    private List<InvitationEntry> shownInvitations(
            PartyStateSnapshot snapshot) {
        PageSearch search = PageSearch.of(this.query);
        List<InvitationEntry> shown = new ArrayList<InvitationEntry>();
        for (InvitationEntry entry : getInvitationEntries(snapshot)) {
            if (search.matches(entry.invitation.getInvitingCharacterName(),
                    entry.invitation.getTargetCharacterName())) {
                shown.add(entry);
            }
        }
        return shown;
    }

    /** The players to invite whose character or account name holds the search's words. */
    private List<PartyInviteTargetSnapshot> shownInviteTargets(
            PartyStateSnapshot snapshot) {
        PageSearch search = PageSearch.of(this.query);
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

    /**
     * The party's tab wears the colour the player wears in the party, the
     * one they chose here; the Party channel's while they are in none.
     */
    @Override
    public int tone() {
        PartyStateSnapshot snapshot = getSnapshot();
        PartySnapshot party = snapshot == null ? null : snapshot.getParty();
        PartyMemberSnapshot member = party == null ? null
                : party.getMember(snapshot.getActiveCharacterId());
        return member == null || member.getColor() == null
                ? ChatChannel.PARTY.getDisplayColor()
                : member.getColor().getRgb();
    }

    @Override
    public String searchPrompt() {
        return I18n.format("gui.losttales.party.search");
    }

    /**
     * New words read every list from its top, the first name each list
     * keeps chosen, so what is chosen is always one of the names shown.
     */
    @Override
    public void search(String words) {
        String typed = words == null ? "" : words.trim();
        if (typed.equals(this.query)) {
            return;
        }
        this.query = typed;
        this.membersScroll = 0;
        this.invitationsScroll = 0;
        this.inviteTargetsScroll = 0;
        PartyStateSnapshot snapshot = getSnapshot();
        if (snapshot == null || !snapshot.isAvailable()
                || typed.length() == 0) {
            return;
        }
        List<PartyMemberSnapshot> members = shownMembers(snapshot.getParty());
        if (!members.isEmpty()) {
            this.selectedMemberCharacterId = members.get(0).getCharacterId();
        }
        List<InvitationEntry> invitations = shownInvitations(snapshot);
        if (!invitations.isEmpty()) {
            this.selectedInvitationId =
                    invitations.get(0).invitation.getInvitationId();
            this.selectedInvitationIncoming = invitations.get(0).incoming;
        }
        List<PartyInviteTargetSnapshot> targets = shownInviteTargets(snapshot);
        if (!targets.isEmpty()) {
            this.selectedInviteOwnerId = targets.get(0).getOwnerId();
        }
        if (this.width >= 0) {
            updateButtons();
        }
    }

    @Override
    public int found() {
        return this.query.length() == 0 ? -1
                : getShownRowCount(getSnapshot());
    }

    /** The arrows walk the open list's names found; Return reads the one chosen. */
    @Override
    public boolean searchKey(int keyCode) {
        if (keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            return true;
        }
        if (keyCode != Keyboard.KEY_UP && keyCode != Keyboard.KEY_DOWN) {
            return false;
        }
        PartyStateSnapshot snapshot = getSnapshot();
        int count = getShownRowCount(snapshot);
        if (count == 0 || this.width < 0) {
            return true;
        }
        int step = keyCode == Keyboard.KEY_UP ? -1 : 1;
        int index = Math.max(0, Math.min(count - 1,
                selectedShownIndex(snapshot) + step));
        selectRow(snapshot, index);
        // The chosen row stays in view.
        int rows = getVisibleRowCount();
        int scroll = getTabScroll();
        int kept = Math.max(index - rows + 1, Math.min(scroll, index));
        if (this.tab == Tab.MEMBERS) {
            this.membersScroll = kept;
        } else if (this.tab == Tab.INVITATIONS) {
            this.invitationsScroll = kept;
        } else {
            this.inviteTargetsScroll = kept;
        }
        updateButtons();
        return true;
    }

    /** The wheel moves the open list a row a turn, whichever way it turns. */
    @Override
    public boolean scroll(LostTalesUiHitBox box, double x, double y,
                          int lines) {
        if (lines != 0 && this.width >= 0 && this.confirming == null) {
            int amount = lines > 0 ? 1 : -1;
            if (this.tab == Tab.MEMBERS) {
                this.membersScroll += amount;
            } else if (this.tab == Tab.INVITATIONS) {
                this.invitationsScroll += amount;
            } else {
                this.inviteTargetsScroll += amount;
            }
            PartyStateSnapshot snapshot = getSnapshot();
            if (snapshot != null) {
                clampScrollOffsets(snapshot);
            }
            return true;
        }
        return false;
    }

    /** A question waiting on its answer keeps every key. */
    @Override
    public boolean holdsKeys() {
        return this.confirming != null;
    }

    /**
     * The page's keys while it is the page in front: R refreshes, 1 to 3
     * choose a list. Escape takes back a question waiting on an answer,
     * and otherwise passes to the chat.
     */
    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (this.width < 0) {
            return false;
        }
        if (this.confirming != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                this.confirming = null;
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_R && this.pendingRequestId == 0) {
            beginRequest(PartyClientRequestManager.requestState(), false);
            return true;
        }
        if (keyCode == Keyboard.KEY_1) {
            this.tab = Tab.MEMBERS;
            updateButtons();
            return true;
        }
        if (keyCode == Keyboard.KEY_2) {
            this.tab = Tab.INVITATIONS;
            updateButtons();
            return true;
        }
        if (keyCode == Keyboard.KEY_3) {
            this.tab = Tab.INVITE;
            updateButtons();
            return true;
        }
        return false;
    }

    private void clampScrollOffsets(PartyStateSnapshot snapshot) {
        int visibleRows = getVisibleRowCount();
        this.membersScroll = clampScroll(this.membersScroll,
                shownMembers(snapshot.getParty()).size(), visibleRows);
        this.invitationsScroll = clampScroll(this.invitationsScroll,
                shownInvitations(snapshot).size(), visibleRows);
        this.inviteTargetsScroll = clampScroll(this.inviteTargetsScroll,
                shownInviteTargets(snapshot).size(), visibleRows);
    }

    private int getVisibleRowCount() {
        return Math.max(1, this.listHeight / ROW_HEIGHT);
    }

    private static int clampScroll(int value, int itemCount, int visibleRows) {
        int maximum = Math.max(0, itemCount - visibleRows);
        return Math.max(0, Math.min(value, maximum));
    }

    private PartyStateSnapshot getSnapshot() {
        return ClientPartyStateCache.getState()
                == ClientPartyStateCache.SyncState.READY
                ? ClientPartyStateCache.getSnapshot() : null;
    }

    private PartySnapshot getParty() {
        PartyStateSnapshot snapshot = getSnapshot();
        return snapshot == null ? null : snapshot.getParty();
    }

    private static List<InvitationEntry> getInvitationEntries(
            PartyStateSnapshot snapshot) {
        if (snapshot == null) {
            return Collections.emptyList();
        }
        ArrayList<InvitationEntry> entries = new ArrayList<InvitationEntry>();
        for (PartyInvitationSnapshot invitation
                : snapshot.getIncomingInvitations()) {
            entries.add(new InvitationEntry(invitation, true));
        }
        for (PartyInvitationSnapshot invitation
                : snapshot.getOutgoingInvitations()) {
            entries.add(new InvitationEntry(invitation, false));
        }
        return entries;
    }

    private static InvitationEntry findInvitation(
            List<InvitationEntry> entries,
            UUID invitationId,
            boolean incoming) {
        if (invitationId == null || entries == null) {
            return null;
        }
        for (InvitationEntry entry : entries) {
            if (entry != null && entry.incoming == incoming
                    && invitationId.equals(
                    entry.invitation.getInvitationId())) {
                return entry;
            }
        }
        return null;
    }

    private static PartyInviteTargetSnapshot findInviteTarget(
            List<PartyInviteTargetSnapshot> targets, UUID ownerId) {
        if (ownerId == null || targets == null) {
            return null;
        }
        for (PartyInviteTargetSnapshot target : targets) {
            if (target != null && ownerId.equals(target.getOwnerId())) {
                return target;
            }
        }
        return null;
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

    private static int getColorTextColor(PartyColor color) {
        if (color == PartyColor.GREEN) {
            return LostTalesSkyrimUiStyle.GREEN;
        }
        if (color == PartyColor.YELLOW) {
            return LostTalesSkyrimUiStyle.GOLD;
        }
        if (color == PartyColor.PURPLE) {
            return LostTalesSkyrimUiStyle.PURPLE;
        }
        return LostTalesSkyrimUiStyle.BLUE;
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

    private static boolean isInside(int mouseX, int mouseY,
                                    int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
    }

    private static final class InvitationEntry {
        private final PartyInvitationSnapshot invitation;
        private final boolean incoming;

        private InvitationEntry(PartyInvitationSnapshot invitation,
                                boolean incoming) {
            this.invitation = invitation;
            this.incoming = incoming;
        }
    }
}
