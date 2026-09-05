package com.ninuna.losttales.chat;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Which roles a channel asks for, to read it and to send into it. A
 * side with no roles named is open to everyone the channel's own access
 * already admits. The Operator channel asks for the operator role on
 * both sides unless the config says otherwise. The server keeps the
 * gates in force and checks them on every send and delivery; a client
 * only receives the answer for itself, in the chat access packet.
 */
public final class ChatChannelGates {

    /** One channel's requirement: any of the read roles, any of the send roles. */
    public static final class Gate {
        private final Set<String> readRoles;
        private final Set<String> sendRoles;

        public Gate(Set<String> readRoles, Set<String> sendRoles) {
            this.readRoles = Collections.unmodifiableSet(new HashSet<String>(
                    readRoles == null ? Collections.<String>emptySet() : readRoles));
            this.sendRoles = Collections.unmodifiableSet(new HashSet<String>(
                    sendRoles == null ? Collections.<String>emptySet() : sendRoles));
        }

        public Set<String> getReadRoles() {
            return this.readRoles;
        }

        public Set<String> getSendRoles() {
            return this.sendRoles;
        }
    }

    private static final Gate OPEN = new Gate(null, null);
    private static final Gate OPERATORS_ONLY = new Gate(
            Collections.singleton(ChatAccountRole.OPERATOR_ID),
            Collections.singleton(ChatAccountRole.OPERATOR_ID));
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

    /** The Operator channel for operators, everything else open. */
    public static ChatChannelGates defaults() {
        return of(Collections.<ChatChannel, Gate>emptyMap());
    }

    /** The given gates over the defaults. */
    public static ChatChannelGates of(Map<ChatChannel, Gate> configured) {
        Map<ChatChannel, Gate> gates = new HashMap<ChatChannel, Gate>();
        gates.put(ChatChannel.ADMIN, OPERATORS_ONLY);
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
        return holdsAny(roleMask, gateOf(channel).readRoles);
    }

    /** Whether a holder of these roles may send into the channel. */
    public boolean canSend(int roleMask, ChatChannel channel) {
        return holdsAny(roleMask, gateOf(channel).sendRoles);
    }

    /** Whether the channel asks for a role on either side. */
    public boolean isGated(ChatChannel channel) {
        Gate gate = gateOf(channel);
        return !gate.readRoles.isEmpty() || !gate.sendRoles.isEmpty();
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
        if (gate.readRoles.isEmpty() && gate.sendRoles.isEmpty()) {
            return null;
        }
        return channel.getId() + "=read:" + join(gate.readRoles) + ";send:" + join(gate.sendRoles);
    }

    private static String join(Set<String> ids) {
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
