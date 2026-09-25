package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
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
import java.util.Locale;

/**
 * The bounded player card: opened in a sub-window of its own by a
 * click on the head or name of a chat line, on a mention or on a member,
 * and shown in brief under the pointer for the identity the head button
 * speaks as. A chat line supplies its snapshotted identity; the details —
 * race, starting faction, gender, age, and biography — come from the
 * public appearance the server already synced for that player, and are
 * only shown when they describe the character the line names. An NPC's
 * head and name carry the same card — the portrait, the name in its
 * faction's colour, and the faction its speech was captured with — so an
 * NPC reads as a player that happens not to exist.
 */
final class LostTalesChatHoverCard {
    /** A status line reads in italics. */
    private static final String STATUS_STYLE = "§o";
    static final int PADDING = 6;
    private static final int HEAD_SIZE = 16;
    /** The gap between the head and the words beside it. */
    private static final int HEAD_GAP = 6;
    static final int MIN_WIDTH = 118;
    static final int MAX_WIDTH = 210;
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
     * is read the way anyone else in the chat is.
     */
    static void drawForIdentity(Minecraft minecraft, ChatTab tab, int mouseX,
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
     * A card laid out and ready to draw: its name row, its detail rows and
     * which of them are drawn their own way, and the size it takes.
     */
    static final class Laid {
        final Target target;
        String name = "";
        String suffix = "";
        int nameWidth;
        int nameColor;
        final List<String> lines = new ArrayList<String>(8);
        int titleRow = -1;
        int statusRow = -1;
        int commandRow = -1;
        /** A role card's holders: the rows from here, this many. */
        int memberStart = -1;
        int memberRows;
        boolean head = true;
        int width;
        int height;
        int textWidth;

        Laid(Target target) {
            this.target = target;
        }
    }

    /**
     * Lays the card out: a name row followed by detail rows. The name row
     * reads {@code Character (Account)} for a character identity and just
     * {@code Account} otherwise; every detail row is left out rather than
     * shown empty when the value is unknown. The brief card stops after
     * the title, the command a Server line answers and the roles held; the
     * {@code full} card goes on to the character's race, faction, gender,
     * age and biography. {@code width} fixes the card's width, a window's;
     * at 0 the card takes the width its rows want, up to
     * {@code maxWidth}.
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
        List<String> lines = laid.lines;
        if (title.length() > 0) {
            laid.titleRow = lines.size();
            lines.add(title);
        }
        // What the identity says of itself, as it says it, under its name.
        String statusLine = ClientChatProfanity.filter(target.statusLine());
        if (statusLine.length() > 0) {
            laid.statusRow = lines.size();
            lines.add(statusLine);
        }
        // The Server's card says what it is answering: the command the
        // line under the pointer was the answer to, as inline code.
        if (addDetail(lines, COMMAND_LABEL_KEY, target.note)) {
            laid.commandRow = lines.size() - 1;
        }
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

        if (width > 0) {
            laid.width = width;
        } else {
            int contentWidth = font.getStringWidth(name + suffix);
            for (int index = 0; index < lines.size(); index++) {
                contentWidth = Math.max(contentWidth, index == laid.statusRow
                        ? ChatInlineText.width(font, lines.get(index),
                                STATUS_STYLE)
                        : font.getStringWidth(lines.get(index)));
            }
            if (description.length() > 0) {
                contentWidth = Math.max(contentWidth, Math.min(
                        font.getStringWidth(description), DESCRIPTION_WIDTH));
            }
            laid.width = Math.min(Math.max(MIN_WIDTH, Math.min(MAX_WIDTH,
                    PADDING + HEAD_SIZE + HEAD_GAP + contentWidth + PADDING)),
                    maxWidth);
        }
        laid.textWidth = Math.max(0, laid.width - PADDING - HEAD_SIZE
                - HEAD_GAP - PADDING);
        if (description.length() > 0) {
            appendDescription(font, lines, description, laid.textWidth);
        }
        int nameWidth = font.getStringWidth(name);
        if (nameWidth + font.getStringWidth(suffix) > laid.textWidth) {
            // The account suffix gives way before the name does.
            suffix = LostTalesSkyrimUiStyle.trimToWidth(font, suffix,
                    Math.max(0, laid.textWidth - nameWidth));
            name = LostTalesSkyrimUiStyle.trimToWidth(font, name,
                    laid.textWidth);
            nameWidth = font.getStringWidth(name);
        }
        laid.name = name;
        laid.suffix = suffix;
        laid.nameWidth = nameWidth;
        laid.nameColor = target.nameColor;
        laid.height = Math.max(HEAD_SIZE + PADDING * 2,
                PADDING * 2 + (1 + lines.size()) * font.FONT_HEIGHT);
        return laid;
    }

    /**
     * The card of a mentioned role: {@code @Name} in the role's colour,
     * what the role is, and — on the {@code full} card — the online
     * accounts holding it, the server's own roster, sent with the chat
     * access, so the list is its word and not a guess from who happened
     * to speak.
     */
    private static Laid layOutRole(FontRenderer font, Target target,
                                   boolean full, int width, int maxWidth) {
        ChatAccountRole role = target.role;
        Laid laid = new Laid(target);
        laid.head = false;
        laid.name = "@" + role.getDisplayName();
        laid.nameWidth = font.getStringWidth(laid.name);
        laid.nameColor = role.getColor();
        String description = role.getDisplayDescription();
        List<String> members = full
                ? ClientChatChannelState.roleHolders(role)
                : java.util.Collections.<String>emptyList();
        String membersLabel = StatCollector.translateToLocal(
                "gui.losttales.chat.card.role.members");
        if (width > 0) {
            laid.width = width;
        } else {
            int contentWidth = laid.nameWidth;
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
            laid.width = Math.min(Math.max(MIN_WIDTH,
                    Math.min(MAX_WIDTH, PADDING * 2 + contentWidth)),
                    maxWidth);
        }
        laid.textWidth = Math.max(0, laid.width - PADDING * 2);
        List<String> lines = laid.lines;
        if (description.length() > 0) {
            @SuppressWarnings("unchecked")
            List<String> wrapped = font.listFormattedStringToWidth(
                    description, Math.max(20, laid.textWidth));
            int count = Math.min(wrapped.size(), MAX_DESCRIPTION_LINES);
            for (int index = 0; index < count; index++) {
                lines.add(wrapped.get(index).trim());
            }
        }
        if (full) {
            lines.add(membersLabel);
            if (members.isEmpty()) {
                lines.add("  " + StatCollector.translateToLocal(
                        "gui.losttales.chat.card.role.nobody"));
            } else {
                laid.memberStart = lines.size();
                laid.memberRows = Math.min(members.size(),
                        MAX_ROLE_MEMBER_LINES);
                for (int index = 0; index < laid.memberRows; index++) {
                    lines.add("  " + members.get(index));
                }
                if (members.size() > laid.memberRows) {
                    lines.add("  " + StatCollector.translateToLocalFormatted(
                            "gui.losttales.chat.card.role.more",
                            Integer.valueOf(members.size() - laid.memberRows)));
                }
            }
        }
        laid.height = PADDING * 2 + (1 + lines.size()) * font.FONT_HEIGHT;
        return laid;
    }

    /**
     * Draws a laid-out card with its top left at {@code x}, {@code y}, on
     * whatever surface stands under it: the head, the name row, and the
     * detail rows, each its own way.
     */
    static void drawLaid(Minecraft minecraft, Laid laid, int x, int y,
                         int alpha) {
        FontRenderer font = minecraft.fontRenderer;
        int textX = x + PADDING + (laid.head ? HEAD_SIZE + HEAD_GAP : 0);
        int textY = y + PADDING;
        if (laid.head) {
            drawHead(minecraft, laid.target, x + PADDING, y + PADDING);
        }
        LostTalesUiInk.beginContent();
        drawColored(font, laid.name, textX, textY, laid.nameColor, alpha);
        if (laid.suffix.length() > 0) {
            drawColored(font, laid.suffix, textX + laid.nameWidth, textY,
                    LostTalesSkyrimUiStyle.TEXT_MUTED, alpha);
        }
        textY += font.FONT_HEIGHT;
        for (int index = 0; index < laid.lines.size(); index++) {
            String line = LostTalesSkyrimUiStyle.trimToWidth(font,
                    laid.lines.get(index), laid.textWidth);
            if (index == laid.titleRow) {
                LostTalesChatVisualStyle.drawPlain(font, line, textX, textY,
                        alpha);
            } else if (index == laid.statusRow) {
                // What the identity says of itself, as it says it: italic
                // words and their emojis.
                ChatInlineText.draw(minecraft, font,
                        ChatInlineText.trimToWidth(font, laid.lines.get(index),
                                STATUS_STYLE, laid.textWidth),
                        STATUS_STYLE, textX, textY,
                        LostTalesChatVisualStyle.asideRgb(), alpha);
            } else if (index == laid.commandRow) {
                drawCommandDetail(font, laid.target.note, textX, textY,
                        laid.textWidth, alpha);
            } else {
                // A role card's holders read as the people they are;
                // everything else stays the card's muted grey.
                boolean member = laid.memberStart >= 0
                        && index >= laid.memberStart
                        && index < laid.memberStart + laid.memberRows;
                drawColored(font, line, textX, textY, member
                        ? laid.nameColor : LostTalesSkyrimUiStyle.TEXT_MUTED,
                        alpha);
            }
            textY += font.FONT_HEIGHT;
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
                                          int x, int y, int width,
                                          int alpha) {
        String label = LostTalesSkyrimUiStyle.trimToWidth(font,
                StatCollector.translateToLocal(COMMAND_LABEL_KEY) + ": ",
                width);
        drawColored(font, label, x, y, LostTalesSkyrimUiStyle.TEXT_MUTED,
                alpha);
        int labelWidth = font.getStringWidth(label);
        String code = LostTalesSkyrimUiStyle.trimToWidth(font,
                command == null ? "" : command.trim(), width - labelWidth);
        if (code.length() > 0) {
            drawColored(font, EnumChatFormatting.ITALIC + code,
                    x + labelWidth, y, LostTalesChatVisualStyle.asideRgb(),
                    alpha);
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
                member.getTitle(), member.getAccount(), member.getNameColor());
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
            WindowStyle.drawPopup(x, y, x + width, y + height,
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
