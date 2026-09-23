package net.thermaladd.mod.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.thermaladd.mod.tileentity.TileSingularityStrongbox;

/** 15 x 8 box slots, the player's inventory centred underneath. */
public class ContainerSingularityStrongbox extends Container {

    public static final int SLOT_SIZE = 18;
    public static final int BOX_X = 8;
    public static final int BOX_Y = 18;
    public static final int PANEL_WIDTH = TileSingularityStrongbox.COLUMNS * SLOT_SIZE + 16;
    public static final int PLAYER_INV_X = (PANEL_WIDTH - 9 * SLOT_SIZE) / 2 + 1;
    public static final int PLAYER_INV_Y = BOX_Y + TileSingularityStrongbox.ROWS * SLOT_SIZE + 14;
    public static final int PLAYER_HOTBAR_Y = PLAYER_INV_Y + 58;
    public static final int PANEL_HEIGHT = PLAYER_HOTBAR_Y + 24;

    private final TileSingularityStrongbox tile;

    public ContainerSingularityStrongbox(InventoryPlayer playerInv, TileSingularityStrongbox tile) {
        this.tile = tile;
        tile.openInventory();
        for (int row = 0; row < TileSingularityStrongbox.ROWS; row++) {
            for (int col = 0; col < TileSingularityStrongbox.COLUMNS; col++) {
                addSlotToContainer(new SlotValidated(tile, col + row * TileSingularityStrongbox.COLUMNS,
                        BOX_X + col * SLOT_SIZE, BOX_Y + row * SLOT_SIZE));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, PLAYER_INV_X + col * SLOT_SIZE, PLAYER_INV_Y + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, PLAYER_INV_X + col * SLOT_SIZE, PLAYER_HOTBAR_Y));
        }
    }

    public TileSingularityStrongbox getTile() {
        return tile;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile.isUseableByPlayer(player);
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        tile.closeInventory();
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        Slot slot = (Slot) inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) {
            return null;
        }
        ItemStack stack = slot.getStack();
        ItemStack result = stack.copy();
        int size = TileSingularityStrongbox.SIZE;
        if (slotIndex < size) {
            if (!mergeItemStack(stack, size, size + 36, true)) {
                return null;
            }
        } else if (!tile.isItemValidForSlot(0, stack) || !mergeItemStack(stack, 0, size, false)) {
            // mergeItemStack ignores Slot#isItemValid, so a box that carries an inventory is
            // refused here explicitly.
            return null;
        }
        if (stack.stackSize == 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        return result;
    }
}
