package net.thermaladd.mod.init;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.OreDictionary;
import net.thermaladd.mod.item.ItemSingularityGear;
import net.thermaladd.mod.item.ItemSingularityUpgradeKit;

/** Standalone crafting-material items (not blocks, not augments) - currently just the Singularity Gear. */
public class ModItems {

    public static ItemSingularityGear singularityGear;
    public static ItemSingularityUpgradeKit singularityUpgradeKit;

    private ModItems() {
    }

    public static void init() {
        singularityGear = new ItemSingularityGear();
        singularityUpgradeKit = new ItemSingularityUpgradeKit();
    }

    public static void register() {
        GameRegistry.registerItem(singularityGear, "singularityGear");
        GameRegistry.registerItem(singularityUpgradeKit, "singularityUpgradeKit");
        OreDictionary.registerOre("gearSingularity", singularityGear);
    }
}
