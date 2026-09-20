package net.thermaladd.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.inventory.ContainerImprovedAssembler;
import net.thermaladd.mod.tileentity.TileImprovedAssembler;

/**
 * Same self-drawn Thermal Expansion-styled panel as the other 3 machine GUIs in this mod (see
 * {@link TabbedMachineGui#drawTEPanel}), replacing the old one-off "improvedAssembler.png"
 * texture (a flat, non-TE-styled placeholder from before this mod adopted pixel-matched TE
 * styling everywhere else). 6 schematic/output slot pairs with a small static direction arrow
 * between each pair (the real Assembler crafts instantly, no progress timer to animate), the
 * 9x2 material buffer below, and the vertical RF socket on the right edge.
 */
public class GuiImprovedAssembler extends TabbedMachineGui {

    private static final int ARROW_COLOR = 0xFF8B8B8B;
    private static final int ENERGY_FILL = 0xFFB01010;

    private static final int BASE_WIDTH = 176;
    private static final int BASE_HEIGHT = 220;

    private static final int ENERGY_X = 150;
    private static final int ENERGY_Y = 17;
    private static final int ENERGY_WIDTH = 14;
    private static final int ENERGY_HEIGHT = 58;

    private static final int TAB_STACK_X = BASE_WIDTH;
    private static final int TAB_STACK_Y = 4;
    private static final int TAB_STACK_STEP = 22;
    /** Energy is docked on the LEFT edge (tabX=0, the panel's own left edge), not stacked with the other 3 right-side tabs - see TabEnergy's own javadoc. */
    private static final int LEFT_TAB_X = 0;
    private static final int LEFT_TAB_Y = 4;

    private final TileImprovedAssembler tile;
    private final TabAugmentsAssembler augmentsTab;
    private final TabConfigAssembler configTab;
    private final TabRedstoneControl redstoneTab;
    private final TabEnergy energyTab;

    public GuiImprovedAssembler(InventoryPlayer playerInv, TileImprovedAssembler tile) {
        super(new ContainerImprovedAssembler(playerInv, tile));
        this.tile = tile;
        // See GuiAdvancedPulverizer's constructor for why xSize has to cover the tab flap
        // area, not just the main panel: GuiContainer's own mouseClicked() drops/loses
        // whatever item is on the cursor if the click lands outside (guiLeft, guiTop, xSize,
        // ySize), even when a real Slot was found there - regardless of the fact that the
        // panel itself is only ever DRAWN at BASE_WIDTH/BASE_HEIGHT below.
        xSize = BASE_WIDTH + GuiSideTab.MAX_WIDTH;
        ySize = BASE_HEIGHT;
        this.augmentsTab = new TabAugmentsAssembler(this, (ContainerImprovedAssembler) inventorySlots);
        this.configTab = new TabConfigAssembler(this, tile);
        this.redstoneTab = new TabRedstoneControl(this, tile.xCoord, tile.yCoord, tile.zCoord, tile);
        this.energyTab = new TabEnergy(this, tile);
        augmentsTab.setStackPosition(TAB_STACK_X, TAB_STACK_Y);
        configTab.setStackPosition(TAB_STACK_X, TAB_STACK_Y + TAB_STACK_STEP);
        redstoneTab.setStackPosition(TAB_STACK_X, TAB_STACK_Y + TAB_STACK_STEP * 2);
        energyTab.setStackPosition(LEFT_TAB_X, LEFT_TAB_Y);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        augmentsTab.update();
        if (!tile.augmentReconfigSides) {
            configTab.setOpen(false);
        }
        configTab.update();
        if (!tile.augmentRedstoneControl) {
            redstoneTab.setOpen(false);
        }
        redstoneTab.update();
        energyTab.update();
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString(StatCollector.translateToLocal("tile.improvedAssembler.name"), 8, 6, 0x404040);
        fontRendererObj.drawString(StatCollector.translateToLocal("container.inventory"), 8, ContainerImprovedAssembler.PLAYER_INV_Y - 10, 0x404040);

        augmentsTab.drawForeground(0, 0);
        if (tile.augmentReconfigSides) {
            configTab.drawForeground(0, 0);
        }
        if (tile.augmentRedstoneControl) {
            redstoneTab.drawForeground(0, 0);
        }
        energyTab.drawForeground(0, 0);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;

        drawTEPanel(left, top, BASE_WIDTH, BASE_HEIGHT);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 2; col++) {
                int x = left + ContainerImprovedAssembler.PAIR_X[col];
                int y = top + ContainerImprovedAssembler.ROW_Y[row];
                drawTESlot(x - 1, y - 1);
                drawTESlot(x + ContainerImprovedAssembler.OUTPUT_OFFSET - 1, y - 1);
                drawArrow(x + ContainerImprovedAssembler.SLOT_SIZE + 4, y + 5);
            }
        }

        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 9; col++) {
                drawTESlot(left + ContainerImprovedAssembler.BUFFER_X + col * ContainerImprovedAssembler.SLOT_SIZE - 1,
                        top + ContainerImprovedAssembler.BUFFER_Y + row * ContainerImprovedAssembler.SLOT_SIZE - 1);
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawTESlot(left + 8 + col * 18 - 1, top + ContainerImprovedAssembler.PLAYER_INV_Y + row * 18 - 1);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawTESlot(left + 8 + col * 18 - 1, top + ContainerImprovedAssembler.PLAYER_HOTBAR_Y - 1);
        }

        drawEnergyBar(left, top);

        augmentsTab.drawBackground(left, top);
        if (tile.augmentReconfigSides) {
            configTab.drawBackground(left, top);
        }
        if (tile.augmentRedstoneControl) {
            redstoneTab.drawBackground(left, top);
        }
        energyTab.drawBackground(left, top);
    }

    /** Small static right-pointing triangle (7x7) marking schematic -> output direction; the real Assembler crafts instantly, so there's no progress fraction to animate here. */
    private void drawArrow(int x, int y) {
        int[] widths = {1, 2, 3, 4, 3, 2, 1};
        for (int row = 0; row < widths.length; row++) {
            drawRect(x, y + row, x + widths[row], y + row + 1, ARROW_COLOR);
        }
    }

    private void drawEnergyBar(int left, int top) {
        drawTESocket(left + ENERGY_X, top + ENERGY_Y, ENERGY_WIDTH, ENERGY_HEIGHT);

        int filled = (int) (ENERGY_HEIGHT * ((float) tile.getEnergy() / (float) TileImprovedAssembler.ENERGY_CAPACITY));
        if (filled > 0) {
            drawRect(left + ENERGY_X, top + ENERGY_Y + (ENERGY_HEIGHT - filled),
                    left + ENERGY_X + ENERGY_WIDTH, top + ENERGY_Y + ENERGY_HEIGHT, ENERGY_FILL);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;
        boolean shift = GuiScreen.isShiftKeyDown();

        if (handleTabClick(augmentsTab, mouseX, mouseY, left, top, mouseButton, shift)) {
            return;
        }
        if (tile.augmentReconfigSides && handleTabClick(configTab, mouseX, mouseY, left, top, mouseButton, shift)) {
            return;
        }
        if (tile.augmentRedstoneControl && handleTabClick(redstoneTab, mouseX, mouseY, left, top, mouseButton, shift)) {
            return;
        }
        if (handleTabClick(energyTab, mouseX, mouseY, left, top, mouseButton, shift)) {
            return;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    /**
     * Clicking a tab's own icon always toggles it (open/closed), closing the other tab first.
     * Otherwise, while fully open, a click inside its flap is offered to its content (the
     * Configuration cross-buttons); if nothing there claims it - notably for the Augments
     * tab, which has no custom content, only real vanilla Slots - the click falls through to
     * normal slot handling via super.mouseClicked().
     */
    private boolean handleTabClick(GuiSideTab tab, int mouseX, int mouseY, int left, int top, int mouseButton, boolean shift) {
        if (mouseButton == 0 && tab.isMouseOverIcon(mouseX, mouseY, left, top)) {
            boolean wasOpen = tab.open;
            augmentsTab.setOpen(false);
            configTab.setOpen(false);
            redstoneTab.setOpen(false);
            energyTab.setOpen(false);
            tab.setOpen(!wasOpen);
            return true;
        }
        if (tab.isFullyOpen() && tab.isMouseOverFlap(mouseX, mouseY, left, top)) {
            return tab.onContentClick(mouseX - left - tab.getContentX(), mouseY - top - tab.getTabY(), mouseButton, shift);
        }
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;

        List<String> tooltip = new ArrayList<String>();
        augmentsTab.addTooltip(mouseX, mouseY, left, top, tooltip);
        if (tile.augmentReconfigSides) {
            configTab.addTooltip(mouseX, mouseY, left, top, tooltip);
        }
        if (tile.augmentRedstoneControl) {
            redstoneTab.addTooltip(mouseX, mouseY, left, top, tooltip);
        }
        energyTab.addTooltip(mouseX, mouseY, left, top, tooltip);
        if (!tooltip.isEmpty()) {
            drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
        }
    }
}
