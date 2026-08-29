package com.ninuna.losttales.chat;

import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The roles an account line can show ahead of the sender's name on the
 * account-identity channels — OOC, Operator, Console, whispers, Discord.
 * A role is a presentation fact the server states when it builds the
 * line: which roles, and what colour they and the name take, live here
 * and nowhere else. Roles are cosmetic from the client's point of view;
 * the one with a server-side meaning, {@link #OPERATOR}, is decided by
 * the server's own permission check and only <em>reported</em> through
 * chat, never the other way round.
 *
 * <p>Declaration order is precedence: the first role a sender holds is
 * the <em>primary</em> role and colours the name; every held role is
 * tagged, in this order. {@link #NONE} is the absence of a role and is
 * never tagged. The wire form is a bit set, one bit per role in ordinal
 * order, so roles can be added at the end without disturbing the
 * layout.</p>
 */
public enum ChatAccountRole {
    NONE("", "", 0, false),
    /**
     * A member of the Lost Tales team, recognised by account id. A
     * vanity mark and nothing else: it names nobody the server has
     * business with, so it cannot be addressed.
     */
    DEVELOPER("chat.losttales.tag.developer",
            "chat.losttales.role.developer",
            LostTalesColors.rgb(LostTalesColors.MULBERRY), false),
    /** A server operator, as the server's permission check states it. */
    OPERATOR("chat.losttales.tag.operator",
            "chat.losttales.role.operator",
            LostTalesColors.rgb(LostTalesColors.CRIMSON), true);

    /** Every real role, in precedence order. */
    private static final List<ChatAccountRole> TAGGED = tagged();
    /** Those of them that can be addressed with an {@code @}. */
    private static final List<ChatAccountRole> MENTIONABLE = mentionableRoles();

    /** Roles that can be addressed, in precedence order. */
    public static List<ChatAccountRole> mentionable() {
        return MENTIONABLE;
    }
    /** Every bit a known role occupies. */
    private static final int KNOWN_MASK = knownMask();

    private final String tagKey;
    private final String nameKey;
    private final int color;
    private final boolean mentionable;

    ChatAccountRole(String tagKey, String nameKey, int color,
                    boolean mentionable) {
        this.tagKey = tagKey;
        this.nameKey = nameKey;
        this.color = color & 0xFFFFFF;
        this.mentionable = mentionable;
    }

    /**
     * Whether the role can be addressed with an {@code @}. A role is
     * worth addressing when it names people who are expected to answer;
     * a vanity mark is not, and is only ever worn.
     */
    public boolean isMentionable() {
        return this.mentionable;
    }

    /** Language key of the bracketed tag, empty for {@link #NONE}. */
    public String getTagKey() {
        return this.tagKey;
    }

    /**
     * Language key of the plain role name — the word a player types
     * after an {@code @} to reach everyone holding the role. Bare, so it
     * reads the same in the completion list and in a message.
     */
    public String getNameKey() {
        return this.nameKey;
    }

    /** The role's RGB: its tag and, when primary, the sender's name. */
    public int getColor() {
        return this.color;
    }

    /** The bit this role occupies in a role mask; zero for {@link #NONE}. */
    public int bit() {
        return this == NONE ? 0 : 1 << (ordinal() - 1);
    }

    /** A mask with every given role set; nulls and {@link #NONE} add nothing. */
    public static int maskOf(ChatAccountRole... roles) {
        int mask = 0;
        if (roles != null) {
            for (ChatAccountRole role : roles) {
                if (role != null) {
                    mask |= role.bit();
                }
            }
        }
        return mask;
    }

    /** Whether a mask only names roles this build knows. */
    public static boolean isValidMask(int mask) {
        return (mask & ~KNOWN_MASK) == 0;
    }

    /** The roles set in a mask, in precedence order; unknown bits are ignored. */
    public static List<ChatAccountRole> fromMask(int mask) {
        if ((mask & KNOWN_MASK) == 0) {
            return Collections.emptyList();
        }
        List<ChatAccountRole> held = new ArrayList<ChatAccountRole>(2);
        for (ChatAccountRole role : TAGGED) {
            if ((mask & role.bit()) != 0) {
                held.add(role);
            }
        }
        return held;
    }

    /**
     * The colour an account line's sender name is drawn in: the primary
     * role's, or the chat's plain ivory when the sender holds none.
     * Every account line takes its name colour from here, wherever it is
     * built — the server signing a routed line, the client signing its
     * own half of a conversation nothing is sent for — so one account
     * reads the same in every channel it speaks in.
     */
    public static int nameColor(int mask) {
        ChatAccountRole primary = primary(mask);
        return primary == NONE
                ? LostTalesColors.rgb(LostTalesColors.HUD_LABEL)
                : primary.getColor();
    }

    /** The highest-precedence role in a mask, or {@link #NONE}. */
    public static ChatAccountRole primary(int mask) {
        for (ChatAccountRole role : TAGGED) {
            if ((mask & role.bit()) != 0) {
                return role;
            }
        }
        return NONE;
    }

    private static List<ChatAccountRole> mentionableRoles() {
        List<ChatAccountRole> roles = new ArrayList<ChatAccountRole>();
        for (ChatAccountRole role : TAGGED) {
            if (role.mentionable) {
                roles.add(role);
            }
        }
        return Collections.unmodifiableList(roles);
    }

    private static List<ChatAccountRole> tagged() {
        List<ChatAccountRole> roles = new ArrayList<ChatAccountRole>();
        for (ChatAccountRole role : values()) {
            if (role != NONE) {
                roles.add(role);
            }
        }
        return Collections.unmodifiableList(roles);
    }

    private static int knownMask() {
        int mask = 0;
        for (ChatAccountRole role : values()) {
            mask |= role.bit();
        }
        return mask;
    }
}
