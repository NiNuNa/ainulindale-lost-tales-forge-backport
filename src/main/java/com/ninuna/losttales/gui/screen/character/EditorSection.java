package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.client.camera.StageShot;

/**
 * The Character Editor's sections, in the order a character is made: what
 * it is, how it is built, what it wears, who it is, where it comes from,
 * and what hangs from its shoulders (CHAR-41). Changing a character's look
 * shows only Body, Skin and Capes.
 *
 * <p>Each section brings its own shot of the character on the stage: the
 * face for the sections about who the character is, the whole body for
 * those about how it looks, and the back for the cape.</p>
 */
public enum EditorSection {
    RACE("race", StageShot.UPPER_FRONT),
    BODY("body", StageShot.FULL_FRONT),
    SKIN("skin", StageShot.FULL_FRONT),
    IDENTITY("identity", StageShot.UPPER_FRONT),
    ORIGIN("origin", StageShot.UPPER_FRONT),
    CAPES("capes", StageShot.FULL_BACK);

    private static final String KEY_PREFIX =
            "gui.losttales.character.creator.category.";

    private final String id;
    private final StageShot shot;

    EditorSection(String id, StageShot shot) {
        this.id = id;
        this.shot = shot;
    }

    /** Its code name, which its row in the section list goes by. */
    public String id() {
        return this.id;
    }

    /** The language key of its name. */
    public String getLabelKey() {
        return KEY_PREFIX + this.id;
    }

    /** How the section frames the character before the player moves the camera. */
    public StageShot getShot() {
        return this.shot;
    }

    /** The section of that code name; null for none. */
    public static EditorSection byId(String id) {
        for (EditorSection section : values()) {
            if (section.id.equals(id)) {
                return section;
            }
        }
        return null;
    }
}
