package net.thermaladd.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;
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

    /** Same fluid tank real TE's own Assembler shows (GuiAssembler's ElementFluidTank) - sized/positioned to fit the gap between the 2nd output slot column and the RF socket. */
    private static final int TANK_X = 130;
    private static final int TANK_Y = 17;
    private static final int TANK_WIDTH = 14;
    private static final int TANK_HEIGHT = 58;
    /** Used whenever a fluid reports a plain white tint (most vanilla-style fluids bake their actual color into the texture, not this multiplier) so the bar isn't just a blank white sliver. */
    private static final int TANK_FALLBACK_COLOR = 0xFF3060C0;

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

        // See GuiAdvancedPulverizer's own copy of this comment: the colored role ring tracks
        // live side configuration - a fresh machine starts with every side Disabled, so
        // nothing is highlighted until the Configuration tab is actually used. The material
        // buffer's 2 rows can be configured independently (Row 1/Row 2), so each gets its own
        // live check; the schematic slots just use "is ANY input-flavored mode set anywhere".
        int schematicHighlight = tile.isAnyInputSide() ? HIGHLIGHT_INPUT : HIGHLIGHT_NONE;
        int outputHighlight = tile.isAnyOutputSide() ? HIGHLIGHT_OUTPUT : HIGHLIGHT_NONE;
        int row1Highlight = tile.isAnyInputRow1Side() ? HIGHLIGHT_INPUT : HIGHLIGHT_NONE;
        int row2Highlight = tile.isAnyInputRow2Side() ? HIGHLIGHT_INPUT : HIGHLIGHT_NONE;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 2; col++) {
                int x = left + ContainerImprovedAssembler.PAIR_X[col];
                int y = top + ContainerImprovedAssembler.ROW_Y[row];
                drawTESlot(x - 1, y - 1, schematicHighlight);
                drawTESlot(x + ContainerImprovedAssembler.OUTPUT_OFFSET - 1, y - 1, outputHighlight);
                drawArrow(x + ContainerImprovedAssembler.SLOT_SIZE + 4, y + 5);
            }
        }

        for (int row = 0; row < 2; row++) {
            int rowHighlight = row == 0 ? row1Highlight : row2Highlight;
            for (int col = 0; col < 9; col++) {
                drawTESlot(left + ContainerImprovedAssembler.BUFFER_X + col * ContainerImprovedAssembler.SLOT_SIZE - 1,
                        top + ContainerImprovedAssembler.BUFFER_Y + row * ContainerImprovedAssembler.SLOT_SIZE - 1, rowHighlight);
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
        drawFluidTank(left, top);

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

    private void drawFluidTank(int left, int top) {
        drawTESocket(left + TANK_X, top + TANK_Y, TANK_WIDTH, TANK_HEIGHT);

        FluidStack fluid = tile.getTankFluid();
        if (fluid == null || fluid.amount <= 0) {
            return;
        }
        int filled = (int) (TANK_HEIGHT * ((float) fluid.amount / (float) tile.getTankCapacity()));
        if (filled <= 0) {
            return;
        }
        int rawColor = fluid.getFluid().getColor();
        int color = (rawColor & 0xFFFFFF) == 0xFFFFFF ? TANK_FALLBACK_COLOR : (0xFF000000 | (rawColor & 0xFFFFFF));
        drawRect(left + TANK_X, top + TANK_Y + (TANK_HEIGHT - filled),
                left + TANK_X + TANK_WIDTH, top + TANK_Y + TANK_HEIGHT, color);
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

        if (mouseX >= left + TANK_X && mouseX < left + TANK_X + TANK_WIDTH
                && mouseY >= top + TANK_Y && mouseY < top + TANK_Y + TANK_HEIGHT) {
            List<String> tankTooltip = new ArrayList<String>();
            FluidStack fluid = tile.getTankFluid();
            if (fluid != null && fluid.amount > 0) {
                tankTooltip.add(fluid.getFluid().getLocalizedName(fluid));
                tankTooltip.add(fluid.amount + " / " + tile.getTankCapacity() + " mB");
            } else {
                tankTooltip.add(StatCollector.translateToLocal("gui.thermaladd.tank.empty"));
            }
            drawHoveringText(tankTooltip, mouseX, mouseY, fontRendererObj);
            return;
        }

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
