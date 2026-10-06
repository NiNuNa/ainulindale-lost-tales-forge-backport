package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.client.mapmarker.LostTalesMapMarkerData;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSource;
import com.ninuna.losttales.util.LostTalesLangFile;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.StringTranslate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A marker shared in the chat is offered by the name this game shows, and
 * a token typed with the words the marker was given finds it too; the
 * picker's search answers to both and to the id.
 */
public final class ChatMarkerNamesInThisGameTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String KEY = "lotr.waypoint.LOSTTALES_MOSSY_CAVE";

    @Before
    public void readInAnotherLanguage() {
        inject(KEY + "=Moosige Höhle\n");
    }

    @After
    public void readInEnglishAgain() {
        inject(KEY + "=" + LostTalesLangFile.english().get(KEY) + "\n");
    }

    @Test
    public void aTokenFindsTheMarkerByEitherName() {
        List<ChatShareCandidates.MarkerEntry> entries =
                ChatShareCandidates.markers(Collections.singletonList(cave()));
        assertEquals(1, entries.size());
        ChatShareCandidates.MarkerEntry entry = entries.get(0);
        assertEquals("[m:Moosige Höhle]", entry.token());
        assertTrue(entry.matchesToken(token("[m:Moosige Höhle]")));
        assertTrue(entry.matchesToken(token("[m:mossy cave]")));
        assertFalse(entry.matchesToken(token("[m:Bree]")));
        assertTrue(entry.answers("moos"));
        assertTrue(entry.answers("mossy"));
        assertTrue(entry.answers("losttales:mossy"));
        assertFalse(entry.answers("bree"));
    }

    private static ChatShareTokenParser.Token token(String typed) {
        List<ChatShareTokenParser.Token> tokens = ChatShareTokenParser.parse(typed);
        assertEquals(1, tokens.size());
        assertEquals(ChatShareKind.MARKER, tokens.get(0).kind);
        return tokens.get(0);
    }

    private static LostTalesMapMarkerData cave() {
        return new LostTalesMapMarkerData("losttales:mossy_cave", "Mossy Cave",
                "undiscovered", "gray", "", "", true, 100, -370.0D, 64.0D, 40.0D,
                220.0D, 9.0D, false, true, true, true, 0,
                LostTalesMapMarkerSource.CUSTOM_PRESET);
    }

    private static void inject(String lines) {
        StringTranslate.inject(new ByteArrayInputStream(lines.getBytes(UTF_8)));
    }
}
