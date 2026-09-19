package net.thermaladd.mod.tileentity;

import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import cofh.api.energy.EnergyStorage;
import cofh.api.energy.IEnergyReceiver;
import cofh.api.item.IAugmentItem;
import cofh.api.tileentity.IRedstoneControl;
import cofh.thermalexpansion.item.TEAugments;
import cofh.thermalexpansion.util.crafting.FurnaceManager;
import cofh.thermalexpansion.util.crafting.FurnaceManager.RecipeFurnace;
import cpw.mods.fml.common.network.NetworkRegistry;
import net.thermaladd.mod.network.MessageTileRenderSync;
import net.thermaladd.mod.network.PacketHandler;

/**
 * Advanced Furnace tile entity - the third "improved TE machine" in this mod, same overall
 * design as {@link TileAdvancedPulverizer}: 3 independent input slots instead of the real
 * Furnace's 1, each running its own recipe lookup/energy accumulation/craft cycle in
 * parallel every tick, sharing one RF buffer, one output pair and 9 augment slots. Same
 * "UltimateResonant" power tier as the other two machines.
 *
 * Simpler than the Pulverizer in one respect: {@link FurnaceManager} recipes have no
 * secondary product, so there is no secondary output slot and no Secondary Output / Null
 * augment to support - those augment types are simply not recognized as valid here (they
 * would have zero effect on this machine).
 */
public class TileAdvancedFurnace extends TileEntity implements ISidedInventory, IEnergyReceiver, IRedstoneControl {

    public static final int INPUT_SLOTS = 3;
    public static final int OUTPUT_SLOTS = 2;
    public static final int AUGMENT_SLOTS = 9;
    public static final int TOTAL_SLOTS = INPUT_SLOTS + OUTPUT_SLOTS + AUGMENT_SLOTS;

    public static final int INPUT_START = 0;
    public static final int OUTPUT_START = INPUT_SLOTS;
    public static final int AUGMENT_START = INPUT_SLOTS + OUTPUT_SLOTS;

    /** Same "UltimateResonant" tier and per-line processing rate as the Advanced Pulverizer. */
    public static final String TIER_NAME = "UltimateResonant";
    public static final int BASE_ENERGY_PER_TICK = 80;
    public static final int BASE_ENERGY_CAPACITY = 1000000;
    public static final int ENERGY_RECEIVE_PER_TICK = 10000;

    public static final int SIDE_MODE_AUTO = 0;
    public static final int SIDE_MODE_INPUT = 1;
    public static final int SIDE_MODE_OUTPUT = 2;
    public static final int SIDE_MODE_DISABLED = 3;
    public static final int SIDE_MODE_COUNT = 4;

    public static final int[] FACING_META = {2, 5, 3, 4};

    public static final String AUG_GENERAL_AUTO_OUTPUT = "generalAutoOutput";
    public static final String AUG_GENERAL_AUTO_INPUT = "generalAutoInput";
    public static final String AUG_GENERAL_RECONFIG_SIDES = "generalReconfigSides";
    public static final String AUG_GENERAL_REDSTONE_CONTROL = "generalRedstoneControl";
    public static final String AUG_MACHINE_SPEED = "machineSpeed";
    public static final String AUG_ENERGY_STORAGE = "energyStorage";

    private static final int[] MACHINE_SPEED_PROCESS_MOD = {1, 2, 4, 8};
    private static final int[] MACHINE_SPEED_ENERGY_MOD = {1, 3, 8, 20};
    private static final int[] ENERGY_STORAGE_MOD = {1, 2, 4, 8};
    private static final int MAX_AUGMENT_LEVEL = 3;

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

    /** Refuses to touch the front face, matching real TE's TileReconfigurable#incrSide/decrSide. */
    public boolean cycleSideMode(int side, int direction) {
        if (!augmentReconfigSides || side == facing) {
            return false;
        }
        sideCache[side] = (byte) (((sideCache[side] + direction) % SIDE_MODE_COUNT + SIDE_MODE_COUNT) % SIDE_MODE_COUNT);
        markDirty();
        syncRenderState();
        return true;
    }

    public boolean resetSideMode(int side) {
        if (!augmentReconfigSides || side == facing) {
            return false;
        }
        sideCache[side] = SIDE_MODE_AUTO;
        markDirty();
        syncRenderState();
        return true;
    }

    public boolean resetAllSideModes() {
        if (!augmentReconfigSides) {
            return false;
        }
        for (int i = 0; i < sideCache.length; i++) {
            sideCache[i] = SIDE_MODE_AUTO;
        }
        markDirty();
        syncRenderState();
        return true;
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

    private boolean sideAllowsInput(int side) {
        int mode = sideCache[side];
        return mode == SIDE_MODE_AUTO || mode == SIDE_MODE_INPUT;
    }

    private boolean sideAllowsOutput(int side) {
        int mode = sideCache[side];
        return mode == SIDE_MODE_AUTO || mode == SIDE_MODE_OUTPUT;
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
            speedLevel = Math.max(speedLevel, clampLevel(item.getAugmentLevel(augment, AUG_MACHINE_SPEED)));
            energyLevel = Math.max(energyLevel, clampLevel(item.getAugmentLevel(augment, AUG_ENERGY_STORAGE)));
        }

        if (augmentReconfigSides && !reconfigSides) {
            for (int i = 0; i < sideCache.length; i++) {
                sideCache[i] = SIDE_MODE_AUTO;
            }
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
        if (level < 0) {
            return 0;
        }
        return Math.min(level, MAX_AUGMENT_LEVEL);
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

    public void setEnergyStoredClient(int scaledByFour) {
        energyStorage.setEnergyStored(scaledByFour * 4);
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

    public void setEnergyPerTickClient(int value) {
        energyPerTick = value;
    }

    public void setMaxEnergyPerTickClient(int value) {
        maxEnergyPerTick = value;
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
        if (!canFitStack(OUTPUT_START, output) && !canFitStack(OUTPUT_START + 1, output)) {
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
            if (sideAllowsInput(side) && pullFromSide(ForgeDirection.getOrientation(side))) {
                moved = true;
            }
        }
        return moved;
    }

    private boolean autoPushOutputs() {
        boolean moved = false;
        for (int side = 0; side < 6; side++) {
            if (sideAllowsOutput(side) && pushToSide(ForgeDirection.getOrientation(side))) {
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
        if (isAugmentSlot(slot)) {
            return isValidAugment(stack);
        }
        if (slot < OUTPUT_START) {
            return FurnaceManager.recipeExists(stack);
        }
        return false;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        boolean in = sideAllowsInput(side);
        boolean out = sideAllowsOutput(side);
        int[] slots = new int[(in ? INPUT_SLOTS : 0) + (out ? OUTPUT_SLOTS : 0)];
        int idx = 0;
        if (in) {
            int start = (int) ((worldObj != null ? worldObj.getTotalWorldTime() : 0) % INPUT_SLOTS);
            for (int i = 0; i < INPUT_SLOTS; i++) {
                slots[idx++] = INPUT_START + (start + i) % INPUT_SLOTS;
            }
        }
        if (out) {
            for (int i = 0; i < OUTPUT_SLOTS; i++) {
                slots[idx++] = OUTPUT_START + i;
            }
        }
        return slots;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return slot < OUTPUT_START && sideAllowsInput(side) && FurnaceManager.recipeExists(stack);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        return slot >= OUTPUT_START && slot < AUGMENT_START && sideAllowsOutput(side);
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
            if (sides.length == sideCache.length) {
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
}
