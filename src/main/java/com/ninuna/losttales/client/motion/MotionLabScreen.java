package com.ninuna.losttales.client.motion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.ninuna.losttales.client.gui.LostTalesButton;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * The Motion Lab: every motion the mod plays, each looping over a sample
 * while its numbers and curves are stepped. What is tuned shows at once
 * — the Lab's own buttons play the buttons' motions — and lives only in
 * the Lab until it is saved into {@code config/losttales/client/motion},
 * over the mod's own file. Copy puts the motion's JSON on the clipboard,
 * ready for the mod's own file; Reset takes the Lab's saved version away.
 *
 * <p>The Lab edits a motion as its file writes it, so whatever the file
 * holds survives an edit, the layers a row does not show included. A
 * value is stepped with the arrows beside it, ten steps at a time with
 * Shift; a curve and a choice step through their names.</p>
 */
public final class MotionLabScreen extends GuiScreen {
    private static final int BUTTON_REPLAY = 0;
    private static final int BUTTON_COPY = 1;
    private static final int BUTTON_RESET = 2;
    private static final int BUTTON_SAVE = 3;
    private static final int BUTTON_DONE = 4;
    private static final int MARGIN = 8;
    private static final int TOP = 40;
    private static final int ROW_HEIGHT = 11;
    private static final int BUTTON_WIDTH = 64;
    private static final int BUTTON_HEIGHT = 20;
    private static final int PREVIEW_HEIGHT = 64;
    /** How much larger the sample is drawn than it plays, so a pixel's travel reads. */
    private static final float PREVIEW_ZOOM = 3.0F;
    /** How long the sample rests between two beats. */
    private static final long HOLD_NANOS = 600L * 1000000L;
    /** How long a follower's target stands before it jumps. */
    private static final long FOLLOW_FLIP_NANOS = 700L * 1000000L;
    private static final int ARROW_WIDTH = 9;
    /** The items a part that moves a row of words is sampled with. */
    private static final int SAMPLE_WORDS = 5;

    private final GuiScreen parent;
    private final List<String> ids = new ArrayList<String>();
    private final List<Row> rows = new ArrayList<Row>();
    private final java.util.Set<String> edited = new java.util.HashSet<String>();
    private String selectedId;
    private JsonObject working;
    private int partIndex;
    private int beatIndex;
    private int listScroll;
    private int rowScroll;
    private String problem = "";
    private String notice = "";
    private MotionPlayer samplePlayer;
    private MotionTransition sampleTransition;
    private List<String> sampleBeats = new ArrayList<String>();
    private int sampleBeat;
    private long sampleRestUntil;
    private boolean sampleOn;
    private double followValue;
    private double followTarget = 1.0D;
    private long followFlipNanos;
    private long lastFrameNanos;

    public MotionLabScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        String[] labels = {"replay", "copy", "reset", "save", "done"};
        int total = labels.length * BUTTON_WIDTH + (labels.length - 1) * 4;
        int left = (this.width - total) / 2;
        int top = this.height - BUTTON_HEIGHT - 8;
        for (int index = 0; index < labels.length; index++) {
            this.buttonList.add(new LostTalesButton(index,
                    left + index * (BUTTON_WIDTH + 4), top, BUTTON_WIDTH,
                    BUTTON_HEIGHT, translate(labels[index])));
        }
        this.ids.clear();
        for (String family : Motions.FAMILIES) {
            for (String id : Motions.ids()) {
                if (family.equals(Motions.family(id))) {
                    this.ids.add(id);
                }
            }
        }
        if (this.selectedId == null && !this.ids.isEmpty()) {
            select(this.ids.get(0));
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        // What was not saved goes with the Lab.
        Motions.clearPreviews();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /* ---- choosing and editing ---- */

    private void select(String id) {
        this.selectedId = id;
        this.working = MotionCodec.encode(Motions.get(id));
        this.partIndex = 0;
        this.beatIndex = 0;
        this.rowScroll = 0;
        this.problem = "";
        rebuildRows();
        restartSample();
    }

    /** Reads the edited motion back and plays it in place of its file's. */
    private void applyWorking() {
        JsonObject wrapper = new JsonObject();
        wrapper.add(this.selectedId, this.working);
        MotionCodec.Result result = MotionCodec.read(wrapper.toString());
        Motion motion = result.motions().get(this.selectedId);
        this.problem = result.problems().isEmpty() ? ""
                : result.problems().get(0);
        if (motion != null) {
            Motions.preview(motion);
            this.edited.add(this.selectedId);
        }
        this.notice = "";
        restartSample();
    }

    private void rebuildRows() {
        this.rows.clear();
        JsonObject motion = this.working;
        if (motion.has("follow")) {
            this.rows.add(new NumberRow(translate("row.follow"),
                    owner(motion), "follow", 0.01F, 0.0F,
                    MotionCodec.MAX_FOLLOW_SECONDS, 0.0F));
        } else if (motion.has("parts")) {
            addPartRows(motion.getAsJsonObject("parts"));
        } else {
            addTransitionRows(motion);
        }
        JsonObject params = motion.has("params")
                && motion.get("params").isJsonObject()
                ? motion.getAsJsonObject("params") : null;
        if (params != null) {
            this.rows.add(new TextRow(translate("row.params"), true));
            for (Map.Entry<String, JsonElement> param : params.entrySet()) {
                float value = number(param.getValue(), 0.0F);
                this.rows.add(new NumberRow(param.getKey(), owner(params),
                        param.getKey(), stepFor(value), -MotionCodec.MAX_PARAM,
                        MotionCodec.MAX_PARAM, 0.0F));
            }
        }
    }

    private void addTransitionRows(final JsonObject motion) {
        this.rows.add(new NumberRow(translate("row.duration"), owner(motion),
                "duration", 10.0F, 0.0F, MotionBeat.MAX_MILLIS, 0.0F));
        this.rows.add(new CurveRow(translate("row.curve"), owner(motion),
                "curve", MotionCurve.EASE_OUT.name()));
        // The way back is the way on until it is tuned on its own.
        Owner off = new Owner() {
            @Override
            public JsonObject get(boolean create) {
                if (motion.has("off") && motion.get("off").isJsonObject()) {
                    return motion.getAsJsonObject("off");
                }
                if (!create) {
                    return motion;
                }
                JsonObject made = new JsonObject();
                made.add("duration", copyOf(motion.get("duration")));
                made.add("curve", copyOf(motion.get("curve")));
                motion.add("off", made);
                return made;
            }
        };
        this.rows.add(new NumberRow(translate("row.off_duration"), off,
                "duration", 10.0F, 0.0F, MotionBeat.MAX_MILLIS, 0.0F));
        this.rows.add(new CurveRow(translate("row.off_curve"), off, "curve",
                MotionCurve.EASE_OUT.name()));
    }

    private void addPartRows(JsonObject parts) {
        final List<String> partNames = keys(parts);
        if (partNames.isEmpty()) {
            return;
        }
        this.partIndex = Math.min(this.partIndex, partNames.size() - 1);
        this.rows.add(new PickRow(translate("row.part"), partNames,
                this.partIndex) {
            @Override
            void picked(int index) {
                MotionLabScreen.this.partIndex = index;
                MotionLabScreen.this.beatIndex = 0;
                rebuildRows();
            }
        });
        JsonObject part = objectOf(parts, partNames.get(this.partIndex));
        JsonObject poses = objectOf(part, "poses");
        JsonObject beats = objectOf(part, "beats");
        List<String> beatNames = keys(beats);
        if (!beatNames.isEmpty()) {
            this.beatIndex = Math.min(this.beatIndex, beatNames.size() - 1);
            this.rows.add(new PickRow(translate("row.beat"), beatNames,
                    this.beatIndex) {
                @Override
                void picked(int index) {
                    MotionLabScreen.this.beatIndex = index;
                    rebuildRows();
                }
            });
            final JsonObject beat = objectOf(beats,
                    beatNames.get(this.beatIndex));
            final List<String> targets = new ArrayList<String>();
            targets.add("");
            targets.addAll(keys(poses));
            String to = text(beat.get("to"));
            this.rows.add(new PickRow(translate("row.to"), targets,
                    Math.max(0, targets.indexOf(to))) {
                @Override
                String label(String choice) {
                    return choice.length() == 0 ? translate("row.to_code")
                            : choice;
                }

                @Override
                void picked(int index) {
                    if (targets.get(index).length() == 0) {
                        beat.remove("to");
                    } else {
                        beat.addProperty("to", targets.get(index));
                    }
                    applyWorking();
                }
            });
            this.rows.add(new NumberRow(translate("row.duration"),
                    owner(beat), "duration", 10.0F, 0.0F,
                    MotionBeat.MAX_MILLIS, 0.0F));
            this.rows.add(new NumberRow(translate("row.delay"), owner(beat),
                    "delay", 10.0F, 0.0F, MotionBeat.MAX_MILLIS, 0.0F));
            this.rows.add(new NumberRow(translate("row.stagger"), owner(beat),
                    "stagger", 1.0F, 0.0F, MotionBeat.MAX_MILLIS, 0.0F));
            this.rows.add(new CurveRow(translate("row.curve"), owner(beat),
                    "curve", MotionCurve.EASE_OUT.name()));
            addLayerRows("", objectOf(beat, "tracks"));
            JsonObject from = objectOf(beat, "from");
            for (String pose : keys(from)) {
                addLayerRows(translate("row.from") + " " + pose + " · ",
                        objectOf(from, pose));
            }
        }
        if (!keys(poses).isEmpty()) {
            this.rows.add(new TextRow(translate("row.poses"), true));
            for (String pose : keys(poses)) {
                JsonObject values = objectOf(poses, pose);
                for (String key : keys(values)) {
                    MotionTrack track = MotionTrack.parse(key);
                    if (track == null) {
                        continue;
                    }
                    this.rows.add(new NumberRow(pose + " · " + key,
                            owner(values), key, trackStep(track),
                            -track.reach(), track.reach(), 0.0F));
                }
            }
        }
    }

    /** A row for every number of every layer a beat's tracks hold. */
    private void addLayerRows(String prefix, JsonObject tracks) {
        for (String key : keys(tracks)) {
            MotionTrack track = MotionTrack.parse(key);
            JsonElement listed = tracks.get(key);
            if (track == null || listed == null) {
                continue;
            }
            List<JsonObject> layers = new ArrayList<JsonObject>();
            if (listed.isJsonArray()) {
                for (JsonElement layer : listed.getAsJsonArray()) {
                    if (layer.isJsonObject()) {
                        layers.add(layer.getAsJsonObject());
                    }
                }
            } else if (listed.isJsonObject()) {
                layers.add(listed.getAsJsonObject());
            }
            for (JsonObject layer : layers) {
                String name = prefix + key + " · ";
                if (layer.has("travel")) {
                    this.rows.add(new CurveRow(name + translate("row.travel"),
                            owner(layer), "travel",
                            MotionCurve.EASE_OUT.name()));
                } else if (layer.has("bump")) {
                    this.rows.add(new NumberRow(name + translate("row.bump"),
                            owner(layer), "bump", 0.05F, -track.reach(),
                            track.reach(), 0.0F));
                } else if (layer.has("ring")) {
                    this.rows.add(new NumberRow(name + translate("row.ring"),
                            owner(layer), "ring", 0.05F, -track.reach(),
                            track.reach(), 0.0F));
                    this.rows.add(new NumberRow(name + translate("row.lobes"),
                            owner(layer), "lobes", 1.0F, 1.0F,
                            MotionLayer.MAX_LOBES, 3.0F));
                } else {
                    this.rows.add(new TextRow(name + translate("row.keys"),
                            false));
                    continue;
                }
                this.rows.add(new NumberRow(name + translate("row.begin"),
                        owner(layer), "begin", 0.02F, 0.0F, 1.0F, 0.0F));
                this.rows.add(new NumberRow(name + translate("row.end"),
                        owner(layer), "end", 0.02F, 0.0F, 1.0F, 1.0F));
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_DONE) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (this.selectedId == null) {
            return;
        }
        if (button.id == BUTTON_REPLAY) {
            restartSample();
        } else if (button.id == BUTTON_COPY) {
            JsonObject wrapper = new JsonObject();
            wrapper.add(this.selectedId, this.working);
            MotionCodec.Result result = MotionCodec.read(wrapper.toString());
            GuiScreen.setClipboardString(MotionCodec.write(result.motions()));
            this.notice = translate("copied");
        } else if (button.id == BUTTON_SAVE) {
            Motion motion = Motions.get(this.selectedId);
            if (Motions.save(motion, this.mc.getResourceManager())) {
                Motions.clearPreview(this.selectedId);
                this.edited.remove(this.selectedId);
                this.notice = translate("saved");
            } else {
                this.notice = translate("not_saved");
            }
        } else if (button.id == BUTTON_RESET) {
            Motions.clearPreview(this.selectedId);
            this.edited.remove(this.selectedId);
            if (Motions.forget(this.selectedId,
                    this.mc.getResourceManager())) {
                select(this.selectedId);
                this.notice = translate("was_reset");
            } else {
                this.notice = translate("not_saved");
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (button != 0) {
            return;
        }
        Layout layout = layout();
        int listRow = listRowAt(layout, mouseX, mouseY);
        if (listRow >= 0 && listRow < this.ids.size()) {
            select(this.ids.get(listRow));
            return;
        }
        for (int index = 0; index < this.rows.size(); index++) {
            int top = rowTop(layout, index);
            if (top < layout.rowsTop || top + ROW_HEIGHT > layout.bottom) {
                continue;
            }
            Row row = this.rows.get(index);
            if (!row.editable()) {
                continue;
            }
            boolean fast = GuiScreen.isShiftKeyDown();
            if (lessBox(layout, top).contains(mouseX, mouseY)) {
                row.step(-1, fast);
                return;
            }
            if (moreBox(layout, top).contains(mouseX, mouseY)) {
                row.step(1, fast);
                return;
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        Layout layout = layout();
        int step = wheel > 0 ? -1 : 1;
        if (mouseX < layout.listRight) {
            this.listScroll = Math.max(0, Math.min(Math.max(0,
                    listLines() - layout.listRows()), this.listScroll + step));
        } else {
            this.rowScroll = Math.max(0, Math.min(Math.max(0,
                    this.rows.size() - layout.visibleRows()),
                    this.rowScroll + step));
        }
    }

    /* ---- drawing ---- */

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        LostTalesSkyrimUiStyle.drawScreenShade(this.width, this.height);
        LostTalesSkyrimUiStyle.drawCenteredHeader(this.fontRendererObj,
                translate("title"), translate("subtitle"), this.width, 8);
        Layout layout = layout();
        drawList(layout, mouseX, mouseY);
        drawSelected(layout, mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawList(Layout layout, int mouseX, int mouseY) {
        LostTalesSkyrimUiStyle.drawPanel(MARGIN, TOP, layout.listRight - MARGIN,
                layout.bottom - TOP);
        int hovered = listRowAt(layout, mouseX, mouseY);
        String family = "";
        int line = 0;
        for (int index = 0; index < this.ids.size(); index++) {
            String id = this.ids.get(index);
            String idFamily = Motions.family(id);
            if (!idFamily.equals(family)) {
                family = idFamily;
                int top = listLineTop(layout, line++);
                if (top >= TOP + 4 && top + ROW_HEIGHT <= layout.bottom - 4) {
                    LostTalesSkyrimUiStyle.beginContent();
                    LostTalesSkyrimUiStyle.drawSectionHeader(
                            this.fontRendererObj, family, MARGIN + 6, top + 1,
                            layout.listRight - MARGIN - 12);
                }
            }
            int top = listLineTop(layout, line++);
            if (top < TOP + 4 || top + ROW_HEIGHT > layout.bottom - 4) {
                continue;
            }
            boolean selected = id.equals(this.selectedId);
            LostTalesSkyrimUiStyle.drawSelectionRow(MARGIN + 2, top,
                    layout.listRight - MARGIN - 4, ROW_HEIGHT, selected,
                    index == hovered);
            LostTalesSkyrimUiStyle.beginContent();
            String label = (this.edited.contains(id) ? "* " : "")
                    + id.substring(id.indexOf('.') + 1);
            this.fontRendererObj.drawStringWithShadow(
                    LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj,
                            label, layout.listRight - MARGIN - 24),
                    MARGIN + 16, top + 2, selected
                            ? LostTalesColors.TEXT_BRIGHT
                            : LostTalesColors.TEXT);
        }
    }

    private void drawSelected(Layout layout, int mouseX, int mouseY) {
        int left = layout.listRight + MARGIN;
        int right = this.width - MARGIN;
        LostTalesSkyrimUiStyle.drawPanel(left, TOP, right - left,
                layout.bottom - TOP);
        if (this.selectedId == null) {
            return;
        }
        Motion motion = Motions.get(this.selectedId);
        LostTalesSkyrimUiStyle.beginContent();
        this.fontRendererObj.drawStringWithShadow(this.selectedId, left + 6,
                TOP + 5, LostTalesColors.GOLD);
        List<?> about = this.fontRendererObj.listFormattedStringToWidth(
                motion.about(), right - left - 12);
        for (int index = 0; index < Math.min(2, about.size()); index++) {
            this.fontRendererObj.drawStringWithShadow(
                    String.valueOf(about.get(index)), left + 6,
                    TOP + 16 + index * 10, LostTalesColors.TEXT_MUTED);
        }
        drawSample(motion, left + 6, layout.previewTop, right - left - 12);
        LostTalesSkyrimUiStyle.beginContent();
        String status = this.problem.length() > 0 ? this.problem : this.notice;
        if (status.length() > 0) {
            this.fontRendererObj.drawStringWithShadow(
                    LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj,
                            status, right - left - 12), left + 6,
                    layout.previewTop + PREVIEW_HEIGHT + 3,
                    this.problem.length() > 0 ? LostTalesColors.SALMON
                            : LostTalesColors.TEXT_MUTED);
        }
        for (int index = 0; index < this.rows.size(); index++) {
            int top = rowTop(layout, index);
            if (top < layout.rowsTop || top + ROW_HEIGHT > layout.bottom) {
                continue;
            }
            drawRow(layout, this.rows.get(index), top, mouseX, mouseY);
        }
    }

    private void drawRow(Layout layout, Row row, int top, int mouseX,
                         int mouseY) {
        LostTalesSkyrimUiStyle.beginContent();
        int left = layout.listRight + MARGIN + 6;
        if (!row.editable()) {
            if (row.header()) {
                LostTalesSkyrimUiStyle.drawSectionHeader(this.fontRendererObj,
                        row.label, left, top + 2,
                        this.width - MARGIN - 6 - left);
            } else {
                this.fontRendererObj.drawStringWithShadow(row.label, left,
                        top + 2, LostTalesColors.TEXT_MUTED);
            }
            return;
        }
        this.fontRendererObj.drawStringWithShadow(
                LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj,
                        row.label, layout.valueLeft - left - 6),
                left, top + 2, LostTalesColors.TEXT_MUTED);
        LostTalesUiHitBox less = lessBox(layout, top);
        LostTalesUiHitBox more = moreBox(layout, top);
        this.fontRendererObj.drawStringWithShadow("<", (int)less.left + 2,
                top + 2, less.contains(mouseX, mouseY)
                        ? LostTalesColors.GOLD : LostTalesColors.TEXT_MUTED);
        this.fontRendererObj.drawStringWithShadow(
                LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj,
                        row.value(), (int)(more.left - less.left) - ARROW_WIDTH
                                - 4),
                (int)(less.left + ARROW_WIDTH + 2), top + 2,
                LostTalesColors.TEXT_BRIGHT);
        this.fontRendererObj.drawStringWithShadow(">", (int)more.left + 2,
                top + 2, more.contains(mouseX, mouseY)
                        ? LostTalesColors.GOLD : LostTalesColors.TEXT_MUTED);
    }

    /* ---- the sample ---- */

    private void restartSample() {
        Motion motion = Motions.get(this.selectedId);
        this.samplePlayer = null;
        this.sampleTransition = null;
        this.sampleBeats = new ArrayList<String>();
        this.sampleBeat = 0;
        this.sampleRestUntil = 0L;
        this.followValue = 0.0D;
        this.followTarget = 1.0D;
        this.followFlipNanos = System.nanoTime();
        if (motion.isFollower()) {
            return;
        }
        if (MotionCodec.isTransition(motion)) {
            this.sampleTransition = new MotionTransition(motion.id());
            this.sampleTransition.settle(false);
            this.sampleOn = true;
            return;
        }
        for (MotionPart part : motion.parts().values()) {
            for (String beat : part.beats().keySet()) {
                if (!this.sampleBeats.contains(beat)) {
                    this.sampleBeats.add(beat);
                }
            }
        }
        this.samplePlayer = new MotionPlayer(motion.id());
        this.samplePlayer.settle(Motion.REST);
        playSampleBeat(System.nanoTime());
    }

    private void playSampleBeat(long now) {
        if (this.sampleBeats.isEmpty()) {
            return;
        }
        String beat = this.sampleBeats.get(this.sampleBeat
                % this.sampleBeats.size());
        MotionBeat found = Motions.get(this.selectedId).beat(beat);
        // A beat the code sends somewhere of its own goes back to rest
        // in the sample.
        boolean named = found != null && found.to().length() > 0;
        this.samplePlayer.play(beat, named ? null : Motion.REST, now);
    }

    private void drawSample(Motion motion, int left, int top, int width) {
        long now = System.nanoTime();
        double elapsed = this.lastFrameNanos == 0L ? 0.0D
                : (now - this.lastFrameNanos) / 1.0E9D;
        this.lastFrameNanos = now;
        Gui.drawRect(left, top, left + width, top + PREVIEW_HEIGHT,
                LostTalesColors.withAlpha(LostTalesColors.PLUM_BLACK, 0xA0));
        boolean clipped = beginClip(left, top, width, PREVIEW_HEIGHT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(left + width / 2.0F,
                    top + PREVIEW_HEIGHT / 2.0F, 0.0F);
            GL11.glScalef(PREVIEW_ZOOM, PREVIEW_ZOOM, 1.0F);
            float reach = (width / 2.0F - 8.0F) / PREVIEW_ZOOM;
            if (motion.isFollower()) {
                if (now - this.followFlipNanos >= FOLLOW_FLIP_NANOS) {
                    this.followTarget = 1.0D - this.followTarget;
                    this.followFlipNanos = now;
                }
                this.followValue = Motions.follow(motion.id(),
                        this.followValue, this.followTarget, elapsed);
                float span = Math.min(reach, 30.0F);
                drawBlock(-span + 2.0F * span * (float)this.followTarget, 0.0F,
                        LostTalesColors.PLUM_GRAY, 1.0F, 0.0F, 0.0F, 1.0F,
                        1.0F);
                drawBlock(-span + 2.0F * span * (float)this.followValue, 0.0F,
                        LostTalesColors.IVORY, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F);
            } else if (this.sampleTransition != null) {
                float value = this.sampleTransition.advance(now,
                        this.sampleOn);
                if (this.sampleTransition.isSettled()) {
                    if (this.sampleRestUntil == 0L) {
                        this.sampleRestUntil = now + HOLD_NANOS;
                    } else if (now >= this.sampleRestUntil) {
                        this.sampleOn = !this.sampleOn;
                        this.sampleRestUntil = 0L;
                    }
                }
                float span = Math.min(reach, 30.0F);
                Gui.drawRect(Math.round(-span), 0, Math.round(span), 1,
                        LostTalesColors.BORDER_DIM);
                drawBlock(-span + 2.0F * span * value, -2.0F,
                        LostTalesColors.IVORY, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F);
            } else if (this.samplePlayer != null) {
                stepSample(now);
                drawParts(motion, now, reach);
            }
        } finally {
            GL11.glPopMatrix();
            endClip(clipped);
        }
    }

    /** Moves the sample on to its next beat once this one has rested. */
    private void stepSample(long now) {
        if (!this.samplePlayer.isSettled(now, SAMPLE_WORDS - 1)) {
            return;
        }
        if (this.sampleRestUntil == 0L) {
            this.sampleRestUntil = now + HOLD_NANOS;
        } else if (now >= this.sampleRestUntil) {
            this.sampleRestUntil = 0L;
            this.sampleBeat++;
            playSampleBeat(now);
        }
    }

    private void drawParts(Motion motion, long now, float reach) {
        int count = motion.parts().size();
        int index = 0;
        int[] colours = {LostTalesColors.IVORY, LostTalesColors.HONEY,
                LostTalesColors.SEAFOAM, LostTalesColors.ORCHID};
        for (MotionPart part : motion.parts().values()) {
            float rowY = (index - (count - 1) / 2.0F) * 10.0F;
            int colour = colours[index % colours.length];
            boolean words = movesWords(part);
            int items = words ? SAMPLE_WORDS : 1;
            float startX = words ? -reach / 2.0F : 0.0F;
            for (int item = 0; item < items; item++) {
                float place = item;
                String name = part.name();
                float x = this.samplePlayer.value(name, MotionTrack.X, place,
                        now);
                if (words && items > 1) {
                    x += Math.round(this.samplePlayer.value(name,
                            MotionTrack.GAP, place, now) * item
                            / (float)(items - 1));
                }
                drawBlock(startX + item * 8.0F + x, rowY
                                + this.samplePlayer.value(name, MotionTrack.Y,
                                        place, now),
                        colour,
                        this.samplePlayer.value(name, MotionTrack.FADE, place,
                                now),
                        this.samplePlayer.value(name, MotionTrack.BRIGHTEN,
                                place, now),
                        this.samplePlayer.value(name, MotionTrack.TURN, place,
                                now),
                        this.samplePlayer.value(name, MotionTrack.STRETCH_X,
                                place, now),
                        this.samplePlayer.value(name, MotionTrack.STRETCH_Y,
                                place, now));
            }
            index++;
        }
    }

    /** Whether a part moves a row of words: it staggers or stretches. */
    private static boolean movesWords(MotionPart part) {
        for (MotionBeat beat : part.beats().values()) {
            if (beat.staggerMillis() > 0
                    || beat.tracks().containsKey(MotionTrack.GAP)) {
                return true;
            }
        }
        return false;
    }

    /** A six-pixel sample block, moved, turned, stretched, faded and lit as a part is. */
    private static void drawBlock(float x, float y, int colour, float fade,
                                  float brighten, float turn, float stretchX,
                                  float stretchY) {
        int alpha = Math.round(255.0F * Math.max(0.0F, Math.min(1.0F, fade)));
        if (alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        int rgb = LostTalesUiInk.blend(LostTalesColors.rgb(colour),
                LostTalesColors.rgb(LostTalesColors.IVORY),
                Math.max(0.0F, Math.min(1.0F, brighten)));
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0.0F);
        GL11.glRotatef(turn, 0.0F, 0.0F, 1.0F);
        GL11.glScalef(stretchX, stretchY, 1.0F);
        Gui.drawRect(-3, -3, 3, 3, LostTalesUiInk.argb(rgb, alpha));
        GL11.glPopMatrix();
    }

    private boolean beginClip(int left, int top, int width, int height) {
        ScaledResolution resolution = new ScaledResolution(this.mc,
                this.mc.displayWidth, this.mc.displayHeight);
        int factor = resolution.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(left * factor, this.mc.displayHeight
                        - (top + height) * factor,
                width * factor, height * factor);
        return true;
    }

    private static void endClip(boolean clipped) {
        if (clipped) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    /* ---- layout ---- */

    private static final class Layout {
        final int listRight;
        final int bottom;
        final int previewTop;
        final int rowsTop;
        final int valueLeft;
        final int valueRight;

        Layout(int width, int height) {
            this.listRight = MARGIN + Math.max(120, Math.min(180, width / 4));
            this.bottom = height - BUTTON_HEIGHT - 16;
            this.previewTop = TOP + 38;
            this.rowsTop = this.previewTop + PREVIEW_HEIGHT + 16;
            this.valueRight = width - MARGIN - 8;
            this.valueLeft = Math.max(this.listRight + MARGIN + 90,
                    this.valueRight - 140);
        }

        int listRows() {
            return Math.max(1, (this.bottom - TOP - 8) / ROW_HEIGHT);
        }

        int visibleRows() {
            return Math.max(1, (this.bottom - this.rowsTop) / ROW_HEIGHT);
        }
    }

    private Layout layout() {
        return new Layout(this.width, this.height);
    }

    /** The list's lines: a header for each family, then its motions. */
    private int listLines() {
        int lines = 0;
        String family = "";
        for (String id : this.ids) {
            if (!Motions.family(id).equals(family)) {
                family = Motions.family(id);
                lines++;
            }
            lines++;
        }
        return lines;
    }

    private int listLineTop(Layout layout, int line) {
        return TOP + 5 + (line - this.listScroll) * ROW_HEIGHT;
    }

    /** The motion under the point in the list, or -1. */
    private int listRowAt(Layout layout, int mouseX, int mouseY) {
        if (mouseX < MARGIN || mouseX >= layout.listRight) {
            return -1;
        }
        String family = "";
        int line = 0;
        for (int index = 0; index < this.ids.size(); index++) {
            String idFamily = Motions.family(this.ids.get(index));
            if (!idFamily.equals(family)) {
                family = idFamily;
                line++;
            }
            int top = listLineTop(layout, line++);
            if (top >= TOP + 4 && top + ROW_HEIGHT <= layout.bottom - 4
                    && LostTalesUiHitBox.contains(mouseX, mouseY, MARGIN + 2,
                            top, layout.listRight - MARGIN - 4, ROW_HEIGHT)) {
                return index;
            }
        }
        return -1;
    }

    private int rowTop(Layout layout, int index) {
        return layout.rowsTop + (index - this.rowScroll) * ROW_HEIGHT;
    }

    private static LostTalesUiHitBox lessBox(Layout layout, int top) {
        return new LostTalesUiHitBox(layout.valueLeft, top, ARROW_WIDTH,
                ROW_HEIGHT);
    }

    private static LostTalesUiHitBox moreBox(Layout layout, int top) {
        return new LostTalesUiHitBox(layout.valueRight - ARROW_WIDTH, top,
                ARROW_WIDTH, ROW_HEIGHT);
    }

    /* ---- rows ---- */

    /** Where a row's value lives: an object of the motion's JSON, made when it is first stepped if it must be. */
    private interface Owner {
        JsonObject get(boolean create);
    }

    private static Owner owner(final JsonObject object) {
        return new Owner() {
            @Override
            public JsonObject get(boolean create) {
                return object;
            }
        };
    }

    /** One line of the editor: a name, a value, and a step either way. */
    private abstract class Row {
        final String label;

        Row(String label) {
            this.label = label;
        }

        abstract String value();

        abstract void step(int direction, boolean fast);

        boolean editable() {
            return true;
        }

        boolean header() {
            return false;
        }
    }

    /** A number, stepped within its bounds. */
    private final class NumberRow extends Row {
        private final Owner owner;
        private final String key;
        private final float step;
        private final float least;
        private final float most;
        private final float fallback;

        NumberRow(String label, Owner owner, String key, float step,
                  float least, float most, float fallback) {
            super(label);
            this.owner = owner;
            this.key = key;
            this.step = step;
            this.least = least;
            this.most = most;
            this.fallback = fallback;
        }

        private float current() {
            return number(this.owner.get(false).get(this.key), this.fallback);
        }

        @Override
        String value() {
            return shown(current());
        }

        @Override
        void step(int direction, boolean fast) {
            float next = current() + direction * this.step * (fast ? 10 : 1);
            next = Math.round(next / this.step) * this.step;
            next = Math.max(this.least, Math.min(this.most, next));
            this.owner.get(true).add(this.key, jsonNumber(next));
            applyWorking();
        }
    }

    /** A curve, stepped through the named ones. */
    private final class CurveRow extends Row {
        private final Owner owner;
        private final String key;
        private final String fallback;

        CurveRow(String label, Owner owner, String key, String fallback) {
            super(label);
            this.owner = owner;
            this.key = key;
            this.fallback = fallback;
        }

        @Override
        String value() {
            String name = text(this.owner.get(false).get(this.key));
            return name.length() == 0 ? this.fallback : name;
        }

        @Override
        void step(int direction, boolean fast) {
            List<MotionCurve> curves = MotionCurve.named();
            MotionCurve current = MotionCurve.parse(value());
            int index = Math.max(0, curves.indexOf(current));
            int next = ((index + direction) % curves.size() + curves.size())
                    % curves.size();
            this.owner.get(true).addProperty(this.key,
                    curves.get(next).name());
            applyWorking();
        }
    }

    /** One of a few words, stepped through them. */
    private abstract class PickRow extends Row {
        private final List<String> choices;
        private final int chosen;

        PickRow(String label, List<String> choices, int chosen) {
            super(label);
            this.choices = choices;
            this.chosen = chosen;
        }

        String label(String choice) {
            return choice;
        }

        abstract void picked(int index);

        @Override
        String value() {
            return label(this.choices.get(this.chosen));
        }

        @Override
        void step(int direction, boolean fast) {
            int size = this.choices.size();
            picked(((this.chosen + direction) % size + size) % size);
        }
    }

    /** A section's header, or a note that is read and not stepped. */
    private final class TextRow extends Row {
        private final boolean header;

        TextRow(String label, boolean header) {
            super(label);
            this.header = header;
        }

        @Override
        String value() {
            return "";
        }

        @Override
        void step(int direction, boolean fast) {
        }

        @Override
        boolean editable() {
            return false;
        }

        @Override
        boolean header() {
            return this.header;
        }
    }

    /* ---- JSON ---- */

    private static List<String> keys(JsonObject object) {
        List<String> keys = new ArrayList<String>();
        if (object != null) {
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                keys.add(entry.getKey());
            }
        }
        return keys;
    }

    private static JsonObject objectOf(JsonObject parent, String key) {
        JsonElement element = parent == null ? null : parent.get(key);
        return element != null && element.isJsonObject()
                ? element.getAsJsonObject() : new JsonObject();
    }

    private static String text(JsonElement element) {
        return element != null && element.isJsonPrimitive()
                ? element.getAsString() : "";
    }

    private static float number(JsonElement element, float fallback) {
        return element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isNumber()
                ? element.getAsFloat() : fallback;
    }

    private static JsonElement copyOf(JsonElement element) {
        return element == null ? new JsonPrimitive(Integer.valueOf(0))
                : element;
    }

    private static JsonPrimitive jsonNumber(float value) {
        if (value == Math.rint(value)) {
            return new JsonPrimitive(Integer.valueOf(Math.round(value)));
        }
        return new JsonPrimitive(Double.valueOf(shown(value)));
    }

    /** A value as the Lab shows it: whole where it is, else to three places. */
    private static String shown(float value) {
        if (value == Math.rint(value)) {
            return String.valueOf(Math.round(value));
        }
        String text = String.valueOf(Math.round(value * 1000.0F) / 1000.0F);
        return text;
    }

    private static float stepFor(float value) {
        float size = Math.abs(value);
        return size >= 10.0F ? 1.0F : size >= 1.0F ? 0.25F : 0.05F;
    }

    private static float trackStep(MotionTrack track) {
        switch (track) {
            case X:
            case Y:
            case GAP:
            case TURN:
                return 0.25F;
            default:
                return 0.05F;
        }
    }

    private static String translate(String key) {
        return StatCollector.translateToLocal("gui.losttales.motionlab."
                + key);
    }
}
