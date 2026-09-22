package net.thermaladd.mod.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.StatCollector;

import net.thermaladd.mod.inventory.ContainerSingularityCell;
import net.thermaladd.mod.tileentity.TileSingularityCell;

/**
 * Same flat hand-drawn panel style as GuiAdvancedPulverizer/GuiAdvancedFurnace, laid out closer
 * to real Thermal Expansion's own Energy Cell GUI: a tall vertical bar with live RF/t in/out
 * readouts flanking it, the fill amount printed below, then the player's own inventory - with
 * enough clearance above it (PLAYER_INV_Y = 136, same as GuiImprovedAssembler's own taller
 * layout) that nothing overlaps it, unlike the first version of this GUI. One side tab -
 * Configuration (TabConfigCell), same real-TE-cell 3-mode side config GuiAdvancedPulverizer's
 * own Configuration tab uses for its machine.
 */
public class GuiSingularityCell extends TabbedMachineGui {

    private static final int BASE_WIDTH = 176;
    private static final int BASE_HEIGHT = 220;

    /**
     * Real TE's own Resonant/Redstone Energy Cell GUI (decompiled {@code GuiCell}) draws its bar
     * with the exact same {@code ElementEnergyStored} widget the machine GUIs use, at (80, 18) -
     * which is just this panel's own {@code (BASE_WIDTH - ENERGY_WIDTH) / 2}, 18 formula once
     * ENERGY_WIDTH is real TE's fixed 16. This cell draws it at 2x real TE's own size, per
     * instruction - a bigger, more prominent gauge for a "beyond spec" cell whose capacity
     * already dwarfs any real TE tier - via {@link TabbedMachineGui#drawEnergyStoredFilled(int,
     * int, int, int)}'s scale parameter, still centered by the same formula.
     */
    private static final int ENERGY_SCALE = 2;
    private static final int ENERGY_WIDTH = ENERGY_BAR_WIDTH * ENERGY_SCALE;
    private static final int ENERGY_HEIGHT = ENERGY_BAR_HEIGHT * ENERGY_SCALE;
    private static final int ENERGY_X = (BASE_WIDTH - ENERGY_WIDTH) / 2;
    private static final int ENERGY_Y = 18;

    private static final int PLAYER_INV_Y = ContainerSingularityCell.PLAYER_INV_Y;
    private static final int PLAYER_HOTBAR_Y = ContainerSingularityCell.PLAYER_HOTBAR_Y;

    private static final int TAB_STACK_X = BASE_WIDTH;
    private static final int TAB_STACK_Y = 4;

    private final TileSingularityCell tile;
    private final TabConfigCell configTab;

    public GuiSingularityCell(InventoryPlayer playerInv, TileSingularityCell tile) {
        super(new ContainerSingularityCell(playerInv, tile));
        this.tile = tile;
        // See GuiAdvancedPulverizer's constructor for why xSize has to cover the tab flap area.
        xSize = BASE_WIDTH + GuiSideTab.MAX_WIDTH;
        ySize = BASE_HEIGHT;
        this.configTab = new TabConfigCell(this, tile);
        configTab.setStackPosition(TAB_STACK_X, TAB_STACK_Y);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;

        // See GuiAdvancedPulverizer's own copy of this comment: stepped once per rendered frame
        // (not once per game tick, which is what calling this from updateScreen() would mean) to
        // match real Thermal Expansion's own GuiBase#drawTabs - this is what makes the tab
        // animation look smooth and open at the expected speed regardless of framerate.
        configTab.update();

        drawTEPanel(left, top, BASE_WIDTH, BASE_HEIGHT);
        drawEnergyBar(left, top);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawTESlot(left + 8 + col * 18 - 1, top + PLAYER_INV_Y + row * 18 - 1);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawTESlot(left + 8 + col * 18 - 1, top + PLAYER_HOTBAR_Y - 1);
        }

        configTab.drawBackground(left, top);
    }

    /** Same real-TE {@code Energy.png} art the 3 machine GUIs use (see TabbedMachineGui#drawEnergyStored) - real TE's own Energy Cell GUI draws its bar with the identical ElementEnergyStored widget, just at this panel's own size/position. */
    private void drawEnergyBar(int left, int top) {
        long energy = tile.getEnergyStoredLong();
        long capacity = tile.getCapacityLong();
        double ratio = capacity <= 0 ? 0 : energy / (double) capacity;
        // Texture-pixel space (0-42), NOT ENERGY_HEIGHT (the already-2x'd on-screen size) -
        // drawEnergyStoredFilled's scale parameter is what stretches this to the actual gauge.
        int filled = (int) (ENERGY_BAR_HEIGHT * ratio);
        // Capacity is astronomically larger than any realistic charge rate can fill on a
        // linear scale (even hundreds of millions of RF round down to 0 of 42 pixels against a
        // 1-trillion cap) - guarantee at least a 1px sliver whenever there's any charge at all,
        // so the bar doesn't read as "completely empty" for a cell that very much isn't. The
        // exact numbers below (and the tooltip) stay the real, unrounded ground truth either way.
        if (filled <= 0 && energy > 0) {
            filled = 1;
        }
        drawEnergyStoredFilled(left + ENERGY_X, top + ENERGY_Y, filled, ENERGY_SCALE);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString(StatCollector.translateToLocal("tile.singularityCell.name"), 8, 6, 0x404040);

        long capacity = tile.getCapacityLong();
        String fillLine = formatRF(tile.getEnergyStoredLong()) + " / " + formatRF(capacity) + " RF";
        drawCenteredString(fontRendererObj, fillLine, BASE_WIDTH / 2, ENERGY_Y + ENERGY_HEIGHT + 8, 0x404040);

        String inLine = StatCollector.translateToLocalFormatted("gui.thermaladd.cell.energyIn", formatRF(tile.getEnergyInPerTick()));
        String outLine = StatCollector.translateToLocalFormatted("gui.thermaladd.cell.energyOut", formatRF(tile.getEnergyOutPerTick()));
        fontRendererObj.drawSplitString(inLine, 6, ENERGY_Y + 4, ENERGY_X - 10, 0x404040);
        fontRendererObj.drawSplitString(outLine, ENERGY_X + ENERGY_WIDTH + 6, ENERGY_Y + 4, ENERGY_X - 10, 0x404040);

        fontRendererObj.drawString(StatCollector.translateToLocal("container.inventory"), 8, PLAYER_INV_Y - 10, 0x404040);

        configTab.drawForeground(0, 0);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;
        boolean shift = GuiScreen.isShiftKeyDown();

        if (mouseButton == 0 && configTab.isMouseOverIcon(mouseX, mouseY, left, top)) {
            configTab.setOpen(!configTab.open);
            return;
        }
        if (configTab.isFullyOpen() && configTab.isMouseOverFlap(mouseX, mouseY, left, top)) {
            // Swallowed whether or not the content claimed it: this flap holds no vanilla Slots,
            // and letting the click through to GuiContainer#mouseClicked risks it being read as
            // "clicked outside the window", which drops the stack on the cursor.
            configTab.onContentClick(mouseX - left - configTab.getTabX(), mouseY - top - configTab.getTabY(), mouseButton, shift);
            return;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;

        List<String> tooltip = new ArrayList<String>();
        if (mouseX >= left + ENERGY_X && mouseX < left + ENERGY_X + ENERGY_WIDTH
                && mouseY >= top + ENERGY_Y && mouseY < top + ENERGY_Y + ENERGY_HEIGHT) {
            tooltip.add(String.format(Locale.ROOT, "%,d", tile.getEnergyStoredLong()) + " RF");
            tooltip.add(String.format(Locale.ROOT, "%,d", tile.getCapacityLong()) + " RF " + StatCollector.translateToLocal("gui.thermaladd.cell.capacity"));
        } else {
            configTab.addTooltip(mouseX, mouseY, left, top, tooltip);
        }
        if (!tooltip.isEmpty()) {
            drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
        }
    }

    private static String formatRF(long value) {
        if (value >= 1000000000000L) {
            return String.format(Locale.ROOT, "%.2fT", value / 1000000000000.0);
        }
        if (value >= 1000000000L) {
            return String.format(Locale.ROOT, "%.2fB", value / 1000000000.0);
        }
        if (value >= 1000000L) {
            return String.format(Locale.ROOT, "%.2fM", value / 1000000.0);
        }
        if (value >= 1000L) {
            return String.format(Locale.ROOT, "%.2fK", value / 1000.0);
        }
        return String.valueOf(value);
    }
}
