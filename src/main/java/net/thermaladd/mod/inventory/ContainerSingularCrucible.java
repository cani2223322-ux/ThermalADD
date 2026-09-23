package net.thermaladd.mod.inventory;

import net.minecraft.entity.player.InventoryPlayer;
import net.thermaladd.mod.tileentity.TileSingularCrucible;

/** One input column; everything melts into the tank on the right. */
public class ContainerSingularCrucible extends ContainerSingularityMachine {

    public static final int INPUT_X = 44;
    public static final int INPUT_Y = 17;

    public ContainerSingularCrucible(InventoryPlayer playerInv, TileSingularCrucible tile) {
        super(playerInv, tile);
    }

    @Override
    protected void addMachineSlots() {
        for (int line = 0; line < TileSingularCrucible.LINES; line++) {
            addSlotToContainer(new SlotValidated(tile, line, INPUT_X, INPUT_Y + line * SLOT_SIZE));
        }
    }
}
