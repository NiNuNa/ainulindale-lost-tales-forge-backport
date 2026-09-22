package com.ninuna.losttales.client.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.config.LostTalesConfig;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class ChatFeedTypingTest {
    private static final long RISE_NANOS =
            Motions.nanos(MotionIds.CHAT_FEED_TYPING);
    private static final ChatTab GLOBAL = ChatTab.of(ChatChannel.ALL);
    private static final ChatTab PROXIMITY = ChatTab.of(ChatChannel.PROXIMITY);
    private static final ChatTab OOC = ChatTab.of(ChatChannel.OOC);

    @Before
    public void setUp() {
        ClientChatTypingState.clear();
        LostTalesConfig.showChatTypingIndicators = true;
        LostTalesConfig.animations = true;
        LostTalesConfig.reducedMotion = false;
        LostTalesConfig.animationSpeed = 1.0D;
    }

    @After
    public void tearDown() {
        ClientChatTypingState.clear();
        LostTalesConfig.showChatTypingIndicators = true;
        LostTalesConfig.animations = true;
    }

    @Test
    public void onlyConversationsSomeoneTypesIntoAreNamedInTheFeedsOrder() {
        long now = System.nanoTime();
        ClientChatTypingState.apply(OOC, "Steve", true, now);
        ClientChatTypingState.apply(GLOBAL, "Aldric", true, now);
        ClientChatTypingState.apply(GLOBAL, "Mira", true, now);

        List<ChatFeedTyping.Segment> segments = ChatFeedTyping.segments(
                Arrays.asList(GLOBAL, PROXIMITY, OOC));

        assertEquals(2, segments.size());
        assertEquals(GLOBAL, segments.get(0).tab);
        assertEquals(Arrays.asList("Aldric", "Mira"), segments.get(0).names);
        assertEquals(OOC, segments.get(1).tab);
        // A conversation the feed does not carry is never named.
        assertTrue(ChatFeedTyping.segments(
                Collections.singletonList(PROXIMITY)).isEmpty());
    }

    @Test
    public void theTypingSwitchKeepsTheRowAway() {
        ClientChatTypingState.apply(GLOBAL, "Aldric", true, System.nanoTime());
        LostTalesConfig.showChatTypingIndicators = false;

        assertTrue(ChatFeedTyping.segments(
                Collections.singletonList(GLOBAL)).isEmpty());
    }

    @Test
    public void theRowRisesAndKeepsItsWordsWhileItGoesDown() {
        List<ChatFeedTyping.Segment> typing = Collections.singletonList(
                new ChatFeedTyping.Segment(GLOBAL,
                        Collections.singletonList("Aldric")));
        List<ChatFeedTyping.Segment> nobody =
                Collections.<ChatFeedTyping.Segment>emptyList();
        long start = 1000000000L;

        assertEquals(0.0F, ChatFeedTyping.advance(typing, start), 0.0F);
        float halfway = ChatFeedTyping.advance(typing, start + RISE_NANOS / 2);
        assertTrue(halfway > 0.0F && halfway < 1.0F);
        assertEquals(1.0F, ChatFeedTyping.advance(typing, start + RISE_NANOS),
                0.0F);

        long stopped = start + 2 * RISE_NANOS;
        assertEquals(1.0F, ChatFeedTyping.advance(nobody, stopped), 0.0F);
        assertEquals(typing, ChatFeedTyping.shown());
        assertEquals(0.0F, ChatFeedTyping.advance(nobody,
                stopped + RISE_NANOS), 0.0F);
        assertTrue(ChatFeedTyping.shown().isEmpty());
    }

    @Test
    public void withoutAnimationsTheRowStandsAtOnce() {
        LostTalesConfig.animations = false;
        List<ChatFeedTyping.Segment> typing = Collections.singletonList(
                new ChatFeedTyping.Segment(OOC,
                        Collections.singletonList("Steve")));

        assertEquals(1.0F, ChatFeedTyping.advance(typing, 5L), 0.0F);
        assertEquals(0.0F, ChatFeedTyping.advance(
                Collections.<ChatFeedTyping.Segment>emptyList(), 6L), 0.0F);
    }
}
