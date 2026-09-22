package net.thermaladd.mod.init;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;

import cofh.thermalexpansion.block.TEBlocks;
import cofh.thermalexpansion.block.cell.BlockCell;
import cofh.thermalexpansion.block.machine.BlockMachine;
import cofh.thermalexpansion.block.simple.BlockFrame;
import cofh.thermalexpansion.item.TEAugments;
import cofh.thermalexpansion.item.TEItems;
import cofh.thermalfoundation.item.TFItems;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Recipe registration for both machines in this mod. Runs from {@code ThermalADD#postInit},
 * i.e. strictly after {@code TEBlocks.initialize()}/{@code TEAugments.initialize()} have run
 * (guaranteed by the {@code required-after} mod dependency declared on the @Mod annotation),
 * so {@link TEBlocks#blockMachine} and the augment ItemStack fields are already populated.
 *
 * The Improved Cyclic Assembler's recipe used to look these up defensively via
 * GameRegistry.findBlock/findItem (soft dependency - it used to be a standalone mod that
 * could run without Thermal Expansion installed at all, just silently skipping its recipe).
 * Now that it is merged into ThermalADD, which hard-depends on Thermal Expansion for the
 * Advanced Pulverizer anyway, that defensive lookup is no longer necessary.
 */
public class ModRecipes {

    private ModRecipes() {
    }

    /**
     * ThermalADD#postInit already wraps this whole call in one outer try/catch, but that alone
     * means a single bad ingredient reference (an augment array index that turns out to never
     * actually get populated, say) throws out of whichever registerXRecipe() hit it and silently
     * takes every recipe registered AFTER it down too - discovered the hard way while adding the
     * Charger's own recipe. Each recipe now gets its own try/catch instead, so one broken recipe
     * only ever costs itself.
     */
    public static void register() {
        registerSafely("Advanced Pulverizer", new RecipeRegistration() {
            public void register() {
                registerAdvancedPulverizerRecipe();
            }
        });
        registerSafely("Improved Assembler", new RecipeRegistration() {
            public void register() {
                registerImprovedAssemblerRecipe();
            }
        });
        registerSafely("Advanced Furnace", new RecipeRegistration() {
            public void register() {
                registerAdvancedFurnaceRecipe();
            }
        });
        registerSafely("Advanced Sawmill", new RecipeRegistration() {
            public void register() {
                registerAdvancedSawmillRecipe();
            }
        });
        registerSafely("Advanced Charger", new RecipeRegistration() {
            public void register() {
                registerAdvancedChargerRecipe();
            }
        });
        registerSafely("Speed Level 4 augment", new RecipeRegistration() {
            public void register() {
                registerSpeedLevel4AugmentRecipe();
            }
        });
        registerSafely("Secondary Sieve 4 augment", new RecipeRegistration() {
            public void register() {
                registerSecondarySieve4AugmentRecipe();
            }
        });
        registerSafely("Singularity Cell", new RecipeRegistration() {
            public void register() {
                registerSingularityCellRecipe();
            }
        });
        registerSafely("Singularity Gear", new RecipeRegistration() {
            public void register() {
                registerSingularityGearRecipe();
            }
        });
        registerSafely("Singularity Frame", new RecipeRegistration() {
            public void register() {
                registerSingularityFrameRecipe();
            }
        });
    }

    private interface RecipeRegistration {
        void register();
    }

    private static void registerSafely(String name, RecipeRegistration registration) {
        try {
            registration.register();
        } catch (Throwable t) {
            FMLLog.severe("[ThermalADD] Failed to register the %s recipe: %s", name, t);
        }
    }

    /**
     * Rebuilt around the Singularity Frame + Singularity Gear as the shared "base" the user
     * asked for, replacing the old Invar/Platinum/redstone-dust recipe (which used plain mined
     * materials). Enderium ingots at the corners and a pair of Machine Speed II augments
     * (crafted, consumed/upgraded like all this mod's other augment-consuming recipes) theme
     * this one around raw processing speed - nothing here is a raw ore/dust/gem, every
     * ingredient is itself a crafted item (Enderium is TE's own multi-step alloy, never mined).
     */
    private static void registerAdvancedPulverizerRecipe() {
        // The real Thermal Expansion Pulverizer - metadata is its BlockMachine.Types ordinal.
        ItemStack pulverizer = new ItemStack(TEBlocks.blockMachine, 1, BlockMachine.Types.PULVERIZER.ordinal());

        // E G E      E = Enderium ingot, G = Singularity Gear
        // A P A      A = Machine Speed II augment (crafted, consumed), P = real Pulverizer
        // E F E      F = Singularity Frame
        GameRegistry.addRecipe(new ShapedOreRecipe(
                new ItemStack(ModBlocks.advancedPulverizer),
                "EGE",
                "APA",
                "EFE",
                'E', "ingotEnderium",
                'G', ModItems.singularityGear,
                'A', TEAugments.machineSpeed[1],
                'P', pulverizer,
                'F', ModBlocks.singularityFrame));
    }

    /**
     * Same Singularity Frame + Singularity Gear base as the other two, but themed and shaped
     * differently: Lumium ingots instead of Enderium, and the assembler's own real Auto
     * Input/Output augments (crafted) as the flavor ingredients instead of a speed augment -
     * doubles up on both the Gear and the Frame (2 of each) to mark it as the priciest of the
     * three, reflecting the Assembler's more complex automation role. Every ingredient is a
     * crafted item - Lumium is TE's own multi-step alloy, never mined directly.
     */
    private static void registerImprovedAssemblerRecipe() {
        ItemStack cyclicAssembler = new ItemStack(TEBlocks.blockMachine, 1, BlockMachine.Types.ASSEMBLER.ordinal());

        // L O L      L = Lumium ingot, O = Auto Output augment (crafted)
        // G C G      G = Singularity Gear, C = real Cyclic Assembler
        // F I F      F = Singularity Frame, I = Auto Input augment (crafted)
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModBlocks.improvedAssembler),
                "LOL",
                "GCG",
                "FIF",
                'L', "ingotLumium",
                'O', TEAugments.generalAutoOutput,
                'G', ModItems.singularityGear,
                'C', cyclicAssembler,
                'F', ModBlocks.singularityFrame,
                'I', TEAugments.generalAutoInput));
    }

    /**
     * Third variant of the same Singularity Frame + Singularity Gear base: Signalum ingots for
     * the corners and a pair of crafted Gold Power Coils (TE's own wound/crafted component,
     * never a raw material) for the heat/power theme, replacing the old Sulfur-dust "fire"
     * flavor now that raw dusts are off the table. Single Gear + single Frame like the
     * Pulverizer's recipe, but a different shape/ingredient set entirely.
     */
    private static void registerAdvancedFurnaceRecipe() {
        ItemStack furnace = new ItemStack(TEBlocks.blockMachine, 1, BlockMachine.Types.FURNACE.ordinal());

        // S G S      S = Signalum ingot, G = Singularity Gear
        // P C P      P = Gold Power Coil (crafted), C = real Furnace
        // S F S      F = Singularity Frame
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModBlocks.advancedFurnace),
                "SGS",
                "PCP",
                "SFS",
                'S', "ingotSignalum",
                'G', ModItems.singularityGear,
                'P', TEItems.powerCoilGold,
                'C', furnace,
                'F', ModBlocks.singularityFrame));
    }

    /**
     * Fourth variant of the same Singularity Frame + Singularity Gear base: Invar ingots (a
     * base-tier crafted TE alloy, never used by the other 3 recipes) and a pair of real
     * Reconfigurable Sides augments (crafted, consumed/upgraded like every other augment
     * ingredient in this class) as the flavor - the one general-purpose augment none of the
     * other 3 machine recipes happened to consume yet. Single Gear + single Frame, same shape
     * as the Pulverizer's own recipe. Every ingredient is a crafted item - Invar is smelted from
     * iron + nickel dust via a real Induction Smelter, never mined directly.
     */
    private static void registerAdvancedSawmillRecipe() {
        ItemStack sawmill = new ItemStack(TEBlocks.blockMachine, 1, BlockMachine.Types.SAWMILL.ordinal());

        // I R I      I = Invar ingot, R = Reconfigurable Sides augment (crafted)
        // G S G      G = Singularity Gear, S = real Sawmill
        // I F I      F = Singularity Frame
        GameRegistry.addRecipe(new ShapedOreRecipe(
                new ItemStack(ModBlocks.advancedSawmill),
                "IRI",
                "GSG",
                "IFI",
                'I', "ingotInvar",
                'R', TEAugments.generalReconfigSides,
                'G', ModItems.singularityGear,
                'S', sawmill,
                'F', ModBlocks.singularityFrame));
    }

    /**
     * Fifth variant of the same Singularity Frame + Singularity Gear base: Electrum ingots (a
     * base-tier crafted TE alloy, never used by the other 4 recipes) and a real Machine Speed I
     * augment (crafted, consumed/upgraded like every other augment ingredient in this class) -
     * {@code TEAugments.machineChargerBoost} would have been the perfectly on-theme choice here
     * (real TE's own Charger-specific augment type), but it turns out to be declared and
     * allocated in the decompiled {@code TEAugments} without ever actually being populated by a
     * level loop the way {@code machineSpeed}/{@code machineSecondary} are - every index is
     * {@code null} at runtime, which would have made {@code ShapedOreRecipe}'s constructor throw
     * immediately and (since {@code ModRecipes#register()} has no per-recipe try/catch) take
     * every recipe registered after this one down with it. {@code machineSpeed[0]} is a
     * confirmed-populated, already-proven-safe substitute (levels 1/2 are already this mod's own
     * Pulverizer/Speed-Level-4 recipe ingredients respectively - level 0 was still unused). A
     * Reinforced Capacitor (crafted, one tier below the Resonant Capacitor the Singularity
     * Cell/Secondary Sieve 4 recipes already use) rounds out the energy theme. Every ingredient
     * is a crafted item - Electrum is TE's own multi-step alloy, the Capacitor and augment are
     * themselves upgrade-crafted from lower tiers - nothing raw.
     */
    private static void registerAdvancedChargerRecipe() {
        ItemStack charger = new ItemStack(TEBlocks.blockMachine, 1, BlockMachine.Types.CHARGER.ordinal());

        // E B E      E = Electrum ingot, B = Machine Speed I augment (real TE, crafted)
        // G C G      G = Singularity Gear, C = real Charger
        // K F K      K = Reinforced Capacitor (crafted), F = Singularity Frame
        GameRegistry.addRecipe(new ShapedOreRecipe(
                new ItemStack(ModBlocks.advancedCharger),
                "EBE",
                "GCG",
                "KFK",
                'E', "ingotElectrum",
                'B', TEAugments.machineSpeed[0],
                'G', ModItems.singularityGear,
                'C', charger,
                'K', TEItems.capacitorReinforced,
                'F', ModBlocks.singularityFrame));
    }

    /**
     * ThermalADD's own "beyond spec" Machine Speed augment (see ModAugments) - now based on the
     * Singularity Gear as instructed, alongside its previous real Thermal Expansion tier
     * (machineSpeed[2], "Пространственно-временной унификатор флакса", consumed as the
     * centerpiece), replacing the old Platinum/Diamond/redstone-dust recipe (all raw/mined
     * materials, no longer allowed). Signalum ingots and a pair of crafted Gold Power Coils mark
     * it as a genuine endgame item - every ingredient here is itself crafted from something else:
     * Signalum is TE's own multi-step alloy, the Power Coil is a wound/crafted TE component,
     * Hardened Glass is Induction-Smelter-crafted, and the Gear/augment are this mod's/TE's own
     * crafted items - nothing raw.
     */
    private static void registerSpeedLevel4AugmentRecipe() {
        // S G S      S = Signalum ingot, G = Singularity Gear
        // H A H      H = Hardened Glass, A = real Machine Speed III augment (consumed/upgraded)
        // S P S      P = Gold Power Coil (crafted)
        GameRegistry.addRecipe(new ShapedOreRecipe(
                ModAugments.speedLevel4,
                "SGS",
                "HAH",
                "SPS",
                'S', "ingotSignalum",
                'G', ModItems.singularityGear,
                'H', "blockGlassHardened",
                'A', TEAugments.machineSpeed[2],
                'P', TEItems.powerCoilGold));
    }

    /**
     * ThermalADD's own "beyond spec" Machine Secondary (sieve) tier - same Singularity Gear +
     * previous-tier-augment base as the Speed Level 4 recipe above, themed with Enderium instead
     * of Signalum and a Resonant Capacitor instead of a Power Coil so the two endgame augments
     * don't share an identical shopping list: consumes real Thermal Expansion's own top sieve
     * tier (machineSecondary[2], "Отклик Гиросервомеханизма") as the centerpiece. Every
     * ingredient is crafted - Enderium is TE's own multi-step alloy, the Resonant Capacitor is
     * itself upgrade-crafted from a Reinforced Capacitor + Enderium - nothing raw.
     */
    private static void registerSecondarySieve4AugmentRecipe() {
        // E G E      E = Enderium ingot, G = Singularity Gear
        // H B H      H = Hardened Glass, B = real Machine Secondary III augment (consumed/upgraded)
        // E K E      K = Resonant Capacitor (crafted)
        GameRegistry.addRecipe(new ShapedOreRecipe(
                ModAugments.secondarySieve4,
                "EGE",
                "HBH",
                "EKE",
                'E', "ingotEnderium",
                'G', ModItems.singularityGear,
                'H', "blockGlassHardened",
                'B', TEAugments.machineSecondary[2],
                'K', TEItems.capacitorResonant));
    }

    /**
     * Singularity Energy Cell (see net.thermaladd.mod.tileentity.TileSingularityCell) -
     * "beyond spec" past real TE's own Resonant Energy Cell (BlockCell.Types.RESONANT), 3 of
     * which sit at its base as instructed. Every other ingredient is itself a crafted item, not
     * a raw material: Enderium/Signalum are TE's own multi-step smelted alloys (never mined
     * directly), the Resonant Capacitor is itself upgrade-crafted from Reinforced Capacitor +
     * Enderium, and the Power Coil is a wound/crafted TE component - nothing here is a plain
     * ingot, dust or gem straight from ore.
     */
    private static void registerSingularityCellRecipe() {
        ItemStack resonantCell = new ItemStack(TEBlocks.blockCell, 1, BlockCell.Types.RESONANT.ordinal());

        // N C N      N = Enderium ingot, C = Resonant Energy Cell
        // C R C      R = Resonant Capacitor (TE's own crafted energy-tier upgrade item)
        // S P S      S = Signalum ingot, P = Gold Power Coil (crafted)
        GameRegistry.addRecipe(new ShapedOreRecipe(
                new ItemStack(ModBlocks.singularityCell),
                "NCN",
                "CRC",
                "SPS",
                'N', "ingotEnderium",
                'C', resonantCell,
                'R', TEItems.capacitorResonant,
                'S', "ingotSignalum",
                'P', TEItems.powerCoilGold));
    }

    /**
     * Singularity Gear (see net.thermaladd.mod.item.ItemSingularityGear) - a real Enderium
     * Gear (ThermalFoundation's own TFItems.gearEnderium) as the base, as instructed, fused
     * with Thermal Expansion's other two top-tier alloy gears (Signalum/Lumium) around it.
     * Every ingredient is itself a gear crafted from an already multi-step-smelted TE alloy
     * ingot - nothing here is a raw ore, ingot or dust.
     */
    private static void registerSingularityGearRecipe() {
        // L S L      L = Lumium Gear, S = Signalum Gear
        // S G S      G = real Enderium Gear (centerpiece, as instructed)
        // L S L
        GameRegistry.addRecipe(new ShapedOreRecipe(
                new ItemStack(ModItems.singularityGear),
                "LSL",
                "SGS",
                "LSL",
                'L', TFItems.gearLumium,
                'S', TFItems.gearSignalum,
                'G', TFItems.gearEnderium));
    }

    /**
     * Singularity Machine Frame (see net.thermaladd.mod.block.BlockSingularityFrame) - a real
     * Resonant Machine Frame plus a Singularity Gear as the two centerpiece ingredients, as
     * instructed. Shaped after real TE's own frameMachineResonant recipe (ingots at the
     * corners/edges around the tier below and a gear on top), but with the raw ingotSilver that
     * recipe uses swapped for Enderium/Signalum ingots - both themselves multi-step smelted TE
     * alloys, never mined directly - so every ingredient here is a crafted item.
     */
    private static void registerSingularityFrameRecipe() {
        ItemStack resonantFrame = new ItemStack(TEBlocks.blockFrame, 1, BlockFrame.Types.MACHINE_RESONANT.ordinal());

        // N G N      N = Enderium ingot, G = Singularity Gear
        // S F S      S = Signalum ingot, F = real Resonant Machine Frame (consumed/upgraded)
        // N S N
        GameRegistry.addRecipe(new ShapedOreRecipe(
                new ItemStack(ModBlocks.singularityFrame),
                "NGN",
                "SFS",
                "NSN",
                'N', "ingotEnderium",
                'G', ModItems.singularityGear,
                'S', "ingotSignalum",
                'F', resonantFrame));
    }
}
