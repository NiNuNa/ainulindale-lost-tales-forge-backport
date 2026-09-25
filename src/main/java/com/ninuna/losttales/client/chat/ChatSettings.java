package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.profanity.ChatProfanityMode;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowTab;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

/**
 * The Settings window's rows and what they do (Nils, 2026-09-24, S1-S8,
 * and 2026-09-25, G4): every option in one place, each taking effect and
 * saved the moment it changes — a switch flips on a click, a few-word
 * option steps forward on a click and back on a right-click, a colour
 * opens the palette beside the window — in sections under a search
 * field: Windows first, what reaches every window, then the chat's Look,
 * Messages, Mentions, Typing and Closed Feed, every channel's switches,
 * everyone ignored, and every shortcut the chat has. The game's own
 * options read here are written to the game's options, so its own
 * screens and this window always agree. Restore Defaults puts every
 * setting of the first six sections back as the mod and the game ship
 * it, after asking in its own place. A row is acted on by its id, so a
 * list read again as it is typed into acts the same.
 */
final class ChatSettings {
    /** Marks a setting's row; the rest of the id is its key. */
    private static final String SETTING_PREFIX = "setting:";
    /** A channel's switches: the switch, then the tab's id. */
    private static final String CHANNEL_MUTE_PREFIX = "channel:mute:";
    private static final String CHANNEL_PINGS_PREFIX = "channel:pings:";
    private static final String CHANNEL_HIDE_PREFIX = "channel:hide:";
    /** An ignore's row: an account's by its id, an identity's by its id and name. */
    private static final String IGNORED_ACCOUNT_PREFIX = "ignored:account:";
    private static final String IGNORED_IDENTITY_PREFIX = "ignored:identity:";
    private static final String RESTORE = "restore";
    private static final String RESTORE_CONFIRM = "restore_confirm";

    /** The colour rows, each opening the palette for one surface of the chat. */
    static final String COLOR_BACKGROUND = "color_background";
    static final String COLOR_SELECTED = "color_selected";
    static final String COLOR_MENTION = "color_mention";
    static final String COLOR_SELECTED_MENTION = "color_selected_mention";
    static final String COLOR_REPLY = "color_reply";
    /** Marks a palette row; the rest of the id is the palette entry's name. */
    private static final String PALETTE_PREFIX = "palette:";

    /** The game's chat options as it ships them, which Restore Defaults puts back. */
    private static final float GAME_CHAT_SCALE = 1.0F;
    private static final float GAME_CHAT_OPACITY = 1.0F;
    /** The steps the game's scale and opacity take here: a tenth to the whole. */
    private static final int PERCENT_STEP = 10;

    /** One setting: its name, what it reads now, and what a press does to it. */
    private abstract static class Setting {
        final String key;
        final String labelKey;

        Setting(String key, String labelKey) {
            this.key = key;
            this.labelKey = labelKey;
        }

        String label() {
            return StatCollector.translateToLocal(this.labelKey);
        }

        /** The words the row reads for the value now. */
        abstract String value();

        /** A click steps it on; with {@code back}, a right-click, back. */
        abstract void step(boolean back);

        /** The value as the mod or the game ships it. */
        abstract void restore();
    }

    /** A setting of the mod's client file, saved there as it changes. */
    private abstract static class ModSetting extends Setting {
        ModSetting(String key, String labelKey) {
            super(key, labelKey);
        }

        /** The value the file writes as the mod ships it. */
        String shipped() {
            String value = LostTalesConfig.shippedClientValue(this.key);
            return value == null ? "" : value;
        }
    }

    /** An on-and-off option of the mod's. */
    private abstract static class ModSwitch extends ModSetting {
        ModSwitch(String key, String labelKey) {
            super(key, labelKey);
        }

        abstract boolean get();

        abstract void set(boolean on);

        @Override
        String value() {
            return onOff(get());
        }

        @Override
        void step(boolean back) {
            set(!get());
        }

        @Override
        void restore() {
            set(Boolean.parseBoolean(shipped()));
        }
    }

    /** A few-word option of the mod's: one of {@code words}, each read by its lang key. */
    private abstract static class ModChoice extends ModSetting {
        final String[] words;
        final String wordKeyPrefix;

        ModChoice(String key, String labelKey, String[] words,
                  String wordKeyPrefix) {
            super(key, labelKey);
            this.words = words;
            this.wordKeyPrefix = wordKeyPrefix;
        }

        abstract String get();

        abstract void set(String word);

        @Override
        String value() {
            return StatCollector.translateToLocal(this.wordKeyPrefix
                    + get().toLowerCase(Locale.ROOT));
        }

        @Override
        void step(boolean back) {
            set(this.words[nextIndex(indexOf(this.words, get()),
                    this.words.length, back)]);
        }

        @Override
        void restore() {
            String shipped = shipped();
            set(indexOf(this.words, shipped) >= 0 ? shipped : this.words[0]);
        }
    }

    /** An on-and-off option of the game's own, saved to its options. */
    private abstract static class GameSwitch extends Setting {
        final boolean shipped;

        GameSwitch(String key, String labelKey, boolean shipped) {
            super(key, labelKey);
            this.shipped = shipped;
        }

        abstract boolean get(GameSettings options);

        abstract void set(GameSettings options, boolean on);

        @Override
        String value() {
            return onOff(get(game()));
        }

        @Override
        void step(boolean back) {
            set(game(), !get(game()));
        }

        @Override
        void restore() {
            set(game(), this.shipped);
        }
    }

    /** A share of the game's own, a tenth to the whole in steps of a tenth. */
    private abstract static class GamePercent extends Setting {
        final float shipped;

        GamePercent(String key, String labelKey, float shipped) {
            super(key, labelKey);
            this.shipped = shipped;
        }

        abstract float get(GameSettings options);

        abstract void set(GameSettings options, float share);

        @Override
        String value() {
            return StatCollector.translateToLocalFormatted(
                    "gui.losttales.chat.settings.percent",
                    Integer.valueOf(percentOf(get(game()))));
        }

        @Override
        void step(boolean back) {
            int percent = percentOf(get(game()));
            int next = back ? percent - PERCENT_STEP : percent + PERCENT_STEP;
            if (next > 100) {
                next = PERCENT_STEP;
            } else if (next < PERCENT_STEP) {
                next = 100;
            }
            set(game(), next / 100.0F);
        }

        @Override
        void restore() {
            set(game(), this.shipped);
        }
    }

    private final ChatNoticeSink notices;
    /** Whether Restore Defaults is asking before it restores. */
    private boolean confirmingRestore;

    ChatSettings(ChatNoticeSink notices) {
        this.notices = notices;
    }

    /** The window opened afresh: nothing is being asked. */
    void reset() {
        this.confirmingRestore = false;
    }

    /* ---- The settings ---- */

    /**
     * The Windows section's settings, after the windows' colour: what
     * reaches every window. Window Opacity is the game's own chat opacity
     * underneath (G5), so the game's Chat Settings screen agrees with it.
     */
    private static List<Setting> windows() {
        List<Setting> windows = new ArrayList<Setting>();
        windows.add(new GamePercent("chatOpacity",
                "gui.losttales.chat.settings.opacity", GAME_CHAT_OPACITY) {
            @Override
            float get(GameSettings options) {
                return options.chatOpacity;
            }

            @Override
            void set(GameSettings options, float share) {
                options.chatOpacity = share;
            }
        });
        windows.add(new ModSwitch("enableChatBackgroundBlur",
                "gui.losttales.chat.settings.blur") {
            @Override
            boolean get() {
                return LostTalesConfig.enableChatBackgroundBlur;
            }

            @Override
            void set(boolean on) {
                LostTalesConfig.enableChatBackgroundBlur = on;
            }
        });
        windows.add(new ModSwitch("hideHudWhileChatting",
                "gui.losttales.chat.settings.hide_hud") {
            @Override
            boolean get() {
                return LostTalesConfig.hideHudWhileChatting;
            }

            @Override
            void set(boolean on) {
                LostTalesConfig.hideHudWhileChatting = on;
            }
        });
        return windows;
    }

    /**
     * The Look section's settings, after its colours: the chat's own.
     * Chat Scale sizes the chat's words and nothing else (G5).
     */
    private static List<Setting> look() {
        List<Setting> look = new ArrayList<Setting>();
        look.add(new GamePercent("chatScale",
                "gui.losttales.chat.settings.scale", GAME_CHAT_SCALE) {
            @Override
            float get(GameSettings options) {
                return options.chatScale;
            }

            @Override
            void set(GameSettings options, float share) {
                options.chatScale = share;
            }
        });
        look.add(size("chatSpeakerSize", "gui.losttales.chat.settings.speaker_size"));
        look.add(size("chatQuoteSize", "gui.losttales.chat.settings.quote_size"));
        look.add(new ModSwitch("enableChatMessageGrouping",
                "gui.losttales.chat.settings.grouping") {
            @Override
            boolean get() {
                return LostTalesConfig.enableChatMessageGrouping;
            }

            @Override
            void set(boolean on) {
                LostTalesConfig.enableChatMessageGrouping = on;
            }
        });
        return look;
    }

    private static List<Setting> messages() {
        List<Setting> messages = new ArrayList<Setting>();
        messages.add(new ModSwitch("enableChatEmojis",
                "gui.losttales.chat.settings.emoji") {
            @Override
            boolean get() {
                return LostTalesConfig.enableChatEmojis;
            }

            @Override
            void set(boolean on) {
                LostTalesConfig.enableChatEmojis = on;
            }
        });
        messages.add(new ModSwitch("convertChatEmoticons",
                "gui.losttales.chat.settings.emoticons") {
            @Override
            boolean get() {
                return LostTalesConfig.convertChatEmoticons;
            }

            @Override
            void set(boolean on) {
                LostTalesConfig.convertChatEmoticons = on;
            }
        });
        messages.add(new ModChoice("chatProfanityFilter",
                "gui.losttales.chat.settings.profanity",
                ChatProfanityMode.names(),
                "gui.losttales.chat.settings.profanity.") {
            @Override
            String get() {
                return ChatProfanityMode.of(LostTalesConfig.chatProfanityFilter,
                        ChatProfanityMode.OFF).name();
            }

            @Override
            void set(String word) {
                LostTalesConfig.chatProfanityFilter = word;
            }
        });
        messages.add(new GameSwitch("chatColours",
                "gui.losttales.chat.settings.colours", true) {
            @Override
            boolean get(GameSettings options) {
                return options.chatColours;
            }

            @Override
            void set(GameSettings options, boolean on) {
                options.chatColours = on;
            }
        });
        messages.add(new GameSwitch("chatLinks",
                "gui.losttales.chat.settings.links", true) {
            @Override
            boolean get(GameSettings options) {
                return options.chatLinks;
            }

            @Override
            void set(GameSettings options, boolean on) {
                options.chatLinks = on;
            }
        });
        messages.add(new GameSwitch("chatLinksPrompt",
                "gui.losttales.chat.settings.links_prompt", true) {
            @Override
            boolean get(GameSettings options) {
                return options.chatLinksPrompt;
            }

            @Override
            void set(GameSettings options, boolean on) {
                options.chatLinksPrompt = on;
            }
        });
        messages.add(new Setting("chatVisibility",
                "gui.losttales.chat.settings.visibility") {
            @Override
            String value() {
                return StatCollector.translateToLocal(
                        "gui.losttales.chat.settings.visibility."
                                + game().chatVisibility.name()
                                        .toLowerCase(Locale.ROOT));
            }

            @Override
            void step(boolean back) {
                EntityPlayer.EnumChatVisibility[] all =
                        EntityPlayer.EnumChatVisibility.values();
                game().chatVisibility = all[nextIndex(
                        game().chatVisibility.ordinal(), all.length, back)];
            }

            @Override
            void restore() {
                game().chatVisibility = EntityPlayer.EnumChatVisibility.FULL;
            }
        });
        messages.add(new ModSwitch("enableNpcChatStyling",
                "gui.losttales.chat.settings.npc") {
            @Override
            boolean get() {
                return LostTalesConfig.enableNpcChatStyling;
            }

            @Override
            void set(boolean on) {
                LostTalesConfig.enableNpcChatStyling = on;
            }
        });
        messages.add(new ModSwitch("showChatSpeechBubbles",
                "gui.losttales.chat.settings.bubbles") {
            @Override
            boolean get() {
                return LostTalesConfig.showChatSpeechBubbles;
            }

            @Override
            void set(boolean on) {
                LostTalesConfig.showChatSpeechBubbles = on;
            }
        });
        return messages;
    }

    private static List<Setting> mentions() {
        List<Setting> mentions = new ArrayList<Setting>();
        mentions.add(new ModSwitch("enableChatPings",
                "gui.losttales.chat.settings.pings") {
            @Override
            boolean get() {
                return LostTalesConfig.enableChatPings;
            }

            @Override
            void set(boolean on) {
                LostTalesConfig.enableChatPings = on;
            }
        });
        // The cue is a sound's name in the file; here it is heard or not,
        // and heard it is the one the mod ships.
        mentions.add(new ModSwitch("chatPingSound",
                "gui.losttales.chat.settings.ping_sound") {
            @Override
            boolean get() {
                return LostTalesConfig.chatPingSound.trim().length() > 0;
            }

            @Override
            void set(boolean on) {
                LostTalesConfig.chatPingSound = on ? shipped() : "";
            }

            @Override
            void restore() {
                LostTalesConfig.chatPingSound = shipped();
            }
        });
        return mentions;
    }

    private static List<Setting> typing() {
        List<Setting> typing = new ArrayList<Setting>();
        typing.add(new ModSwitch("sendChatTypingStatus",
                "gui.losttales.chat.settings.typing_send") {
            @Override
            boolean get() {
                return LostTalesConfig.sendChatTypingStatus;
            }

            @Override
            void set(boolean on) {
                LostTalesConfig.sendChatTypingStatus = on;
            }
        });
        typing.add(new ModSwitch("showChatTypingIndicators",
                "gui.losttales.chat.settings.typing_show") {
            @Override
            boolean get() {
                return LostTalesConfig.showChatTypingIndicators;
            }

            @Override
            void set(boolean on) {
                LostTalesConfig.showChatTypingIndicators = on;
            }
        });
        return typing;
    }

    private static List<Setting> feed() {
        List<Setting> feed = new ArrayList<Setting>();
        feed.add(new ModChoice("chatFeedAlignment",
                "gui.losttales.chat.settings.feed_alignment",
                new String[] {"LEFT", "CENTRE", "RIGHT"},
                "gui.losttales.chat.settings.alignment.") {
            @Override
            String get() {
                return ChatFeedAlignment.current().name();
            }

            @Override
            void set(String word) {
                LostTalesConfig.chatFeedAlignment = word;
            }
        });
        feed.add(size("chatFeedSpeakerSize",
                "gui.losttales.chat.settings.feed_speaker_size"));
        feed.add(size("chatFeedMessageSize",
                "gui.losttales.chat.settings.feed_message_size"));
        feed.add(size("chatFeedQuoteSize",
                "gui.losttales.chat.settings.feed_quote_size"));
        return feed;
    }

    /** One of the chat's row sizes: smaller, the same or larger than the words. */
    private static Setting size(final String key, String labelKey) {
        return new ModChoice(key, labelKey, new String[] {
                LostTalesConfig.CHAT_SIZE_SMALLER, LostTalesConfig.CHAT_SIZE_SAME,
                LostTalesConfig.CHAT_SIZE_LARGER},
                "gui.losttales.chat.settings.size.") {
            @Override
            String get() {
                return LostTalesConfig.normalizeSize(sizeValue(key), shipped());
            }

            @Override
            void set(String word) {
                setSize(key, word);
            }
        };
    }

    private static String sizeValue(String key) {
        if ("chatSpeakerSize".equals(key)) {
            return LostTalesConfig.chatSpeakerSize;
        }
        if ("chatQuoteSize".equals(key)) {
            return LostTalesConfig.chatQuoteSize;
        }
        if ("chatFeedSpeakerSize".equals(key)) {
            return LostTalesConfig.chatFeedSpeakerSize;
        }
        if ("chatFeedMessageSize".equals(key)) {
            return LostTalesConfig.chatFeedMessageSize;
        }
        return LostTalesConfig.chatFeedQuoteSize;
    }

    private static void setSize(String key, String word) {
        if ("chatSpeakerSize".equals(key)) {
            LostTalesConfig.chatSpeakerSize = word;
        } else if ("chatQuoteSize".equals(key)) {
            LostTalesConfig.chatQuoteSize = word;
        } else if ("chatFeedSpeakerSize".equals(key)) {
            LostTalesConfig.chatFeedSpeakerSize = word;
        } else if ("chatFeedMessageSize".equals(key)) {
            LostTalesConfig.chatFeedMessageSize = word;
        } else {
            LostTalesConfig.chatFeedQuoteSize = word;
        }
    }

    /** Every setting the sections hold, in their order: what Restore Defaults restores. */
    private static List<Setting> all() {
        List<Setting> all = new ArrayList<Setting>();
        all.addAll(windows());
        all.addAll(look());
        all.addAll(messages());
        all.addAll(mentions());
        all.addAll(typing());
        all.addAll(feed());
        return all;
    }

    private static Setting find(String key) {
        for (Setting setting : all()) {
            if (setting.key.equals(key)) {
                return setting;
            }
        }
        return null;
    }

    /* ---- The rows ---- */

    /**
     * The window's rows for what has been typed into its search: every
     * row whose name, value, group or section holds the words, a group
     * kept whole where its name holds them, a section's header over what
     * is left of it, and a line saying so where nothing is.
     */
    List<ChatMenu.Entry> rows(String filter) {
        String wanted = filter == null ? ""
                : filter.trim().toLowerCase(Locale.ROOT);
        List<ChatMenu.Entry> rows = new ArrayList<ChatMenu.Entry>();
        section(rows, "windows", windowRows(), wanted);
        section(rows, "look", lookRows(), wanted);
        section(rows, "messages", settingRows(messages()), wanted);
        section(rows, "mentions", settingRows(mentions()), wanted);
        section(rows, "typing", settingRows(typing()), wanted);
        section(rows, "feed", settingRows(feed()), wanted);
        section(rows, "channels", channelRows(), wanted);
        section(rows, "ignored", ignoredRows(), wanted);
        section(rows, "shortcuts", ChatShortcuts.rows(), wanted);
        section(rows, "defaults", restoreRows(), wanted);
        if (rows.isEmpty()) {
            rows.add(ChatMenu.Entry.passive(StatCollector.translateToLocal(
                    "gui.losttales.chat.settings.none")));
        }
        return rows;
    }

    /**
     * Adds a section's header and the rows of it the search keeps: all
     * of them where the section's own name holds the words; else, of each
     * group, all of it where its name holds them, or the rows that do
     * under the group's name.
     */
    static void section(List<ChatMenu.Entry> rows, String id,
                        List<ChatMenu.Entry> members, String wanted) {
        String title = StatCollector.translateToLocal(
                "gui.losttales.chat.settings.section." + id);
        List<ChatMenu.Entry> kept = new ArrayList<ChatMenu.Entry>();
        boolean whole = wanted.length() == 0 || holds(title, wanted);
        ChatMenu.Entry group = null;
        boolean groupWhole = false;
        boolean groupShown = false;
        for (ChatMenu.Entry member : members) {
            if (member.group) {
                group = member;
                groupWhole = holds(member.label, wanted);
                groupShown = false;
                if (whole || groupWhole) {
                    kept.add(member);
                    groupShown = true;
                }
                continue;
            }
            if (whole || groupWhole || holds(member.label + " "
                    + member.value + " " + keyWords(member.keys), wanted)) {
                if (group != null && !groupShown) {
                    kept.add(group);
                    groupShown = true;
                }
                kept.add(member);
            }
        }
        if (kept.isEmpty()) {
            return;
        }
        rows.add(ChatMenu.Entry.header(title));
        rows.addAll(kept);
    }

    private static boolean holds(String text, String wanted) {
        return wanted.length() == 0
                || text.toLowerCase(Locale.ROOT).contains(wanted);
    }

    /** A shortcut's keys as words the search finds them by, typed text without its italics. */
    private static String keyWords(Object[] keys) {
        StringBuilder words = new StringBuilder();
        for (Object part : keys) {
            words.append(' ').append(part instanceof Integer
                    ? ChatShortcuts.keyName(((Integer)part).intValue())
                    : EnumChatFormatting.getTextWithoutFormattingCodes(
                            String.valueOf(part)));
        }
        return words.toString();
    }

    /** The windows' colour, then what else reaches every window. */
    private static List<ChatMenu.Entry> windowRows() {
        List<ChatMenu.Entry> rows = new ArrayList<ChatMenu.Entry>();
        rows.add(colorRow(COLOR_BACKGROUND));
        rows.addAll(settingRows(windows()));
        return rows;
    }

    /** The chat's colours, then the look's other settings. */
    private static List<ChatMenu.Entry> lookRows() {
        List<ChatMenu.Entry> rows = new ArrayList<ChatMenu.Entry>();
        rows.add(colorRow(COLOR_SELECTED));
        rows.add(colorRow(COLOR_MENTION));
        rows.add(colorRow(COLOR_SELECTED_MENTION));
        rows.add(colorRow(COLOR_REPLY));
        rows.addAll(settingRows(look()));
        return rows;
    }

    private static List<ChatMenu.Entry> settingRows(List<Setting> settings) {
        List<ChatMenu.Entry> rows = new ArrayList<ChatMenu.Entry>(
                settings.size());
        for (Setting setting : settings) {
            rows.add(new ChatMenu.Entry(SETTING_PREFIX + setting.key,
                    setting.label()).withValue(setting.value()));
        }
        return rows;
    }

    /**
     * A colour row: its name, and the colour it comes to now as a chip
     * beside the colour's own name — the selected mention's, while it is
     * automatic, the colour that comes to and the word.
     */
    private static ChatMenu.Entry colorRow(String role) {
        boolean automatic = COLOR_SELECTED_MENTION.equals(role)
                && !LostTalesColors.isPaletteName(currentColorName(role));
        return new ChatMenu.Entry(role,
                StatCollector.translateToLocal(colorLabelKey(role)))
                .withValueChip(COLOR_SELECTED_MENTION.equals(role)
                        ? LostTalesChatVisualStyle.selectedMentionLineRgb()
                        : LostTalesColors.rgb(LostTalesColors.paletteColor(
                                currentColorName(role),
                                LostTalesColors.PLUM_BLACK)))
                .withValue(automatic ? StatCollector.translateToLocal(
                        "gui.losttales.chat.window.color.automatic")
                        : paletteLabel(currentColorName(role)));
    }

    /**
     * Every channel the player can see, in the order the chat shows
     * them, then every whisper standing in a window, each under its name
     * and icon with its three switches: the same ones its tab's menu has.
     * The whisper channel itself has no tab, only its conversations, so
     * the order the chat shows leaves it out.
     */
    static List<ChatMenu.Entry> channelRows() {
        List<ChatMenu.Entry> rows = new ArrayList<ChatMenu.Entry>();
        for (ChatChannel channel : ChatChannel.presentationOrder()) {
            if (ClientChatChannelState.isAvailable(channel)) {
                addChannel(rows, ChatTab.of(channel));
            }
        }
        for (Window window : WindowLayout.windows()) {
            for (WindowTab each : window.getTabs()) {
                ChatTab tab = ChatTab.from(each);
                if (tab != null && tab.isWhisper() && !tab.isNpc()) {
                    addChannel(rows, tab);
                }
            }
        }
        return rows;
    }

    private static void addChannel(List<ChatMenu.Entry> rows, ChatTab tab) {
        rows.add(ChatMenu.Entry.group(ClientChatChannelState.displayName(tab),
                tab, ClientChatChannelState.displayColor(tab)));
        rows.add(new ChatMenu.Entry(CHANNEL_MUTE_PREFIX + tab.id(),
                StatCollector.translateToLocal(
                        "gui.losttales.chat.settings.channel.mute"))
                .withValue(onOff(ChatLayout.isMuted(tab))));
        rows.add(new ChatMenu.Entry(CHANNEL_PINGS_PREFIX + tab.id(),
                StatCollector.translateToLocal(
                        "gui.losttales.chat.settings.channel.pings"))
                .withValue(onOff(ChatLayout.isPingsMuted(tab))));
        rows.add(new ChatMenu.Entry(CHANNEL_HIDE_PREFIX + tab.id(),
                StatCollector.translateToLocal(
                        "gui.losttales.chat.settings.channel.hide"))
                .withValue(onOff(ChatLayout.isHidden(tab))));
    }

    /** Everyone ignored, each with the way to stop; a line saying so where nobody is. */
    private static List<ChatMenu.Entry> ignoredRows() {
        List<ChatMenu.Entry> rows = new ArrayList<ChatMenu.Entry>();
        for (ClientChatIgnores.Ignored ignored : ClientChatIgnores.ignored()) {
            String stop = StatCollector.translateToLocal(
                    "gui.losttales.chat.settings.ignored.stop");
            rows.add(new ChatMenu.Entry(ignored.identity
                    ? IGNORED_IDENTITY_PREFIX + ignored.accountId + ":"
                            + ignored.name
                    : IGNORED_ACCOUNT_PREFIX + ignored.accountId,
                    ignored.identity ? StatCollector.translateToLocalFormatted(
                            "gui.losttales.chat.settings.ignored.identity",
                            ignored.name) : ignored.name).withValue(stop));
        }
        if (rows.isEmpty()) {
            rows.add(ChatMenu.Entry.passive(StatCollector.translateToLocal(
                    "gui.losttales.chat.settings.ignored.none")));
        }
        return rows;
    }

    /** Restore Defaults, or the question it asks in its own place. */
    private List<ChatMenu.Entry> restoreRows() {
        List<ChatMenu.Entry> rows = new ArrayList<ChatMenu.Entry>(1);
        rows.add(this.confirmingRestore
                ? new ChatMenu.Entry(RESTORE_CONFIRM,
                        StatCollector.translateToLocal(
                                "gui.losttales.chat.settings.restore.confirm"))
                : new ChatMenu.Entry(RESTORE, StatCollector.translateToLocal(
                        "gui.losttales.chat.settings.restore")));
        return rows;
    }

    /* ---- Taking a row ---- */

    /**
     * A row taken: a click, or with {@code back} a right-click, which
     * steps a few-word setting back. Answers the colour role whose palette
     * a colour row asks to be opened beside the window, or null.
     */
    String take(ChatMenu.Entry entry, boolean back) {
        String id = entry.id;
        if (!RESTORE.equals(id)) {
            this.confirmingRestore = false;
        }
        if (isColorRow(id)) {
            return back ? null : id;
        }
        if (id.startsWith(SETTING_PREFIX)) {
            Setting setting = find(id.substring(SETTING_PREFIX.length()));
            if (setting != null) {
                setting.step(back);
                applied(setting instanceof ModSetting,
                        !(setting instanceof ModSetting),
                        "chatScale".equals(setting.key));
            }
        } else if (back) {
            return null;
        } else if (id.startsWith(CHANNEL_MUTE_PREFIX)) {
            ChatTab tab = ChatTab.fromId(id.substring(CHANNEL_MUTE_PREFIX.length()));
            if (tab != null) {
                ChatLayout.setMuted(tab, !ChatLayout.isMuted(tab));
            }
        } else if (id.startsWith(CHANNEL_PINGS_PREFIX)) {
            ChatTab tab = ChatTab.fromId(id.substring(CHANNEL_PINGS_PREFIX.length()));
            if (tab != null) {
                ChatLayout.setPingsMuted(tab,
                        !ChatLayout.isPingsMuted(tab));
            }
        } else if (id.startsWith(CHANNEL_HIDE_PREFIX)) {
            ChatTab tab = ChatTab.fromId(id.substring(CHANNEL_HIDE_PREFIX.length()));
            if (tab != null) {
                ChatLayout.setHidden(tab, !ChatLayout.isHidden(tab));
            }
        } else if (id.startsWith(IGNORED_ACCOUNT_PREFIX)) {
            stopIgnoring(id.substring(IGNORED_ACCOUNT_PREFIX.length()), null,
                    entry.label);
        } else if (id.startsWith(IGNORED_IDENTITY_PREFIX)) {
            String rest = id.substring(IGNORED_IDENTITY_PREFIX.length());
            int colon = rest.indexOf(':');
            if (colon > 0) {
                String name = rest.substring(colon + 1);
                stopIgnoring(rest.substring(0, colon), name, name);
            }
        } else if (RESTORE.equals(id)) {
            this.confirmingRestore = true;
        } else if (RESTORE_CONFIRM.equals(id)) {
            this.confirmingRestore = false;
            restoreAll();
        }
        return null;
    }

    /** Stops ignoring an account, or one identity of it when {@code identity} is named. */
    private void stopIgnoring(String accountId, String identity, String shown) {
        UUID account;
        try {
            account = UUID.fromString(accountId);
        } catch (IllegalArgumentException unreadable) {
            return;
        }
        boolean stopped = identity == null ? ClientChatIgnores.unignore(account)
                : ClientChatIgnores.unignoreIdentity(account, identity);
        if (stopped && this.notices != null) {
            this.notices.showNotice(StatCollector.translateToLocalFormatted(
                    "gui.losttales.chat.unignored", shown));
        }
    }

    /** Every setting of the sections back as the mod and the game ship it. */
    private static void restoreAll() {
        for (Setting setting : all()) {
            setting.restore();
        }
        LostTalesConfig.chatBackgroundColor =
                LostTalesConfig.DEFAULT_CHAT_BACKGROUND_COLOR;
        LostTalesConfig.chatSelectedLineColor =
                LostTalesConfig.DEFAULT_CHAT_SELECTED_LINE_COLOR;
        LostTalesConfig.chatMentionLineColor =
                LostTalesConfig.DEFAULT_CHAT_MENTION_LINE_COLOR;
        LostTalesConfig.chatSelectedMentionColor =
                LostTalesConfig.CHAT_COLOR_AUTOMATIC;
        LostTalesConfig.chatReplyHighlightColor =
                LostTalesConfig.DEFAULT_CHAT_REPLY_HIGHLIGHT_COLOR;
        applied(true, true, true);
    }

    /**
     * Writes what changed where it is kept — the mod's client file, the
     * game's options, which tell the server the chat's visibility — and
     * lays every window out again; a new chat scale lays out the game's
     * own lines too.
     */
    private static void applied(boolean mod, boolean game, boolean scale) {
        if (mod) {
            LostTalesConfig.save();
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (game && minecraft != null && minecraft.gameSettings != null) {
            minecraft.gameSettings.saveOptions();
        }
        if (scale && minecraft != null && minecraft.ingameGUI != null) {
            LostTalesChatHistoryHooks.refresh(
                    minecraft.ingameGUI.getChatGUI());
        }
        ChatWindowLines.noteMutated();
    }

    /* ---- The colours and their palette ---- */

    /** Whether the row is one of the colour rows, which open the palette. */
    static boolean isColorRow(String id) {
        return COLOR_BACKGROUND.equals(id) || COLOR_SELECTED.equals(id)
                || COLOR_MENTION.equals(id)
                || COLOR_SELECTED_MENTION.equals(id) || COLOR_REPLY.equals(id);
    }

    /** The words a colour row goes by, which its palette is named after. */
    static String colorLabelKey(String role) {
        if (COLOR_SELECTED.equals(role)) {
            return "gui.losttales.chat.window.color.selected";
        }
        if (COLOR_MENTION.equals(role)) {
            return "gui.losttales.chat.window.color.mention";
        }
        if (COLOR_SELECTED_MENTION.equals(role)) {
            return "gui.losttales.chat.window.color.selected_mention";
        }
        if (COLOR_REPLY.equals(role)) {
            return "gui.losttales.chat.window.color.reply";
        }
        return "gui.losttales.chat.window.color.background";
    }

    /**
     * What a colour's option holds: a palette entry's name, or — for the
     * selected mention — automatic.
     */
    private static String currentColorName(String role) {
        if (COLOR_SELECTED.equals(role)) {
            return LostTalesConfig.chatSelectedLineColor;
        }
        if (COLOR_MENTION.equals(role)) {
            return LostTalesConfig.chatMentionLineColor;
        }
        if (COLOR_SELECTED_MENTION.equals(role)) {
            return LostTalesConfig.chatSelectedMentionColor;
        }
        if (COLOR_REPLY.equals(role)) {
            return LostTalesConfig.chatReplyHighlightColor;
        }
        return LostTalesConfig.chatBackgroundColor;
    }

    /** The colour the mod ships for a colour's surface: its default. */
    private static String shippedColorName(String role) {
        if (COLOR_SELECTED.equals(role)) {
            return LostTalesConfig.DEFAULT_CHAT_SELECTED_LINE_COLOR;
        }
        if (COLOR_MENTION.equals(role)) {
            return LostTalesConfig.DEFAULT_CHAT_MENTION_LINE_COLOR;
        }
        if (COLOR_SELECTED_MENTION.equals(role)) {
            return LostTalesConfig.CHAT_COLOR_AUTOMATIC;
        }
        if (COLOR_REPLY.equals(role)) {
            return LostTalesConfig.DEFAULT_CHAT_REPLY_HIGHLIGHT_COLOR;
        }
        return LostTalesConfig.DEFAULT_CHAT_BACKGROUND_COLOR;
    }

    /** A palette entry's name as the language file gives it. */
    private static String paletteLabel(String name) {
        String key = "losttales.palette." + name.toLowerCase(Locale.ROOT);
        String label = StatCollector.translateToLocal(key);
        return key.equals(label) ? name : label;
    }

    /**
     * The palette for one of the colours: every entry as a chip beside its
     * name, the one in use named in honey and the one the mod ships marked
     * as the default — for the selected mention, the automatic choice
     * before them, which is its default. Choosing one is the whole change:
     * the option is written to the client file and every window is drawn
     * in it from the next frame, and the palette stays for the next.
     */
    static List<ChatMenu.Entry> paletteRows(String role) {
        String current = currentColorName(role);
        String shipped = shippedColorName(role);
        String[] names = LostTalesColors.paletteNames();
        List<ChatMenu.Entry> entries =
                new ArrayList<ChatMenu.Entry>(names.length + 1);
        if (COLOR_SELECTED_MENTION.equals(role)) {
            // Automatic first: the mention colour a shade lighter,
            // chipped in the colour that comes to now.
            ChatMenu.Entry automatic = new ChatMenu.Entry(
                    PALETTE_PREFIX + LostTalesConfig.CHAT_COLOR_AUTOMATIC,
                    StatCollector.translateToLocalFormatted(
                            "gui.losttales.chat.window.color.default",
                            StatCollector.translateToLocal(
                                    "gui.losttales.chat.window.color.automatic")),
                    false,
                    LostTalesChatVisualStyle.automaticSelectedMentionRgb(),
                    null).asChip();
            if (!LostTalesColors.isPaletteName(current)) {
                automatic.withLabelColor(
                        LostTalesColors.rgb(LostTalesColors.HONEY));
            }
            entries.add(automatic);
        }
        for (int index = 0; index < names.length; index++) {
            String label = paletteLabel(names[index]);
            ChatMenu.Entry entry = new ChatMenu.Entry(
                    PALETTE_PREFIX + names[index],
                    names[index].equalsIgnoreCase(shipped)
                            ? StatCollector.translateToLocalFormatted(
                                    "gui.losttales.chat.window.color.default",
                                    label)
                            : label,
                    false, LostTalesColors.rgb(LostTalesColors.paletteColor(
                            names[index], LostTalesColors.PLUM_BLACK)),
                    null).asChip();
            if (names[index].equalsIgnoreCase(current)) {
                entry.withLabelColor(LostTalesColors.rgb(LostTalesColors.HONEY));
            }
            entries.add(entry);
        }
        return entries;
    }

    /** One row of the palette: the chosen colour becomes the surface's. */
    static void chooseColor(String role, ChatMenu.Entry entry) {
        if (!entry.id.startsWith(PALETTE_PREFIX)) {
            return;
        }
        String name = entry.id.substring(PALETTE_PREFIX.length());
        boolean automatic = LostTalesConfig.CHAT_COLOR_AUTOMATIC.equals(name)
                && COLOR_SELECTED_MENTION.equals(role);
        if (!automatic && !LostTalesColors.isPaletteName(name)) {
            return;
        }
        if (COLOR_SELECTED.equals(role)) {
            LostTalesConfig.chatSelectedLineColor = name;
        } else if (COLOR_MENTION.equals(role)) {
            LostTalesConfig.chatMentionLineColor = name;
        } else if (COLOR_SELECTED_MENTION.equals(role)) {
            LostTalesConfig.chatSelectedMentionColor = name;
        } else if (COLOR_REPLY.equals(role)) {
            LostTalesConfig.chatReplyHighlightColor = name;
        } else {
            LostTalesConfig.chatBackgroundColor = name;
        }
        LostTalesConfig.save();
    }

    /* ---- Small helpers ---- */

    private static GameSettings game() {
        return Minecraft.getMinecraft().gameSettings;
    }

    private static String onOff(boolean on) {
        return StatCollector.translateToLocal(on
                ? "gui.losttales.chat.settings.on"
                : "gui.losttales.chat.settings.off");
    }

    /** A share as the whole percent a tenth's step lands on. */
    static int percentOf(float share) {
        int percent = Math.round(share * 100.0F / PERCENT_STEP) * PERCENT_STEP;
        return Math.max(PERCENT_STEP, Math.min(100, percent));
    }

    /** The index a step lands on among {@code count}, round from either end. */
    static int nextIndex(int index, int count, boolean back) {
        if (count <= 0) {
            return 0;
        }
        int start = index < 0 ? (back ? 0 : count - 1) : index;
        return ((back ? start - 1 : start + 1) % count + count) % count;
    }

    private static int indexOf(String[] words, String word) {
        for (int index = 0; index < words.length; index++) {
            if (words[index].equalsIgnoreCase(word)) {
                return index;
            }
        }
        return -1;
    }
}
