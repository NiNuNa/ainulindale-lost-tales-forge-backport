package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatMentionCandidate;
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
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;

/**
 * The bounded player card: shown for the head or name of a chat line, and
 * for a row of the mention completion list, so hovering either tells the
 * same story about the same player. A chat line supplies its snapshotted
 * identity; the details — race, starting faction, level, gender, age, and
 * biography — come from the public appearance the server already synced
 * for that player, and are only shown when they describe the character
 * the line names. An NPC's head and name carry the same card — the
 * portrait, the name in its faction's colour, and the faction its
 * speech was captured with — so an NPC reads as a player that happens
 * not to exist.
 */
final class LostTalesChatHoverCard {
    private static final int PADDING = 6;
    private static final int HEAD_SIZE = 16;
    private static final int MIN_WIDTH = 118;
    private static final int MAX_WIDTH = 210;
    /** Text width a biography may push the card out to before wrapping. */
    private static final int DESCRIPTION_WIDTH = 170;
    private static final int MAX_DESCRIPTION_LINES = 4;
    /** Holders a role card lists before folding the rest into a count. */
    private static final int MAX_ROLE_MEMBER_LINES = 8;

    private LostTalesChatHoverCard() {}

    static void draw(Minecraft minecraft, float mouseX, float mouseY,
                     int screenWidth, int screenHeight) {
        Target target = find(minecraft, mouseX, mouseY);
        if (target != null) {
            drawCard(minecraft, target, (int)mouseX, (int)mouseY,
                    screenWidth, screenHeight);
        }
    }

    /**
     * Whether the pointer stands on somebody — a sender's identity span
     * or a mention — rather than on message text: exactly where a card
     * is showing. A right-click there belongs to the person, so the
     * message-copy action stands aside.
     */
    static boolean isPointerOnPerson(Minecraft minecraft, float mouseX,
                                     float mouseY) {
        return find(minecraft, mouseX, mouseY) != null;
    }

    /**
     * Card for a mention candidate. The candidate's key is the player's
     * UUID when the appearance sync knows them; without it only the
     * account identity can be shown.
     */
    static void drawForCandidate(Minecraft minecraft,
                                 ChatMentionCandidate candidate,
                                 int mouseX, int mouseY,
                                 int screenWidth, int screenHeight) {
        if (minecraft == null || candidate == null
                || !candidate.isUsable()) {
            return;
        }
        if (candidate.isRole()) {
            // A role row shows the role's own card, exactly as its
            // mention in a message does. The row's key is
            // "role:<name>", the same token the mention marker carries.
            for (ChatAccountRole role : ChatAccountRole.mentionable()) {
                if (candidate.getKey().equalsIgnoreCase(
                        "role:" + role.name())) {
                    drawRoleCard(minecraft, role, mouseX, mouseY,
                            screenWidth, screenHeight);
                    return;
                }
            }
            return;
        }
        UUID playerId = parseUuid(candidate.getKey());
        if (playerId == null && minecraft.thePlayer != null
                && candidate.getAccountName().equalsIgnoreCase(
                        minecraft.thePlayer.getCommandSenderName())) {
            playerId = minecraft.thePlayer.getUniqueID();
        }
        if (playerId == null) {
            return;
        }
        CharacterAppearance appearance =
                ClientCharacterAppearanceCache.getAuthoritative(playerId);
        boolean accountIdentity = appearance == null
                || !appearance.isPresent()
                || candidate.getCharacterName().length() == 0;
        if (accountIdentity) {
            LostTalesCharacterHeadIconRenderer.rememberAccountSkin(
                    minecraft, playerId, candidate.getAccountName());
        }
        drawCard(minecraft, new Target(playerId, accountIdentity,
                        appearance == null ? "" : appearance.getSkinId(),
                        accountIdentity ? candidate.getAccountName()
                                : candidate.getCharacterName(),
                        "", candidate.getAccountName(),
                        LostTalesColors.rgb(LostTalesColors.HUD_LABEL)),
                mouseX, mouseY, screenWidth, screenHeight);
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Lays the card out as a name line followed by detail lines, then
     * draws it. The name line reads {@code Character (Account)} for a
     * character identity and just {@code Account} otherwise; every detail
     * line is omitted rather than shown empty when the value is unknown.
     */
    private static void drawCard(Minecraft minecraft, Target target,
                                 int mouseX, int mouseY,
                                 int screenWidth, int screenHeight) {
        if (minecraft.fontRenderer == null) {
            return;
        }
        if (target.role != null) {
            drawRoleCard(minecraft, target.role, mouseX, mouseY,
                    screenWidth, screenHeight);
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
        addDetail(lines, "gui.losttales.character.race",
                details == null ? "" : ClientCharacterDisplayNames.race(
                        details.getRaceId()));
        addDetail(lines, "gui.losttales.chat.card.faction",
                target.npcIdentity
                        ? ChatChannelIcons.npcFaction(target.playerId)
                        : details == null
                                || details.getStartingFactionId().length() == 0
                        ? "" : ClientCharacterDisplayNames.faction(
                                details.getStartingFactionId()));
        addDetail(lines, "gui.losttales.chat.card.level",
                details == null || details.getRoleplayLevel() <= 0
                        ? "" : String.valueOf(details.getRoleplayLevel()));
        addDetail(lines, "gui.losttales.character.gender",
                details == null || details.getGenderId().length() == 0
                        ? "" : ClientCharacterDisplayNames.gender(
                                details.getGenderId()));
        addDetail(lines, "gui.losttales.character.age",
                details == null || details.getAge() <= 0
                        ? "" : String.valueOf(details.getAge()));
        String description = details == null ? "" : details.getDescription();

        int contentWidth = font.getStringWidth(name + suffix);
        for (int index = 0; index < lines.size(); index++) {
            contentWidth = Math.max(contentWidth,
                    font.getStringWidth(lines.get(index)));
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

        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0.0F, 0.0F, 300.0F);
            LostTalesSkyrimUiStyle.drawPanel(x, y, width, height);
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
     * what the role is, and the online accounts holding it — the
     * server's own roster, sent with the chat access, so the list is
     * its word and not a guess from who happened to speak.
     */
    private static void drawRoleCard(Minecraft minecraft,
                                     ChatAccountRole role, int mouseX,
                                     int mouseY, int screenWidth,
                                     int screenHeight) {
        if (minecraft.fontRenderer == null) {
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        String name = "@" + StatCollector.translateToLocal(
                role.getNameKey());
        String descriptionKey = role.getNameKey() + ".description";
        String description = StatCollector.translateToLocal(descriptionKey);
        if (description.equals(descriptionKey)) {
            description = "";
        }
        List<String> members = ClientChatChannelState.roleHolders(role);
        String membersLabel = StatCollector.translateToLocal(
                "gui.losttales.chat.card.role.members");

        int contentWidth = Math.max(font.getStringWidth(name),
                font.getStringWidth(membersLabel));
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
        lines.add(membersLabel);
        int memberStart = lines.size();
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

        int height = PADDING * 2 + (1 + lines.size()) * font.FONT_HEIGHT;
        int x = cardX(mouseX, width, screenWidth);
        int y = cardY(mouseY, height, screenHeight);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0.0F, 0.0F, 300.0F);
            LostTalesSkyrimUiStyle.drawPanel(x, y, width, height);
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

    private static void addDetail(List<String> lines, String labelKey,
                                  String value) {
        String text = value == null ? "" : value.trim();
        if (text.length() > 0) {
            lines.add(StatCollector.translateToLocal(labelKey) + ": "
                    + text);
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

    private static Target find(Minecraft minecraft, float mouseX,
                               float mouseY) {
        if (minecraft == null || minecraft.ingameGUI == null
                || minecraft.fontRenderer == null) {
            return null;
        }
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        if (chat == null || !chat.getChatOpen()) {
            return null;
        }
        try {
            LostTalesChatOverlayRenderer.Band band =
                    LostTalesChatOverlayRenderer.bandAt(
                            minecraft, mouseX, mouseY);
            List<ChatLine> lines = band == null ? null : band.lines;
            if (band == null || lines == null
                    || band.viewIndex >= lines.size()
                    || lines.get(band.viewIndex) == null) {
                return null;
            }
            // The band already answers the vertical question exactly as
            // drawn; only the horizontal component walk remains, in the
            // line's own text space, skipping what the renderer skipped.
            // The sender's identity is one span — the opening bracket,
            // the head with its clear pixels, the name, the title, the
            // spacers, the closing bracket — and every pixel of it
            // answers with the card, so the card never blinks out in the
            // hairline gaps between two of its parts.
            int cursor = 0;
            boolean identitySpan = false;
            float previousStart = 0.0F;
            String previousText = null;
            for (Object value
                    : lines.get(band.viewIndex).func_151461_a()) {
                if (!(value instanceof IChatComponent)) {
                    continue;
                }
                IChatComponent part = (IChatComponent)value;
                if (ChatPrefixMarker.isHidden(part, true)) {
                    continue;
                }
                int partWidth = LostTalesChatVisualStyle.partWidth(
                        minecraft.fontRenderer, part, true);
                // A mention shows the card of whoever it reaches, not
                // the line's sender: a player's card, or for a role
                // mention the role's own card naming its holders.
                ChatMentionMarker.Data mention =
                        ChatMentionMarker.decode(part);
                if (mention != null && band.localX >= cursor
                        && band.localX < cursor + partWidth) {
                    ChatAccountRole role = mention.role();
                    return role != null ? Target.forRole(role)
                            : targetForAccount(minecraft, mention.account);
                }
                ChatHeadMarker.Data decodedHead =
                        ChatHeadMarker.decode(part);
                String text = decodedHead != null ? ""
                        : LostTalesChatVisualStyle.removeColorCodes(
                                part.getUnformattedTextForChat()).trim();
                boolean inSpan = identitySpan;
                if (isReplyIdentity(part) && !identitySpan) {
                    // A player's opening bracket carries the reply
                    // identity and starts the span.
                    identitySpan = true;
                    inSpan = true;
                }
                if (decodedHead != null) {
                    inSpan = true;
                    if (decodedHead.npcIdentity && !identitySpan) {
                        // An NPC's brackets carry no reply identity —
                        // nobody is on the other end of a /msg — so its
                        // span opens at its head and reaches back over
                        // the bracket standing just before it.
                        identitySpan = true;
                        if (previousText != null
                                && previousText.startsWith("<")
                                && band.localX >= previousStart
                                && band.localX < cursor) {
                            return targetForGroup(lines, band.viewIndex);
                        }
                    }
                }
                // The span's last part is the closing bracket run, whose
                // trailing space belongs to the gap before the message,
                // not to the name: the span ends on the bracket's own
                // glyphs, so the hitbox matches what is drawn.
                boolean closesSpan = identitySpan && decodedHead == null
                        && text.startsWith(">");
                int spanWidth = partWidth;
                if (closesSpan) {
                    String raw = part.getUnformattedTextForChat();
                    int trimmed = raw.length();
                    while (trimmed > 0 && raw.charAt(trimmed - 1) == ' ') {
                        trimmed--;
                    }
                    spanWidth -= minecraft.fontRenderer.getCharWidth(' ')
                            * (raw.length() - trimmed);
                }
                if (inSpan && band.localX >= cursor
                        && band.localX < cursor + spanWidth) {
                    return targetForGroup(lines, band.viewIndex);
                }
                if (closesSpan) {
                    identitySpan = false;
                }
                previousStart = cursor;
                previousText = decodedHead == null ? text : null;
                cursor += partWidth;
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
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
                || !appearance.isPresent()
                || appearance.getCharacterName().length() == 0;
        if (accountIdentity) {
            LostTalesCharacterHeadIconRenderer.rememberAccountSkin(
                    minecraft, playerId, account);
        }
        return new Target(playerId, accountIdentity,
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
                if (isReplyIdentity(part)) {
                    // The brackets answer to the pointer as the name
                    // does, but they are not it. The name is the one
                    // that follows the head: the opening bracket comes
                    // before it, the closing one after it is already
                    // read, so position tells them apart without
                    // anything having to look at the text.
                    if (afterHead && identity.length() == 0) {
                        identity = LostTalesChatVisualStyle.removeColorCodes(
                                part.getUnformattedTextForChat()).trim();
                        account = replyAccount(part);
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
            return new Target(marker.senderId, false, true, marker.skinId,
                    identity, "", "", marker.nameColor);
        }
        return account.length() == 0 ? null
                : new Target(marker.senderId, marker.accountIdentity,
                        marker.skinId, identity, title, account,
                        marker.nameColor);
    }

    private static boolean isReplyIdentity(IChatComponent part) {
        ClickEvent click = part == null || part.getChatStyle() == null
                ? null : part.getChatStyle().getChatClickEvent();
        return click != null
                && click.getAction() == ClickEvent.Action.SUGGEST_COMMAND
                && click.getValue() != null
                && click.getValue().startsWith("/msg ");
    }

    private static String replyAccount(IChatComponent part) {
        String value = part.getChatStyle().getChatClickEvent().getValue();
        String account = value.substring("/msg ".length()).trim();
        int space = account.indexOf(' ');
        return space < 0 ? account : account.substring(0, space);
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
        if (appearance == null || !appearance.isPresent()) {
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
                && LostTalesChatMessagePacket.DISCORD_SENDER_ID.equals(
                        target.playerId)) {
            // A Discord sender has no account head; the Discord mark
            // stands in, 1:1 — never scaled — centred in the head's box.
            float inset = (HEAD_SIZE - ChatEmoji.SPRITE_SIZE) / 2.0F;
            ChatEmojiRenderer.draw(minecraft, ChatEmoji.DISCORD,
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

    static boolean contains(float x, float y, float left, float top,
                            float right, float bottom) {
        return x >= Math.min(left, right) && x < Math.max(left, right)
                && y >= Math.min(top, bottom) && y < Math.max(top, bottom);
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

    private static final class Target {
        final UUID playerId;
        final boolean accountIdentity;
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

        Target(UUID playerId, boolean accountIdentity, String skinId,
               String identityName, String title, String accountName,
               int nameColor) {
            this(playerId, accountIdentity, false, skinId, identityName,
                    title, accountName, nameColor);
        }

        Target(UUID playerId, boolean accountIdentity, boolean npcIdentity,
               String skinId, String identityName, String title,
               String accountName, int nameColor) {
            this(playerId, accountIdentity, npcIdentity, skinId,
                    identityName, title, accountName, nameColor, null);
        }

        private Target(UUID playerId, boolean accountIdentity,
                       boolean npcIdentity, String skinId,
                       String identityName, String title,
                       String accountName, int nameColor,
                       ChatAccountRole role) {
            this.playerId = playerId;
            this.accountIdentity = accountIdentity;
            this.npcIdentity = npcIdentity;
            this.skinId = skinId == null ? "" : skinId;
            this.identityName = identityName;
            this.title = title;
            this.accountName = accountName;
            this.nameColor = nameColor;
            this.role = role;
        }

        static Target forRole(ChatAccountRole role) {
            return new Target(null, false, false, "", "", "", "",
                    role.getColor(), role);
        }
    }
}
