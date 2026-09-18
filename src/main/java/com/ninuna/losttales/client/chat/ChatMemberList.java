package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.network.packet.LostTalesChatMembersPacket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;

/**
 * A window's member list: who is in the conversation in front, down the
 * window's right-hand side, the way Discord lists a channel's members
 * beside its messages. It stands on the chat's inset surface, as the
 * timestamp area does on the left, beside the history between the
 * window's rules, with a separator of its own, and it scrolls on its own.
 *
 * <p>Every conversation has one. The server says who is in it and how
 * they are grouped ({@link ClientChatMembers}): those here by faction on
 * an in-character channel and by highest role on an out-of-character one,
 * each group headed with its name and count, those without a role under
 * Online; then under Offline everyone else who may read it, by name — its
 * count taking in those an answer too long to list leaves out, who are
 * counted on a line of their own at the end. A whisper lists its two
 * people and the Client Console the player alone; a conversation with an
 * NPC lists the player and the NPC, whom this client adds itself
 * ({@link #membersOf}).</p>
 *
 * <p>The rows are the chat's small text throughout: everything a row
 * holds is laid out in the list's own units, one of the words' pixels
 * each, and drawn at {@link LostTalesChatVisualStyle#stackSmallScale} —
 * the head, its sphere, the name and the title under it alike — so a long
 * name has room, and every piece keeps whole display pixels. A row is the
 * member's head, wearing its status as every head does — Offline for
 * everyone under Offline — their name in the colour it wears in the
 * conversation, and their LOTR title under it where they carry one. The
 * absent are drawn fainter ({@link #OFFLINE_OPACITY}) until the pointer
 * lights them, as Discord's are. A click opens the member's card and a
 * right-click their menu, as on a name in a message.</p>
 *
 * <p>The list comes out and goes away with the button at the tool strip's
 * right end, sliding in from the window's right edge while the words give
 * it their room; a window too narrow to keep {@link #MIN_MESSAGE_WIDTH}
 * for its words keeps its list away.</p>
 */
final class ChatMemberList {
    /** The list's width in the chat's pixels, its separator included. */
    static final int WIDTH = 100;
    /** The least room the words keep beside a list; narrower, the list stays away. */
    static final int MIN_MESSAGE_WIDTH = 160;
    /** A member's row, in the list's units: the avatar-sized head and three clear rows above and below. */
    static final int ROW_HEIGHT = 22;
    /** A group's heading, in the list's units: the capitals and clear rows round them. */
    static final int HEADER_HEIGHT = 14;
    /** Clear space above a heading that follows a group, in the list's units. */
    static final int GROUP_GAP = 6;
    /** Clear rows at the top and the foot of the list, in the list's units. */
    static final int PAD = 4;
    /** Clear space between the separator and a row's head or a heading, in the list's units. */
    static final int INSET = 5;
    /** Clear space between a head's sphere and the name, in the list's units. */
    static final int NAME_GAP = 3;
    /** Clear space between the name's capitals and the title's, in the list's units. */
    static final int TITLE_GAP = 3;
    /** Clear space the names keep from the window's edge, in the list's units. */
    static final int RIGHT_GAP = 3;
    /** How much of its own opacity an absent member is drawn at while the pointer is elsewhere. */
    static final float OFFLINE_OPACITY = 0.5F;
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /** One row of the list: a group's heading or a line of its own, or a member. */
    static final class Row {
        /** The heading's words; null on a member's row. */
        final String heading;
        final LostTalesChatMembersPacket.Member member;
        /** The row's top, in the list's units, measured down from the list's own top. */
        final int top;
        final int height;

        private Row(String heading, LostTalesChatMembersPacket.Member member,
                    int top, int height) {
            this.heading = heading;
            this.member = member;
            this.top = top;
            this.height = height;
        }
    }

    /** One window's list: where it is scrolled to, and what the pointer is on. */
    static final class State {
        /** The answer and the tab the rows were laid out for; they are laid out again for others. */
        ClientChatMembers.Answer laidFor;
        ChatTab laidTab;
        /** How far the list is scrolled, in the chat's pixels, as drawn. */
        float scroll;
        /** Where the scroll is heading. */
        float scrollTarget;
        long scrollNanos;
        /** The rows as last laid out, and the list's height with them in the chat's pixels. */
        List<Row> rows = Collections.emptyList();
        float contentHeight;
        /** The member the pointer rests on, or null. */
        LostTalesChatMembersPacket.Member hovered;
        float hoverFade;
        long hoverNanos;
        /** The last member lit, kept while its light fades. */
        LostTalesChatMembersPacket.Member lit;
        /** Where the list stood on screen last frame, and each member's box there. */
        float screenLeft;
        float screenTop;
        float screenRight;
        float screenBottom;
        final List<float[]> memberBoxes = new ArrayList<float[]>();
        final List<LostTalesChatMembersPacket.Member> boxMembers =
                new ArrayList<LostTalesChatMembersPacket.Member>();

        /** Forgets where the list stood: it is not on screen this frame. */
        void clearDrawn() {
            this.memberBoxes.clear();
            this.boxMembers.clear();
            this.screenRight = this.screenLeft;
            this.hovered = null;
        }
    }

    private ChatMemberList() {}

    /** How much of the list stands in the window, in the chat's pixels. */
    static float drawnWidth(ChatWindowFrame frame) {
        return frame == null ? 0.0F : WIDTH * frame.membersShare();
    }

    /** Whether a window whose words have {@code messageWidth} pixels keeps room for a list. */
    static boolean fits(float messageWidth) {
        return messageWidth - WIDTH >= MIN_MESSAGE_WIDTH;
    }

    /** The chat's pixels to one of the list's units: the chat's small text. */
    static float unit() {
        return LostTalesChatVisualStyle.stackSmallScale();
    }

    /**
     * The members a tab's list shows: the server's answer, and in a
     * conversation with an NPC the NPC too, whom the server knows nothing
     * of, here and in the order the list keeps.
     */
    static List<LostTalesChatMembersPacket.Member> membersOf(ChatTab tab,
            List<LostTalesChatMembersPacket.Member> answered) {
        if (tab == null || !tab.isNpc()) {
            return answered;
        }
        List<LostTalesChatMembersPacket.Member> members =
                new ArrayList<LostTalesChatMembersPacket.Member>(answered);
        members.add(npcOf(tab, answered));
        Collections.sort(members, LostTalesChatMembersPacket.ORDER);
        return members;
    }

    /**
     * The NPC a conversation is with, as its list shows it: its portrait
     * for a head, its name in the colour its speech wore, and the faction
     * it spoke for as its group — the group of a member here that already
     * goes by that name, so the two stand under one heading — or the plain
     * one where no faction was captured.
     */
    static LostTalesChatMembersPacket.Member npcOf(ChatTab tab,
            List<LostTalesChatMembersPacket.Member> answered) {
        UUID id = ChatChannelIcons.npcId(tab);
        if (id == null) {
            id = UUID.nameUUIDFromBytes(("npc:" + tab.getPartnerIdentity())
                    .getBytes(UTF_8));
        }
        String faction = ChatChannelIcons.npcFaction(id);
        String groupKey = "";
        String groupName = "";
        int groupOrder = 0;
        if (faction != null && faction.trim().length() > 0) {
            groupName = faction.trim();
            groupKey = "npc:" + groupName.toLowerCase(Locale.ROOT);
            for (LostTalesChatMembersPacket.Member member : answered) {
                if (member.isOnline()
                        && member.getGroupName().equalsIgnoreCase(groupName)) {
                    groupKey = member.getGroupKey();
                    groupOrder = member.getGroupOrder();
                    break;
                }
            }
        }
        return LostTalesChatMembersPacket.Member.npc(id, tab.getPartnerIdentity(),
                ClientChatChannelState.displayColor(tab),
                ChatChannelIcons.npcPortrait(tab), groupKey, groupName,
                groupOrder);
    }

    /**
     * The list's rows for the members as they stand, in the list's units:
     * a heading over each group of those here — their faction or role, or
     * Online for those without a role — with the group's count, and the
     * absent under Offline at the end, whose count takes in the
     * {@code unlisted} absent the answer left out, counted on a line of
     * their own after the last one listed.
     */
    static List<Row> layOut(List<LostTalesChatMembersPacket.Member> members,
                            int unlisted) {
        List<Row> rows = new ArrayList<Row>();
        int top = PAD;
        int index = 0;
        int count = members == null ? 0 : members.size();
        boolean offlineHeaded = false;
        while (index < count) {
            LostTalesChatMembersPacket.Member first = members.get(index);
            int end = index + 1;
            while (end < count && sameGroup(first, members.get(end))) {
                end++;
            }
            if (!rows.isEmpty()) {
                top += GROUP_GAP;
            }
            boolean offline = !first.isOnline();
            offlineHeaded |= offline;
            rows.add(new Row(headingOf(first, end - index
                    + (offline ? unlisted : 0)), null, top, HEADER_HEIGHT));
            top += HEADER_HEIGHT;
            for (int at = index; at < end; at++) {
                rows.add(new Row(null, members.get(at), top, ROW_HEIGHT));
                top += ROW_HEIGHT;
            }
            index = end;
        }
        if (unlisted > 0) {
            if (!offlineHeaded) {
                if (!rows.isEmpty()) {
                    top += GROUP_GAP;
                }
                rows.add(new Row(offlineHeading(unlisted), null, top,
                        HEADER_HEIGHT));
                top += HEADER_HEIGHT;
            }
            rows.add(new Row(StatCollector.translateToLocalFormatted(
                    "gui.losttales.chat.members.more",
                    Integer.toString(unlisted)), null, top, HEADER_HEIGHT));
        }
        return rows;
    }

    /** The list's height with its rows laid out, its padding included, in the list's units. */
    static int heightOf(List<Row> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        Row last = rows.get(rows.size() - 1);
        return last.top + last.height + PAD;
    }

    private static boolean sameGroup(LostTalesChatMembersPacket.Member one,
                                     LostTalesChatMembersPacket.Member other) {
        return one.isOnline() == other.isOnline()
                && (!one.isOnline()
                        || one.getGroupKey().equals(other.getGroupKey()));
    }

    /** A group's heading: its name and how many stand in it, {@code Gondor — 3}. */
    static String headingOf(LostTalesChatMembersPacket.Member first,
                            int count) {
        if (!first.isOnline()) {
            return offlineHeading(count);
        }
        String name = first.getGroupName().length() > 0 ? first.getGroupName()
                : StatCollector.translateToLocal("gui.losttales.chat.members.online");
        return StatCollector.translateToLocalFormatted(
                "gui.losttales.chat.members.heading", name,
                Integer.toString(count));
    }

    private static String offlineHeading(int count) {
        return StatCollector.translateToLocalFormatted(
                "gui.losttales.chat.members.heading",
                StatCollector.translateToLocal("gui.losttales.chat.members.offline"),
                Integer.toString(count));
    }

    /**
     * The opacity a member's row is drawn at: an absent member's
     * {@link #OFFLINE_OPACITY} of the list's, rising to the whole as far as
     * the pointer lights the row, as {@code lit} says.
     */
    static int rowAlpha(LostTalesChatMembersPacket.Member member, int alpha,
                        float lit) {
        if (member.isOnline()) {
            return alpha;
        }
        float light = Math.max(0.0F, Math.min(1.0F, lit));
        return Math.round(alpha * (OFFLINE_OPACITY
                + (1.0F - OFFLINE_OPACITY) * light));
    }

    /**
     * Moves the list's scroll on toward where it is heading, as the
     * history's scroll eases, and keeps it inside the list, whose room is
     * {@code room} pixels tall.
     */
    static void advanceScroll(State state, float room) {
        long now = System.nanoTime();
        double elapsed = state.scrollNanos == 0L ? 0.0D
                : (now - state.scrollNanos) / 1.0E9D;
        state.scrollNanos = now;
        float most = Math.max(0.0F, state.contentHeight - room);
        state.scrollTarget = Math.max(0.0F, Math.min(most, state.scrollTarget));
        if (!LostTalesConfig.enableChatAnimations) {
            state.scroll = state.scrollTarget;
            return;
        }
        state.scroll = (float)LostTalesChatMotion.approach(state.scroll,
                state.scrollTarget, elapsed,
                LostTalesChatMotion.SCROLL_EASE_SECONDS);
        if (Math.abs(state.scroll - state.scrollTarget) < 0.05F) {
            state.scroll = state.scrollTarget;
        }
    }

    /** Turns the wheel over the list: {@code pixels} down, negative up. */
    static void scrollBy(State state, float pixels) {
        state.scrollTarget += pixels;
    }

    /** The member whose row is under a screen point, or null. */
    static LostTalesChatMembersPacket.Member memberAt(State state, double x,
                                                     double y) {
        for (int index = 0; index < state.memberBoxes.size(); index++) {
            float[] box = state.memberBoxes.get(index);
            if (x >= box[0] && x < box[2] && y >= box[1] && y < box[3]) {
                return state.boxMembers.get(index);
            }
        }
        return null;
    }

    /** Whether a screen point is on the list where it stood last frame. */
    static boolean contains(State state, double x, double y) {
        return state.screenRight > state.screenLeft && x >= state.screenLeft
                && x < state.screenRight && y >= state.screenTop
                && y < state.screenBottom;
    }

    /**
     * A member's head: whose it is and what it is drawn with, wearing the
     * status of the identity the list shows, as a message's avatar does —
     * Offline for an absent member, whose character may be one the player
     * plays without speaking as it — or an NPC's portrait, which wears none.
     */
    static ChatHeadMarker.Data headOf(LostTalesChatMembersPacket.Member member) {
        if (member.isNpc()) {
            return ChatHeadMarker.Data.npc(member.getPlayerId(),
                    member.getSkinId());
        }
        return ChatHeadMarker.Data.member(member.getPlayerId(),
                member.getCharacterId() == null,
                member.isOnline() ? member.getCharacterId() : null,
                member.getSkinId());
    }

    /**
     * Draws a window's list in the window's own space, whose units are
     * the chat's pixels: its surface from {@code left} to the window's
     * right edge at {@code windowRight} between the rules at {@code top}
     * and {@code bottom} — the list is {@link #WIDTH} wide and stands as
     * far into the window as it has come out, the rest past the edge —
     * its separator, and its rows, cut to the room the history's rules
     * leave. {@code originX}, {@code originY} and {@code scale} carry the
     * space onto the screen, where the rows are recorded for the pointer.
     */
    static void draw(Minecraft minecraft, FontRenderer font,
                     ChatWindowFrame frame, float left, float top,
                     float bottom, float windowRight, float clipTop,
                     float clipBottom, float originX, float originY,
                     float scale, int surfaceAlpha, int alpha) {
        State state = frame.members;
        state.memberBoxes.clear();
        state.boxMembers.clear();
        state.screenLeft = originX + left * scale;
        state.screenRight = originX + windowRight * scale;
        state.screenTop = clipTop;
        state.screenBottom = clipBottom;
        if (windowRight <= left + 1.0F) {
            state.clearDrawn();
            return;
        }
        ChatTab tab = frame.view;
        ClientChatMembers.requestIfDue(tab);
        ClientChatMembers.Answer answer = ClientChatMembers.of(tab);
        if (answer == null) {
            state.rows = Collections.emptyList();
            state.laidFor = null;
            state.laidTab = null;
        } else if (state.laidFor != answer || !tab.equals(state.laidTab)) {
            state.rows = layOut(membersOf(tab, answer.members), answer.unlisted);
            state.laidFor = answer;
            state.laidTab = tab;
        }
        float unit = unit();
        state.contentHeight = heightOf(state.rows) * unit;
        advanceScroll(state, bottom - top);
        long now = System.nanoTime();
        double elapsed = state.hoverNanos == 0L ? 0.0D
                : (now - state.hoverNanos) / 1.0E9D;
        state.hoverNanos = now;
        if (state.hovered != null) {
            state.lit = state.hovered;
        }
        state.hoverFade = LostTalesChatVisualStyle.hoverFade(state.hoverFade,
                state.hovered != null, elapsed);
        // The list's own surface: the chat's inset one, as the timestamp
        // area wears on the other side, standing beside the history's
        // panel rather than over it.
        LostTalesChatOverlayRenderer.fillRect(left, top, windowRight, bottom,
                LostTalesChatVisualStyle.argb(LostTalesChatVisualStyle.SURFACE_RGB,
                        surfaceAlpha));
        // The rows' own space starts past the separator, on the display's
        // grid, so its whole units land on whole display pixels while the
        // list slides.
        float rowsLeft = LostTalesChatOverlayRenderer.floorToStackPixel(
                left + ChatTimestampColumn.SEPARATOR_WIDTH);
        float roomRight = (left + WIDTH - rowsLeft) / unit - RIGHT_GAP;
        float rowsOriginX = originX + rowsLeft * scale;
        boolean clipped = LostTalesChatOverlayRenderer.beginClip(minecraft,
                state.screenLeft, state.screenRight, clipTop, clipBottom,
                true);
        try {
            for (Row row : state.rows) {
                // Rows move by whole display pixels, as the history's
                // stack does: the heads are pixel art, and at a fraction
                // of a pixel their texels crawl.
                float rowTop = LostTalesChatOverlayRenderer.floorToStackPixel(
                        top + row.top * unit - state.scroll);
                float rowBottom = rowTop + row.height * unit;
                if (rowBottom < top || rowTop > bottom) {
                    continue;
                }
                if (row.member == null) {
                    drawHeading(minecraft, font, row.heading, rowsLeft, rowTop,
                            unit, roomRight, rowsOriginX, scale, clipTop,
                            clipBottom, alpha);
                    continue;
                }
                float lit = row.member == state.lit ? state.hoverFade : 0.0F;
                if (lit > 0.0F) {
                    LostTalesChatOverlayRenderer.recolourSurface(rowsLeft,
                            rowTop, windowRight, rowBottom, surfaceAlpha,
                            LostTalesChatVisualStyle.SURFACE_RGB,
                            LostTalesChatVisualStyle.blend(
                                    LostTalesChatVisualStyle.SURFACE_RGB,
                                    LostTalesChatVisualStyle.selectedLineRgb(),
                                    lit));
                }
                drawMember(minecraft, font, row.member, rowsLeft, rowTop, unit,
                        roomRight, rowsOriginX, scale, clipTop, clipBottom,
                        rowAlpha(row.member, alpha, lit));
                float boxTop = Math.max(clipTop, originY + rowTop * scale);
                float boxBottom = Math.min(clipBottom, originY + rowBottom * scale);
                if (boxBottom > boxTop) {
                    state.memberBoxes.add(new float[] {state.screenLeft,
                            boxTop, state.screenRight, boxBottom});
                    state.boxMembers.add(row.member);
                }
            }
        } finally {
            LostTalesChatOverlayRenderer.endVerticalClip(clipped);
        }
        LostTalesChatOverlayRenderer.drawVerticalRule(left, left
                        + ChatTimestampColumn.SEPARATOR_WIDTH, top, bottom,
                alpha);
    }

    /**
     * A heading, or the line counting the absent left out: the chat's
     * small text in its aside tone, its capitals centred in the row, the
     * odd pixel up, sinking into the list's edge where it is cut.
     */
    private static void drawHeading(Minecraft minecraft, FontRenderer font,
                                    String heading, float rowsLeft,
                                    float rowTop, float unit, float roomRight,
                                    float rowsOriginX, float scale,
                                    float clipTop, float clipBottom,
                                    int alpha) {
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(rowsLeft, rowTop, 0.0F);
            GL11.glScalef(unit, unit, 1.0F);
            drawCutText(minecraft, font, heading, INSET,
                    LostTalesUiInk.centredStart(HEADER_HEIGHT,
                            LostTalesChatOverlayRenderer.GLYPH_CAP_HEIGHT),
                    LostTalesChatVisualStyle.asideRgb(), alpha, roomRight,
                    rowsOriginX, scale * unit, clipTop, clipBottom);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * One member's row, in the list's units: the head and its sphere, the
     * name — and the title under it where the member carries one — the
     * two centred together on the row, the odd pixel up, each sinking into
     * the list's edge where it is cut.
     */
    private static void drawMember(Minecraft minecraft, FontRenderer font,
                                   LostTalesChatMembersPacket.Member member,
                                   float rowsLeft, float rowTop, float unit,
                                   float roomRight, float rowsOriginX,
                                   float scale, float clipTop,
                                   float clipBottom, int alpha) {
        int capitals = LostTalesChatOverlayRenderer.GLYPH_CAP_HEIGHT;
        boolean titled = member.getTitle().length() > 0;
        int nameTop = LostTalesUiInk.centredStart(ROW_HEIGHT,
                titled ? capitals + TITLE_GAP + capitals : capitals);
        int textLeft = INSET + ChatAvatar.ICON_WIDTH + NAME_GAP;
        float unitScale = scale * unit;
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(rowsLeft, rowTop, 0.0F);
            GL11.glScalef(unit, unit, 1.0F);
            LostTalesChatOverlayRenderer.drawFace(minecraft, headOf(member),
                    INSET, LostTalesUiInk.centredStart(ROW_HEIGHT,
                            ChatAvatar.SIZE), ChatAvatar.SIZE, alpha);
            drawCutText(minecraft, font, member.getName(), textLeft, nameTop,
                    member.getNameColor(), alpha, roomRight, rowsOriginX,
                    unitScale, clipTop, clipBottom);
            if (titled) {
                drawCutText(minecraft, font, member.getTitle(), textLeft,
                        nameTop + capitals + TITLE_GAP, member.getTitleColor(),
                        alpha, roomRight, rowsOriginX, unitScale, clipTop,
                        clipBottom);
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * Words from {@code x} that sink into the edge at {@code right} where
     * they are cut, as a cut tab name does; {@code originX} and
     * {@code scale} carry the words' space onto the screen, and the words
     * stay between the history's rules.
     */
    private static void drawCutText(Minecraft minecraft, FontRenderer font,
                                    String text, int x, int y, int rgb,
                                    int alpha, float right, float originX,
                                    float scale, float clipTop,
                                    float clipBottom) {
        int width = font.getStringWidth(text);
        float room = right - x;
        if (room <= 0.0F) {
            return;
        }
        if (width <= room) {
            LostTalesChatVisualStyle.drawColored(font, text, x, y, rgb, alpha);
            return;
        }
        float depth = LostTalesChatOverlayRenderer.sideFadeDepth(room * scale);
        LostTalesChatOverlayRenderer.drawFadingText(minecraft, font, text, x,
                0.0F, y, rgb, alpha, originX + x * scale,
                originX + right * scale, clipTop, clipBottom, depth, 0.0F,
                LostTalesChatOverlayRenderer.sideFadeStrength(
                        (width - room) * scale, depth));
    }
}
