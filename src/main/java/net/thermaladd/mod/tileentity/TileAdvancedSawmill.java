package net.thermaladd.mod.tileentity;

import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import cofh.api.energy.EnergyStorage;
import cofh.api.energy.IEnergyContainerItem;
import cofh.api.energy.IEnergyReceiver;
import cofh.api.item.IAugmentItem;
import cofh.api.tileentity.IEnergyInfo;
import cofh.api.tileentity.IRedstoneControl;
import cofh.thermalexpansion.item.TEAugments;
import cofh.thermalexpansion.util.crafting.SawmillManager;
import cofh.thermalexpansion.util.crafting.SawmillManager.RecipeSawmill;
import cpw.mods.fml.common.network.NetworkRegistry;
import net.thermaladd.mod.network.MessageTileRenderSync;
import net.thermaladd.mod.network.PacketHandler;

/**
 * Advanced Sawmill tile entity - the fourth "improved TE machine" in this mod, same overall
 * design as {@link TileAdvancedPulverizer}: 3 independent input slots instead of the real
 * Sawmill's 1, each running its own recipe lookup/energy accumulation/craft cycle in parallel
 * every tick, sharing one RF buffer, one primary output slot per input line (3) and one shared
 * pair of secondary output slots (2) for the chance-based byproduct (sawdust, etc.) - and 9
 * augment slots instead of a real (tiered) TE machine's 3-6.
 *
 * {@link SawmillManager}'s own {@code RecipeSawmill} has the exact same shape as
 * {@code RecipePulverizer} (input -> primary output + chance-based secondary output, one flat
 * RF cost), verified via the decompiled {@code cofh.thermalexpansion.block.machine.TileSawmill}
 * - real Thermal Expansion 1.7.10 (the exact vendored jar this mod builds against) does ship a
 * genuine Sawmill, with real recipes (Log -> Planks + Sawdust, etc.) and real face textures
 * ({@code Machine_Face_Sawmill}/{@code Machine_Active_Sawmill}), just no bundled multi-line
 * variant of its own.
 *
 * Side config modes are numerically and color-wise identical to the Pulverizer's own (verified
 * against {@code TileSawmill#initialize}: {@code sideTex = {0,1,2,3,4,7}}, same
 * Blue/Red/Yellow/Orange/Open badge numbering) - real TE's Sawmill just happens to reuse the
 * exact same 6-mode Input/OutputPrimary/OutputSecondary/OutputBoth/All shape the Pulverizer
 * uses, so this tile (and its GUI's badge textures) can share that vocabulary directly instead
 * of inventing a new one.
 */
public class TileAdvancedSawmill extends TileEntity implements ISidedInventory, IEnergyReceiver, IRedstoneControl, IEnergyInfo {

    public static final int INPUT_SLOTS = 3;
    /** One primary-output slot per input line, matching the Pulverizer's own per-line output design. */
    public static final int OUTPUT_PRIMARY_SLOTS = 3;
    public static final int OUTPUT_SECONDARY_SLOTS = 2;
    public static final int AUGMENT_SLOTS = 9;
    /** See TileAdvancedPulverizer#CHARGE_SLOTS - same "drain an RF-storing item into the buffer" slot real TE gives every powered machine. */
    public static final int CHARGE_SLOTS = 1;
    public static final int TOTAL_SLOTS = INPUT_SLOTS + OUTPUT_PRIMARY_SLOTS + OUTPUT_SECONDARY_SLOTS + AUGMENT_SLOTS + CHARGE_SLOTS;

    public static final int INPUT_START = 0;
    public static final int OUTPUT_PRIMARY_START = INPUT_SLOTS;
    public static final int OUTPUT_SECONDARY_START = INPUT_SLOTS + OUTPUT_PRIMARY_SLOTS;
    public static final int AUGMENT_START = INPUT_SLOTS + OUTPUT_PRIMARY_SLOTS + OUTPUT_SECONDARY_SLOTS;
    public static final int CHARGE_SLOT = AUGMENT_START + AUGMENT_SLOTS;

    /**
     * RF spent per tick per active processing line while its recipe is being worked on - 2x a
     * stock level-0 Sawmill's real 20 RF/t base power (see the decompiled
     * {@code TileSawmill#initialize}'s {@code BasePower} config default), matching the same
     * "roughly twice the work speed" relationship the Pulverizer's own 80 (2x its own real 40
     * RF/t base) keeps to ITS stock machine - the Sawmill is simply cheaper per operation than
     * the Pulverizer in real TE too, and this preserves that relative balance.
     */
    public static final int BASE_ENERGY_PER_TICK = 40;

    /** Same "UltimateResonant" power tier identity as the other 3 machines in this mod - see {@link TileAdvancedPulverizer#TIER_NAME}. */
    public static final String TIER_NAME = "UltimateResonant";
    public static final int BASE_ENERGY_CAPACITY = 1000000;
    public static final int ENERGY_RECEIVE_PER_TICK = 10000;

    /** See TileAdvancedPulverizer#ENERGY_SYNC_SCALE - same windowProperty short-overflow fix, same capacity ceiling (BASE_ENERGY_CAPACITY * 8 with a maxed Energy Storage augment). */
    public static final int ENERGY_SYNC_SCALE = 256;

    /**
     * Side config modes - see this class's own javadoc: numerically and color-wise identical to
     * {@link TileAdvancedPulverizer}'s own (verified against the decompiled
     * {@code TileSawmill#initialize}, same {@code sideTex = {0,1,2,3,4,7}} badge numbering).
     */
    public static final int SIDE_MODE_DISABLED = 0;
    public static final int SIDE_MODE_INPUT = 1;
    public static final int SIDE_MODE_OUTPUT_PRIMARY = 2;
    public static final int SIDE_MODE_OUTPUT_SECONDARY = 3;
    public static final int SIDE_MODE_OUTPUT_BOTH = 4;
    public static final int SIDE_MODE_ALL = 5;
    public static final int SIDE_MODE_COUNT = 6;

    /** North/South/West/East facing metas, same convention vanilla furnaces use. */
    public static final int[] FACING_META = {2, 5, 3, 4};

    // -------------------------------------------------- augments (real TE augment items)

    public static final String AUG_GENERAL_AUTO_OUTPUT = "generalAutoOutput";
    public static final String AUG_GENERAL_AUTO_INPUT = "generalAutoInput";
    public static final String AUG_GENERAL_RECONFIG_SIDES = "generalReconfigSides";
    public static final String AUG_GENERAL_REDSTONE_CONTROL = "generalRedstoneControl";
    public static final String AUG_MACHINE_SPEED = "machineSpeed";
    public static final String AUG_MACHINE_SECONDARY = "machineSecondary";
    public static final String AUG_MACHINE_NULL = "machineNull";
    public static final String AUG_ENERGY_STORAGE = "energyStorage";

    /** See TileAdvancedPulverizer's own copy of these tables for the level-4 rationale. */
    private static final int[] MACHINE_SPEED_PROCESS_MOD = {1, 2, 4, 8, 10};
    private static final int[] MACHINE_SPEED_ENERGY_MOD = {1, 3, 8, 20, 60};
    private static final int[] MACHINE_SECONDARY_MOD = {0, 10, 15, 20, 200};
    private static final int[] MACHINE_SECONDARY_ENERGY_PCT = {100, 100, 100, 100, 125};
    private static final int[] ENERGY_STORAGE_MOD = {1, 2, 4, 8};
    private static final int MAX_AUGMENT_LEVEL = 3;
    private static final int MAX_SPEED_LEVEL = 4;
    private static final int MAX_SECONDARY_LEVEL = 4;

    private static final int AUTO_IO_INTERVAL = 8;

    public boolean augmentAutoInput = false;
    public boolean augmentAutoOutput = false;
    public boolean augmentReconfigSides = false;
    public boolean augmentRedstoneControl = false;
    /** Matches real Thermal Expansion's own default - a freshly installed augment starts on "Low" (paused while powered). */
    private ControlMode rsMode = ControlMode.LOW;
    private boolean rsPowered = false;
    public boolean augmentSecondaryNull = false;

    private int speedProcessMod = 1;
    private int speedEnergyMod = 1;
    private int secondaryEnergyPct = 100;
    private int secondaryChanceDivisor = 100;
    private int autoIOTimer = 0;

    private final ItemStack[] inventory = new ItemStack[TOTAL_SLOTS];
    private final EnergyStorage energyStorage = new EnergyStorage(BASE_ENERGY_CAPACITY, ENERGY_RECEIVE_PER_TICK);

    private final int[] progress = new int[INPUT_SLOTS];
    private final int[] progressMax = new int[INPUT_SLOTS];
    private int energyPerTick = 0;
    private int maxEnergyPerTick = INPUT_SLOTS * BASE_ENERGY_PER_TICK;

    private byte facing = 3; // south, matches BlockContainer's default before onBlockPlacedBy runs
    private byte[] sideCache = new byte[6];

    private boolean isActive = false;

    public boolean isActive() {
        return isActive;
    }

    // ---------------------------------------------------------------- facing / sides

    public int getFacing() {
        return facing;
    }

    public void setFacing(int meta) {
        facing = (byte) meta;
        markDirty();
        syncRenderState();
    }

    public int getSideMode(int side) {
        return sideCache[side];
    }

    /**
     * {@code side} arrives straight from a client-sent network packet (MessageCycleSide's
     * {@code side} field is an unchecked byte, -128..127) - without this bounds check, an
     * out-of-range value indexes {@code sideCache} out of bounds and throws, which Forge's
     * packet handling turns into a disconnect for the sender.
     */
    private static boolean isValidSide(int side) {
        return side >= 0 && side < 6;
    }

    /** See readFromNBT's own use of this - guards against a mode value outside SIDE_MODE_COUNT reaching sideCache from tampered/foreign NBT. */
    private static boolean isValidSideArray(byte[] sides) {
        for (byte mode : sides) {
            if (mode < 0 || mode >= SIDE_MODE_COUNT) {
                return false;
            }
        }
        return true;
    }

    public boolean cycleSideMode(int side, int direction) {
        if (!isValidSide(side) || !augmentReconfigSides || side == facing) {
            return false;
        }
        sideCache[side] = (byte) (((sideCache[side] + direction) % SIDE_MODE_COUNT + SIDE_MODE_COUNT) % SIDE_MODE_COUNT);
        markDirty();
        syncRenderState();
        return true;
    }

    public boolean resetSideMode(int side) {
        if (!isValidSide(side) || !augmentReconfigSides || side == facing) {
            return false;
        }
        sideCache[side] = SIDE_MODE_DISABLED;
        markDirty();
        syncRenderState();
        return true;
    }

    public boolean resetAllSideModes() {
        if (!augmentReconfigSides) {
            return false;
        }
        setDefaultSides();
        return true;
    }

    /**
     * Every side starts (and resets back to) plain Disabled - no "smart" per-side defaults, same
     * as every other machine in this mod - see {@link TileAdvancedPulverizer#setDefaultSides}.
     */
    public void setDefaultSides() {
        for (int i = 0; i < sideCache.length; i++) {
            sideCache[i] = SIDE_MODE_DISABLED;
        }
        markDirty();
        syncRenderState();
    }

    /** See TileAdvancedPulverizer#syncRenderState for why this explicit push (not World#markBlockForUpdate) is required. */
    private void syncRenderState() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        PacketHandler.INSTANCE.sendToAllAround(new MessageTileRenderSync(xCoord, yCoord, zCoord, facing, sideCache),
                new NetworkRegistry.TargetPoint(worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 64.0));
    }

    public void setFacingClient(int meta) {
        facing = (byte) meta;
    }

    public void setSideModeClient(int side, int mode) {
        sideCache[side] = (byte) mode;
    }

    private static boolean modeAllowsInsertInput(int mode) {
        return mode == SIDE_MODE_INPUT || mode == SIDE_MODE_ALL;
    }

    /** Real TE quirk, same as the Pulverizer's own Input mode: a side left on plain Input can still have its input slots drained back out. */
    private static boolean modeAllowsExtractInput(int mode) {
        return mode == SIDE_MODE_INPUT;
    }

    private static boolean modeAllowsExtractPrimary(int mode) {
        return mode == SIDE_MODE_OUTPUT_PRIMARY || mode == SIDE_MODE_OUTPUT_BOTH || mode == SIDE_MODE_ALL;
    }

    private static boolean modeAllowsExtractSecondary(int mode) {
        return mode == SIDE_MODE_OUTPUT_SECONDARY || mode == SIDE_MODE_OUTPUT_BOTH || mode == SIDE_MODE_ALL;
    }

    /** See TileAdvancedPulverizer#isAnyInputSide for why these scan sideCache live instead of reading any fixed default. */
    public boolean isAnyInputSide() {
        for (int side = 0; side < 6; side++) {
            if (modeAllowsInsertInput(sideCache[side])) {
                return true;
            }
        }
        return false;
    }

    public boolean isAnyOutputPrimarySide() {
        for (int side = 0; side < 6; side++) {
            if (modeAllowsExtractPrimary(sideCache[side])) {
                return true;
            }
        }
        return false;
    }

    public boolean isAnyOutputSecondarySide() {
        for (int side = 0; side < 6; side++) {
            if (modeAllowsExtractSecondary(sideCache[side])) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- augments

    public void installAugments() {
        boolean autoInput = false;
        boolean autoOutput = false;
        boolean reconfigSides = false;
        boolean redstoneControl = false;
        boolean secondaryNull = false;
        int speedLevel = 0;
        int secondaryLevel = 0;
        int energyLevel = 0;

        for (int i = 0; i < AUGMENT_SLOTS; i++) {
            ItemStack augment = inventory[AUGMENT_START + i];
            if (!isAugmentItem(augment)) {
                continue;
            }
            IAugmentItem item = (IAugmentItem) augment.getItem();
            if (item.getAugmentLevel(augment, AUG_GENERAL_AUTO_OUTPUT) > 0) {
                autoOutput = true;
            }
            if (item.getAugmentLevel(augment, AUG_GENERAL_AUTO_INPUT) > 0) {
                autoInput = true;
            }
            if (item.getAugmentLevel(augment, AUG_GENERAL_RECONFIG_SIDES) > 0) {
                reconfigSides = true;
            }
            if (item.getAugmentLevel(augment, AUG_GENERAL_REDSTONE_CONTROL) > 0) {
                redstoneControl = true;
            }
            if (item.getAugmentLevel(augment, AUG_MACHINE_NULL) > 0) {
                secondaryNull = true;
            }
            speedLevel = Math.max(speedLevel, clampLevel(item.getAugmentLevel(augment, AUG_MACHINE_SPEED), MAX_SPEED_LEVEL));
            secondaryLevel = Math.max(secondaryLevel, clampLevel(item.getAugmentLevel(augment, AUG_MACHINE_SECONDARY), MAX_SECONDARY_LEVEL));
            energyLevel = Math.max(energyLevel, clampLevel(item.getAugmentLevel(augment, AUG_ENERGY_STORAGE)));
        }

        if (augmentReconfigSides && !reconfigSides) {
            setDefaultSides();
        }
        if (!redstoneControl) {
            rsMode = ControlMode.DISABLED;
        }

        augmentAutoInput = autoInput;
        augmentAutoOutput = autoOutput;
        augmentReconfigSides = reconfigSides;
        augmentRedstoneControl = redstoneControl;
        augmentSecondaryNull = secondaryNull;

        speedProcessMod = MACHINE_SPEED_PROCESS_MOD[speedLevel];
        speedEnergyMod = MACHINE_SPEED_ENERGY_MOD[speedLevel];
        secondaryEnergyPct = MACHINE_SECONDARY_ENERGY_PCT[secondaryLevel];
        maxEnergyPerTick = INPUT_SLOTS * BASE_ENERGY_PER_TICK * speedEnergyMod * secondaryEnergyPct / 100;
        secondaryChanceDivisor = Math.max(1, 100 - MACHINE_SECONDARY_MOD[secondaryLevel]);

        energyStorage.setCapacity(BASE_ENERGY_CAPACITY * ENERGY_STORAGE_MOD[energyLevel]);
        energyStorage.setMaxTransfer(ENERGY_RECEIVE_PER_TICK * ENERGY_STORAGE_MOD[energyLevel]);
        markDirty();
    }

    /** See TileAdvancedPulverizer#installDefaultAugments for the decompiled source this mirrors. */
    public void installDefaultAugments() {
        setInventorySlotContents(AUGMENT_START, TEAugments.generalAutoOutput.copy());
        setInventorySlotContents(AUGMENT_START + 1, TEAugments.generalRedstoneControl.copy());
        setInventorySlotContents(AUGMENT_START + 2, TEAugments.generalReconfigSides.copy());
    }

    /** See TileAdvancedPulverizer#writeAugmentsToNBT for why this exists. */
    public NBTTagCompound writeAugmentsToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < AUGMENT_SLOTS; i++) {
            ItemStack stack = inventory[AUGMENT_START + i];
            if (stack != null) {
                NBTTagCompound itemTag = new NBTTagCompound();
                itemTag.setByte("Slot", (byte) i);
                stack.writeToNBT(itemTag);
                list.appendTag(itemTag);
            }
        }
        tag.setTag("Augments", list);
        return tag;
    }

    public void readAugmentsFromNBT(NBTTagCompound tag) {
        NBTTagList list = tag.getTagList("Augments", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound itemTag = list.getCompoundTagAt(i);
            int slot = itemTag.getByte("Slot") & 255;
            if (slot >= 0 && slot < AUGMENT_SLOTS) {
                inventory[AUGMENT_START + slot] = ItemStack.loadItemStackFromNBT(itemTag);
            }
        }
        installAugments();
    }

    private static int clampLevel(int level) {
        return clampLevel(level, MAX_AUGMENT_LEVEL);
    }

    private static int clampLevel(int level, int max) {
        if (level < 0) {
            return 0;
        }
        return Math.min(level, max);
    }

    public static boolean isAugmentItem(ItemStack stack) {
        return stack != null && stack.getItem() instanceof IAugmentItem;
    }

    public static boolean isValidAugment(ItemStack stack) {
        if (!isAugmentItem(stack)) {
            return false;
        }
        IAugmentItem item = (IAugmentItem) stack.getItem();
        Set<String> types = item.getAugmentTypes(stack);
        if (types == null) {
            return false;
        }
        return types.contains(AUG_GENERAL_AUTO_OUTPUT) || types.contains(AUG_GENERAL_AUTO_INPUT)
                || types.contains(AUG_GENERAL_RECONFIG_SIDES) || types.contains(AUG_GENERAL_REDSTONE_CONTROL)
                || types.contains(AUG_MACHINE_SPEED) || types.contains(AUG_MACHINE_SECONDARY)
                || types.contains(AUG_MACHINE_NULL) || types.contains(AUG_ENERGY_STORAGE);
    }

    /** See TileAdvancedPulverizer#hasDuplicateAugmentType for why this exists. */
    public boolean hasDuplicateAugmentType(ItemStack candidate, int excludeSlot) {
        if (!isAugmentItem(candidate)) {
            return false;
        }
        Set<String> types = ((IAugmentItem) candidate.getItem()).getAugmentTypes(candidate);
        if (types == null || types.isEmpty()) {
            return false;
        }
        for (int i = 0; i < AUGMENT_SLOTS; i++) {
            int slot = AUGMENT_START + i;
            if (slot == excludeSlot) {
                continue;
            }
            ItemStack other = inventory[slot];
            if (!isAugmentItem(other)) {
                continue;
            }
            Set<String> otherTypes = ((IAugmentItem) other.getItem()).getAugmentTypes(other);
            if (otherTypes == null) {
                continue;
            }
            for (String type : types) {
                if (otherTypes.contains(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- energy (RF)

    @Override
    public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
        return energyStorage.receiveEnergy(maxReceive, simulate);
    }

    @Override
    public int getEnergyStored(ForgeDirection from) {
        return energyStorage.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored(ForgeDirection from) {
        return energyStorage.getMaxEnergyStored();
    }

    @Override
    public boolean canConnectEnergy(ForgeDirection from) {
        return true;
    }

    public int getEnergy() {
        return energyStorage.getEnergyStored();
    }

    public int getMaxEnergy() {
        return energyStorage.getMaxEnergyStored();
    }

    /** Client-side only: applies a value received (already divided by ENERGY_SYNC_SCALE for the packet) via the container. */
    public void setEnergyStoredClient(int scaled) {
        energyStorage.setEnergyStored(scaled * ENERGY_SYNC_SCALE);
    }

    public void setMaxEnergyClient(int value) {
        energyStorage.setCapacity(value);
    }

    public int getProgress(int line) {
        return progress[line];
    }

    public int getProgressMax(int line) {
        return progressMax[line];
    }

    public void setProgressClient(int line, int value) {
        progress[line] = value;
    }

    public void setProgressMaxClient(int line, int value) {
        progressMax[line] = value;
    }

    public int getEnergyPerTick() {
        return energyPerTick;
    }

    public int getMaxEnergyPerTick() {
        return maxEnergyPerTick;
    }

    public void setEnergyPerTickClient(int value) {
        energyPerTick = value;
    }

    public void setMaxEnergyPerTickClient(int value) {
        maxEnergyPerTick = value;
    }

    // ---------------------------------------------------------------- IEnergyInfo (real TE's own Energy tab)

    @Override
    public int getInfoEnergyPerTick() {
        return getEnergyPerTick();
    }

    @Override
    public int getInfoMaxEnergyPerTick() {
        return getMaxEnergyPerTick();
    }

    @Override
    public int getInfoEnergyStored() {
        return getEnergy();
    }

    @Override
    public int getInfoMaxEnergyStored() {
        return getMaxEnergy();
    }

    // ---------------------------------------------------------------- redstone control (TE-compatible)

    @Override
    public void setControl(ControlMode mode) {
        rsMode = mode;
        markDirty();
    }

    @Override
    public ControlMode getControl() {
        return rsMode;
    }

    @Override
    public void setPowered(boolean powered) {
        rsPowered = powered;
    }

    @Override
    public boolean isPowered() {
        return rsPowered;
    }

    public void setControlClient(int ordinal) {
        rsMode = ControlMode.values()[ordinal];
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }

        boolean dirty = false;
        energyPerTick = 0;

        if (chargeFromItem()) {
            dirty = true;
        }

        setPowered(worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord));
        boolean redstoneAllows = !augmentRedstoneControl || rsMode.isDisabled() || rsMode.isHigh() == isPowered();
        if (redstoneAllows) {
            for (int line = 0; line < INPUT_SLOTS; line++) {
                if (tryProcess(line)) {
                    dirty = true;
                }
            }
        }

        if (augmentAutoInput || augmentAutoOutput) {
            if (++autoIOTimer >= AUTO_IO_INTERVAL) {
                autoIOTimer = 0;
                if (augmentAutoInput && autoPullInputs()) {
                    dirty = true;
                }
                if (augmentAutoOutput && autoPushOutputs()) {
                    dirty = true;
                }
            }
        }

        boolean nowActive = false;
        for (int line = 0; line < INPUT_SLOTS; line++) {
            if (progressMax[line] > 0) {
                nowActive = true;
                break;
            }
        }
        if (nowActive != isActive) {
            isActive = nowActive;
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            dirty = true;
        }

        if (dirty) {
            markDirty();
        }
    }

    /** See TileAdvancedPulverizer#chargeFromItem - same real-TE {@code TilePowered#chargeEnergy} behavior. */
    private boolean chargeFromItem() {
        ItemStack stack = inventory[CHARGE_SLOT];
        if (stack == null || !(stack.getItem() instanceof IEnergyContainerItem)) {
            return false;
        }
        int receive = Math.min(energyStorage.getMaxReceive(), energyStorage.getMaxEnergyStored() - energyStorage.getEnergyStored());
        if (receive <= 0) {
            return false;
        }
        IEnergyContainerItem energyItem = (IEnergyContainerItem) stack.getItem();
        int extracted = energyItem.extractEnergy(stack, receive, false);
        if (extracted <= 0) {
            return false;
        }
        energyStorage.receiveEnergy(extracted, false);
        if (stack.stackSize <= 0) {
            inventory[CHARGE_SLOT] = null;
        }
        return true;
    }

    /** One input line's share of the tick - see TileAdvancedPulverizer#tryProcess for the full rationale, identical here bar the recipe manager. */
    private boolean tryProcess(int line) {
        ItemStack input = inventory[INPUT_START + line];
        if (input == null) {
            if (progressMax[line] != 0) {
                progress[line] = 0;
                progressMax[line] = 0;
                return true;
            }
            return false;
        }

        RecipeSawmill recipe = SawmillManager.getRecipe(input);
        if (recipe == null || input.stackSize < recipe.getInput().stackSize) {
            if (progressMax[line] != 0) {
                progress[line] = 0;
                progressMax[line] = 0;
                return true;
            }
            return false;
        }

        progressMax[line] = recipe.getEnergy();

        if (progress[line] < progressMax[line]) {
            int energyCost = BASE_ENERGY_PER_TICK * speedEnergyMod * secondaryEnergyPct / 100;
            if (energyStorage.getEnergyStored() < energyCost) {
                return false;
            }
            energyStorage.modifyEnergyStored(-energyCost);
            energyPerTick += energyCost;
            progress[line] += BASE_ENERGY_PER_TICK * speedProcessMod;
            return true;
        }

        if (!canFitOutput(recipe)) {
            return false;
        }

        placeOutput(recipe);
        input.stackSize -= recipe.getInput().stackSize;
        if (input.stackSize <= 0) {
            inventory[INPUT_START + line] = null;
        }
        progress[line] = 0;
        progressMax[line] = 0;
        return true;
    }

    private boolean canFitOutput(RecipeSawmill recipe) {
        ItemStack primary = recipe.getPrimaryOutput();
        boolean primaryFits = false;
        for (int i = 0; i < OUTPUT_PRIMARY_SLOTS; i++) {
            if (canFitStack(OUTPUT_PRIMARY_START + i, primary)) {
                primaryFits = true;
                break;
            }
        }
        if (!primaryFits) {
            return false;
        }
        ItemStack secondary = recipe.getSecondaryOutput();
        if (secondary != null && !augmentSecondaryNull) {
            boolean anyFits = false;
            for (int i = 0; i < OUTPUT_SECONDARY_SLOTS; i++) {
                if (canFitStack(OUTPUT_SECONDARY_START + i, secondary)) {
                    anyFits = true;
                    break;
                }
            }
            if (!anyFits) {
                return false;
            }
        }
        return true;
    }

    private boolean canFitStack(int slot, ItemStack stack) {
        ItemStack existing = inventory[slot];
        if (existing == null) {
            return true;
        }
        return existing.getItem() == stack.getItem()
                && existing.getItemDamage() == stack.getItemDamage()
                && ItemStack.areItemStackTagsEqual(existing, stack)
                && existing.stackSize + stack.stackSize <= existing.getMaxStackSize();
    }

    private void placeOutput(RecipeSawmill recipe) {
        addToFirstFitting(OUTPUT_PRIMARY_START, OUTPUT_PRIMARY_SLOTS, recipe.getPrimaryOutput());

        ItemStack secondary = recipe.getSecondaryOutput();
        if (secondary == null) {
            return;
        }
        int chance = recipe.getSecondaryOutputChance();
        if (chance >= 100 || worldObj.rand.nextInt(secondaryChanceDivisor) < chance) {
            addToFirstFitting(OUTPUT_SECONDARY_START, OUTPUT_SECONDARY_SLOTS, secondary);
        }
    }

    private void addToFirstFitting(int firstSlot, int slotCount, ItemStack stack) {
        for (int i = 0; i < slotCount; i++) {
            int slot = firstSlot + i;
            if (canFitStack(slot, stack)) {
                if (inventory[slot] == null) {
                    inventory[slot] = stack.copy();
                } else {
                    inventory[slot].stackSize += stack.stackSize;
                }
                return;
            }
        }
    }

    // -------------------------------------------------- auto input/output (Auto I/O augments)

    private boolean autoPullInputs() {
        boolean moved = false;
        for (int side = 0; side < 6; side++) {
            if (modeAllowsInsertInput(sideCache[side]) && pullFromSide(ForgeDirection.getOrientation(side))) {
                moved = true;
            }
        }
        return moved;
    }

    private boolean autoPushOutputs() {
        boolean moved = false;
        for (int side = 0; side < 6; side++) {
            int mode = sideCache[side];
            boolean primary = modeAllowsExtractPrimary(mode);
            boolean secondary = modeAllowsExtractSecondary(mode);
            if ((primary || secondary) && pushToSide(ForgeDirection.getOrientation(side), primary, secondary)) {
                moved = true;
            }
        }
        return moved;
    }

    private boolean pullFromSide(ForgeDirection dir) {
        TileEntity neighbor = worldObj.getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
        if (!(neighbor instanceof IInventory)) {
            return false;
        }
        IInventory neighborInv = (IInventory) neighbor;
        ForgeDirection fromSide = dir.getOpposite();
        int[] slots = neighborInv instanceof ISidedInventory
                ? ((ISidedInventory) neighborInv).getAccessibleSlotsFromSide(fromSide.ordinal())
                : allSlots(neighborInv);

        for (int slotIdx : slots) {
            ItemStack candidate = neighborInv.getStackInSlot(slotIdx);
            if (candidate == null || !SawmillManager.recipeExists(candidate)) {
                continue;
            }
            if (neighborInv instanceof ISidedInventory
                    && !((ISidedInventory) neighborInv).canExtractItem(slotIdx, candidate, fromSide.ordinal())) {
                continue;
            }
            int inputSlot = findInputSlotFor(candidate);
            if (inputSlot < 0) {
                continue;
            }
            if (inventory[inputSlot] == null) {
                ItemStack moved = candidate.copy();
                moved.stackSize = 1;
                inventory[inputSlot] = moved;
            } else {
                inventory[inputSlot].stackSize++;
            }
            neighborInv.decrStackSize(slotIdx, 1);
            neighborInv.markDirty();
            return true;
        }
        return false;
    }

    private boolean pushToSide(ForgeDirection dir, boolean primary, boolean secondary) {
        TileEntity neighbor = worldObj.getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
        if (!(neighbor instanceof IInventory)) {
            return false;
        }
        IInventory neighborInv = (IInventory) neighbor;
        ForgeDirection toSide = dir.getOpposite();

        if (primary) {
            for (int i = 0; i < OUTPUT_PRIMARY_SLOTS; i++) {
                if (pushSlot(neighborInv, toSide, OUTPUT_PRIMARY_START + i)) {
                    return true;
                }
            }
        }
        if (secondary) {
            for (int i = 0; i < OUTPUT_SECONDARY_SLOTS; i++) {
                if (pushSlot(neighborInv, toSide, OUTPUT_SECONDARY_START + i)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean pushSlot(IInventory neighborInv, ForgeDirection toSide, int slot) {
        ItemStack stack = inventory[slot];
        if (stack == null) {
            return false;
        }
        if (insertIntoInventory(neighborInv, toSide, stack)) {
            stack.stackSize--;
            if (stack.stackSize <= 0) {
                inventory[slot] = null;
            }
            return true;
        }
        return false;
    }

    private int findInputSlotFor(ItemStack stack) {
        int emptySlot = -1;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack buf = inventory[INPUT_START + i];
            if (buf == null) {
                if (emptySlot < 0) {
                    emptySlot = INPUT_START + i;
                }
                continue;
            }
            if (buf.getItem() == stack.getItem() && buf.getItemDamage() == stack.getItemDamage()
                    && ItemStack.areItemStackTagsEqual(buf, stack) && buf.stackSize < buf.getMaxStackSize()) {
                return INPUT_START + i;
            }
        }
        return emptySlot;
    }

    private boolean insertIntoInventory(IInventory inv, ForgeDirection side, ItemStack stack) {
        int[] slots = inv instanceof ISidedInventory
                ? ((ISidedInventory) inv).getAccessibleSlotsFromSide(side.ordinal())
                : allSlots(inv);

        for (int slotIdx : slots) {
            ItemStack single = stack.copy();
            single.stackSize = 1;

            if (inv instanceof ISidedInventory && !((ISidedInventory) inv).canInsertItem(slotIdx, single, side.ordinal())) {
                continue;
            }
            if (!inv.isItemValidForSlot(slotIdx, single)) {
                continue;
            }
            ItemStack existing = inv.getStackInSlot(slotIdx);
            if (existing == null) {
                inv.setInventorySlotContents(slotIdx, single);
                inv.markDirty();
                return true;
            }
            if (existing.getItem() == single.getItem() && existing.getItemDamage() == single.getItemDamage()
                    && ItemStack.areItemStackTagsEqual(existing, single) && existing.stackSize < existing.getMaxStackSize()) {
                existing.stackSize++;
                inv.markDirty();
                return true;
            }
        }
        return false;
    }

    private static int[] allSlots(IInventory inv) {
        int[] slots = new int[inv.getSizeInventory()];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = i;
        }
        return slots;
    }

    // ---------------------------------------------------------------- inventory

    @Override
    public int getSizeInventory() {
        return TOTAL_SLOTS;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inventory[slot];
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (inventory[slot] == null) {
            return null;
        }
        ItemStack result;
        if (inventory[slot].stackSize <= amount) {
            result = inventory[slot];
            inventory[slot] = null;
        } else {
            result = inventory[slot].splitStack(amount);
            if (inventory[slot].stackSize == 0) {
                inventory[slot] = null;
            }
        }
        if (isAugmentSlot(slot)) {
            installAugments();
        }
        markDirty();
        return result;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        ItemStack stack = inventory[slot];
        inventory[slot] = null;
        if (isAugmentSlot(slot)) {
            installAugments();
        }
        return stack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        inventory[slot] = stack;
        if (stack != null && stack.stackSize > getInventoryStackLimit()) {
            stack.stackSize = getInventoryStackLimit();
        }
        if (isAugmentSlot(slot)) {
            installAugments();
        }
        markDirty();
    }

    private static boolean isAugmentSlot(int slot) {
        return slot >= AUGMENT_START && slot < AUGMENT_START + AUGMENT_SLOTS;
    }

    @Override
    public String getInventoryName() {
        return "container.advancedSawmill";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
                && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64.0;
    }

    @Override
    public void openInventory() {
    }

    @Override
    public void closeInventory() {
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (slot == CHARGE_SLOT) {
            return stack.getItem() instanceof IEnergyContainerItem;
        }
        if (isAugmentSlot(slot)) {
            return isValidAugment(stack) && !hasDuplicateAugmentType(stack, slot);
        }
        if (slot < OUTPUT_PRIMARY_START) {
            return SawmillManager.recipeExists(stack);
        }
        return false;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        int mode = sideCache[side];
        boolean input = modeAllowsInsertInput(mode) || modeAllowsExtractInput(mode);
        boolean primary = modeAllowsExtractPrimary(mode);
        boolean secondary = modeAllowsExtractSecondary(mode);

        int[] slots = new int[(input ? INPUT_SLOTS : 0) + (primary ? OUTPUT_PRIMARY_SLOTS : 0) + (secondary ? OUTPUT_SECONDARY_SLOTS : 0)];
        int idx = 0;
        if (input) {
            int start = (int) ((worldObj != null ? worldObj.getTotalWorldTime() : 0) % INPUT_SLOTS);
            for (int i = 0; i < INPUT_SLOTS; i++) {
                slots[idx++] = INPUT_START + (start + i) % INPUT_SLOTS;
            }
        }
        if (primary) {
            for (int i = 0; i < OUTPUT_PRIMARY_SLOTS; i++) {
                slots[idx++] = OUTPUT_PRIMARY_START + i;
            }
        }
        if (secondary) {
            for (int i = 0; i < OUTPUT_SECONDARY_SLOTS; i++) {
                slots[idx++] = OUTPUT_SECONDARY_START + i;
            }
        }
        return slots;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return slot < OUTPUT_PRIMARY_START && modeAllowsInsertInput(sideCache[side]) && SawmillManager.recipeExists(stack);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        int mode = sideCache[side];
        if (slot < OUTPUT_PRIMARY_START) {
            return modeAllowsExtractInput(mode);
        }
        if (slot < OUTPUT_SECONDARY_START) {
            return modeAllowsExtractPrimary(mode);
        }
        if (slot < AUGMENT_START) {
            return modeAllowsExtractSecondary(mode);
        }
        return false;
    }

    // ---------------------------------------------------------------- nbt

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        energyStorage.writeToNBT(tag);
        tag.setByte("Facing", facing);
        tag.setByteArray("Sides", sideCache);
        tag.setIntArray("Progress", progress);
        tag.setIntArray("ProgressMax", progressMax);
        tag.setByte("RSControl", (byte) rsMode.ordinal());

        NBTTagList items = new NBTTagList();
        for (int i = 0; i < inventory.length; i++) {
            if (inventory[i] != null) {
                NBTTagCompound itemTag = new NBTTagCompound();
                itemTag.setByte("Slot", (byte) i);
                inventory[i].writeToNBT(itemTag);
                items.appendTag(itemTag);
            }
        }
        tag.setTag("Items", items);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        energyStorage.readFromNBT(tag);
        facing = tag.getByte("Facing");
        if (tag.hasKey("RSControl")) {
            rsMode = ControlMode.values()[tag.getByte("RSControl") & 0xFF];
        }

        if (tag.hasKey("Sides")) {
            byte[] sides = tag.getByteArray("Sides");
            if (sides.length == sideCache.length && isValidSideArray(sides)) {
                sideCache = sides;
            }
        }
        if (tag.hasKey("Progress")) {
            int[] p = tag.getIntArray("Progress");
            System.arraycopy(p, 0, progress, 0, Math.min(p.length, progress.length));
        }
        if (tag.hasKey("ProgressMax")) {
            int[] p = tag.getIntArray("ProgressMax");
            System.arraycopy(p, 0, progressMax, 0, Math.min(p.length, progressMax.length));
        }

        NBTTagList items = tag.getTagList("Items", 10);
        for (int i = 0; i < inventory.length; i++) {
            inventory[i] = null;
        }
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound itemTag = items.getCompoundTagAt(i);
            int slot = itemTag.getByte("Slot") & 255;
            if (slot >= 0 && slot < inventory.length) {
                inventory[slot] = ItemStack.loadItemStackFromNBT(itemTag);
            }
        }

        installAugments();

        isActive = false;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (progressMax[i] > 0) {
                isActive = true;
                break;
            }
        }
    }

    /** See TileAdvancedPulverizer#getDescriptionPacket for why this exists. */
    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        readFromNBT(packet.func_148857_g());
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }
}
