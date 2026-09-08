package com.ninuna.losttales.gui.screen.character.creator;

/**
 * The pages of the creator, in the order a character is made: what it is,
 * how it is built, what it wears, who it is, where it comes from, and
 * what hangs from its shoulders.
 *
 * <p>The order is also the order the tab strip shows them in and the
 * order the page keys walk them, wrapping at either end. Each page brings
 * its own shot of the character: the face for the pages about who the
 * character is, the whole body for the pages about how it looks, and the
 * back for the cape.</p>
 */
public enum CharacterCreatorCategory {
    RACE("race", CharacterCreatorShot.UPPER_FRONT),
    BODY("body", CharacterCreatorShot.FULL_FRONT),
    SKIN("skin", CharacterCreatorShot.FULL_FRONT),
    IDENTITY("identity", CharacterCreatorShot.UPPER_FRONT),
    ORIGIN("origin", CharacterCreatorShot.UPPER_FRONT),
    CAPES("capes", CharacterCreatorShot.FULL_BACK);

    private static final String KEY_PREFIX =
            "gui.losttales.character.creator.category.";

    private final String id;
    private final CharacterCreatorShot shot;

    CharacterCreatorCategory(String id, CharacterCreatorShot shot) {
        this.id = id;
        this.shot = shot;
    }

    public String getId() {
        return this.id;
    }

    /** The language key of the tab's name. */
    public String getLabelKey() {
        return KEY_PREFIX + this.id;
    }

    /** How the page frames the character before the player moves the camera. */
    public CharacterCreatorShot getShot() {
        return this.shot;
    }

    public CharacterCreatorCategory next() {
        CharacterCreatorCategory[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public CharacterCreatorCategory previous() {
        CharacterCreatorCategory[] all = values();
        return all[(ordinal() + all.length - 1) % all.length];
    }
}
