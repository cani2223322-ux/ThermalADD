package net.thermaladd.mod.inventory;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.thermaladd.mod.tileentity.TileAdvancedCharger;

public class SlotAugmentCharger extends Slot {

    public SlotAugmentCharger(IInventory inv, int index, int x, int y) {
        super(inv, index, x, y);
    }

    /** Rejects both an invalid item AND a valid augment whose type already sits in another slot - see TileAdvancedCharger#hasDuplicateAugmentType. */
    @Override
    public boolean isItemValid(ItemStack stack) {
        if (!TileAdvancedCharger.isValidAugment(stack)) {
            return false;
        }
        return !((TileAdvancedCharger) this.inventory).hasDuplicateAugmentType(stack, getSlotIndex());
    }
}
