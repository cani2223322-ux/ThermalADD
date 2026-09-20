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

    /** Rejects both an invalid item AND a valid augment whose type already sits in another slot - see TileImprovedAssembler#hasDuplicateAugmentType. */
    @Override
    public boolean isItemValid(ItemStack stack) {
        if (!TileImprovedAssembler.isValidAugment(stack)) {
            return false;
        }
        return !((TileImprovedAssembler) this.inventory).hasDuplicateAugmentType(stack, getSlotIndex());
    }
}
