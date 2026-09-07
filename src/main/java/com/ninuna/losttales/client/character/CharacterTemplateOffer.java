package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.server.CharacterTemplateAdoption;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.validation.CharacterValidator;

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
 * for the next world.</p>
 */
public final class CharacterTemplateOffer {

    /** The roster this session has already offered to. */
    private static UUID offeredFor;

    private CharacterTemplateOffer() {}

    /** Called with every roster the server sends. */
    public static synchronized void onRoster(CharacterRosterSnapshot snapshot) {
        if (snapshot == null || snapshot.isTemplateTaken()) {
            return;
        }
        UUID ownerId = snapshot.getOwnerId();
        if (ownerId == null || ownerId.equals(offeredFor)) {
            return;
        }
        UUID account = LostTalesClientAccount.id();
        if (account == null || !account.equals(ownerId)) {
            // Somebody else's roster, or a session that cannot say whose
            // it is; there is no template of this account's to offer.
            return;
        }
        offeredFor = ownerId;
        ClientCharacterNetwork.adoptTemplate(
                adoption(snapshot.getRevision(),
                        CharacterTemplateStore.load(account)));
    }

    /** What the template asks for, or an empty offer when there is none. */
    static CharacterTemplateAdoption adoption(long rosterRevision,
                                              CharacterTemplate template) {
        if (template == null || template.isEmpty() || !template.hasUsableName()
                || !isSelectableRace(template.getRaceId())) {
            // A template no server would accept is not offered: a name
            // too short, or a race nobody may choose any more. Refusing
            // it here spends the reading cleanly rather than failing on
            // every login, and the template editor names the missing
            // choice the next time it is opened.
            return CharacterTemplateAdoption.none(rosterRevision);
        }
        return new CharacterTemplateAdoption(rosterRevision, true,
                template.getName(), template.getRaceId(),
                template.getGenderId(), template.getSkinId(),
                template.getBodyTypeId(), template.getChestTypeId(),
                template.getDescription(),
                // A template that never named an age keeps the one the
                // record was made with, which is the lowest a character
                // may have; zero is not an age any server accepts.
                Math.max(CharacterValidator.MIN_AGE, template.getAge()));
    }

    /** Whether that race is one a character may still be made as. */
    private static boolean isSelectableRace(String raceId) {
        CharacterRaceDefinition race = CharacterRaceRegistry.get(raceId);
        return race != null && race.isSelectable();
    }

    /** Cleared with every other client cache when the world is left. */
    public static synchronized void clear() {
        offeredFor = null;
    }
}
