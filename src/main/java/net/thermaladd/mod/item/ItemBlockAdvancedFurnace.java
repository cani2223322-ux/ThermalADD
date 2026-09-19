package net.thermaladd.mod.item;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.tileentity.TileAdvancedFurnace;

/** Same "Tier: UltimateResonant" + RF stats readout as the other two machines' ItemBlocks. */
public class ItemBlockAdvancedFurnace extends ItemBlock {

    public ItemBlockAdvancedFurnace(Block block) {
        super(block);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.tier", TileAdvancedFurnace.TIER_NAME));
        list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.capacity", TileAdvancedFurnace.BASE_ENERGY_CAPACITY));
        list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.input", TileAdvancedFurnace.ENERGY_RECEIVE_PER_TICK));
    }
}
