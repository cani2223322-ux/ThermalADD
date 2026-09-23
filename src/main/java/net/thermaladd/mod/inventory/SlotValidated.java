package net.thermaladd.mod.inventory;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * A slot that accepts exactly what its inventory's isItemValidForSlot accepts - recipe inputs,
 * line locks, augment rules, and nothing at all for an output slot.
 */
public class SlotValidated extends Slot {

    public SlotValidated(IInventory inventory, int slotIndex, int x, int y) {
        super(inventory, slotIndex, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return inventory.isItemValidForSlot(getSlotIndex(), stack);
    }
}
