package com.ninuna.losttales.gui.screen;

import com.ninuna.losttales.client.gui.LostTalesPointerInteractable;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimations;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.screen.quest.QuestDialogueLayout;
import com.ninuna.losttales.gui.screen.quest.QuestDialogueModel;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesQuestActionPacket;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.motion.MotionIds;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * A conversation with somebody about a quest.
 *
 * <p>One screen for both systems: the words and the replies come from a
 * {@link QuestDialogueModel}, and what a reply does is the
 * {@link Action} the screen was given. A Middle-earth offer answers LOTR
 * the way its own screen would; a Lost Tales quest asks the server,
 * which decides again whether the player is really standing beside the
 * person they say they are talking to.</p>
 */
public class LostTalesQuestDialogueGui extends GuiScreen
        implements LostTalesPointerInteractable {

    /** What the screen does once a reply is chosen. */
    public interface Action {
        /** The player took the quest. */
        void accept();

        /** The player refused it, or simply left. */
        void leave();

        /** The player gave over what was asked for. */
        void handOver();

        /**
         * The speaker says this. The screen holds no words of its own:
         * a giver's line goes to the chat, where it is read beside every
         * other word and stays in the log once the talk is over.
         */
        void says(String line);
    }

    private final GuiScreen parent;
    private final Action action;
    private QuestDialogueModel model;
    /** The reply on the middle row: what Enter says. */
    private int chosen;
    /** Where the column is drawn, easing toward {@link #chosen}. */
    private double chosenShown;
    private int hovered = -1;
    private long lastFrameNanos;

    public LostTalesQuestDialogueGui(GuiScreen parent, QuestDialogueModel model,
                                     Action action) {
        this.parent = parent;
        this.model = model;
        this.action = action;
    }

    /**
     * A conversation about a Lost Tales quest: every reply is a request
     * naming the quest, and the server decides whether it may happen.
     */
    public static Action questAction(final String questReference) {
        return new Action() {
            @Override
            public void accept() {
                send(LostTalesQuestActionPacket.ACTION_ACCEPT, questReference);
            }

            @Override
            public void handOver() {
                send(LostTalesQuestActionPacket.ACTION_HAND_IN, questReference);
            }

            @Override
            public void leave() {
            }

            @Override
            public void says(String line) {
                // Asking the server is all this one does; whoever opened
                // the conversation wraps it with a voice.
            }
        };
    }

    private static void send(String action, String questReference) {
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new LostTalesQuestActionPacket(action, questReference));
    }

    @Override
    public void initGui() {
        super.initGui();
        // The opening words are said once, as the talk begins.
        if (!this.opened) {
            this.opened = true;
            say(this.model.getSaid());
        }
    }

    /** Has the speaker said their opening line yet. */
    private boolean opened;

    /** Hands a line to whoever is speaking it. */
    private void say(String line) {
        if (this.action != null && line != null && line.length() > 0) {
            this.action.says(line);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        advanceMotion();
        QuestDialogueLayout layout = layout();
        this.hovered = replyAt(layout, mouseX, mouseY);

        // The world stays where it is; a conversation is laid over it
        // rather than in a panel, so it is dimmed only enough for words
        // to read against whatever is behind them.
        if (!LostTalesGuiAnimations.isManagingBackdrop(this)) {
            drawRect(0, 0, this.width, this.height,
                    LostTalesColors.withAlpha(LostTalesColors.PLUM_BLACK, 0x66));
        }
        drawRule(layout);
        drawSpeaker(layout);
        drawReplies(layout);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** Moves the column one frame toward the reply being chosen. */
    private void advanceMotion() {
        long now = System.nanoTime();
        double elapsed = this.lastFrameNanos == 0L ? 0.0D
                : (now - this.lastFrameNanos) / 1000000000.0D;
        this.lastFrameNanos = now;
        this.chosenShown = Motions.followTravel(
                MotionIds.SCREEN_DIALOGUE_GLIDE, this.chosenShown,
                this.chosen, elapsed);
    }

    private QuestDialogueLayout layout() {
        return new QuestDialogueLayout(this.width, this.height,
                this.model.getReplies().size());
    }

    /**
     * The upright rule, faintest at its ends: it marks where the replies
     * begin without drawing a box round them.
     */
    private void drawRule(QuestDialogueLayout layout) {
        LostTalesUiHitBox rule = layout.rule();
        int rows = (int)rule.height;
        for (int row = 0; row < rows; row++) {
            double from = Math.abs(row - rows / 2.0D) / (rows / 2.0D);
            int alpha = (int)Math.round(0xB4 * (1.0D - from * from));
            if (alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
                continue;
            }
            drawRect((int)rule.left, (int)rule.top + row,
                    (int)rule.right(), (int)rule.top + row + 1,
                    LostTalesUiInk.argb(
                            LostTalesColors.rgb(LostTalesColors.SAND), alpha));
        }
    }

    /**
     * Who is speaking, written right up to the rule and level with the
     * reply being chosen, so the name and the answer read as one
     * exchange. The quest is named under it, quietly.
     */
    private void drawSpeaker(QuestDialogueLayout layout) {
        LostTalesSkyrimUiStyle.beginContent();
        String name = LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj,
                this.model.getSpeaker(), layout.nameWidth());
        this.fontRendererObj.drawStringWithShadow(name,
                layout.nameRight() - this.fontRendererObj.getStringWidth(name),
                layout.nameY(),
                LostTalesColors.rgb(LostTalesColors.TEXT_BRIGHT));

        // Who they are and what the talk is about, quietly under the
        // name and written up to the rule like it, so everything left of
        // the rule reads as one block. A line nobody gave is skipped
        // rather than left as a gap.
        int row = 0;
        row += drawNote(layout, this.model.getSubtitle(), row,
                LostTalesColors.TEXT_MUTED);
        row += drawNote(layout, this.model.getQuestTitle(), row,
                LostTalesColors.TEXT_MUTED);
        drawNote(layout, this.model.getObjective(), row,
                LostTalesColors.TEXT_DIM);
    }

    /** Writes one quiet line; answers how many rows it took. */
    private int drawNote(QuestDialogueLayout layout, String note, int row,
                         int rgb) {
        if (note == null || note.length() == 0) {
            return 0;
        }
        String trimmed = LostTalesSkyrimUiStyle.trimToWidth(
                this.fontRendererObj, note, layout.nameWidth());
        this.fontRendererObj.drawStringWithShadow(trimmed,
                layout.nameRight()
                        - this.fontRendererObj.getStringWidth(trimmed),
                layout.noteY(row), LostTalesColors.rgb(rgb));
        return 1;
    }

    /**
     * The replies, the chosen one on the rule's middle row and the rest
     * above and below it. The one being chosen is written in ivory and
     * the rest quietly, so the eye lands on the middle row without a
     * surface being drawn behind it.
     */
    private void drawReplies(QuestDialogueLayout layout) {
        List<QuestDialogueModel.Reply> replies = this.model.getReplies();
        LostTalesSkyrimUiStyle.beginContent();
        for (int index = 0; index < replies.size(); index++) {
            LostTalesUiHitBox row = layout.replyAt(index, this.chosenShown);
            double distance = Math.abs(index - this.chosenShown);
            int alpha = (int)Math.round(0xFF * Math.max(0.35D,
                    1.0D - distance * 0.30D));
            boolean lit = index == this.chosen || index == this.hovered;
            int rgb = LostTalesColors.rgb(lit ? LostTalesColors.TEXT_BRIGHT
                    : LostTalesColors.TEXT_MUTED);
            drawShadowed(LostTalesSkyrimUiStyle.trimToWidth(
                            this.fontRendererObj,
                            this.model.labelOf(replies.get(index)),
                            (int)row.width),
                    (int)row.left, (int)Math.round(row.top + 2), rgb, alpha);
        }
        drawMarker(layout);
    }

    /** The mark on the rule beside the reply being chosen. */
    private void drawMarker(QuestDialogueLayout layout) {
        if (this.model.getReplies().isEmpty()) {
            return;
        }
        String mark = "\u00ab";
        LostTalesUiHitBox box = layout.marker(
                this.fontRendererObj.getStringWidth(mark), 8);
        drawShadowed(mark, (int)box.left, (int)Math.round(box.top),
                LostTalesColors.rgb(LostTalesColors.GOLD), 0xFF);
    }

    /** One run in the mod's ink: the shadow a pixel down, then the words. */
    private void drawShadowed(String text, int x, int y, int rgb, int alpha) {
        int shadow = LostTalesUiInk.shadowAlpha(alpha);
        if (shadow >= LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            this.fontRendererObj.drawString(text,
                    x + LostTalesUiInk.SHADOW_OFFSET,
                    y + LostTalesUiInk.SHADOW_OFFSET,
                    LostTalesUiInk.argb(LostTalesUiInk.SHADOW, shadow));
        }
        this.fontRendererObj.drawString(text, x, y,
                LostTalesUiInk.argb(rgb, alpha));
    }

    /** The reply row under the point, or -1. */
    private int replyAt(QuestDialogueLayout layout, int mouseX, int mouseY) {
        for (int index = 0; index < this.model.getReplies().size(); index++) {
            if (layout.replyAt(index, this.chosenShown).contains(mouseX,
                    mouseY)) {
                return index;
            }
        }
        return -1;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            // Any reply may be pressed where it stands, not only the one
            // on the middle row.
            int index = replyAt(layout(), mouseX, mouseY);
            if (index >= 0) {
                this.chosen = index;
                choose(this.model.getReplies().get(index));
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean isPointerOverInteractable(int mouseX, int mouseY) {
        return replyAt(layout(), mouseX, mouseY) >= 0;
    }

    @Override
    public void handleMouseInput() {
        int wheel = Mouse.getEventDWheel();
        int count = this.model.getReplies().size();
        if (wheel != 0 && count > 0) {
            this.chosen = clamp(this.chosen + (wheel > 0 ? -1 : 1), count);
        }
        super.handleMouseInput();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        int count = this.model.getReplies().size();
        if (keyCode == Keyboard.KEY_ESCAPE) {
            choose(QuestDialogueModel.Reply.LEAVE);
            return;
        }
        if (keyCode == Keyboard.KEY_UP && count > 0) {
            this.chosen = clamp(this.chosen - 1, count);
            return;
        }
        if (keyCode == Keyboard.KEY_DOWN && count > 0) {
            this.chosen = clamp(this.chosen + 1, count);
            return;
        }
        if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_SPACE)
                && this.chosen < count) {
            choose(this.model.getReplies().get(this.chosen));
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    /**
     * The choice inside the list. It stops at both ends rather than
     * wrapping: the column is a list being moved past a fixed row, and a
     * list that jumps from its end to its start reads as a mistake.
     */
    private static int clamp(int index, int count) {
        return index < 0 ? 0 : index >= count ? count - 1 : index;
    }

    /**
     * Says a reply: asking for more carries the conversation on, and
     * everything else does what it says and closes.
     */
    private void choose(QuestDialogueModel.Reply reply) {
        if (reply == QuestDialogueModel.Reply.MORE) {
            this.model = this.model.told();
            this.chosen = 0;
            say(this.model.getSaid());
            return;
        }
        if (this.action != null) {
            if (reply == QuestDialogueModel.Reply.ACCEPT) {
                this.action.accept();
            } else if (reply == QuestDialogueModel.Reply.HAND_OVER) {
                this.action.handOver();
            } else {
                this.action.leave();
            }
        }
        this.mc.displayGuiScreen(this.parent);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /** Whether the screen may be opened at all right now. */
    public static boolean isEnabled() {
        return LostTalesConfig.enableQuestDialogue;
    }
}
