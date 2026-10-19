package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatAction;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.moderation.ChatMuteEntry;
import com.ninuna.losttales.chat.moderation.ChatMuteStorage;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * The one ordered path every chat line passes, whatever its source
 * (CHAT-82): a player's line, the Narrator's, a Discord member's and the
 * Server's go through the same stages in the same order, each rule kept
 * here once, and a new source plugs in by naming itself. NPC lines are the
 * client's own and never reach the server; the client reads them with the
 * same words rule ({@link com.ninuna.losttales.chat.profanity.ChatProfanityFilter}).
 *
 * <ol>
 * <li>{@link Stage#GATE}: who may speak where. A player and the Narrator
 * by the channel's send gate, their identity and the whisper rule; a
 * Discord member by the bridge's inbound policy; the Server always.</li>
 * <li>{@link Stage#MUTE}: a mute stored against the sender's id, a
 * player's account or a Discord member's, refuses the line
 * ({@link #muteOf}); the Server is nobody to mute.</li>
 * <li>{@link Stage#RATE_LIMIT}: a player's and the Narrator's lines are
 * counted at the server's door, as every request is; a Discord member is
 * held to the same budget here ({@link #withinBudget}).</li>
 * <li>{@link Stage#WORDS}: the words a message may carry, an action ended
 * as the sentence it reads as ({@link #words}). Swearing is each reader's
 * to filter as they read (CHAT-60).</li>
 * <li>{@link Stage#MENTIONS}: whom the {@code @names} reach, found among
 * the conversation's members as its member list shows them, for every
 * source alike ({@link ChatMentionTargets}); the roles only a player's own
 * line calls ({@link #callsRoles}).</li>
 * <li>{@link Stage#SHARES}: the things a player's line shares, read from
 * the server's own state; a forward carries its message's; a Discord or
 * Server line shares nothing it did not build itself.</li>
 * <li>{@link Stage#READERS}: each reader is sent their copy: the author
 * their own (the echo name and what they typed), everyone else the shared
 * one (the Narrator's anonymous for all but the narrator), the reactions as
 * each reader is shown them; then the history and the audit keep it, and
 * the bridge carries it on.</li>
 * </ol>
 */
public final class ChatLinePipeline {
    /** Where a line comes from. */
    public enum Source { PLAYER, NARRATOR, DISCORD, SERVER }

    /** One stage of the path, in its order. */
    public enum Stage { GATE, MUTE, RATE_LIMIT, WORDS, MENTIONS, SHARES, READERS }

    /** The stages in the order every line passes them. */
    public static final List<Stage> ORDER = Collections.unmodifiableList(
            Arrays.asList(Stage.values()));

    private ChatLinePipeline() {}

    /**
     * The mute standing against {@code senderId} now, a player's account or
     * a Discord member's id; null for none. A world whose mutes cannot be
     * read silences nobody, and says so once (fail open).
     */
    public static ChatMuteEntry muteOf(UUID senderId) {
        MinecraftServer server = MinecraftServer.getServer();
        if (senderId == null || server == null) {
            return null;
        }
        try {
            return server.worldServerForDimension(0) == null ? null
                    : ChatMuteStorage.get(server.worldServerForDimension(0))
                            .getActiveMute(senderId, System.currentTimeMillis());
        } catch (RuntimeException exception) {
            LostTalesChatService.noteStorageFailure("the mute list", exception);
            return null;
        }
    }

    /** Whether a mute stands against the player's account now. */
    public static boolean isMuted(EntityPlayerMP player) {
        return player != null && muteOf(player.getUniqueID()) != null;
    }

    /**
     * Whether a line of {@code source} is inside its sender's budget: a
     * player's and the Narrator's were counted at the door, the Server has
     * none, a Discord member is held to a player's here.
     */
    public static boolean withinBudget(Source source, UUID senderId,
                                       LostTalesRequestRateLimiter.RequestType type,
                                       String what) {
        if (source != Source.DISCORD) {
            return true;
        }
        if (LostTalesRequestRateLimiter.allowSender(senderId, type)) {
            return true;
        }
        LostTalesRequestRateLimiter.logSenderRateLimited(senderId, type, what);
        return false;
    }

    /**
     * The words a line keeps, or null for words no line may carry: an
     * action as the sentence it reads as ({@link ChatAction#sentence}),
     * any other as it came.
     */
    public static String words(String message, boolean action) {
        String kept = action ? ChatAction.sentence(message) : message;
        return ChatMessageValidator.isValid(kept) ? kept : null;
    }

    /**
     * Whether a line of {@code source} calls the roles its words name: a
     * player's or the Narrator's own line, never a forward, a Discord
     * member's (DISC-19) or the Server's.
     */
    public static boolean callsRoles(Source source, boolean forward) {
        return !forward && (source == Source.PLAYER || source == Source.NARRATOR);
    }

    /** The roles a line calls, by {@link #callsRoles}. */
    static List<String> rolesOf(Source source, boolean forward, String message) {
        return callsRoles(source, forward) ? ChatMentionTargets.rolesOf(message)
                : Collections.<String>emptyList();
    }

    /**
     * Whom a line of a conversation mentions, the same members for every
     * source: those the conversation's member list shows a reader of it,
     * {@code viewer}, here or gone, players or Discord members; none
     * without a reader or for a forward.
     */
    static List<ChatNamedPlayer> membersNamed(EntityPlayerMP viewer, ChatChannel channel,
                                              String scope, String message,
                                              boolean forward) {
        if (forward || viewer == null) {
            return Collections.emptyList();
        }
        return ChatMentionTargets.of(viewer, null, channel, scope, null, message);
    }
}
