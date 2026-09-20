package net.thermaladd.mod.inventory;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.thermaladd.mod.tileentity.TileAdvancedPulverizer;

public class SlotAugment extends Slot {

    public SlotAugment(IInventory inv, int index, int x, int y) {
        super(inv, index, x, y);
    }

    /** Rejects both an invalid item AND a valid augment whose type already sits in another slot - see TileAdvancedPulverizer#hasDuplicateAugmentType. */
    @Override
    public boolean isItemValid(ItemStack stack) {
        if (!TileAdvancedPulverizer.isValidAugment(stack)) {
            return false;
        }
        return !((TileAdvancedPulverizer) this.inventory).hasDuplicateAugmentType(stack, getSlotIndex());
    }
}
