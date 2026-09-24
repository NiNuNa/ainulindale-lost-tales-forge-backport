package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.party.PartyClientRequestManager;
import com.ninuna.losttales.party.sync.PartyInvitationNotice;
import com.ninuna.losttales.party.sync.PartyOperationFeedback;
import com.ninuna.losttales.party.sync.PartyOperationType;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentTranslation;

/**
 * Answers to party invitations clicked in the chat. They are sent as the
 * Party screen sends them, and since that screen is not open to show the
 * outcome, the outcome is said in the Client Console.
 */
public final class ChatPartyInvitationAnswers {
    /** Answers waiting for the server; more than a player clicks in a moment. */
    private static final int MAX_WAITING = 16;
    private static final Set<Integer> WAITING = new LinkedHashSet<Integer>();

    private ChatPartyInvitationAnswers() {}

    /** Sends the answer a click on Accept or Decline gave. */
    static void answer(PartyInvitationNotice.Answer answer) {
        if (answer == null) {
            return;
        }
        int requestId = answer.accept
                ? PartyClientRequestManager.acceptInvitation(answer.invitationId)
                : PartyClientRequestManager.declineInvitation(answer.invitationId);
        WAITING.add(Integer.valueOf(requestId));
        Iterator<Integer> oldest = WAITING.iterator();
        while (WAITING.size() > MAX_WAITING && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }

    /** Says how an answer given in the chat went; every other request is the Party screen's. */
    public static void onResult(PartyOperationFeedback feedback) {
        if (feedback == null
                || !WAITING.remove(Integer.valueOf(feedback.getRequestId()))) {
            return;
        }
        String key = !feedback.isSuccessful()
                ? "gui.losttales.party.error." + feedback.getErrorId().getId()
                : feedback.getOperationType() == PartyOperationType.ACCEPT_INVITATION
                        ? "chat.losttales.party.invitation.joined"
                        : "chat.losttales.party.invitation.declined";
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.thePlayer != null) {
            minecraft.thePlayer.addChatMessage(new ChatComponentTranslation(key));
        }
    }

    public static void clear() {
        WAITING.clear();
    }
}
