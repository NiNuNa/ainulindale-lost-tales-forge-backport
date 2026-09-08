package com.ninuna.losttales.client.gui;

/** Fits the complete title above the menu without changing its aspect ratio. */
final class MainMenuTitleLayout {
    static final int TEXTURE_WIDTH = 2006;
    static final int TEXTURE_HEIGHT = 560;
    static final int TOP = 8;
    static final int SIDE_MARGIN = 12;
    static final int GAP = 8;
    static final int MAX_WIDTH = 310;

    final float left;
    final float width;
    final float height;

    private MainMenuTitleLayout(int screenWidth, float width) {
        this.width = width;
        this.height = width * TEXTURE_HEIGHT / TEXTURE_WIDTH;
        this.left = (screenWidth - width) / 2.0F;
    }

    static MainMenuTitleLayout fit(int screenWidth, int buttonsTop, int subtitleHeight) {
        int room = buttonsTop - TOP - GAP;
        if (subtitleHeight > 0) {
            room -= subtitleHeight + GAP;
        }
        float width = Math.min(MAX_WIDTH, screenWidth - SIDE_MARGIN * 2);
        width = Math.min(width, room * (float) TEXTURE_WIDTH / TEXTURE_HEIGHT);
        return new MainMenuTitleLayout(screenWidth, Math.max(0.0F, width));
    }

    int subtitleY() {
        // Round toward the logo to keep the reserved gap below the subtitle.
        return (int) (TOP + this.height) + GAP;
    }
}
