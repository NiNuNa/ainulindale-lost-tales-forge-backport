package com.ninuna.losttales.client.progression;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.OtherPage;
import com.ninuna.losttales.client.window.PageContent;
import com.ninuna.losttales.client.window.PageKeys;
import com.ninuna.losttales.client.window.TabIcons;
import com.ninuna.losttales.client.window.WheelStep;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowLists;
import com.ninuna.losttales.client.window.WindowPages;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.client.window.WorldPageReach;
import com.ninuna.losttales.client.window.WorldPageWatch;
import com.ninuna.losttales.block.custom.LostTalesBlockResetStatue;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiCutName;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.ProgressionRequestPacket;
import com.ninuna.losttales.network.packet.ProgressionStatuePacket;
import com.ninuna.losttales.progression.PerkWords;
import com.ninuna.losttales.progression.ProgressionState;
import com.ninuna.losttales.progression.statue.ResetStatueService;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import com.ninuna.losttales.quest.LostTalesQuestItemSpec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

/**
 * A reset statue's page (PROG-12): what the character's next reset costs
 * there, and every skill it took perks in, each with the points it gives
 * back. A press on a skill asks first, then the server resets it if the
 * character pays; nothing else offers a reset. It opens only from the
 * statue and closes once the player walks away from it.
 */
public final class ResetStatuePage extends PageContent implements WorldPageWatch.Watched {
    /** The code name the page is registered under. */
    public static final String PAGE_ID = "reset_statue";
    /** The item the tab wears until the statue has artwork of its own. */
    public static final ItemStack ICON = new ItemStack(Blocks.stonebrick);

    private static final int LINE_HEIGHT = 10;
    private static final String SERVER_LINES = "chat.losttales.statue.";

    private final Minecraft mc = Minecraft.getMinecraft();
    private FontRenderer font;
    /** The statue as the server last sent it; null while no statue is open. */
    private ProgressionStatuePacket statue;
    /** The world the player stood in as the statue opened, which the packet does not carry. */
    private int dimension;
    private int scroll;
    private int hovered = -1;

    /** The server's word that the player used a statue: the page turns to it and comes forward. */
    public static void accept(ProgressionStatuePacket packet) {
        if (packet == null || packet.isMalformed()) {
            return;
        }
        WindowPages.Page page = WindowPages.byId(PAGE_ID);
        PageContent content = page == null ? null : page.content();
        if (!(content instanceof ResetStatuePage)) {
            return;
        }
        ResetStatuePage shown = (ResetStatuePage) content;
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        shown.statue = packet;
        shown.dimension = player == null ? 0 : player.dimension;
        WindowScreen.openPage(PAGE_ID);
    }

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX, double clipY,
                     double pointerX, double pointerY, float partialTicks, int alpha) {
        this.font = minecraft.fontRenderer;
        int width = (int) Math.floor(box.width);
        int height = (int) Math.floor(box.height);
        List<Row> rows = rows(width - 2 * MenuWindow.PADDING_X);
        int total = 0;
        for (Row row : rows) {
            total += row.height();
        }
        int maxScroll = Math.max(0, total + 2 * MenuWindow.PADDING_Y - height);
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll));
        int mouseY = Double.isNaN(pointerY) ? Integer.MIN_VALUE / 2
                : (int) Math.floor(pointerY - box.top);
        int mouseX = Double.isNaN(pointerX) ? Integer.MIN_VALUE / 2
                : (int) Math.floor(pointerX - box.left);
        this.hovered = rowAt(rows, width, height, mouseX, mouseY);
        int surfaceAlpha = WindowLists.pageSurfaceAlpha(minecraft, alpha);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef((float) box.left, (float) box.top, 0.0F);
            int y = MenuWindow.PADDING_Y - this.scroll;
            for (int index = 0; index < rows.size(); index++) {
                Row row = rows.get(index);
                if (y + row.height() > 0 && y < height) {
                    drawRow(row, index, width, y, surfaceAlpha, alpha);
                }
                y += row.height();
            }
        } finally {
            GL11.glPopMatrix();
        }
        WindowLists.drawScroll(box.left, box.top, box.right(), box.bottom(), box.top,
                box.bottom(), this.scroll, maxScroll, alpha);
    }

    private void drawRow(Row row, int index, int width, int y, int surfaceAlpha, int alpha) {
        int left = MenuWindow.PADDING_X;
        int right = width - MenuWindow.PADDING_X;
        if (row.kind == Row.HEADING) {
            WindowLists.drawHeading(this.font, row.text, left, left, right, y, row.height(), true,
                    alpha);
            return;
        }
        if (row.kind == Row.SEPARATOR) {
            WindowLists.drawSeparator(left, right, y, alpha);
            return;
        }
        int textTop = y + LostTalesUiInk.centredStart(row.height(), LostTalesUiInk.CAP_HEIGHT);
        if (row.kind == Row.SKILL) {
            if (index == this.hovered) {
                WindowLists.drawLitRow(0.0D, width, 0.0D, y, width, y + row.height(),
                        surfaceAlpha);
            }
            int valueX = right - Math.max(0, this.font.getStringWidth(row.value) - 1);
            LostTalesUiInk.drawText(this.font, row.value, valueX, textTop,
                    LostTalesColors.rgb(LostTalesColors.GOLD), alpha);
            // A skill's name too long for its row fades into the row's end
            // and slides along under the pointer (LOOK-37).
            LostTalesUiCutName.drawText(this.font, row.text, left, textTop,
                    valueX - TabIcons.GAP - left,
                    LostTalesColors.rgb(index == this.hovered ? LostTalesColors.TEXT_BRIGHT
                            : LostTalesColors.TEXT), alpha, this, Integer.valueOf(index),
                    index == this.hovered);
            return;
        }
        LostTalesUiInk.drawText(this.font, row.text, left, textTop, row.rgb, alpha);
    }

    /** The page's rows: the statue's heading, its price, then every skill with perks to give back. */
    private List<Row> rows(int width) {
        List<Row> rows = new ArrayList<Row>();
        if (this.font == null || this.statue == null) {
            return rows;
        }
        rows.add(Row.heading(StatCollector.translateToLocal("gui.losttales.page.reset_statue")));
        wrap(rows, price(), width, LostTalesColors.rgb(LostTalesColors.TEXT_BRIGHT));
        wrap(rows, StatCollector.translateToLocal("gui.losttales.statue.about"), width,
                WindowStyle.asideRgb());
        rows.add(Row.separator());
        ProgressionState state = ClientProgression.state();
        boolean any = false;
        for (SkillDefinition skill : ClientProgression.skills()) {
            int ranks = state.ranksIn(skill.getId());
            if (ranks > 0) {
                rows.add(Row.skill(skill.getId(), PerkWords.skillName(skill),
                        StatCollector.translateToLocalFormatted(ranks == 1
                                ? "gui.losttales.statue.point" : "gui.losttales.statue.points",
                                Integer.valueOf(ranks))));
                any = true;
            }
        }
        if (!any) {
            wrap(rows, StatCollector.translateToLocal("gui.losttales.statue.none"), width,
                    LostTalesColors.rgb(LostTalesColors.TEXT_DIM));
        }
        return rows;
    }

    /** What the next reset costs: coins, an item besides where the server asks one, or nothing. */
    private String price() {
        String item = itemName(this.statue.getItem());
        int coins = this.statue.getCoins();
        if (item.length() > 0) {
            return coins > 0 ? StatCollector.translateToLocalFormatted(
                    "gui.losttales.statue.price.both", Integer.valueOf(coins), item)
                    : StatCollector.translateToLocalFormatted("gui.losttales.statue.price.item",
                            item);
        }
        return coins > 0 ? StatCollector.translateToLocalFormatted(
                "gui.losttales.statue.price.coins", Integer.valueOf(coins))
                : StatCollector.translateToLocal("gui.losttales.statue.price.free");
    }

    private static String itemName(String written) {
        if (written == null || written.length() == 0) {
            return "";
        }
        LostTalesQuestItemSpec spec = LostTalesQuestItemSpec.parse(written, 1, 0);
        Item item = spec.item();
        return item == null ? written
                : new ItemStack(item, 1, Math.max(0, spec.getMeta())).getDisplayName();
    }

    private void wrap(List<Row> rows, String text, int width, int rgb) {
        for (Object line : this.font.listFormattedStringToWidth(text, Math.max(1, width))) {
            rows.add(Row.text(String.valueOf(line), rgb));
        }
    }

    private int rowAt(List<Row> rows, int width, int height, int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return -1;
        }
        int top = MenuWindow.PADDING_Y - this.scroll;
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            if (y >= top && y < top + row.height()) {
                return row.kind == Row.SKILL ? index : -1;
            }
            top += row.height();
        }
        return -1;
    }

    @Override
    public boolean mousePressed(Minecraft minecraft, LostTalesUiHitBox box, double x, double y,
                                int button) {
        if (button != 0 || this.font == null) {
            return false;
        }
        int width = (int) Math.floor(box.width);
        List<Row> rows = rows(width - 2 * MenuWindow.PADDING_X);
        int index = rowAt(rows, width, (int) Math.floor(box.height),
                (int) Math.floor(x - box.left), (int) Math.floor(y - box.top));
        if (index < 0) {
            return false;
        }
        askToReset(rows.get(index));
        return true;
    }

    @Override
    public boolean acts(LostTalesUiHitBox box, double x, double y) {
        int width = (int) Math.floor(box.width);
        return this.font != null && rowAt(rows(width - 2 * MenuWindow.PADDING_X), width,
                (int) Math.floor(box.height), (int) Math.floor(x - box.left),
                (int) Math.floor(y - box.top)) >= 0;
    }

    @Override
    public boolean scroll(LostTalesUiHitBox box, double x, double y, int lines) {
        if (lines == 0) {
            return false;
        }
        this.scroll += WheelStep.pixels(WheelStep.menuRows(lines), MenuWindow.rowHeight());
        return true;
    }

    /** Asks before the reset: the price is spent, and the points come back only to spend again. */
    private void askToReset(final Row row) {
        WindowScreen screen = WindowScreen.current();
        final ProgressionStatuePacket at = this.statue;
        if (screen == null || at == null) {
            return;
        }
        screen.ask(tab(),
                StatCollector.translateToLocalFormatted("gui.losttales.statue.ask.title", row.text),
                StatCollector.translateToLocalFormatted("gui.losttales.statue.ask.detail",
                        row.value, price()),
                StatCollector.translateToLocal("gui.losttales.statue.ask.confirm"),
                new Runnable() {
                    @Override
                    public void run() {
                        LostTalesNetworkHandler.CHANNEL.sendToServer(
                                ProgressionRequestPacket.resetAtStatue(at.getX(), at.getY(),
                                        at.getZ(), row.id));
                        sayWorking(StatCollector.translateToLocal("gui.losttales.statue.resetting"));
                    }
                });
    }

    @Override
    public boolean answersLine(String key) {
        return key != null && key.startsWith(SERVER_LINES);
    }

    @Override
    public void answerLine(String key, String words) {
        if (key.startsWith(SERVER_LINES + "reset.")) {
            sayDone(words);
        } else {
            sayRefused(words);
        }
    }

    @Override
    public List<PageKeys.Area> keyAreas() {
        return PageKeys.pageArea("gui.losttales.page.reset_statue",
                PageKeys.pageKey(PAGE_ID, "reset", PageKeys.CLICK),
                PageKeys.pageKey(PAGE_ID, "wheel", PageKeys.WHEEL));
    }

    /* ---- Watching the statue ---- */

    @Override
    public void tick() {
        watch();
    }

    /** Closes the tab where the player no longer stands at the statue. */
    @Override
    public void watch() {
        OtherPage tab = WindowPages.tab(PAGE_ID);
        if (tab == null || !WindowLayout.isOpen(tab)) {
            return;
        }
        WorldPageReach.Leave leave = leave();
        if (leave != null) {
            WorldPageWatch.close(tab, StatCollector.translateToLocal(
                    leave.messageKey("statue")));
            this.statue = null;
        }
    }

    private WorldPageReach.Leave leave() {
        EntityPlayer player = this.mc.thePlayer;
        World world = this.mc.theWorld;
        if (this.statue == null || player == null || world == null) {
            return WorldPageReach.Leave.GONE;
        }
        int x = this.statue.getX();
        int y = this.statue.getY();
        int z = this.statue.getZ();
        boolean standing = world.getBlock(x, y, z) instanceof LostTalesBlockResetStatue;
        return WorldPageReach.check(player.dimension, player.posX, player.boundingBox.minY,
                player.posZ, this.dimension, x, y, z, standing,
                ResetStatueService.REACH * ResetStatueService.REACH);
    }

    /** The tab closed, by hand or by itself: the statue goes with it. */
    @Override
    public void hidden() {
        OtherPage tab = WindowPages.tab(PAGE_ID);
        if (tab == null || !WindowLayout.isOpen(tab)) {
            this.statue = null;
        }
    }

    /** A row of the page: the heading, words, a separator, or a skill to reset. */
    private static final class Row {
        static final int TEXT = 0;
        static final int HEADING = 1;
        static final int SEPARATOR = 2;
        static final int SKILL = 3;

        final int kind;
        String text = "";
        String value = "";
        String id = "";
        int rgb;

        Row(int kind) {
            this.kind = kind;
        }

        static Row heading(String text) {
            Row row = new Row(HEADING);
            row.text = text;
            return row;
        }

        static Row text(String text, int rgb) {
            Row row = new Row(TEXT);
            row.text = text;
            row.rgb = rgb;
            return row;
        }

        static Row separator() {
            return new Row(SEPARATOR);
        }

        static Row skill(String id, String name, String points) {
            Row row = new Row(SKILL);
            row.id = id;
            row.text = name;
            row.value = points;
            return row;
        }

        int height() {
            switch (this.kind) {
                case HEADING:
                case SKILL:
                    return MenuWindow.rowHeight();
                case SEPARATOR:
                    return WindowLists.SEPARATOR_HEIGHT;
                default:
                    return LINE_HEIGHT;
            }
        }
    }
}
