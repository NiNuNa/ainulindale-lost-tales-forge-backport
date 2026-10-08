package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.profanity.ChatProfanityMode;
import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.Settings;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.placement.HudPlacementPage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;

/**
 * The chat's sections of Settings: Chat Settings, after the windows' own,
 * with its Look, Messages, Mentions and Typing, and everyone ignored; and
 * the chat feed's own settings, which every conversation's Chat Feed
 * Settings holds under its three words. A conversation's own Notification
 * Settings and Chat Feed Settings stand in its options. Every Settings has
 * them.
 */
public final class ChatSettingsSections {
    /**
     * The chat's sections, given to every Settings as it is made. The
     * Ignored section's notice shows over the bar of the chat on the
     * screen open, and nowhere where there is none.
     */
    public static final Settings.Sections SECTIONS = new Settings.Sections() {
        @Override
        public void addTo(Settings settings) {
            ChatSettingsSections.addTo(settings, new ChatNoticeSink() {
                @Override
                public void showNotice(String message) {
                    WindowScreen screen = WindowScreen.current();
                    ChatScreenPart part = screen == null ? null
                            : screen.part(ChatScreenPart.class);
                    if (part != null) {
                        part.showNotice(message);
                    }
                }
            });
        }
    };
    /** An ignore's row: an account's by its id, an identity's by its id and name. */
    private static final String IGNORED_ACCOUNT_PREFIX = "ignored:account:";
    private static final String IGNORED_IDENTITY_PREFIX = "ignored:identity:";
    /** The game's chat scale as it ships, which Restore Defaults puts back. */
    private static final float GAME_CHAT_SCALE = 1.0F;

    private ChatSettingsSections() {}

    /**
     * Adds the chat's sections to a screen's Settings: Chat Settings, which
     * every conversation's cog opens. The Ignored section's notice shows
     * over the chat's bar.
     */
    static void addTo(Settings settings, ChatNoticeSink notices) {
        settings.addSection(Settings.Place.CHAT, new LookSection());
        settings.addSection(Settings.Place.CHAT,
                settingsOnly("messages", messages()));
        settings.addSection(Settings.Place.CHAT,
                settingsOnly("mentions", mentions()));
        settings.addSection(Settings.Place.CHAT,
                settingsOnly("typing", typing()));
        settings.addSection(Settings.Place.CHAT, new IgnoredSection(notices));
        settings.addSection(Settings.Place.FEED, new FeedSection());
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
            look.add(new Settings.Colour("chatMentionLineColor",
                    Settings.optionName("chatMentionLineColor")) {
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
                    Settings.optionName("chatSelectedMentionColor")) {
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
                    Settings.optionName("chatReplyHighlightColor")) {
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
            look.add(size("chatSpeakerSize"));
            look.add(size("chatQuoteSize"));
            look.add(new Settings.ModSwitch("enableChatMessageGrouping",
                    Settings.optionName("enableChatMessageGrouping")) {
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

    /* ---- Messages, Mentions, Typing, Chat Feed ---- */

    private static List<Settings.Setting> messages() {
        List<Settings.Setting> messages = new ArrayList<Settings.Setting>();
        messages.add(new Settings.ModSwitch("enableChatEmojis",
                Settings.optionName("enableChatEmojis")) {
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
                Settings.optionName("convertChatEmoticons")) {
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
                Settings.optionName("chatProfanityFilter"),
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
                return word(game().chatVisibility);
            }

            private String word(EntityPlayer.EnumChatVisibility visibility) {
                return StatCollector.translateToLocal(
                        "gui.losttales.chat.settings.visibility."
                                + visibility.name().toLowerCase(Locale.ROOT));
            }

            @Override
            public List<String> words() {
                List<String> words = new ArrayList<String>();
                for (EntityPlayer.EnumChatVisibility visibility
                        : EntityPlayer.EnumChatVisibility.values()) {
                    words.add(word(visibility));
                }
                return words;
            }

            @Override
            public int wordIndex() {
                return game().chatVisibility.ordinal();
            }

            @Override
            public int shippedWordIndex() {
                return EntityPlayer.EnumChatVisibility.FULL.ordinal();
            }

            @Override
            public void pickWord(int index) {
                EntityPlayer.EnumChatVisibility[] all =
                        EntityPlayer.EnumChatVisibility.values();
                if (index >= 0 && index < all.length) {
                    game().chatVisibility = all[index];
                }
            }

            @Override
            public void restore() {
                game().chatVisibility = EntityPlayer.EnumChatVisibility.FULL;
            }
        });
        messages.add(new Settings.ModSwitch("enableNpcChatStyling",
                Settings.optionName("enableNpcChatStyling")) {
            @Override
            protected boolean get() {
                return LostTalesConfig.enableNpcChatStyling;
            }

            @Override
            protected void set(boolean on) {
                LostTalesConfig.enableNpcChatStyling = on;
            }
        });
        messages.add(new Settings.Numeric("npcConversationsOpen",
                Settings.optionName("npcConversationsOpen"), 1.0D, 0) {
            @Override
            protected double get() {
                return LostTalesConfig.npcConversationsOpen;
            }

            @Override
            protected void set(double value) {
                LostTalesConfig.npcConversationsOpen = (int)Math.round(value);
            }
        });
        messages.add(new Settings.ModSwitch("showChatSpeechBubbles",
                Settings.optionName("showChatSpeechBubbles")) {
            @Override
            protected boolean get() {
                return LostTalesConfig.showChatSpeechBubbles;
            }

            @Override
            protected void set(boolean on) {
                LostTalesConfig.showChatSpeechBubbles = on;
            }
        });
        // How many messages the history keeps, within the bounds its
        // option is defined with: a safety bound, stepped by fifty.
        messages.add(new Settings.Numeric("chatHistoryLines",
                Settings.optionName("chatHistoryLines"), 50.0D, 0) {
            @Override
            protected double get() {
                return LostTalesConfig.chatHistoryLines;
            }

            @Override
            protected void set(double value) {
                LostTalesConfig.chatHistoryLines = (int)Math.round(value);
            }
        });
        return messages;
    }

    private static List<Settings.Setting> mentions() {
        List<Settings.Setting> mentions = new ArrayList<Settings.Setting>();
        mentions.add(new Settings.ModSwitch("enableChatPings",
                Settings.optionName("enableChatPings")) {
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
                Settings.optionName("chatPingSound")) {
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
                Settings.optionName("sendChatTypingStatus")) {
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
                Settings.optionName("showChatTypingIndicators")) {
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

    /* ---- The chat feed ---- */

    /**
     * The chat feed's own settings, the same for every conversation: its
     * width, its lines and how long a line stays; how its lines stand and
     * how big their rows are; and where it stands, placed on the HUD
     * Placement page or back at its default place over the hotbar.
     */
    private static final class FeedSection extends Settings.GroupedSection {
        FeedSection() {
            add(new Settings.Numeric("chatFeedWidth",
                    Settings.optionName("chatFeedWidth"), 10.0D, 0) {
                @Override
                protected double get() {
                    return LostTalesConfig.chatFeedWidth;
                }

                @Override
                protected void set(double value) {
                    LostTalesConfig.chatFeedWidth = (int)Math.round(value);
                }
            });
            add(new Settings.Numeric("chatFeedLines",
                    Settings.optionName("chatFeedLines"), 1.0D, 0) {
                @Override
                protected double get() {
                    return LostTalesConfig.chatFeedLines;
                }

                @Override
                protected void set(double value) {
                    LostTalesConfig.chatFeedLines = (int)Math.round(value);
                }
            });
            add(new Settings.Numeric("chatFeedSeconds",
                    Settings.optionName("chatFeedSeconds"), 1.0D, 0) {
                @Override
                protected double get() {
                    return LostTalesConfig.chatFeedSeconds;
                }

                @Override
                protected void set(double value) {
                    LostTalesConfig.chatFeedSeconds = (int)Math.round(value);
                }
            });
            add(new Settings.ModChoice("chatFeedAlignment",
                    Settings.optionName("chatFeedAlignment"),
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
            add(size("chatFeedSpeakerSize"));
            add(size("chatFeedMessageSize"));
            add(size("chatFeedQuoteSize"));
            group("gui.losttales.chat.settings.feed_place");
            add(new Settings.Action("feed_arrange",
                    "gui.losttales.chat.settings.feed_arrange") {
                @Override
                protected void run() {
                    WindowScreen.openPage(HudPlacementPage.PAGE_ID);
                }
            });
            add(new Settings.Action("feed_default_place",
                    "gui.losttales.chat.settings.feed_default_place") {
                @Override
                protected String unavailable() {
                    return ChatLayout.isFeedPlaced() ? ""
                            : StatCollector.translateToLocal(
                                    "gui.losttales.chat.settings.feed_at_default");
                }

                @Override
                protected void run() {
                    ChatLayout.resetFeedPlace();
                }
            });
        }

        @Override
        public String titleKey() {
            return ChatSettingsSections.titleKey("feed");
        }
    }

    /** One of the chat's row sizes: smaller, the same or larger than the words. */
    private static Settings.Setting size(final String key) {
        return new Settings.ModChoice(key, Settings.optionName(key), new String[] {
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
}
