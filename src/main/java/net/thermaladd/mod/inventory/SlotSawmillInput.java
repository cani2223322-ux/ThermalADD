package net.thermaladd.mod.inventory;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import cofh.thermalexpansion.util.crafting.SawmillManager;

/** Only accepts items that actually have a real Sawmill recipe - same rule Thermal Expansion's own Sawmill GUI enforces. */
public class SlotSawmillInput extends Slot {

    public SlotSawmillInput(IInventory inventory, int slotIndex, int x, int y) {
        super(inventory, slotIndex, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return SawmillManager.recipeExists(stack);
    }
}
