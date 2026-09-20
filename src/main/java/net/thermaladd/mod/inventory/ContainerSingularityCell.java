package net.thermaladd.mod.inventory;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import net.thermaladd.mod.network.MessageEnergyCellSync;
import net.thermaladd.mod.network.PacketHandler;
import net.thermaladd.mod.tileentity.TileSingularityCell;

/**
 * No custom slots at all - this block only stores energy, nothing physical - just the player's
 * own inventory, plus a live sync of the tile's true (beyond-int-range) charge to whichever
 * player has the GUI open. The charge can't ride vanilla's windowProperty channel like this
 * mod's other machines' stats do (Container#sendProgressBarUpdate ultimately serializes its
 * value as a signed short, nowhere near enough range for a value in the billions), so it goes
 * out as a small dedicated packet instead, sent only to the viewing player - not broadcast to
 * everyone nearby like MessageTileRenderSync, since nobody else needs live GUI-only numbers.
 */
public class ContainerSingularityCell extends Container {

    public static final int PLAYER_INV_Y = 32;
    public static final int PLAYER_HOTBAR_Y = 90;

    private final TileSingularityCell tile;
    private long lastEnergy = -1L;

    public ContainerSingularityCell(InventoryPlayer playerInv, TileSingularityCell tile) {
        this.tile = tile;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, 8 + col * 18, PLAYER_HOTBAR_Y));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile.isUseableByPlayer(player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        long energy = tile.getEnergyStoredLong();
        if (energy == lastEnergy) {
            return;
        }
        lastEnergy = energy;
        List<ICrafting> list = (List<ICrafting>) crafters;
        for (ICrafting crafter : list) {
            if (crafter instanceof EntityPlayerMP) {
                PacketHandler.INSTANCE.sendTo(
                        new MessageEnergyCellSync(tile.xCoord, tile.yCoord, tile.zCoord, energy),
                        (EntityPlayerMP) crafter);
            }
        }
    }
}
