package com.ninuna.losttales.gui.screen.character.creator;

import net.minecraft.client.resources.I18n;

/**
 * A choice that is always the player's to make: never fixed by another
 * choice, and reading "no options" when there is nothing to choose from.
 * Most of the creator's choices are this; the body and chest, which the
 * skin can decide, implement {@link CreatorChoice} themselves.
 */
public abstract class PlainChoice implements CreatorChoice {

    @Override
    public boolean isFixed() {
        return false;
    }

    @Override
    public String fixedLabel() {
        return "";
    }

    @Override
    public String emptyLabel() {
        return I18n.format("gui.losttales.character.no_options");
    }
}
