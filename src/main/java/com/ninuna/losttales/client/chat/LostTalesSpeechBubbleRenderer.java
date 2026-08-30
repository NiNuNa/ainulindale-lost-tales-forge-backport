package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmojiParser;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.core.LostTalesClassTransformer;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

/**
 * What a speaker has just said, over their head.
 *
 * <p>Players and NPCs alike: an NPC's floating speech is LOTR's, and the
 * coremod hands that pass over to Lost Tales so both are drawn the same
 * way — the speaker's name in the colour the chat signs it with, the
 * words under it in the chat's ivory on the chat's black, and the same
 * emoji the chat draws rather than the {@code :shortcode:} standing for
 * them. The size and the wrapping width are LOTR's own, so the speech
 * reads where theirs did.</p>
 *
 * <p>Drawn from the entity's own render pass, which is where vanilla
 * hangs a nameplate: the position comes in with the event rather than
 * being worked out from the camera, so it lands over the head whatever
 * the camera is doing — the third-person camera this mod carries
 * included. Nothing is shown while the chat screen itself is open; the
 * words are already being read there.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesSpeechBubbleRenderer {
    /** LOTR's own speech scale, so ours reads at the size theirs did. */
    private static final float BUBBLE_SCALE = 0.015F;
    /** Widest a line may run before it wraps, in font pixels; LOTR's. */
    private static final int MAX_WIDTH = 150;
    /** Where the lowest row sits above the speaker's head. */
    private static final double BUBBLE_Y_OFFSET = 0.5D;
    /** Speech is a local thing; past this nothing is drawn at all. */
    private static final double MAX_DISTANCE = 48.0D;
    /** Rows of speech drawn over one head at most, newest kept. */
    private static final int MAX_ROWS = 4;
    private static final int ROW_STRIDE = 10;
    /** Clear air between the name and the words under it. */
    private static final int NAME_GAP = 4;
    private static final int PADDING_X = 3;
    private static final int PADDING_Y = 1;
    /** An emoji's size inline, and the room it takes with its gap. */
    private static final float EMOJI_SIZE = 8.0F;
    private static final int EMOJI_ADVANCE = 9;
    /** The chat's black behind the words: half, as the chat's own is. */
    private static final int BACKDROP_ALPHA = 0x80;
    /**
     * How far LOTR's floating alignment is lifted so it clears what a
     * speaker wears: the anchor, plus a name and the rows of speech that
     * fit over one head, plus a little air. The alignment is a spawned
     * effect with a position of its own, so it cannot be measured
     * against one speaker — it is lifted for everyone, by enough to
     * clear the tallest thing that can stand there.
     */
    private static final double ALIGNMENT_LIFT = BUBBLE_Y_OFFSET
            + (MAX_ROWS + 1) * (ROW_STRIDE + NAME_GAP) * BUBBLE_SCALE + 0.15D;

    private LostTalesSpeechBubbleRenderer() {}

    /**
     * Where LOTR's floating alignment is drawn, asked by the coremod
     * from that effect's own renderer: above whatever a speaker wears,
     * rather than through the middle of it.
     */
    public static double liftAlignment(double y) {
        return LostTalesConfig.showChatSpeechBubbles
                ? y + ALIGNMENT_LIFT : y;
    }

    /**
     * Whether this entity's floating name is Lost Tales' to draw, asked
     * by the coremod from vanilla's own label draw. While a player has
     * words over their head we put the name the chat signs them with
     * above those words, in its own colour, and vanilla's plain account
     * name in the same place would only double it. Nobody sees their own
     * nameplate, so the local player is left alone, and an NPC's name is
     * LOTR's.
     */
    public static boolean hidesNameplate(EntityLivingBase speaker) {
        try {
            if (!LostTalesConfig.showChatSpeechBubbles
                    || !(speaker instanceof EntityPlayer)
                    || speaker == Minecraft.getMinecraft().thePlayer
                    || ChatSpeechBubbles.isEmpty()) {
                return false;
            }
            ChatSpeechBubbles.Speech speech = ChatSpeechBubbles.speechOf(
                    speaker.getUniqueID(), System.nanoTime());
            return speech != null && speech.name.length() > 0;
        } catch (Throwable ignored) {
            // Vanilla drawing its own name is always the safe answer.
            return false;
        }
    }

    /**
     * Called for one speaker as they are rendered. {@code x}, {@code y}
     * and {@code z} are the offsets the render pass was given, so the
     * speaker's feet are exactly there.
     */
    public static void render(EntityLivingBase speaker, double x, double y,
                              double z) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!LostTalesConfig.showChatSpeechBubbles || speaker == null
                || minecraft == null || minecraft.fontRenderer == null
                || minecraft.currentScreen instanceof GuiChat
                || speaker.isInvisible()
                || ChatSpeechBubbles.isEmpty()) {
            return;
        }
        if (x * x + y * y + z * z > MAX_DISTANCE * MAX_DISTANCE) {
            return;
        }
        // LOTR draws an NPC's speech itself unless the coremod took that
        // pass over. If it did not, staying out of the way is the only
        // safe thing to do: two sets of words over one head is worse
        // than LOTR's own. A player's speech is nobody else's to draw.
        if (!(speaker instanceof EntityPlayer)
                && !Boolean.getBoolean(LostTalesClassTransformer
                        .NPC_SPEECH_RENDER_ACTIVE_PROPERTY)) {
            return;
        }
        long now = System.nanoTime();
        ChatSpeechBubbles.Speech speech =
                ChatSpeechBubbles.speechOf(speaker.getUniqueID(), now);
        if (speech == null) {
            return;
        }
        List<Row> rows = layOut(minecraft.fontRenderer, speech, now);
        if (rows.isEmpty()) {
            return;
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            RenderManager renderManager = RenderManager.instance;
            GL11.glTranslated(x, y + speaker.height + BUBBLE_Y_OFFSET, z);
            GL11.glNormal3f(0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
            GL11.glScalef(-BUBBLE_SCALE, -BUBBLE_SCALE, BUBBLE_SCALE);
            draw(minecraft, minecraft.fontRenderer, rows);
        } finally {
            GL11.glPopMatrix();
            GL11.glDepthMask(true);
            GL11.glPopAttrib();
        }
    }

    /**
     * The speaker's name over their words, oldest line at the top so the
     * newest sits nearest the head. Each line keeps its own opacity, so
     * an older one thins out from under a newer one; the name goes with
     * the <em>newest</em> of them, since the speaker is still there for
     * as long as anything they said is, and a name that faded with the
     * first line would come back with the second.
     */
    private static List<Row> layOut(FontRenderer font,
                                    ChatSpeechBubbles.Speech speech,
                                    long nowNanos) {
        List<ChatSpeechBubbles.Line> lines = speech.lines;
        List<Row> rows = new ArrayList<Row>(MAX_ROWS + 1);
        float newest = 0.0F;
        for (int index = 0; index < lines.size(); index++) {
            ChatSpeechBubbles.Line line = lines.get(index);
            float opacity = line.opacity(nowNanos);
            if (opacity <= 0.0F) {
                continue;
            }
            newest = Math.max(newest, opacity);
            List<?> wrapped = font.listFormattedStringToWidth(line.text,
                    MAX_WIDTH);
            for (int part = 0; part < wrapped.size(); part++) {
                rows.add(Row.of(font, String.valueOf(wrapped.get(part)),
                        opacity, LostTalesChatVisualStyle.IVORY, 0));
            }
        }
        while (rows.size() > MAX_ROWS) {
            rows.remove(0);
        }
        // The name over the words, in the very colour the chat signs the
        // line with — a hobbit's green here and in the log both — and
        // standing clear of them the way LOTR's does.
        if (!rows.isEmpty() && speech.name.length() > 0) {
            rows.add(0, Row.of(font, speech.name, newest, speech.nameColor,
                    NAME_GAP));
        }
        return rows;
    }

    /** The stack, growing upward so its last row stands on the anchor. */
    private static void draw(Minecraft minecraft, FontRenderer font,
                             List<Row> rows) {
        int height = 0;
        for (int index = 0; index < rows.size(); index++) {
            height += ROW_STRIDE + rows.get(index).gapBelow;
        }
        int y = -height;
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            int alpha = Math.round(255.0F * row.opacity);
            // FontRenderer treats an alpha under four as fully opaque, so
            // a line at the very end of its fade would flash back at full
            // strength instead of going out. Nothing is drawn there.
            if (alpha >= LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
                int half = row.width / 2;
                drawBackdrop(-half - PADDING_X, y - PADDING_Y,
                        half + PADDING_X, y + ROW_STRIDE - 1 - PADDING_Y,
                        row.opacity);
                drawRow(minecraft, font, row, -half, y, alpha);
            }
            y += ROW_STRIDE + row.gapBelow;
        }
    }

    /** One row: its runs of text and its emoji, left to right. */
    private static void drawRow(Minecraft minecraft, FontRenderer font,
                                Row row, int left, int y, int alpha) {
        int shadowAlpha = Math.round(160.0F * row.opacity);
        int x = left;
        for (int index = 0; index < row.parts.size(); index++) {
            ChatEmojiParser.Segment part = row.parts.get(index);
            if (part.isEmoji()) {
                ChatInlineIcons.drawEmoji(minecraft, part.getEmoji(), x,
                        y - 1.0F, EMOJI_SIZE, alpha);
                x += EMOJI_ADVANCE;
                continue;
            }
            String text = part.getText();
            if (shadowAlpha >= LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
                font.drawString(text, x + 1, y + 1,
                        (shadowAlpha << 24)
                                | LostTalesChatVisualStyle.SHADOW);
            }
            font.drawString(text, x, y,
                    (alpha << 24) | (row.rgb & 0xFFFFFF));
            x += font.getStringWidth(text);
        }
    }

    /** The chat's own black, behind one row of speech. */
    private static void drawBackdrop(float left, float top, float right,
                                     float bottom, float opacity) {
        int alpha = Math.round(BACKDROP_ALPHA * opacity);
        if (alpha <= 0) {
            return;
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_I(LostTalesChatVisualStyle.SURFACE_RGB,
                alpha);
        tessellator.addVertex(left, bottom, 0.0D);
        tessellator.addVertex(right, bottom, 0.0D);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.addVertex(left, top, 0.0D);
        tessellator.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /**
     * One drawn row: the speaker's name, or a wrapped piece of a line,
     * split into the runs of text and the emoji the chat would draw, and
     * measured once so the row can be centred.
     */
    private static final class Row {
        private final List<ChatEmojiParser.Segment> parts;
        private final int width;
        private final float opacity;
        private final int rgb;
        private final int gapBelow;

        private Row(List<ChatEmojiParser.Segment> parts, int width,
                    float opacity, int rgb, int gapBelow) {
            this.parts = parts;
            this.width = width;
            this.opacity = opacity;
            this.rgb = rgb;
            this.gapBelow = gapBelow;
        }

        static Row of(FontRenderer font, String text, float opacity,
                      int rgb, int gapBelow) {
            List<ChatEmojiParser.Segment> parts = ChatEmojiParser.split(text);
            int width = 0;
            for (int index = 0; index < parts.size(); index++) {
                ChatEmojiParser.Segment part = parts.get(index);
                width += part.isEmoji() ? EMOJI_ADVANCE
                        : font.getStringWidth(part.getText());
            }
            return new Row(parts, width, opacity, rgb, gapBelow);
        }
    }
}
