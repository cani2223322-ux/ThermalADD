package net.thermaladd.mod.inventory;

import net.minecraft.entity.player.InventoryPlayer;
import net.thermaladd.mod.tileentity.TileSingularSmelter;

/** Two input columns (A, B) per line, then the Sawmill's output layout. */
public class ContainerSingularSmelter extends ContainerSingularityMachine {

    public static final int INPUT_A_X = 30;
    public static final int INPUT_B_X = 48;
    public static final int INPUT_Y = 17;
    public static final int OUTPUT_PRIMARY_X = 116;
    public static final int OUTPUT_PRIMARY_Y = 17;
    public static final int OUTPUT_SECONDARY_X = 134;
    public static final int OUTPUT_SECONDARY_Y = 35;

    public ContainerSingularSmelter(InventoryPlayer playerInv, TileSingularSmelter tile) {
        super(playerInv, tile);
    }

    @Override
    protected void addMachineSlots() {
        for (int line = 0; line < TileSingularSmelter.LINES; line++) {
            int y = INPUT_Y + line * SLOT_SIZE;
            addSlotToContainer(new SlotValidated(tile, line * 2, INPUT_A_X, y));
            addSlotToContainer(new SlotValidated(tile, line * 2 + 1, INPUT_B_X, y));
        }
        for (int i = 0; i < TileSingularSmelter.OUTPUT_PRIMARY_SLOTS; i++) {
            addSlotToContainer(new SlotValidated(tile, TileSingularSmelter.OUTPUT_PRIMARY_START + i,
                    OUTPUT_PRIMARY_X, OUTPUT_PRIMARY_Y + i * SLOT_SIZE));
        }
        for (int i = 0; i < TileSingularSmelter.OUTPUT_SECONDARY_SLOTS; i++) {
            addSlotToContainer(new SlotValidated(tile, TileSingularSmelter.OUTPUT_SECONDARY_START + i,
                    OUTPUT_SECONDARY_X, OUTPUT_SECONDARY_Y + i * SLOT_SIZE));
        }
    }
}
