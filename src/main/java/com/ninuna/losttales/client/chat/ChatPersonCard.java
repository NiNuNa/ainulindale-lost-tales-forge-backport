package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.client.gui.tooltip.LostTalesTooltipSmoothing;
import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.SubWindowContent;
import com.ninuna.losttales.client.window.WindowHover;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.client.window.WordButton;
import com.ninuna.losttales.gui.screen.character.CharactersPage;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.gui.style.LostTalesUiTheme;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.StatCollector;

/**
 * A person's card in a sub-window of its own, in full: what a click on
 * a name, a head, a mention or a member opens. Each person has one; it
 * reads the person afresh every frame, so a status or a status line
 * changed while the card stands shows at once. A character's card shows
 * its glances, the pointer on one saying its words, and View Profile
 * under them, which opens the character's profile in the Characters
 * tab. The Server's shows how the server stands and Copy Address under
 * it. A card narrower or shorter than it wants cuts its rows at the
 * window's edge.
 */
final class ChatPersonCard extends SubWindowContent {
    /** Room for the head and a short name beside it. */
    private static final int MIN_WIDTH = 80;
    private static final String VIEW = "view_profile";
    private static final String COPY_ADDRESS = "copy_address";
    private static final String GLANCE_PREFIX = "glance:";

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

    /**
     * The button under the rows: View Profile on a card with a profile,
     * Copy Address on the Server's; empty for none.
     */
    private String buttonLabel() {
        return this.target.hasProfile()
                ? StatCollector.translateToLocal(
                        "gui.losttales.chat.card.view_profile")
                : isServer() ? StatCollector.translateToLocal(
                        "gui.losttales.chat.card.server.copy_address")
                : "";
    }

    /** What the button is pressed as: {@link #VIEW} or {@link #COPY_ADDRESS}. */
    private String buttonPart() {
        return this.target.hasProfile() ? VIEW : COPY_ADDRESS;
    }

    private boolean isServer() {
        return LostTalesChatMessagePacket.isServerSender(this.target.playerId);
    }

    /**
     * The address Copy Address copies: the one the server names, else the
     * one this client joined by; empty in a world of this computer's own.
     */
    private static String addressToCopy() {
        String named = ClientServerStatus.address();
        if (named.length() > 0) {
            return named;
        }
        ServerData joined = Minecraft.getMinecraft().func_147104_D();
        return joined == null || joined.serverIP == null ? ""
                : joined.serverIP.trim();
    }

    /** The room the button takes under the rows, and the padding under it; none on a card without one. */
    private int footer() {
        return buttonLabel().length() > 0
                ? LostTalesUiFramedButton.HEIGHT + MenuWindow.PADDING_Y
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

    /** The head and the name's two rows, and the padding round them. */
    @Override
    public int minHeight() {
        return MenuWindow.PADDING_Y * 2
                + LostTalesChatHoverCard.HEAD_ROWS * MenuWindow.rowHeight();
    }

    /** The button at the rows' left, the padding under the last row, which the card's height ends on. */
    private LostTalesUiHitBox buttonBox(FontRenderer font, LostTalesUiHitBox box,
                                        LostTalesChatHoverCard.Laid laid) {
        return new LostTalesUiHitBox(
                Math.floor(box.left) + MenuWindow.PADDING_X,
                Math.floor(box.top) + laid.height,
                WordButton.width(font, buttonLabel()),
                LostTalesUiFramedButton.HEIGHT);
    }

    /** Whether the button can be pressed now: Copy Address needs an address to copy. */
    private boolean buttonActs() {
        return !COPY_ADDRESS.equals(buttonPart()) || addressToCopy().length() > 0;
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
            String label = buttonLabel();
            if (label.length() > 0) {
                LostTalesUiHitBox button = buttonBox(minecraft.fontRenderer,
                        box, laid);
                boolean acts = buttonActs();
                WordButton.draw(minecraft.fontRenderer, button, label,
                        false, acts, acts && button.contains(pointerX, pointerY),
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
        int glance = LostTalesChatHoverCard.glanceAt(laid,
                (int)Math.floor(box.left), (int)Math.floor(box.top), x, y);
        this.hoveredGlance = glance < 0 ? null : laid.glances.get(glance);
        String part = glance >= 0 ? GLANCE_PREFIX + glance
                : buttonLabel().length() > 0
                        && buttonBox(minecraft.fontRenderer, box, laid)
                                .contains(x, y) ? buttonPart() : null;
        if (part == null) {
            return null;
        }
        WindowHover hover = new WindowHover(WindowHover.Kind.SUB_WINDOW);
        hover.part = part;
        hover.acts = VIEW.equals(part)
                || COPY_ADDRESS.equals(part) && buttonActs();
        if (COPY_ADDRESS.equals(part) && !buttonActs()) {
            hover.tip = StatCollector.translateToLocal(
                    "gui.losttales.chat.card.server.no_address");
        }
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
        if (COPY_ADDRESS.equals(hover.part) && button == 0 && buttonActs()) {
            if (LostTalesChatClipboard.copy(addressToCopy())) {
                WindowScreen screen = WindowScreen.current();
                ChatScreenPart chat = screen == null ? null
                        : screen.part(ChatScreenPart.class);
                if (chat != null) {
                    chat.showNotice(StatCollector.translateToLocal(
                            "gui.losttales.chat.copied"));
                }
            }
            return true;
        }
        return hover.part != null && hover.part.startsWith(GLANCE_PREFIX);
    }

    @Override
    public String tipKey() {
        return this.hoveredGlance == null ? ""
                : this.hoveredGlance.getTitle() + "|" + this.hoveredGlance.getLine();
    }

    /**
     * A glance's title and line beside the pointer, as every tip is
     * drawn: a popup of lines, its title in the accent on the first and its
     * line under it in ivory, broken where a tip breaks.
     */
    @Override
    public void drawTip(Minecraft minecraft, int tipX, int tipY,
                        int screenWidth, float share) {
        CharacterProfile.Glance glance = this.hoveredGlance;
        if (glance == null) {
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        String title = ClientChatProfanity.filter(glance.getTitle());
        List<String> words = glance.getLine().length() == 0
                ? Collections.<String>emptyList()
                : WindowStyle.tipLines(font, ClientChatProfanity.filter(
                        glance.getLine()));
        int boxWidth = WindowStyle.popupLineWidth(font, title);
        for (String line : words) {
            boxWidth = Math.max(boxWidth,
                    WindowStyle.popupLineWidth(font, line));
        }
        int boxHeight = WindowStyle.popupLinesHeight(1 + words.size());
        int x = Math.max(2, Math.min(screenWidth - boxWidth - 2,
                tipX - boxWidth / 2));
        int y = tipY - 3 - boxHeight;
        // Beside the pointer, so it keeps pace with the cursor, as every
        // other tip does.
        LostTalesTooltipSmoothing.begin(tipX, tipY);
        try {
            int alpha = Math.round(255.0F * Math.max(0.0F,
                    Math.min(1.0F, share)));
            WindowStyle.drawPopup(x, y, x + boxWidth, y + boxHeight, share);
            int textX = x + WindowStyle.POPUP_INSET;
            int lineY = y + WindowStyle.POPUP_INSET;
            LostTalesUiInk.drawText(font, title, textX, lineY,
                    LostTalesUiTheme.accentRgb(), alpha);
            for (String line : words) {
                lineY += WindowStyle.LINE_HEIGHT;
                LostTalesUiInk.drawText(font, line, textX, lineY,
                        LostTalesUiInk.IVORY, alpha);
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
