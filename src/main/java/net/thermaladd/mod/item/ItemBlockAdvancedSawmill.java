package net.thermaladd.mod.item;

import java.util.List;
import java.util.Locale;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.tileentity.TileAdvancedSawmill;

/** Adds the "Tier: UltimateResonant" + RF stats readout real CoFH machine tooltips show - see ItemBlockAdvancedPulverizer's own copy of this class. */
public class ItemBlockAdvancedSawmill extends ItemBlock {

    public ItemBlockAdvancedSawmill(Block block) {
        super(block);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.tier", TileAdvancedSawmill.TIER_NAME));
        list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.capacity",
                String.format(Locale.ROOT, "%,d", TileAdvancedSawmill.BASE_ENERGY_CAPACITY)));
        list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.input",
                String.format(Locale.ROOT, "%,d", TileAdvancedSawmill.ENERGY_RECEIVE_PER_TICK)));
    }
}
