package net.thermaladd.mod.client.gui;

import net.minecraft.client.gui.Gui;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.inventory.ContainerAdvancedSawmill;
import net.thermaladd.mod.tileentity.TileAdvancedSawmill;

/** "Augments" tab - see TabAugments (the Pulverizer's own copy) for the full design rationale; identical 3x3 layout. */
public class TabAugmentsSawmill extends GuiSideTab {

    private static final int SLOT_X = 24;
    private static final int SLOT_Y = 20;
    private static final int SLOT_STEP = 18;
    private static final int COLS = 3;

    private static final int TINT = 0x089E4C;
    private static final int HEADER = 0xE1C92F;

    private final ContainerAdvancedSawmill container;

    public TabAugmentsSawmill(GuiAdvancedSawmill gui, ContainerAdvancedSawmill container) {
        super(gui, new ResourceLocation("cofh", "textures/items/icons/Icon_Augment.png"),
                TINT, HEADER, StatCollector.translateToLocal("gui.thermaladd.tab.augments"));
        this.container = container;
    }

    @Override
    protected void onUpdate(boolean fullyOpen) {
        for (int i = 0; i < TileAdvancedSawmill.AUGMENT_SLOTS; i++) {
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
        for (int i = 0; i < TileAdvancedSawmill.AUGMENT_SLOTS; i++) {
            int sx = x + SLOT_X + (i % COLS) * SLOT_STEP;
            int sy = y + SLOT_Y + (i / COLS) * SLOT_STEP;
            Gui.drawRect(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF8B8B8B);
            Gui.drawRect(sx, sy, sx + 16, sy + 16, 0xFF373737);
        }
    }
}
