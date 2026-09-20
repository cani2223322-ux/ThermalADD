package net.thermaladd.mod.init;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;

import cofh.thermalexpansion.block.TEBlocks;
import cofh.thermalexpansion.block.cell.BlockCell;
import cofh.thermalexpansion.block.machine.BlockMachine;
import cofh.thermalexpansion.block.simple.BlockFrame;
import cofh.thermalexpansion.item.TEAugments;
import cofh.thermalexpansion.item.TEItems;
import cofh.thermalfoundation.item.TFItems;
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

    public static void register() {
        registerAdvancedPulverizerRecipe();
        registerImprovedAssemblerRecipe();
        registerAdvancedFurnaceRecipe();
        registerSpeedLevel4AugmentRecipe();
        registerSecondarySieve4AugmentRecipe();
        registerSingularityCellRecipe();
        registerSingularityGearRecipe();
        registerSingularityFrameRecipe();
    }

    /**
     * Now noticeably pricier than the first beta's recipe: a Machine Speed augment (I) is
     * consumed into the frame itself (justifying the "faster than stock" claim), and half the
     * Invar is swapped for Platinum - Thermal Foundation's rarest base-tier ingot - to reflect
     * the UltimateResonant tier's higher material cost.
     */
    private static void registerAdvancedPulverizerRecipe() {
        // The real Thermal Expansion Pulverizer - metadata is its BlockMachine.Types ordinal.
        ItemStack pulverizer = new ItemStack(TEBlocks.blockMachine, 1, BlockMachine.Types.PULVERIZER.ordinal());

        // I S I      I = Invar ingot, S = Machine Speed I augment (consumed into the frame)
        // G P G      G = Hardened Glass, P = real Pulverizer
        // N D N      N = Platinum ingot (rarer/pricier than plain Invar), D = Redstone dust
        GameRegistry.addRecipe(new ShapedOreRecipe(
                new ItemStack(ModBlocks.advancedPulverizer),
                "ISI",
                "GPG",
                "NDN",
                'I', "ingotInvar",
                'S', TEAugments.machineSpeed[0],
                'G', "blockGlassHardened",
                'P', pulverizer,
                'N', "ingotPlatinum",
                'D', "dustRedstone"));
    }

    /**
     * Crafted from a real Thermal Expansion Cyclic Assembler (there is no "Resonant" tier
     * in this TE version - TE4 processing machines aren't tiered at all) plus the 3 real TE
     * "General" augments this block supports, invar and hardened glass for the frame, and
     * redstone to power it. Now also requires Platinum, on top of everything the original
     * beta recipe needed, to match the UltimateResonant tier's higher material cost.
     */
    private static void registerImprovedAssemblerRecipe() {
        ItemStack cyclicAssembler = new ItemStack(TEBlocks.blockMachine, 1, BlockMachine.Types.ASSEMBLER.ordinal());

        // N A I      N = Platinum ingot, A = Auto Output augment, I = Invar ingot
        // B C D      B = Auto Input augment, C = real Cyclic Assembler, D = Reconfig Sides augment
        // G R G      G = Hardened Glass, R = Redstone dust
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModBlocks.improvedAssembler),
                "NAI",
                "BCD",
                "GRG",
                'N', "ingotPlatinum",
                'A', TEAugments.generalAutoOutput,
                'I', "ingotInvar",
                'B', TEAugments.generalAutoInput,
                'C', cyclicAssembler,
                'D', TEAugments.generalReconfigSides,
                'G', "blockGlassHardened",
                'R', "dustRedstone"));
    }

    /**
     * Own design (no real Thermal Expansion equivalent recipe to copy from, since TE's own
     * Furnace is unlocked from the start): a real TE Furnace at the core, Sulfur dust for the
     * heat/fire theme, Hardened Glass and Invar for the frame like the other two machines, a
     * Machine Speed I augment consumed into the build, and Platinum for the UltimateResonant
     * tier's material cost.
     */
    private static void registerAdvancedFurnaceRecipe() {
        ItemStack furnace = new ItemStack(TEBlocks.blockMachine, 1, BlockMachine.Types.FURNACE.ordinal());

        // N D N      N = Platinum ingot, D = Sulfur dust (heat/fire theme)
        // G F G      G = Hardened Glass, F = real Furnace
        // I A I      I = Invar ingot, A = Machine Speed I augment (consumed into the frame)
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModBlocks.advancedFurnace),
                "NDN",
                "GFG",
                "IAI",
                'N', "ingotPlatinum",
                'D', "dustSulfur",
                'G', "blockGlassHardened",
                'F', furnace,
                'I', "ingotInvar",
                'A', TEAugments.machineSpeed[0]));
    }

    /**
     * ThermalADD's own "beyond spec" Machine Speed augment (see ModAugments) - crafted by
     * pushing real Thermal Expansion's own top speed tier (machineSpeed[2], "Пространственно-
     * временной унификатор флакса") past its limits, consuming it as the centerpiece ingredient
     * rather than a base material. Platinum + Diamond either side mark it as a genuine endgame
     * item, well past what this mod's own machine recipes cost.
     */
    private static void registerSpeedLevel4AugmentRecipe() {
        // P D P      P = Platinum ingot, D = Diamond
        // G S G      G = Hardened Glass, S = real Machine Speed III augment (consumed/upgraded)
        // P R P      R = Redstone dust
        GameRegistry.addRecipe(new ShapedOreRecipe(
                ModAugments.speedLevel4,
                "PDP",
                "GSG",
                "PRP",
                'P', "ingotPlatinum",
                'D', new ItemStack(Items.diamond),
                'G', "blockGlassHardened",
                'S', TEAugments.machineSpeed[2],
                'R', "dustRedstone"));
    }

    /**
     * ThermalADD's own "beyond spec" Machine Secondary (sieve) tier - same idea as the Speed
     * Level 4 recipe above, just themed with Emerald instead of Diamond so the two endgame
     * augments don't share an identical shopping list: consumes real Thermal Expansion's own
     * top sieve tier (machineSecondary[2], "Отклик Гиросервомеханизма") as the centerpiece.
     */
    private static void registerSecondarySieve4AugmentRecipe() {
        // P E P      P = Platinum ingot, E = Emerald
        // G S G      G = Hardened Glass, S = real Machine Secondary III augment (consumed/upgraded)
        // P R P      R = Redstone dust
        GameRegistry.addRecipe(new ShapedOreRecipe(
                ModAugments.secondarySieve4,
                "PEP",
                "GSG",
                "PRP",
                'P', "ingotPlatinum",
                'E', new ItemStack(Items.emerald),
                'G', "blockGlassHardened",
                'S', TEAugments.machineSecondary[2],
                'R', "dustRedstone"));
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
