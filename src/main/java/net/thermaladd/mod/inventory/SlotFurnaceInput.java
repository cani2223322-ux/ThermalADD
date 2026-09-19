package net.thermaladd.mod.inventory;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import cofh.thermalexpansion.util.crafting.FurnaceManager;

/** Only accepts items that actually have a real Furnace recipe - same rule Thermal Expansion's own Furnace GUI enforces. */
public class SlotFurnaceInput extends Slot {

    public SlotFurnaceInput(IInventory inventory, int slotIndex, int x, int y) {
        super(inventory, slotIndex, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return FurnaceManager.recipeExists(stack);
    }
}
