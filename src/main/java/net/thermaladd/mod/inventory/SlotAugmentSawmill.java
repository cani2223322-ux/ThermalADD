package net.thermaladd.mod.inventory;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.thermaladd.mod.tileentity.TileAdvancedSawmill;

public class SlotAugmentSawmill extends Slot {

    public SlotAugmentSawmill(IInventory inv, int index, int x, int y) {
        super(inv, index, x, y);
    }

    /** Rejects both an invalid item AND a valid augment whose type already sits in another slot - see TileAdvancedSawmill#hasDuplicateAugmentType. */
    @Override
    public boolean isItemValid(ItemStack stack) {
        if (!TileAdvancedSawmill.isValidAugment(stack)) {
            return false;
        }
        return !((TileAdvancedSawmill) this.inventory).hasDuplicateAugmentType(stack, getSlotIndex());
    }
}
