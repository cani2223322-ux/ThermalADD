package net.thermaladd.mod.inventory;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;

public class SlotSchematic extends Slot {

    public SlotSchematic(IInventory inv, int index, int x, int y) {
        super(inv, index, x, y);
    }

    /** Only a TE Schematic - see TileImprovedAssembler#isItemValidForSlot. */
    @Override
    public boolean isItemValid(net.minecraft.item.ItemStack stack) {
        return inventory.isItemValidForSlot(getSlotIndex(), stack);
    }

    @Override
    public int getSlotStackLimit() {
        return 1;
    }
}
