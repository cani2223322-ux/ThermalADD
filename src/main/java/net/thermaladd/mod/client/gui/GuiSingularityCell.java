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

    private static final int ENERGY_FILL_LOW = 0xFF8B2FD8;
    private static final int ENERGY_FILL_HIGH = 0xFF2FD8C8;

    private static final int BASE_WIDTH = 176;
    private static final int BASE_HEIGHT = 220;

    private static final int ENERGY_WIDTH = 20;
    private static final int ENERGY_HEIGHT = 78;
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
    public void updateScreen() {
        super.updateScreen();
        configTab.update();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;

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

    private void drawEnergyBar(int left, int top) {
        drawTESocket(left + ENERGY_X, top + ENERGY_Y, ENERGY_WIDTH, ENERGY_HEIGHT);

        long energy = tile.getEnergyStoredLong();
        long capacity = tile.getCapacityLong();
        double ratio = capacity <= 0 ? 0 : energy / (double) capacity;
        int filled = (int) (ENERGY_HEIGHT * ratio);
        // Capacity is astronomically larger than any realistic charge rate can fill on a
        // linear scale (even hundreds of millions of RF round down to 0 of 78 pixels against a
        // 1-trillion cap) - guarantee at least a 1px sliver whenever there's any charge at all,
        // so the bar doesn't read as "completely empty" for a cell that very much isn't. The
        // exact numbers below (and the tooltip) stay the real, unrounded ground truth either way.
        if (filled <= 0 && energy > 0) {
            filled = 1;
        }
        if (filled > 0) {
            // Gradient fill (violet at the bottom -> cyan at the top), matching the block's own gradient texture.
            for (int i = 0; i < filled; i++) {
                float t = ENERGY_HEIGHT <= 1 ? 0 : (float) i / (ENERGY_HEIGHT - 1);
                int color = lerpColor(ENERGY_FILL_LOW, ENERGY_FILL_HIGH, t);
                int y = top + ENERGY_Y + ENERGY_HEIGHT - i - 1;
                drawRect(left + ENERGY_X, y, left + ENERGY_X + ENERGY_WIDTH, y + 1, color);
            }
        }
    }

    private static int lerpColor(int from, int to, float t) {
        int fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int r = (int) (fr + (tr - fr) * t);
        int g = (int) (fg + (tg - fg) * t);
        int b = (int) (fb + (tb - fb) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
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
            if (configTab.onContentClick(mouseX - left - configTab.getTabX(), mouseY - top - configTab.getTabY(), mouseButton, shift)) {
                return;
            }
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
