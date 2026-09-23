package net.thermaladd.mod.inventory;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Only accepts what the tile accepts for this line: an item with a real Pulverizer recipe - the
 * same rule Thermal Expansion's own Pulverizer GUI enforces - and, if the line is locked, only
 * the item it is locked to. Asking the tile keeps both rules in one place.
 */
public class SlotPulverizerInput extends Slot {

    public SlotPulverizerInput(IInventory inventory, int slotIndex, int x, int y) {
        super(inventory, slotIndex, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return inventory.isItemValidForSlot(getSlotIndex(), stack);
    }
}
