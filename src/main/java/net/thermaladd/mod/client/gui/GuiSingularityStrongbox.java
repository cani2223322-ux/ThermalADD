package net.thermaladd.mod.client.gui;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.inventory.ContainerSingularityStrongbox;
import net.thermaladd.mod.tileentity.TileSingularityStrongbox;

/** The same TE-style panel and sunken slots as the machines, sized for 15 x 8. */
public class GuiSingularityStrongbox extends TabbedMachineGui {

    private final TileSingularityStrongbox tile;

    public GuiSingularityStrongbox(InventoryPlayer playerInv, TileSingularityStrongbox tile) {
        super(new ContainerSingularityStrongbox(playerInv, tile));
        this.tile = tile;
        xSize = ContainerSingularityStrongbox.PANEL_WIDTH;
        ySize = ContainerSingularityStrongbox.PANEL_HEIGHT;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;
        drawTEPanel(left, top, xSize, ySize);
        for (int row = 0; row < TileSingularityStrongbox.ROWS; row++) {
            for (int col = 0; col < TileSingularityStrongbox.COLUMNS; col++) {
                drawTESlot(left + ContainerSingularityStrongbox.BOX_X + col * 18 - 1,
                        top + ContainerSingularityStrongbox.BOX_Y + row * 18 - 1);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawTESlot(left + ContainerSingularityStrongbox.PLAYER_INV_X + col * 18 - 1,
                        top + ContainerSingularityStrongbox.PLAYER_INV_Y + row * 18 - 1);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawTESlot(left + ContainerSingularityStrongbox.PLAYER_INV_X + col * 18 - 1,
                    top + ContainerSingularityStrongbox.PLAYER_HOTBAR_Y - 1);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        drawMachineTitle(tile, xSize);
        fontRendererObj.drawString(StatCollector.translateToLocal("container.inventory"),
                ContainerSingularityStrongbox.PLAYER_INV_X, ContainerSingularityStrongbox.PLAYER_INV_Y - 10, 0x404040);
    }
}
