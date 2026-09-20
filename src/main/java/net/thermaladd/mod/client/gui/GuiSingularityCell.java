package net.thermaladd.mod.client.gui;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.StatCollector;

import net.thermaladd.mod.inventory.ContainerSingularityCell;
import net.thermaladd.mod.tileentity.TileSingularityCell;

/** Same flat hand-drawn panel style as GuiAdvancedPulverizer/GuiAdvancedFurnace - one big energy bar, no tabs (this block has no augments, no side config, nothing else to show). */
public class GuiSingularityCell extends GuiContainer {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_DARK = 0xFF8B8B8B;
    private static final int BORDER = 0xFF373737;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int ENERGY_FILL_LOW = 0xFF8B2FD8;
    private static final int ENERGY_FILL_HIGH = 0xFF2FD8C8;

    private static final int BASE_WIDTH = 176;
    private static final int BASE_HEIGHT = 178;

    private static final int ENERGY_X = 21;
    private static final int ENERGY_Y = 17;
    private static final int ENERGY_WIDTH = 134;
    private static final int ENERGY_HEIGHT = 40;

    private static final int PLAYER_INV_Y = ContainerSingularityCell.PLAYER_INV_Y;
    private static final int PLAYER_HOTBAR_Y = ContainerSingularityCell.PLAYER_HOTBAR_Y;

    private final TileSingularityCell tile;

    public GuiSingularityCell(InventoryPlayer playerInv, TileSingularityCell tile) {
        super(new ContainerSingularityCell(playerInv, tile));
        this.tile = tile;
        xSize = BASE_WIDTH;
        ySize = BASE_HEIGHT;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;

        drawRect(left, top, left + BASE_WIDTH, top + BASE_HEIGHT, PANEL);
        drawPanelBorder(left, top, BASE_WIDTH, BASE_HEIGHT);
        drawEnergyBar(left, top);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotFrame(left + 8 + col * 18 - 1, top + PLAYER_INV_Y + row * 18 - 1);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotFrame(left + 8 + col * 18 - 1, top + PLAYER_HOTBAR_Y - 1);
        }
    }

    private void drawPanelBorder(int left, int top, int w, int h) {
        drawRect(left, top, left + w, top + 1, BORDER);
        drawRect(left, top + h - 1, left + w, top + h, BORDER);
        drawRect(left, top, left + 1, top + h, BORDER);
        drawRect(left + w - 1, top, left + w, top + h, BORDER);
    }

    private void drawSlotFrame(int x, int y) {
        drawRect(x, y, x + 18, y + 18, BORDER);
        drawRect(x + 1, y + 1, x + 17, y + 17, SLOT_BG);
    }

    private void drawEnergyBar(int left, int top) {
        drawRect(left + ENERGY_X - 1, top + ENERGY_Y - 1,
                left + ENERGY_X + ENERGY_WIDTH + 1, top + ENERGY_Y + ENERGY_HEIGHT + 1, BORDER);
        drawRect(left + ENERGY_X, top + ENERGY_Y,
                left + ENERGY_X + ENERGY_WIDTH, top + ENERGY_Y + ENERGY_HEIGHT, PANEL_DARK);

        long energy = tile.getEnergyStoredLong();
        long capacity = tile.getCapacityLong();
        double ratio = capacity <= 0 ? 0 : energy / (double) capacity;
        int filled = (int) (ENERGY_WIDTH * ratio);
        if (filled > 0) {
            // Gradient fill (violet -> cyan) matching the block's own gradient texture, left to right.
            for (int i = 0; i < filled; i++) {
                float t = ENERGY_WIDTH <= 1 ? 0 : (float) i / (ENERGY_WIDTH - 1);
                int color = lerpColor(ENERGY_FILL_LOW, ENERGY_FILL_HIGH, t);
                drawRect(left + ENERGY_X + i, top + ENERGY_Y, left + ENERGY_X + i + 1, top + ENERGY_Y + ENERGY_HEIGHT, color);
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
        fontRendererObj.drawString(StatCollector.translateToLocal("container.inventory"), 8, PLAYER_INV_Y - 10, 0x404040);

        long energy = tile.getEnergyStoredLong();
        long capacity = tile.getCapacityLong();
        String line1 = formatRF(energy) + " / " + formatRF(capacity) + " RF";
        String line2 = String.format("%,d", energy);
        drawCenteredString(fontRendererObj, line1, xSize / 2, ENERGY_Y + ENERGY_HEIGHT + 6, 0x404040);
        drawCenteredString(fontRendererObj, "(" + line2 + ")", xSize / 2, ENERGY_Y + ENERGY_HEIGHT + 16, 0x707070);
    }

    private static String formatRF(long value) {
        if (value >= 1000000000000L) {
            return String.format("%.2fT", value / 1000000000000.0);
        }
        if (value >= 1000000000L) {
            return String.format("%.2fB", value / 1000000000.0);
        }
        if (value >= 1000000L) {
            return String.format("%.2fM", value / 1000000.0);
        }
        if (value >= 1000L) {
            return String.format("%.2fK", value / 1000.0);
        }
        return String.valueOf(value);
    }
}
