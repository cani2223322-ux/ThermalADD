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
import cofh.thermalexpansion.util.crafting.ChargerManager;
import cofh.thermalexpansion.util.crafting.ChargerManager.RecipeCharger;
import cpw.mods.fml.common.network.NetworkRegistry;
import net.thermaladd.mod.network.MessageTileRenderSync;
import net.thermaladd.mod.network.PacketHandler;

/**
 * Advanced Charger tile entity - the fifth "improved TE machine" in this mod, and a genuinely
 * "beyond spec" one rather than a straight multi-line port: real Thermal Expansion's own Charger
 * (decompiled {@code cofh.thermalexpansion.block.machine.TileCharger}) has exactly ONE working
 * slot that does double duty - insert an {@code IEnergyContainerItem} (a Capacitor, a charged
 * tool, ...) and it drains RF straight into it in place; insert anything else with a real
 * {@link ChargerManager} recipe and it instead runs that item->item RF-cost conversion (TE ships
 * exactly one default recipe for this: Certus Quartz Crystal -> Charged Certus Quartz Crystal,
 * though other mods/addons can register more via the same public API). This tile keeps that
 * exact same dual-mode-per-slot behavior (see {@link #tryProcess}), just across 9 independent
 * lines running in parallel instead of 1 - 9 items can be charged, or converted, or any mix of
 * both, all at once. Each line gets its own dedicated output slot (matching {@link
 * TileAdvancedFurnace}'s own one-output-per-line shape) - a charged item, once full, moves there
 * automatically exactly like real TE's own {@code transferOutput()} does, freeing its line slot
 * for the next item.
 *
 * Side config modes are numerically and color-wise identical to the Furnace's own (verified
 * against the decompiled {@code TileCharger#initialize}: {@code sideTex = {0,1,4,7}}, the same
 * Blue/Orange/Open Disabled/Input/Output/All shape) - real TE's own Charger happens to use the
 * exact same simple 4-mode side config the Furnace does, so this tile (and its GUI's badge
 * textures) reuses that vocabulary directly instead of inventing a new one.
 */
public class TileAdvancedCharger extends TileEntity implements ISidedInventory, IEnergyReceiver, IRedstoneControl, IEnergyInfo {

    public static final int LINE_SLOTS = 9;
    /** One output slot per line, matching TileAdvancedFurnace's own one-output-per-line shape. */
    public static final int OUTPUT_SLOTS = 9;
    public static final int AUGMENT_SLOTS = 9;
    /** See TileAdvancedPulverizer#CHARGE_SLOTS - the machine's OWN "fuel me from a Capacitor" slot, a different concept entirely from the 9 lines this tile charges OTHER items in. */
    public static final int CHARGE_SLOTS = 1;
    public static final int TOTAL_SLOTS = LINE_SLOTS + OUTPUT_SLOTS + AUGMENT_SLOTS + CHARGE_SLOTS;

    public static final int LINE_START = 0;
    public static final int OUTPUT_START = LINE_SLOTS;
    public static final int AUGMENT_START = LINE_SLOTS + OUTPUT_SLOTS;
    public static final int CHARGE_SLOT = AUGMENT_START + AUGMENT_SLOTS;

    /**
     * Real TE's own Charger defaults to 8000 RF/t (decompiled {@code TileCharger#initialize}'s
     * {@code BasePower} config default) - two orders of magnitude above the Pulverizer's own 40
     * RF/t, since meaningfully charging a battery item needs a lot more throughput than grinding
     * ore. Same "2x the real stock machine's own base rate" relationship the other 3 machines
     * keep to their own real-TE counterparts. Doubles as both the per-line RF/t cap when charging
     * an item in place AND the flat RF/t cost/progress-gain rate for a real ChargerManager
     * conversion recipe - real TE's own Charger uses this exact same rate for both purposes too
     * (its {@code calcEnergy()} drives both).
     */
    public static final int BASE_ENERGY_PER_TICK = 16000;

    /** Same "UltimateResonant" power tier identity as the other 4 machines - see {@link TileAdvancedPulverizer#TIER_NAME}. */
    public static final String TIER_NAME = "UltimateResonant";
    /** Larger than the other machines' own 1,000,000 - 9 lines charging at once can draw up to 9x BASE_ENERGY_PER_TICK, so the buffer needs real headroom to absorb bursts. */
    public static final int BASE_ENERGY_CAPACITY = 2000000;
    /** Larger than the other machines' own 10,000 for the same reason - sized to sustain all 9 lines charging flat-out at once (9 * 16,000 = 144,000 RF/t) with margin. */
    public static final int ENERGY_RECEIVE_PER_TICK = 200000;

    /**
     * See TileAdvancedPulverizer#ENERGY_SYNC_SCALE for the base windowProperty short-overflow
     * fix - but this tile needs a bigger divisor than the other 4 machines' own 256: this
     * machine's own capacity ceiling is already 2x theirs (2,000,000 * 8 = 16,000,000 with a
     * maxed Energy Storage augment, vs. their 8,000,000), and unlike their own small flat
     * recipe-RF progress values, THIS tile's own progress/progressMax (see {@link
     * #setProgressClient}) reuses this same scale for a charging line's item-charge progress -
     * a real Resonant Capacitor alone holds 4,000,000 RF (see {@code cofh.thermalexpansion.item.
     * ItemCapacitor.CAPACITY}). 1024 keeps even the 16,000,000 capacity ceiling safely under the
     * windowProperty channel's 32,767 short limit (16,000,000 / 1024 = 15,625) with real margin
     * to spare for a Capacitor's own progress value too.
     */
    public static final int ENERGY_SYNC_SCALE = 1024;

    /** Real TE has no ambient sound event for the Charger either (no {@code blockMachineCharger} entry in the vendored jar's own sounds.json) - it doesn't grind continuously, so there's nothing to loop, same reasoning as why the Assembler has none. */

    /**
     * Side config modes - see this class's own javadoc: numerically and color-wise identical to
     * {@link TileAdvancedFurnace}'s own (verified against the decompiled
     * {@code TileCharger#initialize}, same {@code sideTex = {0,1,4,7}} badge numbering).
     */
    public static final int SIDE_MODE_DISABLED = 0;
    public static final int SIDE_MODE_INPUT = 1;
    public static final int SIDE_MODE_OUTPUT = 2;
    public static final int SIDE_MODE_ALL = 3;
    public static final int SIDE_MODE_COUNT = 4;

    /** North/South/West/East facing metas, same convention vanilla furnaces use. */
    public static final int[] FACING_META = {2, 5, 3, 4};

    // -------------------------------------------------- augments (real TE augment items)

    public static final String AUG_GENERAL_AUTO_OUTPUT = "generalAutoOutput";
    public static final String AUG_GENERAL_AUTO_INPUT = "generalAutoInput";
    public static final String AUG_GENERAL_RECONFIG_SIDES = "generalReconfigSides";
    public static final String AUG_GENERAL_REDSTONE_CONTROL = "generalRedstoneControl";
    public static final String AUG_MACHINE_SPEED = "machineSpeed";
    public static final String AUG_ENERGY_STORAGE = "energyStorage";

    /** See TileAdvancedPulverizer's own copy of these tables for the level-4 rationale. No Machine Secondary/Null support - real TE's own Charger has no secondary-output concept for this augment to affect, same reasoning as the Furnace. */
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
    private ControlMode rsMode = ControlMode.LOW;
    private boolean rsPowered = false;

    private int speedProcessMod = 1;
    private int speedEnergyMod = 1;
    private int autoIOTimer = 0;

    private final ItemStack[] inventory = new ItemStack[TOTAL_SLOTS];
    private final EnergyStorage energyStorage = new EnergyStorage(BASE_ENERGY_CAPACITY, ENERGY_RECEIVE_PER_TICK);

    /** For a battery-charging line: the item's own current/max RF (so the GUI progress bar reads "how full", not "how far into a fixed recipe"). For a recipe-conversion line: RF already invested / the recipe's own flat cost - exactly like the other machines. */
    private final int[] progress = new int[LINE_SLOTS];
    private final int[] progressMax = new int[LINE_SLOTS];
    /**
     * Which {@link RecipeCharger} instance {@code progress[line]} was accumulated against for a
     * recipe-conversion line, or {@code null} while that line is empty/in charge-mode - see
     * {@link #tryProcess}. Without this, swapping a line's contents between a battery item and a
     * cheap recipe item (or between two different recipes) could carry a large leftover
     * {@code progress[line]} value straight past the new, unrelated recipe's own {@code
     * progressMax}, completing it for free the very next tick - real TE's own single-slot
     * Charger can't hit this since its own recipe conversion never shares a slot with a
     * different, independently-tracked progress semantic the way 9 dual-mode lines can.
     */
    private final RecipeCharger[] progressRecipe = new RecipeCharger[LINE_SLOTS];
    private int energyPerTick = 0;
    private int maxEnergyPerTick = LINE_SLOTS * BASE_ENERGY_PER_TICK;

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

    private static boolean isValidSide(int side) {
        return side >= 0 && side < 6;
    }

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

    public void setDefaultSides() {
        for (int i = 0; i < sideCache.length; i++) {
            sideCache[i] = SIDE_MODE_DISABLED;
        }
        markDirty();
        syncRenderState();
    }

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

    /** Client-side only: applied by MessageTileRenderSyncHandler so the idle/active face icon stays in sync even for a client who never opens this machine's GUI. Unlike the Pulverizer/Furnace/Sawmill, no sound is tied to this - real TE's own Charger has no ambient sound event either. */
    public void setActiveClient(boolean active) {
        isActive = active;
    }

    private static boolean modeAllowsInsertInput(int mode) {
        return mode == SIDE_MODE_INPUT || mode == SIDE_MODE_ALL;
    }

    private static boolean modeAllowsExtractInput(int mode) {
        return mode == SIDE_MODE_INPUT;
    }

    private static boolean modeAllowsExtractOutput(int mode) {
        return mode == SIDE_MODE_OUTPUT || mode == SIDE_MODE_ALL;
    }

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
        maxEnergyPerTick = LINE_SLOTS * BASE_ENERGY_PER_TICK * speedEnergyMod;

        energyStorage.setCapacity(BASE_ENERGY_CAPACITY * ENERGY_STORAGE_MOD[energyLevel]);
        energyStorage.setMaxTransfer(ENERGY_RECEIVE_PER_TICK * ENERGY_STORAGE_MOD[energyLevel]);
        markDirty();
    }

    public void installDefaultAugments() {
        setInventorySlotContents(AUGMENT_START, TEAugments.generalAutoOutput.copy());
        setInventorySlotContents(AUGMENT_START + 1, TEAugments.generalRedstoneControl.copy());
        setInventorySlotContents(AUGMENT_START + 2, TEAugments.generalReconfigSides.copy());
    }

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

    /** No Machine Secondary/Null support here - see this class's own field-table comment for why. */
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

    /**
     * Unlike the other 3 machines' own flat, small real-TE recipe RF costs, a charging line's
     * progress/progressMax can be as large as whatever RF-storing item is sitting in it - a real
     * Resonant Capacitor alone holds 4,000,000 RF (see {@code cofh.thermalexpansion.item.
     * ItemCapacitor.CAPACITY}), and even the cheapest real Capacitor tier (32,000) already
     * exceeds the windowProperty channel's 16-bit short limit (32,767) on its own. Scaled by the
     * same ENERGY_SYNC_SCALE the main energy bar already needs for the same reason - see
     * ContainerAdvancedCharger's own division on the way out.
     */
    public void setProgressClient(int line, int scaled) {
        progress[line] = scaled * ENERGY_SYNC_SCALE;
    }

    public void setProgressMaxClient(int line, int scaled) {
        progressMax[line] = scaled * ENERGY_SYNC_SCALE;
    }

    public int getEnergyPerTick() {
        return energyPerTick;
    }

    public int getMaxEnergyPerTick() {
        return maxEnergyPerTick;
    }

    /** See ContainerAdvancedCharger's own doc comment on why ids 30/31 need ENERGY_SYNC_SCALE too, unlike the other 3 machines' own unscaled equivalents. */
    public void setEnergyPerTickClient(int scaled) {
        energyPerTick = scaled * ENERGY_SYNC_SCALE;
    }

    public void setMaxEnergyPerTickClient(int scaled) {
        maxEnergyPerTick = scaled * ENERGY_SYNC_SCALE;
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
            for (int line = 0; line < LINE_SLOTS; line++) {
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
        for (int line = 0; line < LINE_SLOTS; line++) {
            if (progressMax[line] > 0) {
                nowActive = true;
                break;
            }
        }
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

    /** See TileAdvancedPulverizer#chargeFromItem - the machine's OWN "fuel me from a Capacitor" slot, unrelated to the 9 lines below (which spend that fuel charging OTHER items). */
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

    /**
     * One line's share of the tick - dual mode, exactly like real TE's own single-slot Charger
     * (see this class's own javadoc): an {@code IEnergyContainerItem} gets charged in place
     * ({@link #tryChargeItem}); anything else falls back to a real {@link ChargerManager}
     * item->item conversion recipe, processed the same progress-accumulation way every other
     * machine in this mod handles its own recipes.
     */
    private boolean tryProcess(int line) {
        ItemStack input = inventory[LINE_START + line];
        if (input == null) {
            if (progressMax[line] != 0 || progressRecipe[line] != null) {
                progress[line] = 0;
                progressMax[line] = 0;
                progressRecipe[line] = null;
                return true;
            }
            return false;
        }

        if (input.getItem() instanceof IEnergyContainerItem) {
            // Leaving (or staying out of) recipe-mode - null this out so that if this line goes
            // back to a recipe item later, the identity check below can never coincidentally see
            // a stale match and skip resetting progress (see this field's own doc comment).
            progressRecipe[line] = null;
            return tryChargeItem(line, input);
        }

        RecipeCharger recipe = ChargerManager.getRecipe(input);
        if (recipe == null || input.stackSize < recipe.getInput().stackSize) {
            if (progressMax[line] != 0 || progressRecipe[line] != null) {
                progress[line] = 0;
                progressMax[line] = 0;
                progressRecipe[line] = null;
                return true;
            }
            return false;
        }

        if (progressRecipe[line] != recipe) {
            // A different recipe than whatever this line's existing progress belonged to
            // (including "nothing yet" or "was charging an item a moment ago") - discard any
            // leftover progress before it's compared against this recipe's own cost below.
            progress[line] = 0;
            progressRecipe[line] = recipe;
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

        if (!canFitOutput(line, recipe.getOutput())) {
            return false;
        }

        addToOutput(line, recipe.getOutput());
        input.stackSize -= recipe.getInput().stackSize;
        if (input.stackSize <= 0) {
            inventory[LINE_START + line] = null;
        }
        progress[line] = 0;
        progressMax[line] = 0;
        progressRecipe[line] = null;
        return true;
    }

    /**
     * Drains RF from this machine's own buffer straight into the item sitting in {@code line},
     * up to {@code BASE_ENERGY_PER_TICK * speedEnergyMod} per tick - the exact reverse direction
     * of {@link #chargeFromItem}. {@code progress}/{@code progressMax} track the ITEM's own
     * stored/max RF (not a fixed recipe cost), so the GUI's progress bar reads as "how full is
     * this battery" for a charging line. Once full, the item moves to this line's own output
     * slot - real TE's own {@code transferOutput()} does the identical move for its one slot.
     */
    private boolean tryChargeItem(int line, ItemStack stack) {
        IEnergyContainerItem energyItem = (IEnergyContainerItem) stack.getItem();
        int itemEnergy = energyItem.getEnergyStored(stack);
        int itemMax = Math.max(1, energyItem.getMaxEnergyStored(stack));
        progressMax[line] = itemMax;
        progress[line] = itemEnergy;

        if (itemEnergy >= itemMax) {
            if (!canFitOutput(line, stack)) {
                return false;
            }
            addToOutput(line, stack.copy());
            inventory[LINE_START + line] = null;
            progress[line] = 0;
            progressMax[line] = 0;
            return true;
        }

        int rate = Math.min(BASE_ENERGY_PER_TICK * speedEnergyMod, Math.min(itemMax - itemEnergy, energyStorage.getEnergyStored()));
        if (rate <= 0) {
            return false;
        }
        int accepted = energyItem.receiveEnergy(stack, rate, false);
        if (accepted <= 0) {
            return false;
        }
        energyStorage.modifyEnergyStored(-accepted);
        energyPerTick += accepted;
        progress[line] = energyItem.getEnergyStored(stack);
        return true;
    }

    private boolean canFitOutput(int line, ItemStack stack) {
        ItemStack existing = inventory[OUTPUT_START + line];
        if (existing == null) {
            return true;
        }
        return existing.getItem() == stack.getItem()
                && existing.getItemDamage() == stack.getItemDamage()
                && ItemStack.areItemStackTagsEqual(existing, stack)
                && existing.stackSize + stack.stackSize <= existing.getMaxStackSize();
    }

    private void addToOutput(int line, ItemStack stack) {
        int slot = OUTPUT_START + line;
        if (inventory[slot] == null) {
            inventory[slot] = stack.copy();
        } else {
            inventory[slot].stackSize += stack.stackSize;
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

    /** Only pulls items this tile can actually do something with - a real IEnergyContainerItem, or a real ChargerManager recipe input - same "don't blindly hoover up random junk" rule the other machines' own auto-input already follows. */
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
            if (candidate == null || !(candidate.getItem() instanceof IEnergyContainerItem) && !ChargerManager.recipeExists(candidate)) {
                continue;
            }
            if (neighborInv instanceof ISidedInventory
                    && !((ISidedInventory) neighborInv).canExtractItem(slotIdx, candidate, fromSide.ordinal())) {
                continue;
            }
            int lineSlot = findLineSlotFor(candidate);
            if (lineSlot < 0) {
                continue;
            }
            if (inventory[lineSlot] == null) {
                ItemStack moved = candidate.copy();
                moved.stackSize = 1;
                inventory[lineSlot] = moved;
            } else {
                inventory[lineSlot].stackSize++;
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

    /**
     * Unlike the other machines' own findInputSlotFor, a battery-type item almost never stacks
     * (each one tracks its own current charge in its own NBT), so this only ever merges into an
     * existing line slot for genuinely stackable, NBT-identical items (uncharged/undamaged
     * duplicates, or plain ChargerManager recipe ingredients) - otherwise it takes the first
     * empty line.
     */
    private int findLineSlotFor(ItemStack stack) {
        int emptySlot = -1;
        for (int i = 0; i < LINE_SLOTS; i++) {
            ItemStack buf = inventory[LINE_START + i];
            if (buf == null) {
                if (emptySlot < 0) {
                    emptySlot = LINE_START + i;
                }
                continue;
            }
            if (buf.getItem() == stack.getItem() && buf.getItemDamage() == stack.getItemDamage()
                    && ItemStack.areItemStackTagsEqual(buf, stack) && buf.stackSize < buf.getMaxStackSize()) {
                return LINE_START + i;
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
        return "container.advancedCharger";
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
            return stack.getItem() instanceof IEnergyContainerItem || ChargerManager.recipeExists(stack);
        }
        return false;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        int mode = sideCache[side];
        boolean input = modeAllowsInsertInput(mode) || modeAllowsExtractInput(mode);
        boolean output = modeAllowsExtractOutput(mode);

        int[] slots = new int[(input ? LINE_SLOTS : 0) + (output ? OUTPUT_SLOTS : 0)];
        int idx = 0;
        if (input) {
            int start = (int) ((worldObj != null ? worldObj.getTotalWorldTime() : 0) % LINE_SLOTS);
            for (int i = 0; i < LINE_SLOTS; i++) {
                slots[idx++] = LINE_START + (start + i) % LINE_SLOTS;
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
        return slot < OUTPUT_START && modeAllowsInsertInput(sideCache[side])
                && (stack.getItem() instanceof IEnergyContainerItem || ChargerManager.recipeExists(stack));
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
        for (int i = 0; i < LINE_SLOTS; i++) {
            if (progressMax[i] > 0) {
                isActive = true;
                break;
            }
        }
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
