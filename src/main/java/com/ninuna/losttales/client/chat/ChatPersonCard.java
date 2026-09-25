package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.SubWindowContent;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import net.minecraft.client.Minecraft;

/**
 * A person's card in a sub-window of its own, in full: what a click on
 * a name, a head, a mention or a member opens. Each person has one; it
 * reads the person afresh every frame, so a status or a status line
 * changed while the card stands shows at once. A card narrower or
 * shorter than it wants cuts its rows at the window's edge.
 */
final class ChatPersonCard extends SubWindowContent {
    /** Room for the head and a short name beside it. */
    private static final int MIN_WIDTH = 80;

    final LostTalesChatHoverCard.Target target;

    ChatPersonCard(LostTalesChatHoverCard.Target target) {
        this.target = target;
    }

    @Override
    public String stripTitle() {
        return this.target.windowTitle();
    }

    @Override
    public LostTalesUiSheet stripIcon() {
        return null;
    }

    @Override
    public int naturalWidth() {
        return LostTalesChatHoverCard.layOut(Minecraft.getMinecraft(),
                this.target, true, 0, LostTalesChatHoverCard.MAX_WIDTH).width;
    }

    @Override
    public int naturalHeight(int width) {
        return LostTalesChatHoverCard.layOut(Minecraft.getMinecraft(),
                this.target, true, width, width).height;
    }

    @Override
    public int minWidth() {
        return MIN_WIDTH;
    }

    @Override
    public int minHeight() {
        return LostTalesChatHoverCard.PADDING * 2 + 16;
    }

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
              double clipY, double pointerX, double pointerY, int alpha,
              int surfaceAlpha) {
        int width = (int)Math.floor(box.width);
        LostTalesChatHoverCard.Laid laid = LostTalesChatHoverCard.layOut(
                minecraft, this.target, true, width, width);
        boolean clipped = beginClip(minecraft, clipX, clipY, box.width,
                box.height);
        try {
            LostTalesChatHoverCard.drawLaid(minecraft, laid,
                    (int)Math.floor(box.left), (int)Math.floor(box.top),
                    alpha);
        } finally {
            endClip(clipped);
        }
    }

    /** The person the card is about comes back with the chat. */
    @Override
    public Object sessionState() {
        return this.target;
    }
}
