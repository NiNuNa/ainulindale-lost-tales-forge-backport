package com.ninuna.losttales.command;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.kit.StartingKits;
import com.ninuna.losttales.character.lore.LoreCharacterRegistry;
import com.ninuna.losttales.character.lore.sync.LoreCharacterSyncManager;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.server.CharacterRaceCatalog;
import com.ninuna.losttales.character.server.CharacterRaceCatalogSync;
import com.ninuna.losttales.character.server.CharacterRaceGameplayHandler;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerCatalog;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerFiles;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerStorage;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSyncManager;
import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.permission.LostTalesPermissions;
import com.ninuna.losttales.progression.ProgressionCatalog;
import com.ninuna.losttales.progression.ProgressionSync;
import com.ninuna.losttales.quest.LostTalesQuestRegistry;
import com.ninuna.losttales.quest.ServerQuestSync;
import com.ninuna.losttales.quest.missive.MissiveTemplates;
import com.ninuna.losttales.util.LostTalesLog;
import com.ninuna.losttales.world.waystone.LostTalesWaystoneStructureRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldServer;

/**
 * {@code /losttales reload <kind|all>} (DATA-11): reads one kind of
 * content again, bundled and the server's own, as the server reads it as
 * it starts, and answers with each file's warnings ({@link ReloadAnswer}).
 * Each kind needs its own system's capability, and {@code all} the
 * server's settings' ({@link ReloadKind}). What a client holds a copy of
 * goes to every player online again: the quests their logs name, the
 * skills, the markers, the races and capes, each player's lore list. After
 * the races every player online is sized again to the race they play, on
 * the server thread the command runs on; a player whose race no file has
 * any more plays as the body it stood on.
 */
public final class LostTalesCommandReload extends LostTalesCommandBase {

    /** What the lang key of each of the command's own answers begins with. */
    static final String SAY = "chat.losttales.command.reload.";

    public LostTalesCommandReload() {
        super("reload");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/losttales reload <quests|skills|markers|races|capes|kits|missives|lore|all>";
    }

    /**
     * Open to whoever may read at least one kind again; each kind asks for
     * its own capability before it is read.
     */
    @Override
    public boolean canCommandSenderUseCommand(final ICommandSender sender) {
        return !ReloadKind.wordsFor(holder(sender)).isEmpty();
    }

    /** The level that reads some kind again with no role: an operator's. */
    @Override
    public int getRequiredPermissionLevel() {
        return ReloadKind.lowestOperatorLevel();
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args == null || args.length != 1 || sender == null
                || sender.getEntityWorld() == null || sender.getEntityWorld().isRemote) {
            sendUsage(sender);
            return;
        }
        LostTalesCapability needed = ReloadKind.capabilityFor(args[0]);
        if (needed == null) {
            sendUsage(sender);
            return;
        }
        if (!LostTalesPermissions.has(sender, needed)) {
            say(sender, EnumChatFormatting.RED, "commands.generic.permission");
            return;
        }
        final MinecraftServer server = MinecraftServer.getServer();
        final WorldServer overworld = server == null ? null : server.worldServerForDimension(0);
        if (overworld == null) {
            say(sender, EnumChatFormatting.RED, SAY + "no_world");
            return;
        }
        List<ReloadAnswer.Outcome> outcomes = ReloadAnswer.run(ReloadKind.asked(args[0]),
                new ReloadAnswer.Reader() {
                    @Override
                    public ReloadAnswer.Outcome read(ReloadKind kind) {
                        return LostTalesCommandReload.read(kind, overworld);
                    }

                    @Override
                    public void spread(ReloadKind kind) {
                        LostTalesCommandReload.spread(kind, server, overworld);
                    }
                });
        for (IChatComponent line : ReloadAnswer.lines(outcomes)) {
            sender.addChatMessage(line);
        }
    }

    /** Reads {@code kind} again and puts it in place; what it holds now and every reason a file was left out. */
    private static ReloadAnswer.Outcome read(ReloadKind kind, WorldServer overworld) {
        switch (kind) {
            case QUESTS: {
                List<String> reasons = LostTalesQuestRegistry.reload();
                int own = LostTalesQuestRegistry.serverQuestIds().size();
                return ReloadAnswer.Outcome.read(kind, reasons,
                        Integer.valueOf(LostTalesQuestRegistry.bundledQuestCount() + own),
                        Integer.valueOf(own));
            }
            case SKILLS: {
                List<String> reasons = ProgressionCatalog.load();
                return ReloadAnswer.Outcome.read(kind, reasons,
                        Integer.valueOf(ProgressionCatalog.skills().size()),
                        Integer.valueOf(ProgressionCatalog.deeds().size()));
            }
            case MARKERS: {
                List<String> reasons = LostTalesMapMarkerFiles.reload();
                return ReloadAnswer.Outcome.read(kind, reasons,
                        Integer.valueOf(LostTalesMapMarkerCatalog.getMarkers().size()),
                        Integer.valueOf(LostTalesWaystoneStructureRegistry.houseIds().size()));
            }
            case RACES: {
                List<String> reasons = CharacterRaceCatalog.load(overworld);
                return ReloadAnswer.Outcome.read(kind, reasons,
                        Integer.valueOf(CharacterRaceRegistry.getOffered().size()),
                        Integer.valueOf(CharacterCapeCatalog.getDefinitions().size()));
            }
            case CAPES: {
                List<String> reasons = CharacterRaceCatalog.loadCapes();
                return ReloadAnswer.Outcome.read(kind, reasons,
                        Integer.valueOf(CharacterCapeCatalog.getDefinitions().size()));
            }
            case KITS: {
                List<String> reasons = StartingKits.load();
                return ReloadAnswer.Outcome.read(kind, reasons,
                        Integer.valueOf(StartingKits.kits().size()));
            }
            case MISSIVES: {
                List<String> reasons = MissiveTemplates.load();
                return ReloadAnswer.Outcome.read(kind, reasons,
                        Integer.valueOf(MissiveTemplates.templates().size()));
            }
            case LORE: {
                List<String> reasons = LoreCharacterRegistry.load();
                return ReloadAnswer.Outcome.read(kind, reasons,
                        Integer.valueOf(LoreCharacterRegistry.getAll().size()));
            }
            default:
                throw new IllegalArgumentException("no reader for " + kind.id);
        }
    }

    /** Sends what was read to the players online who hold a copy of it. */
    private static void spread(ReloadKind kind, MinecraftServer server, WorldServer overworld) {
        switch (kind) {
            case QUESTS:
                ServerQuestSync.resendAfterReload(server);
                break;
            case SKILLS:
                ProgressionSync.sendCatalogToAll(server);
                break;
            case MARKERS:
                // The store takes the markers new to this world now, as at a
                // start; a marker it holds keeps its record until a reseed.
                LostTalesMapMarkerStorage.get(overworld);
                LostTalesMapMarkerSyncManager.syncAll();
                break;
            case RACES:
                for (EntityPlayerMP player : online(server)) {
                    CharacterRaceCatalogSync.send(player);
                    resize(player);
                }
                break;
            case CAPES:
                for (EntityPlayerMP player : online(server)) {
                    CharacterRaceCatalogSync.send(player);
                }
                break;
            case LORE:
                for (EntityPlayerMP player : online(server)) {
                    LoreCharacterSyncManager.send(player);
                }
                break;
            default:
                break;
        }
    }

    /**
     * Sizes a player again to the race their character plays, as every
     * tick does: its hitbox grows only where there is room, its eye height
     * and its race's health, speed and attack follow; a race gone stands
     * in as the body it stood on, and one past every stand-in as a plain
     * player's.
     */
    private static void resize(EntityPlayerMP player) {
        try {
            CharacterRaceGameplayHandler.apply(player);
        } catch (RuntimeException failure) {
            LostTalesLog.warning("Could not size %s again after the races were read: %s",
                    player.getUniqueID(), failure.toString());
        }
    }

    private static List<EntityPlayerMP> online(MinecraftServer server) {
        List<EntityPlayerMP> players = new ArrayList<EntityPlayerMP>();
        if (server == null || server.getConfigurationManager() == null) {
            return players;
        }
        for (Object each : server.getConfigurationManager().playerEntityList) {
            if (each instanceof EntityPlayerMP) {
                players.add((EntityPlayerMP)each);
            }
        }
        return players;
    }

    /** What a sender holds, asked of the one permission question. */
    private static ReloadKind.Holder holder(final ICommandSender sender) {
        return new ReloadKind.Holder() {
            @Override
            public boolean holds(LostTalesCapability capability) {
                return LostTalesPermissions.has(sender, capability);
            }
        };
    }

    private void sendUsage(ICommandSender sender) {
        usage(sender, getCommandUsage(sender));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args == null || args.length != 1) {
            return null;
        }
        List<String> words = ReloadKind.wordsFor(holder(sender));
        return getListOfStringsMatchingLastWord(args, words.toArray(new String[words.size()]));
    }
}
