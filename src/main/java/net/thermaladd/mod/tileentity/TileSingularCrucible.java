package net.thermaladd.mod.tileentity;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import cofh.thermalexpansion.util.crafting.CrucibleManager;
import cofh.thermalexpansion.util.crafting.CrucibleManager.RecipeCrucible;

/**
 * Singular Magma Crucible - three parallel lines of real Thermal Expansion's Magma Crucible
 * ({@code TileCrucible}) melting into one shared tank, on TE's own {@link CrucibleManager}
 * recipes (cobblestone/stone/obsidian/netherrack to lava, redstone, glowstone, ender pearls and
 * the elemental dusts to their fluids).
 *
 * Side modes are TE's own four ({@code TileCrucible#initialize}: Input, Output, All, same blue and
 * orange badges). TE only lets a side in plain Output mode give up the fluid; here All does too.
 */
public class TileSingularCrucible extends TileSingularityMachine implements IFluidHandler {

    public static final int LINES = 3;
    public static final int INPUT_SLOTS = LINES;
    public static final int MACHINE_SLOTS = INPUT_SLOTS;
    /** Ten times real TE's 10,000 mB, like the Assembler's tank. */
    public static final int TANK_CAPACITY = 100000;

    /** Twice real TE's 400 RF/t Crucible base power per line. */
    public static int BASE_ENERGY_PER_TICK = 800;
    public static int BASE_ENERGY_CAPACITY = 2000000;
    public static int ENERGY_RECEIVE_PER_TICK = 20000;

    public static final String SOUND_NAME = "thermalexpansion:blockMachineCrucible";

    public static final int SIDE_MODE_INPUT = 1;
    public static final int SIDE_MODE_OUTPUT = 2;
    public static final int SIDE_MODE_ALL = 3;
    public static final int SIDE_MODE_COUNT = 4;

    public static final String[] SIDE_BADGES = {null, "Input", "Output", "All"};
    public static final String[] SIDE_NAME_KEYS = {
            "gui.thermaladd.mode.disabled", "gui.thermaladd.mode.input",
            "gui.thermaladd.mode.outputFluid", "gui.thermaladd.mode.all"};

    public TileSingularCrucible() {
        super(MACHINE_SLOTS, LINES, INPUT_SLOTS, TANK_CAPACITY);
    }

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
        return "singularCrucible";
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
        return "Crucible";
    }

    @Override
    public String getSoundName() {
        return SOUND_NAME;
    }

    /** No secondary output to sieve. */
    @Override
    protected boolean usesSecondaryAugments() {
        return false;
    }

    @Override
    public boolean sideInserts(int mode, int slot) {
        return (mode == SIDE_MODE_INPUT || mode == SIDE_MODE_ALL) && slot < INPUT_SLOTS;
    }

    /** TE's allowExtractionSide: Input and All give the inputs back. */
    @Override
    public boolean sideExtracts(int mode, int slot) {
        return (mode == SIDE_MODE_INPUT || mode == SIDE_MODE_ALL) && slot < INPUT_SLOTS;
    }

    @Override
    protected boolean sideDrainsFluid(int mode) {
        return mode == SIDE_MODE_OUTPUT || mode == SIDE_MODE_ALL;
    }

    @Override
    public boolean isOutputSlot(int slot) {
        return false;
    }

    @Override
    protected boolean isValidMachineInput(int slot, ItemStack stack) {
        return CrucibleManager.recipeExists(stack);
    }

    @Override
    public boolean isLineOccupied(int line) {
        return inventory[line] != null;
    }

    @Override
    protected boolean processLine(int line) {
        ItemStack input = inventory[line];
        RecipeCrucible recipe = input == null ? null : CrucibleManager.getRecipe(input);
        if (recipe == null || input.stackSize < recipe.getInput().stackSize) {
            return idleLine(line);
        }
        if (workLine(line, recipe.getEnergy())) {
            return true;
        }
        if (!isLineComplete(line)) {
            return false;
        }
        FluidStack output = recipe.getOutput();
        if (tank.fill(output, false) != output.amount) {
            return false;
        }
        tank.fill(output, true);
        consumeInput(line, recipe.getInput().stackSize);
        resetLine(line);
        return true;
    }

    // ---------------------------------------------------------------- IFluidHandler

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        return 0;
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

    private boolean canDrainFrom(ForgeDirection from) {
        return from == ForgeDirection.UNKNOWN || sideDrainsFluid(sideCache[from.ordinal()]);
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return false;
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
