package net.thermaladd.mod.init;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.OreDictionary;
import net.thermaladd.mod.item.ItemSingularityGear;

/** Standalone crafting-material items (not blocks, not augments) - currently just the Singularity Gear. */
public class ModItems {

    public static ItemSingularityGear singularityGear;

    private ModItems() {
    }

    public static void init() {
        singularityGear = new ItemSingularityGear();
    }

    public static void register() {
        GameRegistry.registerItem(singularityGear, "singularityGear");
        OreDictionary.registerOre("gearSingularity", singularityGear);
    }
}
