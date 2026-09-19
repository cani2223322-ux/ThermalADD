package net.thermaladd.mod.client.gui;

import net.minecraft.client.gui.Gui;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.inventory.ContainerAdvancedPulverizer;
import net.thermaladd.mod.tileentity.TileAdvancedPulverizer;

/**
 * "Augments" tab - same green header/slot-grid layout as Thermal Expansion's real
 * TabAugment (cofh.core.gui.element.TabAugment), just with 9 slots (3x3) instead of a
 * tiered machine's 3-6, laid out across 3 rows since 9 in a single row would overflow the
 * tab's fixed 100px flap width.
 */
public class TabAugments extends GuiSideTab {

    private static final int SLOT_X = 24;
    private static final int SLOT_Y = 20;
    private static final int SLOT_STEP = 18;
    private static final int COLS = 3;

    /** Same green tint Thermal Expansion uses for this tab (TabAugment.defaultBackgroundColor). */
    private static final int TINT = 0x089E4C;
    /** Same gold header text colour TE uses across all of its tabs. */
    private static final int HEADER = 0xE1C92F;

    private final ContainerAdvancedPulverizer container;

    public TabAugments(GuiAdvancedPulverizer gui, ContainerAdvancedPulverizer container) {
        super(gui, new ResourceLocation("cofh", "textures/items/icons/Icon_Augment.png"),
                TINT, HEADER, StatCollector.translateToLocal("gui.thermaladd.tab.augments"));
        this.container = container;
    }

    @Override
    protected void onUpdate(boolean fullyOpen) {
        // Slot.xDisplayPosition/yDisplayPosition are relative to the whole container (guiLeft/
        // guiTop), not to this tab - so the tab's own stacked position has to be added in.
        for (int i = 0; i < TileAdvancedPulverizer.AUGMENT_SLOTS; i++) {
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
        for (int i = 0; i < TileAdvancedPulverizer.AUGMENT_SLOTS; i++) {
            int sx = x + SLOT_X + (i % COLS) * SLOT_STEP;
            int sy = y + SLOT_Y + (i / COLS) * SLOT_STEP;
            Gui.drawRect(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF8B8B8B);
            Gui.drawRect(sx, sy, sx + 16, sy + 16, 0xFF373737);
        }
    }
}
