package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.profanity.ChatProfanityMode;
import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.Settings;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowTab;
import com.ninuna.losttales.config.LostTalesConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;

/**
 * The chat's sections of Settings, after the windows' own: its Look,
 * Messages, Mentions, Typing and Closed Feed, every channel's switches,
 * everyone ignored, and every shortcut the chat has.
 */
final class ChatSettingsSections {
    /** A channel's switches: the switch, then the tab's id. */
    private static final String CHANNEL_MUTE_PREFIX = "channel:mute:";
    private static final String CHANNEL_PINGS_PREFIX = "channel:pings:";
    private static final String CHANNEL_HIDE_PREFIX = "channel:hide:";
    /** An ignore's row: an account's by its id, an identity's by its id and name. */
    private static final String IGNORED_ACCOUNT_PREFIX = "ignored:account:";
    private static final String IGNORED_IDENTITY_PREFIX = "ignored:identity:";
    /** The game's chat scale as it ships, which Restore Defaults puts back. */
    private static final float GAME_CHAT_SCALE = 1.0F;

    private ChatSettingsSections() {}

    /** Adds the chat's sections to a screen's Settings; the Ignored section's notice shows over its bar. */
    static void addTo(Settings settings, ChatNoticeSink notices) {
        settings.addSection(new LookSection());
        settings.addSection(settingsOnly("messages", messages()));
        settings.addSection(settingsOnly("mentions", mentions()));
        settings.addSection(settingsOnly("typing", typing()));
        settings.addSection(settingsOnly("feed", feed()));
        settings.addSection(new ChannelsSection());
        settings.addSection(new IgnoredSection(notices));
        settings.addSection(new ShortcutsSection());
    }

    private static String titleKey(String id) {
        return "gui.losttales.chat.settings.section." + id;
    }

    /** A section of settings and nothing else. */
    private static Settings.Section settingsOnly(final String id,
                                                 final List<Settings.Setting> held) {
        return new Settings.Section() {
            @Override
            public String titleKey() {
                return ChatSettingsSections.titleKey(id);
            }

            @Override
            public List<Settings.Setting> settings() {
                return held;
            }
        };
    }

    /* ---- Look ---- */

    /**
     * The chat's colours, then its other looks. Chat Scale sizes the
     * chat's words and nothing else; a new scale lays out the game's
     * own lines too.
     */
    private static final class LookSection extends Settings.Section {
        @Override
        public String titleKey() {
            return ChatSettingsSections.titleKey("look");
        }

        @Override
        public List<Settings.Setting> settings() {
            List<Settings.Setting> look = new ArrayList<Settings.Setting>();
            look.add(new Settings.Colour("chatSelectedLineColor",
                    "gui.losttales.chat.settings.color.selected") {
                @Override
                protected String current() {
                    return LostTalesConfig.chatSelectedLineColor;
                }

                @Override
                protected void set(String name) {
                    LostTalesConfig.chatSelectedLineColor = name;
                }
            });
            look.add(new Settings.Colour("chatMentionLineColor",
                    "gui.losttales.chat.settings.color.mention") {
                @Override
                protected String current() {
                    return LostTalesConfig.chatMentionLineColor;
                }

                @Override
                protected void set(String name) {
                    LostTalesConfig.chatMentionLineColor = name;
                }
            });
            // Automatic, the mention colour a shade lighter, is its
            // default; its chip is the colour that comes to now.
            look.add(new Settings.Colour("chatSelectedMentionColor",
                    "gui.losttales.chat.settings.color.selected_mention") {
                @Override
                protected String current() {
                    return LostTalesConfig.chatSelectedMentionColor;
                }

                @Override
                protected void set(String name) {
                    LostTalesConfig.chatSelectedMentionColor = name;
                }

                @Override
                protected int chipRgb() {
                    return LostTalesChatVisualStyle.selectedMentionLineRgb();
                }

                @Override
                protected int automaticRgb() {
                    return LostTalesChatVisualStyle
                            .automaticSelectedMentionRgb();
                }
            });
            look.add(new Settings.Colour("chatReplyHighlightColor",
                    "gui.losttales.chat.settings.color.reply") {
                @Override
                protected String current() {
                    return LostTalesConfig.chatReplyHighlightColor;
                }

                @Override
                protected void set(String name) {
                    LostTalesConfig.chatReplyHighlightColor = name;
                }
            });
            look.add(new Settings.GamePercent("chatScale",
                    "gui.losttales.chat.settings.scale", GAME_CHAT_SCALE) {
                @Override
                protected float get(GameSettings options) {
                    return options.chatScale;
                }

                @Override
                protected void set(GameSettings options, float share) {
                    options.chatScale = share;
                }

                @Override
                public void changed() {
                    Minecraft minecraft = Minecraft.getMinecraft();
                    if (minecraft != null && minecraft.ingameGUI != null) {
                        LostTalesChatHistoryHooks.refresh(
                                minecraft.ingameGUI.getChatGUI());
                    }
                }
            });
            look.add(size("chatSpeakerSize",
                    "gui.losttales.chat.settings.speaker_size"));
            look.add(size("chatQuoteSize",
                    "gui.losttales.chat.settings.quote_size"));
            look.add(new Settings.ModSwitch("enableChatMessageGrouping",
                    "gui.losttales.chat.settings.grouping") {
                @Override
                protected boolean get() {
                    return LostTalesConfig.enableChatMessageGrouping;
                }

                @Override
                protected void set(boolean on) {
                    LostTalesConfig.enableChatMessageGrouping = on;
                }
            });
            return look;
        }

        /** Anything changed in Settings: every window lays its lines out again. */
        @Override
        public void changed() {
            ChatWindowLines.noteMutated();
        }
    }

    /* ---- Messages, Mentions, Typing, Closed Feed ---- */

    private static List<Settings.Setting> messages() {
        List<Settings.Setting> messages = new ArrayList<Settings.Setting>();
        messages.add(new Settings.ModSwitch("enableChatEmojis",
                "gui.losttales.chat.settings.emoji") {
            @Override
            protected boolean get() {
                return LostTalesConfig.enableChatEmojis;
            }

            @Override
            protected void set(boolean on) {
                LostTalesConfig.enableChatEmojis = on;
            }
        });
        messages.add(new Settings.ModSwitch("convertChatEmoticons",
                "gui.losttales.chat.settings.emoticons") {
            @Override
            protected boolean get() {
                return LostTalesConfig.convertChatEmoticons;
            }

            @Override
            protected void set(boolean on) {
                LostTalesConfig.convertChatEmoticons = on;
            }
        });
        messages.add(new Settings.ModChoice("chatProfanityFilter",
                "gui.losttales.chat.settings.profanity",
                ChatProfanityMode.names(),
                "gui.losttales.chat.settings.profanity.") {
            @Override
            protected String get() {
                return ChatProfanityMode.of(LostTalesConfig.chatProfanityFilter,
                        ChatProfanityMode.OFF).name();
            }

            @Override
            protected void set(String word) {
                LostTalesConfig.chatProfanityFilter = word;
            }
        });
        messages.add(new Settings.GameSwitch("chatColours",
                "gui.losttales.chat.settings.colours", true) {
            @Override
            protected boolean get(GameSettings options) {
                return options.chatColours;
            }

            @Override
            protected void set(GameSettings options, boolean on) {
                options.chatColours = on;
            }
        });
        messages.add(new Settings.GameSwitch("chatLinks",
                "gui.losttales.chat.settings.links", true) {
            @Override
            protected boolean get(GameSettings options) {
                return options.chatLinks;
            }

            @Override
            protected void set(GameSettings options, boolean on) {
                options.chatLinks = on;
            }
        });
        messages.add(new Settings.GameSwitch("chatLinksPrompt",
                "gui.losttales.chat.settings.links_prompt", true) {
            @Override
            protected boolean get(GameSettings options) {
                return options.chatLinksPrompt;
            }

            @Override
            protected void set(GameSettings options, boolean on) {
                options.chatLinksPrompt = on;
            }
        });
        messages.add(new Settings.GameSetting("chatVisibility",
                "gui.losttales.chat.settings.visibility") {
            @Override
            public String value() {
                return StatCollector.translateToLocal(
                        "gui.losttales.chat.settings.visibility."
                                + game().chatVisibility.name()
                                        .toLowerCase(Locale.ROOT));
            }

            @Override
            public void step(boolean back) {
                EntityPlayer.EnumChatVisibility[] all =
                        EntityPlayer.EnumChatVisibility.values();
                game().chatVisibility = all[Settings.nextIndex(
                        game().chatVisibility.ordinal(), all.length, back)];
            }

            @Override
            public void restore() {
                game().chatVisibility = EntityPlayer.EnumChatVisibility.FULL;
            }
        });
        messages.add(new Settings.ModSwitch("enableNpcChatStyling",
                "gui.losttales.chat.settings.npc") {
            @Override
            protected boolean get() {
                return LostTalesConfig.enableNpcChatStyling;
            }

            @Override
            protected void set(boolean on) {
                LostTalesConfig.enableNpcChatStyling = on;
            }
        });
        messages.add(new Settings.ModSwitch("showChatSpeechBubbles",
                "gui.losttales.chat.settings.bubbles") {
            @Override
            protected boolean get() {
                return LostTalesConfig.showChatSpeechBubbles;
            }

            @Override
            protected void set(boolean on) {
                LostTalesConfig.showChatSpeechBubbles = on;
            }
        });
        return messages;
    }

    private static List<Settings.Setting> mentions() {
        List<Settings.Setting> mentions = new ArrayList<Settings.Setting>();
        mentions.add(new Settings.ModSwitch("enableChatPings",
                "gui.losttales.chat.settings.pings") {
            @Override
            protected boolean get() {
                return LostTalesConfig.enableChatPings;
            }

            @Override
            protected void set(boolean on) {
                LostTalesConfig.enableChatPings = on;
            }
        });
        // The cue is a sound's name in the file; here it is heard or not,
        // and heard it is the one the mod ships.
        mentions.add(new Settings.ModSwitch("chatPingSound",
                "gui.losttales.chat.settings.ping_sound") {
            @Override
            protected boolean get() {
                return LostTalesConfig.chatPingSound.trim().length() > 0;
            }

            @Override
            protected void set(boolean on) {
                LostTalesConfig.chatPingSound = on ? shipped() : "";
            }

            @Override
            public void restore() {
                LostTalesConfig.chatPingSound = shipped();
            }
        });
        return mentions;
    }

    private static List<Settings.Setting> typing() {
        List<Settings.Setting> typing = new ArrayList<Settings.Setting>();
        typing.add(new Settings.ModSwitch("sendChatTypingStatus",
                "gui.losttales.chat.settings.typing_send") {
            @Override
            protected boolean get() {
                return LostTalesConfig.sendChatTypingStatus;
            }

            @Override
            protected void set(boolean on) {
                LostTalesConfig.sendChatTypingStatus = on;
            }
        });
        typing.add(new Settings.ModSwitch("showChatTypingIndicators",
                "gui.losttales.chat.settings.typing_show") {
            @Override
            protected boolean get() {
                return LostTalesConfig.showChatTypingIndicators;
            }

            @Override
            protected void set(boolean on) {
                LostTalesConfig.showChatTypingIndicators = on;
            }
        });
        return typing;
    }

    private static List<Settings.Setting> feed() {
        List<Settings.Setting> feed = new ArrayList<Settings.Setting>();
        feed.add(new Settings.ModChoice("chatFeedAlignment",
                "gui.losttales.chat.settings.feed_alignment",
                new String[] {"LEFT", "CENTRE", "RIGHT"},
                "gui.losttales.chat.settings.alignment.") {
            @Override
            protected String get() {
                return ChatFeedAlignment.current().name();
            }

            @Override
            protected void set(String word) {
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
    private static Settings.Setting size(final String key, String labelKey) {
        return new Settings.ModChoice(key, labelKey, new String[] {
                LostTalesConfig.CHAT_SIZE_SMALLER, LostTalesConfig.CHAT_SIZE_SAME,
                LostTalesConfig.CHAT_SIZE_LARGER},
                "gui.losttales.chat.settings.size.") {
            @Override
            protected String get() {
                return LostTalesConfig.normalizeSize(sizeValue(key), shipped());
            }

            @Override
            protected void set(String word) {
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

    private static GameSettings game() {
        return Minecraft.getMinecraft().gameSettings;
    }

    /* ---- Channels ---- */

    /**
     * Every channel the player can see, in the order the chat shows
     * them, then every whisper standing in a window, each under its name
     * and icon with its three switches: the same ones its tab's menu has.
     * The whisper channel itself has no tab, only its conversations, so
     * the order the chat shows leaves it out.
     */
    static final class ChannelsSection extends Settings.Section {
        @Override
        public String titleKey() {
            return ChatSettingsSections.titleKey("channels");
        }

        @Override
        public List<MenuWindow.Entry> rows() {
            List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
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

        private static void addChannel(List<MenuWindow.Entry> rows,
                                       ChatTab tab) {
            rows.add(MenuWindow.Entry.group(
                    ClientChatChannelState.displayName(tab), tab,
                    ClientChatChannelState.displayColor(tab)));
            rows.add(new MenuWindow.Entry(CHANNEL_MUTE_PREFIX + tab.id(),
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.settings.channel.mute"))
                    .withValue(Settings.onOff(ChatLayout.isMuted(tab))));
            rows.add(new MenuWindow.Entry(CHANNEL_PINGS_PREFIX + tab.id(),
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.settings.channel.pings"))
                    .withValue(Settings.onOff(ChatLayout.isPingsMuted(tab))));
            rows.add(new MenuWindow.Entry(CHANNEL_HIDE_PREFIX + tab.id(),
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.settings.channel.hide"))
                    .withValue(Settings.onOff(ChatLayout.isHidden(tab))));
        }

        /** A switch flips on a click; a right-click leaves it. */
        @Override
        public boolean take(MenuWindow.Entry entry, boolean back) {
            String id = entry.id;
            ChatTab tab;
            if (id.startsWith(CHANNEL_MUTE_PREFIX)) {
                tab = ChatTab.fromId(id.substring(CHANNEL_MUTE_PREFIX.length()));
                if (tab != null && !back) {
                    ChatLayout.setMuted(tab, !ChatLayout.isMuted(tab));
                }
                return true;
            }
            if (id.startsWith(CHANNEL_PINGS_PREFIX)) {
                tab = ChatTab.fromId(id.substring(CHANNEL_PINGS_PREFIX.length()));
                if (tab != null && !back) {
                    ChatLayout.setPingsMuted(tab, !ChatLayout.isPingsMuted(tab));
                }
                return true;
            }
            if (id.startsWith(CHANNEL_HIDE_PREFIX)) {
                tab = ChatTab.fromId(id.substring(CHANNEL_HIDE_PREFIX.length()));
                if (tab != null && !back) {
                    ChatLayout.setHidden(tab, !ChatLayout.isHidden(tab));
                }
                return true;
            }
            return false;
        }
    }

    /* ---- Ignored ---- */

    /** Everyone ignored, each with the way to stop; a line saying so where nobody is. */
    private static final class IgnoredSection extends Settings.Section {
        private final ChatNoticeSink notices;

        IgnoredSection(ChatNoticeSink notices) {
            this.notices = notices;
        }

        @Override
        public String titleKey() {
            return ChatSettingsSections.titleKey("ignored");
        }

        @Override
        public List<MenuWindow.Entry> rows() {
            List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
            String stop = StatCollector.translateToLocal(
                    "gui.losttales.chat.settings.ignored.stop");
            for (ClientChatIgnores.Ignored ignored : ClientChatIgnores.ignored()) {
                rows.add(new MenuWindow.Entry(ignored.identity
                        ? IGNORED_IDENTITY_PREFIX + ignored.accountId + ":"
                                + ignored.name
                        : IGNORED_ACCOUNT_PREFIX + ignored.accountId,
                        ignored.identity ? StatCollector.translateToLocalFormatted(
                                "gui.losttales.chat.settings.ignored.identity",
                                ignored.name) : ignored.name).withValue(stop));
            }
            if (rows.isEmpty()) {
                rows.add(MenuWindow.Entry.passive(StatCollector.translateToLocal(
                        "gui.losttales.chat.settings.ignored.none")));
            }
            return rows;
        }

        /** A click stops ignoring; a right-click leaves it. */
        @Override
        public boolean take(MenuWindow.Entry entry, boolean back) {
            String id = entry.id;
            if (id.startsWith(IGNORED_ACCOUNT_PREFIX)) {
                if (!back) {
                    stopIgnoring(id.substring(IGNORED_ACCOUNT_PREFIX.length()),
                            null, entry.label);
                }
                return true;
            }
            if (id.startsWith(IGNORED_IDENTITY_PREFIX)) {
                String rest = id.substring(IGNORED_IDENTITY_PREFIX.length());
                int colon = rest.indexOf(':');
                if (colon > 0 && !back) {
                    String name = rest.substring(colon + 1);
                    stopIgnoring(rest.substring(0, colon), name, name);
                }
                return true;
            }
            return false;
        }

        /** Stops ignoring an account, or one identity of it when {@code identity} is named. */
        private void stopIgnoring(String accountId, String identity,
                                  String shown) {
            UUID account;
            try {
                account = UUID.fromString(accountId);
            } catch (IllegalArgumentException unreadable) {
                return;
            }
            boolean stopped = identity == null
                    ? ClientChatIgnores.unignore(account)
                    : ClientChatIgnores.unignoreIdentity(account, identity);
            if (stopped) {
                this.notices.showNotice(StatCollector.translateToLocalFormatted(
                        "gui.losttales.chat.unignored", shown));
            }
        }
    }

    /* ---- Shortcuts ---- */

    /** Every shortcut the chat has, each a row that is read, not taken. */
    private static final class ShortcutsSection extends Settings.Section {
        @Override
        public String titleKey() {
            return ChatSettingsSections.titleKey("shortcuts");
        }

        @Override
        public List<MenuWindow.Entry> rows() {
            return ChatShortcuts.rows();
        }
    }
}
