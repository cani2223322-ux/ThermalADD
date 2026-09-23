package net.thermaladd.mod.config;

import java.io.File;

import cpw.mods.fml.common.FMLLog;
import net.minecraftforge.common.config.Configuration;
import net.thermaladd.mod.tileentity.TileAdvancedCharger;
import net.thermaladd.mod.tileentity.TileAdvancedFurnace;
import net.thermaladd.mod.tileentity.TileAdvancedPulverizer;
import net.thermaladd.mod.tileentity.TileAdvancedSawmill;
import net.thermaladd.mod.tileentity.TileImprovedAssembler;
import net.thermaladd.mod.tileentity.TileSingularityCell;
import net.thermaladd.mod.tileentity.TileSingularCrucible;
import net.thermaladd.mod.tileentity.TileSingularSmelter;
import net.thermaladd.mod.tileentity.TileSingularTransposer;
import net.thermaladd.mod.tileentity.TileSingularityTank;

/**
 * Loads config/ThermalADD.cfg and writes the tunable values straight into the tiles' own
 * static fields, so each tile keeps a single source of truth for its default (the field's
 * own initializer, which is also what the config file is seeded with on first run).
 *
 * Must run in preInit, strictly before any tile is constructed - the tiles read these fields
 * in their field initializers ({@code new EnergyStorage(BASE_ENERGY_CAPACITY, ...)}).
 *
 * WHY EVERY VALUE IS CLAMPED: the machine GUIs sync their readouts through vanilla's
 * Container#sendProgressBarUpdate, which serializes as a SIGNED 16-BIT SHORT (max 32767). Energy
 * and RF/t are sent as exact low/high halves and so are effectively unbounded, but the per-line
 * progress values are still sent whole, and a config value past what those can carry would
 * silently wrap and show garbage (or negative) numbers. The maxima below are derived from those
 * limits rather than picked for flavor, and each is noted with the reasoning behind it.
 */
public final class ModConfig {

    private ModConfig() {
    }

    private static final String CAT_PULVERIZER = "machines.pulverizer";
    private static final String CAT_FURNACE = "machines.furnace";
    private static final String CAT_SAWMILL = "machines.sawmill";
    private static final String CAT_CHARGER = "machines.charger";
    private static final String CAT_ASSEMBLER = "machines.assembler";
    private static final String CAT_CELL = "machines.cell";
    private static final String CAT_SMELTER = "machines.smelter";
    private static final String CAT_CRUCIBLE = "machines.crucible";
    private static final String CAT_TRANSPOSER = "machines.transposer";
    private static final String CAT_TANK = "storage.tank";
    private static final String CAT_RECIPES = "recipes";
    private static final String CAT_GUI = "gui";

    /**
     * Energy buffer ceiling. The buffer itself is no longer the constraint - stored and maximum RF
     * are sent to the GUI as exact low/high 16-bit halves (see
     * TileAdvancedPulverizer#applyClientEnergy) rather than divided by a scale - so this only has
     * to leave room for the Energy Storage augment's x8 multiplier inside a 32-bit int.
     */
    private static final int MAX_CAPACITY = 100000000;

    /**
     * RF/t ceiling for the three "3 parallel lines" machines. The RF/t readouts are exact halves
     * too now, so what actually bounds this is the per-line PROGRESS sync, which is still a raw
     * short: one tick adds {@code cost * 10} (the maximum Machine Speed process multiplier) on top
     * of a recipe's own RF cost, and 1000 * 10 = 10,000 leaves plenty of room under 32767 for even
     * an unusually expensive third-party recipe.
     */
    private static final int MAX_PROCESS_ENERGY_3LINE = 1000;
    /**
     * The Charger's per-line progress is sent as exact halves, so no short limit applies to it;
     * this ceiling only keeps the cost in the same order of magnitude as its default of 16,000.
     */
    private static final int MAX_PROCESS_ENERGY_CHARGER = 40000;
    /** The Assembler has no accumulating progress at all - a craft completes within one tick. */
    private static final int MAX_PROCESS_ENERGY_ASSEMBLER = 1000;
    /**
     * The TileSingularityMachine family (Smelter, Crucible, Transposer) syncs progress as exact halves
     * too, so this only keeps the cost within reason - real TE's own Crucible already runs at 400.
     */
    private static final int MAX_PROCESS_ENERGY_SINGULAR = 4000;

    private static final int MIN_CAPACITY = 10000;
    private static final int MIN_TRANSFER = 100;
    private static final int MAX_TRANSFER = 10000000;

    /** The Cell syncs through its own packet using 64-bit longs, so it has no short-sized ceiling. */
    private static final long MIN_CELL_CAPACITY = 1000000L;
    private static final long MAX_CELL_CAPACITY = 1000000000000000L;

    /** Read by TabbedMachineGui when it loads - see loadGui(). */
    public static boolean colorBlindPalette = false;

    public static boolean recipeAdvancedPulverizer = true;
    public static boolean recipeAdvancedFurnace = true;
    public static boolean recipeAdvancedSawmill = true;
    public static boolean recipeAdvancedCharger = true;
    public static boolean recipeImprovedAssembler = true;
    public static boolean recipeSingularSmelter = true;
    public static boolean recipeSingularCrucible = true;
    public static boolean recipeSingularTransposer = true;
    public static boolean recipeSingularityTank = true;
    public static boolean recipeSingularityStrongbox = true;
    public static boolean recipeSingularityCell = true;
    public static boolean recipeSingularityGear = true;
    public static boolean recipeSingularityFrame = true;
    public static boolean recipeSpeedLevel4Augment = true;
    public static boolean recipeSecondarySieve4Augment = true;

    public static void load(File configFile) {
        Configuration cfg = new Configuration(configFile);
        try {
            cfg.load();
            loadMachines(cfg);
            loadRecipes(cfg);
            loadGui(cfg);
        } catch (Throwable t) {
            // A broken config must never stop the mod from loading - every field keeps whatever
            // default it was initialized with, which is exactly the shipped balance.
            FMLLog.severe("[ThermalADD] Failed to read the config, using defaults: %s", t);
        } finally {
            if (cfg.hasChanged()) {
                cfg.save();
            }
        }
        // Taken whatever happened above, so a client always has its own values to go back to
        // after leaving a server that sent different ones.
        localMachineValues = snapshotMachineValues();
        localCellCapacity = TileSingularityCell.CAPACITY;
    }

    private static void loadMachines(Configuration cfg) {
        TileAdvancedPulverizer.BASE_ENERGY_CAPACITY = capacity(cfg, CAT_PULVERIZER,
                TileAdvancedPulverizer.BASE_ENERGY_CAPACITY, MAX_CAPACITY);
        TileAdvancedPulverizer.ENERGY_RECEIVE_PER_TICK = transfer(cfg, CAT_PULVERIZER,
                TileAdvancedPulverizer.ENERGY_RECEIVE_PER_TICK);
        TileAdvancedPulverizer.BASE_ENERGY_PER_TICK = process(cfg, CAT_PULVERIZER,
                TileAdvancedPulverizer.BASE_ENERGY_PER_TICK, MAX_PROCESS_ENERGY_3LINE);

        TileAdvancedFurnace.BASE_ENERGY_CAPACITY = capacity(cfg, CAT_FURNACE,
                TileAdvancedFurnace.BASE_ENERGY_CAPACITY, MAX_CAPACITY);
        TileAdvancedFurnace.ENERGY_RECEIVE_PER_TICK = transfer(cfg, CAT_FURNACE,
                TileAdvancedFurnace.ENERGY_RECEIVE_PER_TICK);
        TileAdvancedFurnace.BASE_ENERGY_PER_TICK = process(cfg, CAT_FURNACE,
                TileAdvancedFurnace.BASE_ENERGY_PER_TICK, MAX_PROCESS_ENERGY_3LINE);

        TileAdvancedSawmill.BASE_ENERGY_CAPACITY = capacity(cfg, CAT_SAWMILL,
                TileAdvancedSawmill.BASE_ENERGY_CAPACITY, MAX_CAPACITY);
        TileAdvancedSawmill.ENERGY_RECEIVE_PER_TICK = transfer(cfg, CAT_SAWMILL,
                TileAdvancedSawmill.ENERGY_RECEIVE_PER_TICK);
        TileAdvancedSawmill.BASE_ENERGY_PER_TICK = process(cfg, CAT_SAWMILL,
                TileAdvancedSawmill.BASE_ENERGY_PER_TICK, MAX_PROCESS_ENERGY_3LINE);

        TileAdvancedCharger.BASE_ENERGY_CAPACITY = capacity(cfg, CAT_CHARGER,
                TileAdvancedCharger.BASE_ENERGY_CAPACITY, MAX_CAPACITY);
        TileAdvancedCharger.ENERGY_RECEIVE_PER_TICK = transfer(cfg, CAT_CHARGER,
                TileAdvancedCharger.ENERGY_RECEIVE_PER_TICK);
        TileAdvancedCharger.BASE_ENERGY_PER_TICK = process(cfg, CAT_CHARGER,
                TileAdvancedCharger.BASE_ENERGY_PER_TICK, MAX_PROCESS_ENERGY_CHARGER);

        TileImprovedAssembler.ENERGY_CAPACITY = capacity(cfg, CAT_ASSEMBLER,
                TileImprovedAssembler.ENERGY_CAPACITY, MAX_CAPACITY);
        TileImprovedAssembler.ENERGY_RECEIVE_PER_TICK = transfer(cfg, CAT_ASSEMBLER,
                TileImprovedAssembler.ENERGY_RECEIVE_PER_TICK);
        TileImprovedAssembler.PROCESS_ENERGY = process(cfg, CAT_ASSEMBLER,
                TileImprovedAssembler.PROCESS_ENERGY, MAX_PROCESS_ENERGY_ASSEMBLER);

        TileSingularSmelter.BASE_ENERGY_CAPACITY = capacity(cfg, CAT_SMELTER,
                TileSingularSmelter.BASE_ENERGY_CAPACITY, MAX_CAPACITY);
        TileSingularSmelter.ENERGY_RECEIVE_PER_TICK = transfer(cfg, CAT_SMELTER,
                TileSingularSmelter.ENERGY_RECEIVE_PER_TICK);
        TileSingularSmelter.BASE_ENERGY_PER_TICK = process(cfg, CAT_SMELTER,
                TileSingularSmelter.BASE_ENERGY_PER_TICK, MAX_PROCESS_ENERGY_SINGULAR);

        TileSingularCrucible.BASE_ENERGY_CAPACITY = capacity(cfg, CAT_CRUCIBLE,
                TileSingularCrucible.BASE_ENERGY_CAPACITY, MAX_CAPACITY);
        TileSingularCrucible.ENERGY_RECEIVE_PER_TICK = transfer(cfg, CAT_CRUCIBLE,
                TileSingularCrucible.ENERGY_RECEIVE_PER_TICK);
        TileSingularCrucible.BASE_ENERGY_PER_TICK = process(cfg, CAT_CRUCIBLE,
                TileSingularCrucible.BASE_ENERGY_PER_TICK, MAX_PROCESS_ENERGY_SINGULAR);

        TileSingularTransposer.BASE_ENERGY_CAPACITY = capacity(cfg, CAT_TRANSPOSER,
                TileSingularTransposer.BASE_ENERGY_CAPACITY, MAX_CAPACITY);
        TileSingularTransposer.ENERGY_RECEIVE_PER_TICK = transfer(cfg, CAT_TRANSPOSER,
                TileSingularTransposer.ENERGY_RECEIVE_PER_TICK);
        TileSingularTransposer.BASE_ENERGY_PER_TICK = process(cfg, CAT_TRANSPOSER,
                TileSingularTransposer.BASE_ENERGY_PER_TICK, MAX_PROCESS_ENERGY_SINGULAR);

        TileSingularityCell.CAPACITY = cellCapacity(cfg, TileSingularityCell.CAPACITY);
        TileSingularityTank.CAPACITY = clampedInt(cfg, CAT_TANK, "capacity", TileSingularityTank.CAPACITY, 512000, 1000000000,
                "Fluid the Singularity Tank holds, in mB. Range 512000-1000000000.");

        // Cross-checks between values that are each individually in range. A machine only starts a
        // tick of work when its buffer holds that tick's whole cost, and the worst case is the
        // base cost times the Machine Speed IV energy multiplier (60), times 1.25 more on the two
        // machines that take the level-4 Secondary Sieve. A buffer smaller than that means the
        // machine, once fitted with those augments, never starts at all - e.g. a Charger at the
        // maximum cost of 40,000 needs 2,400,000 against a default buffer of 2,000,000.
        TileAdvancedPulverizer.BASE_ENERGY_CAPACITY = ensureRunnable(CAT_PULVERIZER,
                TileAdvancedPulverizer.BASE_ENERGY_CAPACITY, TileAdvancedPulverizer.BASE_ENERGY_PER_TICK, WORST_COST_WITH_SIEVE);
        TileAdvancedFurnace.BASE_ENERGY_CAPACITY = ensureRunnable(CAT_FURNACE,
                TileAdvancedFurnace.BASE_ENERGY_CAPACITY, TileAdvancedFurnace.BASE_ENERGY_PER_TICK, WORST_COST_SPEED_ONLY);
        TileAdvancedSawmill.BASE_ENERGY_CAPACITY = ensureRunnable(CAT_SAWMILL,
                TileAdvancedSawmill.BASE_ENERGY_CAPACITY, TileAdvancedSawmill.BASE_ENERGY_PER_TICK, WORST_COST_WITH_SIEVE);
        TileAdvancedCharger.BASE_ENERGY_CAPACITY = ensureRunnable(CAT_CHARGER,
                TileAdvancedCharger.BASE_ENERGY_CAPACITY, TileAdvancedCharger.BASE_ENERGY_PER_TICK, WORST_COST_SPEED_ONLY);
        TileImprovedAssembler.ENERGY_CAPACITY = ensureRunnable(CAT_ASSEMBLER,
                TileImprovedAssembler.ENERGY_CAPACITY, TileImprovedAssembler.PROCESS_ENERGY, 1);
        TileSingularSmelter.BASE_ENERGY_CAPACITY = ensureRunnable(CAT_SMELTER,
                TileSingularSmelter.BASE_ENERGY_CAPACITY, TileSingularSmelter.BASE_ENERGY_PER_TICK, WORST_COST_WITH_SIEVE);
        TileSingularCrucible.BASE_ENERGY_CAPACITY = ensureRunnable(CAT_CRUCIBLE,
                TileSingularCrucible.BASE_ENERGY_CAPACITY, TileSingularCrucible.BASE_ENERGY_PER_TICK, WORST_COST_SPEED_ONLY);
        TileSingularTransposer.BASE_ENERGY_CAPACITY = ensureRunnable(CAT_TRANSPOSER,
                TileSingularTransposer.BASE_ENERGY_CAPACITY, TileSingularTransposer.BASE_ENERGY_PER_TICK, WORST_COST_WITH_SIEVE);
    }

    /** Machine Speed IV's energy multiplier. */
    private static final int WORST_COST_SPEED_ONLY = 60;
    /** Machine Speed IV (x60) plus the level-4 Secondary Sieve surcharge (x1.25). */
    private static final int WORST_COST_WITH_SIEVE = 75;

    private static int ensureRunnable(String category, int capacity, int cost, int worstMultiplier) {
        int required = cost * worstMultiplier;
        if (capacity >= required) {
            return capacity;
        }
        FMLLog.warning("[ThermalADD] %s.energyCapacity (%d) cannot hold a single tick of work at the most"
                        + " expensive augment setup (%d RF), so the machine would never start - raising it to %d.",
                category, Integer.valueOf(capacity), Integer.valueOf(required), Integer.valueOf(required));
        return required;
    }

    // ------------------------------------------------ server -> client sync

    /**
     * The values each machine's GUI, tooltips and client-side augment maths are computed from.
     * A client joining a server with a different config would otherwise show its OWN numbers - a
     * wrong capacity, a wrong RF/t, the Cell's light level computed against the wrong capacity - so
     * the server sends its values on login (MessageConfigSync) and the client puts its own back on
     * disconnect. Order is fixed and shared by snapshotMachineValues/applyMachineValues.
     */
    public static final int SYNCED_VALUE_COUNT = 25;

    private static int[] localMachineValues;
    private static long localCellCapacity;

    public static int[] snapshotMachineValues() {
        return new int[]{
                TileAdvancedPulverizer.BASE_ENERGY_CAPACITY, TileAdvancedPulverizer.ENERGY_RECEIVE_PER_TICK, TileAdvancedPulverizer.BASE_ENERGY_PER_TICK,
                TileAdvancedFurnace.BASE_ENERGY_CAPACITY, TileAdvancedFurnace.ENERGY_RECEIVE_PER_TICK, TileAdvancedFurnace.BASE_ENERGY_PER_TICK,
                TileAdvancedSawmill.BASE_ENERGY_CAPACITY, TileAdvancedSawmill.ENERGY_RECEIVE_PER_TICK, TileAdvancedSawmill.BASE_ENERGY_PER_TICK,
                TileAdvancedCharger.BASE_ENERGY_CAPACITY, TileAdvancedCharger.ENERGY_RECEIVE_PER_TICK, TileAdvancedCharger.BASE_ENERGY_PER_TICK,
                TileImprovedAssembler.ENERGY_CAPACITY, TileImprovedAssembler.ENERGY_RECEIVE_PER_TICK, TileImprovedAssembler.PROCESS_ENERGY,
                TileSingularSmelter.BASE_ENERGY_CAPACITY, TileSingularSmelter.ENERGY_RECEIVE_PER_TICK, TileSingularSmelter.BASE_ENERGY_PER_TICK,
                TileSingularCrucible.BASE_ENERGY_CAPACITY, TileSingularCrucible.ENERGY_RECEIVE_PER_TICK, TileSingularCrucible.BASE_ENERGY_PER_TICK,
                TileSingularTransposer.BASE_ENERGY_CAPACITY, TileSingularTransposer.ENERGY_RECEIVE_PER_TICK, TileSingularTransposer.BASE_ENERGY_PER_TICK,
                TileSingularityTank.CAPACITY
        };
    }

    /** Values come off the network, so a wrong-length array is ignored rather than trusted. */
    public static void applyMachineValues(int[] v, long cellCapacity) {
        if (v == null || v.length != SYNCED_VALUE_COUNT) {
            return;
        }
        TileAdvancedPulverizer.BASE_ENERGY_CAPACITY = v[0];
        TileAdvancedPulverizer.ENERGY_RECEIVE_PER_TICK = v[1];
        TileAdvancedPulverizer.BASE_ENERGY_PER_TICK = v[2];
        TileAdvancedFurnace.BASE_ENERGY_CAPACITY = v[3];
        TileAdvancedFurnace.ENERGY_RECEIVE_PER_TICK = v[4];
        TileAdvancedFurnace.BASE_ENERGY_PER_TICK = v[5];
        TileAdvancedSawmill.BASE_ENERGY_CAPACITY = v[6];
        TileAdvancedSawmill.ENERGY_RECEIVE_PER_TICK = v[7];
        TileAdvancedSawmill.BASE_ENERGY_PER_TICK = v[8];
        TileAdvancedCharger.BASE_ENERGY_CAPACITY = v[9];
        TileAdvancedCharger.ENERGY_RECEIVE_PER_TICK = v[10];
        TileAdvancedCharger.BASE_ENERGY_PER_TICK = v[11];
        TileImprovedAssembler.ENERGY_CAPACITY = v[12];
        TileImprovedAssembler.ENERGY_RECEIVE_PER_TICK = v[13];
        TileImprovedAssembler.PROCESS_ENERGY = v[14];
        TileSingularSmelter.BASE_ENERGY_CAPACITY = v[15];
        TileSingularSmelter.ENERGY_RECEIVE_PER_TICK = v[16];
        TileSingularSmelter.BASE_ENERGY_PER_TICK = v[17];
        TileSingularCrucible.BASE_ENERGY_CAPACITY = v[18];
        TileSingularCrucible.ENERGY_RECEIVE_PER_TICK = v[19];
        TileSingularCrucible.BASE_ENERGY_PER_TICK = v[20];
        TileSingularTransposer.BASE_ENERGY_CAPACITY = v[21];
        TileSingularTransposer.ENERGY_RECEIVE_PER_TICK = v[22];
        TileSingularTransposer.BASE_ENERGY_PER_TICK = v[23];
        TileSingularityTank.CAPACITY = v[24];
        if (cellCapacity > 0L) {
            TileSingularityCell.CAPACITY = cellCapacity;
        }
    }

    /** Puts this client's own config back after leaving a server that sent different values. */
    public static void restoreLocalValues() {
        if (localMachineValues != null) {
            applyMachineValues(localMachineValues, localCellCapacity);
        }
    }

    private static int capacity(Configuration cfg, String category, int def, int max) {
        return clampedInt(cfg, category, "energyCapacity", def, MIN_CAPACITY, max,
                "Internal RF buffer of the machine. Range " + MIN_CAPACITY + "-" + max + ".");
    }

    private static int transfer(Configuration cfg, String category, int def) {
        return clampedInt(cfg, category, "energyReceiveRate", def, MIN_TRANSFER, MAX_TRANSFER,
                "Maximum RF/t the machine accepts from adjacent conduits. Range " + MIN_TRANSFER + "-" + MAX_TRANSFER + ".");
    }

    private static int process(Configuration cfg, String category, int def, int max) {
        return clampedInt(cfg, category, "processEnergyPerTick", def, 1, max,
                "Base RF/t one processing line draws, before augments. Raise it to make the machine"
                        + " more expensive to run, lower it to make it cheaper. Range 1-" + max + ".");
    }

    private static int clampedInt(Configuration cfg, String category, String key, int def, int min, int max, String comment) {
        int value = cfg.get(category, key, def, comment).getInt(def);
        if (value < min || value > max) {
            int clamped = value < min ? min : max;
            FMLLog.warning("[ThermalADD] Config value %s.%s is %d, outside the supported range %d-%d - using %d instead.",
                    category, key, Integer.valueOf(value), Integer.valueOf(min), Integer.valueOf(max), Integer.valueOf(clamped));
            return clamped;
        }
        return value;
    }

    /**
     * Read as a string rather than an int: the Cell's capacity is a long (a trillion RF by
     * default), far past what Forge's own integer config property can represent.
     */
    private static long cellCapacity(Configuration cfg, long def) {
        String raw = cfg.get(CAT_CELL, "capacity", String.valueOf(def),
                "Total RF the Singularity Cell holds. Written as a plain number of RF; range "
                        + MIN_CELL_CAPACITY + "-" + MAX_CELL_CAPACITY + ".").getString();
        long value;
        try {
            value = Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            FMLLog.warning("[ThermalADD] Config value %s.capacity is not a number (%s) - using %d instead.",
                    CAT_CELL, raw, Long.valueOf(def));
            return def;
        }
        if (value < MIN_CELL_CAPACITY || value > MAX_CELL_CAPACITY) {
            long clamped = value < MIN_CELL_CAPACITY ? MIN_CELL_CAPACITY : MAX_CELL_CAPACITY;
            FMLLog.warning("[ThermalADD] Config value %s.capacity is %d, outside the supported range %d-%d - using %d instead.",
                    CAT_CELL, Long.valueOf(value), Long.valueOf(MIN_CELL_CAPACITY),
                    Long.valueOf(MAX_CELL_CAPACITY), Long.valueOf(clamped));
            return clamped;
        }
        return value;
    }

    /**
     * The machine GUIs mark each slot's role with a coloured ring, which is no help at all to a
     * player who cannot tell those colours apart. Real Thermal Expansion answers the same problem
     * by swapping its Slots.png for a SlotsCB.png; this mod draws its rings in code, so what
     * changes here is the palette itself - to the Okabe-Ito set, chosen specifically so the
     * colours stay distinguishable under the common forms of colour blindness.
     */
    private static void loadGui(Configuration cfg) {
        // Only the flag is read here. ModConfig runs in preInit on the dedicated server too, so it
        // must never touch TabbedMachineGui - that class extends GuiContainer and does not exist
        // server-side. The GUI reads this flag itself when it first loads, which is client-only.
        colorBlindPalette = cfg.get(CAT_GUI, "colorBlindPalette", false,
                "Draw the slot role rings in a colour-blind-safe palette (Okabe-Ito) instead of"
                        + " the Thermal Expansion colours.").getBoolean(false);
    }

    private static void loadRecipes(Configuration cfg) {
        cfg.addCustomCategoryComment(CAT_RECIPES,
                "Turn an individual crafting recipe off. The block or item itself stays registered,"
                        + " so existing worlds and creative-mode/command access are unaffected.");
        recipeAdvancedPulverizer = recipe(cfg, "advancedPulverizer");
        recipeAdvancedFurnace = recipe(cfg, "advancedFurnace");
        recipeAdvancedSawmill = recipe(cfg, "advancedSawmill");
        recipeAdvancedCharger = recipe(cfg, "advancedCharger");
        recipeImprovedAssembler = recipe(cfg, "improvedAssembler");
        recipeSingularSmelter = recipe(cfg, "singularSmelter");
        recipeSingularCrucible = recipe(cfg, "singularCrucible");
        recipeSingularTransposer = recipe(cfg, "singularTransposer");
        recipeSingularityTank = recipe(cfg, "singularityTank");
        recipeSingularityStrongbox = recipe(cfg, "singularityStrongbox");
        recipeSingularityCell = recipe(cfg, "singularityCell");
        recipeSingularityGear = recipe(cfg, "singularityGear");
        recipeSingularityFrame = recipe(cfg, "singularityFrame");
        recipeSpeedLevel4Augment = recipe(cfg, "speedLevel4Augment");
        recipeSecondarySieve4Augment = recipe(cfg, "secondarySieve4Augment");
    }

    private static boolean recipe(Configuration cfg, String key) {
        return cfg.get(CAT_RECIPES, key, true).getBoolean(true);
    }
}
