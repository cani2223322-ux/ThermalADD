package net.thermaladd.mod;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import net.thermaladd.mod.handler.GuiHandler;
import net.thermaladd.mod.init.ModAugments;
import net.thermaladd.mod.init.ModBlocks;
import net.thermaladd.mod.init.ModRecipes;
import net.thermaladd.mod.network.PacketHandler;
import net.thermaladd.mod.proxy.CommonProxy;
import net.thermaladd.mod.tileentity.TileAdvancedFurnace;
import net.thermaladd.mod.tileentity.TileAdvancedPulverizer;
import net.thermaladd.mod.tileentity.TileImprovedAssembler;
import net.thermaladd.mod.tileentity.TileSingularityCell;

/**
 * ThermalADD - a Thermal Expansion 4 addon for Minecraft 1.7.10.
 *
 * Adds "upgraded" versions of TE4 machines with more slots than the originals:
 * - Advanced Pulverizer: same recipe pool as the real Pulverizer (via
 *   {@link cofh.thermalexpansion.util.crafting.PulverizerManager}), but with 3 independent
 *   input slots processed in parallel instead of 1, 9 augment slots and a dedicated
 *   "UltimateResonant" power tier.
 * - Improved Cyclic Assembler: accepts genuine TE schematics, but has 6 schematic slots
 *   evaluated in parallel instead of 1. Originally its own standalone mod with an optional
 *   TE integration; merged into ThermalADD as a hard addon.
 *
 * Unlike a soft/optional TE integration, this mod is a hard addon: it depends on CoFHCore
 * and ThermalExpansion being present (see build.gradle), and calls straight into TE's own
 * recipe manager and API interfaces at compile time.
 */
@Mod(modid = ThermalADD.MODID, name = ThermalADD.NAME, version = ThermalADD.VERSION,
        dependencies = "required-after:CoFHCore;required-after:ThermalExpansion")
public class ThermalADD {

    public static final String MODID = "thermaladd";
    public static final String NAME = "ThermalADD";
    public static final String VERSION = "1.7.10-beta 1";

    public static final int GUI_ID_ADVANCED_PULVERIZER = 0;
    public static final int GUI_ID_IMPROVED_ASSEMBLER = 1;
    public static final int GUI_ID_ADVANCED_FURNACE = 2;
    public static final int GUI_ID_SINGULARITY_CELL = 3;

    @Instance(MODID)
    public static ThermalADD instance;

    @SidedProxy(clientSide = "net.thermaladd.mod.proxy.ClientProxy",
            serverSide = "net.thermaladd.mod.proxy.CommonProxy")
    public static CommonProxy proxy;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModBlocks.init();
        ModBlocks.register();
        ModAugments.init();
        ModAugments.register();
        GameRegistry.registerTileEntity(TileAdvancedPulverizer.class, MODID + "_advanced_pulverizer_tile");
        GameRegistry.registerTileEntity(TileImprovedAssembler.class, MODID + "_improved_assembler_tile");
        GameRegistry.registerTileEntity(TileAdvancedFurnace.class, MODID + "_advanced_furnace_tile");
        GameRegistry.registerTileEntity(TileSingularityCell.class, MODID + "_singularity_cell_tile");
        proxy.registerRenderers();
        PacketHandler.init();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(instance, new GuiHandler());
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        try {
            ModRecipes.register();
        } catch (Throwable t) {
            // Defensive: a recipe/ingredient lookup failing should never take the whole
            // mod (and every other postInit mod) down with it - just skip the recipe(s).
            FMLLog.severe("[%s] Failed to register recipes: %s", NAME, t);
        }

        // Deliberately a bare string, not a class reference - see
        // net.thermaladd.mod.waila.ThermalADDWailaPlugin's own javadoc for why that class must
        // never be imported/touched anywhere else in this mod, Waila installed or not. Harmless
        // no-op if Waila isn't present: an IMC message with no listening mod just goes nowhere.
        FMLInterModComms.sendMessage("Waila", "register", "net.thermaladd.mod.waila.ThermalADDWailaPlugin.callbackRegister");
    }
}
