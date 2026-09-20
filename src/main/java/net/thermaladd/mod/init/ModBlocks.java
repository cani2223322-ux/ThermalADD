package net.thermaladd.mod.init;

import cpw.mods.fml.common.registry.GameRegistry;
import net.thermaladd.mod.block.BlockAdvancedFurnace;
import net.thermaladd.mod.block.BlockAdvancedPulverizer;
import net.thermaladd.mod.block.BlockImprovedAssembler;
import net.thermaladd.mod.block.BlockSingularityCell;
import net.thermaladd.mod.block.BlockSingularityFrame;
import net.thermaladd.mod.item.ItemBlockAdvancedFurnace;
import net.thermaladd.mod.item.ItemBlockAdvancedPulverizer;
import net.thermaladd.mod.item.ItemBlockImprovedAssembler;
import net.thermaladd.mod.item.ItemBlockSingularityCell;

public class ModBlocks {

    public static BlockAdvancedPulverizer advancedPulverizer;
    public static BlockImprovedAssembler improvedAssembler;
    public static BlockAdvancedFurnace advancedFurnace;
    public static BlockSingularityCell singularityCell;
    public static BlockSingularityFrame singularityFrame;

    public static void init() {
        advancedPulverizer = new BlockAdvancedPulverizer();
        improvedAssembler = new BlockImprovedAssembler();
        advancedFurnace = new BlockAdvancedFurnace();
        singularityCell = new BlockSingularityCell();
        singularityFrame = new BlockSingularityFrame();
    }

    public static void register() {
        GameRegistry.registerBlock(advancedPulverizer, ItemBlockAdvancedPulverizer.class, "advancedPulverizer");
        GameRegistry.registerBlock(improvedAssembler, ItemBlockImprovedAssembler.class, "improvedAssembler");
        GameRegistry.registerBlock(advancedFurnace, ItemBlockAdvancedFurnace.class, "advancedFurnace");
        GameRegistry.registerBlock(singularityCell, ItemBlockSingularityCell.class, "singularityCell");
        // Plain crafting material, no custom tooltip/behavior needed - default ItemBlock is fine.
        GameRegistry.registerBlock(singularityFrame, "singularityFrame");
    }
}
