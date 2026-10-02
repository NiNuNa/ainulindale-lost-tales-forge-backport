package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.fellowship.FellowshipClientRequestManager;
import com.ninuna.losttales.fellowship.sync.FellowshipInvitationNotice;
import com.ninuna.losttales.fellowship.sync.FellowshipOperationFeedback;
import com.ninuna.losttales.fellowship.sync.FellowshipOperationType;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentTranslation;

/**
 * Answers to fellowship invitations clicked in the chat. They are sent as the
 * Fellowship page sends them, and since the page may not be open to show the
 * outcome, the outcome is said in the Console.
 */
public final class ChatFellowshipInvitationAnswers {
    /** Answers waiting for the server; more than a player clicks in a moment. */
    private static final int MAX_WAITING = 16;
    private static final Set<Integer> WAITING = new LinkedHashSet<Integer>();

    private ChatFellowshipInvitationAnswers() {}

    /** Sends the answer a click on Accept or Decline gave. */
    static void answer(FellowshipInvitationNotice.Answer answer) {
        if (answer == null) {
            return;
        }
        int requestId = answer.accept
                ? FellowshipClientRequestManager.acceptInvitation(answer.invitationId)
                : FellowshipClientRequestManager.declineInvitation(answer.invitationId);
        WAITING.add(Integer.valueOf(requestId));
        Iterator<Integer> oldest = WAITING.iterator();
        while (WAITING.size() > MAX_WAITING && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }

    /** Says how an answer given in the chat went; every other request is the Fellowship page's. */
    public static void onResult(FellowshipOperationFeedback feedback) {
        if (feedback == null
                || !WAITING.remove(Integer.valueOf(feedback.getRequestId()))) {
            return;
        }
        String key = !feedback.isSuccessful()
                ? "gui.losttales.fellowship.error." + feedback.getErrorId().getId()
                : feedback.getOperationType() == FellowshipOperationType.ACCEPT_INVITATION
                        ? "chat.losttales.fellowship.invitation.joined"
                        : "chat.losttales.fellowship.invitation.declined";
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.thePlayer != null) {
            minecraft.thePlayer.addChatMessage(new ChatComponentTranslation(key));
        }
    }

    public static void clear() {
        WAITING.clear();
    }
}
