package net.thermaladd.mod.client.gui;

import net.minecraft.client.gui.Gui;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.inventory.ContainerImprovedAssembler;
import net.thermaladd.mod.tileentity.TileImprovedAssembler;

/**
 * "Augments" tab for the Improved Cyclic Assembler - same green header/slot-row layout as
 * Thermal Expansion's real TabAugment. 3 slots in a single row (this machine keeps TE's own
 * count, unlike the Advanced Pulverizer's 9), reusing the real CoFHCore tab/icon textures
 * directly now that CoFHCore is a hard dependency of the merged mod.
 */
public class TabAugmentsAssembler extends GuiSideTab {

    private static final int SLOT_X = 24;
    private static final int SLOT_Y = 33;
    private static final int SLOT_STEP = 18;

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
                slot.xDisplayPosition = getTabX() + SLOT_X + i * SLOT_STEP;
                slot.yDisplayPosition = getTabY() + SLOT_Y;
            } else {
                slot.xDisplayPosition = -1000;
                slot.yDisplayPosition = -1000;
            }
        }
    }

    @Override
    protected void drawContentBackground(int x, int y) {
        for (int i = 0; i < TileImprovedAssembler.AUGMENT_SLOTS; i++) {
            int sx = x + SLOT_X + i * SLOT_STEP;
            int sy = y + SLOT_Y;
            Gui.drawRect(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF8B8B8B);
            Gui.drawRect(sx, sy, sx + 16, sy + 16, 0xFF373737);
        }
    }
}
