package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.character.ClientCharacterDisplayNames;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;

/**
 * The bounded player card: opened by a click on the head or name of a
 * chat line, or on a mention, and shown in brief under the pointer for
 * the identity the head button speaks as. A chat line supplies its
 * snapshotted identity; the details — race, starting faction, gender,
 * age, and biography — come from the public appearance the
 * server already synced for that player, and are only shown when they
 * describe the character the line names. An NPC's head and name carry
 * the same card — the portrait, the name in its faction's colour, and
 * the faction its speech was captured with — so an NPC reads as a player
 * that happens not to exist.
 */
final class LostTalesChatHoverCard {
    /** A status line reads in italics. */
    private static final String STATUS_STYLE = "\u00a7o";
    /**
     * The card a click opened, standing where it was opened until a
     * click elsewhere, Escape or the screen closing takes it down; null
     * while none is. It shows the person in full, the way a messenger's
     * profile opens from a name.
     */
    private static Target pinned;
    private static int pinnedX;
    private static int pinnedY;
    /** The pinned card's rectangle as last drawn, for the click that closes it. */
    private static int pinnedLeft;
    private static int pinnedTop;
    private static int pinnedRight;
    private static int pinnedBottom;
    private static final int PADDING = 6;
    private static final int HEAD_SIZE = 16;
    private static final int MIN_WIDTH = 118;
    private static final int MAX_WIDTH = 210;
    /** Text width a biography may push the card out to before wrapping. */
    private static final int DESCRIPTION_WIDTH = 170;
    private static final int MAX_DESCRIPTION_LINES = 4;
    /** Holders a role card lists before folding the rest into a count. */
    private static final int MAX_ROLE_MEMBER_LINES = 8;
    /** The label of the row naming the command a Server line answers. */
    private static final String COMMAND_LABEL_KEY =
            "gui.losttales.chat.card.command";

    private LostTalesChatHoverCard() {}

    /**
     * The brief card of the identity a tab speaks as: what the head
     * button shows on hover, so who the roleplaying channels speak as
     * is read the way anyone else in the chat is. Nothing while a
     * clicked card stands open.
     */
    static void drawForIdentity(Minecraft minecraft, ChatTab tab, int mouseX,
                                int mouseY, int screenWidth, int screenHeight) {
        if (pinned != null || minecraft == null || minecraft.thePlayer == null) {
            return;
        }
        ClientChatSignature.Signature signature = ClientChatSignature.of(tab);
        ChatPresenceIdentity speaker = ClientChatPresence.speakerOf(tab);
        drawCard(minecraft, new Target(minecraft.thePlayer.getUniqueID(),
                        signature.accountLine, speaker.getCharacterId(),
                        signature.skinId, signature.identityName, "",
                        signature.accountName, signature.nameColor),
                mouseX, mouseY, screenWidth, screenHeight, false);
    }

    /**
     * Opens {@code target}'s full card at the pointer, where it stays
     * until {@link #unpin()}.
     */
    static void pin(Target target, int mouseX, int mouseY) {
        pinned = target;
        pinnedX = mouseX;
        pinnedY = mouseY;
        pinnedLeft = pinnedRight = pinnedTop = pinnedBottom = 0;
    }

    static void unpin() {
        pinned = null;
    }

    static boolean isPinned() {
        return pinned != null;
    }

    /** Whether a GUI point lies on the clicked card as it was last drawn. */
    static boolean pinnedContains(double mouseX, double mouseY) {
        return pinned != null && contains((float)mouseX, (float)mouseY,
                pinnedLeft, pinnedTop, pinnedRight, pinnedBottom);
    }

    /** The clicked card in full, where the click opened it. */
    static void drawPinned(Minecraft minecraft, int screenWidth,
                           int screenHeight) {
        if (pinned != null) {
            drawCard(minecraft, pinned, pinnedX, pinnedY, screenWidth,
                    screenHeight, true);
        }
    }

    /**
     * Lays the card out as a name line followed by detail lines, then
     * draws it. The name line reads {@code Character (Account)} for a
     * character identity and just {@code Account} otherwise; every detail
     * line is omitted rather than shown empty when the value is unknown.
     * The brief card — the hover — stops after the title, the command a
     * Server line answers and the roles held; the {@code full} card a
     * click opens goes on to the character's race, faction, gender, age
     * and biography.
     */
    private static void drawCard(Minecraft minecraft, Target target,
                                 int mouseX, int mouseY,
                                 int screenWidth, int screenHeight,
                                 boolean full) {
        if (minecraft.fontRenderer == null) {
            return;
        }
        if (target.role != null) {
            drawRoleCard(minecraft, target.role, mouseX, mouseY,
                    screenWidth, screenHeight, full);
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        CharacterAppearance details = detailsFor(target);
        String name = LostTalesChatVisualStyle.removeColorCodes(
                target.identityName).trim();
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
        String title = cleanBracketed(target.title);
        List<String> lines = new ArrayList<String>(8);
        if (title.length() > 0) {
            lines.add(title);
        }
        // What the identity says of itself, as it says it, under its name.
        String statusLine = ClientChatProfanity.filter(target.statusLine());
        int statusRow = -1;
        if (statusLine.length() > 0) {
            statusRow = lines.size();
            lines.add(statusLine);
        }
        // The Server's card says what it is answering: the command the
        // line under the pointer was the answer to, as inline code.
        int commandRow = addDetail(lines, COMMAND_LABEL_KEY, target.note)
                ? lines.size() - 1 : -1;
        // The roles the identity wears, whichever channel the line was
        // said in: an account its own, a character the account's and its
        // own. A role not worn on an in-character line is still held, and
        // the card is where it shows.
        int worn = target.accountIdentity || details == null
                ? ChatMentionColors.rolesFor(account)
                : ChatMentionColors.rolesFor(account,
                        details.getCharacterId());
        addDetail(lines, "gui.losttales.chat.card.roles", target.npcIdentity
                ? "" : roleNames(worn));
        // Away, Do Not Disturb or Offline, for the identity the card
        // is about; Online is no news.
        addDetail(lines, "gui.losttales.chat.card.status",
                ChatPresenceMark.label(target.presence()));
        // An NPC's faction is what its speech was captured with, and the
        // brief card says it too: without it the card is a name alone.
        if (full || target.npcIdentity) {
            addDetail(lines, "gui.losttales.chat.card.faction",
                    target.npcIdentity
                            ? ChatChannelIcons.npcFaction(target.playerId)
                            : details == null ? ""
                            : ClientCharacterDisplayNames.faction(
                                    details.getStartingFactionId()));
        }
        String description = "";
        if (full) {
            addDetail(lines, "gui.losttales.character.race",
                    details == null ? "" : ClientCharacterDisplayNames.race(
                            details.getRaceId()));
            addDetail(lines, "gui.losttales.character.gender",
                    details == null || details.getGenderId().length() == 0
                            ? "" : ClientCharacterDisplayNames.gender(
                                    details.getGenderId()));
            addDetail(lines, "gui.losttales.character.age",
                    details == null || details.getAge() <= 0
                            ? "" : String.valueOf(details.getAge()));
            description = details == null ? "" : details.getDescription();
        }

        int contentWidth = font.getStringWidth(name + suffix);
        for (int index = 0; index < lines.size(); index++) {
            contentWidth = Math.max(contentWidth, index == statusRow
                    ? ChatInlineText.width(font, lines.get(index), STATUS_STYLE)
                    : font.getStringWidth(lines.get(index)));
        }
        if (description.length() > 0) {
            contentWidth = Math.max(contentWidth, Math.min(
                    font.getStringWidth(description), DESCRIPTION_WIDTH));
        }
        int width = Math.max(MIN_WIDTH,
                Math.min(MAX_WIDTH, PADDING + HEAD_SIZE + 6
                        + contentWidth + PADDING));
        width = Math.min(width, Math.max(40, screenWidth - 8));
        int textWidth = width - PADDING - HEAD_SIZE - 6 - PADDING;
        if (description.length() > 0) {
            appendDescription(font, lines, description, textWidth);
        }
        int nameWidth = font.getStringWidth(name);
        if (nameWidth + font.getStringWidth(suffix) > textWidth) {
            // The account suffix gives way before the name does.
            suffix = LostTalesSkyrimUiStyle.trimToWidth(font, suffix,
                    Math.max(0, textWidth - nameWidth));
            name = LostTalesSkyrimUiStyle.trimToWidth(font, name, textWidth);
            nameWidth = font.getStringWidth(name);
        }
        int lineCount = 1 + lines.size();
        int height = Math.max(HEAD_SIZE + PADDING * 2,
                PADDING * 2 + lineCount * font.FONT_HEIGHT);
        int x = cardX(mouseX, width, screenWidth);
        int y = cardY(mouseY, height, screenHeight);
        if (full) {
            rememberPinnedBounds(x, y, width, height);
        }

        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0.0F, 0.0F, 300.0F);
            LostTalesChatVisualStyle.drawPopup(x, y, x + width, y + height,
                    1.0F);
            drawHead(minecraft, target, x + PADDING, y + PADDING);
            int textX = x + PADDING + HEAD_SIZE + 6;
            int textY = y + PADDING;
            drawColored(font, name, textX, textY, target.nameColor);
            if (suffix.length() > 0) {
                drawColored(font, suffix, textX + nameWidth, textY,
                        LostTalesSkyrimUiStyle.TEXT_MUTED);
            }
            textY += font.FONT_HEIGHT;
            for (int index = 0; index < lines.size(); index++) {
                String line = LostTalesSkyrimUiStyle.trimToWidth(font,
                        lines.get(index), textWidth);
                if (index == 0 && title.length() > 0) {
                    LostTalesChatVisualStyle.drawPlain(font, line,
                            textX, textY, 255);
                } else if (index == statusRow) {
                    // What the identity says of itself, as it says it:
                    // italic words and their emojis.
                    ChatInlineText.draw(minecraft, font,
                            ChatInlineText.trimToWidth(font, lines.get(index),
                                    STATUS_STYLE, textWidth),
                            STATUS_STYLE, textX, textY,
                            LostTalesChatVisualStyle.asideRgb(), 255);
                } else if (index == commandRow) {
                    drawCommandDetail(font, target.note, textX, textY,
                            textWidth);
                } else {
                    drawColored(font, line, textX, textY,
                            LostTalesSkyrimUiStyle.TEXT_MUTED);
                }
                textY += font.FONT_HEIGHT;
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
        }
    }

    /**
     * The card of a mentioned role: {@code @Name} in the role's colour,
     * what the role is, and — on the {@code full} card a click opens —
     * the online accounts holding it, the server's own roster, sent
     * with the chat access, so the list is its word and not a guess
     * from who happened to speak.
     */
    private static void drawRoleCard(Minecraft minecraft,
                                     ChatAccountRole role, int mouseX,
                                     int mouseY, int screenWidth,
                                     int screenHeight, boolean full) {
        if (minecraft.fontRenderer == null) {
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        String name = "@" + role.getDisplayName();
        String description = role.getDisplayDescription();
        List<String> members = full
                ? ClientChatChannelState.roleHolders(role)
                : java.util.Collections.<String>emptyList();
        String membersLabel = StatCollector.translateToLocal(
                "gui.losttales.chat.card.role.members");

        int contentWidth = font.getStringWidth(name);
        if (full) {
            contentWidth = Math.max(contentWidth,
                    font.getStringWidth(membersLabel));
        }
        if (description.length() > 0) {
            contentWidth = Math.max(contentWidth, Math.min(
                    font.getStringWidth(description), DESCRIPTION_WIDTH));
        }
        for (int index = 0; index < members.size()
                && index < MAX_ROLE_MEMBER_LINES; index++) {
            contentWidth = Math.max(contentWidth,
                    font.getStringWidth("  " + members.get(index)));
        }
        int width = Math.max(MIN_WIDTH,
                Math.min(MAX_WIDTH, PADDING * 2 + contentWidth));
        width = Math.min(width, Math.max(40, screenWidth - 8));
        int textWidth = width - PADDING * 2;

        List<String> lines = new ArrayList<String>(8);
        if (description.length() > 0) {
            @SuppressWarnings("unchecked")
            List<String> wrapped = font.listFormattedStringToWidth(
                    description, Math.max(20, textWidth));
            int count = Math.min(wrapped.size(), MAX_DESCRIPTION_LINES);
            for (int index = 0; index < count; index++) {
                lines.add(wrapped.get(index).trim());
            }
        }
        int memberStart = lines.size() + 1;
        if (full) {
            lines.add(membersLabel);
            if (members.isEmpty()) {
                lines.add("  " + StatCollector.translateToLocal(
                        "gui.losttales.chat.card.role.nobody"));
            } else {
                int shown = Math.min(members.size(), MAX_ROLE_MEMBER_LINES);
                for (int index = 0; index < shown; index++) {
                    lines.add("  " + members.get(index));
                }
                if (members.size() > shown) {
                    lines.add("  " + StatCollector.translateToLocalFormatted(
                            "gui.losttales.chat.card.role.more",
                            Integer.valueOf(members.size() - shown)));
                }
            }
        }

        int height = PADDING * 2 + (1 + lines.size()) * font.FONT_HEIGHT;
        int x = cardX(mouseX, width, screenWidth);
        int y = cardY(mouseY, height, screenHeight);
        if (full) {
            rememberPinnedBounds(x, y, width, height);
        }
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0.0F, 0.0F, 300.0F);
            LostTalesChatVisualStyle.drawPopup(x, y, x + width, y + height,
                    1.0F);
            int textX = x + PADDING;
            int textY = y + PADDING;
            drawColored(font, name, textX, textY, role.getColor());
            textY += font.FONT_HEIGHT;
            for (int index = 0; index < lines.size(); index++) {
                String line = LostTalesSkyrimUiStyle.trimToWidth(font,
                        lines.get(index), textWidth);
                // The holders read as the people they are; everything
                // else stays the card's muted grey.
                boolean member = !members.isEmpty() && index >= memberStart
                        && index < memberStart + Math.min(members.size(),
                                MAX_ROLE_MEMBER_LINES);
                drawColored(font, line, textX, textY, member
                        ? role.getColor()
                        : LostTalesSkyrimUiStyle.TEXT_MUTED);
                textY += font.FONT_HEIGHT;
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
        }
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

    /** Adds a {@code Label: value} row for a known value; whether it did. */
    private static boolean addDetail(List<String> lines, String labelKey,
                                     String value) {
        String text = value == null ? "" : value.trim();
        if (text.length() == 0) {
            return false;
        }
        lines.add(StatCollector.translateToLocal(labelKey) + ": " + text);
        return true;
    }

    /**
     * The row naming the command a Server line answers: the label in the
     * card's muted tone, and the command as the chat's inline code, in
     * italics and the aside tone, as the chat shows a command wherever
     * it shows one.
     */
    private static void drawCommandDetail(FontRenderer font, String command,
                                          int x, int y, int width) {
        String label = LostTalesSkyrimUiStyle.trimToWidth(font,
                StatCollector.translateToLocal(COMMAND_LABEL_KEY) + ": ",
                width);
        drawColored(font, label, x, y, LostTalesSkyrimUiStyle.TEXT_MUTED);
        int labelWidth = font.getStringWidth(label);
        String code = LostTalesSkyrimUiStyle.trimToWidth(font,
                command == null ? "" : command.trim(), width - labelWidth);
        if (code.length() > 0) {
            drawColored(font, EnumChatFormatting.ITALIC + code,
                    x + labelWidth, y, LostTalesChatVisualStyle.asideRgb());
        }
    }

    /**
     * The biography, wrapped to the card and bounded to a few lines so a
     * long one cannot push the card off the screen.
     */
    private static void appendDescription(FontRenderer font,
                                          List<String> lines,
                                          String description,
                                          int textWidth) {
        String label = StatCollector.translateToLocal(
                "gui.losttales.character.description") + ": ";
        @SuppressWarnings("unchecked")
        List<String> wrapped = font.listFormattedStringToWidth(
                label + LostTalesChatVisualStyle.removeColorCodes(
                        description), Math.max(20, textWidth));
        int count = Math.min(wrapped.size(), MAX_DESCRIPTION_LINES);
        for (int index = 0; index < count; index++) {
            String line = wrapped.get(index).trim();
            if (index == count - 1 && wrapped.size() > count) {
                line = LostTalesSkyrimUiStyle.trimToWidth(font,
                        line + "...", textWidth);
            }
            lines.add(line);
        }
    }

    /** Where the clicked card was last drawn, for the click that closes it. */
    private static void rememberPinnedBounds(int x, int y, int width,
                                             int height) {
        pinnedLeft = x;
        pinnedTop = y;
        pinnedRight = x + width;
        pinnedBottom = y + height;
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
                    inSpan = true;
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
     * Card target for a mentioned player: whoever this client can place
     * under the account now, and otherwise the player as the line
     * recorded them — the identity they were playing and its head — so a
     * mention in an older line still opens its card after the player has
     * gone, named as a live mention's card names them. Null when neither
     * names anybody.
     */
    static Target targetForMention(Minecraft minecraft,
                                   ChatMentionMarker.Data mention) {
        Target placed = targetForAccount(minecraft, mention.account);
        if (placed != null || mention.recorded == null
                || mention.recorded.getPlayerId() == null) {
            return placed;
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
        if (accountIdentity && minecraft != null) {
            LostTalesCharacterHeadIconRenderer.rememberAccountSkin(
                    minecraft, member.getPlayerId(), member.getAccount());
        }
        return new Target(member.getPlayerId(), accountIdentity,
                member.getCharacterId(), member.getSkinId(), member.getName(),
                member.getTitle(), member.getAccount(), member.getNameColor());
    }

    /** The card of a player as a line recorded them. */
    static Target recordedTarget(Minecraft minecraft,
                                 ChatNamedPlayer recorded) {
        boolean accountIdentity = recorded.getCharacterId() == null;
        if (accountIdentity && minecraft != null) {
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
        if (playerId == null) {
            return null;
        }
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
                                    int x, int y, int color) {
        LostTalesChatVisualStyle.drawColored(font,
                LostTalesChatVisualStyle.removeColorCodes(text),
                x, y, color, 255);
    }

    /** Whether the point lies between two corners, whichever way round they are given. */
    static boolean contains(float x, float y, float left, float top,
                            float right, float bottom) {
        return LostTalesUiHitBox.contains(x, y, Math.min(left, right),
                Math.min(top, bottom), Math.abs(right - left),
                Math.abs(bottom - top));
    }

    /**
     * A tooltip of plain lines, drawn as every other popup is: the
     * chat's popup surface, the chat's shadow, and vanilla's colour
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
        int width = Math.min(PADDING * 2 + contentWidth, Math.max(40, screenWidth - 8));
        int height = PADDING * 2 + lines.size() * font.FONT_HEIGHT;
        int x = cardX(mouseX, width, screenWidth);
        int y = cardY(mouseY, height, screenHeight);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0.0F, 0.0F, 300.0F);
            LostTalesChatVisualStyle.drawPopup(x, y, x + width, y + height,
                    1.0F);
            int textY = y + PADDING;
            for (int index = 0; index < lines.size(); index++) {
                LostTalesChatVisualStyle.drawLegacyFormatted(font,
                        lines.get(index) == null ? "" : lines.get(index),
                        x + PADDING, textY, 255);
                textY += font.FONT_HEIGHT;
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
         * is none to tell — a role, an NPC, the server, the client, the
         * Discord bridge, or a character the card cannot name.
         */
        ChatPresence presence() {
            if (this.role != null || this.npcIdentity || this.playerId == null
                    || LostTalesChatMessagePacket.isSystemSender(this.playerId)
                    || LostTalesChatMessagePacket.isDiscordSender(this.playerId)) {
                return null;
            }
            if (this.accountIdentity) {
                return ClientChatPresence.presenceOf(this.playerId,
                        ChatPresenceIdentity.ACCOUNT);
            }
            return this.characterId == null ? null
                    : ClientChatPresence.presenceOf(this.playerId,
                            ChatPresenceIdentity.character(this.characterId));
        }
    }
}
