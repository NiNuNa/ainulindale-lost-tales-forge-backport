package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatEpithet;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import com.ninuna.losttales.gui.style.LostTalesUiFlatLayers;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.network.packet.LostTalesChatMembersPacket;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.motion.MotionIds;
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
 * for its words keeps its list away. Its left edge is a handle: dragged,
 * the list grows to a third of the window or narrows to its heads alone,
 * and a name its row cuts short slides along while the pointer rests on
 * it, as a tab's does.</p>
 */
final class ChatMemberList {
    /** The list's own width in the chat's pixels, its separator included. */
    static final int DEFAULT_WIDTH = 100;
    /** The most of its window's width a list may take. */
    static final float MAX_SHARE = 1.0F / 3.0F;
    /**
     * How far into the list its edge answers as a handle, in GUI pixels,
     * from the separator in: the history's scrollbar answers up to the
     * separator from the other side.
     */
    static final float EDGE_HANDLE = 4.0F;
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

    /** One window's list: its width, where it is scrolled to, and what the pointer is on. */
    static final class State {
        /**
         * How wide the list stands in its window this frame, in the
         * chat's pixels, and how wide it may be dragged: what the window
         * asked for, bounded by its heads and a third of the window.
         */
        float width = DEFAULT_WIDTH;
        float minWidth;
        float maxWidth = DEFAULT_WIDTH;
        /** Whether the rows have room for any of a name this frame. */
        boolean namesShown = true;
        /** How long the pointer has rested on {@link #hovered}, for its name's slide. */
        double hoverSeconds;
        /** How far the rested-on row's name and title have slid, in the list's units. */
        float nameSlide;
        float titleSlide;
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
        return frame == null ? 0.0F
                : frame.members.width * frame.membersShare();
    }

    /**
     * Whether a window whose words have {@code messageWidth} pixels keeps
     * room for a list {@code width} wide.
     */
    static boolean fits(float messageWidth, float width) {
        return messageWidth - width >= MIN_MESSAGE_WIDTH;
    }

    /**
     * Lays the list's width out for a window {@code windowWidth} of the
     * chat's pixels wide: the width the player dragged it to, or its own,
     * never past a third of the window nor narrower than its heads.
     */
    static void measure(State state, ChatWindow window, float windowWidth) {
        state.minWidth = minWidth();
        state.maxWidth = Math.max(state.minWidth, windowWidth * MAX_SHARE);
        double chosen = window == null ? 0.0D : window.getMembersWidth();
        state.width = clampWidth(chosen > 0.0D ? (float)chosen
                : DEFAULT_WIDTH, state.minWidth, state.maxWidth);
    }

    /** A width between the list's least and most. */
    static float clampWidth(float width, float least, float most) {
        return Math.max(least, Math.min(most, width));
    }

    /**
     * The narrowest a list may be, in the chat's pixels: its separator,
     * and its heads with their spheres, clear space either side.
     */
    static float minWidth() {
        return ChatTimestampColumn.SEPARATOR_WIDTH
                + (INSET + ChatAvatar.ICON_WIDTH + INSET) * unit();
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
        state.scroll = (float)Motions.followTravel(MotionIds.CHAT_SCROLL,
                state.scroll, state.scrollTarget, elapsed);
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
     * Whether a screen point is on the list's left edge, the handle that
     * resizes it: the separator and a few pixels in from it, where the
     * list stood last frame.
     */
    static boolean edgeContains(State state, double x, double y) {
        return contains(state, x, y)
                && x < state.screenLeft + EDGE_HANDLE;
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
        if (state.hovered != null && state.hovered != state.lit) {
            // Another row under the pointer: its name starts from home.
            state.hoverSeconds = 0.0D;
            state.nameSlide = 0.0F;
            state.titleSlide = 0.0F;
        }
        if (state.hovered != null) {
            state.lit = state.hovered;
            state.hoverSeconds += elapsed;
        } else {
            state.hoverSeconds = 0.0D;
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
        float roomRight = (left + state.width - rowsLeft) / unit - RIGHT_GAP;
        state.namesShown = roomRight > INSET + ChatAvatar.ICON_WIDTH + NAME_GAP;
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
                drawMember(minecraft, font, state, row.member, rowsLeft,
                        rowTop, unit, roomRight, rowsOriginX, scale, clipTop,
                        clipBottom, rowAlpha(row.member, alpha, lit), elapsed);
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
                    0.0F, rowsOriginX, scale * unit, clipTop, clipBottom);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * One member's row, in the list's units: the head and its sphere, the
     * name with its title after it as a line of theirs carries it — {@code
     * Aldric, the Gondor Farmer}, the title in its own colour — and under
     * them the identity's status line in italics in the chat's aside
     * tone, its emojis drawn, the two lines centred together on the row,
     * the odd pixel up, each sinking into the list's edge where it is cut.
     * A row drawn faint is one picture faded ({@link LostTalesUiFlatLayers}):
     * no layer of it — the hat, the face, a shadow — shows through the one
     * above it.
     */
    private static void drawMember(final Minecraft minecraft,
                                   final FontRenderer font, State state,
                                   final LostTalesChatMembersPacket.Member member,
                                   float rowsLeft, float rowTop, float unit,
                                   final float roomRight,
                                   final float rowsOriginX, float scale,
                                   final float clipTop, final float clipBottom,
                                   final int alpha, double elapsed) {
        final int capitals = LostTalesChatOverlayRenderer.GLYPH_CAP_HEIGHT;
        final List<Part> nameLine = new ArrayList<Part>(2);
        nameLine.add(new Part(member.getName(), "", member.getNameColor()));
        if (member.getTitle().length() > 0) {
            nameLine.add(new Part(ChatEpithet.translate(
                    "chat.losttales.title.suffix", ", the %s",
                    ChatEpithet.epithet(member.getGroupName(),
                            member.getTitle())), "", member.getTitleColor()));
        }
        String statusLine = ClientChatProfanity.filter(
                ClientChatPresence.lineOf(member.getPlayerId(),
                        member.getCharacterId() == null
                                ? ChatPresenceIdentity.ACCOUNT
                                : ChatPresenceIdentity.character(
                                        member.getCharacterId())));
        final List<Part> statusRun = statusLine.length() == 0
                ? Collections.<Part>emptyList()
                : Collections.singletonList(new Part(statusLine, "\u00a7o",
                        LostTalesChatVisualStyle.asideRgb()));
        final boolean said = !statusRun.isEmpty();
        final int nameTop = LostTalesUiInk.centredStart(ROW_HEIGHT,
                said ? capitals + TITLE_GAP + capitals : capitals);
        final int textLeft = INSET + ChatAvatar.ICON_WIDTH + NAME_GAP;
        final float unitScale = scale * unit;
        // A line cut short slides along while the pointer rests on its
        // row, as a cut tab name does, and glides home after; one row at
        // a time, the one last lit, and every other stays home.
        boolean lit = member == state.lit;
        boolean rested = member == state.hovered;
        final float nameSlide = lit ? slide(state, rested, true,
                width(font, nameLine), roomRight - textLeft,
                unitScale, elapsed) : 0.0F;
        final float statusSlide = lit && said ? slide(state, rested, false,
                width(font, statusRun), roomRight - textLeft,
                unitScale, elapsed) : 0.0F;
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(rowsLeft, rowTop, 0.0F);
            GL11.glScalef(unit, unit, 1.0F);
            LostTalesUiFlatLayers.draw(alpha, 0.0F, 0.0F, roomRight
                    + RIGHT_GAP, ROW_HEIGHT, new LostTalesUiFlatLayers.Layers() {
                        @Override
                        public void draw() {
                            LostTalesChatOverlayRenderer.drawFace(minecraft,
                                    headOf(member), INSET,
                                    LostTalesUiInk.centredStart(ROW_HEIGHT,
                                            ChatAvatar.SIZE),
                                    ChatAvatar.SIZE, alpha);
                            LostTalesUiFlatLayers.nextLayer();
                            drawCutParts(minecraft, font, nameLine,
                                    textLeft, nameTop, alpha, roomRight,
                                    nameSlide, rowsOriginX, unitScale,
                                    clipTop, clipBottom);
                            if (said) {
                                LostTalesUiFlatLayers.nextLayer();
                                drawCutParts(minecraft, font, statusRun,
                                        textLeft, nameTop + capitals
                                                + TITLE_GAP, alpha,
                                        roomRight, statusSlide, rowsOriginX,
                                        unitScale, clipTop, clipBottom);
                            }
                        }
                    });
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * How far a row's name ({@code name}) or title has slid, in the list's
     * units: along while the pointer rests on the row and the words run
     * past their {@code room}, by the tab's own marquee, home again after
     * — laid on a display pixel, so the glyphs stay on theirs.
     */
    private static float slide(State state, boolean rested, boolean name,
                               int width, float room, float unitScale,
                               double elapsed) {
        int overflow = (int)Math.ceil(width - room);
        float current = name ? state.nameSlide : state.titleSlide;
        float next;
        if (rested && overflow > 0 && room > 0.0F && Motions.enabled()) {
            next = (float)ChatChannelTabBar.marqueeOffset(state.hoverSeconds,
                    overflow);
        } else {
            next = ChatChannelTabBar.eased(current, 0.0F, elapsed);
        }
        if (name) {
            state.nameSlide = next;
        } else {
            state.titleSlide = next;
        }
        double step = ChatChannelTabBar.displayStep() / unitScale;
        return (float)ChatChannelTabBar.snapped(next, step);
    }

    /**
     * Words from {@code x} that sink into the edge at {@code right} where
     * they are cut, as a cut tab name does, slid {@code slide} units left
     * — sinking into the left edge too as far as they have gone past it;
     * {@code originX} and {@code scale} carry the words' space onto the
     * screen, and the words stay between the history's rules.
     */
    /** One piece of a row's line: its words, a formatting code ahead of them, their colour. */
    private static final class Part {
        final String text;
        final String style;
        final int rgb;

        Part(String text, String style, int rgb) {
            this.text = text == null ? "" : text;
            this.style = style;
            this.rgb = rgb;
        }
    }

    /** A line's drawn width, its emojis in their slots. */
    private static int width(FontRenderer font, List<Part> parts) {
        int width = 0;
        for (Part part : parts) {
            width += ChatInlineText.width(font, part.text, part.style);
        }
        return width;
    }

    /**
     * A line of pieces, each in its own colour and its emojis drawn, from
     * {@code x}, cut at {@code right} and sinking into that edge — and
     * into the left one as far as it has slid past it — as a cut tab name
     * does.
     */
    private static void drawCutParts(final Minecraft minecraft,
                                     final FontRenderer font,
                                     final List<Part> parts, final int x,
                                     final int y, final int alpha,
                                     float right, final float slide,
                                     float originX, float scale,
                                     float clipTop, float clipBottom) {
        int width = width(font, parts);
        float room = right - x;
        if (room <= 0.0F) {
            return;
        }
        if (width <= room) {
            drawParts(minecraft, font, parts, x, y, alpha);
            return;
        }
        float depth = LostTalesChatOverlayRenderer.sideFadeDepth(room * scale);
        LostTalesChatOverlayRenderer.drawFading(minecraft, originX + x * scale,
                originX + right * scale, clipTop, clipBottom, depth,
                LostTalesChatOverlayRenderer.sideFadeStrength(slide * scale,
                        depth),
                LostTalesChatOverlayRenderer.sideFadeStrength(
                        (width - slide - room) * scale, depth),
                new LostTalesChatOverlayRenderer.FadingPainter() {
                    @Override
                    public void paint(float share) {
                        int sliceAlpha = Math.round(alpha * share);
                        if (sliceAlpha < LostTalesChatVisualStyle
                                .MIN_VISIBLE_ALPHA) {
                            return;
                        }
                        GL11.glPushMatrix();
                        try {
                            GL11.glTranslatef(-slide, 0.0F, 0.0F);
                            drawParts(minecraft, font, parts, x, y,
                                    sliceAlpha);
                        } finally {
                            GL11.glPopMatrix();
                        }
                    }
                });
    }

    private static void drawParts(Minecraft minecraft, FontRenderer font,
                                  List<Part> parts, int x, int y, int alpha) {
        int cursor = x;
        for (Part part : parts) {
            ChatInlineText.draw(minecraft, font, part.text, part.style, cursor,
                    y, part.rgb, alpha);
            cursor += ChatInlineText.width(font, part.text, part.style);
        }
    }

    private static void drawCutText(Minecraft minecraft, FontRenderer font,
                                    String text, int x, int y, int rgb,
                                    int alpha, float right, float slide,
                                    float originX, float scale, float clipTop,
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
                -slide, y, rgb, alpha, originX + x * scale,
                originX + right * scale, clipTop, clipBottom, depth,
                LostTalesChatOverlayRenderer.sideFadeStrength(slide * scale,
                        depth),
                LostTalesChatOverlayRenderer.sideFadeStrength(
                        (width - slide - room) * scale, depth));
    }
}
