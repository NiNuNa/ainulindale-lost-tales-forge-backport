package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.registry.CharacterRaceGameplayProfile;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.client.character.ClientCharacterDisplayNames;
import com.ninuna.losttales.client.chat.ChatEmojiIcon;
import com.ninuna.losttales.client.chat.ClientChatProfanity;
import com.ninuna.losttales.client.character.ClientCharacterRaceAttributes;
import com.ninuna.losttales.client.quest.LostTalesClientQuestDefinitionStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.FoldRule;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiClip;
import com.ninuna.losttales.gui.style.LostTalesUiCutName;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiTheme;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestWords;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveSelection;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveTextHelper;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

/**
 * The words of a character's profile, one column like a
 * messenger's profile panel: the name, and under it what the character is
 * — played now, its race, its people — then its glances, About with
 * Appearance, Personality and History, the Facts (age, the six short
 * facts, sex and look), its Race and what the race gives, the Quests of
 * the one played, and the Account behind one of the player's own. Another
 * person's shows what this client knows of them. The profile's words
 * pass the profanity filter as this client has it set, as the chat's do.
 * A long History folds as a long chat message does ({@link FoldRule}),
 * its folded rows shown as far as the page has opened them. Laid out as
 * lines of known heights, so the column scrolls by whole lines and knows
 * how tall it is.
 */
final class CharacterProfileColumn {
    private static final int LINE = 10;
    private static final int HEADING = 15;
    /** A glance's line stands under its title, clear of the emoji. */
    private static final int GLANCE_INDENT = ChatEmojiIcon.SIZE + 3;
    /** Air above every section but the first. */
    private static final int SECTION_GAP = 6;
    /** The narrowest a label's column is; a longer label widens it. */
    private static final int LABEL_MIN_WIDTH = 74;
    /** How many tracked quests the Quests section names at most. */
    private static final int TRACKED_SHOWN = 5;

    /**
     * What a line is. A FOLDED line is a History row past the ones a fold
     * keeps; FOLD is the Read more or Show less under them.
     */
    private enum Kind { NAME, ASIDE, HEADING, LABEL, PAIR, TEXT, FOLDED, FOLD,
        GLANCE, GLANCE_LINE, GAP }

    /** One line: its kind, its words, and for a pair its value. */
    private static final class Line {
        final Kind kind;
        final String text;
        final String value;
        final int rgb;

        Line(Kind kind, String text, String value, int rgb) {
            this.kind = kind;
            this.text = text;
            this.value = value;
            this.rgb = rgb;
        }

        int height() {
            switch (this.kind) {
                case HEADING:
                    return HEADING;
                case GAP:
                    return SECTION_GAP;
                case NAME:
                case LABEL:
                case GLANCE:
                    return LINE + 2;
                default:
                    return LINE;
            }
        }
    }

    private final List<Line> lines = new ArrayList<Line>();
    /** Pixels of the History's folded rows shown: none while it is folded, all of them open. */
    private double revealed;

    /**
     * The profile of {@code subject}: {@code profile} as the server last
     * sent it, null while it is on its way or, with {@code unavailable},
     * when the server will not show it here. A long History says Show
     * less under it while {@code historyOpen}, else Read more.
     */
    CharacterProfileColumn(Minecraft minecraft, FontRenderer font, int width,
                           ProfileSubject subject, CharacterProfile profile,
                           boolean unavailable, String accountName,
                           boolean played, boolean lore,
                           boolean historyOpen) {
        String raceId = subject.raceId;
        add(Kind.NAME, subject.name, "", played
                ? LostTalesUiTheme.accentRgb() : LostTalesUiInk.IVORY);
        List<String> facts = new ArrayList<String>(4);
        if (played) {
            facts.add(I18n.format("gui.losttales.character.playing"));
        }
        if (lore) {
            facts.add(I18n.format("gui.losttales.character.lore"));
        }
        if (raceId.length() > 0) {
            facts.add(ClientCharacterDisplayNames.race(raceId));
        }
        if (subject.factionId.length() > 0) {
            facts.add(ClientCharacterDisplayNames.faction(subject.factionId));
        }
        add(Kind.ASIDE, join(facts), "", WindowStyle.asideRgb());

        if (profile != null) {
            addGlances(font, width, profile);
        }
        addAbout(font, width, subject, profile, unavailable, historyOpen);
        addFacts(subject, profile);

        if (raceId.length() > 0) {
            heading("gui.losttales.character.section.race");
            CharacterRaceGameplayProfile race = ClientCharacterRaceAttributes.resolve(
                    minecraft == null ? null : minecraft.theWorld, raceId);
            pair("gui.losttales.character.race",
                    ClientCharacterDisplayNames.race(raceId));
            pair("gui.losttales.character.attribute.health",
                    ClientCharacterRaceAttributes.formatHealth(race));
            pair("gui.losttales.character.attribute.movement_speed",
                    ClientCharacterRaceAttributes.formatMovementSpeed(race));
            pair("gui.losttales.character.attribute.attack_damage",
                    ClientCharacterRaceAttributes.formatAttackDamage(race));
            pair("gui.losttales.character.attribute.hitbox",
                    ClientCharacterRaceAttributes.formatHitbox(race));
            pair("gui.losttales.character.attribute.eye_height",
                    ClientCharacterRaceAttributes.formatEyeHeight(race));
        }

        if (played) {
            addQuests(font, width);
        }

        if (subject.own) {
            heading("gui.losttales.character.section.account");
            pair("gui.losttales.character.minecraft_account", accountName);
            if (played) {
                World world = minecraft == null ? null : minecraft.theWorld;
                pair("gui.losttales.character.location", dimensionName(world));
                pair("gui.losttales.character.world_time", worldTime(world));
            }
            wrapped(font, width, I18n.format(
                    "gui.losttales.character.shared_state_detail"),
                    WindowStyle.asideRgb());
        }
    }

    /** The glances at the profile's top: each one's emoji and title, and its line under them. */
    private void addGlances(FontRenderer font, int width,
                            CharacterProfile profile) {
        if (profile.glances().isEmpty()) {
            return;
        }
        add(Kind.GAP, "", "", 0);
        for (CharacterProfile.Glance glance : profile.glances()) {
            add(Kind.GLANCE, ClientChatProfanity.filter(glance.getTitle()),
                    glance.getEmoji(), LostTalesUiTheme.accentRgb());
            if (glance.getLine().length() > 0) {
                for (Object line : font.listFormattedStringToWidth(
                        ClientChatProfanity.filter(glance.getLine()),
                        Math.max(1, width - GLANCE_INDENT))) {
                    add(Kind.GLANCE_LINE, String.valueOf(line), "",
                            LostTalesUiInk.IVORY);
                }
            }
        }
    }

    /** About: Appearance, Personality and History, each under its name, or a word on why there is nothing. */
    private void addAbout(FontRenderer font, int width, ProfileSubject subject,
                          CharacterProfile profile, boolean unavailable,
                          boolean historyOpen) {
        heading("gui.losttales.character.section.about");
        if (profile == null) {
            wrapped(font, width, I18n.format(unavailable
                    ? "gui.losttales.character.profile.unavailable"
                    : "gui.losttales.character.profile.loading"),
                    WindowStyle.asideRgb());
            return;
        }
        boolean written = false;
        for (CharacterProfile.Section section : CharacterProfile.Section.values()) {
            String text = profile.section(section);
            if (text.length() == 0) {
                continue;
            }
            written = true;
            add(Kind.LABEL, I18n.format("gui.losttales.character.profile."
                    + section.getId()), "", LostTalesColors.rgb(
                            LostTalesColors.TEXT));
            if (section == CharacterProfile.Section.HISTORY) {
                addHistory(font, width, ClientChatProfanity.filter(text),
                        historyOpen);
            } else {
                wrapped(font, width, ClientChatProfanity.filter(text),
                        LostTalesUiInk.IVORY);
            }
        }
        if (!written) {
            wrapped(font, width, I18n.format(subject.own
                    ? "gui.losttales.character.profile.empty_own"
                    : "gui.losttales.character.profile.empty"),
                    WindowStyle.asideRgb());
        }
    }

    /**
     * The History's rows: past five rows the first four stand, the rest
     * wait under them as folded rows, and Read more or Show less stands
     * under all of them.
     */
    private void addHistory(FontRenderer font, int width, String text,
                            boolean open) {
        List<?> rows = font.listFormattedStringToWidth(text,
                Math.max(1, width));
        int kept = FoldRule.shownRows(rows.size(), false);
        for (int index = 0; index < rows.size(); index++) {
            add(index < kept ? Kind.TEXT : Kind.FOLDED,
                    String.valueOf(rows.get(index)), "", LostTalesUiInk.IVORY);
        }
        if (FoldRule.folds(rows.size())) {
            add(Kind.FOLD, I18n.format(FoldRule.toggleKey(open)), "",
                    WindowStyle.asideRgb());
        }
    }

    /** The Facts: the age, each short fact given, then the sex and the look where they are known. */
    private void addFacts(ProfileSubject subject, CharacterProfile profile) {
        heading("gui.losttales.character.profile.facts");
        if (subject.age > 0) {
            pair("gui.losttales.character.age", String.valueOf(subject.age));
        }
        if (profile != null) {
            for (CharacterProfile.Fact fact : CharacterProfile.Fact.values()) {
                String value = profile.fact(fact);
                if (value.length() > 0) {
                    pair("gui.losttales.character.profile.fact."
                            + fact.getId(), ClientChatProfanity.filter(value));
                }
            }
        }
        if (subject.genderId.length() > 0) {
            pair("gui.losttales.character.gender",
                    ClientCharacterDisplayNames.gender(subject.genderId));
        }
        if (subject.skinId.length() > 0 && subject.raceId.length() > 0) {
            pair("gui.losttales.character.profile.look",
                    ClientCharacterDisplayNames.skin(subject.skinId));
            if (ClientCharacterDisplayNames.hasBodyTypeChoice(subject.skinId)) {
                pair("gui.losttales.character.body",
                        ClientCharacterDisplayNames.bodyType(
                                subject.bodyTypeId));
            }
            if (ClientCharacterDisplayNames.hasChestChoice(subject.skinId)) {
                pair("gui.losttales.character.chest",
                        ClientCharacterDisplayNames.chestType(
                                subject.chestTypeId));
            }
        }
    }

    /** The quests of the one played: how many run, are tracked and are done, then what the tracked ones ask next. */
    private void addQuests(FontRenderer font, int width) {
        heading("gui.losttales.character.section.quests");
        Collection<LostTalesQuestProgress> active =
                LostTalesClientQuestProgressStore.getActiveQuests();
        Collection<LostTalesQuestProgress> tracked =
                LostTalesClientQuestProgressStore.getPinnedQuests();
        pair("gui.losttales.character.quests.active",
                String.valueOf(active.size()));
        pair("gui.losttales.character.quests.tracked",
                String.valueOf(tracked.size()));
        pair("gui.losttales.character.quests.completed",
                String.valueOf(LostTalesClientQuestProgressStore
                        .getCompletedQuestIds().size()));
        if (tracked.isEmpty()) {
            wrapped(font, width, I18n.format(
                    "gui.losttales.character.quests.none_tracked"),
                    WindowStyle.asideRgb());
            return;
        }
        int shown = 0;
        for (LostTalesQuestProgress progress : tracked) {
            if (shown++ >= TRACKED_SHOWN) {
                break;
            }
            LostTalesQuestDefinition quest = LostTalesClientQuestDefinitionStore
                    .getQuest(progress.getQuestId());
            add(Kind.TEXT, quest == null ? progress.getQuestId()
                    : LostTalesQuestWords.title(quest), "",
                    LostTalesUiTheme.accentRgb());
            wrapped(font, width, objectiveOf(quest, progress),
                    LostTalesUiInk.IVORY);
        }
    }

    /** What the quest asks next: its current stage's first objective not yet done. */
    private static String objectiveOf(LostTalesQuestDefinition quest,
                                      LostTalesQuestProgress progress) {
        LostTalesQuestStageDefinition stage = LostTalesQuestObjectiveSelection
                .getCurrentStage(quest, progress);
        if (stage == null || stage.getObjectives().isEmpty()) {
            return I18n.format("gui.losttales.character.quests.no_objective");
        }
        for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
            int target = LostTalesQuestObjectiveTextHelper
                    .getObjectiveTargetCount(objective);
            int current = progress == null ? 0
                    : progress.getObjectiveProgress(objective.getId());
            if (current < target) {
                return LostTalesQuestObjectiveTextHelper.buildObjectiveLine(
                        progress, objective, true, false);
            }
        }
        return LostTalesQuestObjectiveTextHelper.buildObjectiveLine(progress,
                stage.getObjectives().get(0), true, false);
    }

    private static String dimensionName(World world) {
        if (world == null || world.provider == null) {
            return I18n.format("gui.losttales.character.unknown");
        }
        try {
            return world.provider.getDimensionName();
        } catch (RuntimeException unnamed) {
            return I18n.format("gui.losttales.character.dimension",
                    Integer.valueOf(world.provider.dimensionId));
        }
    }

    /** The world's day and hour, the day starting at six in the morning as the game's does. */
    private static String worldTime(World world) {
        if (world == null) {
            return I18n.format("gui.losttales.character.unknown");
        }
        long total = world.getWorldTime();
        long day = total / 24000L + 1L;
        long timeOfDay = (total + 6000L) % 24000L;
        int hour = (int)(timeOfDay / 1000L);
        int minute = (int)((timeOfDay % 1000L) * 60L / 1000L);
        return I18n.format("gui.losttales.character.world_time_value",
                Long.valueOf(day), String.format("%02d:%02d",
                        Integer.valueOf(hour), Integer.valueOf(minute)));
    }

    private static String join(List<String> words) {
        StringBuilder joined = new StringBuilder();
        for (String word : words) {
            if (word.length() == 0) {
                continue;
            }
            joined.append(joined.length() == 0 ? "" : " - ").append(word);
        }
        return joined.toString();
    }

    private void add(Kind kind, String text, String value, int rgb) {
        this.lines.add(new Line(kind, text == null ? "" : text,
                value == null ? "" : value, rgb));
    }

    private void heading(String key) {
        add(Kind.GAP, "", "", 0);
        add(Kind.HEADING, LostTalesSkyrimUiStyle.uppercase(I18n.format(key)),
                "", LostTalesColors.rgb(LostTalesColors.TEXT));
    }

    private void pair(String labelKey, String value) {
        add(Kind.PAIR, I18n.format(labelKey), value, LostTalesUiInk.IVORY);
    }

    private void wrapped(FontRenderer font, int width, String text, int rgb) {
        for (Object line : font.listFormattedStringToWidth(text,
                Math.max(1, width))) {
            add(Kind.TEXT, String.valueOf(line), "", rgb);
        }
    }

    /** How tall the History's folded rows stand, all of them shown; 0 for a History that does not fold. */
    int foldedHeight() {
        int total = 0;
        for (Line line : this.lines) {
            if (line.kind == Kind.FOLDED) {
                total += line.height();
            }
        }
        return total;
    }

    /** Shows {@code pixels} of the History's folded rows; what stands under them stands that much lower. */
    void reveal(double pixels) {
        this.revealed = Math.max(0.0D, Math.min(foldedHeight(), pixels));
    }

    /** How tall the column is, as much of the History's folded rows as is shown. */
    int height() {
        int total = 0;
        for (Line line : this.lines) {
            if (line.kind != Kind.FOLDED) {
                total += line.height();
            }
        }
        return total + (int)Math.ceil(this.revealed);
    }

    /**
     * Where Read more or Show less stands for the column drawn from
     * {@code left}, {@code top}: its words' width and a line's height.
     * Null for a History that does not fold.
     */
    LostTalesUiHitBox foldBox(FontRenderer font, double left, double top) {
        double y = top;
        boolean passedFolded = false;
        for (Line line : this.lines) {
            if (line.kind == Kind.FOLDED) {
                if (!passedFolded) {
                    y += this.revealed;
                    passedFolded = true;
                }
                continue;
            }
            if (line.kind == Kind.FOLD) {
                return new LostTalesUiHitBox(left, y,
                        font.getStringWidth(line.text), line.height());
            }
            y += line.height();
        }
        return null;
    }

    /**
     * Draws the column from {@code left}, {@code top}, {@code width} wide,
     * at {@code alpha}. The History's folded rows are cut to as much of
     * them as is shown, and what stands under them is drawn that much
     * lower, its fraction of a pixel through the matrix.
     */
    void draw(FontRenderer font, int left, int top, int width, int alpha) {
        int y = top;
        int labelWidth = labelWidth(font, width);
        boolean lowered = false;
        try {
            int index = 0;
            while (index < this.lines.size()) {
                Line line = this.lines.get(index);
                if (line.kind == Kind.FOLDED) {
                    index = drawFolded(font, index, left, y, width, alpha);
                    int whole = (int)Math.floor(this.revealed);
                    y += whole;
                    if (!lowered) {
                        GL11.glPushMatrix();
                        lowered = true;
                        GL11.glTranslatef(0.0F,
                                (float)(this.revealed - whole), 0.0F);
                    }
                    continue;
                }
                drawLine(font, line, left, y, width, labelWidth, alpha);
                y += line.height();
                index++;
            }
        } finally {
            if (lowered) {
                GL11.glPopMatrix();
            }
        }
    }

    /**
     * The run of folded rows from {@code first}, from {@code top}, cut to
     * as much of them as is shown; answers the line after the run.
     */
    private int drawFolded(FontRenderer font, int first, int left, int top,
                           int width, int alpha) {
        int end = first;
        while (end < this.lines.size()
                && this.lines.get(end).kind == Kind.FOLDED) {
            end++;
        }
        if (this.revealed <= 0.0D) {
            return end;
        }
        boolean whole = this.revealed >= foldedHeight();
        // A text's shadow falls a pixel right; the cut leaves it room.
        boolean clipped = !whole && LostTalesUiClip.beginLocal(
                Minecraft.getMinecraft(), left, top, left + width + 1.0F,
                (float)(top + this.revealed));
        if (!whole && !clipped) {
            return end;
        }
        try {
            int y = top;
            for (int index = first; index < end && y < top + this.revealed;
                 index++) {
                Line line = this.lines.get(index);
                drawLine(font, line, left, y, width, 0, alpha);
                y += line.height();
            }
        } finally {
            LostTalesUiClip.end(clipped);
        }
        return end;
    }

    /**
     * One line from {@code y}; a pair's value stands at {@code labelWidth}.
     * Words too long for the column fade into its end (LOOK-37).
     */
    private void drawLine(FontRenderer font, Line line, int left, int y,
                          int width, int labelWidth, int alpha) {
        switch (line.kind) {
            case NAME:
            case ASIDE:
            case TEXT:
            case FOLDED:
            case FOLD:
                LostTalesUiCutName.drawText(font, line.text, left, y, width,
                        line.rgb, alpha);
                break;
            case LABEL:
                LostTalesUiCutName.drawText(font, line.text, left, y + 2,
                        width, line.rgb, alpha);
                break;
            case GLANCE:
                ChatEmojiIcon.draw(Minecraft.getMinecraft(),
                        ChatEmoji.fromName(line.value), left, y, alpha);
                LostTalesUiCutName.drawText(font, line.text,
                        left + GLANCE_INDENT, y + 1, width - GLANCE_INDENT,
                        line.rgb, alpha);
                break;
            case GLANCE_LINE:
                LostTalesUiInk.drawText(font, line.text,
                        left + GLANCE_INDENT, y, line.rgb, alpha);
                break;
            case HEADING: {
                int textTop = y + LostTalesUiInk.centredStart(HEADING, 7);
                LostTalesUiInk.drawText(font, line.text, left, textTop,
                        line.rgb, alpha);
                int ruleLeft = left + font.getStringWidth(line.text) + 5;
                if (ruleLeft < left + width) {
                    Gui.drawRect(ruleLeft, textTop + 3, left + width,
                            textTop + 4, LostTalesColors.BORDER_DIM);
                }
                break;
            }
            case PAIR:
                LostTalesUiInk.drawText(font, line.text, left, y,
                        WindowStyle.asideRgb(), alpha);
                LostTalesUiCutName.drawText(font, line.value,
                        left + labelWidth, y, width - labelWidth, line.rgb,
                        alpha);
                break;
            default:
                break;
        }
    }

    /** One column for every label: the widest of them, and a little air. */
    private int labelWidth(FontRenderer font, int width) {
        int widest = LABEL_MIN_WIDTH;
        for (Line line : this.lines) {
            if (line.kind == Kind.PAIR) {
                widest = Math.max(widest, font.getStringWidth(line.text) + 8);
            }
        }
        return Math.min(widest, Math.max(LABEL_MIN_WIDTH, width / 2));
    }
}
