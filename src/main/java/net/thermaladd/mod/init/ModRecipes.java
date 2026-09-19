package net.thermaladd.mod.init;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;

import cofh.thermalexpansion.block.TEBlocks;
import cofh.thermalexpansion.block.machine.BlockMachine;
import cofh.thermalexpansion.item.TEAugments;
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
}
