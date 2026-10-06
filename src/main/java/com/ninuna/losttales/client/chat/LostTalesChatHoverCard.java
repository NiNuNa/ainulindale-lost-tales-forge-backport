package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.TabIcons;
import com.ninuna.losttales.client.window.WindowLists;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.client.window.WindowPage;
import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.ChatNames;
import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import com.ninuna.losttales.chat.ChatRoleplayStatus;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.character.ClientCharacterDisplayNames;
import com.ninuna.losttales.client.character.ClientCharacterProfileCache;
import com.ninuna.losttales.gui.screen.character.CharactersPage;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.util.LostTalesWords;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;
import java.util.Locale;

/**
 * The bounded player card: opened in a sub-window of its own by a
 * click on the head or name of a chat line, on a mention or on a member,
 * and shown in brief under the pointer for the identity the head button
 * speaks as. It is laid out as a menu is: the head beside the name and
 * the title on two menu rows, then a row for each detail, its label at
 * the left and its value at the row's right end in the aside tone, a
 * status line or a description wrapped as a menu's note, and a heading
 * over a group of rows. A chat line supplies its snapshotted identity;
 * the details — faction, race, gender, age and glances — come from the
 * public appearance and profile the server synced for that player, and
 * are only shown when they describe the character the line names. An
 * NPC's head and name carry the same card — the portrait, the name in
 * its faction's colour, and the faction its speech was captured with —
 * so an NPC reads as a player that happens not to exist.
 */
final class LostTalesChatHoverCard {
    /** What a status line and a command read in. */
    private static final String ITALIC = "§o";
    private static final int HEAD_SIZE = 16;
    /** The menu rows the head stands across: the name on the first, the title on the second. */
    static final int HEAD_ROWS = 2;
    static final int MIN_WIDTH = 118;
    static final int MAX_WIDTH = 210;
    /** Text width a note, a status line or a role's description, may push its card out to before it wraps. */
    private static final int NOTE_WIDTH = 170;
    /** The lines a note keeps at most. */
    private static final int MAX_NOTE_LINES = 4;
    /** A glance's place in the card's row of them: its emoji and a pixel either side. */
    static final int GLANCE_STRIDE = ChatEmojiIcon.SIZE + 2;
    /** Holders a role card lists before folding the rest into a count. */
    private static final int MAX_ROLE_MEMBER_LINES = 8;
    /** The label of the row naming the command a Server line answers. */
    private static final String COMMAND_LABEL_KEY =
            "gui.losttales.chat.card.command";

    private LostTalesChatHoverCard() {}

    /**
     * The brief card of who the player is on a tab: what its identity
     * button shows on hover, so who the roleplaying channels speak as, or
     * the character a page is about, is read the way anyone else in the
     * chat is.
     */
    static void drawForIdentity(Minecraft minecraft, WindowPage tab, int mouseX,
                                int mouseY, int screenWidth, int screenHeight) {
        if (minecraft == null || minecraft.thePlayer == null
                || minecraft.fontRenderer == null) {
            return;
        }
        ClientChatSignature.Signature signature = ClientChatSignature.of(tab);
        ChatPresenceIdentity speaker = ClientChatPresence.speakerOf(tab);
        Laid laid = layOut(minecraft, new Target(
                        minecraft.thePlayer.getUniqueID(),
                        signature.accountLine, speaker.getCharacterId(),
                        signature.skinId, signature.identityName, "",
                        signature.accountName, signature.nameColor),
                false, 0, Math.max(40, screenWidth - 8));
        int x = cardX(mouseX, laid.width, screenWidth);
        int y = cardY(mouseY, laid.height, screenHeight);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0.0F, 0.0F, 300.0F);
            WindowStyle.drawPopup(x, y, x + laid.width,
                    y + laid.height, 1.0F);
            drawLaid(minecraft, laid, x, y, 255);
        } finally {
            GL11.glPopMatrix();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
        }
    }

    /**
     * A card laid out and ready to draw: its name, the title beside the
     * head, the rows under them, and the size it takes.
     */
    static final class Laid {
        final Target target;
        String name = "";
        String suffix = "";
        int nameWidth;
        int nameColor;
        /** The title on the head's second row; empty for none. */
        String title = "";
        final List<Row> rows = new ArrayList<Row>(8);
        /** The role-play status whose mark stands after the name; null for none. */
        ChatRoleplayStatus roleplay;
        /** The person's glances, drawn as a row of their emoji; empty for none. */
        List<CharacterProfile.Glance> glances =
                Collections.<CharacterProfile.Glance>emptyList();
        /** The glances' row's top below the card's top; -1 for none. */
        int glancesTop = -1;
        boolean head = true;
        int width;
        int height;
        /** The rows' width: the card's, less the padding either side. */
        int textWidth;

        Laid(Target target) {
            this.target = target;
        }
    }

    /** What a row under a card's name is, and so how it is drawn. */
    private enum Kind {
        /** A label at the left and its value at the row's right end. */
        DETAIL,
        /** Words wrapped over a note's lines. */
        NOTE,
        /** A heading over the rows after it. */
        HEADING,
        /** Words alone: a role's holder. */
        ITEM,
        /** The person's glances, a row of their emoji. */
        GLANCES
    }

    /** One row of a card under its name, as a menu lays its rows. */
    private static final class Row {
        final Kind kind;
        /** A detail's label, a note's words before they wrap, or a heading's or an item's words. */
        final String text;
        /** A detail's value; empty for every other row. */
        final String value;
        /** What a note's words or a detail's value read in: italics, or empty. */
        final String style;
        /** Whether a note's words may hold emoji, as a status line's do. */
        final boolean inline;
        /** An item's colour; -1 for the aside tone. */
        final int rgb;
        /** A note's lines, wrapped to the card's width. */
        List<String> lines = Collections.<String>emptyList();
        /** The row's top below the card's top. */
        int top;

        private Row(Kind kind, String text, String value, String style,
                    boolean inline, int rgb) {
            this.kind = kind;
            this.text = text;
            this.value = value;
            this.style = style;
            this.inline = inline;
            this.rgb = rgb;
        }

        static Row detail(String label, String value, String style) {
            return new Row(Kind.DETAIL, label, value, style, false, -1);
        }

        static Row note(String text, String style, boolean inline) {
            return new Row(Kind.NOTE, text, "", style, inline, -1);
        }

        static Row heading(String text) {
            return new Row(Kind.HEADING, text, "", "", false, -1);
        }

        static Row item(String text, int rgb) {
            return new Row(Kind.ITEM, text, "", "", false, rgb);
        }

        static Row glances() {
            return new Row(Kind.GLANCES, "", "", "", false, -1);
        }

        /** How tall the row stands: a note its lines and the clear room round them, any other a menu's row. */
        int height() {
            return this.kind == Kind.NOTE ? MenuWindow.NOTE_PADDING * 2
                    + this.lines.size() * MenuWindow.NOTE_LINE
                    : MenuWindow.rowHeight();
        }

        /** The width the row asks of the card between its padding; a note no more than the note width. */
        int wants(FontRenderer font, Laid laid) {
            if (this.kind == Kind.DETAIL) {
                return font.getStringWidth(this.text) + MenuWindow.VALUE_GAP
                        + font.getStringWidth(this.value);
            }
            if (this.kind == Kind.NOTE) {
                return Math.min(NOTE_WIDTH, this.inline
                        ? ChatInlineText.width(font, this.text, this.style)
                        : font.getStringWidth(this.text));
            }
            if (this.kind == Kind.GLANCES) {
                return laid.glances.size() * GLANCE_STRIDE;
            }
            return font.getStringWidth(this.text);
        }
    }

    /**
     * Lays the card out: the name and the title beside the head, then a
     * row for each detail. The name reads {@code Character (Account)} for
     * a character identity and just {@code Account} otherwise; a detail is
     * left out rather than shown empty when its value is unknown. The
     * brief card shows the status line, the command a Server line
     * answers, the server's own status, the roles held, the status and an
     * NPC's faction; the {@code full} card goes on to the character's
     * faction, race, gender, age and glances. {@code width} fixes the
     * card's width, a window's; at 0 the card takes the width its rows
     * want, up to {@code maxWidth}.
     */
    static Laid layOut(Minecraft minecraft, Target target, boolean full,
                       int width, int maxWidth) {
        if (target.role != null) {
            return layOutRole(minecraft.fontRenderer, target, full, width,
                    maxWidth);
        }
        FontRenderer font = minecraft.fontRenderer;
        Laid laid = new Laid(target);
        CharacterAppearance details = detailsFor(target);
        // The Server, the Client and the Narrator are named in this
        // client's language.
        String name = ChatNames.sender(LostTalesWords.LANG, target.playerId,
                target.skinId,
                LostTalesChatVisualStyle.removeColorCodes(target.identityName).trim());
        String account = target.accountName.trim();
        // Never "Name ()" or "(Account)": the suffix only exists when both
        // halves do, and an account identity shows the account alone.
        String suffix = !target.accountIdentity && name.length() > 0
                && account.length() > 0 && !name.equalsIgnoreCase(account)
                ? " (" + account + ")" : "";
        if (name.length() == 0) {
            name = account;
            suffix = "";
        }
        laid.name = name;
        laid.suffix = suffix;
        laid.nameColor = target.nameColor;
        laid.title = cleanBracketed(target.title);
        List<Row> rows = laid.rows;
        // What the identity says of itself, as it says it, under its name.
        String statusLine = ClientChatProfanity.filter(target.statusLine())
                .trim();
        if (statusLine.length() > 0) {
            rows.add(Row.note(statusLine, ITALIC, true));
        }
        // The Server's card says what it is answering: the command the
        // line under the pointer was the answer to, as inline code.
        addDetail(rows, COMMAND_LABEL_KEY, target.note, ITALIC);
        // And how the server stands, each part of its status a row: its
        // players, its address, its speed, how long it has run, the mod it
        // runs, and its conversations linked to Discord.
        if (LostTalesChatMessagePacket.isServerSender(target.playerId)) {
            addDetail(rows, "gui.losttales.chat.card.server.players",
                    ClientServerStatus.count());
            addDetail(rows, "gui.losttales.chat.card.server.address",
                    ClientServerStatus.address());
            addDetail(rows, "gui.losttales.chat.card.server.speed",
                    ClientServerStatus.speed());
            addDetail(rows, "gui.losttales.chat.card.server.up",
                    ClientServerStatus.uptime());
            addDetail(rows, "gui.losttales.chat.card.server.version",
                    LostTalesMetaData.MOD_VERSION);
            addDetail(rows, "gui.losttales.chat.card.server.discord",
                    joined(ClientChatChannelState.discordLinkedNames()));
        }
        // The roles the identity wears, whichever channel the line was
        // said in: an account its own, a character the account's and its
        // own. A role not worn on an in-character line is still held, and
        // the card is where it shows.
        int worn = target.accountIdentity || details == null
                ? ChatMentionColors.rolesFor(account)
                : ChatMentionColors.rolesFor(account,
                        details.getCharacterId());
        addDetail(rows, "gui.losttales.chat.card.roles", target.npcIdentity
                ? "" : roleNames(worn));
        // Away, Do Not Disturb or Offline, for the identity the card
        // is about; Online is no news.
        addDetail(rows, "gui.losttales.chat.card.status",
                ChatPresenceMark.label(target.presence()));
        // An NPC's faction is what its speech was captured with, and the
        // brief card says it too: without it the card is a name alone.
        if (full || target.npcIdentity) {
            addDetail(rows, "gui.losttales.chat.card.faction",
                    target.npcIdentity
                            ? ChatChannelIcons.npcFaction(target.playerId)
                            : details == null ? ""
                            : ClientCharacterDisplayNames.faction(
                                    details.getFactionId()));
        }
        if (full) {
            addDetail(rows, "gui.losttales.character.race",
                    details == null ? "" : ClientCharacterDisplayNames.race(
                            details.getRaceId()));
            addDetail(rows, "gui.losttales.character.gender",
                    details == null || details.getGenderId().length() == 0
                            ? "" : ClientCharacterDisplayNames.gender(
                                    details.getGenderId()));
            addDetail(rows, "gui.losttales.character.age",
                    details == null || details.getAge() <= 0
                            ? "" : String.valueOf(details.getAge()));
            // The glances the character shows: asked for as the
            // card shows them, a row of their emoji once they come.
            if (target.hasProfile()) {
                ClientCharacterProfileCache.want(target.characterId);
                CharacterProfile profile = ClientCharacterProfileCache.get(
                        target.characterId);
                if (profile != null && !profile.glances().isEmpty()) {
                    laid.glances = profile.glances();
                    rows.add(Row.glances());
                }
            }
        }
        laid.roleplay = target.presence() == null ? null
                : ChatRoleplayMark.markedFor(target.playerId,
                        target.accountIdentity ? ChatPresenceIdentity.ACCOUNT
                                : ChatPresenceIdentity.character(
                                        target.characterId));
        return finish(font, laid, width, maxWidth);
    }

    /**
     * The card of a mentioned role: {@code @Name} in the role's colour,
     * what the role is, and — on the {@code full} card — the online
     * accounts holding it under a heading, the server's own roster, sent
     * with the chat access, so the list is its word and not a guess from
     * who happened to speak.
     */
    private static Laid layOutRole(FontRenderer font, Target target,
                                   boolean full, int width, int maxWidth) {
        ChatAccountRole role = target.role;
        Laid laid = new Laid(target);
        laid.head = false;
        laid.name = "@" + role.getDisplayName();
        laid.nameColor = role.getColor();
        List<Row> rows = laid.rows;
        String description = role.getDisplayDescription();
        if (description.trim().length() > 0) {
            rows.add(Row.note(description, "", false));
        }
        if (full) {
            rows.add(Row.heading(StatCollector.translateToLocal(
                    "gui.losttales.chat.card.role.members")));
            List<String> members = ClientChatChannelState.roleHolders(role);
            if (members.isEmpty()) {
                rows.add(Row.item(StatCollector.translateToLocal(
                        "gui.losttales.chat.card.role.nobody"), -1));
            } else {
                // The holders read as the people they are, in the role's
                // colour; the count of the rest in the aside tone.
                int shown = Math.min(members.size(), MAX_ROLE_MEMBER_LINES);
                for (int index = 0; index < shown; index++) {
                    rows.add(Row.item(members.get(index), role.getColor()));
                }
                if (members.size() > shown) {
                    rows.add(Row.item(StatCollector.translateToLocalFormatted(
                            "gui.losttales.chat.card.role.more",
                            Integer.valueOf(members.size() - shown)), -1));
                }
            }
        }
        return finish(font, laid, width, maxWidth);
    }

    /**
     * Sizes a card whose name and rows are in: its width, between
     * {@link #MIN_WIDTH} and {@link #MAX_WIDTH} unless {@code width} fixes
     * it; the name cut to the room beside the head, the account suffix
     * giving way first; the notes wrapped to the rows' width; each row's
     * place under the name; and the height they take, the padding above
     * and below them.
     */
    private static Laid finish(FontRenderer font, Laid laid, int width,
                               int maxWidth) {
        int lead = headLead(laid);
        int markWidth = markWidth(laid);
        if (width > 0) {
            laid.width = width;
        } else {
            int contentWidth = lead + Math.max(
                    font.getStringWidth(laid.name + laid.suffix) + markWidth,
                    font.getStringWidth(laid.title));
            for (Row row : laid.rows) {
                contentWidth = Math.max(contentWidth, row.wants(font, laid));
            }
            laid.width = Math.min(Math.max(MIN_WIDTH, Math.min(MAX_WIDTH,
                    MenuWindow.PADDING_X * 2 + contentWidth)), maxWidth);
        }
        laid.textWidth = Math.max(0, laid.width - MenuWindow.PADDING_X * 2);
        int room = Math.max(0, laid.textWidth - lead);
        String name = laid.name;
        String suffix = laid.suffix;
        int nameWidth = font.getStringWidth(name);
        if (nameWidth + markWidth + font.getStringWidth(suffix) > room) {
            // The account suffix gives way before the name does.
            suffix = LostTalesSkyrimUiStyle.trimToWidth(font, suffix,
                    Math.max(0, room - nameWidth - markWidth));
            name = LostTalesSkyrimUiStyle.trimToWidth(font, name,
                    Math.max(0, room - markWidth));
            nameWidth = font.getStringWidth(name);
        }
        laid.name = name;
        laid.suffix = suffix;
        laid.nameWidth = nameWidth;
        int top = MenuWindow.PADDING_Y
                + (laid.head ? HEAD_ROWS : 1) * MenuWindow.rowHeight();
        for (Row row : laid.rows) {
            if (row.kind == Kind.NOTE) {
                row.lines = row.inline
                        ? wrapInline(font, row.text, row.style, laid.textWidth)
                        : wrapPlain(font, row.text, laid.textWidth);
            } else if (row.kind == Kind.GLANCES) {
                laid.glancesTop = top;
            }
            row.top = top;
            top += row.height();
        }
        laid.height = top + MenuWindow.PADDING_Y;
        return laid;
    }

    /** Words over the lines they take {@code room} wide, {@link #MAX_NOTE_LINES} at most. */
    private static List<String> wrapPlain(FontRenderer font, String text,
                                          int room) {
        @SuppressWarnings("unchecked")
        List<String> wrapped = font.listFormattedStringToWidth(text,
                Math.max(20, room));
        List<String> lines = new ArrayList<String>(MAX_NOTE_LINES);
        for (int index = 0; index < wrapped.size()
                && index < MAX_NOTE_LINES; index++) {
            lines.add(wrapped.get(index).trim());
        }
        return lines;
    }

    /**
     * Words that may hold emoji over the lines they take {@code room}
     * wide, broken between words, {@link #MAX_NOTE_LINES} at most; a word
     * wider than a line is cut at the line's end.
     */
    private static List<String> wrapInline(FontRenderer font, String text,
                                           String style, int room) {
        int width = Math.max(20, room);
        List<String> lines = new ArrayList<String>(MAX_NOTE_LINES);
        String line = "";
        for (String word : text.trim().split(" +")) {
            String joined = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0
                    && ChatInlineText.width(font, joined, style) > width) {
                lines.add(ChatInlineText.trimToWidth(font, line, style, width));
                if (lines.size() == MAX_NOTE_LINES) {
                    return lines;
                }
                joined = word;
            }
            line = joined;
        }
        if (line.length() > 0) {
            lines.add(ChatInlineText.trimToWidth(font, line, style, width));
        }
        return lines;
    }

    /**
     * Draws a laid-out card with its top left at {@code x}, {@code y}, on
     * whatever surface stands under it: the head on the middle of its two
     * rows, the name on the first beside it and the title on the second,
     * and each row under them.
     */
    static void drawLaid(Minecraft minecraft, Laid laid, int x, int y,
                         int alpha) {
        FontRenderer font = minecraft.fontRenderer;
        int rowHeight = MenuWindow.rowHeight();
        int left = x + MenuWindow.PADDING_X;
        int right = left + laid.textWidth;
        int top = y + MenuWindow.PADDING_Y;
        int textX = left + headLead(laid);
        if (laid.head) {
            drawHead(minecraft, laid.target, left, top
                    + LostTalesUiInk.centredStart(HEAD_ROWS * rowHeight,
                            HEAD_SIZE));
        }
        LostTalesUiInk.beginContent();
        int nameTop = capTop(top);
        drawColored(font, laid.name, textX, nameTop, laid.nameColor, alpha);
        if (laid.roleplay != null) {
            ChatRoleplayMark.draw(laid.roleplay, textX + laid.nameWidth
                    + ChatRoleplayMark.GAP - 1, nameTop + 1, alpha);
        }
        if (laid.suffix.length() > 0) {
            drawColored(font, laid.suffix, textX + laid.nameWidth
                    + markWidth(laid), nameTop, WindowStyle.asideRgb(),
                    alpha);
        }
        if (laid.title.length() > 0) {
            drawColored(font, LostTalesSkyrimUiStyle.trimToWidth(font,
                    laid.title, right - textX), textX, capTop(top + rowHeight),
                    WindowStyle.asideRgb(), alpha);
        }
        for (Row row : laid.rows) {
            drawRow(minecraft, font, laid, row, left, right, y + row.top,
                    alpha);
        }
    }

    /**
     * One row under the name, from {@code left} to {@code right} with its
     * top at {@code rowY}: a detail's label in ivory and its value at the
     * row's right end in the aside tone, the label cut to keep the gap
     * before it; a note's lines a note's pitch apart in the aside tone; a
     * heading over its hairline; an item in its colour; the glances'
     * emoji, a pixel either side of each.
     */
    private static void drawRow(Minecraft minecraft, FontRenderer font,
                                Laid laid, Row row, int left, int right,
                                int rowY, int alpha) {
        int aside = WindowStyle.asideRgb();
        if (row.kind == Kind.HEADING) {
            WindowLists.drawHeading(font, row.text, left, left, right, rowY,
                    MenuWindow.rowHeight(), false, alpha);
            return;
        }
        if (row.kind == Kind.NOTE) {
            int lineY = rowY + MenuWindow.NOTE_PADDING;
            for (String line : row.lines) {
                int lineTop = lineY + LostTalesUiInk.centredStart(
                        MenuWindow.NOTE_LINE, LostTalesUiInk.CAP_HEIGHT);
                if (row.inline) {
                    // What the identity says of itself, as it says it:
                    // its words and their emojis.
                    ChatInlineText.draw(minecraft, font, line, row.style,
                            left, lineTop, aside, alpha);
                } else {
                    drawColored(font, row.style + line, left, lineTop, aside,
                            alpha);
                }
                lineY += MenuWindow.NOTE_LINE;
            }
            return;
        }
        if (row.kind == Kind.GLANCES) {
            int glanceTop = rowY + LostTalesUiInk.centredStart(
                    MenuWindow.rowHeight(), ChatEmojiIcon.SIZE);
            for (int glance = 0; glance < laid.glances.size(); glance++) {
                ChatEmojiIcon.draw(minecraft, ChatEmoji.fromName(
                        laid.glances.get(glance).getEmoji()),
                        left + glance * GLANCE_STRIDE + 1, glanceTop, alpha);
            }
            return;
        }
        int labelTop = capTop(rowY);
        if (row.kind == Kind.ITEM) {
            drawColored(font, LostTalesSkyrimUiStyle.trimToWidth(font,
                    row.text, right - left), left, labelTop,
                    row.rgb >= 0 ? row.rgb : aside, alpha);
            return;
        }
        String value = LostTalesSkyrimUiStyle.trimToWidth(font, row.value,
                valueRoom(font, row, right - left));
        int valueX = right - font.getStringWidth(value);
        drawColored(font, row.style + value, valueX, labelTop, aside, alpha);
        drawColored(font, LostTalesSkyrimUiStyle.trimToWidth(font, row.text,
                valueX - MenuWindow.VALUE_GAP - left), left, labelTop,
                LostTalesUiInk.IVORY, alpha);
    }

    /**
     * How wide a detail's value may stand in a row {@code room} wide: the
     * room its label and the gap leave, and never less than half the row,
     * so a long label gives way to the value down to that half.
     */
    private static int valueRoom(FontRenderer font, Row row, int room) {
        return Math.max(room / 2, room - MenuWindow.VALUE_GAP
                - font.getStringWidth(row.text));
    }

    /** Where a menu row's words stand from its top: their capitals on its middle. */
    private static int capTop(int rowY) {
        return rowY + LostTalesUiInk.centredStart(MenuWindow.rowHeight(),
                LostTalesUiInk.CAP_HEIGHT);
    }

    /** The room before the name and the title: the head and the gap after it; none on a card without one. */
    private static int headLead(Laid laid) {
        return laid.head ? HEAD_SIZE + TabIcons.GAP : 0;
    }

    /**
     * The roles in a mask as the card lists them: every one, highest
     * display priority first, separated by commas; empty for none.
     */
    static String roleNames(int mask) {
        StringBuilder names = new StringBuilder();
        for (ChatAccountRole role : ChatAccountRole.fromMask(mask)) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(role.getDisplayName());
        }
        return names.toString();
    }

    /** Names one after another, a comma between each two; empty for none. */
    private static String joined(List<String> names) {
        StringBuilder text = new StringBuilder();
        for (String name : names) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(name);
        }
        return text.toString();
    }

    /** Adds a detail for a known value, its label from {@code labelKey}. */
    private static void addDetail(List<Row> rows, String labelKey,
                                  String value) {
        addDetail(rows, labelKey, value, "");
    }

    /** As above, the value read in {@code style}: italics for a command, as the chat shows one. */
    private static void addDetail(List<Row> rows, String labelKey,
                                  String value, String style) {
        String text = value == null ? "" : value.trim();
        if (text.length() > 0) {
            rows.add(Row.detail(StatCollector.translateToLocal(labelKey),
                    text, style));
        }
    }

    /** The room the role-play mark takes after the name: the mark and a gap either side; none without one. */
    private static int markWidth(Laid laid) {
        return laid.roleplay == null ? 0
                : ChatRoleplayMark.GAP + ChatRoleplayMark.SIZE
                        + ChatRoleplayMark.GAP - 1;
    }

    /**
     * The glance under a point on a card drawn with its top left at
     * {@code cardX}, {@code cardY}: the glances' row, a glance's place
     * along it from the rows' left; -1 off them.
     */
    static int glanceAt(Laid laid, int cardX, int cardY, double x,
                        double y) {
        if (laid.glancesTop < 0 || Double.isNaN(x) || Double.isNaN(y)) {
            return -1;
        }
        int left = cardX + MenuWindow.PADDING_X;
        int rowTop = cardY + laid.glancesTop;
        if (y < rowTop || y >= rowTop + MenuWindow.rowHeight() || x < left) {
            return -1;
        }
        int index = (int)Math.floor((x - left) / GLANCE_STRIDE);
        return index < laid.glances.size() ? index : -1;
    }

    /**
     * What the pointer rests on: the person or role, whether it is the
     * row's sender's name — not a mention, and not the sender's head,
     * which answers for the same person as an element of its own — and
     * the drawn row itself.
     */
    static final class Found {
        final Target target;
        /** Whether the name's underline follows the pointer. */
        final boolean sender;
        final IChatComponent row;

        Found(Target target, boolean sender, IChatComponent row) {
            this.target = target;
            this.sender = sender;
            this.row = row;
        }
    }

    private static Found found(Target target, boolean sender,
                               IChatComponent row) {
        return target == null ? null : new Found(target, sender, row);
    }

    /**
     * The person the hit stands on, or null: a mention answers with
     * whoever it reaches; the sender's identity span — the opening
     * bracket, the head with its clear pixels, the name, the title,
     * the spacers and the closing bracket's own glyphs, never the gap
     * after them — answers with the row's sender. The hit is the one
     * the screen took for the frame, so the card, the underline and
     * the hand cursor all read the same run; the row is walked again
     * only to know whether that run stands inside the span, and every
     * width it needs the hit already carries.
     */
    static Found locate(Minecraft minecraft,
                        LostTalesChatOverlayRenderer.Hit hit) {
        if (minecraft == null || minecraft.fontRenderer == null
                || hit == null || hit.band == null
                || hit.band.lines == null) {
            return null;
        }
        try {
            List<ChatLine> lines = hit.band.lines;
            int viewIndex = hit.band.viewIndex;
            IChatComponent row = hit.line;
            // The row's drawn runs in order, with their places on the
            // row: the walk below looks one run ahead for an NPC's
            // head, whose bracket carries no whisper and belongs to the
            // span all the same.
            List<IChatComponent> parts = new ArrayList<IChatComponent>();
            List<Integer> places = new ArrayList<Integer>();
            int index = -1;
            for (Object value : row) {
                if (!(value instanceof IChatComponent)) {
                    continue;
                }
                index++;
                IChatComponent part = (IChatComponent)value;
                if (!ChatPrefixMarker.isHidden(part, true)) {
                    parts.add(part);
                    places.add(Integer.valueOf(index));
                }
            }
            boolean identitySpan = false;
            for (int at = 0; at < parts.size(); at++) {
                IChatComponent part = parts.get(at);
                boolean atHit = places.get(at).intValue() == hit.index;
                if (ChatStampMarker.isMarker(part)) {
                    // The time behind a name is past the sender; it
                    // reads out its moment instead.
                    if (atHit) {
                        return null;
                    }
                    identitySpan = false;
                    continue;
                }
                if (ChatLayoutMarker.isSpanEnd(part)) {
                    // An action's words follow its speaker's name.
                    identitySpan = false;
                    continue;
                }
                ChatMentionMarker.Data mention =
                        ChatMentionMarker.decode(part);
                if (mention != null) {
                    if (atHit) {
                        ChatAccountRole role = mention.role();
                        return found(role != null ? Target.forRole(role)
                                : targetForMention(minecraft, mention),
                                false, row);
                    }
                    continue;
                }
                ChatHeadMarker.Data head = ChatHeadMarker.decode(part);
                String text = head != null ? ""
                        : LostTalesChatVisualStyle.removeColorCodes(
                                part.getUnformattedTextForChat()).trim();
                boolean inSpan = identitySpan;
                if (ChatSenderSpan.isSenderName(part) && !identitySpan) {
                    // A player's opening bracket carries the reply
                    // identity and starts the span.
                    identitySpan = true;
                    inSpan = true;
                }
                if (head != null) {
                    // An action's head is worn in the Narrator's voice and
                    // opens no card; the speaker's name in its words does.
                    inSpan = !head.voiced;
                    if (head.npcIdentity) {
                        identitySpan = true;
                    }
                } else if (!inSpan && text.startsWith("<")
                        && at + 1 < parts.size()) {
                    // An NPC's brackets carry no reply identity —
                    // nobody is on the other end of a /msg — so its
                    // span opens at its head and reaches back over the
                    // bracket standing just before it.
                    ChatHeadMarker.Data next =
                            ChatHeadMarker.decode(parts.get(at + 1));
                    if (next != null && next.npcIdentity) {
                        identitySpan = true;
                        inSpan = true;
                    }
                }
                boolean closesSpan = identitySpan && head == null
                        && text.startsWith(">");
                if (atHit) {
                    if (!inSpan) {
                        return null;
                    }
                    if (ChatSpacerMarker.isMarker(part)
                            && !spanGoesOnAfter(parts, at)) {
                        // A gap the span ends on — the space before the
                        // time behind a name — is not the name, just as
                        // its underline stops short of it.
                        return null;
                    }
                    if (closesSpan) {
                        // The span's last run is the closing bracket,
                        // whose trailing space belongs to the gap before
                        // the message, not to the name: the span ends on
                        // the bracket's own glyphs, so the hitbox
                        // matches what is drawn.
                        String raw = part.getUnformattedTextForChat();
                        int trimmed = raw.length();
                        while (trimmed > 0 && raw.charAt(trimmed - 1) == ' ') {
                            trimmed--;
                        }
                        int spanWidth = hit.partWidth
                                - minecraft.fontRenderer.getCharWidth(' ')
                                        * (raw.length() - trimmed);
                        if (hit.localX() >= hit.partLeft + spanWidth) {
                            return null;
                        }
                    }
                    // The head answers for the sender too, but it is
                    // an element of its own: the name is not underlined
                    // under it.
                    return found(targetForGroup(lines, viewIndex),
                            head == null, row);
                }
                if (closesSpan) {
                    identitySpan = false;
                }
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    /**
     * Whether the sender's span goes on past the gap at {@code at}: the
     * next run that draws anything is still the sender — a title, a
     * bracket — rather than the time behind the name or the row's end.
     */
    static boolean spanGoesOnAfter(List<IChatComponent> parts, int at) {
        for (int next = at + 1; next < parts.size(); next++) {
            IChatComponent part = parts.get(next);
            if (ChatSpacerMarker.isMarker(part)
                    || ChatLayoutMarker.decode(part) != null) {
                continue;
            }
            return !ChatStampMarker.isMarker(part)
                    && !ChatReactionMarker.isAddButton(part);
        }
        return false;
    }

    /**
     * Card target for a mentioned player: the player the line recorded,
     * as they are now while this client knows them, and otherwise as the
     * line recorded them — the identity they were playing and its head —
     * so a mention still opens its card after the player has gone. A
     * mention that recorded nobody is placed by its name. Null when
     * neither names anybody.
     */
    static Target targetForMention(Minecraft minecraft,
                                   ChatMentionMarker.Data mention) {
        UUID recordedId = mention.recorded == null ? null
                : mention.recorded.getPlayerId();
        if (recordedId == null) {
            return targetForAccount(minecraft, mention.account);
        }
        if (ClientCharacterAppearanceCache.getAuthoritative(recordedId) != null) {
            return placedTarget(minecraft, recordedId, mention.account);
        }
        return recordedTarget(minecraft, mention.recorded);
    }

    /**
     * The card of a member of a conversation's list, as the list shows
     * them; an NPC's as its lines open it.
     */
    static Target forMember(Minecraft minecraft,
                            com.ninuna.losttales.network.packet
                                    .LostTalesChatMembersPacket.Member member) {
        if (member.isNpc()) {
            return new Target(member.getPlayerId(), false, true, null,
                    member.getSkinId(), member.getName(), "", "",
                    member.getNameColor());
        }
        boolean accountIdentity = member.getCharacterId() == null;
        // A Discord member has no Minecraft account to fetch a skin for.
        if (accountIdentity && minecraft != null
                && !LostTalesChatMessagePacket.isDiscordSender(member.getPlayerId())) {
            LostTalesCharacterHeadIconRenderer.rememberAccountSkin(
                    minecraft, member.getPlayerId(), member.getAccount());
        }
        return new Target(member.getPlayerId(), accountIdentity,
                member.getCharacterId(), member.getSkinId(), member.getName(),
                ChatMemberList.epithetOf(member), member.getAccount(),
                member.getNameColor());
    }

    /** The card of a player as a line recorded them. */
    static Target recordedTarget(Minecraft minecraft,
                                 ChatNamedPlayer recorded) {
        boolean accountIdentity = recorded.getCharacterId() == null;
        // A Discord member has no Minecraft account to fetch a skin for.
        if (accountIdentity && minecraft != null
                && !LostTalesChatMessagePacket.isDiscordSender(recorded.getPlayerId())) {
            LostTalesCharacterHeadIconRenderer.rememberAccountSkin(
                    minecraft, recorded.getPlayerId(), recorded.getAccount());
        }
        return new Target(recorded.getPlayerId(), accountIdentity,
                recorded.getCharacterId(), recorded.getSkinId(),
                recorded.getIdentityName(), "", recorded.getAccount(),
                LostTalesColors.rgb(LostTalesColors.HUD_LABEL));
    }

    /**
     * Card target for a mentioned account: the character the appearance
     * sync knows for it, or the bare account; null when this client
     * cannot place the name at all.
     */
    private static Target targetForAccount(Minecraft minecraft,
                                           String account) {
        UUID playerId = ChatChannelIcons.partnerId(minecraft, account);
        return playerId == null ? null
                : placedTarget(minecraft, playerId, account);
    }

    /**
     * Card target for the player {@code playerId} under {@code account}
     * as this client knows them now: the character the appearance sync
     * knows for them, or the bare account.
     */
    private static Target placedTarget(Minecraft minecraft, UUID playerId,
                                       String account) {
        CharacterAppearance appearance =
                ClientCharacterAppearanceCache.getAuthoritative(playerId);
        boolean accountIdentity = appearance == null
                || !appearance.hasCharacter()
                || appearance.getCharacterName().length() == 0;
        if (accountIdentity) {
            LostTalesCharacterHeadIconRenderer.rememberAccountSkin(
                    minecraft, playerId, account);
        }
        return new Target(playerId, accountIdentity,
                accountIdentity ? null : appearance.getCharacterId(),
                appearance == null ? "" : appearance.getSkinId(),
                accountIdentity ? account
                        : appearance.getCharacterName(),
                "", account,
                LostTalesColors.rgb(LostTalesColors.HUD_LABEL));
    }

    private static Target targetForGroup(List<ChatLine> lines,
                                         int lineIndex) {
        ChatLine selected = lines.get(lineIndex);
        if (selected == null) {
            return null;
        }
        ChatHeadMarker.Data marker = null;
        String identity = "";
        String title = "";
        String account = "";
        boolean afterHead = false;
        // GuiNewChat inserts wrapped lines at index zero. Walk this group in
        // reverse list order to reconstruct the original component sequence.
        for (int candidateIndex = lines.size() - 1;
             candidateIndex >= 0; candidateIndex--) {
            ChatLine candidate = lines.get(candidateIndex);
            if (candidate == null
                    || candidate.getUpdatedCounter()
                    != selected.getUpdatedCounter()
                    || candidate.getChatLineID()
                    != selected.getChatLineID()) {
                continue;
            }
            for (Object value : candidate.func_151461_a()) {
                if (!(value instanceof IChatComponent)) {
                    continue;
                }
                IChatComponent part = (IChatComponent)value;
                ChatHeadMarker.Data decoded = ChatHeadMarker.decode(part);
                if (decoded != null) {
                    marker = decoded;
                    afterHead = true;
                    continue;
                }
                // An NPC's name is the first bare text after its head:
                // the brackets around it are told apart by their text,
                // since nothing in the line answers to a /msg.
                if (marker != null && marker.npcIdentity && afterHead
                        && identity.length() == 0) {
                    String text = LostTalesChatVisualStyle.removeColorCodes(
                            part.getUnformattedTextForChat()).trim();
                    if (text.length() > 0 && !text.startsWith("<")
                            && !text.startsWith(">")) {
                        identity = text;
                    }
                    continue;
                }
                if (ChatSenderSpan.isSenderName(part)) {
                    // The brackets answer to the pointer as the name
                    // does, but they are not it. The name is the one
                    // that follows the head: the opening bracket comes
                    // before it, the closing one after it is already
                    // read, so position tells them apart without
                    // anything having to look at the text.
                    if (afterHead && identity.length() == 0) {
                        identity = LostTalesChatVisualStyle.removeColorCodes(
                                part.getUnformattedTextForChat()).trim();
                        account = ChatSenderSpan.accountOf(part);
                    }
                    continue;
                }
                ChatTitleMarker.Data titleData = ChatTitleMarker.decode(part);
                if (titleData != null && afterHead) {
                    title = titleData.epithet.trim();
                }
            }
        }
        if (marker == null || identity.length() == 0) {
            return null;
        }
        if (marker.npcIdentity) {
            // A fake player's card: the portrait, the name in the
            // faction's colour, no account behind it.
            return new Target(marker.senderId, false, true, null,
                    marker.skinId, identity, "", "", marker.nameColor);
        }
        if (account.length() == 0) {
            return null;
        }
        Target target = new Target(marker.senderId, marker.accountIdentity,
                marker.characterId, marker.skinId, identity, title, account,
                marker.nameColor);
        if (marker.isSystemSender()) {
            target = target.withNote(LostTalesChatPresentation
                    .commandAnsweredOn(selected.getChatLineID()));
        }
        return target;
    }

    /**
     * The live public character details behind a card, or null when the
     * card describes no character: an NPC, an account identity — whose
     * card is the account, never whatever character its player happens
     * to be playing — or a character other than the one the line names,
     * since a historic chat line must not borrow the sender's newer
     * identity after a switch.
     */
    private static CharacterAppearance detailsFor(Target target) {
        if (target.npcIdentity || target.accountIdentity) {
            // Nothing in the appearance sync describes an NPC, and an
            // account line names no character at all.
            return null;
        }
        CharacterAppearance appearance =
                ClientCharacterAppearanceCache.getAuthoritative(
                        target.playerId);
        if (appearance == null || !appearance.hasCharacter()) {
            return null;
        }
        if (!LostTalesChatVisualStyle.removeColorCodes(
                target.identityName).trim().equals(
                appearance.getCharacterName())) {
            return null;
        }
        return appearance;
    }

    private static void drawHead(Minecraft minecraft, Target target,
                                 float x, float y) {
        if (target.accountIdentity
                && (LostTalesChatMessagePacket.isDiscordSender(
                        target.playerId)
                        || LostTalesChatMessagePacket.isSystemSender(
                                target.playerId))) {
            // A Discord sender, the server or the client has no account
            // head; its mark stands in, 1:1 — never scaled — centred in
            // the head's box.
            float inset = (HEAD_SIZE - ChatEmoji.SPRITE_SIZE) / 2.0F;
            ChatEmojiRenderer.draw(minecraft,
                    LostTalesChatMessagePacket.isSystemSender(target.playerId)
                            ? ChatEmoji.CONSOLE : ChatEmoji.DISCORD,
                    x + inset, y + inset, ChatEmoji.SPRITE_SIZE, 255);
        } else if (target.npcIdentity) {
            LostTalesCharacterHeadIconRenderer.drawNpcHead(minecraft,
                    target.skinId, x, y, HEAD_SIZE, 1.0F, 1.0F);
        } else if (target.accountIdentity) {
            LostTalesCharacterHeadIconRenderer.drawAccountHead(
                    minecraft, target.playerId, x, y,
                    HEAD_SIZE, 1.0F, 1.0F);
        } else {
            LostTalesCharacterHeadIconRenderer.drawSnapshotHead(
                    minecraft, target.playerId, target.skinId,
                    x, y, HEAD_SIZE, 1.0F, 1.0F);
        }
    }

    private static void drawColored(FontRenderer font, String text,
                                    int x, int y, int color, int alpha) {
        LostTalesUiInk.drawText(font,
                LostTalesChatVisualStyle.removeColorCodes(text),
                x, y, color, alpha);
    }

    /**
     * A tooltip of plain lines, laid out and drawn as every other tip is
     * ({@link WindowStyle#drawPopupLines}): the popup's surface and inset,
     * its lines a line apart, the chat's shadow, and vanilla's colour
     * codes in the palette's own tones. What the achievement and text hover cards
     * and the shared-marker tooltip draw with, in place of vanilla's
     * hovering text, so nothing hanging off a chat line reads in vanilla's
     * colours beside the chat's.
     */
    static void drawTextCard(Minecraft minecraft, List<String> lines,
                             int mouseX, int mouseY,
                             int screenWidth, int screenHeight) {
        if (minecraft == null || minecraft.fontRenderer == null
                || lines == null || lines.isEmpty()) {
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        int contentWidth = 0;
        for (int index = 0; index < lines.size(); index++) {
            contentWidth = Math.max(contentWidth,
                    font.getStringWidth(lines.get(index) == null ? "" : lines.get(index)));
        }
        int width = Math.min(contentWidth + WindowStyle.POPUP_INSET * 2,
                Math.max(40, screenWidth - 8));
        int height = WindowStyle.popupLinesHeight(lines.size());
        int x = cardX(mouseX, width, screenWidth);
        int y = cardY(mouseY, height, screenHeight);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0.0F, 0.0F, 300.0F);
            WindowStyle.drawPopup(x, y, x + width, y + height,
                    1.0F);
            int textY = y + WindowStyle.POPUP_INSET;
            for (int index = 0; index < lines.size(); index++) {
                LostTalesChatVisualStyle.drawLegacyFormatted(font,
                        lines.get(index) == null ? "" : lines.get(index),
                        x + WindowStyle.POPUP_INSET, textY, 255);
                textY += WindowStyle.LINE_HEIGHT;
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
        }
    }

    static int cardX(int mouseX, int width, int screenWidth) {
        int candidate = mouseX + 12;
        if (candidate + width > screenWidth - 4) {
            candidate = mouseX - width - 12;
        }
        return Math.max(4, Math.min(candidate,
                Math.max(4, screenWidth - width - 4)));
    }

    static int cardY(int mouseY, int height, int screenHeight) {
        int candidate = mouseY + 8;
        if (candidate + height > screenHeight - 4) {
            candidate = mouseY - height - 8;
        }
        return Math.max(4, Math.min(candidate,
                Math.max(4, screenHeight - height - 4)));
    }

    private static String cleanBracketed(String value) {
        String result = value == null ? "" : value.trim();
        if (result.startsWith("[") && result.endsWith("]")
                && result.length() > 1) {
            result = result.substring(1, result.length() - 1).trim();
        }
        return result;
    }

    static final class Target {
        final UUID playerId;
        final boolean accountIdentity;
        /**
         * The character the card is about; null for the account, an NPC,
         * and a card that does not know which character.
         */
        final UUID characterId;
        /** An NPC's card: the portrait for a head, no account, and the
         *  faction its speech was captured with — a player's card in
         *  everything but the data behind it. */
        final boolean npcIdentity;
        /** The skin snapshot id, or the portrait path for an NPC. */
        final String skinId;
        final String identityName;

        /**
         * Whether the card is about a character with a profile to show: a
         * player's character by its id, not an account, an NPC, a role, a
         * Discord member or the Server.
         */
        boolean hasProfile() {
            return this.role == null && !this.npcIdentity
                    && !this.accountIdentity && this.characterId != null
                    && this.playerId != null
                    && !LostTalesChatMessagePacket.isDiscordSender(this.playerId)
                    && !LostTalesChatMessagePacket.isSystemSender(this.playerId);
        }

        /** The visit that shows this character's profile on the Characters page. */
        CharactersPage.Visit visit() {
            return new CharactersPage.Visit(this.playerId, this.characterId,
                    LostTalesChatVisualStyle.removeColorCodes(
                            this.identityName).trim(), this.skinId);
        }
        final String title;
        final String accountName;
        final int nameColor;
        /** A role mention's card target; every other field idle then. */
        final ChatAccountRole role;
        /**
         * A line of the card's own about the line under the pointer —
         * the command a Server line answers — or empty.
         */
        final String note;

        Target(UUID playerId, boolean accountIdentity, UUID characterId,
               String skinId, String identityName, String title,
               String accountName, int nameColor) {
            this(playerId, accountIdentity, false, characterId, skinId,
                    identityName, title, accountName, nameColor);
        }

        Target(UUID playerId, boolean accountIdentity, boolean npcIdentity,
               UUID characterId, String skinId, String identityName,
               String title, String accountName, int nameColor) {
            this(playerId, accountIdentity, npcIdentity, characterId, skinId,
                    identityName, title, accountName, nameColor, null, "");
        }

        private Target(UUID playerId, boolean accountIdentity,
                       boolean npcIdentity, UUID characterId, String skinId,
                       String identityName, String title,
                       String accountName, int nameColor,
                       ChatAccountRole role, String note) {
            this.playerId = playerId;
            this.accountIdentity = accountIdentity;
            this.npcIdentity = npcIdentity;
            this.characterId = characterId;
            this.skinId = skinId == null ? "" : skinId;
            this.identityName = identityName;
            this.title = title;
            this.accountName = accountName;
            this.nameColor = nameColor;
            this.role = role;
            this.note = note == null ? "" : note;
        }

        /** The same target with a line of its own about the line hovered. */
        Target withNote(String value) {
            return new Target(this.playerId, this.accountIdentity,
                    this.npcIdentity, this.characterId, this.skinId,
                    this.identityName, this.title, this.accountName,
                    this.nameColor, this.role, value);
        }

        static Target forRole(ChatAccountRole role) {
            return new Target(null, false, false, null, "", "", "", "",
                    role.getColor(), role, "");
        }

        /**
         * Who the card is about, so a person has one card: a role, an
         * NPC, an account, or one character of it.
         */
        String key() {
            if (this.role != null) {
                return "role:" + this.role.getId();
            }
            String who = this.playerId == null ? "" : this.playerId.toString();
            if (this.npcIdentity) {
                return "npc:" + who + "|" + this.skinId;
            }
            if (this.accountIdentity) {
                return "account:" + who;
            }
            return "character:" + who + "|" + (this.characterId != null
                    ? this.characterId.toString()
                    : LostTalesChatVisualStyle.removeColorCodes(
                            this.identityName).trim().toLowerCase(Locale.ROOT));
        }

        /** A person is the same one by who the card is about, whatever else was read off the line. */
        @Override
        public boolean equals(Object other) {
            return other instanceof Target
                    && ((Target)other).key().equals(key());
        }

        @Override
        public int hashCode() {
            return key().hashCode();
        }

        /** The name the card's window goes by: the person's, or the role's mention. */
        String windowTitle() {
            if (this.role != null) {
                return "@" + this.role.getDisplayName();
            }
            String name = LostTalesChatVisualStyle.removeColorCodes(
                    this.identityName).trim();
            return name.length() > 0 ? name : this.accountName.trim();
        }

        /**
         * The status line of the identity the card is about; empty where
         * there is none, or no identity to have one.
         */
        String statusLine() {
            if (presence() == null) {
                return "";
            }
            return ClientChatPresence.lineOf(this.playerId,
                    this.accountIdentity ? ChatPresenceIdentity.ACCOUNT
                            : ChatPresenceIdentity.character(this.characterId));
        }

        /**
         * The status of the identity the card is about; null where there
         * is none to tell — a role, a voice that is never online, or a
         * character the card cannot name.
         */
        ChatPresence presence() {
            if (this.role != null || !ChatPresenceMark.hasStatus(this.playerId,
                    this.npcIdentity, false)
                    || (!this.accountIdentity && this.characterId == null)) {
                return null;
            }
            return ChatPresenceMark.statusOf(this.playerId,
                    this.accountIdentity, this.characterId);
        }
    }
}
