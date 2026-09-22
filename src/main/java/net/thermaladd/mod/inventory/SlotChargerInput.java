package net.thermaladd.mod.inventory;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import cofh.api.energy.IEnergyContainerItem;
import cofh.thermalexpansion.util.crafting.ChargerManager;

/** Accepts either a real RF-storing item (to charge in place) or an item with a real ChargerManager recipe (to convert) - see TileAdvancedCharger's own javadoc for the dual-mode rationale, mirroring real Thermal Expansion's own single Charger slot exactly. */
public class SlotChargerInput extends Slot {

    public SlotChargerInput(IInventory inventory, int slotIndex, int x, int y) {
        super(inventory, slotIndex, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return stack.getItem() instanceof IEnergyContainerItem || ChargerManager.recipeExists(stack);
    }
}
