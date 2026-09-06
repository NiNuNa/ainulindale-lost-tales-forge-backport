package com.ninuna.losttales.chat;

import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.permission.LostTalesCapability;
import net.minecraft.util.StatCollector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A role an account line can show ahead of the sender's name on the
 * account-identity channels — OOC, Operator, Console, whispers, Discord
 * — and that a channel can be gated by. A role is a presentation fact
 * the server states when it builds the line: which roles, and what
 * colour they and the name take, live here and nowhere else.
 *
 * <p>One role is built in: the Lost Tales Team mark, a vanity role held
 * by the accounts the code recognises and by nobody else. Every other
 * role comes from the server's config ({@link ChatRoleConfig}) — the
 * operator role included, which is only the entry a fresh file starts
 * with — and reaches clients through the chat access packet. The roles in
 * force are the {@link ChatRoleCatalog}; the wire form of a set of them
 * is a bit set, one bit per role in the catalogue's order, so a role can
 * be added without disturbing the layout. Precedence — which role
 * colours the name, which tag comes first — is the catalogue's order,
 * by rank. Rank is presentation only: what a role lets its holders
 * <em>do</em> is the set of {@link LostTalesCapability} grants the
 * config gives it, read by {@code LostTalesPermissions} on the server
 * and never sent to a client.</p>
 */
public final class ChatAccountRole {

    public static final String TEAM_ID = "team";
    public static final int MAX_ID_LENGTH = 32;
    public static final int MAX_TEXT_LENGTH = 64;
    public static final int MAX_DESCRIPTION_LENGTH = 256;

    /** The absence of a role; never tagged, never a bit. */
    public static final ChatAccountRole NONE = new ChatAccountRole("", -1, "", "", "",
            "", "", 0, false, true, Integer.MAX_VALUE, Collections.<ChatRoleSource>emptyList(),
            null);
    /**
     * A member of the Lost Tales team, recognised by account id in the
     * code and by nothing else. A vanity mark: it names nobody the server
     * has business with, cannot be addressed, grants nothing, and no
     * config or command edits or assigns it.
     */
    public static final ChatAccountRole TEAM = new ChatAccountRole(TEAM_ID, 0,
            "chat.losttales.role.team", "chat.losttales.tag.team", "", "", "",
            LostTalesColors.rgb(LostTalesColors.MULBERRY), false, true, 0,
            Collections.<ChatRoleSource>emptyList(), null);
    private final String id;
    private final int bitIndex;
    private final String nameKey;
    private final String tagKey;
    private final String name;
    private final String tag;
    private final String description;
    private final int color;
    private final boolean mentionable;
    private final boolean locked;
    private final int rank;
    private final List<ChatRoleSource> sources;
    private final Set<LostTalesCapability> grants;

    ChatAccountRole(String id, int bitIndex, String nameKey, String tagKey,
                    String name, String tag, String description, int color,
                    boolean mentionable, boolean locked, int rank,
                    List<ChatRoleSource> sources,
                    Set<LostTalesCapability> grants) {
        this.id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        this.bitIndex = bitIndex;
        this.nameKey = nameKey == null ? "" : nameKey;
        this.tagKey = tagKey == null ? "" : tagKey;
        this.name = clip(name, MAX_TEXT_LENGTH);
        this.tag = clip(tag, MAX_TEXT_LENGTH);
        this.description = clip(description, MAX_DESCRIPTION_LENGTH);
        this.color = color & 0xFFFFFF;
        this.mentionable = mentionable;
        this.locked = locked;
        this.rank = rank;
        this.sources = sources == null ? Collections.<ChatRoleSource>emptyList()
                : Collections.unmodifiableList(new ArrayList<ChatRoleSource>(sources));
        this.grants = grants == null || grants.isEmpty()
                ? Collections.<LostTalesCapability>emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(grants));
    }

    /** The same role at another bit, which is the catalogue's to give. */
    ChatAccountRole withBit(int bitIndex) {
        return new ChatAccountRole(this.id, bitIndex, this.nameKey, this.tagKey, this.name,
                this.tag, this.description, this.color, this.mentionable, this.locked,
                this.rank, this.sources, this.grants);
    }

    /** The same role with another look; what an edit of a built-in changes. */
    public ChatAccountRole withLook(String name, String tag, String description, int color,
                                    boolean mentionable, int rank) {
        return new ChatAccountRole(this.id, this.bitIndex, this.nameKey, this.tagKey, name,
                tag, description, color, mentionable, this.locked, rank, this.sources,
                this.grants);
    }

    /** A config-defined role that grants nothing, before the catalogue gives it a bit. */
    public static ChatAccountRole custom(String id, String name, String tag, String description,
                                         int color, boolean mentionable, int rank,
                                         List<ChatRoleSource> sources) {
        return custom(id, name, tag, description, color, mentionable, rank, sources, null);
    }

    /** A config-defined role with its grants, before the catalogue gives it a bit. */
    public static ChatAccountRole custom(String id, String name, String tag, String description,
                                         int color, boolean mentionable, int rank,
                                         List<ChatRoleSource> sources,
                                         Set<LostTalesCapability> grants) {
        return new ChatAccountRole(id, -1, "", "", name, tag, description, color,
                mentionable, false, rank, sources, grants);
    }

    /**
     * A role as the wire describes it, with its bit already given. The
     * wire carries no grants: what a role allows is the server's alone.
     */
    public static ChatAccountRole fromWire(String id, int bitIndex, String nameKey,
                                           String tagKey, String name, String tag,
                                           String description, int color,
                                           boolean mentionable, boolean locked, int rank) {
        return new ChatAccountRole(id, bitIndex, nameKey, tagKey, name, tag, description,
                color, mentionable, locked, rank, null, null);
    }

    public String getId() {
        return this.id;
    }

    /** Whether this is the absence of a role. */
    public boolean isNone() {
        return this.bitIndex < 0;
    }

    /** The bit this role occupies in a role mask; zero for {@link #NONE}. */
    public int bit() {
        return this.bitIndex < 0 || this.bitIndex >= ChatRoleCatalog.MAX_ROLES
                ? 0 : 1 << this.bitIndex;
    }

    int getBitIndex() {
        return this.bitIndex;
    }

    /**
     * Whether the role can be addressed with an {@code @}. A role is
     * worth addressing when it names people who are expected to answer;
     * a vanity mark is not, and is only ever worn.
     */
    public boolean isMentionable() {
        return this.mentionable;
    }

    /** Whether no config or command may edit, assign or delete the role. */
    public boolean isLocked() {
        return this.locked;
    }

    /** Precedence: lower comes first, colours the name and is tagged first. */
    public int getRank() {
        return this.rank;
    }

    /** Language key of the bracketed tag; empty for a config role. */
    public String getTagKey() {
        return this.tagKey;
    }

    /** Language key of the plain role name; empty for a config role. */
    public String getNameKey() {
        return this.nameKey;
    }

    /** The literal name a config role was given; empty for a built-in. */
    public String getName() {
        return this.name;
    }

    public String getTag() {
        return this.tag;
    }

    /** The literal description; empty for a built-in, whose key describes it. */
    public String getDescription() {
        return this.description;
    }

    /** What the config states grants the role; empty for the team mark. */
    public List<ChatRoleSource> getSources() {
        return this.sources;
    }

    /**
     * The capabilities the config grants the role's holders; empty for
     * both built-ins and for a role read off the wire.
     */
    public Set<LostTalesCapability> getGrants() {
        return this.grants;
    }

    /** Whether the role grants the capability. */
    public boolean grants(LostTalesCapability capability) {
        return capability != null && this.grants.contains(capability);
    }

    /**
     * The plain role name as shown — the word a player types after an
     * {@code @} to reach everyone holding the role: the literal name, or
     * the translated key for a built-in.
     */
    public String getDisplayName() {
        if (this.name.length() > 0) {
            return this.name;
        }
        return this.nameKey.length() == 0 ? "" : StatCollector.translateToLocal(this.nameKey);
    }

    /** The bracketed tag as shown ahead of the sender's name. */
    public String getDisplayTag() {
        if (this.tag.length() > 0) {
            return this.tag;
        }
        if (this.tagKey.length() > 0) {
            return StatCollector.translateToLocal(this.tagKey);
        }
        return this.name.length() > 0 ? "[" + this.name + "]" : "";
    }

    /** The description as shown on the role's card; empty for none. */
    public String getDisplayDescription() {
        if (this.description.length() > 0) {
            return this.description;
        }
        if (this.nameKey.length() == 0) {
            return "";
        }
        String key = this.nameKey + ".description";
        String translated = StatCollector.translateToLocal(key);
        return translated.equals(key) ? "" : translated;
    }

    /** The role's RGB: its tag and, when primary, the sender's name. */
    public int getColor() {
        return this.color;
    }

    /* ---- The catalogue in force ---- */

    /** Every role in force, in precedence order, {@link #NONE} left out. */
    public static List<ChatAccountRole> all() {
        return ChatRoleCatalog.current().roles();
    }

    /** The role with that id, or {@link #NONE}. */
    public static ChatAccountRole byId(String id) {
        ChatAccountRole role = ChatRoleCatalog.current().byId(id);
        return role == null ? NONE : role;
    }

    /** Roles that can be addressed, in precedence order. */
    public static List<ChatAccountRole> mentionable() {
        List<ChatAccountRole> roles = new ArrayList<ChatAccountRole>();
        for (ChatAccountRole role : all()) {
            if (role.mentionable) {
                roles.add(role);
            }
        }
        return roles;
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

    /** Whether a mask only names roles the catalogue in force knows. */
    public static boolean isValidMask(int mask) {
        return (mask & ~ChatRoleCatalog.current().knownMask()) == 0;
    }

    /** The roles set in a mask, in precedence order; unknown bits are ignored. */
    public static List<ChatAccountRole> fromMask(int mask) {
        List<ChatAccountRole> held = new ArrayList<ChatAccountRole>(2);
        for (ChatAccountRole role : all()) {
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
        return primary.isNone()
                ? LostTalesColors.rgb(LostTalesColors.HUD_LABEL)
                : primary.getColor();
    }

    /** The highest-precedence role in a mask, or {@link #NONE}. */
    public static ChatAccountRole primary(int mask) {
        for (ChatAccountRole role : all()) {
            if ((mask & role.bit()) != 0) {
                return role;
            }
        }
        return NONE;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ChatAccountRole
                && ((ChatAccountRole)other).id.equals(this.id)
                && ((ChatAccountRole)other).bitIndex == this.bitIndex;
    }

    @Override
    public int hashCode() {
        return this.id.hashCode() * 31 + this.bitIndex;
    }

    @Override
    public String toString() {
        return this.id.length() == 0 ? "NONE" : this.id;
    }

    private static String clip(String value, int maximum) {
        String text = value == null ? "" : value.trim();
        return text.length() > maximum ? text.substring(0, maximum) : text;
    }
}
