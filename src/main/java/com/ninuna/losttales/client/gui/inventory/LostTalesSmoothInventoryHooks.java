package com.ninuna.losttales.client.gui.inventory;

import com.ninuna.losttales.client.gui.animation.LostTalesGuiEasing;
import com.ninuna.losttales.config.LostTalesConfig;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

/**
 * Client-only bridge that makes a changed stack travel from its previous slot
 * to its new one. The server/container remains the sole owner of inventory
 * state; this class changes only how the already-synchronized stacks render.
 */
public final class LostTalesSmoothInventoryHooks {
    private static final long PENDING_SOURCE_NANOS = 1500000000L;
    private static final long TRANSFER_INTENT_NANOS = 1500000000L;
    private static final int MAX_TRANSFER_SLOT_CHANGES = 12;
    private static GuiContainer currentScreen;
    private static GuiContainer transferScreen;
    private static long transferIntentNanos;
    private static List<Snapshot> previous = new ArrayList<Snapshot>();
    private static final List<Source> pendingSources =
            new ArrayList<Source>();
    private static final Map<Slot, List<Motion>> motions =
            new IdentityHashMap<Slot, List<Motion>>();

    private LostTalesSmoothInventoryHooks() {}

    /**
     * Records the vanilla click mode before it mutates the container.
     * Mode 1 is Shift-click and mode 2 is a 1-9 hotbar swap. Normal pickup,
     * placement, and drag modes deliberately invalidate the travel effect.
     */
    public static void recordTransferIntent(GuiContainer screen, int mode) {
        if (screen != null && isTransferMode(mode)
                && !isCreativeScreen(screen)) {
            transferScreen = screen;
            transferIntentNanos = System.nanoTime();
            return;
        }
        transferScreen = null;
        transferIntentNanos = 0L;
        pendingSources.clear();
        motions.clear();
    }

    /** Captures one coherent container state before vanilla draws its slots. */
    public static void beginFrame(GuiContainer screen) {
        if (screen == null || screen.inventorySlots == null
                || screen.inventorySlots.inventorySlots == null) {
            reset(null);
            return;
        }
        List<Snapshot> current = snapshot(screen);
        if (screen != currentScreen) {
            reset(screen);
            previous = current;
            return;
        }
        if (!LostTalesConfig.enableSmoothInventoryMovement) {
            previous = current;
            motions.clear();
            pendingSources.clear();
            return;
        }

        long now = System.nanoTime();
        prune(now);
        if (!sameState(previous, current)) {
            int changedSlots = changedSlots(previous, current);
            if (isCreativeScreen(screen)
                    || screen != transferScreen
                    || now - transferIntentNanos > TRANSFER_INTENT_NANOS
                    || changedSlots > MAX_TRANSFER_SLOT_CHANGES) {
                pendingSources.clear();
                motions.clear();
            } else {
                resolveChanges(previous, current, now);
            }
            previous = current;
        }
    }

    public static void renderItemAndEffectIntoGUI(
            RenderItem renderer, FontRenderer font,
            TextureManager textures, ItemStack stack, int x, int y,
            GuiContainer screen, Slot slot) {
        if (renderer == null) {
            return;
        }
        Visual visual = visualFor(screen, slot, stack);
        if (visual == null) {
            renderer.renderItemAndEffectIntoGUI(
                    font, textures, stack, x, y);
            return;
        }
        if (visual.base != null && visual.base.stackSize > 0) {
            renderer.renderItemAndEffectIntoGUI(
                    font, textures, visual.base, x, y);
        }
        for (Motion motion : visual.motions) {
            float progress = progress(motion, System.nanoTime());
            if (progress >= 1.0F) {
                continue;
            }
            float eased = ease(progress);
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(
                        motion.deltaX * (1.0F - eased),
                        motion.deltaY * (1.0F - eased), 0.0F);
                renderer.renderItemAndEffectIntoGUI(
                        font, textures, motion.stack, x, y);
            } finally {
                GL11.glPopMatrix();
            }
        }
    }

    public static void renderItemOverlayIntoGUI(
            RenderItem renderer, FontRenderer font,
            TextureManager textures, ItemStack stack, int x, int y,
            String label, GuiContainer screen, Slot slot) {
        if (renderer == null) {
            return;
        }
        Visual visual = visualFor(screen, slot, stack);
        if (visual == null) {
            renderer.renderItemOverlayIntoGUI(
                    font, textures, stack, x, y, label);
            return;
        }
        if (visual.base != null && visual.base.stackSize > 0) {
            renderer.renderItemOverlayIntoGUI(
                    font, textures, visual.base, x, y, null);
        }
        long now = System.nanoTime();
        for (Motion motion : visual.motions) {
            float motionProgress = progress(motion, now);
            if (motionProgress >= 1.0F) {
                continue;
            }
            float eased = ease(motionProgress);
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(
                        motion.deltaX * (1.0F - eased),
                        motion.deltaY * (1.0F - eased), 0.0F);
                renderer.renderItemOverlayIntoGUI(
                        font, textures, motion.stack, x, y, null);
            } finally {
                GL11.glPopMatrix();
            }
        }
    }

    /** Exposed for deterministic tests and shared visual tuning. */
    public static float ease(float progress) {
        return LostTalesGuiEasing.subtleBackOut(progress);
    }

    private static Visual visualFor(
            GuiContainer screen, Slot slot, ItemStack stack) {
        if (!LostTalesConfig.enableSmoothInventoryMovement
                || screen == null || screen != currentScreen
                || slot == null || stack == null
                || stack != slot.getStack()) {
            return null;
        }
        List<Motion> slotMotions = motions.get(slot);
        if (slotMotions == null || slotMotions.isEmpty()) {
            return null;
        }
        int moving = 0;
        long now = System.nanoTime();
        for (Motion motion : slotMotions) {
            if (progress(motion, now) < 1.0F) {
                moving += motion.stack.stackSize;
            }
        }
        if (moving <= 0) {
            motions.remove(slot);
            return null;
        }
        ItemStack base = null;
        int baseCount = Math.max(0, stack.stackSize - moving);
        if (baseCount > 0) {
            base = stack.copy();
            base.stackSize = baseCount;
        }
        return new Visual(base, slotMotions);
    }

    private static void resolveChanges(
            List<Snapshot> oldState, List<Snapshot> newState, long now) {
        List<Source> freshSources = new ArrayList<Source>();
        int count = Math.min(oldState.size(), newState.size());
        for (int index = 0; index < count; index++) {
            Snapshot oldSlot = oldState.get(index);
            Snapshot newSlot = newState.get(index);
            if (!equivalent(oldSlot.stack, newSlot.stack)
                    || size(oldSlot.stack) != size(newSlot.stack)) {
                // A second transfer supersedes any still-running arrival in
                // this destination; never layer a stale item over new state.
                motions.remove(newSlot.slot);
            }
            int retained = equivalent(oldSlot.stack, newSlot.stack)
                    ? Math.min(size(oldSlot.stack), size(newSlot.stack)) : 0;
            int lost = size(oldSlot.stack) - retained;
            if (lost > 0) {
                ItemStack lostStack = oldSlot.stack.copy();
                lostStack.stackSize = lost;
                freshSources.add(new Source(
                        lostStack, oldSlot.x, oldSlot.y, now));
            }
        }
        for (int index = count; index < oldState.size(); index++) {
            Snapshot oldSlot = oldState.get(index);
            if (oldSlot.stack != null) {
                freshSources.add(new Source(
                        oldSlot.stack.copy(), oldSlot.x, oldSlot.y, now));
            }
        }

        List<Source> available = new ArrayList<Source>(pendingSources);
        available.addAll(freshSources);
        pendingSources.clear();
        for (int index = 0; index < newState.size(); index++) {
            Snapshot newSlot = newState.get(index);
            Snapshot oldSlot = index < oldState.size()
                    ? oldState.get(index) : null;
            int retained = oldSlot != null
                    && equivalent(oldSlot.stack, newSlot.stack)
                    ? Math.min(size(oldSlot.stack), size(newSlot.stack)) : 0;
            int gained = size(newSlot.stack) - retained;
            if (gained <= 0 || newSlot.stack == null) {
                continue;
            }
            List<Motion> destination = new ArrayList<Motion>();
            while (gained > 0) {
                Source source = nearestSource(
                        available, newSlot.stack, newSlot.x, newSlot.y);
                if (source == null) {
                    break;
                }
                int amount = Math.min(gained, source.stack.stackSize);
                if (source.x != newSlot.x || source.y != newSlot.y) {
                    ItemStack moving = newSlot.stack.copy();
                    moving.stackSize = amount;
                    destination.add(new Motion(moving,
                            source.x - newSlot.x,
                            source.y - newSlot.y, now));
                }
                gained -= amount;
                source.stack.stackSize -= amount;
                if (source.stack.stackSize <= 0) {
                    available.remove(source);
                }
            }
            if (!destination.isEmpty()) {
                motions.put(newSlot.slot, destination);
            }
        }
        for (Source source : available) {
            if (source.stack.stackSize > 0
                    && now - source.createdNanos
                    <= PENDING_SOURCE_NANOS) {
                pendingSources.add(source);
            }
        }
    }

    private static Source nearestSource(
            List<Source> sources, ItemStack target, int x, int y) {
        Source best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Source source : sources) {
            if (source.stack.stackSize <= 0
                    || !equivalent(source.stack, target)) {
                continue;
            }
            int dx = source.x - x;
            int dy = source.y - y;
            int distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                best = source;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void prune(long now) {
        for (Iterator<Source> iterator = pendingSources.iterator();
             iterator.hasNext();) {
            Source source = iterator.next();
            if (now - source.createdNanos > PENDING_SOURCE_NANOS) {
                iterator.remove();
            }
        }
        for (Iterator<Map.Entry<Slot, List<Motion>>> iterator =
             motions.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<Slot, List<Motion>> entry = iterator.next();
            List<Motion> slotMotions = entry.getValue();
            for (Iterator<Motion> motionIterator = slotMotions.iterator();
                 motionIterator.hasNext();) {
                if (progress(motionIterator.next(), now) >= 1.0F) {
                    motionIterator.remove();
                }
            }
            if (slotMotions.isEmpty()) {
                iterator.remove();
            }
        }
    }

    private static float progress(Motion motion, long now) {
        long duration = Math.max(40,
                LostTalesConfig.smoothInventoryAnimationDurationMillis)
                * 1000000L;
        return LostTalesGuiEasing.clamp(
                (now - motion.startedNanos) / (float)duration);
    }

    private static List<Snapshot> snapshot(GuiContainer screen) {
        List<Snapshot> result = new ArrayList<Snapshot>();
        for (Object value : screen.inventorySlots.inventorySlots) {
            if (!(value instanceof Slot)) {
                continue;
            }
            Slot slot = (Slot)value;
            ItemStack stack = slot.getStack();
            result.add(new Snapshot(slot,
                    stack == null ? null : stack.copy(),
                    slot.xDisplayPosition, slot.yDisplayPosition));
        }
        return result;
    }

    private static boolean sameState(
            List<Snapshot> left, List<Snapshot> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            ItemStack first = left.get(index).stack;
            ItemStack second = right.get(index).stack;
            if (!equivalent(first, second)
                    || size(first) != size(second)) {
                return false;
            }
        }
        return true;
    }

    static boolean isTransferMode(int mode) {
        return mode == 1 || mode == 2;
    }

    static boolean isBulkChange(int changedSlots) {
        return changedSlots > MAX_TRANSFER_SLOT_CHANGES;
    }

    private static int changedSlots(
            List<Snapshot> left, List<Snapshot> right) {
        int changed = Math.abs(left.size() - right.size());
        int count = Math.min(left.size(), right.size());
        for (int index = 0; index < count; index++) {
            ItemStack first = left.get(index).stack;
            ItemStack second = right.get(index).stack;
            if (!equivalent(first, second) || size(first) != size(second)) {
                changed++;
            }
        }
        return changed;
    }

    private static boolean isCreativeScreen(GuiContainer screen) {
        return screen != null && (screen.getClass().getName().equals(
                "net.minecraft.client.gui.inventory.GuiContainerCreative")
                || screen.getClass().getName().endsWith(
                        ".GuiContainerCreative"));
    }

    private static boolean equivalent(ItemStack first, ItemStack second) {
        return first == second || first != null && second != null
                && first.getItem() == second.getItem()
                && first.getItemDamage() == second.getItemDamage()
                && ItemStack.areItemStackTagsEqual(first, second);
    }

    private static int size(ItemStack stack) {
        return stack == null ? 0 : Math.max(0, stack.stackSize);
    }

    private static void reset(GuiContainer screen) {
        currentScreen = screen;
        transferScreen = null;
        transferIntentNanos = 0L;
        previous = new ArrayList<Snapshot>();
        pendingSources.clear();
        motions.clear();
    }

    private static final class Snapshot {
        private final Slot slot;
        private final ItemStack stack;
        private final int x;
        private final int y;

        private Snapshot(Slot slot, ItemStack stack, int x, int y) {
            this.slot = slot;
            this.stack = stack;
            this.x = x;
            this.y = y;
        }
    }

    private static final class Source {
        private final ItemStack stack;
        private final int x;
        private final int y;
        private final long createdNanos;

        private Source(ItemStack stack, int x, int y, long createdNanos) {
            this.stack = stack;
            this.x = x;
            this.y = y;
            this.createdNanos = createdNanos;
        }
    }

    private static final class Motion {
        private final ItemStack stack;
        private final float deltaX;
        private final float deltaY;
        private final long startedNanos;

        private Motion(ItemStack stack, float deltaX, float deltaY,
                       long startedNanos) {
            this.stack = stack;
            this.deltaX = deltaX;
            this.deltaY = deltaY;
            this.startedNanos = startedNanos;
        }
    }

    private static final class Visual {
        private final ItemStack base;
        private final List<Motion> motions;

        private Visual(ItemStack base, List<Motion> motions) {
            this.base = base;
            this.motions = motions;
        }
    }
}
