package com.ninuna.losttales.character.state.component;

import com.ninuna.losttales.character.state.CharacterStateApplyPhase;
import com.ninuna.losttales.character.state.CharacterStateComponent;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import com.ninuna.losttales.quest.LostTalesQuestManager;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import com.ninuna.losttales.world.map.waypoint.LostTalesMapMarkerWaypointUnlockHelper;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

import java.util.Set;

/** Lost Tales quests, objective progress, pins, and discovered marker state. */
public final class LostTalesQuestStateComponent implements CharacterStateComponent {

    public static final String ID = "losttales_quests";

    private static final int VERSION = 1;
    private static final String TAG_VERSION = "Version";
    private static final String TAG_QUEST_DATA = "QuestData";

    private static final int MAX_DEPTH = 24;
    private static final int MAX_NODES = 100000;
    private static final int MAX_LIST_ENTRIES = 32768;
    private static final int MAX_COMPOUND_ENTRIES = 4096;
    private static final int MAX_KEY_CHARACTERS = 1024;
    private static final int MAX_STRING_CHARACTERS = 32767;
    private static final long MAX_TOTAL_STRING_CHARACTERS = 2000000L;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public CharacterStateApplyPhase getApplyPhase() {
        return CharacterStateApplyPhase.BEFORE_ATTRIBUTES;
    }

    @Override
    public NBTTagCompound capture(EntityPlayerMP player)
            throws CharacterStateValidationException {
        LostTalesQuestPlayerData data = requireData(player);
        if (data.isReadOnlyForNewerVersion()) {
            throw new CharacterStateValidationException(
                    "Lost Tales quest data is read-only or unsupported");
        }
        NBTTagCompound state = wrap(data.writeCharacterState());
        validate(state);
        return state;
    }

    @Override
    public NBTTagCompound createDefault() {
        return wrap(new LostTalesQuestPlayerData().writeCharacterState());
    }

    @Override
    public void validate(NBTTagCompound state)
            throws CharacterStateValidationException {
        requireVersion(state);
        Set<?> keys = state.func_150296_c();
        if (keys.size() != 2 || !keys.contains(TAG_VERSION)
                || !keys.contains(TAG_QUEST_DATA)
                || !state.hasKey(TAG_QUEST_DATA, Constants.NBT.TAG_COMPOUND)) {
            throw new CharacterStateValidationException(
                    "Lost Tales quest component is incomplete or contains unsupported fields");
        }

        NBTTagCompound questData = state.getCompoundTag(TAG_QUEST_DATA);
        validateExpandedTree(questData);
        try {
            LostTalesQuestPlayerData.validateCharacterState(questData);
        } catch (IllegalArgumentException exception) {
            throw new CharacterStateValidationException(
                    "Lost Tales quest state is malformed or unsupported", exception);
        }
    }

    @Override
    public void apply(EntityPlayerMP player, NBTTagCompound state)
            throws CharacterStateValidationException {
        validate(state);
        LostTalesQuestPlayerData data = requireData(player);
        try {
            data.replaceCharacterState(state.getCompoundTag(TAG_QUEST_DATA));
            LostTalesMapMarkerWaypointUnlockHelper.reconcileBundledWaypointRegions(
                    player, data.getDiscoveredMarkerIds());
        } catch (RuntimeException exception) {
            throw new CharacterStateValidationException(
                    "Lost Tales quest state could not be applied", exception);
        }
    }

    @Override
    public void synchronize(EntityPlayerMP player) {
        LostTalesQuestManager.syncToClient(player);
    }

    private static LostTalesQuestPlayerData requireData(EntityPlayerMP player)
            throws CharacterStateValidationException {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            throw new CharacterStateValidationException(
                    "Lost Tales quest state requires a connected server player");
        }
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null) {
            throw new CharacterStateValidationException(
                    "Lost Tales quest data is unavailable");
        }
        return data;
    }

    private static NBTTagCompound wrap(NBTTagCompound questData) {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger(TAG_VERSION, VERSION);
        state.setTag(TAG_QUEST_DATA, questData == null
                ? new NBTTagCompound() : questData.copy());
        return state;
    }

    private static void requireVersion(NBTTagCompound state)
            throws CharacterStateValidationException {
        if (state == null || !state.hasKey(TAG_VERSION, Constants.NBT.TAG_INT)) {
            throw new CharacterStateValidationException(
                    "Lost Tales quest component version is missing");
        }
        int version = state.getInteger(TAG_VERSION);
        if (version != VERSION) {
            throw new CharacterStateValidationException(
                    "Unsupported Lost Tales quest component version " + version);
        }
    }

    private static void validateExpandedTree(NBTTagCompound root)
            throws CharacterStateValidationException {
        ValidationBudget budget = new ValidationBudget();
        validateTag(root, 0, budget);
    }

    private static void validateTag(NBTBase tag, int depth,
                                    ValidationBudget budget)
            throws CharacterStateValidationException {
        if (tag == null || depth > MAX_DEPTH || ++budget.nodes > MAX_NODES) {
            throw new CharacterStateValidationException(
                    "Lost Tales quest state exceeds the expanded structure limit");
        }
        switch (tag.getId()) {
            case Constants.NBT.TAG_BYTE:
            case Constants.NBT.TAG_INT:
            case Constants.NBT.TAG_LONG:
                return;
            case Constants.NBT.TAG_DOUBLE:
                double value = ((NBTTagDouble) tag).func_150286_g();
                if (Double.isNaN(value) || Double.isInfinite(value)) {
                    throw new CharacterStateValidationException(
                            "Lost Tales quest state contains a non-finite number");
                }
                return;
            case Constants.NBT.TAG_STRING:
                String text = ((NBTTagString) tag).func_150285_a_();
                if (text.length() > MAX_STRING_CHARACTERS) {
                    throw new CharacterStateValidationException(
                            "Lost Tales quest state contains an oversized string");
                }
                budget.stringCharacters += (long) text.length();
                if (budget.stringCharacters > MAX_TOTAL_STRING_CHARACTERS) {
                    throw new CharacterStateValidationException(
                            "Lost Tales quest state exceeds the expanded string limit");
                }
                return;
            case Constants.NBT.TAG_LIST:
                validateList((NBTTagList) tag, depth, budget);
                return;
            case Constants.NBT.TAG_COMPOUND:
                validateCompound((NBTTagCompound) tag, depth, budget);
                return;
            default:
                throw new CharacterStateValidationException(
                        "Lost Tales quest state contains unsupported NBT type "
                                + tag.getId());
        }
    }

    private static void validateList(NBTTagList list, int depth,
                                     ValidationBudget budget)
            throws CharacterStateValidationException {
        if (list.tagCount() > MAX_LIST_ENTRIES
                || list.tagCount() > 0
                && list.func_150303_d() != Constants.NBT.TAG_COMPOUND) {
            throw new CharacterStateValidationException(
                    "Lost Tales quest state contains an invalid or oversized list");
        }
        for (int index = 0; index < list.tagCount(); index++) {
            validateTag(list.getCompoundTagAt(index), depth + 1, budget);
        }
    }

    private static void validateCompound(NBTTagCompound compound, int depth,
                                         ValidationBudget budget)
            throws CharacterStateValidationException {
        Set<?> keys = compound.func_150296_c();
        if (keys.size() > MAX_COMPOUND_ENTRIES) {
            throw new CharacterStateValidationException(
                    "Lost Tales quest state contains an oversized compound");
        }
        for (Object keyObject : keys) {
            if (!(keyObject instanceof String)) {
                throw new CharacterStateValidationException(
                        "Lost Tales quest state contains an invalid key");
            }
            String key = (String) keyObject;
            if (key.length() == 0 || key.length() > MAX_KEY_CHARACTERS) {
                throw new CharacterStateValidationException(
                        "Lost Tales quest state contains an invalid key length");
            }
            validateTag(compound.getTag(key), depth + 1, budget);
        }
    }

    private static final class ValidationBudget {
        private int nodes;
        private long stringCharacters;
    }
}
