package com.ninuna.losttales.gui.screen.party;

import com.ninuna.losttales.client.party.ClientPartyDisplayNames;
import com.ninuna.losttales.client.party.ClientPartyStateCache;
import com.ninuna.losttales.client.party.ClientPartyTrackingCache;
import com.ninuna.losttales.client.party.PartyClientRequestManager;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
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
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Client-only profile-integrated party management screen.
 *
 * The screen renders only synchronized snapshots and never mutates local party
 * state optimistically. Every action is revalidated by the server.
 */
public final class LostTalesPartyManagementGui extends GuiScreen {

    private static final int BUTTON_BACK = 1;
    private static final int BUTTON_REFRESH = 2;
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

    private final GuiScreen parent;

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

    public LostTalesPartyManagementGui(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        calculateLayout();

        int tabWidth = Math.min(132, Math.max(80, (this.panelWidth - 20) / 3));
        int tabsTotal = tabWidth * 3;
        int tabsX = this.width / 2 - tabsTotal / 2;
        this.membersTabButton = new GuiButton(BUTTON_TAB_MEMBERS,
                tabsX, 34, tabWidth, 20,
                I18n.format("gui.losttales.party.tab.members"));
        this.invitationsTabButton = new GuiButton(BUTTON_TAB_INVITATIONS,
                tabsX + tabWidth, 34, tabWidth, 20,
                I18n.format("gui.losttales.party.tab.invitations"));
        this.inviteTabButton = new GuiButton(BUTTON_TAB_INVITE,
                tabsX + tabWidth * 2, 34, tabWidth, 20,
                I18n.format("gui.losttales.party.tab.invite"));
        this.buttonList.add(this.membersTabButton);
        this.buttonList.add(this.invitationsTabButton);
        this.buttonList.add(this.inviteTabButton);

        int gap = 6;
        int footerWidth = Math.max(52, (this.width - 16 - gap * 3) / 4);
        int footerY = this.height - 28;
        this.buttonList.add(new GuiButton(BUTTON_BACK, 8, footerY,
                footerWidth, 20, I18n.format("gui.back")));
        this.refreshButton = new GuiButton(BUTTON_REFRESH,
                8 + footerWidth + gap, footerY, footerWidth, 20,
                I18n.format("gui.losttales.party.refresh"));
        this.buttonList.add(this.refreshButton);

        int actionThreeX = 8 + (footerWidth + gap) * 2;
        int actionFourX = 8 + (footerWidth + gap) * 3;
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
        this.panelY = 58;
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
    public void updateScreen() {
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

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_BACK) {
            this.mc.displayGuiScreen(this.parent);
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

    private void confirm(String title, String detail, Runnable action) {
        this.mc.displayGuiScreen(new LostTalesPartyConfirmationGui(
                this, title, detail, action));
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
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        LostTalesSkyrimUiStyle.drawScreenShade(this.width, this.height);
        LostTalesSkyrimUiStyle.drawCenteredHeader(this.fontRendererObj,
                I18n.format("gui.losttales.party.title"),
                I18n.format("gui.losttales.party.server_authoritative"),
                this.width, 10);
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
            drawCenteredString(this.fontRendererObj,
                    LostTalesSkyrimUiStyle.trimToWidth(
                            this.fontRendererObj, this.statusMessage,
                            this.width - 24),
                    this.width / 2,
                    this.height - 67,
                    this.statusError
                            ? LostTalesSkyrimUiStyle.RED
                            : LostTalesSkyrimUiStyle.GREEN);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
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

        List<PartyMemberSnapshot> members = party.getMembers();
        int visibleRows = getVisibleRowCount();
        for (int visible = 0; visible < visibleRows; visible++) {
            int index = this.membersScroll + visible;
            if (index >= members.size()) {
                break;
            }
            PartyMemberSnapshot member = members.get(index);
            int rowY = this.listY + visible * ROW_HEIGHT;
            boolean selected = member.getCharacterId().equals(
                    this.selectedMemberCharacterId);
            boolean hovered = isInside(mouseX, mouseY,
                    this.listX, rowY, this.listWidth, ROW_HEIGHT - 2);
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
        int visibleRows = getVisibleRowCount();
        long now = System.currentTimeMillis();
        for (int visible = 0; visible < visibleRows; visible++) {
            int index = this.invitationsScroll + visible;
            if (index >= entries.size()) {
                break;
            }
            InvitationEntry entry = entries.get(index);
            PartyInvitationSnapshot invitation = entry.invitation;
            int rowY = this.listY + visible * ROW_HEIGHT;
            boolean selected = invitation.getInvitationId().equals(
                    this.selectedInvitationId)
                    && entry.incoming == this.selectedInvitationIncoming;
            boolean hovered = isInside(mouseX, mouseY,
                    this.listX, rowY, this.listWidth, ROW_HEIGHT - 2);
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
        int visibleRows = getVisibleRowCount();
        for (int visible = 0; visible < visibleRows; visible++) {
            int index = this.inviteTargetsScroll + visible;
            if (index >= targets.size()) {
                break;
            }
            PartyInviteTargetSnapshot target = targets.get(index);
            int rowY = this.listY + visible * ROW_HEIGHT;
            boolean selected = target.getOwnerId().equals(
                    this.selectedInviteOwnerId);
            boolean hovered = isInside(mouseX, mouseY,
                    this.listX, rowY, this.listWidth, ROW_HEIGHT - 2);
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
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && isInside(mouseX, mouseY,
                this.listX, this.listY, this.listWidth, this.listHeight)) {
            int row = (mouseY - this.listY) / ROW_HEIGHT;
            PartyStateSnapshot snapshot = getSnapshot();
            if (snapshot != null) {
                if (this.tab == Tab.MEMBERS && snapshot.getParty() != null) {
                    int index = this.membersScroll + row;
                    if (index >= 0
                            && index < snapshot.getParty().getMemberCount()) {
                        this.selectedMemberCharacterId = snapshot.getParty()
                                .getMembers().get(index).getCharacterId();
                    }
                } else if (this.tab == Tab.INVITATIONS) {
                    List<InvitationEntry> entries =
                            getInvitationEntries(snapshot);
                    int index = this.invitationsScroll + row;
                    if (index >= 0 && index < entries.size()) {
                        InvitationEntry entry = entries.get(index);
                        this.selectedInvitationId =
                                entry.invitation.getInvitationId();
                        this.selectedInvitationIncoming = entry.incoming;
                    }
                } else if (this.tab == Tab.INVITE) {
                    int index = this.inviteTargetsScroll + row;
                    if (index >= 0
                            && index < snapshot.getInviteTargets().size()) {
                        this.selectedInviteOwnerId = snapshot
                                .getInviteTargets().get(index).getOwnerId();
                    }
                }
                updateButtons();
            }
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void handleMouseInput() {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int amount = wheel > 0 ? -1 : 1;
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
        }
        super.handleMouseInput();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (keyCode == Keyboard.KEY_R && this.pendingRequestId == 0) {
            beginRequest(PartyClientRequestManager.requestState(), false);
            return;
        }
        if (keyCode == Keyboard.KEY_1) {
            this.tab = Tab.MEMBERS;
            updateButtons();
            return;
        }
        if (keyCode == Keyboard.KEY_2) {
            this.tab = Tab.INVITATIONS;
            updateButtons();
            return;
        }
        if (keyCode == Keyboard.KEY_3) {
            this.tab = Tab.INVITE;
            updateButtons();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void clampScrollOffsets(PartyStateSnapshot snapshot) {
        int visibleRows = getVisibleRowCount();
        int memberCount = snapshot.getParty() == null
                ? 0 : snapshot.getParty().getMemberCount();
        this.membersScroll = clampScroll(
                this.membersScroll, memberCount, visibleRows);
        this.invitationsScroll = clampScroll(
                this.invitationsScroll,
                getInvitationEntries(snapshot).size(), visibleRows);
        this.inviteTargetsScroll = clampScroll(
                this.inviteTargetsScroll,
                snapshot.getInviteTargets().size(), visibleRows);
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

    @Override
    public boolean doesGuiPauseGame() {
        return false;
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
