package com.ninuna.losttales.character.registry;

/**
 * One entry of a race file's skins (CHAR-38): a group of {@code count}
 * pictures, {@code 0.png} and on, in the folder {@code texture} names,
 * for one body type or, with none named, every body type the race has. A bundled
 * group's words are the lang line {@code gui.losttales.character.skin_group.<group>};
 * a server's are written in its file.
 */
public final class CharacterRaceSkins {
    private final String group;
    private final String name;
    private final String bodyTypeId;
    private final String texture;
    private final int count;

    public CharacterRaceSkins(String group, String name, String bodyTypeId,
                              String texture, int count) {
        this.group = group == null ? "" : group;
        this.name = name == null ? "" : name;
        this.bodyTypeId = bodyTypeId == null ? "" : bodyTypeId;
        this.texture = texture == null ? "" : texture;
        this.count = count;
    }

    public String getGroup() {
        return this.group;
    }

    /** The group's words as a server's file writes them; empty for a bundled group. */
    public String getName() {
        return this.name;
    }

    /** The body type the pictures are painted for; empty for every body type the race has. */
    public String getBodyTypeId() {
        return this.bodyTypeId;
    }

    /** The folder of the pictures: LOTR's ({@code lotr:mob/...}) or the mod's own. */
    public String getTexture() {
        return this.texture;
    }

    public int getCount() {
        return this.count;
    }
}
