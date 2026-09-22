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
import cofh.api.tileentity.IPortableData;
import cofh.api.tileentity.IRedstoneControl;
import cofh.thermalexpansion.item.TEAugments;
import cofh.thermalexpansion.util.crafting.FurnaceManager;
import cofh.thermalexpansion.util.crafting.FurnaceManager.RecipeFurnace;
import cpw.mods.fml.common.network.NetworkRegistry;
import net.thermaladd.mod.network.MessageTileRenderSync;
import net.thermaladd.mod.network.PacketHandler;
import net.thermaladd.mod.util.IPortableMachineState;

/**
 * Advanced Furnace tile entity - the third "improved TE machine" in this mod, same overall
 * design as {@link TileAdvancedPulverizer}: 3 independent input slots instead of the real
 * Furnace's 1, each running its own recipe lookup/energy accumulation/craft cycle in
 * parallel every tick, sharing one RF buffer, one output slot per input line (3) and 9
 * augment slots. Same "UltimateResonant" power tier as the other two machines.
 *
 * Simpler than the Pulverizer in one respect: {@link FurnaceManager} recipes have no
 * secondary product, so there is no secondary output slot and no Secondary Output / Null
 * augment to support - those augment types are simply not recognized as valid here (they
 * would have zero effect on this machine).
 */
public class TileAdvancedFurnace extends TileEntity
        implements ISidedInventory, IEnergyReceiver, IRedstoneControl, IEnergyInfo, IPortableData, IPortableMachineState {

    public static final int INPUT_SLOTS = 3;
    /** One output slot per input line, matching the Pulverizer's own per-line output design. */
    public static final int OUTPUT_SLOTS = 3;
    public static final int AUGMENT_SLOTS = 9;
    /** See TileAdvancedPulverizer#CHARGE_SLOTS - same "drain an RF-storing item into the buffer" slot real TE gives every powered machine. */
    public static final int CHARGE_SLOTS = 1;
    public static final int TOTAL_SLOTS = INPUT_SLOTS + OUTPUT_SLOTS + AUGMENT_SLOTS + CHARGE_SLOTS;

    public static final int INPUT_START = 0;
    public static final int OUTPUT_START = INPUT_SLOTS;
    public static final int AUGMENT_START = INPUT_SLOTS + OUTPUT_SLOTS;
    public static final int CHARGE_SLOT = AUGMENT_START + AUGMENT_SLOTS;

    /** Same "UltimateResonant" tier and per-line processing rate as the Advanced Pulverizer. */
    public static final String TIER_NAME = "UltimateResonant";
    /** These three are overridable from config/ThermalADD.cfg - see {@link net.thermaladd.mod.config.ModConfig}. */
    public static int BASE_ENERGY_PER_TICK = 80;
    public static int BASE_ENERGY_CAPACITY = 1000000;
    public static int ENERGY_RECEIVE_PER_TICK = 10000;

    /** See TileAdvancedPulverizer#ENERGY_SYNC_SCALE - same overflow fix, same capacity ceiling (BASE_ENERGY_CAPACITY * 8 with a maxed Energy Storage augment). */
    public static final int ENERGY_SYNC_SCALE = 256;
    /**
     * Same trick as ENERGY_SYNC_SCALE, but for the two RF/t readouts. Sending those raw was only
     * safe while BASE_ENERGY_PER_TICK was hardcoded; now that config can raise it, the worst case
     * ({@code INPUT_SLOTS * BASE_ENERGY_PER_TICK * 60}) needs the same headroom the energy buffer
     * already had. See TileAdvancedPulverizer.RATE_SYNC_SCALE for the full reasoning.
     */
    public static final int RATE_SYNC_SCALE = 8;

    /** See TileAdvancedPulverizer#SOUND_NAME - same real Thermal Expansion ambient sound reuse, verified against the vendored jar's own sounds.json (blockMachineFurnace -> blocks/machine/furnace.ogg). */
    public static final String SOUND_NAME = "thermalexpansion:blockMachineFurnace";

    /**
     * Side config modes, verified against real Thermal Expansion's own Furnace (decompiled
     * {@code cofh.thermalexpansion.block.machine.TileFurnace#initialize}: 4 modes -
     * {@code sideTex = {0,1,4,7}} indexing real TE's own {@code Config_None/Blue/Orange/Open}
     * badge textures). Mode 0 ("no badge, plain casing") is TE's actual DISABLED state, not an
     * "accept everything" default the way this mod used to treat it - the front face is
     * permanently forced onto it for exactly that reason (see {@code setDefaultSides()}). Real
     * TE's Furnace Output badge is ORANGE (Config_4), unlike the Pulverizer's RED primary output
     * (Config_2) - the Furnace has no primary/secondary split, so it only ever needed the one
     * generic "Output" slot in TE's shared badge numbering.
     */
    public static final int SIDE_MODE_DISABLED = 0;
    public static final int SIDE_MODE_INPUT = 1;
    public static final int SIDE_MODE_OUTPUT = 2;
    public static final int SIDE_MODE_ALL = 3;
    public static final int SIDE_MODE_COUNT = 4;


    public static final int[] FACING_META = {2, 5, 3, 4};

    public static final String AUG_GENERAL_AUTO_OUTPUT = "generalAutoOutput";
    public static final String AUG_GENERAL_AUTO_INPUT = "generalAutoInput";
    public static final String AUG_GENERAL_RECONFIG_SIDES = "generalReconfigSides";
    public static final String AUG_GENERAL_REDSTONE_CONTROL = "generalRedstoneControl";
    public static final String AUG_MACHINE_SPEED = "machineSpeed";
    public static final String AUG_ENERGY_STORAGE = "energyStorage";

    /** See TileAdvancedPulverizer's own copy of these tables for the level-4 rationale. */
    private static final int[] MACHINE_SPEED_PROCESS_MOD = {1, 2, 4, 8, 10};
    private static final int[] MACHINE_SPEED_ENERGY_MOD = {1, 3, 8, 20, 60};
    private static final int[] ENERGY_STORAGE_MOD = {1, 2, 4, 8};
    private static final int MAX_AUGMENT_LEVEL = 3;
    private static final int MAX_SPEED_LEVEL = 4;

    private static final int AUTO_IO_INTERVAL = 8;

    public boolean augmentAutoInput = false;
    public boolean augmentAutoOutput = false;
    public boolean augmentReconfigSides = false;
    public boolean augmentRedstoneControl = false;
    /** Matches real Thermal Expansion's own default - a freshly installed augment starts on "Low" (paused while powered). */
    private ControlMode rsMode = ControlMode.LOW;
    private boolean rsPowered = false;

    private int speedProcessMod = 1;
    private int speedEnergyMod = 1;
    private int autoIOTimer = 0;

    private final ItemStack[] inventory = new ItemStack[TOTAL_SLOTS];
    private final EnergyStorage energyStorage = new EnergyStorage(BASE_ENERGY_CAPACITY, ENERGY_RECEIVE_PER_TICK);

    private final int[] progress = new int[INPUT_SLOTS];
    private final int[] progressMax = new int[INPUT_SLOTS];
    /** RF actually spent on the tick just finished - shown as "Energy Consumption" in the GUI. */
    private int energyPerTick = 0;
    /** RF/t all 3 lines together would draw at once, at the current Machine Speed augment level - recomputed in installAugments(). */
    private int maxEnergyPerTick = INPUT_SLOTS * BASE_ENERGY_PER_TICK;

    private byte facing = 3;
    private byte[] sideCache = new byte[6];
    private boolean isActive = false;

    public boolean isActive() {
        return isActive;
    }

    // ---------------------------------------------------------------- facing / sides

    /**
     * Comparator signal: how many of the machine's processing lines currently hold an input item,
     * scaled to 1-15, with 0 meaning every line is empty. See
     * TileAdvancedPulverizer#getComparatorSignal for why this counts lines instead of stack sizes.
     */
    public int getComparatorSignal() {
        int occupied = 0;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (inventory[INPUT_START + i] != null) {
                occupied++;
            }
        }
        return occupied == 0 ? 0 : 1 + (occupied * 14) / INPUT_SLOTS;
    }

    // ---------------------------------------------------------------- IPortableData (TE Redprint)

    /** See TileAdvancedPulverizer's own IPortableData block for the reasoning behind all of this. */
    @Override
    public String getDataType() {
        return "tile.thermaladd.advancedFurnace";
    }

    @Override
    public void writePortableData(EntityPlayer player, NBTTagCompound tag) {
        tag.setByteArray("SideCache", sideCache.clone());
        tag.setByte("RSControl", (byte) rsMode.ordinal());
    }

    @Override
    public void readPortableData(EntityPlayer player, NBTTagCompound tag) {
        if (augmentReconfigSides && tag.hasKey("SideCache")) {
            applySideModes(tag.getByteArray("SideCache"));
        }
        if (augmentRedstoneControl && tag.hasKey("RSControl")) {
            int ordinal = tag.getByte("RSControl") & 0xFF;
            if (ordinal < ControlMode.values().length) {
                rsMode = ControlMode.values()[ordinal];
                markDirty();
            }
        }
    }

    // ---------------------------------------------------------------- IPortableMachineState

    @Override
    public byte[] getSideModesCopy() {
        return sideCache.clone();
    }

    @Override
    public void applySideModes(byte[] modes) {
        if (modes != null && modes.length == sideCache.length && isValidSideArray(modes)) {
            sideCache = modes.clone();
            markDirty();
            syncRenderState();
        }
    }

    @Override
    public void setStoredEnergy(int energy) {
        int capped = Math.max(0, Math.min(energy, energyStorage.getMaxEnergyStored()));
        energyStorage.setEnergyStored(capped);
        markDirty();
    }

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

    /** Refuses to touch the front face, matching real TE's TileReconfigurable#incrSide/decrSide. */
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
     * Every side starts (and resets back to) plain Disabled - no "smart" per-side defaults.
     * See TileAdvancedPulverizer#setDefaultSides for why: a side only ever carries a role (and
     * only ever shows a slot highlight) once the player has actually chosen one via the
     * Configuration tab.
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
        PacketHandler.INSTANCE.sendToAllAround(new MessageTileRenderSync(xCoord, yCoord, zCoord, facing, sideCache, isActive),
                new NetworkRegistry.TargetPoint(worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 64.0));
    }

    public void setFacingClient(int meta) {
        facing = (byte) meta;
    }

    public void setSideModeClient(int side, int mode) {
        sideCache[side] = (byte) mode;
    }

    /** Client-side only: applied by MessageTileRenderSyncHandler, which also uses the false->true edge of this same value to start the ambient machine sound. */
    public void setActiveClient(boolean active) {
        isActive = active;
    }

    private static boolean modeAllowsInsertInput(int mode) {
        return mode == SIDE_MODE_INPUT || mode == SIDE_MODE_ALL;
    }

    /**
     * Real TE quirk, verified against the decompiled {@code TileFurnace}
     * ({@code allowExtractionSide[1] = true} for its own Input mode): a side left on plain
     * Input can still have its input slots drained back out.
     */
    private static boolean modeAllowsExtractInput(int mode) {
        return mode == SIDE_MODE_INPUT;
    }

    private static boolean modeAllowsExtractOutput(int mode) {
        return mode == SIDE_MODE_OUTPUT || mode == SIDE_MODE_ALL;
    }

    /**
     * Live per-role slot-highlight queries for the GUI (see GuiAdvancedFurnace) - see
     * TileAdvancedPulverizer#isAnyInputSide for why these scan sideCache live instead of
     * reading any fixed default.
     */
    public boolean isAnyInputSide() {
        for (int side = 0; side < 6; side++) {
            if (modeAllowsInsertInput(sideCache[side])) {
                return true;
            }
        }
        return false;
    }

    public boolean isAnyOutputSide() {
        for (int side = 0; side < 6; side++) {
            if (modeAllowsExtractOutput(sideCache[side])) {
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
        int speedLevel = 0;
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
            speedLevel = Math.max(speedLevel, clampLevel(item.getAugmentLevel(augment, AUG_MACHINE_SPEED), MAX_SPEED_LEVEL));
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

        speedProcessMod = MACHINE_SPEED_PROCESS_MOD[speedLevel];
        speedEnergyMod = MACHINE_SPEED_ENERGY_MOD[speedLevel];
        maxEnergyPerTick = INPUT_SLOTS * BASE_ENERGY_PER_TICK * speedEnergyMod;

        energyStorage.setCapacity(BASE_ENERGY_CAPACITY * ENERGY_STORAGE_MOD[energyLevel]);
        energyStorage.setMaxTransfer(ENERGY_RECEIVE_PER_TICK * ENERGY_STORAGE_MOD[energyLevel]);
        markDirty();
    }

    /**
     * Mirrors real Thermal Expansion's own default-augment behavior (see
     * {@link TileAdvancedPulverizer#installDefaultAugments()} for the decompiled source) -
     * a freshly placed machine already has Auto Output, Redstone Control and Reconfigurable
     * Sides installed, not just an empty augment bay. Only called once, from
     * {@code onBlockPlacedBy}.
     */
    public void installDefaultAugments() {
        setInventorySlotContents(AUGMENT_START, TEAugments.generalAutoOutput.copy());
        setInventorySlotContents(AUGMENT_START + 1, TEAugments.generalRedstoneControl.copy());
        setInventorySlotContents(AUGMENT_START + 2, TEAugments.generalReconfigSides.copy());
    }

    /**
     * Serializes the 9 augment slots (relative index 0-8) so they can travel inside the dropped
     * block item's NBT - see {@code BlockAdvancedFurnace#breakBlock/getDrops}. See
     * {@link TileAdvancedPulverizer#writeAugmentsToNBT} for why this exists.
     */
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

    /** Secondary Output / Null augments aren't recognized here - this machine has no secondary product for them to affect. */
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
                || types.contains(AUG_MACHINE_SPEED) || types.contains(AUG_ENERGY_STORAGE);
    }

    /** See TileAdvancedPulverizer#hasDuplicateAugmentType for why this exists - this mod refuses a second augment of the same type outright rather than silently ignoring it like real TE does. */
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

    /** RF actually drawn on the last tick that ran server-side - what real TE's own "Energy Consumption" line shows. */
    public int getEnergyPerTick() {
        return energyPerTick;
    }

    /** RF/t this machine would draw if all 3 lines were processing at once, at the current Machine Speed augment level. */
    public int getMaxEnergyPerTick() {
        return maxEnergyPerTick;
    }

    /** Takes the RATE_SYNC_SCALE-divided value the container sent and restores the real RF/t. */
    public void setEnergyPerTickClient(int scaled) {
        energyPerTick = scaled * RATE_SYNC_SCALE;
    }

    public void setMaxEnergyPerTickClient(int scaled) {
        maxEnergyPerTick = scaled * RATE_SYNC_SCALE;
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

    /** Client-side only: applies a control-mode ordinal received via the container's progress bar sync. */
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
            // See TileAdvancedPulverizer's own copy of this comment: without this, a client
            // without the GUI open never learns isActive changed, so its face icon stays stuck
            // and the ambient machine sound never starts.
            syncRenderState();
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

        RecipeFurnace recipe = FurnaceManager.getRecipe(input);
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
            int energyCost = BASE_ENERGY_PER_TICK * speedEnergyMod;
            if (energyStorage.getEnergyStored() < energyCost) {
                return false;
            }
            energyStorage.modifyEnergyStored(-energyCost);
            energyPerTick += energyCost;
            progress[line] += BASE_ENERGY_PER_TICK * speedProcessMod;
            return true;
        }

        ItemStack output = recipe.getOutput();
        boolean fits = false;
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            if (canFitStack(OUTPUT_START + i, output)) {
                fits = true;
                break;
            }
        }
        if (!fits) {
            return false;
        }

        addToFirstFitting(output);
        input.stackSize -= recipe.getInput().stackSize;
        if (input.stackSize <= 0) {
            inventory[INPUT_START + line] = null;
        }
        progress[line] = 0;
        progressMax[line] = 0;
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

    private void addToFirstFitting(ItemStack stack) {
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            int slot = OUTPUT_START + i;
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
            if (modeAllowsExtractOutput(sideCache[side]) && pushToSide(ForgeDirection.getOrientation(side))) {
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
            if (candidate == null || !FurnaceManager.recipeExists(candidate)) {
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

    private boolean pushToSide(ForgeDirection dir) {
        TileEntity neighbor = worldObj.getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
        if (!(neighbor instanceof IInventory)) {
            return false;
        }
        IInventory neighborInv = (IInventory) neighbor;
        ForgeDirection toSide = dir.getOpposite();

        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            int slot = OUTPUT_START + i;
            ItemStack stack = inventory[slot];
            if (stack == null) {
                continue;
            }
            if (insertIntoInventory(neighborInv, toSide, stack)) {
                stack.stackSize--;
                if (stack.stackSize <= 0) {
                    inventory[slot] = null;
                }
                return true;
            }
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
        return "container.advancedFurnace";
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
        if (slot < OUTPUT_START) {
            return FurnaceManager.recipeExists(stack);
        }
        return false;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        int mode = sideCache[side];
        boolean input = modeAllowsInsertInput(mode) || modeAllowsExtractInput(mode);
        boolean output = modeAllowsExtractOutput(mode);

        int[] slots = new int[(input ? INPUT_SLOTS : 0) + (output ? OUTPUT_SLOTS : 0)];
        int idx = 0;
        if (input) {
            int start = (int) ((worldObj != null ? worldObj.getTotalWorldTime() : 0) % INPUT_SLOTS);
            for (int i = 0; i < INPUT_SLOTS; i++) {
                slots[idx++] = INPUT_START + (start + i) % INPUT_SLOTS;
            }
        }
        if (output) {
            for (int i = 0; i < OUTPUT_SLOTS; i++) {
                slots[idx++] = OUTPUT_START + i;
            }
        }
        return slots;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return slot < OUTPUT_START && modeAllowsInsertInput(sideCache[side]) && FurnaceManager.recipeExists(stack);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        int mode = sideCache[side];
        if (slot < OUTPUT_START) {
            return modeAllowsExtractInput(mode);
        }
        if (slot < AUGMENT_START) {
            return modeAllowsExtractOutput(mode);
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
            // See TileAdvancedPulverizer#readFromNBT's identical guard - tampered/foreign NBT
            // could otherwise carry a mode outside SIDE_MODE_COUNT and later index
            // BlockAdvancedFurnace's per-mode icon arrays out of bounds during rendering.
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

    /** See TileAdvancedPulverizer#getDescriptionPacket for why this exists - without it, a freshly loaded chunk never tells the client this tile's facing/sides/augments, so it renders with whatever defaults it was constructed with. */
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
