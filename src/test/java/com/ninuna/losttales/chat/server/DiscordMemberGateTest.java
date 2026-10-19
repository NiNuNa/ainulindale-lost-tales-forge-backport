package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.compat.discord.LostTalesDiscordBridge;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A Discord member is held to what a player is held to: a mute refuses
 * their lines, edits and reactions, and each of those, and their typing,
 * draws on the budget a player's has. A player is told only the Discord
 * links of the conversations they may read.
 */
public final class DiscordMemberGateTest {

    @After
    public void forget() {
        LostTalesRequestRateLimiter.clear();
    }

    @Test
    public void aMemberPastAPlayersBudgetIsHeldBack() {
        UUID member = UUID.randomUUID();
        for (int line = 0; line < 20; line++) {
            assertTrue(LostTalesChatService.discordMemberWithinBudget(member,
                    LostTalesRequestRateLimiter.RequestType.CHAT_MESSAGE, "Discord line"));
        }
        assertFalse(LostTalesChatService.discordMemberWithinBudget(member,
                LostTalesRequestRateLimiter.RequestType.CHAT_MESSAGE, "Discord line"));
        assertTrue("reactions draw on a budget of their own",
                LostTalesChatService.discordMemberWithinBudget(member,
                        LostTalesRequestRateLimiter.RequestType.CHAT_REACTION,
                        "Discord reaction"));
        assertTrue("another member is not held back",
                LostTalesChatService.discordMemberWithinBudget(UUID.randomUUID(),
                        LostTalesRequestRateLimiter.RequestType.CHAT_MESSAGE,
                        "Discord line"));
    }

    @Test
    public void everyWayInFromDiscordAsksTheMuteAndTheBudget() throws IOException {
        ClassNode service = readClass();
        for (String entry : Arrays.asList("sendFromDiscord", "editFromDiscord",
                "reactFromDiscord", "typingFromDiscord")) {
            Set<String> calls = callsOf(service, entry);
            assertTrue(entry + " asks the mute", calls.contains("isDiscordSenderMuted"));
            assertTrue(entry + " asks the budget",
                    calls.contains("discordMemberWithinBudget"));
        }
        for (String entry : Arrays.asList("sendFromDiscord", "editFromDiscord")) {
            assertTrue(entry + " holds its pings to the budget",
                    callsOf(service, entry).contains("pingsFromDiscord"));
        }
    }

    @Test
    public void aPlayerIsToldOnlyTheLinksOfConversationsTheyRead() {
        final Set<String> read = new HashSet<String>(Arrays.asList(
                "global|", "faction|lotr:gondor"));
        LostTalesChatService.ConversationReader reader =
                new LostTalesChatService.ConversationReader() {
                    @Override
                    public boolean reads(ChatChannel channel, String factionScope) {
                        return read.contains(channel.getId() + "|" + factionScope);
                    }
                };
        assertEquals(Arrays.asList("global", "gondor"), LostTalesChatService.linksReadBy(
                Arrays.asList(
                        new LostTalesDiscordBridge.Link(ChatChannel.GLOBAL, "", "global"),
                        new LostTalesDiscordBridge.Link(ChatChannel.OPERATOR, "", "operator"),
                        new LostTalesDiscordBridge.Link(ChatChannel.FACTION,
                                "lotr:gondor", "gondor"),
                        new LostTalesDiscordBridge.Link(ChatChannel.FACTION,
                                "lotr:mordor", "mordor")),
                reader));
    }

    private static Set<String> callsOf(ClassNode owner, String name) {
        Set<String> calls = new HashSet<String>();
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode)value;
            if (!name.equals(method.name)) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode) {
                    calls.add(((MethodInsnNode)instruction).name);
                }
            }
        }
        return calls;
    }

    private static ClassNode readClass() throws IOException {
        String path = "/com/ninuna/losttales/chat/server/LostTalesChatService.class";
        InputStream stream = DiscordMemberGateTest.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IOException("missing " + path);
        }
        try {
            ClassNode owner = new ClassNode();
            new ClassReader(stream).accept(owner, 0);
            return owner;
        } finally {
            stream.close();
        }
    }
}
