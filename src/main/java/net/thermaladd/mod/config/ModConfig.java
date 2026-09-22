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
 * WHY EVERY VALUE IS CLAMPED: the machine GUIs sync their energy/progress/RF-per-tick readouts
 * through vanilla's Container#sendProgressBarUpdate, which serializes as a SIGNED 16-BIT SHORT
 * (max 32767). Each machine divides its large values by its own ENERGY_SYNC_SCALE/RATE_SYNC_SCALE
 * before sending and multiplies back on receive, so the real ceiling for a given value is
 * {@code 32767 * thatScale}, divided by whatever the augments can multiply it by. A config value
 * past that point would silently wrap and show garbage (or negative) numbers in the GUI, so the
 * maxima below are derived from those limits rather than picked for flavor. Each one is noted
 * with the arithmetic that produced it.
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
     * Energy buffer ceiling for the four machines that have an Energy Storage augment.
     * That augment multiplies the buffer by up to 8, and the result is sent as
     * {@code capacity * 8 / ENERGY_SYNC_SCALE}, which must stay under 32767:
     * Pulverizer/Furnace/Sawmill use scale 256 -> 32767 * 256 / 8 = 1,048,544, rounded down.
     */
    private static final int MAX_CAPACITY_SCALE_256 = 1048000;
    /** Same arithmetic for the Charger, which uses ENERGY_SYNC_SCALE 1024: 32767 * 1024 / 8 = 4,194,176. */
    private static final int MAX_CAPACITY_CHARGER = 4194000;
    /** The Assembler has no Energy Storage augment, so its buffer is sent unmultiplied: 32767 * 256 = 8,388,352. */
    private static final int MAX_CAPACITY_ASSEMBLER = 8388000;

    /**
     * RF/t ceiling for the three "3 parallel lines" machines. Worst case sent to the GUI is
     * {@code 3 lines * cost * 60 (max Machine Speed energy multiplier) * 1.25 (level-4 Secondary
     * Sieve surcharge) / RATE_SYNC_SCALE}; with RATE_SYNC_SCALE 8 that is
     * {@code 3 * 1000 * 75 / 8 = 28,125}, safely under 32767. The same limit also keeps a single
     * progress step ({@code cost * 10}) from overflowing the raw progress sync.
     */
    private static final int MAX_PROCESS_ENERGY_3LINE = 1000;
    /**
     * Charger worst case is {@code 9 lines * cost * 60 / ENERGY_SYNC_SCALE (1024)}; at 40,000 that
     * is {@code 9 * 40000 * 60 / 1024 = 21,093}, under 32767.
     */
    private static final int MAX_PROCESS_ENERGY_CHARGER = 40000;
    /** Assembler worst case is {@code 6 lines * cost} sent raw: 6 * 1000 = 6,000, well under 32767. */
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
                TileAdvancedPulverizer.BASE_ENERGY_CAPACITY, MAX_CAPACITY_SCALE_256);
        TileAdvancedPulverizer.ENERGY_RECEIVE_PER_TICK = transfer(cfg, CAT_PULVERIZER,
                TileAdvancedPulverizer.ENERGY_RECEIVE_PER_TICK);
        TileAdvancedPulverizer.BASE_ENERGY_PER_TICK = process(cfg, CAT_PULVERIZER,
                TileAdvancedPulverizer.BASE_ENERGY_PER_TICK, MAX_PROCESS_ENERGY_3LINE);

        TileAdvancedFurnace.BASE_ENERGY_CAPACITY = capacity(cfg, CAT_FURNACE,
                TileAdvancedFurnace.BASE_ENERGY_CAPACITY, MAX_CAPACITY_SCALE_256);
        TileAdvancedFurnace.ENERGY_RECEIVE_PER_TICK = transfer(cfg, CAT_FURNACE,
                TileAdvancedFurnace.ENERGY_RECEIVE_PER_TICK);
        TileAdvancedFurnace.BASE_ENERGY_PER_TICK = process(cfg, CAT_FURNACE,
                TileAdvancedFurnace.BASE_ENERGY_PER_TICK, MAX_PROCESS_ENERGY_3LINE);

        TileAdvancedSawmill.BASE_ENERGY_CAPACITY = capacity(cfg, CAT_SAWMILL,
                TileAdvancedSawmill.BASE_ENERGY_CAPACITY, MAX_CAPACITY_SCALE_256);
        TileAdvancedSawmill.ENERGY_RECEIVE_PER_TICK = transfer(cfg, CAT_SAWMILL,
                TileAdvancedSawmill.ENERGY_RECEIVE_PER_TICK);
        TileAdvancedSawmill.BASE_ENERGY_PER_TICK = process(cfg, CAT_SAWMILL,
                TileAdvancedSawmill.BASE_ENERGY_PER_TICK, MAX_PROCESS_ENERGY_3LINE);

        TileAdvancedCharger.BASE_ENERGY_CAPACITY = capacity(cfg, CAT_CHARGER,
                TileAdvancedCharger.BASE_ENERGY_CAPACITY, MAX_CAPACITY_CHARGER);
        TileAdvancedCharger.ENERGY_RECEIVE_PER_TICK = transfer(cfg, CAT_CHARGER,
                TileAdvancedCharger.ENERGY_RECEIVE_PER_TICK);
        TileAdvancedCharger.BASE_ENERGY_PER_TICK = process(cfg, CAT_CHARGER,
                TileAdvancedCharger.BASE_ENERGY_PER_TICK, MAX_PROCESS_ENERGY_CHARGER);

        TileImprovedAssembler.ENERGY_CAPACITY = capacity(cfg, CAT_ASSEMBLER,
                TileImprovedAssembler.ENERGY_CAPACITY, MAX_CAPACITY_ASSEMBLER);
        TileImprovedAssembler.ENERGY_RECEIVE_PER_TICK = transfer(cfg, CAT_ASSEMBLER,
                TileImprovedAssembler.ENERGY_RECEIVE_PER_TICK);
        TileImprovedAssembler.PROCESS_ENERGY = process(cfg, CAT_ASSEMBLER,
                TileImprovedAssembler.PROCESS_ENERGY, MAX_PROCESS_ENERGY_ASSEMBLER);

        TileSingularityCell.CAPACITY = cellCapacity(cfg, TileSingularityCell.CAPACITY);
    }

    private static int capacity(Configuration cfg, String category, int def, int max) {
        return clampedInt(cfg, category, "energyCapacity", def, MIN_CAPACITY, max,
                "Internal RF buffer of the machine. Range " + MIN_CAPACITY + "-" + max
                        + "; the upper bound is what the vanilla GUI sync can carry, not a balance choice.");
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
