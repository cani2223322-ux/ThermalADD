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
    private static final String CAT_RECIPES = "recipes";

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
    /** The Charger's progress is scaled by its own ENERGY_SYNC_SCALE of 1024, so it has far more headroom. */
    private static final int MAX_PROCESS_ENERGY_CHARGER = 40000;
    /** The Assembler has no accumulating progress at all - a craft completes within one tick. */
    private static final int MAX_PROCESS_ENERGY_ASSEMBLER = 1000;

    private static final int MIN_CAPACITY = 10000;
    private static final int MIN_TRANSFER = 100;
    private static final int MAX_TRANSFER = 10000000;

    /** The Cell syncs through its own packet using 64-bit longs, so it has no short-sized ceiling. */
    private static final long MIN_CELL_CAPACITY = 1000000L;
    private static final long MAX_CELL_CAPACITY = 1000000000000000L;

    public static boolean recipeAdvancedPulverizer = true;
    public static boolean recipeAdvancedFurnace = true;
    public static boolean recipeAdvancedSawmill = true;
    public static boolean recipeAdvancedCharger = true;
    public static boolean recipeImprovedAssembler = true;
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
        } catch (Throwable t) {
            // A broken config must never stop the mod from loading - every field keeps whatever
            // default it was initialized with, which is exactly the shipped balance.
            FMLLog.severe("[ThermalADD] Failed to read the config, using defaults: %s", t);
        } finally {
            if (cfg.hasChanged()) {
                cfg.save();
            }
        }
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

        TileSingularityCell.CAPACITY = cellCapacity(cfg, TileSingularityCell.CAPACITY);
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

    private static void loadRecipes(Configuration cfg) {
        cfg.addCustomCategoryComment(CAT_RECIPES,
                "Turn an individual crafting recipe off. The block or item itself stays registered,"
                        + " so existing worlds and creative-mode/command access are unaffected.");
        recipeAdvancedPulverizer = recipe(cfg, "advancedPulverizer");
        recipeAdvancedFurnace = recipe(cfg, "advancedFurnace");
        recipeAdvancedSawmill = recipe(cfg, "advancedSawmill");
        recipeAdvancedCharger = recipe(cfg, "advancedCharger");
        recipeImprovedAssembler = recipe(cfg, "improvedAssembler");
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
