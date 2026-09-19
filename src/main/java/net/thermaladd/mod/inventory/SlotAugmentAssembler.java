package net.thermaladd.mod.inventory;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.thermaladd.mod.tileentity.TileImprovedAssembler;

/** Named distinctly from {@link SlotAugment} (the Advanced Pulverizer's own augment slot) since the two machines recognize different augment type sets. */
public class SlotAugmentAssembler extends Slot {

    public SlotAugmentAssembler(IInventory inv, int index, int x, int y) {
        super(inv, index, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return TileImprovedAssembler.isValidAugment(stack);
    }
}
