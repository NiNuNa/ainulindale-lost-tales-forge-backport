package com.ninuna.losttales.chat;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Which roles a channel asks for, to read it and to send into it. A
 * side with no roles named is open to everyone the channel's own access
 * already admits. Every gate comes from the config — the server's file
 * is seeded with the Operator channel asking for the operator role on
 * both sides, and no channel is gated by anything but what the file
 * says. A side that named a role the catalogue does not have is
 * <em>closed</em>: a misspelt role must not open a staff channel to
 * everyone. The server keeps the gates in force and checks them on
 * every send and delivery; a client only receives the answer for
 * itself, in the chat access packet.
 */
public final class ChatChannelGates {

    /**
     * One channel's requirement: any of the read roles, any of the send
     * roles; a side may instead be closed to everyone.
     */
    public static final class Gate {
        private final Set<String> readRoles;
        private final Set<String> sendRoles;
        private final boolean readClosed;
        private final boolean sendClosed;

        public Gate(Set<String> readRoles, Set<String> sendRoles) {
            this(readRoles, sendRoles, false, false);
        }

        public Gate(Set<String> readRoles, Set<String> sendRoles,
                    boolean readClosed, boolean sendClosed) {
            this.readRoles = Collections.unmodifiableSet(new HashSet<String>(
                    readRoles == null ? Collections.<String>emptySet() : readRoles));
            this.sendRoles = Collections.unmodifiableSet(new HashSet<String>(
                    sendRoles == null ? Collections.<String>emptySet() : sendRoles));
            this.readClosed = readClosed;
            this.sendClosed = sendClosed;
        }

        public Set<String> getReadRoles() {
            return this.readRoles;
        }

        public Set<String> getSendRoles() {
            return this.sendRoles;
        }

        /** Whether nobody may read: a side that named a role nothing knows. */
        public boolean isReadClosed() {
            return this.readClosed;
        }

        /** Whether nobody may send. */
        public boolean isSendClosed() {
            return this.sendClosed;
        }

        boolean asksAnything() {
            return this.readClosed || this.sendClosed
                    || !this.readRoles.isEmpty() || !this.sendRoles.isEmpty();
        }
    }

    private static final Gate OPEN = new Gate(null, null);
    private static volatile ChatChannelGates current = defaults();

    private final Map<ChatChannel, Gate> gates;

    private ChatChannelGates(Map<ChatChannel, Gate> gates) {
        this.gates = Collections.unmodifiableMap(new HashMap<ChatChannel, Gate>(gates));
    }

    public static ChatChannelGates current() {
        return current;
    }

    public static void install(ChatChannelGates gates) {
        current = gates == null ? defaults() : gates;
    }

    public static void resetToDefaults() {
        current = defaults();
    }

    /** No gate at all: what stands before a config is read. */
    public static ChatChannelGates defaults() {
        return of(Collections.<ChatChannel, Gate>emptyMap());
    }

    /** Exactly the given gates; every other channel is open. */
    public static ChatChannelGates of(Map<ChatChannel, Gate> configured) {
        Map<ChatChannel, Gate> gates = new HashMap<ChatChannel, Gate>();
        if (configured != null) {
            gates.putAll(configured);
        }
        return new ChatChannelGates(gates);
    }

    public Gate gateOf(ChatChannel channel) {
        Gate gate = channel == null ? null : this.gates.get(channel);
        return gate == null ? OPEN : gate;
    }

    /** Whether a holder of these roles may read the channel. */
    public boolean canRead(int roleMask, ChatChannel channel) {
        Gate gate = gateOf(channel);
        return !gate.readClosed && holdsAny(roleMask, gate.readRoles);
    }

    /** Whether a holder of these roles may send into the channel. */
    public boolean canSend(int roleMask, ChatChannel channel) {
        Gate gate = gateOf(channel);
        return !gate.sendClosed && holdsAny(roleMask, gate.sendRoles);
    }

    /** Whether the channel asks for a role on either side, or is closed on one. */
    public boolean isGated(ChatChannel channel) {
        return gateOf(channel).asksAnything();
    }

    /**
     * Whether the config named this channel at all, which is not the same
     * as gating it: an entry saying {@code any} on both sides is a
     * decision that the channel is open, while no entry is the file
     * saying nothing about it.
     */
    public boolean hasEntry(ChatChannel channel) {
        return channel != null && this.gates.containsKey(channel);
    }

    private static boolean holdsAny(int roleMask, Set<String> roleIds) {
        if (roleIds.isEmpty()) {
            return true;
        }
        for (String id : roleIds) {
            ChatAccountRole role = ChatRoleCatalog.server().byId(id);
            if (role != null && (roleMask & role.bit()) != 0) {
                return true;
            }
        }
        return false;
    }

    /** The config entry a channel's gate is written as; null when open. */
    public static String format(ChatChannel channel, Gate gate) {
        if (!gate.asksAnything()) {
            return null;
        }
        return channel.getId() + "=read:" + join(gate.readRoles, gate.readClosed)
                + ";send:" + join(gate.sendRoles, gate.sendClosed);
    }

    private static String join(Set<String> ids, boolean closed) {
        if (closed) {
            return "none";
        }
        if (ids.isEmpty()) {
            return "any";
        }
        java.util.List<String> sorted = new java.util.ArrayList<String>(ids);
        Collections.sort(sorted);
        StringBuilder joined = new StringBuilder();
        for (String id : sorted) {
            if (joined.length() > 0) {
                joined.append(',');
            }
            joined.append(id);
        }
        return joined.toString();
    }
}
