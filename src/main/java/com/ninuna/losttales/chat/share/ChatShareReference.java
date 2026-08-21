package com.ninuna.losttales.chat.share;

/**
 * What the sending client attaches for one share token: only a lookup key
 * the server re-resolves against its own state — an inventory slot for an
 * item, a marker id for a map marker — never the data itself. A reference
 * whose kind does not match the token at the same position is ignored.
 */
public final class ChatShareReference {
    /** Main inventory (0–35) plus armour (36–39) of {@code InventoryPlayer}. */
    public static final int MAX_ITEM_SLOT = 39;
    /** Placeholder for a token the client could not resolve. */
    public static final int NO_SLOT = 255;
    /** Marker ids are bounded like {@code LostTalesMapMarkerRecord} ids. */
    public static final int MAX_MARKER_ID_BYTES = 256;

    private final ChatShareKind kind;
    private final int slot;
    private final String markerId;

    private ChatShareReference(ChatShareKind kind, int slot,
                               String markerId) {
        this.kind = kind;
        this.slot = slot;
        this.markerId = markerId == null ? "" : markerId;
    }

    public static ChatShareReference item(int slot) {
        if (!isValidItemSlot(slot) && slot != NO_SLOT) {
            throw new IllegalArgumentException("invalid item slot");
        }
        return new ChatShareReference(ChatShareKind.ITEM, slot, "");
    }

    public static ChatShareReference marker(String markerId) {
        String id = markerId == null ? "" : markerId.trim();
        if (id.length() > MAX_MARKER_ID_BYTES) {
            throw new IllegalArgumentException("invalid marker id");
        }
        return new ChatShareReference(ChatShareKind.MARKER, NO_SLOT, id);
    }

    /** The unresolved placeholder for a token of the given kind. */
    public static ChatShareReference unresolved(ChatShareKind kind) {
        return kind == ChatShareKind.MARKER ? marker("") : item(NO_SLOT);
    }

    public static boolean isValidItemSlot(int slot) {
        return slot >= 0 && slot <= MAX_ITEM_SLOT;
    }

    public ChatShareKind getKind() {
        return this.kind;
    }

    /** Inventory slot for an item reference; {@link #NO_SLOT} otherwise. */
    public int getSlot() {
        return this.slot;
    }

    /** Marker id for a marker reference; empty otherwise. */
    public String getMarkerId() {
        return this.markerId;
    }

    public boolean isResolved() {
        return this.kind == ChatShareKind.ITEM
                ? isValidItemSlot(this.slot)
                : this.markerId.length() > 0;
    }
}
