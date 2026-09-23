package net.thermaladd.mod.tileentity;

import net.minecraft.item.ItemStack;

import cofh.thermalexpansion.util.crafting.SmelterManager;
import cofh.thermalexpansion.util.crafting.SmelterManager.RecipeSmelter;

/**
 * Singular Induction Smelter - three parallel lines of real Thermal Expansion's Induction Smelter
 * ({@code TileSmelter}), each with its own pair of inputs, running TE's own {@link SmelterManager}
 * recipes. Primary outputs go to three shared slots, the chance-based secondary (Rich Slag and the
 * like) to two, exactly like the Sawmill's layout.
 *
 * The side modes are TE's own eight, numbered and coloured the same ({@code TileSmelter#initialize}:
 * Input, Output Primary/Secondary/Both, then Input Primary (green) and Input Secondary (purple) for
 * feeding the two inputs from different sides, then All).
 *
 * Beyond TE: a line's second input only accepts what forms a recipe with whatever already sits
 * in its first (and the other way round), so auto input and pipes build valid pairs instead of
 * filling a line with two stacks of sand. Every input slot can also be locked on its own.
 */
public class TileSingularSmelter extends TileSingularityMachine {

    public static final int LINES = 3;
    /** Two per line: slot 2*line is input A, 2*line+1 input B. */
    public static final int INPUT_SLOTS = LINES * 2;
    public static final int OUTPUT_PRIMARY_SLOTS = 3;
    public static final int OUTPUT_SECONDARY_SLOTS = 2;
    public static final int OUTPUT_PRIMARY_START = INPUT_SLOTS;
    public static final int OUTPUT_SECONDARY_START = OUTPUT_PRIMARY_START + OUTPUT_PRIMARY_SLOTS;
    public static final int MACHINE_SLOTS = OUTPUT_SECONDARY_START + OUTPUT_SECONDARY_SLOTS;

    /** Twice real TE's 40 RF/t Smelter base power per line, the same ratio as the other machines. */
    public static int BASE_ENERGY_PER_TICK = 80;
    public static int BASE_ENERGY_CAPACITY = 1000000;
    public static int ENERGY_RECEIVE_PER_TICK = 10000;

    public static final String SOUND_NAME = "thermalexpansion:blockMachineSmelter";

    public static final int SIDE_MODE_INPUT = 1;
    public static final int SIDE_MODE_OUTPUT_PRIMARY = 2;
    public static final int SIDE_MODE_OUTPUT_SECONDARY = 3;
    public static final int SIDE_MODE_OUTPUT_BOTH = 4;
    public static final int SIDE_MODE_INPUT_PRIMARY = 5;
    public static final int SIDE_MODE_INPUT_SECONDARY = 6;
    public static final int SIDE_MODE_ALL = 7;
    public static final int SIDE_MODE_COUNT = 8;

    public static final String[] SIDE_BADGES = {
            null, "Input", "OutputPrimary", "OutputSecondary", "OutputBoth", "InputRow1", "InputRow2", "All"};
    public static final String[] SIDE_NAME_KEYS = {
            "gui.thermaladd.mode.disabled", "gui.thermaladd.mode.input",
            "gui.thermaladd.mode.outputPrimary", "gui.thermaladd.mode.outputSecondary",
            "gui.thermaladd.mode.outputBoth", "gui.thermaladd.mode.inputPrimary",
            "gui.thermaladd.mode.inputSecondary", "gui.thermaladd.mode.all"};

    public TileSingularSmelter() {
        super(MACHINE_SLOTS, LINES, INPUT_SLOTS, 0);
    }

    public static boolean isInputA(int slot) {
        return slot >= 0 && slot < INPUT_SLOTS && slot % 2 == 0;
    }

    public static boolean isInputB(int slot) {
        return slot >= 0 && slot < INPUT_SLOTS && slot % 2 == 1;
    }

    private static boolean isPrimaryOutput(int slot) {
        return slot >= OUTPUT_PRIMARY_START && slot < OUTPUT_SECONDARY_START;
    }

    private static boolean isSecondaryOutput(int slot) {
        return slot >= OUTPUT_SECONDARY_START && slot < MACHINE_SLOTS;
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
        return "singularSmelter";
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
        return "Smelter";
    }

    @Override
    public String getSoundName() {
        return SOUND_NAME;
    }

    @Override
    protected boolean usesSecondaryAugments() {
        return true;
    }

    /** TE's slot groups: {@code {[], [0,1], [2,3], [4], [2,3,4], [0], [1], [0..4]}}. */
    @Override
    public boolean sideInserts(int mode, int slot) {
        switch (mode) {
            case SIDE_MODE_INPUT:
            case SIDE_MODE_ALL:
                return slot < INPUT_SLOTS;
            case SIDE_MODE_INPUT_PRIMARY:
                return isInputA(slot);
            case SIDE_MODE_INPUT_SECONDARY:
                return isInputB(slot);
            default:
                return false;
        }
    }

    /** TE's allowExtractionSide: Input and All also give their inputs back, the input-only modes do not. */
    @Override
    public boolean sideExtracts(int mode, int slot) {
        switch (mode) {
            case SIDE_MODE_INPUT:
                return slot < INPUT_SLOTS;
            case SIDE_MODE_OUTPUT_PRIMARY:
                return isPrimaryOutput(slot);
            case SIDE_MODE_OUTPUT_SECONDARY:
                return isSecondaryOutput(slot);
            case SIDE_MODE_OUTPUT_BOTH:
                return isPrimaryOutput(slot) || isSecondaryOutput(slot);
            case SIDE_MODE_ALL:
                return slot < MACHINE_SLOTS;
            default:
                return false;
        }
    }

    @Override
    public boolean isOutputSlot(int slot) {
        return slot >= OUTPUT_PRIMARY_START && slot < MACHINE_SLOTS;
    }

    /**
     * Any item TE's Smelter takes at all, and - when the other input of the line is filled - only
     * one that forms a recipe with it.
     */
    @Override
    protected boolean isValidMachineInput(int slot, ItemStack stack) {
        if (!SmelterManager.isItemValid(stack)) {
            return false;
        }
        ItemStack partner = inventory[slot ^ 1];
        return partner == null || SmelterManager.getRecipe(partner, stack) != null;
    }

    @Override
    public boolean isLineOccupied(int line) {
        return inventory[line * 2] != null || inventory[line * 2 + 1] != null;
    }

    @Override
    protected boolean processLine(int line) {
        int slotA = line * 2;
        int slotB = slotA + 1;
        ItemStack a = inventory[slotA];
        ItemStack b = inventory[slotB];
        RecipeSmelter recipe = SmelterManager.getRecipe(a, b);
        if (recipe == null) {
            return idleLine(line);
        }
        // TE matches either order and consumes by whichever way round the recipe was found.
        boolean reversed = SmelterManager.isRecipeReversed(a, b);
        int needA = reversed ? recipe.getSecondaryInput().stackSize : recipe.getPrimaryInput().stackSize;
        int needB = reversed ? recipe.getPrimaryInput().stackSize : recipe.getSecondaryInput().stackSize;
        if (a.stackSize < needA || b.stackSize < needB) {
            return idleLine(line);
        }

        if (workLine(line, recipe.getEnergy())) {
            return true;
        }
        if (!isLineComplete(line)) {
            return false;
        }

        ItemStack primary = recipe.getPrimaryOutput();
        ItemStack secondary = recipe.getSecondaryOutput();
        if (!canFitAny(OUTPUT_PRIMARY_START, OUTPUT_PRIMARY_SLOTS, primary)) {
            return false;
        }
        if (secondary != null && !augmentSecondaryNull
                && !canFitAny(OUTPUT_SECONDARY_START, OUTPUT_SECONDARY_SLOTS, secondary)) {
            return false;
        }

        addToFirstFitting(OUTPUT_PRIMARY_START, OUTPUT_PRIMARY_SLOTS, primary);
        if (secondary != null && rollChance(recipe.getSecondaryOutputChance())) {
            addToFirstFitting(OUTPUT_SECONDARY_START, OUTPUT_SECONDARY_SLOTS, secondary);
        }
        consumeInput(slotA, needA);
        consumeInput(slotB, needB);
        resetLine(line);
        return true;
    }
}
