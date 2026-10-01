package com.ninuna.losttales.client.fellowship;

import com.ninuna.losttales.fellowship.model.FellowshipIcon;
import com.ninuna.losttales.fellowship.sync.FellowshipSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipStateSnapshot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The item a fellowship wears, as this client draws it: made once for each
 * icon and kept, since the page and the chat's tabs draw it every frame.
 * An icon of an item this client does not have draws as nothing.
 */
public final class ClientFellowshipIcons {
    private static final Map<FellowshipIcon, ItemStack> STACKS =
            new HashMap<FellowshipIcon, ItemStack>();

    private ClientFellowshipIcons() {}

    /** The item for an icon; null for none, and for an item this client does not have. */
    public static synchronized ItemStack stackOf(FellowshipIcon icon) {
        if (icon == null) {
            return null;
        }
        if (!STACKS.containsKey(icon)) {
            Object item = Item.itemRegistry.getObject(icon.getItemName());
            STACKS.put(icon, item instanceof Item
                    ? new ItemStack((Item)item, 1, icon.getDamage()) : null);
        }
        return STACKS.get(icon);
    }

    /** The item one of the player's fellowships wears; null for none. */
    public static ItemStack stackOf(UUID fellowshipId) {
        FellowshipStateSnapshot state = ClientFellowshipStateCache.getSnapshot();
        FellowshipSnapshot fellowship = state == null ? null : state.getFellowship(fellowshipId);
        return fellowship == null ? null : stackOf(fellowship.getIcon());
    }

    public static synchronized void clear() {
        STACKS.clear();
    }
}
