package net.thermaladd.mod.init;

import cpw.mods.fml.common.registry.GameRegistry;
import net.thermaladd.mod.block.BlockAdvancedFurnace;
import net.thermaladd.mod.block.BlockAdvancedPulverizer;
import net.thermaladd.mod.block.BlockImprovedAssembler;
import net.thermaladd.mod.item.ItemBlockAdvancedFurnace;
import net.thermaladd.mod.item.ItemBlockAdvancedPulverizer;
import net.thermaladd.mod.item.ItemBlockImprovedAssembler;

public class ModBlocks {

    public static BlockAdvancedPulverizer advancedPulverizer;
    public static BlockImprovedAssembler improvedAssembler;
    public static BlockAdvancedFurnace advancedFurnace;

    public static void init() {
        advancedPulverizer = new BlockAdvancedPulverizer();
        improvedAssembler = new BlockImprovedAssembler();
        advancedFurnace = new BlockAdvancedFurnace();
    }

    public static void register() {
        GameRegistry.registerBlock(advancedPulverizer, ItemBlockAdvancedPulverizer.class, "advancedPulverizer");
        GameRegistry.registerBlock(improvedAssembler, ItemBlockImprovedAssembler.class, "improvedAssembler");
        GameRegistry.registerBlock(advancedFurnace, ItemBlockAdvancedFurnace.class, "advancedFurnace");
    }
}
