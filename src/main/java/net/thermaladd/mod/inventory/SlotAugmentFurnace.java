package net.thermaladd.mod.inventory;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.thermaladd.mod.tileentity.TileAdvancedFurnace;

public class SlotAugmentFurnace extends Slot {

    public SlotAugmentFurnace(IInventory inv, int index, int x, int y) {
        super(inv, index, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return TileAdvancedFurnace.isValidAugment(stack);
    }
}
