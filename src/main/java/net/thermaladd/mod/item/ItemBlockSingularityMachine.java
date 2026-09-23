package net.thermaladd.mod.item;

import java.util.List;
import java.util.Locale;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.block.BlockSingularityMachine;
import net.thermaladd.mod.tileentity.TileSingularityMachine;

/** Tier + RF stats tooltip, same as ItemBlockAdvancedSawmill, for every BlockSingularityMachine. */
public class ItemBlockSingularityMachine extends ItemBlock {

    public ItemBlockSingularityMachine(Block block) {
        super(block);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        if (!(field_150939_a instanceof BlockSingularityMachine)) {
            return;
        }
        BlockSingularityMachine block = (BlockSingularityMachine) field_150939_a;
        list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.tier", TileSingularityMachine.TIER_NAME));
        list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.capacity",
                String.format(Locale.ROOT, "%,d", block.getItemCapacity())));
        list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.input",
                String.format(Locale.ROOT, "%,d", block.getItemReceiveRate())));
    }
}
