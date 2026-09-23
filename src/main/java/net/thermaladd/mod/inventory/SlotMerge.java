package net.thermaladd.mod.inventory;

import java.util.List;

import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Shift-click into single-item slots (augments, schematics). Vanilla's Container#mergeItemStack
 * ignores both Slot#isItemValid and Slot#getSlotStackLimit, so it would put a whole stack of
 * augments - or a second augment of a type already installed - into one slot. This places one
 * item per empty slot, asking each slot first, as real TE's slots only ever hold one.
 */
public final class SlotMerge {

    private SlotMerge() {
    }

    /** Returns whether anything moved. {@code stack} is reduced by what was placed. */
    @SuppressWarnings("rawtypes")
    public static boolean mergeOnePerSlot(List slots, ItemStack stack, int start, int end) {
        boolean moved = false;
        for (int i = start; i < end && stack.stackSize > 0; i++) {
            Slot slot = (Slot) slots.get(i);
            if (slot.getHasStack()) {
                continue;
            }
            ItemStack single = stack.copy();
            single.stackSize = 1;
            if (!slot.isItemValid(single)) {
                continue;
            }
            slot.putStack(single);
            stack.stackSize--;
            moved = true;
        }
        return moved;
    }
}
