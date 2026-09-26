package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.client.gui.tooltip.LostTalesTooltipSmoothing;
import com.ninuna.losttales.client.window.SubWindowContent;
import com.ninuna.losttales.client.window.WindowHover;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.client.window.WordButton;
import com.ninuna.losttales.gui.screen.character.CharactersPage;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.StatCollector;

/**
 * A person's card in a sub-window of its own, in full: what a click on
 * a name, a head, a mention or a member opens. Each person has one; it
 * reads the person afresh every frame, so a status or a status line
 * changed while the card stands shows at once. A character's card shows
 * its glances, the pointer on one saying its words, and View Profile
 * under them, which opens the character's profile in the Characters tab
 * (P5 a). A card narrower or shorter than it wants cuts its rows at the
 * window's edge.
 */
final class ChatPersonCard extends SubWindowContent {
    /** Room for the head and a short name beside it. */
    private static final int MIN_WIDTH = 80;
    private static final String VIEW = "view_profile";
    private static final String GLANCE_PREFIX = "glance:";
    /** The widest a glance's tip wraps its line to. */
    private static final int TIP_WIDTH = 160;

    final LostTalesChatHoverCard.Target target;
    private final LostTalesUiButtonMotion viewMotion =
            new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
    /** The glance under the pointer, for its tip; null for none. */
    private CharacterProfile.Glance hoveredGlance;

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

    private static String viewLabel() {
        return StatCollector.translateToLocal(
                "gui.losttales.chat.card.view_profile");
    }

    /** The room View Profile takes under the rows; none on a card without a profile. */
    private int footer() {
        return this.target.hasProfile()
                ? LostTalesUiFramedButton.HEIGHT + LostTalesChatHoverCard.PADDING
                : 0;
    }

    @Override
    public int naturalWidth() {
        return LostTalesChatHoverCard.layOut(Minecraft.getMinecraft(),
                this.target, true, 0, LostTalesChatHoverCard.MAX_WIDTH).width;
    }

    @Override
    public int naturalHeight(int width) {
        return LostTalesChatHoverCard.layOut(Minecraft.getMinecraft(),
                this.target, true, width, width).height + footer();
    }

    @Override
    public int minWidth() {
        return MIN_WIDTH;
    }

    @Override
    public int minHeight() {
        return LostTalesChatHoverCard.PADDING * 2 + 16;
    }

    private LostTalesUiHitBox viewBox(FontRenderer font, LostTalesUiHitBox box,
                                      LostTalesChatHoverCard.Laid laid) {
        return new LostTalesUiHitBox(
                Math.floor(box.left) + LostTalesChatHoverCard.PADDING,
                Math.floor(box.top) + laid.height,
                WordButton.width(font, viewLabel()),
                LostTalesUiFramedButton.HEIGHT);
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
            if (this.target.hasProfile()) {
                LostTalesUiHitBox view = viewBox(minecraft.fontRenderer, box,
                        laid);
                WordButton.draw(minecraft.fontRenderer, view, viewLabel(),
                        false, true, view.contains(pointerX, pointerY),
                        this.viewMotion, alpha, surfaceAlpha);
            }
        } finally {
            endClip(clipped);
        }
    }

    @Override
    public WindowHover hoverAt(LostTalesUiHitBox box, double x, double y) {
        Minecraft minecraft = Minecraft.getMinecraft();
        int width = (int)Math.floor(box.width);
        LostTalesChatHoverCard.Laid laid = LostTalesChatHoverCard.layOut(
                minecraft, this.target, true, width, width);
        int glance = LostTalesChatHoverCard.glanceAt(minecraft, laid,
                (int)Math.floor(box.left), (int)Math.floor(box.top), x, y);
        this.hoveredGlance = glance < 0 ? null : laid.glances.get(glance);
        String part = glance >= 0 ? GLANCE_PREFIX + glance
                : this.target.hasProfile()
                        && viewBox(minecraft.fontRenderer, box, laid)
                                .contains(x, y) ? VIEW : null;
        if (part == null) {
            return null;
        }
        WindowHover hover = new WindowHover(WindowHover.Kind.SUB_WINDOW);
        hover.part = part;
        hover.acts = VIEW.equals(part);
        return hover;
    }

    @Override
    public boolean pressed(WindowHover hover, double x, double y,
                           int button) {
        if (VIEW.equals(hover.part) && button == 0
                && this.target.hasProfile()) {
            CharactersPage.visit(this.target.visit());
            return true;
        }
        return hover.part != null && hover.part.startsWith(GLANCE_PREFIX);
    }

    /** A glance's title and line, beside the pointer. */
    @Override
    public void drawTip(Minecraft minecraft, int tipX, int tipY,
                        int screenWidth) {
        CharacterProfile.Glance glance = this.hoveredGlance;
        if (glance == null) {
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        String title = ClientChatProfanity.filter(glance.getTitle());
        @SuppressWarnings("unchecked")
        List<String> words = glance.getLine().length() == 0
                ? Collections.<String>emptyList()
                : font.listFormattedStringToWidth(ClientChatProfanity.filter(
                        glance.getLine()), TIP_WIDTH);
        int width = font.getStringWidth(title);
        for (String line : words) {
            width = Math.max(width, font.getStringWidth(line));
        }
        int pad = 4;
        int boxWidth = width + 2 * pad;
        int boxHeight = (1 + words.size()) * font.FONT_HEIGHT + 2 * pad;
        int x = Math.max(2, Math.min(screenWidth - boxWidth - 2,
                tipX - boxWidth / 2));
        int y = tipY - 3 - boxHeight;
        // Beside the pointer, so it keeps pace with the cursor, as every
        // other tip does.
        LostTalesTooltipSmoothing.begin(tipX, tipY);
        try {
            WindowStyle.drawPopup(x, y, x + boxWidth, y + boxHeight, 1.0F);
            LostTalesUiInk.drawText(font, title, x + pad, y + pad,
                    LostTalesColors.rgb(LostTalesColors.HONEY), 255);
            int lineY = y + pad + font.FONT_HEIGHT;
            for (String line : words) {
                LostTalesUiInk.drawText(font, line, x + pad, lineY,
                        LostTalesUiInk.IVORY, 255);
                lineY += font.FONT_HEIGHT;
            }
        } finally {
            LostTalesTooltipSmoothing.end();
        }
    }

    /** The person the card is about comes back with the chat. */
    @Override
    public Object sessionState() {
        return this.target;
    }
}
