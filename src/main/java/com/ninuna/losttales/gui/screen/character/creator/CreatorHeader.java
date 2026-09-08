package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;

/** A section header with its rule, the way every panel here names a part of itself. */
public final class CreatorHeader extends CreatorControl {

    private final String title;

    public CreatorHeader(CreatorContext context, String title) {
        super(context);
        this.title = title;
    }

    @Override
    public int height() {
        return 16;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        LostTalesSkyrimUiStyle.beginContent();
        LostTalesSkyrimUiStyle.drawSectionHeader(this.context.getFont(),
                this.title, this.x, this.y, this.width);
        LostTalesSkyrimUiStyle.beginContent();
    }
}
