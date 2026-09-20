package net.thermaladd.mod.item;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/** Shows the charge a picked-up cell is carrying (see BlockSingularityCell#breakBlock/getDrops), same spirit as real TE's own Energy Cell tooltip. */
public class ItemBlockSingularityCell extends ItemBlock {

    public ItemBlockSingularityCell(Block block) {
        super(block);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        long energy = stack.hasTagCompound() && stack.getTagCompound().hasKey("Energy")
                ? stack.getTagCompound().getLong("Energy") : 0L;
        list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.singularityCell.charge",
                String.format("%,d", energy), String.format("%,d", net.thermaladd.mod.tileentity.TileSingularityCell.CAPACITY)));
    }
}
