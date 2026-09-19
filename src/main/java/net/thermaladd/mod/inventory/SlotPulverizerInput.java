package net.thermaladd.mod.inventory;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import cofh.thermalexpansion.util.crafting.PulverizerManager;

/** Only accepts items that actually have a real Pulverizer recipe - same rule Thermal Expansion's own Pulverizer GUI enforces. */
public class SlotPulverizerInput extends Slot {

    public SlotPulverizerInput(IInventory inventory, int slotIndex, int x, int y) {
        super(inventory, slotIndex, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return PulverizerManager.recipeExists(stack);
    }
}
