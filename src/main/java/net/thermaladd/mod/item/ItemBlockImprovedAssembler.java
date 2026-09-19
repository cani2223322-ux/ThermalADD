package net.thermaladd.mod.item;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.tileentity.TileImprovedAssembler;

/** Adds the same "Tier: UltimateResonant" + RF stats readout as {@link ItemBlockAdvancedPulverizer}. */
public class ItemBlockImprovedAssembler extends ItemBlock {

    public ItemBlockImprovedAssembler(Block block) {
        super(block);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.tier", TileImprovedAssembler.TIER_NAME));
        list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.capacity", TileImprovedAssembler.ENERGY_CAPACITY));
        list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.input", TileImprovedAssembler.ENERGY_RECEIVE_PER_TICK));
    }
}
