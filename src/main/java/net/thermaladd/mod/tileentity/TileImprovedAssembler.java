package net.thermaladd.mod.tileentity;

import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;
import net.minecraftforge.oredict.OreDictionary;

import cofh.api.energy.IEnergyContainerItem;
import cofh.api.energy.IEnergyReceiver;
import cofh.api.item.IAugmentItem;
import cofh.api.tileentity.IEnergyInfo;
import cofh.api.tileentity.IRedstoneControl;
import cofh.thermalexpansion.item.TEAugments;
import cpw.mods.fml.common.network.NetworkRegistry;
import net.thermaladd.mod.network.MessageTileRenderSync;
import net.thermaladd.mod.network.PacketHandler;

/**
 * Improved Cyclic Assembler.
 *
 * Modeled directly on Thermal Expansion's own TileAssembler (decompiled from
 * thermalexpansion-1.7.10-4.1.5-248.jar): a schematic ItemStack carries the full
 * 3x3 crafting pattern in NBT ("SlotN" item + optional "OreN" OreDictionary tag
 * per cell), the machine rebuilds a real crafting grid from its material buffer,
 * looks the result up via the vanilla recipe list and crafts for a flat RF cost
 * (no artificial "progress" timer - exactly like the real Assembler).
 *
 * This version accepts genuine Thermal Expansion schematics (made with a normal
 * TE Assembler / Schematic Table) and, unlike the original (1 schematic slot,
 * one job at a time), has 6 schematic slots that are all evaluated - and can all
 * craft - every single tick, so several schematics run truly in parallel.
 */
public class TileImprovedAssembler extends TileEntity implements ISidedInventory, IEnergyReceiver, IRedstoneControl, IEnergyInfo, IFluidHandler {

    public static final int SCHEMATIC_SLOTS = 6;
    public static final int INPUT_SLOTS = 18;
    public static final int OUTPUT_SLOTS = 6;
    /** Same 9 slots as the Advanced Pulverizer/Furnace (real TE's own TileAugmentable caps at 3-6; this mod's machines all get 9, fixed, since none has a separate tier/upgrade item). */
    public static final int AUGMENT_SLOTS = 9;
    /** See TileAdvancedPulverizer#CHARGE_SLOTS - same "drain an RF-storing item into the buffer" slot real TE gives every powered machine. */
    public static final int CHARGE_SLOTS = 1;
    public static final int TOTAL_SLOTS = SCHEMATIC_SLOTS + INPUT_SLOTS + OUTPUT_SLOTS + AUGMENT_SLOTS + CHARGE_SLOTS;

    public static final int SCHEMATIC_START = 0;
    public static final int INPUT_START = SCHEMATIC_SLOTS;
    public static final int OUTPUT_START = SCHEMATIC_SLOTS + INPUT_SLOTS;
    public static final int AUGMENT_START = SCHEMATIC_SLOTS + INPUT_SLOTS + OUTPUT_SLOTS;
    public static final int CHARGE_SLOT = AUGMENT_START + AUGMENT_SLOTS;

    /** Same flat per-craft cost as the real Thermal Expansion Assembler (TileAssembler.PROCESS_ENERGY). */
    public static final int PROCESS_ENERGY = 20;

    /**
     * Same "UltimateResonant" power tier as the Advanced Pulverizer (see
     * {@link net.thermaladd.mod.tileentity.TileAdvancedPulverizer#TIER_NAME}) - both machines
     * in this mod share one power-tier identity rather than each having its own scale.
     */
    public static final String TIER_NAME = "UltimateResonant";
    /** Was 64,000 RF - bumped to match the mod's UltimateResonant tier. */
    public static final int ENERGY_CAPACITY = 500000;
    /** Was 800 RF/t - bumped to match the mod's UltimateResonant tier (6 slots at 20 RF/craft each need only 120 RF/t at most, so this is pure headroom, not a starvation fix like the Pulverizer's). */
    public static final int ENERGY_RECEIVE_PER_TICK = 5000;

    /**
     * Same fluid tank real Thermal Expansion's own Assembler has (decompiled
     * {@code TileAssembler}: {@code new FluidTankAdv(10000)}), bumped 10x past the stock
     * machine's own capacity per instruction. Used exactly like real TE does: a schematic cell
     * that calls for a filled container item (a bucket of water, etc.) can be satisfied by
     * draining the matching fluid from this tank instead, without needing the actual bucket in
     * the material buffer - see {@link #matchGrid}.
     */
    public static final int TANK_CAPACITY = 100000;
    private final FluidTank tank = new FluidTank(TANK_CAPACITY);
    /** matchGrid()'s usedSlot[] sentinel marking a cell satisfied by draining the tank rather than a material buffer slot. */
    private static final int FLUID_CELL = -2;

    // -------------------------------------------------- augments (TE-compatible)

    /** Matches cofh.thermalexpansion.item.TEAugments.GENERAL_AUTO_INPUT. */
    public static final String AUG_AUTO_INPUT = "generalAutoInput";
    /** Matches cofh.thermalexpansion.item.TEAugments.GENERAL_AUTO_OUTPUT. */
    public static final String AUG_AUTO_OUTPUT = "generalAutoOutput";
    /** Matches cofh.thermalexpansion.item.TEAugments.GENERAL_RECONFIG_SIDES. */
    public static final String AUG_RECONFIG_SIDES = "generalReconfigSides";
    /** Matches cofh.thermalexpansion.item.TEAugments.GENERAL_REDSTONE_CONTROL. */
    public static final String AUG_REDSTONE_CONTROL = "generalRedstoneControl";

    /**
     * Side config modes, verified against real Thermal Expansion's own Cyclic Assembler
     * (decompiled {@code cofh.thermalexpansion.block.machine.TileAssembler#initialize}: 6
     * modes - {@code sideTex = {0,1,4,5,6,7}} indexing real TE's own {@code Config_None/Blue/
     * Orange/Green/Purple/Open} badge textures). Mode 0 ("no badge, plain casing") is TE's
     * actual DISABLED state, forced onto the front face permanently, not an "accept everything"
     * default. Real TE's material buffer (18 slots, same 9x2 layout this mod's own buffer
     * uses) can be fed as a whole (Input, Blue) or split into its two rows individually - Row 1
     * (Green) and Row 2 (Purple) - for wiring different item pipelines to each half; Output
     * (Orange) is extraction-only. Unlike the Pulverizer/Furnace, real TE's Assembler Input
     * modes (plain Input, Row 1, Row 2) never allow extraction back out, only insertion -
     * verified via {@code allowExtractionSide = {false,false,true,false,false,true}}.
     */
    public static final int SIDE_MODE_DISABLED = 0;
    public static final int SIDE_MODE_INPUT = 1;
    public static final int SIDE_MODE_OUTPUT = 2;
    public static final int SIDE_MODE_INPUT_ROW1 = 3;
    public static final int SIDE_MODE_INPUT_ROW2 = 4;
    public static final int SIDE_MODE_ALL = 5;
    public static final int SIDE_MODE_COUNT = 6;


    /** The material buffer is a 9x2 grid; Row 1/Row 2 side modes address one half each. */
    private static final int BUFFER_ROW_SIZE = 9;

    private static final int AUTO_IO_INTERVAL = 8;

    public boolean augmentAutoInput = false;
    public boolean augmentAutoOutput = false;
    public boolean augmentReconfigSides = false;
    public boolean augmentRedstoneControl = false;
    /** Matches real Thermal Expansion's own default - a freshly installed augment starts on "Low" (paused while powered). */
    private ControlMode rsMode = ControlMode.LOW;
    private boolean rsPowered = false;

    private byte[] sideCache = new byte[6];
    private int autoIOTimer = 0;

    private static final Container DUMMY_CONTAINER = new Container() {
        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }
    };

    private ItemStack[] inventory = new ItemStack[TOTAL_SLOTS];
    private int energyStored = 0;
    /** RF actually spent on the tick just finished (PROCESS_ENERGY per schematic that crafted) - "Energy Consumption" in the GUI. */
    private int energyPerTick = 0;

    // ---------------------------------------------------------------- energy

    @Override
    public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
        int energyReceived = Math.min(ENERGY_CAPACITY - energyStored, Math.min(ENERGY_RECEIVE_PER_TICK, maxReceive));
        if (!simulate && energyReceived > 0) {
            energyStored += energyReceived;
            markDirty();
        }
        return energyReceived;
    }

    @Override
    public int getEnergyStored(ForgeDirection from) {
        return energyStored;
    }

    @Override
    public int getMaxEnergyStored(ForgeDirection from) {
        return ENERGY_CAPACITY;
    }

    @Override
    public boolean canConnectEnergy(ForgeDirection from) {
        return true;
    }

    public int getEnergy() {
        return energyStored;
    }

    /**
     * Unlike the Pulverizer/Furnace, this block's facing was never given its own tile field -
     * it's read straight from block metadata, which BlockImprovedAssembler already keeps in
     * sync via the normal block-update packet. Exposed here so TabConfigAssembler can compute
     * Left/Right/Back the same way the other two machines' tabs do.
     */
    public int getFacing() {
        return worldObj != null ? worldObj.getBlockMetadata(xCoord, yCoord, zCoord) : 3;
    }

    public void setEnergyStoredClient(int scaledByFour) {
        this.energyStored = scaledByFour * 4;
    }

    /** RF actually drawn on the last tick that ran server-side - what real TE's own "Energy Consumption" line shows. */
    public int getEnergyPerTick() {
        return energyPerTick;
    }

    /** RF/t this machine would draw if all 6 schematic slots crafted on the same tick. */
    public int getMaxEnergyPerTick() {
        return SCHEMATIC_SLOTS * PROCESS_ENERGY;
    }

    public void setEnergyPerTickClient(int value) {
        energyPerTick = value;
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
        return ENERGY_CAPACITY;
    }

    // ---------------------------------------------------------------- fluid tank (real TE-compatible)

    /**
     * Same side-config gate the material buffer's own insert/extract checks use
     * (modeInsertsRow1/modeInsertsRow2/modeExtractsOutput) - real TE's own {@code fill}/
     * {@code drain} check the identical {@code sideConfig.allowInsertionSide}/
     * {@code allowExtractionSide} arrays the item slots use, not a separate fluid-only
     * permission set, so a side configured for material input accepts fluid too.
     */
    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        if (from != ForgeDirection.UNKNOWN && !(modeInsertsRow1(sideCache[from.ordinal()]) || modeInsertsRow2(sideCache[from.ordinal()]))) {
            return 0;
        }
        return tank.fill(resource, doFill);
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        if (from != ForgeDirection.UNKNOWN && !modeExtractsOutput(sideCache[from.ordinal()])) {
            return null;
        }
        if (resource == null || !resource.isFluidEqual(tank.getFluid())) {
            return null;
        }
        return tank.drain(resource.amount, doDrain);
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        if (from != ForgeDirection.UNKNOWN && !modeExtractsOutput(sideCache[from.ordinal()])) {
            return null;
        }
        return tank.drain(maxDrain, doDrain);
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return true;
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return true;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        return new FluidTankInfo[] { tank.getInfo() };
    }

    public FluidStack getTankFluid() {
        return tank.getFluid();
    }

    public int getTankCapacity() {
        return TANK_CAPACITY;
    }

    // Client-side only: the fluid registry id and amount arrive as 2 separate windowProperty
    // updates (each only sent when it actually changes, possibly on different ticks - see
    // ContainerImprovedAssembler), so each setter caches its own half and re-derives the
    // FluidStack from both cached halves rather than assuming they arrive together.
    private int clientFluidId = -1;
    private int clientFluidAmount = 0;

    public void setTankFluidIdClient(int fluidId) {
        clientFluidId = fluidId;
        rebuildClientTank();
    }

    /** {@code amount} arrives pre-divided by 4 (see ContainerImprovedAssembler#detectAndSendChanges) to fit the windowProperty short. */
    public void setTankFluidAmountClient(int amount) {
        clientFluidAmount = amount * 4;
        rebuildClientTank();
    }

    private void rebuildClientTank() {
        if (clientFluidId < 0 || clientFluidAmount <= 0) {
            tank.setFluid(null);
            return;
        }
        Fluid fluid = FluidRegistry.getFluid(clientFluidId);
        tank.setFluid(fluid == null ? null : new FluidStack(fluid, clientFluidAmount));
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

        // Same 3-way control real TE uses - see TileAdvancedPulverizer#updateEntity for details.
        setPowered(worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord));
        boolean redstoneAllows = !augmentRedstoneControl || rsMode.isDisabled() || rsMode.isHigh() == isPowered();
        if (redstoneAllows) {
            for (int slot = 0; slot < SCHEMATIC_SLOTS; slot++) {
                if (energyStored < PROCESS_ENERGY) {
                    break;
                }
                if (tryCraft(slot)) {
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

        if (dirty) {
            markDirty();
        }
    }

    /** See TileAdvancedPulverizer#chargeFromItem - same real-TE {@code TilePowered#chargeEnergy} behavior, adapted to this tile's plain int energy field (it has no EnergyStorage object). */
    private boolean chargeFromItem() {
        ItemStack stack = inventory[CHARGE_SLOT];
        if (stack == null || !(stack.getItem() instanceof IEnergyContainerItem)) {
            return false;
        }
        int receive = Math.min(ENERGY_RECEIVE_PER_TICK, ENERGY_CAPACITY - energyStored);
        if (receive <= 0) {
            return false;
        }
        IEnergyContainerItem energyItem = (IEnergyContainerItem) stack.getItem();
        int extracted = energyItem.extractEnergy(stack, receive, false);
        if (extracted <= 0) {
            return false;
        }
        energyStored += extracted;
        if (stack.stackSize <= 0) {
            inventory[CHARGE_SLOT] = null;
        }
        return true;
    }

    // ------------------------------------------------------ augments & side config

    /**
     * Mirrors TileAugmentable#installAugments(): re-derive the auto-input/auto-output/
     * reconfigurable-sides flags from whatever real Thermal Expansion augment items are
     * currently sitting in the 3 augment slots.
     */
    public void installAugments() {
        boolean autoInput = false;
        boolean autoOutput = false;
        boolean reconfigSides = false;
        boolean redstoneControl = false;

        for (int i = 0; i < AUGMENT_SLOTS; i++) {
            ItemStack augment = inventory[AUGMENT_START + i];
            if (augment == null || !(augment.getItem() instanceof IAugmentItem)) {
                continue;
            }
            IAugmentItem item = (IAugmentItem) augment.getItem();
            Set<String> types = item.getAugmentTypes(augment);
            if (types == null) {
                continue;
            }
            if (types.contains(AUG_AUTO_INPUT) && item.getAugmentLevel(augment, AUG_AUTO_INPUT) > 0) {
                autoInput = true;
            }
            if (types.contains(AUG_AUTO_OUTPUT) && item.getAugmentLevel(augment, AUG_AUTO_OUTPUT) > 0) {
                autoOutput = true;
            }
            if (types.contains(AUG_RECONFIG_SIDES) && item.getAugmentLevel(augment, AUG_RECONFIG_SIDES) > 0) {
                reconfigSides = true;
            }
            if (types.contains(AUG_REDSTONE_CONTROL) && item.getAugmentLevel(augment, AUG_REDSTONE_CONTROL) > 0) {
                redstoneControl = true;
            }
        }

        if (augmentReconfigSides && !reconfigSides) {
            // the augment that unlocked side reconfiguration was removed - lock back to defaults
            setDefaultSides();
        }
        if (!redstoneControl) {
            rsMode = ControlMode.DISABLED;
        }

        augmentAutoInput = autoInput;
        augmentAutoOutput = autoOutput;
        augmentReconfigSides = reconfigSides;
        augmentRedstoneControl = redstoneControl;
        markDirty();
    }

    public static boolean isValidAugment(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof IAugmentItem)) {
            return false;
        }
        IAugmentItem item = (IAugmentItem) stack.getItem();
        Set<String> types = item.getAugmentTypes(stack);
        return types != null
                && (types.contains(AUG_AUTO_INPUT) || types.contains(AUG_AUTO_OUTPUT)
                        || types.contains(AUG_RECONFIG_SIDES) || types.contains(AUG_REDSTONE_CONTROL));
    }

    /** See TileAdvancedPulverizer#hasDuplicateAugmentType for why this exists - this mod refuses a second augment of the same type outright rather than silently ignoring it like real TE does. */
    public boolean hasDuplicateAugmentType(ItemStack candidate, int excludeSlot) {
        if (candidate == null || !(candidate.getItem() instanceof IAugmentItem)) {
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
            if (other == null || !(other.getItem() instanceof IAugmentItem)) {
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

    /**
     * Mirrors real Thermal Expansion's own default-augment behavior (see
     * {@link net.thermaladd.mod.tileentity.TileAdvancedPulverizer#installDefaultAugments()}
     * for the decompiled source) - a freshly placed machine already has Auto Output,
     * Redstone Control and Reconfigurable Sides installed, filling all 3 of this machine's
     * augment slots. Only called once, from {@code onBlockPlacedBy}.
     */
    public void installDefaultAugments() {
        setInventorySlotContents(AUGMENT_START, TEAugments.generalAutoOutput.copy());
        setInventorySlotContents(AUGMENT_START + 1, TEAugments.generalRedstoneControl.copy());
        setInventorySlotContents(AUGMENT_START + 2, TEAugments.generalReconfigSides.copy());
    }

    /**
     * Serializes the 3 augment slots (relative index 0-2) so they can travel inside the dropped
     * block item's NBT - see {@code BlockImprovedAssembler#breakBlock/getDrops}. See
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

    public int getSideMode(int side) {
        return sideCache[side];
    }

    /**
     * Server-side: cycles a side's mode forward (direction 1) or backward (-1), gated by the
     * augment. Also refuses to touch the front face, matching real TE's
     * TileReconfigurable#incrSide/decrSide.
     */
    public boolean cycleSideMode(int side, int direction) {
        if (!augmentReconfigSides || side == getFacing()) {
            return false;
        }
        sideCache[side] = (byte) (((sideCache[side] + direction) % SIDE_MODE_COUNT + SIDE_MODE_COUNT) % SIDE_MODE_COUNT);
        markDirty();
        syncRenderState();
        return true;
    }

    public boolean resetSideMode(int side) {
        if (!augmentReconfigSides || side == getFacing()) {
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

    /**
     * The connection badge on a face depends on sideCache, which isn't stored in block
     * metadata, so vanilla's block-change networking never reaches it - World#markBlockForUpdate
     * called here (server-side) would be a no-op, since it only affects whichever World
     * instance it's called on, never the client's. This explicitly pushes the new state to
     * every nearby client, which then repaints the block from its OWN World - see
     * MessageTileRenderSyncHandler. (Facing itself is real block metadata already, so it's
     * sent as 0 here and simply ignored on the receiving end for this tile type.)
     */
    private void syncRenderState() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        PacketHandler.INSTANCE.sendToAllAround(new MessageTileRenderSync(xCoord, yCoord, zCoord, (byte) 0, sideCache),
                new NetworkRegistry.TargetPoint(worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 64.0));
    }

    /** Client-side only: apply a side mode received from the server without the augment gate. */
    public void setSideModeClient(int side, int mode) {
        sideCache[side] = (byte) mode;
    }

    /** Whole-buffer Input, Row 1 alone, or All all insert into the buffer's first 9 slots. */
    private static boolean modeInsertsRow1(int mode) {
        return mode == SIDE_MODE_INPUT || mode == SIDE_MODE_INPUT_ROW1 || mode == SIDE_MODE_ALL;
    }

    /** Whole-buffer Input, Row 2 alone, or All all insert into the buffer's second 9 slots. */
    private static boolean modeInsertsRow2(int mode) {
        return mode == SIDE_MODE_INPUT || mode == SIDE_MODE_INPUT_ROW2 || mode == SIDE_MODE_ALL;
    }

    /**
     * Real TE quirk, verified against the decompiled {@code TileAssembler}
     * ({@code allowExtractionSide = {false,false,true,false,false,true}}): unlike the
     * Pulverizer/Furnace, none of this machine's Input-flavored modes ever allow extraction -
     * only Output and All do.
     */
    private static boolean modeExtractsOutput(int mode) {
        return mode == SIDE_MODE_OUTPUT || mode == SIDE_MODE_ALL;
    }

    /**
     * Live per-role slot-highlight queries for the GUI (see GuiImprovedAssembler) - see
     * TileAdvancedPulverizer#isAnyInputSide for why these scan sideCache live instead of
     * reading any fixed default. isAnyInputSide() covers the schematic slots (shown blue
     * whenever ANY input-flavored mode is set anywhere); the row-specific ones drive the
     * matching half of the material buffer only.
     */
    public boolean isAnyInputSide() {
        for (int side = 0; side < 6; side++) {
            int mode = sideCache[side];
            if (modeInsertsRow1(mode) || modeInsertsRow2(mode)) {
                return true;
            }
        }
        return false;
    }

    public boolean isAnyInputRow1Side() {
        for (int side = 0; side < 6; side++) {
            if (modeInsertsRow1(sideCache[side])) {
                return true;
            }
        }
        return false;
    }

    public boolean isAnyInputRow2Side() {
        for (int side = 0; side < 6; side++) {
            if (modeInsertsRow2(sideCache[side])) {
                return true;
            }
        }
        return false;
    }

    public boolean isAnyOutputSide() {
        for (int side = 0; side < 6; side++) {
            if (modeExtractsOutput(sideCache[side])) {
                return true;
            }
        }
        return false;
    }

    private boolean autoPullInputs() {
        boolean moved = false;
        for (int side = 0; side < 6; side++) {
            int mode = sideCache[side];
            boolean row1 = modeInsertsRow1(mode);
            boolean row2 = modeInsertsRow2(mode);
            if ((row1 || row2) && pullFromSide(ForgeDirection.getOrientation(side), row1, row2)) {
                moved = true;
            }
        }
        return moved;
    }

    private boolean autoPushOutputs() {
        boolean moved = false;
        for (int side = 0; side < 6; side++) {
            if (modeExtractsOutput(sideCache[side]) && pushToSide(ForgeDirection.getOrientation(side))) {
                moved = true;
            }
        }
        return moved;
    }

    /** Pulls a single item from the neighboring inventory on {@code dir} into the material buffer - only into row 1 and/or row 2 as permitted by the side's own mode. */
    private boolean pullFromSide(ForgeDirection dir, boolean row1, boolean row2) {
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
            if (candidate == null) {
                continue;
            }
            if (neighborInv instanceof ISidedInventory
                    && !((ISidedInventory) neighborInv).canExtractItem(slotIdx, candidate, fromSide.ordinal())) {
                continue;
            }
            int bufferSlot = findBufferSlotFor(candidate, row1, row2);
            if (bufferSlot < 0) {
                continue;
            }
            if (inventory[INPUT_START + bufferSlot] == null) {
                ItemStack moved = candidate.copy();
                moved.stackSize = 1;
                inventory[INPUT_START + bufferSlot] = moved;
            } else {
                inventory[INPUT_START + bufferSlot].stackSize++;
            }
            neighborInv.decrStackSize(slotIdx, 1);
            neighborInv.markDirty();
            return true;
        }
        return false;
    }

    /** Pushes a single item from the output slots into the neighboring inventory on {@code dir}. */
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

    private int findBufferSlotFor(ItemStack stack, boolean row1, boolean row2) {
        int emptySlot = -1;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (i < BUFFER_ROW_SIZE ? !row1 : !row2) {
                continue;
            }
            ItemStack buf = inventory[INPUT_START + i];
            if (buf == null) {
                if (emptySlot < 0) {
                    emptySlot = i;
                }
                continue;
            }
            if (buf.getItem() == stack.getItem() && buf.getItemDamage() == stack.getItemDamage()
                    && ItemStack.areItemStackTagsEqual(buf, stack) && buf.stackSize < buf.getMaxStackSize()) {
                return i;
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

            if (inv instanceof ISidedInventory
                    && !((ISidedInventory) inv).canInsertItem(slotIdx, single, side.ordinal())) {
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

    /**
     * Mirrors TileAssembler#updateOutput()/#createItem(): rebuild the 3x3 grid for
     * this schematic slot from the shared material buffer, look up the real recipe
     * for that grid, and if everything (materials, output space, energy) lines up,
     * craft one item and pay the flat RF cost.
     */
    private boolean tryCraft(int schematicSlot) {
        ItemStack schematic = inventory[SCHEMATIC_START + schematicSlot];
        if (schematic == null) {
            return false;
        }

        InventoryCrafting grid = new InventoryCrafting(DUMMY_CONTAINER, 3, 3);
        int[] usedBufferSlot = matchGrid(schematic, grid);
        if (usedBufferSlot == null) {
            return false;
        }

        ItemStack result = findMatchingRecipe(grid);
        if (result == null) {
            return false;
        }

        int outSlot = OUTPUT_START + schematicSlot;
        if (!canFitOutput(outSlot, result)) {
            return false;
        }

        for (int cell = 0; cell < 9; cell++) {
            int bufferIndex = usedBufferSlot[cell];
            if (bufferIndex == FLUID_CELL) {
                // Re-derive from the schematic's own recorded ingredient rather than trusting
                // any state carried over from matchGrid() - deterministic either way, since
                // nothing else touches the schematic or the tank between the two calls.
                FluidStack needed = FluidContainerRegistry.getFluidForFilledItem(getSchematicSlot(schematic, cell));
                if (needed != null) {
                    tank.drain(needed.amount, true);
                }
                continue;
            }
            if (bufferIndex < 0) {
                continue;
            }
            ItemStack buf = inventory[INPUT_START + bufferIndex];
            buf.stackSize--;
            if (buf.stackSize <= 0) {
                inventory[INPUT_START + bufferIndex] = null;
            }
        }

        if (inventory[outSlot] == null) {
            inventory[outSlot] = result.copy();
        } else {
            inventory[outSlot].stackSize += result.stackSize;
        }

        energyStored -= PROCESS_ENERGY;
        energyPerTick += PROCESS_ENERGY;
        return true;
    }

    /**
     * Fills {@code grid} from the material buffer according to the schematic's
     * recorded pattern. Returns, per crafting-grid cell, which buffer slot the
     * item was taken from (-1 for a cell the schematic leaves empty), or null if
     * the buffer can't satisfy the pattern right now.
     */
    private int[] matchGrid(ItemStack schematic, InventoryCrafting grid) {
        int[] usedSlot = new int[9];
        int[] remaining = new int[INPUT_SLOTS];
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack buf = inventory[INPUT_START + i];
            remaining[i] = buf != null ? buf.stackSize : 0;
        }
        // Mutable running total, same idea as remaining[] above but for the tank - a schematic
        // needing 2 buckets of the same fluid across 2 different cells has to actually have
        // 2x the fluid available, not just enough for one.
        FluidStack tankFluid = tank.getFluid();
        int fluidRemaining = tankFluid != null ? tankFluid.amount : 0;

        boolean anyRequired = false;
        for (int cell = 0; cell < 9; cell++) {
            ItemStack sample = getSchematicSlot(schematic, cell);
            if (sample == null) {
                usedSlot[cell] = -1;
                grid.setInventorySlotContents(cell, null);
                continue;
            }
            anyRequired = true;

            // Mirrors real TE's TileAssembler#createItem: a schematic cell asking for a filled
            // container item (a bucket of water, etc.) can be satisfied by draining the
            // equivalent fluid from the tank instead of needing that actual item in the buffer.
            FluidStack neededFluid = FluidContainerRegistry.getFluidForFilledItem(sample);
            if (neededFluid != null && tankFluid != null && tankFluid.isFluidEqual(neededFluid) && fluidRemaining >= neededFluid.amount) {
                fluidRemaining -= neededFluid.amount;
                usedSlot[cell] = FLUID_CELL;
                grid.setInventorySlotContents(cell, sample.copy());
                continue;
            }

            String ore = getSchematicOreSlot(schematic, cell);

            int found = -1;
            for (int i = 0; i < INPUT_SLOTS; i++) {
                if (remaining[i] <= 0) {
                    continue;
                }
                if (matchesRequirement(inventory[INPUT_START + i], sample, ore)) {
                    found = i;
                    break;
                }
            }
            if (found == -1) {
                return null;
            }
            remaining[found]--;
            usedSlot[cell] = found;
            ItemStack piece = inventory[INPUT_START + found].copy();
            piece.stackSize = 1;
            grid.setInventorySlotContents(cell, piece);
        }

        return anyRequired ? usedSlot : null;
    }

    private boolean matchesRequirement(ItemStack buf, ItemStack sample, String oreName) {
        if (buf == null) {
            return false;
        }
        if (oreName != null) {
            int[] ids = OreDictionary.getOreIDs(buf);
            for (int id : ids) {
                if (OreDictionary.getOreName(id).equals(oreName)) {
                    return true;
                }
            }
            return false;
        }
        return buf.getItem() == sample.getItem()
                && (sample.getItemDamage() == Short.MAX_VALUE || buf.getItemDamage() == sample.getItemDamage());
    }

    @SuppressWarnings("unchecked")
    private ItemStack findMatchingRecipe(InventoryCrafting grid) {
        List<IRecipe> recipes = (List<IRecipe>) CraftingManager.getInstance().getRecipeList();
        for (IRecipe recipe : recipes) {
            if (recipe.matches(grid, worldObj)) {
                return recipe.getCraftingResult(grid);
            }
        }
        return null;
    }

    private boolean canFitOutput(int slot, ItemStack result) {
        if (result == null) {
            return false;
        }
        ItemStack existing = inventory[slot];
        if (existing == null) {
            return true;
        }
        return existing.getItem() == result.getItem()
                && existing.getItemDamage() == result.getItemDamage()
                && existing.stackSize + result.stackSize <= existing.getMaxStackSize();
    }

    // -------------------------------------------------- schematic NBT (TE-compatible)

    /**
     * Same NBT layout Thermal Expansion's SchematicHelper writes/reads: each
     * populated crafting-grid cell is stored as "Slot0".."Slot8" (a full
     * ItemStack tag) plus an optional "Ore0".."Ore8" OreDictionary name.
     */
    private ItemStack getSchematicSlot(ItemStack schematic, int cell) {
        if (schematic == null || schematic.stackTagCompound == null) {
            return null;
        }
        String key = "Slot" + cell;
        if (!schematic.stackTagCompound.hasKey(key)) {
            return null;
        }
        return ItemStack.loadItemStackFromNBT(schematic.stackTagCompound.getCompoundTag(key));
    }

    private String getSchematicOreSlot(ItemStack schematic, int cell) {
        if (schematic == null || schematic.stackTagCompound == null) {
            return null;
        }
        String key = "Ore" + cell;
        if (!schematic.stackTagCompound.hasKey(key)) {
            return null;
        }
        return schematic.stackTagCompound.getString(key);
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
        return "container.improvedAssembler";
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
        return slot < OUTPUT_START;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        int mode = sideCache[side];
        boolean row1 = modeInsertsRow1(mode);
        boolean row2 = modeInsertsRow2(mode);
        boolean output = modeExtractsOutput(mode);

        int[] slots = new int[(row1 ? BUFFER_ROW_SIZE : 0) + (row2 ? BUFFER_ROW_SIZE : 0) + (output ? OUTPUT_SLOTS : 0)];
        int idx = 0;
        if (row1) {
            for (int i = 0; i < BUFFER_ROW_SIZE; i++) {
                slots[idx++] = INPUT_START + i;
            }
        }
        if (row2) {
            for (int i = BUFFER_ROW_SIZE; i < INPUT_SLOTS; i++) {
                slots[idx++] = INPUT_START + i;
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
        if (slot < INPUT_START || slot >= OUTPUT_START) {
            return false;
        }
        int mode = sideCache[side];
        int rel = slot - INPUT_START;
        return rel < BUFFER_ROW_SIZE ? modeInsertsRow1(mode) : modeInsertsRow2(mode);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        return slot >= OUTPUT_START && slot < AUGMENT_START && modeExtractsOutput(sideCache[side]);
    }

    @Override
    public void markDirty() {
        // NOT worldObj.markBlockForUpdate() here: that forces a full chunk render-mesh
        // rebuild plus a block-change network packet, and this is called up to 20 times a
        // second (every energy transfer, every craft tick, every side-mode change...) - the
        // block's actual render (texture/metadata) never depends on any of that. Plain
        // TileEntity#markDirty() just flags the chunk for saving, which is all this needs;
        // clients already get inventory/energy/side updates for free via the open Container
        // (Slot diffing + ContainerImprovedAssembler#detectAndSendChanges()).
        super.markDirty();
    }

    // ---------------------------------------------------------------- nbt

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("Energy", energyStored);
        tag.setByteArray("Sides", sideCache);
        tag.setByte("RSControl", (byte) rsMode.ordinal());
        NBTTagCompound tankTag = new NBTTagCompound();
        tank.writeToNBT(tankTag);
        tag.setTag("Tank", tankTag);

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
        energyStored = tag.getInteger("Energy");
        if (tag.hasKey("RSControl")) {
            rsMode = ControlMode.values()[tag.getByte("RSControl") & 0xFF];
        }

        if (tag.hasKey("Sides")) {
            byte[] sides = tag.getByteArray("Sides");
            if (sides.length == sideCache.length) {
                sideCache = sides;
            }
        }
        if (tag.hasKey("Tank")) {
            tank.readFromNBT(tag.getCompoundTag("Tank"));
        }

        NBTTagList items = tag.getTagList("Items", 10);
        inventory = new ItemStack[TOTAL_SLOTS];
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound itemTag = items.getCompoundTagAt(i);
            int slot = itemTag.getByte("Slot") & 255;
            if (slot >= 0 && slot < inventory.length) {
                inventory[slot] = ItemStack.loadItemStackFromNBT(itemTag);
            }
        }

        installAugments();
    }

    /**
     * See TileAdvancedPulverizer#getDescriptionPacket for why this exists. This tile's own
     * facing already rides on real block metadata (always sent with chunk data), but sideCache/
     * augments/energy/redstone mode are all tile-only fields that would otherwise sit at their
     * construction defaults on a freshly loaded client until something else resynced them.
     */
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
