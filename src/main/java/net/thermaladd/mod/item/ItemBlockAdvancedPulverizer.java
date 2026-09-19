package net.thermaladd.mod.item;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.tileentity.TileAdvancedPulverizer;

/** Adds the "Tier: UltimateResonant" + RF stats readout real CoFH machine tooltips show. */
public class ItemBlockAdvancedPulverizer extends ItemBlock {

    public ItemBlockAdvancedPulverizer(Block block) {
        super(block);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.tier", TileAdvancedPulverizer.TIER_NAME));
        list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.capacity", TileAdvancedPulverizer.BASE_ENERGY_CAPACITY));
        list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.input", TileAdvancedPulverizer.ENERGY_RECEIVE_PER_TICK));
    }
}
