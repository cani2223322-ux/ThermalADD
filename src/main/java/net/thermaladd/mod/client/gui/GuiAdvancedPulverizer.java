package net.thermaladd.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.inventory.ContainerAdvancedPulverizer;
import net.thermaladd.mod.tileentity.TileAdvancedPulverizer;

/**
 * Entirely self-drawn main panel (no background texture file): a pixel-matched Thermal
 * Expansion-styled panel (see {@link TabbedMachineGui#drawTEPanel}) built out of plain
 * rectangles, since this machine's 3-parallel-input layout is taller/different from any stock
 * TE machine texture. Draws the RF bar, 3 independent progress bars (one per input line) and
 * frames for all machine slots. The Augments and Configuration side tabs, on the other hand,
 * reuse Thermal Expansion's own real tab/icon textures (see GuiSideTab) for a pixel-accurate
 * look, since CoFHCore is a hard dependency.
 */
public class GuiAdvancedPulverizer extends TabbedMachineGui {

    private static final int PROGRESS_FILL = 0xFF3CA0DC;
    private static final int PROGRESS_DONE = 0xFF3CDC6E;

    private static final int BASE_WIDTH = 176;
    private static final int BASE_HEIGHT = 178;

    private static final int ENERGY_X = 8;
    private static final int ENERGY_Y = 17;
    /** Real TE's own {@code ElementEnergyStored} size - see TabbedMachineGui#drawEnergyStored, never resized. */
    private static final int ENERGY_WIDTH = ENERGY_BAR_WIDTH;
    private static final int ENERGY_HEIGHT = ENERGY_BAR_HEIGHT;
    /** Real TE's own charge-slot offset from the bar's origin (bar at Y+0, slot at Y+45) - see ContainerAdvancedPulverizer.CHARGE_X/CHARGE_Y, which this must stay in sync with. */
    private static final int CHARGE_SLOT_X = ENERGY_X;
    private static final int CHARGE_SLOT_Y = ENERGY_Y + 45;

    private static final int PROGRESS_X = 66;
    private static final int PROGRESS_WIDTH = 46;
    private static final int PROGRESS_HEIGHT = 10;

    private static final int TAB_STACK_X = BASE_WIDTH;
    private static final int TAB_STACK_Y = 4;
    private static final int TAB_STACK_STEP = 22;
    /** Energy is docked on the LEFT edge (tabX=0, the panel's own left edge), not stacked with the other 3 right-side tabs - see TabEnergy's own javadoc. */
    private static final int LEFT_TAB_X = 0;
    private static final int LEFT_TAB_Y = 4;

    private final TileAdvancedPulverizer tile;
    private final TabAugments augmentsTab;
    private final TabConfig configTab;
    private final TabRedstoneControl redstoneTab;
    private final TabEnergy energyTab;

    public GuiAdvancedPulverizer(InventoryPlayer playerInv, TileAdvancedPulverizer tile) {
        super(new ContainerAdvancedPulverizer(playerInv, tile));
        this.tile = tile;
        // xSize/ySize drive more than just the background texture size - GuiContainer's own
        // mouseClicked() separately checks the click against (guiLeft, guiTop, xSize, ySize)
        // and forces the slot to -999 ("clicked outside the window", which drops whatever is
        // on the cursor) whenever that check fails, REGARDLESS of whether getSlotAtPosition()
        // already found a real Slot there. The augment/config tabs render past x=176 (right of
        // the main panel), so xSize has to cover that whole flap area or every click on a tab
        // slot gets treated as "outside" and the item is lost. The panel itself is still only
        // drawn at BASE_WIDTH/BASE_HEIGHT (see drawGuiContainerBackgroundLayer) - only the
        // click-bounds grow, which is exactly how real Thermal Expansion's own tabbed machine
        // GUIs end up visibly off-center-left instead of centered.
        xSize = BASE_WIDTH + GuiSideTab.MAX_WIDTH;
        ySize = BASE_HEIGHT;
        this.augmentsTab = new TabAugments(this, (ContainerAdvancedPulverizer) inventorySlots);
        this.configTab = new TabConfig(this, tile);
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
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;

        // Deliberately BASE_WIDTH/BASE_HEIGHT here, NOT xSize/ySize: xSize is padded to cover
        // the tab flap area for click-detection (see the constructor), but the visible panel
        // itself is still only the original 176x178 box.
        drawTEPanel(left, top, BASE_WIDTH, BASE_HEIGHT);

        drawEnergyBar(left, top);
        drawTESlot(left + CHARGE_SLOT_X - 1, top + CHARGE_SLOT_Y - 1);

        // Matches real Thermal Expansion: the colored role ring only appears once the player
        // has actually configured a side to grant that role - not just because the
        // Reconfigurable Sides augment is installed. A fresh machine starts with every side
        // Disabled (see TileAdvancedPulverizer#setDefaultSides), so nothing is highlighted
        // until the Configuration tab is actually used.
        int inputHighlight = tile.isAnyInputSide() ? HIGHLIGHT_INPUT : HIGHLIGHT_NONE;
        int primaryHighlight = tile.isAnyOutputPrimarySide() ? HIGHLIGHT_OUTPUT_PRIMARY : HIGHLIGHT_NONE;
        int secondaryHighlight = tile.isAnyOutputSecondarySide() ? HIGHLIGHT_OUTPUT_SECONDARY : HIGHLIGHT_NONE;
        for (int i = 0; i < TileAdvancedPulverizer.INPUT_SLOTS; i++) {
            drawTESlot(left + ContainerAdvancedPulverizer.INPUT_X - 1,
                    top + ContainerAdvancedPulverizer.INPUT_Y + i * ContainerAdvancedPulverizer.SLOT_SIZE - 1, inputHighlight);
            drawProgressBar(left, top, i);
        }
        for (int i = 0; i < TileAdvancedPulverizer.OUTPUT_PRIMARY_SLOTS; i++) {
            drawTESlot(left + ContainerAdvancedPulverizer.OUTPUT_PRIMARY_X - 1,
                    top + ContainerAdvancedPulverizer.OUTPUT_PRIMARY_Y + i * ContainerAdvancedPulverizer.SLOT_SIZE - 1, primaryHighlight);
        }
        for (int i = 0; i < TileAdvancedPulverizer.OUTPUT_SECONDARY_SLOTS; i++) {
            drawTESlot(left + ContainerAdvancedPulverizer.OUTPUT_SECONDARY_X - 1,
                    top + ContainerAdvancedPulverizer.OUTPUT_SECONDARY_Y + i * ContainerAdvancedPulverizer.SLOT_SIZE - 1, secondaryHighlight);
        }

        // player inventory slot frames
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawTESlot(left + 8 + col * 18 - 1, top + ContainerAdvancedPulverizer.PLAYER_INV_Y + row * 18 - 1);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawTESlot(left + 8 + col * 18 - 1, top + ContainerAdvancedPulverizer.PLAYER_HOTBAR_Y - 1);
        }

        augmentsTab.drawBackground(left, top);
        if (tile.augmentReconfigSides) {
            configTab.drawBackground(left, top);
        }
        if (tile.augmentRedstoneControl) {
            redstoneTab.drawBackground(left, top);
        }
        energyTab.drawBackground(left, top);
    }

    private void drawEnergyBar(int left, int top) {
        drawEnergyStored(left + ENERGY_X, top + ENERGY_Y, tile.getEnergy(), tile.getMaxEnergy());
    }

    private void drawProgressBar(int left, int top, int line) {
        int x = left + PROGRESS_X;
        int y = top + ContainerAdvancedPulverizer.INPUT_Y + line * ContainerAdvancedPulverizer.SLOT_SIZE + 4;

        drawTESocket(x, y, PROGRESS_WIDTH, PROGRESS_HEIGHT);

        int max = tile.getProgressMax(line);
        if (max <= 0) {
            return;
        }
        int p = tile.getProgress(line);
        int filled = Math.min(PROGRESS_WIDTH, PROGRESS_WIDTH * p / max);
        if (filled > 0) {
            drawRect(x, y, x + filled, y + PROGRESS_HEIGHT, p >= max ? PROGRESS_DONE : PROGRESS_FILL);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString(StatCollector.translateToLocal("tile.advancedPulverizer.name"), 8, 6, 0x404040);
        fontRendererObj.drawString(StatCollector.translateToLocal("container.inventory"),
                8, ContainerAdvancedPulverizer.PLAYER_INV_Y - 10, 0x404040);

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
     * Clicking a tab's own icon always toggles it (open/closed), closing the other tabs first.
     * Otherwise, while fully open, a click inside its flap is offered to its content (the
     * Configuration cross-buttons, the Redstone Control buttons); if nothing there claims it -
     * notably for the Augments tab, which has no custom content, only real vanilla Slots - the
     * click falls through to normal slot handling via super.mouseClicked().
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

        if (mouseX >= left + ENERGY_X && mouseX < left + ENERGY_X + ENERGY_WIDTH
                && mouseY >= top + ENERGY_Y && mouseY < top + ENERGY_Y + ENERGY_HEIGHT) {
            // Matches real ElementEnergyStored#addTooltip exactly: just "stored / max RF", always.
            List<String> energyTooltip = new ArrayList<String>();
            energyTooltip.add(tile.getEnergy() + " / " + tile.getMaxEnergy() + " RF");
            drawHoveringText(energyTooltip, mouseX, mouseY, fontRendererObj);
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
