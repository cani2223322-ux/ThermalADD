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
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.IFluidHandler;

import cofh.api.energy.EnergyStorage;
import cofh.api.energy.IEnergyContainerItem;
import cofh.api.energy.IEnergyReceiver;
import cofh.api.item.IAugmentItem;
import cofh.api.tileentity.IEnergyInfo;
import cofh.api.tileentity.IPortableData;
import cofh.api.tileentity.IRedstoneControl;
import cofh.thermalexpansion.item.TEAugments;
import cpw.mods.fml.common.network.NetworkRegistry;
import net.thermaladd.mod.network.MessageTileRenderSync;
import net.thermaladd.mod.network.PacketHandler;
import net.thermaladd.mod.util.IPortableMachineState;
import net.thermaladd.mod.util.LineLocks;
import net.thermaladd.mod.util.SideRotation;

/**
 * Shared base of the second generation of singularity machines (Induction Smelter, Magma
 * Crucible, Fluid Transposer). The first five machines each carry their own full copy of this
 * plumbing; these three differ only in their slots, their side modes and what one processing line
 * does, so everything else lives here once: the RF buffer, the nine augment slots and what they
 * change, the side configuration, redstone control, the charge slot, line locks, auto input and
 * output, Redprint support, dismantling state, NBT and client sync.
 *
 * Inventory layout is fixed: the machine's own slots first (lockable input slots at the very
 * start), then {@link #AUGMENT_SLOTS} augment slots, then one charge slot last.
 *
 * A subclass describes its sides through {@link #sideInserts}/{@link #sideExtracts} per mode and
 * slot, which is all the generic code needs to answer pipes, run auto input/output and colour the
 * GUI's slot rings.
 */
public abstract class TileSingularityMachine extends TileEntity
        implements ISidedInventory, IEnergyReceiver, IRedstoneControl, IEnergyInfo, IPortableData, IPortableMachineState {

    public static final String TIER_NAME = "UltimateResonant";
    public static final int AUGMENT_SLOTS = 9;
    public static final int SIDE_MODE_DISABLED = 0;
    /** North/East/South/West facing metas, same order as the other machines' wrench rotation. */
    public static final int[] FACING_META = {2, 5, 3, 4};

    public static final String AUG_GENERAL_AUTO_OUTPUT = "generalAutoOutput";
    public static final String AUG_GENERAL_AUTO_INPUT = "generalAutoInput";
    public static final String AUG_GENERAL_RECONFIG_SIDES = "generalReconfigSides";
    public static final String AUG_GENERAL_REDSTONE_CONTROL = "generalRedstoneControl";
    public static final String AUG_MACHINE_SPEED = "machineSpeed";
    public static final String AUG_MACHINE_SECONDARY = "machineSecondary";
    public static final String AUG_MACHINE_NULL = "machineNull";
    public static final String AUG_ENERGY_STORAGE = "energyStorage";

    /** Same tables as TileAdvancedPulverizer - level 4 is this mod's own Speed IV / Sieve IV. */
    private static final int[] MACHINE_SPEED_PROCESS_MOD = {1, 2, 4, 8, 10};
    private static final int[] MACHINE_SPEED_ENERGY_MOD = {1, 3, 8, 20, 60};
    private static final int[] MACHINE_SECONDARY_MOD = {0, 10, 15, 20, 200};
    private static final int[] MACHINE_SECONDARY_ENERGY_PCT = {100, 100, 100, 100, 125};
    private static final int[] ENERGY_STORAGE_MOD = {1, 2, 4, 8};
    private static final int MAX_AUGMENT_LEVEL = 3;
    private static final int MAX_SPEED_LEVEL = 4;
    private static final int MAX_SECONDARY_LEVEL = 4;

    private static final int AUTO_IO_INTERVAL = 8;
    /** Items moved per side per auto-I/O pass - enough to keep up with three lines at Speed IV. */
    private static final int AUTO_ITEM_TRANSFER = 16;
    /** mB pushed out per tick through all fluid-output sides together (real TE pushes 500). */
    private static final int AUTO_FLUID_TRANSFER = 1000;

    public boolean augmentAutoInput = false;
    public boolean augmentAutoOutput = false;
    public boolean augmentReconfigSides = false;
    public boolean augmentRedstoneControl = false;
    public boolean augmentSecondaryNull = false;
    /** Real TE's default - a freshly installed Redstone Control augment starts on "Low". */
    private ControlMode rsMode = ControlMode.LOW;
    private boolean rsPowered = false;

    protected int speedProcessMod = 1;
    protected int speedEnergyMod = 1;
    protected int secondaryEnergyPct = 100;
    protected int secondaryChanceDivisor = 100;
    private int autoIOTimer = 0;

    protected final ItemStack[] inventory;
    protected final EnergyStorage energyStorage;
    protected final int[] progress;
    protected final int[] progressMax;
    protected final LineLocks lineLocks;
    /** Null on machines without a tank. */
    protected final FluidTank tank;

    protected int energyPerTick = 0;
    protected int maxEnergyPerTick;

    private String customName;
    protected byte facing = 3;
    protected byte[] sideCache = new byte[6];
    protected boolean isActive = false;

    /**
     * @param machineSlots  the machine's own slots (inputs, outputs), not counting augments/charge
     * @param lines         parallel processing lines
     * @param lockableSlots how many of the first slots can be locked to an item
     * @param tankCapacity  mB, or 0 for no tank
     */
    protected TileSingularityMachine(int machineSlots, int lines, int lockableSlots, int tankCapacity) {
        inventory = new ItemStack[machineSlots + AUGMENT_SLOTS + 1];
        energyStorage = new EnergyStorage(getBaseCapacity(), getBaseReceive());
        progress = new int[lines];
        progressMax = new int[lines];
        lineLocks = new LineLocks(lockableSlots);
        tank = tankCapacity > 0 ? new FluidTank(tankCapacity) : null;
        maxEnergyPerTick = lines * getBaseEnergyPerTick();
    }

    // ---------------------------------------------------------------- what a subclass defines

    /** These three read the subclass's config-backed statics. */
    protected abstract int getBaseCapacity();

    protected abstract int getBaseReceive();

    public abstract int getBaseEnergyPerTick();

    /** Block/lang name, e.g. "singularSmelter". */
    public abstract String getMachineKey();

    public abstract int getSideModeCount();

    /** Badge texture base name per side mode ("Input", "OutputPrimary", ...); entry 0 is unused. */
    public abstract String[] getSideModeBadges();

    /** Lang key per side mode. */
    public abstract String[] getSideModeNameKeys();

    /** Real TE face texture suffix: "Smelter" for Machine_Face_Smelter/Machine_Active_Smelter. */
    public abstract String getFaceTextureName();

    /** Ambient sound while working, or null. */
    public abstract String getSoundName();

    /** Whether the Secondary Sieve and Auxiliary Reception augments do anything here. */
    protected abstract boolean usesSecondaryAugments();

    /** Can a side in {@code mode} insert into this machine slot? */
    public abstract boolean sideInserts(int mode, int slot);

    /** Can a side in {@code mode} extract from this machine slot? */
    public abstract boolean sideExtracts(int mode, int slot);

    public abstract boolean isOutputSlot(int slot);

    /** What may go into a machine slot, before line locks are applied. */
    protected abstract boolean isValidMachineInput(int slot, ItemStack stack);

    /** One line's share of a tick. Returns whether anything changed. */
    protected abstract boolean processLine(int line);

    /** For the comparator: does this line hold something to work on? */
    public abstract boolean isLineOccupied(int line);

    /** Fluid-capable machines: does a side in {@code mode} push its tank out? */
    protected boolean sideDrainsFluid(int mode) {
        return false;
    }

    // ---------------------------------------------------------------- layout

    public int getAugmentStart() {
        return inventory.length - AUGMENT_SLOTS - 1;
    }

    public int getChargeSlot() {
        return inventory.length - 1;
    }

    public int getLineCount() {
        return progress.length;
    }

    public boolean isAugmentSlot(int slot) {
        return slot >= getAugmentStart() && slot < getChargeSlot();
    }

    /** Lock index of a slot, or -1 when it cannot be locked. */
    public int lockIndexOf(int slot) {
        return slot >= 0 && slot < lineLocks.size() ? slot : -1;
    }

    public boolean isActive() {
        return isActive;
    }

    // ---------------------------------------------------------------- machine mode (e.g. Transposer fill/extract)

    public int getMachineMode() {
        return 0;
    }

    /** Server side, from MessageMachineMode. Machines without a mode ignore it. */
    public void setMachineMode(int mode) {
    }

    /** Client side, from the container sync. */
    public void setMachineModeClient(int mode) {
    }

    // ---------------------------------------------------------------- comparator

    /** Occupied lines scaled to 1-15, 0 when every line is empty - same rule as the other machines. */
    public int getComparatorSignal() {
        int occupied = 0;
        for (int i = 0; i < getLineCount(); i++) {
            if (isLineOccupied(i)) {
                occupied++;
            }
        }
        return occupied == 0 ? 0 : 1 + (occupied * 14) / getLineCount();
    }

    // ---------------------------------------------------------------- IPortableData (TE Redprint)

    @Override
    public String getDataType() {
        return "tile.thermaladd." + getMachineKey();
    }

    @Override
    public void writePortableData(EntityPlayer player, NBTTagCompound tag) {
        tag.setByteArray("SideCache", sideCache.clone());
        tag.setByte("Facing", facing);
        tag.setByte("RSControl", (byte) rsMode.ordinal());
    }

    @Override
    public void readPortableData(EntityPlayer player, NBTTagCompound tag) {
        if (augmentReconfigSides && tag.hasKey("SideCache")) {
            applySideModes(tag.getByteArray("SideCache"), tag.hasKey("Facing") ? tag.getByte("Facing") : -1);
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
    public void applySideModes(byte[] modes, int sourceFacing) {
        if (modes == null || modes.length != sideCache.length || !isValidSideArray(modes)) {
            return;
        }
        byte[] rotated = SideRotation.rotate(modes, sourceFacing, facing);
        if (facing >= 0 && facing < rotated.length) {
            rotated[facing] = SIDE_MODE_DISABLED;
        }
        sideCache = rotated;
        markDirty();
        syncRenderState();
    }

    public void rotateFacing(int newFacing) {
        byte[] rotated = SideRotation.rotate(sideCache, facing, newFacing);
        rotated[newFacing] = SIDE_MODE_DISABLED;
        sideCache = rotated;
        setFacing(newFacing);
    }

    public void clearContentsOnBreak() {
        for (int i = 0; i < inventory.length; i++) {
            inventory[i] = null;
        }
    }

    @Override
    public void setStoredEnergy(int energy) {
        energyStorage.setEnergyStored(Math.max(0, Math.min(energy, energyStorage.getMaxEnergyStored())));
        markDirty();
    }

    @Override
    public int getFacing() {
        return facing;
    }

    public void setFacing(int meta) {
        facing = (byte) meta;
        markDirty();
        syncRenderState();
    }

    // ---------------------------------------------------------------- side configuration

    public int getSideMode(int side) {
        return sideCache[side];
    }

    private static boolean isValidSide(int side) {
        return side >= 0 && side < 6;
    }

    private boolean isValidSideArray(byte[] sides) {
        for (byte mode : sides) {
            if (mode < 0 || mode >= getSideModeCount()) {
                return false;
            }
        }
        return true;
    }

    public boolean cycleSideMode(int side, int direction) {
        if (!isValidSide(side) || !augmentReconfigSides || side == facing) {
            return false;
        }
        int count = getSideModeCount();
        sideCache[side] = (byte) (((sideCache[side] + direction) % count + count) % count);
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

    public void setDefaultSides() {
        for (int i = 0; i < sideCache.length; i++) {
            sideCache[i] = SIDE_MODE_DISABLED;
        }
        markDirty();
        syncRenderState();
    }

    /** See TileAdvancedPulverizer#syncRenderState. */
    protected void syncRenderState() {
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
        if (isValidSide(side) && mode >= 0 && mode < getSideModeCount()) {
            sideCache[side] = (byte) mode;
        }
    }

    public void setActiveClient(boolean active) {
        isActive = active;
    }

    /** For the GUI's slot rings: is some side currently feeding this slot? */
    public boolean isSlotInsertedByAnySide(int slot) {
        for (int side = 0; side < 6; side++) {
            if (sideInserts(sideCache[side], slot)) {
                return true;
            }
        }
        return false;
    }

    public boolean isSlotExtractedByAnySide(int slot) {
        for (int side = 0; side < 6; side++) {
            if (sideExtracts(sideCache[side], slot)) {
                return true;
            }
        }
        return false;
    }

    public boolean isFluidDrainedByAnySide() {
        for (int side = 0; side < 6; side++) {
            if (sideDrainsFluid(sideCache[side])) {
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
            ItemStack augment = inventory[getAugmentStart() + i];
            if (!isAugmentItem(augment)) {
                continue;
            }
            IAugmentItem item = (IAugmentItem) augment.getItem();
            autoOutput |= item.getAugmentLevel(augment, AUG_GENERAL_AUTO_OUTPUT) > 0;
            autoInput |= item.getAugmentLevel(augment, AUG_GENERAL_AUTO_INPUT) > 0;
            reconfigSides |= item.getAugmentLevel(augment, AUG_GENERAL_RECONFIG_SIDES) > 0;
            redstoneControl |= item.getAugmentLevel(augment, AUG_GENERAL_REDSTONE_CONTROL) > 0;
            speedLevel = Math.max(speedLevel, clampLevel(item.getAugmentLevel(augment, AUG_MACHINE_SPEED), MAX_SPEED_LEVEL));
            energyLevel = Math.max(energyLevel, clampLevel(item.getAugmentLevel(augment, AUG_ENERGY_STORAGE), MAX_AUGMENT_LEVEL));
            if (usesSecondaryAugments()) {
                secondaryNull |= item.getAugmentLevel(augment, AUG_MACHINE_NULL) > 0;
                secondaryLevel = Math.max(secondaryLevel, clampLevel(item.getAugmentLevel(augment, AUG_MACHINE_SECONDARY), MAX_SECONDARY_LEVEL));
            }
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
        secondaryChanceDivisor = Math.max(1, 100 - MACHINE_SECONDARY_MOD[secondaryLevel]);
        maxEnergyPerTick = getLineCount() * getLineEnergyCost();

        energyStorage.setCapacity(getBaseCapacity() * ENERGY_STORAGE_MOD[energyLevel]);
        energyStorage.setMaxTransfer(getBaseReceive() * ENERGY_STORAGE_MOD[energyLevel]);
        markDirty();
    }

    /** Real TE's crafted-machine defaults - see TileAdvancedPulverizer#installDefaultAugments. */
    public void installDefaultAugments() {
        setInventorySlotContents(getAugmentStart(), TEAugments.generalAutoOutput.copy());
        setInventorySlotContents(getAugmentStart() + 1, TEAugments.generalRedstoneControl.copy());
        setInventorySlotContents(getAugmentStart() + 2, TEAugments.generalReconfigSides.copy());
    }

    /** Always writes the tag, even empty - see MachineDismantle#createDrop. */
    @Override
    public NBTTagCompound writeAugmentsToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < AUGMENT_SLOTS; i++) {
            ItemStack stack = inventory[getAugmentStart() + i];
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

    @Override
    public void readAugmentsFromNBT(NBTTagCompound tag) {
        NBTTagList list = tag.getTagList("Augments", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound itemTag = list.getCompoundTagAt(i);
            int slot = itemTag.getByte("Slot") & 255;
            if (slot < AUGMENT_SLOTS) {
                inventory[getAugmentStart() + slot] = ItemStack.loadItemStackFromNBT(itemTag);
            }
        }
        installAugments();
    }

    private static int clampLevel(int level, int max) {
        return level < 0 ? 0 : Math.min(level, max);
    }

    public static boolean isAugmentItem(ItemStack stack) {
        return stack != null && stack.getItem() instanceof IAugmentItem;
    }

    public boolean isValidAugment(ItemStack stack) {
        if (!isAugmentItem(stack)) {
            return false;
        }
        Set<String> types = ((IAugmentItem) stack.getItem()).getAugmentTypes(stack);
        if (types == null) {
            return false;
        }
        if (types.contains(AUG_GENERAL_AUTO_OUTPUT) || types.contains(AUG_GENERAL_AUTO_INPUT)
                || types.contains(AUG_GENERAL_RECONFIG_SIDES) || types.contains(AUG_GENERAL_REDSTONE_CONTROL)
                || types.contains(AUG_MACHINE_SPEED) || types.contains(AUG_ENERGY_STORAGE)) {
            return true;
        }
        return usesSecondaryAugments() && (types.contains(AUG_MACHINE_SECONDARY) || types.contains(AUG_MACHINE_NULL));
    }

    /** See TileAdvancedPulverizer#hasDuplicateAugmentType. */
    public boolean hasDuplicateAugmentType(ItemStack candidate, int excludeSlot) {
        if (!isAugmentItem(candidate)) {
            return false;
        }
        Set<String> types = ((IAugmentItem) candidate.getItem()).getAugmentTypes(candidate);
        if (types == null || types.isEmpty()) {
            return false;
        }
        for (int i = 0; i < AUGMENT_SLOTS; i++) {
            int slot = getAugmentStart() + i;
            ItemStack other = inventory[slot];
            if (slot == excludeSlot || !isAugmentItem(other)) {
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

    @Override
    public int getEnergy() {
        return energyStorage.getEnergyStored();
    }

    public int getMaxEnergy() {
        return energyStorage.getMaxEnergyStored();
    }

    public int getEnergyPerTick() {
        return energyPerTick;
    }

    public int getMaxEnergyPerTick() {
        return maxEnergyPerTick;
    }

    /** RF one working line draws per tick with the current augments. */
    public int getLineEnergyCost() {
        return getBaseEnergyPerTick() * speedEnergyMod * secondaryEnergyPct / 100;
    }

    @Override
    public int getInfoEnergyPerTick() {
        return energyPerTick;
    }

    @Override
    public int getInfoMaxEnergyPerTick() {
        return maxEnergyPerTick;
    }

    @Override
    public int getInfoEnergyStored() {
        return getEnergy();
    }

    @Override
    public int getInfoMaxEnergyStored() {
        return getMaxEnergy();
    }

    // ---------------------------------------------------------------- client sync (exact 16-bit halves)

    private int clientEnergyLow;
    private int clientEnergyHigh;
    private int clientMaxEnergyLow;
    private int clientMaxEnergyHigh;

    public void setEnergyHalfClient(boolean high, int value) {
        if (high) {
            clientEnergyHigh = value & 0xFFFF;
        } else {
            clientEnergyLow = value & 0xFFFF;
        }
        applyClientEnergy();
    }

    public void setMaxEnergyHalfClient(boolean high, int value) {
        if (high) {
            clientMaxEnergyHigh = value & 0xFFFF;
        } else {
            clientMaxEnergyLow = value & 0xFFFF;
        }
        applyClientEnergy();
    }

    /** Capacity first - CoFH's setEnergyStored clamps to it. */
    private void applyClientEnergy() {
        int capacity = clientMaxEnergyHigh << 16 | clientMaxEnergyLow;
        if (capacity > 0) {
            energyStorage.setCapacity(capacity);
        }
        energyStorage.setEnergyStored(clientEnergyHigh << 16 | clientEnergyLow);
    }

    public void setEnergyPerTickHalfClient(boolean high, int value) {
        energyPerTick = setHalf(energyPerTick, high, value);
    }

    public void setMaxEnergyPerTickHalfClient(boolean high, int value) {
        maxEnergyPerTick = setHalf(maxEnergyPerTick, high, value);
    }

    public void setProgressHalfClient(int line, boolean high, int value) {
        if (line >= 0 && line < progress.length) {
            progress[line] = setHalf(progress[line], high, value);
        }
    }

    public void setProgressMaxHalfClient(int line, boolean high, int value) {
        if (line >= 0 && line < progressMax.length) {
            progressMax[line] = setHalf(progressMax[line], high, value);
        }
    }

    protected static int setHalf(int current, boolean high, int value) {
        return high ? ((value & 0xFFFF) << 16) | (current & 0xFFFF) : (current & 0xFFFF0000) | (value & 0xFFFF);
    }

    public int getProgress(int line) {
        return progress[line];
    }

    public int getProgressMax(int line) {
        return progressMax[line];
    }

    public void setControlClient(int ordinal) {
        if (ordinal >= 0 && ordinal < ControlMode.values().length) {
            rsMode = ControlMode.values()[ordinal];
        }
    }

    // ---------------------------------------------------------------- tank

    public boolean hasTank() {
        return tank != null;
    }

    public FluidStack getTankFluid() {
        return tank == null ? null : tank.getFluid();
    }

    public int getTankCapacity() {
        return tank == null ? 0 : tank.getCapacity();
    }

    private int clientFluidId = -1;
    private int clientFluidAmount = 0;

    public void setTankFluidIdClient(int fluidId) {
        clientFluidId = fluidId;
        rebuildClientTank();
    }

    public void setTankAmountHalfClient(boolean high, int value) {
        clientFluidAmount = setHalf(clientFluidAmount, high, value);
        rebuildClientTank();
    }

    private void rebuildClientTank() {
        if (tank == null) {
            return;
        }
        Fluid fluid = clientFluidId < 0 ? null : FluidRegistry.getFluid(clientFluidId);
        tank.setFluid(fluid == null || clientFluidAmount <= 0 ? null : new FluidStack(fluid, clientFluidAmount));
    }

    /** Pushes the tank out of every fluid-output side, AUTO_FLUID_TRANSFER mB per tick in total. */
    private boolean pushFluid() {
        if (tank == null || !augmentAutoOutput || tank.getFluidAmount() <= 0) {
            return false;
        }
        int budget = AUTO_FLUID_TRANSFER;
        boolean moved = false;
        for (int side = 0; side < 6 && budget > 0 && tank.getFluidAmount() > 0; side++) {
            if (!sideDrainsFluid(sideCache[side])) {
                continue;
            }
            ForgeDirection dir = ForgeDirection.getOrientation(side);
            TileEntity neighbor = worldObj.getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
            if (!(neighbor instanceof IFluidHandler)) {
                continue;
            }
            FluidStack offer = tank.getFluid().copy();
            offer.amount = Math.min(offer.amount, budget);
            int filled = ((IFluidHandler) neighbor).fill(dir.getOpposite(), offer, true);
            if (filled > 0) {
                tank.drain(filled, true);
                budget -= filled;
                moved = true;
            }
        }
        return moved;
    }

    // ---------------------------------------------------------------- redstone control

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
        if (pushFluid()) {
            dirty = true;
        }

        setPowered(worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord));
        boolean redstoneAllows = !augmentRedstoneControl || rsMode.isDisabled() || rsMode.isHigh() == isPowered();
        if (redstoneAllows) {
            for (int line = 0; line < getLineCount(); line++) {
                if (processLine(line)) {
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

        boolean nowActive = computeActive();
        if (nowActive != isActive) {
            isActive = nowActive;
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            syncRenderState();
            dirty = true;
        }

        if (dirty) {
            markDirty();
        }
    }

    protected boolean computeActive() {
        for (int line = 0; line < getLineCount(); line++) {
            if (progressMax[line] > 0) {
                return true;
            }
        }
        return false;
    }

    /** See TileAdvancedPulverizer#chargeFromItem. */
    private boolean chargeFromItem() {
        int slot = getChargeSlot();
        ItemStack stack = inventory[slot];
        if (stack == null || !(stack.getItem() instanceof IEnergyContainerItem)) {
            return false;
        }
        int receive = Math.min(energyStorage.getMaxReceive(), energyStorage.getMaxEnergyStored() - energyStorage.getEnergyStored());
        if (receive <= 0) {
            return false;
        }
        int extracted = ((IEnergyContainerItem) stack.getItem()).extractEnergy(stack, receive, false);
        if (extracted <= 0) {
            return false;
        }
        energyStorage.receiveEnergy(extracted, false);
        if (stack.stackSize <= 0) {
            inventory[slot] = null;
        }
        return true;
    }

    // ---------------------------------------------------------------- line helpers for subclasses

    /** Drops a line's progress when its input went away. Returns whether that changed anything. */
    protected boolean idleLine(int line) {
        if (progressMax[line] != 0 || progress[line] != 0) {
            progress[line] = 0;
            progressMax[line] = 0;
            return true;
        }
        return false;
    }

    /**
     * Spends one tick of work on a line whose current recipe costs {@code recipeEnergy} in total.
     * Progress restarts if the recipe changed under it (a different item was put in). Returns
     * whether work was done; false both when the line is already complete and when the buffer
     * cannot pay for this tick.
     */
    protected boolean workLine(int line, int recipeEnergy) {
        if (progressMax[line] != recipeEnergy) {
            progress[line] = 0;
            progressMax[line] = recipeEnergy;
        }
        if (progress[line] >= progressMax[line]) {
            return false;
        }
        int cost = getLineEnergyCost();
        if (energyStorage.getEnergyStored() < cost) {
            return false;
        }
        energyStorage.modifyEnergyStored(-cost);
        energyPerTick += cost;
        progress[line] += getBaseEnergyPerTick() * speedProcessMod;
        return true;
    }

    protected boolean isLineComplete(int line) {
        return progressMax[line] > 0 && progress[line] >= progressMax[line];
    }

    protected void resetLine(int line) {
        progress[line] = 0;
        progressMax[line] = 0;
    }

    /** Secondary-output roll, with the Secondary Sieve's divisor - same rule as the other machines. */
    protected boolean rollChance(int chance) {
        return chance >= 100 || worldObj.rand.nextInt(secondaryChanceDivisor) < chance;
    }

    protected boolean canFitStack(int slot, ItemStack stack) {
        ItemStack existing = inventory[slot];
        if (existing == null) {
            return true;
        }
        return stacksMatch(existing, stack) && existing.stackSize + stack.stackSize <= existing.getMaxStackSize();
    }

    protected boolean canFitAny(int firstSlot, int count, ItemStack stack) {
        for (int i = 0; i < count; i++) {
            if (canFitStack(firstSlot + i, stack)) {
                return true;
            }
        }
        return false;
    }

    protected void addToFirstFitting(int firstSlot, int count, ItemStack stack) {
        for (int i = 0; i < count; i++) {
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

    protected void consumeInput(int slot, int amount) {
        if (inventory[slot] == null) {
            return;
        }
        inventory[slot].stackSize -= amount;
        if (inventory[slot].stackSize <= 0) {
            inventory[slot] = null;
        }
    }

    protected static boolean stacksMatch(ItemStack a, ItemStack b) {
        return a != null && b != null && a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage()
                && ItemStack.areItemStackTagsEqual(a, b);
    }

    // ---------------------------------------------------------------- auto input / output

    private boolean autoPullInputs() {
        boolean moved = false;
        for (int side = 0; side < 6; side++) {
            if (modeInsertsAnything(sideCache[side]) && pullFromSide(side)) {
                moved = true;
            }
        }
        return moved;
    }

    private boolean modeInsertsAnything(int mode) {
        for (int slot = 0; slot < getAugmentStart(); slot++) {
            if (sideInserts(mode, slot)) {
                return true;
            }
        }
        return false;
    }

    private boolean pullFromSide(int side) {
        ForgeDirection dir = ForgeDirection.getOrientation(side);
        TileEntity neighbor = worldObj.getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
        if (!(neighbor instanceof IInventory)) {
            return false;
        }
        IInventory neighborInv = (IInventory) neighbor;
        int fromSide = dir.getOpposite().ordinal();
        int[] slots = neighborInv instanceof ISidedInventory
                ? ((ISidedInventory) neighborInv).getAccessibleSlotsFromSide(fromSide)
                : allSlots(neighborInv);

        int budget = AUTO_ITEM_TRANSFER;
        for (int slotIdx : slots) {
            ItemStack candidate = neighborInv.getStackInSlot(slotIdx);
            if (candidate == null) {
                continue;
            }
            if (neighborInv instanceof ISidedInventory
                    && !((ISidedInventory) neighborInv).canExtractItem(slotIdx, candidate, fromSide)) {
                continue;
            }
            int target = findInsertSlot(candidate, sideCache[side]);
            if (target < 0) {
                continue;
            }
            int room = inventory[target] == null
                    ? Math.min(candidate.getMaxStackSize(), getInventoryStackLimit())
                    : inventory[target].getMaxStackSize() - inventory[target].stackSize;
            int amount = Math.min(Math.min(room, candidate.stackSize), budget);
            if (amount <= 0) {
                continue;
            }
            if (inventory[target] == null) {
                ItemStack moved = candidate.copy();
                moved.stackSize = amount;
                inventory[target] = moved;
            } else {
                inventory[target].stackSize += amount;
            }
            neighborInv.decrStackSize(slotIdx, amount);
            neighborInv.markDirty();
            return true;
        }
        return false;
    }

    /**
     * The slot an incoming stack should go to through a side in {@code mode}: first a slot already
     * holding the same item, then an empty slot locked to it, then any empty unlocked slot - the
     * same preference order the other machines' findInputSlotFor uses. -1 when nothing takes it.
     */
    protected int findInsertSlot(ItemStack stack, int mode) {
        int emptyLocked = -1;
        int emptyFree = -1;
        for (int slot = 0; slot < getAugmentStart(); slot++) {
            if (!sideInserts(mode, slot) || !isItemValidForSlot(slot, stack)) {
                continue;
            }
            ItemStack existing = inventory[slot];
            if (existing == null) {
                int lock = lockIndexOf(slot);
                if (lock >= 0 && lineLocks.isLocked(lock)) {
                    if (emptyLocked < 0) {
                        emptyLocked = slot;
                    }
                } else if (emptyFree < 0) {
                    emptyFree = slot;
                }
            } else if (stacksMatch(existing, stack) && existing.stackSize < existing.getMaxStackSize()) {
                return slot;
            }
        }
        return emptyLocked >= 0 ? emptyLocked : emptyFree;
    }

    private boolean autoPushOutputs() {
        boolean moved = false;
        for (int side = 0; side < 6; side++) {
            int mode = sideCache[side];
            for (int slot = 0; slot < getAugmentStart(); slot++) {
                if (isOutputSlot(slot) && sideExtracts(mode, slot) && inventory[slot] != null && pushSlot(side, slot)) {
                    moved = true;
                    break;
                }
            }
        }
        return moved;
    }

    private boolean pushSlot(int side, int slot) {
        ForgeDirection dir = ForgeDirection.getOrientation(side);
        TileEntity neighbor = worldObj.getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
        if (!(neighbor instanceof IInventory)) {
            return false;
        }
        ItemStack stack = inventory[slot];
        int inserted = insertIntoInventory((IInventory) neighbor, dir.getOpposite().ordinal(), stack,
                Math.min(stack.stackSize, AUTO_ITEM_TRANSFER));
        if (inserted <= 0) {
            return false;
        }
        stack.stackSize -= inserted;
        if (stack.stackSize <= 0) {
            inventory[slot] = null;
        }
        return true;
    }

    /** Inserts up to {@code max} of {@code stack} (which is not modified). Returns how many went in. */
    private static int insertIntoInventory(IInventory inv, int side, ItemStack stack, int max) {
        int[] slots = inv instanceof ISidedInventory ? ((ISidedInventory) inv).getAccessibleSlotsFromSide(side) : allSlots(inv);
        int remaining = max;
        for (int slotIdx : slots) {
            if (remaining <= 0) {
                break;
            }
            ItemStack single = stack.copy();
            single.stackSize = 1;
            if (inv instanceof ISidedInventory && !((ISidedInventory) inv).canInsertItem(slotIdx, single, side)) {
                continue;
            }
            if (!inv.isItemValidForSlot(slotIdx, single)) {
                continue;
            }
            int limit = Math.min(stack.getMaxStackSize(), inv.getInventoryStackLimit());
            ItemStack existing = inv.getStackInSlot(slotIdx);
            if (existing == null) {
                ItemStack put = stack.copy();
                put.stackSize = Math.min(remaining, limit);
                inv.setInventorySlotContents(slotIdx, put);
                remaining -= put.stackSize;
            } else if (stacksMatch(existing, stack) && existing.stackSize < limit) {
                int add = Math.min(remaining, limit - existing.stackSize);
                existing.stackSize += add;
                remaining -= add;
            }
        }
        if (remaining != max) {
            inv.markDirty();
        }
        return max - remaining;
    }

    private static int[] allSlots(IInventory inv) {
        int[] slots = new int[inv.getSizeInventory()];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = i;
        }
        return slots;
    }

    // ---------------------------------------------------------------- line locks

    public LineLocks getLineLocks() {
        return lineLocks;
    }

    public void toggleLineLock(int slot) {
        int lock = lockIndexOf(slot);
        if (lock < 0) {
            return;
        }
        if (lineLocks.toggle(lock, inventory[slot])) {
            markDirty();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    // ---------------------------------------------------------------- inventory

    @Override
    public int getSizeInventory() {
        return inventory.length;
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

    @Override
    public String getInventoryName() {
        return hasCustomInventoryName() ? customName : "tile." + getMachineKey() + ".name";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return customName != null && customName.length() > 0;
    }

    public void setCustomName(String name) {
        customName = name;
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
        if (stack == null) {
            return false;
        }
        if (slot == getChargeSlot()) {
            return stack.getItem() instanceof IEnergyContainerItem;
        }
        if (isAugmentSlot(slot)) {
            return isValidAugment(stack) && !hasDuplicateAugmentType(stack, slot);
        }
        if (slot < 0 || slot >= getAugmentStart() || isOutputSlot(slot)) {
            return false;
        }
        int lock = lockIndexOf(slot);
        return (lock < 0 || lineLocks.accepts(lock, stack)) && isValidMachineInput(slot, stack);
    }

    /** Every machine slot some mode of this side touches, rotated each tick so pipes spread over the lines. */
    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        if (!isValidSide(side)) {
            return new int[0];
        }
        int mode = sideCache[side];
        int count = 0;
        for (int slot = 0; slot < getAugmentStart(); slot++) {
            if (sideInserts(mode, slot) || sideExtracts(mode, slot)) {
                count++;
            }
        }
        int[] slots = new int[count];
        if (count == 0) {
            return slots;
        }
        int start = (int) ((worldObj != null ? worldObj.getTotalWorldTime() : 0) % count);
        int idx = 0;
        for (int slot = 0; slot < getAugmentStart(); slot++) {
            if (sideInserts(mode, slot) || sideExtracts(mode, slot)) {
                slots[(idx + count - start) % count] = slot;
                idx++;
            }
        }
        return slots;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return isValidSide(side) && slot < getAugmentStart() && sideInserts(sideCache[side], slot)
                && isItemValidForSlot(slot, stack);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        return isValidSide(side) && slot < getAugmentStart() && sideExtracts(sideCache[side], slot);
    }

    // ---------------------------------------------------------------- nbt

    /** Extra state of a subclass (modes, trackers). */
    protected void writeMachineToNBT(NBTTagCompound tag) {
    }

    protected void readMachineFromNBT(NBTTagCompound tag) {
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        energyStorage.writeToNBT(tag);
        tag.setByte("Facing", facing);
        tag.setByteArray("Sides", sideCache);
        tag.setIntArray("Progress", progress);
        tag.setIntArray("ProgressMax", progressMax);
        tag.setByte("RSControl", (byte) rsMode.ordinal());
        if (hasCustomInventoryName()) {
            tag.setString("CustomName", customName);
        }
        lineLocks.writeToNBT(tag);
        if (tank != null) {
            tag.setTag("Tank", tank.writeToNBT(new NBTTagCompound()));
        }
        writeMachineToNBT(tag);

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
        facing = tag.getByte("Facing");
        if (tag.hasKey("CustomName")) {
            customName = tag.getString("CustomName");
        }
        lineLocks.readFromNBT(tag);
        if (tag.hasKey("RSControl")) {
            int ordinal = tag.getByte("RSControl") & 0xFF;
            if (ordinal < ControlMode.values().length) {
                rsMode = ControlMode.values()[ordinal];
            }
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
        if (tank != null) {
            tank.setFluid(null);
            if (tag.hasKey("Tank")) {
                tank.readFromNBT(tag.getCompoundTag("Tank"));
            }
        }
        readMachineFromNBT(tag);

        NBTTagList items = tag.getTagList("Items", 10);
        for (int i = 0; i < inventory.length; i++) {
            inventory[i] = null;
        }
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound itemTag = items.getCompoundTagAt(i);
            int slot = itemTag.getByte("Slot") & 255;
            if (slot < inventory.length) {
                inventory[slot] = ItemStack.loadItemStackFromNBT(itemTag);
            }
        }

        installAugments();
        // After installAugments(), never before: CoFH clamps the loaded value to the current
        // capacity, which only the Energy Storage augment raises.
        energyStorage.readFromNBT(tag);
        isActive = computeActive();
    }

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
