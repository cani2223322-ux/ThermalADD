package net.thermaladd.mod.inventory;

import net.minecraft.entity.player.InventoryPlayer;
import net.thermaladd.mod.tileentity.TileSingularTransposer;

/** Input column, output column, and the tank on the far right (drawn by the GUI). */
public class ContainerSingularTransposer extends ContainerSingularityMachine {

    public static final int INPUT_X = 44;
    public static final int INPUT_Y = 17;
    public static final int OUTPUT_X = 112;

    public ContainerSingularTransposer(InventoryPlayer playerInv, TileSingularTransposer tile) {
        super(playerInv, tile);
    }

    @Override
    protected void addMachineSlots() {
        for (int line = 0; line < TileSingularTransposer.LINES; line++) {
            addSlotToContainer(new SlotValidated(tile, line, INPUT_X, INPUT_Y + line * SLOT_SIZE));
        }
        for (int i = 0; i < TileSingularTransposer.OUTPUT_SLOTS; i++) {
            addSlotToContainer(new SlotValidated(tile, TileSingularTransposer.OUTPUT_START + i, OUTPUT_X, INPUT_Y + i * SLOT_SIZE));
        }
    }
}
