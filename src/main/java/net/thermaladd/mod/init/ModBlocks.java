package net.thermaladd.mod.init;

import cpw.mods.fml.common.registry.GameRegistry;
import net.thermaladd.mod.block.BlockAdvancedCharger;
import net.thermaladd.mod.block.BlockAdvancedFurnace;
import net.thermaladd.mod.block.BlockAdvancedPulverizer;
import net.thermaladd.mod.block.BlockAdvancedSawmill;
import net.thermaladd.mod.block.BlockImprovedAssembler;
import net.thermaladd.mod.block.BlockSingularityCell;
import net.thermaladd.mod.block.BlockSingularityFrame;
import net.thermaladd.mod.block.BlockSingularCrucible;
import net.thermaladd.mod.block.BlockSingularSmelter;
import net.thermaladd.mod.block.BlockSingularTransposer;
import net.thermaladd.mod.block.BlockSingularityStrongbox;
import net.thermaladd.mod.block.BlockSingularityTank;
import net.thermaladd.mod.item.ItemBlockSingularityMachine;
import net.thermaladd.mod.item.ItemBlockSingularityStrongbox;
import net.thermaladd.mod.item.ItemBlockSingularityTank;
import net.thermaladd.mod.item.ItemBlockAdvancedCharger;
import net.thermaladd.mod.item.ItemBlockAdvancedFurnace;
import net.thermaladd.mod.item.ItemBlockAdvancedPulverizer;
import net.thermaladd.mod.item.ItemBlockAdvancedSawmill;
import net.thermaladd.mod.item.ItemBlockImprovedAssembler;
import net.thermaladd.mod.item.ItemBlockSingularityCell;

public class ModBlocks {

    public static BlockAdvancedPulverizer advancedPulverizer;
    public static BlockImprovedAssembler improvedAssembler;
    public static BlockAdvancedFurnace advancedFurnace;
    public static BlockAdvancedSawmill advancedSawmill;
    public static BlockAdvancedCharger advancedCharger;
    public static BlockSingularityCell singularityCell;
    public static BlockSingularityFrame singularityFrame;
    public static BlockSingularSmelter singularSmelter;
    public static BlockSingularCrucible singularCrucible;
    public static BlockSingularTransposer singularTransposer;
    public static BlockSingularityTank singularityTank;
    public static BlockSingularityStrongbox singularityStrongbox;

    public static void init() {
        singularityTank = new BlockSingularityTank();
        singularityStrongbox = new BlockSingularityStrongbox();
        singularSmelter = new BlockSingularSmelter();
        singularCrucible = new BlockSingularCrucible();
        singularTransposer = new BlockSingularTransposer();
        advancedPulverizer = new BlockAdvancedPulverizer();
        improvedAssembler = new BlockImprovedAssembler();
        advancedFurnace = new BlockAdvancedFurnace();
        advancedSawmill = new BlockAdvancedSawmill();
        advancedCharger = new BlockAdvancedCharger();
        singularityCell = new BlockSingularityCell();
        singularityFrame = new BlockSingularityFrame();
    }

    public static void register() {
        GameRegistry.registerBlock(advancedPulverizer, ItemBlockAdvancedPulverizer.class, "advancedPulverizer");
        GameRegistry.registerBlock(improvedAssembler, ItemBlockImprovedAssembler.class, "improvedAssembler");
        GameRegistry.registerBlock(advancedFurnace, ItemBlockAdvancedFurnace.class, "advancedFurnace");
        GameRegistry.registerBlock(advancedSawmill, ItemBlockAdvancedSawmill.class, "advancedSawmill");
        GameRegistry.registerBlock(advancedCharger, ItemBlockAdvancedCharger.class, "advancedCharger");
        GameRegistry.registerBlock(singularityCell, ItemBlockSingularityCell.class, "singularityCell");
        GameRegistry.registerBlock(singularSmelter, ItemBlockSingularityMachine.class, "singularSmelter");
        GameRegistry.registerBlock(singularCrucible, ItemBlockSingularityMachine.class, "singularCrucible");
        GameRegistry.registerBlock(singularTransposer, ItemBlockSingularityMachine.class, "singularTransposer");
        GameRegistry.registerBlock(singularityTank, ItemBlockSingularityTank.class, "singularityTank");
        GameRegistry.registerBlock(singularityStrongbox, ItemBlockSingularityStrongbox.class, "singularityStrongbox");
        // Plain crafting material, no custom tooltip/behavior needed - default ItemBlock is fine.
        GameRegistry.registerBlock(singularityFrame, "singularityFrame");
    }
}
