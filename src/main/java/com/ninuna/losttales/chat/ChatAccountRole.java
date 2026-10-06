package com.ninuna.losttales.chat;

import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.util.LostTalesWords;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A role an account holds on the account-identity channels — OOC,
 * Operator, Console, whispers, Discord — which colours the sender's name
 * there, can be mentioned, and can gate a channel. A role is a
 * presentation fact the server states when it builds the line: which
 * roles, and what colour the name takes, live here and nowhere else.
 *
 * <p>One role is built in: the Lost Tales Team mark, a vanity role held
 * by the accounts the code recognises and by nobody else. Every other
 * role comes from the server's config ({@link ChatRoleConfig}) — the
 * operator role included, which is only the entry a fresh file starts
 * with — and reaches clients through the chat access packet. The roles in
 * force are the {@link ChatRoleCatalog}; the wire form of a set of them
 * is a bit set, one bit per role in the catalogue's order, so a role can
 * be added without disturbing the layout. Precedence — which role
 * colours the name — is the catalogue's order, by rank. Rank is presentation only: what a role lets its holders
 * <em>do</em> is the permissions the config grants it, read by
 * {@code LostTalesPermissions} against the permissions in force and
 * never sent to a client.</p>
 *
 * <p>A role wears an icon where its members are listed under it, as a
 * channel wears one on its tab: one of the chat's emoji or an item's
 * icon, written as a channel's is ({@link ChatChannelIconSpec}). A role
 * given none wears the face a channel given none wears.</p>
 *
 * <p>A role the mod ships — the team mark, and a role a fresh file is
 * seeded with while its entry names it nothing — is named by its lang line
 * ({@link #nameKeyOf}), so each game reads it in its own language; a role
 * an operator named is shown as they wrote it, and one named nothing by
 * its id. A mention reaches a role by the name this game shows it by or by
 * its id ({@link #mentionNames}), so {@code @operator} reaches the
 * operators whatever language each of them reads.</p>
 */
public final class ChatAccountRole {

    public static final String TEAM_ID = "team";
    public static final int MAX_ID_LENGTH = 32;
    public static final int MAX_TEXT_LENGTH = 64;
    public static final int MAX_DESCRIPTION_LENGTH = 256;
    /** What a role's lang line begins with, before its id. */
    public static final String NAME_KEY_PREFIX = "chat.losttales.role.";

    /** The absence of a role; never a bit. */
    public static final ChatAccountRole NONE = new ChatAccountRole("", -1, "", "", "", 0,
            false, true, Integer.MAX_VALUE, Collections.<ChatRoleSource>emptyList(), null,
            null);
    /**
     * A member of the Lost Tales team, recognised by account id in the
     * code and by nothing else. A vanity mark: it names nobody the server
     * has business with, cannot be addressed, grants nothing, and no
     * config or command edits or assigns it.
     */
    public static final ChatAccountRole TEAM = new ChatAccountRole(TEAM_ID, 0,
            nameKeyOf(TEAM_ID), "", "",
            LostTalesColors.rgb(LostTalesColors.MULBERRY), false, true, 0,
            Collections.<ChatRoleSource>emptyList(), null,
            ChatChannelIconSpec.parse("emoji:purple_heart"));
    private final String id;
    private final int bitIndex;
    private final String nameKey;
    private final String name;
    private final String description;
    private final int color;
    private final boolean mentionable;
    private final boolean locked;
    private final int rank;
    private final List<ChatRoleSource> sources;
    private final Set<String> grants;
    /** The icon the role wears over its members; null for none chosen. */
    private final ChatChannelIconSpec icon;

    ChatAccountRole(String id, int bitIndex, String nameKey, String name,
                    String description, int color, boolean mentionable,
                    boolean locked, int rank, List<ChatRoleSource> sources,
                    Set<String> grants, ChatChannelIconSpec icon) {
        this.id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        this.bitIndex = bitIndex;
        this.nameKey = nameKey == null ? "" : nameKey;
        this.name = clip(name, MAX_TEXT_LENGTH);
        this.description = clip(description, MAX_DESCRIPTION_LENGTH);
        this.color = color & 0xFFFFFF;
        this.mentionable = mentionable;
        this.locked = locked;
        this.rank = rank;
        this.sources = sources == null ? Collections.<ChatRoleSource>emptyList()
                : Collections.unmodifiableList(new ArrayList<ChatRoleSource>(sources));
        this.grants = Collections.unmodifiableSet(normalized(grants));
        this.icon = icon;
    }

    /** The same role at another bit, which is the catalogue's to give. */
    ChatAccountRole withBit(int bitIndex) {
        return new ChatAccountRole(this.id, bitIndex, this.nameKey, this.name,
                this.description, this.color, this.mentionable, this.locked, this.rank,
                this.sources, this.grants, this.icon);
    }

    /**
     * A config-defined role with its grants, before the catalogue gives it
     * a bit, wearing {@code icon} over its members; null for none chosen.
     */
    public static ChatAccountRole custom(String id, String name, String description,
                                         int color, boolean mentionable, int rank,
                                         List<ChatRoleSource> sources,
                                         Set<String> grants,
                                         ChatChannelIconSpec icon) {
        return new ChatAccountRole(id, -1, "", name, description, color, mentionable,
                false, rank, sources, grants, icon);
    }

    /**
     * A config-defined role the mod ships a name for, its entry naming it
     * nothing: each game names it by its lang line ({@link #nameKeyOf}).
     */
    static ChatAccountRole shipped(String id, String description, int color,
                                   boolean mentionable, int rank,
                                   List<ChatRoleSource> sources, Set<String> grants,
                                   ChatChannelIconSpec icon) {
        return new ChatAccountRole(id, -1, nameKeyOf(id), "", description, color,
                mentionable, false, rank, sources, grants, icon);
    }

    /** The lang line a role the mod ships is named by: {@code chat.losttales.role.operator}. */
    public static String nameKeyOf(String id) {
        return NAME_KEY_PREFIX + (id == null ? "" : id.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * A role as the wire describes it, with its bit already given. The
     * wire carries no grants: what a role allows is the server's alone.
     */
    public static ChatAccountRole fromWire(String id, int bitIndex, String nameKey,
                                           String name, String description, int color,
                                           boolean mentionable, boolean locked, int rank,
                                           ChatChannelIconSpec icon) {
        return new ChatAccountRole(id, bitIndex, nameKey, name, description, color,
                mentionable, locked, rank, null, null, icon);
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

    /** Precedence: lower comes first and colours the name. */
    public int getRank() {
        return this.rank;
    }

    /** The lang line a shipped role is named by; empty for a role its entry names. */
    public String getNameKey() {
        return this.nameKey;
    }

    /** The name a config entry gave the role; empty for one named by its lang line. */
    public String getName() {
        return this.name;
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
     * The permissions the config grants the role's holders, by id, in
     * the order the entry names them; empty for both built-ins and for
     * a role read off the wire. An id naming no permission is read as a
     * capability of that id, and one naming neither is kept and reaches
     * nothing — {@code LostTalesPermissionCatalog} is what resolves them.
     */
    public Set<String> getGrants() {
        return this.grants;
    }

    /**
     * The role's name in {@code words}: the name its entry gave it, a
     * shipped role's lang line, or its id where the lang file has no line.
     */
    public String displayName(LostTalesWords words) {
        if (this.name.length() > 0) {
            return this.name;
        }
        if (this.nameKey.length() == 0) {
            return this.id;
        }
        String said = words.format(this.nameKey);
        return said == null || said.trim().length() == 0 || said.equals(this.nameKey)
                ? this.id : said.trim();
    }

    /** The role's name as this side's game shows it ({@link #displayName}). */
    public String getDisplayName() {
        return displayName(LostTalesWords.LANG);
    }

    /**
     * The names an {@code @} reaches the role by, in {@code words}: the
     * name shown, then the id, which reads the same in every game. Matched
     * whatever their case, so {@code @Operator} is the operator role's id
     * too.
     */
    public List<String> mentionNames(LostTalesWords words) {
        List<String> names = new ArrayList<String>(2);
        String shown = displayName(words);
        if (shown.length() > 0) {
            names.add(shown);
        }
        if (this.id.length() > 0 && !this.id.equalsIgnoreCase(shown)) {
            names.add(this.id);
        }
        return names;
    }

    /** The names an {@code @} reaches the role by in this side's game. */
    public List<String> mentionNames() {
        return mentionNames(LostTalesWords.LANG);
    }

    /** Whether {@code name} after an {@code @} reaches the role here, whatever its case. */
    public boolean answersTo(String name) {
        String wanted = name == null ? "" : name.trim();
        if (wanted.length() == 0) {
            return false;
        }
        for (String own : mentionNames()) {
            if (own.equalsIgnoreCase(wanted)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The role's name as words each reader's game names it, for a line the
     * server sends: a shipped role's lang line, any other as its entry
     * names it.
     */
    public IChatComponent nameComponent() {
        if (this.name.length() == 0 && this.nameKey.length() > 0) {
            return new ChatComponentTranslation(this.nameKey);
        }
        return new ChatComponentText(this.name.length() > 0 ? this.name : this.id);
    }

    /**
     * The description in {@code words}: the one its entry wrote, else the
     * line under a shipped role's name ({@code chat.losttales.role.operator.description});
     * empty for none.
     */
    public String displayDescription(LostTalesWords words) {
        if (this.description.length() > 0) {
            return this.description;
        }
        if (this.nameKey.length() == 0) {
            return "";
        }
        String key = this.nameKey + ".description";
        String said = words.format(key);
        return said == null || said.equals(key) ? "" : said;
    }

    /** The description as shown on the role's card in this side's game; empty for none. */
    public String getDisplayDescription() {
        return displayDescription(LostTalesWords.LANG);
    }

    /** The role's RGB: the sender's name where it is primary, and a mention of it. */
    public int getColor() {
        return this.color;
    }

    /** The icon the role wears over its members; null for none chosen. */
    public ChatChannelIconSpec getIcon() {
        return this.icon;
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

    /**
     * Whether every bit of the mask names a role some catalogue in this
     * JVM knows. Both catalogues are asked: an integrated server shares
     * the JVM with its client, which resets the installed catalogue on
     * its first connect and is told the server's again only after the
     * login replay has gone out — so a kept operator line validated
     * against the installed catalogue alone would fail on the server
     * thread in between, and the encoder would drop the connection.
     */
    public static boolean isValidMask(int mask) {
        int known = ChatRoleCatalog.current().knownMask()
                | ChatRoleCatalog.server().knownMask();
        return (mask & ~known) == 0;
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

    /** The granted ids, trimmed and lower-cased, in the order given, blanks left out. */
    private static Set<String> normalized(Set<String> grants) {
        Set<String> ids = new LinkedHashSet<String>();
        if (grants != null) {
            for (String grant : grants) {
                String id = grant == null ? "" : grant.trim().toLowerCase(Locale.ROOT);
                if (id.length() > 0) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private static String clip(String value, int maximum) {
        String text = value == null ? "" : value.trim();
        return text.length() > maximum ? text.substring(0, maximum) : text;
    }
}
