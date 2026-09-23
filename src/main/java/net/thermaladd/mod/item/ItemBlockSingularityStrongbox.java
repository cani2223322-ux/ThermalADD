package net.thermaladd.mod.item;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.tileentity.TileSingularityStrongbox;

/** Slot count, and how full a picked-up box is. One per stack once it carries anything. */
public class ItemBlockSingularityStrongbox extends ItemBlock {

    public ItemBlockSingularityStrongbox(Block block) {
        super(block);
    }

    @Override
    public int getItemStackLimit(ItemStack stack) {
        return TileSingularityStrongbox.carriesInventory(stack) ? 1 : 64;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.strongbox.size",
                String.valueOf(TileSingularityStrongbox.SIZE)));
        if (TileSingularityStrongbox.carriesInventory(stack)) {
            int used = stack.getTagCompound().getTagList(TileSingularityStrongbox.TAG_INVENTORY, 10).tagCount();
            list.add(StatCollector.translateToLocalFormatted("tooltip.thermaladd.strongbox.used",
                    String.valueOf(used), String.valueOf(TileSingularityStrongbox.SIZE)));
        }
    }
}
