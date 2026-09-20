package net.thermaladd.mod.client.gui;

import net.minecraft.client.gui.Gui;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.inventory.ContainerImprovedAssembler;
import net.thermaladd.mod.tileentity.TileImprovedAssembler;

/**
 * "Augments" tab for the Singularity Cyclic Assembler - same green header/slot-grid layout as
 * {@link TabAugments} (the Pulverizer's own): 9 slots across 3 rows, matching this mod's other
 * two machines now that this one no longer keeps real TE's own 3-slot count, reusing the real
 * CoFHCore tab/icon textures directly now that CoFHCore is a hard dependency of the merged mod.
 */
public class TabAugmentsAssembler extends GuiSideTab {

    private static final int SLOT_X = 24;
    private static final int SLOT_Y = 20;
    private static final int SLOT_STEP = 18;
    private static final int COLS = 3;

    /** Same green tint Thermal Expansion uses for this tab (TabAugment.defaultBackgroundColor). */
    private static final int TINT = 0x089E4C;
    /** Same gold header text colour TE uses across all of its tabs. */
    private static final int HEADER = 0xE1C92F;

    private final ContainerImprovedAssembler container;

    public TabAugmentsAssembler(TabbedMachineGui gui, ContainerImprovedAssembler container) {
        super(gui, new ResourceLocation("cofh", "textures/items/icons/Icon_Augment.png"),
                TINT, HEADER, StatCollector.translateToLocal("gui.improvedassembler.tab.augments"));
        this.container = container;
    }

    @Override
    protected void onUpdate(boolean fullyOpen) {
        // Slot.xDisplayPosition/yDisplayPosition are relative to the whole container (guiLeft/
        // guiTop), not to this tab - so the tab's own stacked position has to be added in.
        for (int i = 0; i < TileImprovedAssembler.AUGMENT_SLOTS; i++) {
            Slot slot = container.getAugmentSlot(i);
            if (fullyOpen) {
                slot.xDisplayPosition = getTabX() + SLOT_X + (i % COLS) * SLOT_STEP;
                slot.yDisplayPosition = getTabY() + SLOT_Y + (i / COLS) * SLOT_STEP;
            } else {
                slot.xDisplayPosition = -1000;
                slot.yDisplayPosition = -1000;
            }
        }
    }

    @Override
    protected void drawContentBackground(int x, int y) {
        for (int i = 0; i < TileImprovedAssembler.AUGMENT_SLOTS; i++) {
            int sx = x + SLOT_X + (i % COLS) * SLOT_STEP;
            int sy = y + SLOT_Y + (i / COLS) * SLOT_STEP;
            Gui.drawRect(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF8B8B8B);
            Gui.drawRect(sx, sy, sx + 16, sy + 16, 0xFF373737);
        }
    }
}
