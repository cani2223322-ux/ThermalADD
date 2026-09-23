package net.thermaladd.mod.inventory;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.thermaladd.mod.tileentity.TileAdvancedFurnace;

public class SlotAugmentFurnace extends Slot {

    public SlotAugmentFurnace(IInventory inv, int index, int x, int y) {
        super(inv, index, x, y);
    }

    /** Rejects both an invalid item AND a valid augment whose type already sits in another slot - see TileAdvancedFurnace#hasDuplicateAugmentType. */
    @Override
    public boolean isItemValid(ItemStack stack) {
        if (!TileAdvancedFurnace.isValidAugment(stack)) {
            return false;
        }
        return !((TileAdvancedFurnace) this.inventory).hasDuplicateAugmentType(stack, getSlotIndex());
    }

    /** One augment per slot, as in real TE's SlotAugment. */
    @Override
    public int getSlotStackLimit() {
        return 1;
    }
}
