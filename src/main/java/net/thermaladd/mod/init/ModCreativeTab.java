package net.thermaladd.mod.init;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.thermaladd.mod.ThermalADD;

public class ModCreativeTab extends CreativeTabs {

    public static final CreativeTabs TAB = new ModCreativeTab(ThermalADD.MODID);

    public ModCreativeTab(String label) {
        super(CreativeTabs.getNextID(), label);
    }

    @Override
    public Item getTabIconItem() {
        return Item.getItemFromBlock(ModBlocks.advancedPulverizer);
    }
}
