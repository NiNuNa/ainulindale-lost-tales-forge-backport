package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.CharacterSlotState;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.character.sync.CharacterOperationFeedback;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.character.ClientCharacterDisplayNames;
import com.ninuna.losttales.client.character.ClientCharacterNetwork;
import com.ninuna.losttales.client.character.ClientCharacterProfileCache;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.character.ClientLoreCharacterCache;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.quest.LostTalesClientQuestDefinitionStore;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.client.window.BarItem;
import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.PageContent;
import com.ninuna.losttales.client.window.PageTab;
import com.ninuna.losttales.client.window.SubWindow;
import com.ninuna.losttales.client.window.ToolStrip;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowBar;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowMenus;
import com.ninuna.losttales.client.window.WindowPages;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiClip;
import com.ninuna.losttales.gui.style.LostTalesUiFlatLayers;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * The Characters tab (C1 a): the account's character and every slot in
 * a roster the tool strip's left button folds away, and the picked
 * character's profile beside it in one column (C2 a), its figure live
 * for the one played and posed from its look for any other (C3 a). The
 * window holds the rest: the well's search narrows the roster, and the
 * input bar holds Play as, Edit Profile, Capes and Delete, last in red —
 * Create in Play as's place for an empty slot. Edit Profile, Capes, the
 * lore characters and the question before a deletion open as sub-windows
 * in the tab's window; the creator stays a screen of its own. Another
 * person's profile, opened from their card or their menu (P5 a), stands
 * in the roster's place, read only, until Back.
 *
 * <p>It draws only the server's roster and never changes it on its own;
 * every request is checked again by the server.</p>
 */
public final class CharactersPage extends PageContent {
    /** The code name the page is registered and remembered under. */
    public static final String PAGE_ID = "characters";

    /** The item the tab wears: a head. */
    public static final ItemStack ICON = new ItemStack(Items.skull, 1, 3);

    private static final int ROW_HEIGHT = 14;
    private static final int HEADING_HEIGHT = 15;
    private static final int NOTE_LINE = 10;
    /** A head before a name, and its gap. */
    private static final int HEAD = 8;
    private static final int HEAD_GAP = 4;
    /** Nothing picked yet: the one played is picked as the roster arrives. */
    private static final int NOT_PICKED = Integer.MIN_VALUE;

    /** The roster's button at the strip's left end: the person. */
    private static final ToolStrip.Panel ROSTER_PANEL = new ToolStrip.Panel(
            LostTalesUiSheet.AREA, LostTalesUiSheet.AREA_HOVER,
            "gui.losttales.character.roster.show",
            "gui.losttales.character.roster.hide");

    /** The bar's items: their ids, which the page is told when one is pressed. */
    private static final String PLAY_AS = "play_as";
    private static final String CREATE = "create";
    private static final String EDIT = "edit";
    private static final String CAPES = "capes";
    private static final String DELETE = "delete";
    private static final String REFRESH = "refresh";
    private static final String BACK = "back";

    /** Another person's character, shown read only in the roster's place. */
    public static final class Visit {
        final UUID playerId;
        final UUID characterId;
        final String name;
        final String skinId;

        public Visit(UUID playerId, UUID characterId, String name,
                     String skinId) {
            this.playerId = playerId;
            this.characterId = characterId;
            this.name = name == null ? "" : name;
            this.skinId = skinId == null ? "" : skinId;
        }
    }

    private final Minecraft mc = Minecraft.getMinecraft();
    private final CharacterFigureStage figure = new CharacterFigureStage();
    private FontRenderer font;
    private int width = -1;
    private int height = -1;
    private boolean rosterOut = true;
    private int pickedSlot = NOT_PICKED;
    /** Where the roster and the profile were scrolled to, and where they stand on screen, gliding there. */
    private int rosterScroll;
    private int profileScroll;
    private double shownRosterScroll;
    private double shownProfileScroll;
    private long glideNanos;
    /** The seconds since the last frame, which every glide of this frame takes. */
    private double frameSeconds;
    private CharacterRosterRows.Row hovered;
    /** The words in the window's well; empty while its search is closed. */
    private String query = "";
    private int pendingRequestId;
    private int rosterRequestId;
    private String statusMessage = "";
    private boolean statusError;
    /** Whether the figure is held and turning. */
    private boolean holdingFigure;
    /** The person whose profile stands in the roster's place; null for the player's own. */
    private Visit visit;

    /** The page's content on the tab as registered; null while it is not registered. */
    static CharactersPage current() {
        WindowPages.Page page = WindowPages.byId(PAGE_ID);
        PageContent content = page == null ? null : page.content();
        return content instanceof CharactersPage ? (CharactersPage)content
                : null;
    }

    /* ---- The roster and the pick ---- */

    private static CharacterRosterSnapshot snapshot() {
        return ClientCharacterRosterCache.getState()
                == ClientCharacterRosterCache.SyncState.READY
                ? ClientCharacterRosterCache.getSnapshot() : null;
    }

    private String accountName() {
        return this.mc.thePlayer == null
                ? I18n.format("gui.losttales.character.unknown")
                : this.mc.thePlayer.getCommandSenderName();
    }

    private List<CharacterRosterRows.Row> rows(CharacterRosterSnapshot snapshot) {
        return CharacterRosterRows.of(snapshot, accountName(), this.query);
    }

    /** The row picked, the one played until another is. */
    private CharacterRosterRows.Row picked(CharacterRosterSnapshot snapshot,
                                           List<CharacterRosterRows.Row> rows) {
        CharacterRosterRows.Row row = CharacterRosterRows.atSlot(rows,
                this.pickedSlot);
        if (row == null && snapshot != null && this.query.length() == 0) {
            row = CharacterRosterRows.played(snapshot, rows);
            if (row != null) {
                pick(row.slot);
            }
        }
        return row;
    }

    private void pick(int slot) {
        if (slot != this.pickedSlot) {
            this.pickedSlot = slot;
            this.profileScroll = 0;
            this.shownProfileScroll = 0.0D;
            this.figure.reset();
        }
    }

    /** The slot a lore character is claimed into: the empty one picked, else the first empty one; -1 for none. */
    int claimSlot() {
        CharacterRosterSnapshot snapshot = snapshot();
        if (snapshot != null && CharacterRoster.isCreatableSlotIndex(
                this.pickedSlot) && snapshot.getCharacterAtSlot(
                        this.pickedSlot) == null
                && snapshot.getSlotState(this.pickedSlot)
                        == CharacterSlotState.UNLOCKED) {
            return this.pickedSlot;
        }
        return CharacterRosterRows.firstEmptySlot(snapshot);
    }

    /**
     * Opens another person's character in the Characters tab, read only,
     * in the roster's place, and brings the tab forward; one of this
     * player's own is picked in the roster instead.
     */
    public static void visit(Visit visit) {
        CharactersPage page = current();
        if (page == null || visit == null || visit.characterId == null) {
            return;
        }
        page.startVisit(visit);
        WindowScreen.openPage(PAGE_ID);
    }

    private void startVisit(Visit visit) {
        UUID self = this.mc.thePlayer == null ? null
                : this.mc.thePlayer.getUniqueID();
        if (visit.playerId != null && visit.playerId.equals(self)) {
            CharacterRosterSnapshot snapshot =
                    ClientCharacterRosterCache.getSnapshot();
            CharacterSummary own = snapshot == null ? null
                    : snapshot.getCharacter(visit.characterId);
            this.visit = null;
            if (own != null) {
                this.query = "";
                pick(own.getSlotIndex());
            }
            return;
        }
        this.visit = visit;
        this.profileScroll = 0;
        this.shownProfileScroll = 0.0D;
        this.figure.reset();
        ClientCharacterProfileCache.refresh(visit.characterId);
    }

    private void endVisit() {
        if (this.visit != null) {
            this.visit = null;
            this.profileScroll = 0;
            this.shownProfileScroll = 0.0D;
            this.figure.reset();
        }
    }

    private static boolean isLore(CharacterSummary character) {
        return character != null && ClientLoreCharacterCache
                .findOwnedCharacter(character.getCharacterId()) != null;
    }

    /* ---- Requests ---- */

    /** Follows a request to the server: its answer stands at the page's foot. */
    void track(int requestId, String workingKey) {
        this.pendingRequestId = requestId;
        this.statusMessage = workingKey == null ? ""
                : I18n.format(workingKey);
        this.statusError = false;
    }

    /** Whether a request is on its way; everything that would send another waits. */
    boolean isPending() {
        return this.pendingRequestId != 0
                && ClientCharacterRosterCache.isRequestPending(
                        this.pendingRequestId);
    }

    @Override
    public void tick() {
        if (this.pendingRequestId == 0
                || ClientCharacterRosterCache.isRequestPending(
                        this.pendingRequestId)) {
            return;
        }
        int completed = this.pendingRequestId;
        this.pendingRequestId = 0;
        CharacterOperationFeedback feedback =
                ClientCharacterRosterCache.getOperation(completed);
        if (feedback == null) {
            this.statusMessage = "";
            return;
        }
        ClientCharacterRosterCache.clearOperation(completed);
        this.statusError = !feedback.isSuccessful();
        this.statusMessage = feedback.isSuccessful()
                ? ClientCharacterDisplayNames.operationSuccess(
                        feedback.getOperationType().getId())
                : ClientCharacterDisplayNames.error(feedback);
    }

    /** Asks the server for the roster where none is known, or its last answer failed. */
    private void requestRosterIfNeeded() {
        ClientCharacterRosterCache.SyncState state =
                ClientCharacterRosterCache.getState();
        boolean asked = this.rosterRequestId != 0
                && ClientCharacterRosterCache.isRequestPending(
                        this.rosterRequestId);
        if (!asked && (state == ClientCharacterRosterCache.SyncState.UNKNOWN
                || (state == ClientCharacterRosterCache.SyncState.ERROR
                        && this.rosterRequestId == 0))) {
            this.rosterRequestId = ClientCharacterNetwork.requestRoster();
        }
    }

    private void refresh() {
        this.statusMessage = "";
        this.rosterRequestId = ClientCharacterNetwork.requestRoster();
    }

    /* ---- Drawing ---- */

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     float partialTicks, int alpha) {
        this.font = minecraft.fontRenderer;
        this.width = (int)Math.floor(box.width);
        this.height = (int)Math.floor(box.height);
        LostTalesClientQuestDefinitionStore.ensureLoaded(
                minecraft.getResourceManager());
        requestRosterIfNeeded();
        long now = System.nanoTime();
        this.frameSeconds = this.glideNanos == 0L ? 0.0D
                : (now - this.glideNanos) / 1.0E9D;
        this.glideNanos = now;
        int mouseX = pageX(box, pointerX);
        int mouseY = pageY(box, pointerY);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef((float)box.left, (float)box.top, 0.0F);
            drawPage(clipX, clipY, mouseX, mouseY, alpha);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** A screen x in the page's own space; far off for a pointer away. */
    private static int pageX(LostTalesUiHitBox box, double x) {
        return Double.isNaN(x) ? Integer.MIN_VALUE / 2
                : (int)Math.floor(x - box.left);
    }

    private static int pageY(LostTalesUiHitBox box, double y) {
        return Double.isNaN(y) ? Integer.MIN_VALUE / 2
                : (int)Math.floor(y - box.top);
    }

    private CharactersLayout layout() {
        return new CharactersLayout(this.width, this.height,
                this.rosterOut && this.visit == null);
    }

    private void drawPage(double clipX, double clipY, int mouseX, int mouseY,
                          int alpha) {
        CharacterRosterSnapshot snapshot = snapshot();
        if (snapshot == null) {
            drawCentred(I18n.format(ClientCharacterRosterCache.getState()
                    == ClientCharacterRosterCache.SyncState.ERROR
                    ? "gui.losttales.character.sync_failed_detail"
                    : "gui.losttales.character.loading_detail"), alpha);
            return;
        }
        CharactersLayout layout = layout();
        List<CharacterRosterRows.Row> rows = rows(snapshot);
        CharacterRosterRows.Row picked = picked(snapshot, rows);
        LostTalesUiHitBox roster = layout.roster();
        if (roster.width > 0) {
            clampRosterScroll(rows, roster);
            this.hovered = rowAt(rows, roster, mouseX, mouseY);
            drawRoster(snapshot, rows, picked, roster, alpha);
        } else {
            this.hovered = null;
        }
        LostTalesUiHitBox divider = layout.divider();
        if (divider.width > 0) {
            Gui.drawRect((int)divider.left, (int)divider.top,
                    (int)divider.right(), (int)divider.bottom(),
                    LostTalesColors.BORDER_DIM);
        }
        if (layout.profile().width > 0) {
            if (this.visit != null) {
                drawVisit(layout, clipX, clipY, alpha);
            } else {
                drawProfile(snapshot, picked, layout, clipX, clipY, alpha);
            }
        }
        if (this.statusMessage.length() > 0) {
            LostTalesUiInk.drawText(this.font, this.font.trimStringToWidth(
                            this.statusMessage, this.width
                                    - 2 * CharactersLayout.MARGIN),
                    CharactersLayout.MARGIN, this.height
                            - CharactersLayout.MARGIN - NOTE_LINE + 2,
                    this.statusError ? LostTalesColors.rgb(LostTalesColors.RED)
                            : WindowStyle.asideRgb(), alpha);
        }
    }

    /** A message alone in the middle of the page, in the aside tone. */
    private void drawCentred(String text, int alpha) {
        List<?> lines = this.font.listFormattedStringToWidth(text,
                Math.max(1, this.width - 2 * CharactersLayout.MARGIN));
        int y = (this.height - lines.size() * NOTE_LINE) / 2;
        for (Object line : lines) {
            String each = String.valueOf(line);
            LostTalesUiInk.drawText(this.font, each,
                    (this.width - this.font.getStringWidth(each)) / 2, y,
                    WindowStyle.asideRgb(), alpha);
            y += NOTE_LINE;
        }
    }

    /* ---- The roster ---- */

    private static int rowHeight(CharacterRosterRows.Row row) {
        return row.kind == CharacterRosterRows.Kind.HEADING ? HEADING_HEIGHT
                : ROW_HEIGHT;
    }

    private static int contentHeight(List<CharacterRosterRows.Row> rows) {
        int total = 0;
        for (CharacterRosterRows.Row row : rows) {
            total += rowHeight(row);
        }
        return total;
    }

    /** Keeps the roster's scroll within its rows, and glides the drawn one after it. */
    private void clampRosterScroll(List<CharacterRosterRows.Row> rows,
                                   LostTalesUiHitBox roster) {
        int most = Math.max(0, contentHeight(rows) - rosterHeight(roster));
        this.rosterScroll = Math.max(0, Math.min(this.rosterScroll, most));
        this.shownRosterScroll = glide(this.shownRosterScroll,
                this.rosterScroll);
    }

    private double glide(double shown, int target) {
        return Motions.followTravel(MotionIds.SCREEN_CHARACTERS_GLIDE, shown,
                target, this.frameSeconds);
    }

    /** The whole pixels a scroll stands at; its fraction is drawn through the matrix. */
    private static int whole(double scroll) {
        return (int)Math.floor(scroll);
    }

    /** The room the roster's rows have: its box less the status line. */
    private static int rosterHeight(LostTalesUiHitBox roster) {
        return Math.max(ROW_HEIGHT, (int)roster.height - NOTE_LINE);
    }

    private void drawRoster(CharacterRosterSnapshot snapshot,
                            List<CharacterRosterRows.Row> rows,
                            CharacterRosterRows.Row picked,
                            LostTalesUiHitBox roster, int alpha) {
        int top = (int)roster.top;
        boolean clipped = LostTalesUiClip.beginLocal(this.mc,
                (float)roster.left - 2, top, (float)roster.right(),
                top + rosterHeight(roster));
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0.0F, (float)(whole(this.shownRosterScroll)
                    - this.shownRosterScroll), 0.0F);
            int y = top - whole(this.shownRosterScroll);
            for (CharacterRosterRows.Row row : rows) {
                drawRow(snapshot, row, roster, y, row == picked,
                        row == this.hovered, alpha);
                y += rowHeight(row);
            }
        } finally {
            GL11.glPopMatrix();
            LostTalesUiClip.end(clipped);
        }
    }

    private void drawRow(CharacterRosterSnapshot snapshot,
                         CharacterRosterRows.Row row, LostTalesUiHitBox roster,
                         int y, boolean picked, boolean hovered, int alpha) {
        int left = (int)roster.left;
        int right = (int)roster.right();
        if (row.kind == CharacterRosterRows.Kind.HEADING) {
            String name = LostTalesSkyrimUiStyle.uppercase(I18n.format(
                    "gui.losttales.character.heading.slots",
                    Integer.valueOf(CharacterRosterRows.filledSlots(snapshot)),
                    Integer.valueOf(snapshot.getUnlockedSlotCount())));
            int textTop = y + LostTalesUiInk.centredStart(HEADING_HEIGHT, 7);
            LostTalesUiInk.drawText(this.font, name, left, textTop,
                    LostTalesColors.rgb(LostTalesColors.TEXT), alpha);
            int ruleLeft = left + this.font.getStringWidth(name) + 5;
            if (ruleLeft < right) {
                Gui.drawRect(ruleLeft, textTop + 3, right, textTop + 4,
                        LostTalesColors.BORDER_DIM);
            }
            return;
        }
        if (picked || hovered) {
            Gui.drawRect(left - 2, y, right, y + ROW_HEIGHT, picked
                    ? LostTalesColors.withAlpha(LostTalesColors.PLUM_GRAY, 0xB4)
                    : LostTalesColors.withAlpha(LostTalesColors.PLUM_DARK, 0x72));
        }
        int textTop = y + LostTalesUiInk.centredStart(ROW_HEIGHT, 7);
        int nameX = left + HEAD + HEAD_GAP;
        if (row.kind == CharacterRosterRows.Kind.EMPTY
                || row.kind == CharacterRosterRows.Kind.LORE) {
            LostTalesUiSheet sprite = row.kind == CharacterRosterRows.Kind.EMPTY
                    ? (hovered ? LostTalesUiSheet.PLUS_HOVER : LostTalesUiSheet.PLUS)
                    : (hovered ? LostTalesUiSheet.MEMBERS_HOVER
                            : LostTalesUiSheet.MEMBERS);
            LostTalesUiInk.beginContent();
            sprite.drawWithShadow(left + LostTalesUiInk.centredStart(HEAD,
                    sprite.getWidth()), textTop + Math.floorDiv(
                            LostTalesUiInk.CAP_HEIGHT - sprite.getHeight(), 2),
                    alpha);
            String words = I18n.format(row.kind == CharacterRosterRows.Kind.EMPTY
                    ? "gui.losttales.character.create"
                    : "gui.losttales.lore.open");
            LostTalesUiInk.drawText(this.font, "§o" + this.font
                            .trimStringToWidth(words, Math.max(0, right - nameX)),
                    nameX, textTop, WindowStyle.asideRgb(), alpha);
            return;
        }
        drawHead(row, left, textTop + LostTalesUiInk.CAP_HEIGHT / 2 - HEAD / 2,
                alpha);
        boolean played = CharacterRosterRows.isPlayed(snapshot, row);
        String name = row.character == null ? accountName()
                : row.character.getName();
        String aside = asideOf(row, played);
        int asideWidth = aside.length() == 0 ? 0
                : this.font.getStringWidth(aside) + 4;
        LostTalesUiInk.drawText(this.font, this.font.trimStringToWidth(name,
                        Math.max(0, right - nameX - asideWidth)), nameX,
                textTop, played ? LostTalesColors.rgb(LostTalesColors.HONEY)
                        : picked ? LostTalesUiInk.IVORY
                        : LostTalesColors.rgb(LostTalesColors.TEXT), alpha);
        if (aside.length() > 0) {
            LostTalesUiInk.drawText(this.font, aside,
                    right - this.font.getStringWidth(aside), textTop,
                    WindowStyle.asideRgb(), alpha);
        }
    }

    /** What stands at a row's right: the account's own, a lore character, the one played. */
    private static String asideOf(CharacterRosterRows.Row row, boolean played) {
        List<String> words = new ArrayList<String>(2);
        if (row.kind == CharacterRosterRows.Kind.ACCOUNT) {
            words.add(I18n.format("gui.losttales.character.account_tile"));
        } else if (isLore(row.character)) {
            words.add(I18n.format("gui.losttales.character.lore"));
        }
        if (played) {
            words.add(I18n.format("gui.losttales.character.playing"));
        }
        StringBuilder aside = new StringBuilder();
        for (String word : words) {
            aside.append(aside.length() == 0 ? "" : ", ").append(word);
        }
        return aside.toString();
    }

    /** A row's head, eight pixels, fading as one picture with its window. */
    private void drawHead(CharacterRosterRows.Row row, final float x,
                          final float y, int alpha) {
        final UUID owner = this.mc.thePlayer == null ? null
                : this.mc.thePlayer.getUniqueID();
        if (owner == null) {
            return;
        }
        final String skinId = row.character == null ? ""
                : row.character.getSkinId();
        final float opacity = alpha / 255.0F;
        final Minecraft minecraft = this.mc;
        LostTalesUiFlatLayers.draw(alpha, x - 1.0F, y - 1.0F, x + HEAD + 2.0F,
                y + HEAD + 2.0F, new LostTalesUiFlatLayers.Layers() {
                    @Override
                    public void draw() {
                        if (skinId.length() == 0) {
                            LostTalesCharacterHeadIconRenderer.drawAccountHead(
                                    minecraft, owner, x, y, HEAD, 1.0F,
                                    opacity);
                        } else {
                            LostTalesCharacterHeadIconRenderer.drawSnapshotHead(
                                    minecraft, owner, skinId, x, y, HEAD, 1.0F,
                                    opacity);
                        }
                    }
                });
    }

    /** The row under a point in the page's own space; null off every row that is picked. */
    private CharacterRosterRows.Row rowAt(List<CharacterRosterRows.Row> rows,
                                          LostTalesUiHitBox roster, int x,
                                          int y) {
        if (x < roster.left - 2 || x >= roster.right() || y < roster.top
                || y >= roster.top + rosterHeight(roster)) {
            return null;
        }
        int top = (int)roster.top
                - (int)Math.round(this.shownRosterScroll);
        for (CharacterRosterRows.Row row : rows) {
            int rowHeight = rowHeight(row);
            if (y >= top && y < top + rowHeight) {
                return row.kind == CharacterRosterRows.Kind.HEADING ? null
                        : row;
            }
            top += rowHeight;
        }
        return null;
    }

    /* ---- The profile ---- */

    private void drawProfile(CharacterRosterSnapshot snapshot,
                             CharacterRosterRows.Row picked,
                             CharactersLayout layout, double clipX,
                             double clipY, int alpha) {
        LostTalesUiHitBox profile = layout.profile();
        if (picked == null) {
            drawNote(profile, I18n.format(this.query.length() > 0
                    ? "gui.losttales.character.search.none"
                    : "gui.losttales.character.pick"), alpha);
            return;
        }
        if (picked.kind == CharacterRosterRows.Kind.EMPTY) {
            drawNote(profile, I18n.format("gui.losttales.character.empty_detail"),
                    alpha);
            return;
        }
        boolean played = CharacterRosterRows.isPlayed(snapshot, picked);
        LostTalesUiHitBox words = layout.words();
        CharacterSummary character = picked.character;
        UUID characterId = character == null ? null
                : character.getCharacterId();
        ClientCharacterProfileCache.want(characterId);
        CharacterProfileColumn column = new CharacterProfileColumn(this.mc,
                this.font, (int)words.width, character == null
                        ? ProfileSubject.account(accountName())
                        : ProfileSubject.of(character),
                ClientCharacterProfileCache.get(characterId),
                ClientCharacterProfileCache.isUnavailable(characterId),
                accountName(), played, isLore(character));
        drawColumn(layout, column, played ? this.mc.thePlayer : null,
                snapshot.getOwnerId(), lookOf(snapshot, character), clipX,
                clipY, alpha);
    }

    /**
     * The person visited: their character's profile as their appearance
     * shows it when it is the one they play, else by its name alone, and
     * its figure posed from that appearance where there is one.
     */
    private void drawVisit(CharactersLayout layout, double clipX, double clipY,
                           int alpha) {
        CharacterAppearance look = ClientCharacterAppearanceCache
                .getAuthoritative(this.visit.playerId);
        boolean known = look != null
                && this.visit.characterId.equals(look.getCharacterId());
        ClientCharacterProfileCache.want(this.visit.characterId);
        CharacterProfile profile = ClientCharacterProfileCache.get(
                this.visit.characterId);
        CharacterProfileColumn column = new CharacterProfileColumn(this.mc,
                this.font, (int)layout.words().width, known
                        ? ProfileSubject.of(look)
                        : ProfileSubject.named(this.visit.characterId,
                                this.visit.name, this.visit.skinId),
                profile, ClientCharacterProfileCache.isUnavailable(
                        this.visit.characterId), "", false, false);
        drawColumn(layout, column, null, this.visit.playerId,
                known ? look : null, clipX, clipY, alpha);
    }

    /** The profile's words and its figure in the profile's box, scrolled as one. */
    private void drawColumn(CharactersLayout layout,
                            CharacterProfileColumn column,
                            EntityLivingBase entity,
                            UUID ownerId, CharacterAppearance look,
                            double clipX, double clipY, int alpha) {
        LostTalesUiHitBox profile = layout.profile();
        LostTalesUiHitBox words = layout.words();
        int most = Math.max(0, layout.wordsOffset() + column.height()
                - (int)profile.height);
        this.profileScroll = Math.max(0, Math.min(this.profileScroll, most));
        this.shownProfileScroll = glide(this.shownProfileScroll,
                this.profileScroll);
        int scrolled = whole(this.shownProfileScroll);
        float fraction = (float)(this.shownProfileScroll - scrolled);
        boolean clipped = LostTalesUiClip.beginLocal(this.mc,
                (float)profile.left, (float)profile.top,
                (float)profile.right(), (float)profile.bottom());
        try {
            // A figure beside the words stands still; above them it
            // scrolls with them.
            GL11.glPushMatrix();
            try {
                if (!layout.figureBeside()) {
                    GL11.glTranslatef(0.0F, -fraction, 0.0F);
                }
                this.figure.draw(this.mc, layout.figure(scrolled), clipX,
                        clipY, entity, ownerId, look, alpha);
            } finally {
                GL11.glPopMatrix();
            }
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(0.0F, -fraction, 0.0F);
                column.draw(this.font, (int)words.left, (int)words.top
                        + layout.wordsOffset() - scrolled, (int)words.width,
                        alpha);
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            LostTalesUiClip.end(clipped);
        }
    }

    /** A character's look, for the figure posed from it. */
    private static CharacterAppearance lookOf(CharacterRosterSnapshot snapshot,
                                              CharacterSummary character) {
        return character == null ? null : CharacterAppearance.preview(
                snapshot.getOwnerId(), character.getRaceId(),
                character.getGenderId(), character.getSkinId(),
                character.getBodyTypeId(), character.getChestTypeId(),
                character.isMinecraftCapeVisible(),
                character.getCosmeticCapeId());
    }

    private void drawNote(LostTalesUiHitBox box, String text, int alpha) {
        int y = (int)box.top;
        for (Object line : this.font.listFormattedStringToWidth(text,
                Math.max(1, (int)box.width))) {
            LostTalesUiInk.drawText(this.font, String.valueOf(line),
                    (int)box.left, y, WindowStyle.asideRgb(), alpha);
            y += NOTE_LINE;
        }
    }

    /* ---- The pointer ---- */

    @Override
    public boolean mousePressed(Minecraft minecraft, LostTalesUiHitBox box,
                                double x, double y, int button) {
        if (this.width < 0 || button != 0) {
            return false;
        }
        int pageX = pageX(box, x);
        int pageY = pageY(box, y);
        CharacterRosterSnapshot snapshot = snapshot();
        CharactersLayout layout = layout();
        CharacterRosterRows.Row row = snapshot == null ? null
                : rowAt(rows(snapshot), layout.roster(), pageX, pageY);
        if (row != null) {
            if (row.kind == CharacterRosterRows.Kind.LORE) {
                openLoreCharacters();
            } else {
                pick(row.slot);
            }
            return true;
        }
        if (layout.figure(whole(this.shownProfileScroll)).contains(pageX, pageY)
                && layout.profile().contains(pageX, pageY)) {
            this.holdingFigure = true;
            this.figure.grab(pageX, pageY);
            return true;
        }
        return false;
    }

    @Override
    public void mouseDragged(Minecraft minecraft, LostTalesUiHitBox box,
                             double x, double y, int button) {
        if (this.holdingFigure) {
            this.figure.drag(pageX(box, x), pageY(box, y));
        }
    }

    @Override
    public void mouseReleased(Minecraft minecraft, LostTalesUiHitBox box,
                              double x, double y, int button) {
        this.holdingFigure = false;
        this.figure.release();
    }

    @Override
    public boolean acts(LostTalesUiHitBox box, double x, double y) {
        if (this.width < 0) {
            return false;
        }
        int pageX = pageX(box, x);
        int pageY = pageY(box, y);
        CharactersLayout layout = layout();
        CharacterRosterSnapshot snapshot = snapshot();
        return snapshot != null && (rowAt(rows(snapshot), layout.roster(),
                pageX, pageY) != null || layout.figure(whole(this.shownProfileScroll))
                        .contains(pageX, pageY));
    }

    /** The wheel zooms the figure over it; anywhere else it scrolls what is under it. */
    @Override
    public boolean scroll(LostTalesUiHitBox box, double x, double y,
                          int lines) {
        if (lines == 0 || this.width < 0) {
            return false;
        }
        int pageX = pageX(box, x);
        int pageY = pageY(box, y);
        CharactersLayout layout = layout();
        if (layout.roster().contains(pageX, pageY)) {
            this.rosterScroll += lines * ROW_HEIGHT;
            return true;
        }
        if (layout.figure(whole(this.shownProfileScroll)).contains(pageX, pageY)
                && layout.figureBeside()) {
            this.figure.zoomBy(-lines);
            return true;
        }
        if (layout.profile().contains(pageX, pageY)) {
            this.profileScroll = Math.max(0,
                    this.profileScroll + lines * NOTE_LINE);
            return true;
        }
        return false;
    }

    /* ---- The keys ---- */

    /** The arrows walk the roster; R asks the server again. */
    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (this.width < 0) {
            return false;
        }
        if (keyCode == Keyboard.KEY_R) {
            refresh();
            return true;
        }
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
            walkRows(keyCode == Keyboard.KEY_UP ? -1 : 1);
            return true;
        }
        return false;
    }

    /** Moves the pick to the row before or after it among those shown. */
    private void walkRows(int step) {
        CharacterRosterSnapshot snapshot = snapshot();
        List<CharacterRosterRows.Row> pickable =
                new ArrayList<CharacterRosterRows.Row>();
        for (CharacterRosterRows.Row row : rows(snapshot)) {
            if (row.pickable()) {
                pickable.add(row);
            }
        }
        if (pickable.isEmpty()) {
            return;
        }
        int at = pickable.indexOf(CharacterRosterRows.atSlot(pickable,
                this.pickedSlot));
        int next = at < 0 ? 0 : Math.max(0, Math.min(pickable.size() - 1,
                at + step));
        pick(pickable.get(next).slot);
    }

    /* ---- The window's strip ---- */

    @Override
    public ToolStrip.Panel panel() {
        return ROSTER_PANEL;
    }

    @Override
    public boolean isPanelOut() {
        return this.rosterOut && this.visit == null;
    }

    /** The roster's button brings the roster back from a visit, else folds it away or out. */
    @Override
    public void togglePanel() {
        if (this.visit != null) {
            endVisit();
            this.rosterOut = true;
            return;
        }
        this.rosterOut = !this.rosterOut;
    }

    @Override
    public String searchPrompt() {
        return I18n.format("gui.losttales.character.search");
    }

    /** New words read the roster from its top, the first character found picked. */
    @Override
    public void search(String words) {
        String typed = words == null ? "" : words.trim();
        if (typed.equals(this.query)) {
            return;
        }
        this.query = typed;
        this.rosterScroll = 0;
        if (typed.length() > 0) {
            endVisit();
            for (CharacterRosterRows.Row row : rows(snapshot())) {
                if (row.pickable()) {
                    pick(row.slot);
                    break;
                }
            }
        }
    }

    @Override
    public int found() {
        return this.query.length() == 0 ? -1
                : CharacterRosterRows.found(rows(snapshot()));
    }

    /** The arrows walk the characters found; Return gives the page the keys. */
    @Override
    public boolean searchKey(int keyCode) {
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
            walkRows(keyCode == Keyboard.KEY_UP ? -1 : 1);
            return true;
        }
        return keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER;
    }

    /* ---- The quick switcher ---- */

    @Override
    public String findHeading() {
        return "gui.losttales.character.find";
    }

    /** The roster's characters whose names hold the words, each's race beside it. */
    @Override
    public List<MenuWindow.Entry> find(String words) {
        List<MenuWindow.Entry> found = new ArrayList<MenuWindow.Entry>();
        for (CharacterRosterRows.Row row : CharacterRosterRows.of(snapshot(),
                accountName(), words)) {
            if (row.kind == CharacterRosterRows.Kind.ACCOUNT
                    || row.kind == CharacterRosterRows.Kind.CHARACTER) {
                found.add(new MenuWindow.Entry(String.valueOf(row.slot),
                        row.character == null ? accountName()
                                : row.character.getName()).withValue(
                        ClientCharacterDisplayNames.race(
                                row.character == null
                                        ? CharacterRaceRegistry.HUMAN
                                        : row.character.getRaceId())));
            }
        }
        return found;
    }

    /** The character found is picked, its profile beside the roster. */
    @Override
    public void show(String id) {
        endVisit();
        try {
            pick(Integer.parseInt(id));
        } catch (NumberFormatException unreadable) {
            // A row this page never made names no slot.
        }
    }

    /* ---- The window's bar ---- */

    /**
     * Play as, lit for the one played — Create for an empty slot — then
     * Edit Profile, Capes, and Delete last in red. Each is there whatever
     * is picked, greyed with the reason where it cannot be taken. While
     * the roster is not known, Refresh alone.
     */
    @Override
    public List<BarItem> barItems() {
        List<BarItem> items = new ArrayList<BarItem>(5);
        if (this.visit != null) {
            // Another person's profile is read only: the way back is all.
            String back = I18n.format("gui.losttales.character.profile.back");
            items.add(BarItem.button(BACK, back, LostTalesUiSheet.AREA,
                    LostTalesUiSheet.AREA_HOVER).tip(back));
            return items;
        }
        CharacterRosterSnapshot snapshot = snapshot();
        String busy = isPending()
                ? I18n.format("gui.losttales.character.working") : "";
        if (snapshot == null) {
            String refresh = I18n.format("gui.losttales.character.refresh");
            items.add(orBusy(BarItem.button(REFRESH, refresh,
                    new ItemStack(Items.clock))
                    .tip(WindowBar.withKey(refresh, Keyboard.KEY_R)), busy));
            return items;
        }
        CharacterRosterRows.Row picked = picked(snapshot, rows(snapshot));
        CharacterSummary character = picked == null ? null : picked.character;
        boolean played = CharacterRosterRows.isPlayed(snapshot, picked);
        boolean lore = isLore(character);
        String name = picked == null ? ""
                : character == null ? accountName() : character.getName();
        if (picked != null && picked.kind == CharacterRosterRows.Kind.EMPTY) {
            String create = I18n.format("gui.losttales.character.create");
            items.add(orBusy(BarItem.button(CREATE, create,
                    LostTalesUiSheet.PLUS, LostTalesUiSheet.PLUS_HOVER)
                    .tip(create), busy));
        } else {
            String playAs = I18n.format("gui.losttales.character.play_as");
            BarItem item = BarItem.button(PLAY_AS, playAs,
                    new ItemStack(Items.name_tag))
                    .lit(played)
                    .tip(played ? I18n.format(
                            "gui.losttales.character.playing_as", name)
                            : I18n.format("gui.losttales.character.play_as_tip",
                                    name));
            items.add(picked == null ? item.unavailable(I18n.format(
                    "gui.losttales.character.pick")) : played ? item
                    : character == null ? item.unavailable(I18n.format(
                            "gui.losttales.character.not_made"))
                    : orBusy(item, busy));
        }
        String edit = I18n.format("gui.losttales.character.profile_edit.button");
        items.add(orBusy(BarItem.button(EDIT, edit, new ItemStack(
                Items.feather)).tip(edit), character == null
                ? I18n.format(picked == null
                        || picked.kind == CharacterRosterRows.Kind.EMPTY
                        ? "gui.losttales.character.pick"
                        : "gui.losttales.character.not_made")
                : lore ? I18n.format(
                        "gui.losttales.character.error.lore_character_cannot_edit")
                : busy));
        String capes = I18n.format("gui.losttales.character.cape.button");
        items.add(orBusy(BarItem.button(CAPES, capes, new ItemStack(
                Items.leather)).tip(capes), picked == null
                || picked.kind == CharacterRosterRows.Kind.EMPTY
                ? I18n.format("gui.losttales.character.pick") : busy));
        String delete = I18n.format("gui.losttales.character.delete");
        items.add(orBusy(BarItem.button(DELETE, delete,
                LostTalesUiSheet.CLOSE, LostTalesUiSheet.CLOSE_HOVER)
                .tip(delete).ending(), character == null
                ? I18n.format(picked == null
                        || picked.kind == CharacterRosterRows.Kind.EMPTY
                        ? "gui.losttales.character.pick"
                        : "gui.losttales.character.error.delete_default_character")
                : character.isDefault() ? I18n.format(
                        "gui.losttales.character.error.delete_default_character")
                : lore ? I18n.format(
                        "gui.losttales.character.error.lore_character_cannot_delete")
                : busy));
        return items;
    }

    private static BarItem orBusy(BarItem item, String why) {
        return why == null || why.length() == 0 ? item : item.unavailable(why);
    }

    @Override
    public void barPressed(String id, int offer) {
        if (REFRESH.equals(id)) {
            refresh();
            return;
        }
        if (BACK.equals(id)) {
            endVisit();
            return;
        }
        CharacterRosterSnapshot snapshot = snapshot();
        if (snapshot == null || isPending()) {
            return;
        }
        CharacterRosterRows.Row picked = picked(snapshot, rows(snapshot));
        if (picked == null) {
            return;
        }
        CharacterSummary character = picked.character;
        if (CREATE.equals(id) && picked.kind == CharacterRosterRows.Kind.EMPTY) {
            WindowScreen screen = WindowScreen.current();
            this.mc.displayGuiScreen(new LostTalesCharacterCreationGui(screen,
                    picked.slot));
        } else if (PLAY_AS.equals(id) && character != null
                && !CharacterRosterRows.isPlayed(snapshot, picked)) {
            track(ClientCharacterNetwork.selectCharacter(
                    snapshot.getRevision(), character.getCharacterId()),
                    "gui.losttales.character.selecting");
        } else if (EDIT.equals(id) && character != null && !isLore(character)) {
            openEditor(character);
        } else if (CAPES.equals(id)) {
            openCapes(character);
        } else if (DELETE.equals(id) && character != null
                && !character.isDefault() && !isLore(character)) {
            askToDelete(snapshot, character);
        }
    }

    /* ---- The sub-windows ---- */

    private String windowId() {
        Window window = WindowLayout.windowOf(WindowPages.tab(PAGE_ID));
        return window == null ? null : window.getId();
    }

    private void openEditor(CharacterSummary character) {
        WindowScreen screen = WindowScreen.current();
        PageTab tab = WindowPages.tab(PAGE_ID);
        if (screen == null || tab == null) {
            return;
        }
        String key = character.getCharacterId().toString();
        SubWindow open = screen.subWindows().find(
                CharacterSubWindows.PROFILE_EDIT, key);
        ProfileEditWindow editor = open != null
                && open.content instanceof ProfileEditWindow
                ? (ProfileEditWindow)open.content
                : new ProfileEditWindow(character.getCharacterId());
        editor.restart();
        screen.openOverPage(tab, CharacterSubWindows.PROFILE_EDIT, key, editor);
    }

    /** The capes of the character picked; the account's own while its record is not made. */
    private void openCapes(CharacterSummary character) {
        WindowScreen screen = WindowScreen.current();
        if (screen != null) {
            screen.menus().show(CharacterSubWindows.CAPES,
                    CharacterMenus.capesAbout(character),
                    WindowMenus.centredIn(windowId()), true);
        }
    }

    private void openLoreCharacters() {
        WindowScreen screen = WindowScreen.current();
        if (screen != null) {
            screen.menus().show(CharacterSubWindows.LORE, null,
                    WindowMenus.centredIn(windowId()), true);
        }
    }

    /** Asks before a character is deleted; on the player's yes it goes with the roster as it stood. */
    private void askToDelete(final CharacterRosterSnapshot snapshot,
                             final CharacterSummary character) {
        WindowScreen screen = WindowScreen.current();
        if (screen == null) {
            return;
        }
        screen.ask(WindowPages.tab(PAGE_ID),
                I18n.format("gui.losttales.character.delete_question",
                        character.getName()),
                I18n.format("gui.losttales.character.delete_slot_remains"),
                I18n.format("gui.losttales.character.confirm_delete"),
                new Runnable() {
                    @Override
                    public void run() {
                        track(ClientCharacterNetwork.deleteCharacter(
                                snapshot.getRevision(),
                                character.getCharacterId()),
                                "gui.losttales.character.deleting");
                    }
                });
    }
}
