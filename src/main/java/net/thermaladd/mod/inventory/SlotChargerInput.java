package net.thermaladd.mod.inventory;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Accepts what the tile accepts for this line: a real RF-storing item (to charge in place) or an
 * item with a real ChargerManager recipe (to convert) - see TileAdvancedCharger's own javadoc for
 * the dual-mode rationale - and, if the line is locked, only the item it is locked to.
 */
public class SlotChargerInput extends Slot {

    public SlotChargerInput(IInventory inventory, int slotIndex, int x, int y) {
        super(inventory, slotIndex, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return inventory.isItemValidForSlot(getSlotIndex(), stack);
    }
}
