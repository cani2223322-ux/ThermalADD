package net.thermaladd.mod.tileentity;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidContainerItem;
import net.minecraftforge.fluids.IFluidHandler;

import cofh.thermalexpansion.util.crafting.TransposerManager;
import cofh.thermalexpansion.util.crafting.TransposerManager.RecipeTransposer;

/**
 * Singular Fluid Transposer - three parallel lines of real Thermal Expansion's Fluid Transposer
 * ({@code TileTransposer}) around one shared tank, on TE's own {@link TransposerManager} fill and
 * extraction recipes. The whole machine is either filling (tank into items) or extracting (items
 * into the tank), toggled from the GUI like TE's own mode button.
 *
 * Fluid container items TE handles without a recipe (portable tanks and the like - anything that
 * is an {@link IFluidContainerItem}) are filled or emptied gradually, one at a time per line, and
 * moved to the output once full or empty - the same thing TE's processContainerItem does.
 *
 * Side modes are TE's own six ({@code TileTransposer#initialize}): Input (items, and fluid into the
 * tank while filling), Output Items, Output Fluid (while extracting), Output Both, All. All also
 * moves fluid here, which TE's does not.
 */
public class TileSingularTransposer extends TileSingularityMachine implements IFluidHandler {

    public static final int LINES = 3;
    public static final int INPUT_SLOTS = LINES;
    public static final int OUTPUT_SLOTS = 3;
    public static final int OUTPUT_START = INPUT_SLOTS;
    public static final int MACHINE_SLOTS = INPUT_SLOTS + OUTPUT_SLOTS;
    public static final int TANK_CAPACITY = 100000;

    /** Twice real TE's 40 RF/t Transposer base power per line. */
    public static int BASE_ENERGY_PER_TICK = 80;
    public static int BASE_ENERGY_CAPACITY = 1000000;
    public static int ENERGY_RECEIVE_PER_TICK = 10000;

    public static final String SOUND_NAME = "thermalexpansion:blockMachineTransposer";

    public static final int MODE_FILL = 0;
    public static final int MODE_EXTRACT = 1;

    public static final int SIDE_MODE_INPUT = 1;
    public static final int SIDE_MODE_OUTPUT_ITEMS = 2;
    public static final int SIDE_MODE_OUTPUT_FLUID = 3;
    public static final int SIDE_MODE_OUTPUT_BOTH = 4;
    public static final int SIDE_MODE_ALL = 5;
    public static final int SIDE_MODE_COUNT = 6;

    public static final String[] SIDE_BADGES = {null, "Input", "OutputPrimary", "OutputSecondary", "OutputBoth", "All"};
    public static final String[] SIDE_NAME_KEYS = {
            "gui.thermaladd.mode.disabled", "gui.thermaladd.mode.input",
            "gui.thermaladd.mode.outputItems", "gui.thermaladd.mode.outputFluid",
            "gui.thermaladd.mode.outputBoth", "gui.thermaladd.mode.all"};

    private int machineMode = MODE_FILL;

    public TileSingularTransposer() {
        super(MACHINE_SLOTS, LINES, INPUT_SLOTS, TANK_CAPACITY);
    }

    // ---------------------------------------------------------------- mode

    @Override
    public int getMachineMode() {
        return machineMode;
    }

    public boolean isExtracting() {
        return machineMode == MODE_EXTRACT;
    }

    @Override
    public void setMachineMode(int mode) {
        if ((mode == MODE_FILL || mode == MODE_EXTRACT) && mode != machineMode) {
            machineMode = mode;
            // Work in progress belongs to the other direction's recipe.
            for (int line = 0; line < LINES; line++) {
                resetLine(line);
            }
            markDirty();
        }
    }

    @Override
    public void setMachineModeClient(int mode) {
        machineMode = mode == MODE_EXTRACT ? MODE_EXTRACT : MODE_FILL;
    }

    @Override
    protected void writeMachineToNBT(NBTTagCompound tag) {
        tag.setBoolean("Rev", machineMode == MODE_EXTRACT);
    }

    @Override
    protected void readMachineFromNBT(NBTTagCompound tag) {
        machineMode = tag.getBoolean("Rev") ? MODE_EXTRACT : MODE_FILL;
    }

    // ---------------------------------------------------------------- description

    @Override
    protected int getBaseCapacity() {
        return BASE_ENERGY_CAPACITY;
    }

    @Override
    protected int getBaseReceive() {
        return ENERGY_RECEIVE_PER_TICK;
    }

    @Override
    public int getBaseEnergyPerTick() {
        return BASE_ENERGY_PER_TICK;
    }

    @Override
    public String getMachineKey() {
        return "singularTransposer";
    }

    @Override
    public int getSideModeCount() {
        return SIDE_MODE_COUNT;
    }

    @Override
    public String[] getSideModeBadges() {
        return SIDE_BADGES;
    }

    @Override
    public String[] getSideModeNameKeys() {
        return SIDE_NAME_KEYS;
    }

    @Override
    public String getFaceTextureName() {
        return "Transposer";
    }

    @Override
    public String getSoundName() {
        return SOUND_NAME;
    }

    /** Extraction recipes have a chance-based item output, which the Secondary Sieve improves. */
    @Override
    protected boolean usesSecondaryAugments() {
        return true;
    }

    private static boolean isOutput(int slot) {
        return slot >= OUTPUT_START && slot < MACHINE_SLOTS;
    }

    @Override
    public boolean sideInserts(int mode, int slot) {
        return (mode == SIDE_MODE_INPUT || mode == SIDE_MODE_ALL) && slot < INPUT_SLOTS;
    }

    /** TE's slot groups: {@code {[], [0], [2], [], [2], [0,2]}}; Input gives its inputs back. */
    @Override
    public boolean sideExtracts(int mode, int slot) {
        switch (mode) {
            case SIDE_MODE_INPUT:
                return slot < INPUT_SLOTS;
            case SIDE_MODE_OUTPUT_ITEMS:
            case SIDE_MODE_OUTPUT_BOTH:
                return isOutput(slot);
            case SIDE_MODE_ALL:
                return slot < MACHINE_SLOTS;
            default:
                return false;
        }
    }

    @Override
    protected boolean sideDrainsFluid(int mode) {
        return isExtracting() && (mode == SIDE_MODE_OUTPUT_FLUID || mode == SIDE_MODE_OUTPUT_BOTH || mode == SIDE_MODE_ALL);
    }

    private boolean sideFillsFluid(int mode) {
        return !isExtracting() && (mode == SIDE_MODE_INPUT || mode == SIDE_MODE_ALL);
    }

    @Override
    public boolean isOutputSlot(int slot) {
        return isOutput(slot);
    }

    @Override
    protected boolean isValidMachineInput(int slot, ItemStack stack) {
        return TransposerManager.isItemValid(stack) || stack.getItem() instanceof IFluidContainerItem;
    }

    @Override
    public boolean isLineOccupied(int line) {
        return inventory[line] != null;
    }

    // ---------------------------------------------------------------- processing

    /** The recipe a line would run in the current mode, or null. */
    public RecipeTransposer getLineRecipe(int line) {
        ItemStack input = inventory[line];
        if (input == null) {
            return null;
        }
        return isExtracting() ? TransposerManager.getExtractionRecipe(input) : TransposerManager.getFillRecipe(input, tank.getFluid());
    }

    @Override
    protected boolean processLine(int line) {
        ItemStack input = inventory[line];
        if (input == null) {
            return idleLine(line);
        }
        RecipeTransposer recipe = getLineRecipe(line);
        if (recipe == null) {
            if (input.getItem() instanceof IFluidContainerItem && input.stackSize == 1) {
                return processContainer(line, input);
            }
            // Filling with an empty tank: hold the progress until fluid arrives.
            return !isExtracting() && tank.getFluidAmount() <= 0 ? false : idleLine(line);
        }
        if (input.stackSize < recipe.getInput().stackSize) {
            return idleLine(line);
        }
        if (workLine(line, recipe.getEnergy())) {
            return true;
        }
        if (!isLineComplete(line)) {
            return false;
        }

        FluidStack fluid = recipe.getFluid();
        ItemStack output = recipe.getOutput();
        if (output != null && !canFitAny(OUTPUT_START, OUTPUT_SLOTS, output)) {
            return false;
        }
        if (isExtracting()) {
            if (tank.fill(fluid, false) != fluid.amount) {
                return false;
            }
            tank.fill(fluid, true);
            if (output != null && rollChance(recipe.getChance())) {
                addToFirstFitting(OUTPUT_START, OUTPUT_SLOTS, output);
            }
        } else {
            if (tank.getFluidAmount() < fluid.amount) {
                return false;
            }
            tank.drain(fluid.amount, true);
            addToFirstFitting(OUTPUT_START, OUTPUT_SLOTS, output);
        }
        consumeInput(line, recipe.getInput().stackSize);
        resetLine(line);
        return true;
    }

    /**
     * A fluid container item: moves up to one tick's worth of fluid (the line's work rate, in mB -
     * TE also trades RF for mB one to one) between it and the tank, and hands it to the output
     * once it is full (filling) or empty (extracting). Progress shows how far along it is.
     */
    private boolean processContainer(int line, ItemStack stack) {
        IFluidContainerItem item = (IFluidContainerItem) stack.getItem();
        int capacity = item.getCapacity(stack);
        FluidStack contained = item.getFluid(stack);
        int amount = contained == null ? 0 : contained.amount;
        if (capacity <= 0) {
            return idleLine(line);
        }

        boolean done = isExtracting() ? amount <= 0 : amount >= capacity;
        if (done) {
            if (!canFitAny(OUTPUT_START, OUTPUT_SLOTS, stack)) {
                return false;
            }
            addToFirstFitting(OUTPUT_START, OUTPUT_SLOTS, stack);
            inventory[line] = null;
            resetLine(line);
            return true;
        }

        int cost = getLineEnergyCost();
        if (energyStorage.getEnergyStored() < cost) {
            return false;
        }
        int rate = getBaseEnergyPerTick() * speedProcessMod;
        int moved;
        if (isExtracting()) {
            FluidStack simulated = item.drain(stack, rate, false);
            int accepted = simulated == null ? 0 : tank.fill(simulated, false);
            if (accepted <= 0) {
                return false;
            }
            FluidStack drained = item.drain(stack, accepted, true);
            moved = drained == null ? 0 : tank.fill(drained, true);
        } else {
            if (tank.getFluidAmount() <= 0) {
                return false;
            }
            FluidStack offer = tank.getFluid().copy();
            offer.amount = Math.min(rate, offer.amount);
            moved = item.fill(stack, offer, true);
            if (moved > 0) {
                tank.drain(moved, true);
            }
        }
        if (moved <= 0) {
            // The container refuses this fluid - leave it for the player to take out.
            return false;
        }
        energyStorage.modifyEnergyStored(-cost);
        energyPerTick += cost;
        FluidStack now = item.getFluid(stack);
        int nowAmount = now == null ? 0 : now.amount;
        progressMax[line] = capacity;
        progress[line] = isExtracting() ? capacity - nowAmount : nowAmount;
        return true;
    }

    // ---------------------------------------------------------------- IFluidHandler

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        if (!canFillFrom(from)) {
            return 0;
        }
        return tank.fill(resource, doFill);
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        if (resource == null || !resource.isFluidEqual(tank.getFluid()) || !canDrainFrom(from)) {
            return null;
        }
        return tank.drain(resource.amount, doDrain);
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        return canDrainFrom(from) ? tank.drain(maxDrain, doDrain) : null;
    }

    private boolean canFillFrom(ForgeDirection from) {
        return from != ForgeDirection.UNKNOWN && sideFillsFluid(sideCache[from.ordinal()]);
    }

    private boolean canDrainFrom(ForgeDirection from) {
        return from != ForgeDirection.UNKNOWN && sideDrainsFluid(sideCache[from.ordinal()]);
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return canFillFrom(from);
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return canDrainFrom(from);
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        return new FluidTankInfo[]{tank.getInfo()};
    }
}
