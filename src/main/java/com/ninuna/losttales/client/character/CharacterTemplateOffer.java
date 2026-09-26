package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.server.CharacterTemplateAdoption;
import com.ninuna.losttales.character.sync.CharacterOperationFeedback;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.validation.CharacterValidator;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentTranslation;

import java.util.UUID;

/**
 * Offers the account's template to a world that has not taken one.
 *
 * <p>A world says, in the roster it sends, whether it has read the
 * account's template yet. It has not on the login where it makes the
 * default character, and it has on every login after. The offer is made
 * once per session and never repeated: the server decides whether the
 * reading is still there to spend, and a second offer would only be
 * answered with the same "nothing to do".</p>
 *
 * <p>An account with no template still offers, with nothing in it. That
 * is what spends the reading, and it is what makes the world's default
 * character that world's from then on — a template written afterwards is
 * for the next world. A template the server refuses spends it too, and
 * the refusal is said in chat, so the player knows the world's default
 * character is the plain one and can change it there.</p>
 */
public final class CharacterTemplateOffer {

    /** The chat line for a refusal; its one argument is the reason. */
    static final String REFUSED_KEY = "gui.losttales.character.template.refused";

    /** The roster this session has already offered to. */
    private static UUID offeredFor;
    /** The offer the server has not answered yet; zero for none. */
    private static int pendingRequestId;

    private CharacterTemplateOffer() {}

    /** Called with every roster the server sends. */
    public static synchronized void onRoster(CharacterRosterSnapshot snapshot) {
        if (snapshot == null || snapshot.isTemplateTaken()
                // A roster with no default character yet has nothing to
                // take the template onto: the login sequence sends one
                // before it makes the character, and the one offer this
                // session makes must not be spent on it.
                || snapshot.getDefaultCharacter() == null) {
            return;
        }
        UUID ownerId = snapshot.getOwnerId();
        if (ownerId == null || ownerId.equals(offeredFor)) {
            return;
        }
        UUID player = LostTalesClientAccount.id();
        if (player == null || !player.equals(ownerId)) {
            // Somebody else's roster, or a session that cannot say whose
            // it is; there is no template of this account's to offer.
            return;
        }
        UUID account = LostTalesClientAccount.templateId();
        if (account == null) {
            return;
        }
        offeredFor = ownerId;
        pendingRequestId = ClientCharacterNetwork.adoptTemplate(
                adoption(snapshot.getRevision(),
                        CharacterTemplateStore.load(account)));
    }

    /**
     * The server's answer to an offer this session made. Nothing needs
     * doing on success; a refusal is said in chat with its reason.
     */
    public static void onResult(CharacterOperationFeedback feedback) {
        int request;
        synchronized (CharacterTemplateOffer.class) {
            if (!isAnswerTo(feedback, pendingRequestId)) {
                return;
            }
            request = pendingRequestId;
            pendingRequestId = 0;
        }
        ClientCharacterRosterCache.clearOperation(request);
        if (feedback.isSuccessful()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.ingameGUI == null) {
            return;
        }
        minecraft.ingameGUI.getChatGUI().printChatMessage(
                new ChatComponentTranslation(REFUSED_KEY,
                        ClientCharacterDisplayNames.error(feedback)));
    }

    /** Whether that answer is to the offer still waiting for one. */
    static boolean isAnswerTo(CharacterOperationFeedback feedback, int pendingRequestId) {
        return feedback != null && pendingRequestId != 0
                && feedback.getRequestId() == pendingRequestId;
    }

    /** What the template asks for, or an empty offer when there is none. */
    static CharacterTemplateAdoption adoption(long rosterRevision,
                                              CharacterTemplate template) {
        if (template == null || template.isEmpty() || !template.hasUsableName()
                || !template.hasSelectableRace()) {
            // A template no server would accept is not offered: a name
            // too short, or a race the registry does not let anyone choose.
            // Refusing it here spends the reading cleanly, and the template
            // editor names the missing choice the next time it is opened.
            return CharacterTemplateAdoption.none(rosterRevision);
        }
        return new CharacterTemplateAdoption(rosterRevision, true,
                template.getName(), template.getRaceId(),
                template.getGenderId(), template.getSkinId(),
                template.getBodyTypeId(), template.getChestTypeId(),
                template.getHistory(),
                // A template that never named an age keeps the one the
                // record was made with, which is the lowest a character
                // may have; zero is not an age any server accepts.
                Math.max(CharacterValidator.MIN_AGE, template.getAge()),
                template.isMinecraftCapeVisible(), template.getCosmeticCapeId());
    }

    /** Cleared with every other client cache when the world is left. */
    public static synchronized void clear() {
        offeredFor = null;
        pendingRequestId = 0;
    }
}
