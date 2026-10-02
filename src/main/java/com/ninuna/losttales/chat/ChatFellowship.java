package com.ninuna.losttales.chat;

import com.ninuna.losttales.fellowship.model.Fellowship;

import java.util.UUID;

/**
 * One fellowship as the chat knows it: its id, the name its conversation
 * is called by, and the colour the player wears in it, which the
 * conversation speaks in.
 */
public final class ChatFellowship {
    private final UUID id;
    private final String name;
    private final int color;

    public ChatFellowship(UUID id, String name, int color) {
        // The colour is red, green and blue alone, nothing in the alpha byte.
        if (id == null || !Fellowship.isWellFormedName(name)
                || (color & 0xFF000000) != 0) {
            throw new IllegalArgumentException("chat fellowship is not well formed");
        }
        this.id = id;
        this.name = name;
        this.color = color;
    }

    /**
     * The fellowship a request's target names: its id written as
     * {@link UUID#toString} writes it, and nothing else; null otherwise.
     */
    public static UUID idOf(String target) {
        if (target == null || target.length() != 36) {
            return null;
        }
        try {
            UUID id = UUID.fromString(target);
            return id.toString().equals(target) ? id : null;
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }

    public UUID getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public int getColor() {
        return this.color;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ChatFellowship)) {
            return false;
        }
        ChatFellowship fellowship = (ChatFellowship)other;
        return this.id.equals(fellowship.id) && this.name.equals(fellowship.name)
                && this.color == fellowship.color;
    }

    @Override
    public int hashCode() {
        return (this.id.hashCode() * 31 + this.name.hashCode()) * 31 + this.color;
    }
}
