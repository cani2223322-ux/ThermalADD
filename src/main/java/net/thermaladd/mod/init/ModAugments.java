package net.thermaladd.mod.init;

import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.registry.GameRegistry;
import net.thermaladd.mod.item.ItemADDAugment;
import net.thermaladd.mod.tileentity.TileAdvancedPulverizer;

/**
 * ThermalADD's own augments - none of these exist in real Thermal Expansion, but they use the
 * same augment type strings (see TileAdvancedPulverizer/TileAdvancedFurnace's AUG_MACHINE_*
 * constants) our tiles already recognize, so they plug into the existing 9-slot augment system
 * with no special-casing needed there.
 */
public class ModAugments {

    public static final int META_SPEED_4 = 0;

    public static ItemADDAugment item;
    public static ItemStack speedLevel4;

    private ModAugments() {
    }

    public static void init() {
        item = new ItemADDAugment();

        // "Beyond spec" 4th Machine Speed tier - one level past real TE's own top tier
        // (machineSpeed[2], "Пространственно-временной унификатор флакса"), which this
        // augment's own recipe consumes as an ingredient (see ModRecipes). x10 process speed
        // for +200% RF/t over the level-3 tier's own already-steep cost - see
        // TileAdvancedPulverizer.MACHINE_SPEED_PROCESS_MOD/MACHINE_SPEED_ENERGY_MOD index 4.
        item.addAugment(META_SPEED_4, TileAdvancedPulverizer.AUG_MACHINE_SPEED, 4, "speedLevel4",
                new String[]{
                        "tooltip.thermaladd.addAugment.speedLevel4.0",
                        "tooltip.thermaladd.addAugment.speedLevel4.1"
                });
        speedLevel4 = item.getStack(META_SPEED_4);
    }

    public static void register() {
        GameRegistry.registerItem(item, "addAugment");
    }
}
